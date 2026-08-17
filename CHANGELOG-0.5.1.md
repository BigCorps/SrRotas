# Changelog — Sr. Rotas 0.5.1 Alpha

## Performance

- intervalo de amostragem visual de 1100 ms para 250 ms;
- somente um OCR por vez, mantendo no máximo o frame alterado mais recente em espera;
- detector de mudanças usa média + células localmente alteradas;
- HUD do melhor card é exibido antes de persistir todo o lote Radar;
- logs de duplicatas são agrupados para reduzir I/O;
- Acessibilidade sai imediatamente quando MediaProjection está ativa.

## Parser

- leitura com apenas um par tempo/distância é rejeitada quando diverge mais de 15% do R$/km anunciado;
- divergências gerais acima de 22% do R$/km anunciado são rejeitadas;
- correção específica dos casos reais R$17,99 incompleto e R$23,64 incompleto;
- filtro de linhas compactas de overlays externos e resumo `1h19m - 13.90km`;
- `parser_version = sr-rotas-v0.5.1`.

## Dedupe

- avaliação do passageiro, service type, offer type e R$/km anunciado deixam de definir identidade;
- chave local usa tarifa + geometria;
- chave de backend segue a mesma filosofia dentro da janela temporal.

## Diagnóstico / privacidade

- Home e contexto desconhecido não salvam OCR bruto a cada frame;
- apenas candidatos de oferta que falharam no parser mantêm diagnóstico bruto local para calibração;
- frames rejeitados geram somente log resumido e limitado.
