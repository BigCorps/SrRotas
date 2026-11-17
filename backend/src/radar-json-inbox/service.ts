import { createHash } from "node:crypto";
import { adminSupabase } from "../supabase";
import {
  requireRadarAdminV1,
  radarAdminRateLimitV1,
  adminJsonV1,
  validUuidV1,
} from "../radar-admin-http-v1";
import {
  ImportError,
  parseStrict,
  MAX_BYTES,
} from "../radar-admin-import/validation";
import type { UploadReceipt } from "./types";
const headers = {
  "Cache-Control": "private, no-store",
  "X-Content-Type-Options": "nosniff",
  "Cross-Origin-Resource-Policy": "same-origin",
};
export function sanitizeUploadName(value: string) {
  let name = value
    .normalize("NFC")
    .split(/[\\/]/)
    .at(-1)!
    .replace(/[\u0000-\u001f\u007f\u202a-\u202e\u2066-\u2069]/g, "")
    .trim();
  if (!name || !/\.json$/i.test(name))
    throw new ImportError("JSON_FILENAME_REQUIRED");
  if (Array.from(name).length > 120)
    name = Array.from(name.slice(0, -5)).slice(0, 115).join("") + ".json";
  return name;
}
export async function readUpload(request: Request) {
  if (
    request.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !==
      "application/json" ||
    request.headers.has("content-encoding")
  )
    throw new ImportError("JSON_REQUIRED", 415);
  const claimed = request.headers.get("content-length");
  if (
    claimed !== null &&
    (!/^\d+$/.test(claimed) || Number(claimed) > MAX_BYTES)
  )
    throw new ImportError("PAYLOAD_TOO_LARGE", 413);
  const reader = request.body?.getReader();
  if (!reader) throw new ImportError("INVALID_JSON");
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_BYTES) {
        await reader.cancel();
        throw new ImportError("PAYLOAD_TOO_LARGE", 413);
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  if (claimed !== null && Number(claimed) !== size)
    throw new ImportError("INCOMPLETE_UPLOAD");
  const bytes = Buffer.concat(chunks);
  parseStrict(bytes);
  return bytes;
}
async function auth(request: Request, scope: string, max: number) {
  const checked = await requireRadarAdminV1(request);
  if (checked.response) return checked;
  // This private web inbox uses the actual Admin session, never a collector or device realm.
  if (checked.actor!.source !== "admin")
    return {
      actor: null,
      response: adminJsonV1({ error: "admin_session_required" }, 403),
    };
  const limited = await radarAdminRateLimitV1(
    request,
    scope,
    checked.actor!.email,
    max,
  );
  return limited ? { actor: null, response: limited } : checked;
}
export function adminInboxIdentity(
  request: Request,
  actor: { authUserId: string; email: string },
) {
  let token = "";
  for (const part of (request.headers.get("cookie") ?? "").split(";")) {
    const [key, ...value] = part.trim().split("=");
    if (key === "sr_import_admin") token = decodeURIComponent(value.join("="));
  }
  return {
    p_actor_id: actor.authUserId,
    p_email: actor.email,
    p_session_hash: createHash("sha256").update(token).digest("hex"),
  };
}
async function rpc(name: string, args: Record<string, unknown>) {
  const { data, error } = await adminSupabase().rpc(name, args);
  if (error)
    throw new ImportError(
      /^PT\d{3}$/.test(error.code)
        ? String(error.message)
        : "INBOX_UNAVAILABLE",
      /^PT\d{3}$/.test(error.code) ? Number(error.code.slice(2)) : 503,
    );
  return data;
}
function failed(error: unknown) {
  const allowed = new Set([
    "JSON_FILENAME_REQUIRED",
    "JSON_REQUIRED",
    "PAYLOAD_TOO_LARGE",
    "INCOMPLETE_UPLOAD",
    "INVALID_JSON",
    "INVALID_UTF8",
    "MAX_DEPTH",
    "DUPLICATE_JSON_KEY",
    "idempotency_conflict",
    "upload_quota_reached",
    "session_expired",
    "admin_required",
    "upload_not_found",
    "upload_purged",
    "integrity_mismatch",
  ]);
  return adminJsonV1(
    {
      error: {
        code:
          error instanceof ImportError && allowed.has(error.code)
            ? error.code
            : "INBOX_UNAVAILABLE",
        message:
          "Não foi possível concluir. Confira Envios antes de repetir; nenhum evento ou local foi importado.",
      },
    },
    error instanceof ImportError ? error.status : 503,
  );
}
export async function upload(request: Request) {
  try {
    const checked = await auth(request, "radar-json-upload", 20);
    if (checked.response) return checked.response;
    const key = request.headers.get("idempotency-key");
    if (!validUuidV1(key)) throw new ImportError("INVALID_JSON");
    const name = sanitizeUploadName(
      decodeURIComponent(request.headers.get("x-file-name") ?? ""),
    );
    const bytes = await readUpload(request);
    const receipt = await rpc("sr_radar_json_upload_v1", {
      ...adminInboxIdentity(request, checked.actor!),
      p_key: key,
      p_name: name,
      p_base64: bytes.toString("base64"),
    });
    return Response.json({ receipt }, { headers });
  } catch (error) {
    return failed(error);
  }
}
export async function list(request: Request) {
  try {
    const checked = await auth(request, "radar-json-list", 120);
    if (checked.response) return checked.response;
    const cursor = new URL(request.url).searchParams.get("cursor");
    if (cursor && !validUuidV1(cursor)) throw new ImportError("INVALID_JSON");
    const uploadId = new URL(request.url).searchParams.get("upload_id");
    if (uploadId && !validUuidV1(uploadId))
      throw new ImportError("INVALID_JSON");
    const rows: UploadReceipt[] = await rpc("sr_radar_json_list_v1", {
      ...adminInboxIdentity(request, checked.actor!),
      p_cursor: cursor,
      p_upload_id: uploadId,
    });
    const uploads = rows.slice(0, 20);
    return Response.json(
      {
        uploads,
        next_cursor: rows.length > 20 ? uploads.at(-1)!.upload_id : null,
      },
      { headers },
    );
  } catch (error) {
    return failed(error);
  }
}
export async function download(request: Request, id: string) {
  try {
    const checked = await auth(request, "radar-json-download", 30);
    if (checked.response) return checked.response;
    if (!validUuidV1(id)) throw new ImportError("INVALID_JSON");
    const result = await rpc("sr_radar_json_download_v1", {
      ...adminInboxIdentity(request, checked.actor!),
      p_upload_id: id,
    });
    const bytes = Buffer.from(result.base64, "base64");
    const receipt: UploadReceipt = result.receipt;
    if (
      bytes.length !== receipt.size_bytes ||
      createHash("sha256").update(bytes).digest("hex") !== receipt.sha256
    )
      throw new ImportError("integrity_mismatch", 409);
    return new Response(bytes, {
      headers: {
        ...headers,
        "Content-Type": "application/json; charset=utf-8",
        "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(sanitizeUploadName(receipt.original_name))}`,
        "Content-Security-Policy": "sandbox; default-src 'none'",
      },
    });
  } catch (error) {
    return failed(error);
  }
}
