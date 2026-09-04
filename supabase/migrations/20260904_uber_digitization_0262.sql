-- Sr. Rotas 0.26.2 — enriquecimento aditivo da digitalização manual da Uber.
-- Não altera ride_offers nem transforma automaticamente histórico em oferta.

alter table public.uber_session_imports
  add column if not exists journey_id uuid,
  add column if not exists observation text;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'uber_session_imports_journey_id_fkey'
      and conrelid = 'public.uber_session_imports'::regclass
  ) then
    alter table public.uber_session_imports
      add constraint uber_session_imports_journey_id_fkey
      foreign key (journey_id) references public.driver_journeys(id) on delete set null;
  end if;
end $$;

create index if not exists uber_session_imports_driver_journey_idx
  on public.uber_session_imports(driver_id, journey_id, captured_at desc);

alter table public.uber_completed_ride_imports
  add column if not exists duration_seconds integer,
  add column if not exists distance_km numeric,
  add column if not exists surge_amount numeric,
  add column if not exists extra_amount numeric,
  add column if not exists ride_status text not null default 'completed';

-- Na 0.26.2 cancelamentos podem ser registrados com tarifa zero. Eles ficam
-- separados e nunca corrigem ride_outcomes automaticamente.
alter table public.uber_completed_ride_imports
  drop constraint if exists uber_completed_ride_imports_fare_check;
alter table public.uber_completed_ride_imports
  add constraint uber_completed_ride_imports_fare_check check (fare >= 0);

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'uber_completed_ride_imports_duration_seconds_check'
      and conrelid = 'public.uber_completed_ride_imports'::regclass
  ) then
    alter table public.uber_completed_ride_imports
      add constraint uber_completed_ride_imports_duration_seconds_check
      check (duration_seconds is null or duration_seconds between 0 and 86400);
  end if;
  if not exists (
    select 1 from pg_constraint
    where conname = 'uber_completed_ride_imports_distance_km_check'
      and conrelid = 'public.uber_completed_ride_imports'::regclass
  ) then
    alter table public.uber_completed_ride_imports
      add constraint uber_completed_ride_imports_distance_km_check
      check (distance_km is null or (distance_km >= 0 and distance_km <= 2000));
  end if;
  if not exists (
    select 1 from pg_constraint
    where conname = 'uber_completed_ride_imports_surge_amount_check'
      and conrelid = 'public.uber_completed_ride_imports'::regclass
  ) then
    alter table public.uber_completed_ride_imports
      add constraint uber_completed_ride_imports_surge_amount_check
      check (surge_amount is null or surge_amount >= 0);
  end if;
  if not exists (
    select 1 from pg_constraint
    where conname = 'uber_completed_ride_imports_extra_amount_check'
      and conrelid = 'public.uber_completed_ride_imports'::regclass
  ) then
    alter table public.uber_completed_ride_imports
      add constraint uber_completed_ride_imports_extra_amount_check
      check (extra_amount is null or extra_amount >= 0);
  end if;
  if not exists (
    select 1 from pg_constraint
    where conname = 'uber_completed_ride_imports_ride_status_check'
      and conrelid = 'public.uber_completed_ride_imports'::regclass
  ) then
    alter table public.uber_completed_ride_imports
      add constraint uber_completed_ride_imports_ride_status_check
      check (ride_status in ('completed','cancelled'));
  end if;
end $$;

create index if not exists uber_completed_ride_imports_driver_status_time_idx
  on public.uber_completed_ride_imports(driver_id, ride_status, occurred_at desc nulls last, captured_at desc);

comment on column public.uber_session_imports.journey_id is
  'Jornada Sr. Rotas associada ao Resumo da sessão somente quando a correspondência temporal é segura.';
comment on column public.uber_session_imports.observation is
  'Mensagem complementar visível no resumo da Uber; não substitui métricas estruturadas.';
comment on column public.uber_completed_ride_imports.ride_status is
  'Situação observada no Histórico da Uber. Cancelled não altera ride_outcomes automaticamente.';
