# Aplicar — Sr. Rotas 0.9.0 Alpha

Patch incremental sobre a `main` 0.8.0, commit esperado:

`a4b8b91bac5425ba6987a70f8c68888c45f814c1`

## Esta é a fase 0.9 inteira

### IA do Sr. Rotas
- perguntas rápidas;
- período Hoje / 7 / 30 / 90 dias;
- resposta com contexto determinístico da fase 0.8;
- limite de saída para controlar custo;
- amostra compacta de ofertas, sem OCR bruto;
- telemetria de tokens/duração/modelo;
- pergunta e resposta **não são gravadas** em `ai_usage_logs`.

### MCP do usuário
- cada motorista gera as próprias chaves;
- cada chave pode ter nome (ChatGPT, Claude, Cursor etc.);
- segredo mostrado uma única vez;
- servidor armazena somente SHA-256 + prefixo;
- até 6 chaves ativas por motorista;
- revogação individual;
- `last_used_at`;
- MCP passa a autenticar o motorista correto, em vez do token global do Alpha.

### Separação de custos
- `/api/v1/ask` = IA do Sr. Rotas, usa OpenAI e será cobrada em créditos na 0.10;
- `/mcp` = ferramentas determinísticas somente leitura; o cliente externo usa a própria IA e não consome OpenAI do Sr. Rotas.

## SQL obrigatório

Antes do deploy, execute manualmente:

`supabase/migrations/20260817_ai_mcp_09.sql`

Ele cria:
- `mcp_access_tokens`;
- `ai_usage_logs`.

Nenhum SQL será executado automaticamente por este ZIP.

## Aplicação

1. Execute a migration no Supabase.
2. Extraia o ZIP na raiz do repositório.
3. Commit/push na `main`.
4. Aguarde GitHub Actions + Vercel.
5. Não é necessário baixar/gerar APK de teste agora; podemos acumular as próximas fases antes do beta ampliado.

## Variáveis

Não há variável nova.

`MCP_API_TOKEN` deixa de ser necessário para autenticação MCP de usuários. Se ainda existir na Vercel, pode permanecer por enquanto; o código 0.9 simplesmente não o utiliza.

`OPENAI_API_KEY` continua opcional para o restante do app, mas é necessária para a IA própria responder.

## Testes mínimos de backend

- build Android;
- build Vercel;
- criar uma chave MCP depois que o APK futuro estiver disponível;
- confirmar que chave revogada deixa de autenticar;
- confirmar que `/api/v1/ask` cria linha em `ai_usage_logs` sem armazenar pergunta/resposta.
