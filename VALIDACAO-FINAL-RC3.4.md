# Validação final — Sr. Rotas 0.27.0-RC3.4

Base confirmada no GitHub: `74ffaf777f84e1caf270962383ad4d31b389decd` (RC3.3).

## Validações concluídas
- versão alterada para `0.27.0-rc3.4` / versionCode `62`;
- nova Activity da Janela Flutuante registrada no Manifest;
- controles independentes de tamanho do botão, opacidade do botão e opacidade da janela presentes;
- preferência histórica `bubble_opacity` preservada como opacidade do botão;
- nova preferência de painel `bubble_window_opacity_027034` adicionada;
- mascote correto `sr0265_settings_ready` reaplicado após reconstrução de Configurações;
- seção legada da Janela Flutuante é ocultada na configuração do HUD;
- prévia usa `Hud023Renderer.build`, eliminando renderer visual paralelo;
- indicação Uber suportado / 99 em implementação incluída;
- nenhum arquivo congelado do reader/OCR/parser está no pacote;
- nenhuma permissão ampla de galeria foi adicionada;
- nenhuma automação de aceitar/rejeitar/tocar em oferta foi adicionada;
- `SRROTAS-PLANO-MESTRE.md` atualizado e incluído como documento obrigatório das próximas entregas;
- Kotlin foi submetido ao parser/compilador local sem diagnósticos de sintaxe; referências Android/projeto ficam naturalmente não resolvidas fora do projeto completo.

## Limite da validação local
Este ambiente não contém checkout completo do repositório Android com SDK/Gradle configurados. Portanto o build integral `testDebugUnitTest/assembleDebug` não foi executado localmente.

Após o upload direto no GitHub, o workflow Android normal deve ser usado como validação de compilação/build do projeto completo antes do APK de campo ser considerado aprovado.

## Supabase / backend
Nenhuma migration, mudança de backend ou alteração remota é necessária nesta RC3.4.
