# Aplicar — Sr. Rotas 0.20.0-beta

Base usada:

`4faec5feb2be8cbb1cf53f8c47d393931bbee6d4`

## Versão

- `versionCode 27`
- `versionName 0.20.0-beta`

## SQL

**Nenhum SQL novo.**

Não execute migrations antigas novamente.

## Aplicação

1. Extraia o ZIP.
2. Envie todo o conteúdo para a raiz do repositório, preservando as pastas.
3. Aguarde Vercel + GitHub Actions.
4. O Actions deve usar os mesmos Secrets de assinatura estável configurados na 0.19.
5. Distribua somente o artifact:
   `sr-rotas-field-release-apk`

## Instalação

Instale por cima da `0.19.0-beta`.

Não desinstale antes do primeiro teste.

A fila de 124 itens da instalação 0.19 é um caso de teste real da migração.

## Primeiro teste obrigatório

Antes de iniciar nova jornada:

1. abrir Perfil;
2. conferir quantos itens estão aguardando;
3. tocar `Sincronizar agora`;
4. aguardar a mensagem;
5. conferir novamente.

Esperado:

`124 → 0`

Pode haver pequena diferença se novos eventos forem criados entre as medições.

O importante é:
- os itens antigos serem enviados;
- não haver loop permanente 400/404;
- não limpar SQLite;
- não duplicar ofertas.

## Novo menu flutuante

Com permissão de overlay:

- mascote permanece disponível ao abrir o Sr. Rotas;
- toque abre o menu;
- 3 últimas ofertas;
- uma expandida por vez;
- Embarque/Destino;
- Estou fazendo;
- Realizada/Não realizada quando aplicável;
- Iniciar/Pausar/Retomar/Encerrar;
- Histórico;
- X recolhe o menu;
- drag do mascote continua.

## Screenshot histórico

A infraestrutura permanece no código/admin.

A opção deixa de aparecer na interface normal do motorista.

## Offer Engine

Nenhum arquivo congelado do Offer Engine faz parte deste ZIP.
