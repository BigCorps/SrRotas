# APLICAR — Sr. Rotas 0.21.2-beta

Base usada: `main` no commit `89ff22aa6a71f8cb2a79d8bb32bacc7cf59dc5d4` (`0.21.1-beta`), versão efetivamente avaliada em campo.

## GitHub Online

1. Abra `BigCorps/SrRotas` na branch `main`.
2. Extraia o ZIP final da `0.21.2-beta`.
3. Em **Add file → Upload files**, envie **todo o conteúdo da raiz do ZIP**, preservando as pastas `android/`, `backend/`, `supabase/` e os arquivos da raiz.
4. Confirme a substituição dos caminhos existentes.
5. Faça um único commit, por exemplo: `Sr. Rotas 0.21.2-beta`.
6. Aguarde GitHub Actions e Vercel antes de instalar o APK.

Não altere os secrets de assinatura. A versão continua usando a mesma chave estável.

## Supabase

**Não há SQL novo para executar na 0.21.2.**

As migrations da 0.21/0.21.1 já aplicadas em produção permanecem válidas. Os SQLs presentes neste ZIP existem apenas para manter o repositório reproduzível.

## Android

Esperado após o upload:

```text
versionCode 33
versionName 0.21.2-beta
```

Instale por cima da `0.21.1-beta`, sem desinstalar e sem limpar dados.

## Guardrails

A 0.21.2 **não inclui nem altera** os arquivos congelados do Offer Engine:

- `OfferParser.kt`
- `SpatialOfferParser.kt`
- `UberOfferDetector.kt`
- `CardStabilizer.kt`
- `OfferDeduplicator.kt`

Sampling continua em 250 ms e limite OCR em 2100 px. As correções de captura foram feitas ao redor do motor validado.

## Alterações desta rodada

- captura de app individual no Android 14+ acompanha o tamanho real do conteúdo compartilhado;
- bolha e HUD protegidos contra captura do próprio Sr. Rotas;
- OCR suspenso enquanto uma Activity do Sr. Rotas está visível;
- perfis Popular/Conforto/Premium aplicam valores e ficam destacados;
- Busca em km/min fica dentro de Estratégia e HUD;
- indicador passa a ser `Busca Boa / Média / Alta`;
- km/min de busca não ocupam linhas separadas no HUD;
- tamanhos Compacto/Normal/Grande diferenciados;
- voltar fixo nas telas longas de configuração;
- descrições permanentes removidas das telas principais;
- adaptive icon sem fundo preto forçado;
- borda do HUD com 3 dp e cor do estado geral;
- rota `Combinado` via Google Maps quando busca e destino estão disponíveis;
- cards Agora mostram a faixa real de 3 horas analisada.

## Depois do deploy

Siga `TESTE-0.21.2.md`. Só avance para 1.0-A se os itens críticos de leitura/captura e anti-loop estiverem aprovados em campo.
