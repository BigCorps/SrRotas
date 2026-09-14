begin;

-- Sr. Rotas 0.27.0-RC3.3
-- Contrato V7.1 para reprocessamento histórico, comparação de lotes e métricas de qualidade.
-- Esta migration NÃO apaga lotes antigos e NÃO executa o refresh do seed automaticamente.

alter table public.historical_import_batches
  add column if not exists schema_version text not null default 'legacy',
  add column if not exists extractor_version text,
  add column if not exists supersedes_batch_id uuid references public.historical_import_batches(id) on delete set null,
  add column if not exists processing_manifest jsonb not null default '{}'::jsonb,
  add column if not exists quality_summary jsonb not null default '{}'::jsonb,
  add column if not exists demand_temporal_ready_count integer not null default 0 check (demand_temporal_ready_count >= 0),
  add column if not exists route_flow_ready_count integer not null default 0 check (route_flow_ready_count >= 0),
  add column if not exists financial_ready_count integer not null default 0 check (financial_ready_count >= 0),
  add column if not exists fully_ready_count integer not null default 0 check (fully_ready_count >= 0);

alter table public.historical_import_rows
  add column if not exists schema_version text not null default 'legacy',
  add column if not exists extractor_version text,
  add column if not exists record_id text,
  add column if not exists offer_index integer,
  add column if not exists quality_flags jsonb not null default '[]'::jsonb,
  add column if not exists quality_demand_temporal_ready boolean not null default false,
  add column if not exists quality_route_flow_ready boolean not null default false,
  add column if not exists quality_financial_ready boolean not null default false,
  add column if not exists quality_fully_ready boolean not null default false;

do $$ begin
  alter table public.historical_import_rows
    add constraint historical_import_rows_record_id_sha_check
    check (record_id is null or record_id ~ '^[a-f0-9]{64}$');
exception when duplicate_object then null; end $$;

do $$ begin
  alter table public.historical_import_rows
    add constraint historical_import_rows_offer_index_check
    check (offer_index is null or offer_index >= 0);
exception when duplicate_object then null; end $$;

create index if not exists historical_import_batches_supersedes_idx
  on public.historical_import_batches(supersedes_batch_id)
  where supersedes_batch_id is not null;

-- Não é UNIQUE de propósito: durante comparação V7 antigo x V7.1 as duas versões coexistem.
create index if not exists historical_import_rows_record_id_idx
  on public.historical_import_rows(record_id)
  where record_id is not null;

create index if not exists historical_import_rows_source_offer_idx
  on public.historical_import_rows(source_file_sha256, offer_index)
  where source_file_sha256 is not null and offer_index is not null;

create index if not exists historical_import_rows_quality_temporal_idx
  on public.historical_import_rows(batch_id, quality_demand_temporal_ready, row_index);
create index if not exists historical_import_rows_quality_route_idx
  on public.historical_import_rows(batch_id, quality_route_flow_ready, row_index);
create index if not exists historical_import_rows_quality_financial_idx
  on public.historical_import_rows(batch_id, quality_financial_ready, row_index);

