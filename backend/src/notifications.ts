import { adminSupabase } from "./supabase";
import { serverEnv } from "./env";

export type NotificationPreferences = {
  operational_enabled: boolean;
  journey_summary_enabled: boolean;
  sync_alerts_enabled: boolean;
  product_updates_enabled: boolean;
};

const defaults: NotificationPreferences = {
  operational_enabled: true,
  journey_summary_enabled: true,
  sync_alerts_enabled: true,
  product_updates_enabled: false,
};

export async function getNotificationPreferences(driverId: string): Promise<NotificationPreferences> {
  const supabase = adminSupabase();
  const found = await supabase.from("notification_preferences")
    .select("operational_enabled,journey_summary_enabled,sync_alerts_enabled,product_updates_enabled")
    .eq("driver_id", driverId).maybeSingle();
  if (found.error) throw new Error(found.error.message);
  if (found.data) return found.data as NotificationPreferences;

  const inserted = await supabase.from("notification_preferences")
    .upsert({ driver_id: driverId, ...defaults }, { onConflict: "driver_id" })
    .select("operational_enabled,journey_summary_enabled,sync_alerts_enabled,product_updates_enabled")
    .single();
  if (inserted.error) throw new Error(inserted.error.message);
  return inserted.data as NotificationPreferences;
}

export async function updateNotificationPreferences(driverId: string, input: Partial<NotificationPreferences>) {
  const current = await getNotificationPreferences(driverId);
  const next: NotificationPreferences = {
    operational_enabled: typeof input.operational_enabled === "boolean" ? input.operational_enabled : current.operational_enabled,
    journey_summary_enabled: typeof input.journey_summary_enabled === "boolean" ? input.journey_summary_enabled : current.journey_summary_enabled,
    sync_alerts_enabled: typeof input.sync_alerts_enabled === "boolean" ? input.sync_alerts_enabled : current.sync_alerts_enabled,
    product_updates_enabled: typeof input.product_updates_enabled === "boolean" ? input.product_updates_enabled : current.product_updates_enabled,
  };
  const updated = await adminSupabase().from("notification_preferences").upsert({
    driver_id: driverId, ...next, updated_at: new Date().toISOString(),
  }, { onConflict: "driver_id" }).select(
    "operational_enabled,journey_summary_enabled,sync_alerts_enabled,product_updates_enabled"
  ).single();
  if (updated.error) throw new Error(updated.error.message);
  return updated.data as NotificationPreferences;
}

export async function sendDriverPush(
  driverId: string,
  input: {
    category: "operational" | "journey_summary" | "sync_alert" | "product_update" | "test";
    title: string;
    body: string;
    dedupeKey?: string;
    route?: string;
    source?: string;
  },
) {
  const env = serverEnv();
  const response = await fetch(`${env.supabaseUrl.replace(/\/$/,"")}/functions/v1/srrotas-send-push`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${env.supabaseServiceRoleKey}`,
      apikey: env.supabaseServiceRoleKey,
    },
    body: JSON.stringify({
      driver_id: driverId,
      category: input.category,
      title: input.title,
      body: input.body,
      dedupe_key: input.dedupeKey ?? null,
      data: { route: input.route ?? "", source: input.source ?? "backend" },
    }),
    cache: "no-store",
  });
  const payload = await response.json().catch(() => ({})) as Record<string, unknown>;
  if (!response.ok) throw new Error(String(payload.error ?? `push_http_${response.status}`));
  return payload;
}
