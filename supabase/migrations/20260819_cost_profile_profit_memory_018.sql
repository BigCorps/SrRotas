-- Sr. Rotas 0.18.0 — Perfil de custos + memória do Lucro est.*
-- Aplicar manualmente ANTES de publicar o backend Android/Web 0.18.

begin;

create table if not exists public.driver_cost_profiles (
  driver_id uuid primary key
    references public.drivers(id)
    on delete cascade,

  vehicle_type text not null
    default 'combustion'
    check (
      vehicle_type in (
        'combustion',
        'electric',
        'hybrid',
        'plugin_hybrid'
      )
    ),

  ownership_type text not null
    default 'paid'
    check (
      ownership_type in (
        'paid',
        'financed',
        'rented',
        'subscription'
      )
    ),

  energy_mode text not null
    default 'gasoline'
    check (
      energy_mode in (
        'gasoline',
        'ethanol',
        'gnv',
        'electricity',
        'combination'
      )
    ),

  combination_liquid_fuel text not null
    default 'gasoline'
    check (
      combination_liquid_fuel in (
        'gasoline',
        'ethanol',
        'gnv'
      )
    ),

  fuel_price_per_unit numeric(12,4)
    check (
      fuel_price_per_unit is null
      or fuel_price_per_unit >= 0
    ),

  fuel_km_per_unit numeric(12,4)
    check (
      fuel_km_per_unit is null
      or fuel_km_per_unit >= 0
    ),

  electricity_price_per_kwh numeric(12,4)
    check (
      electricity_price_per_kwh is null
      or electricity_price_per_kwh >= 0
    ),

  electric_kwh_per_100_km numeric(12,4)
    check (
      electric_kwh_per_100_km is null
      or electric_kwh_per_100_km >= 0
    ),

  ownership_monthly numeric(14,2)
    not null default 0
    check (ownership_monthly >= 0),

  insurance_monthly numeric(14,2)
    not null default 0
    check (insurance_monthly >= 0),

  maintenance_monthly numeric(14,2)
    not null default 0
    check (maintenance_monthly >= 0),

  tires_monthly numeric(14,2)
    not null default 0
    check (tires_monthly >= 0),

  other_monthly numeric(14,2)
    not null default 0
    check (other_monthly >= 0),

  monthly_work_km numeric(14,2)
    check (
      monthly_work_km is null
      or monthly_work_km > 0
    ),

  monthly_work_km_source text
    not null default 'estimated'
    check (
      monthly_work_km_source in (
        'userProvided',
        'estimated'
      )
    ),

  estimated_monthly_work_km numeric(14,2)
    not null default 3000
    check (
      estimated_monthly_work_km > 0
    ),

  average_journey_hours numeric(10,2)
    check (
      average_journey_hours is null
      or (
        average_journey_hours >= 0
        and average_journey_hours <= 24
      )
    ),

  monthly_work_hours numeric(12,2)
    check (
      monthly_work_hours is null
      or monthly_work_hours >= 0
    ),

  effective_cost_per_km numeric(12,4)
    not null default 0
    check (
      effective_cost_per_km >= 0
    ),

  variable_cost_per_km numeric(12,4)
    not null default 0
    check (
      variable_cost_per_km >= 0
    ),

  fixed_cost_per_km numeric(12,4)
    not null default 0
    check (
      fixed_cost_per_km >= 0
    ),

  fixed_monthly_total numeric(14,2)
    not null default 0
    check (
      fixed_monthly_total >= 0
    ),

  allocation_km_per_month numeric(14,2)
    not null default 3000
    check (
      allocation_km_per_month > 0
    ),

  calculation_version text
    not null default 'sr-cost-v0.18.0',

  calculation_snapshot jsonb
    not null default '{}'::jsonb,

  client_updated_at timestamptz
    not null default now(),

  created_at timestamptz
    not null default now(),

  updated_at timestamptz
    not null default now(),

  check (
    monthly_work_km_source <> 'userProvided'
    or monthly_work_km is not null
  )
);

create table if not exists public.driver_cost_profile_revisions (
  id uuid primary key
    default gen_random_uuid(),

  driver_id uuid not null
    references public.drivers(id)
    on delete cascade,

  client_updated_at timestamptz
    not null,

  profile_snapshot jsonb
    not null,

  calculation_snapshot jsonb
    not null,

  calculation_version text
    not null,

  created_at timestamptz
    not null default now(),

  unique (
    driver_id,
    client_updated_at
  )
);

create index if not exists
  driver_cost_profile_revisions_driver_time_idx
