import { adminSupabase } from "../supabase";
import { adminInboxIdentity } from "../radar-json-inbox/service";
import {
  requireRadarAdminV1,radarAdminRateLimitV1,adminJsonV1,validUuidV1,
} from "../radar-admin-http-v1";

const noStore = { "Cache-Control": "no-store" };
const fail=(code:string,status:number)=>
  adminJsonV1({error:{code,message:"Confirmação não concluída. Confira Envios antes de repetir com a mesma chave."}},status);
async function admin(request: Request, scope:string,max:number) {
  const checked=await requireRadarAdminV1(request);
  if(checked.response)return checked;
  if(checked.actor?.source!=="admin")
    return {actor:null,response:fail("ADMIN_SESSION_REQUIRED",403)};
  const rate=await radarAdminRateLimitV1(request,scope,checked.actor.email,max);
  return rate?{actor:null,response:rate}:checked;
}
function rpcError(e:{code?:string;message?:string}){
  if(/^PT(400|403|404|409|410|413|422|429)$/.test(e.code??""))
    return fail(e.message?.slice(0,100) || "INVALID_REQUEST",Number(e.code?.slice(2)));
  // Do not leak SQL, field values, payloads or session/cookie data.
  return fail("COMMIT_STORE_UNAVAILABLE",503);
}
async function limitedJson(request:Request){
  if(request.headers.get("content-type")?.split(";")[0].trim()!=="application/json" ||
     request.headers.has("content-encoding"))
    throw {code:"PT415",message:"JSON_REQUIRED"};
  const reader=request.body?.getReader();
  if(!reader)throw {code:"PT400",message:"INVALID_JSON"};
  let size=0;const chunks:Uint8Array[]=[];
  try {
    while(true){
      const {done,value}=await reader.read();
      if(done)break;
      size+=value.byteLength;
      if(size>70_000){await reader.cancel();throw {code:"PT413",message:"PAYLOAD_TOO_LARGE"};}
      chunks.push(value);
    }
  }finally{reader.releaseLock();}
  const raw=new Uint8Array(size);
  let offset=0;for(const part of chunks){raw.set(part,offset);offset+=part.length;}
  try{
    return JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(raw));
  }catch{throw {code:"PT400",message:"INVALID_JSON"};}
}
export async function commitManualPoiBatch(request: Request) {
  const access=await admin(request,"radar-manual-poi-commit",10);
  if(access.response)return access.response;
  if(process.env.RADAR_MANUAL_COMMIT_ENABLED!=="true")
    return fail("IMPORT_COMMIT_NOT_ENABLED",503);
  const key=request.headers.get("Idempotency-Key");
  if(!validUuidV1(key))return fail("INVALID_IDEMPOTENCY_KEY",422);
  try{
    const body=await limitedJson(request);
    if(!body || typeof body!=="object" || Array.isArray(body) ||
       Object.keys(body).some(k=>!["upload_id","selected_item_ids","decisions"].includes(k)) ||
       !validUuidV1(body.upload_id) ||
       !Array.isArray(body.selected_item_ids) ||
       body.selected_item_ids.length<1 || body.selected_item_ids.length>500 ||
       body.selected_item_ids.some((x:unknown)=>typeof x!=="string" ||
          !/^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$/.test(x)) ||
       body.decisions===null || typeof body.decisions!=="object" ||
       Array.isArray(body.decisions))
      return fail("INVALID_SELECTION",422);
    const {data,error}=await adminSupabase().rpc("sr_radar_manual_poi_commit_v1",{
      ...adminInboxIdentity(request,access.actor!),
      p_upload_id:body.upload_id,
      p_key:key,p_selected:body.selected_item_ids,
      p_decisions:body.decisions??{},
    });
    if(error)return rpcError(error);
    return Response.json({receipt:data},{status:data?.idempotent_replay?200:201,headers:noStore});
  }catch(error){
    return rpcError(error as {code?:string;message?:string});
  }
}
export async function readManualPoiReceipts(request: Request, batchId?:string){
  const access=await admin(request,"radar-manual-poi-receipts",60);
  if(access.response)return access.response;
  if(batchId && !validUuidV1(batchId))return fail("INVALID_BATCH_ID",422);
  if(process.env.RADAR_MANUAL_COMMIT_ENABLED!=="true")
    return fail("IMPORT_COMMIT_NOT_ENABLED",503);
  const {data,error}=await adminSupabase().rpc("sr_radar_manual_receipts_v1",{
    ...adminInboxIdentity(request,access.actor!),
    p_batch_id:batchId??null,
  });
  if(error)return rpcError(error);
  if(batchId && (!Array.isArray(data)||data.length===0))return fail("BATCH_NOT_FOUND",404);
  return Response.json(batchId?data[0]:{batches:data??[]},{headers:noStore});
}
