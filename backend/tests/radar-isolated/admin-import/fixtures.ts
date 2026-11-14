import {
  SCHEMA,
  materialHash,
  type Registry,
  type ImportItem,
} from "../../../src/radar-admin-import/validation";
export function fixture(
  suffix = "a",
  kind: "event" | "poi" = "event",
): ImportItem {
  const now = Date.now();
  const later = (h: number) => new Date(now + h * 3600000).toISOString();
  const common = {
    item_id: suffix,
    provenance: {
      namespace: "qa-authorized",
      external_id: suffix,
      official_url: `https://example.org/qa/${suffix}`,
      rights_basis: "organizer_permission" as const,
      rights_reference:
        "ISOLATED SYNTHETIC authorization fixture; never production",
      coordinate_method: "official_geodata" as const,
      verified_at: later(-1),
      verified_by: "QA-only",
    },
  };
  const place = {
    address: `Rua QA ${suffix}, 100`,
    city: "São Paulo",
    state: "SP" as const,
    lat: -23.55,
    lng: -46.63,
  };
  return kind === "event"
    ? {
        ...common,
        kind,
        data: {
          ...place,
          name: `SINTÉTICO evento ${suffix}`,
          event_type: "music",
          venue_name: `SINTÉTICO arena ${suffix}`,
          starts_at: later(1),
          expected_end_at: later(3),
          egress_start_at: later(2.5),
          egress_end_at: later(4),
          end_time_basis: "official",
        },
      }
    : {
        ...common,
        kind,
        data: {
          ...place,
          canonical_name: `SINTÉTICO local ${suffix}`,
          poi_type: "cultural",
        },
      };
}
export function registryFor(items: ImportItem[]): Registry {
  return {
    namespace: "qa-authorized",
    revision: 1,
    enabled: true,
    approved_by: "qa-isolated-admin",
    approved_at: new Date().toISOString(),
    domains: ["example.org"],
    rights: ["organizer_permission"],
    upstream_sources: ["ticketmaster", "agenda_sp"],
    attestations: Object.fromEntries(
      items.map((i) => [
        `${i.kind}:${i.provenance.external_id}`,
        materialHash(i),
      ]),
    ),
  };
}
export const envelope = (items: unknown[]) => ({ schema: SCHEMA, items });