-- Resumo auditável usado pelo Admin. O backend recalcula as flags; não confia apenas
-- nos booleanos enviados pelo extrator/GPT.
create or replace function public.sr_historical_import_quality_summary_v1(target_batch uuid)
returns jsonb
language sql
stable
security definer
set search_path=public,pg_temp
as $$
  with base as (
    select *
    from public.historical_import_rows
    where batch_id = target_batch
  ), status_counts as (
    select
      count(*)::integer total,
      count(*) filter (where validation_status='valid')::integer valid,
      count(*) filter (where validation_status='partial')::integer partial,
      count(*) filter (where validation_status='invalid')::integer invalid,
      count(*) filter (where validation_status='duplicate')::integer duplicate,
      count(*) filter (where validation_status not in ('invalid','duplicate') and quality_demand_temporal_ready)::integer demand_temporal_ready,
      count(*) filter (where validation_status not in ('invalid','duplicate') and quality_route_flow_ready)::integer route_flow_ready,
      count(*) filter (where validation_status not in ('invalid','duplicate') and quality_financial_ready)::integer financial_ready,
      count(*) filter (where validation_status not in ('invalid','duplicate') and quality_fully_ready)::integer fully_ready
    from base
  ), flag_counts as (
    select coalesce(jsonb_object_agg(flag, n order by n desc), '{}'::jsonb) value
    from (
      select flag, count(*)::integer n
      from base b
      cross join lateral jsonb_array_elements_text(
        case when jsonb_typeof(b.quality_flags)='array' then b.quality_flags else '[]'::jsonb end
      ) as extracted(flag)
      group by flag
    ) q
  ), platform_counts as (
    select coalesce(jsonb_object_agg(platform, n order by n desc), '{}'::jsonb) value
    from (
      select coalesce(nullif(lower(normalized_payload->>'platform'),''),'unknown') platform, count(*)::integer n
      from base
      group by 1
    ) q
  ), month_counts as (
    select coalesce(jsonb_object_agg(month_key, n order by month_key), '{}'::jsonb) value
    from (
      select to_char((normalized_payload->>'observed_at')::timestamptz at time zone 'America/Sao_Paulo','YYYY-MM') month_key,
             count(*)::integer n
      from base
      where normalized_payload ? 'observed_at'
        and nullif(normalized_payload->>'observed_at','') is not null
        and (normalized_payload->>'observed_at') ~ '^\\d{4}-\\d{2}-\\d{2}T'
      group by 1
    ) q
    where month_key is not null
  )
  select jsonb_build_object(
    'schema','sr-historical-import-quality-v1',
    'batch_id',target_batch,
    'total',s.total,
    'valid',s.valid,
    'partial',s.partial,
    'invalid',s.invalid,
    'duplicate',s.duplicate,
    'demand_temporal_ready',s.demand_temporal_ready,
    'route_flow_ready',s.route_flow_ready,
    'financial_ready',s.financial_ready,
    'fully_ready',s.fully_ready,
    'quality_flags',f.value,
    'platforms',p.value,
    'months',m.value
  )
  from status_counts s
  cross join flag_counts f
  cross join platform_counts p
  cross join month_counts m;
$$;

revoke all on function public.sr_historical_import_quality_summary_v1(uuid) from public,anon,authenticated;
grant execute on function public.sr_historical_import_quality_summary_v1(uuid) to service_role;

-- Filtro mais rígido para impedir textos de interface de virarem nomes de região.
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
    if key <> ''
       and key !~ '^(sp|brasil|sao-paulo|area|regiao|destino|origem|retirada|embarque|buscar|aceitar|escolher)$'
       and key !~ '^desloque-se(-|$)'
    then
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
       or key in ('sp','brasil','brazil','sao-paulo','sao','paulo','area','regiao','destino','origem','retirada','embarque','buscar','aceitar','escolher')
       or candidate ~* '^\d{5}-?\d{3}'
       or candidate ~* '^\d+[a-z]?$'
       or key ~ '^(como-foi-a-viagem|viagem-longa|entrada-principal|area-semi|semi-coberta)$'
       or key ~ '^desloque-se(-|$)'
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

  key := sr_text_key_v1(cleaned);
  if n = 1 and length(cleaned) between 3 and 55
     and cleaned !~* '^(rua|r\.|avenida|av\.|alameda|al\.|estrada|rodovia|travessa|tv\.)\b'
     and cleaned !~ '[0-9]{2,}'
     and key !~ '^(area|regiao|destino|origem|retirada|embarque|buscar|aceitar|escolher)$'
     and key !~ '^desloque-se(-|$)'
  then
    return initcap(cleaned);
  end if;
  return null;
end;
$$;

