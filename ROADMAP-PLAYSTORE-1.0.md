# Sr. Rotas — Plano de ação oficial até a Play Store e divulgação

Data-base: 17/08/2026  
Produto: Sr. Rotas  
Android principal: `com.srrotas.app`  
Stack: Kotlin Android + Next.js + Supabase + Vercel  
Empresa: BigCorps  
Suporte: contato@bigcorps.com.br

## Decisões já fechadas

1. **Um único aplicativo na Play Store:** `com.srrotas.app`.
2. O núcleo crítico fica nativo e local: MediaProjection, OCR, parser, cálculos e HUD.
3. O dashboard Next.js ficará acessível pelo navegador e pelo mesmo aplicativo Android, via integração segura.
4. O antigo TWA `com.srrotas.web` fica opcional e não será um segundo app da Play Store.
5. **Ícone oficial:** a arte do personagem enviada em 17/08/2026, sem nome abaixo. O arquivo-fonte exato está preservado em `docs/brand/sr-rotas-icon-source.png`. A adaptação para launcher/adaptive/PWA acontece na fase 0.6 sem redesenhar o personagem.
6. **Monetização escolhida:** mensalidade do Sr. Rotas adquirida fora do app, no site, com Pix. Preço-alvo inicial: **R$ 9,90/mês**.
7. O binário da Play Store será de consumo/acesso: login, status da assinatura e uso do serviço. Não haverá checkout Pix embutido no app da Play.
8. **IA própria é opcional e usa créditos.** A conta recebe **20 créditos iniciais** uma única vez após a primeira ativação paga. Pacotes avulsos serão vendidos por Pix no site.
9. OCR, HUD, cálculos, histórico estruturado e MCP **não gastam créditos de IA**.
10. MCP continua **somente leitura**. Nunca aceita/recusa corridas nem controla o Uber.
11. OneSignal entra somente depois do motor estar congelado.
12. AccessibilityService deve ser removido do build de produção da Play Store se MediaProjection continuar suficiente nos testes finais.
13. Raw OCR não é enviado ao servidor por padrão; screenshots privadas seguem opcionais e desligadas por padrão.

---

## 0.5.2 — Engine Freeze Candidate

### Objetivo
Fazer uma última rodada controlada com o motor atual, sem mudar fórmulas ou thresholds já aprovados.

### Entrega
- mesma velocidade da 0.5.1;
- mesmos filtros e dedupe;
- telemetria local de desempenho do OCR, sem conteúdo da tela;
- linha final no log:
  `DESEMPENHO OCR · ... ocr_médio=...ms · ocr_máx=...ms`.

### Validar no aparelho
- HUD aparece rápido;
- ofertas curtas não são perdidas sistematicamente;
- sem falso positivo grave;
- Radar não mistura cards;
- sem R$/h absurdo;
- duplicatas controladas;
- sem travamento/aquecimento anormal;
- `Pendentes` volta a zero com internet.

### Critério de saída
Se a rodada vier limpa, declarar **Offer Engine v1 congelado**. Depois disso só alterar o parser com evidência reproduzível de um novo caso real.

---

## 0.6 — Design System, identidade e UX

### Objetivo
Parar de criar UI tela por tela e estabelecer a linguagem visual final antes de ampliar o produto.

### Identidade
- aplicar a arte oficial do Sr. Rotas nos ícones Android;
- adaptive icon;
- launcher icon mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi;
- PWA/favicon/web app;
- assets de Play Store derivados do mesmo original;
- sem redesenhar o personagem.

### Design tokens
Definir centralmente:
- espaçamento;
- raios de borda;
- tipografia;
- elevação;
- cores de superfície/ação/status;
- tamanhos de toque;
- estados loading/erro/vazio/desabilitado.

### Tamanho e densidade
Criar preferências globais:
- **Compacto**
- **Normal**
- **Grande**

Separar:
- densidade das telas do app;
- tamanho dos cards;
- tamanho do HUD;
- tamanho da fonte.

A escolha deve se propagar por componentes, não por telas isoladas.

### Navegação-base
- Início
- Jornada
- Histórico
- IA
- Perfil

### Critério de saída
Todas as telas atuais usam o mesmo sistema visual e continuam corretas em telas pequenas/grandes, modo claro/escuro e fontes maiores.

---

## 0.7 — Conta, onboarding e app autossuficiente

### Objetivo
Uma pessoa nova precisa instalar e configurar o app sem ajuda da BigCorps.

### Fluxo
`Instalar → Entrar/Criar conta → Consentimentos → Permissões → Estratégia inicial → Tutorial do HUD → Primeira jornada`

### Requisitos
- sessão segura;
- recuperação de acesso;
- pareamento invisível/automático quando possível;
- status claro da conta;
- status claro da sincronização;
- modo offline;
- fila pendente explícita;
- erros em linguagem comum;
- suporte dentro do app;
- deep links;
- integração segura do dashboard web.

### Critério de saída
Um beta tester desconhecido consegue chegar à primeira jornada apenas seguindo o app.

---

## 0.8 — Histórico, dashboard e analytics sem IA

### Objetivo
Extrair valor máximo dos dados sem custo de modelo.

### Métricas
- ofertas por jornada;
- R$/km, R$/min e R$/h;
- boas/atenção/ruins;
- horários;
- dias;
- serviço (UberX/Comfort/Black/Priority);
- Radar x Exclusive;
- avaliação;
- comparação entre períodos;
- tendências;
- filtros;
- gráficos.

### Regra
Cálculo agregado fica em SQL/TypeScript sempre que possível. Não gastar IA para produzir uma média ou filtro que o banco consegue responder.

### Critério de saída
O motorista entende seu padrão de ofertas mesmo sem usar um único crédito de IA.

