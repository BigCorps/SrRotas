# Sr. Rotas — README DE CONTINUIDADE

Versão documental: `2026-10-02.5`
Roadmap mestre: `ROADMAP-CANONICO.md`
Roadmap de publicação: `ROADMAP-PLAYSTORE-1.0.md`

Base Android homologada: `0.33.6-field / versionCode 83`
Field validada mais recente: `0.33.8-field / versionCode 85`
HEAD 0.33.9: `0.33.9-field / versionCode 86` — Gate 5 Android Access/UI.
Admin Web: produção READY.

## O que é o vc86
O vc86 alinha o Android ao contrato comercial já homologado:
- Sr. Rotas Copiloto = grátis;
- Sr. Rotas Inteligência = R$ 9,90 / 30 dias;
- trial de Inteligência;
- sem créditos por pergunta;
- fim do trial/assinatura não bloqueia Reader/HUD/Copiloto.

Também remove textos antigos de Alpha/créditos nas superfícies Android alteradas.

## O que não muda
- Reader M1;
- Reader2;
- Controlled Hybrid;
- parser;
- Money;
- HUD;
- histórico;
- regras de corrida;
- Radar legado;
- Supabase schema;
- V7.

## Gate do tester
Instalar 0.33.9-field / vc86 por cima do 0.33.8-field / vc85.
Não desinstalar e não limpar dados.

Esperado:
- sessão/configurações preservadas;
- Reader/HUD sem regressão;
- oferta real continua entrando;
- plano mostra Copiloto gratuito + Inteligência;
- nenhuma referência comercial a créditos/Alpha;
- assinatura paga reconhecida;
- Estatísticas/Pergunte Premium funcionam;
- diagnóstico continua exportando Access Resolver.

## Access Resolver
Continua em OBSERVE:
- enforcement_mode=observe;
- max_active_devices=2;
- require_device_identity=false.

## Comercial
Homologado:
- Pix real R$ 9,90;
- Banco Inter CONCLUIDA;
- assinatura por 30 dias;
- PAID_ACTIVE;
- sem cobrança automática.

## MCP
OAuth externo homologado funcionalmente.
Perfil duplicado do usuário Jadiel foi consolidado em um único driver canônico.
Android, Web/OAuth e V7 agora convergem para o mesmo perfil.

## Próximos gates
- validar vc86 sobre vc85;
- teste de novo usuário ponta a ponta;
- offline→online;
- soak;
- observe→enforce controlado;
- Play Integrity;
- Data Safety/AAB/RC.
