import { authenticateBillingActor } from "@/src/billing-auth";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const COLORS = [
  "shortcut01",
  "shortcut02",
  "shortcut03",
  "shortcut04",
  "shortcut05",
  "shortcut06",
] as const;

type ColorToken = (typeof COLORS)[number];
type MessageInput = {
  order: number;
  shortLabel: string;
  accessibilityLabel: string | null;
  text: string;
  colorToken: ColorToken;
  enabled: boolean;
};

function rowToMessage(row: Record<string, unknown>) {
  const order = Number(row.sort_order) || 0;
  return {
    id: `slot-${order + 1}`,
    order,
    shortLabel: String(row.short_label ?? order + 1),
    accessibilityLabel: row.accessibility_label ? String(row.accessibility_label) : null,
    text: String(row.message_text ?? ""),
    colorToken: String(row.color_token ?? "shortcut01"),
    enabled: Boolean(row.enabled),
  };
}

async function readMessages(driverId: string) {
  const { data, error } = await adminSupabase()
    .from("driver_message_presets")
    .select("sort_order,short_label,accessibility_label,message_text,color_token,enabled")
    .eq("driver_id", driverId)
    .order("sort_order", { ascending: true });
  if (error) throw new Error(error.message);
  return (data ?? []).map((row) => rowToMessage(row as Record<string, unknown>));
}

function parseMessages(value: unknown): MessageInput[] {
  if (!Array.isArray(value)) throw new Error("messages_must_be_array");
  if (value.length > 12) throw new Error("too_many_messages");

  const seen = new Set<number>();
  return value.map((raw, index) => {
    if (!raw || typeof raw !== "object") throw new Error(`invalid_message_${index}`);
    const item = raw as Record<string, unknown>;
    const order = Number(item.order);
    if (!Number.isInteger(order) || order < 0 || order > 99 || seen.has(order)) {
      throw new Error(`invalid_order_${index}`);
    }
    seen.add(order);

    const shortLabel = String(item.shortLabel ?? "").trim().slice(0, 2);
    const text = String(item.text ?? "").slice(0, 500);
    const accessibility = String(item.accessibilityLabel ?? "").trim().slice(0, 120) || null;
    const color = String(item.colorToken ?? "") as ColorToken;
    if (!shortLabel) throw new Error(`invalid_label_${index}`);
    if (!text.trim()) throw new Error(`empty_text_${index}`);
    if (!COLORS.includes(color)) throw new Error(`invalid_color_${index}`);

    return {
      order,
      shortLabel,
      accessibilityLabel: accessibility,
      text,
      colorToken: color,
      enabled: item.enabled !== false,
    };
  });
}

export async function GET(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  try {
    return Response.json({ ok: true, schemaVersion: 1, messages: await readMessages(auth.driverId) });
  } catch (error) {
    return Response.json(
      { error: error instanceof Error ? error.message : "message_presets_failed" },
      { status: 500 },
    );
  }
}

export async function PUT(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });

  const body = await request.json().catch(() => null);
  try {
    const messages = parseMessages(body?.messages);
    const db = adminSupabase();

    if (messages.length === 0) {
      const deleted = await db.from("driver_message_presets").delete().eq("driver_id", auth.driverId);
      if (deleted.error) throw new Error(deleted.error.message);
    } else {
      const orders = messages.map((item) => item.order);
      const rows = messages.map((item) => ({
        driver_id: auth.driverId,
        sort_order: item.order,
        short_label: item.shortLabel,
        accessibility_label: item.accessibilityLabel,
        message_text: item.text,
        color_token: item.colorToken,
        enabled: item.enabled,
        updated_at: new Date().toISOString(),
      }));

      const saved = await db
        .from("driver_message_presets")
        .upsert(rows, { onConflict: "driver_id,sort_order" });
      if (saved.error) throw new Error(saved.error.message);

      const existing = await db
        .from("driver_message_presets")
        .select("sort_order")
        .eq("driver_id", auth.driverId);
      if (existing.error) throw new Error(existing.error.message);
      const stale = (existing.data ?? [])
        .map((row) => Number(row.sort_order))
        .filter((order) => !orders.includes(order));
      if (stale.length) {
        const removed = await db
          .from("driver_message_presets")
          .delete()
          .eq("driver_id", auth.driverId)
          .in("sort_order", stale);
        if (removed.error) throw new Error(removed.error.message);
      }
    }

    return Response.json({ ok: true, schemaVersion: 1, messages: await readMessages(auth.driverId) });
  } catch (error) {
    const message = error instanceof Error ? error.message : "message_presets_failed";
    const bad = /^(messages_|too_many_|invalid_|empty_)/.test(message);
    return Response.json({ error: message }, { status: bad ? 400 : 500 });
  }
}
