begin;

-- RC3.2: desde a RC2, a seleção para relatório é independente por oferta.
-- A constraint anterior permitia somente UMA seleção por jornada e fazia o
-- endpoint /api/v1/offers/report-selection retornar 500 (unique_violation).
drop index if exists public.ride_offers_one_report_selection_per_journey_idx;

-- Mantém a consulta eficiente sem impor unicidade por jornada.
create index if not exists ride_offers_report_selected_journey_idx
  on public.ride_offers(driver_id, journey_id, observed_at desc)
  where report_selected = true;

comment on column public.ride_offers.report_selected is
  'Seleção manual independente por oferta para relatórios; múltiplas ofertas da mesma jornada podem ser selecionadas.';

commit;
