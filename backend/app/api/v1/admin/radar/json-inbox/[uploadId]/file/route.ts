import { download } from "@/src/radar-json-inbox/service";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export async function GET(
  request: Request,
  context: { params: Promise<{ uploadId: string }> },
) {
  return download(request, (await context.params).uploadId);
}
