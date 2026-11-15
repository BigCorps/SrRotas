/**
 * Conservative, read-only preview for the externally supplied Radar v1.3-style
 * envelope (schema_version=radar.admin-import.v2). This is not the draft0.2
 * writer contract: no ownership, rights, coordinates or category are inferred.
 * Uploaded bytes and the upstream batch_id are never written or migrated here.
 */
import {
  digest, ImportError,
  type Snapshot, type ItemResult, type Preview,
} from "./validation";

export const EXTERNAL_V2_SCHEMA = "radar.admin-import.v2";
const supportedPoiTypes = new Set([
  "music", "sports", "theatre", "fair_convention", "cultural",
  "mall", "hospital", "hotel", "airport", "bus_terminal", "mobility_hub",
]);
const BR_STATES = new Set([
  "AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS",
  "MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO",
]);
const asObject = (value: unknown): Record<string, unknown> | null =>
  value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown> : null;
const text = (value: unknown): string =>
  typeof value === "string" ? value.normalize("NFC").trim() : "";
const reason = (code: string, message: string, field?: string) => ({
  code, message, ...(field ? { field } : {}),
});
const meters = (a: { lat: number; lng: number }, b: { lat: number; lng: number }) => {
  const rad = Math.PI / 180;
  const h = Math.sin((a.lat-b.lat)*rad/2)**2 +
    Math.cos(a.lat*rad)*Math.cos(b.lat*rad)*Math.sin((a.lng-b.lng)*rad/2)**2;
  return 12742000 * Math.asin(Math.sqrt(Math.min(1,h)));
};
const comparable = (v: string) =>
  v.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase()
    .replace(/[^a-z0-9]+/g, " ").trim();

