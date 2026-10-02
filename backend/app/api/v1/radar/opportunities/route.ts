import { accessDeniedResponse } from '@/src/access';
import { authenticateBillingActor } from '@/src/billing-auth';
import { destinationRadarOpportunitiesV1 } from '@/src/radar-opportunities-service-v1';
export const runtime='nodejs'; export const dynamic='force-dynamic';
export async function GET(request:Request){
 const auth=await authenticateBillingActor(request); if(!auth)return Response.json({error:'unauthorized'},{status:401});
 const denied=accessDeniedResponse(auth.access,'can_analytics'); if(denied)return denied;
 const u=new URL(request.url),lat=Number(u.searchParams.get('lat')),lng=Number(u.searchParams.get('lng')),eta=u.searchParams.get('eta')||'';
 if(!Number.isFinite(lat)||!Number.isFinite(lng)||!eta)return Response.json({error:'destination_and_eta_required'},{status:400});
 try{return Response.json(await destinationRadarOpportunitiesV1({driverId:auth.driverId,lat,lng,eta,radiusKm:Number(u.searchParams.get('radius_km')||5),limit:Number(u.searchParams.get('limit')||6),destinationLabel:u.searchParams.get('destination_label'),serviceProfile:u.searchParams.get('profile')}));}
 catch(e){const m=e instanceof Error?e.message:'radar_opportunities_failed';return Response.json({error:m},{status:m.startsWith('invalid_')?400:500});}
}
