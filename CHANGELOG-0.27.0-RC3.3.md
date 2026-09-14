# Changelog — 0.27.0-RC3.3

## Android
- Gate de integridade impede HUD/persistência financeira de oferta com perna ausente.
- Shadow recovery de até 4 passes sobre a mesma captura privada em memória.
- Releituras normal, painel esquerdo, painel direito e variante redimensionada.
- `latest-frame-wins` para reduzir backlog OCR velho.
- Watchdog de lacuna semântica sem encerrar jornada.
- Falhas automáticas rate-limited no diagnóstico.
- Export de reports ampliado: sem limite silencioso de 20; retenção local de até 80 manuais + 120 automáticos (200 no total).
- Diagnóstico v7 com métricas de shadow recovery.
- Agora com status compacto.
- Configurações protegidas contra decoração repetida em global-layout.
- Jornadas abre em 90 dias e mostra até 50 registros na seção.
- Regiões genéricas de UI filtradas no cliente.

## Admin / backend
- Suporte a `srrotas-historical-offer-v1`.
- Uma linha = uma oferta; várias ofertas podem compartilhar a mesma imagem.
- `record_id`, `offer_index`, versão do schema/extrator e manifesto.
- Qualidade recalculada pelo servidor: temporal, rota, financeiro e completo.
- Histórico parcial preserva utilidade temporal sem contaminar financeiro.
- Deduplicação V7.1 por identidade forte; hash da imagem sozinho não elimina outra oferta do mesmo print.
- `supersedes_batch_id` permite comparação com lote antigo sem apagá-lo.
- Arquivar/restaurar substitui exclusão destrutiva de lotes concluídos.
- Painel mostra taxas de qualidade e principais flags.

## Supabase
- Novas colunas de contrato/qualidade em batches e rows.
- Resumo de qualidade por lote.
- Filtro regional mais rígido.
- Seed regional separa elegibilidade temporal de elegibilidade financeira.
- Legacy permanece utilizável até a troca completa, mas financeiro legacy passa a exigir as duas pernas.
- Migration não apaga históricos e não executa refresh automático.

## Fora de escopo / preservado
- `OfferParser.kt` não alterado.
- `UberSpatialParser0221.kt` não alterado.
- `OfferDeduplicator.kt` não alterado.
- fórmulas e thresholds financeiros não alterados.
- sampling interval e resolução OCR principal não alterados.
- sem automação de aceitar/rejeitar/tocar em oferta.
