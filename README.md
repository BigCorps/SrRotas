# Driver AI MVP

MVP independente para validar captura de ofertas do Uber Driver no Android, cálculo local de rentabilidade, histórico em Supabase, Pesquisa IA opcional e servidor MCP remoto.

> O projeto está deliberadamente sem nome/marca final. O `applicationId` provisório é `com.bigcorps.driveraimvp` e pode ser trocado antes de publicar.

## O que já existe neste ZIP

- `android/` — app Android nativo em Kotlin.
  - AccessibilityService limitado inicialmente ao pacote `com.ubercab.driver` (Uber Driver).
  - Leitura da árvore de acessibilidade.
  - Fallback opcional: screenshot via AccessibilityService + OCR **local** com ML Kit.
  - Parser de valor, distâncias e minutos.
  - Cálculo de R$/km, R$/hora, custo estimado e lucro estimado.
  - Semáforo local (boa / regular / ruim).
  - Overlay somente informativo, sem aceitar/rejeitar corrida e sem executar toques.
  - Modo diagnóstico com o texto bruto capturado para calibrar o parser.
  - Pareamento com backend e envio de ofertas estruturadas.
  - Caixa de Pesquisa IA dentro do próprio app, consumindo `/api/v1/ask`.
- `backend/` — Next.js 16 + Supabase + MCP.
  - Pareamento simples por código para o MVP.
  - Autenticação do aparelho por token.
  - Ingestão de ofertas.
  - Resumo e consulta de ofertas.
  - Endpoint `/api/v1/ask` com OpenAI Responses API (opcional).
  - Endpoint `/mcp` com ferramentas read-only.
- `supabase/` — migration inicial para um projeto Supabase separado.

## Arquitetura

```text
Uber Driver
   ↓
AccessibilityService
   ├─ árvore de acessibilidade
   └─ screenshot + OCR local (fallback opcional)
   ↓
Parser local
   ↓
Cálculo local + overlay
   ↓
Backend Next.js
   ↓
Supabase
   ├─ Pesquisa IA (/api/v1/ask)
   └─ MCP (/mcp)
```

## 1. Criar o backend separado

Crie um projeto Supabase novo, sem reaproveitar o banco do MonitorIA.

No SQL Editor do projeto, execute:

`supabase/migrations/20260815_initial.sql`

Depois copie:

```bash
cd backend
cp .env.example .env.local
```

Preencha:

- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `PAIRING_CODE` — por exemplo um código de 6 dígitos para o seu teste.
- `MCP_API_TOKEN` — token longo aleatório para testar o MCP.
- `OPENAI_API_KEY` — opcional no primeiro teste; necessário para `/api/v1/ask`.
- `OPENAI_MODEL` — padrão sugerido no exemplo: `gpt-5.6`.

Rode:

```bash
npm install
npm run dev
```

Teste:

```text
http://localhost:3000/api/health
```

Para testar no celular na mesma rede Wi-Fi, use o IP do computador, por exemplo:

```text
http://192.168.1.50:3000
```

Para uso fora da rede local, publique o backend na Vercel e use HTTPS.

## 2. Build Android no VS Code

### Requisitos

- JDK 17 ou superior.
- Android SDK Platform 36.
- Android SDK Build Tools compatíveis.
- `ANDROID_HOME` ou `ANDROID_SDK_ROOT` configurado.
- ADB se quiser instalar pelo terminal.

### APK de teste — recomendado para o primeiro teste

Na primeira execução, se `gradle-wrapper.jar` ainda não existir, os scripts `gradlew`/`gradlew.bat` baixam automaticamente o wrapper oficial do Gradle 8.13.


```bash
cd android
./gradlew :app:assembleDebug
```

No Windows:

```bat
gradlew.bat :app:assembleDebug
```

Saída:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Instale:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Você também pode copiar o APK para o aparelho e abrir manualmente.

### AAB

AAB é para distribuição/Play Console, não para instalação direta. Para gerar um bundle de debug:

