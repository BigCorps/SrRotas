# Sr. Rotas — README DE CONTINUIDADE

Versão documental: `2026-10-02.6`
Roadmap mestre: `ROADMAP-CANONICO.md`
Roadmap de publicação: `ROADMAP-PLAYSTORE-1.0.md`

Base Android homologada: `0.33.6-field / versionCode 83`
Field validada mais recente: `0.33.8-field / versionCode 85`
HEAD reconciliado: `0.33.10-field / versionCode 87` — Gate 5 Android Access/UI.
Admin Web: produção READY.

## O que é o vc87
O vc87 corrige resíduos encontrados ao validar o vc86 sem voltar o projeto para versão antiga:
- onboarding real passa a Copiloto grátis + 7 dias de Inteligência;
- remove referências ativas a carteira/créditos;
- Base Coletiva ativa usa degradê diagonal;
- Base Pessoal usa somente borda azul;
- seleção visual deixa claro qual base está ativa;
- cabeçalho preserva logo à esquerda, reduzido ~30%, e seção à direita;
- guard de arquitetura passa a resolver a versão dinamicamente.

## Preservado
- Reader M1;
- Reader2 shadow;
- Controlled Hybrid OFF;
- MediaProjection/OCR;
- roteamento Uber/99 na mesma jornada;
- Money;
- HUD;
- Histórico;
- Radar legado;
- V7;
- Access Resolver em OBSERVE.

## Gate do tester
Instalar `0.33.10-field / versionCode 87` por cima do vc86/vc85.
Não desinstalar e não limpar dados.

Esperado:
- sessão/configurações preservadas;
- Reader/HUD sem regressão;
- oferta real continua entrando;
- onboarding não mostra créditos/Alpha;
- Copiloto + Inteligência coerentes;
- Base Coletiva/Pessoal visualmente distintas;
- pesquisa de região recolhida;
- assinatura paga reconhecida;
- Estatísticas/Pergunte funcionam;
- diagnóstico exporta Access Resolver.

## Comercial
Homologado:
- Pix real R$ 9,90;
- Banco Inter CONCLUIDA;
- assinatura por 30 dias;
- PAID_ACTIVE;
- sem cobrança automática.

## MCP
OAuth externo homologado funcionalmente.
Perfil duplicado do Jadiel consolidado em um único driver canônico.

## Próximos gates
- validar vc87 sobre vc86/vc85;
- teste de novo usuário ponta a ponta;
- offline→online;
- soak;
- observe→enforce controlado;
- Play Integrity;
- Data Safety/AAB/RC.
