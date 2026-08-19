import { clearImportCookieHeader, deleteImportWebSession } from "@/src/admin-import-auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  await deleteImportWebSession(request).catch(() => undefined);
  return Response.json(
    { ok: true },
    { headers: { "Set-Cookie": clearImportCookieHeader() } },
  );
}
