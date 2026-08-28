# Sr. Rotas 0.23.3-beta — Navegação, largura e Sua IA

Base: commit a8b9db2e2af943856d436c406240d76ece5a1ede (0.23.2-beta).

## Corrige
- Botão Agora passa a ser um botão central real: 58dp, circular, elevado, sempre visível e com ícone branco próprio.
- Não depende mais de tint sobre o VectorDrawable antigo para mostrar o ícone.
- Cards de sugestões da IA passam a ocupar 100% da mesma largura de Resposta e do campo de pergunta.
- Cards de Configurações passam a usar linhas com pesos iguais e largura total, sem GridLayout de largura ambígua.
- MCP deixa de ser apresentado como "Segurança MCP".
- A área passa a se chamar "Sua IA" / "Sua IA (MCP)" e explica claramente as duas opções:
  1. IA do Sr. Rotas, integrada ao APK e com créditos.
  2. IA do próprio usuário via MCP, para consultar suas corridas e métricas.
- A página MCP mantém chaves e revogação como mecanismos técnicos, mas o objetivo da seção é usar a própria IA.

## Versão
versionCode 39
versionName 0.23.3-beta

Não há SQL novo.
Não altera OCR, parser, Uber/99, MediaProjection, cálculos, jornada, dedupe ou banco.
