# Exemplos da proposta — não executar

Somente JSON ilustrativo do draft `srrotas.radar-admin-import.draft.v0.2`. Não é o contrato v1.3,
não há compatibilidade v1/v2. A prévia de leitura existe; commit/recibos HTTP permanecem inativos.
Nomes, posições e direitos são sintéticos; não representam autorização nem geodados de produção.

1. `payload.proposal.json`: envelope misto de cinco itens. Os dois primeiros só seriam prontos
   em fixture com namespace e documentos previamente aprovados; não são prontos por preencher strings.
   O terceiro pressupõe catálogo sintético com identidade exata já existente; o quarto tem estimativa;
   o quinto tem coordenada inválida. A classificação depende do servidor/política/catálogo, não do arquivo.
2. `preview-response.proposal.json`: exemplo de resposta 200 sem qualquer write. Hashes zero são placeholders ilustrativos, não um resultado HTTP real. commit_enabled=false; a resposta HTTP não fornece token de commit.
3. `commit-request.proposal.json`: chamada-alvo ainda INATIVA com `Idempotency-Key: 00000000-0000-4000-8000-000000000099`,
   selecionando apenas dois prontos. Reenvia o envelope e token ilustrativo do experimento isolado (não emitido pela rota de preview publicada); nenhum status aprovado do cliente.
4. `receipt.proposal.json`: resposta 201 com Location para receipt. Replay retorna 200 com o mesmo
   corpo/batch_id, sem inserir novamente. GET após reload expõe este snapshot e pode adicionar
   `current_review` separado; nunca altera o resultado original para parecer que o lote já foi aprovado.
5. `errors.proposal.json`: exemplos de HTTP e corpo seguro, incluindo conflito, bloqueio da proposta e
   resultado de commit desconhecido. Não são evidências de testes de endpoints reais.

Antes de ativar: aprovar revisão 0.2, persistência, política de direitos/dedupe comum aos writers,
revisar transação/ACL/auditoria e executar a matriz do documento principal em banco descartável.

As respostas ilustram a API alvo completa. No protótipo atual, a prévia publicada tem commit_enabled=false, não emite token de commit e não possui registro de fontes aprovado; o experimento AEAD/recibo existe somente no runner PostgreSQL descartável. Links de recibo no Admin usam `?tab=runs&batch_id=UUID`.
