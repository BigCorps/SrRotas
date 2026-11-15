import { createHash } from "node:crypto";
import { isIP } from "node:net";
import { z } from "zod-v4";
import { nameSimilarityV1 } from "../radar-poi-resolver-v1";

export const SCHEMA = "srrotas.radar-admin-import.draft.v0.2";
export const MAX_BYTES = 2_000_000;
export class ImportError extends Error {
  constructor(
    public code: string,
    public status = 400,
  ) {
    super(code);
  }
}
/** Sorted JSON for this restricted JSON domain (finite numbers, NFC strings, no undefined). */
export function canonical(value: any): string {
  if (value === undefined) return "null";
  if (value instanceof Date) return JSON.stringify(value.toISOString());
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value && typeof value === "object")
    return `{${Object.keys(value)
      .filter((k) => value[k] !== undefined)
      .sort()
      .map((k) => JSON.stringify(k) + ":" + canonical(value[k]))
      .join(",")}}`;
  return JSON.stringify(value);
}
export const digest = (v: unknown) =>
  createHash("sha256").update(canonical(v)).digest("hex");
export function parseStrict(bytes: Uint8Array): unknown {
  if (bytes.byteLength > MAX_BYTES)
    throw new ImportError("PAYLOAD_TOO_LARGE", 413);
  let source: string;
  try {
    source = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    throw new ImportError("INVALID_UTF8");
  }
  let i = 0;
  const ws = () => {
    while (/\s/.test(source[i] ?? "") && i < source.length) i++;
  };
  const str = (): string => {
    const start = i++;
    while (i < source.length) {
      const c = source[i++];
      if (c === "\\") i++;
      else if (c === '"') return JSON.parse(source.slice(start, i));
    }
    throw new ImportError("INVALID_JSON");
  };
  const value = (depth: number): void => {
    if (depth > 12) throw new ImportError("MAX_DEPTH");
    ws();
    const c = source[i];
    if (c === '"') {
      str();
      return;
    }
    if (c === "{" || c === "[") {
      i++;
      ws();
      const close = c === "{" ? "}" : "]",
        keys = new Set<string>();
      if (source[i] === close) {
        i++;
        return;
      }
      while (i < source.length) {
        if (c === "{") {
          ws();
          if (source[i] !== '"') throw new ImportError("INVALID_JSON");
          const key = str();
          if (keys.has(key)) throw new ImportError("DUPLICATE_JSON_KEY");
          keys.add(key);
          ws();
          if (source[i++] !== ":") throw new ImportError("INVALID_JSON");
        }
        value(depth + 1);
        ws();
        if (source[i] === close) {
          i++;
          return;
        }
        if (source[i++] !== ",") throw new ImportError("INVALID_JSON");
      }
      throw new ImportError("INVALID_JSON");
    }
    const start = i;
    while (i < source.length && !/[\s,\]}]/.test(source[i])) i++;
    if (start === i) throw new ImportError("INVALID_JSON");
  };
  try {
    value(0);
    ws();
    if (i !== source.length) throw new Error();
    return JSON.parse(source);
  } catch (e) {
    if (e instanceof ImportError) throw e;
    throw new ImportError("INVALID_JSON");
  }
}
export async function readImport(request: Request): Promise<unknown> {
  if (
    request.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !==
    "application/json"
  )
    throw new ImportError("JSON_REQUIRED", 415);
  if (request.headers.has("content-encoding"))
    throw new ImportError("JSON_REQUIRED", 415);
  const reader = request.body?.getReader();
  if (!reader) throw new ImportError("INVALID_JSON");
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    for (;;) {
      const r = await reader.read();
      if (r.done) break;
      size += r.value.byteLength;
      if (size > MAX_BYTES) {
        await reader.cancel();
        throw new ImportError("PAYLOAD_TOO_LARGE", 413);
      }
      chunks.push(r.value);
    }
  } finally {
    reader.releaseLock();
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const c of chunks) {
    bytes.set(c, offset);
    offset += c.length;
  }
  return parseStrict(bytes);
}
const text = (min: number, max: number) =>
  z
    .string()
    .transform((v) => v.normalize("NFC").trim())
    .pipe(
      z
        .string()
        .min(min)
        .max(max)
        .refine(
          (v) => !/[\u0000-\u001f\u007f\ud800-\udfff]/u.test(v),
          "Texto inválido",
        ),
    );
