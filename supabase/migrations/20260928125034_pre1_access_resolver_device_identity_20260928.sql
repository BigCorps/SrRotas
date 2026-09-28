-- Sr. Rotas 1.0-B — Device Identity + Access Resolver + trial anti-abuse.
-- JÁ APLICADA EM PRODUÇÃO VIA MCP EM 28/09/2026.
-- Rollout inicial em OBSERVE para não bloquear a 0.33.6 legada.

create schema if not exists private;
revoke all on schema private from public, anon, authenticated, service_role;

create table if not exists private.sr_security_secrets (
  name text primary key,
  secret bytea not null,
  created_at timestamptz not null default now()
);
revoke all on private.sr_security_secrets from public, anon, authenticated, service_role;

insert into private.sr_security_secrets(name, secret)
values ('device_identity_hmac_v1', extensions.gen_random_bytes(32))
on conflict (name) do nothing;

alter table public.driver_devices
  add column if not exists identity_key text,
  add column if not exists identity_version text,
  add column if not exists identity_bound_at timestamptz,
  add column if not exists last_token_rotated_at timestamptz,
  add column if not exists app_version_name text,
  add column if not exists app_version_code integer,
  add column if not exists revoked_at timestamptz,
  add column if not exists revoked_reason text;

create unique index if not exists driver_devices_driver_identity_unique
  on public.driver_devices(driver_id, identity_key)
  where identity_key is not null;
create index if not exists driver_devices_identity_idx
  on public.driver_devices(identity_key)
  where identity_key is not null;
create index if not exists driver_devices_driver_active_identity_idx
  on public.driver_devices(driver_id, revoked, identity_key)
  where identity_key is not null;

create table if not exists public.device_identity_registry (
  identity_key text primary key,
  identity_version text not null default 'android_ssaid_v1',
  first_seen_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  last_driver_id uuid references public.drivers(id) on delete set null,
  last_device_name text,
  trial_redeemed_at timestamptz,
  trial_expires_at timestamptz,
  trial_driver_id uuid references public.drivers(id) on delete set null,
  updated_at timestamptz not null default now()
);
alter table public.device_identity_registry enable row level security;
revoke all on public.device_identity_registry from public, anon, authenticated;
grant select, insert, update, delete on public.device_identity_registry to service_role;

create table if not exists public.sr_access_config (
  singleton boolean primary key default true check (singleton),
  enforcement_mode text not null default 'observe' check (enforcement_mode in ('observe','enforce')),
  max_active_devices integer not null default 2 check (max_active_devices between 1 and 10),
  require_device_identity boolean not null default false,
  trial_days integer not null default 7 check (trial_days between 1 and 30),
  trial_ai_credits integer not null default 5 check (trial_ai_credits between 0 and 100),
  trial_device_cooldown_days integer not null default 365 check (trial_device_cooldown_days between 30 and 3650),
  updated_at timestamptz not null default now()
);
insert into public.sr_access_config(singleton) values (true) on conflict (singleton) do nothing;
alter table public.sr_access_config enable row level security;
revoke all on public.sr_access_config from public, anon, authenticated;
grant select, insert, update on public.sr_access_config to service_role;

alter table public.drivers
  add column if not exists access_blocked boolean not null default false,
  add column if not exists access_blocked_at timestamptz,
  add column if not exists access_blocked_reason text;

create or replace function private.sr_device_identity_key_v1(p_identity_raw text)
returns text language plpgsql security definer
set search_path = pg_catalog, private, extensions
as $$
declare v_secret bytea; v_input text := btrim(coalesce(p_identity_raw,''));
begin
  if length(v_input) < 8 or length(v_input) > 256 then raise exception 'invalid_device_identity'; end if;
  select secret into v_secret from private.sr_security_secrets where name='device_identity_hmac_v1';
  if v_secret is null then raise exception 'device_identity_secret_missing'; end if;
  return encode(extensions.hmac(convert_to(v_input,'UTF8'),v_secret,'sha256'),'hex');
end; $$;
revoke all on function private.sr_device_identity_key_v1(text) from public, anon, authenticated, service_role;

