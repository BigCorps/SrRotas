import { authenticateDevice } from "@/src/device-auth";
import { getNotificationPreferences, updateNotificationPreferences } from "@/src/notifications";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  try {
    return Response.json(await getNotificationPreferences(auth.driverId));
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "notification_preferences_failed" }, { status: 500 });
  }
}

export async function PATCH(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const body = await request.json().catch(() => ({})) as Record<string, unknown>;
  try {
    return Response.json(await updateNotificationPreferences(auth.driverId, {
      operational_enabled: typeof body.operational_enabled === "boolean" ? body.operational_enabled : undefined,
      journey_summary_enabled: typeof body.journey_summary_enabled === "boolean" ? body.journey_summary_enabled : undefined,
      sync_alerts_enabled: typeof body.sync_alerts_enabled === "boolean" ? body.sync_alerts_enabled : undefined,
      product_updates_enabled: typeof body.product_updates_enabled === "boolean" ? body.product_updates_enabled : undefined,
    }));
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "notification_preferences_failed" }, { status: 500 });
  }
}
