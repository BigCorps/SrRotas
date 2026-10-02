# vc87 — correção de compilação

Base: `a8ac387106b7ea4d6c0336db1cc66a14c7985118`

Falha real do Action:
- Web / Next.js: PASS
- Architecture regression guard: PASS
- Android compileDebugKotlin: FAIL
- `AiPanel023.kt:67:423 Syntax error: Expecting ')'`

Causa:
A linha compactada da barra `Período / Inteligência` fechava o `LayoutParams.apply`
mas não fechava corretamente o `LinearLayout.apply`.

Correção:
`AiPanel023.kt` foi reformatado mantendo a mesma lógica do vc87 e fechando
explicitamente todos os blocos da composição.

Nenhuma alteração em Reader, OCR, HUD, MediaProjection, Radar, backend,
Supabase, V7, Pix ou enforcement.
