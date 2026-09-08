# Sr. Rotas — revisão da Política de Privacidade para 1.0.0

## Arquivo para aplicar

`backend/app/privacidade/page.tsx`

## Revisão realizada contra a implementação atual

A política anterior já cobria conta, MediaProjection/OCR, IA, MCP, pagamentos,
OneSignal, segurança e exclusão. A revisão para 1.0.0 acrescenta ou explicita:

- localização aproximada durante jornadas;
- Base Pessoal;
- Base Coletiva com opt-in e mínimo de 3 motoristas nas agregações atuais;
- ofertas, jornadas, odômetro, combustível/recarga e gastos;
- digitalização de jornadas e histórico;
- Assistente Ativo;
- screenshots privadas desligadas por padrão;
- diagnósticos e feedback;
- provedores utilizados;
- ausência de venda de dados;
- finalidades e bases legais;
- retenção;
- transferências internacionais;
- direitos do titular conforme LGPD;
- independência em relação a Uber/99;
- data de atualização para 08/09/2026.

## O que NÃO foi alterado

- Termos de Uso;
- fluxo de exclusão;
- Supabase;
- Vercel;
- banco de dados;
- Android;
- política de cobrança.

## Próximo passo recomendado na Play Console

Usar:

`https://srrotas.com/privacidade`

Depois do deploy desta alteração, revisar o formulário **Segurança dos dados
(Data Safety)** usando esta política como referência, para evitar divergências
entre a declaração da Play Store e o comportamento real do app.

> Esta revisão foi feita com base no comportamento técnico atual do Sr. Rotas.
> Para uma validação jurídica formal, a BigCorps pode submeter o texto a um
> profissional jurídico especializado em LGPD.
