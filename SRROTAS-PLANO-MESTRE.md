# Sr. Rotas — Plano Mestre de Continuidade

**Documento vivo obrigatório em todas as entregas futuras.**  
**Atualizado em:** 14/09/2026  
**Versão de código em preparação:** `0.27.0-rc3.4` (`versionCode 62`)  
**Base confirmada no GitHub:** `74ffaf777f84e1caf270962383ad4d31b389decd` — RC3.3.

> Regra de continuidade: todo ZIP de evolução do Sr. Rotas deve conter a versão atualizada deste arquivo. Nunca depender apenas do histórico da conversa para recuperar decisões, pendências ou critérios de aceite.

---

# ATUALIZAÇÃO RC3.4 — FECHAMENTO UBER / UX

## 0. Decisão estratégica consolidada

O objetivo central do Sr. Rotas não é apenas calcular se uma corrida parece boa ou ruim. A prioridade máxima é **catalogar corretamente os dados das ofertas** para alimentar uma inteligência histórica, temporal, regional, financeira e de fluxo cada vez mais confiável.

Princípio de produto:

> **O Sr. Rotas calcula. O motorista decide. A inteligência depende da qualidade da catalogação.**

A primeira versão pública terá **Uber como plataforma homologada de lançamento**. O suporte à **99 permanece em implementação contínua**, mas deixa de bloquear o lançamento inicial.

Estado de comunicação no produto:

```text
Uber · suportado no lançamento
99   · em implementação
```

Não remover o código da 99. Continuar coletando diagnóstico e evoluindo a leitura em paralelo, sem comprometer a estabilidade da Uber.

---

## 0.1 Evidência do diagnóstico RC3.3

Diagnóstico analisado da versão:

```text
versionName = 0.27.0-rc3.3
versionCode = 61
```

Janela local observada:

```text
ofertas = 1532
financeiramente completas = 1532
Uber = 1507
99 = 4
outros = 21
route_intelligence_ready = 1072
```

Jornada atual do diagnóstico:

```text
ofertas = 187
financeiramente completas = 187
Uber = 179
99 = 0
route_intelligence_ready = 93
```

Conclusão importante:

- HUD financeiramente completo **não significa** automaticamente oferta pronta para inteligência de rota;
- a próxima meta deve elevar qualidade geotemporal e de rota sem inventar campos ausentes;
- Uber está suficientemente madura para ser a referência de lançamento;
- 99 ainda não possui catalogação suficiente para ser considerada homologada.

Telemetria RC3.3 validada:

```text
failure reports exportados = 148
manuais = 28
automáticos = 120
limite de retenção = 200

shadow recovery:
submitted = 5
pass_attempts = 14
recovered_jobs = 1
recovered_offers = 1
exhausted_jobs = 3
```

Isso confirma que o diagnóstico ampliado e o shadow recovery estão operando e podem continuar sendo usados para evolução baseada em evidência.

---

## 0.2 Escopo oficial da RC3.4

Nome interno:

```text
RC3.4 — Fechamento Uber / UX
```

Objetivo: concluir os problemas de interface relatados após o teste RC3.3 **sem modificar agressivamente o reader Uber, parsers ou fórmulas financeiras**.

### Item 1 — Ícone de status

Problema:
- o card/status “Sr. Rotas está pronto” voltou a usar a arte antiga.

RC3.4:
- usar diretamente `sr0265_settings_ready` no reparo visual;
- reaplicar o recurso de forma idempotente quando `SettingsHub` for reconstruído;
- não depender de uma decoração aplicada apenas uma vez.

### Item 2 — Janela Flutuante independente

Problema:
- configurações da janela ficam dentro de “Configuração do HUD”.

RC3.4:
- criar tela independente `Janela flutuante`;
- adicionar item próprio em Configurações;
- ocultar a seção legada dentro da tela de HUD;
- manter as mesmas preferências existentes para não perder configuração do usuário.

### Item 3 — Controles corretos de tamanho/opacidade

A nova tela deve mostrar separadamente:

```text
Tamanho do botão
Opacidade do botão
Opacidade da janela
```

