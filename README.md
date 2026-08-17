# Sr. Rotas 2.0 — Alpha 0.5.4

Sr. Rotas é um copiloto Android para motoristas de aplicativo. O núcleo usa MediaProjection autorizado pelo usuário, OCR local com ML Kit, parser contextual de ofertas e HUD configurável de rentabilidade.

**Desenvolvido pela BigCorps** — contato@bigcorps.com.br

## Alpha 0.5.4

Mantém a velocidade da 0.5.3 e adiciona o Card Stabilizer:

`frame -> OCR -> parser -> HUD imediato`

`                     -> Card Stabilizer (até 750 ms) -> histórico/backend`

Versão: versionCode 9, versionName `0.5.4-alpha`, parser `sr-rotas-v0.5.4`.

Se a rodada de campo vier limpa, o Offer Engine v1 será congelado e a próxima fase será a 0.6 visual: Design System, tamanhos de cards, preferências e identidade própria do HUD.
