begin;

create table if not exists public.web_handoff_tokens (
  id uuid primary key default gen_random_uuid(),
  driver_id uuid not null references public.drivers(id) on delete cascade,
  token_hash text not null unique,
  target_path text not null default '/app',
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now()
);

create index if not exists web_handoff_tokens_expiry_idx
  on public.web_handoff_tokens(expires_at)
  where used_at is null;

alter table public.web_handoff_tokens enable row level security;
revoke all on table public.web_handoff_tokens from public,anon,authenticated;
grant all on table public.web_handoff_tokens to service_role;

comment on table public.web_handoff_tokens is
  'Tokens de uso unico para handoff Android -> Web. Apenas hash e driver_id ficam no banco; device_token nunca entra na URL.';

commit;
