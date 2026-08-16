# Sr. Rotas 2.0 Alpha

Assistente Android não-intrusivo para motoristas de aplicativo, com análise local das ofertas e camada de histórico/IA/MCP no backend.

## Arquitetura do Alpha

```text
Uber Driver
    ↓
MediaProjection + VirtualDisplay + ImageReader
    ↓
Google ML Kit OCR local
    ↓
SpatialOfferParser (Oferta exclusiva / Radar)
    ↓
Regras do motorista + semáforo
    ↓
HUD WindowManager
    ↓
Dados estruturados → backend/Supabase
    ↓
Pesquisa IA + MCP read-only
```

A Acessibilidade permanece como segundo motor de leitura/diagnóstico, não como requisito do fluxo principal.

## Privacidade

- screenshots são processados em memória e descartados;
- OCR é local;
- texto OCR bruto fica no aparelho para diagnóstico;
- o backend recebe os campos estruturados por padrão;
- o app não aceita nem rejeita corridas automaticamente.

## Pastas

- `android/` — aplicativo Android nativo;
- `backend/` — Next.js + APIs + Pesquisa IA + MCP;
- `supabase/` — migrations;
- `twa/` — arquivos auxiliares para a interface web/TWA de `srrotas.com`;
- `docs/` — roteiro de teste e arquitetura.

Leia `APLICAR-PATCH.md` antes de substituir os arquivos do primeiro ZIP.
