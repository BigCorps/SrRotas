import { clearBillingCookieHeader,deleteBillingWebSession } from "@/src/billing-auth";
export const runtime="nodejs";
export async function POST(request:Request){await deleteBillingWebSession(request);return Response.json({ok:true},{headers:{"Set-Cookie":clearBillingCookieHeader()}})}
