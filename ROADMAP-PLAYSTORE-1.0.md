# Sr. Rotas — Roadmap oficial atualizado até a Play Store e divulgação

**Data-base:** 18/08/2026  
**Produto:** Sr. Rotas  
**Android principal:** `com.srrotas.app`  
**Stack:** Kotlin Android + Next.js + Supabase + Vercel  
**Empresa:** BigCorps  
**Suporte:** `contato@bigcorps.com.br`  
**Estado-base para o fechamento:** `0.13.x-beta`

---

# 1. Decisões arquiteturais e comerciais fechadas

1. **Um único aplicativo na Play Store:** `com.srrotas.app`.

2. O núcleo crítico permanece nativo e local:
   - MediaProjection;
   - OCR com ML Kit;
   - parser;
   - Card Stabilizer;
   - deduplicação;
   - cálculos de rentabilidade;
   - HUD;
   - jornada;
   - fila/offline;
   - sincronização;
   - permissões e foreground service.

3. O dashboard Next.js passa a ser a superfície dinâmica do produto:
   - Histórico;
   - gráficos;
   - IA;
   - MCP;
   - Perfil;
   - dispositivos;
   - assinatura;
   - créditos;
   - cobrança;
   - suporte;
   - páginas jurídicas.

4. O dashboard Web será aberto **dentro do próprio app Kotlin**, usando Trusted Web Activity / Android Browser Helper, sem publicar um segundo aplicativo.

5. O antigo conceito de `com.srrotas.web` deixa de fazer parte do lançamento oficial. Não haverá um segundo app Sr. Rotas na Play Store.

6. O domínio canônico será:
   - `https://srrotas.com`
   - sem `www` como origem principal;
   - `www` apenas redireciona para o apex.

7. **Identidade oficial:** usar exatamente a arte do personagem fornecida em 18/08/2026.
   - fonte oficial: `docs/brand/sr-rotas-icon-source.png`;
   - app/launcher: `docs/brand/sr-rotas-icon-app-black.png`, preservando o fundo preto externo;
   - Web/PWA: `docs/brand/sr-rotas-logo-web-transparent.png`, com transparência somente fora do ícone;
   - não redesenhar, reinterpretar ou trocar o personagem;
   - todos os ícones Android, Web, PWA, favicon e materiais futuros derivam desse mesmo original.

8. **Plano comercial inicial:** R$ 9,90 por 30 dias.

9. **Pagamento:** Pix Banco Inter / conta BigCorps.

10. A apresentação do checkout deverá ser controlável remotamente pelo backend/Web:
    - `PLAY_PAYMENT_MODE=monitoria_full`
    - fallback possível: `PLAY_PAYMENT_MODE=consumption_only`
    - a regra de entitlement continua independente da interface de pagamento.

11. **Trial oficial:**
    - 7 dias;
    - começa somente na **primeira oferta válida persistida**;
    - cadastro, login, instalação e primeira jornada sem oferta não iniciam o relógio;
    - 5 créditos temporários de IA durante o trial;
    - sem cartão/Pix obrigatório para começar.

12. Após o primeiro pagamento confirmado:
    - assinatura ativa por 30 dias;
    - trial vira `converted`;
    - são concedidos **20 créditos iniciais de IA uma única vez**.

13. OCR, HUD, fórmulas, histórico estruturado, SQL analytics e MCP não consomem créditos de IA.

14. A IA própria do Sr. Rotas é opcional e usa créditos.

15. MCP continua somente leitura:
    - não aceita corrida;
    - não recusa corrida;
    - não controla Uber ou outro aplicativo;
    - não executa ações operacionais.

16. Após expiração do trial ou assinatura:
    - histórico permanece disponível;
    - gráficos históricos permanecem disponíveis;
    - Perfil, suporte, segurança e gerenciamento de MCP permanecem acessíveis;
    - novas jornadas/OCR produtivo/IA/MCP operacional ficam bloqueados até reativação.

