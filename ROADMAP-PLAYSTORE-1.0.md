# ROADMAP OFICIAL — SR. ROTAS ATÉ 1.0.0

**Atualizado em:** 25/08/2026  
**Documento canônico:** `ROADMAP-PLAYSTORE-1.0.md`  
**Produto Android:** `com.srrotas.app`  
**Domínio:** `https://srrotas.com`  
**Empresa:** BigCorps  

> Este arquivo substitui a sequência antiga fragmentada do roadmap.
> Quando uma decisão anterior conflitar com este documento, vale a decisão mais recente registrada aqui.

---

# 1. VISÃO DO PRODUTO

O Sr. Rotas é um copiloto de rentabilidade para motoristas de aplicativos.

Fluxo principal:

```text
oferta na tela
→ captura autorizada por MediaProjection
→ OCR local
→ Offer Engine financeiro
→ Context Engine
→ origem/destino
→ geolocalização e ETA
→ histórico pessoal
→ exposição regional
→ estatística
→ custos pessoais
→ decisão do motorista
```

Princípio:

> **O Sr. Rotas calcula. O motorista decide.**

O produto:
- não aceita corridas;
- não recusa corridas;
- não toca automaticamente no Uber/99;
- não usa AccessibilityService como motor principal;
- não envia screenshot/OCR bruto por padrão.

---

# 2. COMPONENTES CONGELADOS

## 2.1 Offer Engine v1

Baseline:

```text
parser_version = sr-rotas-v0.5.4
```

Até nova decisão explícita, não alterar durante acabamento comercial:

- `OfferParser.kt`;
- `UberOfferDetector.kt`;
- `SpatialOfferParser.kt`;
- `CardStabilizer.kt`;
- `OfferDeduplicator.kt`;
- fórmulas financeiras;
- thresholds;
- sampling OCR;
- regras de Radar já validadas.

## 2.2 Motores independentes

```text
Offer Engine
Context Engine
Motor Estatístico
Cost Engine
Sync Coordinator
```

Um motor não deve alterar retrospectivamente os resultados do outro.

---

# 3. IDENTIDADE VISUAL

Fonte cromática oficial: landing `srrotas.com`.

Paleta atual:

```text
creme         #F8F4DF
ink           #073746
teal          #0C8788 / #0E9998
dourado       #F4CA50 / #E6B631
surface       #FFFDF6
```

O mockup do menu flutuante define **estrutura e comportamento**, não uma nova paleta.

Todas as superfícies Android devem continuar usando `UiKit`.

---

# 4. FASES CONCLUÍDAS

## 0.13.x — Beta, HUD e estabilidade

Concluído:
- onboarding;
- conta;
- HUD;
- drag;
- tamanhos;
- paleta;
- voz;
- closed beta;
- crash telemetry;
- feedback.

## 0.14 — Context Engine

Concluído:
- retirada;
- destino;
- geocoder;
- células geográficas;
- ETA;
- Maps;
- persistência local/remota.

## 0.15 — Jornada, outcomes e exposição

Concluído:
- ACTIVE / PAUSED / ENDED;
- OFFERED / DOING_RIDE / COMPLETED / NOT_COMPLETED / CANCELLED;
- exposição regional;
- localização durante jornada;
- correção posterior de corrida.

## 0.16 — Performance + ícone

Concluído:
- retirada de I/O pesado do hot-path OCR;
- pós-processamento em executor de baixa prioridade;
- correção do launcher icon;
- preservação do sampling 250 ms.

## 0.17 — Inteligência estatística

Concluído:
- estatística pessoal;
- coletivo opt-in;
- guardrail de amostra;
- burst coalescence;
- continuidade regional;
- portal Web administrativo para histórico.

## 0.18 — Custos e Lucro est.*

Concluído:
- perfil de custos;
- combustão/elétrico/híbrido;
- rateio mensal;
- `Não sei` com fonte estimada;
- memória do cálculo;
- snapshot de custo por oferta;
- `sr-cost-v0.18.0`.

## 0.19 — Validação de campo do núcleo ✅

Versão:

```text
0.19.0-beta
versionCode 26
```

Validação real registrada em aparelho Android 16:

