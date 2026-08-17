begin;

alter table public.drivers
  add column if not exists auth_user_id uuid references auth.users(id) on delete cascade,
  add column if not exists email text,
  add column if not exists onboarding_completed boolean not null default false,
  add column if not exists last_login_at timestamptz;

create unique index if not exists drivers_auth_user_id_unique
  on public.drivers(auth_user_id)
  where auth_user_id is not null;

create unique index if not exists drivers_email_lower_unique
  on public.drivers(lower(email))
  where email is not null and btrim(email) <> '';

-- Continua sem policies públicas. O aplicativo não acessa tabelas diretamente:
-- autenticação e dados passam pelo backend com service_role.
-- A fase 0.12 revisará verificação de e-mail, rate limiting e políticas finais.

commit;
