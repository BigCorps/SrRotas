# Sr. Rotas 0.23.0-beta — UI Freeze

Base esperada: commit `1e14672f4c7f9c8662663626c0027fbded0472ff` (0.22.1-beta).

## Aplicação

1. Extraia este ZIP.
2. Envie **todos** os arquivos e pastas para a raiz do repositório, preservando exatamente os caminhos e permitindo substituição dos arquivos existentes.
3. Execute no Supabase SQL Editor o arquivo:
   `supabase/migrations/20260828_driver_message_presets_023.sql`
4. Aguarde o GitHub Actions concluir o build Android e o deploy Web/Vercel concluir.
5. Instale o APK 0.23.0-beta e faça o teste visual/funcional.

Não é necessário aplicar separadamente os ZIPs Parte 1, Parte 2 ou Parte 3: este pacote já contém as três etapas consolidadas e integradas.

## Versão

- Android `versionCode`: **36**
- Android `versionName`: **0.23.0-beta**

## O que está integrado

- Design System 0.23 em Android Views/Kotlin; nenhum Compose foi adicionado.
- Light, Dark e Automático com a mesma estrutura visual e troca apenas de paleta.
- Navegação final: Histórico · IA · Agora · Configurações · Usuário.
- Usuário abre a área Web autenticada `/app/perfil` pelo handoff existente.
- Agora e IA redesenhados próximos aos mockups, mantendo os clientes e dados existentes.
- Configurações em hub visual; funções existentes continuam em suas telas/fluxos nativos.
- HUD final Compacto/Normal/Grande com a mesma informação e somente composição diferente.
- HUD mantém arraste por pressão longa, posição salva, fechamento por toque e supressão da oferta atual.
- Janela flutuante preserva últimas ofertas, seleção para relatório, Maps, continuidade de destino e ações operacionais.
- Barra inferior da janela flutuante por ícones e trilho lateral de mensagens rápidas.
- Mensagens rápidas configuradas pela Web e copiadas pelo Android; não são enviadas automaticamente.
- Endereços na janela flutuante não são mais truncados artificialmente em 110 caracteres.

## Proteções deliberadas

- Nenhum `ic_launcher`, `mipmap` ou recurso do ícone atual foi incluído/alterado.
- Nenhuma mudança no OCR, parser Uber/99, roteamento multiplataforma, dedupe, cálculos financeiros ou estratégia ponderada.
- Nenhuma mudança de applicationId/package.
- A migration apenas cria a tabela de mensagens rápidas; não altera dados existentes.
- A migration usa RLS e não cria policy pública, preservando o modelo service-role do backend.

## Checklist de teste

- Action Android compila Debug/Release.
- App inicia e mantém onboarding já concluído.
- Light / Dark / Automático.
- Histórico / IA / Agora / Configurações / Usuário.
- Iniciar, pausar, retomar e encerrar jornada.
- Uber e 99 continuam sendo lidos.
- HUD Compacto, Normal e Grande.
- Fechar HUD não faz a mesma oferta reaparecer imediatamente.
- Janela flutuante mostra 3 últimas ofertas e abre os detalhes.
- Busca/Destino/Combinado continuam abrindo Maps.
- Mensagens 1–6 aparecem na aba lateral e copiam o texto configurado.
- Perfil Web abre sem login adicional pelo handoff.
- `/app/perfil/mensagens` salva e o Android sincroniza os atalhos.

## Observação de validação

Foram executadas validações locais de estrutura, XML, regras puras do HUD e mensagens, sintaxe TypeScript/TSX e integridade do ZIP. O build Android completo não foi executado localmente porque este ambiente não possui Android SDK/`ANDROID_HOME`; o GitHub Actions continua sendo a validação definitiva de compilação Android.