17. Máximo inicial de **2 aparelhos ativos por conta**.
    - terceiro aparelho exige revogar/substituir um anterior;
    - troca legítima de celular não perde conta nem histórico.

18. A 1.0 pública exige e-mail confirmado antes de iniciar o trial.

19. Antiabuso não deve usar IMEI, serial, MAC address ou fingerprint invasiva.
    - usar identificador apropriado + HMAC server-side;
    - Play Integrity entra como camada adicional.

20. Play Integrity começa em:
    - `observe`
    - depois `soft`
    - `hard` somente após validar falsos positivos.

21. `deviceRecall`, se disponível/aprovado para o projeto, pode ser usado para reforçar a regra “um trial por aparelho”.

22. OneSignal permanece para comunicação e operação, nunca para avaliação de oferta em tempo real.

23. Raw OCR não é enviado ao servidor por padrão.

24. Screenshots/evidências continuam privadas, opcionais e desligadas por padrão.

25. Nenhum secret administrativo, `service_role`, chave bancária ou token MCP fica no cliente Android ou no navegador.

---

# 2. Histórico do Offer Engine

## 0.5.2 — Engine Freeze Candidate

Objetivo original:
- última rodada controlada;
- telemetria de latência;
- mesmos filtros e fórmulas.

## 0.5.3 — Otimização do pipeline OCR

Principais avanços:
- pipeline mais rápido;
- menor pressão de fila;
- correções de OCR;
- melhor classificação;
- ícone oficial efetivamente ligado ao app.

## 0.5.4 — Offer Engine v1 congelado

**Status oficial:** baseline do motor.

A partir daqui:
- parser/OCR não mudam por refinamento subjetivo;
- só reabrir com caso real, reproduzível e claramente incorreto;
- nenhuma fase visual/comercial deve alterar o Offer Engine.

---

# 3. Fases de produto já consolidadas antes da 1.0

## 0.6 — Design System, identidade e UX

Objetivo:
- linguagem visual única;
- identidade Sr. Rotas;
- presets Compacto/Normal/Grande;
- HUD configurável;
- tipografia, espaçamento, estados e componentes compartilhados.

## 0.7 — Conta, onboarding e app autossuficiente

Fluxo-base:
`Instalar → Entrar/Criar conta → Consentimentos → Permissões → Estratégia → Tutorial HUD → Primeira jornada`

## 0.8 — Histórico, dashboard e analytics sem IA

Valor estruturado:
- ofertas/jornadas;
- R$/km;
- R$/min;
- R$/h;
- verdict;
- horários;
- dias;
- serviço;
- Radar/Exclusive;
- filtros;
- comparações;
- gráficos.

Regra:
- SQL/TypeScript antes de LLM.

## 0.9 — Pesquisa IA + MCP

IA:
- contextual;
- baseada nos dados do próprio motorista;
- créditos transacionais;
- falha técnica não consome crédito definitivo.

MCP:
- leitura;
- jornadas;
- ofertas;
- métricas;
- estratégia;
- comparação;
- resumos.

## 0.10 — Monetização, Pix, créditos e entitlements

Base já definida:
- assinatura;
- pagamentos;
- carteira;
- ledger;
- entitlements;
- Banco Inter;
- Pix.

## 0.11 — OneSignal

Uso:
- versão;
- manutenção;
- compatibilidade;
- falha de sync;
- resumo;
- trial;
- pagamento;
- comunicação de produto.

## 0.12 — Segurança, privacidade e Play Compliance

Base:
- API 36;
- MediaProjection;
- exclusão de conta;
- privacidade;
- termos;
- suporte;
- segurança de sessão;
- páginas Web;
- ausência de segredo administrativo no cliente.

## 0.13 — Closed Beta

Objetivo:
- validação real de aparelhos;
- estabilidade;
- clareza do onboarding;
- bateria/CPU;
- sincronização;
- feedback;
- bugs de uso real.

---

# 4. 0.13.x — Fechamento do beta

