#!/usr/bin/env bash
set -euo pipefail

APK="app/build/outputs/apk/release/app-release.apk"
[[ -f "$APK" ]] || { echo "::error::APK release não encontrado"; exit 1; }

# Baseline bruto confirmado a partir do APK real do Action #101 / RC3.6.1.
# Não usar o size_in_bytes do artifact do GitHub: esse valor é o ZIP comprimido.
baseline=62821516

# Política canônica: uma build pode crescer no máximo 2% sobre a baseline
# sem revisão explícita do orçamento.
max=$((baseline * 102 / 100))
bytes=$(stat -c%s "$APK")
delta=$((bytes - baseline))

printf 'Release APK bruto: %d bytes\n' "$bytes"
printf 'Baseline RC3.6.1 bruta: %d bytes\n' "$baseline"
printf 'Delta vs baseline: %+d bytes\n' "$delta"
printf 'Budget (+2%%): %d bytes\n' "$max"

if (( bytes > max )); then
  echo "::error::APK ultrapassou +2% da baseline bruta. Revise assets/dependências antes de publicar artefato."
  exit 1
fi

# Os seis PNGs que causaram o salto histórico de ~28,5% ficam com teto explícito.
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

echo "Size guard aprovado."
