# Roteiro do primeiro teste com Uber Driver

## Objetivo

Validar se a oferta exibida no Uber Driver pode ser transformada em texto e métricas sem qualquer automação de aceite/recusa.

## Antes de ficar online

- App instalado via APK debug.
- Consentimento marcado.
- AccessibilityService ativo.
- OCR local ligado.
- Regras de R$/km e R$/hora salvas.
- Backend pareado (opcional para validar apenas a captura; recomendado para validar ponta a ponta).

## Ao aparecer a primeira oferta

Não precisa tocar em nada no app de análise. Observe somente:

1. apareceu um overlay no topo?
2. o valor da oferta está correto?
3. R$/km parece plausível?
4. R$/hora parece plausível?
5. o overlay desaparece sozinho?
6. o Uber continua respondendo normalmente?

Depois da oferta, abra o Driver AI MVP e copie:

- `Última captura`;
- `Método` (`accessibility-tree` ou `screenshot-ocr`);
- `Texto bruto capturado`.

Esse texto é o artefato mais importante do primeiro teste.

## Critério de sucesso V0

Sucesso mínimo:

- detectar a oferta sem tocar na UI do Uber;
- extrair o valor corretamente;
- extrair pelo menos uma distância ou duração;
- mostrar o overlay;
- não travar/interferir no Uber;
- guardar o texto bruto para calibração.

Não é obrigatório na V0 acertar a divisão entre distância de embarque e distância da viagem. Essa calibração depende do texto real mostrado na versão brasileira instalada no aparelho.
