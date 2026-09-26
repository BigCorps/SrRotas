import { adminSupabase } from "./supabase";

const OUTCOME_STATUSES = ["OFFERED", "DOING_RIDE", "COMPLETED", "NOT_COMPLETED", "CANCELLED"] as const;

async function countRows(table: string, apply?: (query: any) => any) {
  const supabase: any = adminSupabase();
  let query: any = supabase.from(table).select("*", { count: "exact", head: true });
  if (apply) query = apply(query);
  const { count, error } = await query;
  if (error) throw new Error(error.message);
  return Number(count ?? 0);
}

async function outcomeCounts() {
  const pairs = await Promise.all(
    OUTCOME_STATUSES.map(async (status) => [
      status,
      await countRows("ride_outcomes", (q) => q.eq("status", status)),
    ] as const),
  );
  return Object.fromEntries(pairs);
}

function byDriver(rows: any[]) {
  const map = new Map<string, any[]>();
  for (const row of rows) {
    const key = String(row?.driver_id || "");
    if (!key) continue;
    const current = map.get(key) ?? [];
    current.push(row);
    map.set(key, current);
  }
  return map;
}

function latestByDriver(rows: any[], dateField?: string) {
  const map = new Map<string, any>();
  for (const row of rows) {
    const key = String(row?.driver_id || "");
    if (!key) continue;
    const current = map.get(key);
    if (!current) {
      map.set(key, row);
      continue;
    }
    if (dateField) {
      const a = new Date(row?.[dateField] || 0).getTime();
      const b = new Date(current?.[dateField] || 0).getTime();
      if (a > b) map.set(key, row);
    }
  }
  return map;
}

function newest(rows: any[], field: string) {
  return [...rows].sort((a, b) => new Date(b?.[field] || 0).getTime() - new Date(a?.[field] || 0).getTime())[0] ?? null;
}

function isRecent(value: string | null | undefined, minutes: number) {
  if (!value) return false;
  const time = new Date(value).getTime();
  return Number.isFinite(time) && Date.now() - time <= minutes * 60 * 1000;
}

function publicBatch(row: any) {
  const manifest = row?.processing_manifest && typeof row.processing_manifest === "object"
    ? row.processing_manifest
    : {};
  return {
    id: row.id,
    status: row.status,
    source_name: row.source_name,
    original_filename: row.original_filename,
    schema_version: row.schema_version,
    extractor_version: row.extractor_version,
    received_count: Number(row.received_count || 0),
    valid_count: Number(row.valid_count || 0),
    partial_count: Number(row.partial_count || 0),
    invalid_count: Number(row.invalid_count || 0),
    duplicate_count: Number(row.duplicate_count || 0),
    demand_temporal_ready_count: Number(row.demand_temporal_ready_count || 0),
    route_flow_ready_count: Number(row.route_flow_ready_count || 0),
    financial_ready_count: Number(row.financial_ready_count || 0),
    fully_ready_count: Number(row.fully_ready_count || 0),
    created_at: row.created_at,
    finalized_at: row.finalized_at,
    created_by_driver_id: row.created_by_driver_id,
    canonical_for_intelligence: Boolean(manifest.canonical_for_intelligence),
    canonical_owner_resolved: Boolean(manifest.canonical_owner_resolved),
    canonicalized_at: manifest.canonicalized_at || null,
  };
}

function sum(rows: any[], field: string) {
  return rows.reduce((total, row) => total + Number(row?.[field] || 0), 0);
}

function safePayment(row: any) {
  return {
    id: row.id,
    driver_id: row.driver_id,
    subscription_id: row.subscription_id,
    kind: row.kind,
    provider: row.provider,
    amount_cents: Number(row.amount_cents || 0),
    status: row.status,
    expires_at: row.expires_at,
    confirmed_at: row.confirmed_at,
    bank_status: row.bank_status,
    check_attempts: Number(row.check_attempts || 0),
    error_code: row.error_code,
    created_at: row.created_at,
    updated_at: row.updated_at,
  };
}