Compatibilidade:
- `bubble_opacity` continua representando o botão, preservando usuários atuais;
- nova chave `bubble_window_opacity_027034` controla apenas o painel expandido;
- tamanho atual do botão é preservado.

### Item 4 — posição sempre visível

O botão “Restaurar posição da janela” deixa de ser necessário na interface.

A regra passa a ser:

> O botão flutuante nunca pode permanecer fora da área visível.

O controller já possui clamp automático em:
- criação;
- refresh;
- término de arraste;
- recolhimento;
- alteração de viewport.

A RC3.4 remove o controle manual da experiência e mantém o clamp automático para:
- rotação;
- tela dividida;
- tablets;
- mudanças de tamanho do botão;
- coordenadas salvas fora dos limites atuais.

### Item 5 — 99 em implementação

A RC3.4 deve comunicar claramente:

```text
Uber suportado · 99 em implementação
```

Regra de release:
- falha da 99 não bloqueia lançamento inicial;
- regressão Uber continua sendo bloqueadora;
- melhoria da 99 é trilha paralela.

### Item 6 — Prévia do HUD

Problema:
- a prévia manual da tela de configuração divergiu do HUD ao vivo.

RC3.4:
- remover a segunda implementação visual da prévia;
- construir a amostra através de `Hud023Renderer`, o renderer real do HUD;
- manter uma oferta fictícia somente como fonte de valores;
- tamanho, tipografia, cards, métricas, opacidade e layout passam pelo renderer usado em produção.

---

## 0.2.1 Implementação RC3.4 preparada

Arquivos de código desta rodada:

```text
android/app/build.gradle.kts
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/com/bigcorps/driveraimvp/FieldValidationPolish0265.kt
android/app/src/main/java/com/bigcorps/driveraimvp/FloatingWindowOpacity027034.kt
android/app/src/main/java/com/bigcorps/driveraimvp/FloatingWindowSettingsActivity027034.kt
android/app/src/main/java/com/bigcorps/driveraimvp/HudConfigPreview024.kt
android/app/src/main/java/com/bigcorps/driveraimvp/JourneyUiPreferences.kt
```

Decisões de implementação:

- `FieldValidationPolish0265` reaplica o mascote correto após cada reconstrução de Configurações e cria os itens independentes de Janela Flutuante e status de plataformas;
- a seção legada de Janela Flutuante dentro de `Strategy021Activity` é ocultada, preservando compatibilidade das preferências sem duplicar controles para o usuário;
- a descrição de `Configuração do HUD` deixa de prometer janela/mensagens e passa a indicar métricas, limites e prévia;
- `JourneyUiPreferences` preserva `bubble_opacity` como opacidade do botão e adiciona `bubble_window_opacity_027034` para a janela;
- `FloatingWindowOpacity027034` aplica somente a opacidade visual do painel expandido e trilho de mensagens;
- `FloatingWindowSettingsActivity027034` é a tela independente da janela;
- `HudConfigPreview024` deixa de desenhar um HUD paralelo e passa a chamar `Hud023Renderer.build`, compartilhando a renderização principal do HUD;
- nenhum arquivo do reader/OCR congelado entra no pacote.

---

## 0.3 Arquivos congelados nesta rodada

Não alterar nesta RC3.4:

```text
OfferParser.kt
UberSpatialParser0221.kt
OfferDeduplicator.kt
MediaProjectionOcrService.kt
OfferIntegrityGate027033.kt
ShadowOfferRecovery027033.kt
ReaderAutoFailure027033.kt
```

Também não alterar:
- fórmulas financeiras;
- thresholds financeiros;
- sampling OCR;
- resolução OCR principal;
- regras de auto-aceite/rejeite (continuam inexistentes).

---

## 0.4 Critério de aceite RC3.4

### Configurações
- card “Sr. Rotas está pronto” usa mascote correto;
- “Janela flutuante” aparece como item independente;
- seção legada “Janela flutuante” não aparece dentro de “Configuração do HUD”;
- “Plataformas” informa Uber suportado e 99 em implementação.

### Janela flutuante
- tamanho do botão preservado ao atualizar;
- opacidade do botão altera somente o botão;
- opacidade da janela altera painel expandido/mensagens, não o botão;
- 1–5 ofertas continua funcionando;
- tamanho de texto continua funcionando;
- fechar/reabrir app preserva preferências;
- botão permanece visível após rotação e tela dividida;
- não existe botão de “Restaurar posição” na nova experiência.

