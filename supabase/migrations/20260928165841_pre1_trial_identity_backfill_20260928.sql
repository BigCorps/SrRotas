-- JÁ APLICADA EM PRODUÇÃO VIA MCP EM 28/09/2026.
-- Hotfix 1.0-B: identidades vinculadas depois de um trial antigo herdam
-- o trial já consumido para evitar novo trial por troca de e-mail.

create or replace function public.sr_claim_device_identity_v1(
  p_driver_id uuid,
  p_device_id uuid,
  p_identity_raw text,
  p_device_name text default null,
  p_identity_version text default 'android_ssaid_v1',
  p_app_version_name text default null,
  p_app_version_code integer default null
)
returns jsonb
language plpgsql
security definer
set search_path = pg_catalog, public, private, extensions
as $$
declare
  v_key text;
  v_cfg public.sr_access_config%rowtype;
  v_device public.driver_devices%rowtype;
  v_existing public.driver_devices%rowtype;
  v_trial public.driver_trials%rowtype;
  v_active_count integer := 0;
  v_name text;
  v_version text := left(coalesce(nullif(btrim(p_identity_version),''),'android_ssaid_v1'),60);
begin
  perform pg_advisory_xact_lock(hashtextextended(p_driver_id::text, 104729));
  select * into v_cfg from public.sr_access_config where singleton=true;

  select * into v_device
  from public.driver_devices
  where id=p_device_id and driver_id=p_driver_id
  for update;

  if not found then return jsonb_build_object('ok',false,'error','device_not_found'); end if;
  if v_device.revoked then return jsonb_build_object('ok',false,'error','device_revoked','device_id',p_device_id); end if;

  v_key := private.sr_device_identity_key_v1(p_identity_raw);
  v_name := left(coalesce(nullif(btrim(p_device_name),''),v_device.name,'Android'),120);

  if v_device.identity_key is not null and v_device.identity_key <> v_key then
    return jsonb_build_object('ok',false,'error','device_identity_mismatch','device_id',p_device_id);
  end if;

  select * into v_existing
  from public.driver_devices
  where driver_id=p_driver_id and identity_key=v_key and id<>p_device_id
  order by created_at desc limit 1 for update;

  if found then
    if v_existing.revoked then
      return jsonb_build_object('ok',false,'error','device_revoked','device_id',v_existing.id);
    end if;
    update public.driver_devices
    set identity_key=null,identity_version=null,identity_bound_at=null
    where id=v_existing.id;
  end if;

  select count(*)::integer into v_active_count
  from public.driver_devices
  where driver_id=p_driver_id and not revoked
    and identity_key is not null and id<>p_device_id;

  if v_active_count >= v_cfg.max_active_devices then
    return jsonb_build_object(
      'ok',false,'error','device_limit_reached',
      'active_count',v_active_count,'max_active_devices',v_cfg.max_active_devices
    );
  end if;

  update public.driver_devices
  set identity_key=v_key,
      identity_version=v_version,
      identity_bound_at=coalesce(identity_bound_at,now()),
      last_seen_at=now(),
      name=v_name,
      app_version_name=left(nullif(btrim(coalesce(p_app_version_name,'')),''),80),
      app_version_code=p_app_version_code
  where id=p_device_id;

  insert into public.device_identity_registry(
    identity_key,identity_version,last_driver_id,last_device_name,last_seen_at,updated_at
  ) values (
    v_key,v_version,p_driver_id,v_name,now(),now()
  )
  on conflict(identity_key) do update set
    identity_version=excluded.identity_version,
    last_driver_id=excluded.last_driver_id,
    last_device_name=excluded.last_device_name,
    last_seen_at=now(),
    updated_at=now();

  select * into v_trial
  from public.driver_trials
  where driver_id=p_driver_id;

  if found then
    update public.device_identity_registry
    set trial_redeemed_at=coalesce(trial_redeemed_at,v_trial.trial_started_at),
        trial_expires_at=coalesce(
          trial_expires_at,
          v_trial.trial_started_at + make_interval(days => v_cfg.trial_device_cooldown_days)
        ),
        trial_driver_id=coalesce(trial_driver_id,p_driver_id),
        updated_at=now()
    where identity_key=v_key;
  end if;

  return jsonb_build_object(
    'ok',true,'device_id',p_device_id,'identity_bound',true,
    'trial_backfilled',v_trial.driver_id is not null,
    'active_count',v_active_count+1,'max_active_devices',v_cfg.max_active_devices
  );
end;
$$;

revoke all on function public.sr_claim_device_identity_v1(uuid,uuid,text,text,text,text,integer)
  from public, anon, authenticated;
grant execute on function public.sr_claim_device_identity_v1(uuid,uuid,text,text,text,text,integer)
  to service_role;

update public.device_identity_registry r
set trial_redeemed_at=coalesce(r.trial_redeemed_at,t.trial_started_at),
    trial_expires_at=coalesce(
      r.trial_expires_at,
      t.trial_started_at + make_interval(days => cfg.trial_device_cooldown_days)
    ),
    trial_driver_id=coalesce(r.trial_driver_id,t.driver_id),
    updated_at=now()
from public.driver_devices d
join public.driver_trials t on t.driver_id=d.driver_id
cross join public.sr_access_config cfg
where d.identity_key=r.identity_key
  and cfg.singleton=true
  and d.identity_key is not null;