export async function adminOpsOverview() {
  const supabase: any = adminSupabase();
  const now = new Date();
  const nowIso = now.toISOString();
  const dayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString();
  const monthAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000).toISOString();

  const [
    driversCount,
    devicesCount,
    activeDevicesCount,
    openJourneysCount,
    offersCount,
    offers24hCount,
    outcomesCount,
    outcomesByStatus,
    trialsCount,
    subscriptionsCount,
    activeSubscriptionsCount,
    paymentsCount,
    entitlementsCount,
    activeWebSessionsCount,
    activeMcpTokensCount,
    vehicleMetricsCount,
    energyEntriesCount,
    aiCalls30d,
    notifications24h,
    driverResult,
    deviceResult,
    journeyResult,
    trialResult,
    subscriptionResult,
    paymentResult,
    entitlementResult,
    walletResult,
    batchResult,
    recentOffersResult,
    aiUsageResult,
    notificationResult,
    webSessionResult,
    mcpResult,
  ] = await Promise.all([
    countRows("drivers"),
    countRows("driver_devices"),
    countRows("driver_devices", (q) => q.eq("revoked", false)),
    countRows("driver_journeys", (q) => q.is("ended_at", null)),
    countRows("ride_offers"),
    countRows("ride_offers", (q) => q.gte("observed_at", dayAgo)),
    countRows("ride_outcomes"),
    outcomeCounts(),
    countRows("driver_trials"),
    countRows("subscriptions"),
    countRows("subscriptions", (q) => q.eq("status", "active")),
    countRows("payments"),
    countRows("entitlements"),
    countRows("billing_web_sessions", (q) => q.gt("expires_at", nowIso)),
    countRows("mcp_access_tokens", (q) => q.eq("revoked", false)),
    countRows("journey_vehicle_metrics"),
    countRows("journey_energy_entries"),
    countRows("ai_usage_logs", (q) => q.gte("created_at", monthAgo)),
    countRows("notification_deliveries", (q) => q.gte("created_at", dayAgo)),
    supabase.from("drivers")
      .select("id,display_name,email,onboarding_completed,created_at,updated_at,last_login_at")
      .order("created_at", { ascending: false }).limit(300),
    supabase.from("driver_devices")
      .select("id,driver_id,name,revoked,last_seen_at,created_at")
      .order("created_at", { ascending: false }).limit(1500),
    supabase.from("driver_journeys")
      .select("id,driver_id,device_id,platform,state,started_at,ended_at,end_reason,state_updated_at")
      .order("started_at", { ascending: false }).limit(600),
    supabase.from("driver_trials")
      .select("driver_id,first_offer_at,trial_started_at,trial_ends_at,ai_credits_granted,created_at,updated_at")
      .order("updated_at", { ascending: false }).limit(300),
    supabase.from("subscriptions")
      .select("id,driver_id,plan_id,status,starts_at,current_period_end,canceled_at,payment_provider,created_at,updated_at")
      .order("updated_at", { ascending: false }).limit(500),
    supabase.from("payments")
      .select("id,driver_id,subscription_id,kind,provider,amount_cents,status,expires_at,confirmed_at,bank_status,check_attempts,error_code,created_at,updated_at")
      .order("created_at", { ascending: false }).limit(500),
    supabase.from("entitlements")
      .select("driver_id,entitlement,active,valid_until,updated_at")
      .order("updated_at", { ascending: false }).limit(1000),
    supabase.from("credit_wallets")
      .select("driver_id,balance,lifetime_granted,lifetime_spent,updated_at")
      .order("updated_at", { ascending: false }).limit(300),
    supabase.from("historical_import_batches")
      .select("id,status,source_name,original_filename,schema_version,extractor_version,received_count,valid_count,partial_count,invalid_count,duplicate_count,demand_temporal_ready_count,route_flow_ready_count,financial_ready_count,fully_ready_count,created_at,finalized_at,created_by_driver_id,processing_manifest")
      .order("created_at", { ascending: false }).limit(30),
    supabase.from("ride_offers")
      .select("id,driver_id,journey_id,platform,observed_at,capture_method,verdict,parser_version,confidence,service_type")
      .gte("observed_at", dayAgo).order("observed_at", { ascending: false }).limit(5000),
    supabase.from("ai_usage_logs")
      .select("id,driver_id,source,model,status,input_tokens,output_tokens,total_tokens,offer_count,duration_ms,error_code,created_at")
      .gte("created_at", monthAgo).order("created_at", { ascending: false }).limit(1000),
    supabase.from("notification_deliveries")
      .select("id,driver_id,category,status,error_code,created_at,sent_at")
      .gte("created_at", dayAgo).order("created_at", { ascending: false }).limit(500),
    supabase.from("billing_web_sessions")
      .select("id,driver_id,expires_at,created_at")
      .gt("expires_at", nowIso).order("created_at", { ascending: false }).limit(1000),
    supabase.from("mcp_access_tokens")
      .select("id,driver_id,name,token_prefix,revoked,last_used_at,created_at")
      .eq("revoked", false).order("created_at", { ascending: false }).limit(1000),
  ]);

  const resultSet = [
    driverResult, deviceResult, journeyResult, trialResult, subscriptionResult,
    paymentResult, entitlementResult, walletResult, batchResult, recentOffersResult,
    aiUsageResult, notificationResult, webSessionResult, mcpResult,
  ];
  for (const result of resultSet) {
    if (result.error) throw new Error(result.error.message);
  }

  const drivers = driverResult.data ?? [];
  const devices = deviceResult.data ?? [];
  const journeys = journeyResult.data ?? [];
  const trials = trialResult.data ?? [];
  const subscriptions = subscriptionResult.data ?? [];
  const payments = paymentResult.data ?? [];
  const entitlements = entitlementResult.data ?? [];
  const wallets = walletResult.data ?? [];
  const batches = (batchResult.data ?? []).map(publicBatch);
  const recentOffers = recentOffersResult.data ?? [];
  const aiUsage = aiUsageResult.data ?? [];
  const notifications = notificationResult.data ?? [];
  const webSessions = webSessionResult.data ?? [];
  const mcpTokens = mcpResult.data ?? [];

  const devicesByDriver = byDriver(devices);
  const journeysByDriver = byDriver(journeys);
  const offersByDriver = byDriver(recentOffers);
  const aiByDriver = byDriver(aiUsage);
  const notificationsByDriver = byDriver(notifications);
  const sessionsByDriver = byDriver(webSessions);
  const mcpByDriver = byDriver(mcpTokens);
  const entitlementsByDriver = byDriver(entitlements);
  const paymentsByDriver = byDriver(payments);
  const trialByDriver = latestByDriver(trials, "updated_at");
  const subscriptionByDriver = latestByDriver(subscriptions, "updated_at");
  const walletByDriver = latestByDriver(wallets, "updated_at");

  const driverRows = drivers.map((driver: any) => {
    const driverId = String(driver.id);
    const deviceRows = devicesByDriver.get(driverId) ?? [];
    const journeyRows = journeysByDriver.get(driverId) ?? [];
    const offerRows = offersByDriver.get(driverId) ?? [];
    const aiRows = aiByDriver.get(driverId) ?? [];
    const notificationRows = notificationsByDriver.get(driverId) ?? [];
    const currentJourney = journeyRows.find((row: any) => !row.ended_at) ?? null;
    const latestDevice = newest(deviceRows, "last_seen_at");
    const latestOffer = newest(offerRows, "observed_at");
    const latestNotification = newest(notificationRows, "created_at");
    const trial = trialByDriver.get(driverId) ?? null;
    const subscription = subscriptionByDriver.get(driverId) ?? null;
    const wallet = walletByDriver.get(driverId) ?? null;
    const driverEntitlements = entitlementsByDriver.get(driverId) ?? [];
    const driverPayments = paymentsByDriver.get(driverId) ?? [];
    const latestPayment = newest(driverPayments, "created_at");
    const activeDevices = deviceRows.filter((row: any) => !row.revoked);
    const online = activeDevices.some((row: any) => isRecent(row.last_seen_at, 15));
    const operationalState = currentJourney ? (online ? "driving" : "journey_stale") : online ? "online" : isRecent(latestOffer?.observed_at, 60) ? "recent" : "offline";

    return {
      id: driver.id,
      display_name: driver.display_name,
      email: driver.email,
      onboarding_completed: Boolean(driver.onboarding_completed),
      created_at: driver.created_at,
      last_login_at: driver.last_login_at,
      devices_total: deviceRows.length,
      devices_active: activeDevices.length,
      latest_device_seen_at: latestDevice?.last_seen_at ?? null,
      operational_state: operationalState,
      offers_24h: offerRows.length,
      last_offer_at: latestOffer?.observed_at ?? null,
      latest_offer: latestOffer ? {
        id: latestOffer.id,
        platform: latestOffer.platform,
        observed_at: latestOffer.observed_at,
        capture_method: latestOffer.capture_method,
        parser_version: latestOffer.parser_version,
        verdict: latestOffer.verdict,
        service_type: latestOffer.service_type,
      } : null,
      current_journey: currentJourney ? {
        id: currentJourney.id,
        state: currentJourney.state,
        platform: currentJourney.platform,
        started_at: currentJourney.started_at,
        state_updated_at: currentJourney.state_updated_at,
      } : null,
      trial: trial ? {
        trial_started_at: trial.trial_started_at,
        trial_ends_at: trial.trial_ends_at,
        ai_credits_granted: Number(trial.ai_credits_granted || 0),
      } : null,
      subscription: subscription ? {
        id: subscription.id,
        plan_id: subscription.plan_id,
        status: subscription.status,
        current_period_end: subscription.current_period_end,
      } : null,
      latest_payment: latestPayment ? safePayment(latestPayment) : null,
      wallet: wallet ? {
        balance: Number(wallet.balance || 0),
        lifetime_granted: Number(wallet.lifetime_granted || 0),
        lifetime_spent: Number(wallet.lifetime_spent || 0),
      } : null,
      active_entitlements: driverEntitlements.filter((row: any) => row.active).map((row: any) => row.entitlement),
      active_web_sessions: (sessionsByDriver.get(driverId) ?? []).length,
      active_mcp_tokens: (mcpByDriver.get(driverId) ?? []).length,
      ai_calls_30d: aiRows.length,
      ai_tokens_30d: sum(aiRows, "total_tokens"),
      last_notification_status: latestNotification?.status ?? null,
      last_notification_at: latestNotification?.created_at ?? null,
    };
  });

  const driverName = new Map(driverRows.map((driver: any) => [String(driver.id), driver.display_name || driver.email || driver.id]));
  const alerts: any[] = [];

  for (const driver of driverRows) {
    if (driver.operational_state === "journey_stale") {
      alerts.push({
        severity: "warning",
        kind: "journey_stale",
        driver_id: driver.id,
        driver_name: driver.display_name,
        title: "Jornada aberta sem contato recente",
        detail: "A jornada continua aberta, mas nenhum aparelho ativo foi visto nos últimos 15 minutos.",
        at: driver.latest_device_seen_at || driver.current_journey?.started_at,
      });
    }
    if (driver.latest_payment && ["failed", "manual_review"].includes(String(driver.latest_payment.status))) {
      alerts.push({
        severity: "critical",
        kind: "payment_attention",
        driver_id: driver.id,
        driver_name: driver.display_name,
        title: "Pagamento exige atenção",
        detail: `${driver.latest_payment.status}${driver.latest_payment.error_code ? ` · ${driver.latest_payment.error_code}` : ""}`,
        at: driver.latest_payment.created_at,
      });
    }
  }

  for (const row of notifications.filter((item: any) => String(item.status).toLowerCase() === "failed")) {
    alerts.push({
      severity: "warning",
      kind: "notification_failed",
      driver_id: row.driver_id,
      driver_name: driverName.get(String(row.driver_id)) || "Motorista",
      title: "Falha de notificação",
      detail: `${row.category || "notificação"}${row.error_code ? ` · ${row.error_code}` : ""}`,
      at: row.created_at,
    });
  }

  for (const row of aiUsage.filter((item: any) => String(item.status).toLowerCase() === "failed")) {
    alerts.push({
      severity: "warning",
      kind: "ai_failed",
      driver_id: row.driver_id,
      driver_name: driverName.get(String(row.driver_id)) || "Motorista",
      title: "Falha em chamada de IA",
      detail: `${row.model || "modelo"}${row.error_code ? ` · ${row.error_code}` : ""}`,
      at: row.created_at,
    });
  }

  alerts.sort((a, b) => new Date(b.at || 0).getTime() - new Date(a.at || 0).getTime());

  const canonicalBatch = batches.find((batch: any) => batch.canonical_for_intelligence && batch.status === "ready") ?? null;
  const paymentTotalConfirmedCents = payments
    .filter((row: any) => row.confirmed_at || ["paid", "confirmed"].includes(String(row.status).toLowerCase()))
    .reduce((total: number, row: any) => total + Number(row.amount_cents || 0), 0);

  const aiModels = Object.values(aiUsage.reduce((acc: Record<string, any>, row: any) => {
    const model = String(row.model || "unknown");
    const current = acc[model] || { model, calls: 0, input_tokens: 0, output_tokens: 0, total_tokens: 0, failures: 0 };
    current.calls += 1;
    current.input_tokens += Number(row.input_tokens || 0);
    current.output_tokens += Number(row.output_tokens || 0);
    current.total_tokens += Number(row.total_tokens || 0);
    if (String(row.status).toLowerCase() === "failed") current.failures += 1;
    acc[model] = current;
    return acc;
  }, {}));

  return {
    generated_at: nowIso,
    deployment: {
      environment: process.env.VERCEL_ENV || null,
      commit_sha: process.env.VERCEL_GIT_COMMIT_SHA || null,
    },
    counts: {
      drivers: driversCount,
      drivers_online: driverRows.filter((row: any) => ["online", "driving"].includes(row.operational_state)).length,
      drivers_driving: driverRows.filter((row: any) => row.operational_state === "driving").length,
      devices: devicesCount,
      active_devices: activeDevicesCount,
      open_journeys: openJourneysCount,
      offers: offersCount,
      offers_24h: offers24hCount,
      outcomes: outcomesCount,
      trials: trialsCount,
      subscriptions: subscriptionsCount,
      active_subscriptions: activeSubscriptionsCount,
      payments: paymentsCount,
      entitlements: entitlementsCount,
      active_web_sessions: activeWebSessionsCount,
      active_mcp_tokens: activeMcpTokensCount,
      vehicle_metrics: vehicleMetricsCount,
      energy_entries: energyEntriesCount,
      ai_calls_30d: aiCalls30d,
      ai_tokens_30d: sum(aiUsage, "total_tokens"),
      ai_failures_30d: aiUsage.filter((row: any) => String(row.status).toLowerCase() === "failed").length,
      notifications_24h: notifications24h,
      notification_failures_24h: notifications.filter((row: any) => String(row.status).toLowerCase() === "failed").length,
      admin_alerts: alerts.length,
    },
    outcomes: outcomesByStatus,
    drivers: driverRows,
    recent_journeys: journeys.slice(0, 60),
    alerts: alerts.slice(0, 60),
    finance: {
      subscriptions,
      payments: payments.map(safePayment),
      entitlements,
      confirmed_revenue_cents: paymentTotalConfirmedCents,
    },
    costs: {
      ai: {
        period_days: 30,
        calls: aiUsage.length,
        input_tokens: sum(aiUsage, "input_tokens"),
        output_tokens: sum(aiUsage, "output_tokens"),
        total_tokens: sum(aiUsage, "total_tokens"),
        failures: aiUsage.filter((row: any) => String(row.status).toLowerCase() === "failed").length,
        models: aiModels,
        monetary_cost_available: false,
        note: "O banco registra uso e tokens, mas não registra preço por modelo. O Admin não inventa custo monetário.",
      },
      infrastructure: {
        vercel: { connected_to_billing: false, note: "Uso financeiro da Vercel não é persistido no backend do Sr. Rotas." },
        supabase: { connected_to_billing: false, note: "Uso financeiro do Supabase não é persistido no backend do Sr. Rotas." },
      },
    },
    v7: {
      canonical_batch: canonicalBatch,
      recent_batches: batches,
    },
    system: {
      database: "ok",
      admin_mode: "bigcorps_control_center",
      active_web_sessions: webSessions.length,
      active_mcp_tokens: mcpTokens.length,
      notifications_24h: notifications.slice(0, 100),
      ai_usage_30d: aiUsage.slice(0, 100),
      sensitive_offer_text_exposed: false,
      coordinates_exposed: false,
      destructive_account_actions_enabled: false,
      support_actions_enabled: true,
      self_service_account_deletion: true,
    },
  };
}

