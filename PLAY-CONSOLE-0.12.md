# Sr. Rotas — preparação Google Play (0.12)

Documento operacional; revisar novamente imediatamente antes do envio da 1.0.

## Package e API
- package: `com.srrotas.app`
- compileSdk: 36
- targetSdk: 36
- minSdk: 26

## AccessibilityService
Removida da declaração do AndroidManifest e substituída exclusivamente pelo fluxo MediaProjection iniciado pelo usuário. Não declarar AccessibilityService no Play Console se o AAB final mantiver esta configuração.

## Foreground Service — mediaProjection
O manifesto declara:
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PROJECTION`
- service `MediaProjectionOcrService` com `foregroundServiceType="mediaProjection"`.

### Texto sugerido para a declaração do Play Console
**Funcionalidade:** "O motorista inicia voluntariamente uma jornada no Sr. Rotas. O Android exibe a tela nativa de autorização de MediaProjection. Enquanto a jornada está ativa, um serviço em primeiro plano captura frames da tela para OCR local e mostra cálculos auxiliares em um HUD."

**Impacto se adiado:** "A oferta exibida pode desaparecer antes de ser analisada e o cálculo não seria apresentado no momento esperado pelo usuário."

**Impacto se interrompido:** "A análise para imediatamente; o usuário pode encerrar a jornada pelo Sr. Rotas e a notificação persistente torna a atividade perceptível."

### Vídeo para declaração FGS
Gravar um vídeo curto mostrando:
1. abrir Sr. Rotas;
2. tocar Iniciar jornada;
3. consentimento/MediaProjection do Android;
4. notificação de primeiro plano;
5. abrir uma tela de oferta de teste e mostrar HUD;
6. voltar ao Sr. Rotas e Encerrar jornada.

## Exclusão de conta
- caminho no app: Perfil → Excluir minha conta e dados;
- URL externa: `https://srrotas.com/excluir-conta`.

O fluxo web permite autenticar e concluir a exclusão sem reinstalar o Android.

## Data Safety — base para preenchimento
Validar novamente no AAB final. Categorias atualmente relevantes podem incluir:
- informações pessoais: nome/e-mail para conta;
- atividade do app/dados de uso: jornadas, ofertas estruturadas e analytics;
- informações financeiras limitadas à cobrança/conciliação (txid, valor, status);
- identificadores do app/conta/dispositivo para sessão, push e segurança;
- conteúdo fornecido pelo usuário: pergunta enviada à IA e feedback explícito, quando aplicável.

Importante:
- OCR bruto não faz parte do upload normal de ofertas;
- capturas privadas ficam locais por padrão;
- não declarar venda de dados;
- verificar no formulário final como cada SDK (OneSignal/OpenAI via servidor) afeta coleta/compartilhamento.

## Pagamento externo
O APK não contém geração de Pix/QR Code/checkout. Ele exibe somente status do plano e créditos. O checkout fica no site. Revisar a política de pagamentos vigente imediatamente antes do envio à Play.

## Domínio canônico
`https://srrotas.com`
- `/mcp`
- `/conta`
- `/privacidade`
- `/termos`
- `/suporte`
- `/excluir-conta`

## Antes da 1.0
- revisar Data Safety no Play Console;
- revisar declaração FGS;
- confirmar política de pagamentos vigente;
- gerar vídeo FGS;
- screenshots/listing;
- testar AAB assinado em Internal/Closed testing;
- confirmar exclusão de conta fim a fim;
- confirmar OneSignal/FCM depois que o Google liberar o projeto Firebase.
