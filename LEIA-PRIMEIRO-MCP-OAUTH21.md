# Aplicar — Sr. Rotas MCP OAuth 2.1

Base esperada: `4acce57e6885b69dff1bcdadd7de8aefa2f43661`.

## Supabase
As duas migrations deste ZIP JÁ foram aplicadas via MCP:
- 20260929224538_pre1_mcp_oauth21_complete_20260929
- 20260929224602_pre1_mcp_oauth21_fk_indexes_20260929
- 20260929225306_pre1_mcp_oauth21_retention_20260929

Não execute SQL manualmente.

## O que muda
- OAuth 2.1 + PKCE S256;
- Dynamic Client Registration;
- discovery RFC 8414/RFC 9728;
- refresh token rotativo;
- revogação;
- consentimento usando a sessão Web já existente;
- conexões OAuth visíveis/revogáveis em `/app/mcp`;
- chaves manuais preservadas;
- Admin "Revogar MCP" passa a revogar OAuth + manual;
- cron limpa requests/codes/tokens OAuth expirados;
- documentação pública do conector.

## Não muda
- Android;
- Reader;
- Money;
- HUD;
- Histórico;
- V7;
- preço/plano;
- crédito de IA;
- tools MCP existentes além das annotations/version.

## Depois do deploy
Não enviar APK; esta entrega é Web/backend.
Validar:
- discovery;
- DCR;
- autorização;
- token exchange;
- tools/list;
- uma ferramenta real;
- refresh;
- revogação e 401 após revogar.