### HUD
- prévia visual usa renderer real;
- Compacto/Normal/Grande correspondem ao HUD vivo;
- tema/opacidade/fonte continuam reativos;
- nenhuma regressão de cálculo/veredito.

### Reader
- Uber continua com o comportamento da RC3.3;
- 99 continua diagnosticável, mas não bloqueia release.

---

# NOVA PRIORIDADE P0 — CATALOGAÇÃO E INTELIGÊNCIA

Após a RC3.4, a pergunta principal deixa de ser apenas:

> “O HUD apareceu?”

Passa a ser também:

> “A oferta foi catalogada com qualidade suficiente para alimentar a inteligência correta?”

Indicadores a acompanhar:

```text
financial_ready
demand_temporal_ready
route_flow_ready
fully_ready
```

No Android ao vivo, acompanhar também o equivalente geotemporal:

```text
valid_observed_at
with_pickup_label
with_destination_label
with_pickup_cell
with_destination_cell
resolved_contexts
route_intelligence_ready
```

Meta: aumentar `route_intelligence_ready` preservando verdade dos dados. Nunca completar campos por inferência fraca apenas para elevar percentual.

---

**Data-base:** 13/09/2026  
**Versão em teste:** `0.27.0-rc3.3`  
**Objetivo desta fase:** fechar a confiabilidade da leitura Android e consolidar a base histórica V7.1 antes de voltar a adicionar novos recursos.

---

## 1. Princípio geral da fase

A prioridade absoluta continua sendo:

> **O Sr. Rotas precisa ler corretamente as ofertas antes de ganhar novos recursos.**

A RC3.3 e o reprocessamento V7.1 são duas frentes paralelas, mas complementares:

1. **RC3.3:** garantir captura/leitura confiável em tempo real.
2. **V7.1:** garantir histórico confiável para previsões, regiões e inteligência coletiva.

Nenhuma das duas frentes deve contaminar a outra com dados incompletos ou suposições.

---

# FRENTE A — TESTE DE CAMPO DA RC3.3

## 2. Testar a RC3.3 em situação real

O irmão do usuário deve usar a versão normalmente em campo.

### Prioridades do teste

Validar principalmente:

- Uber e 99 funcionando simultaneamente;
- leitura contínua durante toda a jornada;
- nenhuma necessidade de encerrar/reiniciar jornada para o leitor voltar;
- 99 com:
  - motorista → passageiro;
  - passageiro → destino;
  - total correto das duas pernas;
- Uber sem regressões;
- ofertas Radar;
- ofertas Exclusive;
- categorias diferentes quando aparecerem;
- HUD aparecendo no momento correto;
- nenhuma mistura de campos entre duas ofertas;
- nenhuma mistura Uber ↔ 99;
- recuperação automática quando uma leitura inicial estiver incompleta;
- nenhuma oferta incompleta sendo tratada como financeiramente completa;
- Configurações sem redimensionamento/pulos repetidos;
- Histórico/Jornadas funcionando normalmente;
- painel Agora compacto sem ocupar largura exagerada.

---

## 3. Regra crítica de integridade da oferta

A geometria correta é:

```text
pickup = motorista → passageiro
trip   = passageiro → destino

total_km      = pickup_km + trip_km
total_minutes = pickup_minutes + trip_minutes
```

### Nunca permitir

```text
pickup ausente = 0
trip = total
```

ou:

```text
apenas passageiro → destino
= oferta financeiramente completa
```

Uma oferta pode continuar útil para análise temporal mesmo estando financeiramente incompleta.

---

# FRENTE B — DIAGNÓSTICOS DE CAMPO

## 4. Registrar falhas reais

Quando uma oferta estiver claramente visível no Uber/99 e o HUD do Sr. Rotas não aparecer:

1. não encerrar a jornada imediatamente;
2. usar **Reportar falha**;
3. registrar a ocorrência o mais próximo possível do momento da falha;
4. se possível, guardar screenshot do card problemático;
5. continuar usando o app;
6. depois exportar o diagnóstico.

