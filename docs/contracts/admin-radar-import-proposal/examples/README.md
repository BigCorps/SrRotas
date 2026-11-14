# Exemplos da proposta — não executar

Somente JSON ilustrativo do draft `srrotas.radar-admin-import.draft.v0.1`. Não é o contrato v1.3,
não há compatibilidade v1/v2 e não existe endpoint implementado para estes arquivos.
Nomes, posições e direitos são sintéticos; não representam autorização nem geodados de produção.

1. `payload.proposal.json`: envelope misto de cinco itens. Os dois primeiros só seriam prontos
   em fixture com namespace e documentos previamente aprovados; não são prontos por preencher strings.
   O terceiro pressupõe catálogo sintético com identidade exata já existente; o quarto tem estimativa;
   o quinto tem coordenada inválida. A classificação depende do servidor/política/catálogo, não do arquivo.
2. `preview-response.proposal.json`: exemplo de resposta 200 sem qualquer write. Hash zero, token e
   policy_version são placeholders ilustrativos, não um resultado HTTP real ou assinatura válida.
3. `commit-request.proposal.json`: POST proposto com `Idempotency-Key: 00000000-0000-4000-8000-000000000099`,
   selecionando apenas dois prontos. Reenvia o envelope e token de prévia; nenhum status aprovado do cliente.
4. `receipt.proposal.json`: resposta 201 com Location para receipt. Replay retorna 200 com o mesmo
   corpo/batch_id, sem inserir novamente. GET após reload expõe este snapshot e pode adicionar
   `current_review` separado; nunca altera o resultado original para parecer que o lote já foi aprovado.
5. `errors.proposal.json`: exemplos de HTTP e corpo seguro, incluindo conflito, bloqueio da proposta e
   resultado de commit desconhecido. Não são evidências de testes de endpoints reais.

Antes de implementar: aprovar proposta, persistência, política de direitos/dedupe comum aos writers,
revisar transação/ACL/auditoria e executar a matriz do documento principal em banco descartável.
