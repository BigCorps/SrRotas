# Sr. Rotas — Contrato 0.33.0 · Release Prep Pack 1

## Objetivo

Avançar módulos não bloqueados pelo Reader 2 sem perder isolamento arquitetural.
Reader 2 Consensus 0.32.1 continua coletando evidência em paralelo e permanece sem efeito oficial.

## A. Screenshot Storage Guard

- manter o recorte de card já validado;
- no máximo uma captura para ofertas semanticamente equivalentes dentro de 60 s;
- mesma proteção vale para M1 e M2;
- JPEG em qualidade 72, suficiente para auditoria/OCR sem armazenamento excessivo;
- cache privado continua limitado a 30 arquivos;
- cópia visível passa a ter retenção máxima de 180 arquivos;
- diagnóstico exporta `screenshot_storage_033`;
- nenhuma mudança em parser, admissão, Histórico ou backend.

## B. Responsividade

- `SrUi023.maxContentWidthPx` deixa de limitar conteúdo a 760 dp;
- celular/tablet usam praticamente toda a largura útil;
- cap passa a 1440 dp apenas para telas realmente largas;
- margem externa padrão passa a 12 dp;
- a mudança compartilhada pode ampliar visualmente Histórico/Radar/IA/Configurações, sem alterar seus contratos funcionais.

## C. Dark Mode

A infraestrutura atual já usa `Appearance021`/`SrTheme024` e os inputs canônicos aplicam cores de texto/hint/surface específicas para dark mode.
O 0.33 torna esse comportamento parte do guard de regressão nos inputs compartilhados e no pré-jornada. Não reintroduzir campos com cor fixa ilegível.

## D. Jornada compacta

Em Develop Mode o centro do controle de jornada deve mostrar somente o estado curto e o reader ativo:

- `OK ✓ — M1/2.0`;
- `OK ✓ — M2`;
- `OK ✓ — M1/M2/2.0`;
- `⚠ Retomar — ...` quando a captura exigir nova autorização.

Texto explicativo fica oculto em estado normal. O detalhe `Jornada preservada` aparece somente durante recuperação.

## E. Reader 2

- Consensus 0.32.1 continua ligado em shadow;
- `controlled_hybrid_effect=false` continua obrigatório;
- 0.33 não promove Reader 2 e não muda M1 oficial;
- o próximo JSON continua válido para decidir Controlled Hybrid sem bloquear módulos de produto.

## Próximo pacote

`0.34.x — Navigation Pack`: Radar/lugares salvos, Buscar Destino/Combinado e índice oferta→screenshot/preview.
