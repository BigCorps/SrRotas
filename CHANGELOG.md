# Changelog — Sr. Rotas

Este é o changelog contínuo. Arquivos `CHANGELOG-*` antigos permanecem apenas como histórico das RCs anteriores.

## 0.27.0 RC3.7 Consolidation Field — 19/09/2026

### Arquitetura
- `MainActivity` passa a usar `ConsolidatedMainActivity027037` como shell único.
- Remove do runtime a cadeia de polishes visuais acumulados entre 0.26.2 e RC3.6.1.
- Implementações visuais antigas são reduzidas a stubs de compatibilidade sem watchers/tickers.
- `README.md` passa a ser a especificação canônica do estado atual.
- CI ganha Architecture Regression Guard e orçamento de tamanho.

### Agora
- Uma única pesquisa de região.
- Remove “Visualizar Radar”.
- Remove contador “N regiões em destaque”.
- Controle de jornada tem uma única árvore visual estável.
- Pré-jornada unificado para odômetro e abastecimento/recarga.
- Agora usa rosa neon distinto de IA.

### Navegação
- Configurações e Usuário integrados diretamente ao cabeçalho.
- Barra inferior permanece funcional ao sair de Configurações.
- Rotas fixas: Estatísticas · IA · Agora · Histórico · Radar.

### Histórico
- Atualização direta quando oferta oficial é persistida e a rota está visível.
- “Fiz essa corrida” pode ser desfeito.

### M1 / M2
- M1 passa a ser o modo padrão seguro.
- Modo Comparativo: M1 oficial + M2 apenas pela árvore de Accessibility, sem segundo ML Kit concorrente.
- M2 isolado: MediaProjection não inicia; M2 usa Accessibility e pode usar OCR local sem persistir na base oficial.
- Troca de leitor é bloqueada durante jornada.
- Supervisor do M1 não executa em M2 isolado.

### Diagnóstico
- Configurações, quick action e Activity de exportação usam `ReaderLabCombinedDiagnostic0270361`.
- Evita novo JSON sem `reader_lab_0270361` / `m2_health`.

### Preservado
- Sem alteração deliberada em `OfferParser`, `OfferDeduplicator`, fórmulas financeiras ou contrato oficial da base do M1.
