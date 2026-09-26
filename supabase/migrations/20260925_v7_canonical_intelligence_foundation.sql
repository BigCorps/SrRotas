-- Sr. Rotas — V7 canonical intelligence foundation
-- Canonical roadmap: 2026-09-25.4
-- Production state already applied/read-back verified on 2026-09-25/26.
--
-- Goals:
-- 1) V7.5 is the only historical source allowed to feed intelligence.
-- 2) Legacy batches remain archived/restorable, never silently mixed.
-- 3) Synthetic historical promotions are removed from ride_offers.
-- 4) Real operational ride_offers remain intact.
-- 5) Analytics gets one canonical contract: operational + V7.

begin;

-- Canonical V7 batch.
update public.historical_import_batches
set
  status='ready',
  processing_manifest=coalesce(processing_manifest,'{}'::jsonb)
    || jsonb_build_object(
      'canonical_for_intelligence',true,
      'canonicalization_reason','V7.5 canonical historical base'
    )
where id='48323962-cabd-497b-890f-8315e0d0753a'
  and schema_version='srrotas-historical-offer-v1'
  and extractor_version='V7.5';

-- Resolve ownership from the unique account that uploaded the batch.
update public.historical_import_batches b
set
  created_by_driver_id=d.id,
  created_by_auth_user_id=d.auth_user_id,
  processing_manifest=coalesce(b.processing_manifest,'{}'::jsonb)
    || jsonb_build_object('canonical_owner_resolved',true)
from public.drivers d
where b.id='48323962-cabd-497b-890f-8315e0d0753a'
  and b.created_by_driver_id is null
  and lower(d.email)=lower(b.created_by_email);

-- Preserve legacy batches for audit/rollback, but remove them from eligibility.
update public.historical_import_batches
set
  status='archived',
  processing_manifest=coalesce(processing_manifest,'{}'::jsonb)
    || jsonb_build_object(
      'archived_for_v7_canonicalization',true,
      'superseded_by_batch_id','48323962-cabd-497b-890f-8315e0d0753a'
    )
where schema_version='legacy'
  and status<>'archived';

-- V7-only seed refresh.
create or replace function public.sr_refresh_region_seed_v1()
returns integer
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
declare
  inserted_count integer;
begin
  delete from public.sr_region_seed_v1;

  with source as (
    select
      sr_region_label_v1(
        coalesce(
          nullif(r.normalized_payload->>'pickup_region_candidate',''),
          r.normalized_payload->>'pickup_text'
        )
      ) region_label,
      (r.normalized_payload->>'observed_at')::timestamptz observed_at,
      nullif((r.normalized_payload->>'fare')::numeric,0) fare,
      nullif((r.normalized_payload->>'pickup_km')::numeric,0) pickup_km,
      nullif((r.normalized_payload->>'trip_km')::numeric,0) trip_km,
      nullif((r.normalized_payload->>'pickup_minutes')::numeric,0) pickup_minutes,
      nullif((r.normalized_payload->>'trip_minutes')::numeric,0) trip_minutes,
      sr_service_profile_v1(r.normalized_payload->>'service_type') service_profile,
      r.quality_financial_ready financial_ready
    from public.historical_import_rows r
    join public.historical_import_batches b on b.id=r.batch_id
    where r.validation_status not in ('invalid','duplicate')
      and b.status='ready'
      and b.schema_version='srrotas-historical-offer-v1'
      and b.extractor_version='V7.5'
      and coalesce((b.processing_manifest->>'canonical_for_intelligence')::boolean,false)=true
      and r.schema_version='srrotas-historical-offer-v1'
      and r.extractor_version='V7.5'
      and r.quality_demand_temporal_ready=true
      and nullif(r.normalized_payload->>'observed_at','') is not null
  ),
  prepared as (
    select
      sr_text_key_v1(region_label) region_key,
      region_label,
      extract(isodow from observed_at at time zone 'America/Sao_Paulo')::smallint weekday_iso,
      (floor(extract(hour from observed_at at time zone 'America/Sao_Paulo')/3)*3)::smallint hour_bucket,
      service_profile,
      fare,
      pickup_km,
      pickup_minutes,
      observed_at,
      case
        when financial_ready and pickup_km>0 and trip_km>0
        then pickup_km+trip_km
      end total_km,
      case
        when financial_ready and pickup_minutes>0 and trip_minutes>0
        then pickup_minutes+trip_minutes
      end total_minutes
    from source
    where region_label is not null
      and sr_text_key_v1(region_label)<>''
  ),
  metrics as (
    select *,
      case when total_km>0 and fare>0 then fare/total_km end per_km,
      case when total_minutes>0 and fare>0 then fare/total_minutes end per_minute,
      case when total_minutes>0 and fare>0 then fare/(total_minutes/60.0) end per_hour
    from prepared
  )
  insert into public.sr_region_seed_v1(
    region_key,region_label,weekday_iso,hour_bucket,service_profile,sample_count,
    average_fare,median_fare,p25_fare,p75_fare,
    average_per_km,median_per_km,p25_per_km,p75_per_km,
    average_per_minute,median_per_minute,p25_per_minute,p75_per_minute,
    average_per_hour,median_per_hour,p25_per_hour,p75_per_hour,
    average_pickup_km,average_pickup_minutes,
    first_observed_at,last_observed_at,refreshed_at
  )
  select
    region_key,min(region_label),weekday_iso,hour_bucket,service_profile,count(*)::integer,
    round(avg(fare),2),
    round(percentile_cont(.5) within group(order by fare)::numeric,2),
    round(percentile_cont(.25) within group(order by fare)::numeric,2),
    round(percentile_cont(.75) within group(order by fare)::numeric,2),
    round(avg(per_km),2),
    round(percentile_cont(.5) within group(order by per_km)::numeric,2),
    round(percentile_cont(.25) within group(order by per_km)::numeric,2),
    round(percentile_cont(.75) within group(order by per_km)::numeric,2),
    round(avg(per_minute),2),
    round(percentile_cont(.5) within group(order by per_minute)::numeric,2),
    round(percentile_cont(.25) within group(order by per_minute)::numeric,2),
    round(percentile_cont(.75) within group(order by per_minute)::numeric,2),
    round(avg(per_hour),2),
    round(percentile_cont(.5) within group(order by per_hour)::numeric,2),
    round(percentile_cont(.25) within group(order by per_hour)::numeric,2),
    round(percentile_cont(.75) within group(order by per_hour)::numeric,2),
    round(avg(pickup_km),2),
    round(avg(pickup_minutes),2),
    min(observed_at),max(observed_at),now()
  from metrics
  group by region_key,weekday_iso,hour_bucket,service_profile;

  get diagnostics inserted_count=row_count;
  return inserted_count;
