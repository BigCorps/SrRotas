import {readManualPoiReceipts} from "@/src/radar-admin-import/commit-service";
export const runtime="nodejs";
export const dynamic="force-dynamic";
export async function GET(request:Request){return readManualPoiReceipts(request);}
