# Aplicar — Sr. Rotas 0.12.0 Alpha

Base esperada: `2293334f63b90934e18e58427978cc1641ffe303` (0.11 aplicada).

## Ordem
1. Execute `supabase/migrations/20260818_security_rate_limit_012.sql` manualmente.
2. Extraia o ZIP na raiz do repositório e faça commit/push.
3. Aguarde Android CI e Vercel.
4. A Edge `srrotas-delete-push-user` pode ser deployada agora mesmo; sem secrets OneSignal ela retorna `skipped` e não bloqueia exclusão. Quando a 0.11 for concluída, redeploy não é necessário se os secrets forem adicionados ao projeto.

## O que mudou
- AccessibilityService removida do produto compilado/manifest;
- MediaProjection passa a ser a única fonte de captura;
- backend padrão migra automaticamente para `https://srrotas.com`;
- exclusão real dentro do app e pela Web;
- limpeza de dados locais após exclusão pelo Android;
- rate limit de login/cadastro/exclusão Web usando somente hash;
- privacidade/termos atualizados;
- documentação para Data Safety/FGS/Play Console.

## Não depende do Firebase
A 0.12 funciona mesmo enquanto aguardamos a liberação do projeto Firebase/OneSignal.