Antes de iniciar a construção da 1.0:

- consolidar feedback dos testadores atuais;
- corrigir apenas bugs reais;
- classificar P0/P1/P2;
- não criar funcionalidades novas;
- congelar comportamento aprovado;
- registrar casos de aparelhos/fabricantes;
- registrar problemas de permissão;
- registrar problemas de TWA/Web se já houver testes;
- revisar crash/ANR;
- revisar consumo de bateria;
- revisar fila/sincronização.

### Critério de saída

```text
[ ] nenhum P0 aberto
[ ] nenhum P1 aberto
[ ] Offer Engine permanece congelado
[ ] sincronização confiável
[ ] onboarding compreensível
[ ] sem regressão de HUD
```

---

# 5. 1.0-A — Hardening de segurança e banco

Esta passa a ser a **primeira etapa real da fase 1.0**.

## Supabase

Resolver antes de adicionar o trial comercial:

- revisar todas as funções `SECURITY DEFINER`;
- revogar execução pública de RPCs server-only;
- impedir `anon`/`authenticated` de chamar diretamente funções de:
  - Pix;
  - confirmação de pagamento;
  - concessão de créditos;
  - reserva/consumo/devolução de créditos;
  - operações server-only;
- conceder `EXECUTE` apenas aos papéis necessários;
- rodar novamente Security Advisor.

## Auth

- habilitar Leaked Password Protection;
- manter e-mail confirmado como requisito para trial público.

## Performance

Criar índices de cobertura para FKs relevantes detectadas no banco:

- `driver_journeys.device_id`;
- `entitlements.source_subscription_id`;
- `payments.subscription_id`.

### Critério de saída

```text
[ ] Security Advisor sem WARN crítico server-only
[ ] grants revisados
[ ] leaked password protection ligada
[ ] índices necessários aplicados
[ ] migrations reproduzíveis
```

---

# 6. 1.0-B — Trial, identidade do aparelho e antiabuso

## Trial

```text
7 dias
5 créditos de IA
início = primeira oferta válida
```

## Novas estruturas

Criar:

- `trial_runs`;
- `trial_device_fingerprints`;
- `trial_abuse_markers`.

Alterar:

- `driver_devices`.

## `trial_runs`

Estados:

```text
pending
active
expired
converted
blocked
```

Campos principais:

- driver;
- início;
- fim;
- oferta que iniciou;
- limite IA = 5;
- uso IA;
- conversão;
- expiração.

## Dispositivo

Adicionar ao `driver_devices`:

- `device_key_hash`;
- `platform`;
- `app_version`;
- `integrity_last_checked_at`;
- `integrity_verdict`;
- `revoked_at`.

## Regras

- um usuário não ganha novo trial ao reinstalar;
- novo e-mail no mesmo aparelho não ganha novo trial;
- mesma conta em aparelho permitido não reinicia trial;
- máximo inicial: 2 aparelhos ativos;
- terceiro aparelho exige revogação/substituição.

### Critério de saída

```text
[ ] trial começa somente com primeira oferta válida
[ ] reinstalação retoma trial existente
[ ] segundo e-mail no mesmo aparelho não ganha trial
[ ] 5 créditos temporários funcionam
[ ] expiração não apaga histórico
```

---

# 7. 1.0-C — Access Resolver central

Criar uma única fonte de verdade para todas as superfícies.

Exemplo:

```text
resolve_driver_access(driver_id, device_id)
```

Estados:

```text
TRIAL_PENDING
TRIAL_ACTIVE
PAID_ACTIVE
EXPIRED_READ_ONLY
BLOCKED
```

Toda superfície deve usar a mesma decisão:

- Android;
- backend;
- Web;
- IA;
- MCP;
- billing;
- sincronização.

Endpoint esperado:

```text
GET /api/v1/access/status
```

### Capacidades controladas

- `new_journey`;
- `ocr`;
- `hud`;
- `history_read`;
- `ai`;
- `mcp_query`;
- `billing`;
- `profile`.

