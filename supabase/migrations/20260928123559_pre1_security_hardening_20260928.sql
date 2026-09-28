-- Sr. Rotas — pré-1.0 hardening.
-- Aplicada em produção via Supabase MCP em 28/09/2026.
-- Arquitetura atual: clientes não acessam public diretamente; backend/Edge usam service_role.

alter function public.sr_text_key_v1(text) set search_path = pg_catalog, public;
alter function public.sr_service_profile_v1(text) set search_path = pg_catalog, public;
alter function public.sr_region_label_v1(text) set search_path = pg_catalog, public;
alter function public.sr_region_canonical_label_v1(text) set search_path = pg_catalog, public;
alter function public.sr_region_key_v1(text) set search_path = pg_catalog, public;

revoke all privileges on all tables in schema public from public, anon, authenticated;
revoke all privileges on all sequences in schema public from public, anon, authenticated;
revoke all privileges on all functions in schema public from public, anon, authenticated;
grant execute on all functions in schema public to service_role;

alter default privileges for role postgres in schema public revoke all privileges on tables from anon, authenticated;
alter default privileges for role postgres in schema public revoke all privileges on sequences from anon, authenticated;
alter default privileges for role postgres in schema public revoke execute on functions from public, anon, authenticated;
alter default privileges for role postgres in schema public grant all privileges on tables to service_role;
alter default privileges for role postgres in schema public grant all privileges on sequences to service_role;
alter default privileges for role postgres in schema public grant execute on functions to service_role;

create index if not exists entitlements_source_subscription_id_idx on public.entitlements(source_subscription_id) where source_subscription_id is not null;
create index if not exists journey_energy_entries_device_id_idx on public.journey_energy_entries(device_id) where device_id is not null;
create index if not exists journey_state_events_device_id_idx on public.journey_state_events(device_id) where device_id is not null;
create index if not exists payments_subscription_id_idx on public.payments(subscription_id) where subscription_id is not null;
create index if not exists ride_outcomes_device_id_idx on public.ride_outcomes(device_id) where device_id is not null;
create index if not exists uber_completed_ride_imports_device_id_idx on public.uber_completed_ride_imports(device_id) where device_id is not null;
create index if not exists uber_completed_ride_imports_matched_offer_idx on public.uber_completed_ride_imports(matched_ride_offer_id) where matched_ride_offer_id is not null;
create index if not exists uber_session_imports_journey_id_idx on public.uber_session_imports(journey_id) where journey_id is not null;
create index if not exists web_handoff_tokens_driver_id_idx on public.web_handoff_tokens(driver_id);
create index if not exists zone_exposures_device_id_idx on public.zone_exposures(device_id) where device_id is not null;
