import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const source = readFileSync(new URL("../../app/app/ia/page.tsx", import.meta.url), "utf8");

test("IA Web preserva contrato determinístico e contexto de sessão", () => {
  assert.match(source, /fetch\("\/api\/v1\/ask"/);
  assert.match(source, /JSON\.stringify\(\{ question: clean, context: contextRef\.current \}\)/);
  assert.match(source, /contextRef\.current = data\.context \?\? null/);
  assert.match(source, /contextRef\.current = null/);
  assert.match(source, /setTurns\(\[\]\)/);
  assert.doesNotMatch(source, /(?:localStorage|sessionStorage|IndexedDB)/);
});

test("IA Web não apresenta modelo, tokens ou métricas inventadas", () => {
  assert.match(source, /answer\.metrics\?\.length/);
  assert.match(source, /answer\.status !== "ok"/);
  assert.match(source, /answer\.evidence\.sampleCount/);
  assert.doesNotMatch(source, /\{answer\.model\}/);
  assert.doesNotMatch(source, /\{answer\.totalTokens\}/);
  assert.match(source, /Entenda o resultado/);
});

test("IA Web confirma ditado antes de enviar e prevê cancelamento de conversa", () => {
  assert.match(source, /setVoiceState\("draft"\)/);
  assert.match(source, /Texto reconhecido\. Confira e toque em Enviar/);
  assert.match(source, /abortRef\.current\?\.abort\(\)/);
  assert.match(source, /Analisando seus dados/);
});
