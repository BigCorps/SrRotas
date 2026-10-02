# vc87 — correção de compilação do teste

Base: `5e067067e979ac3dbc0841eb81584acce8ac97d9`

O código Android principal já compilou.
A falha ficou em `CommercialModelGate5ContractTest.kt`, linha 52.

Erro:
- `Unresolved reference 'Base'`
- `Unresolved reference 'pessoal'`
- `Syntax error: Expecting ','`

Causa:
A string de teste que procurava `Base pessoal` tinha aspas internas sem escape.

Correção:
A verificação foi dividida em dois `contains`, evitando qualquer literal aninhado.

Nenhum arquivo de produção foi alterado.
