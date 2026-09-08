# Como aplicar — Sr. Rotas 0.26.5-beta

Este ZIP é um **patch de sobreposição** com arquivos completos nos caminhos corretos. Ele deve ser aplicado sobre a base `92a087424748665a865a529d5624a17295e104a0`.

1. Confirme que o repositório `BigCorps/SrRotas` está na base 0.26.4 mais recente.
2. Extraia o ZIP.
3. No GitHub, envie todo o conteúdo extraído preservando a estrutura de pastas e aceite substituir os arquivos existentes com o mesmo caminho.
4. Confirme o commit.
5. Aguarde **Android CI + Field APK** terminar.
6. Só instale o APK de campo se **Unit tests**, **Build debug APK** e **Build field release APK** ficarem verdes.
7. Baixe o artifact `sr-rotas-field-release-apk` quando a assinatura estável estiver disponível.

Não há SQL nem mudança manual no Supabase para aplicar nesta rodada.

## Importante sobre a fonte do cabeçalho

O pedido visual cita Russo One. Este patch não distribui um arquivo de fonte. O cabeçalho usa uma família condensada nativa como fallback e mantém a nova assinatura visual clara/escura. Isso evita inserir no pacote um arquivo de fonte externo e não interfere nas demais correções.
