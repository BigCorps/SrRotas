import { accessDeniedResponse } from "@/src/access";
import { authenticateBillingActor } from "@/src/billing-auth";
import { saveRadarContextualEventV1 } from "@/src/radar-contextual-events-v1";
export const runtime="nodejs"; export const dynamic="force-dynamic";
export async function POST(request:Request){
 const auth=await authenticateBillingActor(request); if(!auth)return Response.json({error:"unauthorized"},{status:401});
 const denied=accessDeniedResponse(auth.access,"can_analytics"); if(denied)return denied;
 try{return Response.json({ok:true,event:await saveRadarContextualEventV1(auth.driverId,auth.deviceId??null,await request.json())});}
 catch(e){return Response.json({error:e instanceof Error?e.message:"radar_event_failed"},{status:400});}
}
