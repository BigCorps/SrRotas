# Aplicar — Sr. Rotas 0.13.0 Beta Candidate

Base esperada: `5484449b25bf5f9fbb1679568f5782d87594cdb1`.

## Ordem
1. Execute manualmente `supabase/migrations/20260818_closed_beta_013.sql`.
2. Extraia este ZIP na raiz do repositório.
3. Commit/push na `main`.
4. Aguarde Android CI e Vercel.
5. O primeiro APK gerado após esse push já deve compilar com a `ONESIGNAL_APP_ID` configurada em GitHub Repository Variables.

## O que a 0.13 adiciona
- versão `0.13.0-beta`, versionCode 17;
- Central do testador no Perfil;
- checklist de 12 etapas persistido localmente;
- feedback estruturado por área/impacto;
- captura segura de crash fatal e envio na próxima abertura;
- página `https://srrotas.com/beta`;
- tabela `beta_feedback`;
- endpoints `/api/v1/beta/feedback` e `/api/v1/beta/crash`.

## Privacidade do beta
Não são enviados automaticamente:
- OCR bruto;
- screenshots;
- senha;
- device token;
- chave MCP;
- conteúdo do clipboard.

Crash automático contém:
- classe da exceção;
- mensagem curta;
- até 10 frames técnicos;
- versão do app;
- Android SDK;
- fabricante/modelo.

## Testadores
Distribuir o mesmo APK para os 3 testadores.

Peça que cada um:
- faça o checklist inteiro;
- use por pelo menos um turno normal;
- envie problemas pelo próprio Perfil;
- não faça Pix real a menos que você peça um teste específico;
- faça configurações e feedback sempre com o veículo parado.

## OneSignal
A infraestrutura 0.11 já está configurada. Este é o primeiro build em que a variável GitHub `ONESIGNAL_APP_ID` deve entrar efetivamente no APK dos testadores.

No teste, validar:
- permissão;
- botão de teste;
- logout;
- resumo de jornada;
- ausência de push por oferta individual.
