# Limitações conhecidas — 0.4 Alpha

- O parser ainda depende de calibração com telas reais do Uber Brasil.
- O Radar pode exigir regras espaciais próprias depois das primeiras amostras.
- O backend Alpha usa um código global de pareamento e associa novos aparelhos ao primeiro motorista de teste; isso não é o modelo de autenticação para produção multiusuário.
- O histórico representa ofertas observadas, não corridas realizadas.
- Não há inferência automática de aceite/conclusão.
- A Pesquisa IA depende de ofertas já sincronizadas e de `OPENAI_API_KEY`.
- O MCP usa token de servidor no Alpha e é somente leitura.
- A TWA aguarda domínio e chave real de assinatura.
- Acessibilidade é auxiliar; o motor principal é MediaProjection.
- A fila offline persiste ofertas localmente, mas ainda não possui interface detalhada de inspeção item a item.
