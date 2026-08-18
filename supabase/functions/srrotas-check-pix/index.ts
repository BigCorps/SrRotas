import { createClient } from "npm:@supabase/supabase-js@2.110.9";
import { bankAmountToCents, bankResponseData, normalizeBankStatus, PAID_BANK_STATUSES } from "../_shared/pix.ts";
import { serviceAuthorized } from "../_shared/service-auth.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const BANCO_INTER_API_KEY = Deno.env.get("BANCO_INTER_API_KEY") ?? "";
const BRIDGE_BASE_URL = (Deno.env.get("BANCO_INTER_BRIDGE_BASE_URL") ?? "https://inter.btsolucao.com.br").replace(/\/$/, "");
const admin = createClient(SUPABASE_URL,SERVICE_ROLE_KEY,{auth:{persistSession:false,autoRefreshToken:false}});

function json(body:Record<string,unknown>,status=200){return new Response(JSON.stringify(body),{status,headers:{"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store"}})}
function uuid(value:unknown){const c=String(value??"").trim();return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(c)?c:null}
function objectValue(value:unknown){return value&&typeof value==="object"&&!Array.isArray(value)?value as Record<string,unknown>: {}}

Deno.serve(async(request:Request)=>{
  if(request.method!=="POST")return json({success:false,error:"method_not_allowed"},405);
  if(!serviceAuthorized(request,SERVICE_ROLE_KEY))return json({success:false,error:"unauthorized"},401);
  if(!BANCO_INTER_API_KEY)return json({success:false,error:"banco_inter_not_configured"},503);
  try{
    const body=await request.json().catch(()=>({}));
    const paymentId=uuid((body as Record<string,unknown>).payment_id);
    if(!paymentId)return json({success:false,error:"invalid_payment_id"},400);

    const {data:payment,error}=await admin.from("payments")
      .select("id,driver_id,status,txid,amount_cents,expires_at,confirmed_at,bank_status,error_code,error_message")
      .eq("id",paymentId).maybeSingle();
    if(error||!payment)return json({success:false,error:"payment_not_found"},404);

    if(payment.status==="paid")return json({success:true,status:"paid",payment_id:payment.id,paid_at:payment.confirmed_at,message:"Pagamento já confirmado."});
    if(["expired","canceled","failed","refunded"].includes(String(payment.status)))return json({success:false,status:payment.status,payment_id:payment.id,error:payment.error_code??`payment_${payment.status}`,message:payment.error_message??"Cobrança indisponível."});
    if(payment.expires_at&&new Date(String(payment.expires_at)).getTime()<=Date.now()){
      await admin.rpc("sr_expire_pix_payments");
      return json({success:false,status:"expired",payment_id:payment.id,message:"O Pix expirou. Gere nova cobrança."});
    }
    if(!payment.txid)return json({success:false,status:"pending",payment_id:payment.id,message:"A cobrança ainda está sendo preparada."});

    const response=await fetch(`${BRIDGE_BASE_URL}/get.php?txid=${encodeURIComponent(payment.txid)}`,{
      method:"GET",headers:{Authorization:`Bearer ${BANCO_INTER_API_KEY}`,"Content-Type":"application/json"},signal:AbortSignal.timeout(20000),
    });
    const text=await response.text();let providerPayload:Record<string,unknown>={};
    try{providerPayload=objectValue(JSON.parse(text))}catch{providerPayload={raw:text.slice(0,2000),httpStatus:response.status}}

    if(!response.ok){
      await admin.rpc("sr_mark_pix_checked",{p_payment_id:payment.id,p_bank_status:`HTTP_${response.status}`,p_provider_payload:providerPayload});
      return json({success:false,status:"pending",payment_id:payment.id,message:"O banco ainda não pôde confirmar o pagamento."});
    }

    const data=bankResponseData(providerPayload);
    const bankStatus=normalizeBankStatus(data.status??data.situacao??data.state);
    if(PAID_BANK_STATUSES.has(bankStatus)){
      const paidAmountCents=bankAmountToCents(data);
      const {data:confirmation,error:confirmationError}=await admin.rpc("sr_apply_confirmed_payment",{
        p_payment_id:payment.id,p_txid:payment.txid,p_paid_amount_cents:paidAmountCents,
        p_provider_status:bankStatus,p_provider_payload:providerPayload,p_confirmed_at:new Date().toISOString(),
      });
      if(confirmationError)return json({success:false,error:"payment_confirmation_failed",message:confirmationError.message},500);
      const result=objectValue(confirmation);
      if(result.success===true)return json({success:true,status:"paid",payment_id:payment.id,period_end:result.periodEnd??null,balance:result.balance??null,duplicate:result.duplicate===true,message:"Pagamento confirmado e plano ativado."});
      return json({success:false,status:String(result.status??"manual_review"),payment_id:payment.id,reason:result.reason??"manual_review",message:result.reason==="amount_mismatch"?"O valor recebido é diferente da cobrança e precisa de revisão.":"Pagamento precisa de revisão."});
    }

    await admin.rpc("sr_mark_pix_checked",{p_payment_id:payment.id,p_bank_status:bankStatus||"PENDENTE",p_provider_payload:providerPayload});
    return json({success:false,status:"pending",bank_status:bankStatus||"PENDENTE",payment_id:payment.id,message:"Pagamento ainda não confirmado."});
  }catch(error){return json({success:false,status:"error",error:"unexpected_error",message:error instanceof Error?error.message:"Não foi possível consultar o pagamento."},500)}
});
