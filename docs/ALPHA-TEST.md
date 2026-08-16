# Roteiro de teste — Alpha

## Teste atual do Uber

Não interrompa um teste da versão 0.2 apenas para instalar esta atualização. Primeiro recolha os resultados reais de captura/OCR/HUD.

Quando instalar 0.3:

1. Abra o Sr. Rotas e confira a URL do backend.
2. Pareie o aparelho.
3. Autorize HUD e notificações quando solicitado.
4. Marque o consentimento e inicie a jornada.
5. Autorize compartilhamento da tela inteira.
6. Abra o Uber Driver e observe de 5 a 10 ofertas, sem necessidade de aceitá-las.
7. Compare valor, km, tempo e indicadores com o card do Uber.
8. Ao encontrar erro, use **Compartilhar diagnóstico**.
9. Encerre a jornada e confirme que o resumo local foi preservado.
10. Desligue a internet em um teste controlado, observe uma oferta e depois religue; use **Sincronizar pendências** para validar a fila offline.

## Resultados que interessam

- captura funciona ou fica vazia/preta;
- OCR reconhece os números corretos;
- parser separa retirada/viagem/tempo corretamente;
- HUD aparece no momento correto;
- Radar e oferta exclusiva são distinguidos;
- modelo do aparelho e versão do Android;
- diagnóstico textual quando houver divergência.