```text
60 ofertas
35 Exclusive
25 Radar
60 com contexto
17 com célula de destino
27 contextos resolvidos/parciais
55 exposições fechadas
60 outcomes
0 crashes pendentes
0 violações do guardrail estatístico
60 ofertas com snapshot de custo
```

Parecer de campo:
- funcionamento geral aprovado;
- velocidade/leitura aprovada;
- nenhum P0/P1 visual relatado na rodada.

### Bug descoberto após a validação

Fila local:

```text
124 pendentes
31 ofertas
31 contextos
1 evento de jornada
31 outcomes
30 exposições
```

Produção confirmou:
- POST `/api/v1/offers` retornando 400;
- POST `/api/v1/journeys` retornando 404.

Causa arquitetural:
- filhos locais podiam referenciar `journey_id` marcado localmente como sincronizado, mas ausente no backend;
- o botão Perfil → Sincronizar agora não acionava todas as filas;
- flushes independentes podiam repetir retries.

Correção passa para 0.20.

---

# 5. 0.20 — UX ANDROID FINAL + SYNC AUTO-REPARÁVEL

**Status:** implementado e endurecido até `0.20.3-beta`; permanece como baseline estável de sync/menu para a 0.21.

Versão final do bloco:

```text
0.20.3-beta
versionCode 30
```

Nenhuma migration nova obrigatória.

## 5.1 Sync Coordinator

Criar fonte única para sincronização Android:

```text
SyncCoordinator
```

Ordem:

```text
garantir jornada pai
→ ofertas
→ atualizações de contexto
→ eventos de jornada
→ outcomes
→ exposições
→ encerramento da jornada
```

Regras:
- `journey_id` é garantido idempotentemente antes dos filhos;
- jornada realmente ausente pode ser recriada usando o mesmo UUID local;
- `invalid_journey` / `journey_not_found` geram reparo/retry controlado;
- **`journey_id_conflict` (409) nunca é remapeado para a conta atual**: o backend confirmou que o UUID pertence a outro motorista/sessão;
- filhos de um `journey_id_conflict` são preservados localmente em estado terminal `sync_state=2`, sem upload para a conta errada e sem exclusão do histórico local;
- nenhum item é apagado para zerar contador;
- `sync_state=1` somente após HTTP 2xx;
- chamadas simultâneas são coalescidas;
- startup, `onResume` e botão manual usam o mesmo coordenador;
- Perfil deixa de chamar somente a fila antiga.

### Caso obrigatório de teste

A instalação que possui os **124 itens pendentes da 0.19** deve ser atualizada por cima.

Critério:

```text
124
↓
Sync 0.20
↓
0
```

Sem:
- limpar app;
- apagar SQLite;
- duplicar ofertas;
- perder histórico.

## 5.2 Estado de sync na UI

Não mostrar detalhes técnicos ao motorista como requisito normal.

Mostrar:

```text
Tudo sincronizado
Sincronizando 124 itens…
124 itens aguardando sincronização
Sem internet — dados salvos no aparelho
```

Detalhes continuam disponíveis no diagnóstico.

## 5.3 Novo menu flutuante

Referência funcional aprovada pelo idealizador.

Mascote:
- toque abre/recolhe;
- drag continua;
- tamanho/opacidade continuam configuráveis;
- X recolhe a janela, não elimina o mascote.

### Estado recolhido

Mostrar até as 3 últimas ofertas:

```text
Categoria
Valor
Estou fazendo
Expandir/recolher
```

### Oferta expandida

Mostrar:

- categoria;
- valor;
- Embarque;
- Destino;
- R$/km;
- R$/min;
- R$/h;
- km/minutos quando disponíveis;
- Lucro est.* quando disponível;
- botão Embarque;
- botão Destino;
- estado da corrida.

Somente uma oferta expandida por vez.

### Controles inferiores

Sempre que aplicável:

```text
INICIAR
PAUSAR
RETOMAR
ENCERRAR
HISTÓRICO
```

`Iniciar` abre o fluxo oficial do Android para MediaProjection.

Não tentar iniciar captura de tela silenciosamente pelo overlay.

## 5.4 Relação com HUD financeiro

Menu flutuante ≠ HUD.

O HUD continua responsável por leitura rápida da oferta atual.

O menu:
- histórico imediato;
- contexto;
- ações de jornada;
- Maps;
- estado de corrida.

