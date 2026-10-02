import { accessDeniedResponse } from "@/src/access";
import { authenticateBillingActor } from "@/src/billing-auth";
import { nearbyPoiOpportunitiesV1 } from "@/src/radar-poi-v1";
export const runtime="nodejs"; export const dynamic="force-dynamic";
export async function GET(request:Request){
 const auth=await authenticateBillingActor(request); if(!auth)return Response.json({error:"unauthorized"},{status:401});
 const denied=accessDeniedResponse(auth.access,"can_analytics"); if(denied)return denied;
 const u=new URL(request.url),lat=Number(u.searchParams.get("lat")),lng=Number(u.searchParams.get("lng"));
 const eta=(u.searchParams.get("eta")||"").trim(),radius=Math.max(.5,Math.min(Number(u.searchParams.get("radius_km")||3)||3,15));
 if(!Number.isFinite(lat)||!Number.isFinite(lng)||!eta)return Response.json({error:"lat_lng_eta_required"},{status:400});
 try {
   const opportunities=await nearbyPoiOpportunitiesV1({driverId:auth.driverId,lat,lng,eta,radiusKm:radius,limit:8});
   return Response.json({schema_version:"srrotas-radar-poi-v1",destination:{lat,lng,eta},opportunities,
    note:"Probabilidade vem de exposição observada na célula; contexto externo apenas explica temporalidade e não cria demanda."});
 } catch(e){const m=e instanceof Error?e.message:"poi_opportunities_failed";return Response.json({error:m},{status:m.startsWith("invalid_")?400:500});}
}
