import { authenticateDevice } from "@/src/device-auth";
import { sendDriverPush } from "@/src/notifications";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const auth = await authenticateDevice(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  try {
    const result = await sendDriverPush(auth.driverId, {
      category: "test",
      title: "Sr. Rotas conectado",
      body: "As notificações operacionais estão funcionando neste aparelho.",
      route: "perfil",
      source: "self_test",
      dedupeKey: `test:${auth.driverId}:${Math.floor(Date.now() / 60000)}`,
    });
    return Response.json({ ...result, message: "Notificação de teste enviada." });
  } catch (error) {
    const message = error instanceof Error ? error.message : "notification_test_failed";
    return Response.json({ error: message }, { status: message === "onesignal_not_configured" ? 503 : 500 });
  }
}
