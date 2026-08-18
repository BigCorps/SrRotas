import { adminSupabase } from "./supabase";
import { serverEnv } from "./env";

async function deletePushIdentity(driverId: string) {
  const env = serverEnv();
  await fetch(`${env.supabaseUrl.replace(/\/$/, "")}/functions/v1/srrotas-delete-push-user`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${env.supabaseServiceRoleKey}`,
      apikey: env.supabaseServiceRoleKey,
    },
    body: JSON.stringify({ driver_id: driverId }),
    cache: "no-store",
    signal: AbortSignal.timeout(10000),
  }).catch(() => undefined);
}

export async function deleteDriverAccount(driverId: string) {
  const supabase = adminSupabase();
  const found = await supabase.from("drivers").select("id,auth_user_id").eq("id", driverId).maybeSingle();
  if (found.error) throw new Error(found.error.message);
  if (!found.data) return { deleted: true, alreadyDeleted: true };

  await deletePushIdentity(driverId);

  if (found.data.auth_user_id) {
    const authDelete = await supabase.auth.admin.deleteUser(String(found.data.auth_user_id));
    if (authDelete.error) throw new Error(authDelete.error.message);
    // The FK is ON DELETE CASCADE, but explicitly ensure the profile disappeared.
    await supabase.from("drivers").delete().eq("id", driverId);
  } else {
    const deleted = await supabase.from("drivers").delete().eq("id", driverId);
    if (deleted.error) throw new Error(deleted.error.message);
  }

  return { deleted: true, alreadyDeleted: false };
}
