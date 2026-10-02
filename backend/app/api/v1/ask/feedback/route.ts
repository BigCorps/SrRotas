import { authenticateBillingActor } from "@/src/billing-auth";
import { adminSupabase } from "@/src/supabase";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const body = await request.json().catch(() => ({}));
  const useful = body?.useful === true ? true : body?.useful === false ? false : null;
  if (useful === null) return Response.json({ error: "feedback_invalid" }, { status: 400 });

  const inserted = await adminSupabase().from("beta_feedback").insert({
    driver_id: auth.driverId,
    kind: "feedback",
    category: "IA",
    severity: "Sugestão",
    message: useful ? "Pergunte: resposta útil" : "Pergunte: resposta não útil",
    metadata: {
      source: "web_intelligence_v1",
      intent: String(body?.intent ?? "").slice(0, 80) || null,
      semantic: String(body?.semantic ?? "").slice(0, 80) || null,
    },
  }).select("id,created_at").single();
  if (inserted.error) return Response.json({ error: inserted.error.message }, { status: 400 });
  return Response.json({ ok: true, feedback: inserted.data }, { status: 201 });
}
