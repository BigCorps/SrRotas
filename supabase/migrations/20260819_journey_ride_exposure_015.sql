-- Sr. Rotas 0.15.0 — Jornada operacional, resultado de corrida e exposição regional.
-- Aplicar manualmente no SQL Editor do projeto SrRotas antes de testar o APK 0.15.

begin;

alter table public.driver_journeys
  add column if not exists state text not null default 'ACTIVE',
  add column if not exists state_updated_at timestamptz not null default now();

update public.driver_journeys
set state = case when ended_at is null then 'ACTIVE' else 'ENDED' end,
    state_updated_at = coalesce(ended_at, started_at, created_at, now())
where state is null
   or state not in ('ACTIVE','PAUSED','ENDED')
   or (ended_at is not null and state <> 'ENDED');

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'driver_journeys_state_check' and conrelid = 'public.driver_journeys'::regclass) then
    alter table public.driver_journeys
      add constraint driver_journeys_state_check check (state in ('ACTIVE','PAUSED','ENDED'));
  end if;
end $$;

create index if not exists driver_journeys_device_id_idx
  on public.driver_journeys(device_id);
create index if not exists driver_journeys_driver_state_started_idx
  on public.driver_journeys(driver_id, state, started_at desc);

create table if not exists public.journey_state_events (
  id uuid primary key default gen_random_uuid(),
  client_event_id uuid not null,
  driver_id uuid not null references public.drivers(id) on delete cascade,
  device_id uuid references public.driver_devices(id) on delete set null,
  journey_id uuid not null references public.driver_journeys(id) on delete cascade,
  event_type text not null check (event_type in ('start','pause','resume','end')),
  state text not null check (state in ('ACTIVE','PAUSED','ENDED')),
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  unique (driver_id, client_event_id)
);

create index if not exists journey_state_events_journey_time_idx
  on public.journey_state_events(journey_id, occurred_at desc);
create index if not exists journey_state_events_driver_time_idx
  on public.journey_state_events(driver_id, occurred_at desc);

alter table public.journey_state_events enable row level security;

create table if not exists public.ride_outcomes (
  id uuid primary key default gen_random_uuid(),
  driver_id uuid not null references public.drivers(id) on delete cascade,
  device_id uuid references public.driver_devices(id) on delete set null,
  journey_id uuid not null references public.driver_journeys(id) on delete cascade,
  ride_offer_id bigint references public.ride_offers(id) on delete set null,
  local_offer_id text not null,
  status text not null check (status in ('OFFERED','DOING_RIDE','COMPLETED','NOT_COMPLETED','CANCELLED')),
  started_at timestamptz,
  completed_at timestamptz,
  cancelled_at timestamptz,
  corrected_at timestamptz,
  source text not null default 'android',
  revision integer not null default 1 check (revision > 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (driver_id, local_offer_id)
);

create index if not exists ride_outcomes_journey_status_idx
  on public.ride_outcomes(journey_id, status, updated_at desc);
create index if not exists ride_outcomes_driver_updated_idx
  on public.ride_outcomes(driver_id, updated_at desc);
create index if not exists ride_outcomes_offer_idx
  on public.ride_outcomes(ride_offer_id)
  where ride_offer_id is not null;

alter table public.ride_outcomes enable row level security;

create table if not exists public.zone_exposures (
  id uuid primary key default gen_random_uuid(),
  client_exposure_id uuid not null,
  driver_id uuid not null references public.drivers(id) on delete cascade,
  device_id uuid references public.driver_devices(id) on delete set null,
  journey_id uuid not null references public.driver_journeys(id) on delete cascade,
  cell text not null check (cell ~ '^g2:-?[0-9]+:-?[0-9]+$'),
  started_at timestamptz not null,
  ended_at timestamptz not null,
  duration_seconds integer not null default 0 check (duration_seconds >= 0 and duration_seconds <= 86400),
  close_reason text not null default 'unknown',
  next_offer_local_id text,
  location_accuracy_m numeric(10,2) check (location_accuracy_m is null or location_accuracy_m >= 0),
  created_at timestamptz not null default now(),
  unique (driver_id, client_exposure_id),
  check (ended_at >= started_at)
);

create index if not exists zone_exposures_cell_time_idx
  on public.zone_exposures(cell, started_at desc);
create index if not exists zone_exposures_journey_time_idx
  on public.zone_exposures(journey_id, started_at desc);
create index if not exists zone_exposures_driver_time_idx
  on public.zone_exposures(driver_id, started_at desc);
create index if not exists zone_exposures_next_offer_idx
  on public.zone_exposures(driver_id, next_offer_local_id)
  where next_offer_local_id is not null;

alter table public.zone_exposures enable row level security;

comment on table public.journey_state_events is
  'Eventos operacionais da jornada 0.15: iniciar, pausar, retomar e encerrar.';
comment on table public.ride_outcomes is
  'Estado confirmado pelo motorista para separar oferta observada de corrida realmente realizada.';
comment on table public.zone_exposures is
  'Intervalos agregados de disponibilidade por célula regional g2. Não armazena trilha contínua de latitude/longitude.';
comment on column public.zone_exposures.location_accuracy_m is
  'Precisão aproximada reportada pelo Android; coordenadas exatas não são persistidas nesta tabela.';

commit;

-- VERIFICAÇÃO: deve retornar as 3 tabelas e as colunas state/state_updated_at.
select table_name
from information_schema.tables
where table_schema = 'public'
  and table_name in ('journey_state_events','ride_outcomes','zone_exposures')
order by table_name;

select column_name, data_type
from information_schema.columns
where table_schema = 'public'
  and table_name = 'driver_journeys'
  and column_name in ('state','state_updated_at')
order by column_name;
