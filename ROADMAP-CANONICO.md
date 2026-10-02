# SR. ROTAS — ROADMAP CANÔNICO MESTRE

CANONICAL_VERSION: 2026-10-02.5
DATA_CANÔNICA: 02/10/2026
CURRENT_HEAD_STAGE: 0.33.9-field / versionCode 86 — Gate 5 Android Access/UI
CURRENT_FIELD_TEST: 0.33.8-field / versionCode 85 — VALIDADA EM CAMPO
BASE_HOMOLOGADA: 0.33.6-field / versionCode 83 — HOMOLOGADA
STATUS: RETA FINAL 1.0
README_CANONICO: README-CONTINUIDADE.md

## 0. Filosofia
ESTABILIZAR → CORRIGIR → CONSOLIDAR DADOS → GERAR INTELIGÊNCIA → COMPLETAR PRODUTO → OPERACIONALIZAR → HOMOLOGAR → LANÇAR.
Integridade > estabilidade > contrato > compatibilidade > UX > novas funções.

## 1. Android
- 0.33.6/vc83 permanece a base formalmente homologada.
- 0.33.7/vc84 validou Device Identity/Access Resolver em dois aparelhos.
- 0.33.8/vc85 validou Sync Recovery + Crash Flush em campo.
- 0.33.9/vc86 é o HEAD candidato do Gate 5: alinhamento Copiloto gratuito × Inteligência.
- M1 oficial.
- Reader2 shadow.
- Controlled Hybrid OFF.
- Histórico congelado.
- Money/fórmulas não alterados.

## 2. Gate 5 — Android Access/UI
Objetivo:
- preservar Reader/HUD/captura;
- alinhar Android ao modelo comercial já homologado;
- remover referências antigas a créditos/Alpha;
- exibir Copiloto grátis e Inteligência;
- instalar vc86 por cima do vc85 sem limpar dados.

O Gate 5 não altera:
- Reader M1;
- OCR;
- MediaProjection;
- HUD;
- OfferDispatcher;
- Radar legado;
- schema Supabase;
- V7.

## 3. Access Resolver
- HMAC Device Identity server-side;
- raw Android ID não é armazenado;
- limite=2;
- trial antiabuso por aparelho;
- enforcement continua `observe`;
- `require_device_identity=false` até homologação final.

Contrato comercial atual:
- Copiloto: gratuito e permanente para contas não bloqueadas;
- Inteligência: trial ou assinatura;
- R$ 9,90 / 30 dias;
- Pergunte determinístico sem créditos por consulta.

## 4. Comercial / Pix
Homologado em 02/10/2026:
- Pix real de R$ 9,90;
- Banco Inter retornou CONCLUIDA;
- assinatura `core_monthly` ativa por 30 dias;
- Access Resolver retornou PAID_ACTIVE;
- sem cobrança automática;
- trial não concede novos créditos;
- pagamento não concede novos créditos.

Checkout deve identificar:
- Intermediações de Pagamentos BigCorps
- Sr.Rotas | Desenvolvido por BigCorps

## 5. MCP / OAuth
Homologação externa funcional:
- OAuth 2.1;
- DCR;
- PKCE S256;
- refresh rotation;
- revogação;
- reautorização;
- 12 ferramentas read-only;
- sem chave manual.

A duplicidade histórica de perfis do usuário Jadiel foi consolidada em um único driver canônico.
O perfil canônico passou a concentrar Android operacional, Web/OAuth e V7 sem reprocessamento.

## 6. V7
Batch canônico preservado.
Não reprocessar.

## 7. Gate de campo vc86
Instalar por cima do vc85.
Não desinstalar e não limpar dados.

Validar:
- app abre e mantém sessão/configurações;
- Reader/HUD continuam funcionando;
- oferta real continua sendo capturada;
- usuário mostra Copiloto + Inteligência;
- sem referências a créditos/Alpha;
- assinatura paga é reconhecida;
- Estatísticas e Pergunte funcionam;
- MCP consulta atividade recente do mesmo perfil;
- diagnóstico exporta Access Resolver sem regressão.

## 8. Próxima reta após vc86
- teste de novo usuário ponta a ponta;
- offline→online;
- soak;
- bateria/sync/crash;
- promover observe→enforce somente após homologação;
- Play Integrity soft;
- Data Safety/declarações;
- MediaProjection/FGS;
- assinatura final/assetlinks;
- AAB;
- RC sobre Field sem apagar dados;
- zero P0/P1.

## 9. Regra
Módulos independentes podem avançar em paralelo desde que não quebrem contratos congelados, integridade ou gates de homologação.

IMPLEMENTADO ≠ HOMOLOGADO.
HEAD ≠ BUILD EM CAMPO.
CI VERDE ≠ TESTE DE CAMPO.
