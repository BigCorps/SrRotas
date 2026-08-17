# Aplicar — Sr. Rotas 0.6.0 Alpha

Patch incremental sobre a `main` 0.5.4 / commit esperado `05a4a1099799f32e5123c4b9e7c63c2fdd7f0d32`.

## O que esta fase entrega

Esta é a **fase 0.6 inteira**, não um 0.6A/0.6B.

- Design System nativo do Sr. Rotas;
- nova navegação: Início / Jornada / Histórico / IA / Perfil;
- tela inicial simplificada para uso rápido;
- Estratégia e HUD redesenhada;
- tamanhos de card: Compacto / Normal / Grande;
- tamanho da fonte independente do card;
- tema do HUD: Automático / Claro / Escuro;
- métricas ativáveis e reordenáveis;
- novo visual “Painel de Rota”, com trilho lateral próprio da marca;
- toque no HUD fecha a oferta atual;
- segurar + arrastar move o HUD;
- posição arrastada fica salva;
- botão para restaurar a posição inicial;
- diagnóstico passa a registrar tamanho/gestos/posição do HUD;
- interface principal acompanha claro/escuro do sistema.

## O motor NÃO muda

A fase 0.6 não altera:
- MediaProjection;
- OCR;
- parser;
- Card Stabilizer;
- dedupe;
- cálculos financeiros;
- banco/Supabase.

Por isso o `parser_version` permanece `sr-rotas-v0.5.4`, mesmo o aplicativo passando para `0.6.0-alpha`.

## Como aplicar

1. Extraia este ZIP na raiz do repositório.
2. Substitua os arquivos existentes e mantenha os novos.
3. Commit/push na `main`.
4. Aguarde o workflow `Android Debug APK`.
5. Confirme `Test parser`, `Build debug APK` e `Upload APK`.

## O que testar no APK

1. Início/Jornada/Histórico/IA/Perfil sem quebra em tela pequena.
2. Iniciar e encerrar jornada como antes.
3. Em Estratégia e HUD, testar Compacto / Normal / Grande.
4. Testar fonte 14, 16, 20 e 24.
5. Testar tema Automático / Claro / Escuro.
6. Durante uma oferta: toque simples fecha o card.
7. Segure aproximadamente 0,4 s e arraste: o card deve mover e manter a nova posição nas próximas ofertas.
8. “Restaurar posição inicial” deve voltar a usar Esquerda/Centro/Direita.
9. Confirmar que OCR e `DESEMPENHO OCR` continuam equivalentes à 0.5.4.

## Não há nesta fase

- SQL/migration;
- alteração do Supabase;
- alteração do Vercel;
- OneSignal;
- cobrança/Pix;
- créditos de IA.
