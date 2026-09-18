# Sr. Rotas 0.27.0 RC3.6 Field — pré-1.0 integrado

## Objetivo

Esta build NÃO é a 1.0.0 pública. É a build integrada de campo que precisa provar, na mesma rodada:

- leitura M1 (MediaProjection) sem regressão;
- leitura M2 experimental via AccessibilityService;
- comparação M1 × M2 em shadow mode;
- recuperação automática do M1 sem reiniciar o app;
- diagnóstico de qual leitor viu a oferta;
- screenshots diagnósticos limitados à janela/área conhecida do Uber quando seguro;
- correções de navegação/UX da RC3.5;
- redução do flicker da janela flutuante;
- métricas em Mais detalhes com limites do próprio motorista.

## Instalação

1. Extraia o ZIP.
2. Envie TODO o conteúdo interno para a raiz do repositório `BigCorps/SrRotas`, preservando as pastas.
3. Commit sugerido: `Sr Rotas RC3.6 Field - Reader Lab M1 M2 and pre1 hardening`.
4. O push em `main` deve iniciar `Android CI + Field APK`.
5. Não distribua o APK antes do Action ficar verde.

## Ativação do M2 no aparelho de campo

Na tela Configurações do Sr. Rotas:

1. Abra `Leitor M1 × M2`.
2. Selecione `Comparativo — M1 + M2`.
3. Toque em `Ativar Acessibilidade`.
4. Leia a divulgação apresentada pelo Sr. Rotas.
5. No Android, habilite `Sr. Rotas — leitor experimental M2`.
6. Volte ao app e inicie a jornada normalmente.

O M2 só observa `com.ubercab.driver` e não executa ações no Uber.

## Diagnóstico visual

A janela flutuante exibe uma etiqueta pequena:

- `M1`: leitura oficial MediaProjection;
- `M2`: última leitura observada veio do leitor Accessibility;
- `M1+M2`: os dois métodos conseguiram correlacionar a oferta.

O M2 permanece shadow-only nesta build: uma leitura exclusivamente M2 NÃO entra sozinha na Base Coletiva/ride_offers.

## Sem SQL

Esta rodada Android não exige migration nem alteração no Supabase. O banco deve permanecer intocado durante a validação do Reader Lab.
