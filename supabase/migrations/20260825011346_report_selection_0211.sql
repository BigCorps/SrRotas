begin;

alter table public.ride_offers
  add column if not exists report_selected boolean not null default false,
  add column if not exists report_selected_at timestamptz;

create index if not exists ride_offers_report_selected_idx
  on public.ride_offers(driver_id, observed_at desc)
  where report_selected = true;

create unique index if not exists ride_offers_one_report_selection_per_journey_idx
  on public.ride_offers(driver_id, journey_id)
  where report_selected = true and journey_id is not null;

comment on column public.ride_offers.report_selected is
  'Seleção manual para relatórios. Não altera estado operacional da jornada/corrida e não bloqueia OCR.';

commit;
