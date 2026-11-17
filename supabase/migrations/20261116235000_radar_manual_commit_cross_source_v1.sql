-- Sr. Rotas Radar: cross-writer exact-duplicate gate and private manual POI review import.
-- SECURITY: only an authenticated Admin two-step commit publishes eligible POIs;
-- upload alone never changes Radar, and duplicates/incomplete rows are skipped.
-- Apply only after isolated transaction/concurrency and ACL tests pass.
create or replace function public.sr_radar_dedupe_name_v1(v text)
returns text language sql immutable strict parallel safe set search_path='' as $$
  select trim(regexp_replace(
    translate(lower(v),'áàâãäéêèëíìîïóôõòöúùûüçñ','aaaaaeeeeiiiiooooouuuucn'),
    '[^a-z0-9]+',' ','g'
  ))
$$;
revoke all on function public.sr_radar_dedupe_name_v1(text) from public,anon,authenticated;
grant execute on function public.sr_radar_dedupe_name_v1(text) to service_role;
-- A shared BEFORE INSERT trigger participates even when a writer is
-- Ticketmaster, collector RPC, or admin; advisory locks are transaction-scoped.
create or replace function private.sr_radar_global_dedupe_gate_v1()
returns trigger language plpgsql security definer set search_path='' as $$
declare v_id uuid;
begin
  if tg_table_name='sr_radar_pois' then
    perform pg_catalog.pg_advisory_xact_lock(20261116,801);
    select e.id into v_id from public.sr_radar_pois e
    where (e.source is distinct from new.source or e.source_external_id is distinct from new.source_external_id)
      and public.sr_radar_dedupe_name_v1(e.canonical_name)=public.sr_radar_dedupe_name_v1(new.canonical_name)
      and public.sr_radar_dedupe_name_v1(e.address)=public.sr_radar_dedupe_name_v1(new.address)
      and e.address is not null and new.address is not null
      and abs(e.lat-new.lat)<0.00025 and abs(e.lng-new.lng)<0.00025
      and 12742000*asin(sqrt(least(1,power(sin(radians(e.lat-new.lat)/2),2) +
          cos(radians(e.lat))*cos(radians(new.lat))*power(sin(radians(e.lng-new.lng)/2),2))))<=20
    order by e.id limit 1;
  else
    perform pg_catalog.pg_advisory_xact_lock(20261116,802);
    select e.id into v_id from public.sr_event_opportunities e
    where (e.source is distinct from new.source or e.external_id is distinct from new.external_id)
      and public.sr_radar_dedupe_name_v1(e.name)=public.sr_radar_dedupe_name_v1(new.name)
      and public.sr_radar_dedupe_name_v1(coalesce(e.venue_name,''))=
          public.sr_radar_dedupe_name_v1(coalesce(new.venue_name,''))
      and abs(extract(epoch from e.starts_at-new.starts_at))<=60
      and abs(e.lat-new.lat)<0.00025 and abs(e.lng-new.lng)<0.00025
      and 12742000*asin(sqrt(least(1,power(sin(radians(e.lat-new.lat)/2),2) +
          cos(radians(e.lat))*cos(radians(new.lat))*power(sin(radians(e.lng-new.lng)/2),2))))<=20
    order by e.id limit 1;
  end if;
  if v_id is not null then
    raise exception using errcode='23505', message='radar_cross_source_duplicate',
      detail='existing_id='||v_id::text;
  end if;
  return new;
end $$;
revoke all on function private.sr_radar_global_dedupe_gate_v1() from public,anon,authenticated,service_role;
drop trigger if exists radar_global_dedupe_gate_v1 on public.sr_radar_pois;
create trigger radar_global_dedupe_gate_v1 before insert on public.sr_radar_pois
for each row execute function private.sr_radar_global_dedupe_gate_v1();
-- Events are NOT imported by this flow. Leave Ticketmaster event batches
-- unaffected until event-specific cross-source handling is implemented.
drop trigger if exists radar_global_dedupe_gate_v1 on public.sr_event_opportunities;

