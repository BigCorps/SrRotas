import {readManualPoiReceipts} from "@/src/radar-admin-import/commit-service";
export const runtime="nodejs";
export const dynamic="force-dynamic";
export async function GET(request:Request,context:{params:Promise<{batchId:string}>}){
 const {batchId}=await context.params;
 return readManualPoiReceipts(request,batchId);
}
