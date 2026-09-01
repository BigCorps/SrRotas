# Diagnóstico da validação de 01/09/2026

## Ícone
O APK 0.24.1 foi inspecionado. O foreground compilado tinha 432×432 em RGB (sem alpha), com preto preenchendo todo o entorno da arte. Por isso a borda preta sobrevivia a alterações no XML do adaptive icon. A 0.24.2 mantém a escala visual e troca apenas o entorno por transparência, além de usar fundo adaptável azul.

## Endereços
A consulta somente leitura aos registros recentes confirmou geocodes absurdamente distantes gerados a partir de OCR parcial (por exemplo abreviações curtas resolvidas para células fora da região da outra ponta). A barreira 0.24.2 não altera o OCR financeiro: ela filtra somente contexto textual/geográfico e deixa de gerar célula quando a confirmação é fraca/incompatível.

## Base Coletiva
A Base Coletiva continua usando células/estatísticas agregadas; endereço textual e OCR bruto não são enviados como dado coletivo. A correção de qualidade protege principalmente as células futuras contra contaminação por geocode ruim.
