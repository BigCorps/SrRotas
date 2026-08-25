# FASE 0.21 — IMPLEMENTADA E FECHADA NA 0.21.1

## Baseline

- `0.21.0-beta`: estratégia multiplataforma, Agora, inteligência regional, Web, trial e temas.
- `0.21.1-beta`: acabamento UX final e correção de continuidade do OCR após avaliação de campo.
- Android: `versionCode 32`.
- Offer Engine: permanece congelado em `sr-rotas-v0.5.4`.

## Navegação final Android

Menu principal:

- **Agora** — tela principal;
- **Histórico** — inclui jornadas e ofertas;
- **IA**;
- **Configurações**.

A antiga Home deixou de ser uma aba. Seus estados operacionais foram incorporados a Agora/Configurações. Perfil passa a ser apresentado como Configurações. A versão aparece somente em Configurações na UI comum.

## Continuidade da leitura

O antigo joinha foi substituído por um **✓ minimalista para relatório**.

A seleção:

- não chama `markDoingRide()`;
- não muda `RideOperationalStatus`;
- não pausa nem encerra jornada;
- não fecha exposição;
- não interrompe MediaProjection/OCR;
- não bloqueia novas ofertas;
- permite somente uma oferta selecionada por jornada/escopo;
- sincroniza separadamente para `ride_offers.report_selected`.

Além disso, `canObserveOffers()` na 0.21.1 depende da jornada estar `ACTIVE`; a existência de uma corrida operacional em andamento não bloqueia mais a observação OCR. Pausa e fim de jornada continuam bloqueando, como devem.

## HUD e Buscar

Padronização visível para **Buscar** em vez de “retirada”.

Nova métrica selecionável no HUD:

- `Buscar: OK` — verde;
- `Buscar: Média` — amarelo;
- `Buscar: Alta` — vermelho.

O card mantém também:

- `Tempo para buscar: X min`;
- `Distância para buscar: X km`.

A classificação usa os limites independentes de km/min. Exceder qualquer limite configurado resulta em Alta. A faixa a partir de 75% do limite é Média.

## Estratégia

Perfis oficiais:

### Popular
- R$/km: `1,20 → 1,50`
- R$/min: `0,40 → 0,50`
- R$/h: `24 → 30`

### Conforto
- R$/km: `1,50 → 1,80`
- R$/min: `0,50 → 0,65`
- R$/h: `30 → 39`

### Premium
- R$/km: `1,80 → 2,20`
- R$/min: `0,65 → 0,85`
- R$/h: `39 → 51`

Os limites de Buscar são independentes dos presets e não são sobrescritos ao trocar Popular/Conforto/Premium. Qualquer ajuste financeiro fora do preset passa a ser tratado como Personalizado.

## Agora e indicador de funcionamento

Agora é a primeira tela do app e continua oferecendo:

- Agora;
- Hoje;
- Semana;
- Pesquisa;
- Base pessoal;
- Base coletiva opt-in;
- fallback Base Sr. Rotas histórica agregada.

A tela também mostra:

- **Tudo pronto**;
- **Ação necessária**.

O estado considera sobreposição/HUD, localização e captura OCR durante uma jornada. Pendências levam o motorista para Configurações/permissões.

## Histórico

- Jornada é subtópico/agrupamento do Histórico;
- ofertas recentes são visualmente separadas por cards/divisores;
- a oferta selecionada pelo ✓ recebe `✓ RELATÓRIO`;
- correção posterior de “Fiz esta corrida / Não realizei” continua separada do ✓;
- importação de screenshots históricos continua fora da experiência normal do motorista.

## Design

O Android continua nativo Kotlin. O `UiKit` passa a seguir a linguagem visual Web com:

- superfícies mais limpas;
- cantos e bordas consistentes;
- sombras leves;
- hierarquia tipográfica;
- espaçamento mais uniforme;
- Claro/Escuro/Automático.

