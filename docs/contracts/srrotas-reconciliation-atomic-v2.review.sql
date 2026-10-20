-- REVIEW ONLY / DO NOT APPLY.
-- Proposed future migration for Sr. Rotas reconciliation_v2.
-- NOT placed in supabase/migrations intentionally: financial/security cross-review
-- and explicit authorization are mandatory before any DB change.
-- Never interpret candidate scores or legacy hints as human approval.
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
  p_offer_id bigint default null,
  p_score numeric default null
) returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_import public.uber_completed_ride_imports%rowtype;
  v_offer public.ride_offers%rowtype;
  v_existing public.ride_outcomes%rowtype;
  v_outcome_id uuid;
  v_timestamp timestamptz;
begin
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

  if p_decision = 'no_match' then
    -- Preserve legacy events for audit. Only clear the hint on this import.
    update public.uber_completed_ride_imports
       set matched_ride_offer_id = null
     where id = p_import_id and driver_id = p_driver_id;
    insert into public.sr_ride_reconciliations
      (driver_id, import_id, decision, selected_ride_offer_id,
       candidate_score, candidate_version)
    values (p_driver_id, p_import_id, 'no_match', null, null,
            'journey-reconciliation-atomic-v2');
    return jsonb_build_object(
      'ok', true, 'decision', 'no_match', 'import_id', p_import_id,
      'final_payment_verified', false
    );
  end if;

  -- Until timestamps are verified, missing or future data are not eligible.
  v_timestamp := v_import.occurred_at;
  if v_timestamp is null or v_timestamp > now() then
    raise exception using message = 'reconciliation_import_time_unverified';
  end if;
  if p_score is null or p_score < 0 or p_score > 100 then
    raise exception using message = 'reconciliation_candidate_score_invalid';
  end if;

  -- Serialize competing approvals on the same offer row.
  select * into v_offer
    from public.ride_offers
   where id = p_offer_id and driver_id = p_driver_id
   for update;
  if not found then
    raise exception using message = 'reconciliation_offer_not_found';
  end if;
  if v_offer.journey_id is null or v_offer.local_offer_id is null or
     not exists (select 1 from public.driver_journeys
                  where id = v_offer.journey_id and driver_id = p_driver_id) then
    raise exception using message = 'reconciliation_offer_without_owned_journey';
  end if;
  if exists (select 1 from public.sr_ride_reconciliations
             where driver_id = p_driver_id
               and selected_ride_offer_id = p_offer_id
               and decision = 'confirmed') then
    raise exception using message = 'reconciliation_offer_already_used';
  end if;

  select * into v_existing
    from public.ride_outcomes
   where driver_id = p_driver_id and local_offer_id = v_offer.local_offer_id
   for update;
  if found and (v_existing.source <> 'offer_engine' or
                v_existing.status <> 'OFFERED' or
                v_existing.journey_id is distinct from v_offer.journey_id) then
    raise exception using message = 'reconciliation_existing_outcome_conflict';
  end if;

  -- Upsert is allowed only over the non-realized OFFERED observation.
  -- A conflicting completed or active ride cannot be overwritten.
  insert into public.ride_outcomes
    (driver_id, device_id, journey_id, ride_offer_id, local_offer_id,
     status, started_at, completed_at, corrected_at, source, revision, updated_at)
  values
    (p_driver_id, v_import.device_id, v_offer.journey_id, v_offer.id,
     v_offer.local_offer_id, 'COMPLETED',
     case when v_import.duration_seconds is not null
          then v_timestamp - v_import.duration_seconds * interval '1 second'
          else null end,
     v_timestamp, now(), 'reconciliation_v1', 1, now())
  on conflict (driver_id, local_offer_id)
  do update
     set ride_offer_id = excluded.ride_offer_id,
         status = excluded.status,
         started_at = excluded.started_at,
         completed_at = excluded.completed_at,
         corrected_at = excluded.corrected_at,
         source = excluded.source,
         revision = public.ride_outcomes.revision + 1,
         updated_at = now()
   where public.ride_outcomes.source = 'offer_engine'
     and public.ride_outcomes.status = 'OFFERED'
     and public.ride_outcomes.journey_id = excluded.journey_id
  returning id into v_outcome_id;
  if v_outcome_id is null then
    raise exception using message = 'reconciliation_existing_outcome_conflict';
  end if;

  -- Unique partial index rejects simultaneous approvals for one offer.
  insert into public.sr_ride_reconciliations
    (driver_id, import_id, decision, selected_ride_offer_id,
     candidate_score, candidate_version)
  values
    (p_driver_id, p_import_id, 'confirmed', p_offer_id,
     p_score, 'journey-reconciliation-atomic-v2');

  update public.uber_completed_ride_imports
     set matched_ride_offer_id = p_offer_id
   where id = p_import_id and driver_id = p_driver_id;

  return jsonb_build_object(
    'ok', true, 'decision', 'confirmed', 'import_id', p_import_id,
    'offer_id', p_offer_id, 'final_payment_verified', false
  );
end;
$$;

revoke all on function public.sr_apply_reconciliation_atomic_v2(uuid,uuid,text,bigint,numeric)
  from public, anon, authenticated;
grant execute on function public.sr_apply_reconciliation_atomic_v2(uuid,uuid,text,bigint,numeric)
  to service_role;

commit;

-- OPEN REVIEW ITEMS BEFORE ACTIVATION:
-- 1. Confirm UUID type of ride_outcomes.id and PostgREST RPC schema cache.
-- 2. Prove inserted/updated row consistency via an authorized rollback-only test.
-- 3. Verify row-lock order and deadlock behavior with two separate sessions.
-- 4. Ensure the server recomputes candidate score and validates user selection.
-- 5. Exercise duplicate same-import, duplicate distinct-import same-offer,
--    preexisting COMPLETED/DOING_RIDE, invalid/future timestamp, cross-driver,
--    canceled import, lost connection, transaction rollback, and 0-value fare.
-- 6. No browser/Android API may call this function directly. Human click only.
-- 7. New endpoint remains blocked until deployment + independent review.
