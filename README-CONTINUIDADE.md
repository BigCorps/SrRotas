# Sr. Rotas — README DE CONTINUIDADE

**Leia este arquivo antes de alterar qualquer coisa.**

Versão documental: `2026-09-26.4`
Roadmap mestre: `ROADMAP-CANONICO.md`
HEAD técnico: `0.33.5-field / versionCode 82`
APK atualmente em campo: `0.33.2-field / versionCode 79`

## 1. Como trabalhar neste projeto

- Repositório: `BigCorps/SrRotas`.
- O agente pode inspecionar GitHub, Actions, Vercel e Supabase.
- O agente **não deve escrever diretamente no GitHub**.
- Entregas de código devem ser ZIPs com arquivos completos em caminhos relativos.
- O usuário sobe manualmente na `main`.
- Não criar branch/PR sem pedido explícito.
- Supabase só recebe escrita com autorização explícita.
- Antes de alterar, conferir código/CI/ambientes reais.

Todo ZIP técnico deve conter:
- `ROADMAP-CANONICO.md`;
- `README-CONTINUIDADE.md`;
- `MANIFEST-<VERSAO>.json`;
- arquivos modificados completos;
- `LEIA-PRIMEIRO-<VERSAO>.md` quando houver instrução específica.

## 2. Estado exato antes da 0.33.5-CI2

`main`:
- commit `c00eba3f2df7ab4e295539b6b7c350ecbdbffe30`;
- `0.33.5-field / vc82`.

Action #128:
- Architecture regression guard: SUCCESS;
- Unit tests: FAILURE;
- debug/release APK: SKIPPED.

A falha do step `Unit tests` ocorreu antes dos testes:
`ActiveAssistantPolish0265.kt:171:44 Unresolved reference 'line'`.

`SrUi023.Palette` usa `outline`, não `line`.

CI2:
- troca somente `p.line` → `p.outline`;
- corrige também o teste documental legado de 0.33.0 que seria o próximo bloqueio;
- mantém `0.33.5-field / vc82`;
- não muda motor/ranking, Reader, captura, Histórico, V7, backend ou Supabase.

## 3. Campo

O irmão continua usando:
`0.33.2-field / vc79`

Objetivo:
- Capture Continuity;
- notificação de leitura pausada;
- retomar captura com novo consentimento;
- preservar a mesma jornada;
- crash observability.

**Aguardar o JSON 3.**
Não pedir anotações durante a direção.

Mesmo se CI2 ficar verde, **não enviar 0.33.5 ao irmão antes da análise do JSON 3**.

## 4. P1 já pronto em código

`0.33.3-field / vc80`
- Action #125 verde;
- Odometer Sync Recovery.

`0.33.4-field / vc81`
- Action #126 verde;
- Floating Metrics Restore.

A 0.33.5 verde será candidata a consolidação dessas correções depois do gate P0.

## 5. Módulos congelados / contratos preservados

- Histórico Android: FROZEN.
- M1: Reader oficial.
- Reader2 Controlled Hybrid: OFF.
- Money Roles/Turbo: congelado salvo regressão real.
- V7 processamento: concluído.
- `ride_offers`: operacional real.
- V7: histórico analítico de ofertas observadas.
- Base Coletiva: apenas dados reais + opt-in; não misturar V7.

## 6. V7

Batch:
`48323962-cabd-497b-890f-8315e0d0753a`

Conferência live de 26/09/2026:
- ready;
- V7.5;
- 36.089 registros;
- 33.532 fully ready;
- 35.996 temporal ready;
- 35.452 route flow ready;
- 34.129 financial ready;
- 0 invalid;
- 0 duplicate;
- `canonical_for_intelligence = true`.

Próxima etapa de dados:
**Intelligence QA/hardening**, não reprocessamento do histórico.

## 7. Vercel / Supabase

Vercel:
- deploy de produção do commit `c00eba3f...`: READY;
- nenhum erro de runtime na janela de 24h consultada.

Supabase:
- projeto `gheymrttmfdxnjdbgvgl`;
- ACTIVE_HEALTHY.

Advisors atuais devem entrar no audit P5/P6, sem correção em massa:
- 37 tabelas com RLS sem policy;
- 5 funções com search_path mutável;
- leaked password protection desabilitada;
- FKs sem índices de cobertura.

Uma tabela com RLS e sem policy pode significar negação total intencional. Classificar o modelo de acesso antes de criar policies.

## 8. Depois de CI verde

Continuar aguardando/analisando JSON 3 para fechar P0.

Em paralelo, pode avançar:
**Web/Admin factual audit + Intelligence QA/hardening**.

Não promover 1.0 sem Admin Web mínimo operacional.

## 9. Regra de ouro

**HEAD não significa homologado. IMPLEMENTADO não significa HOMOLOGADO.**

Sempre distinguir:
- código atual;
- CI;
- APK realmente instalado;
- evidência real de campo;
- estado live de Supabase/Vercel.
