# Aplicar — Sr. Rotas 0.11.0 Alpha — OneSignal

Base esperada: `a4d0b80da3544427b34b4e5ea6b923491147827f`.

## O que entra

- OneSignal Android SDK `5.9.8`;
- `Application` própria para inicialização correta;
- motorista identificado no OneSignal pelo `drivers.id` como `external_id`;
- tags: `platform`, `app_version`, `paired`, `strategy`, `sync_pending` e preferências;
- tela de preferências de push no Perfil;
- botão de teste;
- resumo de jornada por push;
- Edge `srrotas-send-push`;
- auditoria de entregas;
- sem push para avaliação de ofertas em tempo real;
- sem Web Push nesta fase.

## 1. SQL manual

Execute:

`supabase/migrations/20260818_onesignal_notifications_011.sql`

## 2. Criar o app Android no OneSignal

No OneSignal:
1. crie o app **Sr. Rotas**;
2. configure Google Android (FCM);
3. conecte as credenciais Firebase/FCM;
4. copie o **OneSignal App ID**;
5. copie a **App API Key** em Settings > Keys & IDs.

Push Android não será entregue enquanto o FCM não estiver configurado no painel OneSignal.

## 3. Supabase secrets

No projeto Supabase Sr. Rotas:

- `ONESIGNAL_APP_ID=<UUID do app>`
- `ONESIGNAL_APP_API_KEY=<App API Key>`

A App API Key fica somente no Supabase. Nunca Android/GitHub/Vercel.

## 4. GitHub Actions variable

Em:

Settings → Secrets and variables → Actions → Variables

crie:

`ONESIGNAL_APP_ID=<mesmo UUID do app>`

Não é segredo; é usado apenas para compilar o Android com o App ID.

Sem essa variável, o CI continua compilando normalmente, porém o OneSignal fica desativado no APK.

## 5. Deploy Edge

Depois dos secrets:

`srrotas-send-push`

com `verify_jwt=true`.

## 6. Aplicar ZIP

Extraia na raiz do repositório e faça commit/push.

Não precisamos gerar APK agora. O SDK ficará pronto e será validado quando prepararmos o próximo APK para o beta ampliado.

## Comportamento

### Identidade
`drivers.id` → OneSignal `external_id`.

Não usamos `driver_id` como tag para segmentação individual; o OneSignal recomenda `external_id` para esse caso.

### Preferências
Padrões:
- atualizações/compatibilidade: ligado;
- resumo de jornada: ligado;
- sincronização: ligado;
- novidades do produto: desligado.

### Jornada
Quando a jornada é encerrada no backend, o Sr. Rotas tenta enviar um resumo:
- quantidade de ofertas observadas;
- quantas foram classificadas como boas;
- média R$/km.

A mensagem continua dizendo/representando **ofertas observadas**, não corridas concluídas.

### Sincronização
O app mantém a tag `sync_pending=true/false`, permitindo alertas operacionais segmentados sem colocar OneSignal no caminho crítico do OCR.

## O que NÃO fazemos
- aceitar/recusar corrida por push;
- enviar cada oferta para OneSignal;
- enviar OCR bruto;
- colocar App API Key no APK;
- habilitar Web Push no domínio temporário.
