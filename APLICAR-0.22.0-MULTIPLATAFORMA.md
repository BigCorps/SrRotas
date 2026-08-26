# Aplicar — Sr. Rotas 0.22.0-beta

Este ZIP é **incremental** e foi montado para ser aplicado por cima do repositório atual.

## Como aplicar no GitHub online

1. Abra o repositório `BigCorps/SrRotas` na branch `main`.
2. Extraia este ZIP no computador.
3. Faça upload do conteúdo extraído na raiz do repositório, mantendo exatamente as pastas.
4. Ao GitHub perguntar por arquivos existentes, substitua-os pelos arquivos do pacote.
5. Faça um único commit, por exemplo: `0.22.0-beta: multiplataforma real e acabamento UX`.
6. Aguarde o GitHub Actions terminar.
7. Gere/instale o APK 0.22.0-beta por cima da versão atual, sem limpar os dados do app.

## Não executar

- Não há SQL para executar.
- Não há migration do Supabase.
- Não é necessário alterar variáveis da Vercel.
- Não apague histórico/SQLite antes do teste.

## Teste curto obrigatório antes da RC

### 1. Regressão Uber

- Inicie uma jornada.
- Abra o Uber e receba pelo menos 3 ofertas, incluindo Radar se possível.
- Confirme que valor, km, minutos, HUD, voz, menu flutuante e persistência continuam iguais à versão validada.

### 2. 99

- Sem encerrar a jornada, troque do Uber para o 99.
- Receba uma oferta do 99.
- Confira se o card aparece no Sr. Rotas.
- Em ofertas semelhantes ao fixture real, validar:
  - `R$25,00` como tarifa;
  - `591 m` como aproximadamente `0,59 km` de busca;
  - `2 km` de viagem;
  - total aproximado `2,59 km`;
  - `28 min` no total;
  - categoria `99Plus` quando vier `Plus Nova`;
  - avaliação `4,81` quando disponível.

### 3. Alternância na mesma jornada

- Uber → 99 → Uber sem encerrar a jornada.
- Confirmar que o OCR continua ativo nas três etapas.
- Confirmar que uma oferta do 99 não é deduplicada/mesclada com uma oferta Uber de valores parecidos.

### 4. Endereços

- Em oferta nova, abrir a janela flutuante.
- Confirmar Embarque e Destino reais.
- Abrir Histórico e confirmar os mesmos endereços na oferta recente.
- Confirmar que textos como `Você está online`, `Para onde?`, `Como foi a viagem?` ou `Isso é tudo por enquanto` não aparecem como endereço.

### 5. Veredito ponderado do HUD

- Configure 3 métricas no HUD.
- Deixe apenas uma delas abaixo da faixa ruim e as outras duas boas.
- A oferta não deve virar automaticamente `Abaixo da meta` apenas por essa métrica.
- Inverta a ordem das métricas e confirme que a primeira posição tem mais influência na classificação.

### 6. Tema

- Configurações → Aparência deve ser a primeira seção.
- Teste Automático, Claro e Escuro.
- Confirme a mudança nas quatro abas do aplicativo.

### 7. UX

- Confirmar que não aparecem cabeçalhos grandes repetidos com os nomes `Agora`, `Histórico`, `IA` e `Configurações`.
- Confirmar que Configurações está separada pelos departamentos definidos na 0.22.

## Critério para avançar

Se Uber continuar sem regressão e o 99 passar pelo menos 5 ofertas reais com leitura correta de valor, distância, tempo e endereço, esta versão pode seguir para a etapa RC da 1.0.0.
