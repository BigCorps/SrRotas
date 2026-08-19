# Aplicar — Sr. Rotas 0.17.0-beta

## Ordem obrigatória

1. **Mantenha o motorista testando o APK 0.16 normalmente.**
2. No Supabase SQL Editor, execute primeiro:
   `supabase/migrations/20260819_statistical_intelligence_017.sql`
3. No resultado final confirme:
   - `collective_stats_opt_in` existe;
   - `anon_select = false`;
   - `authenticated_select = false`;
   - `service_role_select = true`.
4. Depois envie todo o conteúdo deste ZIP para a raiz do GitHub.
5. Aguarde Vercel + GitHub Actions.
6. Nenhuma configuração nova do Vercel é esperada.

## Importação histórica

A 0.16 originalmente planejada para importação foi usada para corrigir
performance/launcher. Por isso a importação histórica foi incorporada à 0.17.

Fluxo:
- seleção múltipla de imagens;
- SHA-256 local por arquivo;
- OCR ML Kit local;
- Offer Engine v1 existente;
- Context Engine existente;
- dedupe do arquivo + fingerprint semântico;
- sincronização apenas de dados estruturados válidos;
- screenshot bruto não é enviado;
- raw OCR não é enviado.

A data é classificada em:
- `metadata_taken`;
- `exif`;
- `filename`;
- `last_modified`;
- `unknown`.

`unknown` é somente um placeholder técnico para o campo obrigatório
`observed_at` e não é considerado evidência temporal confiável.

## Probabilidade regional

Usa `zone_exposures` como denominador. Antes do cálculo, ofertas simultâneas
do mesmo Radar são coalescidas em uma única chegada estatística quando formam
um burst de até 15 segundos na mesma jornada/célula.

Horizontes:
- 5 minutos;
- 10 minutos;
- 15 minutos.

Para um horizonte H:

`elegível = exposição >= H OU oferta observada antes de H`

`sucesso = oferta observada antes de H`

Assim, uma exposição curta encerrada por pausa, troca de célula ou início de
corrida é censurada e não vira uma falha falsa.

Percentual só é exibido com pelo menos **20 intervalos elegíveis**.
Até lá a UI mostra `DADOS INSUF.`.

## Continuidade no destino

Novo endpoint autenticado:

`GET /api/v1/intelligence?cell=g2:...&eta=<ISO>&days=60`

Busca pessoal:
1. mesma célula + mesmo dia da semana + mesma faixa de 3h;
2. mesma célula + mesma faixa de 3h;
3. mesma célula em todo o período.

Se a base pessoal for insuficiente, o backend também consegue consultar o
agregado coletivo quando ele existir.

## Base coletiva

Default: **desligada**.

O motorista precisa ativar explicitamente:
`collective_stats_opt_in = true`.

O agregado coletivo:
- não expõe `driver_id`;
- não contém latitude/longitude exata;
- não contém endereço;
- não contém OCR bruto;
- não contém screenshot;
- só aparece com pelo menos **3 contribuidores distintos**;
- só é consultável pelo backend `service_role`.

## Compatibilidade

A migration é aditiva.
O APK 0.16 continua compatível com o banco/backend 0.17.

## Versão Android

- versionCode: 24
- versionName: `0.17.0-beta`
