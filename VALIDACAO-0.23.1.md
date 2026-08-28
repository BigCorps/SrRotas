# Validação local 0.23.1

Checagens executadas ao empacotar:
- integridade ZIP;
- XML/resources do pacote 0.23 existente permanecem inalterados;
- verificação de ausência de ic_launcher/mipmap no patch;
- verificação de que ui-023.css não redefine --sr-bg/--sr-card/--sr-ink;
- verificação de redirects Web duplicados para /app/perfil;
- verificação de versionCode 37 / 0.23.1-beta;
- verificação de referências ao logo_srrotas no cabeçalho nativo.

O Gradle Android completo não é executado neste ambiente por ausência do Android SDK. O GitHub Action é a validação definitiva de compilação/testes.
