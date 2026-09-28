-- JÁ APLICADA EM PRODUÇÃO VIA MCP EM 28/09/2026.
create index if not exists device_identity_registry_last_driver_id_idx on public.device_identity_registry(last_driver_id) where last_driver_id is not null;
create index if not exists device_identity_registry_trial_driver_id_idx on public.device_identity_registry(trial_driver_id) where trial_driver_id is not null;
