function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Missing environment variable: ${name}`);
  return value;
}
function bool(name: string, fallback = false) {
  const value = process.env[name]?.trim().toLowerCase();
  if (!value) return fallback;
  return ["1","true","yes","on"].includes(value);
}
export function serverEnv() {
  return {
    supabaseUrl: required("SUPABASE_URL"),
    supabaseServiceRoleKey: required("SUPABASE_SERVICE_ROLE_KEY"),
    pairingCode: required("PAIRING_CODE"),
    openAiApiKey: process.env.OPENAI_API_KEY?.trim() || "",
    openAiModel: process.env.OPENAI_MODEL?.trim() || "gpt-5.6",
    timezone: process.env.DEFAULT_TIMEZONE?.trim() || "America/Sao_Paulo",
    billingEnforcement: bool("BILLING_ENFORCEMENT", false),
  };
}
