import { authenticateBillingActor } from "@/src/billing-auth";
import { billingStatus } from "@/src/billing";
export const runtime="nodejs";export const dynamic="force-dynamic";
export async function GET(request:Request){const actor=await authenticateBillingActor(request);if(!actor)return Response.json({error:"unauthorized"},{status:401});try{return Response.json(await billingStatus(actor.driverId,true))}catch(error){return Response.json({error:error instanceof Error?error.message:"billing_status_failed"},{status:500})}}
