# Changelog — Sr. Rotas 0.7.0 Alpha

## Conta
- criação de conta com e-mail/senha;
- login;
- sessão própria por dispositivo;
- logout revogando o token do aparelho;
- endpoint `/api/v1/account/me`;
- compatibilidade com sessões Alpha antigas.

## Onboarding
Novo fluxo em 6 etapas:
1. boas-vindas/nome;
2. conta/aparelho;
3. permissões;
4. estratégia e tamanho do HUD;
5. tutorial do Painel de Rota;
6. checklist final.

## Produto
- Home mostra estado de configuração;
- estado online/offline;
- modo local vs. conectado;
- quantidade de ofertas pendentes;
- Perfil com conta, sessão, sincronização e suporte.

## Banco
Nova migration adiciona a `drivers`:
- `auth_user_id`;
- `email`;
- `onboarding_completed`;
- `last_login_at`.

## Motor
Nenhuma alteração em OCR/parser/Card Stabilizer.
`parser_version = sr-rotas-v0.5.4`.
