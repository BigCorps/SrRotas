-- JÁ APLICADA EM PRODUÇÃO VIA MCP EM 28/09/2026.
-- 1.0-C — separa trial temporário dos 20 créditos da primeira ativação paga
-- e torna reserve/consume/refund idempotentes sob concorrência.

alter table public.credit_transactions
  drop constraint if exists credit_transactions_type_check;

alter table public.credit_transactions
  add constraint credit_transactions_type_check
  check (
    type = any(array[
      'welcome'::text,
      'trial_grant'::text,
      'trial_expire'::text,
      'purchase'::text,
      'reserve'::text,
      'consume'::text,
      'refund'::text,
      'adjustment'::text
    ])
  );

drop index if exists public.credit_transactions_one_welcome_per_driver;

update public.credit_transactions
set type='trial_grant',
    metadata=coalesce(metadata,'{}'::jsonb) ||
      jsonb_build_object('ledger_migrated_1_0c',true)
where type='welcome'
  and idempotency_key like 'trial-first-offer-v1:%';

create unique index if not exists credit_transactions_one_welcome_per_driver
  on public.credit_transactions(driver_id)
  where type='welcome';

create or replace function public.sr_start_trial_on_first_offer_v1()
returns trigger
language plpgsql
security definer
set search_path=pg_catalog,public,pg_temp
as $$
declare
  inserted_driver uuid;
  v_identity_key text;
  v_registry public.device_identity_registry%rowtype;
  v_cfg public.sr_access_config%rowtype;
begin
  if new.capture_method like 'historical-import/%' then return new; end if;
  if exists(select 1 from public.driver_trials where driver_id=new.driver_id) then
    return new;
  end if;

  select * into v_cfg from public.sr_access_config where singleton=true;

  select identity_key into v_identity_key
  from public.driver_devices
  where id=new.device_id and driver_id=new.driver_id;

  if v_identity_key is not null then
    insert into public.device_identity_registry(
      identity_key,identity_version,last_driver_id,last_seen_at,updated_at
    )
    select
      v_identity_key,
      coalesce(nullif(identity_version,''),'android_ssaid_v1'),
      new.driver_id,
      now(),
      now()
    from public.driver_devices
    where id=new.device_id
    on conflict(identity_key) do update set
      last_driver_id=excluded.last_driver_id,
      last_seen_at=now(),
      updated_at=now();

    select * into v_registry
    from public.device_identity_registry
    where identity_key=v_identity_key
    for update;

    if v_registry.trial_redeemed_at is not null
       and coalesce(v_registry.trial_expires_at,'infinity'::timestamptz)>now()
       and v_registry.trial_driver_id is distinct from new.driver_id then
      return new;
    end if;
  elsif v_cfg.enforcement_mode='enforce' and v_cfg.require_device_identity then
    return new;
  end if;

  insert into public.driver_trials(
    driver_id,first_offer_at,trial_started_at,trial_ends_at,
    ai_credits_granted,created_at,updated_at
  ) values (
    new.driver_id,new.observed_at,new.observed_at,
    new.observed_at + make_interval(days=>v_cfg.trial_days),
    v_cfg.trial_ai_credits,now(),now()
  )
  on conflict(driver_id) do nothing
  returning driver_id into inserted_driver;

  if inserted_driver is not null then
    if v_identity_key is not null then
      update public.device_identity_registry
      set trial_redeemed_at=coalesce(trial_redeemed_at,new.observed_at),
          trial_expires_at=coalesce(
            trial_expires_at,
            new.observed_at + make_interval(days=>v_cfg.trial_device_cooldown_days)
          ),
          trial_driver_id=coalesce(trial_driver_id,new.driver_id),
          last_driver_id=new.driver_id,
          last_seen_at=now(),
          updated_at=now()
      where identity_key=v_identity_key;
    end if;

    insert into public.credit_wallets(
      driver_id,balance,lifetime_granted,lifetime_spent,updated_at
    )
    values(
      new.driver_id,v_cfg.trial_ai_credits,v_cfg.trial_ai_credits,0,now()
    )
    on conflict(driver_id) do update set
      balance=public.credit_wallets.balance+v_cfg.trial_ai_credits,
      lifetime_granted=public.credit_wallets.lifetime_granted+v_cfg.trial_ai_credits,
      updated_at=now();

    insert into public.credit_transactions(
      driver_id,type,amount,reference_id,idempotency_key,metadata,created_at
    ) values (
      new.driver_id,'trial_grant',v_cfg.trial_ai_credits,'trial_7d',
      'trial-first-offer-v1:'||new.driver_id::text,
      jsonb_build_object(
        'source','first_live_offer',
        'trial_days',v_cfg.trial_days,
        'temporary_trial_credits',v_cfg.trial_ai_credits,
        'device_identity_bound',v_identity_key is not null
      ),
      now()
    )
    on conflict(idempotency_key) do nothing;
  end if;

  return new;
