import { authenticateBillingWeb } from "@/src/billing-auth";
import { createSubscriptionCharge } from "@/src/billing";
export const runtime="nodejs";
export async function POST(request:Request){const actor=await authenticateBillingWeb(request);if(!actor)return Response.json({error:"unauthorized"},{status:401});try{return Response.json(await createSubscriptionCharge(actor.driverId),{status:201})}catch(error){const message=error instanceof Error?error.message:"checkout_failed";return Response.json({error:message},{status:message==="banco_inter_not_configured"?503:500})}}
