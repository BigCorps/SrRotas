-- JÁ APLICADA EM PRODUÇÃO VIA MCP EM 28/09/2026.

alter table public.beta_feedback
  add column if not exists triage_status text not null default 'new',
  add column if not exists admin_note text,
  add column if not exists triaged_at timestamptz,
  add column if not exists triaged_by text;

alter table public.beta_feedback
  drop constraint if exists beta_feedback_triage_status_check;

alter table public.beta_feedback
  add constraint beta_feedback_triage_status_check
  check(triage_status in ('new','reviewing','resolved','ignored'));

create index if not exists beta_feedback_triage_created_idx
  on public.beta_feedback(triage_status,created_at desc);

create or replace function public.sr_purge_expired_device_identities_v1()
returns integer
language plpgsql
security definer
set search_path=pg_catalog,public
as $$
declare v_count integer:=0;
begin
  delete from public.device_identity_registry
  where last_driver_id is null
    and trial_driver_id is null
    and (
      (trial_expires_at is not null and trial_expires_at<=now())
      or (
        trial_redeemed_at is null
        and trial_expires_at is null
        and last_seen_at<now()-interval '30 days'
      )
    );
  get diagnostics v_count=row_count;
  return v_count;
end;
$$;

revoke all on function public.sr_purge_expired_device_identities_v1()
  from public,anon,authenticated;
grant execute on function public.sr_purge_expired_device_identities_v1()
  to service_role;
