function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Missing environment variable: ${name}`);
  return value;
}

export function serverEnv() {
  return {
    supabaseUrl: required("SUPABASE_URL"),
    supabaseServiceRoleKey: required("SUPABASE_SERVICE_ROLE_KEY"),
    pairingCode: required("PAIRING_CODE"),
    openAiApiKey: process.env.OPENAI_API_KEY?.trim() || "",
    openAiModel: process.env.OPENAI_MODEL?.trim() || "gpt-5.6",
    timezone: process.env.DEFAULT_TIMEZONE?.trim() || "America/Sao_Paulo",
  };
}