create or replace function public.sr_bind_device_identity_v1(
  p_driver_id uuid, p_identity_raw text, p_device_name text, p_token_hash text,
  p_identity_version text default 'android_ssaid_v1', p_app_version_name text default null,
  p_app_version_code integer default null
) returns jsonb language plpgsql security definer
set search_path = pg_catalog, public, private, extensions
as $$
declare
  v_key text; v_cfg public.sr_access_config%rowtype; v_device public.driver_devices%rowtype;
  v_active_count integer := 0; v_name text := left(coalesce(nullif(btrim(p_device_name),''),'Android'),120);
  v_version text := left(coalesce(nullif(btrim(p_identity_version),''),'android_ssaid_v1'),60);
begin
  if p_driver_id is null or not exists(select 1 from public.drivers where id=p_driver_id) then return jsonb_build_object('ok',false,'error','driver_not_found'); end if;
  if p_token_hash is null or length(btrim(p_token_hash)) < 32 then return jsonb_build_object('ok',false,'error','invalid_token_hash'); end if;
  perform pg_advisory_xact_lock(hashtextextended(p_driver_id::text,104729));
  select * into v_cfg from public.sr_access_config where singleton=true;
  v_key := private.sr_device_identity_key_v1(p_identity_raw);
  insert into public.device_identity_registry(identity_key,identity_version,last_driver_id,last_device_name,last_seen_at,updated_at)
  values(v_key,v_version,p_driver_id,v_name,now(),now())
  on conflict(identity_key) do update set identity_version=excluded.identity_version,last_driver_id=excluded.last_driver_id,last_device_name=excluded.last_device_name,last_seen_at=now(),updated_at=now();
  select * into v_device from public.driver_devices where driver_id=p_driver_id and identity_key=v_key for update;
  if found then
    if v_device.revoked then return jsonb_build_object('ok',false,'error','device_revoked','device_id',v_device.id,'max_active_devices',v_cfg.max_active_devices); end if;
    update public.driver_devices set name=v_name,token_hash=btrim(p_token_hash),identity_version=v_version,identity_bound_at=coalesce(identity_bound_at,now()),last_token_rotated_at=now(),last_seen_at=now(),app_version_name=left(nullif(btrim(coalesce(p_app_version_name,'')),''),80),app_version_code=p_app_version_code where id=v_device.id;
    select count(*)::integer into v_active_count from public.driver_devices where driver_id=p_driver_id and not revoked and identity_key is not null;
    return jsonb_build_object('ok',true,'device_id',v_device.id,'reused',true,'identity_bound',true,'active_count',v_active_count,'max_active_devices',v_cfg.max_active_devices);
  end if;
  select count(*)::integer into v_active_count from public.driver_devices where driver_id=p_driver_id and not revoked and identity_key is not null;
  if v_active_count >= v_cfg.max_active_devices then return jsonb_build_object('ok',false,'error','device_limit_reached','active_count',v_active_count,'max_active_devices',v_cfg.max_active_devices); end if;
  insert into public.driver_devices(driver_id,name,token_hash,revoked,last_seen_at,identity_key,identity_version,identity_bound_at,last_token_rotated_at,app_version_name,app_version_code)
  values(p_driver_id,v_name,btrim(p_token_hash),false,now(),v_key,v_version,now(),now(),left(nullif(btrim(coalesce(p_app_version_name,'')),''),80),p_app_version_code) returning * into v_device;
  return jsonb_build_object('ok',true,'device_id',v_device.id,'reused',false,'identity_bound',true,'active_count',v_active_count+1,'max_active_devices',v_cfg.max_active_devices);
end; $$;
revoke all on function public.sr_bind_device_identity_v1(uuid,text,text,text,text,text,integer) from public,anon,authenticated;
grant execute on function public.sr_bind_device_identity_v1(uuid,text,text,text,text,text,integer) to service_role;

create or replace function public.resolve_driver_access(p_driver_id uuid,p_device_id uuid default null)
returns jsonb language plpgsql security definer set search_path=pg_catalog,public
as $$
declare
 v_cfg public.sr_access_config%rowtype; v_driver public.drivers%rowtype; v_device public.driver_devices%rowtype; v_trial public.driver_trials%rowtype; v_identity public.device_identity_registry%rowtype; v_subscription public.subscriptions%rowtype;
 v_state text; v_reason text:=null; v_policy_operate boolean:=false; v_policy_read boolean:=false; v_policy_ai boolean:=false; v_policy_mcp boolean:=false; v_effective_operate boolean:=false; v_effective_read boolean:=false; v_effective_ai boolean:=false; v_effective_mcp boolean:=false; v_active_identity_devices integer:=0; v_identity_bound boolean:=false;
