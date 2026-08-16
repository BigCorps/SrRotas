import { createClient } from "@supabase/supabase-js";
import { serverEnv } from "./env";

// Alpha: o schema e criado pelas migrations do projeto, mas ainda nao geramos
// database.types.ts pelo Supabase CLI. Tipar o cache com
// ReturnType<typeof createClient> faz o TypeScript inferir as tabelas como
// `never` nas versoes atuais do supabase-js, quebrando inserts/selects no build.
// Mantemos o cliente administrativo sem schema estatico por enquanto e geramos
// os tipos do banco antes da fase beta.
let cached: any = null;

export function adminSupabase(): any {
  if (cached) return cached;
  const env = serverEnv();
  cached = createClient(env.supabaseUrl, env.supabaseServiceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  return cached;
}
