import { createHash } from "node:crypto";
import { McpServer } from "@modelcontextprotocol/server";
import { z } from "zod-v4";
import { adminSupabase } from "../supabase";
import { askSrRotas } from "../ai";
import {
  bestHours,
  costBreakdown,
  driverSummary,
  fetchOffers,
  summarizeOffers,
} from "../analytics";
import type { McpContext } from "./auth";

const RangeShape = {
  from: z.string().datetime().optional(),
  to: z.string().datetime().optional(),
};

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

export function createDriverMcpServer(context: McpContext) {
  const server = new McpServer(
    { name: "sr-rotas", version: "0.2.0-alpha" },
    {
      instructions:
        "Sr. Rotas: ferramentas somente de consulta. Os registros do MVP representam ofertas observadas e não devem ser tratados automaticamente como corridas aceitas ou concluídas.",
    },
  );

  const annotations = { readOnlyHint: true, destructiveHint: false, openWorldHint: false };

  server.registerTool(
    "get_driver_summary",
    {
      title: "Resumo do motorista",
      description: "Resume ofertas observadas, médias de R$/km, R$/hora, custos e lucro estimado em um período.",
      inputSchema: z.object({ ...RangeShape }).shape,
      annotations,
    },
    async (args) => result(await audited(context, "get_driver_summary", args, () => driverSummary(context.driverId, args.from, args.to))),
  );

  server.registerTool(
    "search_offers",
    {
      title: "Pesquisar ofertas",
      description: "Pesquisa ofertas observadas por período, plataforma e classificação.",
      inputSchema: z.object({
        ...RangeShape,
        platform: z.string().optional(),
        verdict: z.enum(["boa", "regular", "ruim"]).optional(),
        limit: z.number().int().min(1).max(200).default(50),
      }).shape,
      annotations,
    },
    async (args) => result(await audited(context, "search_offers", args, async () => {
      const found = await fetchOffers(context.driverId, args);
      return { range: found.range, offers: found.offers };
    })),
  );

  server.registerTool(
    "compare_periods",
    {
      title: "Comparar períodos",
      description: "Compara métricas de ofertas entre dois períodos.",
      inputSchema: z.object({
        period_a_from: z.string().datetime(),
        period_a_to: z.string().datetime(),
        period_b_from: z.string().datetime(),
        period_b_to: z.string().datetime(),
      }).shape,
      annotations,
    },
    async (args) => result(await audited(context, "compare_periods", args, async () => {
      const [a, b] = await Promise.all([
        fetchOffers(context.driverId, { from: args.period_a_from, to: args.period_a_to, limit: 500 }),
        fetchOffers(context.driverId, { from: args.period_b_from, to: args.period_b_to, limit: 500 }),
      ]);
      return {
        period_a: { range: a.range, summary: summarizeOffers(a.offers) },
        period_b: { range: b.range, summary: summarizeOffers(b.offers) },
      };
    })),
  );

  server.registerTool(
    "get_best_hours",
    {
      title: "Melhores horários",
      description: "Agrupa as ofertas observadas por hora do dia para encontrar faixas mais rentáveis.",
      inputSchema: z.object({ days: z.number().int().min(1).max(180).default(30) }).shape,
      annotations,
    },
    async (args) => result(await audited(context, "get_best_hours", args, () => bestHours(context.driverId, args.days))),
  );

  server.registerTool(
    "get_cost_breakdown",
    {
      title: "Custos e lucro estimado",
      description: "Calcula km observados, valor oferecido, custo e lucro estimados.",
      inputSchema: z.object({ ...RangeShape }).shape,
      annotations,
    },
    async (args) => result(await audited(context, "get_cost_breakdown", args, () => costBreakdown(context.driverId, args.from, args.to))),
  );

  server.registerTool(
    "ask_sr_rotas",
    {
      title: "Perguntar ao Sr. Rotas",
      description: "Responde uma pergunta em linguagem natural usando apenas as ofertas observadas no período. Requer OPENAI_API_KEY configurada.",
      inputSchema: z.object({
        question: z.string().min(3).max(800),
        ...RangeShape,
      }).shape,
      annotations,
    },
    async (args) => result(await audited(context, "ask_sr_rotas", args, () => askSrRotas(context.driverId, args.question, args.from, args.to))),
  );

  return server;
}
