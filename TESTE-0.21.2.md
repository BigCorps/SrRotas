# TESTE DE CAMPO — Sr. Rotas 0.21.2-beta

Instale **por cima da 0.21.1**, sem limpar dados.

## P0 — leitura e duplicação

### 1. Compartilhar somente Uber

1. Inicie uma jornada.
2. No seletor de MediaProjection, escolha compartilhar **somente o aplicativo Uber**.
3. Abra ofertas Exclusive e Radar reais.
4. Compare a leitura com uma segunda rodada usando **Tela inteira**.

Esperado:

- ofertas reconhecidas nos dois modos;
- sem achatamento/corte causado por dimensões incorretas;
- velocidade próxima da referência 0.20.3/0.21.1.

### 2. Anti-loop da bolha

1. Aguarde uma oferta válida aparecer.
2. Expanda e recolha a oferta várias vezes na bolha.
3. Mantenha o card aberto por alguns segundos.

Esperado:

- a oferta não é computada novamente;
- contador/histórico não aumenta só por abrir a bolha;
- novas ofertas reais continuam sendo lidas.

### 3. Anti-loop do Histórico

1. Com jornada/captura ativa, abra Histórico.
2. Role por ofertas antigas.
3. Volte ao Uber.

Esperado:

- nenhum card do Histórico vira nova oferta;
- ao voltar ao Uber, OCR retoma normalmente.

## Estratégia

### 4. Perfis

Toque em Popular, Conforto e Premium.

Esperado:

- perfil selecionado permanece destacado com ✓;
- resumo muda imediatamente;
- tela detalhada mostra os valores aprovados:

| Perfil | R$/km | R$/min | R$/h |
|---|---|---|---|
| Popular | 1,20 → 1,50 | 0,40 → 0,50 | 24 → 30 |
| Conforto | 1,50 → 1,80 | 0,50 → 0,65 | 30 → 39 |
| Premium | 1,80 → 2,20 | 0,65 → 0,85 | 39 → 51 |

### 5. Busca

Em Configurações → Estratégia/HUD:

- localizar seção **Busca**;
- alterar km e minutos;
- salvar e reabrir.

Esperado: valores persistem e `Outros limites` fica separado.

## HUD

### 6. Selo Busca

Habilite a métrica Busca.

Esperado:

- `Busca Boa` verde;
- `Busca Média` amarela;
- `Busca Alta` vermelha;
- nenhuma linha separada `Tempo para buscar` / `Distância para buscar` no HUD.

### 7. Tamanhos

Compare Compacto, Normal e Grande.

Esperado:

- Compacto claramente menor;
- Normal significativamente menor que Grande;
- Grande comporta mais métricas/detalhes.

### 8. Borda

Teste ofertas boa/intermediária/ruim.

Esperado: borda do card visivelmente mais grossa e verde/amarela/vermelha conforme o estado geral.

## Navegação e visual

### 9. Voltar fixo

Abra Estratégia, Estratégia/HUD e Meus custos; role até o meio.

Esperado:

- `‹` continua visível no topo;
- saída de tela editável avisa sobre alterações não salvas.

### 10. Telas principais

Confira Agora, Histórico, IA e Configurações.

Esperado:

- sem parágrafos descritivos permanentes no cabeçalho;
- Agora possui `?` para explicação sob demanda;
- sem repetição `Agora / Agora` no topo.

### 11. Ícone do aplicativo

Conferir launcher em pelo menos dois aparelhos/launchers, se possível.

Esperado:

- sem borda preta artificial;
- sem corte novo do personagem;
- bolha continua usando o ícone exclusivo da 0.21.1.

## Rotas

### 12. Combinado

Em uma oferta com busca e destino identificados:

1. expanda o card;
2. toque `COMBINADO · MAPS`.

Esperado: Google Maps abre rota da posição atual → busca → destino final.

Se busca ou destino não estiverem identificados, o botão deve ficar indisponível/ausente, sem inventar endereço.

## Inteligência regional

### 13. Horário dos cards Agora

Abra Agora em horários diferentes.

Esperado: cada card mostra `Base: N rotas · Horário analisado: Xh–Yh`, usando a janela de 3 h do agregado (ex.: 12h–15h).

## Regressão obrigatória

Confirmar também:

```text
[ ] ✓ de relatório não pausa OCR/jornada
[ ] P10/continuidade no destino continua aparecendo
[ ] sync/quarentena continua sem regressão
[ ] mapa Busca/Destino continua funcionando
[ ] tema Claro/Escuro/Automático continua funcionando
[ ] Agora/Hoje/Semana/Pesquisa continuam funcionando
[ ] sem importação de screenshot na UI normal
[ ] zero crash
[ ] zero P0
[ ] zero P1
```
