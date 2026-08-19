import { IMPORT_CHUNK_LIMIT, requireImportAccess, validateHistoricalImportRow, type ValidatedImportRow } from "@/src/admin-imports";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type IncomingRow = { row_index?: unknown; payload?: unknown };

type StoredKeyRow = {
  source_file_sha256: string | null;
  semantic_key: string | null;
};

async function canUseBatch(batchId: string, authUserId: string, email: string, isOwner: boolean) {
  const { data, error } = await adminSupabase()
    .from("historical_import_batches")
    .select("id,created_by_auth_user_id,created_by_email,status")
    .eq("id", batchId)
    .maybeSingle();
  if (error || !data) return null;
  if (!isOwner) {
    const sameAuthUser = String(data.created_by_auth_user_id || "") === authUserId;
    const legacySameEmail = !data.created_by_auth_user_id && String(data.created_by_email || "").toLowerCase() === email;
    if (!sameAuthUser && !legacySameEmail) return null;
  }
  return data;
}

export async function POST(request: Request, context: { params: Promise<{ id: string }> }) {
  const checked = await requireImportAccess(request);
  if (checked.response) return checked.response;
  const actor = checked.actor!;
  const { id } = await context.params;

  const batch = await canUseBatch(id, actor.authUserId, actor.email, actor.isOwner);
  if (!batch) return Response.json({ error: "batch_not_found" }, { status: 404 });
  if (batch.status !== "receiving") return Response.json({ error: "batch_not_receiving" }, { status: 409 });

  const body = await request.json().catch(() => ({}));
  const rows = Array.isArray(body?.rows) ? body.rows as IncomingRow[] : [];
  if (!rows.length || rows.length > IMPORT_CHUNK_LIMIT) {
    return Response.json({ error: "invalid_chunk", max_rows: IMPORT_CHUNK_LIMIT }, { status: 400 });
  }

  const normalizedIncoming = rows.map((item, index) => {
    const candidate = Number(item?.row_index);
    return { rowIndex: Number.isInteger(candidate) && candidate >= 0 ? candidate : index, payload: item?.payload };
  });

  // Idempotência por lote/índice: um retry de rede não transforma uma linha já
  // aceita em "duplicate" nem substitui o status validado anteriormente.
  const incomingIndexes = normalizedIncoming.map((item) => item.rowIndex);
  const existingIndexResult = await adminSupabase()
    .from("historical_import_rows")
    .select("row_index")
    .eq("batch_id", id)
    .in("row_index", incomingIndexes);
  if (existingIndexResult.error) return Response.json({ error: existingIndexResult.error.message }, { status: 500 });
  const existingIndexes = new Set<number>((existingIndexResult.data ?? []).map((row: { row_index: number }) => Number(row.row_index)));
  const freshIncoming = normalizedIncoming.filter((item) => !existingIndexes.has(item.rowIndex));

  if (!freshIncoming.length) {
    return Response.json({ ok: true, received: 0, replayed: rows.length, counts: { valid: 0, partial: 0, invalid: 0, duplicate: 0 } });
  }

  const validated = freshIncoming.map((item) => validateHistoricalImportRow(item.rowIndex, item.payload));

  const hashes = Array.from(new Set(validated.map((row) => row.sourceFileSha256).filter((value): value is string => Boolean(value))));
  const semantics = Array.from(new Set(validated.map((row) => row.semanticKey)));
  const existingHashes = new Set<string>();
  const existingSemantics = new Set<string>();

  if (hashes.length) {
    const found = await adminSupabase()
      .from("historical_import_rows")
      .select("source_file_sha256,semantic_key")
      .in("source_file_sha256", hashes);
    (found.data as StoredKeyRow[] | null)?.forEach((row: StoredKeyRow) => {
      if (row.source_file_sha256) existingHashes.add(row.source_file_sha256);
      if (row.semantic_key) existingSemantics.add(row.semantic_key);
    });
  }

  if (semantics.length) {
    const found = await adminSupabase()
      .from("historical_import_rows")
      .select("source_file_sha256,semantic_key")
      .in("semantic_key", semantics);
    (found.data as StoredKeyRow[] | null)?.forEach((row: StoredKeyRow) => {
      if (row.source_file_sha256) existingHashes.add(row.source_file_sha256);
      if (row.semantic_key) existingSemantics.add(row.semantic_key);
    });
  }

  const chunkHashes = new Set<string>();
  const chunkSemantics = new Set<string>();
  const finalRows = validated.map((row: ValidatedImportRow) => {
    const duplicateByHash = Boolean(row.sourceFileSha256 && (existingHashes.has(row.sourceFileSha256) || chunkHashes.has(row.sourceFileSha256)));
    const duplicateBySemantic = existingSemantics.has(row.semanticKey) || chunkSemantics.has(row.semanticKey);

    if (row.sourceFileSha256) chunkHashes.add(row.sourceFileSha256);
    chunkSemantics.add(row.semanticKey);

    if (!duplicateByHash && !duplicateBySemantic) return row;
    return {
      ...row,
      validationStatus: "duplicate" as const,
      validationErrors: [...row.validationErrors, duplicateByHash ? "arquivo_duplicado" : "oferta_semanticamente_duplicada"],
    };
  });

  const payload = finalRows.map((row: ValidatedImportRow) => ({
    batch_id: id,
    row_index: row.rowIndex,
    source_file_name: row.sourceFileName,
    source_file_sha256: row.sourceFileSha256,
    semantic_key: row.semanticKey,
    validation_status: row.validationStatus,
    validation_errors: row.validationErrors,
    original_payload: row.originalPayload,
    normalized_payload: row.normalizedPayload,
  }));

  const { error } = await adminSupabase()
    .from("historical_import_rows")
    .upsert(payload, { onConflict: "batch_id,row_index" });
  if (error) return Response.json({ error: error.message }, { status: 500 });

  const counts = { valid: 0, partial: 0, invalid: 0, duplicate: 0 };
  finalRows.forEach((row: ValidatedImportRow) => { counts[row.validationStatus] += 1; });
  return Response.json({ ok: true, received: finalRows.length, replayed: rows.length - freshIncoming.length, counts });
}
