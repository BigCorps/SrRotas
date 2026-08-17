import { createClient } from "@supabase/supabase-js";
import { adminSupabase } from "./supabase";
import { serverEnv } from "./env";
import { newToken, sha256 } from "./security";
import { ensurePreferences } from "./preferences";

export function normalizeEmail(value: unknown) {
  return String(value ?? "").trim().toLowerCase().slice(0, 180);
}

export function normalizeDisplayName(value: unknown) {
  return String(value ?? "").trim().slice(0, 80) || "Motorista";
}

export function normalizeDeviceName(value: unknown) {
  return String(value ?? "").trim().slice(0, 120) || "Android";
}

export async function createDeviceSession(driverId: string, deviceName: string) {
  const supabase = adminSupabase();
  const token = newToken();
  const { data, error } = await supabase
    .from("driver_devices")
    .insert({ driver_id: driverId, name: normalizeDeviceName(deviceName), token_hash: sha256(token) })
    .select("id")
    .single();
  if (error) throw new Error(error.message);
  return { deviceId: String(data.id), token };
}

export async function driverForAuthUser(authUserId: string) {
  const supabase = adminSupabase();
  const { data, error } = await supabase
    .from("drivers")
    .select("id, display_name, email, onboarding_completed")
    .eq("auth_user_id", authUserId)
    .maybeSingle();
  if (error) throw new Error(error.message);
  return data;
}

export async function createDriverForAuthUser(authUserId: string, email: string, displayName: string) {
  const supabase = adminSupabase();
  const { data, error } = await supabase
    .from("drivers")
    .insert({
      auth_user_id: authUserId,
      email: normalizeEmail(email),
      display_name: normalizeDisplayName(displayName),
      last_login_at: new Date().toISOString(),
    })
    .select("id, display_name, email, onboarding_completed")
    .single();
  if (error) throw new Error(error.message);
  await ensurePreferences(String(data.id));
  return data;
}

export async function passwordAuth(email: string, password: string) {
  const env = serverEnv();
  const client = createClient(env.supabaseUrl, env.supabaseServiceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
  });
  const { data, error } = await client.auth.signInWithPassword({ email, password });
  if (error || !data.user) throw new Error("invalid_credentials");
  return data.user;
}
