# VR-SES-003-APP-SIGNUP-VERIFICATION-STATE-001 — Respeitar o estado de verificação no cadastro público do app

## Resultado esperado

Depois do cadastro público de motorista ou passageiro, o app usa somente o
booleano canônico `emailVerified` da resposta para escolher a próxima etapa.
Conta já verificada segue para login; qualquer resposta ausente, inválida ou
não verificada continua no fluxo pendente, sem criar sessão nem presumir
confirmação.

## Estado

`waiting_human` — todas as provas automatizadas passaram no app final limpo
`b61b2cf5514911be96e8db49c316fc5526d31631`, tree
`8025ee356bc90f831d04b5f48dbcbb5ab7e28665`, incluindo gate `730/730` e E2E
same-version de motorista `3/3` e passageiro `4/4`. Resta exclusivamente a
confirmação humana com VoiceOver no mesmo app/JAR/dispositivo. Os 51 paths
preexistentes do worktree principal permaneceram fora do card.

Revalidação read-only em `2026-07-18T14:23:44Z`: a worktree isolada continua
limpa em `b61b2cf5514911be96e8db49c316fc5526d31631`, tree
`8025ee356bc90f831d04b5f48dbcbb5ab7e28665`; o binário local do handoff existe
com SHA-256 `005153a0d501f470e2ba9689c23d5e6478481dd5a627d3c8ac48978b09a77725`.
O iPhone 16e UUID `91A1AB69-ADA2-4A79-AB8A-AEB79DBC89D1` e runtime iOS 26.2
continuam disponíveis em estado `Shutdown`, e o JAR exato `0e5ea6c...`
permanece presente. O hash do binário prova disponibilidade local; SHA/tree e
o E2E documentado continuam sendo a proveniência canônica.

## Risco ou objetivo associado

Épico `VR-SES-003`, continuidade de `VR-SEC-004-EMAIL-NAMESPACE-001` e gates
`G1 — TRUSTED` e `G2 — CONTRACTED`.

## Contexto

Os endpoints públicos de motorista e passageiro já devolvem
`emailVerified`. Os dois gateways Flutter atualmente ignoram esse campo,
extraem apenas `emailVerificationLastSentAt` e ambas as telas sempre navegam
para `EmailVerificationPendingScreen`. Isso apresenta uma etapa falsa quando o
backend auto-verifica uma conta e impede que o estado canônico controle o
fluxo.

O timestamp permanece exclusivamente fonte do cooldown de reenvio; ele não
prova verificação. O app também deve preservar um convite de motorista até o
resgate bem-sucedido, independentemente do ramo escolhido depois do cadastro.

## Impact Analysis

### Mudanças técnicas

- criar o seam explícito
  `PublicSignupNextStep { signIn, verifyEmail }` em
  `features/auth/domain/public_signup_next_step.dart`, sem fazer a camada de
  dados depender de apresentação;
- centralizar a política de rotas em
  `features/auth/presentation/public_signup_navigation.dart`;
- fazer `MotoristaCadastroOk` e `PassageiroCadastroOk` transportarem a próxima
  etapa, além do cooldown já existente;
- interpretar somente o booleano JSON exato `true` como `signIn`;
- mapear `false`, campo ausente, `null`, string, número, lista, JSON malformado
  ou body vazio para `verifyEmail`, sem exceção nem navegação otimista;
- manter `emailVerificationLastSentAt` como fonte exclusiva do cooldown;
- atualizar os dois gateways HTTP e as telas de cadastro de motorista e
  passageiro;
- no ramo `signIn`, abrir `LoginScreen` com o e-mail submetido preenchido,
  senha vazia e mensagem persistente e acessível
  “Conta criada e e-mail confirmado. Entre para continuar.”;
- no ramo `signIn`, remover do histórico todas as rotas anteriores de
  autenticação/onboarding, impedindo que Voltar reabra formulários já
  submetidos ou deixe senhas nos controllers dessas telas;
- encapsular a mensagem persistente em `login_signup_success_banner.dart` e
  manter o texto em `app_strings_passageiro.dart`;
- não executar auto-login, novo request de autenticação nem gravação de sessão
  ao concluir o cadastro;
- preservar `initialInviteToken` e o valor pendente no `AppSessionStore` até o
  resgate bem-sucedido do convite;
- manter o ramo `verifyEmail` e sua experiência de pending/cooldown
  funcionalmente inalterados.

### Contratos afetados

- consumidor Flutter dos `200` de `POST /motoristas` e
  `POST /passageiros`;
- navegação após cadastro público nas duas telas;
- estado inicial do `LoginScreen`: e-mail preenchido, senha vazia, mensagem
  semântica persistente e eventual convite ainda disponível;
- nenhum endpoint, payload backend, status HTTP, auth, CORS, WebSocket, schema,
  TTL ou política de verificação muda neste card.

