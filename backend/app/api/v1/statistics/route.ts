import { accessDeniedResponse } from "@/src/access";
import { authenticateBillingActor } from "@/src/billing-auth";
import { askNaturalQuestion } from "@/src/intelligence/engine";

export const runtime = "nodejs";
export const maxDuration = 60;

const prompts: Record<string, string[]> = {
  resumo: ["Me dê um resumo do período", "Qual meu lucro no período?"],
  produtividade: ["Quanto rodei no período?", "Qual foi minha melhor jornada?"],
  horarios: ["Quais meus melhores horários?"],
  regioes: ["Qual região pagou melhor?"],
  corridas: ["Quantas corridas concluídas fiz?", "Quanto faturei?"],
  oportunidades: ["Quanto perdi em oportunidades?", "Como está a qualidade do mercado?"],
  custos: ["Quanto gastei?", "Qual meu lucro?"],
};

export async function GET(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const denied = accessDeniedResponse(auth.access, "can_analytics");
  if (denied) return denied;

  const url = new URL(request.url);
  const section = (url.searchParams.get("section") || "resumo").toLowerCase();
  if (!prompts[section]) return Response.json({ error: "statistics_section_invalid" }, { status: 400 });

  const preset = url.searchParams.get("preset") || "7";
  const from = url.searchParams.get("from") || undefined;
  const to = url.searchParams.get("to") || undefined;
  const days = Math.max(1, Math.min(90, Number(preset) || 7));
  const suffix = preset === "yesterday" ? " ontem" : "";
  const options = from || to ? { from, to, days } : preset === "yesterday" ? {} : { days };

  try {
    const answers = await Promise.all(prompts[section].map((prompt) => askNaturalQuestion(auth.driverId, `${prompt}${suffix}`, null, options)));
    return Response.json(
      {
        section,
        preset,
        period: answers[0]?.evidence.period ?? null,
        answers,
        foundation: "srrotas-natural-question-v1",
      },
      { headers: { "Cache-Control": "no-store" } },
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "statistics_failed";
    return Response.json({ error: message }, { status: 500, headers: { "Cache-Control": "no-store" } });
  }
}
