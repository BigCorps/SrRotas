begin;

alter table public.historical_import_access
  add column if not exists added_by_auth_user_id uuid;

alter table public.historical_import_batches
  add column if not exists created_by_auth_user_id uuid;

alter table public.historical_import_batches
  alter column created_by_driver_id drop not null;

create index if not exists historical_import_batches_auth_creator_idx
  on public.historical_import_batches(created_by_auth_user_id, created_at desc);

create index if not exists historical_import_batches_email_creator_idx
  on public.historical_import_batches(created_by_email, created_at desc);

create table if not exists public.historical_import_web_sessions (
  id uuid primary key default gen_random_uuid(),
  auth_user_id uuid not null,
  email text not null,
  token_hash text not null unique,
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  constraint historical_import_web_sessions_email_normalized
    check (email = lower(trim(email))),
  constraint historical_import_web_sessions_token_hash
    check (token_hash ~ '^[a-f0-9]{64}$')
);

create index if not exists historical_import_web_sessions_user_idx
  on public.historical_import_web_sessions(auth_user_id, expires_at desc);

create index if not exists historical_import_web_sessions_expiry_idx
  on public.historical_import_web_sessions(expires_at);

alter table public.historical_import_web_sessions enable row level security;

revoke all on table public.historical_import_web_sessions from public, anon, authenticated;
grant all on table public.historical_import_web_sessions to service_role;

comment on table public.historical_import_web_sessions is
  'Sessao Web-only do portal interno de importacoes. Independente de drivers/device tokens.';

comment on column public.historical_import_batches.created_by_auth_user_id is
  'Supabase Auth user que enviou o lote. Importadores administrativos nao precisam ser motoristas.';

comment on column public.historical_import_access.added_by_auth_user_id is
  'Supabase Auth user administrador que autorizou o email.';

commit;
