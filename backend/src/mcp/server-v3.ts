import { createHash } from "node:crypto";
import { McpServer } from "@modelcontextprotocol/server";
import { z } from "zod-v4";
import { adminSupabase } from "../supabase";
import { askNaturalQuestion } from "../intelligence/engine";
import {
  fetchOffers,
  historyDashboard,
  strategyProgress,
} from "../analytics";
import { ensurePreferences } from "../preferences";
import { currentJourney, journeySummary, listJourneys } from "../journeys";
import type { McpContext } from "./auth";

const RangeShape = {
  from: z.string().datetime().optional(),
  to: z.string().datetime().optional(),
};

function result(data: unknown) {
  return {
    content: [{ type: "text" as const, text: JSON.stringify(data) }],
    structuredContent: data && typeof data === "object" ? data as Record<string, unknown> : { value: data },
  };
}

async function audited<T>(context: McpContext, tool: string, args: unknown, fn: () => Promise<T>): Promise<T> {
  const started = performance.now();
  let status = "success";
  let errorCode: string | null = null;
  try {
    return await fn();
  } catch (error) {
    status = "error";
    errorCode = error instanceof Error ? error.message.slice(0, 120) : "tool_error";
    throw error;
  } finally {
    const argumentHash = createHash("sha256").update(JSON.stringify(args ?? {})).digest("hex");
    await adminSupabase().from("mcp_tool_audit_logs").insert({
      driver_id: context.driverId,
      client_id: context.clientId,
      tool_name: tool,
      status,
      duration_ms: Math.max(0, Math.round(performance.now() - started)),
      argument_hash: argumentHash,
      error_code: errorCode,
    });
  }
}

function naturalOptions(from?: string, to?: string, days?: number) {
  return {
    ...(from ? { from } : {}),
    ...(to ? { to } : {}),
    ...(days ? { days } : {}),
  };
}

