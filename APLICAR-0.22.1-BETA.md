# Sr. Rotas 0.22.1-beta — Patch de validação de campo

Base esperada: **0.22.0-beta / versionCode 34**  
Base analisada: commit `bcf50931922ed413918cf11c2431e9c7ff11a393`  
Nova versão: **0.22.1-beta / versionCode 35**

## Como aplicar

Este ZIP é incremental. Extraia/copie o conteúdo **na raiz do repositório SrRotas**, preservando os caminhos e substituindo os arquivos existentes quando solicitado pelo GitHub. Os arquivos novos devem ser criados nos mesmos caminhos presentes no ZIP.

Depois de aplicar, gere um novo APK de teste pela rotina Android já usada no projeto. Não há SQL, migration, Edge Function, variável da Vercel ou alteração de Supabase necessária nesta etapa.

## O que esta versão corrige

1. **HUD em tablet:** largura máxima física de 220 dp (Compacto), 260 dp (Normal) e 300 dp (Grande), sem crescer proporcionalmente em telas grandes.
2. **Oferta dispensada:** o toque para fechar silencia somente a oferta atual por identidade financeira/geométrica; pequenas oscilações do OCR não fazem o mesmo card reaparecer, mas uma corrida diferente libera o HUD imediatamente.
3. **Busca sem veto:** Busca deixa de transformar sozinha todo o HUD em vermelho. Ela participa da média ponderada como uma das métricas habilitadas.
4. **Cor por indicador:** R$/min, R$/km, Avaliação, R$/h, lucro, margem e Busca recebem avaliação própria verde/amarela/vermelha.
5. **Compacto somente indicadores:** nova opção local que remove o fundo geral/cabeçalho do HUD compacto e exibe até quatro indicadores em boxes individuais.
6. **Tela Agora:** o card de jornada acompanha o estado local e muda para **Iniciar nova jornada** após o encerramento, sem depender da janela flutuante.
7. **Reinício de jornada:** uma sessão antiga de MediaProjection não pode mais encerrar/desativar uma jornada nova que já tenha começado.
8. **Sr. Rotas aberto:** a presença da Activity do Sr. Rotas não pausa mais todo o OCR. O roteador descarta a própria interface quando ela é a única coisa relevante, mas ainda reconhece Uber/99 em outra janela do tablet.
9. **Ícone:** adaptive icon passa a usar uma camada segura com inset do foreground original. O personagem não foi redesenhado nem substituído.
10. **Tela dividida:** Uber/99 são analisados em clusters espaciais ao redor do valor da oferta. Com Waze/Maps presentes, o recorte horizontal fica mais restrito e o Uber exige marcador explícito de card.
11. **HUD normal/grande enxuto:** removidos do card os quilômetros/tempos repetidos da oferta. O valor da corrida passa a ser opcional e vem desativado por padrão.
12. **Aba lateral removida:** o traço/linha lateral com pontos foi retirado. O gesto de segurar e arrastar por qualquer parte do HUD foi preservado.
13. **Endereços:** retirada e destino passam a ser vinculados às respectivas linhas de geometria dentro do mesmo card/coluna OCR; o mesmo contexto alimenta HUD/menu, Maps e notificações.

## Opções novas do HUD

Abra **Configurações → Estratégia e HUD → Personalizar métricas e Painel de Rota**. Nesta tela foram adicionadas:

- `Compacto: mostrar somente os indicadores`
- `Mostrar valor da oferta no HUD`

As preferências são locais e independentes das configurações antigas, então a atualização não apaga metas, ordem de indicadores, tema, tamanho, posição ou conta já configurada.

## Teste de campo recomendado

Validar pelo menos estes cenários antes de promover para RC:

- Uber Exclusive e Radar em celular;
- 99 com distâncias em metros e km;
- fechar manualmente um HUD e aguardar o mesmo card continuar tocando;
- receber a próxima oferta logo após dispensar a anterior;
- Busca vermelha + demais métricas verdes;
- HUD Compacto / Normal / Grande em celular e tablet;
- Compacto com opção “somente indicadores” ligada/desligada;
- encerrar pela tela Agora e iniciar outra jornada;
- oferta imediatamente após reiniciar a jornada;
- Sr. Rotas + Uber/99 em tela dividida;
- Waze + Uber + Google Maps ao mesmo tempo, durante uma corrida e durante uma oferta real;
- conferir retirada/destino na bolha e notificação;
- conferir o ícone no launcher do tablet após reinstalar/atualizar o APK.

## Validações feitas neste pacote

- XML dos três resources modificados validado por parser XML.
- Helpers puros de avaliação ponderada e supressão de oferta compilados com Kotlin local e exercitados por teste de execução.
- Fixture de contexto da oferta 99 validou associação `Rua Prof. Atílio Innocenti...` → `Rua Gomes de Carvalho...`.
- Fixture de 99 permanece no teste automatizado com `R$25,00`, `591 m + 2 km`, `R$9,65/km`, `R$53,57/h`, avaliação `4,81` e `99plus`.

> Observação: neste ambiente não foi possível executar o Gradle Android completo com SDK/dependências do repositório. O pacote inclui os testes de unidade e foi revisado estaticamente, mas o APK gerado pelo workflow deve ser a validação final antes do 1.0.0-rc1.