-- O seed histórico passa a separar elegibilidade temporal da elegibilidade financeira.
-- Linhas parciais podem contribuir para frequência/tempo quando temporal_ready=true,
-- mas NUNCA entram em R$/km, R$/min ou R$/h sem as duas pernas completas.
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
      sr_region_label_v1(coalesce(nullif(r.normalized_payload->>'pickup_region_candidate',''), r.normalized_payload->>'pickup_text')) region_label,
      (r.normalized_payload->>'observed_at')::timestamptz observed_at,
      nullif((r.normalized_payload->>'fare')::numeric,0) fare,
      nullif((r.normalized_payload->>'pickup_km')::numeric,0) pickup_km,
      nullif((r.normalized_payload->>'trip_km')::numeric,0) trip_km,
      nullif((r.normalized_payload->>'pickup_minutes')::numeric,0) pickup_minutes,
      nullif((r.normalized_payload->>'trip_minutes')::numeric,0) trip_minutes,
      sr_service_profile_v1(r.normalized_payload->>'service_type') service_profile,
      case
        when r.schema_version='srrotas-historical-offer-v1' then r.quality_financial_ready
        else
          nullif((r.normalized_payload->>'fare')::numeric,0) > 0
          and nullif((r.normalized_payload->>'pickup_km')::numeric,0) > 0
          and nullif((r.normalized_payload->>'trip_km')::numeric,0) > 0
          and nullif((r.normalized_payload->>'pickup_minutes')::numeric,0) > 0
          and nullif((r.normalized_payload->>'trip_minutes')::numeric,0) > 0
      end financial_ready
    from public.historical_import_rows r
    join public.historical_import_batches b on b.id=r.batch_id
    where r.validation_status not in ('invalid','duplicate')
      and b.status <> 'archived'
      and nullif(r.normalized_payload->>'observed_at','') is not null
      and (
        (r.schema_version='srrotas-historical-offer-v1' and r.quality_demand_temporal_ready=true)
        or
        (r.schema_version<>'srrotas-historical-offer-v1' and sr_region_label_v1(coalesce(nullif(r.normalized_payload->>'pickup_region_candidate',''), r.normalized_payload->>'pickup_text')) is not null)
      )
  ), prepared as (
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
      case when financial_ready and pickup_km>0 and trip_km>0
        then pickup_km + trip_km end total_km,
      case when financial_ready and pickup_minutes>0 and trip_minutes>0
        then pickup_minutes + trip_minutes end total_minutes
    from source
    where region_label is not null
      and sr_text_key_v1(region_label)<>''
  ), metrics as (
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
    average_pickup_km,average_pickup_minutes,first_observed_at,last_observed_at,refreshed_at
  )
  select
    region_key,min(region_label),weekday_iso,hour_bucket,service_profile,count(*)::integer,
    round(avg(fare),2),round(percentile_cont(.5) within group(order by fare)::numeric,2),
    round(percentile_cont(.25) within group(order by fare)::numeric,2),round(percentile_cont(.75) within group(order by fare)::numeric,2),
    round(avg(per_km),2),round(percentile_cont(.5) within group(order by per_km)::numeric,2),
    round(percentile_cont(.25) within group(order by per_km)::numeric,2),round(percentile_cont(.75) within group(order by per_km)::numeric,2),
    round(avg(per_minute),2),round(percentile_cont(.5) within group(order by per_minute)::numeric,2),
    round(percentile_cont(.25) within group(order by per_minute)::numeric,2),round(percentile_cont(.75) within group(order by per_minute)::numeric,2),
    round(avg(per_hour),2),round(percentile_cont(.5) within group(order by per_hour)::numeric,2),
    round(percentile_cont(.25) within group(order by per_hour)::numeric,2),round(percentile_cont(.75) within group(order by per_hour)::numeric,2),
    round(avg(pickup_km),2),round(avg(pickup_minutes),2),min(observed_at),max(observed_at),now()
  from metrics
  group by region_key,weekday_iso,hour_bucket,service_profile;

  get diagnostics inserted_count=row_count;
  return inserted_count;
end;
$$;

revoke all on function public.sr_refresh_region_seed_v1() from public,anon,authenticated;
grant execute on function public.sr_refresh_region_seed_v1() to service_role;

commit;