A RC3.3 também possui registros automáticos para alguns tipos de falha.

---

## 5. Informações desejadas junto com cada problema

Quando possível, registrar:

- Uber ou 99;
- tipo da oferta;
- card permaneceu visível ou desapareceu rapidamente;
- HUD apareceu parcialmente ou não apareceu;
- havia outra plataforma aberta simultaneamente;
- tela dividida ou app em primeiro plano;
- screenshot da oferta;
- diagnóstico correspondente.

---

## 6. O que queremos descobrir nos diagnósticos

Classificar as falhas em categorias técnicas:

```text
1. captura não entregou frame útil
2. OCR não leu conteúdo suficiente
3. OCR leu, mas plataforma não foi identificada
4. plataforma correta, porém campos ficaram incompletos
5. duas ofertas/cards foram misturados
6. integrity gate bloqueou corretamente uma oferta incompleta
7. shadow recovery recuperou a oferta
8. shadow recovery tentou e não conseguiu
9. pipeline ficou semanticamente parado
10. outro problema ainda não identificado
```

Isso evita alterar parser/captura apenas por impressão.

---

# FRENTE C — DECISÃO APÓS O TESTE RC3.3

## 7. Não criar RC3.4 automaticamente

Depois do teste, reunir:

- relatório do irmão;
- diagnósticos;
- screenshots;
- número aproximado de ofertas observadas;
- número aproximado de HUDs corretos;
- separação por Uber e 99.

### Decisão

#### Cenário A — RC3.3 resolveu o problema principal

Se a leitura estiver confiável:

> **Congelar o reader.**

Não mexer mais na captura/parsers sem motivo real.

#### Cenário B — poucas falhas específicas

Criar uma eventual:

```text
0.27.0-rc3.4
```

mas somente com correções cirúrgicas baseadas nos diagnósticos.

#### Cenário C — problema sistêmico continua

Manter prioridade absoluta no pipeline de captura/leitura antes de qualquer novo recurso.

---

# FRENTE D — PILOTO HISTÓRICO V7.1

## 8. Começar com piloto pequeno

Não reprocessar as ~40 mil imagens imediatamente.

Primeiro reprocessar aproximadamente:

```text
500 a 1.000 screenshots
```

A amostra deve conter, se possível:

- Uber;
- 99;
- Radar;
- Exclusive;
- diferentes categorias;
- cards incompletos;
- telas divididas;
- imagens com mais de uma oferta;
- diferentes horários e regiões.

---

## 9. Contrato obrigatório V7.1

Formato preferido:

```text
JSONL
```

Uma oferta por linha.

Uma imagem pode gerar:

```text
0 ofertas
1 oferta
2 ou mais ofertas
```

Nunca assumir:

```text
1 imagem = 1 oferta
```

---

## 10. Identidade das ofertas históricas

Usar:

```text
record_id
source_file_sha256
offer_index
crop_sha256 (quando disponível)
```

### Regra crítica

```text
source_file_sha256 sozinho NÃO é chave de duplicidade
```

Porque uma mesma imagem pode conter várias ofertas.

---

# FRENTE E — QUALIDADE V7.1

## 11. Quatro níveis independentes de aproveitamento

O importador deve avaliar separadamente:

### Temporal

```text
demand_temporal_ready
```

Requer principalmente:

- horário confiável;
- pickup/região utilizável.

Pode existir mesmo com dados financeiros incompletos.

---

### Fluxo de rota

```text
route_flow_ready
```

Requer:

- horário;
- pickup utilizável;
- destination utilizável.

---

### Financeiro

```text
financial_ready
```

Requer:

- tarifa;
- pickup_minutes;
- pickup_km;
- trip_minutes;
- trip_km;
- geometria coerente.

---

### Completo

```text
fully_ready
```

Somente quando os três usos anteriores estiverem disponíveis e não houver conflito crítico.

---

# FRENTE F — HORÁRIO

## 12. Preservar horário real da oferta

Nunca usar:

```text
horário do reprocessamento
```

como horário da corrida.

Usar, quando disponível:

- nome do arquivo;
- metadados da imagem;
- horário visível na tela;
- metadado externo confiável.

Registrar:

