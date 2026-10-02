# Rollback — Radar Contextual vc88

## Rollback operacional sem trocar APK
No Field APK:
1. abrir aba Radar;
2. tocar `Rollback`;
3. flags UI/runtime/assistant ficam false;
4. runtime é parado;
5. card contextual é removido;
6. `RadarPanel027035` volta a ser a superfície ativa.

Não é necessário desinstalar, limpar dados ou encerrar a jornada.

## Rollback de código
Se for necessário reverter o pacote inteiro, voltar apenas os arquivos listados no manifest do vc88 para o SHA-base `925078db920ac56649b2b116a9b3e0e60054a76c`.

Não reverter migrations Radar: elas já estavam aplicadas antes do vc88.
Não apagar os 4 POIs R1 automaticamente; eles são dados canônicos derivados de locais exatos e têm audit log.
