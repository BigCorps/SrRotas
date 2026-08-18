# Changelog — Sr. Rotas 0.10.0 Alpha

## Banco Inter BigCorps
- removida completamente a proposta OpenPix;
- mesma ponte usada pelo MonitorIA (`inter.btsolucao.com.br`);
- geração em `/cob.php`;
- consulta por `txid` em `/get.php`;
- Edge Functions isolam a credencial bancária.

## Billing
- R$ 9,90 / 30 dias;
- Pix reutiliza cobrança pendente válida;
- valida txid, valor pago e status do banco;
- divergência vai para `manual_review`;
- processamento individual e em lote.

## Créditos
- 20 iniciais apenas na primeira ativação paga;
- ledger reserva/consome/estorna;
- MCP e analytics não consomem IA própria.

## Alpha
`BILLING_ENFORCEMENT=false` mantém os testes atuais livres de cobrança obrigatória.
