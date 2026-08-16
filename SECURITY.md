# Segurança — Sr. Rotas

## Segredos

Nunca faça commit de `SUPABASE_SERVICE_ROLE_KEY`, `OPENAI_API_KEY`, `MCP_API_TOKEN`, código de pareamento, keystore ou senhas de assinatura.

O APK recebe somente um `device_token` gerado no pareamento. No banco, o token é mantido como hash.

## Princípios do Alpha

- OCR principal local no Android.
- Screenshot não é persistido nem enviado pelo fluxo normal.
- Texto OCR bruto permanece local por padrão.
- MCP somente leitura.
- Backend acessa Supabase com `service_role`; tabelas permanecem com RLS habilitado e sem policy pública nesta fase.
- Ofertas observadas nunca devem ser promovidas automaticamente a corridas aceitas ou concluídas.

## Diagnóstico

O botão de compartilhamento cria texto explicitamente escolhido pelo usuário e exclui token do aparelho, código de pareamento e screenshot.

## Publicação

A fingerprint da TWA só deve ser publicada após existir uma chave real de assinatura. O endpoint `.well-known/assetlinks.json` responde 404 até que as variáveis correspondentes sejam configuradas.
