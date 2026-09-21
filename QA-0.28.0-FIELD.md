# Sr. Rotas 0.28.0 Field — validação de campo

Versão: **0.28.0-field**  
versionCode: **69**  
Base: **0.27.0 RC3.7.2 / c1a095aabe94e35ba9f8178bc5a5d084a6dc5060**

## Objetivo desta rodada

Validar a baseline **M1** depois de três regressões encontradas em campo: janela compacta, Pesquisa por região colapsável e leitura numérica/geometria incorreta. O Reader 2.0 externo **não está ativo nesta build**.

## 1. Janela flutuante compacta

Configurações → Janela flutuante:

1. confirmar que existe **Usar painel compacto**;
2. testar marcado e desmarcado;
3. fechar e abrir as Configurações e confirmar que a escolha persiste;
4. abrir a janela com 1, 3 e 5 ofertas;
5. abrir Mais detalhes, Mensagens e os botões de mapa;
6. deixar a janela aberta por pelo menos 10 minutos.

Aprovação:

- Compacta e normal ficam claramente diferentes;
- modo compacto reduz largura/espaçamentos/texto sem esconder ações;
- nenhum pulo periódico de largura/altura;
- não reaparece watcher/polish visual antigo.

## 2. Agora — Pesquisar região

1. abrir Agora;
2. confirmar que **Pesquisar região ▾** inicia recolhido;
3. tocar e confirmar campo + Momento/Hoje/Semana/Pesquisa + Base + Perfil + Consultar;
4. tocar novamente e confirmar que recolhe;
5. realizar uma consulta e confirmar resultados normalmente.

## 3. Leitura M1 — foco principal

Manter **Método 1 / M1** durante esta rodada.

Para cada oferta visível, quando possível conferir:

- horário;
- local de embarque;
- tempo até embarque;
- local de destino;
- tempo total.

Também observar tarifa, distância de busca, distância da viagem e os cálculos exibidos.

### Se aparecer HUD com número suspeito

1. tocar **Registrar falha** imediatamente;
2. anotar horário aproximado e o valor correto visto no Uber;
3. não encerrar a jornada só por causa do reporte;
4. continuar o teste para vermos se as próximas ofertas permanecem normais.

Caso de regressão que a 0.28 deve bloquear: leitura equivalente a **11 km em 4 min** de busca quando a oferta real era aproximadamente **1,0 km em 4 min**.

## 4. Gate de integridade

O gate 0.28 deve rejeitar amostras impossíveis antes do HUD/base, mas não pode eliminar ofertas legítimas de forma recorrente.

Aprovação:

- nenhuma oferta com geometria claramente impossível aparece no HUD;
- ofertas legítimas continuam aparecendo;
- viagens longas legítimas continuam sendo aceitas;
- não há aumento forte de ofertas visíveis no Uber sem HUD.

## 5. Jornada longa

Fazer, se possível, pelo menos **2 horas** de uso real:

- iniciar jornada;
- receber várias ofertas;
- aceitar/fazer ao menos uma corrida;
- voltar a receber novas ofertas depois da corrida;
- bloquear/desbloquear a tela;
- alternar Uber e Sr. Rotas normalmente;
- encerrar jornada somente no final.

## 6. Diagnóstico obrigatório no final

Configurações → Diagnóstico de leitura → compartilhar JSON.

Confirmar no arquivo:

- `reader_lab_0270361`;
- `m2_health`;
- `offer_integrity_028`.

Dentro de `offer_integrity_028`, observar especialmente:

- `rejected_total`;
- `pickup_speed_outlier`;
- `trip_speed_outlier`;
- `total_km_mismatch`;
- `per_km_mismatch`;
- `last_reason`.

## Critério para avançar para 0.29

A 0.29 será o **Integration Map do Reader 2.0**, não uma substituição automática do M1. Só avançar quando esta 0.28 demonstrar:

- janela compacta restaurada e estável;
- pesquisa colapsável restaurada;
- nenhuma regressão numérica importante conhecida;
- M1 utilizável em jornada longa;
- diagnóstico suficiente para qualquer falha residual;
- nenhum P0/P1.
