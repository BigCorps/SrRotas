# Sr. Rotas — MCP OAuth 2.1 completo

Base de código: `4acce57e6885b69dff1bcdadd7de8aefa2f43661`

## Arquitetura
O MCP do Sr. Rotas mantém o toolset existente e adiciona autenticação OAuth 2.1 completa.

Fluxo:
1. cliente recebe `https://srrotas.com/mcp`;
2. `/mcp` responde 401 com `resource_metadata`;
3. cliente consulta `/.well-known/oauth-protected-resource/mcp`;
4. cliente consulta `/.well-known/oauth-authorization-server`;
5. se necessário, registra-se via DCR;
6. inicia Authorization Code + PKCE S256;
7. Sr. Rotas cria request de consentimento de 10 min;
8. usuário entra pela sessão Web existente e aprova;
9. authorization code de 5 min e uso único;
10. access token de 1 h;
11. refresh token rotativo com janela máxima de 30 dias;
12. cada chamada ainda passa pelo Access Resolver;
13. usuário ou Admin pode revogar tudo.

## Segurança
- códigos, access tokens, refresh tokens e client secrets somente em SHA-256;
- PKCE S256 obrigatório;
- redirect_uri precisa coincidir exatamente com o DCR;
- HTTPS obrigatório para Web; HTTP apenas localhost; private-use URI permitido para app nativo;
- apenas `srrotas.read`;
- OAuth grants revogáveis;
- refresh token rotativo e single-use;
- replay de authorization code bloqueado;
- DCR com rate-limit;
- RLS ligada e tabelas sem grants para anon/authenticated;
- tool annotations: readOnly=true, destructive=false, idempotent=true;
- ferramentas continuam sem ações sobre Uber/99.

## Compatibilidade
A chave manual `srmcp_...` continua funcionando como modo legado/avançado.
OAuth usa access token `srmcpo_...` e refresh token `srmcpr_...`.

## Escopo
`srrotas.read`

Não consome crédito da IA interna do Sr. Rotas.
