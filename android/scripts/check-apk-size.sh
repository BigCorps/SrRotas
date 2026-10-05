#!/usr/bin/env bash
set -euo pipefail

APK="app/build/outputs/apk/release/app-release.apk"
[[ -f "$APK" ]] || { echo "::error::APK release não encontrado"; exit 1; }

# Desde vc89 o Radar Contextual usa MapLibre Native.
# O orçamento bruto histórico de +2% não é mais um gate válido: a dependência
# cartográfica nativa é uma decisão deliberada de produto. Mantemos o tamanho
# visível no CI para acompanhar regressões, mas sem reprovar a build por ele.
bytes=$(stat -c%s "$APK")
printf 'Release APK bruto: %d bytes\n' "$bytes"
echo "APK size: informativo desde vc89 (MapLibre Native no Radar Contextual)."

# Assets gráficos próprios continuam sob orçamento separado para evitar
# crescimento acidental de PNGs não relacionado ao mapa.
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
  echo "::error::Assets gráficos próprios ultrapassaram o orçamento de tamanho."
  exit 1
fi

echo "Size guard aprovado (APK bruto informativo; assets próprios protegidos)."
