begin;

-- Sr. Rotas 0.21 — estratégia multiplataforma, aparência e baseline regional.
-- Não altera ofertas individuais nem o Offer Engine.

alter table public.driver_preferences
  add column if not exists strategy_preset text not null default 'popular',
  add column if not exists max_pickup_minutes integer not null default 8,
  add column if not exists app_theme text not null default 'auto',
  add column if not exists hud_theme_mode text not null default 'follow_app';

do $$ begin
  alter table public.driver_preferences add constraint driver_preferences_strategy_preset_check
    check (strategy_preset in ('popular','comfort','premium','custom'));
exception when duplicate_object then null; end $$;

do $$ begin
  alter table public.driver_preferences add constraint driver_preferences_max_pickup_minutes_check
    check (max_pickup_minutes between 0 and 120);
exception when duplicate_object then null; end $$;

do $$ begin
  alter table public.driver_preferences add constraint driver_preferences_app_theme_check
    check (app_theme in ('auto','light','dark'));
exception when duplicate_object then null; end $$;

do $$ begin
  alter table public.driver_preferences add constraint driver_preferences_hud_theme_mode_check
    check (hud_theme_mode in ('follow_app','light','dark'));
exception when duplicate_object then null; end $$;

-- Apenas defaults de novas contas. Linhas existentes não são sobrescritas.
alter table public.driver_preferences alter column red_per_km_below set default 1.20;
alter table public.driver_preferences alter column min_per_km set default 1.50;
alter table public.driver_preferences alter column red_per_minute_below set default 0.40;
alter table public.driver_preferences alter column min_per_minute set default 0.50;
alter table public.driver_preferences alter column red_per_hour_below set default 24.00;
alter table public.driver_preferences alter column min_per_hour set default 30.00;
alter table public.driver_preferences alter column max_pickup_km set default 4.00;

create or replace function public.sr_text_key_v1(value text)
returns text
language sql
immutable
parallel safe
as $$
  select trim(both '-' from regexp_replace(
    translate(lower(coalesce(value,'')),
      'áàâãäéèêëíìîïóòôõöúùûüçñ',
      'aaaaaeeeeiiiiooooouuuucn'),
    '[^a-z0-9]+','-','g'));
$$;

create or replace function public.sr_service_profile_v1(value text)
returns text
language sql
immutable
parallel safe
as $$
  select case
    when sr_text_key_v1(value) ~ '(black|premium|luxo|lux|top)' then 'premium'
    when sr_text_key_v1(value) ~ '(comfort|confort|comfortplus|comfort-plus)' then 'comfort'
    when sr_text_key_v1(value) ~ '(uberx|99pop|pop|econom|standard|padrao|popular|priority|prioridade|moto)' then 'popular'
    else 'unknown'
  end;
$$;

create or replace function public.sr_region_label_v1(value text)
returns text
language plpgsql
immutable
parallel safe
as $$
declare
  cleaned text := regexp_replace(coalesce(value,''), '[\n\r\t]+', ' ', 'g');
  dash_match text[];
  parts text[];
  n int;
  i int;
  candidate text;
  previous text;
  key text;
begin
  cleaned := trim(regexp_replace(cleaned, '\s+', ' ', 'g'));
  if length(cleaned) < 3 then return null; end if;

  dash_match := regexp_match(
    cleaned,
    '-\s*([^,\-]{3,55})\s*-\s*(?:s[aã]o|sao|sa0)\s*paulo(?:\s*-\s*sp)?',
    'i'
  );
  if dash_match is not null then
    candidate := trim(dash_match[1]);
    key := sr_text_key_v1(candidate);
    if key <> '' and key !~ '^(sp|brasil|sao-paulo)$' then
      return initcap(candidate);
    end if;
  end if;

  parts := regexp_split_to_array(cleaned, '\s*,\s*');
  n := coalesce(array_length(parts,1),0);
  if n = 0 then return null; end if;

  i := n;
  while i >= 1 loop
    candidate := trim(parts[i]);
    key := sr_text_key_v1(candidate);
    if candidate = ''
       or key in ('sp','brasil','brazil','sao-paulo','sao','paulo')
       or candidate ~* '^\d{5}-?\d{3}'
       or candidate ~* '^\d+[a-z]?$'
       or key ~ '^(como-foi-a-viagem|viagem-longa|entrada-principal|area-semi|semi-coberta)$'
    then
      i := i - 1;
      continue;
    end if;

    if candidate ~* '^(rua|r\.|avenida|av\.|alameda|al\.|estrada|rodovia|travessa|tv\.|praca|praça|largo)\b'
       or candidate ~ '[0-9]{2,}'
    then
      i := i - 1;
      continue;
    end if;

    previous := case when i > 1 then trim(parts[i-1]) else null end;
    if previous is not null
       and sr_text_key_v1(previous) in ('vila','jardim','parque','itaim','alto','santa','santo','bom','barra')
       and candidate !~* '^(sao|são|paulo)$'
    then
      candidate := previous || ' ' || candidate;
    end if;

    if length(candidate) between 3 and 55 then
      return initcap(candidate);
    end if;
    i := i - 1;
  end loop;

  if n = 1 and length(cleaned) between 3 and 55
     and cleaned !~* '^(rua|r\.|avenida|av\.|alameda|al\.|estrada|rodovia|travessa|tv\.)\b'
     and cleaned !~ '[0-9]{2,}'
  then
    return initcap(cleaned);
  end if;
  return null;
