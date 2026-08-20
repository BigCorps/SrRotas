# Sr. Rotas 0.19 — Validação consolidada de campo

## Base

`40593bb8ab13e55615dae15d80fd1d6d550d6794`

## Estado observado antes do pacote

Leitura read-only em 20/08/2026:

- 915 ofertas remotas;
- 122 exposições;
- 122 outcomes;
- 91 ofertas com `destination_cell`;
- 890 ofertas com snapshot de custo;
- 122 exposições fechadas;
- 0 perfis de custo 0.18 configurados até o momento.

Isso prova que o backend está recebendo o núcleo operacional, mas não encerra os critérios manuais da 0.19.

## Vercel

A produção estava `READY` no commit base da 0.19 e sem erros/fatals encontrados na consulta da última hora.

## O que a 0.19 adiciona

### FieldValidationActivity
Tela específica para a rodada consolidada.

### Evidência automática
`FieldValidationCollector` inspeciona o banco local e os estados já produzidos pelo núcleo.

### Avaliação conservadora
`FieldValidationAssessment` usa:

- PASS;
- ATENÇÃO;
- FALHA;
- MANUAL.

Itens visualmente impossíveis de provar por SQL nunca viram PASS automáticos.

### Desempenho
`FieldValidationSession` mede:

- tempo;
- CPU do processo;
- PSS;
- bateria;
- temperatura;
- thermal status.

Se o processo reiniciar durante a medição, a sessão marca CPU como não comparável.

### Relatório
`FieldValidationReporter` gera JSON agregado sem dados sensíveis ou contexto textual exato.

### Diagnóstico v3
`DiagnosticBundle` passa a excluir por padrão:
- OCR bruto;
- log local;
- endereço;
- coordenadas exatas.

### Central do testador
Atualizada para 0.19 e continua opcional.

### Assinatura estável
Workflow de campo agora suporta keystore por GitHub Secrets e diferencia claramente fallback debug.

## Não há migration

0.19 reutiliza o modelo 0.14–0.18.

## Offer Engine

Permanece congelado.

Nenhum dos arquivos do Offer Engine numérico faz parte deste patch.

## Próximo passo

A 0.19 não termina no upload.

Ela termina depois da rodada real e da triagem:

1. CI verde;
2. APK distribuído;
3. campo;
4. dados/relatos cruzados;
5. correção de qualquer P0/P1;
6. validação novamente;
7. somente então iniciar bloco 1.0-A.