Não introduzir processamento pesado no callback OCR.

## 5.5 Importação de screenshots

Decisão final para o aplicativo do motorista:

> **não expor upload/importação de screenshots na interface normal.**

Manter internamente:
- parser histórico;
- staging;
- dedupe;
- infraestrutura Web/admin.

Remover visualmente:
- botão em Histórico;
- atalho da validação;
- chamadas de UX ao motorista.

Portal Web administrativo pode continuar trabalhando com JSON/JSONL.

## 5.6 Saída da 0.20

```text
[ ] Actions verde
[ ] release assinado com a mesma chave estável
[ ] instala por cima da 0.19
[ ] 124 pendentes recuperados
[ ] fila chega a zero
[ ] nenhuma duplicação
[ ] novo menu mostra 3 últimas ofertas
[ ] só um card expandido
[ ] Embarque correto
[ ] Destino correto
[ ] Estou fazendo correto
[ ] pausa/retomada correto
[ ] iniciar jornada abre MediaProjection
[ ] histórico abre
[ ] importação de screenshot não aparece ao motorista
[ ] zero P0
[ ] zero P1
```

Depois de validada, a estrutura principal da UI Android fica congelada para a RC.

---


# 5A. 0.21 — ESTRATÉGIA MULTIPLATAFORMA + INTELIGÊNCIA REGIONAL

**Status:** implementação concluída e refinada na `0.21.1-beta`; aguardando validação curta de campo do acabamento final.

```text
0.21.1-beta
versionCode 32
```

A 0.21 não descongela o Offer Engine. Ela cria uma camada de estratégia e inteligência **depois** do parser, além de aproximar Android e Web.

## 5A.1 Estratégias

Perfis de partida:

```text
Popular
Conforto
Premium
Personalizado
```

Os presets atualizam **somente as metas financeiras**. Os limites de busca permanecem independentes e o motorista continua podendo editar tudo.

Limites para buscar o passageiro:
- distância máxima em km;
- tempo máximo em minutos;
- `0` desativa o respectivo limite.

A regra de km/min é aplicada por `StrategyGuard021` após a leitura válida. Ela não modifica OCR, parser, estabilizador, dedupe ou fórmulas extraídas.

## 5A.2 Agora / Hoje / Semana / Pesquisa

Nova superfície `Agora` no Android e no Web.

Fontes:
- **Sua base:** agregados das ofertas live do próprio motorista;
- **Comunidade:** somente dados live de motoristas com opt-in e grupos com pelo menos 3 contribuidores;
- **Base Sr. Rotas:** baseline histórico anonimizado, usado como fallback/referência.

A interface deve sempre deixar claro:

> Tendência histórica não é demanda em tempo real e não garante nova corrida.

A ordenação compara a janela observada com as metas do próprio motorista; não existe LLM no cálculo estatístico.

## 5A.3 Seed histórico seguro

A base histórica administrativa foi transformada em agregados por:
- região normalizada;
- dia da semana;
- janela de 3 horas;
- perfil de serviço;
- amostra;
- média/mediana/P25/P75 de métricas financeiras e busca.

O seed **não contém**:
- `driver_id`;
- screenshot;
- OCR bruto;
- endereço exato;
- oferta individual consultável.

Na carga inicial da 0.21, 37.495 dos 38.771 registros históricos válidos puderam ser classificados de forma conservadora em região. Os 1.276 restantes foram descartados do seed regional em vez de forçar classificação insegura.

Aliases frequentes de OCR foram normalizados, por exemplo `Consolagao → Consolação` e `Ltaim Bibi → Itaim Bibi`.

## 5A.4 Trial real

O trial deixa de ser apenas texto de planejamento:

```text
primeira oferta live válida persistida
→ trial_started_at
→ +7 dias
→ +5 créditos temporários de IA, uma única vez
```

Não iniciam trial:
- instalação;
- criação de conta;
- abertura do app;
- histórico importado.

Não existe cobrança automática ao terminar o trial.

A assinatura paga continua separada e permanece em R$ 9,90/30 dias via Pix/Banco Inter.

## 5A.5 Aparência

Aplicativo:
- Automático;
- Claro;
- Escuro.

