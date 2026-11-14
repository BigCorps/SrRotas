import { GET as adminRuns } from "../../app/api/v1/admin/radar/runs/route";
import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { Pool, type PoolClient } from "pg";
import { randomUUID, createHmac } from "node:crypto";
import { readFileSync } from "node:fs";
import { execFileSync, spawn } from "node:child_process";
import { createServer, request as httpRequest, type Server } from "node:http";
import * as adminKeys from "../../app/api/v1/admin/radar/collectors/route";
import * as adminEvents from "../../app/api/v1/admin/radar/events/route";
import * as adminPois from "../../app/api/v1/admin/radar/pois/route";
import { POST as ingest } from "../../app/api/v1/radar/ingest/route";
import { GET as ping } from "../../app/api/v1/radar/collector/ping/route";
import { POST as pois } from "../../app/api/v1/radar/collector/pois/route";
import { sha256Hex } from "../../src/radar-collectors-v1";
import { validateCollectorBatchV1 } from "../../src/radar-collector-ingest-v1";
import { nearbyEventRadar026 } from "../../src/event-radar-026";
const url = process.env.SR_RADAR_TEST_DATABASE_URL;
if (!url || !["localhost", "127.0.0.1"].includes(new URL(url).hostname))
  throw Error("Use isolated Docker runner. Never production.");
const root = new Pool({ connectionString: url });
const database = "sr_radar_test_" + randomUUID().replaceAll("-", "");
let db: Pool;
let proxy: Server;
const restName = "sr-radar-rest-" + randomUUID();
let key: string;
let collector: any;
const actor = "contato@bigcorps.com.br",
  session = "srimp_test_only_session";
