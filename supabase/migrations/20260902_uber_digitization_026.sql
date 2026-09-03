begin;
create table if not exists public.uber_session_imports (
 id uuid primary key default gen_random_uuid(), driver_id uuid not null references public.drivers(id) on delete cascade,
 source_key text not null, captured_at timestamptz not null default now(), started_at timestamptz, ended_at timestamptz,
 earnings numeric(12,2), completed_trips integer, offered_trips integer, confidence numeric(5,4) not null default 0,
 created_at timestamptz not null default now(), unique(driver_id,source_key),
 check (earnings is null or earnings >= 0), check (completed_trips is null or completed_trips >= 0), check (offered_trips is null or offered_trips >= 0), check (confidence between 0 and 1)
);
create table if not exists public.uber_completed_ride_imports (
 id uuid primary key default gen_random_uuid(), driver_id uuid not null references public.drivers(id) on delete cascade,
 device_id uuid references public.driver_devices(id) on delete set null, source_key text not null, captured_at timestamptz not null default now(), occurred_at timestamptz,
 fare numeric(12,2) not null, service_type text not null default 'unknown', pickup_label text, destination_label text,
 confidence numeric(5,4) not null default 0, matched_ride_offer_id bigint references public.ride_offers(id) on delete set null,
 created_at timestamptz not null default now(), unique(driver_id,source_key), check(fare>0), check(confidence between 0 and 1)
);
create index if not exists uber_session_imports_driver_captured_idx on public.uber_session_imports(driver_id,captured_at desc);
create index if not exists uber_completed_ride_imports_driver_time_idx on public.uber_completed_ride_imports(driver_id,occurred_at desc nulls last,captured_at desc);
alter table public.uber_session_imports enable row level security;
alter table public.uber_completed_ride_imports enable row level security;
revoke all on public.uber_session_imports from public,anon,authenticated;
revoke all on public.uber_completed_ride_imports from public,anon,authenticated;
grant all on public.uber_session_imports to service_role;
grant all on public.uber_completed_ride_imports to service_role;
comment on table public.uber_completed_ride_imports is 'Corridas que o motorista confirmou após OCR do histórico da Uber. Não são ofertas e não entram automaticamente nas médias de oferta.';
commit;
