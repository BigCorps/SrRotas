# Sr. Rotas — README DE CONTINUIDADE

Versão documental: 2026-09-26.5
Roadmap mestre: ROADMAP-CANONICO.md
HEAD Android técnico: 0.33.5-field / vc82
APK em campo: 0.33.5-field / vc82
Frente Web: P5-01 — Início + Agora + Histórico
Baseline Web: Next.js 16.3.6

## Como trabalhar
- Repositório: BigCorps/SrRotas.
- Ler GitHub, Actions, Vercel e Supabase para diagnóstico.
- Não escrever diretamente no GitHub; o usuário sobe ZIPs na main.
- Não criar branch/PR sem pedido.
- Supabase só recebe escrita com autorização explícita.
- Todo ZIP técnico: ROADMAP + README + MANIFEST + arquivos completos + LEIA-PRIMEIRO quando aplicável.

## Android
Action #129 verde para 0.33.5-field/vc82.
Tester já recebeu a build.
Ela consolida Capture Continuity, Odometer Recovery, Floating Metrics e Assistente UX.
Próximo JSON deve validar especialmente recovery notification, métricas de veículo/energia, HUD restaurado e Assistente IGNORAR|VER.

Não promover Reader2 Controlled Hybrid. M1 é oficial.
Histórico Android e Money Roles/Turbo continuam congelados salvo regressão factual.

## V7 / inteligência
V7 já foi processado. Não reprocessar.
Batch canônico: 48323962-cabd-497b-890f-8315e0d0753a.
V7 é oferta histórica observada, não corrida realizada.

Ownership e isolamento foram revalidados:
- V7 canônico pertence ao driver 267c61ce-7d2c-4171-9ba2-226e3b61b923;
- conta do tester permanece isolada;
- proprietário V7 recebe 35.996 historical_v7 + 32 operacionais na view canônica.

## Web
Antes do P5-01, o Web era principalmente central de conta; rotas de produto como /app/agora e /app/historico redirecionavam ao perfil.

P5-01 substitui isso por:
- /app = dashboard real;
- /app/agora = now-intelligence explicável;
- /app/historico = analytics + jornadas;
- /app/historico/[id] permanece detalhe já existente;
- footer passa a Início | Agora | Histórico | Usuário | Plano.

Não cria API nem migration.

Próximo pacote previsto:
- Configurações;
- IA;
- depois Admin operacional.

## Segurança
Next atualizado para 16.3.6 e deploy de produção READY.
RLS sem policy não deve ser corrigido em massa: tabelas são backend-only no modelo atual.
Hardening futuro:
- fixar search_path em 5 helpers;
- ativar/revisar leaked password protection;
- avaliar índices de 10 FKs;
- revisar grants/advisors a cada migration.

## Regras de dados
ride_offers = operacional real.
V7 = histórico analítico.
Base Coletiva = dados reais opt-in.
Nenhum painel deve chamar V7 de corrida aceita/concluída.
Lucro de oferta/estimativa não é lucro realizado.

## Regra de ouro
HEAD não significa homologado.
Implementado não significa homologado.
Sempre cruzar código + CI + campo + Supabase + Vercel.
