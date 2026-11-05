begin;
-- Incremental hardening over the vc110 schema. No parallel credentials/inbox/feed.
alter table sr_radar_collectors add column expires_at timestamptz not null default (now()+interval '365 days'), add column row_version bigint not null default 1, add column changed_by text;
alter table sr_event_opportunities add column row_version bigint not null default 1;
alter table sr_radar_pois add column row_version bigint not null default 1;
alter table sr_radar_source_runs add column request_hash text;
alter table sr_radar_collector_usage alter column collector_id drop not null;
alter table sr_event_opportunities drop constraint sr_event_opportunities_review_status_chk;
alter table sr_event_opportunities add constraint sr_event_opportunities_review_status_chk check(review_status in ('pending','approved','rejected','changes_requested','unpublished'));
alter table sr_radar_review_decisions drop constraint sr_radar_review_decisions_decision_chk;
alter table sr_radar_review_decisions add constraint sr_radar_review_decisions_decision_chk check(decision in ('approve','reject','request_changes','unpublish'));
create table sr_radar_revision_history (
 id bigint generated always as identity primary key, entity_type text not null, entity_id uuid not null,
 version bigint not null, actor text not null, before_data jsonb, after_data jsonb not null, created_at timestamptz not null default now()
);
create index sr_radar_revision_history_entity_idx on sr_radar_revision_history(entity_type,entity_id,id desc);
alter table sr_radar_revision_history enable row level security;
revoke all on sr_radar_revision_history from public,anon,authenticated;
grant select,insert on sr_radar_revision_history to service_role;
grant usage,select on sequence sr_radar_revision_history_id_seq to service_role;
revoke update,delete on sr_radar_review_decisions,sr_radar_collector_usage from service_role;
revoke delete on sr_radar_collectors from service_role;

create function sr_radar_revision_v2() returns trigger language plpgsql security invoker set search_path=public as $$
declare managed boolean; mode text:=current_setting('sr.radar.mode',true);
begin
 if tg_op='UPDATE' and tg_table_name<>'sr_radar_collectors' then
  managed:=case when tg_table_name='sr_event_opportunities' then (to_jsonb(old)->>'collector_id') is not null else old.metadata ? 'collector_id' end;
  if managed and coalesce(mode,'') not in ('ingest','review') then
   if tg_table_name='sr_event_opportunities' and new.status='expired' and (to_jsonb(old)->>'egress_end_at')::timestamptz<now() then
    old.status:=new.status; new:=old;
   elsif tg_table_name='sr_event_opportunities' and (to_jsonb(new)-'poi_id'-'updated_at')=(to_jsonb(old)-'poi_id'-'updated_at') and
    exists(select 1 from sr_radar_pois where id=(to_jsonb(new)->>'poi_id')::uuid and status='active') then null;
   else raise exception 'collector_record_requires_review' using errcode='PT403'; end if;
  end if;
  if tg_table_name='sr_event_opportunities' and old.status in ('cancelled','expired') and new.status='active' then new.status:=old.status; end if;
 end if;
 if tg_op='UPDATE' and (to_jsonb(new)-'last_used_at'-'last_verified_at'-'ingest_run_id'-'updated_at')=(to_jsonb(old)-'last_used_at'-'last_verified_at'-'ingest_run_id'-'updated_at') then new.row_version:=old.row_version;return new;end if;
 new.row_version:=case when tg_op='INSERT' then 1 else old.row_version+1 end;
 return new;
end $$;
create function sr_radar_history_v2() returns trigger language plpgsql security invoker set search_path=public as $$
begin
 insert into sr_radar_revision_history(entity_type,entity_id,version,actor,before_data,after_data)
 values(tg_table_name,new.id,new.row_version,coalesce(nullif(current_setting('sr.radar.actor',true),''),to_jsonb(new)->>'changed_by','internal'),
 case when tg_op='UPDATE' then to_jsonb(old)-'key_hash' else null end,to_jsonb(new)-'key_hash');
 return new;
end $$;
do $$ declare t text; begin foreach t in array array['sr_radar_collectors','sr_event_opportunities','sr_radar_pois'] loop
 execute format('create trigger radar_revision_v2 before insert or update on %I for each row execute function sr_radar_revision_v2()',t);
 execute format('create trigger radar_history_v2 after insert or update on %I for each row execute function sr_radar_history_v2()',t);
end loop; end $$;

