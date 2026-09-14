import { HISTORICAL_SCHEMA_V1, IMPORT_CHUNK_LIMIT, requireImportAccess, validateHistoricalImportRow, type ValidatedImportRow } from "@/src/admin-imports";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type IncomingRow = { row_index?: unknown; payload?: unknown };
type StoredKeyRow = {
  batch_id: string;
  source_file_sha256: string | null;
  offer_index: number | null;
  record_id: string | null;
  semantic_key: string | null;
};
type DedupBatch = { id: string; status: string; valid_count: number; partial_count: number };
type UsableBatch = { id: string; status: string; supersedes_batch_id: string | null };
type DedupSets = {
  recordIds: Set<string>;
  semanticKeys: Set<string>;
  sourceOfferKeys: Set<string>;
};

function sourceOfferKey(hash: string | null, offerIndex: number | null) {
  return hash && offerIndex !== null ? `${hash}:${offerIndex}` : null;
}

async function addEligibleStoredKeys(rows: StoredKeyRow[], sets: DedupSets, ignoredBatchIds: Set<string>) {
  const batchIds = Array.from(new Set(rows.map((row) => row.batch_id).filter(Boolean)));
  if (!batchIds.length) return null;
  const batches = await adminSupabase()
    .from("historical_import_batches")
    .select("id,status,valid_count,partial_count")
    .in("id", batchIds)
    .limit(5000);
  if (batches.error) return batches.error.message;

  const eligibleIds = new Set(
    ((batches.data ?? []) as DedupBatch[])
      .filter((batch) => !ignoredBatchIds.has(batch.id) && batch.status !== "archived" && (Number(batch.valid_count) > 0 || Number(batch.partial_count) > 0))
      .map((batch) => batch.id),
  );
  rows.filter((row) => eligibleIds.has(row.batch_id)).forEach((row) => {
    if (row.record_id) sets.recordIds.add(row.record_id);
    if (row.semantic_key) sets.semanticKeys.add(row.semantic_key);
    const pair = sourceOfferKey(row.source_file_sha256, row.offer_index);
    if (pair) sets.sourceOfferKeys.add(pair);
  });
  return null;
}

