-- JÁ APLICADA EM PRODUÇÃO VIA MCP EM 28/09/2026.
-- Preserva lote histórico/V7 sem impedir a exclusão da conta criadora.

alter table public.historical_import_batches
  drop constraint if exists historical_import_batches_created_by_driver_id_fkey;

alter table public.historical_import_batches
  add constraint historical_import_batches_created_by_driver_id_fkey
  foreign key(created_by_driver_id)
  references public.drivers(id)
  on delete set null;
