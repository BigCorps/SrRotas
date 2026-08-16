begin;

alter table public.ride_offers
  add column if not exists confidence numeric(5,4) not null default 0.5000,
  add column if not exists offer_type text not null default 'exclusive',
  add column if not exists raw_text_shared boolean not null default false;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'ride_offers_confidence_check'
  ) then
    alter table public.ride_offers
      add constraint ride_offers_confidence_check check (confidence >= 0 and confidence <= 1);
  end if;

  if not exists (
    select 1 from pg_constraint where conname = 'ride_offers_offer_type_check'
  ) then
    alter table public.ride_offers
      add constraint ride_offers_offer_type_check check (offer_type in ('exclusive','radar'));
  end if;
end $$;

create index if not exists ride_offers_driver_confidence_idx
  on public.ride_offers(driver_id, confidence desc, observed_at desc);

commit;
