# Reader R3-Lab — contrato experimental (branch `feature/reader-r3-lab`)

**Estado (11/11/2026):** promovido a Reader principal no APK operacional **vc112** por autorização expressa (issue #7) — ver README-CONTINUIDADE, seção vc112. O APK `r3lab` continua como laboratório isolado. Homologação física pendente. M1/MediaProjection
continua o leitor oficial; Reader2 continua shadow; Controlled Hybrid OFF. Base: `main` `a18df43` (vc111, `0.33.34-field`).
Origem: dossiê de transferência R3 de 10/11/2026 (23 rodadas de investigação estática) e fundação Fase 0/1 revisada.
Nada do binário, perfis ou scripts do concorrente foi copiado; os perfis R3 são próprios.

## 1. Isolamento (decisão principal)

| Item | Operacional (`release`, `debug`) | APK experimental (`r3lab`) |
|---|---|---|
| Pacote | `com.srrotas.app` / `.debug` | `com.srrotas.app.r3lab` (instala ao lado, não substitui) |
| Nome | Sr. Rotas | **Sr. Rotas R3-Lab** + ícone **R3-Lab** |
| `BuildConfig.R3_LAB` | `false` → ganchos R3 inertes | `true` |
| Acessibilidade (`packageNames`) | `com.ubercab.driver` (inalterado) | `com.ubercab.driver,com.app99.driver` |
| Telas R3 (`R3LabActivity`, `R3EvidenceViewerActivity`) | ausentes do manifest | `src/r3lab/AndroidManifest.xml` |
| Backend/push | inalterado | `backendUrl`/`deviceToken` vazios, OneSignal sem ID: nenhuma sincronização com produção |
| Assinatura | chave de campo | debug do CI (artefato privado; trocar de build pode exigir desinstalar o R3-Lab) |

Não há segundo serviço de Acessibilidade: o `DriverAccessibilityService` existente recebe três ganchos
(`attach`/`onEvent`/`detach`) que só agem quando `BuildConfig.R3_LAB` é verdadeiro. Os arquivos congelados (M1, router,
parsers financeiros, gates, Reader2) não foram alterados.

## 2. Arquitetura (`android/app/src/main/java/com/bigcorps/driveraimvp/r3lab/`)

```
R3Supervisor (thread r3-lab, tick 1 s; tick ≠ screenshot ≠ OCR)
 ├─ R3LabGate          fail-closed: build lab, laboratório ON, aviso aceito, Acessibilidade ON, API ≥ 30,
 │                     tela ligada, M1 deste APK inativo, app operacional NÃO visível, M2/Comparar OFF, alvo Uber/99
 │                     (ganchos guardados por BuildConfig.R3_LAB: nas builds operacionais R3Supervisor nem é carregado)
 ├─ R3WindowSelector   só janelas de aplicativo Uber/99 com tamanho útil; sem getRoot() só com evento recente do
 │                     mesmo windowId (≤ 3 s); Uber + 99 em tela dividida = dois alvos; em API 30–33 alvo coberto
 │                     por outra janela (notificação, bolha) é pulado — o recorte de tela não captura outro app
 ├─ R3CaptureLease     1 screenshot em voo; token + prazo 5 s; callback vencido não libera token novo
 ├─ captura            API ≥ 34 takeScreenshotOfWindow (janela); API 30–33 takeScreenshot + recorte da janela;
 │                     HardwareBuffer fechado em finally; backoff por código (intervalo curto, janela segura, inválida)
 ├─ R3FrameGate        assinatura 64×64 por janela; igual não vai ao OCR; refresh forçado 2,5 s
 ├─ R3Exclusivity      (issue #6) coordenador ÚNICO de screenshot, OCR ao vivo e replay: no máximo uma tarefa
 │                     ML Kit por vez; só a conclusão REAL do mesmo ticket a encerra (callback antigo ignorado);
 │                     tarefa > 30 s ENVENENA (falha fechado: nenhum OCR novo até ela concluir; se nunca concluir,
 │                     "Forçar parada" do R3-Lab); início de screenshot atômico com a reserva do replay
 ├─ R3LiveOcrScheduler só o quadro mais recente espera; inicia apenas com ticket LIVE concedido
 ├─ interpretação      C: DriverPlatformOfferRouter oficial | D: R3ProfileExtractor (perfil versionado) →
 │                     FlexibleDriverOfferParser oficial; ambos → OfferIntegrityGate027033 (sem bypass)
 ├─ R3Stabilizer       agrupa o MESMO card (janela + plataforma + campos); emite após 3,5 s ocioso (> refresh 2,5 s)
 │                     ou 20 s; mesma identidade em ≤ 10 s após emitir não abre outra oferta;
 │                     campo ausente só vem de observação do mesmo candidato, marcado RECOVERED
 ├─ R3LabDb            banco isolado r3_lab.db: r3_frame, r3_observation, r3_lab_offer, r3_offer_evidence,
 │                     r3_ground_truth — commit atômico oferta + vínculos
 ├─ R3EvidenceStore    files/r3lab/frames/<frameId>.webp (lossless), .tmp + fsync + rename, SHA-256 dos bytes
 └─ R3LabTelemetry     contadores/latências locais, exportação redigida
```

Perfis: `R3ProfileBundle` imutável (id, geração, SHA-256 do conteúdo, regras com nº de grupos = nº de campos
validado); `R3ProfileManager` faz validação → troca atômica → rollback; cada `R3CaptureEnvelope` congela o perfil
usado do screenshot até a observação. Perfis iniciais: `uber-r3-1`, `99-r3-1` (99 **sem fixture real**: experimental).

## 3. Identidade e evidência (não negociável)

`frameId` (quadro/recorte usado no OCR) → `observationId` (OCR + interpretação daquele quadro) → `candidateId`
(estabilizador) → `labOfferId` (gravado no banco do laboratório). N:N: um quadro pode ter várias ofertas e uma
oferta vários quadros (PRIMARY + até 4 SUPPLEMENTAL). O vínculo é gravado **somente** no commit da oferta, com os IDs
exatos das observações que a compuseram — **nunca** por tarifa, distância ou horário. Recusado: quadro `PURGED`,
quadros de pacotes diferentes. Imagem só com consentimento separado; sem ele o quadro fica `FAILED/consent_off`.
Estados `STAGED → READY | FAILED | MISSING | PURGED` (transições do ledger). `READY` exige SHA-256 dos bytes
gravados; a visualização reconfere o hash e mostra `MISSING`/`sha256_divergente` sem abrir outra imagem.
Retenção: quadros não vinculados em 10 min, mais antigos que 7 dias, ou acima de 300 MB → `PURGED`.
O laboratório **não** grava `local_offers`, `ride_outcomes`, receita, histórico, HUD ou backend.
`RideOutcome.localOfferId` continua exclusivamente oficial.

## 4. Interface do laboratório (ícone R3-Lab)

Estado e bloqueio atual, janelas elegíveis, conflitos de Acessibilidade (lê `ENABLED_ACCESSIBILITY_SERVICES`),
consentimentos (aviso, laboratório, imagens), modo C/D, diagnóstico (screenshots, quadros iguais/novos, OCR e
latências p50/p95, candidatos, ofertas distintas, motivos de rejeição, bloqueios, cobertura de imagem), lista de
ofertas com **Ver imagem utilizada** (só visualização, SHA conferido, caixas dos campos daquela observação) separado de
**Reprocessar OCR** (replay C × D do mesmo quadro; reserva atômica bloqueia screenshot/OCR ao vivo, espera o screenshot e a tarefa em voo, roda um OCR e libera sempre; quadros atrasados ficam pendentes; nada gravado), rótulos TP/FP, registro de oferta
não detectada (FN), exportação redigida, zerar contadores e apagar imagens.

## 5. Homologação — matriz A/B/C/D

| Célula | Captura | Interpretação | Como medir |
|---|---|---|---|
| A | M1 (app operacional) | parser oficial | baseline: diagnóstico operacional + gabarito manual |
| B | M1 | alternativo | replay do mesmo OCR (`reader_evidence_111`, Reader2 shadow) |
| C | R3 (R3-Lab, modo C) | parser oficial | diagnóstico R3-Lab |
| D | R3 (R3-Lab, modo D) | perfil R3 → parser oficial | diagnóstico R3-Lab; C × D no mesmo quadro por "Reprocessar OCR" |

Regras: sessões **alternadas** (nunca M1 e R3 lendo ao mesmo tempo; o R3 se bloqueia se o M1 deste APK estiver ativo
ou se a interface do app operacional estiver visível); gabarito **independente** das leituras (observação manual
registrada como TP/FP/FN); contar **ofertas distintas**, nunca quadros/OCR/candidatos. Métricas: recall = TP/(TP+FN),
precisão = TP/(TP+FP), exatidão por campo (centavos, minutos inteiros, km com tolerância declarada), falsos positivos,
taxa de vínculo errado (meta **zero**), cobertura de imagem (aceitas com PRIMARY READY / aceitas), latência p50/p95,
CPU de processo e bateria (início/fim da sessão), bloqueios e falhas por código. Sanidade com 30–50 ofertas rotuladas,
depois ≥ 100 estratificadas (Uber/99, tela dividida, dia/noite, layouts). Com amostra pequena, reportar incerteza.
Registro por caso: `ID, data, build/SHA, aparelho, API, versão Uber/99, célula, perfil, frameId, observationId,
esperado, obtido, etapa de descarte, código, status da imagem, labOfferId, intervenção manual, bateria, notas`.

## 6. Instruções de campo (SM-X626B, Android 16)

1. Instalar o APK `sr-rotas-r3lab-apk` (artefato privado do Actions) — o app operacional continua instalado.
2. Abrir **R3-Lab** → ler e aceitar o aviso → ativar a Acessibilidade "Sr. Rotas R3-Lab" → voltar e ativar o laboratório.
3. Para guardar evidência visual, ligar "Guardar a imagem de cada oferta".
4. Para sessões C/D: encerrar a jornada/leitura M1 do app operacional e confirmar na tela R3-Lab ("leitura M1 do app
   operacional ENCERRADA", vale 12 h — sem a confirmação a captura fica bloqueada). No app operacional manter o Reader
   em M1 (M2/Comparar desligados). **Restrição de protocolo:** a Acessibilidade do lab só enxerga Uber/99 e não prova
   a ausência de OCR no app operacional; o bloqueio por interface operacional visível é apenas complementar.
5. Trabalhar normalmente no Uber/99; rotular cada oferta (TP/FP) e registrar as não detectadas (FN).
6. Ao fim: "Exportar diagnóstico" (sem dados pessoais). Imagens só são compartilhadas por escolha explícita.

## 7. Rollback

Se a tela mostrar "OCR travado", usar "Forçar parada" do R3-Lab nas configurações do Android e reabrir.
Desligar o laboratório na tela R3-Lab, ou desativar a Acessibilidade "Sr. Rotas R3-Lab", ou desinstalar o APK R3-Lab
(apaga banco e imagens do laboratório). O app operacional não depende do R3. Na branch: os ganchos são três linhas em
`DriverAccessibilityService` e a constante `R3_LAB=false` nas builds operacionais; remover a pasta `r3lab/`,
`src/r3lab/`, o buildType `r3lab` e o workflow `r3lab-apk.yml` desfaz tudo.

## 8. Limitações conhecidas

Sem verdade-terreno de campo ainda; nenhuma alegação de ganho sobre o M1 ou o concorrente. API 26–29 sem R3 (M1
continua). O 99 não tem fixture real. Janelas `FLAG_SECURE` aparecem pretas; o HUD operacional (FLAG_SECURE) pode
cobrir parte do recorte em API 30–33. O roteador oficial do modo C atualiza memórias internas (painel 99 e contadores
do Reader2) **dentro do APK do laboratório**; os números do Reader2 do R3-Lab não valem como shadow do operacional.
A detecção do app operacional depende de `getRoot()` da sua janela: com `packageNames` restrito a Uber/99 ela pode não disparar — **não verificado em aparelho**; por isso a confirmação de protocolo é obrigatória e não há promessa de gate automático cross-app. Desligamento: geração invalidada, screenshot cancelado (callback tardio fecha o buffer), pendentes descartados, tarefa em voo dona do bitmap até concluir, gravação adiada só com o serviço vigente e o laboratório ativo (após opt-out nada é gravado). No modo C os totais calculados pelo parser oficial não são registrados como campos lidos. Assinatura debug do CI pode mudar
entre builds.

## 9. Perguntas à investigação (R3Q)

Só quando uma falha reproduzida no R3-Lab depender especificamente do concorrente, seguir o protocolo
`R3Q-XXXX` (componente, observado × esperado, fixture/frameId, SHA e linha Big Corps, rodada/offset, hipóteses,
evidência pedida, fallback seguro). Problemas de Android, concorrência, SQLite, ML Kit ou parser próprio são
investigados aqui. Nenhuma R3Q aberta nesta entrega.
