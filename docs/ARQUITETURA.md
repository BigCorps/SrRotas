# Arquitetura técnica

## Android

### DriverAccessibilityService

Escuta apenas `com.ubercab.driver`, por configuração XML e checagem adicional no código.

Fluxo:

1. `AccessibilityEvent` informa alteração da janela/conteúdo.
2. Tenta ler `rootInActiveWindow`.
3. Percorre `AccessibilityNodeInfo` e concatena `text` + `contentDescription`.
4. Executa `OfferParser`.
5. Se não houver oferta parseável e OCR estiver habilitado, limita screenshot a no máximo uma tentativa a cada ~1,6 s.
6. Screenshot é convertido em `Bitmap` e processado pelo ML Kit no aparelho.
7. O bitmap é reciclado após OCR.
8. Se o parser reconhecer uma oferta, calcula métricas, mostra overlay e envia JSON ao backend.

### Overlay

Usa `TYPE_ACCESSIBILITY_OVERLAY` com flags `FLAG_NOT_FOCUSABLE` e `FLAG_NOT_TOUCHABLE`. Portanto, o overlay não recebe toque nem executa ações sobre o Uber.

### Dedupe

Cada oferta recebe SHA-256 de valor + km + tempo + trecho do texto. O mesmo hash é ignorado por 30 segundos no app e também possui unicidade `(device_id, dedupe_key)` no banco.

## Backend

### Pareamento

`POST /api/v1/pair`

O código mestre está em `PAIRING_CODE`. Ao parear, o backend cria um token aleatório de 256 bits. Só o SHA-256 do token fica no banco.

### Ingestão

`POST /api/v1/offers`

Autenticação: `Authorization: Bearer <device_token>`.

O backend nunca recebe screenshot no MVP; recebe apenas campos estruturados e `raw_text` reconhecido.

### IA

`POST /api/v1/ask` consulta até 250 ofertas do período, reduz os campos a métricas compactas e usa OpenAI Responses API. A chamada usa `store: false`.

### MCP

`/mcp` usa o SDK oficial `@modelcontextprotocol/server` e `createMcpHandler`, seguindo o mesmo padrão técnico do MonitorIA atual.

O MVP usa `MCP_API_TOKEN`; antes de produto público, migrar para OAuth.
