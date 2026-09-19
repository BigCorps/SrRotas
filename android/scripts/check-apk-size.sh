#!/usr/bin/env bash
set -euo pipefail
APK="app/build/outputs/apk/release/app-release.apk"
[[ -f "$APK" ]] || { echo "::error::APK release não encontrado"; exit 1; }
bytes=$(stat -c%s "$APK")
max=40500000
printf 'Release APK: %d bytes (budget: %d)\n' "$bytes" "$max"
if (( bytes > max )); then
  echo "::error::APK ultrapassou o orçamento de tamanho. Revise assets/dependências antes de publicar artefato."
  exit 1
fi

# Os seis PNGs que causaram o salto de ~28,5% ficam com teto explícito.
assets=(
  app/src/main/res/drawable-nodpi/ic_launcher_foreground.png
  app/src/main/res/drawable-nodpi/sr0265_ai_mascot.png
  app/src/main/res/drawable-nodpi/sr0265_header_dark.png
  app/src/main/res/drawable-nodpi/sr0265_header_light.png
  app/src/main/res/drawable-nodpi/sr0265_settings_ready.png
  app/src/main/res/drawable-nodpi/srrotas_bubble_icon.png
)
total=0
for f in "${assets[@]}"; do
  [[ -f "$f" ]] || continue
  total=$((total + $(stat -c%s "$f")))
done
asset_max=12000000
printf 'Tracked nodpi assets: %d bytes (budget: %d)\n' "$total" "$asset_max"
if (( total > asset_max )); then
  echo "::error::Assets gráficos ultrapassaram o orçamento de tamanho."
  exit 1
fi
