# Fase 0.9 — Checklist de aceite

- [ ] Migration `20260817_ai_mcp_09.sql` aplicada.
- [ ] Android CI passa.
- [ ] Vercel build/deploy passa.
- [ ] IA aceita período 1/7/30/90 dias.
- [ ] IA não recebe OCR bruto.
- [ ] `ai_usage_logs` registra tokens sem pergunta/resposta.
- [ ] Nova chave MCP é mostrada uma única vez.
- [ ] Banco guarda apenas `token_hash` e `token_prefix`.
- [ ] Token de um motorista não consulta dados de outro.
- [ ] Revogação invalida a chave.
- [ ] MCP continua read-only.
- [ ] MCP não chama `ask_sr_rotas`/OpenAI internamente.
- [ ] Histórico/analytics continuam sem IA.
- [ ] Offer Engine continua em `sr-rotas-v0.5.4`.

Aprovada a fase 0.9, a próxima é a **0.10 completa: Pix + assinatura + 20 créditos iniciais + carteira/ledger de créditos**.
