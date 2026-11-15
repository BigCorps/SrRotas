-- Private quarantine only. No event, POI, source-run, review or import writer.
create schema if not exists private;
create table public.sr_radar_json_uploads (
  upload_id uuid primary key default gen_random_uuid(),
  received_by uuid not null,
  actor_email text not null,
  idempotency_key uuid not null,
  original_name text not null check (length(original_name) between 1 and 120 and original_name !~ '[[:cntrl:]/\\]'),
  size_bytes integer not null check (size_bytes between 1 and 2000000),
  sha256 text not null check (sha256 ~ '^[0-9a-f]{64}$'),
  received_at timestamptz not null default clock_timestamp(),
  retention_review_at timestamptz not null default (clock_timestamp()+interval '30 days'),
  status text not null default 'quarantined' check (status='quarantined'),
  unique(received_by,idempotency_key)
);
create index sr_radar_json_uploads_received on public.sr_radar_json_uploads(received_at desc,upload_id desc);
create table private.sr_radar_json_upload_bytes (
  upload_id uuid primary key references public.sr_radar_json_uploads(upload_id) on delete restrict,
  payload bytea not null check (octet_length(payload) between 1 and 2000000)
);
create table public.sr_radar_json_upload_audit (
  id bigint generated always as identity primary key,
  upload_id uuid not null references public.sr_radar_json_uploads(upload_id) on delete restrict,
  actor_id uuid not null,
  action text not null check (action in ('received','replayed','downloaded','purged')),
  created_at timestamptz not null default clock_timestamp()
);
create index sr_radar_json_upload_audit_upload on public.sr_radar_json_upload_audit(upload_id,created_at);
alter table public.sr_radar_json_uploads enable row level security;
alter table private.sr_radar_json_upload_bytes enable row level security;
alter table public.sr_radar_json_upload_audit enable row level security;
-- Explicitly defeat Supabase default service_role grants. All access is RPC-only.
revoke all on public.sr_radar_json_uploads,private.sr_radar_json_upload_bytes,public.sr_radar_json_upload_audit from public,anon,authenticated,service_role;
revoke all on sequence public.sr_radar_json_upload_audit_id_seq from public,anon,authenticated,service_role;

create function private.sr_radar_json_actor_v1(p_actor_id uuid,p_email text,p_session_hash text)
returns void language plpgsql security definer set search_path='' as $$
begin
  if p_email is null or p_email not in ('contato@bigcorps.com.br','jadielalmeida@gmail.com') or p_actor_id is null then
    raise sqlstate 'PT403' using message='admin_required';
  end if;
  perform 1 from public.historical_import_web_sessions
    where auth_user_id=p_actor_id and email=p_email and token_hash=p_session_hash
      and expires_at>clock_timestamp() for share;
  if not found then raise sqlstate 'PT401' using message='session_expired'; end if;
  if p_email<>'contato@bigcorps.com.br' then
    perform 1 from public.historical_import_access where email=p_email and enabled for share;
    if not found then raise sqlstate 'PT403' using message='admin_required'; end if;
  end if;
end $$;
revoke all on function private.sr_radar_json_actor_v1(uuid,text,text) from public,anon,authenticated,service_role;