create function sr_radar_budget_v2(p_key text,p_limit int,p_seconds int) returns boolean language plpgsql security invoker set search_path=public as $$
declare n int;
begin
 if p_limit not between 1 and 10000 or p_seconds not between 1 and 3600 then return false; end if;
 insert into security_rate_limits(key_hash,attempts) values(p_key,1)
 on conflict(key_hash) do update set
 attempts=case when security_rate_limits.window_started_at+make_interval(secs=>p_seconds)<=now() then 1 else security_rate_limits.attempts+1 end,
 window_started_at=case when security_rate_limits.window_started_at+make_interval(secs=>p_seconds)<=now() then now() else security_rate_limits.window_started_at end,updated_at=now()
 returning attempts into n; return n<=p_limit;
end $$;
grant select,insert,update on security_rate_limits to service_role;

create function sr_radar_receive_v2(p_collector uuid,p_key_hash text,p_kind text,p_run text,p_hash text,p_items jsonb,p_rejected jsonb,p_received int,p_dry boolean)
returns jsonb language plpgsql security invoker set search_path=public as $$
declare c sr_radar_collectors; old_event sr_event_opportunities; e sr_event_opportunities; old_poi sr_radar_pois; p sr_radar_pois;
 item jsonb; body jsonb; source_name text; ext text; previous sr_radar_source_runs; results jsonb:=p_rejected; r jsonb; changed boolean;
 accepted int:=0; unchanged int:=0; rejected int:=jsonb_array_length(p_rejected); pending int:=0; code text; outcome text; next_review text; target uuid; summary jsonb;
