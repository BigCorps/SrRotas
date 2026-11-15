/** NOT imported by Next/server production. Internal experiment, no HTTP writer. */
import {
  createCipheriv,
  createDecipheriv,
  randomBytes,
  randomUUID,
} from "node:crypto";
import { Pool, type PoolClient } from "pg";
import {
  ImportError,
  previewImport,
  digest,
  identity,
  materialHash,
  SCHEMA,
  type Registry,
  type Snapshot,
  type ImportItem,
} from "../../../src/radar-admin-import/validation";
import { existingRows } from "../../../src/radar-admin-import/preview-service";
import { normalizeRadarPoiAdminV1 } from "../../../src/radar-poi-admin-v1";
export class Sandbox {
  private readonly key = randomBytes(32);
  constructor(
    private readonly db: Pool,
    private readonly nonce: string,
  ) {}
  async guard(c: Pool | PoolClient = this.db) {
    const url = new URL(this.db.options.connectionString!);
    if (!["127.0.0.1", "localhost"].includes(url.hostname))
      throw new ImportError("NOT_DISPOSABLE", 503);
    const r = await c.query(
      "select current_database() as name, (select nonce from radar_admin_sandbox.marker) as nonce",
    );
    if (
      !r.rows[0].name.startsWith("sr_radar_test_") ||
      r.rows[0].nonce !== this.nonce
    )
      throw new ImportError("NOT_DISPOSABLE", 503);
  }
  async registerNamespace(
    reg: Registry,
    actor: string,
    expectedRevision: number,
  ) {
    if (
      !/^[a-z][a-z0-9_-]{2,63}$/.test(reg.namespace) ||
      ["ticketmaster", "manual", "admin", "internal", "admin_manual"].includes(
        reg.namespace,
      ) ||
      reg.namespace.includes("sintetico") ||
      !actor ||
      reg.approved_by !== actor ||
      reg.revision !== expectedRevision + 1 ||
      !Number.isFinite(Date.parse(reg.approved_at)) ||
      Date.parse(reg.approved_at) > Date.now() + 300000 ||
      !reg.domains.length ||
      reg.domains.some((d) => {
        try {
          return new URL("https://" + d).hostname !== d || !d.includes(".");
        } catch {
          return true;
        }
      }) ||
      !reg.rights.length ||
      reg.rights.some(
        (r) =>
          ![
            "organizer_permission",
            "commercial_license",
            "original_curation",
          ].includes(r),
      ) ||
      Object.values(reg.attestations).some((h) => !/^[a-f0-9]{64}$/.test(h))
    )
      throw new ImportError("INVALID_REGISTRY", 422);
    const c = await this.db.connect();
    try {
      await this.guard(c);
      await c.query("begin");
      await c.query("select pg_advisory_xact_lock(921822)");
      if (
        (
          await c.query(
            "select 1 from sr_radar_collectors where $1=any(allowed_sources) limit 1",
            [reg.namespace],
          )
        ).rowCount
      )
        throw new ImportError("COLLECTOR_NAMESPACE_RESERVED", 422);
      const existing = (
        await c.query(
          "select revision from radar_admin_sandbox.namespaces where namespace=$1 for update",
          [reg.namespace],
        )
      ).rows[0];
      if (Number(existing?.revision ?? 0) !== expectedRevision)
        throw new ImportError("REGISTRY_CONFLICT", 409);
      await c.query(
        "insert into radar_admin_sandbox.namespaces values($1,$2,$3) on conflict(namespace) do update set revision=excluded.revision,document=excluded.document",
        [reg.namespace, reg.revision, reg],
      );
      await c.query(
        "insert into radar_admin_sandbox.registry_audit(namespace,revision,actor,action) values($1,$2,$3,$4)",
        [
          reg.namespace,
          reg.revision,
          actor,
          reg.enabled ? "authorize" : "revoke",
        ],
      );
      await c.query("commit");
    } catch (e) {
      await c.query("rollback");
      throw e;
    } finally {
      c.release();
    }
  }
  async snapshot(c: Pool | PoolClient = this.db): Promise<Snapshot> {
    await this.guard(c);
    const [registry, events, pois] = await Promise.all([
      c.query(
        "select document from radar_admin_sandbox.namespaces order by namespace",
      ),
      c.query("select * from sr_event_opportunities order by id"),
      c.query("select * from sr_radar_pois order by id"),
    ]);
    return {
      registry: registry.rows.map((r) => r.document as Registry),
      registryAvailable: true,
      existing: existingRows(events.rows, pois.rows),
      complete: true,
    };
  }
  seal(payload: any) {
    const iv = randomBytes(12),
      cipher = createCipheriv("aes-256-gcm", this.key, iv);
    const encrypted = Buffer.concat([
      cipher.update(JSON.stringify(payload)),
      cipher.final(),
    ]);
    return Buffer.concat([iv, cipher.getAuthTag(), encrypted]).toString(
      "base64url",
    );
  }
  open(token: string) {
    try {
      if (token.length > 65536) throw Error();
      const b = Buffer.from(token, "base64url"),
        c = createDecipheriv("aes-256-gcm", this.key, b.subarray(0, 12));
      c.setAuthTag(b.subarray(12, 28));
      return JSON.parse(
        Buffer.concat([c.update(b.subarray(28)), c.final()]).toString(),
      );
    } catch {
      throw new ImportError("PREVIEW_STALE", 409);
    }
  }
  async preview(
    envelope: unknown,
    actor: string,
    session: string,
    expiry = Date.now() + 600000,
  ) {
    const c = await this.db.connect();
    try {
      await this.guard(c);
      await c.query("begin isolation level repeatable read read only");
      const result = previewImport(envelope, await this.snapshot(c));
      await c.query("commit");
      return {
        ...result,
        preview_token: this.seal({
          actor,
          session,
          schema: SCHEMA,
          expiry,
          hash: result.envelope_hash,
          fingerprint: result.fingerprint,
          ids: result.items
            .filter((r) => r.classification === "ready")
            .map((r) => r.item_id),
        }),
      };
    } finally {
      await c.query("rollback");
      c.release();
    }
  }
  /** Experimental transaction proves ADMIN-only invariants. All deployed commits remain disabled.
   * Other writers do not acquire our lock; cross-writer negative test is an activation blocker. */
  async commit(
    input: {
      envelope: unknown;
      selected: string[];
      token: string;
      key: string;
      actor: string;
      session: string;
    },
    failAfter?: number,
  ) {
    const { envelope, selected, token, key, actor, session } = input;
    if (!/^[a-f\d]{8}-(?:[a-f\d]{4}-){3}[a-f\d]{12}$/i.test(key))
      throw new ImportError("INVALID_IDEMPOTENCY_KEY");
    if (
      !Array.isArray(selected) ||
      selected.length < 1 ||
      selected.length > 200 ||
      new Set(selected).size !== selected.length ||
      selected.some((x) => typeof x !== "string")
    )
      throw new ImportError("SELECTION_NOT_READY", 422);
    const hash = digest({ envelope, selected: [...selected].sort() }),
      c = await this.db.connect();
    try {
      await this.guard(c);
      await c.query("begin");
      await c.query("select pg_advisory_xact_lock(921822)");
      await c.query("lock table radar_admin_sandbox.namespaces in share mode");
      const previous = (
        await c.query(
          "select * from radar_admin_sandbox.batches where request_key=$1",
          [key],
        )
      ).rows[0];
      if (previous) {
        if (previous.actor !== actor)
          throw new ImportError("IDEMPOTENCY_SCOPE_MISMATCH", 409);
        if (previous.request_hash !== hash)
          throw new ImportError("IDEMPOTENCY_CONFLICT", 409);
        await c.query("commit");
        return { status: 200, receipt: previous.receipt };
      }
      const proof = this.open(token);
      if (proof.actor !== actor || proof.session !== session)
        throw new ImportError("PREVIEW_STALE", 409);
      if (proof.expiry < Date.now())
        throw new ImportError("PREVIEW_EXPIRED", 409);
      const p = previewImport(envelope, await this.snapshot(c));
      if (
        proof.schema !== SCHEMA ||
        p.envelope_hash !== proof.hash ||
        p.fingerprint !== proof.fingerprint
      )
        throw new ImportError("PREVIEW_STALE", 409);
      const ready = new Map(
        p.items
          .filter((r) => r.classification === "ready")
          .map((r) => [r.item_id, r.item!]),
      );
      if (selected.some((id) => !ready.has(id) || !proof.ids.includes(id)))
        throw new ImportError("SELECTION_NOT_READY", 422);
      const batch = randomUUID(),
        created_at = new Date().toISOString(),
        items: any[] = [];
      for (const id of selected) {
        const i = ready.get(id)!;
        const target_id = await this.insert(c, i, batch);
        items.push({
          item_id: id,
          kind: i.kind,
          target_id,
          initial_review: "pending",
        });
        if (failAfter === items.length) throw Error("INJECTED_AUDIT_FAILURE");
      }
      const receipt = {
        status: "committed",
        batch_id: batch,
        schema: SCHEMA,
        created_at,
        actor,
        client_request_id: key,
        request_hash: hash,
        counts: p.counts,
        selected_count: selected.length,
        not_selected_count: p.items.length - selected.length,
        items,
      };
      await c.query(
        "insert into radar_admin_sandbox.batches values($1,$2,$3,$4,$5)",
        [batch, key, actor, hash, JSON.stringify(receipt)],
      );
      for (const item of items)
        await c.query(
          "insert into radar_admin_sandbox.items values($1,$2,$3,$4,$5)",
          [
            batch,
            item.item_id,
            item.kind,
            item.kind === "event" ? item.target_id : null,
            item.kind === "poi" ? item.target_id : null,
          ],
        );
      await c.query(
        "insert into radar_admin_sandbox.audit(batch_id,actor,action) values($1,$2,'commit')",
        [batch, actor],
      );
      if (failAfter === -1) throw Error("INJECTED_POST_AUDIT_FAILURE");
      await c.query("commit");
      return { status: 201, receipt };
    } catch (e) {
      await c.query("rollback");
      throw e;
    } finally {
      c.release();
    }
  }
  private async insert(c: PoolClient, i: ImportItem, batch: string) {
    const source_id = digest(JSON.parse(identity(i)));
    const metadata = {
      ingest_kind: "manual",
      admin_import: {
        schema: SCHEMA,
        batch_id: batch,
        material_hash: materialHash(i),
        provenance: i.provenance,
      },
      review: "pending",
    };
    let row: any;
    if (i.kind === "event") {
      const { end_time_basis, ...data } = i.data;
      row = {
        ...data,
        source: "admin_manual",
        external_id: source_id,
        country_code: "BR",
        source_url: i.provenance.official_url,
        confidence: 0.5,
        end_time_source: end_time_basis === "official" ? "source" : "estimated",
        status: "active",
        review_status: "pending",
        last_verified_at: i.provenance.verified_at,
        metadata,
      };
    } else
      row = {
        ...normalizeRadarPoiAdminV1({
          ...i.data,
          source: "admin_manual",
          source_external_id: source_id,
          source_url: i.provenance.official_url,
          source_confidence: 0.5,
          ingest_kind: "manual",
        }),
        last_verified_at: i.provenance.verified_at,
        metadata,
      };
    const keys = Object.keys(row);
    const result = await c.query(
      `insert into ${i.kind === "event" ? "sr_event_opportunities" : "sr_radar_pois"} (${keys.join(",")}) values(${keys.map((_, n) => "$" + (n + 1)).join(",")}) returning id`,
      keys.map((k) => row[k]),
    );
    return result.rows[0].id as string;
  }
  async receipt(batch: string, actor: string) {
    await this.guard();
    return (
      (
        await this.db.query(
          "select receipt from radar_admin_sandbox.batches where batch_id=$1 and actor=$2",
          [batch, actor],
        )
      ).rows[0]?.receipt ?? null
    );
  }
}
