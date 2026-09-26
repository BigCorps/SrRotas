# Sr. Rotas — README DE CONTINUIDADE

**Leia este arquivo antes de alterar qualquer coisa.**

Versão documental: `2026-09-26.3`
Roadmap mestre: `ROADMAP-CANONICO.md`
HEAD técnico: `0.33.5-field / versionCode 82`
APK atualmente em campo: `0.33.2-field / versionCode 79`

## 1. Como trabalhar neste projeto

- Repositório: `BigCorps/SrRotas`.
- O agente pode inspecionar GitHub, mas **não deve escrever diretamente no repositório**.
- Entregas de código devem ser ZIPs com arquivos completos em caminhos relativos.
- O usuário sobe manualmente na `main`.
- Não criar branch sem pedido explícito.
- Supabase pode ser alterado somente quando o usuário autorizar a operação correspondente.
- Evitar retrabalho: investigar o estado real antes de “implementar” algo que pode já existir.

### Regra documental obrigatória

Todo ZIP técnico deve conter:
- `ROADMAP-CANONICO.md`;
- `README-CONTINUIDADE.md`;
- manifest da entrega;
- arquivos modificados completos;
- LEIA-PRIMEIRO quando necessário.

Nunca entregar um patch sem atualizar o estado do projeto nesses dois documentos.

## 2. O que está acontecendo agora

### Em campo
`0.33.2-field / vc79`

O irmão/testador está usando esta versão para validar:
- perda de MediaProjection;
- notificação “Sr. Rotas — leitura pausada”;
- retomada com novo consentimento;
- mesma jornada preservada;
- crash observability.

**Não trocar o APK dele até chegar o JSON 3**, salvo regressão bloqueante.

### Pronto em paralelo
`0.33.3-field / vc80`
- Action #125 verde.
- Corrige recovery do sync de odômetro/energia.
- Ainda não enviada ao irmão.

`0.33.4-field / vc81`
- Restaura métricas da janela flutuante.
- Upload em `main`: commit `7161902957cf6903dd0ac26f748da8de31bc228c`.
- Action #126 **SUCCESS**.
- Ainda não enviar ao irmão.

## 3. Próximo passo exato

Aguardar o usuário enviar o **JSON 3 da 0.33.2**.

Quando chegar:
1. analisar `capture_resilience_0311`;
2. analisar `capture_recovery_notification_0332`;
3. analisar `crash_observability_0332`;
4. conferir OCR / M1 / Reader 2 / Money / storage para regressões;
5. se P0 estiver aprovado, escolher a próxima build consolidada de campo com 0.33.3 + 0.33.4 já verdes;
6. avisar explicitamente ao usuário quando for hora de enviar novo APK ao irmão.

## 4. Módulos que NÃO devem ser reabertos sem evidência

- Histórico Android — FROZEN.
- M1 — Reader oficial.
- Offer admission/integrity — estável.
- Turbo Mais / Money Roles — estável.
- V7 processamento — concluído.
- V7 canonicalização/legacy cleanup — concluído.
- Base Coletiva — dados reais + opt-in; não misturar V7.
- `ride_offers` — operacional real; não repopular com histórico sintético.

## 5. V7 em uma frase

Os ~40 mil screenshots **já foram processados**. O foco não é OCR histórico.

O V7 está no backend e passou a alimentar:
- seed temporal/geográfica;
- Base Pessoal;
- Agora;
- continuidade de destino;
- Estatísticas;
- melhores horários;
- contexto analítico da IA.

A próxima etapa de dados é QA da inteligência, confiança/amostra, probabilidade e recomendações — não reconversão de screenshots.

## 6. Reader em uma frase

M1 continua oficial.

M2/Reader 2 está em:
`Parallel → Accumulator → Consensus`

Controlled Hybrid continua desligado até o JSON real provar consenso Reader2-only estável e seguro.

Não promover M2 por intuição.

## 7. P1 atuais

### Odômetro
0.33.3 corrige a ordem:
`SyncCoordinator core → journey garantida → métricas/energia`.

Depois do próximo APK de campo:
- preencher odômetro normalmente;
- verificar `pending_metrics=0`;
- confirmar `journey_vehicle_metrics` no backend.

### Janela flutuante
0.33.4 restaura no primeiro nível:
- R$/km;
- R$/min;
- R$/h;
- km;
- minutos;
- lucro estimado;
- demais métricas habilitadas quando disponíveis.

Classificação vem do `HudMetricEvaluation0221`; não duplicar thresholds.

## 8. Prioridades resumidas

P0: estabilidade/captura/recovery/long-run.
P1: regressões reais — odômetro, janela flutuante e bugs confirmados.
P2: V7 + inteligência temporal/geográfica + Base Pessoal/Agora/probabilidade.
P3: Estatísticas avançadas/IA algorítmica/recomendação.
P4: Radar contextual.
P5: comercial/admin web.
P6: hardening, LGPD, Data Safety, Play Store, RC, 1.0.

## 9. QA de campo

O motorista não deve fazer anotações enquanto dirige.

Usar:
- uso normal;
- bug report quando seguro;
- JSON ao final;
- screenshots somente se naturalmente disponíveis.

## 10. Regra de ouro

**HEAD não significa homologado. IMPLEMENTADO não significa HOMOLOGADO.**

Sempre distinguir:
- código atual;
- CI;
- build realmente instalada;
- evidência real de campo.



## 11. Atualização DOC2 — 26/09/2026

A 0.33.4 está oficialmente **CI VERDE** na Action #126.

Estado de continuidade:
- build em campo: 0.33.2;
- build pronta de Odômetro: 0.33.3 / Action #125 verde;
- build pronta de Janela Flutuante: 0.33.4 / Action #126 verde;
- próximo gatilho de decisão: JSON 3 da 0.33.2.

Depois do JSON 3, decidir se o próximo APK de campo será uma consolidação das correções já verdes.


## 12. 0.33.5 / CI1 — estado exato

O primeiro upload da 0.33.5 gerou o commit:
`26ae4e3a8a564bf9d9609e546678927ee229fc15`.

A Action #127 falhou no `Architecture regression guard`.
Unit tests e builds foram pulados.

Causa: o guard ainda exigia textos fixos do Roadmap 2026-09-24.2 / estágio
0.33.0. Isto conflita com a nova regra de manter Roadmap e README sempre
atualizados.

O CI1 corrige somente o guard/documentação. Não altera o runtime da 0.33.5.

### Campo

O irmão continua usando 0.33.2/vc79.
Não enviar 0.33.3, 0.33.4 ou 0.33.5 individualmente enquanto o JSON 3 não for
analisado. Elas são candidatas à próxima consolidação depois do P0.
