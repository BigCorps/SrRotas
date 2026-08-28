# Sr. Rotas 0.23.2-beta — UI Coherence

Base: commit 340db70749336ff6e04d0546dedca04d560d571f (0.23.1-beta).

## Correções
- Rodapé Web: 4 destinos em 4 colunas reais, sem a antiga 5ª coluna vazia.
- Ativação Web: `/app/perfil/mensagens` não deixa também "Usuário" marcado.
- Header Web: usa o mesmo padrão estrutural do APK (logo + Sr. Rotas + subtítulo + título da seção) e remove o topbar duplicado.
- Header institucional usa a mesma cor petróleo no Claro e no Escuro.
- Android: o botão central "Agora" fica sempre visível; ativo muda de destaque, inativo continua com botão petróleo.
- Android: troca de tema recria a Activity inteira quando necessário e preserva a seção selecionada.
- Android: mudanças de tema vindas da sincronização também recriam a tela inteira, evitando metade Claro/metade Escuro.
- Estratégia e HUD passam a usar o mesmo header e spinners tematizados das telas principais.
- Plano, Mensagens e MCP Web recebem o mesmo header da Central do Usuário e superfícies coerentes com Light/Dark.

## Não muda
OCR, parsers, Uber/99, MediaProjection, dedupe, cálculos, jornada, RegionalClient, banco, Supabase ou API de mensagens.

## Versão
versionCode 38
versionName 0.23.2-beta

Não há SQL novo.
