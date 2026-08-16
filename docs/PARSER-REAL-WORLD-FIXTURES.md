# Parser 0.4 — casos reais de calibração

A calibração 0.4 foi guiada por vídeos, screenshots e logs do Alpha em Uber Driver no Brasil.

## Regras acrescentadas

- uma oferta precisa ter preço principal + geometria de viagem + âncora de card;
- `Registro de viagens`, `Tendências de ganhos`, `Você está online` e `Procurando viagens` não são ofertas por si só;
- `+R$ ...` é ignorado como preço principal;
- `R$ .../km aprox.` é usado como validação, não como preço;
- faixas de mapa como `1-4 min` não entram no tempo da oferta;
- `Radar de Viagens`/`Selecionar` sinaliza Radar;
- `Exclusivo`/`Aceitar` sinaliza oferta exclusiva;
- Priority, UberX, Comfort, Black e Moto são reconhecidos como tipo de serviço quando o texto estiver disponível;
- se o R$/km calculado divergir mais de 40% do valor aproximado exibido pela plataforma, a leitura é descartada.

## Casos negativos preservados

Os falsos positivos observados no 0.2/0.3 são úteis como regressão e não devem voltar a ser enviados ao backend.
