import { adminSupabase } from "./supabase";

const OUTCOME_STATUSES = ["OFFERED", "DOING_RIDE", "COMPLETED", "NOT_COMPLETED", "CANCELLED"] as const;

async function countRows(
  table: string,
  apply?: (query: any) => any,
) {
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
    const key = String(row.driver_id || "");
    if (!key) continue;
    const current = map.get(key) ?? [];
    current.push(row);
    map.set(key, current);
  }
  return map;
}

function latestByDriver(rows: any[]) {
  const map = new Map<string, any>();
  for (const row of rows) {
    const key = String(row.driver_id || "");
    if (key && !map.has(key)) map.set(key, row);
  }
  return map;
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

export async function adminOpsOverview() {
  const supabase: any = adminSupabase();
  const now = new Date();
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
    countRows("billing_web_sessions", (q) => q.gt("expires_at", now.toISOString())),
    countRows("mcp_access_tokens", (q) => q.eq("revoked", false)),
    countRows("journey_vehicle_metrics"),
    countRows("journey_energy_entries"),
    countRows("ai_usage_logs", (q) => q.gte("created_at", monthAgo)),
    countRows("notification_deliveries", (q) => q.gte("created_at", dayAgo)),
    supabase
      .from("drivers")
      .select("id,display_name,email,onboarding_completed,created_at,updated_at,last_login_at")
      .order("created_at", { ascending: false })
      .limit(100),
    supabase
      .from("driver_devices")
      .select("id,driver_id,name,revoked,last_seen_at,created_at")
      .order("created_at", { ascending: false })
      .limit(500),
    supabase
      .from("driver_journeys")
      .select("id,driver_id,device_id,platform,state,started_at,ended_at,end_reason,state_updated_at")
      .order("started_at", { ascending: false })
      .limit(80),
    supabase
      .from("driver_trials")
      .select("driver_id,first_offer_at,trial_started_at,trial_ends_at,ai_credits_granted,updated_at")
      .order("trial_started_at", { ascending: false })
      .limit(100),
    supabase
      .from("subscriptions")
      .select("id,driver_id,plan_id,status,starts_at,current_period_end,canceled_at,payment_provider,created_at,updated_at")
      .order("created_at", { ascending: false })
      .limit(100),
    supabase
      .from("payments")
      .select("id,driver_id,subscription_id,kind,provider,amount_cents,status,expires_at,confirmed_at,bank_status,check_attempts,error_code,created_at,updated_at")
      .order("created_at", { ascending: false })
      .limit(100),
    supabase
      .from("entitlements")
      .select("driver_id,entitlement,active,valid_until,updated_at")
      .order("updated_at", { ascending: false })
      .limit(300),
    supabase
      .from("credit_wallets")
      .select("driver_id,balance,lifetime_granted,lifetime_spent,updated_at")
      .order("updated_at", { ascending: false })
      .limit(100),
    supabase
      .from("historical_import_batches")
      .select("id,status,source_name,original_filename,schema_version,extractor_version,received_count,valid_count,partial_count,invalid_count,duplicate_count,demand_temporal_ready_count,route_flow_ready_count,financial_ready_count,fully_ready_count,created_at,finalized_at,created_by_driver_id,processing_manifest")
      .order("created_at", { ascending: false })
      .limit(20),
  ]);

  const resultSet = [
    driverResult, deviceResult, journeyResult, trialResult, subscriptionResult,
    paymentResult, entitlementResult, walletResult, batchResult,
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

  const devicesByDriver = byDriver(devices);
  const journeysByDriver = byDriver(journeys);
  const trialByDriver = latestByDriver(trials);
  const subscriptionByDriver = latestByDriver(subscriptions);
  const walletByDriver = latestByDriver(wallets);
  const entitlementsByDriver = byDriver(entitlements);

  const driverRows = drivers.map((driver: any) => {
    const driverId = String(driver.id);
    const deviceRows = devicesByDriver.get(driverId) ?? [];
    const journeyRows = journeysByDriver.get(driverId) ?? [];
    const currentJourney = journeyRows.find((row: any) => !row.ended_at) ?? null;
    const trial = trialByDriver.get(driverId) ?? null;
    const subscription = subscriptionByDriver.get(driverId) ?? null;
    const wallet = walletByDriver.get(driverId) ?? null;
    const driverEntitlements = entitlementsByDriver.get(driverId) ?? [];

    return {
      id: driver.id,
      display_name: driver.display_name,
      email: driver.email,
      onboarding_completed: Boolean(driver.onboarding_completed),
      created_at: driver.created_at,
      last_login_at: driver.last_login_at,
      devices_total: deviceRows.length,
      devices_active: deviceRows.filter((row: any) => !row.revoked).length,
      latest_device_seen_at: deviceRows
        .map((row: any) => row.last_seen_at)
        .filter(Boolean)
        .sort()
        .at(-1) ?? null,
      recent_journeys_loaded: journeyRows.length,
      current_journey: currentJourney
        ? {
            id: currentJourney.id,
            state: currentJourney.state,
            platform: currentJourney.platform,
            started_at: currentJourney.started_at,
          }
        : null,
      trial: trial
        ? {
            trial_started_at: (trial as any).trial_started_at,
            trial_ends_at: (trial as any).trial_ends_at,
            ai_credits_granted: Number((trial as any).ai_credits_granted || 0),
          }
        : null,
      subscription: subscription
        ? {
            id: (subscription as any).id,
            plan_id: (subscription as any).plan_id,
            status: (subscription as any).status,
            current_period_end: (subscription as any).current_period_end,
          }
        : null,
      wallet: wallet
        ? {
            balance: Number((wallet as any).balance || 0),
            lifetime_granted: Number((wallet as any).lifetime_granted || 0),
            lifetime_spent: Number((wallet as any).lifetime_spent || 0),
          }
        : null,
      active_entitlements: driverEntitlements
        .filter((row: any) => row.active)
        .map((row: any) => row.entitlement),
    };
  });

  const canonicalBatch = batches.find((batch: any) => batch.canonical_for_intelligence && batch.status === "ready") ?? null;
  const paymentTotalConfirmedCents = payments
    .filter((row: any) => row.status === "confirmed" || row.confirmed_at)
    .reduce((sum: number, row: any) => sum + Number(row.amount_cents || 0), 0);

  return {
    generated_at: now.toISOString(),
    deployment: {
      environment: process.env.VERCEL_ENV || null,
      commit_sha: process.env.VERCEL_GIT_COMMIT_SHA || null,
    },
    counts: {
      drivers: driversCount,
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
      notifications_24h: notifications24h,
    },
    outcomes: outcomesByStatus,
    drivers: driverRows,
    recent_journeys: journeys.slice(0, 30),
    finance: {
      subscriptions,
      payments,
      entitlements,
      confirmed_revenue_cents: paymentTotalConfirmedCents,
    },
    v7: {
      canonical_batch: canonicalBatch,
      recent_batches: batches,
    },
    system: {
      database: "ok",
      admin_mode: "owner_read_only",
      sensitive_offer_text_exposed: false,
      coordinates_exposed: false,
      self_service_account_deletion: true,
    },
  };
}

