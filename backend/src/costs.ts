import { adminSupabase } from "./supabase";
import { ensurePreferences } from "./preferences";

export const COST_MODEL_VERSION = "sr-cost-v0.18.0";
export const DEFAULT_ESTIMATED_MONTHLY_KM = 3000;

const VEHICLES = new Set([
  "combustion",
  "electric",
  "hybrid",
  "plugin_hybrid",
]);

const OWNERSHIPS = new Set([
  "paid",
  "financed",
  "rented",
  "subscription",
]);

const ENERGIES = new Set([
  "gasoline",
  "ethanol",
  "gnv",
  "electricity",
  "combination",
]);

const LIQUID_FUELS = new Set([
  "gasoline",
  "ethanol",
  "gnv",
]);

type CostProfile = {
  vehicle_type: string;
  ownership_type: string;
  energy_mode: string;
  combination_liquid_fuel: string;

  fuel_price_per_unit: number | null;
  fuel_km_per_unit: number | null;
  electricity_price_per_kwh: number | null;
  electric_kwh_per_100_km: number | null;

  ownership_monthly: number;
  insurance_monthly: number;
  maintenance_monthly: number;
  tires_monthly: number;
  other_monthly: number;

  monthly_work_km: number | null;
  monthly_work_km_source: "userProvided" | "estimated";
  estimated_monthly_work_km: number;
  average_journey_hours: number | null;
  monthly_work_hours: number | null;

  client_updated_at: string;
};

export type CostCalculation = {
  version: string;
  liquid_cost_per_km: number;
  electric_cost_per_km: number;
  variable_cost_per_km: number;
  fixed_monthly_total: number;
  allocation_km_per_month: number;
  allocation_source: "userProvided" | "estimated";
  fixed_cost_per_km: number;
  effective_cost_per_km: number;
  completeness: "complete" | "partial";
  cost_source: string;
  missing_inputs: string[];
};

function finiteOrNull(
  value: unknown,
  min = 0,
  max = 1_000_000,
) {
  if (
    value === null ||
    value === undefined ||
    value === ""
  ) {
    return null;
  }

  const n = Number(value);
  if (!Number.isFinite(n)) return null;
  return Math.max(min, Math.min(max, n));
}

function finiteOr(
  value: unknown,
  fallback: number,
  min = 0,
  max = 1_000_000,
) {
  return (
    finiteOrNull(
      value,
      min,
      max,
    ) ?? fallback
  );
}

function validIso(
  value: unknown,
) {
  const candidate = String(value ?? "").trim();
  if (!candidate) return new Date().toISOString();

  const parsed = new Date(candidate);
  return Number.isNaN(parsed.getTime())
    ? new Date().toISOString()
    : parsed.toISOString();
}

function oneOf(
  value: unknown,
  allowed: Set<string>,
  fallback: string,
) {
  const v = String(value ?? "");
  return allowed.has(v)
    ? v
    : fallback;
}

function r2(
  value: number,
) {
  return Math.round(value * 100) / 100;
}

function r4(
  value: number,
) {
  return Math.round(value * 10_000) / 10_000;
}

export function normalizeCostProfile(
  input: Record<string, unknown>,
): CostProfile {
  const monthlyWorkKm =
    finiteOrNull(
      input.monthly_work_km,
      0,
      200_000,
    );

  const requestedSource =
    String(
      input.monthly_work_km_source ??
        "estimated",
    );

  const monthlySource:
    "userProvided" | "estimated" =
    requestedSource === "userProvided" &&
    monthlyWorkKm !== null &&
    monthlyWorkKm > 0
      ? "userProvided"
      : "estimated";

  return {
    vehicle_type: oneOf(
      input.vehicle_type,
      VEHICLES,
      "combustion",
    ),
    ownership_type: oneOf(
      input.ownership_type,
      OWNERSHIPS,
      "paid",
    ),
    energy_mode: oneOf(
      input.energy_mode,
      ENERGIES,
      "gasoline",
    ),
    combination_liquid_fuel: oneOf(
      input.combination_liquid_fuel,
      LIQUID_FUELS,
      "gasoline",
    ),

    fuel_price_per_unit:
      finiteOrNull(
        input.fuel_price_per_unit,
        0,
        1000,
      ),
    fuel_km_per_unit:
      finiteOrNull(
        input.fuel_km_per_unit,
        0,
        1000,
      ),
    electricity_price_per_kwh:
      finiteOrNull(
        input.electricity_price_per_kwh,
        0,
        1000,
      ),
    electric_kwh_per_100_km:
      finiteOrNull(
        input.electric_kwh_per_100_km,
        0,
        1000,
      ),

    ownership_monthly:
      finiteOr(
        input.ownership_monthly,
        0,
        0,
        100_000,
      ),
    insurance_monthly:
      finiteOr(
        input.insurance_monthly,
        0,
        0,
        100_000,
      ),
    maintenance_monthly:
      finiteOr(
        input.maintenance_monthly,
        0,
        0,
        100_000,
      ),
    tires_monthly:
      finiteOr(
        input.tires_monthly,
        0,
        0,
        100_000,
      ),
    other_monthly:
      finiteOr(
        input.other_monthly,
        0,
        0,
        100_000,
      ),

    monthly_work_km:
      monthlySource === "userProvided"
        ? monthlyWorkKm
        : null,
    monthly_work_km_source:
      monthlySource,
    estimated_monthly_work_km:
      finiteOr(
        input.estimated_monthly_work_km,
        DEFAULT_ESTIMATED_MONTHLY_KM,
        1,
        200_000,
      ),
    average_journey_hours:
      finiteOrNull(
        input.average_journey_hours,
        0,
        24,
      ),
    monthly_work_hours:
      finiteOrNull(
        input.monthly_work_hours,
        0,
        1000,
      ),

    client_updated_at:
      validIso(
        input.client_updated_at,
      ),
  };
}

