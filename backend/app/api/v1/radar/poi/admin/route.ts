import { serverEnv } from "@/src/env";
import { ingestRadarPoisAdminV1, listRadarPoiAdminV1, reviewRadarPoiAdminV1 } from "@/src/radar-poi-admin-v1";

export const runtime="nodejs";
export const dynamic="force-dynamic";

// Server-only admin secret, same protection as existing Radar event ingest.
// NEVER expose this endpoint credential to Android, the browser or a client-side crawler UI.
function authorized(request:Request):boolean {
  const secret=serverEnv().radarIngestSecret;
  return Boolean(secret)&&request.headers.get("authorization")===`Bearer ${secret}`;
}
function unauthorized() {return Response.json({error:"unauthorized"},{status:401})}
export async function GET(request:Request) {
  if(!authorized(request))return unauthorized();
  try {
    const u=new URL(request.url);
    const status=u.searchParams.get("status")??undefined;
    return Response.json({pois:await listRadarPoiAdminV1(Number(u.searchParams.get("limit")||100),status)},
      {headers:{"Cache-Control":"no-store"}});
  }catch(e){return Response.json({error:e instanceof Error?e.message:"poi_admin_list_failed"},{status:500})}
}
export async function POST(request:Request) {
  if(!authorized(request))return unauthorized();
  try {
    const payload=await request.json();
    return Response.json(await ingestRadarPoisAdminV1(payload),{headers:{"Cache-Control":"no-store"}});
  }catch(e){return Response.json({error:e instanceof Error?e.message:"poi_admin_ingest_failed"},{status:400})}
}
export async function PATCH(request:Request) {
  if(!authorized(request))return unauthorized();
  try {
    const body=await request.json();
    const status=body?.status;
    if(status!=="active"&&status!=="inactive")return Response.json({error:"status_invalid"},{status:400});
    return Response.json({poi:await reviewRadarPoiAdminV1(String(body?.id??""),status)});
  }catch(e){return Response.json({error:e instanceof Error?e.message:"poi_admin_review_failed"},{status:400})}
}
