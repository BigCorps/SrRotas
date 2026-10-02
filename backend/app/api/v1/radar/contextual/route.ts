import { accessDeniedResponse } from "@/src/access";
import { authenticateBillingActor } from "@/src/billing-auth";
import { radarContextualV1 } from "@/src/radar-contextual-v1";

export const runtime="nodejs";
export const dynamic="force-dynamic";

export async function GET(request:Request) {
  const auth=await authenticateBillingActor(request);
  if(!auth) return Response.json({error:"unauthorized"},{status:401});
  const denied=accessDeniedResponse(auth.access,"can_analytics");
  if(denied) return denied;

  const u=new URL(request.url);
  const lat=Number(u.searchParams.get("lat"));
  const lng=Number(u.searchParams.get("lng"));
  const eta=(u.searchParams.get("eta")||"").trim();
  const label=(u.searchParams.get("label")||"").trim()||null;
  const radiusKm=Math.max(.5,Math.min(Number(u.searchParams.get("radius_km")||4)||4,15));
  const limit=Math.max(3,Math.min(Number(u.searchParams.get("limit")||6)||6,8));

  if(!Number.isFinite(lat)||lat < -90||lat > 90||!Number.isFinite(lng)||lng < -180||lng > 180)
    return Response.json({error:"invalid_location"},{status:400});
  if(!eta) return Response.json({error:"eta_required"},{status:400});

  try {
    return Response.json(await radarContextualV1({
      driverId:auth.driverId,
      destination:{lat,lng,eta,label},
      radiusKm,limit
    }));
  } catch(error) {
    const message=error instanceof Error?error.message:"radar_contextual_failed";
    return Response.json({error:message},{status:message.startsWith("invalid_")?400:500});
  }
}
