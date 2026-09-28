# QA — Sr. Rotas 0.33.8-field / versionCode 85

## Objetivo
Validar a correção dos 6 exposures legados presos e encerrar o crash antigo pendente.

## Instalação
1. Instalar a 0.33.8 por cima da 0.33.7.
2. NÃO desinstalar.
3. NÃO limpar dados.
4. Confirmar `0.33.8-field / versionCode 85`.
5. Manter M1 oficial. Não mexer no Reader2.

## Teste A — fila de exposures
No SM-X626B, abrir o app com internet e aguardar 30–60 s.
Se houver jornada ativa, pode continuar usando normalmente.
Depois exportar o JSON.

Esperado em `sync`:
- os 6 registros antigos não ficam mais como pending permanente.

Esperado em `exposure_queue_repair_0338`:
- `runs >= 1`;
- `quarantined_total >= 1`;
- neste aparelho, expectativa principal: `quarantined_total = 6`;
- `deletes_local_rows = false`;
- nenhum id/cell/coordenada exportado.

Esperado em `field_validation_019.facts`:
- `quarantined_exposures` reflete os registros preservados.

Importante: se uma exposure nova acabou de fechar exatamente no momento do JSON, `pending_exposures` pode aparecer transitoriamente >0. O critério é que os mesmos 6 não sejam reenviados em ciclos sucessivos e que a Vercel pare de mostrar o padrão de seis HTTP 400 repetidos.

## Teste B — exposures novas
Continuar uma jornada por alguns minutos e observar ofertas/localização.
Novas exposures devem continuar chegando normalmente ao backend.
Não pode haver perda de ofertas nem alteração do HUD.

## Teste C — crash legado
Com internet e conta conectada, aguardar o Access Resolver concluir e exportar novo JSON.

Esperado:
- `crash_observability_0332.pending = false`.

No Admin:
- abrir `/admin/diagnosticos`;
- procurar o crash antigo do SM-X626B;
- versão original esperada: `0.27.0-rc3.7-consolidation-field / vc66`;
- exceção esperada: `java.lang.IllegalStateException`.

## Não testar/reabrir
- fórmula Money;
- Reader2 Primary;
- Histórico;
- V7;
- mudanças de layout não relacionadas.

## Retorno
Enviar o JSON novo do SM-X626B. O segundo aparelho só precisa novo JSON se aparecer algum comportamento diferente.
