# Arquitetura — Sr. Rotas 2.0 Alpha

## Caminho crítico local

```text
Uber Driver
  ↓ tela autorizada pelo usuário
MediaProjection Foreground Service
  ↓ VirtualDisplay / ImageReader (~1 frame/s)
ML Kit Text Recognition (offline)
  ↓ linhas + bounding boxes
SpatialOfferParser
  ↓
OfferParser + metas do motorista
  ↓
HUD WindowManager TYPE_APPLICATION_OVERLAY
```

A decisão visual não depende do backend nem de IA.

## Segundo motor

O `DriverAccessibilityService` é auxiliar. Ele tenta ler a árvore de acessibilidade do pacote `com.ubercab.driver`; se MediaProjection estiver desligado, pode testar screenshot + OCR local.

## Backend

Somente dados estruturados são sincronizados por padrão:

- valor;
- km;
- tempo;
- R$/km;
- R$/hora;
- custo/lucro estimado;
- classificação;
- confiança do parser;
- oferta exclusiva ou Radar.

O texto OCR bruto não é enviado por padrão.

## IA e MCP

O backend mantém Pesquisa IA e MCP read-only. As análises tratam os registros como **ofertas observadas**, não como corridas comprovadamente aceitas/concluídas.

## TWA

`srrotas.com` recebe PWA/TWA para o site/painel. O app do motorista continua Android nativo porque TWA não fornece o motor de MediaProjection/Overlay necessário ao produto.
