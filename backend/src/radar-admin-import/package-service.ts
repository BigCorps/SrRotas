import {adminSupabase} from "../supabase";
import {requireRadarAdminV1,radarAdminRateLimitV1,adminJsonV1,validUuidV1} from "../radar-admin-http-v1";
import {adminInboxIdentity} from "../radar-json-inbox/service";
import {ImportError,MAX_BYTES} from "./validation";
import {inspectRadarPackageV2} from "./package-v2";
import {previewExternalV2} from "./external-v2-preview";
import {deployedReadSnapshot} from "./preview-service";
export const PACKAGE_MEDIA="application/octet-stream";
function failure(error:unknown) {
  const e=error instanceof ImportError?error:new ImportError("PACKAGE_IMPORT_UNAVAILABLE",503);
  const allowed=new Set([
    "PAYLOAD_TOO_LARGE","INVALID_ZIP","ZIP_LIMIT_OR_CENTRAL_DIRECTORY",
    "UNSAFE_ZIP_ENTRY","ZIP_EXPANSION_LIMIT","INVALID_ZIP_COMPRESSION",
    "ZIP_CHECKSUM_MISMATCH","ZIP_DIRECTORY_MISMATCH","UNSUPPORTED_ZIP_COMPRESSION",
    "INVALID_GEOJSON","DUPLICATE_GEOJSON_ID","INVALID_POI_V2_ENVELOPE",
    "INVALID_POI_RECORD","INVALID_POI_CLIENT_REF","NO_POI_JSON_IN_PACKAGE",
    "TOO_MANY_REGIONS","PACKAGE_ITEM_LIMIT","PACKAGE_NORMALIZED_TOO_LARGE",
    "PACKAGE_EXTENSION_REQUIRED","INVALID_JSON","DUPLICATE_JSON_KEY","INVALID_UTF8",
    "PACKAGE_INVALID_NAME","INVALID_IDEMPOTENCY_KEY"
  ]);
  return adminJsonV1({error:{code:allowed.has(e.code)?e.code:"PACKAGE_IMPORT_UNAVAILABLE",
    message:"O pacote não foi aceito. Nenhum local foi alterado."}},e.status);
}
async function readBounded(request:Request){
  if(request.headers.get("content-type")?.split(";")[0].trim().toLowerCase()!==PACKAGE_MEDIA||
     request.headers.has("content-encoding"))throw new ImportError("PACKAGE_MEDIA_REQUIRED",415);
  const declared=request.headers.get("content-length");
  if(declared!==null&&(!/^\d+$/.test(declared)||Number(declared)>MAX_BYTES))
    throw new ImportError("PAYLOAD_TOO_LARGE",413);
  let total=0;
  const chunks:Uint8Array[]=[];
  const reader=request.body?.getReader();
  if(!reader)throw new ImportError("INVALID_PACKAGE");
  try{
    for(;;){
      const {done,value}=await reader.read();if(done)break;
      total+=value.byteLength;
      if(total>MAX_BYTES){await reader.cancel();throw new ImportError("PAYLOAD_TOO_LARGE",413);}
      chunks.push(value);
    }
  }finally{reader.releaseLock();}
  if(declared!==null&&Number(declared)!==total)throw new ImportError("INCOMPLETE_PACKAGE");
  const bytes=new Uint8Array(total);let offset=0;
  for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.byteLength;}
  return bytes;
}
/** Receives ZIP/JSON, normalizes one private JSON v2 receipt, shows the full
 * classification in the same response. Business POIs are NOT written here.
 */
export async function POST(request:Request){
  const checked=await requireRadarAdminV1(request);
  if(checked.response)return checked.response;
  if(checked.actor?.source!=="admin")
    return adminJsonV1({error:{code:"ADMIN_SESSION_REQUIRED"}},403);
  const rate=await radarAdminRateLimitV1(request,"radar-package-v2",checked.actor.email,20,900);
  if(rate)return rate;
  try{
    const key=request.headers.get("idempotency-key");
    if(!validUuidV1(key))throw new ImportError("INVALID_IDEMPOTENCY_KEY",422);
    let filename="";
    try{filename=decodeURIComponent(request.headers.get("x-file-name")??"");}
    catch{throw new ImportError("PACKAGE_INVALID_NAME");}
    if(!filename||filename.length>120||/[/\\\u0000-\u001f\u007f]/.test(filename)||
       !/\.(zip|json)$/i.test(filename))
      throw new ImportError("PACKAGE_INVALID_NAME");
    const bytes=await readBounded(request);
    const parsed=inspectRadarPackageV2(bytes,filename);
    const material=Buffer.from(parsed.canonical).toString("base64");
    const storageName=filename.replace(/\.(zip|json)$/i,"").slice(0,100)+".consolidado.v2.json";
    const [db,snapshot]=await Promise.all([
      adminSupabase().rpc("sr_radar_json_upload_v1",{
        ...adminInboxIdentity(request,checked.actor!),
        p_key:key,p_name:storageName,p_base64:material,
      }),
      deployedReadSnapshot(),
    ]);
    if(db.error){
      const status=/^PT\d{3}$/.test(db.error.code)?Number(db.error.code.slice(2)):503;
      throw new ImportError(status===409?"IDEMPOTENCY_CONFLICT":"INBOX_UNAVAILABLE",status);
    }
    const preview=previewExternalV2(parsed.envelope,snapshot);
    if(snapshot.complete && process.env.RADAR_MANUAL_COMMIT_ENABLED==="true"){
      preview.commit_enabled=true;preview.block_code="";
    }
    return adminJsonV1({
      receipt:db.data,preview,manifest:parsed.manifest,
      note:"Arquivo ZIP convertido em JSON consolidado e armazenado em Envios; ZIP original não é retido."
    },201);
  }catch(error){return failure(error);}
}