const now = Date.now();
const event = {
  source: "agenda_sp",
  external_id: "ev-1",
  name: "Evento teste",
  event_type: "music",
  venue_name: "Arena",
  address: "Rua A",
  city: "São Paulo",
  state: "SP",
  lat: -23.55,
  lng: -46.63,
  starts_at: new Date(now + 3600000).toISOString(),
  expected_end_at: new Date(now + 10800000).toISOString(),
  egress_start_at: new Date(now + 9600000).toISOString(),
  egress_end_at: new Date(now + 14400000).toISOString(),
  source_url: "https://example.org/event",
  confidence: 0.8,
  status: "active",
};
const request = (
  path: string,
  method = "GET",
  body?: unknown,
  token?: string,
  admin = false,
  origin = true,
) =>
  new Request("https://srrotas.com" + path, {
    method,
    headers: {
      ...(body ? { "content-type": "application/json" } : {}),
      ...(token ? { authorization: "Bearer " + token } : {}),
      ...(admin ? { cookie: "sr_import_admin=" + session } : {}),
      ...(origin ? { origin: "https://srrotas.com" } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
const rpc = async (c: Pool | PoolClient, name: string, args: unknown[]) =>
  (
    await c.query(
      `select ${name}(${args.map((_, i) => "$" + (i + 1)).join(",")}) as result`,
      args,
    )
  ).rows[0].result;
async function receive(
  payload: Record<string, unknown>,
  run = randomUUID(),
  c: Pool | PoolClient = db,
) {
  const b = validateCollectorBatchV1({ events: [payload] }, collector);
  return rpc(c, "sr_radar_receive_v2", [
    collector.id,
    sha256Hex(key),
    "event",
    run,
    sha256Hex(JSON.stringify(payload)),
    JSON.stringify(b.rows),
    "[]",
    1,
    false,
  ]);
}
const review = (
  id: string,
  version: number,
  decision = "approve",
  c: Pool | PoolClient = db,
) =>
  rpc(c, "sr_radar_review_v2", [
    "event",
    id,
    version,
    decision,
    "Fonte conferida",
    actor,
  ]);
const get = async (id: string) =>
  (await db.query("select * from sr_event_opportunities where id=$1", [id]))
    .rows[0];
before(async () => {
  await root.query("create database " + database);
  const target = new URL(url!);
  target.pathname = "/" + database;
  db = new Pool({ connectionString: target.toString(), max: 12 });
  await db.query(
    "do $$ begin if not exists(select 1 from pg_roles where rolname='anon') then create role anon;end if;if not exists(select 1 from pg_roles where rolname='authenticated') then create role authenticated;end if;if not exists(select 1 from pg_roles where rolname='service_role') then create role service_role bypassrls;end if;end $$;",
  );
  // Supabase grants all table privileges to service_role by default.
  // Reproduce production defaults so restrictive migrations cannot pass by omission.
  await db.query(
    "alter default privileges for role postgres in schema public grant all on tables to service_role",
  );
  for (const name of [
    "20260818_security_rate_limit_012.sql",
    "20260902_event_radar_026.sql",
    "20261001210000_radar_poi_context_v1.sql",
    "20261001210100_radar_poi_resolver_v1.sql",
    "20261105120000_radar_collectors_review_v1.sql",
    "20261105122755_radar_collector_hardening_v2.sql",
    "20261105144035_radar_audit_append_only_privileges_v2.sql",
  ])
    await db.query(
      readFileSync(
        new URL("../../../supabase/migrations/" + name, import.meta.url),
        "utf8",
      ),
    );
  await db.query(
    "create table historical_import_web_sessions(id uuid default gen_random_uuid(),auth_user_id uuid,email text,token_hash text,expires_at timestamptz);grant select on historical_import_web_sessions to service_role;",
  );
  await db.query(
    "insert into historical_import_web_sessions(auth_user_id,email,token_hash,expires_at) values($1,$2,$3,now()+interval '1 hour')",
    [randomUUID(), actor, sha256Hex(session)],
  );
  const jwtSecret = "isolated-test-only-jwt-secret-at-least-32-characters";
  execFileSync(
    "docker",
    [
      "run",
      "-d",
      "--name",
      restName,
      "--network",
      process.env.SR_RADAR_TEST_NETWORK!,
      "-p",
      "127.0.0.1::3000",
      "-e",
      `PGRST_DB_URI=postgresql://postgres@${process.env.SR_RADAR_TEST_DB_CONTAINER}:5432/${database}`,
      "-e",
      "PGRST_DB_ANON_ROLE=anon",
      "-e",
      `PGRST_JWT_SECRET=${jwtSecret}`,
      "postgrest/postgrest:v13.0.7",
    ],
    { stdio: "pipe" },
  );
  const port = Number(
    execFileSync("docker", ["port", restName, "3000/tcp"], { encoding: "utf8" })
      .trim()
      .split(":")
      .at(-1),
  );
  proxy = createServer((incoming, outgoing) => {
    const p = httpRequest(
      {
        hostname: "127.0.0.1",
        port,
        path: (incoming.url ?? "/").replace(/^\/rest\/v1/, ""),
        method: incoming.method,
        headers: incoming.headers,
      },
      (r) => {
        outgoing.writeHead(r.statusCode ?? 500, r.headers);
        r.pipe(outgoing);
      },
    );
    p.on("error", () => {
      outgoing.writeHead(503);
      outgoing.end();
    });
    incoming.pipe(p);
  });
  await new Promise<void>((r) => proxy.listen(0, "127.0.0.1", r));
  process.env.SUPABASE_URL = `http://127.0.0.1:${(proxy.address() as { port: number }).port}`;
  const head = Buffer.from(
      JSON.stringify({ alg: "HS256", typ: "JWT" }),
    ).toString("base64url"),
    body = Buffer.from(
      JSON.stringify({
        role: "service_role",
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    ).toString("base64url");
  process.env.SUPABASE_SERVICE_ROLE_KEY =
    head +
    "." +
    body +
    "." +
    createHmac("sha256", jwtSecret)
      .update(head + "." + body)
      .digest("base64url");
  process.env.PAIRING_CODE = "test-only";
  process.env.RADAR_INGEST_SECRET = "test-only-internal";
  for (let i = 0; i < 60; i++) {
    try {
      if ((await fetch(`http://127.0.0.1:${port}/`)).ok) break;
    } catch {}
    await new Promise((r) => setTimeout(r, 100));
  }
});
after(async () => {
  if (proxy) await new Promise<void>((r) => proxy.close(() => r()));
  try {
    execFileSync("docker", ["rm", "-f", restName], { stdio: "pipe" });
  } catch {}
  if (db) await db.end();
  await root.query("drop database if exists " + database);
  await root.end();
});

test("existing Admin API creates a one-time hashed credential with expiry", async () => {
  const r = await adminKeys.POST(
    request(
      "/api/v1/admin/radar/collectors",
      "POST",
      {
        name: "Coletor teste",
        allowed_sources: ["agenda_sp"],
        scopes: ["events:ingest", "pois:propose"],
        max_batch: 100,
        rate_limit_per_hour: 1000,
        expires_at: "2032-01-01T00:00:00Z",
      },
      undefined,
      true,
    ),
  );
  const b = await r.json();
  assert.equal(r.status, 201, JSON.stringify(b));
  key = b.key;
  collector = (
    await db.query("select * from sr_radar_collectors where id=$1", [
      b.collector.id,
    ])
  ).rows[0];
  assert.notEqual(collector.key_hash, key);
  const meta = await adminKeys.GET(
    request(
      "/api/v1/admin/radar/collectors",
      "GET",
      undefined,
      undefined,
      true,
    ),
  );
  assert.ok(!JSON.stringify(await meta.json()).includes(key));
  assert.equal(
    (await ping(request("/api/v1/radar/collector/ping", "GET", undefined, key)))
      .status,
    200,
  );
});
test("internal server-secret ingestion remains available without the retired browser console", async () => {
  const payload = {
    ...event,
    source: "internal_test",
    external_id: "legacy-internal",
    name: "Compatibilidade interna",
    lat: -22,
    lng: -45,
  };
  const response = await ingest(
    request(
      "/api/v1/radar/ingest",
      "POST",
      { events: [payload] },
      process.env.RADAR_INGEST_SECRET,
    ),
  );
  assert.equal(response.status, 200);
  const rows = (
    await db.query(
      "select * from sr_event_opportunities where source='internal_test' and external_id='legacy-internal'",
    )
  ).rows;
  assert.equal(rows.length, 1);
  assert.equal(rows[0].collector_id, null);
  assert.equal(rows[0].review_status, "approved");
});

test("collector credentials and foreign origins cannot authorize admin mutations", async () => {
  for (const handler of [
    adminKeys.POST,
    adminKeys.PATCH,
    adminEvents.PATCH,
    adminPois.PATCH,
  ])
    assert.equal(
      (await handler(request("/api/v1/admin/radar/events", "PATCH", {}, key)))
        .status,
      403,
    );
  assert.equal(
    (
      await adminKeys.POST(
        request(
          "/api/v1/admin/radar/collectors",
          "POST",
          {},
          undefined,
          true,
          false,
        ),
      )
    ).status,
    403,
  );
  assert.equal(
    (
      await ping(
        request(
          "/api/v1/radar/collector/ping",
          "GET",
          undefined,
          key + "wrong",
        ),
      )
    ).status,
    401,
  );
});
test("full flow over HTTP/PostgREST: pending → approve → existing Radar → unpublish", async () => {
  const r = await ingest(
    request(
      "/api/v1/radar/ingest",
      "POST",
      { run_id: randomUUID(), events: [event] },
      key,
    ),
  );
  const b = await r.json();
  assert.equal(r.status, 200, JSON.stringify(b));
  const id = b.items[0].event_id;
  assert.ok(id);
  assert.equal((await get(id)).review_status, "pending");
  assert.equal(
    (
      await nearbyEventRadar026({
        lat: event.lat,
        lng: event.lng,
        hours: 8,
        radiusKm: 15,
      })
    ).opportunities.length,
    0,
  );
  const approved = await adminEvents.PATCH(
    request(
      "/api/v1/admin/radar/events",
      "PATCH",
      {
        id,
        expected_version: 1,
        decision: "approve",
        note: "Verificado na fonte",
      },
      undefined,
      true,
    ),
  );
  assert.equal(
    approved.status,
    200,
    JSON.stringify(await approved.clone().json()),
  );
  assert.equal(
    (
      await nearbyEventRadar026({
        lat: event.lat,
        lng: event.lng,
        hours: 8,
        radiusKm: 15,
      })
    ).opportunities.length,
    1,
  );
  const monitoring = await adminRuns(
    request("/api/v1/admin/radar/runs", "GET", undefined, undefined, true),
  );
  const monitorBody = await monitoring.json();
  assert.equal(monitoring.status, 200, JSON.stringify(monitorBody));
  assert.ok(
    monitorBody.runs.some((run: any) => Number(run.approved_count) === 1),
  );
  const stale = await adminEvents.PATCH(
    request(
      "/api/v1/admin/radar/events",
      "PATCH",
      { id, expected_version: 1, decision: "reject", note: "Decisão obsoleta" },
      undefined,
      true,
    ),
  );
  assert.equal(stale.status, 409);
  const unpub = await adminEvents.PATCH(
    request(
      "/api/v1/admin/radar/events",
      "PATCH",
      {
        id,
        expected_version: 2,
        decision: "unpublish",
        note: "Retirada do teste",
      },
      undefined,
      true,
    ),
  );
  assert.equal(unpub.status, 200);
  assert.equal(
    (
      await nearbyEventRadar026({
        lat: event.lat,
        lng: event.lng,
        hours: 8,
        radiusKm: 15,
      })
    ).opportunities.length,
    0,
  );
  assert.equal(
    (
      await db.query(
        "select * from sr_radar_review_decisions where target_id=$1",
        [id],
      )
    ).rowCount,
    2,
  );
});
test("run retries compare payload hashes, duplicates preserve review, rejection cannot be requeued by ingestion", async () => {
  const payload = { ...event, external_id: "idem" };
  const run = randomUUID();
  const r = await receive(payload, run);
  assert.equal((await receive(payload, run)).idempotent_replay, true);
  await assert.rejects(
    receive({ ...payload, name: "changed" }, run),
    (e: any) => e.code === "PT409",
  );
  const id = r.items[0].event_id;
  await review(id, 1, "reject");
  await receive({ ...payload, name: "Changed by collector" });
  assert.equal((await get(id)).review_status, "rejected");
  await assert.rejects(
    db.query(
      "update sr_event_opportunities set review_status='approved' where id=$1",
      [id],
    ),
    (e: any) => e.code === "PT403",
  );
});
test("cancelled/expired records never return through renamed or rescheduled payloads", async () => {
  const r = await receive({ ...event, external_id: "cancel" });
  const id = r.items[0].event_id;
  await review(id, 1);
  await receive({ ...event, external_id: "cancel", status: "cancelled" });
  await receive({ ...event, external_id: "cancel", name: "Again" });
  assert.equal((await get(id)).status, "cancelled");
  await assert.rejects(
    review(id, (await get(id)).row_version),
    (e: any) => e.code === "PT409",
  );
});
test("independent sessions serialize review and ingestion; first-use rate limits are atomic", async () => {
  const r = await receive({ ...event, external_id: "race" }),
    id = r.items[0].event_id;
  const a = await db.connect(),
    b = await db.connect();
  try {
    const result = await Promise.allSettled([
      review(id, 1, "approve", a),
      review(id, 1, "reject", b),
    ]);
    assert.equal(result.filter((r) => r.status === "fulfilled").length, 1);
  } finally {
    a.release();
    b.release();
  }
  const duplicates = await Promise.all([
    receive({ ...event, external_id: "concurrent-ingest" }),
    receive({ ...event, external_id: "concurrent-ingest" }),
  ]);
  assert.equal(
    duplicates.reduce((n, r) => n + r.accepted, 0),
    1,
  );
  assert.equal(
    duplicates.reduce((n, r) => n + r.unchanged, 0),
    1,
  );
  const budget = await Promise.all(
    Array.from({ length: 30 }, () =>
      rpc(db, "sr_radar_budget_v2", ["budget-test", 5, 60]),
    ),
  );
  assert.equal(budget.filter(Boolean).length, 5);
});
test("source/scope isolation, expired/revoked/rotated keys and usage failures", async () => {
  const forged = validateCollectorBatchV1({ events: [event] }, collector).rows;
  forged[0].row.source = "not_allowed";
  await assert.rejects(
    rpc(db, "sr_radar_receive_v2", [
      collector.id,
      sha256Hex(key),
      "event",
      randomUUID(),
      "forged",
      JSON.stringify(forged),
      "[]",
      1,
      false,
    ]),
    (e: any) => e.code === "PT403",
  );
  const r = await adminKeys.POST(
    request(
      "/api/v1/admin/radar/collectors",
      "POST",
      {
        name: "Other collector",
        allowed_sources: ["agenda_sp"],
        scopes: ["events:ingest"],
        expires_at: "2032-01-01T00:00:00Z",
      },
      undefined,
      true,
    ),
  );
  const other = await r.json();
  const foreign = await ingest(
    request(
      "/api/v1/radar/ingest",
      "POST",
      { run_id: randomUUID(), events: [event] },
      other.key,
    ),
  );
  assert.equal((await foreign.json()).items[0].code, "owned_by_other_ingest");
  await db.query(
    "update sr_radar_collectors set expires_at='2000-01-01' where id=$1",
    [other.collector.id],
  );
  assert.equal(
    (await ping(request("/ping", "GET", undefined, other.key))).status,
    401,
  );
  const row = (
    await db.query("select * from sr_radar_collectors where id=$1", [
      other.collector.id,
    ])
  ).rows[0];
  const rotation = await adminKeys.PATCH(
    request(
      "/api/v1/admin/radar/collectors",
      "PATCH",
      {
        id: row.id,
        expected_version: row.row_version,
        action: "rotate",
        expires_at: "2032-01-01T00:00:00Z",
      },
      undefined,
      true,
    ),
  );
  const rotated = await rotation.json();
  assert.equal(rotation.status, 200, JSON.stringify(rotated));
  assert.equal(
    (await ping(request("/ping", "GET", undefined, other.key))).status,
    401,
  );
  const revoke = await adminKeys.PATCH(
    request(
      "/api/v1/admin/radar/collectors",
      "PATCH",
      {
        id: row.id,
        expected_version: rotated.collector.row_version,
        action: "revoke",
      },
      undefined,
      true,
    ),
  );
  assert.equal(revoke.status, 200);
  assert.equal(
    (await ping(request("/ping", "GET", undefined, rotated.key))).status,
    403,
  );
  assert.ok(
    (
      await db.query(
        "select * from sr_radar_collector_usage where collector_id is null",
      )
    ).rowCount! > 0,
  );
});
test("POIs remain inactive until review; concurrent decisions and legacy writes cannot bypass it", async () => {
  const payload = {
    source: "agenda_sp",
    source_external_id: "arena_01",
    canonical_name: "Arena",
    poi_type: "arena",
    address: "Rua A",
    city: "São Paulo",
    state: "SP",
    lat: -23.55,
    lng: -46.63,
    source_url: "https://example.org/arena",
    source_confidence: 0.9,
  };
  const r = await pois(
    request(
      "/api/v1/radar/collector/pois",
      "POST",
      { run_id: randomUUID(), pois: [payload] },
      key,
    ),
  );
  const result = await r.json();
  assert.equal(r.status, 200, JSON.stringify(result));
  const id = result.items[0].event_id;
  assert.equal(
    (await db.query("select status from sr_radar_pois where id=$1", [id]))
      .rows[0].status,
    "inactive",
  );
  const approved = await adminPois.PATCH(
    request(
      "/api/v1/admin/radar/pois",
      "PATCH",
      { id, expected_version: 1, decision: "approve", note: "Local conferido" },
      undefined,
      true,
    ),
  );
  assert.equal(
    approved.status,
    200,
    JSON.stringify(await approved.clone().json()),
  );
  await assert.rejects(
    db.query("update sr_radar_pois set status='inactive' where id=$1", [id]),
    (e: any) => e.code === "PT403",
  );
});
test("ACL/RLS and append-only audit; failed decision rolls back data and audit together", async () => {
  const c = await db.connect();
  try {
    await c.query("set role anon");
    await assert.rejects(
      c.query("select * from sr_radar_collectors"),
      (e: any) => e.code === "42501",
    );
    await assert.rejects(
      rpc(c, "sr_radar_budget_v2", ["x", 1, 60]),
      (e: any) => e.code === "42501",
    );
    await c.query("reset role");
    await c.query("set role service_role");
    await assert.rejects(
      c.query("delete from sr_radar_review_decisions"),
      (e: any) => e.code === "42501",
    );
  } finally {
    await c.query("reset role");
    c.release();
  }
  const snapshots = (
    await db.query(
      "select after_data from sr_radar_revision_history where entity_type='sr_radar_collectors'",
    )
  ).rows;
  assert.ok(!JSON.stringify(snapshots).includes(collector.key_hash));
});

test("audit tables remain append-only under Supabase default grants", async () => {
  const c = await db.connect();
  try {
    await c.query("set role service_role");
    for (const [table, column] of [
      ["sr_radar_revision_history", "actor"],
      ["sr_radar_review_decisions", "note"],
      ["sr_radar_collector_usage", "endpoint"],
    ] as const) {
      const privileges = (
        await c.query(
          "select has_table_privilege(current_user,$1,'select') as can_select, has_table_privilege(current_user,$1,'insert') as can_insert, has_table_privilege(current_user,$1,'update') as can_update, has_table_privilege(current_user,$1,'delete') as can_delete, has_table_privilege(current_user,$1,'truncate') as can_truncate, has_table_privilege(current_user,$1,'trigger') as can_trigger",
          [table],
        )
      ).rows[0];
      assert.deepEqual(
        privileges,
        {
          can_select: true,
          can_insert: true,
          can_update: false,
          can_delete: false,
          can_truncate: false,
          can_trigger: false,
        },
        table,
      );
      for (const statement of [
        `update ${table} set ${column}=${column} where false`,
        `delete from ${table} where false`,
        `truncate ${table}`,
      ]) {
        await assert.rejects(
          c.query(statement),
          (e: any) => e.code === "42501",
        );
      }
    }
  } finally {
    await c.query("reset role");
    c.release();
  }
});

// Inject a real PostgREST usage-insert failure after the receipt RPC commits.
// The trigger lives only in the disposable database and is always removed.
async function failHttpUsage(runId: string, endpoint: string) {
  assert.match(runId, /^(?:[0-9a-f-]{36}|all)$/);
  assert.ok(["events:ingest", "pois:propose"].includes(endpoint));
  await db.query(`create function test_http_usage_failure() returns trigger language plpgsql as $$
    begin
      if new.endpoint=TG_ARGV[1] and new.run_id is not null and
        (TG_ARGV[0]='all' or new.run_id=TG_ARGV[0]) then
        raise exception 'private SQL detail must not reach response or logs' using errcode='42501';
      end if;
      return new;
    end $$;
    create trigger test_http_usage_failure before insert on sr_radar_collector_usage
      for each row execute function test_http_usage_failure('${runId}','${endpoint}');`);
}
async function restoreHttpUsage() {
  await db.query(
    "drop trigger if exists test_http_usage_failure on sr_radar_collector_usage; drop function if exists test_http_usage_failure();",
  );
}

for (const kind of ["event", "poi"] as const) {
  test(`HTTP audit failure after committed ${kind}: same-run retries recover audit without duplicate data`, async (t) => {
    const runId = randomUUID(),
      externalId = "audit-" + randomUUID();
    const endpoint = kind === "event" ? "events:ingest" : "pois:propose";
    const path =
      kind === "event"
        ? "/api/v1/radar/ingest"
        : "/api/v1/radar/collector/pois";
    const handler = kind === "event" ? ingest : pois;
    const payload =
      kind === "event"
        ? { run_id: runId, events: [{ ...event, external_id: externalId }] }
        : {
            run_id: runId,
            pois: [
              {
                source: "agenda_sp",
                source_external_id: externalId,
                canonical_name: "Local de auditoria",
                poi_type: "arena",
                lat: -23.55,
                lng: -46.63,
                source_url: "https://example.org/audit-poi",
                source_confidence: 0.8,
              },
            ],
          };
    const warnings = t.mock.method(console, "warn", () => {});
    await failHttpUsage(runId, endpoint);
    let initial: any;
    try {
      const first = await handler(request(path, "POST", payload, key));
      initial = await first.json();
      assert.equal(first.status, 200);
      assert.equal(first.headers.get("cache-control"), "no-store");
      assert.equal(initial.ingestion_committed, true);
      assert.equal(initial.http_audit.status, "unconfirmed");
      assert.equal(
        initial.http_audit.recovery,
        "retry_same_run_id_same_payload",
      );
      assert.equal(initial.run_id, runId);
      assert.equal(initial.accepted, 1);
      assert.equal(initial.pending_review, 1);
      assert.ok(initial.items[0].event_id);
      const again = await handler(request(path, "POST", payload, key));
      const replay = await again.json();
      assert.equal(again.status, 200);
      assert.equal(replay.idempotent_replay, true);
      assert.equal(replay.ingestion_committed, true);
      assert.equal(replay.http_audit.status, "unconfirmed");
      assert.deepEqual(replay.items, initial.items);
      assert.notEqual(
        replay.http_audit.incident_id,
        initial.http_audit.incident_id,
      );
      assert.equal(
        (
          await db.query(
            "select * from sr_radar_collector_usage where collector_id=$1 and run_id=$2",
            [collector.id, runId],
          )
        ).rowCount,
        0,
      );
      const receipt = (
        await db.query(
          "select * from sr_radar_source_runs where collector_id=$1 and run_id=$2",
          [collector.id, runId],
        )
      ).rows;
      assert.equal(receipt.length, 1);
      assert.equal(receipt[0].saved_count, 1);
      assert.ok(receipt[0].request_hash);
      assert.equal(
        receipt[0].summary.http_audit,
        undefined,
        "per-attempt status must not change the business receipt",
      );
      const entityType =
        kind === "event" ? "sr_event_opportunities" : "sr_radar_pois";
      const history = await db.query(
        "select * from sr_radar_revision_history where entity_type=$1 and entity_id=$2",
        [entityType, initial.items[0].event_id],
      );
      assert.equal(history.rowCount, 1);
      assert.equal(history.rows[0].actor, collector.id);
      assert.equal(
        kind === "event"
          ? history.rows[0].after_data.review_status
          : history.rows[0].after_data.metadata.review,
        "pending",
      );
      assert.equal(warnings.mock.calls.length, 2);
      const incident = warnings.mock.calls[0].arguments[1] as any;
      assert.equal(incident.incident_id, initial.http_audit.incident_id);
      assert.equal(incident.run_id_hash, sha256Hex(runId));
      assert.equal(incident.collector_id, collector.id);
      assert.equal(incident.endpoint, endpoint);
      assert.equal(incident.commit_state, "confirmed");
      const output = JSON.stringify({
        initial,
        warnings: warnings.mock.calls.map((c) => c.arguments),
      });
      assert.ok(!output.includes(key));
      assert.ok(!output.includes("private SQL detail"));
    } finally {
      await restoreHttpUsage();
    }
    const recovered = await handler(request(path, "POST", payload, key));
    const replay = await recovered.json();
    assert.equal(recovered.status, 200);
    assert.equal(replay.http_audit.status, "recorded");
    assert.equal(replay.idempotent_replay, true);
    assert.equal(replay.ingestion_committed, true);
    assert.deepEqual(replay.items, initial.items);
    assert.equal(
      (
        await db.query(
          "select * from sr_radar_source_runs where collector_id=$1 and run_id=$2",
          [collector.id, runId],
        )
      ).rowCount,
      1,
    );
    const table = kind === "event" ? "sr_event_opportunities" : "sr_radar_pois";
    const idField = kind === "event" ? "external_id" : "source_external_id";
    assert.equal(
      (
        await db.query(
          `select * from ${table} where source=$1 and ${idField}=$2`,
          ["agenda_sp", externalId],
        )
      ).rowCount,
      1,
    );
    const usage = (
      await db.query(
        "select * from sr_radar_collector_usage where collector_id=$1 and run_id=$2",
        [collector.id, runId],
      )
    ).rows;
    assert.equal(usage.length, 1);
    assert.equal(usage[0].http_status, 200);
    assert.equal(usage[0].endpoint, endpoint);
    const altered =
      kind === "event"
        ? {
            ...payload,
            events: [
              { ...event, external_id: externalId, name: "Corpo divergente" },
            ],
          }
        : {
            ...payload,
            pois: [
              {
                ...(payload as any).pois[0],
                canonical_name: "Corpo divergente",
              },
            ],
          };
    const conflict = await handler(request(path, "POST", altered, key));
    assert.equal(conflict.status, 409);
    assert.equal((await conflict.json()).error, "run_payload_conflict");
  });
}

test("HTTP usage may succeed while last-access update fails: receipt stays confirmed and retry is idempotent", async (t) => {
  const runId = randomUUID();
  const payload = {
    run_id: runId,
    events: [{ ...event, external_id: "audit-touch-" + randomUUID() }],
  };
  t.mock.method(console, "warn", () => {});
  await db.query(`create function test_last_access_failure() returns trigger language plpgsql as $$
 begin if new.id='${collector.id}'::uuid and new.last_used_at is distinct from old.last_used_at then
 raise exception 'isolated last-access failure' using errcode='42501';end if;return new;end $$;
 create trigger test_last_access_failure before update on sr_radar_collectors for each row execute function test_last_access_failure();`);
  let id: string;
  try {
    const response = await ingest(
      request("/api/v1/radar/ingest", "POST", payload, key),
    );
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.ingestion_committed, true);
    assert.equal(body.http_audit.status, "unconfirmed");
    id = body.items[0].event_id;
    assert.equal((await get(id)).review_status, "pending");
    assert.equal(
      (
        await db.query(
          "select * from sr_radar_collector_usage where run_id=$1",
          [runId],
        )
      ).rowCount,
      1,
    );
  } finally {
    await db.query(
      "drop trigger test_last_access_failure on sr_radar_collectors;drop function test_last_access_failure();",
    );
  }
  const response = await ingest(
    request("/api/v1/radar/ingest", "POST", payload, key),
  );
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.idempotent_replay, true);
  assert.equal(body.items[0].event_id, id!);
  assert.equal(body.http_audit.status, "recorded");
  assert.equal(
    (
      await db.query("select * from sr_radar_source_runs where run_id=$1", [
        runId,
      ])
    ).rowCount,
    1,
  );
  assert.equal(
    (
      await db.query("select * from sr_radar_collector_usage where run_id=$1", [
        runId,
      ])
    ).rowCount,
    2,
    "one usage row per HTTP attempt, not a duplicate ingestion",
  );
});

test("audit failure without a client run_id confirms the commit but never advises an unsafe new-run retry", async (t) => {
  t.mock.method(console, "warn", () => {});
  await failHttpUsage("all", "events:ingest");
  try {
    const response = await ingest(
      request(
        "/api/v1/radar/ingest",
        "POST",
        {
          events: [
            { ...event, external_id: "audit-generated-" + randomUUID() },
          ],
        },
        key,
      ),
    );
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.ingestion_committed, true);
    assert.equal(body.http_audit.recovery, "contact_admin_with_run_id");
    assert.ok(body.run_id);
    assert.equal(
      (
        await db.query("select * from sr_radar_source_runs where run_id=$1", [
          body.run_id,
        ])
      ).rowCount,
      1,
    );
  } finally {
    await restoreHttpUsage();
  }
});

test("audit failure on dry-run or ping returns a controlled 503 without claiming a business commit", async (t) => {
  t.mock.method(console, "warn", () => {});
  const runId = randomUUID();
  await failHttpUsage(runId, "events:ingest");
  try {
    const response = await ingest(
      request(
        "/api/v1/radar/ingest",
        "POST",
        {
          run_id: runId,
          dry_run: true,
          events: [{ ...event, external_id: "audit-dry" }],
        },
        key,
      ),
    );
    const body = await response.json();
    assert.equal(response.status, 503);
    assert.equal(body.error, "collector_http_audit_unavailable");
    assert.equal(body.ingestion_committed, undefined);
    assert.equal(body.run_id, runId);
    assert.equal(
      (
        await db.query("select * from sr_radar_source_runs where run_id=$1", [
          runId,
        ])
      ).rowCount,
      0,
    );
    assert.equal(
      (
        await db.query(
          "select * from sr_event_opportunities where external_id='audit-dry'",
        )
      ).rowCount,
      0,
    );
  } finally {
    await restoreHttpUsage();
  }
  await db.query("revoke insert on sr_radar_collector_usage from service_role");
  try {
    const response = await ping(request("/ping", "GET", undefined, key));
    const body = await response.json();
    assert.equal(response.status, 503);
    assert.equal(body.error, "collector_http_audit_unavailable");
    assert.equal(body.ingestion_committed, undefined);
  } finally {
    await db.query("grant insert on sr_radar_collector_usage to service_role");
  }
});

test("failed audit insert rolls back review; history and source runs cannot become partial", async () => {
  const r = await receive({ ...event, external_id: "audit-rollback" });
  const id = r.items[0].event_id;
  await db.query(
    "revoke insert on sr_radar_review_decisions from service_role",
  );
  const c = await db.connect();
  try {
    await c.query("set role service_role");
    await assert.rejects(
      review(id, 1, "approve", c),
      (e: any) => e.code === "42501",
    );
  } finally {
    await c.query("reset role");
    c.release();
    await db.query("grant insert on sr_radar_review_decisions to service_role");
  }
  assert.equal((await get(id)).review_status, "pending");
  assert.equal((await get(id)).row_version, "1");
});

test("dry-run, payload size and per-collector HTTP budget never bypass validation", async () => {
  const dry = await ingest(
    request(
      "/api/v1/radar/ingest",
      "POST",
      {
        run_id: randomUUID(),
        dry_run: true,
        events: [{ ...event, external_id: "dry-only" }],
      },
      key,
    ),
  );
  assert.equal(dry.status, 200);
  assert.equal(
    (
      await db.query(
        "select * from sr_event_opportunities where external_id='dry-only'",
      )
    ).rowCount,
    0,
  );
  const large = await ingest(
    new Request("https://srrotas.com/api/v1/radar/ingest", {
      method: "POST",
      headers: {
        authorization: "Bearer " + key,
        "content-type": "application/json",
      },
      body: " ".repeat(2000001),
    }),
  );
  assert.equal(large.status, 413);
  const limited = await adminKeys.POST(
    request(
      "/api/v1/admin/radar/collectors",
      "POST",
      {
        name: "Limited test",
        allowed_sources: ["agenda_sp"],
        scopes: ["events:ingest"],
        rate_limit_per_hour: 1,
      },
      undefined,
      true,
    ),
  );
  const k = (await limited.json()).key;
  assert.equal((await ping(request("/ping", "GET", undefined, k))).status, 200);
  const denied = await ping(request("/ping", "GET", undefined, k));
  assert.equal(denied.status, 429);
  assert.ok(denied.headers.get("retry-after"));
});

test("changed venue geometry removes the stale POI link and requires fresh review", async () => {
  const r = await receive({ ...event, external_id: "geometry-change" });
  const id = r.items[0].event_id;
  await review(id, 1);
  const poi = (
    await db.query("select id from sr_radar_pois where status='active' limit 1")
  ).rows[0];
  assert.ok(poi);
  await db.query("update sr_event_opportunities set poi_id=$1 where id=$2", [
    poi.id,
    id,
  ]);
  await receive({ ...event, external_id: "geometry-change", lat: -23.66 });
  assert.equal((await get(id)).poi_id, null);
  assert.equal((await get(id)).review_status, "pending");
});

test(
  "browser uses existing vc110 Admin to generate a key, approve and unpublish",
  { skip: !process.env.SR_RADAR_BROWSER_EXECUTABLE, timeout: 60000 },
  async () => {
    const { chromium } = await import("playwright-core");
    const reservation = createServer();
    await new Promise<void>((r) => reservation.listen(0, "127.0.0.1", r));
    const port = (reservation.address() as { port: number }).port;
    await new Promise<void>((r) => reservation.close(() => r()));
    const base = `http://127.0.0.1:${port}`;
    const next = spawn(
      process.execPath,
      [
        "node_modules/next/dist/bin/next",
        "start",
        "-H",
        "127.0.0.1",
        "-p",
        String(port),
      ],
      { env: { ...process.env, NEXT_TELEMETRY_DISABLED: "1" }, stdio: "pipe" },
    );
    let output = "";
    next.stdout?.on("data", (c) => (output += c.toString()));
    next.stderr?.on("data", (c) => (output += c.toString()));
    let browser: Awaited<ReturnType<typeof chromium.launch>> | undefined;
    try {
      let ready = false;
      for (let i = 0; i < 100; i++) {
        try {
          if ((await fetch(base + "/admin/radar")).ok) {
            ready = true;
            break;
          }
        } catch {}
        await new Promise((r) => setTimeout(r, 100));
      }
      assert.ok(ready, output);
      browser = await chromium.launch({
        executablePath: process.env.SR_RADAR_BROWSER_EXECUTABLE!,
        headless: true,
        args: ["--no-sandbox"],
      });
      const context = await browser.newContext({
        viewport: { width: 1365, height: 900 },
      });
      await context.addCookies([
        {
          name: "sr_import_admin",
          value: session,
          url: base,
          httpOnly: true,
          sameSite: "Strict",
        },
      ]);
      const page = await context.newPage();
      const errors: string[] = [];
      page.on("pageerror", (e) => errors.push(e.message));
      const retired = await page.request.get(base + "/radar-admin", {
        maxRedirects: 0,
      });
      assert.equal(retired.status(), 308);
      assert.equal(
        new URL(retired.headers().location, base).pathname,
        "/admin/radar",
      );
      assert.ok(!(await retired.text()).includes("RADAR_INGEST_SECRET"));
      const legacyRequests: string[] = [];
      page.on("request", (r) => {
        if (new URL(r.url()).pathname === "/api/v1/radar/admin")
          legacyRequests.push(r.method());
      });
      await page.goto(base + "/radar-admin");
      assert.equal(new URL(page.url()).pathname, "/admin/radar");
      assert.equal(await page.getByText("RADAR_INGEST_SECRET").count(), 0);
      // Issue #20: syntax-only preparation never mutates Radar or sends the file.
      const radarSnapshot = async () => {
        const tables = ["sr_event_opportunities", "sr_radar_pois", "sr_radar_source_runs", "sr_radar_review_decisions", "sr_radar_revision_history", "sr_radar_collector_usage"];
        return Promise.all(tables.map(async table => (await db.query(`select to_jsonb(t) as row from ${table} t order by to_jsonb(t)::text`)).rows));
      };
      const beforeInspection = await radarSnapshot();
      const uploadRequests: string[] = [];
      const watchUpload = (r: any) => { if (r.method() !== "GET" && new URL(r.url()).pathname.startsWith("/api/")) uploadRequests.push(r.method() + " " + new URL(r.url()).pathname); };
      page.on("request", watchUpload);
      await page.getByRole("tab", { name: "Importar JSON", exact: true }).click();
      assert.equal(await page.getByRole("heading", { name: "Importar JSON", exact: true }).count(), 1);
      assert.equal(await page.getByRole("button", { name: "Confirmar importação indisponível" }).isDisabled(), true);
      const fileInput = page.getByLabel("Escolher arquivo JSON", { exact: true });
      await fileInput.setInputFiles({ name: "sintaxe-apenas.json", mimeType: "application/json", buffer: Buffer.from('{"amostra":"somente sintaxe, não é um envelope Radar"}') });
      await page.getByRole("status").filter({ hasText: "Sintaxe JSON válida" }).waitFor();
      await page.screenshot({ path: "/tmp/sr-admin-json-desktop.png", fullPage: true });
      await page.setViewportSize({ width: 390, height: 844 });
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      await page.screenshot({ path: "/tmp/sr-admin-json-mobile.png", fullPage: true });
      await fileInput.setInputFiles({ name: "invalido.json", mimeType: "application/json", buffer: Buffer.from('{') });
      await page.getByRole("status").filter({ hasText: "Sintaxe JSON inválida" }).waitFor();
      await fileInput.setInputFiles({ name: "limite.json", mimeType: "application/json", buffer: Buffer.alloc(2_000_001) });
      await page.getByRole("status").filter({ hasText: "ultrapassa o limite local" }).waitFor();
      assert.deepEqual(await radarSnapshot(), beforeInspection);
      assert.deepEqual(uploadRequests, []);
      page.off("request", watchUpload);
      await page.setViewportSize({ width: 1365, height: 900 });
      await page.getByRole("tab", { name: "Chaves", exact: true }).click();
      await page.getByLabel("Nome", { exact: true }).fill("Coletor visual");
      await page.getByLabel("Fontes permitidas").fill("agenda_sp");
      await page.getByLabel("Válida até").fill("2032-01-01");
      const creation = page.waitForResponse(
        (r) =>
          r.url().endsWith("/api/v1/admin/radar/collectors") &&
          r.request().method() === "POST",
      );
      await page
        .getByRole("button", { name: "Criar chave", exact: true })
        .click();
      const created = await creation;
      assert.equal(
        created.status(),
        201,
        created.status() === 201 ? "ok" : await created.text(),
      );
      const browserKey = await page.locator("code").innerText();
      await page.getByRole("button", { name: "Já guardei, ocultar" }).click();
      const response = await page.request.post(base + "/api/v1/radar/ingest", {
        headers: { authorization: "Bearer " + browserKey },
        data: {
          run_id: randomUUID(),
          events: [
            {
              ...event,
              name: "Evento navegador atual",
              external_id: "browser-event",
            },
          ],
        },
      });
      assert.equal(response.status(), 200);
      const id = (await response.json()).items[0].event_id;
      await page.getByRole("tab", { name: "Recebidos", exact: true }).click();
      await page
        .getByRole("button")
        .filter({ hasText: "Evento navegador atual" })
        .click();
      await page
        .getByPlaceholder("Motivo da decisão (obrigatório)…")
        .fill("Fonte conferida no teste visual");
      const approved = page.waitForResponse(
        (r) =>
          r.url().endsWith("/api/v1/admin/radar/events") &&
          r.request().method() === "PATCH",
      );
      await page.getByRole("button", { name: "Aprovar", exact: true }).click();
      assert.equal((await approved).status(), 200);
      assert.equal((await get(id)).review_status, "approved");
      await page.getByLabel("Revisão", { exact: true }).selectOption("");
      await page
        .getByRole("button", { name: "Atualizar", exact: true })
        .click();
      await page
        .getByRole("button")
        .filter({ hasText: "Evento navegador atual" })
        .click();
      await page
        .getByRole("button", { name: "Carregar histórico de alterações" })
        .click();
      await page.getByText(/versão 2/).waitFor();
      await page.screenshot({
        path: "/tmp/sr-radar-main-admin-desktop.png",
        fullPage: true,
      });
      await page.setViewportSize({ width: 390, height: 844 });
      await page.screenshot({
        path: "/tmp/sr-radar-main-admin-mobile.png",
        fullPage: true,
      });
      await page
        .getByPlaceholder("Motivo da decisão (obrigatório)…")
        .fill("Retirada após teste visual");
      const removed = page.waitForResponse(
        (r) =>
          r.url().endsWith("/api/v1/admin/radar/events") &&
          r.request().method() === "PATCH",
      );
      await page
        .getByRole("button", { name: "Despublicar", exact: true })
        .click();
      assert.equal((await removed).status(), 200);
      assert.equal((await get(id)).review_status, "unpublished");
      assert.deepEqual(errors, []);
      assert.deepEqual(legacyRequests, []);
    } finally {
      if (browser) await browser.close();
      next.kill("SIGTERM");
      await new Promise<void>((r) => next.once("exit", () => r()));
    }
  },
);

test("containment rollback revokes and hides external data without deleting history", async () => {
  const count = (
    await db.query("select count(*) as n from sr_radar_revision_history")
  ).rows[0].n;
  await db.query(
    readFileSync(
      new URL(
        "../../../docs/contracts/RADAR-EXTERNAL-COLLECTOR-V2.rollback.sql",
        import.meta.url,
      ),
      "utf8",
    ),
  );
  assert.equal(
    (await db.query("select * from sr_radar_collectors where status='active'"))
      .rowCount,
    0,
  );
  assert.equal(
    (
      await db.query(
        "select * from sr_event_opportunities where collector_id is not null and status='active'",
      )
    ).rowCount,
    0,
  );
  assert.ok(
    Number(
      (await db.query("select count(*) as n from sr_radar_revision_history"))
        .rows[0].n,
    ) >= Number(count),
  );
});
