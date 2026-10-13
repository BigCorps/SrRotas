import { accessDeniedResponse } from "@/src/access";
import { authenticateBillingActor } from "@/src/billing-auth";
import { askNaturalQuestion } from "@/src/intelligence/engine";
import { comparableMetric, planSmartComparison, type MetricEvidence, type SmartComparisonKind } from "@/src/intelligence/statistics-comparisons";
import { ensurePreferences } from "@/src/preferences";

export const runtime = "nodejs";
export const maxDuration = 60;

// Phase 1: two comparisons, each requiring exactly two existing Foundation Premium summaries.
// Historical means need pre-aggregation before becoming a public high-fanout API.
const allowed = new Set<SmartComparisonKind>(["today_yesterday", "week_previous"]);
const keys = new Set(["offers", "avg_per_km", "avg_per_hour", "realized_revenue", "completed_rides", "actual_distance"]);

export async function GET(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401 });
  const denied = accessDeniedResponse(auth.access, "can_analytics");
  if (denied) return denied;

  const url = new URL(request.url);
  const kind = url.searchParams.get("kind") as SmartComparisonKind;
  if (!allowed.has(kind)) return Response.json({ error: "comparison_kind_invalid" }, { status: 400 });

  try {
    const preferences = await ensurePreferences(auth.driverId);
    const plan = planSmartComparison(kind, preferences.timezone || "America/Sao_Paulo");
    const reference = plan.references[0];
    const [current, previous] = await Promise.all([
      askNaturalQuestion(auth.driverId, "Me dê um resumo do período", null, { from: plan.current.from, to: plan.current.to }),
      askNaturalQuestion(auth.driverId, "Me dê um resumo do período", null, { from: reference.from, to: reference.to }),
    ]);
    const currentMetrics = current.metrics.filter((m) => keys.has(m.key));
    const metrics = currentMetrics.map((item) => {
      const other = previous.metrics.find((m) => m.key === item.key && m.semantic === item.semantic);
      const numeric = (value: number | string | null) => typeof value === "number" && Number.isFinite(value) ? value : null;
      const toEvidence = (answer: typeof current, source: typeof item | undefined): MetricEvidence => {
        const isObserved = source?.semantic === "OBSERVED_OFFER";
        const limitReached = isObserved && answer.limitations.some((value) => value.includes("limite analítico"));
        const weakAverage = source?.key.startsWith("avg_") && answer.evidence.sampleCount < 20;
        const coverage = source?.key === "actual_distance"
          ? (answer.details?.distance_coverage as { value?: number | null } | undefined)?.value ?? null
          : answer.evidence.coverage?.value ?? null;
        return {
          value: source ? numeric(source.value) : null,
          semantic: source?.semantic ?? item.semantic,
          status: answer.status !== "ok" || limitReached || weakAverage ? "insufficient" : "ok",
          coverage,
        };
      };
      const delta = comparableMetric(toEvidence(current, item), [toEvidence(previous, other)]);
      return {
        key: item.key, label: item.label, semantic: item.semantic, current_formatted: item.formatted,
        reference_formatted: other?.formatted ?? "—", ...delta,
      };
    });
    return Response.json({
      schema: "srrotas-statistics-smart-comparisons-v1", kind,
      current_period: plan.current, reference_period: reference,
      metrics, notes: [
        "Janelas comparadas até a mesma duração parcial do dia ou da semana.",
        "Ofertas observadas não equivalem a corridas realizadas.",
        "Diferenças indisponíveis permanecem sem cálculo; custo estimado não é gasto comprovado.",
      ],
    }, { headers: { "Cache-Control": "no-store" } });
  } catch {
    return Response.json({ error: "comparison_unavailable" }, { status: 500, headers: { "Cache-Control": "no-store" } });
  }
}
