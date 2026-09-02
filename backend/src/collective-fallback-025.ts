import { adminSupabase } from "./supabase";

type Mode = "now" | "today" | "week" | "search";
type Input = {
  mode?: Mode;
  source?: "personal" | "collective";
  at?: string;
  weekday?: number;
  hour?: number;
  profile?: string | null;
  region?: string | null;
};

type RawRow = Record<string, unknown>;

type Fallback = {
  rows: RawRow[];
  scope: string;
};

const weekdays: Record<string, number> = {
  Mon: 1,
  Tue: 2,
  Wed: 3,
  Thu: 4,
  Fri: 5,
  Sat: 6,
  Sun: 7,
};

function cleanSearch(value?: string | null) {
  return (value || "").trim().replace(/[%_]/g, "").slice(0, 80) || null;
}

function cleanProfile(value?: string | null) {
  const text = String(value || "");
  return ["popular", "comfort", "premium", "unknown"].includes(text)
    ? text
    : null;
}

function localTime(value: Date, timeZone: string) {
  const weekday = new Intl.DateTimeFormat("en-US", {
    timeZone,
    weekday: "short",
  }).format(value);
  const hour =
    Number(
      new Intl.DateTimeFormat("en-US", {
        timeZone,
        hour: "2-digit",
        hour12: false,
      }).format(value),
    ) % 24;
  return {
    weekdayIso: weekdays[weekday] ?? 1,
    hour,
    hourBucket: Math.floor(hour / 3) * 3,
  };
}

function confidence(samples: number) {
  if (samples >= 250) return "high";
  if (samples >= 80) return "medium";
  if (samples >= 20) return "low";
  return "initial";
}

function decorate(rows: RawRow[], scope: string) {
  return rows.map((raw) => {
    const sampleCount = Math.max(0, Number(raw.sample_count || 0));
    const contributors = Math.max(0, Number(raw.contributor_count || 0));
    return {
      ...raw,
      source: "collective",
      confidence: confidence(sampleCount),
      wording:
        scope === "same_3h_any_weekday"
          ? "Base coletiva ampliada para a mesma faixa de horário em outros dias."
          : scope === "all_time_region_profile"
            ? "Base coletiva ampliada para o histórico da região e deste perfil."
            : "Base coletiva ampliada para o histórico agregado da região.",
      privacy_min_contributors: 3,
      contributor_count: contributors,
    };
  });
}

async function rowsFrom(
  view: string,
  options: {
    hour?: number;
    profile?: string | null;
    region?: string | null;
    limit?: number;
  },
): Promise<RawRow[]> {
  let q: any = adminSupabase().from(view).select("*");
  if (options.hour !== undefined) q = q.eq("hour_bucket", options.hour);
  if (options.profile) q = q.eq("service_profile", options.profile);
  if (options.region) q = q.ilike("region_label", `%${options.region}%`);
  const { data, error } = await q
    .order("sample_count", { ascending: false })
    .limit(options.limit ?? 120);
  if (error) throw new Error(error.message);
  return (data ?? []) as RawRow[];
}

async function fallback(
  mode: Mode,
  hourBucket: number,
  profile: string | null,
  region: string | null,
): Promise<Fallback> {
  if (mode === "now" || mode === "today") {
    const sameHour = await rowsFrom(
      "sr_collective_offer_region_hour_anyday_v025",
      { hour: hourBucket, profile, region, limit: 120 },
    );
    if (sameHour.length) {
      return { rows: sameHour, scope: "same_3h_any_weekday" };
    }
  }

  const regionProfile = await rowsFrom(
    "sr_collective_offer_region_profile_v025",
    { profile, region, limit: mode === "search" ? 240 : 160 },
  );
  if (regionProfile.length) {
    return { rows: regionProfile, scope: "all_time_region_profile" };
  }

  const regionAll = await rowsFrom(
    "sr_collective_offer_region_v025",
    { region, limit: mode === "search" ? 240 : 160 },
  );
  if (regionAll.length) {
    return { rows: regionAll, scope: "all_time_region" };
  }

  return { rows: [], scope: "none" };
}

/**
 * 0.25.0: somente amplia a Base Coletiva quando a consulta exata não publicou
 * linhas. As views de fallback já exigem >=3 motoristas distintos, portanto o
 * backend nunca precisa afrouxar o limiar de privacidade.
 */
export async function applyCollectiveFallback025(
  input: Input,
  original: any,
) {
  if (input.source !== "collective") return original;
  if (!original?.collective_opt_in) return original;
  if (Array.isArray(original?.collective) && original.collective.length > 0) {
    return { ...original, collective_scope: original.scope || "exact" };
  }

  const mode: Mode = ["now", "today", "week", "search"].includes(String(input.mode))
    ? (input.mode as Mode)
    : "now";
  const timeZone = String(original?.time_zone || "America/Sao_Paulo");
  const at =
    input.at && !Number.isNaN(new Date(input.at).getTime())
      ? new Date(input.at)
      : new Date();
  const current = localTime(at, timeZone);
  const hour =
    typeof input.hour === "number" && Number.isFinite(input.hour)
      ? Math.max(0, Math.min(23, input.hour))
      : current.hour;
  const hourBucket = Math.floor(hour / 3) * 3;
  const profile = input.profile === "all"
    ? null
    : cleanProfile(input.profile) ?? cleanProfile(original?.selected_service_profile);
  const region = cleanSearch(input.region);

  const found = await fallback(mode, hourBucket, profile, region).catch(() => null);
  if (found === null) {
    // Permite subir o código antes da migration sem derrubar o endpoint.
    return { ...original, collective_scope: "fallback_unavailable" };
  }
  if (!found.rows.length) {
    return {
      ...original,
      collective_scope: "none",
    };
  }

  return {
    ...original,
    collective: decorate(found.rows, found.scope),
    preferred: "collective",
    collective_scope: found.scope,
    note:
      "A combinação coletiva exata ainda não reuniu 3 participantes; o Sr. Rotas ampliou somente a janela agregada, mantendo no mínimo 3 motoristas distintos. Não é demanda em tempo real e não garante corrida.",
  };
}
