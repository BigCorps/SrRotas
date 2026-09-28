import { adminSupabase } from "./supabase";
import { serverEnv } from "./env";

async function deletePushIdentity(driverId: string) {
  const env = serverEnv();
  const response = await fetch(
    `${env.supabaseUrl.replace(/\/$/, "")}/functions/v1/srrotas-delete-push-user`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${env.supabaseServiceRoleKey}`,
        apikey: env.supabaseServiceRoleKey,
      },
      body: JSON.stringify({ driver_id: driverId }),
      cache: "no-store",
      signal: AbortSignal.timeout(15000),
    },
  );

  const payload = await response.json().catch(() => ({})) as Record<string, unknown>;
  if (!response.ok || payload.success !== true) {
    throw new Error("push_identity_delete_failed");
  }
  return payload;
}

export async function deleteDriverAccount(driverId: string) {
  const supabase = adminSupabase();
  const found = await supabase
    .from("drivers")
    .select("id,auth_user_id")
    .eq("id", driverId)
    .maybeSingle();

  if (found.error) throw new Error(found.error.message);
  if (!found.data) return { deleted: true, alreadyDeleted: true };

  // Privacidade 1.0: a identidade externa é eliminada (ou confirmada ausente)
  // ANTES da conta. Falha externa mantém a conta intacta para permitir retry.
  await deletePushIdentity(driverId);

  if (found.data.auth_user_id) {
    const authDelete = await supabase.auth.admin.deleteUser(
      String(found.data.auth_user_id),
    );
    if (authDelete.error) throw new Error(authDelete.error.message);

    // FK de Auth faz cascade, mas garantimos o perfil caso haja atraso.
    const cleanup = await supabase
      .from("drivers")
      .delete()
      .eq("id", driverId);
    if (cleanup.error) throw new Error(cleanup.error.message);
  } else {
    const deleted = await supabase
      .from("drivers")
      .delete()
      .eq("id", driverId);
    if (deleted.error) throw new Error(deleted.error.message);
  }

  return { deleted: true, alreadyDeleted: false };
}