---

## 0.9 — Pesquisa IA + MCP

### IA Sr. Rotas
- chat contextual;
- respostas baseadas somente nos dados do motorista;
- preferência por resumo agregado;
- enviar linhas individuais ao modelo só quando necessário;
- exibir período e quantidade de ofertas analisadas;
- contabilização de créditos transacional;
- falha da IA não consome crédito definitivo.

### MCP
Ferramentas somente leitura, por exemplo:
- consultar jornadas;
- consultar ofertas;
- comparar períodos;
- consultar estratégia;
- consultar métricas;
- resumir desempenho estruturado.

### Regra comercial
- IA Sr. Rotas: consome créditos;
- ChatGPT/Claude/Cursor via MCP: não consome créditos Sr. Rotas;
- usuário escolhe qual IA prefere.

### Critério de saída
A mesma pergunta factual retorna dados coerentes via dashboard, IA própria e MCP.

---

## 0.10 — Monetização Pix + créditos + entitlements

Documento detalhado: `MONETIZACAO-PIX-CREDITOS.md`.

### Base
- plano alvo: R$ 9,90/mês;
- compra/renovação no site `srrotas.com`;
- Pix;
- app da Play não processa o pagamento;
- 20 créditos iniciais de IA, uma vez por conta paga;
- pacotes avulsos de crédito no site;
- preço dos pacotes só será fechado após medirmos custo real.

### Backend
Separar:
- assinatura;
- pagamentos;
- carteira de créditos;
- movimentações da carteira;
- entitlements.

### Critério de saída
Pagamento duplicado não duplica saldo; webhook repetido é idempotente; falha de IA não some com crédito; expiração de assinatura não apaga histórico do usuário.

---

## 0.11 — OneSignal e comunicação

### Android
Implementar OneSignal nativo + FCM.

### Identidade
Associar usuário autenticado por External ID.

### Uso
- nova versão;
- manutenção;
- compatibilidade;
- falha de sincronização;
- resumo;
- recursos novos.

### Não usar para
Avaliação de oferta em tempo real. Esse caminho permanece local.

### Web Push
Somente depois de `srrotas.com` estar registrado e a origem final definida.

---

## 0.12 — Segurança, privacidade e Play Compliance

### Android/Play
- target API 36;
- release AAB;
- assinatura definitiva;
- declaração do Foreground Service `mediaProjection`;
- vídeo de demonstração do fluxo de captura iniciado pelo usuário;
- revisar AccessibilityService e remover do release se dispensável;
- política de privacidade;
- termos;
- Data Safety;
- exclusão de conta dentro do app e em página web;
- retenção de dados documentada;
- segurança de sessão/tokens;
- nenhum segredo admin/service-role no cliente;
- revisão de bibliotecas e permissões.

### Pagamento fora do app
Antes da submissão, revisar novamente a política de pagamentos vigente. O desenho atual é **consumption-only**: compra no site, app acessa o serviço já adquirido; sem checkout Pix embutido na versão Play.

### Critério de saída
Checklist de Play sem pendência e revisão interna de segurança concluída.

---

## 0.13 — Closed Beta

### Público
Começar com 10–30 motoristas reais.

### Matriz
- Samsung;
- Motorola;
- Xiaomi/Redmi;
- outros fabricantes relevantes;
- telas pequenas/grandes;
- Android 12–16;
- UberX/Comfort/Black/Priority conforme disponibilidade.

### Medir
- latência;
- perdas;
- falsos positivos;
- crashes;
- ANRs;
- CPU/bateria;
- sincronização;
- retenção;
- clareza do onboarding;
- conversão até primeira jornada.

### Play
Se a conta de desenvolvedor estiver sujeita a requisito de teste fechado, cumprir a janela e quantidade exigidas pela Play Console antes de produção.

---

## 1.0 RC — Release Candidate

### Produto
- sem bugs P0/P1;
- onboarding fechado;
- assinatura e crédito;
- IA/MCP;
- histórico;
- OneSignal;
- conta/exclusão;
- suporte;
- offline/sync;
- atualização.

### Store Listing
- ícone;
- feature graphic;
- screenshots;
- descrição curta;
- descrição longa;
- vídeo se usado;
- categoria/tags;
- política de privacidade;
- suporte;
- Data Safety;
- declaração FGS;
- classificação de conteúdo.

### Operação
- monitoramento Supabase/Vercel;
- custos;
- alertas;
- suporte;
- processo de incidentes;
- métricas de produto.

---

## 1.0 — Publicação e divulgação

### Pré-lançamento
- landing `srrotas.com`;
- cadastro/login;
- checkout Pix;
- FAQ;
- comparativo de recursos;
- página MCP;
- tutoriais.

### Mensagens-base
**“Você decide a corrida. O Sr. Rotas faz as contas.”**

**“Use a IA do Sr. Rotas ou conecte seus dados à IA que você já usa.”**

### Canais
- Instagram;
- TikTok;
- YouTube/Shorts;
- grupos/comunidades de motoristas;
- creators do nicho;
- indicação.

### Regra de produto
Depois do Engine Freeze, nenhuma ideia nova pula o roadmap salvo:
1. bug crítico;
2. risco de segurança;
3. risco de bloqueio/reprovação de loja.
Todo o restante vai para backlog pós-1.0.

---

## Pós-1.0 — Backlog inicial

- Pix Automático/recorrência, se comercialmente vantajoso;
- novos provedores/plataformas de mobilidade somente após revisão técnica e jurídica;
- personalização avançada;
- benchmarking anonimizado e opt-in;
- relatórios avançados;
- novos clientes MCP;
- widgets;
- recursos premium adicionais;
- experimentos de pricing.
