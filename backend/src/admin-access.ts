export const ADMIN_OPS_EMAILS = new Set([
  "contato@bigcorps.com.br",
  "jadielalmeida@gmail.com",
]);

export function normalizeAdminEmail(value: unknown) {
  return String(value ?? "").trim().toLowerCase().slice(0, 180);
}

export function canAccessAdminOps(email: unknown) {
  return ADMIN_OPS_EMAILS.has(normalizeAdminEmail(email));
}