```bash
./gradlew :app:bundleDebug
```

Para um AAB release assinado, crie um keystore próprio para este aplicativo e configure as variáveis de ambiente:

```bash
export KEYSTORE_PATH=/caminho/app-release.jks
export KEYSTORE_PASSWORD='...'
export KEY_ALIAS='...'
export KEY_PASSWORD='...'
./gradlew :app:bundleRelease
```

Saída:

```text
android/app/build/outputs/bundle/release/app-release.aab
```

## 3. Primeiro teste no celular com Uber

1. Instale o APK debug.
2. Abra o app.
3. Leia a divulgação sobre Acessibilidade e marque o consentimento.
4. Toque em **Abrir configurações de Acessibilidade**.
5. Ative `Driver AI MVP`.
6. Volte ao app.
7. Deixe `OCR por screenshot` ligado para o primeiro diagnóstico. O OCR roda no próprio aparelho.
8. Se estiver usando backend, informe a URL e o `PAIRING_CODE` e toque em **Parear aparelho**.
9. Abra o Uber Driver e fique online.
10. Quando surgir uma oferta, o app tenta ler os nós da interface. Se não encontrar dados suficientes, tenta screenshot + OCR.
11. Se reconhecer valor/distância/tempo, aparece um pequeno overlay no topo com R$/km e R$/h.
12. Volte ao app depois e veja **Última captura** e **Texto bruto capturado**.

### O primeiro teste é também uma calibração

A interface da Uber pode mudar por cidade, versão, categoria e experimento A/B. Por isso o parser está separado em `OfferParser.kt` e o app guarda o texto bruto reconhecido. Se algum campo vier errado, o texto diagnóstico nos diz exatamente como adaptar as regras sem precisar reescrever o serviço.

## 4. Configuração de rentabilidade

No app você pode definir:

- mínimo desejado em R$/km;
- mínimo desejado em R$/hora;
- custo estimado do carro por km.

O cálculo é local. Nenhuma chamada de IA é feita para decidir a cor da oferta.

## 5. MCP

O backend expõe:

```text
GET/POST/DELETE /mcp
```

Autenticação de desenvolvimento:

```text
Authorization: Bearer <MCP_API_TOKEN>
```

Ferramentas iniciais:

- `get_driver_summary`
- `search_offers`
- `compare_periods`
- `get_best_hours`
- `get_cost_breakdown`
- `ask_driver`

Todas são read-only e suas chamadas são auditadas em `mcp_tool_audit_logs`.

O token estático é adequado para o MVP privado. Antes de publicação aberta, substitua-o por OAuth, seguindo o padrão já usado no MonitorIA.

## 6. Pesquisa IA

O endpoint:

```text
POST /api/v1/ask
Authorization: Bearer <device-token>
Content-Type: application/json

{"question":"Qual horário está rendendo melhor esta semana?"}
```

usa apenas as ofertas estruturadas daquele motorista e envia um resumo compacto à OpenAI. A configuração usa `store: false`.

## 7. Segurança e escopo do MVP

O MVP propositalmente **não**:

- toca no botão Aceitar;
- toca no botão Recusar;
- altera configurações do Uber;
- faz automação de interface;
- guarda screenshot no backend;
- envia screenshot para OpenAI;
- usa credenciais da Uber;
- tenta contornar proteções do aplicativo Uber.

A Acessibilidade é usada somente para observar a oferta e fornecer análise ao próprio motorista. Para publicação na Play Store, será necessário manter divulgação destacada, consentimento e preencher a declaração da AccessibilityService.

## 8. Próxima etapa depois do primeiro teste

O dado mais importante será o texto bruto capturado em uma oferta real. Com ele dá para:

- calibrar o parser para o layout atual da Uber no Brasil;
- separar corretamente km até o passageiro e km da viagem;
- reconhecer categorias (UberX, Comfort etc.);
- detectar corridas com múltiplas paradas;
- detectar aceite/conclusão sem automação;
- depois adicionar 99/iFood como adaptadores independentes.