HUD/menu:
- seguir aplicativo;
- Claro;
- Escuro.

O Web usa a mesma preferência `app_theme` e preserva fallback local enquanto carrega a conta.

O Android abre o painel Web por handoff de uso único: o aparelho autenticado cria um token de 2 minutos, o navegador o consome uma vez e recebe a sessão HttpOnly. O `device_token` nunca é colocado na URL.

## 5A.6 Histórico/importação

A infraestrutura administrativa de importação continua disponível em `/admin/importacoes`, mas **não aparece na navegação normal do motorista**.

## 5A.7 Acabamento 0.21.1 — UX final e continuidade do OCR

Após a avaliação da 0.21.0, o acabamento final definiu:

- `Agora` como Home;
- menu Android principal: Agora / Histórico / IA / Configurações;
- Jornada como agrupamento/subtópico de Histórico;
- versão visível somente em Configurações na UI comum;
- remoção de feedback Beta e pareamento Alpha da navegação normal;
- card/menus Android aproximados da linguagem visual Web sem converter o app nativo em WebView;
- padronização do termo **Buscar**;
- métrica HUD `Buscar: OK / Média / Alta`, com tempo e distância detalhados;
- ícone redondo no menu flutuante;
- ✓ minimalista como seleção **exclusiva para relatório**.

A seleção do ✓ usa estado próprio (`report_selected`) e **não** altera `RideOperationalStatus`, exposição ou jornada.

Para impedir a regressão observada em campo, a observação OCR passa a depender apenas de a jornada estar `ACTIVE`; um eventual estado operacional `DOING_RIDE` não bloqueia mais novas ofertas. Pausa e encerramento continuam bloqueando normalmente.

## 5A.8 Critério de saída

```text
[ ] Actions verde
[ ] Vercel production READY
[ ] instala por cima da 0.21.0 sem limpar dados
[ ] sync/quarentena 0.20.3 sem regressão
[ ] OCR/Offer Engine com mesma velocidade e leitura
[ ] Popular / Conforto / Premium / Personalizado
[ ] limite de busca por km e minutos independente do preset
[ ] Agora / Hoje / Semana / Pesquisa
[ ] fallback Base Sr. Rotas
[ ] base coletiva respeita opt-in
[ ] tema Automático / Claro / Escuro
[ ] trial só inicia na primeira oferta live válida
[ ] 5 créditos concedidos somente uma vez
[ ] importação histórica não aparece no app normal
[ ] menu final Agora / Histórico / IA / Configurações
[ ] ✓ de relatório não altera jornada/outcome nem interrompe OCR
[ ] Buscar aparece no HUD com OK/Média/Alta
[ ] feedback Beta e pareamento Alpha fora da UI comum
[ ] zero P0
[ ] zero P1
```

---

# 6. SEQUÊNCIA OFICIAL ATÉ 1.0.0

```text
0.19 ✅
Validação do núcleo
        ↓
0.20.3
UX Android final + Sync Coordinator
        ↓
0.21.1
Estratégia multiplataforma + Inteligência Regional + UX/OCR final
        ↓
1.0-A
Segurança e hardening
        ↓
1.0-B
Trial + dispositivos + Access Resolver
        ↓
1.0-C
Web/TWA + handoff + Digital Asset Links
        ↓
1.0-D
Plano + Pix + créditos
        ↓
1.0-E
Admin & Analytics
        ↓
1.0-F
IA + MCP + OneSignal + Play Integrity
        ↓
1.0-RC
Privacidade + Play + teste final
        ↓
1.0.0
Release pública
```

As fases 1.0-A…F são blocos de desenvolvimento.
Não é obrigatório gerar uma versão de campo para o motorista em cada bloco.

---

## 6.10 Continuidade no destino — fechamento 0.21.1

A funcionalidade central de continuidade passa a ser exibida diretamente na oferta:

- destino/célula + ETA são resolvidos pelo Context Engine;
- **P10** (probabilidade de nova oferta em até 10 min) usa exposição observada com censura e mínimo de 20 intervalos elegíveis;
- prioriza mesmo dia da semana e faixa de 3 h, ampliando a janela somente quando necessário;
- se ainda não houver denominador de exposição, o seed histórico validado do Sr. Rotas fornece apenas **Alta/Média/Baixa recorrência**, nunca um percentual artificial;
- o cálculo é assíncrono e não altera Offer Engine, OCR, verdict ou jornada;
- resultado aparece no HUD e no card expandido da oferta.

