# Issue #20 — preparação segura da importação JSON (bloqueada pelo contrato)

Base `main=e9f7982`, branch `feature/admin-radar-json-import`. PR #19 e Android não alterados.

Causa confirmada: `/admin/radar` só contém listagem/revisão de coletores, eventos, POIs, decisões e
execuções; não existe importador administrativo nem estrutura de lotes administrativos identificada.
As APIs `historical_import_*`/`admin/importacoes` são de histórico e não são usadas nesta alteração.

## O que esta preparação faz

Adiciona **Importar JSON** ao painel após a mesma verificação administrativa já existente. O arquivo
é lido exclusivamente no navegador, sem envio, logs ou persistência. Verifica apenas UTF-8, sintaxe
JSON e limite técnico local de 2 MB (não é um limite contratual inventado). Nem JSON válido nem um
campo contendo `radar.admin-import.v2`/v1 provam compatibilidade ou tornam registros prontos.

Mostra claramente o bloqueio e contagens não avaliadas; confirmação desabilitada. Não há API nova,
seleção por item, batch_id, recibo persistente, gravação ou aprovação. Nenhum exemplo de envelope
administrativo foi criado. Não confundir essa preparação com o importador completo solicitado.

## Bloqueio real

O contrato de integração Radar v1.3 completo não foi fornecido. Buscas nos contratos e histórico Git
acessíveis, nos dois repositórios e no Drive conectado não encontraram o documento. As referências
ao Radar External Collector V2 são contratos diferentes e não autorizam inferir este esquema.

São necessários o envelope/identificador de versão original; campos/tipos obrigatórios de eventos
e locais; mapeamento e compatibilidade exata v1; regras de proveniência/direitos de uso, coordenadas
e fuso; identidades/atualização/colisão entre fontes, incluindo Ticketmaster; semântica de pendente,
pronto e duplicado; limites, seleção, idempotência e recibo batch_id. Até recebê-lo, prévia por item,
transação selecionada, lote administrativo, auditoria/concorrência e consulta por recibo estão bloqueados.
Não criar migração ou substituir batch_id por run_id do coletor por suposição.

## Verificação realizada

Node **22.23.5** / Next.js **16.3.6**: `npm run test:radar` 53/53; `npm run check`; `npm run build`.
`SR_RADAR_BROWSER_EXECUTABLE=/usr/bin/chromium npm run test:radar:isolated`: **21/21**, PostgreSQL
17.7/PostgREST 13.0.7 descartáveis. Regressões existentes de idempotência, concorrência, ACL/RLS,
revisão/aprovação/despublicação e rollback continuam verdes. Não são testes de commit administrativo
v1/v2, que ainda não existe sem contrato.

Teste novo de navegador usa sessão administrativa de teste real, acessa o tab, verifica botão
bloqueado, arquivo sintaticamente válido/inválido/excessivo, desktop e mobile sem overflow. Compara
snapshots completos de eventos, POIs, runs, decisões, revisões e uso antes/depois; nenhuma alteração.
Interceptação confirma zero requests de importação. Fixtures estritamente isoladas, nunca produção.

[Desktop](desktop.png) · [Mobile](mobile.png). Capturas reais do build local com sessão de teste;
preview visual local, não publicação produtiva. Nenhum teste Android executado, nenhum deploy manual,
nenhuma alteração de banco produtivo. Não ativar o fluxo completo nem afirmar compatibilidade v1/v2
antes de recuperar o contrato e testar os casos administrativos correspondentes.

## Revisão do PR #22 — navegação e nova proposta

Causa da sobreposição: `.srAdminUtilityNav` era fixed com z-index 180, sem área reservada. Agora a
mesma faixa visual/atalhos participa do fluxo, com quebra de linha, safe-area e tamanho real; não
se usa altura presumida nem padding fixo para esconder o problema. Inputs, botão e contagens não
ficam sob a faixa. Ícone de arquivo também cresce com a fonte, sem quebrar chaves sobre o título.

Nova matriz real de Chromium/Node 22: desktop 1365×900 e celular 390×844, cada um com fonte 100% e
150% efetiva (font sizes computados multiplicados, não viewport ampliada artificialmente). Testes
verificam retângulos sem interseção, hit-testing do input/botão/contagens após scroll, ausência de
overflow horizontal e texto do ícone dentro de sua área. Capturas abertas e inspecionadas:
[desktop](desktop.png), [mobile](mobile.png), [desktop 150%](desktop-font150.png),
[mobile 150%](mobile-font150.png). `npm run check`, `npm run build` Node 22 e 21/21 PostgreSQL/HTTP/
Chromium isolados aprovados, sem skips; inspeção continua sem requests de importação ou writes Radar.

Busca refeita em 14/11: issues/revisões, contratos/material dos dois repositórios, branches remotas
relacionadas e histórico Git acessível, documentação/arquivos recebidos em `/workspace/attachments`
(os três textos disponíveis tratam de outro projeto), Drive conectado por Radar/SrRotas e busca
GitHub. Nenhum documento original v1.3 recuperado. Não se afirma que ele não exista fora dessas fontes.
Documentos de piloto/coletores são outra interface e não autorizam compatibilidade administrativa.

Por pedido explícito do responsável, preparada **[nova proposta formal](../../contracts/admin-radar-import-proposal/PROPOSAL.md)**
com ID próprio `srrotas.radar-admin-import.draft.v0.1`, exemplos, erros e matriz a executar depois
que for aprovada e implementada. Não é compatível por suposição com v1.3/v1/v2. Nenhum endpoint,
migration, lote ou importação real foi ativado. Aprovação da proposta, desenho de banco/dedupe e
implementação/testes do fluxo completo continuam pendentes.
