import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const page = readFileSync(new URL("../../app/app/estatisticas/page.tsx", import.meta.url), "utf8");
const domain = readFileSync(new URL("../../src/intelligence/analytics-domain.ts", import.meta.url), "utf8");

test("productivity has its own visible work-time and realized-rate cards", () => {
  assert.match(page, /const productivity = section === "produtividade"/);
  assert.match(page, /const showKpis = overview \|\| productivity/);
  for (const label of ["Horas de jornadas encerradas", "Tarifas por hora", "Tarifas por km"]) {
    assert.ok(page.includes(label), label);
  }
  assert.match(page, /\{chosenKpis\.map\(/);
});

test("a closed journey with no observed offers still enables the period summary", () => {
  assert.match(domain, /vehicle\.metricsWithDistance \|\| productivity\.closedJourneyCount \? "ok"/);
});

test("open journeys or missing odometer cannot masquerade as a fully covered period summary", () => {
  assert.match(domain, /!productivity\.allJourneysClosed/);
  assert.match(domain, /vehicle\.metricsWithDistance < realized\.journeys\.length/);
});

test("statistics loading errors can be retried and not render raw backend errors", () => {
  assert.match(page, /setRetry\(\(value\) => value \+ 1\)/);
  assert.match(page, /\[query, customValid, retry\]/);
  assert.doesNotMatch(page, /throw new Error\(body\?\.error/);
});

test("visible coverage states distinguish observed fare coverage from total period completeness", () => {
  assert.match(page, /answer\.evidence\.coverage\?\.label \|\| "Cobertura da amostra"/);
  assert.match(page, /Escopo do resultado:/);
  assert.match(page, /Por que algumas taxas estão indisponíveis/);
  assert.match(page, /reasonsForUnavailableRates\(summary\)/);
});
