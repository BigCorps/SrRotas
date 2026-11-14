import { previewHandler } from "@/src/radar-admin-import/preview-service";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
/** Read-only business preview. No deployed commit route or registry/migration. */
export const POST = previewHandler();
