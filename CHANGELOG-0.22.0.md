# Sr. Rotas 0.22.0-beta — Multiplataforma real + acabamento UX

**Base:** `main` em `ef812fada8e21d9ac99520f56dcec29405ec86f3`  
**Android:** `versionCode 34` / `versionName 0.22.0-beta`

## Multiplataforma

- Mantém o caminho Uber já validado usando o parser atual (`SpatialOfferParser`) sem recalibrar as regras financeiras do Uber.
- Adiciona `DriverPlatformOfferRouter` antes do parser para identificar Uber, 99 e fallback genérico.
- Adiciona suporte dedicado ao 99, incluindo:
  - `R$25,00` e `R$9,80/km` na mesma tela/linha;
  - geometria `(8 min 591 m)` e `(20 min 2 km)`;
  - conversão automática de metros para km;
  - avaliação no formato `4,81 · 237 corridas`;
  - categorias 99Plus/Plus Nova, 99Pop, 99Moto, 99Táxi, 99electric e 99Entrega.
- Adiciona fallback conservador para outros apps de motorista quando houver valor + dois pares de tempo/distância + contexto de oferta.
- Dedupe e estabilização passam a separar plataformas para não misturar cards iguais de apps diferentes.
- Android 14+ solicita compartilhamento do display padrão com consentimento do sistema, permitindo alternar entre Uber, 99 e outros apps durante a mesma jornada.
- Jornadas novas passam a ser registradas como `multi`; cada oferta continua registrando sua plataforma real.

## Classificação financeira

- Uma única métrica ruim do HUD deixa de reprovar automaticamente a oferta.
- O veredito passa por média ponderada das métricas habilitadas.
- A ordem configurada no HUD define prioridade: as primeiras métricas recebem peso maior.
- Limites absolutos de tarifa mínima e de distância/tempo máximo de busca continuam sendo guardrails explícitos do motorista.

## Contexto e endereços

- Context Engine atualizado para `sr-context-v0.22.0`.
- Entende endereço na mesma linha da geometria, como no 99:
  - `(8 min 591 m) Rua Prof. Atílio Innocenti...`
- Junta endereço quebrado em duas linhas quando a primeira linha indica continuação.
- Bloqueia falsos endereços observados em produção, como:
  - `Você está online`;
  - `Para onde?`;
  - `Como foi a viagem? Ajude a melhorar`;
  - `Faça planos como recurso`;
  - `Isso é tudo por enquanto`;
  - `Área de risco`.
- Histórico passa a mostrar Embarque e Destino nas ofertas recentes e filtra rótulos que não parecem lugar.

## Interface

- Remove os títulos duplicados das páginas `Agora`, `Histórico`, `IA` e `Configurações`; a navegação inferior já identifica a seção.
- Configurações reorganizadas por departamentos:
  - Aparência;
  - Jornada e permissões;
  - Estratégia e HUD;
  - Conta e plano;
  - Notificações;
  - Dados e sincronização;
  - Privacidade e suporte.
- A primeira seção de Configurações agora permite escolher `Automático`, `Claro` ou `Escuro` para o aplicativo inteiro.
- Mensagens de jornada deixam de instruir o motorista a abrir exclusivamente o Uber.

## Backend / Supabase / Vercel

- Nenhuma migration necessária.
- Nenhuma Edge Function nova.
- Nenhuma variável nova na Vercel.
- O backend existente já aceita `platform` em texto e o banco não limita a plataforma a Uber.
