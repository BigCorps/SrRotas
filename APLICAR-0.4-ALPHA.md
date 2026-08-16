# Como aplicar — Sr. Rotas 0.4 Alpha

Esta atualização parte do 0.3 que já está aplicado no projeto.

## 1. Supabase primeiro

Execute **somente a nova migration** no SQL Editor do projeto SrRotas:

`supabase/migrations/20260816_parser_strategy_04.sql`

Ela adiciona as novas métricas das ofertas e as faixas configuráveis do Cherry Picker. É idempotente (`if not exists`).

## 2. GitHub

Extraia o ZIP completo por cima do repositório `BigCorps/SrRotas`, preservando os caminhos, e faça commit na `main`.

Android:

- `versionCode = 4`
- `versionName = 0.4.0-alpha`
- package continua `com.srrotas.app`

## 3. Vercel

Nenhuma variável nova é obrigatória.

Mantenha:

```env
NEXT_PUBLIC_SITE_URL=https://sr-rotas.vercel.app
NEXT_PUBLIC_INDEX_SITE=false
NEXT_PUBLIC_SUPPORT_EMAIL=contato@bigcorps.com.br
```

Quando o domínio for registrado, troque `NEXT_PUBLIC_SITE_URL` para `https://srrotas.com`.

## 4. Builds

O workflow Android agora roda os testes unitários do parser antes de montar o APK. Se os testes passarem, baixe o artifact `sr-rotas-debug-apk` e envie o novo APK ao testador.

## 5. O que observar no teste 0.4

1. A tela normal do Uber não pode gerar ofertas falsas de `R$ 260,76` ou `+R$ 1,25`.
2. `R$ x,xx/km aprox.` não pode virar preço da corrida.
3. Priority não pode usar o adicional `+R$ ... incluído` como nova oferta.
4. Radar deve ser identificado como Radar quando houver `Radar de Viagens`/`Selecionar`.
5. Compare R$/km, R$/hora, R$/min, avaliação, lucro e semáforo.
6. Teste o preview e as personalizações de HUD.
7. Se houver diferença, compartilhe o diagnóstico textual do 0.4.

## Privacidade

A captura automática de screenshot é **desligada por padrão**. Se o usuário ativá-la, os arquivos ficam no armazenamento privado do app, limitados aos 30 mais recentes, não entram na galeria e não são enviados automaticamente.

Sr. Rotas é desenvolvido pela BigCorps — contato@bigcorps.com.br.
