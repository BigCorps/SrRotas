import { accessDeniedResponse } from "@/src/access";
import { authenticateBillingActor } from "@/src/billing-auth";
import { searchRadarPoisV1 } from "@/src/radar-poi-resolver-v1";
export const runtime="nodejs"; export const dynamic="force-dynamic";
export async function GET(request:Request){
 const auth=await authenticateBillingActor(request); if(!auth)return Response.json({error:"unauthorized"},{status:401});
 const denied=accessDeniedResponse(auth.access,"can_analytics"); if(denied)return denied;
 const u=new URL(request.url),q=(u.searchParams.get("q")||"").trim();
 if(q.length<2)return Response.json({pois:[]});
 try{return Response.json({pois:await searchRadarPoisV1(q,20)});}
 catch(e){return Response.json({error:e instanceof Error?e.message:"poi_search_failed"},{status:500});}
}