end;
$$;

create table if not exists public.sr_region_seed_v1 (
  region_key text not null,
  region_label text not null,
  weekday_iso smallint not null check (weekday_iso between 1 and 7),
  hour_bucket smallint not null check (hour_bucket in (0,3,6,9,12,15,18,21)),
  service_profile text not null check (service_profile in ('popular','comfort','premium','unknown')),
  sample_count integer not null check (sample_count >= 0),
  average_fare numeric,
  median_fare numeric,
  p25_fare numeric,
  p75_fare numeric,
  average_per_km numeric,
  median_per_km numeric,
  p25_per_km numeric,
  p75_per_km numeric,
  average_per_minute numeric,
  median_per_minute numeric,
  p25_per_minute numeric,
  p75_per_minute numeric,
  average_per_hour numeric,
  median_per_hour numeric,
  p25_per_hour numeric,
  p75_per_hour numeric,
  average_pickup_km numeric,
  average_pickup_minutes numeric,
  first_observed_at timestamptz,
  last_observed_at timestamptz,
  refreshed_at timestamptz not null default now(),
  primary key(region_key,weekday_iso,hour_bucket,service_profile)
);

create index if not exists sr_region_seed_v1_region_idx
  on public.sr_region_seed_v1(region_key,weekday_iso,hour_bucket,sample_count desc);
create index if not exists sr_region_seed_v1_time_idx
  on public.sr_region_seed_v1(weekday_iso,hour_bucket,sample_count desc);

