# Diagnóstico e replay futuro

A versão 0.3 cria uma base local muito mais útil para calibração:

- última captura OCR;
- método de captura;
- log local;
- últimas ofertas estruturadas;
- identificador da jornada;
- versão do parser;
- nível de confiança;
- estado de sincronização.

O botão **Compartilhar diagnóstico** gera um texto via Android Sharesheet. Ele não anexa screenshot e não inclui `device_token` ou código de pareamento.

## Por que ainda não existe replay de imagem

O produto atual descarta o bitmap logo após OCR por privacidade. Um replay completo de OCR exigiria persistir imagens, o que muda a política de dados. Nesta fase preferimos replay de parsing a partir do texto/estrutura quando houver amostras reais suficientes.
