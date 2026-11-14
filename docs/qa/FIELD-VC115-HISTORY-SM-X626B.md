# Homologação privada vc115 — Samsung SM-X626B / Android 16

Candidata `0.33.38-field`, pacote `com.srrotas.app`. Não homologada fisicamente ainda.

[Download privado verificado](https://github.com/BigCorps/SrRotas/actions/runs/43645561370/artifacts/14134151948) (login GitHub autorizado no repositório, expira 14/12/2026 22:04:19 UTC). Extrair o ZIP e instalar **app-release.apk**, sem desinstalar a vc114. Não usar os arquivos de testes como APK.

SHA-256 APK: `5618378d70513b15f9e4498c0f09ba3213f84b828cc16cb45a7d9ed4913bd1b0`.
Certificado SHA-256: `cc4476c4eb3ac725b692914488282f0ac70a83b3682ca6b7ac0f841f9de2117c` (mesmo vc114).
[Verificação completa](FIELD-VC115-RELEASE-VERIFICATION.json), código `5f58704`, CI sem falhas. Nenhuma assinatura ou credencial sai do Secrets/runner temporário.

1. Antes: confirmar vc114 instalada e anotar quantidade de registros no Histórico e preferências atuais. Não desinstalar nem limpar dados. Instalar por cima somente app-release.apk vc115 do artefato privado com certificado igual e checksum conferido. Se Android recusar assinatura, parar, sem desinstalar.
2. Confirmar versão 115 nas configurações. Histórico e configurações anteriores devem continuar. Não alterar consentimento para tentar recuperar imagem passada.
3. Abrir registro R3 que já tinha captura original consentida e vínculo exato. Tocar “Ver captura original”. Conferir mesma oferta, detalhes de vínculo, SHA-256, Voltar. Repetir abertura, girar aparelho e usar tela dividida. Sem trava, imagem trocada ou OCR/releitura.
4. Registro R3 sem imagem, purgado ou anterior ao consentimento: botão apresenta explicação de indisponibilidade, nunca outra captura. Nenhuma reconstrução retrospectiva.
5. Registro M1: se não possui evidência íntegra com vínculo exato, explicação deve informar indisponibilidade. Digitalizados Uber: explicar que os campos foram guardados sem captura original vinculada. Não associar pelo horário/valor.
6. Sem jornada ativa, abrir/fechar o visualizador várias vezes; UI responsiva. Com consentimentos já existentes, realizar uso normal curto do Reader R3 e depois conferir a oferta e sua evidência. Não aceitar corridas apenas para esse teste. Reader/OCR/HUD devem manter o comportamento conhecido.
7. Apenas se já usa M1, iniciar pelo fluxo normal de autorização MediaProjection; autorização continua obrigatória. Não reutilizar token morto. Abrir Histórico depois e confirmar sem interferência no Reader.
8. Tema claro/escuro, fonte 150%, tablet retrato/paisagem e tela dividida: botão, explicação, imagem e Voltar acessíveis. A tela da imagem mantém proteção contra screenshots; não desabilitar FLAG_SECURE para produzir evidência.
9. Registrar versão, modo leitor, ID local mostrado, resultado e texto de indisponibilidade. Não compartilhar imagem privada, endereços ou dados de passageiros; relato redigido ao responsável. Nunca enviar chaves/arquivos internos.

Critério: vínculo exato ou explicação honesta, sem Reader/OCR/HUD regredir, sem perda do Histórico, sem nova captura/consentimento implícito. Falha de imagem trocada, crash ou perda de registros bloqueia homologação. Não fazer downgrade limpando dados; conservar vc114 como referência e consultar responsável em caso de rollback.
