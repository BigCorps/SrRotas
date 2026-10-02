# Gate 4 — Comercial: Copiloto gratuito + Inteligência

Base: `f018618feb49f2d12b4016a15311e976ce853c75`

## Contrato
- Sr. Rotas Copiloto: gratuito e permanente para contas não bloqueadas.
- Sr. Rotas Inteligência: R$ 9,90 / 30 dias.
- Trial: 7 dias de Inteligência, iniciado na primeira oferta operacional válida.
- Sem cobrança automática.
- Pergunte determinístico não consome créditos.
- MCP continua somente leitura.
- `core_monthly` é preservado por compatibilidade interna.

## Capability split em `enforce`
Copiloto:
- can_operate = true
- can_history = true
- can_billing = true
- can_profile = true

Inteligência:
- can_analytics = trial/paid
- can_ai = trial/paid
- can_mcp = trial/paid

`BLOCKED` continua bloqueando operação e leitura.

## Rollout
A migration NÃO muda `enforcement_mode`. Produção permanece `observe` até:
1. CI/Vercel verdes;
2. validar resolver por estados;
3. testar geração Pix real R$ 9,90;
4. confirmar pagamento/renovação;
5. só então promover para `enforce`.

## Créditos legados
As tabelas e saldos existentes são preservados para auditoria/compatibilidade.
Nenhum trial ou pagamento novo concede créditos.