### Critério de saída

Nenhuma camada implementa regra comercial própria divergente.

---

# 8. 1.0-D — Dashboard Web definitivo + TWA dentro do app Kotlin

Esta é a principal mudança em relação ao roadmap antigo.

**Status em paralelo ao Closed Beta:** FUNDAÇÃO WEB ADIANTADA.

Já pode ser construída sem alterar o APK em teste:
- shell `/app`;
- navegação responsiva;
- `/app/historico`;
- `/app/ia`;
- `/app/mcp`;
- `/app/perfil`;
- `/app/plano`;
- PWA com `start_url=/app`;
- rota de Digital Asset Links preparada;
- banner da Play Store condicionado a `NEXT_PUBLIC_PLAY_STORE_URL`;
- nova identidade visual padronizada.

Ainda NÃO conectar ao APK dos testadores até fechar o beta. O handoff Native → Web, Access Resolver, trial e bloqueios de entitlement continuam nas etapas 1.0-B/C/E.

## Arquitetura

```text
com.srrotas.app
    ├── Native
    │   ├── Início técnico
    │   ├── Jornada
    │   ├── MediaProjection
    │   ├── OCR
    │   ├── HUD
    │   └── permissões
    │
    └── TWA → https://srrotas.com/app
        ├── Histórico
        ├── IA
        ├── MCP
        ├── Perfil
        ├── dispositivos
        ├── Plano
        ├── Créditos
        ├── Pix
        └── Suporte
```

## Não criar segundo app

Não publicar:
- `com.srrotas.web`.

Usar Android Browser Helper dentro de:
- `com.srrotas.app`.

## Rotas Web

Criar:

```text
/app
/app/inicio
/app/historico
/app/ia
/app/mcp
/app/perfil
/app/plano
```

## Navegação oficial

```text
Início       → Native
Jornada      → Native
Histórico    → TWA
IA           → TWA
Perfil       → TWA
```

### Critério de saída

O usuário deve sentir que está usando um único aplicativo.

---

# 9. 1.0-E — Sessão transparente Native → Web

Não passar token permanente na URL.

Criar handoff de uso único.

Fluxo:

```text
Android autenticado
↓
POST /api/v1/web/handoff
↓
backend gera código temporário
↓
TWA abre /app/entrar?code=...
↓
servidor consome código
↓
cookie HttpOnly + Secure
↓
/app
```

## Novas tabelas

- `driver_web_sessions`;
- `web_session_handoffs`.

## Requisitos

- hash server-side;
- código de uso único;
- TTL curto;
- nenhum secret permanente na URL;
- revogação;
- logout coerente;
- sessão Web separada do device token.

### Critério de saída

Usuário autenticado no Android abre o dashboard sem novo login e sem exposição de token.

---

# 10. 1.0-F — Digital Asset Links, PWA e origem final

## Domínio

```text
https://srrotas.com
```

## Digital Asset Links

Criar:

```text
/.well-known/assetlinks.json
```

Produção:

```text
package = com.srrotas.app
fingerprint = Play App Signing SHA-256
```

Relações:

```text
delegate_permission/common.handle_all_urls
delegate_permission/common.get_login_creds
```

## Manifest PWA

```text
name = Sr. Rotas
short_name = Sr. Rotas
id = /
start_url = /app
scope = /
display = standalone
```

### Critério de saída

Instalação da Play abre `/app` em superfície confiável/full-screen, sem barra do navegador.

---

# 11. 1.0-G — Billing real, Pix e créditos

## Plano

```text
Sr. Rotas
R$ 9,90
30 dias
```

## Provedor

```text
Banco Inter
conta BigCorps
```

## Fluxo

```text
Plano
↓
Gerar Pix
↓
srrotas-create-pix
↓
Banco Inter
↓
QR + copia e cola
↓
srrotas-check-pix
+
srrotas-process-billing
↓
status + txid + valor
↓
confirmar
↓
assinatura ativa
↓
entitlements
↓
trial convertido
↓
20 créditos iniciais, uma única vez
```

