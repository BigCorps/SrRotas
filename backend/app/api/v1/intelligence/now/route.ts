import { authenticateBillingActor } from "@/src/billing-auth";
import { applyCollectiveFallback025 } from "@/src/collective-fallback-025";
import { nowIntelligence } from "@/src/now-intelligence";
export const runtime="nodejs";
export const dynamic="force-dynamic";
export async function GET(request:Request){
  const ctx=await authenticateBillingActor(request); if(!ctx)return Response.json({error:"unauthorized"},{status:401});
  const u=new URL(request.url);
  const input={
    mode:(u.searchParams.get("mode")||"now") as any,
    source:(u.searchParams.get("source")||"personal") as any,
    at:u.searchParams.get("at")||undefined,
    weekday:u.searchParams.get("weekday")?Number(u.searchParams.get("weekday")):undefined,
    hour:u.searchParams.get("hour")?Number(u.searchParams.get("hour")):undefined,
    profile:u.searchParams.get("profile"), region:u.searchParams.get("region"),
  };
  try{
    const exact=await nowIntelligence(ctx.driverId,input);
    return Response.json(await applyCollectiveFallback025(input,exact));
  }catch(error){return Response.json({error:error instanceof Error?error.message:"now_intelligence_failed"},{status:500});}
}
