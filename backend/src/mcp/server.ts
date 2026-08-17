import { createHash } from "node:crypto";
import { McpServer } from "@modelcontextprotocol/server";
import { z } from "zod-v4";
import { adminSupabase } from "../supabase";
import { bestHours, costBreakdown, driverSummary, fetchOffers, historyDashboard, strategyProgress, summarizeOffers } from "../analytics";
import { ensurePreferences } from "../preferences";
import { currentJourney, journeySummary, listJourneys } from "../journeys";
import type { McpContext } from "./auth";

const RangeShape = { from: z.string().datetime().optional(), to: z.string().datetime().optional() };

function result(data: unknown) {
  return {
    content: [{ type: "text" as const, text: JSON.stringify(data) }],
    structuredContent: data && typeof data === "object" ? (data as Record<string, unknown>) : { value: data },
  };
}

async function audited<T>(context: McpContext, tool: string, args: unknown, fn: () => Promise<T>): Promise<T> {
  const started = performance.now();
  let status = "success";
  let errorCode: string | null = null;
  try { return await fn(); }
  catch (error) { status = "error"; errorCode = error instanceof Error ? error.message.slice(0, 120) : "tool_error"; throw error; }
  finally {
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

export function createDriverMcpServer(context: McpContext) {
  const server = new McpServer(
    { name: "sr-rotas", version: "0.9.0-alpha" },
    {
      instructions:
        "Sr. Rotas fornece ferramentas somente de consulta sobre ofertas observadas. " +
        "Ofertas observadas não provam corrida aceita, concluída, faturamento ou ganho realizado. " +
        "A IA do cliente MCP deve interpretar os dados sem tentar controlar o aplicativo Uber.",
    },
  );
  const annotations = { readOnlyHint: true, destructiveHint: false, openWorldHint: false };

  server.registerTool("get_history_dashboard", {
    title: "Histórico e analytics",
    description: "Retorna resumo, comparação, dias, horários, categorias, jornadas e destaques de ofertas observadas.",
    inputSchema: z.object({ days: z.number().int().min(1).max(90).default(7), verdict: z.enum(["boa","regular","ruim"]).optional(), service_type: z.enum(["uberx","comfort","black","electric","priority","moto","unknown"]).optional(), offer_type: z.enum(["exclusive","radar"]).optional() }).shape,
    annotations,
  }, async (args) => result(await audited(context, "get_history_dashboard", args, () => historyDashboard(context.driverId, { days: args.days, verdict: args.verdict, serviceType: args.service_type, offerType: args.offer_type }))));

  server.registerTool("get_driver_summary", {
    title: "Resumo do motorista", description: "Resume ofertas observadas, R$/km, R$/hora, custos e lucro estimado em um período.",
    inputSchema: z.object({ ...RangeShape }).shape, annotations,
  }, async (args) => result(await audited(context, "get_driver_summary", args, () => driverSummary(context.driverId, args.from, args.to))));

  server.registerTool("get_driver_strategy", {
    title: "Estratégia do motorista", description: "Consulta as metas atuais de R$/km, R$/hora, valor mínimo, deslocamento e lucro estimado.",
    inputSchema: z.object({}).shape, annotations,
  }, async (args) => result(await audited(context, "get_driver_strategy", args, () => ensurePreferences(context.driverId))));

  server.registerTool("get_strategy_progress", {
    title: "Aderência à estratégia", description: "Mostra quantas ofertas observadas atendem simultaneamente às metas configuradas.",
    inputSchema: z.object({ ...RangeShape }).shape, annotations,
  }, async (args) => result(await audited(context, "get_strategy_progress", args, () => strategyProgress(context.driverId, args.from, args.to))));

  server.registerTool("search_offers", {
    title: "Pesquisar ofertas", description: "Pesquisa ofertas observadas por período, plataforma, classificação ou jornada.",
    inputSchema: z.object({ ...RangeShape, platform: z.string().optional(), verdict: z.enum(["boa","regular","ruim"]).optional(), journey_id: z.string().uuid().optional(), limit: z.number().int().min(1).max(200).default(50) }).shape,
    annotations,
  }, async (args) => result(await audited(context, "search_offers", args, async () => {
    const found = await fetchOffers(context.driverId, { from: args.from, to: args.to, platform: args.platform, verdict: args.verdict, journeyId: args.journey_id, limit: args.limit });
    return { range: found.range, offers: found.offers };
  })));

  server.registerTool("compare_periods", {
    title: "Comparar períodos", description: "Compara métricas de ofertas entre dois períodos.",
    inputSchema: z.object({ period_a_from: z.string().datetime(), period_a_to: z.string().datetime(), period_b_from: z.string().datetime(), period_b_to: z.string().datetime() }).shape,
    annotations,
  }, async (args) => result(await audited(context, "compare_periods", args, async () => {
    const [a,b] = await Promise.all([
      fetchOffers(context.driverId, { from: args.period_a_from, to: args.period_a_to, limit: 500 }),
      fetchOffers(context.driverId, { from: args.period_b_from, to: args.period_b_to, limit: 500 }),
    ]);
    return { period_a: { range:a.range, summary:summarizeOffers(a.offers) }, period_b: { range:b.range, summary:summarizeOffers(b.offers) } };
  })));

  server.registerTool("get_best_hours", {
    title: "Melhores horários", description: "Agrupa ofertas observadas por hora do dia para identificar faixas com melhores indicadores.",
    inputSchema: z.object({ days: z.number().int().min(1).max(180).default(30) }).shape, annotations,
  }, async (args) => result(await audited(context, "get_best_hours", args, () => bestHours(context.driverId, args.days))));

  server.registerTool("get_cost_breakdown", {
    title: "Custos e lucro estimado", description: "Calcula km observados, valor oferecido, custo e lucro estimados.",
    inputSchema: z.object({ ...RangeShape }).shape, annotations,
  }, async (args) => result(await audited(context, "get_cost_breakdown", args, () => costBreakdown(context.driverId, args.from, args.to))));

  server.registerTool("get_current_journey", {
    title: "Jornada atual", description: "Consulta a jornada aberta mais recente do motorista.", inputSchema: z.object({}).shape, annotations,
  }, async (args) => result(await audited(context, "get_current_journey", args, () => currentJourney(context.driverId))));

  server.registerTool("list_journeys", {
    title: "Listar jornadas", description: "Lista jornadas registradas do motorista, da mais recente para a mais antiga.",
    inputSchema: z.object({ limit: z.number().int().min(1).max(100).default(30) }).shape, annotations,
  }, async (args) => result(await audited(context, "list_journeys", args, () => listJourneys(context.driverId, args.limit))));

  server.registerTool("get_journey_summary", {
    title: "Resumo de uma jornada", description: "Resume somente as ofertas observadas dentro de uma jornada específica.",
    inputSchema: z.object({ journey_id: z.string().uuid() }).shape, annotations,
  }, async (args) => result(await audited(context, "get_journey_summary", args, () => journeySummary(context.driverId, args.journey_id))));

  return server;
}
