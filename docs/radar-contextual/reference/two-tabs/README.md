# Radar Android — revisão visual do PR #17

Branch `feature/radar-two-tabs`, base `e9f7982`, continuidade a partir de `5b63126`.
Correções orientadas pelos [dez pontos da revisão](https://github.com/BigCorps/SrRotas/pull/17#issuecomment-6867670275).

As imagens são Views Android nativas renderizadas pelo Robolectric API 35, com o painel operacional,
cabeçalho canônico e SrBottomNav023. **Todos os dados exibidos são sintéticos, exclusivamente de
`src/test`: seis eventos de tipos variados e seis POIs distintos.** Nenhum dado foi publicado ou
inserido no app operacional. Não são capturas de aparelho físico.

## Comparação dos dez pontos

| Ponto | Implementação / limite |
| --- | --- |
| 1. Seletor | Fundo azul-claro, borda azul forte, texto marinho; contraste correspondente no tema escuro. |
| 2. Categorias | Ícones brancos em quadrados arredondados coloridos e etiquetas. Tipos explícitos determinam rótulos: Show, Esporte, Feira, Evento, Teatro, Festival, Shopping, Hospital, Hotel. `family` permanece Família; nomes não inferem categoria. |
| 3. Títulos | Eventos próximos / Oportunidades próximas do destino, instrução e contagem discreta; status sem repetir a seção. |
| 4. Resumo | Destino real e ETA no contexto válido; Corrida ativa somente após gate. Por proximidade somente quando há resultados frescos realmente ordenados pela referência consultada. |
| 5. Cards | Hierarquia de título, categoria, distância, contexto/data/fonte e rodapé separado Ver no Maps. Duas colunas compactas no tablet; uma em celular/split/fonte 150%. Medição testa altura compacta e alinhamento dos rodapés. |
| 6. Diagnóstico | Botão abre DiagnosticExportActivity0270 existente por toque; Atualizar é auxiliar. Teste verifica o Intent. Exportação continua no fluxo humano existente. |
| 7. Densidade | Seis eventos e seis POIs em cada perfil/tema de QA, somente fixtures; teste exige seis cards renderizados. |
| 8. Aba inicial | Sem destino confirmado e ETA válida, abre Eventos em geral; preferência manual sobrevive a refresh/recriação. |
| 9. Identidade | Cabeçalho, navegação e temas canônicos preservados; sem mapa interno, probabilidades ou novo catálogo. |
| 10. Ativação | Layout/entradas manuais usam RadarVisualLayoutV1, independentemente de uiEnabled/runtime/assistente. Rollback visual não altera automação. Parar runtime/assistente continua uma ação explícita separada. |

## Evidência e reprodução

Código validado: `8bd58351bcf7fa5a931551562f79759f4c347913`.
[CI de compilação, regressões e capturas](https://github.com/BigCorps/SrRotas/actions/runs/43531633513).
JDK 17, SDK 36, Gradle 8.13: guard de arquitetura, `compileDebugKotlin`, suíte operacional
`testDebugUnitTest` e `RadarTwoTabsQa105Test` nativo. Resultados: 785 testes reportados, zero falhas/erros, dois ignorados (783 aprovados).
[Artefato completo com PNGs/XMLs](https://github.com/BigCorps/SrRotas/actions/runs/43531633513/artifacts/14087687938).
Nove PNGs selecionados inspecionados; SHA-256 em `evidence.json`. A gravação posterior de PNGs/docs
não altera o código testado e não repete Gradle/Actions pesados.

Matriz de **60 capturas**: duas abas, claro/escuro, tablet 800×1280, celular 390×844, largura de
tela dividida 360×800 e celular com fonte 150%. Os perfis com dados incluem capturas do topo e
rodapé; celular normal inclui também sem corrida, sem localização, carregamento, falha de rede,
vazio, cache e ETA expirados. Assertivas verificam seis cards, colunas, altura compacta no tablet,
ausência de reticências, cabeçalho e Diagnóstico visível na captura do rodapé.

| Perfil | Próximas do destino | Eventos em geral |
| --- | --- | --- |
| Tablet claro | [Imagem](tablet-claro-destination-ready.png) | [Imagem](tablet-claro-events-ready.png) |
| Celular escuro | [Imagem](celular-escuro-destination-ready.png) | [Imagem](celular-escuro-events-ready.png) |
| Tela dividida clara | [Imagem](split-claro-destination-ready.png) | [Imagem](split-claro-events-ready.png) |
| Fonte 150%, escuro | [Imagem](celular-fonte150-escuro-destination-ready.png) | [Imagem](celular-fonte150-escuro-events-ready.png) |

[Rodapé com Diagnóstico e fonte 150%](celular-fonte150-escuro-events-bottom.png).
As listas estreitas exigem rolagem para os seis itens; não se reduz a fonte para fazê-los caber.
Artefatos do Actions têm retenção de 14 dias; as nove prévias permanecem versionadas.

## Limites e aprovação pendente

- Comparação final com os mockups originais cabe ao responsável. As correções seguem o comentário
  detalhado da revisão; os arquivos originais não foram disponibilizados nesta sessão.
- Diferenças intencionais: indicadores só com evidência; sem percentuais de demanda fictícios,
  sem datas para POIs permanentes, sem converter Família em Festival nem inferir categorias pelo nome.
- Continuam necessários teste físico SM-X626B/Android 16, abertura do Maps, exportação/compartilhamento
  do diagnóstico, rotação, tela dividida real e retomada após suspensão. Robolectric não substitui campo.
- Regras aprovadas de corrida confirmada, ETA, localização, cache, filtros e invalidação de callbacks
  permanecem. Nenhuma última oferta vira corrida aceita. Leitores existentes seguem responsáveis
  por dados ativos/aprovados; atualizações/despublicações são observadas por refresh/expiração do cache.
- Sem alterações em main, PRs #13/#14, Reader, R3/M1, OCR, HUD, Histórico, backend, Supabase,
  credenciais ou coletor. Testes do coletor não repetidos. Sem merge/deploy/publicação ou APK.
- APK assinado padrão `app-release.apk` / `com.srrotas.app` e novo versionCode apropriado são etapa
  futura após aprovação visual e integração autorizada. O workflow atual é somente de testes,
  `contents:read`, sem signing secrets. A compilação local anterior passou; a suíte local depende de
  mirror Maven inacessível neste ambiente e por isso a execução completa usa GitHub Actions.
