import { requireImportAccess } from "@/src/admin-imports";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function text(value: unknown, max: number) {
  return String(value ?? "").trim().slice(0, max);
}

export async function GET(request: Request) {
  const checked = await requireImportAccess(request);
  if (checked.response) return checked.response;
  const actor = checked.actor!;

  let query = adminSupabase()
    .from("historical_import_batches")
    .select("id,created_by_email,source_name,original_filename,file_size_bytes,file_sha256,format,status,received_count,valid_count,partial_count,invalid_count,duplicate_count,created_at,finalized_at")
    .order("created_at", { ascending: false })
    .limit(30);

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
  const sourceName = text(body?.source_name, 120) || "historical_screenshot_gpt";
  const format = text(body?.format, 20).toLowerCase();
  const fileSha256 = text(body?.file_sha256, 64).toLowerCase();
  const fileSize = Number(body?.file_size_bytes ?? 0);

  if (!originalFilename) return Response.json({ error: "filename_required" }, { status: 400 });
  if (!["jsonl", "json"].includes(format)) return Response.json({ error: "format_not_supported" }, { status: 400 });
  if (fileSha256 && !/^[a-f0-9]{64}$/.test(fileSha256)) return Response.json({ error: "invalid_file_sha256" }, { status: 400 });

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
    })
    .select("id,status,created_at")
    .single();

  if (error) return Response.json({ error: error.message }, { status: 500 });
  return Response.json({ ok: true, batch: data }, { status: 201 });
}
