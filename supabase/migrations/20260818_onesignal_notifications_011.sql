begin;

create table if not exists public.notification_preferences (
  driver_id uuid primary key references public.drivers(id) on delete cascade,
  operational_enabled boolean not null default true,
  journey_summary_enabled boolean not null default true,
  sync_alerts_enabled boolean not null default true,
  product_updates_enabled boolean not null default false,
  updated_at timestamptz not null default now()
);

create table if not exists public.notification_deliveries (
  id uuid primary key default gen_random_uuid(),
  driver_id uuid not null references public.drivers(id) on delete cascade,
  category text not null check (category in ('operational','journey_summary','sync_alert','product_update','test')),
  title text not null,
  body text not null,
  status text not null default 'queued' check (status in ('queued','sent','failed','skipped')),
  dedupe_key text,
  onesignal_idempotency_key uuid not null default gen_random_uuid(),
  onesignal_message_id text,
  error_code text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  sent_at timestamptz
);

create unique index if not exists notification_deliveries_driver_dedupe_unique
  on public.notification_deliveries(driver_id, dedupe_key)
  where dedupe_key is not null and btrim(dedupe_key) <> '';

create index if not exists notification_deliveries_driver_created_idx
  on public.notification_deliveries(driver_id, created_at desc);

alter table public.notification_preferences enable row level security;
alter table public.notification_deliveries enable row level security;

comment on table public.notification_preferences is
  'Preferências de push do motorista. A entrega é feita por OneSignal usando driver.id como external_id.';

comment on table public.notification_deliveries is
  'Auditoria de notificações operacionais. Não armazena OCR bruto, senha, token do aparelho ou chave OneSignal.';

commit;