```text
observed_at
observed_timezone
time_source
time_confidence
```

Timezone esperado:

```text
America/Sao_Paulo
```

---

# FRENTE G — LOCALIZAÇÃO

## 13. Preservar origem e destino corretamente

Campos principais:

```text
pickup_text
destination_text
driver_location_text
pickup_region_candidate
destination_region_candidate
driver_region_candidate
```

### Regra importante

Não inferir a localização atual do motorista a partir do pickup.

---

## 14. Bloquear textos genéricos de interface

Nunca considerar como bairro/região válida textos como:

```text
Área
Região
Destino
Origem
Retirada
Embarque
Buscar
Aceitar
Escolher
Desloque-se até
Entrada principal
```

e outros equivalentes de interface.

Exemplo:

```text
pickup_text = "Área"
pickup_region_candidate = "Barra Funda"
```

Resultado esperado:

- `"Área"` é sinalizado como genérico;
- `"Barra Funda"` pode continuar útil como candidato real de região.

---

# FRENTE H — AUDITORIA DO PILOTO

## 15. Subir piloto no Admin

Página:

```text
https://srrotas.com/admin/importacoes
```

Enviar o JSON/JSONL V7.1.

Quando aplicável, selecionar:

```text
Substitui lote anterior
```

apontando para o lote V7 antigo correspondente.

Isso **não deve apagar o lote antigo**.

---

## 16. Conferir métricas do piloto

Verificar:

```text
Total de ofertas
Temporal
Rota
Financeiro
Completo
Parcial
Duplicado
quality_flags
Uber
99
Distribuição temporal
Distribuição regional
```

---

## 17. Auditoria manual imagem × JSON

Separar uma amostra aleatória de aproximadamente:

```text
50 a 100 imagens
```

Comparar visualmente:

```text
imagem original
↕
JSON produzido
```

Validar:

- plataforma;
- categoria;
- tarifa;
- pickup_minutes;
- pickup_km;
- trip_minutes;
- trip_km;
- origem;
- destino;
- horário;
- quantidade de ofertas presentes na imagem;
- associação correta dos números ao card correto.

---

## 18. Critério de qualidade

Não maximizar artificialmente a porcentagem de preenchimento.

Princípio:

> É melhor ter 80% das ofertas completas e confiáveis do que 98% preenchidas com inferências erradas.

Dados incompletos podem permanecer parciais.

Dados errados não devem ser promovidos como completos.

---

# FRENTE I — AJUSTES DO V7.1

## 19. Corrigir antes das ~40 mil

Se o piloto revelar problemas, corrigir o algoritmo antes do lote completo.

Exemplos:

- crop errado;
- duas ofertas misturadas;
- pickup e trip invertidos;
- plataforma errada;
- horário errado;
- região genérica;
- R$/km anunciado confundido com tarifa;
- total calculado apenas com uma perna;
- números associados ao card errado;
- deduplicação incorreta.

Depois gerar outro piloto pequeno.

Repetir até aprovação.

---

# FRENTE J — REPROCESSAMENTO COMPLETO

## 20. Congelar a versão aprovada

Quando o piloto estiver aprovado:

> Congelar versão, prompt, parâmetros e configuração do V7.1.

O reprocessamento completo deve usar exatamente a mesma configuração aprovada.

Evitar produzir partes da base com versões diferentes do algoritmo.

---

## 21. Reprocessar as ~40 mil imagens

Processar todo o histórico mantendo:

- arquivos originais intactos;
- rastreabilidade por hash;
- offer_index;
- record_id;
- quality_flags;
- confiança;
- readiness flags;
- raw extraction quando aplicável.

---

# FRENTE K — IMPORTAÇÃO COMPLETA

## 22. Importar o lote V7.1 completo

Após o reprocessamento:

1. subir o JSONL completo;
2. marcar o lote antigo correspondente como substituído quando aplicável;
3. finalizar o lote;
4. revisar as métricas.

---

## 23. Comparar V7 antigo × V7.1

Comparar principalmente:

- quantidade de ofertas;
- Uber × 99;
- distribuição por horário;
- regiões;
- rotas;
- financeiro;
- ofertas parciais;
- ofertas completas;
- duplicidades;
- conflitos;
- quality_flags.

