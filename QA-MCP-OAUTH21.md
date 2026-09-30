# QA — MCP OAuth 2.1 Sr. Rotas

## Gate 1 — discovery
GET:
- `https://srrotas.com/.well-known/oauth-protected-resource/mcp`
- `https://srrotas.com/.well-known/oauth-authorization-server`

Esperado:
- resource = `https://srrotas.com/mcp`
- authorization server = `https://srrotas.com`
- scope = `srrotas.read`
- PKCE = S256
- grant types = authorization_code + refresh_token
- DCR anunciado.

## Gate 2 — endpoint protegido
Abrir/chamar `https://srrotas.com/mcp` sem token.

Esperado:
- HTTP 401;
- `WWW-Authenticate` apontando para `/.well-known/oauth-protected-resource/mcp`.

## Gate 3 — cliente real
Em ChatGPT, Claude, Cursor ou cliente MCP compatível:
1. adicionar servidor MCP remoto;
2. informar apenas `https://srrotas.com/mcp`;
3. cliente deve descobrir OAuth/DCR;
4. navegador abre consentimento Sr. Rotas;
5. entrar com conta do motorista;
6. autorizar.

Esperado:
- nenhuma chave manual copiada;
- consentimento mostra nome do cliente + redirect URI;
- só aparece permissão de leitura.

## Gate 4 — toolset
Após conexão:
- listar ferramentas;
- confirmar `get_srrotas_capabilities`;
- confirmar annotations readOnly/destructive=false/idempotent=true;
- chamar `get_srrotas_capabilities`;
- chamar `get_driver_summary`;
- chamar `list_journeys`;
- chamar uma busca `search_offers`.

Esperado:
- dados somente do motorista autenticado;
- nenhuma ferramenta de escrita;
- nenhuma chamada à OpenAI do Sr. Rotas;
- nenhum consumo de crédito da IA interna.

## Gate 5 — auditoria
No Supabase/Admin:
- `mcp_tool_audit_logs` deve registrar tool, client_id, status, duração e hash dos argumentos;
- não deve persistir resposta completa do tool.

## Gate 6 — refresh
Após emitir um par:
- refresh_token A gera access B + refresh B;
- repetir refresh A deve falhar com `invalid_grant`;
- refresh B permanece válido até o limite original de 30 dias.

## Gate 7 — revogação do usuário
Em `/app/mcp`:
- conexão OAuth aparece em "Clientes autorizados";
- clicar Desconectar.

Esperado:
- grant revogado;
- access token existente passa a receber 401;
- refresh token existente deixa de renovar.

## Gate 8 — revogação Admin
Admin → usuário → Revogar MCP.

Esperado:
- revoga chave manual `srmcp_...`;
- revoga grants OAuth;
- revoga access/refresh OAuth.

## Gate 9 — compatibilidade manual
Criar uma chave no modo avançado.

Esperado:
- chave `srmcp_...` continua funcionando;
- endpoint continua `https://srrotas.com/mcp`;
- revogação continua funcionando.

## Gate 10 — segurança
Confirmar:
- nenhum token/código/secret em texto puro no banco;
- PKCE S256 obrigatório;
- redirect URI diferente da registrada é rejeitada;
- authorization code só pode ser usado uma vez;
- refresh token é rotativo;
- Access Resolver ainda é consultado em cada chamada MCP.