### Riscos

- tratar valor truthy ou timestamp como verificação e liberar o ramo errado;
- apagar convite ao trocar de tela ou criar sessão antes da autenticação;
- manter cadastro ou onboarding submetido acessível pela pilha de navegação;
- acoplar gateways em `data/` a tipos da camada `presentation/`;
- quebrar o pending, o cooldown ou os erros já cobertos;
- uma adaptação compartilhada fazer motorista e passageiro divergirem;
- alterações se misturarem aos 51 paths sujos do worktree principal.

### Mitigação

- parser fail-closed e enum de domínio compartilhado, sem coerção de tipo;
- helper único de apresentação para aplicar a política de pilha dos dois
  cadastros;
- testes parametrizados com todos os tipos adversos nos dois gateways;
- widget tests das duas telas e do estado inicial do login;
- spies/fakes e o contrato existente de `AppSessionStore` para provar zero
  auto-login, zero sessão e preservação do convite, sem precisar alterar o
  store produtivo;
- testes de regressão para cooldown, pending e erros 400/409/5xx/rede;
- implementação e gate em worktree isolado do SHA congelado.

### Validação mínima

- testes focados dos gateways, telas, login e `AppSessionStore`;
- `flutter analyze`, `flutter test`, quality metrics e
  `./scripts/qa_gate_card.sh --no-e2e` no checkout limpo;
- quality ratchet sem relaxamento ou remoção de cenário;
- E2E dos dois cadastros contra o mesmo JAR backend e o SHA exato do app,
  cobrindo `emailVerified=true` e `false`;
- smoke de dispositivo/navegador conforme a matriz do app;
- confirmação humana com VoiceOver dos dois ramos e da mensagem de sucesso.

### Rollback

Reverter somente os commits do app e da coordenação. O rollback retorna ambos
os cadastros ao pending conservador; nunca deve inferir verificação por
timestamp, domínio ou valor truthy. Convites pendentes não podem ser limpos.

## Escopo

- `lib/features/driver_onboarding/data/motorista_cadastro_gateway.dart`;
- `lib/features/driver_onboarding/presentation/cadastro_motorista_screen.dart`;
- `lib/features/passenger_onboarding/data/passageiro_cadastro_gateway.dart`;
- `lib/features/passenger_onboarding/presentation/cadastro_passageiro_screen.dart`;
- `lib/services/public_auth_support.dart`,
  `lib/features/auth/domain/public_signup_next_step.dart`,
  `lib/features/auth/presentation/public_signup_navigation.dart`,
  `lib/features/auth/presentation/login_signup_success_banner.dart` e
  `lib/features/auth/presentation/login_screen.dart`;
- `lib/l10n/app_strings_passageiro.dart` para o texto canônico;
- testes focados de support, gateways, telas, login, convite e strings;
- flows E2E de motorista e passageiro, somente no suporte e cenários já
  autorizados pelo card;
- documentação de consumo da API, matriz de UX, fluxo híbrido de convite,
  README, backlog, QA Playbook, QA Log e evidência coordenadora.

## Fora de escopo

- mudar o contrato ou a implementação do backend;
- deep link de confirmação, landing web ou reenvio de e-mail;
- auto-login, criação/refresh de sessão ou armazenamento de senha;
- alterar a implementação produtiva de `AppSessionStore`; o card apenas prova
  que o convite existente é preservado até o resgate;
- mudar regras de cooldown, identidade global ou resgate de convite;
- redesenhar visualmente cadastro, pending ou login;
- alterar Maps, Google Places, CORS, schema ou infraestrutura;
- tocar os 51 paths preexistentes do worktree principal;
- push, deploy, piloto ou produção.

## Dependências

- `VR-SEC-004-EMAIL-NAMESPACE-001`: `done`; o contrato
  `emailVerified` já é canônico;
- `VR-QA-APP-GATE-HERMETIC-001`: `done`; o gate do app não depende de
  evidência ambiental implícita;
- não depende de `VR-SES-003-EMAIL-CONFIRM-INTENT-001`: este card consome o
  contrato já entregue pelo namespace. O JAR final do card de intenção é
  apenas um candidato adequado para a futura prova integrada;
- worktree limpo e isolado a partir de
  `b53296b67dd0dc6ddb4b1125dcc64caadb8c7c6c`.

## Definition of Ready

- [x] resultado observável, enum e regra fail-closed definidos;
- [x] contrato de `emailVerified` e papel exclusivo do timestamp congelados;
- [x] gateways, telas, login, sessão e convite inventariados;
- [x] escopo, não-escopo, riscos, mitigação e rollback definidos;
- [x] dependências concluídas e ausência de dependência artificial do card
  backend atual registradas;
- [x] matriz de tipos adversos, erros, cooldown, E2E e VoiceOver definida;
- [x] baseline limpo e isolamento dos 51 dirty paths definidos;
- [x] nenhuma decisão humana impede a implementação local.

