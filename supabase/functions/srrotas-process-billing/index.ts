import { createClient } from "npm:@supabase/supabase-js@2.110.9";
import { bankAmountToCents, bankResponseData, normalizeBankStatus, PAID_BANK_STATUSES } from "../_shared/pix.ts";
import { serviceAuthorized } from "../_shared/service-auth.ts";

const SUPABASE_URL=Deno.env.get("SUPABASE_URL")??"";
const SERVICE_ROLE_KEY=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")??"";
const BANCO_INTER_API_KEY=Deno.env.get("BANCO_INTER_API_KEY")??"";
const BRIDGE_BASE_URL=(Deno.env.get("BANCO_INTER_BRIDGE_BASE_URL")??"https://inter.btsolucao.com.br").replace(/\/$/,"");
const admin=createClient(SUPABASE_URL,SERVICE_ROLE_KEY,{auth:{persistSession:false,autoRefreshToken:false}});
function json(body:Record<string,unknown>,status=200){return new Response(JSON.stringify(body),{status,headers:{"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store"}})}
function objectValue(value:unknown){return value&&typeof value==="object"&&!Array.isArray(value)?value as Record<string,unknown>: {}}

async function checkPayment(payment:{id:string;txid:string}){
  try{
    const response=await fetch(`${BRIDGE_BASE_URL}/get.php?txid=${encodeURIComponent(payment.txid)}`,{
      method:"GET",headers:{Authorization:`Bearer ${BANCO_INTER_API_KEY}`,"Content-Type":"application/json"},signal:AbortSignal.timeout(18000),
    });
    const text=await response.text();let payload:Record<string,unknown>={};
    try{payload=objectValue(JSON.parse(text))}catch{payload={raw:text.slice(0,2000),httpStatus:response.status}}
    if(!response.ok){await admin.rpc("sr_mark_pix_checked",{p_payment_id:payment.id,p_bank_status:`HTTP_${response.status}`,p_provider_payload:payload});return{paymentId:payment.id,result:"provider_error"}}
    const data=bankResponseData(payload);const status=normalizeBankStatus(data.status??data.situacao??data.state);
    if(!PAID_BANK_STATUSES.has(status)){await admin.rpc("sr_mark_pix_checked",{p_payment_id:payment.id,p_bank_status:status||"PENDENTE",p_provider_payload:payload});return{paymentId:payment.id,result:"pending",bankStatus:status}}
    const paidAmountCents=bankAmountToCents(data);
    const {data:confirmation,error}=await admin.rpc("sr_apply_confirmed_payment",{
      p_payment_id:payment.id,p_txid:payment.txid,p_paid_amount_cents:paidAmountCents,p_provider_status:status,p_provider_payload:payload,p_confirmed_at:new Date().toISOString(),
    });
    if(error)return{paymentId:payment.id,result:"confirmation_error"};
    const result=objectValue(confirmation);return{paymentId:payment.id,result:result.success===true?"confirmed":"manual_review",duplicate:result.duplicate===true};
  }catch{return{paymentId:payment.id,result:"unexpected_error"}}
}

Deno.serve(async(request:Request)=>{
  if(request.method!=="POST")return json({success:false,error:"method_not_allowed"},405);
  if(!serviceAuthorized(request,SERVICE_ROLE_KEY))return json({success:false,error:"unauthorized"},401);
  if(!BANCO_INTER_API_KEY)return json({success:false,error:"banco_inter_not_configured"},503);
  try{
    const {data:expired,error:expireError}=await admin.rpc("sr_expire_pix_payments");
    if(expireError)return json({success:false,error:"billing_deadlines_failed"},500);
    const {data:payments,error}=await admin.from("payments").select("id,txid")
      .in("status",["pending","manual_review"]).not("txid","is",null)
      .gt("expires_at",new Date().toISOString()).order("last_checked_at",{ascending:true,nullsFirst:true}).limit(25);
    if(error)return json({success:false,error:"pending_payments_unavailable"},500);
    const results:Array<Record<string,unknown>>=[];
    for(const p of payments??[])results.push(await checkPayment({id:String(p.id),txid:String(p.txid)}));
    return json({success:true,expiredPayments:Number(expired??0),scannedPayments:results.length,confirmedPayments:results.filter(i=>i.result==="confirmed").length,manualReview:results.filter(i=>i.result==="manual_review").length,results,processedAt:new Date().toISOString()});
  }catch(error){return json({success:false,error:"unexpected_error",message:error instanceof Error?error.message:String(error)},500)}
});
