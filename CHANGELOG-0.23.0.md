# Changelog — Sr. Rotas 0.23.0-beta

## UI Freeze

- Consolida o visual final proposto nos mockups em Kotlin/Android Views.
- Preserva os temas Claro, Escuro e Automático.
- Adiciona navegação final de cinco destinos.
- Redesenha Agora, IA e Configurações.
- Move Conta/Usuário/Plano para experiência Web autenticada.
- Redesenha os três modos do HUD sem modificar a lógica financeira.
- Integra nova janela flutuante com barra por ícones e mensagens rápidas.
- Adiciona configuração Web e sincronização de mensagens rápidas.
- Remove truncamento artificial dos endereços na janela flutuante.

## Banco

- Nova tabela `driver_message_presets` via migration `20260828_driver_message_presets_023.sql`.

## Compatibilidade

- `applicationId`: `com.srrotas.app` inalterado.
- `versionCode`: 36.
- `versionName`: 0.23.0-beta.
- Sem Compose e sem mudança de linguagem/framework.
- Sem alterações no launcher icon.
- Sem alterações intencionais no parser/OCR/MediaProjection/dedupe.