create function public.sr_radar_json_upload_v1(p_actor_id uuid,p_email text,p_session_hash text,p_key uuid,p_name text,p_base64 text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_bytes bytea;v_text text;v_hash text;r public.sr_radar_json_uploads%rowtype;v_day timestamptz;
begin
  perform private.sr_radar_json_actor_v1(p_actor_id,p_email,p_session_hash);
  if p_key is null or p_name is null or length(p_name) not between 1 and 120 or p_name ~ '[[:cntrl:]/\\]' then
    raise sqlstate 'PT400' using message='invalid_upload';
  end if;
  if p_base64 is null or length(p_base64)>2666668 then raise sqlstate 'PT413' using message='payload_too_large'; end if;
  begin v_bytes:=decode(p_base64,'base64');v_text:=convert_from(v_bytes,'UTF8');
  exception when others then raise sqlstate 'PT400' using message='invalid_encoding'; end;
  if octet_length(v_bytes) not between 1 and 2000000 then raise sqlstate 'PT413' using message='payload_too_large'; end if;
  -- PostgreSQL validation complements the bounded Node parser, including nested duplicate keys.
  if not (ltrim(v_text,chr(65279)) is json with unique keys) then raise sqlstate 'PT400' using message='invalid_json'; end if;
  v_hash:=encode(sha256(v_bytes),'hex');
  -- One brief global gate serializes quotas and same-key uploads across all operators.
  perform pg_advisory_xact_lock(20261115,25);
  select * into r from public.sr_radar_json_uploads where received_by=p_actor_id and idempotency_key=p_key;
  if found then
    if r.sha256<>v_hash or r.original_name<>p_name or r.size_bytes<>octet_length(v_bytes) then raise sqlstate 'PT409' using message='idempotency_conflict'; end if;
    if not exists(select 1 from private.sr_radar_json_upload_bytes where upload_id=r.upload_id) then raise sqlstate 'PT410' using message='upload_purged'; end if;
    insert into public.sr_radar_json_upload_audit(upload_id,actor_id,action) values(r.upload_id,p_actor_id,'replayed');
    return to_jsonb(r)-'actor_email'-'idempotency_key'-'received_by';
  end if;
  v_day:=date_trunc('day',clock_timestamp() at time zone 'UTC') at time zone 'UTC';
  if (select count(*) from public.sr_radar_json_uploads where received_at>=v_day and received_by=p_actor_id)>=20
    or (select count(*) from public.sr_radar_json_uploads where received_at>=v_day)>=100
    or (select coalesce(sum(octet_length(payload)),0) from private.sr_radar_json_upload_bytes)+octet_length(v_bytes)>1000000000 then
    raise sqlstate 'PT429' using message='upload_quota_reached';
  end if;
  insert into public.sr_radar_json_uploads(received_by,actor_email,idempotency_key,original_name,size_bytes,sha256)
    values(p_actor_id,p_email,p_key,p_name,octet_length(v_bytes),v_hash) returning * into r;
  insert into private.sr_radar_json_upload_bytes values(r.upload_id,v_bytes);
  insert into public.sr_radar_json_upload_audit(upload_id,actor_id,action) values(r.upload_id,p_actor_id,'received');
  return to_jsonb(r)-'actor_email'-'idempotency_key'-'received_by';
end $$;
revoke all on function public.sr_radar_json_upload_v1(uuid,text,text,uuid,text,text) from public,anon,authenticated;
grant execute on function public.sr_radar_json_upload_v1(uuid,text,text,uuid,text,text) to service_role;

create function public.sr_radar_json_list_v1(p_actor_id uuid,p_email text,p_session_hash text,p_cursor uuid default null,p_upload_id uuid default null)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_at timestamptz;v_rows jsonb;
begin
  perform private.sr_radar_json_actor_v1(p_actor_id,p_email,p_session_hash);
  if p_cursor is not null then
    select received_at into v_at from public.sr_radar_json_uploads where upload_id=p_cursor;
    if not found then raise sqlstate 'PT404' using message='upload_not_found'; end if;
  end if;
  select coalesce(jsonb_agg(row_data order by received_at desc,upload_id desc),'[]'::jsonb) into v_rows from (
    select r.received_at,r.upload_id,(to_jsonb(r)-'actor_email'-'idempotency_key'-'received_by')||jsonb_build_object('status',case when exists(select 1 from private.sr_radar_json_upload_bytes b where b.upload_id=r.upload_id) then 'quarantined' else 'purged' end) as row_data
    from public.sr_radar_json_uploads r where (p_upload_id is null or r.upload_id=p_upload_id) and (p_cursor is null or (r.received_at,r.upload_id)<(v_at,p_cursor))
    order by r.received_at desc,r.upload_id desc limit 21
  ) page;
  return v_rows;
end $$;
revoke all on function public.sr_radar_json_list_v1(uuid,text,text,uuid,uuid) from public,anon,authenticated;
grant execute on function public.sr_radar_json_list_v1(uuid,text,text,uuid,uuid) to service_role;

create function public.sr_radar_json_download_v1(p_actor_id uuid,p_email text,p_session_hash text,p_upload_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare r public.sr_radar_json_uploads%rowtype;v_bytes bytea;
begin
  perform private.sr_radar_json_actor_v1(p_actor_id,p_email,p_session_hash);
  select * into r from public.sr_radar_json_uploads where upload_id=p_upload_id;
  if not found then raise sqlstate 'PT404' using message='upload_not_found'; end if;
  select payload into v_bytes from private.sr_radar_json_upload_bytes where upload_id=p_upload_id;
  if not found then raise sqlstate 'PT410' using message='upload_purged'; end if;
  if octet_length(v_bytes)<>r.size_bytes or encode(sha256(v_bytes),'hex')<>r.sha256 then raise sqlstate 'PT409' using message='integrity_mismatch'; end if;
  insert into public.sr_radar_json_upload_audit(upload_id,actor_id,action) values(r.upload_id,p_actor_id,'downloaded');
  return jsonb_build_object('receipt',to_jsonb(r)-'actor_email'-'idempotency_key'-'received_by','base64',encode(v_bytes,'base64'));
end $$;
revoke all on function public.sr_radar_json_download_v1(uuid,text,text,uuid) from public,anon,authenticated;
grant execute on function public.sr_radar_json_download_v1(uuid,text,text,uuid) to service_role;

-- No automatic deletion or promotion. At 30 days, operator reviews retention.
-- Raw bytes can only be purged by a DB owner with separate explicit approval:
-- lock receipt FOR UPDATE; verify retention_review_at<=now(); DELETE only its
-- private.sr_radar_json_upload_bytes row; append action='purged' to this audit;
-- commit atomically. Preserve receipt/hash/audit at least 90 days. Never touch
-- Radar entities or historical_import_* data. No purge endpoint or cron exists.
-- Containment rollback: revoke EXECUTE on the three public RPCs from service_role
-- and revert the web deployment; retain all tables/bytes for controlled recovery.
