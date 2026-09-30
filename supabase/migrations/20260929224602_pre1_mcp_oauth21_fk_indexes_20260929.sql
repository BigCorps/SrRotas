create index if not exists mcp_oauth_authorization_requests_client_idx
  on public.mcp_oauth_authorization_requests(client_id);
create index if not exists mcp_oauth_authorization_requests_driver_idx
  on public.mcp_oauth_authorization_requests(driver_id)
  where driver_id is not null;

create index if not exists mcp_oauth_codes_request_idx
  on public.mcp_oauth_codes(request_id);
create index if not exists mcp_oauth_codes_driver_idx
  on public.mcp_oauth_codes(driver_id);
create index if not exists mcp_oauth_codes_client_idx
  on public.mcp_oauth_codes(client_id);

create index if not exists mcp_oauth_grants_client_idx
  on public.mcp_oauth_grants(client_id);

create index if not exists mcp_oauth_tokens_grant_idx
  on public.mcp_oauth_tokens(grant_id);
create index if not exists mcp_oauth_tokens_rotated_from_idx
  on public.mcp_oauth_tokens(rotated_from_id)
  where rotated_from_id is not null;
