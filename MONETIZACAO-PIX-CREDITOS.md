# Sr. Rotas — Arquitetura comercial: Pix + créditos de IA

Status: decisão de produto para a versão 1.0.

## Plano-base

Preço-alvo inicial: **R$ 9,90/mês**.

Inclui, enquanto a assinatura estiver ativa:
- leitura OCR;
- HUD;
- cálculos locais;
- histórico;
- estatísticas estruturadas;
- sincronização;
- MCP somente leitura;
- personalização prevista no plano;
- acesso à carteira de IA.

Não há cobrança por quantidade de corridas/ofertas analisadas.

## Compra fora do aplicativo

Fluxo escolhido:

`srrotas.com → conta → Pix → confirmação → entitlement ativo → app reconhece a conta`

Na versão distribuída pela Play Store:
- não incorporar checkout Pix;
- não usar Google Play Billing no desenho atual;
- tratar o app como consumo/acesso ao serviço adquirido fora dele;
- mostrar status da assinatura e conta;
- revisar a política vigente imediatamente antes da submissão final.

## Créditos de IA

### Boas-vindas
**20 créditos iniciais** após a primeira ativação paga.

Regras:
- uma vez por conta, não por instalação;
- reinstalar o app não recria créditos;
- webhook idempotente;
- cancelamento/retorno de pagamento deve ter tratamento no ledger, não alteração manual de saldo.

### Consumo
Versão inicial:
- 1 pergunta comum concluída com sucesso = 1 crédito.

Não descontar crédito por:
- OCR;
- cálculo do HUD;
- filtros;
- analytics SQL/TypeScript;
- MCP;
- erro de autenticação;
- timeout/erro do provedor sem resposta entregue.

Análises muito grandes poderão ter custo maior no futuro, desde que informado antes da execução.

### Pacotes
Pacotes avulsos vendidos por Pix no site.

**Preços e quantidades: TBD após medição real do custo da IA.**

Não fechar margem antes de medir:
- tokens médios de entrada;
- tokens de saída;
- número médio de ofertas enviadas;
- modelo utilizado;
- cache/compactação;
- frequência de perguntas.

## Modelo de dados recomendado

### subscriptions
- id
- user_id
- plan_id
- status
- starts_at
- current_period_end
- canceled_at
- payment_provider
- created_at
- updated_at

### payments
- id
- user_id
- subscription_id nullable
- kind (`subscription`, `credit_pack`)
- provider
- provider_payment_id
- amount_cents
- status
- paid_at
- metadata
- created_at

### credit_wallets
- user_id
- balance
- lifetime_granted
- lifetime_spent
- updated_at

### credit_transactions
Ledger imutável:
- id
- user_id
- type (`welcome`, `purchase`, `reserve`, `consume`, `refund`, `adjustment`)
- amount
- reference_id
- idempotency_key
- metadata
- created_at

### entitlements
Permissões derivadas de plano/status, por exemplo:
- core_app
- history
- mcp
- ai
- advanced_analytics
- future_features

## Transação segura da IA

Fluxo recomendado:

1. validar assinatura/entitlement;
2. validar saldo;
3. criar reserva idempotente de crédito;
4. chamar modelo;
5. resposta entregue → converter reserva em consumo;
6. falha técnica → estornar/liberar reserva;
7. persistir usage/cost_usd para análise de margem.

Nunca fazer apenas:
`balance = balance - 1`
sem ledger e idempotência.

## MCP

MCP:
- não usa a OpenAI da BigCorps;
- não consome créditos Sr. Rotas;
- autentica o motorista;
- só acessa os dados daquele motorista;
- read-only.

Isso permite ao usuário usar ChatGPT, Claude, Cursor ou outro cliente compatível que ele já assina.

## Pricing

R$ 9,90 é o preço-alvo inicial do core.

Antes do lançamento, simular:
- 1.000 usuários;
- 10.000 usuários;
- 100.000 usuários;
- Supabase;
- Vercel;
- armazenamento;
- OneSignal/FCM;
- suporte;
- processamento Pix;
- IA por crédito;
- impostos/taxas;
- churn;
- margem.

O preço dos créditos é independente do preço do core e deve manter margem mesmo para usuários intensivos.
