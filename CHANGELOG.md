# Changelog — Sr. Rotas

Este é o changelog contínuo. Arquivos `CHANGELOG-*` antigos permanecem apenas como histórico das RCs anteriores.

## 0.29.0 Field — 21/09/2026

### M1 Reliability
- Gap semântico deixa de reiniciar o ML Kit. `resetOcrPipeline()` permanece reservado a stall/no-progress técnico e recuperação manual.
- `ShadowOfferRecovery027033` deixa de executar segundo `TextRecognizer`; pedidos antigos são suprimidos e contabilizados.
- Mantém um único OCR pesado no M1 durante a jornada.

### Admissão oficial
- Adiciona `OfferAdmissionGate029` antes de estabilização/HUD/persistência.
- Leitura normal recente pode bloquear salto decimal x10 da mesma oferta sem "corrigir" o valor por adivinhação.
- Caudas extremas aguardam segunda observação compatível em janela de 7 s.
- `platform=other` por `other-text-fallback` sem pickup+destino não entra na base oficial.
- Estado de correlação é curto, em memória e não persiste OCR/endereço/coordenadas.

### Diagnóstico
- Adiciona `offer_admission_029`.
- `shadow_recovery_027033` passa a declarar `disabled_in_029=true`, `pass_attempts=0` e `suppressed_submissions`.
- README registra a evidência de campo da 0.28 e desloca o runtime Reader 2.0 para depois da validação da 0.29.

### Reader 2.0
- Integration Map e Contract v1 são preservados como documentação.
- Nenhum Reader 2.0 é ativado nesta versão.
- Próxima etapa planejada: 0.30 fundação compilável com flags OFF; 0.31 shadow compartilhando o mesmo OCR espacial do M1.

### Versionamento
- `versionCode 70`
- `versionName 0.29.0-field`

## 0.28.0 Field — 21/09/2026

### Regressões corrigidas
- Restaura **Usar painel compacto** em Configurações → Janela flutuante.
- A compactação volta a ser aplicada pelo `JourneyBubbleController`, sem reativar polishes/watchers visuais legados.
- `Pesquisar região` volta a iniciar recolhida e abrir/recolher por toque.

### Integridade de leitura
- Adiciona `OfferIntegrityGuard028` entre interpretação e HUD/persistência.
- Rejeita geometrias com velocidade média impossível por trecho e inconsistências de soma/cálculo interno.
- O caso de campo `1,0 km` → `11 km` em 4 min fica protegido sem inventar correção de decimal.
- `OfferDispatcher` aplica o gate antes de preview/HUD, estabilização e persistência.

### Diagnóstico e testes
- `ReaderLabCombinedDiagnostic0270361` passa a exportar `offer_integrity_028`.
- Novos testes cobrem a regressão 11 km/4 min, a leitura correta 1 km/4 min, viagem longa legítima, painel compacto, pesquisa colapsável e presença do gate.

### Reader 2.0
- O módulo externo recebido **não é ativado** nesta build.
- README registra plano 0.29 para Integration Map e 0.30 para shadow/compare somente após a baseline M1 da 0.28 ser validada.

### Versionamento
- Sai a nomenclatura RC3.x para builds funcionais de campo.
- Próximas entregas seguem 0.28 → 0.29 → 0.30 → ... com `versionCode` crescente.

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
