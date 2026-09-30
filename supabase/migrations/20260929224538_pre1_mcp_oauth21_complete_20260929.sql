create table if not exists public.mcp_oauth_clients (
  id uuid primary key default gen_random_uuid(),
  client_id text not null unique,
  client_name text not null,
  client_uri text,
  logo_uri text,
  redirect_uris text[] not null,
  token_endpoint_auth_method text not null default 'none',
  client_secret_hash text,
  client_secret_prefix text,
  grant_types text[] not null default array['authorization_code','refresh_token']::text[],
  response_types text[] not null default array['code']::text[],
  scopes text[] not null default array['srrotas.read']::text[],
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  revoked_at timestamptz,
  constraint mcp_oauth_clients_id_check check (char_length(client_id) between 12 and 180),
  constraint mcp_oauth_clients_name_check check (char_length(client_name) between 1 and 120),
  constraint mcp_oauth_clients_redirect_count_check check (cardinality(redirect_uris) between 1 and 12),
  constraint mcp_oauth_clients_auth_method_check check (
    token_endpoint_auth_method in ('none','client_secret_basic','client_secret_post')
  ),
  constraint mcp_oauth_clients_secret_check check (
    (token_endpoint_auth_method='none' and client_secret_hash is null)
    or
    (token_endpoint_auth_method<>'none' and client_secret_hash is not null)
  )
);

create table if not exists public.mcp_oauth_authorization_requests (
  id uuid primary key default gen_random_uuid(),
  client_id text not null references public.mcp_oauth_clients(client_id) on delete cascade,
  redirect_uri text not null,
  state text,
  code_challenge text not null,
  code_challenge_method text not null default 'S256',
  resource text not null,
  scopes text[] not null default array['srrotas.read']::text[],
  driver_id uuid references public.drivers(id) on delete cascade,
  status text not null default 'pending',
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  decided_at timestamptz,
  constraint mcp_oauth_authorization_requests_status_check check (
    status in ('pending','approved','denied','expired')
  ),
  constraint mcp_oauth_authorization_requests_pkce_check check (
    code_challenge_method='S256' and char_length(code_challenge) between 43 and 128
  )
);

create table if not exists public.mcp_oauth_codes (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null references public.mcp_oauth_authorization_requests(id) on delete cascade,
  driver_id uuid not null references public.drivers(id) on delete cascade,
  client_id text not null references public.mcp_oauth_clients(client_id) on delete cascade,
  code_hash text not null unique,
  redirect_uri text not null,
  code_challenge text not null,
  resource text not null,
  scopes text[] not null default array['srrotas.read']::text[],
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  used_at timestamptz,
  constraint mcp_oauth_codes_hash_check check (char_length(code_hash)=64)
);

create table if not exists public.mcp_oauth_grants (
  id uuid primary key default gen_random_uuid(),
  driver_id uuid not null references public.drivers(id) on delete cascade,
  client_id text not null references public.mcp_oauth_clients(client_id) on delete cascade,
  scopes text[] not null default array['srrotas.read']::text[],
  approved_at timestamptz not null default now(),
  last_used_at timestamptz,
  revoked_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(driver_id,client_id)
);

create table if not exists public.mcp_oauth_tokens (
  id uuid primary key default gen_random_uuid(),
  driver_id uuid not null references public.drivers(id) on delete cascade,
  client_id text not null references public.mcp_oauth_clients(client_id) on delete cascade,
  grant_id uuid not null references public.mcp_oauth_grants(id) on delete cascade,
  access_token_hash text not null unique,
  access_token_prefix text not null,
  refresh_token_hash text not null unique,
  refresh_token_prefix text not null,
  scopes text[] not null default array['srrotas.read']::text[],
  access_expires_at timestamptz not null,
  refresh_expires_at timestamptz not null,
  last_used_at timestamptz,
  created_at timestamptz not null default now(),
  revoked_at timestamptz,
  revoked_reason text,
  rotated_from_id uuid references public.mcp_oauth_tokens(id) on delete set null,
  constraint mcp_oauth_tokens_access_hash_check check (char_length(access_token_hash)=64),
  constraint mcp_oauth_tokens_refresh_hash_check check (char_length(refresh_token_hash)=64),
  constraint mcp_oauth_tokens_expiry_check check (refresh_expires_at > access_expires_at)
);

