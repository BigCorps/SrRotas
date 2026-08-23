import { authenticateDevice } from "@/src/device-auth";
import {
  billingCookieHeader,
  createBillingWebSession,
} from "@/src/billing-auth";
import { newToken, sha256 } from "@/src/security";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function targetPath(value: unknown) {
  const text = String(value ?? "/app").trim();
  if (!text.startsWith("/app") || text.startsWith("//") || text.includes("\\")) {
    return "/app";
  }
  return text.slice(0, 300);
}

export async function POST(request: Request) {
  const device = await authenticateDevice(request);
  if (!device) return Response.json({ error: "unauthorized" }, { status: 401 });

  const body = await request.json().catch(() => ({}));
  const target = targetPath(body?.target_path);
  const token = `srhandoff_${newToken()}`;
  const expiresAt = new Date(Date.now() + 2 * 60 * 1000).toISOString();
  const { error } = await adminSupabase().from("web_handoff_tokens").insert({
    driver_id: device.driverId,
    token_hash: sha256(token),
    target_path: target,
    expires_at: expiresAt,
  });
  if (error) {
    return Response.json({ error: "handoff_create_failed" }, { status: 500 });
  }

  const url = new URL("/api/v1/web/handoff", request.url);
  url.searchParams.set("token", token);
  return Response.json({
    ok: true,
    handoff_url: url.toString(),
    expires_at: expiresAt,
  });
}

export async function GET(request: Request) {
  const url = new URL(request.url);
  const token = url.searchParams.get("token") || "";
  if (!token.startsWith("srhandoff_") || token.length < 40) {
    return new Response("Link de acesso inválido.", { status: 400 });
  }

  const now = new Date().toISOString();
  const consumed = await adminSupabase()
    .from("web_handoff_tokens")
    .update({ used_at: now })
    .eq("token_hash", sha256(token))
    .is("used_at", null)
    .gt("expires_at", now)
    .select("driver_id,target_path")
    .maybeSingle();

  if (consumed.error || !consumed.data) {
    return new Response("Este link expirou ou já foi utilizado.", { status: 410 });
  }

  const session = await createBillingWebSession(String(consumed.data.driver_id));
  const target = targetPath(consumed.data.target_path);
  const destination = new URL(target, request.url);
  return new Response(null, {
    status: 303,
    headers: {
      Location: destination.toString(),
      "Set-Cookie": billingCookieHeader(session.token),
      "Cache-Control": "no-store",
      "Referrer-Policy": "no-referrer",
    },
  });
}
