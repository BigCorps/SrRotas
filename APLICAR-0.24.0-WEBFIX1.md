# Sr. Rotas 0.24.0 — WEBFIX1: Shell unificado

Base exata analisada: `10f40e5310215cb5c97eff9096d5a597dd1f0215`.

## Objetivo
Unificar o visual da Central Web entre desktop e mobile.

## Alterações
- remove sidebar do desktop;
- header de marca fica igual nos dois formatos;
- header azul/navy contém somente o logo à esquerda;
- títulos/subtítulos passam a ficar abaixo, no conteúdo da página;
- footer com Usuário / Plano e créditos / Mensagens rápidas / Sua IA aparece também no desktop;
- footer usa a mesma composição no desktop e mobile, mudando apenas largura/padding;
- conteúdo desktop aumenta para até 1120px úteis;
- header mobile reduz para 56px;
- cor do header usa `--sr-navy` da paleta 0.24 em vez do petróleo legado;
- footer respeita Light/Dark e cores semânticas da 0.24.

## Arquivos
- backend/app/app/_components/WebAppShell.tsx
- backend/app/app/_components/AccountPageHeader.tsx
- backend/app/app/web-shell-024.css
- backend/app/app/layout.tsx

Sem Android.
Sem SQL.
Sem Supabase.
Sem versionCode/versionName.
