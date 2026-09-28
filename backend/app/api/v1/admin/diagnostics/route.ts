import { canAccessAdminOps } from "@/src/admin-access";
import { importActor } from "@/src/admin-imports";
import { allowSecurityAction } from "@/src/security-rate-limit";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const TRIAGE = new Set(["new", "reviewing", "resolved", "ignored"]);

function validUuid(value: unknown) {
  return /^[0-9a-f-]{36}$/i.test(String(value || ""));
}

async function actorFor(request: Request) {
  const actor = await importActor(request);
  if (!actor) return { actor: null, response: Response.json({ error: "unauthorized" }, { status: 401 }) };
  if (!actor.allowed || !canAccessAdminOps(actor.email)) {
    return { actor, response: Response.json({ error: "admin_ops_forbidden" }, { status: 403 }) };
  }
  return { actor, response: null };
}

function publicRecord(row: any, driver: any) {
  const metadata = row?.metadata && typeof row.metadata === "object" ? row.metadata : {};
  return {
    id: row.id,
    driver_id: row.driver_id,
    driver: driver ? {
      display_name: driver.display_name || "Motorista",
      email: driver.email || "",
    } : null,
    kind: row.kind,
    category: row.category,
    severity: row.severity,
    message: row.message,
    app_version: row.app_version,
    version_code: row.version_code,
    android_sdk: row.android_sdk,
    manufacturer: row.manufacturer,
    model: row.model,
    checklist_completed: row.checklist_completed,
    checklist_total: row.checklist_total,
    event_id: row.event_id,
    exception_class: row.exception_class,
    stack_trace: row.stack_trace ? String(row.stack_trace).slice(0, 3500) : null,
    occurred_at: metadata.occurred_at || null,
    thread: metadata.thread || null,
    triage_status: row.triage_status || "new",
    admin_note: row.admin_note || "",
    triaged_at: row.triaged_at,
    triaged_by: row.triaged_by,
    created_at: row.created_at,
  };
}

export async function GET(request: Request) {
  const checked = await actorFor(request);
  if (checked.response) return checked.response;

  const supabase: any = adminSupabase();
  const url = new URL(request.url);
  const kind = String(url.searchParams.get("kind") || "").trim();
  const severity = String(url.searchParams.get("severity") || "").trim();
  const triage = String(url.searchParams.get("triage") || "").trim();
  const q = String(url.searchParams.get("q") || "").trim().toLowerCase();
  const limit = Math.max(10, Math.min(Number(url.searchParams.get("limit") || 300) || 300, 500));

  let query = supabase
    .from("beta_feedback")
    .select("id,driver_id,kind,category,severity,message,app_version,version_code,android_sdk,manufacturer,model,checklist_completed,checklist_total,event_id,exception_class,stack_trace,metadata,triage_status,admin_note,triaged_at,triaged_by,created_at")
    .order("created_at", { ascending: false })
    .limit(limit);

  if (kind) query = query.eq("kind", kind);
  if (severity) query = query.eq("severity", severity);
  if (triage && TRIAGE.has(triage)) query = query.eq("triage_status", triage);

  const found = await query;
  if (found.error) return Response.json({ error: found.error.message }, { status: 500 });

  const rows = found.data ?? [];
  const ids = [...new Set(rows.map((row: any) => String(row.driver_id || "")).filter(Boolean))];
  let drivers: any[] = [];
  if (ids.length) {
    const driverResult = await supabase
      .from("drivers")
      .select("id,display_name,email")
      .in("id", ids);
    if (driverResult.error) return Response.json({ error: driverResult.error.message }, { status: 500 });
    drivers = driverResult.data ?? [];
  }
  const driverMap = new Map(drivers.map((driver: any) => [String(driver.id), driver]));

  let records = rows.map((row: any) => publicRecord(row, driverMap.get(String(row.driver_id))));
  if (q) {
    records = records.filter((row: any) =>
      [
        row.driver?.display_name,
        row.driver?.email,
        row.category,
        row.severity,
        row.message,
        row.app_version,
        row.model,
        row.exception_class,
      ].some((value) => String(value || "").toLowerCase().includes(q)),
    );
  }

  const now = Date.now();
  const summary = {
    total: records.length,
    new: records.filter((row: any) => row.triage_status === "new").length,
    reviewing: records.filter((row: any) => row.triage_status === "reviewing").length,
    resolved: records.filter((row: any) => row.triage_status === "resolved").length,
    ignored: records.filter((row: any) => row.triage_status === "ignored").length,
    crashes: records.filter((row: any) => row.kind === "crash").length,
    blockers: records.filter((row: any) => row.severity === "Bloqueador").length,
    last_24h: records.filter((row: any) => now - new Date(row.created_at).getTime() <= 86400000).length,
  };

  return Response.json({
    generated_at: new Date().toISOString(),
    summary,
    records,
    privacy: {
      raw_ocr: false,
      coordinates: false,
      device_identity: false,
      tokens: false,
    },
  }, { headers: { "Cache-Control": "no-store" } });
}

export async function PATCH(request: Request) {
  const checked = await actorFor(request);
  if (checked.response) return checked.response;

  const allowed = await allowSecurityAction(
    request,
    "admin-diagnostic-triage",
    checked.actor!.email,
    60,
    900,
  ).catch(() => false);
  if (!allowed) return Response.json({ error: "rate_limited" }, { status: 429 });

  const body = await request.json().catch(() => ({}));
  const id = String(body?.id || "");
  const status = String(body?.triage_status || "");
  const note = String(body?.admin_note || "").trim().slice(0, 1600);

  if (!validUuid(id) || !TRIAGE.has(status)) {
    return Response.json({ error: "invalid_diagnostic_triage" }, { status: 400 });
  }

  const supabase: any = adminSupabase();
  const current = await supabase
    .from("beta_feedback")
    .select("id,driver_id,triage_status,admin_note")
    .eq("id", id)
    .maybeSingle();

  if (current.error) return Response.json({ error: current.error.message }, { status: 500 });
  if (!current.data) return Response.json({ error: "diagnostic_not_found" }, { status: 404 });

  const changedNote = String(current.data.admin_note || "") !== note;
  const updated = await supabase
    .from("beta_feedback")
    .update({
      triage_status: status,
      admin_note: note || null,
      triaged_at: new Date().toISOString(),
      triaged_by: checked.actor!.email,
    })
    .eq("id", id)
    .select("id,driver_id,triage_status,admin_note,triaged_at,triaged_by")
    .single();

  if (updated.error) return Response.json({ error: updated.error.message }, { status: 500 });

  const audit = await supabase.from("admin_diagnostic_audit").insert({
    diagnostic_id: id,
    admin_email: checked.actor!.email,
    from_status: current.data.triage_status || "new",
    to_status: status,
    note_changed: changedNote,
  });
  if (audit.error) return Response.json({ error: audit.error.message }, { status: 500 });

  console.info("sr_admin_diagnostic_triage", {
    admin: checked.actor!.email,
    diagnostic_id: id,
    driver_id: current.data.driver_id,
    from: current.data.triage_status,
    to: status,
    note_changed: changedNote,
  });

  return Response.json({ ok: true, diagnostic: updated.data });
}
