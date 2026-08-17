# Changelog — Sr. Rotas 0.5.3 Alpha

## Pipeline
- preserva dois frames enquanto ML Kit está ocupado: primeiro frame novo + mais recente;
- limita apenas o segundo slot, evitando fila infinita;
- reduz moderadamente resolução para OCR em telas maiores que 2100 px no maior lado;
- mantém amostragem de 250 ms;
- mantém telemetria local de desempenho da 0.5.2.

## Parser
- `I/i/l/L` passam a ser aceitos como `1` somente em tokens numéricos;
- caso real `1l minutos` passa a funcionar;
- adiciona `service_type = electric`;
- mantém validação por R$/km anunciado.

## Contexto
- nova classe `FOREIGN_UI`;
- rejeita contexto forte de Recentes, WhatsApp e Galeria antes do parser;
- fixture real do R$47,93 em `Close all` adicionada aos testes.

## Dedupe
- nova janela fuzzy de 2,5 s para flicker de geometria;
- corrige o caso real R$20,38 completo -> pickup ausente em ~341 ms;
- dedupe exato de 60 s continua igual.

## Marca
- launcher foreground atualizado com a arte oficial enviada;
- logo interno atualizado com o mesmo arquivo-fonte;
- nenhuma arte foi redesenhada.
