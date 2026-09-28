import { adminSupabase } from "./supabase";

export type AccessState =
  | "TRIAL_PENDING"
  | "TRIAL_ACTIVE"
  | "PAID_ACTIVE"
  | "EXPIRED_READ_ONLY"
  | "BLOCKED";

export type AccessPermissions = {
  can_operate: boolean;
  can_history: boolean;
  can_analytics: boolean;
  can_ai: boolean;
  can_mcp: boolean;
  can_billing: boolean;
  can_profile: boolean;
};

export type AccessCapability = keyof AccessPermissions;

export type DriverAccess = {
  state: AccessState;
  reason: string | null;
  enforcement_mode: "observe" | "enforce";
  require_device_identity: boolean;
  device_identity_bound: boolean;
  active_identity_devices: number;
  max_active_devices: number;
  policy: AccessPermissions;
  effective: AccessPermissions;
};

const deny: AccessPermissions = {
  can_operate: false,
  can_history: false,
  can_analytics: false,
  can_ai: false,
  can_mcp: false,
  can_billing: true,
  can_profile: true,
};

function bool(value: unknown) {
  return value === true;
}

function permissions(value: any): AccessPermissions {
  return {
    can_operate: bool(value?.can_operate),
    can_history: bool(value?.can_history),
    can_analytics: bool(value?.can_analytics),
    can_ai: bool(value?.can_ai),
    can_mcp: bool(value?.can_mcp),
    can_billing: value?.can_billing !== false,
    can_profile: value?.can_profile !== false,
  };
}

export async function resolveDriverAccess(
  driverId: string,
  deviceId?: string | null,
): Promise<DriverAccess> {
  const { data, error } = await adminSupabase().rpc("resolve_driver_access", {
    p_driver_id: driverId,
    p_device_id: deviceId || null,
  });
  if (error) throw new Error(`access_resolver_failed:${error.message}`);

  const row = (data ?? {}) as any;
  const state = String(row.state || "BLOCKED") as AccessState;
  const enforcement =
    row.enforcement_mode === "enforce" ? "enforce" : "observe";

  return {
    state,
    reason: row.reason ? String(row.reason) : null,
    enforcement_mode: enforcement,
    require_device_identity: bool(row.require_device_identity),
    device_identity_bound: bool(row.device_identity_bound),
    active_identity_devices: Math.max(
      0,
      Number(row.active_identity_devices || 0) || 0,
    ),
    max_active_devices: Math.max(
      1,
      Number(row.max_active_devices || 2) || 2,
    ),
    policy: row.policy ? permissions(row.policy) : { ...deny },
    effective: row.effective ? permissions(row.effective) : { ...deny },
  };
}

/**
 * Guarda única de capability para endpoints 1.0-B.
 *
 * Em `observe`, o resolver devolve `effective=true` para capacidades comerciais,
 * então adicionar esta guarda NÃO muda a experiência atual.
 *
 * Quando o Supabase for promovido para `enforce`, os mesmos endpoints passam a
 * obedecer ao contrato comercial sem outra reconstrução.
 */
export function accessDeniedResponse(
  access: DriverAccess,
  capability: AccessCapability,
): Response | null {
  if (access.effective[capability]) return null;

  const status = access.state === "BLOCKED" ? 403 : 402;
  return Response.json(
    {
      error: "access_restricted",
      state: access.state,
      reason: access.reason,
      capability,
      enforcement_mode: access.enforcement_mode,
      max_active_devices: access.max_active_devices,
    },
    {
      status,
      headers: { "Cache-Control": "no-store" },
    },
  );
}
