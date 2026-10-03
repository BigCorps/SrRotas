# AGENTS.md — Sr. Rotas

Antes de qualquer alteração:

1. Leia `README-CONTINUIDADE.md` inteiro.
2. Trate-o como a única fonte operacional de verdade.
3. Valide o estado atual no código e, quando aplicável, GitHub Actions, Vercel e Supabase antes de mudar algo.
4. Documentos históricos de commits anteriores não definem o estado atual.
5. Não reintroduza comportamento antigo apenas porque aparece no Git history.
6. Preserve os invariantes e a seção “NÃO podem regredir” do README canônico.
7. Atualize `README-CONTINUIDADE.md` no mesmo PR sempre que mudar versão, arquitetura, gate, feature flag, contrato comercial, schema/semântica ou homologação.
8. Não crie novos handoffs/QA/fases/manifests na raiz. Use `CHANGELOG.md` para histórico e `docs/contracts/` apenas para contratos permanentes.
9. Para tarefas Android, nunca altere Reader/OCR/HUD/parser/dedupe/fórmulas em correções de outra área sem evidência e escopo explícitos.
10. Para Supabase/Vercel/GitHub, valide antes de escrever e mantenha mudanças mínimas e reversíveis.

Se faltar contexto, não adivinhe: leia o código/serviço atual e compare com o README canônico.
