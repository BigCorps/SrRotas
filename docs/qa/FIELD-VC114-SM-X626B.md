# Homologação única vc114 — Samsung SM-X626B / Android 16

Candidata isolada da issue #18, PRs #13/#14/#17. Não é 1.0.0 homologada.
Usar **somente `app-release.apk`, `com.srrotas.app`, vc114 / `0.33.37-field`** do artifact
privado verificado. Certificado SHA-256 esperado:
`cc4476c4eb3ac725b692914488282f0ac70a83b3682ca6b7ac0f841f9de2117c`.
Hash exato do APK e commit constam no `release-verification.json` e `app-release.apk.sha256`
que acompanham a entrega. Não instalar debug, R3-Lab ou APK de outra execução.

Reserve 90–120 minutos, incluindo **mais de 30 minutos contínuos de leitura por leitor**.
Faça manipulações de permissões, menus e falhas com o veículo parado e em local seguro.
Não limpe dados nem desinstale o app para contornar falha de atualização. Se o Android recusar
assinatura/versão, pare e informe a mensagem. Não instalar o R3-Lab em paralelo nesta rodada.

## 1. Preparar e registrar a atualização

1. Na versão anterior, exportar diagnóstico pelo menu Bug/Configurações. Guardar privadamente;
   não publicar capturas de ofertas/endereço ou JSON sem revisão de privacidade.
2. Anotar versão anterior, Android 16/build do Samsung, hora, fuso, tema/fonte, permissões de
   notificações, sobreposição, localização e serviço de Acessibilidade habilitado. Registrar
   jornada/corrida abertas, totais/histórico e posição da janela antes da atualização.
3. Baixar o artifact privado, extrair e instalar `app-release.apk` **por cima**. Confirmar em
   Informações do app `0.33.37-field` / 114, mesma conta e dados. Permissões devem permanecer;
   se o Android pedir alguma novamente, conceder explicitamente e registrar.
4. A Acessibilidade deve ser do **Sr. Rotas principal**, não Sr. Rotas R3-Lab. Sem opção pelo R3,
   M1 continua disponível; não ativar modos M2/Comparar nesta homologação operacional.
5. Abrir Agora/Histórico/Radar; confirmar cabeçalho, navegação, dados anteriores e ausência de
   jornada nova criada pela atualização. Exportar diagnóstico inicial da vc114.

## 2. R3: leitura prolongada e recuperação na mesma jornada

1. Escolher R3 em Configurações › Leitor; aceitar aviso e ativar serviço principal, se necessário.
   Iniciar jornada pelo R3: não deve abrir MediaProjection. Se uma jornada anterior está aberta,
   recuperar a leitura dela; não encerrar nem criar outra só para restaurar o leitor.
2. Por **35 minutos ou mais**, observar ofertas reais Uber (99 somente se disponível) e anotar
   amostra conhecida: quantas foram exibidas e quais chegaram ao HUD/Histórico, seus valores,
   minutos, km, busca e destino. Captura ausente/incompleta deve ficar assim; nunca completar de cabeça.
3. Alternar Agora, Histórico e as duas abas do Radar, voltar ao app de motorista, recolher/expandir
   a bolha, apagar/acender a tela e retomar. Conferir uma bolha/HUD, posição preservada e sem ofertas
   duplicadas. Sem novos pedidos MediaProjection enquanto R3 saudável.
4. Com o veículo parado, desligar a Acessibilidade do Sr. Rotas durante a jornada. A UI/diagnóstico
   deve mostrar leitor indisponível/aguardando ação; verificar aviso após a janela de detecção, sem
   tempestade de notificações. Sem notificação permitida, o estado no Agora deve continuar acionável.
5. Tocar **Recuperar leitor desta jornada** › **Recuperar esta jornada com o M1**. O R3 da jornada
   deve parar antes de iniciar M1. O Android deve pedir **nova autorização de captura**.
6. Primeiro cancelar: a jornada deve continuar aberta, sem nova jornada, sem encerrar corrida
   nem iniciar M1. Reativar Acessibilidade/recuperar R3 se necessário e conferir o estado.
7. Repetir e autorizar M1. Durante o diálogo, girar o tablet/recriar a tela uma vez para testar
   restauração da tentativa. Aceito: mesmo ID/início de jornada e corrida atual, sem troca de leitor
   silenciosa, sem duas capturas/OCRs, sem reiniciar valores/totais. Anotar hora e exportar diagnóstico.

