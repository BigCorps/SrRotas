# Caixa privada de JSON para triagem — v1

Issue #25. Branch `feature/admin-radar-json-inbox`, derivada do PR #22 em `8d8cbbeefa116448609ad60834317e67eb8c82ff`.

## Invariantes e operação

Selecionar arquivo não envia nada. **Enviar JSON para triagem** persiste bytes originais independentemente do schema; **Validar prévia (contrato 0.2)** é ação opcional separada. Formatos antigos sintaticamente válidos são aceitos. Recebimento nunca chama ingestão, revisão, aprovação ou writers de eventos, POIs, runs ou coletores. URLs no JSON não são acessadas. Conteúdo não é executado/renderizado. Confirmação definitiva permanece desabilitada e `/manual-import/commit` inexistente (404).

Recibo real usa `upload_id`, nunca batch_id/run_id, com nome, SHA-256, bytes, data, status **Recebido — aguardando triagem** e data de revisão de retenção. Envios lista arquivos persistentes com download autenticado, filtro por upload_id e paginação de20. Painéis de lotes/runs existentes permanecem separados.

## HTTP e validação

Exige sessão Admin Web real `sr_import_admin` e autorização atual. Cookie de motorista e Bearer de coletor não autorizam a caixa. Cookie HttpOnly/Secure/SameSite Strict; POST exige Origin do próprio Host, sem confiar em X-Forwarded-Host. Sem CORS aberto; respostas privadas no-store.

| Método/rota | Entrada | Resposta |
| --- | --- | --- |
| POST `/api/v1/admin/radar/json-inbox` | body bruto application/json; Idempotency-Key UUID; X-File-Name percent-encoded | 200 `{receipt}`, inclusive replay exato |
| GET mesma rota | cursor UUID e upload_id UUID opcionais | `{uploads, next_cursor}` |
| GET `/api/v1/admin/radar/json-inbox/{upload_id}/file` | UUID | attachment original verificado, CSP sandbox, nosniff, same-origin |

UploadReceipt: upload_id, original_name, size_bytes, sha256, received_at ISO8601, retention_review_at ISO8601, status quarantined ou purged. Sem identidade/email/chave/payload nas listas/recibos. Administradores autorizados compartilham a caixa; não há URL pública/assinada.

UTF-8 estrito, BOM inicial preservado, JSON válido sem chaves duplicadas (inclusive escapadas), profundidade máxima12 e até **2.000.000 bytes**. Não exige schema de negócio. Nome .json normalizado, sem diretórios/controles/bidi, máximo120 caracteres. Encoding comprimido rejeitado; limite durante streaming e conferência de Content-Length quando presente. Bytes originais opacos, sem conversão.

Erros sem SQL/payload:400 inválido,401 sessão,403 autorização/origem,409 conflito de chave ou integridade,410 purgado,413 tamanho,415 tipo/encoding,429 quota,503 indisponibilidade. UI orienta conferir Envios antes de repetir falha ambígua. Mesmo arquivo selecionado conserva chave após falha de rede; nova seleção cria nova solicitação. Não há dedupe global por hash.

## Persistência, autorização e quotas

Migração `supabase/migrations/20261115122811_radar_json_inbox_v1.sql`. PostgreSQL>=16 necessário para IS JSON WITH UNIQUE KEYS; produção consultada17.6.1.155, ensaio descartável17.7/PostgREST13.0.7.