begin
 select * into strict c from sr_radar_collectors where id=p_collector for update;
 if c.status<>'active' or c.expires_at<=now() or c.key_hash<>p_key_hash then raise exception 'collector_inactive' using errcode='PT401'; end if;
 if p_kind not in ('event','poi') or not (case when p_kind='event' then 'events:ingest' else 'pois:propose' end)=any(c.scopes) then raise exception 'scope_missing' using errcode='PT403'; end if;
 if jsonb_array_length(p_items)>(case when p_kind='event' then c.max_batch else 50 end) then raise exception 'batch_too_large' using errcode='PT413'; end if;
 select * into previous from sr_radar_source_runs where collector_id=c.id and run_id=p_run;
 if found then
  if previous.request_hash is distinct from p_hash then raise exception 'run_payload_conflict' using errcode='PT409'; end if;
  return previous.summary||jsonb_build_object('idempotent_replay',true);
 end if;
 perform set_config('sr.radar.mode','ingest',true); perform set_config('sr.radar.actor',c.id::text,true);
 for item in select value from jsonb_array_elements(p_items) order by value->'row'->>'source',coalesce(value->'row'->>'external_id',value->'row'->>'source_external_id') loop
  body:=item->'row'; source_name:=body->>'source'; ext:=coalesce(body->>'external_id',body->>'source_external_id'); target:=null;
  if source_name is null or ext is null or not source_name=any(c.allowed_sources) or source_name in ('ticketmaster','manual','admin','internal') then raise exception 'source_not_allowed' using errcode='PT403'; end if;
  perform pg_advisory_xact_lock(hashtextextended(p_kind||'|'||source_name||'|'||ext,0));
  outcome:='accepted'; code:='created_pending'; next_review:='pending';
  if p_kind='event' then
   select * into old_event from sr_event_opportunities where source=source_name and external_id=ext for update;
   if found and old_event.collector_id is distinct from c.id then outcome:='rejected'; code:='owned_by_other_ingest';
   elsif old_event.id is not null and old_event.status in ('cancelled','expired') then outcome:='unchanged'; code:=old_event.status||'_kept'; target:=old_event.id;
   else
    e:=jsonb_populate_record(old_event,body); e.id:=coalesce(old_event.id,gen_random_uuid()); e.collector_id:=c.id; e.ingest_run_id:=p_run;
    e.created_at:=coalesce(old_event.created_at,now()); e.first_seen_at:=coalesce(old_event.first_seen_at,now()); e.updated_at:=now(); e.metadata:=coalesce(e.metadata,'{}');
    if e.egress_end_at<=now() and e.status='active' then e.status:='expired'; end if;
    changed:=old_event.id is null or exists(select 1 from jsonb_each(body) kv where kv.key not in ('updated_at','last_verified_at') and (to_jsonb(old_event)->kv.key) is distinct from (to_jsonb(e)->kv.key));
    if old_event.id is not null and changed and e.last_verified_at<old_event.last_verified_at then outcome:='rejected';code:='stale_verification';
    elsif not changed then outcome:='unchanged';code:='no_change'; target:=old_event.id;
     if not p_dry then update sr_event_opportunities set last_verified_at=greatest(e.last_verified_at,old_event.last_verified_at),ingest_run_id=p_run where id=old_event.id;end if;
    else
     next_review:=case when old_event.review_status in ('rejected','changes_requested','unpublished') then old_event.review_status else 'pending' end;
     if e.status='cancelled' and old_event.id is not null then next_review:=old_event.review_status;code:='cancelled_applied';end if;
     e.review_status:=next_review;
     if old_event.id is not null then code:='updated_'||next_review; end if;
     if not p_dry then
      if old_event.id is null then insert into sr_event_opportunities select (e).* returning id into target;
      else update sr_event_opportunities set name=e.name,event_type=e.event_type,venue_name=e.venue_name,address=e.address,city=e.city,state=e.state,country_code=e.country_code,
       lat=e.lat,lng=e.lng,poi_id=case when old_event.lat is distinct from e.lat or old_event.lng is distinct from e.lng or old_event.venue_name is distinct from e.venue_name or old_event.address is distinct from e.address then null else old_event.poi_id end,starts_at=e.starts_at,expected_end_at=e.expected_end_at,egress_start_at=e.egress_start_at,egress_end_at=e.egress_end_at,
       source_url=e.source_url,confidence=e.confidence,end_time_source=e.end_time_source,status=e.status,metadata=e.metadata,last_verified_at=e.last_verified_at,
       review_status=e.review_status,ingest_run_id=p_run,updated_at=now() where id=e.id returning id into target; end if;
     end if;
    end if;
   end if;
  else
   select * into old_poi from sr_radar_pois where source=source_name and source_external_id=ext for update;
   if found and old_poi.metadata->>'collector_id' is distinct from c.id::text then outcome:='rejected';code:='owned_by_other_ingest';
   else
    p:=jsonb_populate_record(old_poi,body); p.id:=coalesce(old_poi.id,gen_random_uuid());p.created_at:=coalesce(old_poi.created_at,now());p.updated_at:=now();p.last_verified_at:=coalesce(p.last_verified_at,now());
    changed:=old_poi.id is null or exists(select 1 from jsonb_each(body) kv where kv.key not in ('metadata','updated_at','last_verified_at') and (to_jsonb(old_poi)->kv.key) is distinct from (to_jsonb(p)->kv.key));
    if not changed then outcome:='unchanged';code:='no_change';target:=old_poi.id;
    else
     next_review:=case when old_poi.metadata->>'review' in ('rejected','changes_requested','unpublished') then old_poi.metadata->>'review' else 'pending' end;
     p.status:='inactive';p.aliases:=coalesce(p.aliases,'[]');p.metadata:=coalesce(old_poi.metadata,'{}')||coalesce(p.metadata,'{}')||jsonb_build_object('collector_id',c.id,'review',next_review);
     if not p_dry then
      if old_poi.id is null then insert into sr_radar_pois select(p).* returning id into target;
      else update sr_radar_pois set canonical_name=p.canonical_name,normalized_name=p.normalized_name,poi_type=p.poi_type,address=p.address,city=p.city,state=p.state,country_code=p.country_code,
       lat=p.lat,lng=p.lng,geo_cell=p.geo_cell,source_url=p.source_url,source_confidence=p.source_confidence,status='inactive',metadata=p.metadata,last_verified_at=p.last_verified_at,updated_at=now() where id=p.id returning id into target;end if;
     end if;
    end if;
   end if;
  end if;
  if outcome='accepted' then accepted:=accepted+1;if next_review='pending' then pending:=pending+1;end if;
  elsif outcome='unchanged' then unchanged:=unchanged+1;else rejected:=rejected+1;end if;
  results:=results||jsonb_build_array(jsonb_build_object('index',item->'index','source',source_name,'external_id',ext,'status',outcome,'code',code,'event_id',target,'poi_id',case when p_kind='poi' then target else null end));
 end loop;
 summary:=jsonb_build_object('ok',true,'contract','srrotas-radar-collector-api-v1','kind',p_kind,'run_id',p_run,'dry_run',p_dry,'received',p_received,'accepted',accepted,'unchanged',unchanged,'rejected',rejected,'pending_review',pending,'expired',0,'items',results);
 if not p_dry then insert into sr_radar_source_runs(source,status,collector_id,run_id,request_hash,fetched_count,saved_count,rejected_count,summary)
 values((select case when count(distinct value->'row'->>'source')=1 then min(value->'row'->>'source') else 'collector_multi' end from jsonb_array_elements(p_items)),case when rejected=p_received then 'error' else 'ok' end,c.id,p_run,p_hash,p_received,accepted,rejected,summary);end if;
 perform set_config('sr.radar.mode','',true);perform set_config('sr.radar.actor','',true);
 return summary;
