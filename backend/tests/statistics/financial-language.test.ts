import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { renderAnswer } from "../../src/intelligence/renderer";

const page = readFileSync(new URL("../../app/app/estatisticas/page.tsx", import.meta.url), "utf8");
const analytics = readFileSync(new URL("../../src/intelligence/analytics-domain.ts", import.meta.url), "utf8");
const renderer = readFileSync(new URL("../../src/intelligence/renderer.ts", import.meta.url), "utf8");

test("finance dashboard labels offered fares rather than confirmed payout", () => {
  assert.match(page, /Tarifas de corridas concluídas/);
  assert.match(page, /não comprova recebimento/);
  assert.match(analytics, /não de comprovante do recebimento final/);
  assert.doesNotMatch(page, /Faturamento confirmado/);
  assert.doesNotMatch(renderer, /o faturamento confirmado é/);
});

test("realized earnings explanation does not claim final payment", () => {
  const answer = renderAnswer(
    { intent: "REALIZED_EARNINGS", period: { label: "Últimos 7 dias" } } as any,
    {
      status: "ok", title: "Tarifas", semantic: "REALIZED_REVENUE",
      metrics: [
        { key: "realized_revenue", formatted: "R$ 200" },
        { key: "completed_rides", formatted: "4" },
      ],
    } as any,
    { coverage: { value: 1, label: "Tarifas vinculadas" } } as any,
  );
  assert.match(answer, /tarifas exibidas nas ofertas vinculadas/);
  assert.match(answer, /valor final recebido não foi comprovado/);
  assert.doesNotMatch(answer, /faturamento confirmado/i);
});