begin
 select * into v_cfg from public.sr_access_config where singleton=true;
 select * into v_driver from public.drivers where id=p_driver_id;
 if not found then return jsonb_build_object('state','BLOCKED','reason','driver_not_found','enforcement_mode',v_cfg.enforcement_mode); end if;
 if p_device_id is not null then
  select * into v_device from public.driver_devices where id=p_device_id and driver_id=p_driver_id;
  if not found then return jsonb_build_object('state','BLOCKED','reason','device_not_found','enforcement_mode',v_cfg.enforcement_mode); end if;
  if v_device.revoked then return jsonb_build_object('state','BLOCKED','reason','device_revoked','enforcement_mode',v_cfg.enforcement_mode,'device_id',p_device_id,'can_operate',false,'can_history',false,'can_analytics',false,'can_ai',false,'can_mcp',false,'can_billing',true,'can_profile',true); end if;
  v_identity_bound:=v_device.identity_key is not null;
  if v_device.identity_key is not null then select * into v_identity from public.device_identity_registry where identity_key=v_device.identity_key; end if;
 end if;
 select count(*)::integer into v_active_identity_devices from public.driver_devices where driver_id=p_driver_id and not revoked and identity_key is not null;
 if v_driver.access_blocked then v_state:='BLOCKED';v_reason:=coalesce(nullif(v_driver.access_blocked_reason,''),'driver_blocked');
 else
  select * into v_subscription from public.subscriptions where driver_id=p_driver_id and plan_id='core_monthly' and status='active' and current_period_end is not null and current_period_end>now() limit 1;
  if found then v_state:='PAID_ACTIVE'; else
   select * into v_trial from public.driver_trials where driver_id=p_driver_id;
   if found then if now()<v_trial.trial_ends_at then v_state:='TRIAL_ACTIVE'; else v_state:='EXPIRED_READ_ONLY';v_reason:='trial_expired'; end if;
   else
    if p_device_id is not null and v_cfg.require_device_identity and not v_identity_bound then v_state:='BLOCKED';v_reason:='device_identity_required';
    elsif v_identity.identity_key is not null and v_identity.trial_redeemed_at is not null and coalesce(v_identity.trial_expires_at,'infinity'::timestamptz)>now() and v_identity.trial_driver_id is distinct from p_driver_id then v_state:='EXPIRED_READ_ONLY';v_reason:='device_trial_already_used';
    else v_state:='TRIAL_PENDING'; end if;
   end if;
  end if;
 end if;
 v_policy_operate:=v_state in ('TRIAL_PENDING','TRIAL_ACTIVE','PAID_ACTIVE'); v_policy_read:=v_state in ('TRIAL_PENDING','TRIAL_ACTIVE','PAID_ACTIVE','EXPIRED_READ_ONLY'); v_policy_ai:=v_state in ('TRIAL_ACTIVE','PAID_ACTIVE'); v_policy_mcp:=v_state in ('TRIAL_ACTIVE','PAID_ACTIVE');
 if v_state='BLOCKED' then v_effective_operate:=false;v_effective_read:=false;v_effective_ai:=false;v_effective_mcp:=false;
 elsif v_cfg.enforcement_mode='observe' then v_effective_operate:=true;v_effective_read:=true;v_effective_ai:=true;v_effective_mcp:=true;
 else v_effective_operate:=v_policy_operate;v_effective_read:=v_policy_read;v_effective_ai:=v_policy_ai;v_effective_mcp:=v_policy_mcp; end if;
 return jsonb_build_object('state',v_state,'reason',v_reason,'enforcement_mode',v_cfg.enforcement_mode,'require_device_identity',v_cfg.require_device_identity,'device_identity_bound',v_identity_bound,'active_identity_devices',v_active_identity_devices,'max_active_devices',v_cfg.max_active_devices,'policy',jsonb_build_object('can_operate',v_policy_operate,'can_history',v_policy_read,'can_analytics',v_policy_read,'can_ai',v_policy_ai,'can_mcp',v_policy_mcp,'can_billing',true,'can_profile',true),'effective',jsonb_build_object('can_operate',v_effective_operate,'can_history',v_effective_read,'can_analytics',v_effective_read,'can_ai',v_effective_ai,'can_mcp',v_effective_mcp,'can_billing',true,'can_profile',true));