end;
$function$;

revoke all on function public.sr_refresh_region_seed_v1() from public,anon,authenticated;
grant execute on function public.sr_refresh_region_seed_v1() to service_role;

-- Personal regional intelligence: operational + canonical V7 only.
create or replace view public.sr_personal_offer_region_hour_v1
with (security_invoker=false)
as
with real_source as (
  select
    ro.driver_id,
    sr_region_canonical_label_v1(ro.pickup_label) as region_label,
    ro.observed_at,
    sr_service_profile_v1(ro.service_type) as service_profile,
    ro.fare,
    ro.per_km,
    ro.per_minute,
    ro.per_hour,
    ro.pickup_km,
    ro.pickup_minutes::numeric as pickup_minutes
  from public.ride_offers ro
  where coalesce(ro.capture_method,'') not like 'historical-import/%'
    and sr_region_canonical_label_v1(ro.pickup_label) is not null
),
v7_source as (
  select
    b.created_by_driver_id as driver_id,
    sr_region_canonical_label_v1(
      coalesce(
        nullif(r.normalized_payload->>'pickup_region_candidate',''),
        r.normalized_payload->>'pickup_text'
      )
    ) as region_label,
    (r.normalized_payload->>'observed_at')::timestamptz as observed_at,
    sr_service_profile_v1(r.normalized_payload->>'service_type') as service_profile,
    nullif((r.normalized_payload->>'fare')::numeric,0) as fare,
    case
      when r.quality_financial_ready
       and nullif((r.normalized_payload->>'fare')::numeric,0)>0
       and nullif((r.normalized_payload->>'pickup_km')::numeric,0)>0
       and nullif((r.normalized_payload->>'trip_km')::numeric,0)>0
      then
        (r.normalized_payload->>'fare')::numeric /
        ((r.normalized_payload->>'pickup_km')::numeric +
         (r.normalized_payload->>'trip_km')::numeric)
    end as per_km,
    case
      when r.quality_financial_ready
       and nullif((r.normalized_payload->>'fare')::numeric,0)>0
       and nullif((r.normalized_payload->>'pickup_minutes')::numeric,0)>0
       and nullif((r.normalized_payload->>'trip_minutes')::numeric,0)>0
      then
        (r.normalized_payload->>'fare')::numeric /
        ((r.normalized_payload->>'pickup_minutes')::numeric +
         (r.normalized_payload->>'trip_minutes')::numeric)
    end as per_minute,
    case
      when r.quality_financial_ready
       and nullif((r.normalized_payload->>'fare')::numeric,0)>0
       and nullif((r.normalized_payload->>'pickup_minutes')::numeric,0)>0
       and nullif((r.normalized_payload->>'trip_minutes')::numeric,0)>0
      then
        (r.normalized_payload->>'fare')::numeric /
        (((r.normalized_payload->>'pickup_minutes')::numeric +
          (r.normalized_payload->>'trip_minutes')::numeric)/60.0)
    end as per_hour,
    nullif((r.normalized_payload->>'pickup_km')::numeric,0) as pickup_km,
    nullif((r.normalized_payload->>'pickup_minutes')::numeric,0) as pickup_minutes
  from public.historical_import_rows r
  join public.historical_import_batches b on b.id=r.batch_id
  where b.status='ready'
    and b.schema_version='srrotas-historical-offer-v1'
    and b.extractor_version='V7.5'
    and coalesce((b.processing_manifest->>'canonical_for_intelligence')::boolean,false)=true
    and b.created_by_driver_id is not null
    and r.schema_version='srrotas-historical-offer-v1'
    and r.extractor_version='V7.5'
    and r.validation_status not in ('invalid','duplicate')
    and r.quality_demand_temporal_ready=true
    and nullif(r.normalized_payload->>'observed_at','') is not null
    and sr_region_canonical_label_v1(
      coalesce(
        nullif(r.normalized_payload->>'pickup_region_candidate',''),
        r.normalized_payload->>'pickup_text'
      )
    ) is not null
),
source as (
  select * from real_source
  union all
  select * from v7_source
)
select
  s.driver_id,
  sr_region_key_v1(s.region_label) as region_key,
  min(s.region_label) as region_label,
  extract(isodow from (s.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo')))::smallint as weekday_iso,
  (floor(extract(hour from (s.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo')))/3)*3)::smallint as hour_bucket,
  s.service_profile,
  count(*)::integer as sample_count,
  round(avg(s.fare),2) as average_fare,
  round(avg(s.per_km),2) as average_per_km,
  round(avg(s.per_minute),2) as average_per_minute,
  round(avg(s.per_hour),2) as average_per_hour,
  round(avg(s.pickup_km),2) as average_pickup_km,
  round(avg(s.pickup_minutes),2) as average_pickup_minutes
from source s
left join public.driver_preferences p on p.driver_id=s.driver_id
where s.driver_id is not null
  and s.region_label is not null
  and sr_region_key_v1(s.region_label)<>''
group by
  s.driver_id,
  sr_region_key_v1(s.region_label),
  extract(isodow from (s.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo'))),
  floor(extract(hour from (s.observed_at at time zone coalesce(p.timezone,'America/Sao_Paulo')))/3)*3,
  s.service_profile;

revoke all on public.sr_personal_offer_region_hour_v1 from public,anon,authenticated;
grant select on public.sr_personal_offer_region_hour_v1 to service_role;

-- Canonical analytical contract: real operational offers + canonical V7.
create or replace view public.sr_personal_offer_canonical_v1
with (security_invoker=false)
as
with real_offers as (
  select
    ro.driver_id,
    ('real:' || ro.id::text) as id,
    ro.journey_id,
    ro.observed_at,
    ro.platform,
    ro.fare,
    ro.pickup_km,
    ro.trip_km,
    ro.total_km,
    ro.total_minutes,
    ro.per_km,
    ro.per_hour,
    ro.per_minute,
    ro.estimated_cost,
    ro.estimated_profit,
    ro.profit_per_hour,
    ro.profit_percent,
    ro.passenger_rating,
    ro.advertised_per_km,
    ro.service_type,
    ro.verdict,
    ro.capture_method,
    ro.confidence,
    ro.offer_type,
    ro.pickup_label,
    ro.destination_label,
    ro.pickup_lat,
    ro.pickup_lng,
    ro.destination_lat,
    ro.destination_lng,
    ro.pickup_cell,
    ro.destination_cell,
    ro.estimated_arrival_at,
    ro.context_confidence,
    ro.geocode_status,
    ro.geocode_source,
    ro.context_version,
    ro.context_source_type,
    ro.context_time_source,
    'operational'::text as data_source
  from public.ride_offers ro
  where coalesce(ro.capture_method,'') not like 'historical-import/%'
),
v7_offers as (
  select
    b.created_by_driver_id as driver_id,
    ('v7:' || r.record_id) as id,
    null::uuid as journey_id,
    (r.normalized_payload->>'observed_at')::timestamptz as observed_at,
    coalesce(nullif(r.normalized_payload->>'platform',''),'unknown') as platform,
    nullif((r.normalized_payload->>'fare')::numeric,0) as fare,
    nullif((r.normalized_payload->>'pickup_km')::numeric,0) as pickup_km,
    nullif((r.normalized_payload->>'trip_km')::numeric,0) as trip_km,
    case
      when nullif((r.normalized_payload->>'pickup_km')::numeric,0)>0
       and nullif((r.normalized_payload->>'trip_km')::numeric,0)>0
      then
        (r.normalized_payload->>'pickup_km')::numeric +
        (r.normalized_payload->>'trip_km')::numeric
    end as total_km,
    case
      when nullif((r.normalized_payload->>'pickup_minutes')::numeric,0)>0
       and nullif((r.normalized_payload->>'trip_minutes')::numeric,0)>0
      then
        ((r.normalized_payload->>'pickup_minutes')::numeric +
         (r.normalized_payload->>'trip_minutes')::numeric)::integer
    end as total_minutes,
    case
      when r.quality_financial_ready
       and nullif((r.normalized_payload->>'fare')::numeric,0)>0
       and nullif((r.normalized_payload->>'pickup_km')::numeric,0)>0
       and nullif((r.normalized_payload->>'trip_km')::numeric,0)>0
      then
        (r.normalized_payload->>'fare')::numeric /
        ((r.normalized_payload->>'pickup_km')::numeric +
         (r.normalized_payload->>'trip_km')::numeric)
    end as per_km,
    case
      when r.quality_financial_ready
       and nullif((r.normalized_payload->>'fare')::numeric,0)>0
       and nullif((r.normalized_payload->>'pickup_minutes')::numeric,0)>0
       and nullif((r.normalized_payload->>'trip_minutes')::numeric,0)>0
      then
        (r.normalized_payload->>'fare')::numeric /
        (((r.normalized_payload->>'pickup_minutes')::numeric +
          (r.normalized_payload->>'trip_minutes')::numeric)/60.0)
    end as per_hour,
    case
      when r.quality_financial_ready
       and nullif((r.normalized_payload->>'fare')::numeric,0)>0
       and nullif((r.normalized_payload->>'pickup_minutes')::numeric,0)>0
       and nullif((r.normalized_payload->>'trip_minutes')::numeric,0)>0
      then
        (r.normalized_payload->>'fare')::numeric /
        ((r.normalized_payload->>'pickup_minutes')::numeric +
         (r.normalized_payload->>'trip_minutes')::numeric)
    end as per_minute,
    null::numeric as estimated_cost,
    null::numeric as estimated_profit,
    null::numeric as profit_per_hour,
    null::numeric as profit_percent,
    nullif((r.normalized_payload->>'passenger_rating')::numeric,0) as passenger_rating,
    nullif((r.normalized_payload->>'advertised_per_km')::numeric,0) as advertised_per_km,
    coalesce(nullif(r.normalized_payload->>'service_type',''),'unknown') as service_type,
    'historical_unclassified'::text as verdict,
    'historical-v7'::text as capture_method,
    nullif((r.normalized_payload->>'ocr_confidence')::numeric,0) as confidence,
    coalesce(nullif(r.normalized_payload->>'offer_type',''),'unknown') as offer_type,
    nullif(r.normalized_payload->>'pickup_text','') as pickup_label,
    nullif(r.normalized_payload->>'destination_text','') as destination_label,
    null::double precision as pickup_lat,
    null::double precision as pickup_lng,
    null::double precision as destination_lat,
    null::double precision as destination_lng,
    null::text as pickup_cell,
    null::text as destination_cell,
    case
      when nullif(r.normalized_payload->>'total_minutes','') is not null
      then
        (r.normalized_payload->>'observed_at')::timestamptz +
        make_interval(mins=>(r.normalized_payload->>'total_minutes')::integer)
    end as estimated_arrival_at,
    null::double precision as context_confidence,
    'historical_text'::text as geocode_status,
    'v7_text'::text as geocode_source,
    'srrotas-historical-offer-v1'::text as context_version,
    'historical_v7'::text as context_source_type,
    coalesce(nullif(r.normalized_payload->>'time_source',''),'unknown') as context_time_source,
    'historical_v7'::text as data_source
  from public.historical_import_rows r
  join public.historical_import_batches b on b.id=r.batch_id
  where b.status='ready'
    and b.schema_version='srrotas-historical-offer-v1'
    and b.extractor_version='V7.5'
    and coalesce((b.processing_manifest->>'canonical_for_intelligence')::boolean,false)=true
    and b.created_by_driver_id is not null
    and r.schema_version='srrotas-historical-offer-v1'
    and r.extractor_version='V7.5'
    and r.validation_status not in ('invalid','duplicate')
    and r.quality_demand_temporal_ready=true
    and nullif(r.normalized_payload->>'observed_at','') is not null
)
select * from real_offers
union all
select * from v7_offers;

revoke all on public.sr_personal_offer_canonical_v1 from public,anon,authenticated;
grant select on public.sr_personal_offer_canonical_v1 to service_role;

-- Remove only the old synthetic promotion. Real captures are not touched.
delete from public.ride_offers
where capture_method='historical-import/jsonl'
  and parser_version='historical-import-promotion-v1'
  and source_package='historical_import'
  and device_id='7a602ddb-76b2-4df9-ab2a-03e15e2272bf';

select public.sr_refresh_region_seed_v1();

commit;