export async function adminDriverDetail(driverId: string) {
  const id = String(driverId || "").trim();
  if (!/^[0-9a-f-]{36}$/i.test(id)) throw new Error("invalid_driver_id");

  const supabase: any = adminSupabase();

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
    offersCount,
    outcomesCount,
    completedCount,
    journeysCount,
  ] = await Promise.all([
    supabase
      .from("drivers")
      .select("id,display_name,email,onboarding_completed,created_at,updated_at,last_login_at")
      .eq("id", id)
      .maybeSingle(),
    supabase
      .from("driver_devices")
      .select("id,name,revoked,last_seen_at,created_at")
      .eq("driver_id", id)
      .order("created_at", { ascending: false })
      .limit(100),
    supabase
      .from("driver_journeys")
      .select("id,device_id,platform,state,started_at,ended_at,end_reason,state_updated_at")
      .eq("driver_id", id)
      .order("started_at", { ascending: false })
      .limit(40),
    supabase
      .from("driver_trials")
      .select("first_offer_at,trial_started_at,trial_ends_at,ai_credits_granted,created_at,updated_at")
      .eq("driver_id", id)
      .maybeSingle(),
    supabase
      .from("subscriptions")
      .select("id,plan_id,status,starts_at,current_period_end,canceled_at,payment_provider,created_at,updated_at")
      .eq("driver_id", id)
      .order("created_at", { ascending: false })
      .limit(20),
    supabase
      .from("payments")
      .select("id,subscription_id,kind,provider,amount_cents,status,expires_at,confirmed_at,bank_status,check_attempts,error_code,created_at,updated_at")
      .eq("driver_id", id)
      .order("created_at", { ascending: false })
      .limit(30),
    supabase
      .from("entitlements")
      .select("entitlement,active,valid_until,updated_at")
      .eq("driver_id", id)
      .order("updated_at", { ascending: false })
      .limit(100),
    supabase
      .from("credit_wallets")
      .select("balance,lifetime_granted,lifetime_spent,updated_at")
      .eq("driver_id", id)
      .maybeSingle(),
    supabase
      .from("journey_vehicle_metrics")
      .select("journey_id,odometer_start_km,odometer_end_km,distance_km,updated_at")
      .eq("driver_id", id)
      .order("updated_at", { ascending: false })
      .limit(30),
    supabase
      .from("journey_energy_entries")
      .select("id,journey_id,energy_type,amount_paid,quantity,unit,fuel_type,recorded_at,updated_at")
      .eq("driver_id", id)
      .order("recorded_at", { ascending: false })
      .limit(30),
    countRows("ride_offers", (q) => q.eq("driver_id", id)),
    countRows("ride_outcomes", (q) => q.eq("driver_id", id)),
    countRows("ride_outcomes", (q) => q.eq("driver_id", id).eq("status", "COMPLETED")),
    countRows("driver_journeys", (q) => q.eq("driver_id", id)),
  ]);

  const resultSet = [
    driverResult, devicesResult, journeysResult, trialResult, subscriptionsResult,
    paymentsResult, entitlementsResult, walletResult, metricsResult, energyResult,
  ];
  for (const result of resultSet) {
    if (result.error) throw new Error(result.error.message);
  }
  if (!driverResult.data) throw new Error("driver_not_found");

  return {
    generated_at: new Date().toISOString(),
    driver: driverResult.data,
    counts: {
      offers: offersCount,
      outcomes: outcomesCount,
      completed: completedCount,
      journeys: journeysCount,
      devices: (devicesResult.data ?? []).length,
      active_devices: (devicesResult.data ?? []).filter((row: any) => !row.revoked).length,
    },
    devices: devicesResult.data ?? [],
    journeys: journeysResult.data ?? [],
    trial: trialResult.data ?? null,
    subscriptions: subscriptionsResult.data ?? [],
    payments: paymentsResult.data ?? [],
    entitlements: entitlementsResult.data ?? [],
    wallet: walletResult.data ?? null,
    vehicle_metrics: metricsResult.data ?? [],
    energy_entries: energyResult.data ?? [],
    privacy: {
      raw_offer_text_returned: false,
      coordinates_returned: false,
      destructive_admin_actions_enabled: false,
      account_deletion_path: "/excluir-conta",
    },
  };
}
