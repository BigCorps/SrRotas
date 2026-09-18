# Auditoria pré-1.0 — estado na RC3.6 Field

Este documento separa o que está entrando agora no APK de campo do que ainda precisa de gate antes da publicação pública.

## Implementado nesta rodada

- Reader Lab M1 × M2.
- AccessibilityService Uber-only em shadow mode.
- árvore de acessibilidade + screenshot de janela/fallback local ML Kit.
- comparação de completude do core.
- marca M1/M2/M1+M2 na janela flutuante.
- supervisor externo de recuperação do M1.
- crop seguro das cópias diagnósticas usando a janela Uber conhecida.
- divulgação explícita antes de ativar Acessibilidade.
- Configurações/Usuário integrados ao cabeçalho e dentro do shell principal.
- navegação inferior preservada em Configurações/Usuário.
- regra ampliada de Voltar nas telas secundárias.
- pesquisa consolidada em `Pesquisar região`.
- ícones Estatísticas/Radar corrigidos.
- controle de jornada compactado.
- IA com personagem ampliado/fade.
- métricas detalhadas com limiares do usuário.
- decoração da janela aplicada no mesmo ciclo de reconstrução para reduzir flicker.

## Já existe no projeto atual e precisa de validação, não de reimplementação cega

- trial iniciado na primeira oferta live válida;
- tabelas de entitlements/subscriptions/payments/credit_wallets;
- Pix/Banco Inter;
- reserva/consumo/refund de crédito de IA;
- Web handoff;
- OneSignal;
- exclusão de conta;
- MCP em estrutura separada;
- staging histórico e rastreabilidade da base V7.

## Gate ainda obrigatório antes de chamar 1.0.0

- validar Reader Lab em campo e escolher estratégia de produção (M1, M2 ou híbrida);
- zerar P0/P1 e eliminar travamentos que exijam reset;
- revisar o Access Resolver/comercial, pois o roadmap antigo prevê estados centralizados e o código atual ainda precisa auditoria final dessa camada;
- revisar limite real de 2 dispositivos, não apenas exibição na Web;
- fechar estratégia Play Integrity (observe/soft) ou documentar conscientemente o adiamento;
- revisar Security Advisor/Performance Advisor sem aplicar mudanças cegas;
- corrigir funções com `search_path` mutável se a auditoria confirmar risco;
- revisar índices de FKs críticos conforme carga real;
- habilitar proteção de senha vazada quando compatível com o fluxo de autenticação;
- atualizar Privacidade/Termos/Data Safety e declaração de AccessibilityService;
- gerar AAB assinado definitivo;
- testar atualização sobre a RC sem desinstalar;
- validar Pix real, trial, créditos, OneSignal, handoff e exclusão de conta em ambiente de produção.

## Base histórica V7

Não faz parte deste ZIP. Quando a passagem V7 terminar, integrar como etapa própria:

`artefatos V7 -> manifesto/hashes -> staging -> validação por finalidade -> versionamento -> agregados V2 -> consumo pelo motor`.

Não achatar a V7 para um JSON legado se isso eliminar qualidade/rastreabilidade.
