begin;

-- Sr. Rotas 0.25.0 — fallback coletivo progressivo com privacidade preservada.
-- Nunca publica grupos com menos de 3 motoristas participantes.
-- Não altera a view exata existente; adiciona somente agregações mais amplas
-- para quando região + dia + faixa de 3h + perfil não atingir o mínimo.

create or replace view public.sr_collective_offer_region_hour_anyday_v025
with (security_invoker=false) as
select
  public.sr_text_key_v1(public.sr_region_canonical_label_v1(ro.pickup_label)) as region_key,
  min(public.sr_region_canonical_label_v1(ro.pickup_label)) as region_label,
  0::smallint as weekday_iso,
  (floor(extract(hour from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo')) / 3) * 3)::smallint as hour_bucket,
  public.sr_service_profile_v1(ro.service_type) as service_profile,
  count(distinct ro.driver_id)::integer as contributor_count,
  count(*)::integer as sample_count,
  round(avg(ro.fare),2) as average_fare,
  round(avg(ro.per_km),2) as average_per_km,
  round(avg(ro.per_minute),2) as average_per_minute,
  round(avg(ro.per_hour),2) as average_per_hour,
  round(avg(ro.pickup_km),2) as average_pickup_km,
  round(avg(ro.pickup_minutes),2) as average_pickup_minutes
from public.ride_offers ro
join public.driver_preferences p
  on p.driver_id = ro.driver_id
 and p.collective_stats_opt_in = true
where coalesce(ro.capture_method,'') not like 'historical-import/%'
  and public.sr_region_canonical_label_v1(ro.pickup_label) is not null
group by
  public.sr_text_key_v1(public.sr_region_canonical_label_v1(ro.pickup_label)),
  (floor(extract(hour from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo')) / 3) * 3),
  public.sr_service_profile_v1(ro.service_type)
having count(distinct ro.driver_id) >= 3;

create or replace view public.sr_collective_offer_region_profile_v025
with (security_invoker=false) as
select
  public.sr_text_key_v1(public.sr_region_canonical_label_v1(ro.pickup_label)) as region_key,
  min(public.sr_region_canonical_label_v1(ro.pickup_label)) as region_label,
  0::smallint as weekday_iso,
  (-1)::smallint as hour_bucket,
  public.sr_service_profile_v1(ro.service_type) as service_profile,
  count(distinct ro.driver_id)::integer as contributor_count,
  count(*)::integer as sample_count,
  round(avg(ro.fare),2) as average_fare,
  round(avg(ro.per_km),2) as average_per_km,
  round(avg(ro.per_minute),2) as average_per_minute,
  round(avg(ro.per_hour),2) as average_per_hour,
  round(avg(ro.pickup_km),2) as average_pickup_km,
  round(avg(ro.pickup_minutes),2) as average_pickup_minutes
from public.ride_offers ro
join public.driver_preferences p
  on p.driver_id = ro.driver_id
 and p.collective_stats_opt_in = true
where coalesce(ro.capture_method,'') not like 'historical-import/%'
  and public.sr_region_canonical_label_v1(ro.pickup_label) is not null
group by
  public.sr_text_key_v1(public.sr_region_canonical_label_v1(ro.pickup_label)),
  public.sr_service_profile_v1(ro.service_type)
having count(distinct ro.driver_id) >= 3;

create or replace view public.sr_collective_offer_region_v025
with (security_invoker=false) as
select
  public.sr_text_key_v1(public.sr_region_canonical_label_v1(ro.pickup_label)) as region_key,
  min(public.sr_region_canonical_label_v1(ro.pickup_label)) as region_label,
  0::smallint as weekday_iso,
  (-1)::smallint as hour_bucket,
  'unknown'::text as service_profile,
  count(distinct ro.driver_id)::integer as contributor_count,
  count(*)::integer as sample_count,
  round(avg(ro.fare),2) as average_fare,
  round(avg(ro.per_km),2) as average_per_km,
  round(avg(ro.per_minute),2) as average_per_minute,
  round(avg(ro.per_hour),2) as average_per_hour,
  round(avg(ro.pickup_km),2) as average_pickup_km,
  round(avg(ro.pickup_minutes),2) as average_pickup_minutes
from public.ride_offers ro
join public.driver_preferences p
  on p.driver_id = ro.driver_id
 and p.collective_stats_opt_in = true
where coalesce(ro.capture_method,'') not like 'historical-import/%'
  and public.sr_region_canonical_label_v1(ro.pickup_label) is not null
group by public.sr_text_key_v1(public.sr_region_canonical_label_v1(ro.pickup_label))
having count(distinct ro.driver_id) >= 3;

revoke all on table public.sr_collective_offer_region_hour_anyday_v025 from public, anon, authenticated;
revoke all on table public.sr_collective_offer_region_profile_v025 from public, anon, authenticated;
revoke all on table public.sr_collective_offer_region_v025 from public, anon, authenticated;
grant select on table public.sr_collective_offer_region_hour_anyday_v025 to service_role;
grant select on table public.sr_collective_offer_region_profile_v025 to service_role;
grant select on table public.sr_collective_offer_region_v025 to service_role;

commit;
