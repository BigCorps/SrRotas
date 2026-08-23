begin;

create or replace function public.sr_region_canonical_label_v1(value text)
returns text
language plpgsql
immutable
parallel safe
as $$
declare
  base text := public.sr_region_label_v1(value);
  key text;
begin
  if base is null then return null; end if;
  key := public.sr_text_key_v1(base);

  if base ~* '^(rua|r\.|avenida|av\.|alameda|al\.|estrada|rodovia|travessa|tv\.|ponto de encontro)\b'
     or key ~ '(parada|como-foi|viagem-longa|ajude-a-melhorar)'
     or length(base)>55
  then return null; end if;

  return case
    when key in ('consolagao','consolacao','consolacgao','consola-gao','consola-ao') or key like 'consola%ao' then 'Consolação'
    when key in ('itaim-bibi','ltaim-bibi') then 'Itaim Bibi'
    when key='campo-belo' or key like 'campo%belo' then 'Campo Belo'
    when key in ('saude','satide') then 'Saúde'
    when key in ('chacara-santo-antenio','chacara-santo-antonio') then 'Chácara Santo Antônio'
    when key in ('higienopolis','higiendpolis') then 'Higienópolis'
    when key in ('indianopolis','indiandpolis') then 'Indianópolis'
    when key='cerqueira-cesar' then 'Cerqueira César'
    when key='bairro-de-pinheiros' then 'Pinheiros'
    when key='republica' or key like '%republica' then 'República'
    when key='paraiso' then 'Paraíso'
    when key='limao' then 'Limão'
    when key='agua-branca' then 'Água Branca'
    when key='santa-cecilia' then 'Santa Cecília'
    when key='vila-olimpia' then 'Vila Olímpia'
    when key='butanta' then 'Butantã'
    when key='sacoma' then 'Sacomã'
    when key='vila-maria-vila-guilherme' then 'Vila Maria / Vila Guilherme'
    else base
  end;
end;
$$;

create or replace function public.sr_region_key_v1(value text)
returns text
language sql
immutable
parallel safe
as $$ select public.sr_text_key_v1(public.sr_region_canonical_label_v1(value)); $$;

create or replace function public.sr_refresh_region_seed_v1()
returns integer
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare inserted_count integer;
begin
  delete from public.sr_region_seed_v1;
  with source as (
    select sr_region_canonical_label_v1(r.normalized_payload->>'pickup_text') region_label,
      coalesce((r.normalized_payload->>'observed_at')::timestamptz,r.created_at) observed_at,
      greatest((r.normalized_payload->>'fare')::numeric,0) fare,
      nullif((r.normalized_payload->>'pickup_km')::numeric,0) pickup_km,
      nullif((r.normalized_payload->>'trip_km')::numeric,0) trip_km,
      nullif((r.normalized_payload->>'pickup_minutes')::numeric,0) pickup_minutes,
      nullif((r.normalized_payload->>'trip_minutes')::numeric,0) trip_minutes,
      sr_service_profile_v1(r.normalized_payload->>'service_type') service_profile
    from public.historical_import_rows r where r.validation_status='valid'
  ), m as (
    select *,coalesce(pickup_km,0)+coalesce(trip_km,0) total_km,
      coalesce(pickup_minutes,0)+coalesce(trip_minutes,0) total_minutes
    from source where region_label is not null and fare>0
  ), p as (
    select sr_text_key_v1(region_label) region_key,region_label,
      extract(isodow from observed_at at time zone 'America/Sao_Paulo')::smallint weekday_iso,
      (floor(extract(hour from observed_at at time zone 'America/Sao_Paulo')/3)*3)::smallint hour_bucket,
      service_profile,fare,case when total_km>0 then fare/total_km end per_km,
      case when total_minutes>0 then fare/total_minutes end per_minute,
      case when total_minutes>0 then fare/(total_minutes/60.0) end per_hour,
      pickup_km,pickup_minutes,observed_at
    from m where sr_text_key_v1(region_label)<>''
  )
  insert into public.sr_region_seed_v1(
    region_key,region_label,weekday_iso,hour_bucket,service_profile,sample_count,
    average_fare,median_fare,p25_fare,p75_fare,
    average_per_km,median_per_km,p25_per_km,p75_per_km,
    average_per_minute,median_per_minute,p25_per_minute,p75_per_minute,
    average_per_hour,median_per_hour,p25_per_hour,p75_per_hour,
    average_pickup_km,average_pickup_minutes,first_observed_at,last_observed_at,refreshed_at
  )
  select region_key,min(region_label),weekday_iso,hour_bucket,service_profile,count(*)::integer,
    round(avg(fare),2),round(percentile_cont(.5) within group(order by fare)::numeric,2),
    round(percentile_cont(.25) within group(order by fare)::numeric,2),round(percentile_cont(.75) within group(order by fare)::numeric,2),
    round(avg(per_km),2),round(percentile_cont(.5) within group(order by per_km)::numeric,2),
    round(percentile_cont(.25) within group(order by per_km)::numeric,2),round(percentile_cont(.75) within group(order by per_km)::numeric,2),
    round(avg(per_minute),2),round(percentile_cont(.5) within group(order by per_minute)::numeric,2),
    round(percentile_cont(.25) within group(order by per_minute)::numeric,2),round(percentile_cont(.75) within group(order by per_minute)::numeric,2),
    round(avg(per_hour),2),round(percentile_cont(.5) within group(order by per_hour)::numeric,2),
    round(percentile_cont(.25) within group(order by per_hour)::numeric,2),round(percentile_cont(.75) within group(order by per_hour)::numeric,2),
    round(avg(pickup_km),2),round(avg(pickup_minutes),2),min(observed_at),max(observed_at),now()
  from p group by region_key,weekday_iso,hour_bucket,service_profile;
  get diagnostics inserted_count=row_count;
  return inserted_count;