end;
$$;

revoke all on function public.sr_start_trial_on_first_offer_v1()
  from public,anon,authenticated;
grant execute on function public.sr_start_trial_on_first_offer_v1()
  to service_role;

create or replace function public.sr_grant_welcome_credits(
  p_driver_id uuid,
  p_payment_id uuid
)
returns public.credit_wallets
language plpgsql
security definer
set search_path=pg_catalog,public
as $$
declare
  v_inserted integer:=0;
  v_expire_inserted integer:=0;
  v_result public.credit_wallets;
  v_balance integer:=0;
  v_trial_granted integer:=0;
  v_trial_remaining integer:=0;
begin
  perform public.sr_ensure_credit_wallet(p_driver_id);

  select balance into v_balance
  from public.credit_wallets
  where driver_id=p_driver_id
  for update;

  insert into public.credit_transactions(
    driver_id,type,amount,reference_id,idempotency_key,metadata
  )
  values(
    p_driver_id,'welcome',20,p_payment_id::text,
    'welcome:'||p_driver_id::text,
    jsonb_build_object(
      'payment_id',p_payment_id,
      'source','first_paid_activation',
      'paid_welcome_credits',20
    )
  )
  on conflict(idempotency_key) do nothing;

  get diagnostics v_inserted=row_count;

  if v_inserted=1 then
    select coalesce(ai_credits_granted,0)
      into v_trial_granted
    from public.driver_trials
    where driver_id=p_driver_id;

    v_trial_remaining:=least(
      greatest(coalesce(v_balance,0),0),
      greatest(coalesce(v_trial_granted,0),0)
    );

    if v_trial_remaining>0 then
      insert into public.credit_transactions(
        driver_id,type,amount,reference_id,idempotency_key,metadata
      )
      values(
        p_driver_id,'trial_expire',-v_trial_remaining,p_payment_id::text,
        'trial-expire-on-paid:'||p_driver_id::text,
        jsonb_build_object(
          'payment_id',p_payment_id,
          'reason','temporary_trial_wallet_closed_on_first_paid_activation'
        )
      )
      on conflict(idempotency_key) do nothing;

      get diagnostics v_expire_inserted=row_count;

      if v_expire_inserted=1 then
        update public.credit_wallets
        set balance=greatest(0,balance-v_trial_remaining),
            updated_at=now()
        where driver_id=p_driver_id;
      end if;
    end if;

    update public.credit_wallets
    set balance=balance+20,
        lifetime_granted=lifetime_granted+20,
        updated_at=now()
    where driver_id=p_driver_id;
  end if;

  select * into v_result
  from public.credit_wallets
  where driver_id=p_driver_id;
  return v_result;
end;
$$;

revoke all on function public.sr_grant_welcome_credits(uuid,uuid)
  from public,anon,authenticated;
grant execute on function public.sr_grant_welcome_credits(uuid,uuid)
  to service_role;

