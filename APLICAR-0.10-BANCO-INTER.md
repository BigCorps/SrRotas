# Aplicar — Sr. Rotas 0.10.0 Alpha — Banco Inter BigCorps

Este ZIP **substitui integralmente** o ZIP 0.10 anterior que usava OpenPix. Não aplique o ZIP OpenPix.

Base esperada: `61db2913948ced915fab781604d1a5c7e6432a83` (0.9.0-alpha).

## Arquitetura alinhada ao MonitorIA

A cobrança usa a mesma ponte Banco Inter da BigCorps:

- geração: `POST https://inter.btsolucao.com.br/cob.php`
- consulta: `GET https://inter.btsolucao.com.br/get.php?txid=...`
- autenticação da ponte: `Authorization: Bearer BANCO_INTER_API_KEY`

No Sr. Rotas, Banco Inter fica isolado em Supabase Edge Functions:

- `srrotas-create-pix`
- `srrotas-check-pix`
- `srrotas-process-billing`

O Next.js nunca recebe `BANCO_INTER_API_KEY`.

## 1. SQL manual

Execute:

`supabase/migrations/20260817_billing_inter_credits_010.sql`

## 2. Secrets das Edge Functions do projeto Sr. Rotas

No Supabase **Sr. Rotas** configure:

- `BANCO_INTER_API_KEY` = o mesmo segredo já usado pelo MonitorIA/conta Inter BigCorps;
- `BANCO_INTER_BRIDGE_BASE_URL=https://inter.btsolucao.com.br`

Não coloque `BANCO_INTER_API_KEY` na Vercel, GitHub ou Android.

## 3. Deploy das 3 Edge Functions

Deploy com `verify_jwt=true`:

- `srrotas-create-pix`
- `srrotas-check-pix`
- `srrotas-process-billing`

Cada função usa os arquivos compartilhados:

- `supabase/functions/_shared/pix.ts`
- `supabase/functions/_shared/service-auth.ts`

## 4. Vercel

Não há OpenPix e não há webhook OpenPix.

Opcional durante o Alpha:

`BILLING_ENFORCEMENT=false`

Mantenha `false` enquanto os testadores continuam gerando dados sem cobrança obrigatória.

## 5. Portal

`https://sr-rotas.vercel.app/conta`

Fluxo:

conta → gerar Pix → Edge `srrotas-create-pix` → Banco Inter → QR/copia-e-cola → polling → Edge `srrotas-check-pix` → assinatura/créditos.

A página consulta o status a cada 5 s enquanto há Pix pendente. O backend evita consultar o Banco Inter mais de uma vez a cada ~4 s para o mesmo pagamento.

## 6. Confirmação em background

`srrotas-process-billing` faz a varredura de cobranças pendentes, como no MonitorIA. Nesta fase ele fica pronto para ser chamado por cron/service-role; o polling da página também confirma pagamentos mesmo sem cron.

## Créditos

- plano: R$ 9,90 / 30 dias;
- primeira ativação paga: +20 créditos, uma única vez;
- pergunta IA concluída: 1 crédito;
- falha técnica: crédito reservado volta;
- MCP/OCR/HUD/analytics: zero créditos;
- pacotes avulsos continuam desabilitados até medirmos custo real.

## Android

O Android não gera Pix nem mostra QR Code. Apenas mostra plano e saldo.