## 3. M1: leitura prolongada, interrupção e novo consentimento

1. Continuar na mesma jornada após fallback M1 por **35 minutos ou mais**, com amostra de ofertas
   reais e navegação Agora/Histórico/Radar. Conferir o fluxo oficial HUD → Histórico, sem duplicatas.
2. Usar o controle do Android para parar o compartilhamento/captura (não Encerrar jornada).
   O app deve informar interrupção, mantendo jornada/corrida/histórico e recuperação disponível.
3. Recuperar pelo Agora/menu de diagnóstico. Deve abrir o seletor Android de novo, nunca reutilizar
   token antigo. Cancelar uma vez, depois autorizar. Confirmar continuidade na mesma jornada.
4. Alternar a preferência R3/M1 enquanto a jornada está aberta: a preferência não deve apagar o
   fallback já escolhido nem iniciar dois leitores. Reativar R3 por ação explícita de recuperação,
   se elegível; não esperar que trocar preferência sozinho reutilize uma autorização M1.
5. Se houver leitor parado/OCR travado, exportar diagnóstico **antes** de reiniciar. Para OCR
   comprovadamente travado, usar a recuperação explícita orientada pelo app (reinício/Forçar parada),
   depois abrir novamente. A jornada deve continuar aberta. Não provocar OCR travado artificialmente
   no app de produção; registrar como não exercitado se não ocorrer.
6. Separar: remover dos recentes não garante morte de processo; Forçar parada impede serviços/notificações
   até abrir o app. Não contar ausência de aviso enquanto o app está forçadamente parado como falha.

## 4. Janela flutuante, concorrência e releitura

1. Recolher/expandir/arrastar bolha, trocar app e girar tablet. Não deve haver bolha duplicada,
   HUD duplicado, foco roubado ou perda de posição. Remoção real pelo sistema só pode ser homologada
   se ocorrer; não confundir recolher bolha com remoção pelo WindowManager.
2. Revogar sobreposição, observar explicação e conceder novamente. Voltar ao Agora/recuperação;
   conferir reanexação única. Ausência de permissão não pode ser tratada como janela exibida.
3. Sem corrida ativa, escolher uma imagem legítima em Foto/Rescan e pedir releitura. Com janela
   segura concedida, Reader pausa por no máximo 12 s e depois volta; nada deve confirmar corrida
   nem sobrescrever tarifa/endereço já existente sozinho. Se não puder conceder, mostrar motivo/pendência.
4. Durante corrida confirmada, releitura deve bloquear/adiar; não iniciar segundo OCR. Digitalizar
   Histórico com leitura oficial deve reutilizar os quadros existentes, sem segunda MediaProjection.
   Testar recusa de iniciar leitor durante OCR isolado ainda em voo.

## 5. Histórico: seleção e integridade dos registros

1. Abrir digitalização do Histórico real e rolar lentamente o suficiente para ler cards completos.
   Conferir horário, busca e destino; registrar campos ausentes separadamente da seleção visual.
2. Na Conferência, tocar em diferentes regiões do card inteiro (texto, espaço e borda): alternar
   selecionado/não selecionado, borda azul e descrição acessível. Não deve haver botão interno de seleção.
3. Incompletos não devem vir selecionados; selecionar um manualmente e remover outro completo.
   Confirmar que tocar no card **não grava nem confirma corrida**. Somente o botão Confirmar salva
   os escolhidos; importação não equivale a corrida concluída/receita.
4. Repetir a leitura de um card/rolagem e conferir dedupe. Canceladas e concluídas permanecem distintas;
   cauda de um card não pode contaminar outro. Não corrigir reconhecimento inserindo valores fictícios.
5. Voltar ao Agora e confirmar mesma jornada, mesmo estado do leitor e corrida atual.

## 6. Radar integrado, Maps e Diagnóstico

1. Sem corrida confirmada/ETA válida e sem preferência manual anterior, abrir **Eventos em geral**.
   Devem existir exatamente duas abas; escolha manual deve sobreviver à atualização/retomada.
2. Com localização permitida/recente, consultar dados reais: eventos ativos/aprovados, futuros e
   com saída não encerrada, por distância da referência mostrada. Se não houver dados, estado vazio
   é correto. Não usar os seis exemplos sintéticos das capturas como catálogo de produção.
