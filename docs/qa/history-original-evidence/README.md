# Issue #21 — consulta exata da captura no Histórico

Branch `fix/history-original-evidence`, base vc114/PR #19 `4a8f384`.
PR #19, main, versão 114 e assinatura não alterados; esta branch é independente do Admin #20.

## Causa e alteração

O Histórico condicionava o botão a `captureMethod.startsWith("r3-")` e status READY do quadro.
Isso escondia tanto ausência/retencão quanto vínculos existentes com rótulo diferente. Agora toda
oferta permite consultar sua disponibilidade por **localId completo** no toque; a lista não faz
consultas/hash de imagem por card. Corrida marcada como realizada continua usando o ID da mesma oferta.

O viewer reutiliza officialLink → frame/observation → verifiedFile, compara o SHA-256 armazenado,
mostra IDs completos, horário real do quadro e digest. Hash e decode ficam fora da thread UI; bitmap
de exibição limitado a 2048px, janela FLAG_SECURE e retorno após destroy/rotação descartado. Nenhum
OCR/replay, geocode, associação por horário/valor, substituição de quadro ou publicação de imagem.
Ausência/consentimento desativado ou revogado/purga/arquivo perdido/SHA divergente/erro são explicados
sem expor caminho interno. Falha não impede consultar o card.

## Fontes avaliadas separadamente

| Origem | Evidência existente / decisão nesta rodada |
| --- | --- |
| R3 Uber/99 | Vínculo oficial exato gravado após persistência; hash dos bytes guardados. Consentimento de imagem separado; retenção padrão 7 dias, configurável 1–30, quota 300 MB. Reutilizado sem alterar captura/storage/política. Disponibilidade depende do quadro real, não da plataforma ou categoria do registro. |
| M1 | privateScreenshotEnabled e opção Screenshots das ofertas; PrivateScreenshotStore grava recorte JPEG (qualidade 72), privado limitado a 30 arquivos e cópia visível local existente. Índice de novas gravações usa ID completo; legado sem índice não é varrido por nome/prefixo/hora/valor. Não existe digest original persistido nesse índice. Arquivo encontrado sem esse digest não é apresentado como imagem validada nem recebe hash retrospectivo. |
| Digitalização Uber | HistoryReviewStore109/UberDigitizationStore guardam campos reconhecidos, sem vínculo de imagem por registro. Card explica Captura original não disponível. Importação não confirma corrida nem autoriza escolher um frame aproximado. |

Não foram criadas novas retenções, permissões, migrações, cópias/cloud ou gravações no caminho do
Reader. Ampliar a cobertura M1/importações exige projeto separado de consentimento, vínculo exato,
hash de origem, quota/rotação/limpeza e revisão de privacidade. Não se pode recuperar bytes nunca salvos.

## Verificações

CI direcionada: compilação Kotlin, arquitetura e comparação com vc114 de MediaProjectionOcrService,
OfferDispatcher, JourneyBubbleController, OverlayController, R3Supervisor, R3Mode e R3LabStorage.
Esses arquivos permanecem byte a byte idênticos. Código de OCR/parsers/estabilização/captura/recuperação
não alterado. Sem APK gerado: nenhum signing secret ou upload de instalável no workflow desta rodada.

Testes: 0/1/50 vínculos por IDs exatos, chave errada, tentativa de substituir vínculo, consentimento,
SHA divergente, missing/purged, caminho inválido, índice M1 sem digest, ação do card, viewer,
FLAG_SECURE, rotação e identificadores/hash; regressões R3Primary/Storage/Concurrency, ReaderHealth113,
HistoryReview109/HistoryCapture111/HistoryReview111 e Field114Integration.

A primeira tentativa tinha uma fixture que tentava READY→READY, transição corretamente proibida.
Fixture corrigida para STAGED→READY com caminho inválido; ledger de produção preservado. Resultado
final: código `6f43cffbbd82bd436b0811e6d01ce7c9bf629024`, [CI 43640985108](https://github.com/BigCorps/SrRotas/actions/runs/43640985108) aprovada: 87 testes reportados, 86 aprovados, um ignorado (lab-only na variante operacional), zero falhas/erros.
[XMLs](https://github.com/BigCorps/SrRotas/actions/runs/43640985108/artifacts/14131832952).
O commit posterior apenas documental não altera o código validado e não repete Gradle. Testes Android extensos não foram executados para a branch Admin.

## Homologação física pendente — Samsung SM-X626B / Android 16

1. Identificar tela e origem do registro relatado (oferta, realizada, importação Uber); preservar dados.
2. Em oferta R3 com imagem consentida ainda retida, tocar Ver captura original; comparar quadro correto,
   IDs e hora, digest, legibilidade e Voltar. Rotacionar e reabrir sem trocar a imagem.
3. Em registro sem imagem/consentimento, purgado ou M1 legado/importado, conferir explicação honesta;
   nunca aceitar outra foto como substituta. Não apagar/adulterar arquivos reais de motorista para testar.
4. Voltar ao Histórico e continuar leitura normalmente; conferir jornada, R3/M1/HUD e ausência de duplicação.
   Os testes de adulteração e path são isolados; não repetir em dados reais.
5. Registrar versão, origem, resultado e hora. Evidências reais ficam privadas; FLAG_SECURE protege a
   tela, portanto não desligar a proteção para obter screenshot da oferta. Nenhum APK novo autorizado
   ou entregue nesta rodada; eventual instalável será exclusivamente app-release.apk principal assinado.

## Revisão do intervalo hash → decode

O apontamento era um risco real: a primeira implementação verificava um File e depois reabria o
caminho para bounds/pixels. Troca do arquivo nesse intervalo poderia apresentar conteúdo diferente
do digest exibido. O fluxo agora entrega um VerifiedCaptureSnapshotV1, não um File para a UI.
Lê por um único descritor em buffer de tamanho conhecido e limitado a 8 MiB, verifica novamente
o SHA-256 dos bytes lidos e decodifica bounds/pixels da mesma instância privada. Se os bytes mudarem
antes de formar o snapshot, o hash reprova; após formá-lo, renomear/substituir/apagar o caminho não
troca a imagem. Nenhuma nova persistência ou associação de imagens.

O snapshot é fechado em use/finally, zerado e liberado imediatamente após decode. O bitmap existente
continua limitado a 2048px e ao lifecycle do viewer. Snapshot acima de 8 MiB é recusado com motivo
explícito; o limite de armazenamento/retencão R3 continua intacto. A quota do arquivo guardado não
é a mesma coisa que o limite de memória de uma visualização. Não se cria arquivo temporário.

Testes novos com decoder nativo: original vermelho validado → caminho substituído por azul →
pixels exibidos continuam vermelhos e digest original; substituição antes da leitura do snapshot
reprovada; deleção depois não reabre caminho nem busca substituto; limite codificado e decode
2048px; snapshot liberado e nenhum arquivo adicional. CI desta revisão em andamento.