Com isso, a 0.21/0.21.1 fecha a última funcionalidade de inteligência prevista antes do hardening 1.0-A.

# 7. 1.0-A — SEGURANÇA E HARDENING

## Supabase

Revisar:
- RLS;
- grants;
- `SECURITY DEFINER`;
- funções server-only;
- RPCs de Pix;
- créditos;
- billing;
- admin;
- rate limits;
- índices.

Rodar:
- Security Advisor;
- Performance Advisor.

## Backend

Revisar:
- auth por dispositivo;
- auth Web;
- admin auth;
- validação de payload;
- idempotência;
- replay;
- logs sem secrets.

## Secrets

Nunca no Android/browser:
- service_role;
- Banco Inter;
- OneSignal REST key;
- MCP server secrets;
- keystore.

### Saída

```text
[ ] nenhum segredo cliente
[ ] grants server-only corretos
[ ] RLS revisada
[ ] Security Advisor revisado
[ ] índices críticos reproduzíveis
```

---

# 8. 1.0-B — TRIAL + DEVICE IDENTITY + ACCESS RESOLVER

## Trial

```text
7 dias
```

Início:

> primeira oferta válida persistida.

Trial não começa:
- ao instalar;
- ao criar conta;
- ao abrir onboarding.

Créditos temporários:

```text
5 créditos IA
```

## Dispositivos

Máximo inicial:

```text
2 dispositivos ativos
```

Não usar:
- IMEI;
- serial;
- MAC;
- lista de apps;
- fingerprint invasivo.

Usar:
- app/device ID apropriado;
- vínculo server-side;
- HMAC;
- Play Integrity gradual.

## Antiabuso

Reinstalação:
- não reinicia trial.

Novo e-mail no mesmo dispositivo:
- não cria trial infinito.

## Access Resolver

Fonte comercial única:

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

Controlar:
- iniciar jornada;
- OCR;
- HUD;
- histórico;
- estatística;
- IA;
- MCP;
- billing;
- perfil.

Nenhuma superfície cria regra própria divergente.

---

# 9. 1.0-C — WEB/TWA E HANDOFF

Android continua sendo o único app Play Store.

```text
com.srrotas.app
```

A Web é superfície complementar.

## Dashboard Web final

Integrar:
- jornadas;
- ofertas;
- origem/destino;
- Maps;
- outcomes;
- exposições;
- continuidade;
- custos;
- Lucro est.*;
- amostras estatísticas;
- dispositivos;
- plano.

## Native → Web

Fluxo:

```text
Android
→ POST /api/v1/web/handoff
→ código de uso único
→ /app/entrar?code=...
→ cookie HttpOnly + Secure
```

## DAL

Configurar:
- `/.well-known/assetlinks.json`;
- SHA-256 da assinatura real;
- domínio `srrotas.com`;
- PWA `start_url=/app`.

Não publicar segundo app TWA.

---

# 10. 1.0-D — PLANO, PIX E CRÉDITOS

Plano inicial:

```text
R$ 9,90
30 dias
```

Pagamento:
- Banco Inter;
- Pix.

## Primeira ativação paga

Ao primeiro pagamento confirmado:

```text
+30 dias
+20 créditos IA
```

Os 20 créditos:
- uma vez;
- não repetem em renovação.

## Billing

Obrigatório:
- txid único;
- idempotência;
- valor confere;
- status auditável;
- renovação;
- expiração;
- divergência → revisão;
- falha IA → devolução de crédito.

---

# 11. 1.0-E — ADMIN & ANALYTICS

Esta fase cria o cockpit do negócio.

## Acesso

A administração comercial pode ser compartilhada entre:
- BigCorps/desenvolvedor;
- idealizador/sócio autorizado.

Não compartilhar senha genérica.

Usar conta/papel.

## Visão de negócio compartilhada

