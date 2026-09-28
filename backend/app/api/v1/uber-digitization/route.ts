import { accessDeniedResponse } from "@/src/access";
import { authenticateDevice } from "@/src/device-auth";
import { saveCompletedRides, saveSessionImport } from "@/src/uber-digitization-026";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });

  const denied = accessDeniedResponse(auth.access, "can_operate");
  if (denied) return denied;

  const body = await request.json().catch(() => null);
  if (!body || typeof body !== "object") {
    return Response.json({ error: "invalid_json" }, { status: 400 });
  }

  try {
    const input = body as Record<string, unknown>;
    const action = String(input.action ?? "");
    if (action === "session_summary") {
      return Response.json({
        ok: true,
        session: await saveSessionImport(auth.driverId, input),
      });
    }
    if (action === "completed_rides") {
      return Response.json({
        ok: true,
        rides: await saveCompletedRides(
          auth.driverId,
          auth.deviceId,
          input,
        ),
      });
    }
    return Response.json({ error: "invalid_action" }, { status: 400 });
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "uber_digitization_failed";
    return Response.json(
      { error: message },
      { status: message.endsWith("required") ? 400 : 500 },
    );
  }
}