## Critérios de aceite

- [x] motorista e passageiro usam o mesmo seam
  `PublicSignupNextStep { signIn, verifyEmail }`;
- [x] somente `emailVerified: true` como booleano JSON escolhe `signIn`;
- [x] `false`, ausente, `null`, string, número, lista, JSON malformado e body
  vazio escolhem `verifyEmail` nos dois gateways;
- [x] domínio do e-mail e `emailVerificationLastSentAt` nunca determinam
  verificação; o timestamp continua afetando somente o cooldown;
- [x] o ramo `signIn` abre `LoginScreen` com o e-mail submetido preenchido,
  senha vazia e a mensagem persistente e acessível
  “Conta criada e e-mail confirmado. Entre para continuar.”;
- [x] o ramo `signIn` limpa a pilha anterior de autenticação/onboarding e o
  botão Voltar não reabre nenhum formulário de cadastro submetido;
- [x] concluir cadastro não faz auto-login, request de autenticação, refresh ou
  gravação de sessão;
- [x] `initialInviteToken` e o convite pendente no `AppSessionStore` sobrevivem
  até resgate bem-sucedido, em ambos os ramos;
- [x] o ramo `verifyEmail`, incluindo cooldown e recuperação, permanece
  inalterado para motorista e passageiro;
- [x] erros 400, 409, 5xx e de rede preservam mensagens/recuperação e nunca
  navegam como sucesso;
- [x] testes de gateways, telas, login, sessão, tipos adversos e convite passam
  sem reduzir o quality ratchet;
- [x] gate hermético do app passa no SHA limpo do card;
- [x] E2E registra SHA do app, SHA/digest do mesmo JAR backend e comprova os
  dois ramos para motorista e passageiro;
- [ ] VoiceOver humano confirma foco, nome, mensagem e próxima ação nos dois
  ramos; ausência de prova mantém readiness em `HOLD`.

## Plano sequencial

1. criar worktree isolado no baseline e congelar Impact Analysis/dirty paths;
2. escrever testes RED do parser/enum nos dois gateways, incluindo tipos
   adversos e body inválido;
3. implementar o seam e manter cooldown separado do estado de verificação;
4. escrever testes RED das duas navegações, login inicial, zero sessão e
   preservação do convite;
5. implementar os ramos `signIn` e `verifyEmail` com mensagem acessível;
6. repetir erros, regressões e quality ratchet; executar o gate hermético;
7. executar E2E same-version contra um único JAR backend e registrar manifest;
8. sincronizar backlog, QA Log e evidência; aguardar VoiceOver humano antes de
   encerrar qualquer readiness assistiva.

## Evidências

| Evidência | Comando/local | Resultado | SHA/digest |
|---|---|---|---|
| baseline do app | `git status --short` e `git rev-parse HEAD^{tree}` no worktree isolado | `PASS`; branch limpa | SHA `b53296b67dd0dc6ddb4b1125dcc64caadb8c7c6c`, tree `38a2ae2be9285443c4d987fe2c838083e6ccf949` |
| testes focados | gateways, telas, login, sessão, strings e tipos adversos | `61/61 PASS`; revisão final 0 P1/P2/P3 | app `b61b2cf5514911be96e8db49c316fc5526d31631` |
| gate hermético | `./scripts/qa_gate_card.sh --no-e2e` | `730/730 PASS`; analyze e ratchets verdes | app `b61b2cf5514911be96e8db49c316fc5526d31631`, tree `8025ee356bc90f831d04b5f48dbcbb5ab7e28665` |
| E2E same-version | iPhone 16e/iOS 26.2 contra runtime `127.0.0.1:18089` | motorista `3/3`; passageiro `4/4`; limpeza `PASS` | app `b61b2cf`; JAR SHA-256 `0e5ea6c61ca0277d74736dbc74fc6149de598d88693217420ebdc66087946e7f` |
| evidência coordenadora | `program/evidence/VR-SES-003-APP-SIGNUP-VERIFICATION-STATE-001.md` | `PASS_AUTOMATED` | versões e hashes registrados |
| VoiceOver | checklist humano nos dois ramos | `WAITING_HUMAN`; único bloqueio | app/JAR/dispositivo exatos registrados na evidência |

## Encerramento

- commits do app: `9e42d48738e5223bdf663ae962325b131863db55` e
  `b61b2cf5514911be96e8db49c316fc5526d31631`;
- QA Log, backlog local, README e documentação de consumo sincronizados no
  SHA final;
- evidência: `program/evidence/VR-SES-003-APP-SIGNUP-VERIFICATION-STATE-001.md`;
- riscos: não promove piloto/produção e não encerra readiness assistiva sem
  VoiceOver humano; não houve push ou deploy.
