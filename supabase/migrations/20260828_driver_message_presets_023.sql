begin;

create table if not exists public.driver_message_presets (
  driver_id uuid not null references public.drivers(id) on delete cascade,
  sort_order integer not null check (sort_order between 0 and 99),
  short_label text not null check (char_length(short_label) between 1 and 2),
  accessibility_label text,
  message_text text not null check (char_length(message_text) between 1 and 500),
  color_token text not null check (color_token in (
    'shortcut01','shortcut02','shortcut03','shortcut04','shortcut05','shortcut06'
  )),
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (driver_id, sort_order)
);

create index if not exists driver_message_presets_driver_enabled_idx
  on public.driver_message_presets(driver_id, enabled, sort_order);

alter table public.driver_message_presets enable row level security;

-- Mantém o mesmo modelo de segurança do projeto: nenhuma policy pública.
-- Backend/API autenticado usa service_role e sempre filtra pelo driver_id do ator.

commit;
