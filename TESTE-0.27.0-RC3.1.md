# Teste de campo — Sr. Rotas 0.27.0 RC3.1

## Objetivo
Validar se a 99 deixa de perder cards quando o OCR falha temporariamente em ler os textos de identidade do app, sem piorar a Uber.

## Configuração principal
- usar **Tela inteira** no seletor de captura do Android;
- Uber + 99 em tela dividida;
- manter a jornada ativa normalmente.

## Validar
1. Ofertas 99 aparecem no HUD com mais constância.
2. Uber mantém o comportamento da RC3.
3. Alternância entre Uber e 99 não mistura cards.
4. Não aparecem cards 99 quando só há uma oferta Uber no painel.
5. Rotação/troca de layout não reaproveita incorretamente o painel antigo.

## Em uma falha real
1. NÃO encerrar a jornada.
2. Tocar no botão 🐞.
3. Tocar primeiro em **Reportar falha**.
4. Se possível, deixar o card visível por 10–15 s.
5. Depois usar **Exportar diagnóstico** e enviar o `.json`.
6. Informar: Uber/99, tipo da oferta, se o HUD não apareceu ou apareceu errado e, se possível, anexar print separado.

O passo **Reportar falha antes de exportar** é importante: o diagnóstico enviado da RC3 tinha `manual_failure_marks = 0`, portanto não havia uma janela temporal exata da oferta perdida.
