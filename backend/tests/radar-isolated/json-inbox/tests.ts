import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID, createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import type { Pool } from "pg";
import { upload, list, download } from "../../../src/radar-json-inbox/service";
export async function setupJsonInbox(db: Pool) {
  await db.query(
    "create table if not exists historical_import_access(email text primary key,enabled boolean)",
  );
  await db.query(
    readFileSync(
      new URL(
        "../../../../supabase/migrations/20261115122811_radar_json_inbox_v1.sql",
        import.meta.url,
      ),
      "utf8",
    ),
  );
}
const hash = (value: string | Uint8Array) =>
  createHash("sha256").update(value).digest("hex");
export function registerJsonInboxTests(
  getDb: () => Pool,
  session: string,
  actor: string,
) {
  const url = "https://srrotas.com/api/v1/admin/radar/json-inbox";
  function req(
    body?: Uint8Array,
    key = randomUUID(),
    cookie = session,
    origin = "https://srrotas.com",
    name = "legado.json",
  ) {
    return new Request(url, {
      method: body ? "POST" : "GET",
      headers: {
        ...(cookie ? { cookie: "sr_import_admin=" + cookie } : {}),
        origin,
        "content-type": "application/json",
        "idempotency-key": key,
        "x-file-name": encodeURIComponent(name),
      },
      ...(body ? { body: Buffer.from(body) } : {}),
    });
  }
  const snapshot = async () =>
    Promise.all(
      [
        "sr_event_opportunities",
        "sr_radar_pois",
        "sr_radar_source_runs",
        "sr_radar_revision_history",
        "sr_radar_review_decisions",
        "sr_radar_collector_usage",
      ].map(
        async (table) =>
          (
            await getDb().query(
              `select to_jsonb(t) row from ${table} t order by to_jsonb(t)::text`,
            )
          ).rows,
      ),
    );
  test("JSON inbox: real HTTP/PostgREST/SQL legacy receipt, exact replay and recovery change no Radar entities", async () => {
    const before = await snapshot(),
      bytes = Buffer.from(
        '\ufeff{\n"versao_antiga":1,"itens":[],"url":"http://127.0.0.1/private"}\n',
      ),
      key = randomUUID();
    const response = await upload(req(bytes, key));
    assert.equal(response.status, 200, await response.clone().text());
    const receipt = (await response.json()).receipt;
    assert.equal(receipt.sha256, hash(bytes));
    assert.equal(receipt.size_bytes, bytes.length);
    assert.equal(receipt.status, "quarantined");
    assert.ok(receipt.upload_id);
    const replay = await upload(req(bytes, key));
    assert.deepEqual((await replay.json()).receipt, receipt);
    assert.equal((await upload(req(Buffer.from("{}"), key))).status, 409);
    assert.equal(
      (await upload(req(bytes, key, session, undefined, "changed.json")))
        .status,
      409,
    );
    const metadata = await list(req());
    assert.equal(metadata.status, 200);
    assert.ok(
      (await metadata.json()).uploads.some(
        (r: any) => r.upload_id === receipt.upload_id,
      ),
    );
    const file = await download(req(), receipt.upload_id);
    assert.equal(file.status, 200, await file.clone().text());
    assert.deepEqual(Buffer.from(await file.arrayBuffer()), bytes);
    assert.equal(file.headers.get("x-content-type-options"), "nosniff");
    assert.match(file.headers.get("content-disposition")!, /^attachment/);
    assert.equal(file.headers.get("cache-control"), "private, no-store");
    assert.deepEqual(await snapshot(), before);
    assert.equal(
      (
        await getDb().query(
          "select count(*)::integer n from sr_radar_json_upload_audit where upload_id=$1",
          [receipt.upload_id],
        )
      ).rows[0].n,
      3,
    );
  });
  test("JSON inbox: no-session, CSRF, collector, nonadmin and expired session fail closed", async () => {
    const bytes = Buffer.from("{}");
    assert.equal((await upload(req(bytes, randomUUID(), ""))).status, 401);
    assert.equal(
      (await upload(req(bytes, randomUUID(), session, "https://evil.example")))
        .status,
      403,
    );
    const collector = req(bytes);
    collector.headers.set("authorization", "Bearer srrk_not_an_admin");
    assert.equal((await upload(collector)).status, 403);
    const foreign = "srimp_nonadmin";
    await getDb().query(
      "insert into historical_import_web_sessions(auth_user_id,email,token_hash,expires_at) values($1,'nonadmin@example.org',$2,now()+interval '1 hour')",
      [randomUUID(), hash(foreign)],
    );
    assert.equal((await upload(req(bytes, randomUUID(), foreign))).status, 403);
    const expired = "srimp_expired";
    await getDb().query(
      "insert into historical_import_web_sessions(auth_user_id,email,token_hash,expires_at) values($1,$2,$3,now()-interval '1 second')",
      [randomUUID(), actor, hash(expired)],
    );
    assert.equal((await list(req(undefined, undefined, expired))).status, 401);
  });
  test("JSON inbox: SQL quotas/idempotence are atomic, actor scoped, revocation and append-only ACL enforced", async () => {
    const db = getDb();
    const identity = (
      await db.query(
        "select auth_user_id from historical_import_web_sessions where token_hash=$1",
        [hash(session)],
      )
    ).rows[0].auth_user_id;
    const sql = (key: string, content = "{}") =>
      db.query("select sr_radar_json_upload_v1($1,$2,$3,$4,$5,$6) r", [
        identity,
        actor,
        hash(session),
        key,
        "sql.json",
        Buffer.from(content).toString("base64"),
      ]);
    const key = randomUUID(),
      runs = await Promise.all(Array.from({ length: 8 }, () => sql(key)));
    assert.equal(new Set(runs.map((r) => r.rows[0].r.upload_id)).size, 1);
    // Alternate allowed admin receives its own distinct receipt for same key/content.
    const other = randomUUID(),
      otherToken = "srimp_second";
    await db.query(
      "insert into historical_import_access values('jadielalmeida@gmail.com',true)",
    );
    await db.query(
      "insert into historical_import_web_sessions(auth_user_id,email,token_hash,expires_at) values($1,'jadielalmeida@gmail.com',$2,now()+interval '1 hour')",
      [other, hash(otherToken)],
    );
    const otherResult = (
      await db.query("select sr_radar_json_upload_v1($1,$2,$3,$4,$5,$6) r", [
        other,
        "jadielalmeida@gmail.com",
        hash(otherToken),
        key,
        "sql.json",
        "e30=",
      ])
    ).rows[0].r;
    assert.notEqual(otherResult.upload_id, runs[0].rows[0].r.upload_id);
    await db.query(
      "update historical_import_access set enabled=false where email='jadielalmeida@gmail.com'",
    );
    await assert.rejects(
      () =>
        db.query("select sr_radar_json_upload_v1($1,$2,$3,$4,$5,$6)", [
          other,
          "jadielalmeida@gmail.com",
          hash(otherToken),
          randomUUID(),
          "sql.json",
          "e30=",
        ]),
      (e: any) => e.code === "PT403",
    );
    const client = await db.connect();
    try {
      for (const role of ["anon", "authenticated", "service_role"]) {
        await client.query("set role " + role);
        for (const table of [
          "sr_radar_json_uploads",
          "sr_radar_json_upload_audit",
          "private.sr_radar_json_upload_bytes",
        ])
          await assert.rejects(
            () => client.query("select * from " + table),
            (e: any) => e.code === "42501",
          );
        await client.query("reset role");
      }
      await client.query("set role authenticated");
      await assert.rejects(
        () =>
          client.query("select sr_radar_json_list_v1($1,$2,$3)", [
            identity,
            actor,
            hash(session),
          ]),
        (e: any) => e.code === "42501",
      );
    } finally {
      await client.query("reset role");
      client.release();
    }
    await assert.rejects(
      () => sql(randomUUID(), '{"x":1,"x":2}'),
      (e: any) => e.code === "PT400",
    );
    const count = Number(
      (
        await db.query(
          "select count(*) n from sr_radar_json_uploads where received_by=$1",
          [identity],
        )
      ).rows[0].n,
    );
    const many = await Promise.allSettled(
      Array.from({ length: 25 }, () => sql(randomUUID())),
    );
    assert.equal(
      many.filter((r) => r.status === "fulfilled").length,
      20 - count,
    );
    assert.ok(
      many
        .filter((r) => r.status === "rejected")
        .every((r: any) => r.reason.code === "PT429"),
    );
  });
  test("JSON inbox: audit failure rolls back bytes and receipt; corruption and reviewed retention block download", async () => {
    const db = getDb(),
      uid = randomUUID(),
      token = "srimp_failure_actor";
    await db.query(
      "insert into historical_import_web_sessions(auth_user_id,email,token_hash,expires_at) values($1,$2,$3,now()+interval '1 hour')",
      [uid, actor, hash(token)],
    );
    const invoke = () =>
      db.query("select sr_radar_json_upload_v1($1,$2,$3,$4,$5,$6) r", [
        uid,
        actor,
        hash(token),
        randomUUID(),
        "failure.json",
        "e30=",
      ]);
    const before = (
      await db.query("select count(*) n from sr_radar_json_uploads")
    ).rows[0].n;
    await db.query(
      "create function test_inbox_audit_fail() returns trigger language plpgsql as $$begin raise exception 'synthetic audit failure';end$$;create trigger test_inbox_audit_fail before insert on sr_radar_json_upload_audit for each row execute function test_inbox_audit_fail()",
    );
    try {
      await assert.rejects(invoke);
    } finally {
      await db.query(
        "drop trigger test_inbox_audit_fail on sr_radar_json_upload_audit;drop function test_inbox_audit_fail()",
      );
    }
    assert.equal(
      (await db.query("select count(*) n from sr_radar_json_uploads")).rows[0]
        .n,
      before,
    );
    const receipt = (await invoke()).rows[0].r;
    await db.query(
      "update private.sr_radar_json_upload_bytes set payload=decode('5b5d','hex') where upload_id=$1",
      [receipt.upload_id],
    );
    assert.equal(
      (await download(req(undefined, undefined, token), receipt.upload_id))
        .status,
      409,
    );
    await db.query(
      "delete from private.sr_radar_json_upload_bytes where upload_id=$1",
      [receipt.upload_id],
    );
    assert.equal(
      (await download(req(undefined, undefined, token), receipt.upload_id))
        .status,
      410,
    );
  });
}
