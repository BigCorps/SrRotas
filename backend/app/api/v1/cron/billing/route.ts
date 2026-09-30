import { serverEnv } from "@/src/env";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 60;

export async function GET(request: Request) {
  const env = serverEnv();
  if (!env.cronSecret) {
    return Response.json({ error: "cron_secret_missing" }, { status: 503 });
  }
  if (request.headers.get("authorization") !== `Bearer ${env.cronSecret}`) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }

  const response = await fetch(
    `${env.supabaseUrl.replace(/\/$/, "")}/functions/v1/srrotas-process-billing`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${env.supabaseServiceRoleKey}`,
        apikey: env.supabaseServiceRoleKey,
      },
      body: "{}",
      cache: "no-store",
      signal: AbortSignal.timeout(45000),
    },
  );

  const billing = await response.json().catch(() => ({})) as Record<string, unknown>;
  if (!response.ok) {
    console.error("sr_billing_cron_failed", {
      status: response.status,
      billing,
    });
    return Response.json(
      { error: "billing_processor_failed", status: response.status },
      { status: 502 },
    );
  }

  const supabase = adminSupabase();
  const [purge, oauthCleanup] = await Promise.all([
    supabase.rpc("sr_purge_expired_device_identities_v1"),
    supabase.rpc("sr_cleanup_mcp_oauth_v1"),
  ]);

  if (purge.error) {
    console.error("sr_device_identity_purge_failed", {
      message: purge.error.message,
    });
  }
  if (oauthCleanup.error) {
    console.error("sr_mcp_oauth_cleanup_failed", {
      message: oauthCleanup.error.message,
    });
  }

  return Response.json({
    ok: true,
    billing,
    purged_device_identities:
      purge.error ? null : Number(purge.data || 0),
    mcp_oauth_cleanup:
      oauthCleanup.error ? null : oauthCleanup.data,
    processed_at: new Date().toISOString(),
  });
}
