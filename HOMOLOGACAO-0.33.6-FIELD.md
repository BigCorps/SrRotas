# Sr. Rotas — Homologação de campo 0.33.6

Data: 26/09/2026  
Build: `0.33.6-field / versionCode 83`  
Commit: `f2d7166280f58b22d3ff047a39ea7a3eaf36b026`  
CI: Action #135 — SUCCESS  
Vercel do mesmo commit: READY

## Resultado
A 0.33.6 passa a ser a base Android de campo homologada para a reta final pré-1.0.

### Assistente
JSON final: 9 avaliações, 7 buscas, 10 sugestões comprometidas, 7 overlays vistos, 7 fechados, 9 decorações, 1 IGNORAR, 2 VER e 0 `polish_errors`.

### Métricas e energia
JSON final: métricas 3/3 sucesso, energia 1/1 sucesso, zero falhas e zero pendências. Supabase confirmou uma entrada de energia e métricas recentes após o teste.

### Capture recovery
9 interrupções; 4 pedidos/autorizações/sucessos; 0 falhas; mesma jornada preservada; sem reutilização silenciosa do token. Notificação: 2 postadas, 2 canceladas, 0 falhas.

### Reader
M1 permanece oficial. Reader2 permanece shadow/telemetria e não é gate para 1.0. Controlled Hybrid continua OFF.

### Observações pré-release
- `pending_crash` ainda aponta para crash antigo da 0.27.0 e deve ser encerrado/triado na RC;
- backlog legado de screenshots visíveis ainda existe;
- offline/sync e soak de performance ainda entram no gate RC.

Branding Web não justifica nova APK. A próxima APK deve ser RC/pré-1.0 ou uma correção Android necessária.