async function canUseBatch(batchId: string, authUserId: string, email: string, isOwner: boolean) {
  const { data, error } = await adminSupabase()
    .from("historical_import_batches")
    .select("id,created_by_auth_user_id,created_by_email,status,supersedes_batch_id")
    .eq("id", batchId)
    .maybeSingle();
  if (error || !data) return null;
  if (!isOwner) {
    const sameAuthUser = String(data.created_by_auth_user_id || "") === authUserId;
    const legacySameEmail = !data.created_by_auth_user_id && String(data.created_by_email || "").toLowerCase() === email;
    if (!sameAuthUser && !legacySameEmail) return null;
  }
  return data as UsableBatch;
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
  const rows = Array.isArray(body?.rows) ? rowsOrEmpty(body.rows) : [];
  if (!rows.length || rows.length > IMPORT_CHUNK_LIMIT) {
    return Response.json({ error: "invalid_chunk", max_rows: IMPORT_CHUNK_LIMIT }, { status: 400 });
  }

  const normalizedIncoming = rows.map((item, index) => {
    const candidate = Number(item?.row_index);
    return { rowIndex: Number.isInteger(candidate) && candidate >= 0 ? candidate : index, payload: item?.payload };
  });

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
    return Response.json({
      ok: true, received: 0, replayed: rows.length,
      counts: { valid: 0, partial: 0, invalid: 0, duplicate: 0 },
      quality_counts: { demand_temporal_ready: 0, route_flow_ready: 0, financial_ready: 0, fully_ready: 0 },
    });
  }

  const validated = freshIncoming.map((item) => validateHistoricalImportRow(item.rowIndex, item.payload));
  // Um lote V7.1 de substituição precisa poder coexistir com o lote antigo para auditoria.
  // A deduplicação ignora SOMENTE o lote explicitamente indicado como superseded;
  // todos os demais lotes ativos continuam protegendo contra duplicidade.
  const ignoredBatchIds = new Set<string>([batch.supersedes_batch_id].filter((value): value is string => Boolean(value)));
  const sets: DedupSets = { recordIds: new Set(), semanticKeys: new Set(), sourceOfferKeys: new Set() };
  const recordIds = Array.from(new Set(validated.map((row) => row.recordId).filter(Boolean)));
  const semantics = Array.from(new Set(validated.map((row) => row.semanticKey).filter(Boolean)));
  const hashes = Array.from(new Set(validated.map((row) => row.sourceFileSha256).filter((v): v is string => Boolean(v))));

  if (recordIds.length) {
    const found = await adminSupabase()
      .from("historical_import_rows")
      .select("batch_id,source_file_sha256,offer_index,record_id,semantic_key")
      .in("record_id", recordIds)
      .limit(5000);
    if (found.error) return Response.json({ error: found.error.message }, { status: 500 });
    const keyError = await addEligibleStoredKeys((found.data ?? []) as StoredKeyRow[], sets, ignoredBatchIds);
    if (keyError) return Response.json({ error: keyError }, { status: 500 });
  }

  if (semantics.length) {
    const found = await adminSupabase()
      .from("historical_import_rows")
      .select("batch_id,source_file_sha256,offer_index,record_id,semantic_key")
      .in("semantic_key", semantics)
      .limit(5000);
    if (found.error) return Response.json({ error: found.error.message }, { status: 500 });
    const keyError = await addEligibleStoredKeys((found.data ?? []) as StoredKeyRow[], sets, ignoredBatchIds);
    if (keyError) return Response.json({ error: keyError }, { status: 500 });
  }

  // Hash da imagem sozinho NÃO é duplicidade: uma imagem pode conter várias ofertas.
  // Consultamos o hash apenas para comparar a chave composta hash+offer_index.
  if (hashes.length) {
    const found = await adminSupabase()
      .from("historical_import_rows")
      .select("batch_id,source_file_sha256,offer_index,record_id,semantic_key")
      .in("source_file_sha256", hashes)
      .not("offer_index", "is", null)
      .limit(5000);
    if (found.error) return Response.json({ error: found.error.message }, { status: 500 });
    const keyError = await addEligibleStoredKeys((found.data ?? []) as StoredKeyRow[], sets, ignoredBatchIds);
    if (keyError) return Response.json({ error: keyError }, { status: 500 });
  }

  const chunkRecordIds = new Set<string>();
  const chunkSemantics = new Set<string>();
  const chunkPairs = new Set<string>();
  const finalRows = validated.map((row: ValidatedImportRow) => {
    const pair = sourceOfferKey(row.sourceFileSha256, row.offerIndex);
    const duplicateByRecordId = sets.recordIds.has(row.recordId) || chunkRecordIds.has(row.recordId);
    const hasStrongOfferIdentity = row.schemaVersion === HISTORICAL_SCHEMA_V1 && Boolean(pair);
    const duplicateBySemantic = !hasStrongOfferIdentity && (sets.semanticKeys.has(row.semanticKey) || chunkSemantics.has(row.semanticKey));
    const duplicateByPair = Boolean(pair && (sets.sourceOfferKeys.has(pair) || chunkPairs.has(pair)));

    chunkRecordIds.add(row.recordId);
    chunkSemantics.add(row.semanticKey);
    if (pair) chunkPairs.add(pair);

    if (!duplicateByRecordId && !duplicateBySemantic && !duplicateByPair) return row;
    const reason = duplicateByRecordId ? "record_id_duplicado" : duplicateByPair ? "imagem_offer_index_duplicado" : "oferta_semanticamente_duplicada";
    return {
      ...row,
      validationStatus: "duplicate" as const,
      validationErrors: Array.from(new Set([...row.validationErrors, reason])),
    };
  });

  const payload = finalRows.map((row: ValidatedImportRow) => ({
    batch_id: id,
    row_index: row.rowIndex,
    source_file_name: row.sourceFileName,
    source_file_sha256: row.sourceFileSha256,
    schema_version: row.schemaVersion,
    extractor_version: row.extractorVersion,
    record_id: row.recordId,
    offer_index: row.offerIndex,
    semantic_key: row.semanticKey,
    validation_status: row.validationStatus,
    validation_errors: row.validationErrors,
    quality_flags: row.qualityFlags,
    quality_demand_temporal_ready: row.quality.demand_temporal_ready,
    quality_route_flow_ready: row.quality.route_flow_ready,
    quality_financial_ready: row.quality.financial_ready,
    quality_fully_ready: row.quality.fully_ready,
    original_payload: row.originalPayload,
    normalized_payload: row.normalizedPayload,
  }));

  const { error } = await adminSupabase()
    .from("historical_import_rows")
    .upsert(payload, { onConflict: "batch_id,row_index" });
  if (error) return Response.json({ error: error.message }, { status: 500 });

  const counts = { valid: 0, partial: 0, invalid: 0, duplicate: 0 };
  const qualityCounts = { demand_temporal_ready: 0, route_flow_ready: 0, financial_ready: 0, fully_ready: 0 };
  finalRows.forEach((row: ValidatedImportRow) => {
    counts[row.validationStatus] += 1;
    if (row.validationStatus === "duplicate" || row.validationStatus === "invalid") return;
    if (row.quality.demand_temporal_ready) qualityCounts.demand_temporal_ready += 1;
    if (row.quality.route_flow_ready) qualityCounts.route_flow_ready += 1;
    if (row.quality.financial_ready) qualityCounts.financial_ready += 1;
    if (row.quality.fully_ready) qualityCounts.fully_ready += 1;
  });
  return Response.json({ ok: true, received: finalRows.length, replayed: rows.length - freshIncoming.length, counts, quality_counts: qualityCounts });
}

function rowsOrEmpty(value: unknown): IncomingRow[] {
  return Array.isArray(value) ? value as IncomingRow[] : [];
}