A aprovação não deve ocorrer apenas porque o V7.1 encontrou mais registros.

Ele precisa ser **mais confiável**.

---

# FRENTE L — PRESERVAÇÃO DO HISTÓRICO

## 24. Não apagar o lote antigo

Durante piloto e processamento completo:

```text
NÃO APAGAR
NÃO SOBRESCREVER DESTRUTIVAMENTE
```

O lote antigo continua servindo como comparação e auditoria.

---

## 25. Arquivar somente depois da aprovação completa

Depois que o V7.1 completo estiver validado:

```text
arquivar V7 antigo
```

Preferência:

> arquivar, não excluir.

Assim preservamos a rastreabilidade histórica.

---

# FRENTE M — INTELIGÊNCIA REGIONAL

## 26. Só atualizar o seed depois da aprovação

Não executar antecipadamente:

```sql
sr_refresh_region_seed_v1()
```

Enquanto o V7.1 estiver em piloto ou auditoria.

---

## 27. Atualizar inteligência regional

Somente depois de definir qual lote completo será a fonte oficial.

Então recalcular:

- demanda regional;
- distribuição por horários;
- fluxo origem → destino;
- R$/km;
- R$/hora;
- categorias;
- demais indicadores históricos.

---

# FRENTE N — VALIDAR PREVISÕES

## 28. Revisar o comportamento da inteligência após V7.1

Conferir especialmente:

- regiões recomendadas;
- horários;
- previsão de demanda;
- origem → destino;
- melhor R$/km;
- melhor R$/hora;
- categorias;
- Base Pessoal;
- Base Coletiva.

Verificar também se desapareceram previsões genéricas como:

```text
Área
Região
Desloque-se até
Destino
```

---

# FRENTE O — BASE PESSOAL E COLETIVA

## 29. Manter separação conceitual

### Base Pessoal

Dados do próprio motorista.

### Base Coletiva

Dados agregados/anônimos dos participantes + histórico aprovado quando fizer sentido para o motor correspondente.

Não misturar dados de qualidade insuficiente apenas para aumentar amostra.

---

# FRENTE P — O QUE NÃO ALTERAR NESTA FASE

## 30. Arquivos congelados

Não alterar sem evidência concreta:

```text
OfferParser.kt
UberSpatialParser0221.kt
OfferDeduplicator.kt
```

---

## 31. Fórmulas financeiras

Não alterar fórmulas financeiras já estabilizadas apenas para compensar OCR incompleto.

O problema deve ser corrigido na coleta/validação.

---

## 32. Privacidade

Não adicionar acesso amplo à galeria apenas para melhorar o reader.

O shadow recovery deve continuar trabalhando sobre a captura em memória.

---

## 33. Automação de decisão

Não implementar:

```text
auto-accept
auto-reject
auto-touch
```

Princípio permanente:

> **O Sr. Rotas calcula. O motorista decide.**

---

# FRENTE Q — CI / BUILDS

## 34. Acompanhar GitHub Actions

Depois de cada versão:

- build Android;
- testes;
- APK;
- possíveis regressões.

O erro observado no workflow temporário da RC3.3 ocorreu no typecheck do MCP/Zod e não foi causado pelo código de leitura RC3.3.

Não misturar uma correção ampla de MCP com esta fase sem necessidade.

---

# FRENTE R — CRITÉRIO DE ENCERRAMENTO DA FASE

## 35. Considerar esta etapa concluída somente quando

Todos os itens abaixo estiverem satisfatórios:

### Android

- leitura Uber confiável;
- leitura 99 confiável;
- Uber + 99 simultâneos confiáveis;
- leitor não para durante jornada;
- recuperação automática funciona;
- HUD não usa oferta financeiramente incompleta;
- diagnóstico suficiente para investigar falhas restantes;
- nenhuma regressão crítica.

### Histórico

- piloto V7.1 aprovado;
- associação das duas pernas correta;
- horários confiáveis;
- regiões confiáveis;
- ofertas múltiplas por screenshot tratadas corretamente;
- ~40 mil imagens reprocessadas;
- lote completo importado;
- V7 antigo comparado;
- lote antigo arquivado somente após aprovação;
- seed regional recalculado conscientemente.

