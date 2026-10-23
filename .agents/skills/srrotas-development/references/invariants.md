# Invariantes permanentes

Resumo de `README-CONTINUIDADE.md`, que continua sendo a fonte canônica. Não substitui sua leitura integral; releia-o quando o estado mudar ou uma regra precisar de contexto. Não congela versões, flags operacionais ou homologações neste arquivo.

- M1/MediaProjection é o leitor oficial; Reader 2 é shadow/observacional; Controlled Hybrid OFF. Não promover Reader2-only para oferta oficial nem criar segundo OCR pesado concorrente.
- `ConsolidatedMainActivity027037` é o shell; `JourneyBubbleController` é o host do HUD. Não criar outro overlay/WindowManager concorrente.
- Não alterar Reader/OCR/parser/router/dedupe/admission/fórmulas sem evidência e escopo explícito. Não bypassar gates oficiais. Recovery M1 usa apenas OCRs existentes; ShadowOfferRecovery permanece sem segundo OCR.
- Radar flags iniciam false; preservar rollback `RadarPanel027035` enquanto não homologado. Contexto do destino vem de `currentRide.localOfferId → LocalStore → RideOffer.context`; não presumir latestOffer. Não abrir mapa automaticamente. Contexto externo não fabrica probabilidade de continuidade.
- V7 é histórico canônico observado: não reprocessar. Não inventar valores ausentes. `ride_offers` são observações, nunca prova automática de corrida realizada/receita; métricas fortes excluem legado `uber_history_ocr`. Conciliação exige decisão humana, sem auto-confirmação.
- Não persistir OCR bruto, screenshots, GPS arbitrário, tokens ou Android ID bruto. Não expor endereços/coordenadas/dados sensíveis no diagnóstico; pseudonimização não significa anonimização. Device identity server-side usa HMAC, nunca raw ID.
- MCP permanece read-only, salvo decisão explícita posterior; não executar ações de corridas/mobilidade.
- Copiloto permanece operacional para contas não bloqueadas, inclusive antes do trial e após expiração. Inteligência depende do contrato vigente; não promover enforcement sem homologação autorizada de novo usuário.
- Não reaplicar migrations antigas, fabricar policies de anon/auth para silenciar advisors, reprocessar V7 ou apagar backlog em massa. Funções server-only não devem expor EXECUTE público.
- Atualize o README canônico no mesmo PR se versão, arquitetura, gate, flag, contrato, schema/semântica ou homologação mudar. Histórico em CHANGELOG; contratos permanentes em `docs/contracts/`. Sem nova documentação operacional paralela na raiz.
- IMPLEMENTADO ≠ HOMOLOGADO; CI VERDE ≠ TESTE DE CAMPO; HEAD ≠ BUILD EM CAMPO.
