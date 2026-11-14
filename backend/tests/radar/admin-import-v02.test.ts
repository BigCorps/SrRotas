import { test } from "node:test";
import assert from "node:assert/strict";
import {
  parseStrict,
  readImport,
  previewImport,
  SCHEMA,
  digest,
  materialHash,
  type Snapshot,
} from "../../src/radar-admin-import/validation";
import {
  fixture,
  registryFor,
  envelope,
} from "../radar-isolated/admin-import/fixtures";
const bytes = (s: string) => new TextEncoder().encode(s);
const ready = (items: any[]): Snapshot => ({
  registry: [registryFor(items)],
  registryAvailable: true,
  existing: [],
  complete: true,
});
test("strict JSON: repeated decoded keys, UTF8, depth, envelope and streamed limits", async () => {
  for (const raw of [
    '{"a":1,"a":2}',
    '{"a":1,"\\u0061":2}',
    '{"n":{"n":1,"n":2}}',
  ])
    assert.throws(() => parseStrict(bytes(raw)), /DUPLICATE_JSON_KEY/);
  for (const raw of ["{", "[1,]", '{"a":NaN}', '{"n":1} false'])
    assert.throws(() => parseStrict(bytes(raw)), /INVALID_JSON/);
  assert.throws(() => parseStrict(new Uint8Array([0xff])), /INVALID_UTF8/);
  assert.throws(
    () => parseStrict(bytes("[".repeat(14) + "0" + "]".repeat(14))),
    /MAX_DEPTH/,
  );
  assert.throws(
    () =>
      previewImport(
        { schema: "radar.admin-import.v2", items: [fixture()] },
        ready([]),
      ),
    /INVALID_ENVELOPE/,
  );
  const req = new Request("http://localhost", {
    method: "POST",
    headers: { "content-type": "application/json", "content-length": "1" },
    body: new ReadableStream({
      start(c) {
        c.enqueue(new Uint8Array(2_000_001));
        c.close();
      },
    }),
    duplex: "half",
  } as any);
  await assert.rejects(readImport(req), /PAYLOAD_TOO_LARGE/);
});
test("per-item strict types, coordinates, chronology, forbidden fields and public URL", () => {
  const i = fixture();
  for (const change of [
    { lat: 0, lng: 0 },
    { lat: "-23.55" },
    { starts_at: "2026-02-30T10:00:00-03:00" },
    { starts_at: "2027-01-01T10:00:00" },
    { review_status: "approved" },
    { end_time_basis: "source" },
  ]) {
    assert.equal(
      previewImport(
        envelope([{ ...i, data: { ...i.data, ...change } }]),
        ready([i]),
      ).items[0].classification,
      "invalid",
    );
  }
  for (const url of [
    "http://example.org",
    "https://127.0.0.1/x",
    "https://[::1]/",
    "https://user:password@example.org",
    "file:///file",
    "https://a.local",
  ])
    assert.equal(
      previewImport(
        envelope([
          { ...i, provenance: { ...i.provenance, official_url: url } },
        ]),
        ready([i]),
      ).items[0].classification,
      "invalid",
    );
  const poi = fixture("poi", "poi");
  assert.equal(
    previewImport(
      envelope([{ ...poi, data: { ...poi.data, eta: "fake" } }]),
      ready([poi]),
    ).items[0].classification,
    "invalid",
  );
});
test("approved exact content only, estimates, missing source, synthetic reserved, incomplete snapshot", () => {
  const i = fixture();
  assert.equal(
    previewImport(envelope([i]), ready([i])).items[0].classification,
    "ready",
  );
  for (const snapshot of [
    { ...ready([i]), registry: [] },
    { ...ready([i]), complete: false },
  ])
    assert.equal(
      previewImport(envelope([i]), snapshot).items[0].classification,
      "pending",
    );
  const estimate = structuredClone(i);
  if (estimate.kind === "event")
    estimate.data.end_time_basis = "declared_estimate";
  assert.equal(
    previewImport(envelope([estimate]), ready([estimate])).items[0]
      .classification,
    "pending",
  );
  const synthetic = structuredClone(i);
  synthetic.provenance.namespace = "sintetico-fixture";
  const snap = ready([synthetic]);
  snap.registry[0].namespace = synthetic.provenance.namespace;
  assert.equal(
    previewImport(envelope([synthetic]), snap).items[0].classification,
    "pending",
  );
});
test("duplicate IDs all invalid; same upstream equivalent all duplicate/conflicting pending", () => {
  const a = fixture(),
    b = structuredClone(a);
  assert.deepEqual(
    previewImport(envelope([a, b]), ready([a])).items.map(
      (r) => r.classification,
    ),
    ["invalid", "invalid"],
  );
  b.item_id = "b";
  assert.deepEqual(
    previewImport(envelope([a, b]), ready([a])).items.map(
      (r) => r.classification,
    ),
    ["duplicate", "duplicate"],
  );
  b.data.lat = -23.56;
  assert.deepEqual(
    previewImport(envelope([a, b]), ready([a, b])).items.map(
      (r) => r.classification,
    ),
    ["pending", "pending"],
  );
});
test("existing identity and probable cross-source collisions remain blocked", () => {
  const i = fixture();
  if (i.kind !== "event") throw Error();
  const s = ready([i]);
  s.existing = [
    {
      id: "real-id",
      kind: "event",
      source: "ticketmaster",
      external_id: i.provenance.external_id,
      name: i.data.name,
      venue_name: i.data.venue_name,
      address: i.data.address,
      lat: i.data.lat,
      lng: i.data.lng,
      starts_at: i.data.starts_at,
    },
  ];
  assert.equal(
    previewImport(envelope([i]), s).items[0].classification,
    "pending",
  );
  s.existing[0].material_hash = materialHash(i);
  assert.equal(
    previewImport(envelope([i]), s).items[0].classification,
    "duplicate",
  );
  s.existing[0].external_id = "different";
  delete s.existing[0].material_hash;
  assert.equal(
    previewImport(envelope([i]), s).items[0].classification,
    "pending",
  );
});
test("canonical hash sorts keys; audit fields excluded only from material hash", () => {
  const i = fixture(),
    j = structuredClone(i);
  j.provenance.verified_by = "second human";
  assert.equal(materialHash(i), materialHash(j));
  assert.notEqual(digest(i), digest(j));
  assert.equal(digest({ b: 1, a: 2 }), digest({ a: 2, b: 1 }));
  assert.equal(SCHEMA, "srrotas.radar-admin-import.draft.v0.2");
});
