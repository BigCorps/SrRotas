-- JÁ APLICADA EM PRODUÇÃO VIA MCP EM 28/09/2026.

create table if not exists public.admin_diagnostic_audit (
  id uuid primary key default gen_random_uuid(),
  diagnostic_id uuid not null
    references public.beta_feedback(id) on delete cascade,
  admin_email text not null,
  from_status text,
  to_status text not null,
  note_changed boolean not null default false,
  created_at timestamptz not null default now()
);

alter table public.admin_diagnostic_audit enable row level security;
revoke all on public.admin_diagnostic_audit from public,anon,authenticated;
grant select,insert on public.admin_diagnostic_audit to service_role;

create index if not exists admin_diagnostic_audit_diagnostic_created_idx
  on public.admin_diagnostic_audit(diagnostic_id,created_at desc);
