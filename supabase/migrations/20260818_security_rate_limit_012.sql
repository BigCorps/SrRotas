begin;

create table if not exists public.security_rate_limits (
  key_hash text primary key,
  window_started_at timestamptz not null default now(),
  attempts integer not null default 0 check (attempts >= 0),
  updated_at timestamptz not null default now()
);

alter table public.security_rate_limits enable row level security;

create or replace function public.sr_security_rate_limit(
  p_key_hash text,
  p_window_seconds integer,
  p_max_attempts integer
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_row public.security_rate_limits%rowtype;
begin
  if p_key_hash is null or btrim(p_key_hash) = '' then return false; end if;
  if p_window_seconds < 1 or p_max_attempts < 1 then return false; end if;

  select * into v_row from public.security_rate_limits where key_hash = p_key_hash for update;

  if not found then
    insert into public.security_rate_limits(key_hash, attempts) values (p_key_hash, 1);
    return true;
  end if;

  if v_row.window_started_at + make_interval(secs => p_window_seconds) <= now() then
    update public.security_rate_limits
      set window_started_at = now(), attempts = 1, updated_at = now()
      where key_hash = p_key_hash;
    return true;
  end if;

  if v_row.attempts >= p_max_attempts then
    update public.security_rate_limits set updated_at = now() where key_hash = p_key_hash;
    return false;
  end if;

  update public.security_rate_limits
    set attempts = attempts + 1, updated_at = now()
    where key_hash = p_key_hash;
  return true;
end;
$$;

revoke all on function public.sr_security_rate_limit(text,integer,integer) from public;
grant execute on function public.sr_security_rate_limit(text,integer,integer) to service_role;

commit;
