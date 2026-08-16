begin;

-- Sr. Rotas 0.4 Alpha — métricas de oferta e faixas configuráveis do Cherry Picker.
alter table public.ride_offers
  add column if not exists per_minute numeric(12,2),
  add column if not exists profit_per_hour numeric(12,2),
  add column if not exists profit_percent numeric(8,2),
  add column if not exists passenger_rating numeric(4,2),
  add column if not exists advertised_per_km numeric(12,2),
  add column if not exists service_type text not null default 'unknown';

alter table public.driver_preferences
  add column if not exists red_per_km_below numeric(10,2) not null default 1.45,
  add column if not exists red_per_hour_below numeric(10,2) not null default 28.00,
  add column if not exists good_rating_from numeric(4,2) not null default 4.85,
  add column if not exists red_rating_below numeric(4,2) not null default 4.70,
  add column if not exists min_per_minute numeric(10,2) not null default 0.60,
  add column if not exists red_per_minute_below numeric(10,2) not null default 0.48,
  add column if not exists min_profit_per_hour numeric(10,2) not null default 0,
  add column if not exists red_profit_per_hour_below numeric(10,2) not null default 0,
  add column if not exists min_profit_percent numeric(8,2) not null default 0,
  add column if not exists red_profit_percent_below numeric(8,2) not null default 0;

create index if not exists ride_offers_driver_service_idx
  on public.ride_offers(driver_id, service_type, observed_at desc);

comment on column public.ride_offers.advertised_per_km is
  'R$/km aproximado mostrado pela própria plataforma quando disponível; usado também como checagem de consistência.';
comment on column public.ride_offers.passenger_rating is
  'Avaliação exibida na oferta quando reconhecida. Pode ser nula.';
comment on column public.driver_preferences.red_per_km_below is
  'Limite inferior do Cherry Picker: abaixo deste valor a métrica R$/km fica vermelha.';

commit;
