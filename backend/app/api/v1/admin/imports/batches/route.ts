import { HISTORICAL_SCHEMA_V1, requireImportAccess } from "@/src/admin-imports";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function text(value: unknown, max: number) {
  return String(value ?? "").trim().slice(0, max);
}

function uuidOrNull(value: unknown) {
  const candidate = text(value, 60).toLowerCase();
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(candidate) ? candidate : null;
}

export async function GET(request: Request) {
  const checked = await requireImportAccess(request);
  if (checked.response) return checked.response;
  const actor = checked.actor!;

  let query = adminSupabase()
    .from("historical_import_batches")
    .select("id,created_by_email,source_name,original_filename,file_size_bytes,file_sha256,format,status,received_count,valid_count,partial_count,invalid_count,duplicate_count,created_at,finalized_at,schema_version,extractor_version,supersedes_batch_id,processing_manifest,quality_summary,demand_temporal_ready_count,route_flow_ready_count,financial_ready_count,fully_ready_count")
    .order("created_at", { ascending: false })
    .limit(40);

  if (!actor.isOwner) query = query.eq("created_by_auth_user_id", actor.authUserId);
  const { data, error } = await query;
  if (error) return Response.json({ error: error.message }, { status: 500 });
  return Response.json({ batches: data ?? [] });
}

export async function POST(request: Request) {
  const checked = await requireImportAccess(request);
  if (checked.response) return checked.response;
  const actor = checked.actor!;

  const body = await request.json().catch(() => ({}));
  const originalFilename = text(body?.original_filename, 260);
  const sourceName = text(body?.source_name, 120) || "historical_screenshot_v7_1";
  const format = text(body?.format, 20).toLowerCase();
  const fileSha256 = text(body?.file_sha256, 64).toLowerCase();
  const fileSize = Number(body?.file_size_bytes ?? 0);
  const schemaVersion = text(body?.schema_version, 100) || "legacy";
  const extractorVersion = text(body?.extractor_version, 80) || null;
  const supersedesBatchId = uuidOrNull(body?.supersedes_batch_id);
  const processingManifest = body?.processing_manifest && typeof body.processing_manifest === "object" && !Array.isArray(body.processing_manifest)
    ? body.processing_manifest
    : {};

  if (!originalFilename) return Response.json({ error: "filename_required" }, { status: 400 });
  if (!["jsonl", "json"].includes(format)) return Response.json({ error: "format_not_supported" }, { status: 400 });
  if (fileSha256 && !/^[a-f0-9]{64}$/.test(fileSha256)) return Response.json({ error: "invalid_file_sha256" }, { status: 400 });
  if (schemaVersion !== "legacy" && schemaVersion !== HISTORICAL_SCHEMA_V1) {
    return Response.json({ error: "schema_not_supported", schema_version: schemaVersion }, { status: 400 });
  }

  if (supersedesBatchId) {
    const prior = await adminSupabase()
      .from("historical_import_batches")
      .select("id")
      .eq("id", supersedesBatchId)
      .maybeSingle();
    if (prior.error || !prior.data) return Response.json({ error: "superseded_batch_not_found" }, { status: 400 });
  }

  const { data, error } = await adminSupabase()
    .from("historical_import_batches")
    .insert({
      created_by_auth_user_id: actor.authUserId,
      created_by_driver_id: null,
      created_by_email: actor.email,
      source_name: sourceName,
      original_filename: originalFilename,
      file_size_bytes: Number.isFinite(fileSize) && fileSize >= 0 ? Math.round(fileSize) : 0,
      file_sha256: fileSha256 || null,
      format,
      status: "receiving",
      schema_version: schemaVersion,
      extractor_version: extractorVersion,
      supersedes_batch_id: supersedesBatchId,
      processing_manifest: processingManifest,
    })
    .select("id,status,created_at,schema_version,extractor_version,supersedes_batch_id")
    .single();

  if (error) return Response.json({ error: error.message }, { status: 500 });
  return Response.json({ ok: true, batch: data }, { status: 201 });
}
