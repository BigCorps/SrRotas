begin;

-- Dados reais informados pelo motorista para cada jornada.
-- Mantidos fora de driver_journeys para não alterar o fluxo já validado.
create table if not exists public.journey_vehicle_metrics (
  journey_id uuid primary key references public.driver_journeys(id) on delete cascade,
  driver_id uuid not null references public.drivers(id) on delete cascade,
  odometer_start_km numeric(12,1),
  odometer_end_km numeric(12,1),
  distance_km numeric(12,1) generated always as (
    case
      when odometer_start_km is not null
       and odometer_end_km is not null
       and odometer_end_km >= odometer_start_km
      then odometer_end_km - odometer_start_km
      else null
    end
  ) stored,
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  check (odometer_start_km is null or odometer_start_km >= 0),
  check (odometer_end_km is null or odometer_end_km >= 0),
  check (
    odometer_start_km is null
    or odometer_end_km is null
    or odometer_end_km >= odometer_start_km
  )
);

create table if not exists public.journey_energy_entries (
  id uuid primary key default gen_random_uuid(),
  client_entry_id text not null,
  journey_id uuid not null references public.driver_journeys(id) on delete cascade,
  driver_id uuid not null references public.drivers(id) on delete cascade,
  device_id uuid references public.driver_devices(id) on delete set null,
  energy_type text not null,
  amount_paid numeric(12,2),
  quantity numeric(12,3),
  unit text not null,
  fuel_type text,
  recorded_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  unique (driver_id, client_entry_id),
  check (energy_type in ('fuel', 'electric')),
  check (unit in ('liter', 'kwh')),
  check (
    (energy_type = 'fuel' and unit = 'liter')
    or (energy_type = 'electric' and unit = 'kwh')
  ),
  check (amount_paid is null or amount_paid >= 0),
  check (quantity is null or quantity >= 0),
  check (coalesce(amount_paid, 0) > 0 or coalesce(quantity, 0) > 0)
);

create index if not exists journey_vehicle_metrics_driver_idx
  on public.journey_vehicle_metrics(driver_id, updated_at desc);
create index if not exists journey_energy_entries_journey_idx
  on public.journey_energy_entries(journey_id, recorded_at);
create index if not exists journey_energy_entries_driver_idx
  on public.journey_energy_entries(driver_id, recorded_at desc);

alter table public.journey_vehicle_metrics enable row level security;
alter table public.journey_energy_entries enable row level security;

-- Mesmo modelo de segurança das jornadas atuais: acesso somente pelo backend service_role.
revoke all on public.journey_vehicle_metrics from public, anon, authenticated;
revoke all on public.journey_energy_entries from public, anon, authenticated;
grant select, insert, update, delete on public.journey_vehicle_metrics to service_role;
grant select, insert, update, delete on public.journey_energy_entries to service_role;

comment on table public.journey_vehicle_metrics is
  'Hodômetro real informado pelo motorista para a janela exata da jornada Sr. Rotas.';
comment on table public.journey_energy_entries is
  'Abastecimentos e recargas reais vinculados à jornada. Não alteram automaticamente o custo/km configurado.';

commit;
