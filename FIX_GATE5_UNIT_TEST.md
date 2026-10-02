# Gate 5 — correção do teste contratual de release

Base: `01e4b629dbfc5a63e183a5de22b2733298f7930f`

Falha real do Action:
- Architecture regression guard: PASS
- 346 unit tests executados
- 345 PASS
- 1 FAIL:
  `Release033ContractTest > roadmapTracksCurrentFieldAndHeadSemantically`

Causa:
O teste ainda exigia literalmente `HEAD 0.33.8`, embora o HEAD atual seja
`0.33.9-field / versionCode 86`.

Correção:
O teste agora lê `versionName` e `versionCode` diretamente de
`app/build.gradle.kts` e verifica se Roadmap/README documentam a versão atual.
Assim vc87/vc88 não exigirão novo hardcode do teste.

Não altera código de produção, Android runtime, Reader, HUD, backend ou banco.
