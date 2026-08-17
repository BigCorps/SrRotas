import { authenticateDevice } from "@/src/device-auth";
import { adminSupabase } from "@/src/supabase";
import { newToken, sha256 } from "@/src/security";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const ctx = await authenticateDevice(request);
  if (!ctx) return Response.json({ error: "unauthorized" }, { status: 401 });

  const { data, error } = await adminSupabase()
    .from("mcp_access_tokens")
    .select("id,name,token_prefix,last_used_at,created_at")
    .eq("driver_id", ctx.driverId)
    .eq("revoked", false)
    .order("created_at", { ascending: false })
    .limit(12);

  if (error) return Response.json({ error: error.message }, { status: 500 });
  return Response.json({ tokens: data ?? [], endpoint: new URL("/mcp", request.url).toString() });
}

export async function POST(request: Request) {
  const ctx = await authenticateDevice(request);
  if (!ctx) return Response.json({ error: "unauthorized" }, { status: 401 });

  const body = await request.json().catch(() => ({}));
  const name = String(body?.name ?? "Minha integração").trim().slice(0, 80) || "Minha integração";
  const supabase = adminSupabase();

  const count = await supabase
    .from("mcp_access_tokens")
    .select("id", { count: "exact", head: true })
    .eq("driver_id", ctx.driverId)
    .eq("revoked", false);
  if (count.error) return Response.json({ error: count.error.message }, { status: 500 });
  if ((count.count ?? 0) >= 6) {
    return Response.json({ error: "too_many_active_mcp_tokens", message: "Revogue uma chave antiga antes de criar outra." }, { status: 409 });
  }

  const secret = `srmcp_${newToken()}`;
  const prefix = secret.slice(0, 14);
  const { data, error } = await supabase
    .from("mcp_access_tokens")
    .insert({
      driver_id: ctx.driverId,
      name,
      token_hash: sha256(secret),
      token_prefix: prefix,
    })
    .select("id,name,token_prefix,created_at")
    .single();

  if (error) return Response.json({ error: error.message }, { status: 500 });
  return Response.json({
    ...data,
    token: secret,
    endpoint: new URL("/mcp", request.url).toString(),
  }, { status: 201 });
}

export async function DELETE(request: Request) {
  const ctx = await authenticateDevice(request);
  if (!ctx) return Response.json({ error: "unauthorized" }, { status: 401 });

  const id = new URL(request.url).searchParams.get("id")?.trim() || "";
  if (!id) return Response.json({ error: "token_id_required" }, { status: 400 });

  const { data, error } = await adminSupabase()
    .from("mcp_access_tokens")
    .update({ revoked: true })
    .eq("id", id)
    .eq("driver_id", ctx.driverId)
    .eq("revoked", false)
    .select("id")
    .maybeSingle();

  if (error) return Response.json({ error: error.message }, { status: 500 });
  if (!data) return Response.json({ error: "not_found" }, { status: 404 });
  return Response.json({ ok: true });
}
