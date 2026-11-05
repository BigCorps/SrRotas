#!/usr/bin/env node
// Sr. Rotas — exemplo de cliente do coletor externo (contrato srrotas-radar-collector-api-v1).
// Sem dependências (Node >= 18). NÃO contém credenciais: lê tudo do ambiente.
//
//   export SR_ROTAS_BASE_URL="https://<host>"
//   export SR_ROTAS_COLLECTOR_KEY="srrk_<prefixo>_<segredo>"   # entregue pelo admin, nunca no Git
//
//   node radar-collector-client.example.mjs --ping
//   node radar-collector-client.example.mjs --dry-run eventos.json
//   node radar-collector-client.example.mjs eventos.json [--run-id sp-2026-11-05T12-00Z]
//   node radar-collector-client.example.mjs --pois lugares.json [--dry-run]
//
// eventos.json: { "events": [ ... ] }  ou  [ ... ]   (ver RADAR-COLLECTOR-API-v1.md §3)
// lugares.json: { "pois": [ ... ] }    ou  [ ... ]   (ver §7)

import { readFile } from "node:fs/promises";
import { randomUUID } from "node:crypto";

const BASE = (process.env.SR_ROTAS_BASE_URL || "").replace(/\/+$/, "");
const KEY = process.env.SR_ROTAS_COLLECTOR_KEY || "";
const MAX_RETRIES = 3;

function fail(message, code = 1) {
  console.error(`erro: ${message}`);
  process.exit(code);
}

function parseArgs(argv) {
  const out = { ping: false, dryRun: false, pois: false, runId: null, file: null };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--ping") out.ping = true;
    else if (a === "--dry-run") out.dryRun = true;
    else if (a === "--pois") out.pois = true;
    else if (a === "--run-id") out.runId = argv[++i] || null;
    else if (a.startsWith("--")) fail(`opção desconhecida: ${a}`);
    else out.file = a;
  }
  return out;
}

async function call(method, path, body) {
  for (let attempt = 1; ; attempt++) {
    const response = await fetch(`${BASE}${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${KEY}`,
        ...(body ? { "Content-Type": "application/json" } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(60_000),
    }).catch((error) => ({ networkError: error }));

    if (response.networkError) {
      if (attempt >= MAX_RETRIES) fail(`rede: ${response.networkError.message}`);
    } else {
      const data = await response.json().catch(() => ({}));
      // 429/5xx: espere e repita. Com o mesmo run_id o servidor não reprocessa um lote já aceito.
      const retryable = response.status === 429 || response.status >= 500;
      if (!retryable || attempt >= MAX_RETRIES) return { status: response.status, data };
      const wait = Math.min(Number(response.headers.get("retry-after")) || 30, 900);
      console.error(`HTTP ${response.status} (${data.error || "?"}); nova tentativa em ${wait}s`);
      await new Promise((r) => setTimeout(r, wait * 1000));
      continue;
    }
    await new Promise((r) => setTimeout(r, 5_000 * attempt));
  }
}

function printItems(items = []) {
  const rejected = items.filter((i) => i.status === "rejected");
  const counts = items.reduce((acc, i) => ((acc[`${i.status}:${i.code}`] = (acc[`${i.status}:${i.code}`] || 0) + 1), acc), {});
  console.log("por código:", counts);
  for (const r of rejected.slice(0, 50)) console.log(`  rejeitado #${r.index} ${r.source ?? "?"}/${r.external_id ?? "?"}: ${r.code}`);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!/^https:\/\//.test(BASE) && !/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(BASE)) {
    fail("defina SR_ROTAS_BASE_URL (https://<host>)");
  }
  if (!/^srrk_[0-9a-f]{8}_[A-Za-z0-9_-]{43,}$/.test(KEY)) fail("defina SR_ROTAS_COLLECTOR_KEY (srrk_...)");

  if (args.ping) {
    const { status, data } = await call("GET", "/api/v1/radar/collector/ping");
    console.log(JSON.stringify({ status, ...data }, null, 2));
    process.exit(status === 200 ? 0 : 2);
  }
  if (!args.file) fail("informe o arquivo JSON (ou --ping)");
  const parsed = JSON.parse(await readFile(args.file, "utf8"));

  if (args.pois) {
    const pois = Array.isArray(parsed) ? parsed : parsed.pois;
    if (!Array.isArray(pois) || !pois.length) fail("arquivo sem 'pois'");
    for (let i = 0; i < pois.length; i += 50) {
      const { status, data } = await call("POST", "/api/v1/radar/collector/pois", { dry_run: args.dryRun, pois: pois.slice(i, i + 50) });
      console.log(`lote ${i / 50 + 1}: HTTP ${status}`, data);
      if (status !== 200) process.exit(2);
    }
    return;
  }

  const events = Array.isArray(parsed) ? parsed : parsed.events;
  if (!Array.isArray(events) || !events.length) fail("arquivo sem 'events'");
  const ping = await call("GET", "/api/v1/radar/collector/ping");
  if (ping.status !== 200) fail(`ping falhou: HTTP ${ping.status} ${ping.data.error || ""}`, 2);
  const maxBatch = Number(ping.data.limits?.max_batch) || 200;
  const baseRunId = args.runId || `run-${new Date().toISOString().replace(/[:.]/g, "-")}-${randomUUID().slice(0, 8)}`;

  for (let i = 0, page = 1; i < events.length; i += maxBatch, page++) {
    const body = {
      ...(args.dryRun ? {} : { run_id: `${baseRunId}-p${page}` }),
      dry_run: args.dryRun,
      events: events.slice(i, i + maxBatch),
    };
    const { status, data } = await call("POST", "/api/v1/radar/ingest", body);
    if (status !== 200) {
      console.error(`página ${page}: HTTP ${status}`, data);
      process.exit(2);
    }
    console.log(`página ${page}${data.idempotent_replay ? " (replay)" : ""}: recebidos ${data.received}, aceitos ${data.accepted}, inalterados ${data.unchanged}, rejeitados ${data.rejected}, pendentes de revisão ${data.pending_review}`);
    printItems(data.items);
  }
  console.log(args.dryRun ? "dry-run: nada foi gravado." : "Enviado. Os eventos aparecem no app somente após aprovação no Admin.");
}

main().catch((error) => fail(error?.message || String(error)));
