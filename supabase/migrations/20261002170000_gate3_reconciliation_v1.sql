begin;

create table if not exists public.sr_ride_reconciliations (
  id uuid primary key default gen_random_uuid(),
  driver_id uuid not null references public.drivers(id) on delete cascade,
  import_id uuid not null references public.uber_completed_ride_imports(id) on delete cascade,
  decision text not null,
  selected_ride_offer_id bigint references public.ride_offers(id) on delete set null,
  candidate_score numeric(6,2),
  candidate_version text not null default 'journey-reconciliation-v1',
  decided_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (driver_id, import_id),
  check (decision in ('confirmed','no_match')),
  check (
    (decision='confirmed' and selected_ride_offer_id is not null)
    or (decision='no_match' and selected_ride_offer_id is null)
  ),
  check (candidate_score is null or candidate_score between 0 and 100)
);

create index if not exists sr_ride_reconciliations_driver_decision_idx
  on public.sr_ride_reconciliations(driver_id, decision, decided_at desc);
create index if not exists sr_ride_reconciliations_import_idx
  on public.sr_ride_reconciliations(import_id);
create index if not exists sr_ride_reconciliations_selected_offer_idx
  on public.sr_ride_reconciliations(selected_ride_offer_id)
  where selected_ride_offer_id is not null;

alter table public.sr_ride_reconciliations enable row level security;
revoke all on table public.sr_ride_reconciliations from public, anon, authenticated;
grant select, insert, update, delete on table public.sr_ride_reconciliations to service_role;

comment on table public.sr_ride_reconciliations is
  'Decisão humana que reconcilia uma corrida importada com uma oferta operacional. O ranker apenas sugere candidatos.';

-- A digitalização 0.26 podia promover uma correspondência exata diretamente para
-- ride_outcomes. No Gate 3 isso vira somente pista de conciliação. O trigger
-- mantém compatibilidade com o cliente antigo sem deixar novas confirmações automáticas.
create or replace function public.sr_block_legacy_uber_history_outcome_v1()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.source = 'uber_history_ocr' then
    return null;
  end if;
  return new;
end;
$$;

revoke all on function public.sr_block_legacy_uber_history_outcome_v1() from public, anon, authenticated;
grant execute on function public.sr_block_legacy_uber_history_outcome_v1() to service_role;

drop trigger if exists trg_sr_block_legacy_uber_history_outcome_v1 on public.ride_outcomes;
create trigger trg_sr_block_legacy_uber_history_outcome_v1
before insert or update on public.ride_outcomes
for each row execute function public.sr_block_legacy_uber_history_outcome_v1();

commit;
