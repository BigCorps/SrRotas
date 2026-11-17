import { test } from "node:test";
import assert from "node:assert/strict";
import { previewExternalV2, EXTERNAL_V2_SCHEMA } from "../../src/radar-admin-import/external-v2-preview";
import type { Snapshot } from "../../src/radar-admin-import/validation";

const snapshot: Snapshot = {
  registry: [], registryAvailable: false, existing: [], complete: true,
};
const poi = (ref: string, extra: Record<string, unknown> = {}) => ({
  client_ref: ref, canonical_name: "Mercure Centro São Paulo", poi_type: "hotel",
  city: "São Paulo", state: "SP", address: "Rua Exemplo 200",
  lat: -23.55, lng: -46.63,
  evidence: [{ kind: "web", url: "https://example.com/local", note: "Fonte pública para conferir" }],
  ...extra,
});
const envelope = (pois: unknown[], events: unknown[] = []) => ({
  schema_version: EXTERNAL_V2_SCHEMA,
  batch_id: "7f1a3575-bcd9-4686-9e11-d058bfcafad9",
  events, pois,
});

test("v2 source envelope yields individual conservative review, not import approval", () => {
  const result = previewExternalV2(envelope([
    poi("sp-centro-1"), poi("sp-centro-2", { lat: undefined, lng: undefined }),
    poi("sp-centro-3", { canonical_name: "Sacolão Municipal", poi_type: "category_not_mapped", address: undefined }),
  ]), snapshot);
  assert.equal(result.input_schema, EXTERNAL_V2_SCHEMA);
  assert.equal(result.original_batch_id, "7f1a3575-bcd9-4686-9e11-d058bfcafad9");
  assert.deepEqual(result.counts, { ready: 1, duplicate: 0, pending: 2, invalid: 0 });
  assert.ok(result.items[0].reasons.some(x => x.code === "REVIEW_BEFORE_PUBLICATION"));
  assert.ok(result.items[1].reasons.some(x => x.code === "PIN_REQUIRED"));
  assert.ok(result.items[2].reasons.some(x => x.code === "CATEGORY_MAPPING_REQUIRED"));
  assert.equal(result.commit_enabled, false);
  assert.equal(result.block_code, "DEDUP_COORDINATION_UNAVAILABLE");
});

test("v2 malformed records and repeated refs are identified per item", () => {
  const result = previewExternalV2(envelope([
    poi("sp-center-repeat"), poi("sp-center-repeat"),
    poi("bad-ref", { lat: 99, lng: 999 }),
  ]), snapshot);
  assert.deepEqual(result.counts, { ready: 0, duplicate: 0, pending: 0, invalid: 3 });
  assert.ok(result.items[0].reasons.some(x => x.code === "DUPLICATE_CLIENT_REF"));
  assert.ok(result.items[2].reasons.some(x => x.code === "INVALID_COORDINATES"));
});

test("v2 not mapped to draft 0.2, other envelopes are rejected, event remains pending", () => {
  assert.throws(() => previewExternalV2({ schema: "radar.admin-import.v2", items: [] },snapshot), /INVALID_ENVELOPE/);
  assert.throws(() => previewExternalV2({ ...envelope([poi("id")]), malicious: true },snapshot), /INVALID_ENVELOPE/);
  const result = previewExternalV2(envelope([], [{
    client_ref: "event-1", event_type: "music", name: "Show de exemplo", city: "São Paulo",state:"SP",
  }]),snapshot);
  assert.equal(result.items[0].classification, "pending");
  assert.ok(result.items[0].reasons.some(x => x.code === "EVENT_V2_NOT_MAPPED"));
});


test("cross-source matching distinguishes certain duplicate from reviewable suspicion", () => {
  const exact = { id: "source-1", kind:"poi" as const, source:"ticketmaster",
    external_id:"old",name:"Mercure Centro São Paulo",address:"Rua Exemplo 200",
    lat:-23.55,lng:-46.63 };
  const close = { ...exact,id:"source-2",name:"Mercure Hotel São Paulo Centro",
    lat:-23.5505,lng:-46.6304 };
  const res=previewExternalV2(envelope([
    poi("centro-first"),poi("centro-other",{canonical_name:"Outro Centro",
       lat:-23.5503,lng:-46.6303,address:"Outra Rua 10"}),
  ]), {...snapshot,existing:[exact,close]});
  assert.equal(res.items[0].classification,"duplicate");
  assert.ok(res.items[0].candidates?.some(c=>c.match==="exact"));
  assert.equal(res.commit_enabled,false);
});