- Recibos public.sr_radar_json_uploads; bytes private.sr_radar_json_upload_bytes; trilha public.sr_radar_json_upload_audit. Sem Storage público.
- RLS nas três tabelas sem policies de cliente; ACL revogada inclusive de service_role e sequência. Acesso somente pelas três RPCs restritas a service_role, SECURITY DEFINER/search_path vazio. Helper privado sem EXECUTE para clientes/service_role.
- Cada RPC verifica sessão viva pelo SHA-256 do cookie, identidade, allowlist e acesso habilitado, com locks que coordenam revogação. Não persiste token bruto nesta feature.
- Lock transacional global curto serializa quotas e idempotência por (received_by,idempotency_key). Recibo/bytes/auditoria atômicos; erro de auditoria aborta tudo. Download auditado verifica SHA/tamanho em SQL e Node.
- Novos arquivos:20/admin/dia UTC,100globais/dia UTC,1.000.000.000 bytes totais. Replay exato não consome quota de novos arquivos. Rate limit HTTP separado: upload20/15min, list120/15min, download30/15min; fail closed. Não logar payload/cookie/parâmetros SQL. Auditoria registra só identidade interna, ação, upload_id/data.

## Retenção e rollback

Aos30dias, retention_review_at sinaliza revisão manual de necessidade; **não há exclusão automática**. Sem cron/endpoint destrutivo. DBA só purga bytes com autorização específica: transação, lock do recibo FOR UPDATE, verificar data vencida, apagar só a linha privada e inserir auditoria purged. Preservar recibo/SHA/auditoria por pelo menos90dias, depois revisar obrigações legais. Estado purged deriva da ausência dos bytes; download/replay retorna410. Nunca apagar entidades Radar. Revisar capacidade antes de1GB; saturação rejeita novos uploads sem descarte silencioso. Backup inclui bytes privados, sujeito à política operacional do Supabase.

Rollback: reverter deployment web e revogar EXECUTE de service_role nas três RPCs públicas. Preservar tabelas/bytes/recibos/auditoria para recuperação; não usar DROP nem desfazer dados.

## Gates de publicação

1. Revisar migração/auth/privacidade; CI backend e PostgreSQL descartável/browser desktop/mobile/fonte150%. Revisão cruzada sem escritores concorrentes. Handoff Claude antes de eventual merge conforme skill; merge não é necessário para deployment controlado do SHA web.
2. Registrar SHA, baseline e snapshot integral das entidades Radar, considerando ingestões produtivas concorrentes. Garantir conectividade e sessão normal Admin para smoke **antes** do rollout; não extrair credenciais do banco nem fabricar sessão.
3. Aplicar apenas esta migração revisada; verificar RLS/ACL/RPCs e reload PostgREST. Não ativar commit de importação nem migrations Android/coletor.
4. Publicar somente backend web do SHA para Vercel sr-rotas, root backend; conferir READY/SHA/alias srrotas.com. Deployment anterior consultado: dpl_D5YzVe4ztMgJGUG5YHCiwa3U5H2Z; atualizar referência se produção avançar.
5. Em Admin autenticado enviar SINTETICO-homologacao.json com `{"homologacao":"json-inbox","items":[]}`. Registrar recibo/SHA; replay com mesma chave deve preservar recibo único. Abrir Envios, recarregar, baixar e comparar bytes/SHA. Conferir snapshot sem alterações atribuíveis à solicitação; confirmar botão bloqueado/commit404 e negativa de acesso sem sessão. Só então anunciar disponível.
6. Se gate/smoke falhar, conter via rollback acima. Não anunciar publicação parcial como recebimento operacional.

## Evidência atual e pendências

15/11/2026: local Node22, Radar61/61, Estatísticas72/72, PostgreSQL/HTTP/Chromium35/35 sem skips, TypeScript e build Next aprovados. Cobertos ACL/todos os papéis, sessão/origem/revogação, concorrência/quota/idempotência, rollback de auditoria, corrupção/purge, bytes legados exatos e snapshots Radar intactos. Desktop1365/mobile390, fontes100%/150%, hit-testing e screenshots sintéticos em artifact privado do CI admin-radar-isolated-ui.

**Não é homologação produtiva.** Proxy do ambiente bloqueou srrotas.com com CONNECT403; não há desktop autenticado acessível. Conectores Vercel/Supabase não substituem o envio autenticado no Admin. Migração/publicação produtivas pendentes até permitir smoke e rollback verificáveis. Build/preview READY não comprova recebimento em produção.
