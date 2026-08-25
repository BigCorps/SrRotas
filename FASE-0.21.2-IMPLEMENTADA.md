# FASE 0.21.2 — CORREÇÕES DE CAMPO IMPLEMENTADAS

## Baseline

```text
Base: 0.21.1-beta
Commit base: 89ff22aa6a71f8cb2a79d8bb32bacc7cf59dc5d4
Nova versão: 0.21.2-beta
versionCode: 33
```

O objetivo desta rodada é corrigir o relatório de campo da 0.21.1 sem reabrir o Offer Engine validado.

## 1. Captura somente do Uber

No Android 14+, a área capturada quando o usuário compartilha **um único aplicativo** pode ter dimensões diferentes da tela física.

A 0.21.2 passa a ouvir o resize do conteúdo capturado e reconfigura `ImageReader` + `VirtualDisplay` para as dimensões reais. O parser, sampling e estabilização permanecem intactos.

## 2. Anti-loop do próprio Sr. Rotas

Três proteções trabalham juntas:

1. bolha/menu flutuante usa `FLAG_SECURE`;
2. HUD financeiro usa `FLAG_SECURE`;
3. `OwnUiCaptureGuard0212` suspende processamento OCR enquanto qualquer Activity do Sr. Rotas está em primeiro plano.

`UberScreenGate` continua como defesa textual adicional. Abrir Histórico, Configurações ou expandir a própria bolha não deve gerar ofertas novas.

## 3. Perfis de serviço

- Popular;
- Conforto;
- Premium;
- Personalizado.

O perfil ativo recebe destaque visual com ✓ e botão primário. Ao tocar em um preset, os thresholds financeiros aprovados são aplicados imediatamente e a seleção permanece registrada.

Os limites de Busca continuam independentes do preset.

## 4. Busca dentro da Estratégia

A configuração passa a ter uma seção explícita **Busca**:

- distância máxima para busca — km;
- tempo máximo para busca — min.

`0` desativa o respectivo limite.

O antigo título ambíguo `Limites adicionais` foi substituído por `Outros limites` para valor mínimo e lucro mínimo.

## 5–6. HUD de Busca

No HUD não são exibidas linhas separadas de tempo e distância para busca.

A apresentação é compacta:

```text
Busca Boa   → verde
Busca Média → amarelo
Busca Alta  → vermelho
```

Os valores km/min continuam disponíveis na configuração e podem ser usados pelo cálculo.

## 7. Tamanhos do HUD

A densidade visual foi separada de forma mais clara:

- Compacto: largura ~218 dp, até 2 métricas essenciais;
- Normal: largura ~258 dp, até 4 métricas;
- Grande: largura ~338 dp, até 6 métricas e mais detalhes.

## 8. Voltar fixo

Telas longas de configuração recebem um botão `‹` fixo no topo. Em telas com edição, a saída informa que alterações não salvas, se houver, serão descartadas.

## 9. Limpeza das telas principais

Agora, Histórico, IA e Configurações deixam de repetir textos descritivos permanentes no topo.

Agora mantém um `?` compacto com explicação sob demanda. O modo imediato aparece como `Momento`, evitando repetir `Agora` dentro da própria tela.

## 10. Adaptive icon

A arte do launcher continua a anterior. Foi removido apenas o fundo preto fixo do adaptive icon, substituído por fundo creme da identidade Sr. Rotas. A arte foreground e os launchers legados permanecem preservados.

O ícone exclusivo da bolha/menu continua sendo `srrotas_bubble_icon.png`.

## 11. Borda do HUD

A borda principal passa a usar 3 dp e a cor acompanha o verdict geral:

- boa → verde;
- intermediária → amarelo;
- ruim → vermelho.

O modo daltonismo continua respeitado.

## 12. Percurso combinado

Mantidos os botões existentes de Busca e Destino.

Quando ambos os pontos estão disponíveis, aparece também:

```text
COMBINADO · MAPS
```

A URL do Google Maps é montada como:

```text
origem = localização atual do aparelho
waypoint = busca do passageiro
destino = destino final
```

O Waze não é usado para esta opção porque o deep link público não oferece montagem prévia oficial de duas paradas.

## 13. Horário analisado em Agora

Cada card regional mostra a janela real representada pelo agregado. O backend trabalha com buckets de 3 horas, portanto o app exibe, por exemplo:

```text
Base Sr. Rotas: 100 rotas · Horário analisado: 12h–15h
```

Não é inventada uma janela móvel de 90 minutos se os dados foram agregados em 3 horas.

## Continuidade no destino

A funcionalidade P10/Alta-Média-Baixa adicionada no fechamento 0.21.1 permanece preservada e fora do hot-path do OCR.

## Banco

Nenhuma migration nova foi necessária.
