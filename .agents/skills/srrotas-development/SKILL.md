---
name: srrotas-development
description: "Use for substantive development, debugging, verification, architecture, Android Field, Reader/M1, Radar, backend/API, Supabase, release or handoff work in BigCorps/SrRotas. Do not invoke for trivial text-only edits unless verification or repository invariants are relevant."
---

# Desenvolvimento Sr. Rotas

## Estado real primeiro

- Trabalhe somente em `BigCorps/SrRotas`. GitHub é a fonte da verdade.
- Leia `AGENTS.md` e todo `README-CONTINUIDADE.md`, fonte operacional canônica. Confirme branch, status e HEAD; faça fetch antes de implementar e confira o HEAD remoto pertinente.
- Continue a branch existente de uma feature em andamento. Preserve trabalho local; não troque para main nem crie branch substituta sem necessidade autorizada.
- Não use ZIPs, snapshots antigos, código presumido ou documentação histórica como estado atual. Valide o código/serviço relevante antes de escrever.
- Consulte [invariantes](references/invariants.md) quando o escopo os envolver; releia o README em caso de dúvida ou avanço de estado.

## Planejar e diagnosticar

Antes de editar código crítico ou fazer mudança não trivial, explicite causa/evidência, arquivos envolvidos, invariantes afetados, testes necessários e áreas fora do escopo. Não faça refactor oportunista.

Para bugs, JSON e logs, reconstrua a cadeia causal: localize o estágio que quebra, separe sintoma de causa e compare métricas ao baseline disponível. Não relaxe parser/gate no escuro; prefira diagnóstico adicional quando a evidência ainda não sustentar uma alteração crítica.

## Verificar pelo diff

Escolha apenas as verificações necessárias usando a [matriz de validação e revisão](references/verification-matrix.md). Não declare “corrigido”, “passou” ou “pronto” sem evidência atual; reporte checks bloqueados ou não executados.

Docs/Skills/texto/configuração sem impacto no APK não justificam Gradle, APK ou Actions pesados. Backend/web sem dependência Android também não justifica APK. Mudança Android nativa relevante exige testes JVM, compileDebugKotlin e guards aplicáveis; use `Android CI + Web Build + Field APK` quando o diff realmente chegar ao binário e exigir APK Field. Não execute Actions mecanicamente.

Em código React/Next de `backend/`, use também `$vercel-react-best-practices`, lendo apenas as regras pertinentes. Não carregue Skills web para Kotlin, Android, OCR, Gradle, Radar nativo ou documentação sem código web.

## Revisão seletiva e entrega

Claude não revisa toda mudança. A matriz define revisão cruzada obrigatória para áreas críticas e dispensa texto/docs/CSS/ajustes triviais bem cobertos. Codex implementa, testa e faz commit/push dentro do escopo autorizado; se revisão for necessária, pare antes do merge e produza **Claude Review Handoff** conforme a referência. Claude revisa somente; findings voltam ao Codex, que corrige na mesma branch. Nunca permita dois agentes escrevendo simultaneamente no mesmo branch/worktree. Não instale integração/plugin apenas para revisão hipotética.

Ao concluir implementação relevante, informe branch, base/HEAD inicial, commit, arquivos alterados, testes/resultados, Actions executados ou motivo de dispensa, limitações/riscos e estado de prontidão para Preview, teste manual ou merge. Diferencie CI de homologação em campo. Nunca faça merge sem autorização explícita.
