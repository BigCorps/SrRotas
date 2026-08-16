# Rascunho técnico — Data Safety

Este documento é um inventário técnico para ajudar a preencher a Play Console depois. Não é a declaração final.

## Fluxo atual

- Conteúdo de tela: processado temporariamente no aparelho durante MediaProjection.
- Screenshot/frame: descartado depois do OCR e não enviado no fluxo normal.
- Texto OCR bruto: armazenado localmente para diagnóstico; não enviado no fluxo normal.
- Ofertas estruturadas: podem ser enviadas ao backend/Supabase.
- Dados do dispositivo: nome/modelo é enviado no pareamento.
- Token do aparelho: enviado como credencial às APIs; servidor mantém hash na tabela de dispositivos.
- IA: recebe dados estruturados selecionados do histórico, não screenshot.

## Pontos para revisar antes do lançamento

Autenticação pública, retenção definitiva, exclusão, telemetria, crash reporting, pagamentos e qualquer nova integração devem ser adicionados ao inventário se forem implementados.
