# Aplicar — Sr. Rotas 0.19.0-beta

Base:
`40593bb8ab13e55615dae15d80fd1d6d550d6794`

## Objetivo

A 0.19 não cria uma nova regra financeira.
Ela é a versão consolidada de validação do núcleo 0.14 → 0.18.

Versão Android:

- `versionCode 26`
- `versionName 0.19.0-beta`

## SQL

**Nenhum SQL novo.**

Não execute novamente as migrations 0.14–0.18.

## Aplicação

1. Extraia este ZIP localmente.
2. Envie todo o conteúdo para a raiz do repositório.
3. Preserve os diretórios.
4. Aguarde:
   - Vercel;
   - GitHub Actions;
   - testes unitários;
   - build debug;
   - build release.
5. Só distribua o APK após o Actions ficar verde.
6. Para distribuir a 0.19, prefira configurar os quatro Secrets de assinatura antes do build. Se o primeiro Actions gerar somente `UNSTABLE-SIGNING`, configure os Secrets e use `Run workflow` para gerar novamente sem alterar código.

## APK para o teste de campo

O workflow 0.19 diferencia duas situações.

### Assinatura estável configurada

Artifact:

`sr-rotas-field-release-apk`

Este é o APK que deve ser mantido como linha de atualização dos testes de campo.

O artifact inclui também:

`field-signing-certificate.txt`

Guarde a fingerprint pública do certificado como referência.

### Secrets ausentes/incompletos

Artifact:

`sr-rotas-field-release-UNSTABLE-SIGNING`

Esse build prova que o código compila, mas **não deve ser tratado como APK de campo permanente**.

O fallback usa a assinatura debug criada no runner e ela pode mudar no próximo Actions.

## Antes de trocar o APK que já está no aparelho

No APK antigo:

1. ficar online;
2. abrir `Perfil`;
3. tocar `Sincronizar agora`;
4. aguardar as pendências caírem;
5. se houver algo estranho, compartilhar o diagnóstico antes de remover o app.

Se o Android aceitar instalar a 0.19 diretamente por cima:
- ótimo;
- os dados locais são preservados.

Se aparecer erro de assinatura/conflito:
- não é um bug funcional da 0.19;
- a versão antiga provavelmente foi assinada pela chave debug efêmera de outro runner;
- será necessária uma última reinstalação limpa para migrar para a assinatura estável;
- os dados já sincronizados no Supabase permanecem.

Depois da primeira instalação com a chave estável, as próximas versões devem usar a mesma assinatura.

## Onde fica a validação 0.19

No app:

`Perfil → Central do testador → Abrir validação de campo 0.19`

A Central continua opcional.

O motorista pode simplesmente relatar problemas por mensagem.

## O que a tela prova automaticamente

- quantidade de ofertas locais;
- existência de contexto;
- célula de destino;
- geocoding;
- eventos de jornada;
- outcomes;
- exposições fechadas;
- importações;
- duplicatas evitadas;
- guardrail estatístico;
- perfil de custos;
- snapshots de custo;
- filas de sync;
- permissões;
- crash pendente.

## O que continua sendo manual

- precisão numérica da oferta real;
- Radar sem mistura entre cards;
- Maps abrindo o local correto;
- ETA visualmente coerente;
- HUD sem incomodar/perder chamadas;
- pausa/retomada em uso real;
- fluxo Estou fazendo/Realizada;
- importação prática do mesmo arquivo;
- conferência da memória de custos;
- comportamento com baixa amostra;
- sessão longa/bateria/aquecimento;
- offline → online.

Um `PASS` automático nunca substitui um item manual correspondente.

## Performance

A 0.19 mede no aparelho:

- duração da sessão;
- CPU acumulada do processo;
- relação CPU/tempo;
- memória PSS;
- bateria início/fim;
- queda observada por hora quando houver tempo suficiente;
- temperatura de bateria;
- estado térmico Android.

A 0.19 não inventa um limite universal de aprovação antes dos dados de campo.

## Privacidade do relatório

O relatório 0.19 compartilhado não inclui:

- device token;
- e-mail;
- screenshot;
- OCR bruto;
- log local bruto;
- endereço textual;
- coordenadas exatas;
- senha;
- chave MCP.

## Offer Engine congelado

Não alterados:

- `OfferParser.kt`;
- `UberOfferDetector.kt`;
- `SpatialOfferParser.kt`;
- `CardStabilizer.kt`;
- `OfferDeduplicator.kt`;
- `MediaProjectionOcrService.kt`;
- thresholds;
- fórmulas;
- sampling OCR.