on public.driver_cost_profile_revisions (
  driver_id,
  client_updated_at desc
);

alter table public.driver_cost_profiles
  enable row level security;

alter table public.driver_cost_profile_revisions
  enable row level security;

revoke all
  on table public.driver_cost_profiles
  from public, anon, authenticated;

revoke all
  on table public.driver_cost_profile_revisions
  from public, anon, authenticated;

grant select, insert, update, delete
  on table public.driver_cost_profiles
  to service_role;

grant select, insert, update, delete
  on table public.driver_cost_profile_revisions
  to service_role;

comment on table public.driver_cost_profiles is
  'Perfil pessoal de custos Sr. Rotas 0.18. Usado para produzir custo operacional estimado por km e memória do Lucro est.*.';

comment on table public.driver_cost_profile_revisions is
  'Histórico versionado do perfil de custos. Permite explicar posteriormente qual composição de custos estava associada a uma oferta.';

comment on column public.driver_cost_profiles.monthly_work_km_source is
  'userProvided quando o motorista informou km/mês; estimated quando usa referência estimada configurável.';

comment on column public.driver_cost_profiles.calculation_snapshot is
  'Componentes determinísticos do cálculo; não é resultado de LLM.';

alter table public.ride_offers
  add column if not exists
    cost_per_km_used numeric(12,4);

alter table public.ride_offers
  add column if not exists
    cost_source text not null
    default 'legacy_unknown';

alter table public.ride_offers
  add column if not exists
    cost_profile_version text;

alter table public.ride_offers
  add column if not exists
    cost_profile_updated_at timestamptz;

update public.ride_offers
set
  cost_per_km_used =
    round(
      (
        estimated_cost /
        nullif(total_km, 0)
      )::numeric,
      4
    ),
  cost_source =
    case
      when capture_method like 'historical-import/%'
        then 'historical_revaluation'
      else 'legacy_reconstructed'
    end,
  cost_profile_version =
    'legacy_pre_018'
where
  cost_per_km_used is null
  and estimated_cost is not null
  and total_km is not null
  and total_km > 0;

create index if not exists
  ride_offers_driver_cost_profile_time_idx
on public.ride_offers (
  driver_id,
  cost_profile_updated_at desc
)
where cost_profile_updated_at is not null;

comment on column public.ride_offers.cost_per_km_used is
  'Snapshot do custo por km efetivamente usado no cálculo da oferta.';

comment on column public.ride_offers.cost_source is
  'Origem auditável do custo: perfil 0.18, legado reconstruído, importação histórica ou desconhecido.';

comment on column public.ride_offers.cost_profile_version is
  'Versão do modelo/perfil de custos associada à estimativa da oferta.';

comment on column public.ride_offers.cost_profile_updated_at is
  'Timestamp do perfil de custos ativo quando a oferta foi estruturada, quando disponível.';

commit;

-- VERIFICAÇÃO 1: tabelas.
select table_name
from information_schema.tables
where table_schema = 'public'
  and table_name in (
    'driver_cost_profiles',
    'driver_cost_profile_revisions'
  )
order by table_name;

-- VERIFICAÇÃO 2: novas colunas das ofertas.
select
  column_name,
  data_type,
  column_default
from information_schema.columns
where table_schema = 'public'
  and table_name = 'ride_offers'
  and column_name in (
    'cost_per_km_used',
    'cost_source',
    'cost_profile_version',
    'cost_profile_updated_at'
  )
order by column_name;

-- VERIFICAÇÃO 3: acesso deve ficar somente no backend service_role.
select
  has_table_privilege(
    'anon',
    'public.driver_cost_profiles',
    'SELECT'
  ) as anon_select,
  has_table_privilege(
    'authenticated',
    'public.driver_cost_profiles',
    'SELECT'
  ) as authenticated_select,
  has_table_privilege(
    'service_role',
    'public.driver_cost_profiles',
    'SELECT'
  ) as service_role_select;

-- VERIFICAÇÃO 4: cobertura histórica reconstruível.
select
  count(*) as offers_total,
  count(*) filter (
    where cost_per_km_used is not null
  ) as offers_with_cost_snapshot,
  count(*) filter (
    where cost_source = 'legacy_reconstructed'
  ) as legacy_reconstructed,
  count(*) filter (
    where cost_source = 'historical_revaluation'
  ) as historical_revaluation,
  count(*) filter (
    where cost_source = 'legacy_unknown'
  ) as legacy_unknown
from public.ride_offers;
