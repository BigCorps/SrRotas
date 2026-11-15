import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";
import type { Pool } from "pg";
import { Sandbox } from "./experiment";
import { fixture, registryFor, envelope } from "./fixtures";
import {
  previewImport,
  type ImportItem,
} from "../../../src/radar-admin-import/validation";
import { nearbyEventRadar026 } from "../../../src/event-radar-026";
import { previewHandler } from "../../../src/radar-admin-import/preview-service";
export let sandbox: Sandbox;
let db: Pool;
export async function setupAdminImportSandbox(database: Pool) {
  db = database;
  const nonce = randomUUID();
  await db.query(
    readFileSync(new URL("./schema.sql", import.meta.url), "utf8"),
  );
  await db.query("insert into radar_admin_sandbox.marker values($1)", [nonce]);
  sandbox = new Sandbox(db, nonce);
}

export function registerAdminImportTests(
  getDb: () => Pool,
  request: (
    path: string,
    method?: string,
    body?: unknown,
    token?: string,
    admin?: boolean,
    origin?: boolean,
  ) => Request,
  actor: string,
  externalReceive: (i: ImportItem) => Promise<Response>,
) {
  async function authorize(items: ImportItem[]) {
    const r = registryFor(items);
    await db.query(
      "insert into radar_admin_sandbox.namespaces values($1,$2,$3) on conflict(namespace) do update set revision=excluded.revision,document=excluded.document",
      [r.namespace, r.revision, r],
    );
  }
  const capture = async () =>
    (
      await db.query(
        `select (select count(*) from sr_event_opportunities) e,(select count(*) from sr_radar_pois) p,(select count(*) from radar_admin_sandbox.batches) b,(select count(*) from radar_admin_sandbox.items) i,(select count(*) from radar_admin_sandbox.audit) a`,
      )
    ).rows[0];
  test("admin import: namespace registration version/approval/audit and concurrent configuration conflict", async () => {
    const reg = registryFor([fixture("registered")]);
    reg.namespace = "qa-registered";
    await sandbox.registerNamespace(reg, reg.approved_by, 0);
    await assert.rejects(
      sandbox.registerNamespace(
        { ...reg, namespace: "ticketmaster" },
        reg.approved_by,
        0,
      ),
      /INVALID_REGISTRY/,
    );
    await assert.rejects(
      sandbox.registerNamespace(reg, "another-actor", 0),
      /INVALID_REGISTRY/,
    );
    await assert.rejects(
      sandbox.registerNamespace(
        { ...reg, namespace: "agenda_sp" },
        reg.approved_by,
        0,
      ),
      /COLLECTOR_NAMESPACE_RESERVED/,
    );
    const next = { ...reg, revision: 2 };
    const outcomes = await Promise.allSettled([
      sandbox.registerNamespace(next, next.approved_by, 1),
      sandbox.registerNamespace(
        { ...next, enabled: false },
        next.approved_by,
        1,
      ),
    ]);
    assert.equal(outcomes.filter((o) => o.status === "fulfilled").length, 1);
    assert.equal(
      Number(
        (
          await db.query(
            "select count(*) from radar_admin_sandbox.registry_audit where namespace=$1",
            [reg.namespace],
          )
        ).rows[0].count,
      ),
      2,
    );
  });
  test("admin import: authenticated read-only preview, ready/invalid/pending and no business writes", async () => {
    const good = fixture("preview"),
      bad = {
        ...fixture("invalid"),
        data: { ...fixture("invalid").data, lat: 0, lng: 0 },
      },
      unknown = fixture("unknown");
    await authorize([good]);
    const before = await capture();
    const handler = previewHandler(() => sandbox.snapshot());
    const r = await handler(
      request(
        "/api/v1/admin/radar/manual-import/preview",
        "POST",
        envelope([good, bad, unknown]),
        undefined,
        true,
      ),
    );
    assert.equal(r.status, 200);
    const p = await r.json();
    assert.deepEqual(
      p.items.map((i: any) => i.classification),
      ["ready", "invalid", "pending"],
    );
    assert.equal(p.commit_enabled, false);
    assert.deepEqual(await capture(), before);
    for (const [admin, origin, key, status] of [
      [false, true, undefined, 401],
      [true, false, undefined, 403],
      [true, true, "srrk_not_authorized", 403],
    ] as const) {
      const response = await handler(
        request(
          "/api/v1/admin/radar/manual-import/preview",
          "POST",
          envelope([good]),
          key,
          admin,
          origin,
        ),
      );
      assert.equal(response.status, status);
    }
  });
  test("admin import: one transaction selected only, safe defaults, persistent receipt, replay after expiry", async () => {
    const a = fixture("commit-a"),
      b = fixture("commit-b", "poi"),
      unused = fixture("unused");
    await authorize([a, b, unused]);
    const body = envelope([a, b, unused]);
    const p = await sandbox.preview(body, actor, "session");
    const args = {
      envelope: body,
      selected: [a.item_id, b.item_id],
      token: p.preview_token,
      key: randomUUID(),
      actor,
      session: "session",
    };
    const first = await sandbox.commit(args);
    assert.equal(first.status, 201);
    const receipt = first.receipt;
    assert.equal(receipt.items.length, 2);
    assert.equal(
      (await sandbox.receipt(receipt.batch_id, actor)).batch_id,
      receipt.batch_id,
    );
    assert.equal(await sandbox.receipt(receipt.batch_id, "other"), null);
    const e = (
      await db.query("select * from sr_event_opportunities where id=$1", [
        receipt.items[0].target_id,
      ])
    ).rows[0];
    assert.equal(e.review_status, "pending");
    assert.equal(e.status, "active");
    assert.equal(e.end_time_source, "source");
    assert.equal(e.country_code, "BR");
    assert.equal(e.collector_id, null);
    const poi = (
      await db.query("select * from sr_radar_pois where id=$1", [
        receipt.items[1].target_id,
      ])
    ).rows[0];
    assert.equal(poi.status, "inactive");
    assert.equal(poi.metadata.review, "pending");
    assert.ok(poi.normalized_name);
    assert.match(poi.geo_cell, /^g2:/);
    assert.equal(poi.source_url, b.provenance.official_url);
    const count = await capture();
    const replay = await sandbox.commit({
      ...args,
      selected: [b.item_id, a.item_id],
      token: "expired-or-lost",
    });
    assert.equal(replay.status, 200);
    assert.deepEqual(replay.receipt, receipt);
    assert.deepEqual(await capture(), count);
    await assert.rejects(
      sandbox.commit({ ...args, selected: [a.item_id] }),
      /IDEMPOTENCY_CONFLICT/,
    );
    await assert.rejects(
      sandbox.commit({ ...args, actor: "other" }),
      /IDEMPOTENCY_SCOPE_MISMATCH/,
    );
    const duplicate = previewImport(envelope([a]), await sandbox.snapshot());
    assert.equal(duplicate.items[0].classification, "duplicate");
    await db.query(
      "update sr_event_opportunities set name=name||' alterado materialmente' where id=$1",
      [receipt.items[0].target_id],
    );
    assert.equal(
      previewImport(envelope([a]), await sandbox.snapshot()).items[0]
        .classification,
      "pending",
    );
  });
  test("admin import: existing review RPC + readers require human approval and versioned decisions", async () => {
    const i = fixture("human-review");
    await authorize([i]);
    const body = envelope([i]);
    const p = await sandbox.preview(body, actor, "s");
    const result = await sandbox.commit({
      envelope: body,
      selected: [i.item_id],
      token: p.preview_token,
      key: randomUUID(),
      actor,
      session: "s",
    });
    const id = result.receipt.items[0].target_id;
    const read = async () =>
      nearbyEventRadar026({
        lat: i.data.lat,
        lng: i.data.lng,
        hours: 8,
        radiusKm: 15,
      });
    assert.ok(!(await read()).opportunities.some((r: any) => r.id === id));
    const version = Number(
      (
        await db.query(
          "select row_version from sr_event_opportunities where id=$1",
          [id],
        )
      ).rows[0].row_version,
    );
    await db.query(
      "select sr_radar_review_v2('event',$1,$2,'approve','Conferido por humano de QA',$3)",
      [id, version, actor],
    );
    assert.ok((await read()).opportunities.some((r: any) => r.id === id));
    await assert.rejects(
      db.query(
        "select sr_radar_review_v2('event',$1,$2,'reject','Concorrente',$3)",
        [id, version, actor],
      ),
      /conflict/,
    );
    await db.query(
      "select sr_radar_review_v2('event',$1,$2,'unpublish','Retirada humana QA',$3)",
      [id, version + 1, actor],
    );
    assert.ok(!(await read()).opportunities.some((r: any) => r.id === id));
    const poi = fixture("human-review-poi", "poi");
    await authorize([poi]);
    const poiBody = envelope([poi]);
    const pp = await sandbox.preview(poiBody, actor, "s");
    const pr = await sandbox.commit({
      envelope: poiBody,
      selected: [poi.item_id],
      token: pp.preview_token,
      key: randomUUID(),
      actor,
      session: "s",
    });
    const poiId = pr.receipt.items[0].target_id;
    const getPoi = async () =>
      (await db.query("select * from sr_radar_pois where id=$1", [poiId]))
        .rows[0];
    const beforePoi = await getPoi();
    assert.equal(beforePoi.status, "inactive");
    assert.equal(beforePoi.metadata.review, "pending");
    await db.query(
      "select sr_radar_review_v2('poi',$1,$2,'approve','Fonte conferida por humano',$3)",
      [poiId, beforePoi.row_version, actor],
    );
    assert.equal((await getPoi()).status, "active");
    assert.equal((await getPoi()).metadata.review, "approved");
    await db.query(
      "select sr_radar_review_v2('poi',$1,$2,'unpublish','Retirada humana',$3)",
      [poiId, Number(beforePoi.row_version) + 1, actor],
    );
    assert.equal((await getPoi()).status, "inactive");
  });
  test("admin import: rollback after target and after audit, no partial batch/entities", async () => {
    for (const fail of [1, -1]) {
      const i = fixture("rollback" + fail);
      await authorize([i]);
      const body = envelope([i]);
      const p = await sandbox.preview(body, actor, "session");
      const before = await capture();
      await assert.rejects(
        sandbox.commit(
          {
            envelope: body,
            selected: [i.item_id],
            token: p.preview_token,
            key: randomUUID(),
            actor,
            session: "session",
          },
          fail,
        ),
        /INJECTED/,
      );
      assert.deepEqual(await capture(), before);
    }
  });
  test("admin import: concurrent same-key replay; different keys stale; expired/tampered/session/selection rejected", async () => {
    const i = fixture("parallel");
    await authorize([i]);
    const body = envelope([i]);
    const p = await sandbox.preview(body, actor, "s");
    const args = {
      envelope: body,
      selected: [i.item_id],
      token: p.preview_token,
      key: randomUUID(),
      actor,
      session: "s",
    };
    await assert.rejects(
      sandbox.commit({ ...args, session: "other" }),
      /PREVIEW_STALE/,
    );
    await assert.rejects(
      sandbox.commit({ ...args, token: p.preview_token + "x" }),
      /PREVIEW_STALE/,
    );
    await assert.rejects(
      sandbox.commit({ ...args, selected: ["not-ready"] }),
      /SELECTION_NOT_READY/,
    );
    const expired = await sandbox.preview(body, actor, "s", Date.now() - 1);
    await assert.rejects(
      sandbox.commit({ ...args, token: expired.preview_token }),
      /PREVIEW_EXPIRED/,
    );
    const [a, b] = await Promise.all([
      sandbox.commit(args),
      sandbox.commit(args),
    ]);
    assert.equal(a.receipt.batch_id, b.receipt.batch_id);
    assert.deepEqual([a.status, b.status].sort(), [200, 201]);
    await assert.rejects(
      sandbox.commit({ ...args, key: randomUUID() }),
      /PREVIEW_STALE/,
    );
  });
  test("admin import: simultaneous different keys create exactly one administrative identity", async () => {
    const i = fixture("different-keys");
    await authorize([i]);
    const body = envelope([i]);
    const p = await sandbox.preview(body, actor, "s");
    const args = {
      envelope: body,
      selected: [i.item_id],
      token: p.preview_token,
      actor,
      session: "s",
    };
    const outcomes = await Promise.allSettled([
      sandbox.commit({ ...args, key: randomUUID() }),
      sandbox.commit({ ...args, key: randomUUID() }),
    ]);
    assert.equal(outcomes.filter((o) => o.status === "fulfilled").length, 1);
    const rejected = outcomes.find(
      (o) => o.status === "rejected",
    ) as PromiseRejectedResult;
    assert.match(rejected.reason.message, /PREVIEW_STALE/);
    assert.equal(
      Number(
        (
          await db.query(
            "select count(*) from sr_event_opportunities where source='admin_manual' and name=$1",
            [i.kind === "event" ? i.data.name : ""],
          )
        ).rows[0].count,
      ),
      1,
    );
  });
  test("admin import: actual SQL audit failure rolls back entities, items, receipt and batch", async () => {
    const i = fixture("audit-sql-failure");
    await authorize([i]);
    const body = envelope([i]);
    const p = await sandbox.preview(body, actor, "s");
    const before = await capture();
    await db.query(
      "alter table radar_admin_sandbox.audit add constraint inject_failure check(action<>'commit') not valid",
    );
    try {
      await assert.rejects(
        sandbox.commit({
          envelope: body,
          selected: [i.item_id],
          token: p.preview_token,
          key: randomUUID(),
          actor,
          session: "s",
        }),
        /inject_failure/,
      );
      assert.deepEqual(await capture(), before);
    } finally {
      await db.query(
        "alter table radar_admin_sandbox.audit drop constraint inject_failure",
      );
    }
  });
  test("admin import: revocation between preview and commit, ACL and immutable audit/receipt", async () => {
    const i = fixture("revoked");
    await authorize([i]);
    const body = envelope([i]);
    const p = await sandbox.preview(body, actor, "s");
    await db.query(
      "update radar_admin_sandbox.namespaces set document=jsonb_set(document,'{enabled}','false')",
    );
    const before = await capture();
    await assert.rejects(
      sandbox.commit({
        envelope: body,
        selected: [i.item_id],
        token: p.preview_token,
        key: randomUUID(),
        actor,
        session: "s",
      }),
      /PREVIEW_STALE/,
    );
    assert.deepEqual(await capture(), before);
    for (const role of ["anon", "authenticated", "service_role"]) {
      const c = await db.connect();
      try {
        await c.query("set role " + role);
        await assert.rejects(
          c.query("select * from radar_admin_sandbox.batches"),
          /permission denied/,
        );
      } finally {
        await c.query("reset role");
        c.release();
      }
    }
    await assert.rejects(
      db.query("update radar_admin_sandbox.audit set action='tampered'"),
      /append_only/,
    );
    await assert.rejects(
      db.query("delete from radar_admin_sandbox.batches"),
      /append_only/,
    );
  });
  test("admin import: cross-writer negative proof keeps ALL deployed commits disabled", async () => {
    // Existing Ticketmaster/collector writes use source-scoped uniqueness, ignoring the admin lock.
    const i = fixture("cross-writer");
    await authorize([i]);
    const body = envelope([i]);
    const p = await sandbox.preview(body, actor, "s");
    const created = await sandbox.commit({
      envelope: body,
      selected: [i.item_id],
      token: p.preview_token,
      key: randomUUID(),
      actor,
      session: "s",
    });
    const locker = await db.connect();
    try {
      await locker.query("begin");
      await locker.query("select pg_advisory_xact_lock(921822)");
      // Actual collector HTTP/RPC and native Ticketmaster-style SQL ignore the admin lock.
      const foreign = externalReceive(i);
      const d = i.kind === "event" ? i.data : null;
      if (!d) throw Error();
      await db.query(
        `insert into sr_event_opportunities(source,external_id,name,event_type,venue_name,address,city,state,lat,lng,starts_at,expected_end_at,egress_start_at,egress_end_at,status,review_status) values('ticketmaster',$1,$2,'music',$3,$4,'São Paulo','SP',$5,$6,$7,$8,$9,$10,'active','pending')`,
        [
          i.provenance.external_id,
          d.name,
          d.venue_name,
          d.address,
          d.lat,
          d.lng,
          d.starts_at,
          d.expected_end_at,
          d.egress_start_at,
          d.egress_end_at,
        ],
      );
      const res = await foreign;
      assert.equal(res.status, 200, JSON.stringify(await res.clone().json()));
      assert.equal(
        Number(
          (
            await db.query(
              "select count(distinct source) from sr_event_opportunities where name=$1",
              [d.name],
            )
          ).rows[0].count,
        ),
        3,
      );
      console.log(
        "ADMIN_IMPORT_CROSS_WRITER_GATE: reproduced three sources for same occurrence; deployed commit=false.",
      );
    } finally {
      await locker.query("rollback");
      locker.release();
    }
    const after = await sandbox.preview(body, actor, "s");
    assert.equal(after.commit_enabled, false);
    assert.equal(after.block_code, "DEDUP_COORDINATION_UNAVAILABLE");
    assert.notEqual(after.items[0].classification, "ready");
    assert.ok(created.receipt.batch_id);
  });
}
