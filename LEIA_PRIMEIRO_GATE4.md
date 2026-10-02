# Sr.Rotas — Gate 4

Este ZIP é incremental sobre `f018618feb49f2d12b4016a15311e976ce853c75`.

## Depois do upload
1. Esperar GitHub Actions + Vercel verdes.
2. NÃO executar SQL manualmente.
3. NÃO alterar enforcement.
4. Informar o novo SHA para a instância desenvolvedora.
5. A instância aplica a migration Gate 4 via Supabase MCP e valida.
6. Deploy das Edge Functions `srrotas-create-pix` e `srrotas-check-pix`.
7. Teste real de Pix R$ 9,90.
8. Só depois avaliar `observe -> enforce`.

## Não contém
- alterações Android;
- Reader/OCR/HUD/Radar;
- mudança de plan_id;
- mudança do preço;
- reprocessamento V7;
- exclusão de crédito histórico.