3. Sem localização/permissão, mostrar motivo e não inventar distância. Testar rede desligada, erro,
   retorno online e Atualizar. Após expiração (eventos 5 min, destino 3 min), não tratar cache como atual.
4. Receber uma oferta sem aceitá-la: **Próximas do destino** não pode assumir esse destino. Marcar
   explicitamente **Estou nessa corrida**, com destino/ETA legítimos disponíveis; só então exibir
   destino, ETA/Corrida ativa e POIs reais. ETA vencida deve mostrar estado próprio, sem autoencerrar.
5. Trocar corrida/destino ou localização durante consulta: resposta antiga não pode aparecer como atual.
   Eventos em andamento podem ficar fora da aba geral pelo filtro aprovado de futuros; registrar
   ocorrência como limitação de produto, sem inferir horário de saída ou fabricar evento.
6. Tocar card e **Ver no Maps**: abrir Maps/navegador fora do app, coordenada/local correto; voltar
   preserva jornada/leitor/aba. Não deve haver mapa interno nem abertura automática.
7. Tocar **Diagnóstico**: abrir fluxo existente, gerar e compartilhar privadamente o JSON combinado.
   Cancelar compartilhamento não altera jornada. Verificar presença de `reader_health_113`,
   `reader_timeline_110`, `reader_evidence_111`, `journey_rescan_111` e `radar_contextual_v1` quando aplicáveis.
8. Testar claro/escuro, retrato/paisagem, tela dividida 50% e janela estreita, fonte 100%/150%.
   Tablet largo: duas colunas; estreito/fonte ampliada: uma quando necessário; rolagem e ações acessíveis.
9. Controle Field **Rollback visual** e **Visual duas abas** só trocam visual: não ativam/paralisam
   runtime/assistente, não mexem na jornada/leitor. Não ativar automações só para mostrar a UI nova.

## 7. Fechar a rodada e devolver evidência

Antes de encerrar a jornada: comparar ID/início, corrida atual, contagem de ofertas, duplicatas,
fontes e estado de captura com as anotações. Exportar diagnóstico final. Encerrar manualmente uma
única vez, confirmar odômetro se disponível, conferir totais/histórico e reabrir o app. Não promover
ofertas/importações a receita nem somar resumo Uber como corridas extras.

Para cada passo registrar **PASSOU / FALHOU / NÃO EXERCITADO**, hora, leitor, orientação/fonte e
reprodução mínima. Informar duração real por leitor, amostra exibida × persistida, duplicatas,
recuperações canceladas/autorizadas, continuidade de jornada, erros Maps/diagnóstico e consumo/temperatura.
Se falhar: exportar antes de recuperar quando possível; anotar se a coleta foi antes/depois da falha.
Compartilhar dados privadamente com o responsável; nenhuma mensagem é enviada automaticamente.

**Reprovação crítica:** perda/troca silenciosa de jornada, captura sem consentimento novo, dois OCRs,
HUD/janela duplicada, corrida confirmada sem ação, dados inventados, duplicação persistida, ausência
silenciosa de leitor recuperável ou APK incompatível. Interromper a homologação e informar evidência;
não apagar dados. Corrigir no branch integrado e repetir o trecho afetado antes de considerar merge.

## Pendências para 1.0.0

- Aprovação de campo desta integração (R3/M1 prolongado, recuperação/MediaProjection/overlay e OCR OEM),
  revisão cruzada do diff crítico e zero P0/P1. CI não comprova Android 16/ML Kit em aparelho.
- 99 com fixtures e uso real; cobertura/qualidade de OCR, Histórico e releitura em dados legítimos.
- Soak de bateria, memória, sync offline→online, crash e novo usuário ponta a ponta sem driver duplicado.
- Gates comerciais/segurança pendentes, verificação manual de Leaked Password Protection,
  políticas/declarações de Acessibilidade/MediaProjection/FGS/localização, Data Safety e Play Integrity.
- Assinatura/assetlinks/AAB final, privacidade/termos/suporte/exclusão e atualização sobre Field sem limpar dados.
- Decisão de temporalidade para eventos em andamento e ampliação legítima das fontes/POIs, separadas
  desta integração; não ativar coletor, aprovar registros nem mudar backend para preencher a tela.
- Merge e Play Store exigem autorização posterior. Esta entrega não executa nenhum dos dois.
