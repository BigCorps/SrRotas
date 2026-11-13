# Radar Android — prévias nativas das duas abas

Branch `feature/radar-two-tabs`, base `e9f7982c990ebfdfcd7014897bc16a9b586cbd15`, PR [#17](https://github.com/BigCorps/SrRotas/pull/17).
Implementação isolada dos PRs #13 (R3/M1) e #14 (Histórico). Nenhuma integração na main.

As capturas são Views nativas renderizadas pelo Robolectric API 35, com o painel usado pelo shell,
contexto MainActivity para o cabeçalho canônico e SrBottomNav023 para a navegação inferior.
**Todos os nomes, coordenadas, horários e distâncias das capturas são fixtures sintéticas de teste.**
As fixtures estão exclusivamente em `src/test`, não alimentam o app operacional e não foram enviadas
a backend, Supabase, coletor, Admin ou aparelho de motorista. Não houve aprovação de registro.

## Evidência conferida

Código validado: `6360a71adee8b9db5c578dcd79fe9ee525396e1e`.
[CI final aprovado](https://github.com/BigCorps/SrRotas/actions/runs/43526885288), JDK 17, SDK 36,
Gradle 8.13: `compileDebugKotlin`, suíte operacional `testDebugUnitTest` (incluindo os 13 testes
novos de comportamento do Radar), guard de arquitetura e teste nativo de screenshots aprovados.
[Artefato com matriz completa e XMLs](https://github.com/BigCorps/SrRotas/actions/runs/43526885288/artifacts/14085333035).
Os quatro PNGs selecionados foram lidos e inspecionados, com SHA-256 conferido em `evidence.json`.
A gravação posterior das evidências/documentação não muda o código validado; CI pesado não é repetido
para esse commit documental.

A compilação Kotlin também passou localmente com SDK 36/JDK 17. A suíte local foi bloqueada antes
da execução pelo download de `nativeruntime-dist-compat` (mirror Maven sem acesso neste ambiente);
a execução completa e as capturas foram verificadas no Actions. O workflow é só de testes e usa
`contents:read`, sem signing secrets. Não foram repetidos os testes do coletor PR #6.

## Matriz reproduzível

`RadarTwoTabsQa105Test`, via `Radar Android UI checks`: 44 capturas, duas abas × claro/escuro,
800×1280 (tablet), 390×844 (celular), 360×800 (largura de tela dividida) e celular com fonte 150%.
O celular normal inclui estados com dados, sem corrida, sem localização, carregando, erro,
sem dados, cache expirado e ETA expirada. A matriz mede a largura efetiva do contêiner e afirma
que dois cards compartilham a mesma linha no tablet e ocupam linhas distintas nas larguras estreitas.
Os testes também verificam ausência de reticências/cortes e os ícones canônicos do cabeçalho.

Quatro prévias selecionadas ficam versionadas neste diretório. A matriz inteira e os XMLs das
regressões ficam no artefato privado do Actions (retenção 14 dias). Nenhum APK é gerado pelo job.

| Contexto | Prévia |
| --- | --- |
| Tablet claro, destino | [Abrir](tablet-claro-destination-ready.png) |
| Celular escuro, eventos | [Abrir](celular-escuro-events-ready.png) |
| Tela dividida clara, destino | [Abrir](split-claro-destination-ready.png) |
| Celular escuro, fonte 150% | [Abrir](celular-fonte150-escuro-events-ready.png) |

## Dados e limites da validação

- A aba destino exige o resolver de corrida confirmada já existente e ETA futura; revalida o mesmo
  destino antes de aceitar respostas. Não usa a última oferta como corrida aceita.
- Eventos futuros vêm do leitor autenticado existente, aprovado/ativo no servidor, e são filtrados
  e ordenados por distância da referência consultada, sem alterar o score global do backend.
- A referência regional é o fix verdadeiro consultado, com horário explícito. Pequenas oscilações
  de GPS até 100 m conservam o fix da consulta; deslocamento maior, perda de localização, pausa ou
  saída invalidam a consulta. Não há arredondamento, centroide ou coordenada inventada.
- Cache de eventos até 5 minutos e contextual até 3 minutos; ao vencer, o painel mostra estado de
  expiração sem cards apresentados como atuais. Despublicação no servidor exige refresh ou expiração
  do cache para ser observada no aparelho. O coletor V2 não precisa de outra API para alimentar a UI.
- POIs permanentes não são apresentados como eventos. Nenhuma probabilidade ou incidência é criada.
  Distâncias de POIs são calculadas com suas coordenadas verificadas e o destino real da consulta.
- Testes e capturas usam Android API 35 em JVM; não são execução física no SM-X626B/Android 16.
  São necessários campo, rotação/tela dividida real, Maps no aparelho e lifecycle após suspensão.

## Aprovação visual pendente

Os três mockups recentes e `HANDOFF_VISUAL_RADAR_SR_ROTAS.md` não estavam nos anexos desta sessão
nem nos arquivos/links da issue #11 consultados. A implementação segue a descrição e a autorização
escritas no comentário atualizado da issue. Comparação exata com as imagens originais continua
pendente da disponibilidade dos arquivos e da revisão do responsável.

Cabeçalho, navegação inferior e temas usam os componentes canônicos. Não houve alteração de Reader,
R3/M1, OCR, HUD, Histórico, backend, schema, credenciais, fontes ou versão do APK. O controle Field
Rollback conserva o painel legado sem habilitar runtime/assistente. Homologação e eventual integração
com PRs críticos são ações posteriores; nenhum merge ou publicação Play foi realizado.
