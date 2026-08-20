import { requireImportAccess } from "@/src/admin-imports";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const DELETABLE_STATUSES = new Set(["receiving", "staged", "failed", "archived"]);

type BatchOwner = {
  id: string;
  created_by_auth_user_id: string | null;
  created_by_email: string | null;
  status: string;
  original_filename: string;
};

async function findOwnedBatch(id: string, authUserId: string, email: string, isOwner: boolean) {
  const result = await adminSupabase()
    .from("historical_import_batches")
    .select("id,created_by_auth_user_id,created_by_email,status,original_filename")
    .eq("id", id)
    .maybeSingle();

  if (result.error || !result.data) return null;
  const batch = result.data as BatchOwner;
  if (isOwner) return batch;

  const sameAuthUser = String(batch.created_by_auth_user_id || "") === authUserId;
  const legacySameEmail = !batch.created_by_auth_user_id && String(batch.created_by_email || "").toLowerCase() === email;
  return sameAuthUser || legacySameEmail ? batch : null;
}

export async function DELETE(request: Request, context: { params: Promise<{ id: string }> }) {
  const checked = await requireImportAccess(request);
  if (checked.response) return checked.response;
  const actor = checked.actor!;
  const { id } = await context.params;

  const batch = await findOwnedBatch(id, actor.authUserId, actor.email, actor.isOwner);
  if (!batch) return Response.json({ error: "batch_not_found" }, { status: 404 });
  if (!DELETABLE_STATUSES.has(batch.status)) {
    return Response.json({ error: "batch_not_deletable", status: batch.status }, { status: 409 });
  }

  // historical_import_rows.batch_id possui ON DELETE CASCADE. Apagar o batch
  // remove também as linhas de staging, sem tocar em ride_offers ou outcomes.
  const deleted = await adminSupabase()
    .from("historical_import_batches")
    .delete()
    .eq("id", id)
    .select("id")
    .maybeSingle();

  if (deleted.error) return Response.json({ error: deleted.error.message }, { status: 500 });
  if (!deleted.data) return Response.json({ error: "batch_not_found" }, { status: 404 });

  return Response.json({ ok: true, deleted_batch_id: id, original_filename: batch.original_filename });
}
