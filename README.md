# Sr. Rotas 2.0 — Alpha 0.9.0

**Desenvolvido pela BigCorps** — contato@bigcorps.com.br

## 0.9 — IA + MCP

O motorista agora pode escolher:

1. **IA do Sr. Rotas** — interpretação em linguagem natural usando a OpenAI configurada pelo serviço;
2. **MCP** — conecta seus dados ao ChatGPT, Claude, Cursor ou outro cliente compatível e usa a IA do próprio cliente.

## Custos separados

- OCR/HUD/histórico/analytics/MCP: não chamam a OpenAI do Sr. Rotas.
- somente perguntas feitas à IA própria em `/api/v1/ask` usam modelo pago.
- 0.9 mede tokens reais.
- 0.10 transforma esse consumo em créditos e adiciona assinatura/Pix.

## Privacidade

`ai_usage_logs` não armazena pergunta nem resposta.
Chaves MCP são armazenadas somente como hash SHA-256.

## Offer Engine

`parser_version = sr-rotas-v0.5.4`.
