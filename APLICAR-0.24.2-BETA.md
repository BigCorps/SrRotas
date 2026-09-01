# Sr. Rotas 0.24.2-beta — Estabilização e Integridade

Base: `92f2bfd0aa857dce393095197f261882a0c04a49` (0.24.1-beta validada em campo).

## Esta rodada corrige

- ícone adaptável: remove o preto que estava gravado no próprio raster do foreground;
- campos numéricos: rótulo/métrica/unidade permanecem visíveis durante o preenchimento;
- janela flutuante pequena/média: BUSCAR + DESTINO em duas linhas, sem esmagar 3 botões;
- endereço: bloqueio de texto de interface e quarentena de geocode geograficamente incompatível;
- Histórico: endereço só aparece como confirmado quando há coordenada válida;
- Histórico: comparação e gráficos passam a recolher/expandir;
- Base Coletiva: borda em degradê reforçada no Agora e Inteligência Coletiva no Histórico;
- estados pronto/pendente: fundo + borda verde/amarela/vermelha compartilhados;
- Agora no rodapé: continua destacado em azul mesmo inativo.

## Não entra ainda

A quilometragem inicial/final da jornada e a navegação Waze ficam para a próxima fase porque a primeira exige persistência/sincronização de jornada e a segunda merece usar coordenada/centroide confiável, não um texto de bairro inventado.

Sem migration Supabase nesta etapa.
