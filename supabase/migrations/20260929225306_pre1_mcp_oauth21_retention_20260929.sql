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
  v_old_requests integer := 0;
  v_old_tokens integer := 0;
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

  delete from public.mcp_oauth_authorization_requests
   where status in ('approved','denied','expired')
     and created_at < now()-interval '30 days';
  get diagnostics v_old_requests=row_count;

  delete from public.mcp_oauth_tokens
   where revoked_at is not null
     and revoked_at < now()-interval '90 days';
  get diagnostics v_old_tokens=row_count;

  return jsonb_build_object(
    'expiredAuthorizationRequests',v_requests,
    'deletedAuthorizationCodes',v_codes,
    'revokedExpiredTokens',v_tokens,
    'deletedOldAuthorizationRequests',v_old_requests,
    'deletedOldRevokedTokens',v_old_tokens
  );
end;
$$;

revoke all on function public.sr_cleanup_mcp_oauth_v1() from public,anon,authenticated;
grant execute on function public.sr_cleanup_mcp_oauth_v1() to service_role;
