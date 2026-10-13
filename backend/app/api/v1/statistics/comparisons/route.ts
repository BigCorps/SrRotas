import { accessDeniedResponse } from "@/src/access";
import { authenticateBillingActor } from "@/src/billing-auth";
import { adminSupabase } from "@/src/supabase";
import { allowSecurityAction } from "@/src/security-rate-limit";
import { comparableMetric, planSmartComparison, safeComparisonCutoff, type ComparisonWindow, type MetricEvidence, type SmartComparisonKind } from "@/src/intelligence/statistics-comparisons";
import { ensurePreferences } from "@/src/preferences";

export const runtime = "nodejs";
export const maxDuration = 30;
const allowed = new Set<SmartComparisonKind>(["today_yesterday", "week_previous"]);
const noStore = { "Cache-Control": "private, no-store" };
const PAGE = 500;
const MAX_OFFERS = 2000;
const SYNC_MARGIN_MS = 15 * 60_000;
const FRESHNESS_LIMIT_MS = 60 * 60_000;
const MIN_SAMPLES = 20;

type ObservedRow = { observed_at: string; per_km: number | null; per_hour: number | null };
type ObservedSample = {
  count: number;
  avgPerKm: number | null;
  avgPerHour: number | null;
  kmSamples: number;
  hourSamples: number;
  truncated: boolean;
  recent: boolean;
};
function validTimezone(input: string | null | undefined): string {
  const zone = input || "America/Sao_Paulo";
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: zone }).format(new Date());
    return zone;
  } catch {
    return "America/Sao_Paulo";
  }
}
function average(values: number[]): number | null {
  return values.length ? values.reduce((a, b) => a + b, 0) / values.length : null;
}
/** Canonical observed offers only: no journey, financial reconciliation or odometer scans. */
async function observedSample(driverId: string, window: ComparisonWindow): Promise<ObservedSample> {
  const rows: ObservedRow[] = [];
  let truncated = true;
  for (let offset = 0; offset < MAX_OFFERS; offset += PAGE) {
    const { data, error } = await adminSupabase()
      .from("sr_personal_offer_canonical_v1")
      .select("observed_at,per_km,per_hour")
      .eq("driver_id", driverId)
      .eq("data_source", "operational")
      .gte("observed_at", window.from)
      .lt("observed_at", window.to)
      .order("observed_at", { ascending: false })
      .range(offset, offset + PAGE - 1);
    if (error) throw new Error(error.message);
    const batch = (data ?? []) as ObservedRow[];
    rows.push(...batch);
    if (batch.length < PAGE) { truncated = false; break; }
  }
  const numeric = (key: "per_km" | "per_hour") => rows.map((row) => row[key])
    .filter((n): n is number => typeof n === "number" && Number.isFinite(n));
  const perKm = numeric("per_km");
  const perHour = numeric("per_hour");
  const newest = rows[0]?.observed_at ? Date.parse(rows[0].observed_at) : Number.NaN;
  return {
    count: rows.length,
    avgPerKm: average(perKm),
    avgPerHour: average(perHour),
    kmSamples: perKm.length,
    hourSamples: perHour.length,
    truncated,
    recent: Number.isFinite(newest) && newest >= Date.parse(window.to) - FRESHNESS_LIMIT_MS,
  };
}
const brl = (n: number | null) => n === null ? "—" : `R$ ${n.toLocaleString("pt-BR", { maximumFractionDigits: 2 })}`;

export async function GET(request: Request) {
  const auth = await authenticateBillingActor(request);
  if (!auth) return Response.json({ error: "unauthorized" }, { status: 401, headers: noStore });
  const denied = accessDeniedResponse(auth.access, "can_analytics");
  if (denied) return denied;
  const kind = new URL(request.url).searchParams.get("kind") as SmartComparisonKind;
  if (!allowed.has(kind)) return Response.json({ error: "comparison_kind_invalid" }, { status: 400, headers: noStore });

  try {
    if (!await allowSecurityAction(request, "statistics-comparisons", auth.driverId, 8, 600)) {
      return Response.json({ error: "rate_limited" }, { status: 429, headers: noStore });
    }
    const preferences = await ensurePreferences(auth.driverId);
    const timeZone = validTimezone(preferences.timezone);
    // A conservative grace interval is not a confirmed device-sync watermark.
    // Compare only server-visible operational data and explicitly mark it provisional.
    const actualNow = new Date();
    const todayStart = Date.parse(planSmartComparison("today_yesterday", timeZone, actualNow).current.from);
    const cutoff = safeComparisonCutoff(actualNow.getTime(), todayStart, SYNC_MARGIN_MS);
    const plan = planSmartComparison(kind, timeZone, cutoff);
    const reference = plan.references[0];
    const [current, previous] = await Promise.all([
      observedSample(auth.driverId, plan.current),
      observedSample(auth.driverId, reference),
    ]);
    const eligible = (validCurrent: number, validPrior: number) =>
      !current.truncated && !previous.truncated &&
      current.recent && previous.recent &&
      validCurrent >= MIN_SAMPLES && validPrior >= MIN_SAMPLES;

    const metrics = [
      {
        key: "offers",
        label: "Ofertas capturadas (amostra)",
        semantic: "OBSERVED_OFFER",
        current_formatted: String(current.count),
        reference_formatted: String(previous.count),
        ...{ current: current.count, reference: previous.count, absolute: null, percentage: null, comparable: false },
        comparison_reason: "Sem tempo de captura ativa comprovado: quantidade não mede queda ou alta do mercado.",
      },
      ...([
        ["avg_per_km", "R$/km médio das ofertas", "avgPerKm", "kmSamples"],
        ["avg_per_hour", "R$/h médio das ofertas", "avgPerHour", "hourSamples"],
      ] as const).map(([key, label, valueKey, countKey]) => {
        const valid = eligible(current[countKey], previous[countKey]);
        const evidence = (value: number | null, passed: boolean): MetricEvidence => ({
          value, semantic: "OBSERVED_OFFER", status: passed ? "ok" : "insufficient", coverage: null,
        });
        const delta = comparableMetric(evidence(current[valueKey], valid), [evidence(previous[valueKey], valid)]);
        return {
          key, label, semantic: "OBSERVED_OFFER",
          current_formatted: brl(current[valueKey]),
          reference_formatted: brl(previous[valueKey]),
          ...delta,
          comparison_reason: valid ? null :
            "Amostra, atualização recente ou cobertura de leitura insuficiente para comparar.",
        };
      }),
    ];
    return Response.json({
      schema: "srrotas-statistics-smart-comparisons-v2",
      kind,
      time_zone: timeZone,
      current_period: plan.current,
      reference_period: reference,
      samples: { current: current.count, reference: previous.count },
      provisional: true,
      metrics,
      notes: [
        "Valores representam apenas ofertas operacionais recebidas pelo servidor, nunca receita.",
        "Quantidade de ofertas depende do tempo de captura ativa; não é indicador direto do mercado.",
        "Última sincronização confirmada do aparelho ainda não é conhecida por esta rota. A margem de 15 minutos não garante que ofertas offline tenham chegado.",
        "Médias só são comparadas com pelo menos 20 valores válidos em cada período, sem truncamento e com observações recentes.",
        "Receita, corridas realizadas e odômetro continuam fora dos comparativos por hora até existir recorte temporal auditável.",
      ],
    }, { headers: noStore });
  } catch {
    return Response.json({ error: "comparison_unavailable" }, { status: 500, headers: noStore });
  }
}