create table if not exists public.sr_radar_manual_import_batches(
  batch_id uuid primary key default gen_random_uuid(),
  upload_id uuid not null references public.sr_radar_json_uploads(upload_id),
  source_batch_id text not null,
  actor_id uuid not null,
  actor_email text not null,
  idempotency_key uuid not null,
  request_hash text not null,
  receipt jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  unique(actor_id,idempotency_key)
);
create table if not exists public.sr_radar_manual_import_items(
  batch_id uuid not null references public.sr_radar_manual_import_batches(batch_id),
  upload_id uuid not null references public.sr_radar_json_uploads(upload_id),
  client_ref text not null,
  kind text not null check(kind in ('poi','event')),
  result text not null check(result in ('created','duplicate','pending')),
  target_id uuid,
  result_reason text not null,
  decisions jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  primary key(upload_id,kind,client_ref)
);
create index if not exists sr_radar_manual_import_items_batch_idx
on public.sr_radar_manual_import_items(batch_id);
create table if not exists public.sr_radar_manual_import_audit(
  audit_id bigint generated always as identity primary key,
  batch_id uuid not null references public.sr_radar_manual_import_batches(batch_id),
  actor_id uuid not null,
  action text not null check(action in ('commit','replay')),
  created_at timestamptz not null default now()
);
do $$
declare n text;
begin
  foreach n in array array[
    'sr_radar_manual_import_batches','sr_radar_manual_import_items',
    'sr_radar_manual_import_audit'
  ] loop
    execute format('alter table public.%I enable row level security',n);
    execute format('revoke all on table public.%I from public,anon,authenticated,service_role',n);
  end loop;
end $$;
revoke all on sequence public.sr_radar_manual_import_audit_audit_id_seq
from public,anon,authenticated,service_role;

-- The importer has NO ability to approve or activate a POI, and does not
-- update existing sources/objects. Preview classifications never authorize writes.
create or replace function public.sr_radar_manual_poi_commit_v1(
  p_actor_id uuid,p_email text,p_session_hash text,
  p_upload_id uuid,p_key uuid,p_selected jsonb,p_decisions jsonb default '{}'::jsonb
) returns jsonb language plpgsql security definer set search_path='' as $$
declare
  v_upload public.sr_radar_json_uploads%rowtype;
  v_bytes bytea; v_envelope jsonb; v_pois jsonb; v_keyhash text;
  v_prior public.sr_radar_manual_import_batches%rowtype;
  v_batch uuid:=gen_random_uuid(); v_row jsonb; v_ref text;
  v_seen text[]:=array[]::text[]; v_selected text[];
  v_name text; v_address text; v_city text; v_state text; v_type text;
  v_lat double precision; v_lng double precision; v_source_url text;
  v_sourceid text; v_id uuid; v_existing public.sr_radar_pois%rowtype;
  v_result text; v_reason text; v_decisions jsonb;
  v_statuses jsonb:='[]'::jsonb; v_created int:=0;v_skipped int:=0;v_pending int:=0;
  v_candidate uuid; v_decision jsonb;v_has_suspect boolean; v_is_same boolean;
  v_bad boolean;v_original text;
