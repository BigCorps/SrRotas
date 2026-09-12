# Sr. Rotas 0.27.0-RC3 — Aplicação do patch

Base obrigatória: `main` no commit `1fcf02756c8427b714dc2353761302745824801a` (0.27.0-rc2-diag1 / versionCode 57).

Nova versão: `0.27.0-rc3` / versionCode `58`.

## Como aplicar

1. Extraia o ZIP na raiz do repositório `BigCorps/SrRotas`, preservando os caminhos.
2. Substitua os arquivos existentes pelos arquivos do pacote.
3. Adicione os arquivos novos normalmente.
4. Não execute SQL e não altere Supabase/Vercel para esta rodada.
5. Faça commit/push na `main` somente depois de conferir a lista de arquivos.
6. Aguarde o workflow Android CI + Field APK.
7. Use o APK release gerado pelo workflow para o teste de campo.

## Escopo da RC3

### Leitura 99 / tela dividida
- Mantém o parser Uber (`OfferParser` / `UberSpatialParser`) sem alteração.
- O roteador deixa Uber e 99 analisarem o mesmo frame de OCR em captura de tela inteira.
- A 99 ganha reconstrução conservadora de tempo/distância quando o ML Kit separa, por exemplo, `4 min` e `680 m` em linhas distintas.
- A reconstrução só ocorre dentro do cluster espacial já isolado da 99.
- Em tela com Waze/Maps, continua sendo exigida evidência específica da 99 (`Escolher`, serviço 99 + R$/km, ou Perfil Essencial + R$/km).
- O parser flexível da 99 recebe versão `sr-rotas-multi-v0.27.0-99-flex` para auditoria.

### Diagnóstico / HUD
- Adiciona botão provisório de bug no rodapé da janela flutuante.
- Menu: `Reportar falha`, `Reiniciar leitura`, `Exportar diagnóstico`.
- `Reiniciar leitura` preserva a jornada e pede nova autorização de captura quando necessário.
- `Reportar falha` grava um marcador privacy-safe diretamente no trace local.

### Notificação
- A notificação persistente da jornada passa a oferecer `Reportar falha`, independente da notificação do MediaProjection.
- Se o Android encerrar a captura mantendo a jornada ativa, uma notificação de contingência oferece `Reportar falha`, `Reativar leitura` e `Diagnóstico`.
- A contingência é cancelada quando a captura volta ou a jornada termina.

### Exportação
- O diagnóstico padrão passa a ser compartilhado como arquivo `sr-rotas-diagnostic.json`.
- O arquivo fica no cache privado do app e é compartilhado por URI somente-leitura com permissão temporária.
- Não há envio automático para servidor nesta versão.
- Continua sem OCR bruto, screenshots, tokens, endereço textual ou coordenadas exatas no diagnóstico padrão.

## Fora do escopo
- Sem mudança em `OfferParser.kt`.
- Sem mudança em `UberSpatialParser0221.kt`.
- Sem mudança em `FrameChangeDetector`.
- Sem mudança em frequência de amostragem, resolução máxima do OCR ou ML Kit.
- Sem SQL/Supabase.
- Sem backend/Vercel.
- Sem pagamentos/MCP/admin/AAB.
