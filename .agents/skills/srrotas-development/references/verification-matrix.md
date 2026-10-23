# Validação proporcional e revisão cruzada

Escolha pelo diff e pelos contratos afetados. Autorizações e restrições explícitas da tarefa prevalecem. Registre evidência atual, sem builds/checks repetidos que não resolvam incerteza nova.

| Tipo de alteração | Validação esperada | Revisão cruzada |
|---|---|---|
| Docs/Skills/texto/configuração sem impacto no APK | `git diff --check`; frontmatter/links/escopo quando aplicável. Sem Gradle, APK ou Action Android. | Dispensada |
| Backend Next/React | Typecheck/test/build relevantes; usar `$vercel-react-best-practices` para código web. Sem APK salvo dependência real. Preview quando necessário. | Conforme risco; pequenos CSS/ajustes visuais bem cobertos dispensados |
| Android UI não crítica | Testes relacionados + `compileDebugKotlin`; guards aplicáveis. APK/Action somente quando necessário para validação manual. | Geralmente dispensada |
| M1/OCR/parser/dedupe/admission | Testes unitários/contract, guards, compile, Android Action e field test antes de merge. | Obrigatória |
| MediaProjection/overlays críticos | Testes de lifecycle/contratos, guards, compile e APK/field quando aplicável. | Obrigatória |
| Supabase/schema/migration/RLS/auth | Validação específica de autorização, policies, schema/contratos e impacto; sem migrations/destruição fora do escopo. | Obrigatória |
| Autenticação/autorização/OAuth/MCP/APIs públicas | Testes de identidade, autorização e contrato conforme diff. | Obrigatória |
| Pagamento/financeiro | Testes específicos de valores, estados, idempotência e contratos afetados. | Obrigatória |
| Dados sensíveis/privacidade | Verificar coleta, retenção, exportação, logs e limites de acesso afetados. | Obrigatória |
| Signing/workflow/release | Verificação específica de permissões, assinatura, gatilhos e artifacts; Actions somente se necessários. | Obrigatória |
| Grande refactor arquitetural/alto risco de regressão de produção | Plano, invariantes, regressões relevantes e validação ponta a ponta proporcional. | Obrigatória |

## Codex → Claude → Codex

Claude não é gate universal. Codex constrói → testa → commit/push autorizados. Para os riscos acima, pare antes do merge e entregue o bloco abaixo; não envie mensagens externas nem instale plugins automaticamente. A integração só é usada quando a tarefa realmente exigir revisão e houver autorização pertinente.

Claude revisa sem escrever na branch/worktree; findings retornam ao Codex, que corrige na própria branch e verifica novamente o necessário. Nunca permitir escritores simultâneos. Merge exige autorização explícita, mesmo após revisão favorável.

## Claude Review Handoff

Use este título exato e preencha:

- Repositório: `BigCorps/SrRotas`.
- Branch; base SHA; head SHA exatos.
- Escopo da mudança e áreas excluídas.
- Invariantes que não podem regredir.
- Arquivos críticos e pontos de integração.
- Testes/guards/Actions já executados, resultados e checks pendentes.
- Riscos específicos a procurar, sustentados pelo diff/evidência.
- Instrução: **revisar SOMENTE; não editar, não fazer commit/push nem escrever nesta branch/worktree**.

## Handoff final ao usuário

Informar branch, base/HEAD inicial, commit, arquivos, testes/resultados, Actions ou razão para não executar, limitações/riscos e se pronto para Preview, teste manual ou merge. Prontidão deve refletir checks pendentes e homologação; nunca afirmar aprovação sem evidência nem fazer merge automaticamente.
