import { IMPORT_OWNER_EMAIL, requireImportOwner } from "@/src/admin-imports";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function normalizeEmail(value: unknown) {
  return String(value ?? "").trim().toLowerCase().slice(0, 180);
}

export async function GET(request: Request) {
  const checked = await requireImportOwner(request);
  if (checked.response) return checked.response;

  const { data, error } = await adminSupabase()
    .from("historical_import_access")
    .select("id,email,enabled,created_at,updated_at")
    .order("email", { ascending: true });
  if (error) return Response.json({ error: error.message }, { status: 500 });

  return Response.json({
    owner_email: IMPORT_OWNER_EMAIL,
    access: data ?? [],
  });
}

export async function POST(request: Request) {
  const checked = await requireImportOwner(request);
  if (checked.response) return checked.response;

  const body = await request.json().catch(() => ({}));
  const email = normalizeEmail(body?.email);
  if (!email || !email.includes("@")) return Response.json({ error: "invalid_email" }, { status: 400 });

  const { data, error } = await adminSupabase()
    .from("historical_import_access")
    .upsert({
      email,
      enabled: true,
      added_by_driver_id: checked.actor!.driverId,
      updated_at: new Date().toISOString(),
    }, { onConflict: "email" })
    .select("id,email,enabled,created_at,updated_at")
    .single();

  if (error) return Response.json({ error: error.message }, { status: 500 });
  return Response.json({ ok: true, access: data });
}

export async function DELETE(request: Request) {
  const checked = await requireImportOwner(request);
  if (checked.response) return checked.response;

  const body = await request.json().catch(() => ({}));
  const email = normalizeEmail(body?.email);
  if (!email || !email.includes("@")) return Response.json({ error: "invalid_email" }, { status: 400 });
  if (email === IMPORT_OWNER_EMAIL) return Response.json({ error: "owner_cannot_be_removed" }, { status: 400 });

  const { error } = await adminSupabase()
    .from("historical_import_access")
    .update({ enabled: false, updated_at: new Date().toISOString() })
    .eq("email", email);
  if (error) return Response.json({ error: error.message }, { status: 500 });
  return Response.json({ ok: true });
}
