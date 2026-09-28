# SR. ROTAS — ROADMAP CANÔNICO MESTRE

CANONICAL_VERSION: 2026-09-28.3
DATA_CANÔNICA: 28/09/2026
CURRENT_HEAD_STAGE: 0.33.7-field / versionCode 84 + Web/Admin P5-04 Pré-1.0
CURRENT_FIELD_TEST: 0.33.6-field / versionCode 83 — HOMOLOGADA
STATUS: RETA FINAL 1.0
README_CANONICO: README-CONTINUIDADE.md

## 0. Filosofia
ESTABILIZAR → CORRIGIR → CONSOLIDAR DADOS → GERAR INTELIGÊNCIA → COMPLETAR PRODUTO → OPERACIONALIZAR → HOMOLOGAR → LANÇAR.
Integridade > estabilidade > contrato > compatibilidade > UX > novas funções.

## 1. Android
0.33.6/vc83 permanece homologada.
0.33.7/vc84 validou Device Identity/Access Resolver em dois aparelhos.
M1 oficial. Reader2 shadow. Controlled Hybrid OFF. Histórico congelado.

## 2. 1.0-A — Segurança
Hardening Supabase aplicado:
- search_path corrigido;
- grants cliente removidos;
- service_role server-only;
- FKs indexadas.
Não criar policies RLS artificiais.
Leaked Password Protection é toggle manual do Auth se ainda estiver desligado.

## 3. 1.0-B — Access Resolver
- HMAC Device Identity server-side;
- raw Android ID não é armazenado;
- limite=2;
- teste server-side 1→2→3 aprovado;
- trial antiabuso sobrevive à troca de conta;
- sessões legadas das contas migradas revogadas;
- guards backend presentes neste pacote;
- enforcement continua OBSERVE até gate comercial/RC.

## 4. 1.0-C — Trial, Pix e créditos
Contrato:
- trial 7 dias a partir da primeira oferta válida;
- 5 créditos temporários;
- R$ 9,90 / 30 dias;
- primeira ativação paga: 20 créditos normais, uma vez;
- renovação não repete os 20.

QA interno aprovado:
- first paid → saldo 20;
- 5 entitlements;
- confirmação duplicada idempotente;
- renovação +30 dias sem welcome novo;
- reserve/consume/refund idempotentes;
- valor/txid divergente → manual_review.

Falta: pagamento Banco Inter real de R$ 9,90.
Cron horário entra neste pacote como fallback; polling Web continua imediato.

## 5. 1.0-E — Admin
P5-03: Control Center.
P5-04: Diagnósticos persistentes + triagem + auditoria.

Admin não deve expor:
- SQL/secrets;
- OCR bruto;
- coordenadas;
- token;
- HMAC de aparelho.

## 6. 1.0-F — Integrações
- OneSignal operacional com entregas reais;
- Web handoff de uso único observado em produção;
- MCP estruturalmente read-only, falta teste com chave real;
- exclusão DB validada e OneSignal obrigatório;
- Device Identity anônima retida somente pela janela antiabuso e expurgada depois.

## 7. V7
Batch canônico `48323962-cabd-497b-890f-8315e0d0753a`.
Não reprocessar.

## 8. Próxima reta
- Pix real;
- MCP real;
- offline/sync;
- soak;
- privacidade/Play/Data Safety;
- Play Integrity observe/soft ou decisão explícita;
- AAB;
- RC sobre 0.33.7 sem apagar dados;
- zero P0/P1.

## 9. Regra
Módulos independentes podem avançar em paralelo desde que não quebrem contratos congelados, integridade ou gates de homologação.

IMPLEMENTADO ≠ HOMOLOGADO.
HEAD ≠ BUILD EM CAMPO.
CI VERDE ≠ TESTE DE CAMPO.
