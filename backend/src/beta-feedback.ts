import { adminSupabase } from "./supabase";

const categories = new Set(["Geral","OCR","HUD","Conta/Login","Histórico","IA","MCP","Plano/Pix","Notificações","Bateria/Desempenho"]);
const severities = new Set(["Sugestão","Problema leve","Problema importante","Bloqueador"]);

function text(value: unknown, max: number) {
  return String(value ?? "").trim().slice(0, max);
}
function int(value: unknown, min: number, max: number) {
  const n = Number(value);
  return Number.isFinite(n) ? Math.max(min, Math.min(max, Math.trunc(n))) : null;
}

export async function saveBetaFeedback(driverId: string, body: Record<string, unknown>) {
  const category = text(body.category, 80);
  const severity = text(body.severity, 80);
  const message = text(body.message, 1600);
  if (!categories.has(category)) throw new Error("invalid_category");
  if (!severities.has(severity)) throw new Error("invalid_severity");
  if (message.length < 4) throw new Error("feedback_too_short");

  const inserted = await adminSupabase().from("beta_feedback").insert({
    driver_id: driverId,
    kind: "feedback",
    category,
    severity,
    message,
    app_version: text(body.app_version, 80) || null,
    version_code: int(body.version_code, 0, 1000000),
    android_sdk: int(body.android_sdk, 1, 1000),
    manufacturer: text(body.manufacturer, 80) || null,
    model: text(body.model, 100) || null,
    checklist_completed: int(body.checklist_completed, 0, 100),
    checklist_total: int(body.checklist_total, 0, 100),
    metadata: { source: "android_closed_beta_013" },
  }).select("id,created_at").single();

  if (inserted.error) throw new Error(inserted.error.message);
  return inserted.data;
}

export async function saveBetaCrash(driverId: string, body: Record<string, unknown>) {
  const eventId = text(body.event_id, 36);
  const exceptionClass = text(body.exception_class, 180);
  const stack = text(body.stack, 3500);
  if (!/^[0-9a-f-]{36}$/i.test(eventId) || !exceptionClass) throw new Error("invalid_crash_payload");

  const inserted = await adminSupabase().from("beta_feedback").insert({
    driver_id: driverId,
    kind: "crash",
    category: "Crash",
    severity: "Bloqueador",
    message: text(body.message, 320),
    app_version: text(body.app_version, 80) || null,
    version_code: int(body.version_code, 0, 1000000),
    android_sdk: int(body.android_sdk, 1, 1000),
    manufacturer: text(body.manufacturer, 80) || null,
    model: text(body.model, 100) || null,
    event_id: eventId,
    exception_class: exceptionClass,
    stack_trace: stack,
    metadata: {
      source: "android_closed_beta_013",
      occurred_at: text(body.occurred_at, 80),
      thread: text(body.thread, 80),
    },
  }).select("id,created_at").single();

  if (inserted.error) {
    if (inserted.error.code === "23505") return { duplicate: true };
    throw new Error(inserted.error.message);
  }
  return inserted.data;
}
