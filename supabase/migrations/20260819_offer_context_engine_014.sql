begin;

alter table public.ride_offers
  add column if not exists local_offer_id text,
  add column if not exists pickup_label text,
  add column if not exists destination_label text,
  add column if not exists pickup_lat double precision,
  add column if not exists pickup_lng double precision,
  add column if not exists destination_lat double precision,
  add column if not exists destination_lng double precision,
  add column if not exists pickup_cell text,
  add column if not exists destination_cell text,
  add column if not exists estimated_arrival_at timestamptz,
  add column if not exists context_confidence double precision not null default 0,
  add column if not exists geocode_status text not null default 'unresolved',
  add column if not exists geocode_source text,
  add column if not exists context_version text not null default 'unknown',
  add column if not exists context_source_type text not null default 'live_ocr',
  add column if not exists context_time_source text not null default 'system_observed_at';

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'ride_offers_context_confidence_check'
  ) then
    alter table public.ride_offers
      add constraint ride_offers_context_confidence_check
      check (context_confidence >= 0 and context_confidence <= 1);
  end if;

  if not exists (
    select 1 from pg_constraint where conname = 'ride_offers_pickup_lat_check'
  ) then
    alter table public.ride_offers
      add constraint ride_offers_pickup_lat_check
      check (pickup_lat is null or (pickup_lat >= -90 and pickup_lat <= 90));
  end if;

  if not exists (
    select 1 from pg_constraint where conname = 'ride_offers_pickup_lng_check'
  ) then
    alter table public.ride_offers
      add constraint ride_offers_pickup_lng_check
      check (pickup_lng is null or (pickup_lng >= -180 and pickup_lng <= 180));
  end if;

  if not exists (
    select 1 from pg_constraint where conname = 'ride_offers_destination_lat_check'
  ) then
    alter table public.ride_offers
      add constraint ride_offers_destination_lat_check
      check (destination_lat is null or (destination_lat >= -90 and destination_lat <= 90));
  end if;

  if not exists (
    select 1 from pg_constraint where conname = 'ride_offers_destination_lng_check'
  ) then
    alter table public.ride_offers
      add constraint ride_offers_destination_lng_check
      check (destination_lng is null or (destination_lng >= -180 and destination_lng <= 180));
  end if;

  if not exists (
    select 1 from pg_constraint where conname = 'ride_offers_geocode_status_check'
  ) then
    alter table public.ride_offers
      add constraint ride_offers_geocode_status_check
      check (geocode_status in ('pending','resolved','partial','unresolved'));
  end if;
end $$;

create index if not exists ride_offers_destination_cell_time_idx
  on public.ride_offers(destination_cell, observed_at desc)
  where destination_cell is not null;

create index if not exists ride_offers_pickup_cell_time_idx
  on public.ride_offers(pickup_cell, observed_at desc)
  where pickup_cell is not null;

create index if not exists ride_offers_context_version_idx
  on public.ride_offers(context_version, observed_at desc);

create index if not exists ride_offers_local_offer_idx
  on public.ride_offers(device_id, local_offer_id)
  where local_offer_id is not null;

comment on column public.ride_offers.pickup_label is
  'Retirada extraida pelo Context Engine. Texto pessoal; nao usar como dado coletivo bruto.';
comment on column public.ride_offers.destination_label is
  'Destino extraido pelo Context Engine. Texto pessoal; nao usar como dado coletivo bruto.';
comment on column public.ride_offers.destination_cell is
  'Celula espacial versionada para agregacao estatistica futura.';
comment on column public.ride_offers.context_version is
  'Versao do Context Engine, independente do parser financeiro.';

commit;