export function createDriverMcpServer(context: McpContext) {
  const server = new McpServer(
    { name: "sr-rotas", version: "1.0.0" },
    {
      instructions:
        "Sr. Rotas fornece ferramentas somente de consulta. Analytics usa a mesma Fundação Premium de Estatísticas/Pergunte; " +
        "ofertas observadas não provam aceite, conclusão ou faturamento. O MCP não controla Uber, 99 ou o aplicativo Sr. Rotas.",
    },
  );

  const annotations = {
    readOnlyHint: true,
    destructiveHint: false,
    idempotentHint: true,
    openWorldHint: false,
  };

  server.registerTool(
    "get_srrotas_capabilities",
    {
      title: "Capacidades do Sr. Rotas",
      description: "Explica o conjunto MCP, a Fundação Premium compartilhada e os limites de segurança.",
      inputSchema: z.object({}),
      annotations,
    },
    async (args: any) => result(await audited(context, "get_srrotas_capabilities", args, async () => ({
      server: "sr-rotas",
      version: "1.0.0",
      access: "read_only",
      analytics_foundation: "srrotas-natural-question-v1",
      consumes_srrotas_ai_credits: false,
      tools: [
        "get_srrotas_capabilities",
        "get_history_dashboard",
        "get_driver_summary",
        "get_driver_strategy",
        "get_strategy_progress",
        "search_offers",
        "compare_periods",
        "get_best_hours",
        "get_cost_breakdown",
        "get_current_journey",
        "list_journeys",
        "get_journey_summary",
      ],
      boundaries: {
        accepts_rides: false,
        rejects_rides: false,
        controls_mobility_apps: false,
        changes_driver_strategy: false,
        starts_or_ends_journey: false,
      },
      data_semantics: {
        observed_offers_are_not_proof_of_acceptance: true,
        observed_offers_are_not_proof_of_completion: true,
        legacy_uber_history_ocr_requires_human_reconciliation: true,
        estimated_profit_is_not_realized_profit: true,
      },
    }))));

  server.registerTool(
    "get_history_dashboard",
    {
      title: "Histórico e analytics",
      description: "Retorna o dashboard histórico e inclui o resumo canônico da Fundação Premium para o mesmo período.",
      inputSchema: z.object({
        days: z.number().int().min(1).max(90).default(7),
        verdict: z.enum(["boa", "regular", "ruim"]).optional(),
        service_type: z.enum(["uberx", "comfort", "black", "electric", "priority", "moto", "unknown"]).optional(),
        offer_type: z.enum(["exclusive", "radar"]).optional(),
      }),
      annotations,
    },
    async (args: any) => result(await audited(context, "get_history_dashboard", args, async () => {
      const [dashboard, foundation] = await Promise.all([
        historyDashboard(context.driverId, {
          days: args.days,
          verdict: args.verdict,
          serviceType: args.service_type,
          offerType: args.offer_type,
        }),
        askNaturalQuestion(context.driverId, "Me dê um resumo do período", null, { days: args.days }),
      ]);
      return { ...dashboard, foundation };
    })));

  server.registerTool(
    "get_driver_summary",
    {
      title: "Resumo do motorista",
      description: "Resumo canônico com ofertas observadas, faturamento confirmado, distância e cobertura.",
      inputSchema: z.object({ ...RangeShape }),
      annotations,
    },
    async (args: any) => result(await audited(context, "get_driver_summary", args, () =>
      askNaturalQuestion(context.driverId, "Me dê um resumo do período", null, naturalOptions(args.from, args.to, 7)),
    )));

  server.registerTool(
    "get_driver_strategy",
    {
      title: "Estratégia do motorista",
      description: "Consulta as metas configuradas do motorista.",
      inputSchema: z.object({}),
      annotations,
    },
    async (args: any) => result(await audited(context, "get_driver_strategy", args, () => ensurePreferences(context.driverId))));

  server.registerTool(
    "get_strategy_progress",
    {
      title: "Aderência à estratégia",
      description: "Mostra quantas ofertas operacionais observadas atendem às metas configuradas.",
      inputSchema: z.object({ ...RangeShape }),
      annotations,
    },
    async (args: any) => result(await audited(context, "get_strategy_progress", args, () => strategyProgress(context.driverId, args.from, args.to))));

  server.registerTool(
    "search_offers",
    {
      title: "Pesquisar ofertas",
      description: "Pesquisa ofertas operacionais observadas. Não transforma oferta em corrida concluída.",
      inputSchema: z.object({
        ...RangeShape,
        platform: z.string().optional(),
        verdict: z.enum(["boa", "regular", "ruim"]).optional(),
        journey_id: z.string().uuid().optional(),
        limit: z.number().int().min(1).max(200).default(50),
      }),
      annotations,
    },
    async (args: any) => result(await audited(context, "search_offers", args, async () => {
      const found = await fetchOffers(context.driverId, {
        from: args.from,
        to: args.to,
        platform: args.platform,
        verdict: args.verdict,
        journeyId: args.journey_id,
        limit: args.limit,
      });
      return { range: found.range, offers: found.offers };
    })));

  server.registerTool(
    "compare_periods",
    {
      title: "Comparar períodos",
      description: "Compara dois períodos usando a mesma Fundação Premium; cada lado mantém seu EvidenceEnvelope.",
      inputSchema: z.object({
        period_a_from: z.string().datetime(),
        period_a_to: z.string().datetime(),
        period_b_from: z.string().datetime(),
        period_b_to: z.string().datetime(),
      }),
      annotations,
    },
    async (args: any) => result(await audited(context, "compare_periods", args, async () => {
      const [a, b] = await Promise.all([
        askNaturalQuestion(context.driverId, "Me dê um resumo do período", null, { from: args.period_a_from, to: args.period_a_to }),
        askNaturalQuestion(context.driverId, "Me dê um resumo do período", null, { from: args.period_b_from, to: args.period_b_to }),
      ]);
      return { period_a: a, period_b: b, note: "Ambos os lados usam srrotas-natural-question-v1." };
    })));

  server.registerTool(
    "get_best_hours",
    {
      title: "Melhores horários",
      description: "Ranking por horário usando a mesma Fundação Premium de Estatísticas e Pergunte.",
      inputSchema: z.object({ days: z.number().int().min(1).max(180).default(30) }),
      annotations,
    },
    async (args: any) => result(await audited(context, "get_best_hours", args, () =>
      askNaturalQuestion(context.driverId, "Quais meus melhores horários?", null, { days: Math.min(90, args.days) }),
    )));

  server.registerTool(
    "get_cost_breakdown",
    {
      title: "Custos",
      description: "Custos reais registrados e custo operacional modelado, com cobertura explícita.",
      inputSchema: z.object({ ...RangeShape }),
      annotations,
    },
    async (args: any) => result(await audited(context, "get_cost_breakdown", args, () =>
      askNaturalQuestion(context.driverId, "Quanto gastei no período?", null, naturalOptions(args.from, args.to, 7)),
    )));

  server.registerTool(
    "get_current_journey",
    {
      title: "Jornada atual",
      description: "Consulta a jornada aberta mais recente do motorista.",
      inputSchema: z.object({}),
      annotations,
    },
    async (args: any) => result(await audited(context, "get_current_journey", args, () => currentJourney(context.driverId))));

  server.registerTool(
    "list_journeys",
    {
      title: "Listar jornadas",
      description: "Lista jornadas do motorista, da mais recente para a mais antiga.",
      inputSchema: z.object({ limit: z.number().int().min(1).max(100).default(30) }),
      annotations,
    },
    async (args: any) => result(await audited(context, "list_journeys", args, () => listJourneys(context.driverId, args.limit))));

  server.registerTool(
    "get_journey_summary",
    {
      title: "Resumo de uma jornada",
      description: "Resume ofertas operacionais observadas dentro de uma jornada específica.",
      inputSchema: z.object({ journey_id: z.string().uuid() }),
      annotations,
    },
    async (args: any) => result(await audited(context, "get_journey_summary", args, () => journeySummary(context.driverId, args.journey_id))));

  return server;
}
