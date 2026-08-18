import { createClient } from "npm:@supabase/supabase-js@2.110.9";
import { serviceAuthorized } from "../_shared/service-auth.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const BANCO_INTER_API_KEY = Deno.env.get("BANCO_INTER_API_KEY") ?? "";
const BRIDGE_BASE_URL = (Deno.env.get("BANCO_INTER_BRIDGE_BASE_URL") ?? "https://inter.btsolucao.com.br").replace(/\/$/, "");
const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, { auth: { persistSession: false, autoRefreshToken: false } });

function json(body: Record<string, unknown>, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } });
}
function uuid(value: unknown) {
  const candidate = String(value ?? "").trim();
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(candidate) ? candidate : null;
}
function objectValue(value: unknown) { return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {}; }

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return json({ success:false,error:"method_not_allowed" },405);
  if (!serviceAuthorized(request,SERVICE_ROLE_KEY)) return json({ success:false,error:"unauthorized" },401);
  if (!BANCO_INTER_API_KEY) return json({ success:false,error:"banco_inter_not_configured" },503);

  let paymentId: string | null = null;
  try {
    const body = await request.json().catch(() => ({}));
    const driverId = uuid((body as Record<string, unknown>).driver_id);
    if (!driverId) return json({ success:false,error:"invalid_driver_id" },400);

    const expiresAt = new Date(Date.now() + 30 * 60 * 1000);
    const { data: created, error: createError } = await admin.rpc("sr_create_pix_payment", {
      p_driver_id: driverId,
      p_expires_at: expiresAt.toISOString(),
    });
    if (createError) return json({ success:false,error:"pix_record_creation_failed",message:createError.message },400);

    const local = objectValue(created);
    paymentId = uuid(local.paymentId);
    if (!paymentId) throw new Error("payment_id_missing");

    const { data: existing } = await admin.from("payments")
      .select("id,status,txid,amount_cents,pix_copy_paste,qr_code_payload,expires_at")
      .eq("id",paymentId).single();

    if (local.reused === true && existing?.txid && existing.pix_copy_paste) {
      return json({
        success:true,reused:true,payment_id:existing.id,status:existing.status,txid:existing.txid,
        pix_code:existing.pix_copy_paste,pix_qrcode:existing.qr_code_payload,expires_at:existing.expires_at,
        amount_cents:existing.amount_cents,amount:existing.amount_cents/100,
      });
    }

    const amountCents = Number(local.amountCents ?? 990);
    const response = await fetch(`${BRIDGE_BASE_URL}/cob.php`, {
      method:"POST",
      headers:{ "Content-Type":"application/json", Authorization:`Bearer ${BANCO_INTER_API_KEY}` },
      body:JSON.stringify({
        amount:{ original:(amountCents/100).toFixed(2) }, expiresIn:1800,
        displayText:"Sr. Rotas - plano mensal", modalidadeAlteracao:0,
      }),
      signal:AbortSignal.timeout(25000),
    });

    const responseText = await response.text();
    let providerData: Record<string, unknown> = {};
    try { providerData = objectValue(JSON.parse(responseText)); }
    catch { providerData = { raw:responseText.slice(0,2000), httpStatus:response.status }; }

    if (!response.ok) {
      await admin.rpc("sr_mark_pix_failed", {
        p_payment_id:paymentId,p_error_code:`provider_http_${response.status}`,
        p_error_message:"O Banco Inter não aceitou a geração da cobrança.",p_provider_payload:providerData,
      });
      return json({ success:false,error:"banco_inter_generation_failed",message:"Não foi possível gerar o Pix agora." },502);
    }

    const txid = String(providerData.txid ?? "").trim();
    const pixCode = String(providerData.pixCopiaECola ?? "").trim();
    const qrCode = String(providerData.qrcode ?? "").trim();
    if (!txid || !pixCode) {
      await admin.rpc("sr_mark_pix_failed", {
        p_payment_id:paymentId,p_error_code:"provider_response_invalid",
        p_error_message:"A resposta bancária não trouxe txid ou código Pix.",p_provider_payload:providerData,
      });
      return json({ success:false,error:"banco_inter_response_invalid",message:"O banco retornou uma cobrança incompleta." },502);
    }

    const { error: attachError } = await admin.rpc("sr_attach_pix_provider_data", {
      p_payment_id:paymentId,p_txid:txid,p_pix_copy_paste:pixCode,p_qr_code_payload:qrCode,p_provider_payload:providerData,
    });
    if (attachError) {
      await admin.rpc("sr_mark_pix_failed", {
        p_payment_id:paymentId,p_error_code:"provider_data_persistence_failed",
        p_error_message:"A cobrança foi criada, mas não pôde ser vinculada.",p_provider_payload:providerData,
      });
      return json({ success:false,error:"pix_persistence_failed" },500);
    }

    return json({
      success:true,reused:false,payment_id:paymentId,status:"pending",txid,pix_code:pixCode,pix_qrcode:qrCode||null,
      expires_at:String(local.expiresAt ?? expiresAt.toISOString()),amount_cents:amountCents,amount:amountCents/100,
    });
  } catch (error) {
    if (paymentId) await admin.rpc("sr_mark_pix_failed", {
      p_payment_id:paymentId,p_error_code:"unexpected_error",p_error_message:"Falha inesperada ao gerar a cobrança Pix.",p_provider_payload:{},
    });
    return json({ success:false,error:"unexpected_error",message:error instanceof Error ? error.message : "Não foi possível gerar o Pix." },500);
  }
});
