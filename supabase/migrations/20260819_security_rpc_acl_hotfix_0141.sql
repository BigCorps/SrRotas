-- Sr. Rotas 0.14.1 — Hotfix de ACL das RPCs server-only
-- Data: 2026-08-19
--
-- Objetivo:
-- Corrigir drift de permissões no banco de produção.
-- Estas funções SECURITY DEFINER são chamadas exclusivamente por backend/Edge
-- com service_role e não devem ser executáveis por anon/authenticated.
--
-- Idempotente: pode ser executado novamente sem efeito adverso.

begin;

revoke all on function public.sr_ensure_credit_wallet(uuid)
  from PUBLIC, anon, authenticated;
revoke all on function public.sr_create_pix_payment(uuid, timestamptz)
  from PUBLIC, anon, authenticated;
revoke all on function public.sr_attach_pix_provider_data(uuid, text, text, text, jsonb)
  from PUBLIC, anon, authenticated;
revoke all on function public.sr_mark_pix_checked(uuid, text, jsonb)
  from PUBLIC, anon, authenticated;
revoke all on function public.sr_mark_pix_failed(uuid, text, text, jsonb)
  from PUBLIC, anon, authenticated;
revoke all on function public.sr_expire_pix_payments()
  from PUBLIC, anon, authenticated;
revoke all on function public.sr_grant_welcome_credits(uuid, uuid)
  from PUBLIC, anon, authenticated;
revoke all on function public.sr_apply_confirmed_payment(uuid, text, integer, text, jsonb, timestamptz)
  from PUBLIC, anon, authenticated;
revoke all on function public.sr_reserve_ai_credit(uuid, text)
  from PUBLIC, anon, authenticated;
revoke all on function public.sr_consume_ai_credit(uuid, text)
  from PUBLIC, anon, authenticated;
revoke all on function public.sr_refund_ai_credit(uuid, text)
  from PUBLIC, anon, authenticated;
revoke all on function public.sr_security_rate_limit(text, integer, integer)
  from PUBLIC, anon, authenticated;

grant execute on function public.sr_ensure_credit_wallet(uuid)
  to service_role;
grant execute on function public.sr_create_pix_payment(uuid, timestamptz)
  to service_role;
grant execute on function public.sr_attach_pix_provider_data(uuid, text, text, text, jsonb)
  to service_role;
grant execute on function public.sr_mark_pix_checked(uuid, text, jsonb)
  to service_role;
grant execute on function public.sr_mark_pix_failed(uuid, text, text, jsonb)
  to service_role;
grant execute on function public.sr_expire_pix_payments()
  to service_role;
grant execute on function public.sr_grant_welcome_credits(uuid, uuid)
  to service_role;
grant execute on function public.sr_apply_confirmed_payment(uuid, text, integer, text, jsonb, timestamptz)
  to service_role;
grant execute on function public.sr_reserve_ai_credit(uuid, text)
  to service_role;
grant execute on function public.sr_consume_ai_credit(uuid, text)
  to service_role;
grant execute on function public.sr_refund_ai_credit(uuid, text)
  to service_role;
grant execute on function public.sr_security_rate_limit(text, integer, integer)
  to service_role;

commit;

-- Verificação: o resultado esperado para todas as linhas é
-- anon_exec=false | authenticated_exec=false | service_role_exec=true
select
  p.proname,
  pg_get_function_identity_arguments(p.oid) as arguments,
  has_function_privilege('anon', p.oid, 'EXECUTE') as anon_exec,
  has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_exec,
  has_function_privilege('service_role', p.oid, 'EXECUTE') as service_role_exec
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in (
    'sr_ensure_credit_wallet',
    'sr_create_pix_payment',
    'sr_attach_pix_provider_data',
    'sr_mark_pix_checked',
    'sr_mark_pix_failed',
    'sr_expire_pix_payments',
    'sr_grant_welcome_credits',
    'sr_apply_confirmed_payment',
    'sr_reserve_ai_credit',
    'sr_consume_ai_credit',
    'sr_refund_ai_credit',
    'sr_security_rate_limit'
  )
order by p.proname, arguments;
