# Sr.Rotas 0.25.0 — ZIP 02/04 — Jornada + OCR contínuo

Aplicar **depois** do ZIP 01 (`SrRotas-0.25.0-ZIP-01-Parser-Radar.zip`).

Este pacote não altera Supabase, Vercel, banco, APIs, UI, parser de ofertas, configurações do HUD nem a versão do aplicativo. Ele corrige somente a estabilidade da captura MediaProjection/OCR durante jornadas longas.

## Arquivos desta rodada

- `android/app/src/main/java/com/bigcorps/driveraimvp/MediaProjectionOcrService.kt`
- `android/app/src/main/java/com/bigcorps/driveraimvp/CaptureHealthPolicy025.kt`
- `android/app/src/test/java/com/srrotas/app/CaptureHealthPolicy025Test.kt`

## Problema tratado

Em alguns testes reais a jornada permanecia ativa, porém o OCR parava de ler novas ofertas. Para voltar a funcionar era necessário abrir a bolha, encerrar a jornada e iniciá-la novamente.

Foram encontrados dois estados capazes de produzir esse comportamento:

1. `onCapturedContentVisibilityChanged(false)` fazia o serviço descartar todos os frames enquanto o flag permanecesse falso.
2. Não existia watchdog para detectar `ImageReader`/surface sem novos frames ou um Task do ML Kit que permanecesse ocupado por tempo anormal.

## Correções

### 1. Visibilidade deixa de bloquear OCR

O callback de visibilidade continua registrado e logado, mas passa a ser **diagnóstico**, não uma trava do pipeline. Isso evita uma pausa permanente ao alternar entre Sr.Rotas, Uber, 99, split-screen ou mudanças de visibilidade reportadas pelo Android.

### 2. Watchdog conservador da captura

A cada 4 segundos o serviço avalia a saúde da sessão.

- 12 segundos sem receber nenhuma imagem, com tela ligada e a mesma jornada ainda proprietária da MediaProjection: recria somente `ImageReader` + surface e reconecta ao `VirtualDisplay` existente.
- A MediaProjection e o `journeyId` continuam os mesmos.
- Existe cooldown de 8 segundos para impedir loops rápidos de recuperação.

### 3. Watchdog do ML Kit

Se um OCR permanecer ocupado por 18 segundos:

- uma nova geração do pipeline é aberta;
- o cliente `TextRecognizer` é recriado;
- frames pendentes antigos são descartados com segurança;
- callbacks atrasados da geração antiga ficam inertes e não alteram a fila nova.

### 4. MediaProjection realmente encerrada

Se o Android/usuário encerrar a própria MediaProjection, o Sr.Rotas **não tenta reutilizar silenciosamente a autorização**. A sessão é liberada e o estado da jornada é atualizado imediatamente. Isso preserva as regras de consentimento do Android.

### 5. `START_NOT_STICKY` preservado

Não foi trocado por restart automático do foreground service. Após morte real do processo não é seguro presumir que o token de MediaProjection possa ser reutilizado sem nova autorização.

## Proteções contra regressão

- sampling de 250 ms mantido;
- limite OCR de 2100 px mantido;
- `FrameChangeDetector` mantido;
- roteamento Uber/99/outros mantido;
- `OfferDispatcher`, estabilização, deduplicação e HUD mantidos;
- screenshots privados mantidos;
- resize Android 14+ mantido;
- mesma jornada não é recriada durante uma recuperação de surface/OCR;
- tela apagada não dispara recuperação;
- não há mudança em `build.gradle.kts` nesta rodada.

## Validação realizada antes de empacotar

- `CaptureHealthPolicy025.kt` compilado isoladamente com `kotlinc`;
- smoke tests executados para: captura saudável, ausência de frames, OCR preso, tela apagada e cooldown;
- varredura estrutural do `MediaProjectionOcrService.kt` sem erro sintático Kotlin;
- verificação automática de que o antigo bloqueio `if (!capturedContentVisible)` não permanece;
- verificação de presença do rearm de surface, reset de OCR, generation guard e tratamento explícito de `projection_stopped_by_system`.

O build Android completo será validado no repositório depois que as quatro rodadas forem aplicadas, antes do único APK da 0.25.0.

## Não fazer ainda

- não gerar APK por causa deste ZIP;
- não alterar `versionCode` ou `versionName`;
- não executar SQL;
- não alterar Supabase/Vercel;
- não remover os arquivos aplicados pelo ZIP 01.

Depois deste pacote, a próxima rodada é o **ZIP 03/04 — Inteligência**: Base Coletiva/fallback + “Nova corrida no destino” no HUD, preservando Busca e Destino já validados.
