begin;

create table if not exists public.subscriptions (
  id uuid primary key default gen_random_uuid(),
  driver_id uuid not null references public.drivers(id) on delete cascade,
  plan_id text not null default 'core_monthly',
  status text not null default 'pending' check (status in ('pending','active','past_due','canceled','expired')),
  starts_at timestamptz,
  current_period_end timestamptz,
  canceled_at timestamptz,
  payment_provider text not null default 'banco_inter',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(driver_id, plan_id)
);

create index if not exists subscriptions_driver_status_idx
  on public.subscriptions(driver_id, status, current_period_end desc);

create table if not exists public.payments (
  id uuid primary key default gen_random_uuid(),
  driver_id uuid not null references public.drivers(id) on delete cascade,
  subscription_id uuid references public.subscriptions(id) on delete set null,
  kind text not null check (kind in ('subscription','credit_pack')),
  provider text not null default 'banco_inter',
  amount_cents integer not null check (amount_cents > 0),
  status text not null default 'pending' check (status in ('pending','paid','expired','failed','refunded','canceled','manual_review')),
  txid text,
  pix_copy_paste text,
  qr_code_payload text,
  expires_at timestamptz,
  confirmed_at timestamptz,
  bank_status text,
  provider_payload jsonb not null default '{}'::jsonb,
  provider_last_response jsonb not null default '{}'::jsonb,
  last_checked_at timestamptz,
  check_attempts integer not null default 0,
  error_code text,
  error_message text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists payments_txid_unique
  on public.payments(txid)
  where txid is not null and btrim(txid) <> '';

create unique index if not exists payments_one_pending_subscription_idx
  on public.payments(driver_id, kind)
  where kind = 'subscription' and status = 'pending';

create index if not exists payments_driver_created_idx
  on public.payments(driver_id, created_at desc);

create index if not exists payments_pending_check_idx
  on public.payments(status, last_checked_at, expires_at)
  where status in ('pending','manual_review');

create table if not exists public.credit_wallets (
  driver_id uuid primary key references public.drivers(id) on delete cascade,
  balance integer not null default 0 check (balance >= 0),
  lifetime_granted integer not null default 0 check (lifetime_granted >= 0),
  lifetime_spent integer not null default 0 check (lifetime_spent >= 0),
  updated_at timestamptz not null default now()
);

create table if not exists public.credit_transactions (
  id uuid primary key default gen_random_uuid(),
  driver_id uuid not null references public.drivers(id) on delete cascade,
  type text not null check (type in ('welcome','purchase','reserve','consume','refund','adjustment')),
  amount integer not null,
  reference_id text,
  idempotency_key text not null unique,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists credit_transactions_driver_created_idx
  on public.credit_transactions(driver_id, created_at desc);

create unique index if not exists credit_transactions_one_welcome_per_driver
  on public.credit_transactions(driver_id)
  where type = 'welcome';

create table if not exists public.entitlements (
  driver_id uuid not null references public.drivers(id) on delete cascade,
  entitlement text not null check (entitlement in ('core_app','history','mcp','ai','advanced_analytics','future_features')),
  active boolean not null default false,
  source_subscription_id uuid references public.subscriptions(id) on delete set null,
  valid_until timestamptz,
  updated_at timestamptz not null default now(),
  primary key(driver_id, entitlement)
);

create table if not exists public.billing_web_sessions (
  id uuid primary key default gen_random_uuid(),
  driver_id uuid not null references public.drivers(id) on delete cascade,
  token_hash text not null unique,
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);

create index if not exists billing_web_sessions_driver_exp_idx
  on public.billing_web_sessions(driver_id, expires_at desc);

alter table public.subscriptions enable row level security;
alter table public.payments enable row level security;
alter table public.credit_wallets enable row level security;
alter table public.credit_transactions enable row level security;
alter table public.entitlements enable row level security;
alter table public.billing_web_sessions enable row level security;

create or replace function public.sr_ensure_credit_wallet(p_driver_id uuid)
returns public.credit_wallets
language plpgsql
security definer
set search_path = public
as $$
declare v_result public.credit_wallets;
begin
  insert into public.credit_wallets(driver_id) values (p_driver_id)
  on conflict (driver_id) do nothing;
  select * into v_result from public.credit_wallets where driver_id = p_driver_id;
  return v_result;
end;
$$;

create or replace function public.sr_create_pix_payment(
  p_driver_id uuid,
  p_expires_at timestamptz
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_payment public.payments%rowtype;
  v_payment_id uuid;
begin
  if p_expires_at <= now() then raise exception 'invalid_pix_expiration'; end if;

  if not exists (select 1 from public.drivers where id = p_driver_id) then
    raise exception 'driver_not_found';
  end if;

  select * into v_payment
  from public.payments
  where driver_id = p_driver_id
    and kind = 'subscription'
    and status = 'pending'
    and expires_at > now()
    and txid is not null
    and pix_copy_paste is not null
  order by created_at desc
  limit 1;

  if found then
    return jsonb_build_object(
      'paymentId', v_payment.id,
      'amountCents', v_payment.amount_cents,
      'expiresAt', v_payment.expires_at,
      'reused', true
    );
  end if;

  update public.payments
  set status = 'canceled', error_code = 'replaced_by_new_pix',
      error_message = 'Cobrança substituída por nova geração.', updated_at = now()
  where driver_id = p_driver_id and kind = 'subscription' and status = 'pending';

  insert into public.payments(
    driver_id, kind, provider, amount_cents, status, expires_at, metadata
  ) values (
    p_driver_id, 'subscription', 'banco_inter', 990, 'pending', p_expires_at,
    jsonb_build_object('plan_id','core_monthly','created_by_edge','srrotas-create-pix')
  ) returning id into v_payment_id;

  return jsonb_build_object(
    'paymentId', v_payment_id,
    'amountCents', 990,
    'expiresAt', p_expires_at,
    'reused', false
  );
end;
$$;

create or replace function public.sr_attach_pix_provider_data(
  p_payment_id uuid,
  p_txid text,
  p_pix_copy_paste text,
  p_qr_code_payload text,
  p_provider_payload jsonb default '{}'::jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare v_payment public.payments%rowtype;
begin
  if p_txid is null or btrim(p_txid) = '' then raise exception 'txid_required'; end if;
  if p_pix_copy_paste is null or btrim(p_pix_copy_paste) = '' then raise exception 'pix_copy_paste_required'; end if;

  select * into v_payment from public.payments where id = p_payment_id for update;
  if not found then raise exception 'payment_not_found'; end if;
  if v_payment.status <> 'pending' then raise exception 'payment_not_pending'; end if;

  update public.payments
  set txid = p_txid, pix_copy_paste = p_pix_copy_paste,
      qr_code_payload = nullif(p_qr_code_payload,''),
      provider_payload = coalesce(p_provider_payload,'{}'::jsonb),
      error_code = null, error_message = null, updated_at = now()
  where id = p_payment_id;

  return jsonb_build_object('paymentId', p_payment_id, 'txid', p_txid, 'status', 'pending');
end;
$$;

create or replace function public.sr_mark_pix_checked(
  p_payment_id uuid,
  p_bank_status text,
  p_provider_payload jsonb default '{}'::jsonb
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.payments
  set bank_status = nullif(p_bank_status,''),
      provider_last_response = coalesce(p_provider_payload,'{}'::jsonb),
      last_checked_at = now(), check_attempts = check_attempts + 1, updated_at = now()
  where id = p_payment_id;
  if not found then raise exception 'payment_not_found'; end if;
end;
$$;

create or replace function public.sr_mark_pix_failed(
  p_payment_id uuid,
  p_error_code text,
  p_error_message text,
  p_provider_payload jsonb default '{}'::jsonb
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.payments
  set status = 'failed',
      error_code = left(coalesce(p_error_code,'provider_error'),120),
      error_message = left(coalesce(p_error_message,'Falha no Pix.'),1000),
      provider_last_response = coalesce(p_provider_payload,'{}'::jsonb),
      updated_at = now()
  where id = p_payment_id and status = 'pending';
end;
$$;

create or replace function public.sr_expire_pix_payments()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare v_count integer;
begin
  update public.payments
  set status = 'expired', error_code = 'pix_expired',
      error_message = 'Cobrança Pix expirada.', updated_at = now()
  where status = 'pending' and expires_at is not null and expires_at <= now();
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

create or replace function public.sr_grant_welcome_credits(p_driver_id uuid, p_payment_id uuid)
returns public.credit_wallets
language plpgsql
security definer
set search_path = public
as $$
declare v_inserted integer := 0; v_result public.credit_wallets;
begin
  perform public.sr_ensure_credit_wallet(p_driver_id);
  insert into public.credit_transactions(driver_id,type,amount,reference_id,idempotency_key,metadata)
  values (p_driver_id,'welcome',20,p_payment_id::text,'welcome:'||p_driver_id::text,jsonb_build_object('payment_id',p_payment_id))
  on conflict (idempotency_key) do nothing;
  get diagnostics v_inserted = row_count;
  if v_inserted = 1 then
    update public.credit_wallets
    set balance = balance + 20, lifetime_granted = lifetime_granted + 20, updated_at = now()
    where driver_id = p_driver_id;
  end if;
  select * into v_result from public.credit_wallets where driver_id = p_driver_id;
  return v_result;
end;
$$;

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
set search_path = public
as $$
declare
  v_payment public.payments%rowtype;
  v_subscription public.subscriptions%rowtype;
  v_base timestamptz;
  v_wallet public.credit_wallets;
begin
  select * into v_payment from public.payments where id = p_payment_id for update;
  if not found then raise exception 'payment_not_found'; end if;

  if v_payment.status = 'paid' then
    select * into v_subscription from public.subscriptions
      where driver_id = v_payment.driver_id and plan_id = 'core_monthly';
    perform public.sr_ensure_credit_wallet(v_payment.driver_id);
    select * into v_wallet from public.credit_wallets where driver_id = v_payment.driver_id;
    return jsonb_build_object(
      'success',true,'duplicate',true,'paymentId',v_payment.id,
      'subscriptionId',v_subscription.id,'periodEnd',v_subscription.current_period_end,
      'balance',v_wallet.balance
    );
  end if;

  if v_payment.status not in ('pending','manual_review') then raise exception 'payment_not_confirmable'; end if;

  if v_payment.txid is null or v_payment.txid <> p_txid then
    update public.payments
    set status='manual_review', bank_status=p_provider_status,
        provider_last_response=coalesce(p_provider_payload,'{}'::jsonb),
        last_checked_at=now(), check_attempts=check_attempts+1,
        error_code='txid_mismatch', error_message='O txid confirmado não corresponde à cobrança.', updated_at=now()
    where id=v_payment.id;
    return jsonb_build_object('success',false,'status','manual_review','reason','txid_mismatch');
  end if;

  if p_paid_amount_cents is null or p_paid_amount_cents <> v_payment.amount_cents then
    update public.payments
    set status='manual_review', bank_status=p_provider_status,
        provider_last_response=coalesce(p_provider_payload,'{}'::jsonb),
        last_checked_at=now(), check_attempts=check_attempts+1,
        error_code='amount_mismatch', error_message='O valor recebido não corresponde à cobrança.', updated_at=now()
    where id=v_payment.id;
    return jsonb_build_object(
      'success',false,'status','manual_review','reason','amount_mismatch',
      'expectedAmountCents',v_payment.amount_cents,'paidAmountCents',p_paid_amount_cents
    );
  end if;

  update public.payments
  set status='paid', bank_status=p_provider_status, confirmed_at=p_confirmed_at,
      provider_last_response=coalesce(p_provider_payload,'{}'::jsonb),
      last_checked_at=now(), check_attempts=check_attempts+1,
      error_code=null, error_message=null, updated_at=now()
  where id=v_payment.id;

  select * into v_subscription from public.subscriptions
    where driver_id=v_payment.driver_id and plan_id='core_monthly' for update;

  if not found then
    insert into public.subscriptions(
      driver_id,plan_id,status,starts_at,current_period_end,payment_provider
    ) values (
      v_payment.driver_id,'core_monthly','active',p_confirmed_at,p_confirmed_at + interval '30 days','banco_inter'
    ) returning * into v_subscription;
  else
    v_base := greatest(coalesce(v_subscription.current_period_end,now()),now());
    update public.subscriptions
    set status='active', starts_at=coalesce(starts_at,p_confirmed_at),
        current_period_end=v_base + interval '30 days', canceled_at=null,
        payment_provider='banco_inter', updated_at=now()
    where id=v_subscription.id returning * into v_subscription;
  end if;

  update public.payments set subscription_id=v_subscription.id, updated_at=now() where id=v_payment.id;

  insert into public.entitlements(driver_id,entitlement,active,source_subscription_id,valid_until)
  select v_payment.driver_id,e,true,v_subscription.id,v_subscription.current_period_end
  from unnest(array['core_app','history','mcp','ai','advanced_analytics']) as e
  on conflict(driver_id,entitlement) do update
    set active=true, source_subscription_id=excluded.source_subscription_id,
        valid_until=excluded.valid_until, updated_at=now();

  v_wallet := public.sr_grant_welcome_credits(v_payment.driver_id,v_payment.id);

  return jsonb_build_object(
    'success',true,'duplicate',false,'paymentId',v_payment.id,
    'subscriptionId',v_subscription.id,'periodEnd',v_subscription.current_period_end,
    'balance',v_wallet.balance
  );
end;
$$;

create or replace function public.sr_reserve_ai_credit(p_driver_id uuid, p_reference_id text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare v_balance integer;
begin
  perform public.sr_ensure_credit_wallet(p_driver_id);
  if exists(select 1 from public.credit_transactions where driver_id=p_driver_id and type='reserve' and reference_id=p_reference_id) then return true; end if;
  select balance into v_balance from public.credit_wallets where driver_id=p_driver_id for update;
  if coalesce(v_balance,0) < 1 then return false; end if;
  insert into public.credit_transactions(driver_id,type,amount,reference_id,idempotency_key)
  values(p_driver_id,'reserve',-1,p_reference_id,'ai:reserve:'||p_driver_id::text||':'||p_reference_id);
  update public.credit_wallets set balance=balance-1,updated_at=now() where driver_id=p_driver_id;
  return true;
end;
$$;

create or replace function public.sr_consume_ai_credit(p_driver_id uuid, p_reference_id text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if exists(select 1 from public.credit_transactions where driver_id=p_driver_id and type='consume' and reference_id=p_reference_id) then return true; end if;
  if not exists(select 1 from public.credit_transactions where driver_id=p_driver_id and type='reserve' and reference_id=p_reference_id) then return false; end if;
  if exists(select 1 from public.credit_transactions where driver_id=p_driver_id and type='refund' and reference_id=p_reference_id) then return false; end if;
  insert into public.credit_transactions(driver_id,type,amount,reference_id,idempotency_key)
  values(p_driver_id,'consume',0,p_reference_id,'ai:consume:'||p_driver_id::text||':'||p_reference_id)
  on conflict(idempotency_key) do nothing;
  update public.credit_wallets set lifetime_spent=lifetime_spent+1,updated_at=now() where driver_id=p_driver_id;
  return true;
end;
$$;

create or replace function public.sr_refund_ai_credit(p_driver_id uuid, p_reference_id text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare v_inserted integer:=0;
begin
  if exists(select 1 from public.credit_transactions where driver_id=p_driver_id and type='consume' and reference_id=p_reference_id) then return false; end if;
  if not exists(select 1 from public.credit_transactions where driver_id=p_driver_id and type='reserve' and reference_id=p_reference_id) then return false; end if;
  insert into public.credit_transactions(driver_id,type,amount,reference_id,idempotency_key)
  values(p_driver_id,'refund',1,p_reference_id,'ai:refund:'||p_driver_id::text||':'||p_reference_id)
  on conflict(idempotency_key) do nothing;
  get diagnostics v_inserted=row_count;
  if v_inserted=1 then update public.credit_wallets set balance=balance+1,updated_at=now() where driver_id=p_driver_id; end if;
  return true;
end;
$$;

revoke all on function public.sr_ensure_credit_wallet(uuid) from public;
revoke all on function public.sr_create_pix_payment(uuid,timestamptz) from public;
revoke all on function public.sr_attach_pix_provider_data(uuid,text,text,text,jsonb) from public;
revoke all on function public.sr_mark_pix_checked(uuid,text,jsonb) from public;
revoke all on function public.sr_mark_pix_failed(uuid,text,text,jsonb) from public;
revoke all on function public.sr_expire_pix_payments() from public;
revoke all on function public.sr_grant_welcome_credits(uuid,uuid) from public;
revoke all on function public.sr_apply_confirmed_payment(uuid,text,integer,text,jsonb,timestamptz) from public;
revoke all on function public.sr_reserve_ai_credit(uuid,text) from public;
revoke all on function public.sr_consume_ai_credit(uuid,text) from public;
revoke all on function public.sr_refund_ai_credit(uuid,text) from public;

grant execute on function public.sr_ensure_credit_wallet(uuid) to service_role;
grant execute on function public.sr_create_pix_payment(uuid,timestamptz) to service_role;
grant execute on function public.sr_attach_pix_provider_data(uuid,text,text,text,jsonb) to service_role;
grant execute on function public.sr_mark_pix_checked(uuid,text,jsonb) to service_role;
grant execute on function public.sr_mark_pix_failed(uuid,text,text,jsonb) to service_role;
grant execute on function public.sr_expire_pix_payments() to service_role;
grant execute on function public.sr_grant_welcome_credits(uuid,uuid) to service_role;
grant execute on function public.sr_apply_confirmed_payment(uuid,text,integer,text,jsonb,timestamptz) to service_role;
grant execute on function public.sr_reserve_ai_credit(uuid,text) to service_role;
grant execute on function public.sr_consume_ai_credit(uuid,text) to service_role;
grant execute on function public.sr_refund_ai_credit(uuid,text) to service_role;

commit;