O objetivo é melhorar percepção visual sem adicionar animações pesadas nem trabalho ao hot-path do OCR.

## Web

- `/app` redireciona para `/app/agora`;
- navegação principal: Agora / Histórico / IA / Configurações;
- temas persistidos;
- estratégia sincronizada;
- limites de Buscar independentes;
- handoff Android → Web por token descartável, sem `device_token` na URL;
- endpoint de relatório para ofertas selecionadas pelo ✓.

## Ícone exclusivo do menu flutuante

A bolha/menu flutuante passa a usar a nova arte redonda do Sr. Rotas enviada para esta correção. O recurso foi preparado como `android/app/src/main/res/drawable-nodpi/srrotas_bubble_icon.png`, com a área externa ao selo transparente.

Somente `JourneyBubbleController` referencia esse recurso. Permanecem inalterados:

- launcher principal;
- launcher redondo do aplicativo;
- logo usado nas telas;
- onboarding;
- versão Web;
- demais ícones Android.

## Probabilidade/recorrência de nova corrida no destino

A lacuna identificada na avaliação da 0.21 foi fechada. O motor já possuía cálculo de continuidade P10 no backend, mas o Android não o consultava automaticamente após resolver o destino e o resultado não aparecia no HUD/card.

A 0.21.1 final passa a executar este fluxo:

```text
oferta válida
→ Context Engine
→ destino + ETA
→ geocoder/célula de destino
→ consulta assíncrona de continuidade
→ HUD + card expandido da oferta
```

Regras:

- **P10 percentual** somente com pelo menos 20 intervalos elegíveis de exposição observada na região de destino;
- prioridade estatística: mesmo dia da semana + faixa de 3 h → mesma faixa em outros dias → célula em geral;
- base pessoal tem prioridade; base coletiva de exposição só participa quando o motorista optou por contribuir;
- se o P10 ainda não tiver denominador suficiente, a Base Sr. Rotas histórica usa os históricos validados e agregados para indicar **Alta / Média / Baixa recorrência**;
- o indicador histórico compara a concentração daquela região com as demais regiões na mesma janela temporal e amplia a janela apenas quando necessário;
- histórico de prints **não gera percentual falso**, pois não prova tempo de exposição sem oferta;
- `Dados insuficientes` permanece como resultado válido quando nem exposição nem seed histórico suportam conclusão.

Apresentação:

- HUD: `Destino: 67% · Alta` quando há P10 real;
- HUD: `Destino: Média` quando há apenas recorrência histórica;
- card expandido: `Nova corrida no destino: ...`, região, quantidade de amostras/intervalos e nível de confiança.

A consulta ocorre depois da persistência/geocodificação e não participa de OCR, parser, verdict, dedupe, estabilização ou jornada. Um resultado de rede atrasado só pode atualizar o HUD se a oferta ainda for a mais recente e tiver no máximo 12 segundos, evitando ressuscitar cards antigos.

## Inteligência regional e privacidade

Mantida a arquitetura da 0.21.0:

- histórico individual privado;
- seed global somente agregado;
- região/horário/categoria/métricas, sem oferta individual pública;
- coletivo exige opt-in e anonimização;
- não apresentar tendência como garantia de corrida.

## Limpeza técnica

A navegação comum não instancia mais:

- feedback Beta;
- pareamento Alpha legado.

O fluxo oficial é criar conta/entrar. Ferramentas internas de diagnóstico mantidas por segurança não ficam na navegação principal e poderão ser definitivamente removidas durante o hardening da 1.0 após confirmação de ausência de dependências externas.

## O que NÃO mudou

- Offer Parser;
- Spatial Offer Parser;
- Radar;
- Card Stabilizer;
- dedupe;
- sampling OCR 250 ms;
- fórmulas financeiras do Offer Engine;
- Context Engine;
- Cost Engine;
- ownership/quarentena e ordem de sync da 0.20.3;
- política: Sr. Rotas calcula, motorista decide.