end $$;

create function sr_radar_review_v2(p_kind text,p_id uuid,p_version bigint,p_decision text,p_note text,p_actor text)
returns jsonb language plpgsql security invoker set search_path=public as $$
declare e sr_event_opportunities;p sr_radar_pois;previous text;next_review text;result jsonb;
begin
 if p_decision not in ('approve','reject','request_changes','unpublish') or length(trim(coalesce(p_note,''))) not between 3 and 1000 or coalesce(p_actor,'')='' then raise exception 'invalid_review' using errcode='PT400';end if;
 next_review:=case p_decision when 'approve' then 'approved' when 'reject' then 'rejected' when 'unpublish' then 'unpublished' else 'changes_requested' end;
 perform set_config('sr.radar.mode','review',true);perform set_config('sr.radar.actor',p_actor,true);
 if p_kind='event' then
  select * into strict e from sr_event_opportunities where id=p_id for update;
  if p_version is null or e.row_version<>p_version then raise exception 'review_conflict' using errcode='PT409';end if;
  if p_decision='approve' and (e.status<>'active' or e.egress_end_at<=now()) then raise exception 'terminal_or_expired' using errcode='PT409';end if;
  previous:=e.review_status;
  update sr_event_opportunities set review_status=next_review,review_note=p_note,reviewed_by=p_actor,reviewed_at=now(),updated_at=now() where id=e.id returning to_jsonb(sr_event_opportunities.*) into result;
 elsif p_kind='poi' then
  select * into strict p from sr_radar_pois where id=p_id for update;
  if p_version is null or p.row_version<>p_version then raise exception 'review_conflict' using errcode='PT409';end if;
  previous:=p.metadata->>'review';
  update sr_radar_pois set status=case when p_decision='approve' then 'active' else 'inactive' end,
   metadata=metadata||jsonb_build_object('review',next_review,'reviewed_by',p_actor,'reviewed_at',now(),'review_note',p_note),updated_at=now()
   where id=p.id returning to_jsonb(sr_radar_pois.*) into result;
 else raise exception 'invalid_kind';end if;
 insert into sr_radar_review_decisions(target_type,target_id,decision,note,actor_email,previous_status) values(p_kind,p_id,p_decision,p_note,p_actor,previous);
 perform set_config('sr.radar.mode','',true);perform set_config('sr.radar.actor','',true);
 return result;
end $$;

create function sr_radar_run_health_v2(p_ids bigint[]) returns jsonb language sql security invoker set search_path=public as $$
 select coalesce(jsonb_agg(to_jsonb(t)),'[]') from (
 select r.id,
 count(*) filter(where coalesce(e.review_status,p.metadata->>'review')='approved') as approved_count,
 count(*) filter(where coalesce(e.review_status,p.metadata->>'review')='rejected') as rejected_count,
 count(*) filter(where e.status='expired' or e.egress_end_at<now()) as expired_count
 from sr_radar_source_runs r
 left join lateral jsonb_array_elements(r.summary->'items') a on true
 left join sr_event_opportunities e on coalesce(r.summary->>'kind','event')='event' and e.id=(a->>'event_id')::uuid
 left join sr_radar_pois p on r.summary->>'kind'='poi' and p.id=(a->>'event_id')::uuid
 where r.id=any(p_ids) group by r.id
 ) t;
$$;
revoke all on function sr_radar_run_health_v2(bigint[]) from public,anon,authenticated;
grant execute on function sr_radar_run_health_v2(bigint[]) to service_role;

-- Explicit ACL, including trigger functions: no EXECUTE inherited from PUBLIC.
revoke all on function sr_radar_revision_v2(),sr_radar_history_v2(),sr_radar_budget_v2(text,int,int),sr_radar_receive_v2(uuid,text,text,text,text,jsonb,jsonb,int,boolean),sr_radar_review_v2(text,uuid,bigint,text,text,text) from public,anon,authenticated;
grant execute on function sr_radar_revision_v2(),sr_radar_history_v2(),sr_radar_budget_v2(text,int,int),sr_radar_receive_v2(uuid,text,text,text,text,jsonb,jsonb,int,boolean),sr_radar_review_v2(text,uuid,bigint,text,text,text) to service_role;
commit;
