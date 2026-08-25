# TESTE DE CAMPO — Sr. Rotas 0.21.1-beta

Instale a 0.21.1 **por cima da 0.21.0**. Não desinstale e não limpe dados.

## 1. Build e atualização

- GitHub Actions verde;
- Vercel `READY`;
- APK assinado com a chave estável;
- app informa `0.21.1-beta` somente em Configurações;
- dados/histórico da 0.21.0 continuam presentes.

## 2. Navegação

Confirmar que o rodapé mostra somente:

- Agora;
- Histórico;
- IA;
- Configurações.

Confirmar:

- Agora abre primeiro;
- não existe aba Início;
- não existe aba Jornada;
- Jornada aparece dentro de Histórico;
- Perfil aparece como Configurações;
- cabeçalhos internos não repetem logo + “Sr. Rotas” + versão.

## 3. Regressão do núcleo

Em uma jornada real:

- MediaProjection abre normalmente;
- OCR mantém a velocidade percebida da 0.20.3/0.21.0;
- Exclusive e Radar continuam reconhecidos;
- HUD permanece estável/sem piscar;
- origem/destino e Maps continuam corretos;
- novas ofertas continuam chegando durante toda a jornada;
- sync não recria tempestade 400/404;
- quarentena antiga permanece sem ser atribuída a outra conta.

## 4. ✓ de relatório — teste crítico

Com várias ofertas aparecendo:

1. toque no ✓ de uma oferta;
2. confirme que **somente ela** fica marcada;
3. aguarde/receba outra oferta;
4. confirme que o OCR continua lendo normalmente;
5. confirme que as outras ofertas não ficam apagadas/opacas;
6. marque uma segunda oferta na mesma jornada;
7. confirme que a seleção anterior é substituída;
8. abra Histórico e confirme `✓ RELATÓRIO` na selecionada.

O ✓ **não pode**:

- pausar jornada;
- encerrar jornada;
- esconder o HUD das ofertas seguintes;
- parar MediaProjection;
- mudar automaticamente a corrida para realizada/em andamento.

A correção manual “Fiz esta corrida / Não realizei” no Histórico continua sendo outro conceito.

## 5. Buscar no HUD

Em **Configurações → Estratégia e HUD → Personalizar métricas**, habilite Buscar.

Validar no card:

- `Tempo para buscar: X min`;
- `Distância para buscar: X km`;
- `Buscar: OK`, `Média` ou `Alta`.

Com limites de exemplo `4 km / 8 min`:

- abaixo de 75% dos limites → OK;
- a partir de 75%, sem ultrapassar → Média;
- ultrapassou km **ou** minutos → Alta.

Se só um dado estiver disponível, avaliar apenas o disponível.

## 6. Perfis

Confirmar que só existem:

- Popular;
- Conforto;
- Premium;
- Personalizado.

Valores:

- Popular: km `1,20→1,50`, min `0,40→0,50`, hora `24→30`;
- Conforto: km `1,50→1,80`, min `0,50→0,65`, hora `30→39`;
- Premium: km `1,80→2,20`, min `0,65→0,85`, hora `39→51`.

Trocar o perfil **não pode mudar** os limites de Buscar em km/min.

## 7. Agora / prontidão

Na tela Agora:

- testar Agora / Hoje / Semana / Pesquisa;
- confirmar localização/região quando disponível;
- confirmar fallback histórico quando a base pessoal for insuficiente;
- nunca chamar o resultado de “demanda ao vivo” ou garantia.

Status:

- com permissões necessárias → **Tudo pronto**;
- faltando HUD/localização ou captura durante jornada → **Ação necessária**;
- botão de correção deve levar a Configurações/permissões.

## 8. Histórico

- registros visualmente separados;
- identificar claramente começo/fim de cada oferta;
- jornada agrupada no Histórico;
- ✓ de relatório visível quando aplicável;
- “Fiz esta corrida / Não realizei” continua funcionando sem confundir com ✓.

## 9. Temas

Android e Web:

- Automático;
- Claro;
- Escuro.

Android:

- HUD seguindo app;
- HUD Claro com app Escuro;
- HUD Escuro com app Claro;
- contraste de verde/amarelo/vermelho legível.

## 10. Web

- abrir Web pelo Android já autenticado;
- deve cair em `/app/agora`;
- URL não pode conter `device_token`;
- navegação Web: Agora / Histórico / IA / Configurações;
- preset e tema continuam sincronizados;
- limites de Buscar permanecem independentes dos presets.

## 11. Ícone da bolha

- iniciar uma jornada e confirmar que a bolha flutuante usa a nova arte redonda enviada para a 0.21.1;
- confirmar que o ícone instalado no launcher do Android continua sendo o anterior;
- confirmar que logo, onboarding e Web não foram alterados;
- conferir que o novo ícone não apresenta o quadriculado externo: a área fora do selo deve ficar transparente.

## 12. Continuidade no destino — teste central

Em ofertas nas quais o destino for identificado:

1. confirme que origem/destino e Maps continuam normais;
2. aguarde o enriquecimento assíncrono;
3. no HUD deve aparecer uma linha `Destino: ...`;
4. no card expandido do menu flutuante deve aparecer `Nova corrida no destino: ...`;
5. confirme que novas ofertas continuam sendo lidas enquanto a inteligência é consultada.

Resultados válidos:

- `Destino: 67% · Alta` (exemplo) somente quando houver P10 real com no mínimo 20 intervalos elegíveis;
- `Destino: Alta`, `Média` ou `Baixa` quando a resposta vier do seed histórico;
- `Dados insuficientes` quando a região não tiver base suficiente.

Conferir ainda:

- o percentual nunca deve aparecer a partir de prints históricos isoladamente;
- dia da semana e faixa de horário devem influenciar o resultado;
- ao receber uma segunda oferta enquanto a primeira consulta ainda está em andamento, um retorno atrasado da primeira **não pode** substituir/reabrir o HUD da oferta nova;
- falha de rede/inteligência não pode bloquear OCR, HUD financeiro, Maps ou persistência da oferta;
- a mensagem deve ser tratada como tendência histórica, nunca garantia.

## 13. Critério para ir à 1.0-A

- Actions verde;
- Vercel READY;
- zero P0/P1;
- ✓ não interrompe OCR;
- leitura contínua durante jornada;
- Buscar correto;
- presets corretos;
- navegação final aprovada;
- nenhum feedback Beta/pareamento Alpha na UI comum;
- sync continua zerando fila válida sem quebrar quarentena;
- continuidade no destino aparece sem bloquear OCR e sem inventar percentual histórico.
