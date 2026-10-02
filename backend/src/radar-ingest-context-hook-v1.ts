import { ingestRadarEvents0261, RadarIngestRequest0261 } from "./radar-ingest-0261";
import { reconcileUnlinkedRadarEventsV1 } from "./radar-poi-resolver-v1";

/**
 * Wrapper opt-in para o integrador.
 * Preserva integralmente a normalização/upsert 0.26.1 e apenas reconcilia
 * Event -> POI após o ingest ter sido concluído com sucesso.
 *
 * Pode ser adotado depois; o endpoint antigo continua válido sem este wrapper.
 */
export async function ingestRadarContextV1(input:RadarIngestRequest0261) {
  const ingest=await ingestRadarEvents0261(input);
  const reconcile=await reconcileUnlinkedRadarEventsV1(Math.min(ingest.received ?? 200,500));
  return { ...ingest, poi_resolution: { processed:reconcile.processed, counts:reconcile.counts } };
}
