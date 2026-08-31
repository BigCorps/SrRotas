# Paridade de paleta — APK × Web — Sr. Rotas 0.24

Comparação automática entre `SrTheme024.kt` e `theme-024.css`.

**Resultado: 21/21 tokens Light e 21/21 tokens Dark idênticos.**

| Papel | Light | Dark |
|---|---|---|
| Fundo geral / creme | `#F6F3EB` | `#0A1420` |
| Card principal | `#FFFFFF` | `#111F2D` |
| Card/campo secundário | `#F2EEE3` | `#182838` |
| Creme suave | `#FBF7ED` | `#1C2832` |
| Texto | `#0A2747` | `#F5F8FC` |
| Linha | `#D9E0E7` | `#2B3C4E` |
| Azul-marinho | `#082A56` | `#123D70` |
| Azul Agora | `#1677FF` | `#3D8DFF` |
| Histórico | `#0A9B9A` | `#31C5C0` |
| IA | `#744DFF` | `#9B7CFF` |
| Configurações | `#FF8A18` | `#FFAD4D` |
| Usuário | `#6C9F25` | `#95C953` |

A diferença visual vinha de CSS legado ainda carregado por compatibilidade
(ex.: `#073746`, `#168CC8`, `#6754C6`, `#0B4854`).
O WEBFIX2 adiciona uma camada final que obriga a Central Web a usar os tokens 0.24.
