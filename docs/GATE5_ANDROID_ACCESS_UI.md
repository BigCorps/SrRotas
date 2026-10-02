# Gate 5/7 — Android access/UI

Base: `d8cf7930e104948734d90b9cb5e3ac4da1aa6909`

## Entrega
- Android `0.33.9-field / versionCode 86`, instalável por cima do vc85.
- Sem mudança em Reader, OCR, HUD, MediaProjection, OfferDispatcher ou Radar legado.
- Android passa a apresentar `Sr. Rotas Copiloto` (grátis) e `Sr. Rotas Inteligência` (R$ 9,90 / 30 dias).
- Textos de créditos/Alpha são removidos das superfícies ativas.
- Diagnóstico do Access Resolver passa a exportar `commercial_tier` e `can_analytics`.
- Tela Web do Pix identifica `Intermediações de Pagamentos BigCorps` e `Sr.Rotas | Desenvolvido por BigCorps`.
- Perfil Web remove o saldo de créditos da UI comercial.

## Rollout
`enforcement_mode` continua `observe`. Este Gate NÃO muda banco e NÃO promove enforcement.

## Aceite
1. Actions/Android CI verde.
2. Instalar vc86 por cima do vc85 sem limpar dados.
3. Jornada/Reader/HUD continuam funcionando.
4. Usuário mostra Copiloto grátis + Inteligência correta.
5. Pergunte funciona para PAID_ACTIVE/TRIAL_ACTIVE e não mostra carteira de créditos.
6. Só depois testar enforcement controlado.

## Observação de identidade
A duplicidade específica do Jadiel foi consolidada no banco antes deste Gate. O hardening genérico de cadastro/login legado fica para a homologação final, porque exige alterar o contrato de sessão Android e não deve ser misturado com este patch de acesso/UI sem teste dedicado.
