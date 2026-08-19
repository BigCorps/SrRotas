-- Sr. Rotas 0.17.0 — Inteligência estatística regional v1
-- Executar manualmente ANTES de publicar o backend 0.17.

begin;

alter table public.driver_preferences
  add column if not exists collective_stats_opt_in boolean not null default false;

create index if not exists ride_offers_driver_destination_time_idx
  on public.ride_offers(driver_id, destination_cell, observed_at desc)
  where destination_cell is not null;

create index if not exists ride_offers_driver_local_offer_idx
  on public.ride_offers(driver_id, local_offer_id)
  where local_offer_id is not null;

create index if not exists zone_exposures_driver_cell_time_idx
  on public.zone_exposures(driver_id, cell, started_at desc);

create index if not exists historical_import_access_added_by_driver_idx
  on public.historical_import_access(added_by_driver_id)
  where added_by_driver_id is not null;

comment on column public.driver_preferences.collective_stats_opt_in is
  'Opt-in explícito para contribuição a estatísticas coletivas agregadas. Default false.';

-- A view coletiva não contém driver_id, coordenadas exatas, endereço textual,
-- OCR bruto ou screenshot. Só publica grupos com no mínimo 3 contribuidores.
--
-- Também remove rajadas artificiais de Radar: vários offer_observed em até
-- 15 segundos, na mesma jornada/célula, contam como uma única chegada de
-- oportunidade para o relógio estatístico. As ofertas continuam preservadas
-- normalmente em ride_offers.
create or replace view public.sr_collective_region_hour_v1
with (security_invoker = true)
as
with raw_exposure as (
  select
    z.*,
    lag(z.close_reason) over (
      partition by z.driver_id, z.journey_id, z.cell
      order by z.started_at, z.id
    ) as previous_close_reason,
    lag(z.ended_at) over (
      partition by z.driver_id, z.journey_id, z.cell
      order by z.started_at, z.id
    ) as previous_ended_at
  from public.zone_exposures z
  where z.ended_at is not null
), cleaned_exposure as (
  select z.*
  from raw_exposure z
  where not (
    z.close_reason = 'offer_observed'
    and z.next_offer_local_id is not null
    and z.duration_seconds <= 15
    and z.previous_close_reason = 'offer_observed'
    and z.previous_ended_at is not null
    and abs(extract(epoch from (z.started_at - z.previous_ended_at))) <= 15
  )
)
select
  z.cell,
  extract(isodow from z.started_at at time zone 'America/Sao_Paulo')::smallint as weekday_iso,
  (floor(extract(hour from z.started_at at time zone 'America/Sao_Paulo') / 3) * 3)::smallint as hour_bucket,
  count(distinct z.driver_id)::integer as contributor_count,
  count(*)::integer as exposure_count,
  coalesce(sum(z.duration_seconds), 0)::bigint as total_seconds,

  count(*) filter (
    where z.close_reason = 'offer_observed'
      and z.next_offer_local_id is not null
  )::integer as offer_hits,

  count(*) filter (
    where z.duration_seconds >= 300
       or (
         z.close_reason = 'offer_observed'
         and z.next_offer_local_id is not null
         and z.duration_seconds <= 300
       )
  )::integer as eligible_5,

  count(*) filter (
    where z.close_reason = 'offer_observed'
      and z.next_offer_local_id is not null
      and z.duration_seconds <= 300
  )::integer as success_5,

  count(*) filter (
    where z.duration_seconds >= 600
       or (
         z.close_reason = 'offer_observed'
         and z.next_offer_local_id is not null
         and z.duration_seconds <= 600
       )
  )::integer as eligible_10,

  count(*) filter (
    where z.close_reason = 'offer_observed'
      and z.next_offer_local_id is not null
      and z.duration_seconds <= 600
  )::integer as success_10,

  count(*) filter (
    where z.duration_seconds >= 900
       or (
         z.close_reason = 'offer_observed'
         and z.next_offer_local_id is not null
         and z.duration_seconds <= 900
       )
  )::integer as eligible_15,

  count(*) filter (
    where z.close_reason = 'offer_observed'
      and z.next_offer_local_id is not null
      and z.duration_seconds <= 900
  )::integer as success_15,

  round(avg(ro.per_km)::numeric, 2) as average_per_km,
  round(avg(ro.per_minute)::numeric, 2) as average_per_minute

from cleaned_exposure z
join public.driver_preferences p
  on p.driver_id = z.driver_id
 and p.collective_stats_opt_in = true
left join public.ride_offers ro
  on ro.driver_id = z.driver_id
 and ro.local_offer_id = z.next_offer_local_id
group by
  z.cell,
  extract(isodow from z.started_at at time zone 'America/Sao_Paulo'),
  (floor(extract(hour from z.started_at at time zone 'America/Sao_Paulo') / 3) * 3)
having count(distinct z.driver_id) >= 3;

revoke all on public.sr_collective_region_hour_v1
  from public, anon, authenticated;

grant select on public.sr_collective_region_hour_v1
  to service_role;

comment on view public.sr_collective_region_hour_v1 is
  'Agregado coletivo Sr. Rotas v1. Apenas opt-in, mínimo de 3 contribuidores e sem identificadores/endereço/OCR/screenshot.';

commit;

-- VERIFICAÇÃO
select column_name, data_type, column_default
from information_schema.columns
where table_schema = 'public'
  and table_name = 'driver_preferences'
  and column_name = 'collective_stats_opt_in';

select
  has_table_privilege(
    'anon',
    'public.sr_collective_region_hour_v1',
    'SELECT'
  ) as anon_select,
  has_table_privilege(
    'authenticated',
    'public.sr_collective_region_hour_v1',
    'SELECT'
  ) as authenticated_select,
  has_table_privilege(
    'service_role',
    'public.sr_collective_region_hour_v1',
    'SELECT'
  ) as service_role_select;
