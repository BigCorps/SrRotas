import { RadarContextV1 } from './radar-opportunity-contract-v1';

/** Adapter over the existing sr_event_opportunities contract. No DB replacement. */
export function eventRowToRadarContextV1(row:any):RadarContextV1 {
  return {
    id:String(row.id), kind:String(row.event_type||'event'), name:String(row.name),
    venueName:row.venue_name?String(row.venue_name):null,
    lat:Number(row.lat), lng:Number(row.lng), startsAt:row.starts_at?String(row.starts_at):null,
    endsAt:row.expected_end_at?String(row.expected_end_at):null,
    activeFrom:row.egress_start_at?String(row.egress_start_at):null,
    activeUntil:row.egress_end_at?String(row.egress_end_at):null,
    sourceConfidence:Number(row.confidence??.5), source:String(row.source||'unknown'), metadata:row.metadata||{},
  };
}