const timestamp = z.iso
  .datetime({ offset: true })
  .transform((v) => new Date(v).toISOString());
const publicUrl = text(1, 2048).refine((v) => {
  try {
    const u = new URL(v),
      host = u.hostname.toLowerCase();
    return (
      u.protocol === "https:" &&
      !u.username &&
      !u.password &&
      !u.hash &&
      !isIP(host.replace(/^\[|\]$/g, "")) &&
      host.includes(".") &&
      !/(^|\.)(localhost|local|internal|test|invalid)$/.test(host)
    );
  } catch {
    return false;
  }
}, "URL HTTPS pública obrigatória");
const place = {
  address: text(3, 500),
  city: text(2, 100),
  state: z.enum([
    "AC",
    "AL",
    "AP",
    "AM",
    "BA",
    "CE",
    "DF",
    "ES",
    "GO",
    "MA",
    "MT",
    "MS",
    "MG",
    "PA",
    "PB",
    "PR",
    "PE",
    "PI",
    "RJ",
    "RN",
    "RS",
    "RO",
    "RR",
    "SC",
    "SP",
    "SE",
    "TO",
  ]),
  lat: z.number().min(-34).max(5.5),
  lng: z.number().min(-74.5).max(-28.5),
};
const provenance = z.strictObject({
  namespace: z.string().regex(/^[a-z][a-z0-9_-]{2,63}$/),
  external_id: text(1, 200),
  official_url: publicUrl,
  rights_basis: z.enum([
    "organizer_permission",
    "commercial_license",
    "original_curation",
  ]),
  rights_reference: text(1, 1000),
  coordinate_method: z.enum([
    "official_geodata",
    "verified_original_survey",
    "licensed_dataset",
  ]),
  verified_at: timestamp,
  verified_by: text(1, 100),
});
const common = {
  item_id: z.string().regex(/^[A-Za-z0-9_-]{1,64}$/),
  provenance,
};
const event = z.strictObject({
  ...common,
  kind: z.literal("event"),
  data: z.strictObject({
    ...place,
    name: text(3, 200),
    event_type: z.enum([
      "music",
      "sports",
      "fair_convention",
      "event",
      "theatre",
      "cultural",
      "family",
    ]),
    venue_name: text(1, 200),
    starts_at: timestamp,
    expected_end_at: timestamp,
    egress_start_at: timestamp,
    egress_end_at: timestamp,
    end_time_basis: z.enum(["official", "declared_estimate"]),
  }),
});
const poi = z.strictObject({
  ...common,
  kind: z.literal("poi"),
  data: z.strictObject({
    ...place,
    canonical_name: text(3, 200),
    poi_type: z.enum([
      "music",
      "sports",
      "theatre",
      "fair_convention",
      "cultural",
      "mall",
      "hospital",
      "hotel",
      "airport",
      "bus_terminal",
      "mobility_hub",
    ]),
  }),
});
const itemSchema = z.discriminatedUnion("kind", [event, poi]);
export type ImportItem = z.infer<typeof itemSchema>;
export type Registry = {
  namespace: string;
  revision: number;
  enabled: boolean;
  approved_by: string;
  approved_at: string;
  domains: string[];
  rights: string[];
  upstream_sources: string[];
  attestations: Record<string, string>;
};
export type Existing = {
  id: string;
  kind: "event" | "poi";
  source: string;
  external_id: string | null;
  name: string;
  address: string | null;
  venue_name?: string | null;
  lat: number;
  lng: number;
  starts_at?: string;
  material_hash?: string;
  row_version?: number;
  status?: string;
  review_status?: string;
};
export type Snapshot = {
  registry: Registry[];
  existing: Existing[];
  complete: boolean;
  registryAvailable: boolean;
};
export const identity = (i: ImportItem) =>
  canonical([i.provenance.namespace, i.provenance.external_id, i.kind]);
export const materialHash = (i: ImportItem) =>
  digest({
    kind: i.kind,
    data: i.data,
    provenance: {
      namespace: i.provenance.namespace,
      external_id: i.provenance.external_id,
      official_url: i.provenance.official_url,
      rights_basis: i.provenance.rights_basis,
      rights_reference: i.provenance.rights_reference,
      coordinate_method: i.provenance.coordinate_method,
    },
  });
