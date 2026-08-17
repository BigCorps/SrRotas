# Changelog — Sr. Rotas 0.9.0 Alpha

## IA
- nova experiência de Pesquisa IA;
- sugestões de perguntas;
- seleção de período;
- contexto composto por analytics determinísticos + amostra recente;
- `store: false` no Responses API;
- máximo de saída configurado para reduzir consumo;
- gravação de input/output/total tokens para preparar a fase de créditos.

## MCP multiusuário
- removido o modelo Alpha de token global associado ao primeiro motorista;
- chaves MCP individuais por motorista;
- geração e revogação pelo app;
- hash em repouso;
- endpoint único `/mcp`;
- auditoria das ferramentas continua ativa.

## Princípio importante
O MCP não expõe uma ferramenta que chama a IA paga do Sr. Rotas.
Quem usa ChatGPT/Claude/Cursor via MCP usa o modelo do próprio cliente para interpretar os dados.

## Motor
Offer Engine v1 continua congelado em `sr-rotas-v0.5.4`.
