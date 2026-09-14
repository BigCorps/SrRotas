import { requireImportAccess } from "@/src/admin-imports";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

async function countRows(batchId: string, status?: string) {
  let query = adminSupabase().from("historical_import_rows").select("id", { head: true, count: "exact" }).eq("batch_id", batchId);
  if (status) query = query.eq("validation_status", status);
  const result = await query;
  if (result.error) throw new Error(result.error.message);
  return result.count ?? 0;
}

async function countReady(batchId: string, column: string) {
  const result = await adminSupabase()
    .from("historical_import_rows")
    .select("id", { head: true, count: "exact" })
    .eq("batch_id", batchId)
    .eq(column, true)
    .neq("validation_status", "duplicate")
    .neq("validation_status", "invalid");
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
    .select("id,created_by_auth_user_id,created_by_email,status")
    .eq("id", id)
    .maybeSingle();
  if (found.error || !found.data) return Response.json({ error: "batch_not_found" }, { status: 404 });
  if (!actor.isOwner) {
    const sameAuthUser = String(found.data.created_by_auth_user_id || "") === actor.authUserId;
    const legacySameEmail = !found.data.created_by_auth_user_id && String(found.data.created_by_email || "").toLowerCase() === actor.email;
    if (!sameAuthUser && !legacySameEmail) return Response.json({ error: "forbidden" }, { status: 403 });
  }

  try {
    const [received, valid, partial, invalid, duplicate, demandReady, routeReady, financialReady, fullyReady] = await Promise.all([
      countRows(id), countRows(id, "valid"), countRows(id, "partial"), countRows(id, "invalid"), countRows(id, "duplicate"),
      countReady(id, "quality_demand_temporal_ready"), countReady(id, "quality_route_flow_ready"),
      countReady(id, "quality_financial_ready"), countReady(id, "quality_fully_ready"),
    ]);

    const summaryResult = await adminSupabase().rpc("sr_historical_import_quality_summary_v1", { target_batch: id });
    if (summaryResult.error) throw new Error(summaryResult.error.message);

    const { data, error } = await adminSupabase()
      .from("historical_import_batches")
      .update({
        status: "staged",
        received_count: received,
        valid_count: valid,
        partial_count: partial,
        invalid_count: invalid,
        duplicate_count: duplicate,
        demand_temporal_ready_count: demandReady,
        route_flow_ready_count: routeReady,
        financial_ready_count: financialReady,
        fully_ready_count: fullyReady,
        quality_summary: summaryResult.data ?? {},
        finalized_at: new Date().toISOString(),
      })
      .eq("id", id)
      .select("id,status,received_count,valid_count,partial_count,invalid_count,duplicate_count,demand_temporal_ready_count,route_flow_ready_count,financial_ready_count,fully_ready_count,quality_summary,finalized_at")
      .single();

    if (error) return Response.json({ error: error.message }, { status: 500 });
    return Response.json({ ok: true, batch: data });
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "finalize_failed" }, { status: 500 });
  }
}