export type ItemResult = {
  item_id: string;
  kind: string;
  classification: "ready" | "duplicate" | "pending" | "invalid";
  reasons: { code: string; field?: string; message: string }[];
  item?: ImportItem;
  related_ids: string[];
};
const reason = (code: string, message: string, field?: string) => ({
  code,
  message,
  ...(field ? { field } : {}),
});
function distance(
  a: { lat: number; lng: number },
  b: { lat: number; lng: number },
) {
  const r = Math.PI / 180;
  const h =
    Math.sin(((a.lat - b.lat) * r) / 2) ** 2 +
    Math.cos(a.lat * r) *
      Math.cos(b.lat * r) *
      Math.sin(((a.lng - b.lng) * r) / 2) ** 2;
  return 12742000 * Math.asin(Math.sqrt(Math.min(1, h)));
}
export function previewImport(
  input: unknown,
  snapshot: Snapshot,
  now = Date.now(),
) {
  const env = z
    .strictObject({
      schema: z.literal(SCHEMA),
      items: z.array(z.unknown()).min(1).max(200),
    })
    .safeParse(input);
  if (!env.success) throw new ImportError("INVALID_ENVELOPE");
  const rows: ItemResult[] = env.data.items.map((raw: any, index) => {
    const checked = itemSchema.safeParse(raw);
    if (!checked.success)
      return {
        item_id:
          typeof raw?.item_id === "string" &&
          /^[A-Za-z0-9_-]{1,64}$/.test(raw.item_id)
            ? raw.item_id
            : `invalid-${index + 1}`,
        kind: ["event", "poi"].includes(raw?.kind) ? raw.kind : "unknown",
        classification: "invalid",
        reasons: checked.error.issues.map((e) =>
          reason(
            "INVALID_FIELD",
            "Campo ausente, proibido ou inválido.",
            e.path.join("."),
          ),
        ),
        related_ids: [],
      };
    const item = checked.data,
      p = item.provenance,
      d = item.data;
    const result: ItemResult = {
      item_id: item.item_id,
      kind: item.kind,
      classification: "ready",
      reasons: [],
      item,
      related_ids: [],
    };
    const invalid = (code: string, message: string) => {
      result.classification = "invalid";
      result.reasons.push(reason(code, message));
    };
    const pending = (code: string, message: string) => {
      if (result.classification === "ready") result.classification = "pending";
      result.reasons.push(reason(code, message));
    };
    if (+new Date(p.verified_at) > now + 300000)
      invalid("FUTURE_VERIFICATION", "Verificação está no futuro.");
    if (
      ["ticketmaster", "manual", "admin", "internal", "admin_manual"].includes(
        p.namespace,
      )
    )
      invalid("RESERVED_NAMESPACE", "Namespace reservado.");
    if (item.kind === "event") {
      const [s, e, a, b] = [
        item.data.starts_at,
        item.data.expected_end_at,
        item.data.egress_start_at,
        item.data.egress_end_at,
      ].map((x) => +new Date(x));
      if (
        !(
          s < e &&
          s <= a &&
          a < b &&
          e <= b &&
          b - s <= 72 * 3600000 &&
          b - a <= 12 * 3600000
        )
      )
        invalid("INVALID_CHRONOLOGY", "Cronologia ou duração inválida.");
      if (b <= now) pending("EXPIRED_EVENT", "Saída já encerrada.");
      if (item.data.end_time_basis !== "official")
        pending("ESTIMATED_END", "Horário estimado requer esclarecimento.");
    }
    const reg = snapshot.registry.find(
      (r) => r.namespace === p.namespace && r.enabled,
    );
    if (
      !snapshot.registryAvailable ||
      !reg ||
      !reg.approved_by ||
      !Number.isFinite(Date.parse(reg.approved_at)) ||
      !reg.domains.includes(new URL(p.official_url).hostname.toLowerCase()) ||
      !reg.rights.includes(p.rights_basis)
    )
      pending(
        "SOURCE_NOT_AUTHORIZED",
        "Namespace, domínio e direitos exigem autorização no registro administrativo.",
      );
    if (
      p.namespace.toLowerCase().includes("sintetico") ||
      reg?.attestations[`${item.kind}:${p.external_id}`] !== materialHash(item)
    )
      pending(
        "EVIDENCE_NOT_VERIFIED",
        "Coordenadas, município, horários e proveniência exigem atestação humana do conteúdo exato.",
      );
    if (!snapshot.complete)
      pending(
        "DEDUP_SNAPSHOT_INCOMPLETE",
        "Consulta de duplicidades incompleta; confirmação bloqueada.",
      );
    const matches = snapshot.existing.filter(
      (r) =>
        r.kind === item.kind &&
        ((r.source === "admin_manual" &&
          r.external_id === digest(JSON.parse(identity(item)))) ||
          (reg?.upstream_sources.includes(r.source) &&
            r.external_id === p.external_id)),
    );
    if (matches.length) {
      result.related_ids.push(...matches.map((r) => r.id));
      if (
        matches.every((r) => r.material_hash === materialHash(item)) &&
        result.classification !== "invalid"
      ) {
        result.classification = "duplicate";
        result.reasons.push(
          reason("EXISTING_IDENTITY", "Identidade e conteúdo já recebidos."),
        );
      } else
        pending(
          "IDENTITY_CHANGED",
          "Identidade existente com conteúdo/evidência diferentes; sem sobrescrita.",
        );
    }
    const name =
      item.kind === "event" ? item.data.name : item.data.canonical_name;
    for (const r of snapshot.existing)
      if (
        r.kind === item.kind &&
        !result.related_ids.includes(r.id) &&
        nameSimilarityV1(name, r.name) >= 0.72 &&
        distance(d, r) <= (item.kind === "event" ? 300 : 100) &&
        (item.kind === "poi"
          ? nameSimilarityV1(d.address, r.address ?? "") >= 0.72
          : Math.abs(
              +new Date((item as z.infer<typeof event>).data.starts_at) -
                Date.parse(r.starts_at ?? ""),
            ) <= 900000 &&
            nameSimilarityV1(
              (item as z.infer<typeof event>).data.venue_name,
              r.venue_name ?? "",
            ) >= 0.72)
      ) {
        result.related_ids.push(r.id);
        pending(
          "POSSIBLE_CROSS_SOURCE_DUPLICATE",
          "Possível duplicidade entre fontes; exige revisão, sem fusão automática.",
        );
      }
    return result;
  });
  for (const row of rows) {
    if (rows.filter((r) => r.item_id === row.item_id).length > 1) {
      row.classification = "invalid";
      row.reasons.push(
        reason("DUPLICATE_ITEM_ID", "item_id repetido no arquivo."),
      );
    }
    if (!row.item || row.classification === "invalid") continue;
    const same = rows.filter(
      (r) => r.item && identity(r.item) === identity(row.item!),
    );
    if (same.length > 1) {
      const equal = same.every(
        (r) => materialHash(r.item!) === materialHash(row.item!),
      );
      row.classification = equal ? "duplicate" : "pending";
      row.reasons.push(
        reason(
          equal ? "DUPLICATE_IN_FILE" : "CONFLICT_IN_FILE",
          equal
            ? "Identidade equivalente repetida no arquivo."
            : "Identidade com conteúdos divergentes no arquivo.",
        ),
      );
    }
    // Different upstream IDs can still be the same occurrence/place. Do not admit both in one batch.
    if (
      rows.some(
        (r) =>
          r !== row &&
          r.item &&
          identity(r.item) !== identity(row.item!) &&
          r.kind === row.kind &&
          digest(r.item.data) === digest(row.item!.data),
      )
    ) {
      if (row.classification === "ready") row.classification = "pending";
      row.reasons.push(
        reason(
          "CONTENT_COLLISION_IN_FILE",
          "Mesmo conteúdo com identidades diferentes; esclarecer antes de importar.",
        ),
      );
    }
  }
  return {
    schema: SCHEMA,
    items: rows,
    counts: Object.fromEntries(
      ["ready", "duplicate", "pending", "invalid"].map((k) => [
        k,
        rows.filter((r) => r.classification === k).length,
      ]),
    ),
    fingerprint: digest(snapshot),
    envelope_hash: digest(env.data),
    commit_enabled: false,
    block_code: "DEDUP_COORDINATION_UNAVAILABLE",
  };
}
export type Preview = ReturnType<typeof previewImport>;