export function previewExternalV2(input: unknown, snapshot: Snapshot): Preview {
  const env = asObject(input);
  if (!env || env.schema_version !== EXTERNAL_V2_SCHEMA ||
      !Array.isArray(env.events) || !Array.isArray(env.pois) ||
      typeof env.batch_id !== "string" ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(env.batch_id) ||
      Object.keys(env).some(k => !["schema_version","batch_id","events","pois"].includes(k)) ||
      env.events.length + env.pois.length < 1 ||
      env.events.length + env.pois.length > 200) {
    throw new ImportError("INVALID_ENVELOPE");
  }
  const rows: ItemResult[] = [];
  for (const [kind, collection] of [
    ["event", env.events], ["poi", env.pois],
  ] as const) {
    for (const [index, unknownRow] of collection.entries()) {
      const p = asObject(unknownRow);
      const ref = text(p?.client_ref);
      const name = text(kind === "event" ? (p?.name ?? p?.event_name) : p?.canonical_name);
      const type = text(kind === "event" ? p?.event_type : p?.poi_type);
      const result: ItemResult = {
        item_id: ref || `invalid-${kind}-${index+1}`,
        kind, classification: "pending", reasons: [], related_ids: [],
        display_name: name || `Registro ${index+1} sem nome`,
        display_type: type || "Tipo não informado",
        display_location: [text(p?.address),text(p?.city),text(p?.state)]
          .filter(Boolean).join(" · "),
      };
      const invalid = (code: string, message: string, field?: string) => {
        result.classification = "invalid";
        result.reasons.push(reason(code,message,field));
      };
      const pending = (code: string, message: string, field?: string) =>
        result.reasons.push(reason(code,message,field));

      if (!p) {
        invalid("INVALID_RECORD","Registro precisa ser um objeto.");
        rows.push(result); continue;
      }
      if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$/.test(ref))
        invalid("INVALID_CLIENT_REF","Identificador client_ref ausente ou inválido.","client_ref");
      if (name.length < 3 || name.length > 200)
        invalid("INVALID_NAME","Nome ausente ou fora do limite.","canonical_name");
      if (!/^[a-z][a-z0-9_]{1,63}$/.test(type))
        invalid("INVALID_TYPE","Tipo ausente ou estruturalmente inválido.","poi_type");
      const city = text(p.city), state = text(p.state);
      if (!city || city.length > 100 || !BR_STATES.has(state))
        invalid("INVALID_LOCATION","Cidade ou UF inválida.","city/state");

      if (kind === "event") {
        // Event v2 examples and time rules were not supplied. Do not silently
        // reinterpret these as draft0.2 event items or claim they are ready.
        pending("EVENT_V2_NOT_MAPPED",
          "Evento v2 ainda precisa de mapeamento dos horários e evidências; revisão manual obrigatória.");
      } else {
        const hasLat = p.lat !== undefined && p.lat !== null;
        const hasLng = p.lng !== undefined && p.lng !== null;
        if (hasLat !== hasLng)
          pending("INCOMPLETE_PIN","Latitude e longitude devem ser fornecidas juntas.","lat/lng");
        else if (!hasLat)
          pending("PIN_REQUIRED","Local sem coordenada; conferir pino e acesso veicular.","lat/lng");
        else if (typeof p.lat !== "number" || typeof p.lng !== "number" ||
                 !Number.isFinite(p.lat) || !Number.isFinite(p.lng) ||
                 p.lat < -34 || p.lat > 5.5 || p.lng < -74.5 || p.lng > -28.5)
          invalid("INVALID_COORDINATES","Coordenadas inválidas ou fora do Brasil.","lat/lng");
        else
          pending("PIN_NOT_ATTESTED","Pino de referência deve ser conferido; não presume acesso do motorista.");

        if (!text(p.address))
          pending("ADDRESS_REQUIRED","Endereço não informado; confirmar portaria e via.","address");
        if (!supportedPoiTypes.has(type))
          pending("CATEGORY_MAPPING_REQUIRED",
            "Categoria v2 não possui mapeamento aprovado no Radar atual.","poi_type");

        const evidence = p.evidence;
        if (!Array.isArray(evidence) || evidence.length === 0)
          pending("EVIDENCE_REQUIRED","Local sem referência de fonte.","evidence");
        else if (evidence.length > 30)
          invalid("TOO_MANY_EVIDENCE","Limite de 30 referências por registro.","evidence");
        else {
          for (const [n,e] of evidence.entries()) {
            const v = asObject(e);
            const url = text(v?.url);
            let validUrl = false;
            try {
              const u = new URL(url);
              validUrl = u.protocol === "https:" && !u.username && !u.password &&
                u.hostname.includes(".") && !/(^|\.)(local|localhost|internal)$/.test(u.hostname);
            } catch { /* malformed URL is not evidence */ }
            if (!validUrl)
              pending("EVIDENCE_URL_REVIEW","URL da fonte precisa ser conferida.",
                `evidence.${n}.url`);
          }
        }
        pending("RIGHTS_NOT_ATTESTED",
          "Referência de pesquisa não comprova direitos, licença nem verificação humana.");
      }
      if (!snapshot.complete)
        pending("DEDUP_SNAPSHOT_INCOMPLETE","Consulta de duplicidades incompleta.");
      if (kind === "poi" && typeof p.lat === "number" && typeof p.lng === "number" &&
          Number.isFinite(p.lat) && Number.isFinite(p.lng)) {
        for (const e of snapshot.existing) {
          if (e.kind !== "poi" || !Number.isFinite(e.lat) || !Number.isFinite(e.lng))
            continue;
          const sameRef = e.external_id === ref;
          const samePlace = comparable(e.name) === comparable(name) &&
            meters({lat:p.lat,lng:p.lng},e) <= 100;
          if (sameRef || samePlace) {
            result.related_ids.push(e.id);
            pending("POSSIBLE_EXISTING_POI",
              "Possível local existente; conferir identidade, fonte e portaria.");
          }
        }
      }
      rows.push(result);
    }
  }
  for (const row of rows) {
    if (!row.item_id.startsWith("invalid-") &&
        rows.filter(r => r.kind === row.kind && r.item_id === row.item_id).length > 1) {
      row.classification = "invalid";
      row.reasons.push(reason("DUPLICATE_CLIENT_REF","client_ref repetido no arquivo."));
    }
  }
  const counts = Object.fromEntries(
    ["ready","duplicate","pending","invalid"].map(k => [k,rows.filter(r => r.classification === k).length]),
  );
  return {
    schema: EXTERNAL_V2_SCHEMA,
    input_schema: EXTERNAL_V2_SCHEMA,
    original_batch_id: env.batch_id,
    items: rows,
    counts,
    fingerprint: digest(snapshot),
    envelope_hash: digest(input),
    commit_enabled: false,
    block_code: "DEDUP_COORDINATION_UNAVAILABLE",
  };
}
