# QA — Sr. Rotas 0.33.6-field / versionCode 83

## Gate
Só instalar no aparelho do tester depois de a Action do commit terminar SUCCESS.
O Admin P5-03 já deve estar acessível em `/admin`.

## Objetivo da rodada
Responder objetivamente duas perguntas:
1. O Assistente Ativo chegou à janela de avaliação e, se não mostrou o balão, em qual etapa parou?
2. Odômetro e energia realmente sincronizam e aparecem no Admin?

## Roteiro do tester
1. Atualizar por cima da 0.33.5, sem limpar dados.
2. Confirmar em Configurações que `Assistente ativo` está ATIVO.
3. Iniciar uma jornada normalmente e manter a captura autorizada.
4. Informar o odômetro inicial se o fluxo solicitar.
5. Trabalhar normalmente. Se houver pelo menos 10 minutos sem nova oferta, observar se aparece o balão com o cabeçalho `SR • ASSISTENTE ATIVO`.
6. Se o balão aparecer, em uma ocorrência tocar `VER` e confirmar que abre `Agora` sem marcar corrida. Em outra ocorrência, se houver, pode usar `IGNORAR`.
7. Durante ou ao fim da jornada, registrar um abastecimento/energia pelo fluxo normal do app, se aplicável.
8. Encerrar a jornada e informar o odômetro final quando o fluxo oferecer.
9. Abrir `Configurações > Diagnóstico de leitura` e compartilhar o JSON.

## Conferência no Admin
No `/admin`, abrir o motorista do tester e conferir:
- aparelho com contato recente;
- jornada e última oferta coerentes;
- ofertas das últimas 24h;
- `vehicle_metrics` após odômetro;
- `energy_entries` após o lançamento de energia/combustível.

## Bloco obrigatório do JSON
`active_assistant_0336`.

Leitura:
- `evaluation_episodes = 0`: o motor não chegou à janela de avaliação;
- `evaluation_episodes > 0` e `suggestion_committed_episodes = 0`: avaliou, mas não encontrou sugestão elegível;
- `suggestion_committed_episodes > 0` e `overlay_seen_episodes = 0`: investigar camada de overlay;
- `overlay_seen_episodes > 0`: o overlay foi criado;
- `decorated_episodes > 0`: o balão 0.33.6 foi aplicado;
- `view_clicked` / `ignore_clicked` confirmam interação.

## Contratos que não mudaram
- M1 continua oficial.
- Reader2 continua sem persistência/HUD/backend/admissão oficiais.
- Histórico Android continua congelado.
- O Assistente continua determinístico; nenhuma chamada OpenAI foi adicionada.
- Nenhum dado sensível novo é exportado no bloco do Assistente.
