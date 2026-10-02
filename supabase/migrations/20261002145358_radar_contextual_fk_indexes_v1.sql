begin;
create index if not exists sr_radar_contextual_events_device_idx
  on public.sr_radar_contextual_events(device_id)
  where device_id is not null;
create index if not exists sr_radar_contextual_events_journey_idx
  on public.sr_radar_contextual_events(journey_id)
  where journey_id is not null;
create index if not exists sr_radar_contextual_events_poi_idx
  on public.sr_radar_contextual_events(poi_id)
  where poi_id is not null;
commit;