begin
  perform private.sr_radar_json_actor_v1(p_actor_id,p_email,p_session_hash);
  if p_key is null or p_upload_id is null or p_selected is null or
     jsonb_typeof(p_selected)<>'array' or jsonb_array_length(p_selected) not between 1 and 500 or
     p_decisions is null or jsonb_typeof(p_decisions)<>'object' or
     length(p_decisions::text)>64000 then
    raise sqlstate 'PT422' using message='INVALID_SELECTION';
  end if;
  select array_agg(value order by value) into v_selected
  from jsonb_array_elements_text(p_selected) as x(value);
  if v_selected is null or cardinality(v_selected)<>cardinality(array(select distinct unnest(v_selected))) then
    raise sqlstate 'PT422' using message='DUPLICATE_SELECTION';
  end if;
  if exists(select 1 from unnest(v_selected) x where x !~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$') then
    raise sqlstate 'PT422' using message='INVALID_SELECTION';
  end if;
  v_keyhash:=encode(sha256(convert_to(
    jsonb_build_object('upload_id',p_upload_id,'selected',to_jsonb(v_selected),'decisions',p_decisions)::text,'UTF8')),'hex');
  -- One shared global POI lock also acquired by the trigger on EVERY POI insert.
  perform pg_catalog.pg_advisory_xact_lock(20261116,801);
  select * into v_prior from public.sr_radar_manual_import_batches
  where actor_id=p_actor_id and idempotency_key=p_key;
  if found then
    if v_prior.request_hash<>v_keyhash then raise sqlstate 'PT409' using message='IDEMPOTENCY_CONFLICT'; end if;
    return v_prior.receipt||jsonb_build_object('idempotent_replay',true);
  end if;
  select * into v_upload from public.sr_radar_json_uploads
  where upload_id=p_upload_id and status='quarantined' for share;
  if not found then raise sqlstate 'PT404' using message='UPLOAD_NOT_FOUND'; end if;
  select payload into v_bytes from private.sr_radar_json_upload_bytes where upload_id=p_upload_id;
  if not found or encode(sha256(v_bytes),'hex')<>v_upload.sha256 then
    raise sqlstate 'PT409' using message='UPLOAD_INTEGRITY_MISMATCH';
  end if;
  begin v_envelope:=convert_from(v_bytes,'UTF8')::jsonb;
  exception when others then raise sqlstate 'PT422' using message='INVALID_ENVELOPE'; end;
  if v_envelope->>'schema_version'<>'radar.admin-import.v2' or
     jsonb_typeof(v_envelope->'pois')<>'array' or
     jsonb_typeof(v_envelope->'events')<>'array' or
     v_envelope->>'batch_id' is null or
     jsonb_array_length(v_envelope->'pois')+jsonb_array_length(v_envelope->'events')>500 then
    raise sqlstate 'PT422' using message='INVALID_ENVELOPE';
  end if;
  v_original:=v_envelope->>'batch_id';
  v_pois:=v_envelope->'pois';
  insert into public.sr_radar_manual_import_batches
    (batch_id,upload_id,source_batch_id,actor_id,actor_email,idempotency_key,request_hash)
    values(v_batch,p_upload_id,v_original,p_actor_id,p_email,p_key,v_keyhash);
  foreach v_ref in array v_selected loop
    v_row:=null;
    for v_decision in select value from jsonb_array_elements(v_pois) loop
      if v_decision->>'client_ref'=v_ref then
        if v_row is not null then raise sqlstate 'PT422' using message='DUPLICATE_CLIENT_REF'; end if;
        v_row:=v_decision;
      end if;
    end loop;
    if v_row is null then raise sqlstate 'PT422' using message='ITEM_NOT_FOUND'; end if;
    v_name:=trim(v_row->>'canonical_name');v_address:=trim(v_row->>'address');
    v_city:=trim(v_row->>'city');v_state:=trim(v_row->>'state');
    v_type:=v_row->>'poi_type';v_result:='pending';v_reason:='NEEDS_INPUT';v_id:=null;
    v_bad:=false;
    if v_name is null or length(v_name) not between 3 and 200 or
       v_address is null or length(v_address) not between 3 and 500 or
       v_city is null or length(v_city) not between 2 and 100 or
       (v_state is null or v_state !~ '^[A-Z]{2}$') or
       coalesce(v_type,'') not in ('music','sports','theatre','fair_convention','cultural',
                       'mall','hospital','hotel','airport','bus_terminal','mobility_hub',
                       'market','arena','business_center') or
       jsonb_typeof(v_row->'lat')<>'number' or jsonb_typeof(v_row->'lng')<>'number' or
       jsonb_typeof(v_row->'evidence')<>'array' or
       jsonb_array_length(v_row->'evidence')<1 or
       coalesce(v_row->>'geojson_conflict','false')='true' or
       coalesce(v_row->>'package_conflict','false')='true' then
      v_bad:=true;
    end if;
    if not v_bad then
      v_lat:=(v_row->>'lat')::double precision;
      v_lng:=(v_row->>'lng')::double precision;
      if v_lat not between -34 and 5.5 or v_lng not between -74.5 and -28.5 then v_bad:=true; end if;
      v_source_url:=v_row->'evidence'->0->>'url';
      if v_source_url is null or v_source_url !~ '^https://[A-Za-z0-9.-]+(/|$)' or
         length(v_source_url)>1200 then v_bad:=true; end if;
    end if;
    if v_bad then
      v_reason:='MISSING_OR_INVALID_FIELDS';
    else
      v_sourceid:=encode(sha256(convert_to(v_original||'|'||v_ref,'UTF8')),'hex');
      select * into v_existing from public.sr_radar_pois e
      where (e.source='admin_manual' and e.source_external_id=v_sourceid)
         or ((e.source=v_row->'origin_ref'->>'source') and
             (e.source_external_id=v_row->'origin_ref'->>'external_id') and
             public.sr_radar_dedupe_name_v1(e.canonical_name)=public.sr_radar_dedupe_name_v1(v_name))
      order by e.id limit 1;
      if found then
        v_result:='duplicate';v_reason:='ALREADY_IMPORTED';v_id:=v_existing.id;
      else
        select * into v_existing from public.sr_radar_pois e
        where public.sr_radar_dedupe_name_v1(e.canonical_name)=public.sr_radar_dedupe_name_v1(v_name)
          and public.sr_radar_dedupe_name_v1(e.address)=public.sr_radar_dedupe_name_v1(v_address)
          and abs(e.lat-v_lat)<0.00025 and abs(e.lng-v_lng)<0.00025
          and 12742000*asin(sqrt(least(1,power(sin(radians(e.lat-v_lat)/2),2) +
            cos(radians(e.lat))*cos(radians(v_lat))*power(sin(radians(e.lng-v_lng)/2),2))))<=20
        order by e.id limit 1;
        if found then
          v_result:='duplicate';v_reason:='EXACT_CROSS_SOURCE_DUPLICATE';v_id:=v_existing.id;
        else
          v_has_suspect:=false;v_is_same:=false;v_candidate:=null;
          for v_existing in select * from public.sr_radar_pois e
          where abs(e.lat-v_lat)<0.002 and abs(e.lng-v_lng)<0.002
            and 12742000*asin(sqrt(least(1,power(sin(radians(e.lat-v_lat)/2),2) +
              cos(radians(e.lat))*cos(radians(v_lat))*power(sin(radians(e.lng-v_lng)/2),2))))<=150
            and (position(public.sr_radar_dedupe_name_v1(e.canonical_name)
              in public.sr_radar_dedupe_name_v1(v_name))>0 or
                 position(public.sr_radar_dedupe_name_v1(v_name)
              in public.sr_radar_dedupe_name_v1(e.canonical_name))>0 or
                 public.sr_radar_dedupe_name_v1(e.address)=public.sr_radar_dedupe_name_v1(v_address))
          loop
            v_has_suspect:=true;
            v_decision:=p_decisions->v_ref->(v_existing.id::text);
            if v_decision->>'choice'='same' then
              v_is_same:=true;v_candidate:=v_existing.id;
            elsif v_decision->>'choice'='distinct' and
                  length(trim(coalesce(v_decision->>'reason','')))>=10 then
              continue;
            else
              v_reason:='POSSIBLE_DUPLICATE_NEEDS_DECISION';
              v_candidate:=v_existing.id;
              exit;
            end if;
          end loop;
          if v_is_same then
            v_result:='duplicate';v_reason:='OPERATOR_CONFIRMED_DUPLICATE';v_id:=v_candidate;
          elsif v_has_suspect and v_candidate is not null then
            v_result:='pending';
          else
            begin
              insert into public.sr_radar_pois(
                canonical_name,normalized_name,poi_type,address,city,state,country_code,
                lat,lng,geo_cell,region_key,region_label,source,source_external_id,source_url,
                status,source_confidence,metadata)
              values(
                v_name,public.sr_radar_dedupe_name_v1(v_name),v_type,
                v_address,v_city,v_state,'BR',v_lat,v_lng,
                'g2:'||floor(v_lat*100)::text||':'||floor(v_lng*100)::text,
                lower(regexp_replace(coalesce(v_row->>'region','sem_regiao'),'[^a-zA-Z0-9]+','_','g')),
                coalesce(v_row->>'region','Sem região'),
                'admin_manual',v_sourceid,v_source_url,
                'active',0.5,jsonb_build_object('review','approved',
                  'reviewed_by',p_email,'reviewed_at',now(),
                  'publication_basis','admin_double_confirmation',
                  'ingest_kind','manual','manual_import',jsonb_build_object(
                    'upload_id',p_upload_id,'batch_id',v_batch,'source_batch_id',v_original,
                    'client_ref',v_ref,'evidence',v_row->'evidence',
                    'rights_status','operator_confirmed','decision',p_decisions->v_ref))
              ) returning id into v_id;
              v_result:='created';v_reason:='PUBLISHED_AFTER_ADMIN_CONFIRMATION';
            exception when unique_violation then
              -- Trigger/unique constraint won a concurrent race. Never retry as a new entity.
              v_result:='pending';v_reason:='CONCURRENT_DUPLICATE_RECHECK_REQUIRED';v_id:=null;
            end;
          end if;
        end if;
      end if;
    end if;
    -- Another commit on the same uploaded file must never duplicate the item.
    if exists(select 1 from public.sr_radar_manual_import_items
              where upload_id=p_upload_id and kind='poi' and client_ref=v_ref) then
      select target_id into v_id
      from public.sr_radar_manual_import_items
      where upload_id=p_upload_id and kind='poi' and client_ref=v_ref;
      v_result:='duplicate';v_reason:='PREVIOUSLY_PROCESSED';
    else
      insert into public.sr_radar_manual_import_items(
        batch_id,upload_id,client_ref,kind,result,target_id,result_reason,decisions)
      values(v_batch,p_upload_id,v_ref,'poi',v_result,v_id,v_reason,coalesce(p_decisions->v_ref,'{}'::jsonb));
    end if;
    if v_result='created' then v_created:=v_created+1;
    elsif v_result='duplicate' then v_skipped:=v_skipped+1;
    else v_pending:=v_pending+1;end if;
    v_statuses:=v_statuses||jsonb_build_array(jsonb_build_object(
      'item_id',v_ref,'kind','poi','result',v_result,
      'reason',v_reason,'target_id',v_id,'initial_review','pending'));
  end loop;
  update public.sr_radar_manual_import_batches
  set receipt=jsonb_build_object('batch_id',v_batch,'upload_id',p_upload_id,
    'source_batch_id',v_original,'created_at',now(),'created',v_created,
    'ignored',v_skipped,'pending',v_pending,'published',v_created,
    'items',v_statuses,'publication','active_after_admin_double_confirmation') where batch_id=v_batch;
  insert into public.sr_radar_manual_import_audit(batch_id,actor_id,action)
    values(v_batch,p_actor_id,'commit');
  return (select receipt from public.sr_radar_manual_import_batches where batch_id=v_batch);
end $$;
revoke all on function public.sr_radar_manual_poi_commit_v1(uuid,text,text,uuid,uuid,jsonb,jsonb)
from public,anon,authenticated;
grant execute on function public.sr_radar_manual_poi_commit_v1(uuid,text,text,uuid,uuid,jsonb,jsonb)
to service_role;

create or replace function public.sr_radar_manual_receipts_v1(
 p_actor_id uuid,p_email text,p_session_hash text,p_batch_id uuid default null)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_rows jsonb;
begin
 perform private.sr_radar_json_actor_v1(p_actor_id,p_email,p_session_hash);
 select coalesce(jsonb_agg(r.receipt order by r.created_at desc),'[]'::jsonb)
 into v_rows from (
   select receipt,created_at from public.sr_radar_manual_import_batches
   where p_batch_id is null or batch_id=p_batch_id
   order by created_at desc limit 50
 ) r;
 return v_rows;
end $$;
revoke all on function public.sr_radar_manual_receipts_v1(uuid,text,text,uuid)
from public,anon,authenticated;
grant execute on function public.sr_radar_manual_receipts_v1(uuid,text,text,uuid)
to service_role;


-- Collector compatibility: exact duplicate from a different source is an
-- item-level rejection, not a transaction-wide failed run.
create or replace function public.sr_radar_receive_v2(p_collector uuid,p_key_hash text,p_kind text,p_run text,p_hash text,p_items jsonb,p_rejected jsonb,p_received int,p_dry boolean)
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
      if old_poi.id is null then
        begin
          insert into sr_radar_pois select(p).* returning id into target;
        exception when unique_violation then
          -- A global cross-source dedupe trigger may reject a single POI.
          -- Keep the remaining run rows and its idempotent audit intact.
          outcome:='rejected'; code:='cross_source_duplicate';target:=null;
        end;
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
