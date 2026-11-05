begin;
-- Supabase default privileges grant ALL to service_role on newly created tables.
-- Explicitly reset the ACL: GRANT SELECT, INSERT alone does not remove inherited
-- table grants such as UPDATE, DELETE, TRUNCATE, TRIGGER or MAINTAIN.
-- No rows, sequences, RLS policies or application contracts are changed.
revoke all on table
  public.sr_radar_revision_history,
  public.sr_radar_review_decisions,
  public.sr_radar_collector_usage
from public, anon, authenticated, service_role;

grant select, insert on table
  public.sr_radar_revision_history,
  public.sr_radar_review_decisions,
  public.sr_radar_collector_usage
to service_role;
-- Keep these restrictive ACLs during application rollback; restoring GRANT ALL
-- would restore the audit-integrity defect. Existing identity sequence grants stay.
commit;