export function calculateCostProfile(
  profile: CostProfile,
): CostCalculation {
  const usesLiquid = new Set([
    "gasoline",
    "ethanol",
    "gnv",
    "combination",
  ]).has(profile.energy_mode);

  const usesElectric = new Set([
    "electricity",
    "combination",
  ]).has(profile.energy_mode);

  const liquidPrice =
    profile.fuel_price_per_unit &&
    profile.fuel_price_per_unit > 0
      ? profile.fuel_price_per_unit
      : null;

  const liquidConsumption =
    profile.fuel_km_per_unit &&
    profile.fuel_km_per_unit > 0
      ? profile.fuel_km_per_unit
      : null;

  const electricPrice =
    profile.electricity_price_per_kwh &&
    profile.electricity_price_per_kwh > 0
      ? profile.electricity_price_per_kwh
      : null;

  const electricConsumption =
    profile.electric_kwh_per_100_km &&
    profile.electric_kwh_per_100_km > 0
      ? profile.electric_kwh_per_100_km
      : null;

  const liquidCost =
    usesLiquid &&
    liquidPrice !== null &&
    liquidConsumption !== null
      ? liquidPrice / liquidConsumption
      : 0;

  const electricCost =
    usesElectric &&
    electricPrice !== null &&
    electricConsumption !== null
      ? (
          electricPrice *
          electricConsumption
        ) / 100
      : 0;

  const variableCost =
    liquidCost +
    electricCost;

  const fixedMonthly =
    profile.ownership_monthly +
    profile.insurance_monthly +
    profile.maintenance_monthly +
    profile.tires_monthly +
    profile.other_monthly;

  const allocationSource:
    "userProvided" | "estimated" =
    profile.monthly_work_km_source ===
      "userProvided" &&
    profile.monthly_work_km !== null &&
    profile.monthly_work_km > 0
      ? "userProvided"
      : "estimated";

  const allocationKm =
    allocationSource === "userProvided"
      ? profile.monthly_work_km!
      : (
          profile.estimated_monthly_work_km >
          0
            ? profile.estimated_monthly_work_km
            : DEFAULT_ESTIMATED_MONTHLY_KM
        );

  const fixedPerKm =
    allocationKm > 0
      ? fixedMonthly / allocationKm
      : 0;

  const missing: string[] = [];

  if (
    usesLiquid &&
    liquidPrice === null
  ) {
    missing.push(
      "fuel_price_per_unit",
    );
  }

  if (
    usesLiquid &&
    liquidConsumption === null
  ) {
    missing.push(
      "fuel_km_per_unit",
    );
  }

  if (
    usesElectric &&
    electricPrice === null
  ) {
    missing.push(
      "electricity_price_per_kwh",
    );
  }

  if (
    usesElectric &&
    electricConsumption === null
  ) {
    missing.push(
      "electric_kwh_per_100_km",
    );
  }

  const completeness:
    "complete" | "partial" =
    missing.length
      ? "partial"
      : "complete";

  const costSource =
    `profile_${
      allocationSource === "userProvided"
        ? "user_allocation"
        : "estimated_allocation"
    }${
      completeness === "partial"
        ? "_partial"
        : ""
    }`;

  return {
    version:
      COST_MODEL_VERSION,
    liquid_cost_per_km:
      r4(liquidCost),
    electric_cost_per_km:
      r4(electricCost),
    variable_cost_per_km:
      r4(variableCost),
    fixed_monthly_total:
      r2(fixedMonthly),
    allocation_km_per_month:
      r2(allocationKm),
    allocation_source:
      allocationSource,
    fixed_cost_per_km:
      r4(fixedPerKm),
    effective_cost_per_km:
      r4(
        variableCost +
          fixedPerKm,
      ),
    completeness,
    cost_source:
      costSource,
    missing_inputs:
      missing,
  };
}

