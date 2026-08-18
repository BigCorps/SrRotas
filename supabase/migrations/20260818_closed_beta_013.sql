begin;

create table if not exists public.beta_feedback (
  id uuid primary key default gen_random_uuid(),
  driver_id uuid not null references public.drivers(id) on delete cascade,
  kind text not null check (kind in ('feedback','crash')),
  category text not null default 'Geral',
  severity text not null default 'Informativo',
  message text not null default '',
  app_version text,
  version_code integer,
  android_sdk integer,
  manufacturer text,
  model text,
  checklist_completed integer,
  checklist_total integer,
  event_id uuid,
  exception_class text,
  stack_trace text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create unique index if not exists beta_feedback_event_unique
  on public.beta_feedback(event_id)
  where event_id is not null;

create index if not exists beta_feedback_driver_created_idx
  on public.beta_feedback(driver_id, created_at desc);

create index if not exists beta_feedback_kind_created_idx
  on public.beta_feedback(kind, created_at desc);

alter table public.beta_feedback enable row level security;

comment on table public.beta_feedback is
  'Closed Beta 0.13. Recebe feedback estruturado e crash técnico mínimo. Não armazena OCR bruto, screenshots, senha, device token ou chave MCP.';

commit;
