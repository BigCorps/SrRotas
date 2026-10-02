begin;

create table if not exists public.sr_radar_pois (
  id uuid primary key default gen_random_uuid(),
  canonical_name text not null,
  normalized_name text not null,
  poi_type text not null,
  address text,
  city text,
  state text,
  country_code text,
  lat double precision not null,
  lng double precision not null,
  geo_cell text not null,
  region_key text,
  region_label text,
  source text not null,
  source_external_id text,
  source_url text,
  source_confidence numeric(5,4) not null default 0.5000,
  status text not null default 'active',
  aliases jsonb not null default '[]'::jsonb,
  metadata jsonb not null default '{}'::jsonb,
  last_verified_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (lat between -90 and 90),
  check (lng between -180 and 180),
  check (source_confidence between 0 and 1),
  check (status in ('active','inactive')),
  check (geo_cell ~ '^g2:-?[0-9]+:-?[0-9]+$')
);

create unique index if not exists sr_radar_pois_source_external_uidx
  on public.sr_radar_pois(source, source_external_id)
  where source_external_id is not null;
create index if not exists sr_radar_pois_cell_idx on public.sr_radar_pois(geo_cell) where status='active';
create index if not exists sr_radar_pois_region_idx on public.sr_radar_pois(region_key) where region_key is not null and status='active';
create index if not exists sr_radar_pois_location_idx on public.sr_radar_pois(lat,lng);

alter table public.sr_event_opportunities
  add column if not exists poi_id uuid references public.sr_radar_pois(id) on delete set null;
create index if not exists sr_event_opportunities_poi_idx
  on public.sr_event_opportunities(poi_id, egress_start_at)
  where poi_id is not null and status='active';

alter table public.sr_radar_pois enable row level security;
revoke all on table public.sr_radar_pois from public,anon,authenticated;
grant select,insert,update,delete on table public.sr_radar_pois to service_role;

comment on table public.sr_radar_pois is
 'POIs permanentes do Radar. Contextos temporais permanecem em sr_event_opportunities.';
comment on column public.sr_radar_pois.geo_cell is
 'Célula g2 idêntica a OfferContextEngine.geoCell: floor(lat*100), floor(lng*100).';
comment on column public.sr_radar_pois.region_key is
 'Ponte opcional para agregados regionais/V7. Não implica precisão de POI.';
commit;
