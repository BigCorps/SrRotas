-- REVIEW ONLY / DO NOT APPLY. Contract only; NOT a migration or tested database procedure.
-- Proposed future human-review ledger ONLY (one INSERT, no outcome or payout mutation).
-- NOT placed in supabase/migrations intentionally: financial/security cross-review
-- and explicit authorization are mandatory before any DB change.
-- Never interpret candidate scores or legacy hints as human approval.
-- Browser-provided score is never trusted; candidate_score stays NULL.
-- SECURITY INVOKER, EXECUTE denied to public clients; server service_role only.
-- Requires PostgreSQL/Supabase compatibility, dry-run, authenticated QA and
-- concurrency tests in a separate authorized validation environment.

begin;

-- Refuse two human-confirmed imports pointing to the same offer.
-- Current live database has 0 human decisions, but 7 offer IDs are repeated
-- across unreviewed legacy hints. The latter MUST NOT be auto-migrated.
create unique index if not exists sr_ride_reconciliation_unique_confirmed_offer_v2
  on public.sr_ride_reconciliations (driver_id, selected_ride_offer_id)
  where decision = 'confirmed' and selected_ride_offer_id is not null;

create or replace function public.sr_apply_reconciliation_atomic_v2(
  p_driver_id uuid,
  p_import_id uuid,
  p_decision text,
  p_offer_id bigint default null
) returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_import public.uber_completed_ride_imports%rowtype;
  v_offer public.ride_offers%rowtype;
begin
  if p_driver_id is null or p_import_id is null then
    raise exception using message = 'reconciliation_actor_or_import_required';
  end if;
  if p_decision not in ('confirmed', 'no_match') or p_decision is null then
    raise exception using message = 'reconciliation_decision_invalid';
  end if;
  if (p_decision = 'no_match' and p_offer_id is not null) or
     (p_decision = 'confirmed' and p_offer_id is null) then
    raise exception using message = 'reconciliation_candidate_invalid';
  end if;

  -- Lock the import to serialize two decisions for the same import.
  select * into v_import
    from public.uber_completed_ride_imports
   where id = p_import_id and driver_id = p_driver_id
   for update;
  if not found then
    raise exception using message = 'reconciliation_import_not_found';
  end if;
  if v_import.ride_status <> 'completed' then
    raise exception using message = 'reconciliation_only_completed_rides';
  end if;

  if exists (select 1 from public.sr_ride_reconciliations
             where driver_id = p_driver_id and import_id = p_import_id) then
    raise exception using message = 'reconciliation_already_reviewed';
  end if;

  if p_decision = 'confirmed' then
    -- A missing timestamp must not be substituted with captured_at.
    if v_import.occurred_at is null or v_import.occurred_at > pg_catalog.now() then
      raise exception using message = 'reconciliation_import_time_unverified';
    end if;
    select * into v_offer
      from public.ride_offers
     where id = p_offer_id and driver_id = p_driver_id
     for update;
    if not found then
      raise exception using message = 'reconciliation_offer_not_found';
    end if;
    if v_offer.journey_id is null or v_offer.local_offer_id is null
       or not exists (select 1 from public.driver_journeys
                      where id = v_offer.journey_id and driver_id = p_driver_id) then
      raise exception using message = 'reconciliation_offer_without_owned_journey';
    end if;
    if v_offer.observed_at > pg_catalog.now() then
      raise exception using message = 'reconciliation_offer_time_unverified';
    end if;
    if exists (select 1 from public.sr_ride_reconciliations
               where driver_id = p_driver_id
                 and selected_ride_offer_id = p_offer_id
                 and decision = 'confirmed') then
      raise exception using message = 'reconciliation_offer_already_used';
    end if;
  end if;

  -- ONLY database mutation: the decision ledger. No changes to outcomes,
  -- imported original rows, financial figures, OCR hints, or V7.
  insert into public.sr_ride_reconciliations
    (driver_id, import_id, decision, selected_ride_offer_id,
     candidate_score, candidate_version)
  values (p_driver_id, p_import_id, p_decision, p_offer_id,
          null, 'journey-reconciliation-ledger-v2');

  return pg_catalog.jsonb_build_object(
    'ok', true, 'decision', p_decision, 'import_id', p_import_id,
    'offer_id', p_offer_id, 'outcome_created', false,
    'final_payment_verified', false
  );
end;
$$;

revoke all on function public.sr_apply_reconciliation_atomic_v2(uuid,uuid,text,bigint)
  from public, anon, authenticated;
grant execute on function public.sr_apply_reconciliation_atomic_v2(uuid,uuid,text,bigint)
  to service_role;

commit;

-- OPEN REVIEW GATES (NOT EXECUTED):
-- 1. Independent financial/security review, server origin/CSRF protection,
--    proof of human action, and appropriate immutable audit provenance.
-- 2. Verify Postgres permissions/RLS and signature for server service_role.
-- 3. Test SQL with rollback fixtures on disposable database, two sessions
--    concurrently: same import; different imports/same offer; independent offers.
-- 4. Wrong driver, unowned journey, canceled import, null/future timestamp,
--    duplicate decision, no_match without candidate, and 0 fare.
-- 5. Confirm ride_outcomes, imported rides, V7, and all money metrics unchanged.
-- 6. Never expose service key to browser or unlock POST without separate approval.
-- 7. Subsequent promotion to realized analytics is separate, not implicit.
