# Aplicar — Sr. Rotas 0.7.0 Alpha

Patch incremental sobre a `main` 0.6.0 / commit esperado:

`f5adb337b27095d4aef368983e279a32049430fa`

## Esta é a fase 0.7 inteira

- conta por e-mail e senha no backend;
- sessão por token específico do aparelho;
- logout revogando o token do aparelho;
- fluxo guiado de primeira configuração;
- compatibilidade com aparelhos já pareados no Alpha;
- permissões explicadas em linguagem simples;
- escolha de estratégia inicial;
- escolha de tamanho do HUD;
- tutorial de toque / segurar e arrastar;
- checklist final;
- Home mostra configuração pendente, online/offline, sessão e pendências;
- Perfil mostra conta, sessão, sincronização e suporte;
- diagnóstico passa a incluir estado do onboarding sem expor senha/token/e-mail completo;
- Offer Engine v1 continua congelado em `sr-rotas-v0.5.4`.

## Ordem de aplicação

### 1. Aplicar o SQL manualmente no Supabase

Execute:

`supabase/migrations/20260817_account_onboarding_07.sql`

Este patch **não executa SQL automaticamente**.

### 2. Aplicar os arquivos do ZIP no GitHub

Extraia na raiz do repositório e faça commit/push na `main`.

O push deve:
- disparar o GitHub Actions do Android;
- disparar o deploy do backend na Vercel, se a integração atual estiver ativa.

### 3. Não há novas variáveis de ambiente

A fase 0.7 usa as mesmas:
- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- demais variáveis já existentes.

## Compatibilidade com o aparelho usado nos testes

Se o APK anterior já possuir `device_token`, o onboarding detecta a sessão Alpha existente e permite continuar sem recriar a conta.

Assim não quebramos os testes/histórico atuais.

## Sobre confirmação de e-mail

Nesta fase Alpha fechada, contas criadas pelo endpoint são marcadas como e-mail confirmado pelo backend para não depender de SMTP durante os testes.

**Antes da Play Store, a fase 0.12 deve trocar isso por verificação real de e-mail e revisar rate limiting/segurança.**

## O que testar

1. Atualizar do 0.6 para 0.7 sem limpar os dados:
   - deve reconhecer o aparelho já conectado;
   - deve abrir o onboarding;
   - deve permitir concluir sem perder histórico.

2. Instalação limpa:
   - criar conta;
   - entrar com conta existente;
   - concluir permissões;
   - escolher estratégia;
   - pré-visualizar HUD;
   - concluir onboarding.

3. Home:
   - online/offline;
   - conta conectada/modo local;
   - pendências;
   - botão iniciar jornada.

4. Perfil:
   - atualizar conta;
   - sincronizar;
   - sair deste aparelho;
   - após logout, onboarding volta a aparecer.

5. Motor:
   - OCR/Card Stabilizer devem continuar equivalentes à 0.5.4.

## Não entra nesta fase

- Histórico analítico completo (0.8);
- IA + MCP final (0.9);
- Pix/assinatura/créditos (0.10);
- OneSignal (0.11);
- hardening de produção / verificação de e-mail / Play compliance (0.12).
