# Sr. Rotas — README DE CONTINUIDADE

Versão documental: `2026-09-26.9`
Roadmap mestre: `ROADMAP-CANONICO.md`
HEAD técnico Android: `0.33.6-field / versionCode 83` — candidata, ainda não enviada ao tester
APK atualmente em campo REAL: `0.33.5-field / versionCode 82`
Frente Web: `P5-03 — Admin Control Center` — produção READY

## 1. Como trabalhar
- Repositório: BigCorps/SrRotas.
- Ler GitHub, Actions, Vercel e Supabase antes de alterar.
- Não escrever diretamente no GitHub.
- Usuário sobe ZIP manualmente na main.
- Não criar branch/PR sem pedido.
- Supabase só recebe escrita via ferramenta com autorização explícita.
- Todo ZIP técnico deve conter:
  - `ROADMAP-CANONICO.md`;
  - `README-CONTINUIDADE.md`;
  - manifest;
  - arquivos completos;
  - LEIA-PRIMEIRO quando aplicável.

## 2. Regra nova de APK
Não enviar APK nova por simples incremento de versão.

Próxima APK para o tester exige:
- mudança Android perceptível;
- roteiro de validação claro;
- Admin Web pronto para acompanhar o resultado;
- CI verde.

Commits somente Web podem disparar a Action e gerar APK, mas **essa APK não deve ser enviada**.

## 3. Estado do campo
Tester está na:
`0.33.5-field / versionCode 82`.

O JSON recebido confirma a versão, captura ativa e Reader operando.
Porém:
- 0.33.3: metric_attempts=0 e energy_attempts=0;
- Supabase da jornada: vehicle_metrics=0, energy_entries=0;
- 0.33.2 notification: posted_episodes=0;
- Assistente 0.33.5 não tem telemetria própria exportada.

Portanto a percepção "aparentemente nada mudou" é compatível com os dados: os fluxos novos não foram realmente exercitados ou não são observáveis no diagnóstico atual.

## 4. Candidata Android 0.33.6
A próxima mudança significativa agora está materializada como `0.33.6-field / versionCode 83`:
- `active_assistant_0336` diferencia regra não elegível, avaliação, sugestão comprometida, overlay visto e decoração aplicada;
- conta IGNORAR, VER e fechamento externo;
- o balão real ganha identificação `SR • ASSISTENTE ATIVO`;
- ranking, thresholds e cooldowns continuam iguais;
- M1 continua oficial e Reader2 segue sem efeito operacional;
- sem backend novo, sem migration e sem OpenAI.

A rodada também deve exercitar:
- odômetro inicial/final;
- um lançamento de energia/combustível;
- conferência no detalhe do motorista no `/admin`;
- exportação do diagnóstico ao fim.

## 5. Web
P5-01 continua sendo o Web complementar do motorista:
- Início;
- Agora;
- Histórico;
- Conta/perfil/plano e fluxos Web.

P5-03 redefine `/admin` como console interno de gestão BigCorps, não como cópia Web do aplicativo.

Módulos do Admin:
- Painel executivo;
- Usuários;
- Operação / Radar;
- Financeiro;
- Custos;
- Dados / V7;
- Sistema.

Detalhe de usuário:
- conta;
- aparelhos;
- jornadas e últimas ofertas;
- trial;
- assinatura e pagamentos;
- carteira/créditos;
- IA;
- notificações;
- sessões Web;
- MCP;
- odômetro/energia.

Ações administrativas seguras disponíveis:
- revogar/reativar aparelho;
- estender trial em 1/7/30 dias;
- encerrar sessões Web;
- revogar tokens MCP.

Saldo, assinatura e exclusão de conta continuam sem override administrativo direto até termos rotina transacional/auditável específica.

## 6. Segurança do Admin
- full-admin exatamente para `contato@bigcorps.com.br` e `jadielalmeida@gmail.com`;
- confirmação para ações de suporte;
- rate limit;
- log de runtime das ações;
- sem raw OCR;
- sem coordenadas;
- sem secrets Pix/provider;
- sem migration neste pacote;
- sem OpenAI nova.

## 7. V7
Não reprocessar.
V7 é oferta histórica observada, não corrida concluída.
ride_offers continua operacional real.

## 8. CI
Action #134: verde no commit Admin `e42f8d8e3522b3e644e1015e7b4d0b4fea6012eb`.
Vercel do mesmo commit: READY em produção.
A candidata Android 0.33.6 ainda precisa de uma nova Action verde depois do upload antes de ser enviada ao tester.

Compatibilidade temporária, **não representa o estado real de campo**:
LEGACY_CI_MARKER_ONLY — APK atualmente em campo: `0.33.2-field / versionCode 79`

Esse marcador existe apenas para o guard legado passar durante a frente Web.
O guard e o teste devem ser corrigidos de forma limpa no próximo pacote Android significativo, antes de enviar nova APK.

## 9. Regra de ouro
HEAD não significa homologado.
IMPLEMENTADO não significa HOMOLOGADO.
Build gerada automaticamente não significa build para o tester.

Sempre cruzar:
código + Action + JSON real + Supabase + Vercel + documentação.
