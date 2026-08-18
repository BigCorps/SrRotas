import { adminSupabase } from "./supabase";
import { newToken, sha256 } from "./security";
import { authenticateDevice } from "./device-auth";
export const BILLING_COOKIE="sr_billing";
export async function createBillingWebSession(driverId:string){const token=`srweb_${newToken()}`;const expiresAt=new Date(Date.now()+30*60*1000).toISOString();const {error}=await adminSupabase().from("billing_web_sessions").insert({driver_id:driverId,token_hash:sha256(token),expires_at:expiresAt});if(error)throw new Error(error.message);return{token,expiresAt}}
export function billingCookieHeader(token:string,maxAgeSeconds=1800){return `${BILLING_COOKIE}=${encodeURIComponent(token)}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=${maxAgeSeconds}`}
export function clearBillingCookieHeader(){return `${BILLING_COOKIE}=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0`}
function cookieValue(request:Request,name:string){const raw=request.headers.get("cookie")||"";for(const part of raw.split(";")){const [key,...rest]=part.trim().split("=");if(key===name)return decodeURIComponent(rest.join("="))}return null}
export async function authenticateBillingWeb(request:Request){const token=cookieValue(request,BILLING_COOKIE);if(!token||!token.startsWith("srweb_"))return null;const {data,error}=await adminSupabase().from("billing_web_sessions").select("id,driver_id,expires_at").eq("token_hash",sha256(token)).gt("expires_at",new Date().toISOString()).maybeSingle();if(error||!data)return null;return{driverId:String(data.driver_id),sessionId:String(data.id)}}
export async function authenticateBillingActor(request:Request){const device=await authenticateDevice(request);if(device)return{driverId:device.driverId,source:"device" as const};const web=await authenticateBillingWeb(request);if(web)return{driverId:web.driverId,source:"web" as const};return null}
export async function deleteBillingWebSession(request:Request){const token=cookieValue(request,BILLING_COOKIE);if(!token)return;await adminSupabase().from("billing_web_sessions").delete().eq("token_hash",sha256(token))}
