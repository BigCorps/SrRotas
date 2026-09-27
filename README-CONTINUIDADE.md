# Sr. Rotas — README DE CONTINUIDADE

Versão documental: `2026-09-26.10`  
Roadmap mestre: `ROADMAP-CANONICO.md`  
Roadmap de publicação: `ROADMAP-PLAYSTORE-1.0.md`  
Base Android homologada: `0.33.6-field / versionCode 83`  
Commit: `f2d7166280f58b22d3ff047a39ea7a3eaf36b026`  
CI: Action #135 — SUCCESS  
Vercel do mesmo commit: READY

## Estado
A 0.33.6 deixa de ser candidata e passa a ser base de campo homologada: Assistente/VER/IGNORAR, odômetro/métricas, energia e recovery foram exercitados.

M1 permanece oficial; Reader2 shadow; Histórico Android congelado; V7 não deve ser reprocessada.

## Três produtos
1. Android = operação do motorista.
2. Web `/app` = complemento do Android e mesma identidade visual.
3. Admin `/admin` = console BigCorps, com identidade administrativa própria.

Full-admin permanece exatamente para `contato@bigcorps.com.br` e `jadielalmeida@gmail.com`.

## Segurança atual
Advisor: 5 funções com search_path mutável, leaked-password protection desabilitada, 37 tabelas RLS/no-policy INFO e 10 FKs sem covering index INFO. Revisar sem mudanças cegas.

## Regra
GitHub/Vercel/Supabase podem ser lidos. O usuário sobe ZIP manualmente. Nenhuma escrita remota sem autorização explícita. Branding Web não é motivo para enviar APK ao tester.

Ver `CHECKLIST-1.0.0.md` para o gate restante.