## Regras

- idempotência;
- `txid` único;
- valor recebido precisa bater;
- divergência → `manual_review`;
- pagamento repetido não duplica período;
- pagamento repetido não duplica créditos;
- renovação posterior não concede os 20 novamente;
- falha da IA devolve crédito reservado.

## Interface comercial

Controlada por:

```text
PLAY_PAYMENT_MODE
```

Modos:

```text
monitoria_full
consumption_only
```

O backend de billing é o mesmo nos dois modos.

### Critério de saída

Pix real testado de ponta a ponta com valor real controlado.

---

# 12. 1.0-H — IA e créditos do trial

## Trial

Usar:

```text
trial_runs.ai_credit_limit = 5
trial_runs.ai_credits_used
```

Não misturar com a carteira permanente.

## Assinatura paga

Usar:

- `credit_wallets`;
- `credit_transactions`.

## Fluxo da IA

```text
reserve
↓
OpenAI
↓
resposta entregue
↓
consume
```

Falha:

```text
reserve
↓
erro/timeout
↓
refund
```

### Critério de saída

- trial não consegue gastar mais de 5;
- assinatura usa carteira;
- crédito não desaparece em falha técnica.

---

# 13. 1.0-I — MCP com entitlement

Endpoint final:

```text
https://srrotas.com/mcp
```

Estados:

```text
TRIAL_ACTIVE → leitura liberada
PAID_ACTIVE → leitura liberada
EXPIRED_READ_ONLY → consulta operacional bloqueada
```

Mesmo expirado, usuário ainda pode:
- listar chaves;
- revogar chaves;
- acessar segurança da conta.

### Critério de saída

MCP e dashboard retornam os mesmos dados estruturados para a mesma consulta factual.

---

# 14. 1.0-J — OneSignal do ciclo comercial

Adicionar eventos de trial/assinatura às notificações já existentes.

## Trial iniciado

> Seu teste do Sr. Rotas começou. Você tem 7 dias para usar todos os recursos.

## 48 horas antes

Resumo baseado em dados reais.

## 24 horas antes

Aviso de término.

## Expiração

> Seu teste terminou. Seu histórico continua salvo.

## Pagamento

> Pagamento confirmado. Sr. Rotas ativo por mais 30 dias.

## Regra

Usar `dedupe_key`.

Nunca usar OneSignal para:
- oferta;
- OCR;
- verdict;
- HUD em tempo real.

---

# 15. 1.0-K — Play Integrity

## Primeiro rollout

```text
PLAY_INTEGRITY_ENFORCEMENT=observe
```

Usar em eventos importantes:

- registro de aparelho;
- início de trial;
- troca de aparelho;
- ações sensíveis.

Não usar:
- por frame;
- por OCR;
- por oferta.

## Evolução

```text
observe
↓
soft
↓
hard
```

Somente após telemetria suficiente.

## deviceRecall

Se disponível para o projeto:

- marcar trial consumido;
- usar como reforço da elegibilidade;
- não usar como rastreamento de usuário.

---

# 16. 1.0-L — Privacidade, exclusão e segurança final

Validar fim a fim:

```text
/app/perfil
/excluir-conta
/privacidade
/termos
/suporte
```

## Exclusão

Confirmar remoção/revogação de:

- Auth;
- driver;
- sessões;
- devices;
- MCP;
- OneSignal;
- dados locais.

Se algum marcador antiabuso for preservado:
- pseudônimo;
- sem e-mail/nome;
- prazo definido;
- finalidade documentada;
- revisão jurídica.

---

# 17. 1.0-M — Release Candidate

## Produto

