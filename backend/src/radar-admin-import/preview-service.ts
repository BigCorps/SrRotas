import { adminSupabase } from "../supabase";
import {
  adminJsonV1,
  requireRadarAdminV1,
  radarAdminRateLimitV1,
} from "../radar-admin-http-v1";
import {
  ImportError,
  previewImport,
  readImport,
  materialHash,
  type ImportItem,
  type Snapshot,
  type Existing,
} from "./validation";
/** Recompute material from current columns: a stale metadata digest cannot prove equivalence. */
function currentMaterial(row: any, kind: "event" | "poi") {
  const saved = row.metadata?.admin_import;
  if (!saved?.provenance) return undefined;
  try {
    const place = {
      address: row.address,
      city: row.city,
      state: row.state,
      lat: row.lat,
      lng: row.lng,
    };
    const data =
      kind === "event"
        ? {
            ...place,
            name: row.name,
            event_type: row.event_type,
            venue_name: row.venue_name,
            starts_at: new Date(row.starts_at).toISOString(),
            expected_end_at: new Date(row.expected_end_at).toISOString(),
            egress_start_at: new Date(row.egress_start_at).toISOString(),
            egress_end_at: new Date(row.egress_end_at).toISOString(),
            end_time_basis:
              row.end_time_source === "source"
                ? "official"
                : "declared_estimate",
          }
        : {
            ...place,
            canonical_name: row.canonical_name,
            poi_type: row.poi_type,
          };
    const hash = materialHash({
      kind,
      data,
      provenance: { ...saved.provenance, official_url: row.source_url },
    } as ImportItem);
    return hash === saved.material_hash ? hash : undefined;
  } catch {
    return undefined;
  }
}
export function existingRows(events: any[], pois: any[]): Existing[] {
  return [
    ...events.map((r) => ({
      id: r.id,
      row_version: r.row_version,
      status: r.status,
      review_status: r.review_status ?? r.metadata?.review,
      kind: "event" as const,
      source: r.source,
      external_id: r.external_id,
      name: r.name,
      venue_name: r.venue_name,
      address: r.address,
      lat: r.lat,
      lng: r.lng,
      starts_at: new Date(r.starts_at).toISOString(),
      material_hash: currentMaterial(r, "event"),
    })),
    ...pois.map((r) => ({
      id: r.id,
      row_version: r.row_version,
      status: r.status,
      review_status: r.review_status ?? r.metadata?.review,
      kind: "poi" as const,
      source: r.source,
      external_id: r.source_external_id,
      name: r.canonical_name,
      address: r.address,
      lat: r.lat,
      lng: r.lng,
      material_hash: currentMaterial(r, "poi"),
    })),
  ];
}
/** All origins/statuses, no registry provisioned in deployed environments; never auto-register. */
export async function deployedReadSnapshot(): Promise<Snapshot> {
  async function list(table: string, columns: string) {
    const rows: any[] = [];
    for (let start = 0; start < 10000; start += 500) {
      const { data, error } = await adminSupabase()
        .from(table)
        .select(columns)
        .order("id")
        .range(start, start + 499);
      if (error) throw new ImportError("STORE_UNAVAILABLE", 503);
      rows.push(...(data ?? []));
      if ((data?.length ?? 0) < 500) return { rows, complete: true };
    }
    return { rows, complete: false };
  }
  const [events, pois] = await Promise.all([
    list(
      "sr_event_opportunities",
      "id,source,external_id,name,venue_name,event_type,address,city,state,lat,lng,starts_at,expected_end_at,egress_start_at,egress_end_at,end_time_source,source_url,metadata,row_version,status,review_status",
    ),
    list(
      "sr_radar_pois",
      "id,source,source_external_id,canonical_name,poi_type,address,city,state,lat,lng,source_url,metadata,row_version,status",
    ),
  ]);
  return {
    registry: [],
    registryAvailable: false,
    existing: existingRows(events.rows, pois.rows),
    complete: events.complete && pois.complete,
  };
}
export function previewHandler(
  load: () => Promise<Snapshot> = deployedReadSnapshot,
) {
  return async (request: Request) => {
    const auth = await requireRadarAdminV1(request);
    if (auth.response) return auth.response;
    const limited = await radarAdminRateLimitV1(
      request,
      "radar-admin-import-preview",
      auth.actor!.email,
      30,
    );
    if (limited) return limited;
    try {
      return adminJsonV1(
        previewImport(await readImport(request), await load()),
      );
    } catch (e) {
      return adminJsonV1(
        {
          error: {
            code: e instanceof ImportError ? e.code : "STORE_UNAVAILABLE",
            message:
              "Não foi possível validar o arquivo. Nenhum registro foi importado.",
          },
        },
        e instanceof ImportError ? e.status : 503,
      );
    }
  };
}
