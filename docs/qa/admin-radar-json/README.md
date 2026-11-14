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