```text
[ ] zero P0
[ ] zero P1
[ ] Offer Engine congelado
[ ] onboarding fechado
[ ] trial fechado
[ ] antiabuso fechado
[ ] Access Resolver único
[ ] TWA integrada
[ ] sessão Native → Web transparente
[ ] Histórico Web
[ ] IA Web
[ ] MCP Web
[ ] Perfil Web
[ ] Pix real
[ ] créditos
[ ] OneSignal
[ ] Integrity em observe/soft
[ ] exclusão
[ ] suporte
[ ] offline/sync
```

## Play

- `targetSdk 36`;
- AAB release;
- assinatura definitiva;
- Foreground Service `mediaProjection`;
- vídeo demonstrando captura iniciada pelo usuário;
- Data Safety;
- política de privacidade;
- exclusão de conta;
- classificação;
- conteúdo da loja.

## TWA

Depois do primeiro AAB na Play:

1. obter SHA-256 da **Play App Signing Key**;
2. configurar `TWA_SHA256_FINGERPRINTS`;
3. deploy;
4. validar `assetlinks.json`;
5. reinstalar pela Play;
6. confirmar abertura sem barra.

---

# 18. 1.0 — Publicação

Somente publicar quando:

```text
[ ] Security Advisor revisado
[ ] Performance Advisor revisado
[ ] trial testado
[ ] antiabuso testado
[ ] Pix real testado
[ ] 20 créditos testados
[ ] 5 créditos trial testados
[ ] TWA testada via instalação Play
[ ] sessão handoff testada
[ ] MCP testado
[ ] OneSignal testado
[ ] exclusão validada
[ ] MediaProjection validado
[ ] Data Safety revisada
[ ] AAB assinado
```

---

# 19. Divulgação

## Landing

`srrotas.com`

Conteúdo:

- proposta;
- como funciona;
- screenshots;
- plano;
- trial;
- FAQ;
- IA;
- MCP;
- suporte;
- login.

## Mensagens-base

**“Você decide a corrida. O Sr. Rotas faz as contas.”**

**“Use a IA do Sr. Rotas ou conecte seus dados à IA que você já usa.”**

## Canais

- Instagram;
- TikTok;
- YouTube/Shorts;
- comunidades de motoristas;
- creators do nicho;
- indicação.

---

# 20. Regra de governança

Depois do Offer Engine Freeze, nenhuma ideia nova pula o roadmap salvo:

1. bug crítico;
2. segurança;
3. risco real de reprovação/bloqueio da loja;
4. regressão real observada no beta/RC.

Todo o restante vai para backlog pós-1.0.

---

# 21. Pós-1.0

- Pix Automático/recorrência;
- novos provedores/plataformas após revisão técnica/jurídica;
- benchmarking anonimizado opt-in;
- relatórios avançados;
- personalização avançada;
- widgets;
- recursos premium;
- novos clientes MCP;
- experimentos de pricing;
- iOS;
- expansão internacional.

---

# 22. Sequência oficial atualizada

```text
0.5.4
Offer Engine v1 congelado
        ↓
0.6–0.12
Produto e infraestrutura base
        ↓
0.13.x
Fechamento do Closed Beta
        ↓
1.0-A
Hardening de segurança
        ↓
1.0-B
Trial + device identity + antiabuso
        ↓
1.0-C
Access Resolver
        ↓
1.0-D/E/F
Dashboard Web + TWA + sessão + DAL
        ↓
1.0-G/H/I
Pix + créditos + IA + MCP
        ↓
1.0-J/K/L
OneSignal + Integrity + privacidade
        ↓
1.0-M
Release Candidate
        ↓
1.0.0
Play Store
        ↓
DIVULGAÇÃO
```

---

---

# 23. Regra documental do projeto

`ROADMAP-PLAYSTORE-1.0.md` é a **única fonte oficial de planejamento do Sr. Rotas até a publicação**.

A cada ZIP/fase:
1. consultar a versão atual da `main`;
2. atualizar este mesmo roadmap;
3. não criar novos MDs de plano/checklist/changelog para competir com ele;
4. instruções de aplicação e validação ficam no chat;
5. alterações futuras devem preservar as decisões já fechadas ou registrar explicitamente a mudança aqui.