end;
$$;

create or replace view public.sr_collective_offer_region_hour_v1 with (security_invoker=false) as
select sr_region_key_v1(ro.pickup_label) region_key,min(sr_region_canonical_label_v1(ro.pickup_label)) region_label,
 extract(isodow from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo'))::smallint weekday_iso,
 (floor(extract(hour from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo'))/3)*3)::smallint hour_bucket,
 sr_service_profile_v1(ro.service_type) service_profile,count(distinct ro.driver_id)::integer contributor_count,count(*)::integer sample_count,
 round(avg(ro.fare),2) average_fare,round(avg(ro.per_km),2) average_per_km,
 round(avg(ro.per_minute),2) average_per_minute,round(avg(ro.per_hour),2) average_per_hour,
 round(avg(ro.pickup_km),2) average_pickup_km,round(avg(ro.pickup_minutes),2) average_pickup_minutes
from public.ride_offers ro join public.driver_preferences p on p.driver_id=ro.driver_id and p.collective_stats_opt_in=true
where ro.capture_method not like 'historical-import/%' and sr_region_canonical_label_v1(ro.pickup_label) is not null
group by sr_region_key_v1(ro.pickup_label),extract(isodow from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo')),
 (floor(extract(hour from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo'))/3)*3),sr_service_profile_v1(ro.service_type)
having count(distinct ro.driver_id)>=3;

create or replace view public.sr_personal_offer_region_hour_v1 with (security_invoker=false) as
select ro.driver_id,sr_region_key_v1(ro.pickup_label) region_key,min(sr_region_canonical_label_v1(ro.pickup_label)) region_label,
 extract(isodow from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo'))::smallint weekday_iso,
 (floor(extract(hour from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo'))/3)*3)::smallint hour_bucket,
 sr_service_profile_v1(ro.service_type) service_profile,count(*)::integer sample_count,
 round(avg(ro.fare),2) average_fare,round(avg(ro.per_km),2) average_per_km,
 round(avg(ro.per_minute),2) average_per_minute,round(avg(ro.per_hour),2) average_per_hour,
 round(avg(ro.pickup_km),2) average_pickup_km,round(avg(ro.pickup_minutes),2) average_pickup_minutes
from public.ride_offers ro left join public.driver_preferences p on p.driver_id=ro.driver_id
where ro.capture_method not like 'historical-import/%' and sr_region_canonical_label_v1(ro.pickup_label) is not null
group by ro.driver_id,sr_region_key_v1(ro.pickup_label),extract(isodow from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo')),
 (floor(extract(hour from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo'))/3)*3),sr_service_profile_v1(ro.service_type);

select public.sr_refresh_region_seed_v1();
commit;