Mostrar:
- clientes totais;
- novos hoje;
- novos 7d;
- novos 30d;
- trials ativos;
- trials expirados;
- planos ativos;
- planos vencidos;
- conversão trial → pago;
- pagamentos;
- receita;
- renovações;
- créditos concedidos;
- créditos consumidos;
- saldo de créditos;
- motoristas ativos;
- jornadas;
- ofertas analisadas;
- aparelhos ativos;
- versões do APK;
- uso de IA;
- uso de MCP;
- feedback;
- crashes;
- JSONs de diagnóstico;
- fila/sync agregados;
- retenção;
- churn quando houver base suficiente.

## Clientes

Detalhe por usuário:
- cadastro;
- plano;
- validade;
- trial;
- créditos;
- aparelhos;
- última atividade;
- pagamentos;
- versão instalada;
- diagnóstico enviado.

## Diagnósticos

Área:

```text
Admin → Diagnósticos
```

Relacionar:
- usuário;
- versão;
- dispositivo;
- data;
- JSON;
- feedback;
- crash;
- severidade.

## Admin técnico

Ferramentas de infraestrutura ficam restritas ao proprietário técnico.

Não expor ao perfil de negócio:
- SQL;
- secrets;
- migrations;
- deploy manual;
- comandos;
- service_role.

GitHub/Supabase/Vercel continuam sendo fontes técnicas separadas.

## Histórico/importações

O portal administrativo de JSON/JSONL permanece dentro do contexto de admin.

Não reaparecer no Android do motorista.

---

# 12. 1.0-F — IA, MCP, ONESIGNAL E PLAY INTEGRITY

## IA

Fluxo:

```text
reserve crédito
→ executar
→ sucesso: consume
→ falha: refund
```

Trial:
- carteira temporária de 5.

Pago:
- carteira normal.

## MCP

Somente leitura.

Pode fornecer:
- histórico;
- métricas;
- jornadas;
- contexto;
- continuidade;
- custos;
- estatística.

Nunca:
- aceitar corrida;
- recusar corrida;
- controlar Uber/99.

Respeitar Access Resolver.

## OneSignal

Usar para:
- trial;
- pagamento;
- renovação;
- manutenção;
- versão;
- sync;
- recursos.

Nunca usar para:
- verdict da oferta;
- HUD em tempo real;
- decisão de corrida.

## Play Integrity

Progressão:

```text
observe
→ soft
→ hard somente após telemetria
```

Evitar bloqueio indevido em aparelhos legítimos.

---

# 13. 1.0-RC — RELEASE CANDIDATE

## Produto

```text
[ ] zero P0
[ ] zero P1
[ ] Offer Engine congelado
[ ] Context Engine validado
[ ] Radar validado
[ ] Maps/ETA
[ ] jornada
[ ] outcomes
[ ] exposição
[ ] estatística
[ ] custos
[ ] Lucro est.*
[ ] menu flutuante final
[ ] sync auto-reparável
[ ] trial
[ ] Access Resolver
[ ] 2 dispositivos
[ ] Web/TWA
[ ] Pix
[ ] créditos
[ ] Admin
[ ] IA
[ ] MCP
[ ] OneSignal
[ ] Integrity observe/soft
[ ] exclusão de conta
[ ] offline/sync
```

## Privacidade

Revisar:
- `/privacidade`;
- `/termos`;
- `/excluir-conta`;
- `/suporte`;
- retenção;
- localização;
- dados coletivos;
- antiabuso;
- Data Safety.

## Play

```text
target API 36
AAB release
assinatura definitiva
MediaProjection
foreground services
localização de jornada
Data Safety
screenshots da loja
descrição
classificação
exclusão
```

## Atualização

Testar:

```text
0.20 release
→ RC
```

sem desinstalar e sem perda local.

---

# 14. 1.0.0 — PUBLICAÇÃO

Publicar somente quando:

```text
[ ] RC aprovada
[ ] Security Advisor revisado
[ ] Performance Advisor revisado
[ ] fila/sync confiável
[ ] trial validado
[ ] antiabuso validado
[ ] Pix real validado
[ ] 20 créditos validados
[ ] 5 créditos trial validados
[ ] Admin validado
[ ] Web handoff validado
[ ] MCP validado
[ ] OneSignal validado
[ ] exclusão validada
[ ] AAB assinado
[ ] atualização sobre RC validada
```

Se RC1 estiver limpa:
- o mesmo código pode receber versionamento final `1.0.0`.

