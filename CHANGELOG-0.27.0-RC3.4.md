# Changelog — Sr. Rotas 0.27.0-RC3.4

## Fechamento Uber / UX

### Status / identidade
- Corrige a regressão do mascote no card “Sr. Rotas está pronto”.
- O reparo passa a ser reaplicado após reconstruções de `SettingsHub`, evitando retorno da arte antiga.

### Janela Flutuante
- Cria tela independente `FloatingWindowSettingsActivity027034`.
- A seção antiga da Janela Flutuante deixa de aparecer dentro de Configuração do HUD.
- Separa controles em:
  - Tamanho do botão;
  - Opacidade do botão;
  - Opacidade da janela.
- Preserva `bubble_opacity` como configuração histórica do botão.
- Adiciona `bubble_window_opacity_027034` para o painel expandido.
- Remove da experiência o botão de restaurar posição; permanece o clamp automático existente em criação, atualização, arraste e mudanças de viewport.

### HUD
- A prévia deixa de manter uma segunda implementação visual.
- `HudConfigPreview024` passa a renderizar a amostra através de `Hud023Renderer.build(...)`.
- O card de Configuração do HUD passa a ser descrito como métricas, limites e prévia.

### Plataformas
- Uber: suportado no lançamento inicial.
- 99: em implementação contínua e não bloqueadora do lançamento.

### Estratégia de produto
- Prioridade P0 consolidada: catalogação correta dos dados para inteligência.
- Uma oferta financeiramente completa não é automaticamente considerada pronta para inteligência de rota.

### Preservado
- reader/OCR/parsers congelados;
- fórmulas financeiras;
- thresholds;
- sampling/resolução OCR;
- regra “O Sr. Rotas calcula. O motorista decide.”
