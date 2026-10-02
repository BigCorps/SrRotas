-- Gate 4 — Copiloto gratuito + Inteligência R$ 9,90 / 30 dias.
-- Mantém plan_id core_monthly por compatibilidade com assinaturas e Pix existentes.
-- Mantém enforcement_mode em observe neste deploy; promoção para enforce é um gate separado.

begin;

update public.sr_access_config
set trial_ai_credits=0,
    updated_at=now()
where singleton=true;

-- core_app/history deixam de ser entitlements pagos. Histórico antigo é preservado,
-- mas o acesso básico passa a ser garantido diretamente pelo Access Resolver.
update public.entitlements
set active=false,
    source_subscription_id=null,
    valid_until=null,
    updated_at=now()
where entitlement in ('core_app','history');

create or replace function public.resolve_driver_access(
  p_driver_id uuid,
  p_device_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path=pg_catalog,public
as $$
declare
  v_cfg public.sr_access_config%rowtype;
  v_driver public.drivers%rowtype;
  v_device public.driver_devices%rowtype;
  v_trial public.driver_trials%rowtype;
  v_identity public.device_identity_registry%rowtype;
  v_subscription public.subscriptions%rowtype;
  v_state text;
  v_reason text:=null;
  v_premium boolean:=false;
  v_effective_premium boolean:=false;
  v_active_identity_devices integer:=0;
  v_identity_bound boolean:=false;
begin
  select * into v_cfg from public.sr_access_config where singleton=true;
  select * into v_driver from public.drivers where id=p_driver_id;

  if not found then
    return jsonb_build_object(
      'state','BLOCKED','reason','driver_not_found',
      'enforcement_mode',coalesce(v_cfg.enforcement_mode,'observe'),
      'policy',jsonb_build_object(
        'can_operate',false,'can_history',false,'can_analytics',false,
        'can_ai',false,'can_mcp',false,'can_billing',true,'can_profile',true
      ),
      'effective',jsonb_build_object(
        'can_operate',false,'can_history',false,'can_analytics',false,
        'can_ai',false,'can_mcp',false,'can_billing',true,'can_profile',true
      )
    );
  end if;

  if p_device_id is not null then
    select * into v_device
    from public.driver_devices
    where id=p_device_id and driver_id=p_driver_id;

    if not found then
      return jsonb_build_object(
        'state','BLOCKED','reason','device_not_found',
        'enforcement_mode',v_cfg.enforcement_mode,
        'policy',jsonb_build_object(
          'can_operate',false,'can_history',false,'can_analytics',false,
          'can_ai',false,'can_mcp',false,'can_billing',true,'can_profile',true
        ),
        'effective',jsonb_build_object(
          'can_operate',false,'can_history',false,'can_analytics',false,
          'can_ai',false,'can_mcp',false,'can_billing',true,'can_profile',true
        )
      );
    end if;

    if v_device.revoked then
      return jsonb_build_object(
        'state','BLOCKED','reason','device_revoked',
        'enforcement_mode',v_cfg.enforcement_mode,
        'device_id',p_device_id,
        'policy',jsonb_build_object(
          'can_operate',false,'can_history',false,'can_analytics',false,
          'can_ai',false,'can_mcp',false,'can_billing',true,'can_profile',true
        ),
        'effective',jsonb_build_object(
          'can_operate',false,'can_history',false,'can_analytics',false,
          'can_ai',false,'can_mcp',false,'can_billing',true,'can_profile',true
        )
      );
    end if;

    v_identity_bound:=v_device.identity_key is not null;
    if v_identity_bound then
      select * into v_identity
      from public.device_identity_registry
      where identity_key=v_device.identity_key;
    end if;
  end if;

  select count(*)::integer into v_active_identity_devices
  from public.driver_devices
  where driver_id=p_driver_id
    and not revoked
    and identity_key is not null;

  if v_driver.access_blocked then
    v_state:='BLOCKED';
    v_reason:=coalesce(nullif(v_driver.access_blocked_reason,''),'driver_blocked');
  else
    select * into v_subscription
    from public.subscriptions
    where driver_id=p_driver_id
      and plan_id='core_monthly'
      and status='active'
      and current_period_end is not null
      and current_period_end>now()
    limit 1;

    if found then
      v_state:='PAID_ACTIVE';
    else
      select * into v_trial
      from public.driver_trials
      where driver_id=p_driver_id;

      if found then
        if now()<v_trial.trial_ends_at then
          v_state:='TRIAL_ACTIVE';
        else
          v_state:='EXPIRED_READ_ONLY';
          v_reason:='intelligence_trial_expired';
        end if;
      elsif v_identity.identity_key is not null
        and v_identity.trial_redeemed_at is not null
        and coalesce(v_identity.trial_expires_at,'infinity'::timestamptz)>now()
        and v_identity.trial_driver_id is distinct from p_driver_id then
        v_state:='EXPIRED_READ_ONLY';
        v_reason:='intelligence_trial_already_used';
      else
        v_state:='TRIAL_PENDING';
        if p_device_id is not null
           and v_cfg.require_device_identity
           and not v_identity_bound then
          v_reason:='intelligence_trial_waiting_device_identity';
        end if;
      end if;
    end if;
  end if;

  v_premium:=v_state in ('TRIAL_ACTIVE','PAID_ACTIVE');

  if v_state='BLOCKED' then
    v_effective_premium:=false;
  elsif v_cfg.enforcement_mode='observe' then
    v_effective_premium:=true;
  else
    v_effective_premium:=v_premium;
  end if;

  return jsonb_build_object(
    'state',v_state,
    'reason',v_reason,
    'commercial_tier',case when v_premium then 'intelligence' else 'copilot' end,
    'trial_eligible',v_state='TRIAL_PENDING',
    'enforcement_mode',v_cfg.enforcement_mode,
    'require_device_identity',v_cfg.require_device_identity,
    'device_identity_bound',v_identity_bound,
    'active_identity_devices',v_active_identity_devices,
    'max_active_devices',v_cfg.max_active_devices,
    'policy',jsonb_build_object(
      'can_operate',v_state<>'BLOCKED',
      'can_history',v_state<>'BLOCKED',
      'can_analytics',v_premium,
      'can_ai',v_premium,
      'can_mcp',v_premium,
      'can_billing',true,
      'can_profile',true
    ),
    'effective',jsonb_build_object(
      'can_operate',v_state<>'BLOCKED',
      'can_history',v_state<>'BLOCKED',
      'can_analytics',case when v_state='BLOCKED' then false else v_effective_premium end,
      'can_ai',case when v_state='BLOCKED' then false else v_effective_premium end,
      'can_mcp',case when v_state='BLOCKED' then false else v_effective_premium end,
      'can_billing',true,
      'can_profile',true
    )
  );
end;
$$;

revoke all on function public.resolve_driver_access(uuid,uuid)
from public,anon,authenticated;
grant execute on function public.resolve_driver_access(uuid,uuid)
to service_role;

-- O trial agora libera Inteligência; não cria mais créditos.
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

  select * into v_cfg
  from public.sr_access_config
  where singleton=true;

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
  elsif v_cfg.enforcement_mode='enforce'
        and v_cfg.require_device_identity then
    -- Sem identidade, apenas o trial Premium fica pendente.
    -- O Copiloto gratuito continua acessível pelo resolver.
    return new;
  end if;

  insert into public.driver_trials(
    driver_id,first_offer_at,trial_started_at,trial_ends_at,
    ai_credits_granted,created_at,updated_at
  ) values (
    new.driver_id,new.observed_at,new.observed_at,
    new.observed_at+make_interval(days=>v_cfg.trial_days),
    0,now(),now()
  )
  on conflict(driver_id) do nothing
  returning driver_id into inserted_driver;

  if inserted_driver is not null and v_identity_key is not null then
    update public.device_identity_registry
    set trial_redeemed_at=coalesce(trial_redeemed_at,new.observed_at),
        trial_expires_at=coalesce(
          trial_expires_at,
          new.observed_at+make_interval(days=>v_cfg.trial_device_cooldown_days)
        ),
        trial_driver_id=coalesce(trial_driver_id,new.driver_id),
        last_driver_id=new.driver_id,
        last_seen_at=now(),
        updated_at=now()
    where identity_key=v_identity_key;
  end if;

  return new;
end;
$$;

revoke all on function public.sr_start_trial_on_first_offer_v1()
from public,anon,authenticated;
grant execute on function public.sr_start_trial_on_first_offer_v1()
to service_role;

-- Pagamento continua idempotente e usa o mesmo plan_id, mas não concede créditos.
create or replace function public.sr_apply_confirmed_payment(
  p_payment_id uuid,
  p_txid text,
  p_paid_amount_cents integer,
  p_provider_status text,
  p_provider_payload jsonb default '{}'::jsonb,
  p_confirmed_at timestamptz default now()
)
returns jsonb
language plpgsql
security definer
set search_path=pg_catalog,public
as $$
declare
  v_payment public.payments%rowtype;
  v_subscription public.subscriptions%rowtype;
  v_base timestamptz;
  v_legacy_balance integer:=0;
begin
  select * into v_payment
  from public.payments
  where id=p_payment_id
  for update;

  if not found then raise exception 'payment_not_found'; end if;

  if v_payment.status='paid' then
    select * into v_subscription
    from public.subscriptions
    where driver_id=v_payment.driver_id
      and plan_id='core_monthly';

    select coalesce(balance,0) into v_legacy_balance
    from public.credit_wallets
    where driver_id=v_payment.driver_id;

    return jsonb_build_object(
      'success',true,'duplicate',true,'paymentId',v_payment.id,
      'subscriptionId',v_subscription.id,
      'periodEnd',v_subscription.current_period_end,
      'balance',coalesce(v_legacy_balance,0),
      'product','intelligence'
    );
  end if;

  if v_payment.status not in ('pending','manual_review') then
    raise exception 'payment_not_confirmable';
  end if;

  if v_payment.txid is null or v_payment.txid<>p_txid then
    update public.payments
    set status='manual_review',
        bank_status=p_provider_status,
        provider_last_response=coalesce(p_provider_payload,'{}'::jsonb),
        last_checked_at=now(),
        check_attempts=check_attempts+1,
        error_code='txid_mismatch',
        error_message='O txid confirmado não corresponde à cobrança.',
        updated_at=now()
    where id=v_payment.id;

    return jsonb_build_object(
      'success',false,'status','manual_review','reason','txid_mismatch'
    );
  end if;

  if p_paid_amount_cents is null
     or p_paid_amount_cents<>v_payment.amount_cents then
    update public.payments
    set status='manual_review',
        bank_status=p_provider_status,
        provider_last_response=coalesce(p_provider_payload,'{}'::jsonb),
        last_checked_at=now(),
        check_attempts=check_attempts+1,
        error_code='amount_mismatch',
        error_message='O valor recebido não corresponde à cobrança.',
        updated_at=now()
    where id=v_payment.id;

    return jsonb_build_object(
      'success',false,'status','manual_review','reason','amount_mismatch',
      'expectedAmountCents',v_payment.amount_cents,
      'paidAmountCents',p_paid_amount_cents
    );
  end if;

  update public.payments
  set status='paid',
      bank_status=p_provider_status,
      confirmed_at=p_confirmed_at,
      provider_last_response=coalesce(p_provider_payload,'{}'::jsonb),
      last_checked_at=now(),
      check_attempts=check_attempts+1,
      error_code=null,
      error_message=null,
      updated_at=now()
  where id=v_payment.id;

  select * into v_subscription
  from public.subscriptions
  where driver_id=v_payment.driver_id
    and plan_id='core_monthly'
  for update;

  if not found then
    insert into public.subscriptions(
      driver_id,plan_id,status,starts_at,current_period_end,payment_provider
    ) values (
      v_payment.driver_id,'core_monthly','active',
      p_confirmed_at,p_confirmed_at+interval '30 days','banco_inter'
    )
    returning * into v_subscription;
  else
    v_base:=greatest(
      coalesce(v_subscription.current_period_end,now()),
      now()
    );

    update public.subscriptions
    set status='active',
        starts_at=coalesce(starts_at,p_confirmed_at),
        current_period_end=v_base+interval '30 days',
        canceled_at=null,
        payment_provider='banco_inter',
        updated_at=now()
    where id=v_subscription.id
    returning * into v_subscription;
  end if;

  update public.payments
  set subscription_id=v_subscription.id,
      updated_at=now()
  where id=v_payment.id;

  update public.entitlements
  set active=false,
      source_subscription_id=null,
      valid_until=null,
      updated_at=now()
  where driver_id=v_payment.driver_id
    and entitlement in ('core_app','history');

  insert into public.entitlements(
    driver_id,entitlement,active,source_subscription_id,valid_until
  )
  select
    v_payment.driver_id,
    entitlement_name,
    true,
    v_subscription.id,
    v_subscription.current_period_end
  from unnest(
    array['mcp','ai','advanced_analytics','future_features']
  ) as entitlement_name
  on conflict(driver_id,entitlement) do update
  set active=true,
      source_subscription_id=excluded.source_subscription_id,
      valid_until=excluded.valid_until,
      updated_at=now();

  select coalesce(balance,0) into v_legacy_balance
  from public.credit_wallets
  where driver_id=v_payment.driver_id;

  return jsonb_build_object(
    'success',true,'duplicate',false,'paymentId',v_payment.id,
    'subscriptionId',v_subscription.id,
    'periodEnd',v_subscription.current_period_end,
    'balance',coalesce(v_legacy_balance,0),
    'product','intelligence'
  );
end;
$$;

revoke all on function public.sr_apply_confirmed_payment(
  uuid,text,integer,text,jsonb,timestamptz
) from public,anon,authenticated;
grant execute on function public.sr_apply_confirmed_payment(
  uuid,text,integer,text,jsonb,timestamptz
) to service_role;

commit;
