import { adminSupabase } from "./supabase";
import { serverEnv } from "./env";
import { qrCodeSource } from "./pix";

export const CORE_MONTHLY_CENTS = 990;
export const CORE_PLAN_ID = "core_monthly";

type EntitlementRow = {
  entitlement: string;
  active: boolean;
  valid_until: string | null;
};

async function edge<T = any>(
  slug: string,
  body: Record<string, unknown>,
): Promise<T> {
  const env = serverEnv();
  const response = await fetch(
    `${env.supabaseUrl.replace(/\/$/, "")}/functions/v1/${slug}`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${env.supabaseServiceRoleKey}`,
        apikey: env.supabaseServiceRoleKey,
      },
      body: JSON.stringify(body),
      cache: "no-store",
    },
  );
  const json = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(
      String(json?.error || json?.message || `${slug}_http_${response.status}`),
    );
  }
  return json as T;
}

export async function billingStatus(driverId: string, refreshPending = true) {
  const supabase = adminSupabase();

  if (refreshPending) {
    const pending = await supabase
      .from("payments")
      .select("id,last_checked_at")
      .eq("driver_id", driverId)
      .eq("kind", "subscription")
      .in("status", ["pending", "manual_review"])
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (!pending.error && pending.data) {
      const last = pending.data.last_checked_at
        ? new Date(pending.data.last_checked_at).getTime()
        : 0;
      if (Date.now() - last > 4000) {
        await edge("srrotas-check-pix", {
          payment_id: pending.data.id,
        }).catch(() => undefined);
      }
    }
  }

  const [subscription, wallet, entitlements, pending, trial] =
    await Promise.all([
      supabase
        .from("subscriptions")
        .select(
          "id,plan_id,status,starts_at,current_period_end,canceled_at,payment_provider",
        )
        .eq("driver_id", driverId)
        .eq("plan_id", CORE_PLAN_ID)
        .maybeSingle(),
      supabase
        .from("credit_wallets")
        .select("balance,lifetime_granted,lifetime_spent,updated_at")
        .eq("driver_id", driverId)
        .maybeSingle(),
      supabase
        .from("entitlements")
        .select("entitlement,active,valid_until")
        .eq("driver_id", driverId),
      supabase
        .from("payments")
        .select(
          "id,status,amount_cents,txid,pix_copy_paste,qr_code_payload,expires_at,created_at,error_code,error_message",
        )
        .eq("driver_id", driverId)
        .eq("kind", "subscription")
        .in("status", ["pending", "manual_review"])
        .order("created_at", { ascending: false })
        .limit(1)
        .maybeSingle(),
      supabase
        .from("sr_driver_trial_status_v1")
        .select(
          "first_offer_at,trial_started_at,trial_ends_at,ai_credits_granted,trial_status,days_remaining",
        )
        .eq("driver_id", driverId)
        .maybeSingle(),
    ]);

  for (const query of [subscription, wallet, entitlements, pending, trial]) {
    if (query.error) throw new Error(query.error.message);
  }

  const end = subscription.data?.current_period_end
    ? new Date(subscription.data.current_period_end)
    : null;
  const active =
    subscription.data?.status === "active" &&
    !!end &&
    end.getTime() > Date.now();

  const trialData = trial.data ?? {
    trial_status: "pending",
    days_remaining: null,
    trial_started_at: null,
    trial_ends_at: null,
    ai_credits_granted: 0,
    first_offer_at: null,
  };

  return {
    commercial_model: "copilot_free_intelligence_v1",
    tiers: {
      copilot: {
        id: "copilot_free",
        name: "Sr. Rotas Copiloto",
        amount_cents: 0,
        permanent: true,
        includes: [
          "Leitura de ofertas",
          "HUD e indicadores R$/km, R$/min e R$/h",
          "Custos e análise financeira básica",
          "Histórico pessoal",
        ],
      },
      intelligence: {
        id: CORE_PLAN_ID,
        name: "Sr. Rotas Inteligência",
        amount_cents: CORE_MONTHLY_CENTS,
        interval_days: 30,
        includes: [
          "Estatísticas Premium",
          "Pergunte ao Sr. Rotas",
          "Inteligência regional e temporal",
          "Continuidade, destino e oportunidades",
          "MCP somente leitura",
        ],
      },
    },
    plan: {
      id: CORE_PLAN_ID,
      name: "Sr. Rotas Inteligência",
      amount_cents: CORE_MONTHLY_CENTS,
      interval: "month",
    },
    subscription: subscription.data
      ? { ...subscription.data, active }
      : null,
    trial: {
      ...trialData,
      product: "intelligence",
      deterministic_questions_use_credits: false,
    },
    // Compatibilidade com builds anteriores. A carteira é legado e não recebe
    // novos créditos no modelo Copiloto + Inteligência.
    wallet: wallet.data ?? {
      balance: 0,
      lifetime_granted: 0,
      lifetime_spent: 0,
    },
    credits_legacy: true,
    deterministic_questions_use_credits: false,
    entitlements: (entitlements.data ?? []).map((item: EntitlementRow) => ({
      ...item,
      effective:
        Boolean(item.active) &&
        (!item.valid_until ||
          new Date(item.valid_until).getTime() > Date.now()),
    })),
    pending_payment: pending.data
      ? {
          ...pending.data,
          pix_qrcode: qrCodeSource(pending.data.qr_code_payload),
        }
      : null,
    billing_enforcement: serverEnv().billingEnforcement,
    credit_packs_available: false,
  };
}

export async function createSubscriptionCharge(driverId: string) {
  const result = await edge<any>("srrotas-create-pix", {
    driver_id: driverId,
  });
  return {
    payment_id: result.payment_id,
    amount_cents: result.amount_cents,
    status: result.status,
    txid: result.txid,
    br_code: result.pix_code,
    qr_code_image: qrCodeSource(result.pix_qrcode),
    expires_at: result.expires_at,
    reused: Boolean(result.reused),
    product: "intelligence",
  };
}

/**
 * Legado pré-Gate 4. O Pergunte determinístico não chama estas funções.
 * Mantidas temporariamente para compatibilidade de código antigo e auditoria.
 */
export async function beginAiCredit(
  driverId: string,
  referenceId: string,
) {
  const status = await billingStatus(driverId, false);
  const hasAi = status.entitlements.some(
    (item: any) => item.entitlement === "ai" && item.effective,
  );
  const trialActive = status.trial?.trial_status === "active";

  if (
    !serverEnv().billingEnforcement &&
    ((!hasAi && !trialActive) || Number(status.wallet.balance || 0) <= 0)
  ) {
    return { reserved: false, alphaBypass: true };
  }
  if (!hasAi && !trialActive) throw new Error("subscription_required");
  if (Number(status.wallet.balance || 0) <= 0) {
    throw new Error("ai_credits_required");
  }

  const reserved = await adminSupabase().rpc("sr_reserve_ai_credit", {
    p_driver_id: driverId,
    p_reference_id: referenceId,
  });
  if (reserved.error) throw new Error(reserved.error.message);
  if (!reserved.data) throw new Error("ai_credits_required");
  return { reserved: true, alphaBypass: false };
}

export async function consumeAiCredit(
  driverId: string,
  referenceId: string,
  reserved: boolean,
) {
  if (!reserved) return;
  const result = await adminSupabase().rpc("sr_consume_ai_credit", {
    p_driver_id: driverId,
    p_reference_id: referenceId,
  });
  if (result.error) throw new Error(result.error.message);
}

export async function refundAiCredit(
  driverId: string,
  referenceId: string,
  reserved: boolean,
) {
  if (!reserved) return;
  await adminSupabase().rpc("sr_refund_ai_credit", {
    p_driver_id: driverId,
    p_reference_id: referenceId,
  });
}