create or replace function public.sr_reserve_ai_credit(
  p_driver_id uuid,p_reference_id text
)
returns boolean
language plpgsql
security definer
set search_path=pg_catalog,public
as $$
declare v_balance integer; v_inserted integer:=0;
begin
  perform public.sr_ensure_credit_wallet(p_driver_id);
  select balance into v_balance
  from public.credit_wallets
  where driver_id=p_driver_id
  for update;

  if exists(
    select 1 from public.credit_transactions
    where driver_id=p_driver_id and type='reserve'
      and reference_id=p_reference_id
  ) then return true; end if;

  if coalesce(v_balance,0)<1 then return false; end if;

  insert into public.credit_transactions(
    driver_id,type,amount,reference_id,idempotency_key
  )
  values(
    p_driver_id,'reserve',-1,p_reference_id,
    'ai:reserve:'||p_driver_id::text||':'||p_reference_id
  )
  on conflict(idempotency_key) do nothing;

  get diagnostics v_inserted=row_count;
  if v_inserted=1 then
    update public.credit_wallets
    set balance=balance-1,updated_at=now()
    where driver_id=p_driver_id;
  end if;
  return true;
end;
$$;

create or replace function public.sr_consume_ai_credit(
  p_driver_id uuid,p_reference_id text
)
returns boolean
language plpgsql
security definer
set search_path=pg_catalog,public
as $$
declare v_inserted integer:=0; v_balance integer;
begin
  perform public.sr_ensure_credit_wallet(p_driver_id);
  select balance into v_balance
  from public.credit_wallets
  where driver_id=p_driver_id
  for update;

  if exists(
    select 1 from public.credit_transactions
    where driver_id=p_driver_id and type='consume'
      and reference_id=p_reference_id
  ) then return true; end if;

  if not exists(
    select 1 from public.credit_transactions
    where driver_id=p_driver_id and type='reserve'
      and reference_id=p_reference_id
  ) then return false; end if;

  if exists(
    select 1 from public.credit_transactions
    where driver_id=p_driver_id and type='refund'
      and reference_id=p_reference_id
  ) then return false; end if;

  insert into public.credit_transactions(
    driver_id,type,amount,reference_id,idempotency_key
  )
  values(
    p_driver_id,'consume',0,p_reference_id,
    'ai:consume:'||p_driver_id::text||':'||p_reference_id
  )
  on conflict(idempotency_key) do nothing;

  get diagnostics v_inserted=row_count;
  if v_inserted=1 then
    update public.credit_wallets
    set lifetime_spent=lifetime_spent+1,updated_at=now()
    where driver_id=p_driver_id;
  end if;
  return true;
end;
$$;

create or replace function public.sr_refund_ai_credit(
  p_driver_id uuid,p_reference_id text
)
returns boolean
language plpgsql
security definer
set search_path=pg_catalog,public
as $$
declare v_inserted integer:=0; v_balance integer;
begin
  perform public.sr_ensure_credit_wallet(p_driver_id);
  select balance into v_balance
  from public.credit_wallets
  where driver_id=p_driver_id
  for update;

  if exists(
    select 1 from public.credit_transactions
    where driver_id=p_driver_id and type='consume'
      and reference_id=p_reference_id
  ) then return false; end if;

  if not exists(
    select 1 from public.credit_transactions
    where driver_id=p_driver_id and type='reserve'
      and reference_id=p_reference_id
  ) then return false; end if;

  insert into public.credit_transactions(
    driver_id,type,amount,reference_id,idempotency_key
  )
  values(
    p_driver_id,'refund',1,p_reference_id,
    'ai:refund:'||p_driver_id::text||':'||p_reference_id
  )
  on conflict(idempotency_key) do nothing;

  get diagnostics v_inserted=row_count;
  if v_inserted=1 then
    update public.credit_wallets
    set balance=balance+1,updated_at=now()
    where driver_id=p_driver_id;
  end if;
  return true;
end;
$$;

revoke all on function public.sr_reserve_ai_credit(uuid,text)
  from public,anon,authenticated;
revoke all on function public.sr_consume_ai_credit(uuid,text)
  from public,anon,authenticated;
revoke all on function public.sr_refund_ai_credit(uuid,text)
  from public,anon,authenticated;

grant execute on function public.sr_reserve_ai_credit(uuid,text) to service_role;
grant execute on function public.sr_consume_ai_credit(uuid,text) to service_role;
grant execute on function public.sr_refund_ai_credit(uuid,text) to service_role;
