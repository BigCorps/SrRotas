import { createClient } from "@supabase/supabase-js";
import { serverEnv } from "./env";

let cached: ReturnType<typeof createClient> | null = null;

export function adminSupabase() {
  if (cached) return cached;
  const env = serverEnv();
  cached = createClient(env.supabaseUrl, env.supabaseServiceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  return cached;
}
