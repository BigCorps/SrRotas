import { adminSupabase } from "./supabase";
import { serverEnv } from "./env";
import { qrCodeSource } from "./pix";

export const CORE_MONTHLY_CENTS=990;
export const CORE_PLAN_ID="core_monthly";

type EntitlementRow = {
  entitlement: string;
  active: boolean;
  valid_until: string | null;
};

async function edge<T=any>(slug:string,body:Record<string,unknown>):Promise<T>{
  const env=serverEnv();
  const response=await fetch(`${env.supabaseUrl.replace(/\/$/,"")}/functions/v1/${slug}`,{
    method:"POST",
    headers:{"Content-Type":"application/json",Authorization:`Bearer ${env.supabaseServiceRoleKey}`,apikey:env.supabaseServiceRoleKey},
    body:JSON.stringify(body),cache:"no-store",
  });
  const json=await response.json().catch(()=>({}));
  if(!response.ok)throw new Error(String(json?.error||json?.message||`${slug}_http_${response.status}`));
  return json as T;
}

export async function billingStatus(driverId:string,refreshPending=true){
  const supabase=adminSupabase();
  await supabase.rpc("sr_ensure_credit_wallet",{p_driver_id:driverId});
  if(refreshPending){
    const pending=await supabase.from("payments").select("id,last_checked_at").eq("driver_id",driverId).eq("kind","subscription").in("status",["pending","manual_review"]).order("created_at",{ascending:false}).limit(1).maybeSingle();
    if(!pending.error&&pending.data){
      const last=pending.data.last_checked_at?new Date(pending.data.last_checked_at).getTime():0;
      if(Date.now()-last>4000)await edge("srrotas-check-pix",{payment_id:pending.data.id}).catch(()=>undefined);
    }
  }
  const [subscription,wallet,entitlements,pending]=await Promise.all([
    supabase.from("subscriptions").select("id,plan_id,status,starts_at,current_period_end,canceled_at,payment_provider").eq("driver_id",driverId).eq("plan_id",CORE_PLAN_ID).maybeSingle(),
    supabase.from("credit_wallets").select("balance,lifetime_granted,lifetime_spent,updated_at").eq("driver_id",driverId).maybeSingle(),
    supabase.from("entitlements").select("entitlement,active,valid_until").eq("driver_id",driverId),
    supabase.from("payments").select("id,status,amount_cents,txid,pix_copy_paste,qr_code_payload,expires_at,created_at,error_code,error_message").eq("driver_id",driverId).eq("kind","subscription").in("status",["pending","manual_review"]).order("created_at",{ascending:false}).limit(1).maybeSingle(),
  ]);
  for(const q of [subscription,wallet,entitlements,pending])if(q.error)throw new Error(q.error.message);
  const end=subscription.data?.current_period_end?new Date(subscription.data.current_period_end):null;
  const active=subscription.data?.status==="active"&&!!end&&end.getTime()>Date.now();
  return{
    plan:{id:CORE_PLAN_ID,name:"Sr. Rotas",amount_cents:CORE_MONTHLY_CENTS,interval:"month"},
    subscription:subscription.data?{...subscription.data,active}:null,
    wallet:wallet.data??{balance:0,lifetime_granted:0,lifetime_spent:0},
    entitlements:(entitlements.data??[]).map((e: EntitlementRow)=>({...e,effective:Boolean(e.active)&&(!e.valid_until||new Date(e.valid_until).getTime()>Date.now())})),
    pending_payment:pending.data?{...pending.data,pix_qrcode:qrCodeSource(pending.data.qr_code_payload)}:null,
    billing_enforcement:serverEnv().billingEnforcement,credit_packs_available:false,
  };
}

export async function createSubscriptionCharge(driverId:string){
  const result=await edge<any>("srrotas-create-pix",{driver_id:driverId});
  return{
    payment_id:result.payment_id,amount_cents:result.amount_cents,status:result.status,
    txid:result.txid,br_code:result.pix_code,qr_code_image:qrCodeSource(result.pix_qrcode),
    expires_at:result.expires_at,reused:Boolean(result.reused),
  };
}

export async function beginAiCredit(driverId:string,referenceId:string){
  const status=await billingStatus(driverId,false);
  const hasAi=status.entitlements.some((e:any)=>e.entitlement==="ai"&&e.effective);
  if(!serverEnv().billingEnforcement&&(!hasAi||Number(status.wallet.balance||0)<=0))return{reserved:false,alphaBypass:true};
  if(!hasAi)throw new Error("subscription_required");
  if(Number(status.wallet.balance||0)<=0)throw new Error("ai_credits_required");
  const reserved=await adminSupabase().rpc("sr_reserve_ai_credit",{p_driver_id:driverId,p_reference_id:referenceId});
  if(reserved.error)throw new Error(reserved.error.message);if(!reserved.data)throw new Error("ai_credits_required");return{reserved:true,alphaBypass:false};
}
export async function consumeAiCredit(driverId:string,referenceId:string,reserved:boolean){if(!reserved)return;const r=await adminSupabase().rpc("sr_consume_ai_credit",{p_driver_id:driverId,p_reference_id:referenceId});if(r.error)throw new Error(r.error.message)}
export async function refundAiCredit(driverId:string,referenceId:string,reserved:boolean){if(!reserved)return;await adminSupabase().rpc("sr_refund_ai_credit",{p_driver_id:driverId,p_reference_id:referenceId})}
