begin;

create table if not exists public.driver_trials (
  driver_id uuid primary key references public.drivers(id) on delete cascade,
  first_offer_at timestamptz not null,
  trial_started_at timestamptz not null,
  trial_ends_at timestamptz not null,
  ai_credits_granted integer not null default 5 check (ai_credits_granted between 0 and 100),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists driver_trials_end_idx on public.driver_trials(trial_ends_at);
alter table public.driver_trials enable row level security;
revoke all on table public.driver_trials from public,anon,authenticated;
grant all on table public.driver_trials to service_role;

create or replace function public.sr_start_trial_on_first_offer_v1()
returns trigger
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare inserted_driver uuid;
begin
  if new.capture_method like 'historical-import/%' then return new; end if;

  insert into public.driver_trials(
    driver_id,first_offer_at,trial_started_at,trial_ends_at,ai_credits_granted,created_at,updated_at
  ) values (
    new.driver_id,new.observed_at,new.observed_at,new.observed_at + interval '7 days',5,now(),now()
  )
  on conflict(driver_id) do nothing
  returning driver_id into inserted_driver;

  if inserted_driver is not null then
    insert into public.credit_wallets(driver_id,balance,lifetime_granted,lifetime_spent,updated_at)
    values(new.driver_id,5,5,0,now())
    on conflict(driver_id) do update set
      balance=public.credit_wallets.balance+5,
      lifetime_granted=public.credit_wallets.lifetime_granted+5,
      updated_at=now();

    insert into public.credit_transactions(
      driver_id,type,amount,reference_id,idempotency_key,metadata,created_at
    ) values (
      new.driver_id,'welcome',5,'trial_7d',
      'trial-first-offer-v1:'||new.driver_id::text,
      jsonb_build_object('source','first_live_offer','trial_days',7,'temporary_trial_credits',5),
      now()
    ) on conflict(idempotency_key) do nothing;
  end if;
  return new;
end;
$$;

revoke all on function public.sr_start_trial_on_first_offer_v1() from public,anon,authenticated;
grant execute on function public.sr_start_trial_on_first_offer_v1() to service_role;

drop trigger if exists trg_sr_start_trial_on_first_offer_v1 on public.ride_offers;
create trigger trg_sr_start_trial_on_first_offer_v1
after insert on public.ride_offers
for each row execute function public.sr_start_trial_on_first_offer_v1();

create or replace view public.sr_driver_trial_status_v1
with (security_invoker=false) as
select d.id driver_id,t.first_offer_at,t.trial_started_at,t.trial_ends_at,t.ai_credits_granted,
  case when t.driver_id is null then 'pending'
       when now()<t.trial_ends_at then 'active'
       else 'expired' end trial_status,
  case when t.driver_id is null then null
       else greatest(0,ceil(extract(epoch from (t.trial_ends_at-now()))/86400.0))::integer end days_remaining
from public.drivers d
left join public.driver_trials t on t.driver_id=d.id;

revoke all on table public.sr_driver_trial_status_v1 from public,anon,authenticated;
grant select on table public.sr_driver_trial_status_v1 to service_role;

comment on table public.driver_trials is
  'Trial Sr. Rotas: começa somente na primeira oferta live persistida; nunca na instalação, cadastro ou histórico importado.';

commit;
