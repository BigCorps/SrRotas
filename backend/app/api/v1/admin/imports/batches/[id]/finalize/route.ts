import { requireImportAccess } from "@/src/admin-imports";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

async function countRows(batchId: string, status?: string) {
  let query = adminSupabase()
    .from("historical_import_rows")
    .select("id", { head: true, count: "exact" })
    .eq("batch_id", batchId);
  if (status) query = query.eq("validation_status", status);
  const result = await query;
  if (result.error) throw new Error(result.error.message);
  return result.count ?? 0;
}

export async function POST(request: Request, context: { params: Promise<{ id: string }> }) {
  const checked = await requireImportAccess(request);
  if (checked.response) return checked.response;
  const actor = checked.actor!;
  const { id } = await context.params;

  const found = await adminSupabase()
    .from("historical_import_batches")
    .select("id,created_by_driver_id,status")
    .eq("id", id)
    .maybeSingle();
  if (found.error || !found.data) return Response.json({ error: "batch_not_found" }, { status: 404 });
  if (!actor.isOwner && String(found.data.created_by_driver_id) !== actor.driverId) return Response.json({ error: "forbidden" }, { status: 403 });

  try {
    const [received, valid, partial, invalid, duplicate] = await Promise.all([
      countRows(id), countRows(id, "valid"), countRows(id, "partial"), countRows(id, "invalid"), countRows(id, "duplicate"),
    ]);

    const { data, error } = await adminSupabase()
      .from("historical_import_batches")
      .update({
        status: "staged",
        received_count: received,
        valid_count: valid,
        partial_count: partial,
        invalid_count: invalid,
        duplicate_count: duplicate,
        finalized_at: new Date().toISOString(),
      })
      .eq("id", id)
      .select("id,status,received_count,valid_count,partial_count,invalid_count,duplicate_count,finalized_at")
      .single();

    if (error) return Response.json({ error: error.message }, { status: 500 });
    return Response.json({ ok: true, batch: data });
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "finalize_failed" }, { status: 500 });
  }
}
