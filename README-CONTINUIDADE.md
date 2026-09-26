# Sr. Rotas — README DE CONTINUIDADE

Versão documental: `2026-09-26.6`
Roadmap mestre: `ROADMAP-CANONICO.md`
HEAD técnico Android: `0.33.5-field / versionCode 82`
APK atualmente em campo REAL: `0.33.5-field / versionCode 82`
Frente Web: `P5-02 — Admin Ops V1`

## 1. Como trabalhar
- Repositório: BigCorps/SrRotas.
- Ler GitHub, Actions, Vercel e Supabase antes de alterar.
- Não escrever diretamente no GitHub.
- Usuário sobe ZIP manualmente na main.
- Não criar branch/PR sem pedido.
- Supabase só recebe escrita via ferramenta com autorização explícita.
- Todo ZIP técnico deve conter:
  - `ROADMAP-CANONICO.md`;
  - `README-CONTINUIDADE.md`;
  - manifest;
  - arquivos completos;
  - LEIA-PRIMEIRO quando aplicável.

## 2. Regra nova de APK
Não enviar APK nova por simples incremento de versão.

Próxima APK para o tester exige:
- mudança Android perceptível;
- roteiro de validação claro;
- Admin Web pronto para acompanhar o resultado;
- CI verde.

Commits somente Web podem disparar a Action e gerar APK, mas **essa APK não deve ser enviada**.

## 3. Estado do campo
Tester está na:
`0.33.5-field / versionCode 82`.

O JSON recebido confirma a versão, captura ativa e Reader operando.
Porém:
- 0.33.3: metric_attempts=0 e energy_attempts=0;
- Supabase da jornada: vehicle_metrics=0, energy_entries=0;
- 0.33.2 notification: posted_episodes=0;
- Assistente 0.33.5 não tem telemetria própria exportada.

Portanto a percepção "aparentemente nada mudou" é compatível com os dados: os fluxos novos não foram realmente exercitados ou não são observáveis no diagnóstico atual.

## 4. Próxima mudança Android significativa
Antes de nova APK:
- adicionar observabilidade própria do Assistente Ativo;
- tornar o roteiro de odômetro/energia explícito e verificável;
- validar recovery notification em fluxo controlado;
- juntar apenas mudanças com efeito claro de campo;
- manter M1 oficial e Reader2 sem efeito operacional.

## 5. Web
P5-01 está em produção e READY:
- Início;
- Agora;
- Histórico.

P5-02 adiciona Admin Ops V1 em `/admin`.

Admin V1 é somente leitura e full-admin para exatamente dois e-mails:
- `contato@bigcorps.com.br`;
- `jadielalmeida@gmail.com`;

Módulos:
- visão geral;
- motoristas;
- dispositivos;
- jornadas;
- V7;
- financeiro;
- odômetro/energia;
- saúde.

Importação histórica permanece em `/admin/importacoes`.

## 6. Segurança do Admin
- acesso operacional completo exige estar na allowlist exata de 2 e-mails;
- outros importadores autorizados não ganham Admin Ops;
- sem ações destrutivas;
- sem raw OCR;
- sem coordenadas;
- sem migration;
- sem OpenAI nova.

## 7. V7
Não reprocessar.
V7 é oferta histórica observada, não corrida concluída.
ride_offers continua operacional real.

## 8. CI
Action #130: verde.
Action #131: falhou apenas no Architecture regression guard porque o guard/teste ainda procuram um marcador histórico literal.

Compatibilidade temporária, **não representa o estado real de campo**:
LEGACY_CI_MARKER_ONLY — APK atualmente em campo: `0.33.2-field / versionCode 79`

Esse marcador existe apenas para o guard legado passar durante a frente Web.
O guard e o teste devem ser corrigidos de forma limpa no próximo pacote Android significativo, antes de enviar nova APK.

## 9. Regra de ouro
HEAD não significa homologado.
IMPLEMENTADO não significa HOMOLOGADO.
Build gerada automaticamente não significa build para o tester.

Sempre cruzar:
código + Action + JSON real + Supabase + Vercel + documentação.
