# Validação do consolidado 0.24.0-beta

Base analisada: `6a0d68c5ddbb22e450e92a0434f6e3548786ef5c`.

## Auditoria local
- ordem de merge: Parte 1 -> Parte 2 -> Parte 3 -> Parte 4;
- conflitos conhecidos resolvidos pela versão mais nova;
- `JourneyUiPreferences`: Parte 4 contém restauração da Parte 2 + setters da
  Parte 3 + ativação/contagem/texto da Parte 4;
- `MainActivity` final mantém correções da Parte 2 e adiciona Usuário nativo /
  Demonstração;
- `Strategy021Activity` final mantém o HUD da Parte 3 e corrige `follow_app`;
- `SettingsHub023` final mantém separação Aparência/HUD e adiciona semáforo;
- `JourneyBubbleController` final mantém clamp/restore e adiciona novos controles;
- `NowPanel023` final mantém a fonte corrigida da Parte 2 e acrescenta visual
  da Base Coletiva;
- Histórico usa exatamente a base corrente do commit informado, com alteração
  apenas de apresentação/remoção do card regional da composição.

Verificações estruturais locais: **89**.

## Proteções
- sem SQL;
- sem migration;
- sem launcher/mipmap;
- OCR/parser/cálculo financeiro não foram redesenhados nesta consolidação.

## Build
O ambiente desta sessão não possui o projeto Gradle completo clonado nem Android
SDK acessível para um build Android confiável. Portanto, o build definitivo deve
ser validado pelo workflow já existente no GitHub após o upload do ZIP.

As regras puras são compiladas separadamente no fechamento desta entrega.

- Harness Kotlin das regras puras (HUD, status, bounds, fonte regional e mensagens): **FINAL_024_PURE_RULES_OK**.
