# Aplicar — Sr. Rotas 0.13.2 Beta — startup crash fix

Base esperada: `e8d510adb8d9aab6c477ceb3e7d5df23d018a81c`.

## Causa confirmada pelo crash
A 0.13.1 chamava `activity.window.insetsController` dentro de `UiKit.applySystemBars()`,
antes de `setContentView()`. Em algumas ROMs/Android recentes o `DecorView` ainda não
existe nesse momento e `PhoneWindow.getInsetsController()` lança NullPointerException.

## Correção
- remove totalmente o acesso a `Window.insetsController` antes de criar a tela;
- mantém as cores das system bars;
- ajusta contraste dos ícones somente depois que a View raiz já existe;
- mantém a safe area/insets da 0.13.1;
- versão `0.13.2-beta`, versionCode 19.

## Aplicação
Extraia na raiz do repositório e faça commit/push.

Não há SQL, Edge Function ou alteração em OCR/Offer Engine.
