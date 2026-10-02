begin;

-- O Radar contextual passa a considerar apenas outcomes que não vieram da antiga
-- promoção automática da digitalização. Registros históricos são preservados para
-- auditoria e revisão humana na tela de Conciliação.
create or replace view public.sr_radar_contextual_learning_v1 with(security_invoker=false) as
with anchor as(
 select distinct on(e.driver_id,e.ride_local_offer_id,e.opportunity_id)
  e.driver_id,e.ride_local_offer_id,e.opportunity_id,e.poi_id,e.event_type,e.occurred_at radar_event_at,
  e.ranking_score,e.continuity_probability_pct,e.baseline_probability_pct,e.distance_km
 from public.sr_radar_contextual_events e
 where e.ride_local_offer_id is not null
  and e.event_type in('radar_opened','opportunity_viewed','navigation_opened','assistant_shown','assistant_viewed')
 order by e.driver_id,e.ride_local_offer_id,e.opportunity_id,e.occurred_at desc
), completed as(
 select a.*,r.journey_id,r.completed_at
 from anchor a join public.ride_outcomes r
  on r.driver_id=a.driver_id and r.local_offer_id=a.ride_local_offer_id
 where r.status='COMPLETED'
   and r.source <> 'uber_history_ocr'
   and r.completed_at is not null
)
select c.*,
 n.local_offer_id next_offer_local_id,n.observed_at next_offer_at,n.fare next_fare,n.per_km next_per_km,n.per_minute next_per_minute,n.per_hour next_per_hour,
 case when n.observed_at is not null then round((extract(epoch from(n.observed_at-c.completed_at))/60.0)::numeric,2) end ttnr_minutes,
 exists(
   select 1 from public.ride_outcomes rr
   where rr.driver_id=c.driver_id
     and rr.local_offer_id=n.local_offer_id
     and rr.status='COMPLETED'
     and rr.source <> 'uber_history_ocr'
 ) next_offer_became_completed_ride,
 case when c.continuity_probability_pct is null or c.baseline_probability_pct is null then null
 else round((c.continuity_probability_pct-c.baseline_probability_pct)::numeric,2) end predicted_delta_pp
from completed c
left join lateral(
 select ro.local_offer_id,ro.observed_at,ro.fare,ro.per_km,ro.per_minute,ro.per_hour
 from public.ride_offers ro
 where ro.driver_id=c.driver_id
   and ro.journey_id=c.journey_id
   and ro.observed_at>=c.completed_at
   and coalesce(ro.capture_method,'') not like 'historical-import/%'
 order by ro.observed_at asc limit 1
) n on true;

revoke all on public.sr_radar_contextual_learning_v1 from public,anon,authenticated;
grant select on public.sr_radar_contextual_learning_v1 to service_role;
comment on view public.sr_radar_contextual_learning_v1 is
  'Observacional: somente outcomes confiáveis; não demonstra causalidade.';

commit;
