begin;

-- 0.3 Alpha: jornadas explícitas. Uma jornada organiza ofertas observadas,
-- sem inferir que qualquer oferta foi aceita, iniciada ou concluída.
create table if not exists public.driver_journeys (
  id uuid primary key default gen_random_uuid(),
  driver_id uuid not null references public.drivers(id) on delete cascade,
  device_id uuid references public.driver_devices(id) on delete set null,
  platform text not null default 'uber',
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  end_reason text,
  created_at timestamptz not null default now(),
  check (ended_at is null or ended_at >= started_at)
);

alter table public.driver_journeys enable row level security;

alter table public.ride_offers
  add column if not exists journey_id uuid;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'ride_offers_journey_id_fkey'
  ) then
    alter table public.ride_offers
      add constraint ride_offers_journey_id_fkey
      foreign key (journey_id) references public.driver_journeys(id) on delete set null;
  end if;
end $$;

alter table public.driver_preferences
  add column if not exists min_fare numeric(10,2) not null default 0,
  add column if not exists max_pickup_km numeric(10,2) not null default 5,
  add column if not exists min_profit numeric(10,2) not null default 0;

create index if not exists driver_devices_driver_id_idx
  on public.driver_devices(driver_id);
create index if not exists mcp_tool_audit_logs_driver_id_idx
  on public.mcp_tool_audit_logs(driver_id);
create index if not exists driver_journeys_driver_started_idx
  on public.driver_journeys(driver_id, started_at desc);
create index if not exists driver_journeys_driver_open_idx
  on public.driver_journeys(driver_id, started_at desc) where ended_at is null;
create index if not exists ride_offers_journey_observed_idx
  on public.ride_offers(journey_id, observed_at desc);

comment on table public.driver_journeys is
  'Sessões de observação do Sr. Rotas. Não representam automaticamente tempo em corrida, aceite ou conclusão.';
comment on column public.ride_offers.journey_id is
  'Jornada durante a qual a oferta foi observada. Não implica aceite ou realização da corrida.';

-- Hardening de uma função auxiliar já existente no projeto Supabase.
-- O backend usa service_role; clientes anon/authenticated não precisam executá-la.
do $$
begin
  if to_regprocedure('public.rls_auto_enable()') is not null then
    execute 'revoke execute on function public.rls_auto_enable() from public';
    execute 'revoke execute on function public.rls_auto_enable() from anon';
    execute 'revoke execute on function public.rls_auto_enable() from authenticated';
    execute 'grant execute on function public.rls_auto_enable() to service_role';
  end if;
end $$;

-- Mantemos o padrão do Alpha: RLS habilitado e nenhuma policy pública.
-- Todas as operações atuais passam pelo backend service_role.
commit;
