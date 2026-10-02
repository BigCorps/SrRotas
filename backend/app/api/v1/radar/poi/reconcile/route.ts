import { serverEnv } from "@/src/env";
import { reconcileUnlinkedRadarEventsV1 } from "@/src/radar-poi-resolver-v1";

export const runtime="nodejs";
export const dynamic="force-dynamic";

function authorized(request:Request){
  const secret=serverEnv().radarIngestSecret;
  return Boolean(secret) && request.headers.get("authorization")===`Bearer ${secret}`;
}

export async function POST(request:Request){
  if(!serverEnv().radarIngestSecret) return Response.json({error:"radar_ingest_secret_missing"},{status:503});
  if(!authorized(request)) return Response.json({error:"unauthorized"},{status:401});
  try {
    const body=await request.json().catch(()=>({}));
    const limit=Math.max(1,Math.min(Number(body?.limit||200)||200,500));
    return Response.json(await reconcileUnlinkedRadarEventsV1(limit));
  } catch(error) {
    return Response.json({error:error instanceof Error?error.message:"poi_reconcile_failed"},{status:400});
  }
}
