import { authenticateBillingActor } from "@/src/billing-auth";
import { nowIntelligence } from "@/src/now-intelligence";
export const runtime="nodejs";
export const dynamic="force-dynamic";
export async function GET(request:Request){
  const ctx=await authenticateBillingActor(request); if(!ctx)return Response.json({error:"unauthorized"},{status:401});
  const u=new URL(request.url);
  try{
    return Response.json(await nowIntelligence(ctx.driverId,{
      mode:(u.searchParams.get("mode")||"now") as any,
      source:(u.searchParams.get("source")||"personal") as any,
      at:u.searchParams.get("at")||undefined,
      weekday:u.searchParams.get("weekday")?Number(u.searchParams.get("weekday")):undefined,
      hour:u.searchParams.get("hour")?Number(u.searchParams.get("hour")):undefined,
      profile:u.searchParams.get("profile"), region:u.searchParams.get("region"),
    }));
  }catch(error){return Response.json({error:error instanceof Error?error.message:"now_intelligence_failed"},{status:500});}
}