alter table public.sr_region_seed_v1 enable row level security;
revoke all on table public.sr_region_seed_v1 from public,anon,authenticated;
grant all on table public.sr_region_seed_v1 to service_role;

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
    select
      sr_region_label_v1(r.normalized_payload->>'pickup_text') region_label,
      coalesce((r.normalized_payload->>'observed_at')::timestamptz,r.created_at) observed_at,
      greatest((r.normalized_payload->>'fare')::numeric,0) fare,
      nullif((r.normalized_payload->>'pickup_km')::numeric,0) pickup_km,
      nullif((r.normalized_payload->>'trip_km')::numeric,0) trip_km,
      nullif((r.normalized_payload->>'pickup_minutes')::numeric,0) pickup_minutes,
      nullif((r.normalized_payload->>'trip_minutes')::numeric,0) trip_minutes,
      sr_service_profile_v1(r.normalized_payload->>'service_type') service_profile
    from public.historical_import_rows r
    where r.validation_status='valid'
  ), metrics as (
    select *,coalesce(pickup_km,0)+coalesce(trip_km,0) total_km,
      coalesce(pickup_minutes,0)+coalesce(trip_minutes,0) total_minutes
    from source where region_label is not null and fare>0
  ), prepared as (
    select sr_text_key_v1(region_label) region_key,region_label,
      extract(isodow from observed_at at time zone 'America/Sao_Paulo')::smallint weekday_iso,
      (floor(extract(hour from observed_at at time zone 'America/Sao_Paulo')/3)*3)::smallint hour_bucket,
      service_profile,fare,
      case when total_km>0 then fare/total_km end per_km,
      case when total_minutes>0 then fare/total_minutes end per_minute,
      case when total_minutes>0 then fare/(total_minutes/60.0) end per_hour,
      pickup_km,pickup_minutes,observed_at
    from metrics where sr_text_key_v1(region_label)<>''
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
  from prepared group by region_key,weekday_iso,hour_bucket,service_profile;
  get diagnostics inserted_count=row_count;
  return inserted_count;
end;
$$;

revoke all on function public.sr_refresh_region_seed_v1() from public,anon,authenticated;
grant execute on function public.sr_refresh_region_seed_v1() to service_role;

create or replace view public.sr_collective_offer_region_hour_v1
with (security_invoker=false) as
select sr_text_key_v1(sr_region_label_v1(ro.pickup_label)) region_key,
  min(sr_region_label_v1(ro.pickup_label)) region_label,
  extract(isodow from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo'))::smallint weekday_iso,
  (floor(extract(hour from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo'))/3)*3)::smallint hour_bucket,
  sr_service_profile_v1(ro.service_type) service_profile,
  count(distinct ro.driver_id)::integer contributor_count,count(*)::integer sample_count,
  round(avg(ro.fare),2) average_fare,round(avg(ro.per_km),2) average_per_km,
  round(avg(ro.per_minute),2) average_per_minute,round(avg(ro.per_hour),2) average_per_hour,
  round(avg(ro.pickup_km),2) average_pickup_km,round(avg(ro.pickup_minutes),2) average_pickup_minutes
from public.ride_offers ro
join public.driver_preferences p on p.driver_id=ro.driver_id and p.collective_stats_opt_in=true
where ro.capture_method not like 'historical-import/%'
  and sr_region_label_v1(ro.pickup_label) is not null
group by sr_text_key_v1(sr_region_label_v1(ro.pickup_label)),
  extract(isodow from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo')),
  (floor(extract(hour from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo'))/3)*3),
  sr_service_profile_v1(ro.service_type)
having count(distinct ro.driver_id)>=3;

create or replace view public.sr_personal_offer_region_hour_v1
with (security_invoker=false) as
select ro.driver_id,sr_text_key_v1(sr_region_label_v1(ro.pickup_label)) region_key,
  min(sr_region_label_v1(ro.pickup_label)) region_label,
  extract(isodow from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo'))::smallint weekday_iso,
  (floor(extract(hour from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo'))/3)*3)::smallint hour_bucket,
  sr_service_profile_v1(ro.service_type) service_profile,count(*)::integer sample_count,
  round(avg(ro.fare),2) average_fare,round(avg(ro.per_km),2) average_per_km,
  round(avg(ro.per_minute),2) average_per_minute,round(avg(ro.per_hour),2) average_per_hour,
  round(avg(ro.pickup_km),2) average_pickup_km,round(avg(ro.pickup_minutes),2) average_pickup_minutes
from public.ride_offers ro
left join public.driver_preferences p on p.driver_id=ro.driver_id
where ro.capture_method not like 'historical-import/%'
  and sr_region_label_v1(ro.pickup_label) is not null
group by ro.driver_id,sr_text_key_v1(sr_region_label_v1(ro.pickup_label)),
  extract(isodow from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo')),
  (floor(extract(hour from ro.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo'))/3)*3),
  sr_service_profile_v1(ro.service_type);

create or replace view public.sr_collective_region_centroid_v1
with (security_invoker=false) as
select sr_text_key_v1(sr_region_label_v1(ro.pickup_label)) region_key,
  min(sr_region_label_v1(ro.pickup_label)) region_label,
  count(distinct ro.driver_id)::integer contributor_count,count(*)::integer sample_count,
  avg(ro.pickup_lat) lat,avg(ro.pickup_lng) lng
from public.ride_offers ro
join public.driver_preferences p on p.driver_id=ro.driver_id and p.collective_stats_opt_in=true
where ro.capture_method not like 'historical-import/%'
  and ro.pickup_lat is not null and ro.pickup_lng is not null
  and sr_region_label_v1(ro.pickup_label) is not null
group by sr_text_key_v1(sr_region_label_v1(ro.pickup_label))
having count(distinct ro.driver_id)>=3;

revoke all on table public.sr_collective_offer_region_hour_v1 from public,anon,authenticated;
revoke all on table public.sr_personal_offer_region_hour_v1 from public,anon,authenticated;
revoke all on table public.sr_collective_region_centroid_v1 from public,anon,authenticated;
grant select on table public.sr_collective_offer_region_hour_v1 to service_role;
grant select on table public.sr_personal_offer_region_hour_v1 to service_role;
grant select on table public.sr_collective_region_centroid_v1 to service_role;

comment on table public.sr_region_seed_v1 is
  'Baseline regional anonimizado do Sr. Rotas, derivado apenas de historical_import_rows validos. Nao contem driver_id, endereco exato, screenshot ou oferta individual.';

select public.sr_refresh_region_seed_v1();
commit;
