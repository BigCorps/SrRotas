# QA — Sr. Rotas 0.31.1 Field · Capture Resilience

## Objetivo

Validar que a captura pode cair e voltar **sem encerrar nem recriar a jornada**.

## Instalação

- instalar por cima da versão atual;
- não limpar dados;
- confirmar `0.31.1-field` / `versionCode 74`;
- M1 continua oficial; Reader 2 continua paralelo.

## Teste A — interrupção real da captura

1. iniciar uma jornada normalmente;
2. anotar os 8 primeiros caracteres da jornada mostrados no diagnóstico;
3. dirigir/testar normalmente por alguns minutos;
4. encerrar a captura pelo controle de compartilhamento do Android, sem encerrar a jornada no Sr. Rotas;
5. voltar ao Sr. Rotas.

Esperado:

- jornada continua ativa;
- não é criada nova jornada;
- tela Agora mostra `Captura interrompida · jornada preservada`;
- aparece o botão `Retomar captura`;
- a notificação também oferece `Retomar captura`;
- nenhuma nova oferta é computada enquanto não houver captura.

## Teste B — retomada

1. tocar `Retomar captura`;
2. autorizar novamente a captura na janela do Android;
3. voltar ao Uber/99;
4. aguardar novas ofertas.

Esperado:

- a mesma jornada continua aberta;
- OCR volta a processar frames;
- novas ofertas voltam a aparecer;
- Histórico anterior à interrupção permanece intacto;
- não é necessário encerrar/iniciar jornada.

## Teste C — cancelar autorização

Com a captura interrompida:

1. tocar `Retomar captura`;
2. cancelar a janela do Android.

Esperado:

- jornada continua aberta;
- captura continua marcada como interrompida;
- botão `Retomar captura` continua disponível;
- nenhuma jornada nova é criada.

Depois repetir e autorizar para confirmar recuperação.

## Teste D — duração

Fazer pelo menos uma jornada de **90+ minutos**, idealmente 2 horas.

Se a captura cair sozinha, não encerrar a jornada: usar apenas `Retomar captura` e registrar horário aproximado.

## Reader 2

Durante o teste, observar normalmente. Não promover nem selecionar Reader 2 como oficial.

No diagnóstico, `reader2_parallel_031` deve continuar com:

- `second_ocr=false`;
- `official_persistence=false`;
- `backend_effect=false`;
- `hud_effect=false`;
- `admission_influence=false`.

## Diagnóstico obrigatório

Exportar no fim da jornada e conferir `capture_resilience_0311`.

Após pelo menos uma interrupção + retomada esperada:

- `interruptions >= 1`;
- `resume_requested >= 1`;
- `resume_authorized >= 1`;
- `resume_success >= 1`;
- `pending_recovery=false` após captura voltar;
- `total_interruption_ms > 0`.

Se cancelar uma tentativa:

- `resume_cancelled >= 1`.

## Regressões P1

Parar e reportar se ocorrer qualquer um:

- retomada cria uma nova jornada;
- Histórico some ou duplica;
- captura volta, mas novas ofertas não são processadas;
- jornada é encerrada automaticamente quando a captura cai;
- Reader 2 passa a interferir no HUD/banco;
- Turbo Mais volta a ser tratado como tarifa principal.

## Enviar

- diagnóstico final;
- duração total da jornada;
- quantas vezes a captura caiu;
- quantas retomadas funcionaram;
- se a `journey_id` permaneceu a mesma;
- modelo/aparelho e Android;
- horário aproximado de qualquer falha.