Se houver P0/P1:
- RC2 antes da publicação.

---

# 15. ASSINATURA ANDROID

A chave release estável configurada na 0.19 passa a ser a identidade permanente dos builds de campo/release.

Secrets:

```text
ANDROID_KEYSTORE_BASE64
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

Nunca trocar sem decisão explícita.

Guardar fingerprint pública do certificado.

---

# 16. PRIVACIDADE

## Local

Preferir:
- OCR local;
- screenshots locais;
- cache local;
- contexto local.

## Remoto pessoal

Enviar apenas o necessário para:
- histórico;
- sync;
- estatística pessoal;
- billing;
- recursos solicitados.

## Coletivo

Sempre opt-in.

Preferir:
- célula geográfica;
- janela temporal;
- métricas agregadas.

Não enviar por padrão:
- screenshot;
- OCR bruto;
- endereço textual exato;
- dados de passageiro.

---

# 17. HISTÓRICO ADMINISTRATIVO

A capacidade histórica permanece útil para bootstrap e auditoria.

Canal oficial:

```text
https://srrotas.com/admin/importacoes
JSON / JSONL
→ lote
→ validação
→ dedupe
→ original_payload + normalized_payload
→ staging
→ promoção controlada
```

Não é uma função de uso cotidiano do motorista na 1.0.

## 17.1 Estado já implementado do portal Web

Identidade administrativa permanente:

```text
contato@bigcorps.com.br
```

Importador inicial já autorizado:

```text
jadielalmeida@gmail.com
```

Regras preservadas:
- existe uma única tela pública de login Web em `/app/entrar`;
- identidade administrativa BigCorps pode existir no Supabase Auth sem virar `driver`;
- motorista autorizado continua sendo motorista normal;
- não criar `driver` artificial apenas para satisfazer o admin;
- acesso ao importador depende de allowlist;
- lote Web registra identidade administrativa/importador;
- tabelas de staging são server-only;
- nenhum JSON grava diretamente em `ride_offers`;
- nenhum dado de staging vira estatística antes da promoção controlada.

Estruturas existentes/relevantes:
- `historical_import_access`;
- `historical_import_batches`;
- `historical_import_rows`;
- `historical_import_web_sessions`.

O portal de importação será incorporado visualmente ao Admin & Analytics da 1.0-E, preservando suas regras de acesso e staging.

---

# 18. FORA DO CORE DA 1.0

Não implementar antes do release sem nova decisão:

- score de criminalidade;
- tráfico/milícia;
- segurança regional;
- leitura do heatmap Uber;
- captura automática por rolagem;
- comunidade social/gamificada;
- anúncios;
- gamificação;
- moedas;
- marketplace;
- programa avançado de indicação;
- score único definitivo;
- modelo ML complexo;
- previsão percentual com amostra insuficiente;
- iOS.

---

# 19. PÓS-1.0

Possíveis evoluções:

- score Sr. Rotas consolidado;
- custo de oportunidade;
- sazonalidade avançada;
- coletivo ampliado;
- benchmarking anonimizado;
- relatórios avançados;
- Pix Automático;
- indicação/cupom;
- parceiros;
- widgets;
- personalização;
- novos clientes MCP;
- iOS;
- outras plataformas;
- expansão internacional.

---

# 20. REGRA DOCUMENTAL

A partir deste documento:

1. toda fase consulta a `main` atual;
2. este arquivo é atualizado quando houver mudança de decisão;
3. não manter sequências conflitantes em roadmaps paralelos;
4. ZIPs são incrementais e usam arquivos completos;
5. migrations entram somente quando necessárias;
6. Offer Engine permanece separado dos demais motores;
7. nenhuma alteração Supabase/Vercel/GitHub é executada automaticamente sem autorização explícita;
8. decisões do idealizador/BigCorps entram aqui antes de implementação;
9. uma nova decisão registrada substitui a antiga conflitante.

---

# 21. PRÓXIMO MARCO

```text
AGORA
0.21.1-beta
UX final + ✓ de relatório + Buscar + continuidade OCR
        ↓
VALIDAR EM CAMPO
sem regressão do 0.20.3/0.21.0 + fluxo regional
        ↓
PRÓXIMO BLOCO
1.0-A
segurança e hardening para a reta final
```