### Inteligência

- previsões utilizam dados válidos;
- não aparecem labels genéricos como regiões;
- métricas financeiras não recebem ofertas incompletas;
- Base Pessoal e Coletiva continuam coerentes.

### Infraestrutura

- Actions/builds relevantes aprovados;
- versão de campo instalável;
- backend/Admin funcional;
- migration V7.1 presente e aplicada.

---

# 36. Ordem operacional resumida

```text
RC3.3 EM CAMPO
      ↓
COLETAR DIAGNÓSTICOS
      ↓
CLASSIFICAR FALHAS REAIS
      ↓
CONGELAR READER OU FAZER RC3.4 CIRÚRGICA
      ↓

EM PARALELO:

PILOTO V7.1 — 500 A 1.000 IMAGENS
      ↓
UPLOAD EM /admin/importacoes
      ↓
AUDITORIA AUTOMÁTICA
      ↓
AUDITORIA MANUAL IMAGEM × JSON
      ↓
CORRIGIR V7.1 SE NECESSÁRIO
      ↓
NOVO PILOTO
      ↓
APROVAR E CONGELAR V7.1
      ↓
REPROCESSAR ~40 MIL
      ↓
IMPORTAR LOTE COMPLETO
      ↓
COMPARAR V7 × V7.1
      ↓
ARQUIVAR V7 ANTIGO
      ↓
REFRESH DO SEED REGIONAL
      ↓
VALIDAR PREVISÕES
      ↓
ENCERRAR FASE DE INTEGRIDADE
      ↓
VOLTAR A EVOLUIR RECURSOS DO PRODUTO
```

---

# 37. Regras de segurança para não perder trabalho

Durante toda esta fase:

1. não apagar screenshots originais;
2. não apagar JSONs antigos;
3. não apagar lote V7 anterior;
4. não executar refresh regional antes da aprovação do lote completo;
5. não modificar parsers congelados sem evidência;
6. não corrigir problema de OCR alterando fórmula financeira;
7. não reprocessar as ~40 mil antes de aprovar o piloto;
8. não mudar versão/configuração do V7.1 no meio do lote completo;
9. preservar hashes e rastreabilidade;
10. registrar a versão exata usada em cada lote;
11. manter diagnóstico de campo antes de reiniciar a jornada quando possível;
12. tratar dados parciais como parciais — nunca inventar o que não foi extraído.

---

# 38. Próximo marco

Enquanto o irmão testa a RC3.3, a frente recomendada é:

```text
1. preparar/rodar piloto V7.1
2. gerar JSONL
3. subir em https://srrotas.com/admin/importacoes
4. auditar 500–1.000 imagens
5. corrigir V7.1 se necessário
```

Quando o teste de campo terminar:

```text
6. receber relatório + diagnósticos
7. decidir se reader será congelado ou se haverá RC3.4
```

Somente depois da aprovação do V7.1:

```text
8. reprocessar as ~40 mil
9. importar lote completo
10. comparar com V7 antigo
11. arquivar antigo
12. atualizar seed regional
13. validar inteligência final
```

---

## Regra final

> **Confiabilidade primeiro. Volume depois. Recursos novos por último.**

O objetivo desta fase não é fazer o Sr. Rotas “parecer mais inteligente”.

É garantir que **toda inteligência futura seja construída sobre ofertas realmente lidas, corretamente separadas, temporalmente confiáveis e financeiramente íntegras**.

---

# REGRA PERMANENTE DE DOCUMENTAÇÃO

A partir da RC3.4, toda entrega futura deve incluir este arquivo atualizado:

```text
SRROTAS-PLANO-MESTRE.md
```

Em cada nova evolução registrar obrigatoriamente:

1. versão/versionCode;
2. commit-base usado;
3. problema observado;
4. evidência/diagnóstico usado;
5. decisão tomada;
6. arquivos alterados;
7. arquivos explicitamente congelados;
8. critérios de aceite;
9. resultado do teste de campo;
10. próximo passo.

Nunca substituir o histórico deste documento por um resumo menor. Acrescentar e consolidar, preservando decisões relevantes para que o projeto possa ser retomado em outra conversa sem perda de contexto.