function rowToProfile(
  row: Record<string, unknown>,
) {
  return normalizeCostProfile(
    row,
  );
}

export async function getCostProfile(
  driverId: string,
) {
  const { data, error } =
    await adminSupabase()
      .from("driver_cost_profiles")
      .select(
        [
          "vehicle_type",
          "ownership_type",
          "energy_mode",
          "combination_liquid_fuel",
          "fuel_price_per_unit",
          "fuel_km_per_unit",
          "electricity_price_per_kwh",
          "electric_kwh_per_100_km",
          "ownership_monthly",
          "insurance_monthly",
          "maintenance_monthly",
          "tires_monthly",
          "other_monthly",
          "monthly_work_km",
          "monthly_work_km_source",
          "estimated_monthly_work_km",
          "average_journey_hours",
          "monthly_work_hours",
          "client_updated_at",
          "updated_at",
        ].join(","),
      )
      .eq(
        "driver_id",
        driverId,
      )
      .maybeSingle();

  if (error) {
    throw new Error(
      error.message,
    );
  }

  if (!data) {
    const prefs =
      await ensurePreferences(
        driverId,
      );

    return {
      configured: false,
      profile: null,
      calculation: null,
      legacy_cost_per_km:
        Number(
          prefs.cost_per_km,
        ),
    };
  }

  const profile =
    rowToProfile(
      data as unknown as
        Record<string, unknown>,
    );

  const calculation =
    calculateCostProfile(
      profile,
    );

  return {
    configured: true,
    profile,
    calculation,
  };
}

export async function saveCostProfile(
  driverId: string,
  input: Record<string, unknown>,
) {
  const profile =
    normalizeCostProfile(
      input,
    );

  const calculation =
    calculateCostProfile(
      profile,
    );

  if (
    calculation.effective_cost_per_km <=
    0
  ) {
    throw new Error(
      "cost_profile_without_usable_cost",
    );
  }

  const row = {
    driver_id:
      driverId,
    ...profile,
    effective_cost_per_km:
      calculation.effective_cost_per_km,
    variable_cost_per_km:
      calculation.variable_cost_per_km,
    fixed_cost_per_km:
      calculation.fixed_cost_per_km,
    fixed_monthly_total:
      calculation.fixed_monthly_total,
    allocation_km_per_month:
      calculation.allocation_km_per_month,
    calculation_version:
      COST_MODEL_VERSION,
    calculation_snapshot:
      calculation,
    updated_at:
      new Date().toISOString(),
  };

  const saved =
    await adminSupabase()
      .from("driver_cost_profiles")
      .upsert(
        row,
        {
          onConflict:
            "driver_id",
        },
      )
      .select(
        [
          "vehicle_type",
          "ownership_type",
          "energy_mode",
          "combination_liquid_fuel",
          "fuel_price_per_unit",
          "fuel_km_per_unit",
          "electricity_price_per_kwh",
          "electric_kwh_per_100_km",
          "ownership_monthly",
          "insurance_monthly",
          "maintenance_monthly",
          "tires_monthly",
          "other_monthly",
          "monthly_work_km",
          "monthly_work_km_source",
          "estimated_monthly_work_km",
          "average_journey_hours",
          "monthly_work_hours",
          "client_updated_at",
          "updated_at",
        ].join(","),
      )
      .single();

  if (saved.error) {
    throw new Error(
      saved.error.message,
    );
  }

  await ensurePreferences(
    driverId,
  );

  const prefsUpdate =
    await adminSupabase()
      .from("driver_preferences")
      .update({
        cost_per_km:
          calculation
            .effective_cost_per_km,
        updated_at:
          new Date().toISOString(),
      })
      .eq(
        "driver_id",
        driverId,
      );

  if (prefsUpdate.error) {
    throw new Error(
      prefsUpdate.error.message,
    );
  }

  const savedProfile =
    rowToProfile(
      saved.data as unknown as
        Record<string, unknown>,
    );

  const revision =
    await adminSupabase()
      .from("driver_cost_profile_revisions")
      .upsert(
        {
          driver_id: driverId,
          client_updated_at:
            savedProfile.client_updated_at,
          profile_snapshot:
            savedProfile,
          calculation_snapshot:
            calculateCostProfile(
              savedProfile,
            ),
          calculation_version:
            COST_MODEL_VERSION,
        },
        {
          onConflict:
            "driver_id,client_updated_at",
        },
      );

  if (revision.error) {
    throw new Error(
      revision.error.message,
    );
  }

  return {
    configured: true,
    profile: savedProfile,
    calculation:
      calculateCostProfile(
        savedProfile,
      ),
  };
}