export async function adminDriverDetail(driverId: string) {
  const id = String(driverId || "").trim();
  if (!/^[0-9a-f-]{36}$/i.test(id)) throw new Error("invalid_driver_id");

  const supabase: any = adminSupabase();
  const now = new Date();
  const dayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString();
  const monthAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000).toISOString();

  const [
    driverResult,
    devicesResult,
    journeysResult,
    trialResult,
    subscriptionsResult,
    paymentsResult,
    entitlementsResult,
    walletResult,
    metricsResult,
    energyResult,
    offersResult,
    aiResult,
    notificationResult,
    webSessionsResult,
    mcpResult,
    creditTransactionsResult,
    offersCount,
    offers24hCount,
    outcomesCount,
    completedCount,
    journeysCount,
  ] = await Promise.all([
    supabase.from("drivers")
      .select("id,display_name,email,onboarding_completed,created_at,updated_at,last_login_at")
      .eq("id", id).maybeSingle(),
    supabase.from("driver_devices")
      .select("id,name,revoked,last_seen_at,created_at")
      .eq("driver_id", id).order("created_at", { ascending: false }).limit(100),
    supabase.from("driver_journeys")
      .select("id,device_id,platform,state,started_at,ended_at,end_reason,state_updated_at")
      .eq("driver_id", id).order("started_at", { ascending: false }).limit(80),
    supabase.from("driver_trials")
      .select("first_offer_at,trial_started_at,trial_ends_at,ai_credits_granted,created_at,updated_at")
      .eq("driver_id", id).maybeSingle(),
    supabase.from("subscriptions")
      .select("id,plan_id,status,starts_at,current_period_end,canceled_at,payment_provider,created_at,updated_at")
      .eq("driver_id", id).order("created_at", { ascending: false }).limit(30),
    supabase.from("payments")
      .select("id,subscription_id,kind,provider,amount_cents,status,expires_at,confirmed_at,bank_status,check_attempts,error_code,created_at,updated_at")
      .eq("driver_id", id).order("created_at", { ascending: false }).limit(60),
    supabase.from("entitlements")
      .select("entitlement,active,valid_until,updated_at")
      .eq("driver_id", id).order("updated_at", { ascending: false }).limit(100),
    supabase.from("credit_wallets")
      .select("balance,lifetime_granted,lifetime_spent,updated_at")
      .eq("driver_id", id).maybeSingle(),
    supabase.from("journey_vehicle_metrics")
      .select("journey_id,odometer_start_km,odometer_end_km,distance_km,updated_at")
      .eq("driver_id", id).order("updated_at", { ascending: false }).limit(60),
    supabase.from("journey_energy_entries")
      .select("id,journey_id,energy_type,amount_paid,quantity,unit,fuel_type,recorded_at,updated_at")
      .eq("driver_id", id).order("recorded_at", { ascending: false }).limit(60),
    supabase.from("ride_offers")
      .select("id,journey_id,platform,observed_at,capture_method,fare,pickup_km,trip_km,total_km,pickup_minutes,trip_minutes,total_minutes,per_km,per_hour,per_minute,estimated_cost,estimated_profit,profit_per_hour,profit_percent,verdict,parser_version,confidence,service_type")
      .eq("driver_id", id).order("observed_at", { ascending: false }).limit(80),
    supabase.from("ai_usage_logs")
      .select("id,source,model,status,input_tokens,output_tokens,total_tokens,offer_count,duration_ms,error_code,created_at")
      .eq("driver_id", id).gte("created_at", monthAgo).order("created_at", { ascending: false }).limit(80),
    supabase.from("notification_deliveries")
      .select("id,category,status,error_code,created_at,sent_at")
      .eq("driver_id", id).order("created_at", { ascending: false }).limit(80),
    supabase.from("billing_web_sessions")
      .select("id,expires_at,created_at")
      .eq("driver_id", id).gt("expires_at", now.toISOString()).order("created_at", { ascending: false }).limit(50),
    supabase.from("mcp_access_tokens")
      .select("id,name,token_prefix,revoked,last_used_at,created_at")
      .eq("driver_id", id).order("created_at", { ascending: false }).limit(50),
    supabase.from("credit_transactions")
      .select("id,type,amount,reference_id,created_at")
      .eq("driver_id", id).order("created_at", { ascending: false }).limit(80),
    countRows("ride_offers", (q) => q.eq("driver_id", id)),
    countRows("ride_offers", (q) => q.eq("driver_id", id).gte("observed_at", dayAgo)),
    countRows("ride_outcomes", (q) => q.eq("driver_id", id)),
    countRows("ride_outcomes", (q) => q.eq("driver_id", id).eq("status", "COMPLETED")),
    countRows("driver_journeys", (q) => q.eq("driver_id", id)),
  ]);

  const resultSet = [
    driverResult, devicesResult, journeysResult, trialResult, subscriptionsResult,
    paymentsResult, entitlementsResult, walletResult, metricsResult, energyResult,
    offersResult, aiResult, notificationResult, webSessionsResult, mcpResult, creditTransactionsResult,
  ];
  for (const result of resultSet) {
    if (result.error) throw new Error(result.error.message);
  }
  if (!driverResult.data) throw new Error("driver_not_found");

  const devices = devicesResult.data ?? [];
  const journeys = journeysResult.data ?? [];
  const offers = offersResult.data ?? [];
  const currentJourney = journeys.find((row: any) => !row.ended_at) ?? null;
  const latestDevice = newest(devices.filter((row: any) => !row.revoked), "last_seen_at");
  const latestOffer = newest(offers, "observed_at");
  const online = devices.some((row: any) => !row.revoked && isRecent(row.last_seen_at, 15));

  return {
    generated_at: now.toISOString(),
    driver: driverResult.data,
    health: {
      operational_state: currentJourney ? (online ? "driving" : "journey_stale") : online ? "online" : isRecent(latestOffer?.observed_at, 60) ? "recent" : "offline",
      latest_device_seen_at: latestDevice?.last_seen_at ?? null,
      last_offer_at: latestOffer?.observed_at ?? null,
      current_journey: currentJourney,
      offers_24h: offers24hCount,
      latest_offer: latestOffer ?? null,
    },
    counts: {
      offers: offersCount,
      offers_24h: offers24hCount,
      outcomes: outcomesCount,
      completed: completedCount,
      journeys: journeysCount,
      devices: devices.length,
      active_devices: devices.filter((row: any) => !row.revoked).length,
      active_web_sessions: (webSessionsResult.data ?? []).length,
      active_mcp_tokens: (mcpResult.data ?? []).filter((row: any) => !row.revoked).length,
      ai_calls_30d: (aiResult.data ?? []).length,
      ai_tokens_30d: sum(aiResult.data ?? [], "total_tokens"),
    },
    devices,
    journeys,
    trial: trialResult.data ?? null,
    subscriptions: subscriptionsResult.data ?? [],
    payments: (paymentsResult.data ?? []).map(safePayment),
    entitlements: entitlementsResult.data ?? [],
    wallet: walletResult.data ?? null,
    credit_transactions: creditTransactionsResult.data ?? [],
    vehicle_metrics: metricsResult.data ?? [],
    energy_entries: energyResult.data ?? [],
    offers,
    ai_usage: aiResult.data ?? [],
    notifications: notificationResult.data ?? [],
    web_sessions: webSessionsResult.data ?? [],
    mcp_tokens: mcpResult.data ?? [],
    support_actions: {
      device_revoke: true,
      device_reactivate: true,
      trial_extend_days: [1, 7, 30],
      revoke_web_sessions: true,
      revoke_mcp_tokens: true,
      credit_adjustment: false,
      subscription_override: false,
      account_delete: false,
      note: "Ajustes de saldo/plano não são feitos por update simples porque exigem consistência financeira e trilha transacional.",
    },
    privacy: {
      raw_offer_text_returned: false,
      coordinates_returned: false,
      payment_secrets_returned: false,
      destructive_account_actions_enabled: false,
      account_deletion_path: "/excluir-conta",
    },
  };
}
