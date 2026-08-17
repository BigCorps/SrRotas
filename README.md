# Sr. Rotas 2.0 — Alpha 0.7.0

Sr. Rotas é um copiloto Android para motoristas de aplicativo.

**Desenvolvido pela BigCorps** — contato@bigcorps.com.br

## 0.7 — Conta, onboarding e app autossuficiente

A fase 0.7 adiciona:
- conta e sessão por aparelho;
- onboarding guiado;
- permissões;
- estratégia inicial;
- tutorial do HUD;
- estados de online/offline/sincronização;
- compatibilidade com sessões Alpha já existentes.

## Segurança da sessão

O Android não acessa as tabelas do Supabase diretamente. Login/registro acontecem pelo backend, e o aplicativo recebe um token aleatório exclusivo do aparelho. O banco armazena somente o hash desse token em `driver_devices`.

## Alpha fechado

Para facilitar testes, o backend 0.7 cria usuários já marcados como e-mail confirmado. A etapa de hardening 0.12 deve habilitar verificação real de e-mail e revisar rate limiting antes da Play Store.

## Offer Engine

O motor continua congelado:
`parser_version = sr-rotas-v0.5.4`.

## Próxima fase

0.8 — Histórico, analytics determinísticos, filtros, comparações e gráficos.