create index if not exists mcp_oauth_clients_active_idx
  on public.mcp_oauth_clients(created_at desc)
  where revoked_at is null;

create index if not exists mcp_oauth_authorization_requests_pending_idx
  on public.mcp_oauth_authorization_requests(expires_at)
  where status='pending';

create index if not exists mcp_oauth_codes_active_idx
  on public.mcp_oauth_codes(expires_at)
  where used_at is null;

create index if not exists mcp_oauth_grants_driver_active_idx
  on public.mcp_oauth_grants(driver_id,updated_at desc)
  where revoked_at is null;

create index if not exists mcp_oauth_tokens_driver_active_idx
  on public.mcp_oauth_tokens(driver_id,access_expires_at desc)
  where revoked_at is null;

create index if not exists mcp_oauth_tokens_client_active_idx
  on public.mcp_oauth_tokens(client_id,access_expires_at desc)
  where revoked_at is null;

alter table public.mcp_oauth_clients enable row level security;
alter table public.mcp_oauth_authorization_requests enable row level security;
alter table public.mcp_oauth_codes enable row level security;
alter table public.mcp_oauth_grants enable row level security;
alter table public.mcp_oauth_tokens enable row level security;

revoke all on public.mcp_oauth_clients from public,anon,authenticated;
revoke all on public.mcp_oauth_authorization_requests from public,anon,authenticated;
revoke all on public.mcp_oauth_codes from public,anon,authenticated;
revoke all on public.mcp_oauth_grants from public,anon,authenticated;
revoke all on public.mcp_oauth_tokens from public,anon,authenticated;

grant select,insert,update,delete on public.mcp_oauth_clients to service_role;
grant select,insert,update,delete on public.mcp_oauth_authorization_requests to service_role;
grant select,insert,update,delete on public.mcp_oauth_codes to service_role;
grant select,insert,update,delete on public.mcp_oauth_grants to service_role;
grant select,insert,update,delete on public.mcp_oauth_tokens to service_role;

create or replace function public.sr_cleanup_mcp_oauth_v1()
returns jsonb
language plpgsql
security invoker
set search_path=pg_catalog,public
as $$
declare
  v_requests integer := 0;
  v_codes integer := 0;
  v_tokens integer := 0;
begin
  update public.mcp_oauth_authorization_requests
     set status='expired', decided_at=coalesce(decided_at,now())
   where status='pending' and expires_at<=now();
  get diagnostics v_requests=row_count;

  delete from public.mcp_oauth_codes
   where (expires_at < now()-interval '1 day')
      or (used_at is not null and used_at < now()-interval '1 day');
  get diagnostics v_codes=row_count;

  update public.mcp_oauth_tokens
     set revoked_at=coalesce(revoked_at,now()),
         revoked_reason=coalesce(revoked_reason,'refresh_expired')
   where revoked_at is null and refresh_expires_at<=now();
  get diagnostics v_tokens=row_count;

  return jsonb_build_object(
    'expiredAuthorizationRequests',v_requests,
    'deletedAuthorizationCodes',v_codes,
    'revokedExpiredTokens',v_tokens
  );
end;
$$;

revoke all on function public.sr_cleanup_mcp_oauth_v1() from public,anon,authenticated;
grant execute on function public.sr_cleanup_mcp_oauth_v1() to service_role;

comment on table public.mcp_oauth_clients is
  'OAuth 2.1 MCP dynamic clients. Secrets are stored only as SHA-256 hashes.';
comment on table public.mcp_oauth_codes is
  'One-time OAuth authorization codes stored only as SHA-256 hashes.';
comment on table public.mcp_oauth_tokens is
  'OAuth MCP access and refresh tokens stored only as SHA-256 hashes.';
