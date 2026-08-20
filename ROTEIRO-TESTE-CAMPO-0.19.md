# Roteiro de campo — Sr. Rotas 0.19

O motorista não precisa executar este roteiro como teste técnico formal.
Ele serve como referência para a BigCorps cruzar os relatos com os dados do app.

## Antes de sair

Com o veículo parado:

- confirmar `0.19.0-beta`;
- confirmar HUD autorizado;
- confirmar localização aproximada;
- abrir `Perfil → Central do testador → Validação 0.19`;
- tocar `Iniciar medição de desempenho`;
- se desejar, configurar `Meus custos`.

## Durante a jornada

Observar naturalmente:

### Offer Engine
- valor;
- R$/km;
- R$/min;
- R$/h;
- classificação;
- quantidade de chamadas percebidas.

Comparar principalmente com o comportamento da 0.16, que foi a referência de desempenho recente.

### Exclusive
Quando aparecer:
- retirada faz sentido?
- destino faz sentido?
- Maps abre o lugar esperado?
- ETA combina com pickup + viagem?

### Radar
Quando houver vários cards:
- nenhum endereço pode passar de um card para outro;
- valor/métricas/contexto devem pertencer à mesma oferta.

Mistura entre cards = P1.

### HUD
- não pisca na mesma oferta;
- não bloqueia toque;
- pode ser arrastado;
- não fica aumentando;
- não reduz perceptivelmente a velocidade de leitura.

### Jornada
Testar em momento seguro:
- pausar;
- retomar;
- marcar `Estou fazendo`;
- finalizar uma corrida;
- marcar uma como não realizada;
- corrigir pelo Histórico.

## Exposição e estatística

Em algum momento:
- ficar disponível por alguns minutos;
- receber uma oferta;
- depois conferir se a 0.19 detectou exposição fechada.

Se a região tiver pouca amostra:
- deve continuar em `Dados insuficientes`;
- não aceitar percentual apenas porque houve algumas ofertas.

## Importação

Com o carro parado:

1. importar um screenshot antigo;
2. conferir a oferta criada;
3. importar o mesmo arquivo novamente;
4. confirmar que a duplicata foi evitada.

A imagem bruta não deve ser enviada ao servidor por padrão.

## Custos

Configurar um exemplo conhecido.

Conferir:

`combustível/energia por km`

+

`custos mensais ÷ km/mês`

=

`custo operacional estimado/km`

Depois conferir uma oferta:

`km totais × custo/km = custo est.`

`valor da oferta - custo est. = Lucro est.*`

Abrir `Ver memória do cálculo`.

## Offline

Em teste intencional e seguro:

1. ficar offline;
2. gerar dados;
3. reconectar;
4. abrir validação 0.19;
5. tocar `Sincronizar agora`;
6. conferir se as filas voltam a zero.

## Sessão longa

Meta:
- pelo menos 30 minutos.

Ao terminar:
- abrir a validação 0.19;
- tocar `Encerrar medição`;
- compartilhar relatório se houver lentidão, aquecimento ou perda de chamadas.

## Classificação de gravidade

### P0
- crash repetitivo que impede uso;
- perda/corrupção severa de dados;
- comportamento inseguro que bloqueia o aparelho.

### P1
- perda sistemática de ofertas;
- Radar mistura cards;
- números financeiros errados;
- jornada/exposição produz estado incorreto de forma recorrente;
- sync duplica/perde dados de forma reproduzível.

### P2
- erro visual ou funcional contornável;
- geocoding pontualmente não resolvido;
- inconsistência que não altera a decisão financeira.

### P3
- melhoria;
- acabamento;
- texto;
- preferência.

## Saída da 0.19

Só considerar o núcleo encerrado quando:

- zero P0 conhecido;
- zero P1 conhecido;
- Offer Engine sem regressão;
- Radar sem mistura;
- importação deduplicando;
- exposição gerando denominador;
- estatística sem percentual falso;
- bateria/CPU aceitáveis na prática;
- sync confiável.