end; $$;
revoke all on function public.resolve_driver_access(uuid,uuid) from public,anon,authenticated;
grant execute on function public.resolve_driver_access(uuid,uuid) to service_role;

create or replace function public.sr_start_trial_on_first_offer_v1()
returns trigger language plpgsql security definer set search_path=pg_catalog,public,pg_temp
as $$
declare inserted_driver uuid; v_identity_key text; v_registry public.device_identity_registry%rowtype; v_cfg public.sr_access_config%rowtype;
begin
 if new.capture_method like 'historical-import/%' then return new; end if;
 if exists(select 1 from public.driver_trials where driver_id=new.driver_id) then return new; end if;
 select * into v_cfg from public.sr_access_config where singleton=true;
 select identity_key into v_identity_key from public.driver_devices where id=new.device_id and driver_id=new.driver_id;
 if v_identity_key is not null then
  insert into public.device_identity_registry(identity_key,identity_version,last_driver_id,last_seen_at,updated_at)
  select v_identity_key,coalesce(nullif(identity_version,''),'android_ssaid_v1'),new.driver_id,now(),now() from public.driver_devices where id=new.device_id
  on conflict(identity_key) do update set last_driver_id=excluded.last_driver_id,last_seen_at=now(),updated_at=now();
  select * into v_registry from public.device_identity_registry where identity_key=v_identity_key for update;
  if v_registry.trial_redeemed_at is not null and coalesce(v_registry.trial_expires_at,'infinity'::timestamptz)>now() and v_registry.trial_driver_id is distinct from new.driver_id then return new; end if;
 elsif v_cfg.enforcement_mode='enforce' and v_cfg.require_device_identity then return new; end if;
 insert into public.driver_trials(driver_id,first_offer_at,trial_started_at,trial_ends_at,ai_credits_granted,created_at,updated_at)
 values(new.driver_id,new.observed_at,new.observed_at,new.observed_at+make_interval(days=>v_cfg.trial_days),v_cfg.trial_ai_credits,now(),now())
 on conflict(driver_id) do nothing returning driver_id into inserted_driver;
 if inserted_driver is not null then
  if v_identity_key is not null then update public.device_identity_registry set trial_redeemed_at=coalesce(trial_redeemed_at,new.observed_at),trial_expires_at=coalesce(trial_expires_at,new.observed_at+make_interval(days=>v_cfg.trial_device_cooldown_days)),trial_driver_id=coalesce(trial_driver_id,new.driver_id),last_driver_id=new.driver_id,last_seen_at=now(),updated_at=now() where identity_key=v_identity_key; end if;
  insert into public.credit_wallets(driver_id,balance,lifetime_granted,lifetime_spent,updated_at) values(new.driver_id,v_cfg.trial_ai_credits,v_cfg.trial_ai_credits,0,now()) on conflict(driver_id) do update set balance=public.credit_wallets.balance+v_cfg.trial_ai_credits,lifetime_granted=public.credit_wallets.lifetime_granted+v_cfg.trial_ai_credits,updated_at=now();
  insert into public.credit_transactions(driver_id,type,amount,reference_id,idempotency_key,metadata,created_at) values(new.driver_id,'welcome',v_cfg.trial_ai_credits,'trial_7d','trial-first-offer-v1:'||new.driver_id::text,jsonb_build_object('source','first_live_offer','trial_days',v_cfg.trial_days,'temporary_trial_credits',v_cfg.trial_ai_credits,'device_identity_bound',v_identity_key is not null),now()) on conflict(idempotency_key) do nothing;
 end if;
 return new;
end; $$;
revoke all on function public.sr_start_trial_on_first_offer_v1() from public,anon,authenticated;
grant execute on function public.sr_start_trial_on_first_offer_v1() to service_role;
