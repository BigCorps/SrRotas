# FASE 0.21 — IMPLEMENTADA

## Objetivo

Unificar estratégia Android/Web, introduzir a inteligência regional acionável `Agora`, preparar o trial real e adicionar tema Claro/Escuro/Automático sem mexer no Offer Engine validado.

## Implementado

### Android

- nova aba **Agora**;
- modos Agora / Hoje / Semana / Pesquisa;
- Base pessoal, Base coletiva e fallback Base Sr. Rotas;
- presets Popular / Conforto / Premium / Personalizado;
- limite de busca em km **e** minutos;
- `StrategyGuard021` pós-parser, sem alterar extração financeira;
- sincronização das preferências Android ↔ backend;
- tela de estratégia 0.21;
- carrossel introdutório para contas novas;
- onboarding reduzido e orientado ao uso;
- explicação correta do trial: começa na primeira oferta live válida;
- temas Automático / Claro / Escuro;
- HUD/menu pode seguir o aplicativo ou ter override;
- diagnóstico de campo renomeado para 0.21;
- SyncCoordinator 0.20.3 preservado integralmente.

### Backend/Web

- preferências 0.21 no backend;
- presets aplicáveis também pelo Web;
- `/api/v1/intelligence/now`;
- `/app/agora`;
- handoff Android → Web por token de uso único de 2 minutos; o `device_token` nunca entra na URL;
- tema Web persistido no backend + fallback local;
- perfil Web com estratégia, trial, tema e opt-in coletivo;
- importação histórica removida da navegação normal do motorista;
- billing passa a reconhecer trial ativo para uso dos 5 créditos temporários de IA.

### Supabase

- `strategy_preset`;
- `max_pickup_minutes`;
- `app_theme`;
- `hud_theme_mode`;
- seed regional anonimizado;
- normalização conservadora de região/aliases de OCR;
- agregado pessoal;
- agregado coletivo somente opt-in e mínimo de 3 motoristas;
- trial de 7 dias iniciado pela primeira oferta live válida;
- 5 créditos IA concedidos de forma idempotente uma única vez.

## Privacidade

O seed histórico não guarda nem expõe `driver_id`, screenshot, OCR bruto, endereço exato ou oferta individual consultável. O histórico importado permanece ferramenta administrativa e não aparece na experiência normal do motorista.

## O que NÃO mudou

- parser/OCR/Radar congelados;
- sampling OCR;
- Card Stabilizer;
- dedupe;
- fórmulas financeiras do Offer Engine;
- política de não aceitar/recusar corridas automaticamente;
- Sync Coordinator/ownership quarantine da 0.20.3.
