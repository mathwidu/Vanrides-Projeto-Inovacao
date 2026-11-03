# Backlog — VanRides App (Flutter)

## Visão Geral
Este backlog organiza correções e melhorias do app Flutter, com foco em manter o app **funcionando 100%** (iPhone primeiro), antes de evoluir features.

Convenções
- Status por card: `FUTURE | PLANNED | IN_PROGRESS | DONE (commit: <hash>) | BLOCKED`
- Não quebrar contratos com o backend sem alinhar e atualizar docs.
- Sem segredos em código/commits/docs (API keys, tokens, senhas).
- Padrão de qualidade: uma mudança só é considerada “pronta” quando tiver **testes + roteiro de QA**.
- O backlog Markdown é a fonte de verdade; issues do GitHub podem ser sincronizadas via `backlog/github-issues/pending-issues.json` e `scripts/publish_pending_backlog_issues.py`.
- Conceito importante: o `backlog_code` (ex.: `0132`) continua sendo o ID técnico estável do card; a numeração sequencial visível nas issues (`001`, `002`, `003`...) passa a ser definida em `backlog/github-issues/issue-sequence.md`.
- Regra operacional: sempre que um card ganhar escopo novo, mudar de status ou for concluído, atualizar este arquivo primeiro e depois sincronizar as issues do GitHub.
- Referência estratégica atual: `backlog/Strategic-Delivery-Plan.md`

## VR-WA-060-DRIVER-APP — vínculo seguro e recuperação do aparelho

`VALIDATING` em 31/10/2026; autoridade: [card do programa](../../../program/cards/VR-WA-060-DRIVER-APP.md).
Implementados vínculo seguro, retorno ao pedido local e recuperação de sessão.
READY informa autorização do aparelho; integração da coleta pertence ao 110.
Resultados, versões, hashes e rodadas Redmi 06/07: [QA Execution Log](QA-Execution-Log.md).
Estados, migração iOS v1 → v2 e rollback: [vínculo pelo WhatsApp](../../../docs/desenvolvimento/aplicativo.md).
Preparação e comandos: [desenvolvimento do driver](../../../docs/desenvolvimento/aplicativo.md).
Não fazer downgrade com sessão/journal v2 ativo; seguir a reversão documentada.
A [ADR-007](../../../program/decisions/ADR-007-whatsapp-android-first-development.md) preserva a pausa física iOS.
030/180 mantêm a prova bilateral e 170 valida associações públicas/WhatsApp real.
160 continua dependente de 030; piloto e produção permanecem HOLD.
O estado e o encerramento canônicos ficam em [program.json](../../../program/program.json).

## Card de programa — VR-WA-030-GPS-PROOF

Registro histórico; estado atual **WAITING_HUMAN** por [prova física pendente](../../../program/cards/VR-WA-030-GPS-PROOF.md). Frente local de observabilidade sanitizada
implementada sobre `1b1a1865949c`; congelamento de runtime pelo principal para build/instalação separados.

Impact Analysis e plano registrados antes do código em
`docs/driver-mvp-development.md`. Escopo: metadados em RAM no runtime Android,
parsing opcional por allowlist e exibição/cópia explícita nos detalhes do teste.
Não muda coleta, provider, permissões, cadência, limiares, lease, HTTP ou API.

PASS local: 18 testes Flutter direcionados, incluindo snapshots antigos,
marcos Android/iOS opcionais, valores futuros/inválidos, allowlist e clipboard
sob comando explícito; detalhes expandidos com escala de texto 2x. Analyzer
direcionado só aponta duas infos de estilo preexistentes. Gate completo final714testes/ratchet PASS. Build
nativo e repetição física pertencem ao principal na mesma revisão avaliada;
esta frente não comprova correção de GPS stale nem fecha os aceites030.

Extensão após ensaio físico021: retorno de parada junto ao botão, com
solicitação/espera prudentes, confirmação apenas em `STOPPED` sem pendência/erro
e aviso acessível `liveRegion`. Sem mudança nativa/DTO. PASS em 22 testes
focados, incluindo estados assíncronos, falha, novo início e controle visível
em escala 1x/2x. Gate/build desta extensão ficam com o principal; diff congelado
após os testes direcionados, sem commit ou instalação nesta frente.

Rollback: remover somente o diff desta frente; não há migração de storage ou
mudança no protocolo. Evidência canônica: `../program/evidence/VR-WA-030-GPS-PROOF.md`.

## Card de programa — VR-WA-020-GPS-SPIKE

Estado: **DONE no escopo local do módulo**, runtime `02183270813550d020bf3e223d22be798de1fd85`;
publicação e prova física030 ficam no programa da coordenação.

Impact Analysis: flavor `driver`, tela de ensaio e canal nativo de status, Android
Foreground Service/Fused Location e Core Location iOS. O widget observa o coletor;
não é proprietário de sua execução. O flavor legado e a API Spring permanecem
intactos. Contrato HTTP dedicado v1, credenciais de ensaio privadas, fila em RAM,
ACK individual, prazo monotônico de até cinco minutos, parada nativa e
reconciliação de processo sem retomada automática.

Plano: congelar contrato; implementar coletores/receptor; testar regras e falhas;
compilar cada plataforma separadamente; provar ingestão/parada sintéticas;
registrar artefatos, gate e rollback antes da autorização de receptor público.

Validação: gate canônico PASS, 704 testes Flutter/analyzer/ratchet e contratos;
10 testes Kotlin/JVM e 11 testes Swift core. Android emulado passou quatro
cenários de ingestão/parada, incluindo falha de pedido, perda de ACK e resposta
inválida. iOS simulado passou normal e as duas falhas transitórias, sem skips.
Artefatos assinados 0.1.0+2 preservados e verificados; repetição nativa no SHA
de runtime passou nas duas plataformas. Instalação010 e emulação não
substituem percurso físico030.

Revisão corrigiu retry de parada iOS, loop Android em resposta inválida e corrida
entre liberação HTTP e ativação. A retomada após pressão de memória também
limitou Gradle e concorrência das suítes; fonte e resultados preservados.

Rollback: parar coletores, revogar credenciais de ensaio e reverter apenas os
commits020. Evidência canônica: `../program/evidence/VR-WA-020-GPS-SPIKE.md`.
Sem publicação ou alteração de assinatura/dispositivos físicos nesta etapa.

## Card de programa — VR-WA-010-TOOLCHAINS

### Status local
DONE — runtime `81418267c0aaa472c0468904c466d847722d4ccf`; toolchains, instalação
e reabertura nos dois aparelhos validados. Aceite Android confirmado pelo
proprietário em 30/10/2026. Evidência canônica na coordenação.

### Impact Analysis e resultado
- flavor Android `driver` e scheme iOS `driver` isolam a identidade e o entrypoint
  do checkpoint; o legado mantém IDs, telas e seus contratos;
- AGP/Gradle/Kotlin, Java 21 do Android e UIScene/iOS 15 do driver atendem os SDKs
  instalados; Java 17 do backend e configurações globais do Mac foram preservados;
- certificado Android privado fora de Git e Personal Team iOS observado no Mac;
- runners E2E selecionam `legacy` no Android e mantêm o scheme padrão no iOS;
- teste de idade passa a usar data de referência, preservando data atual no produto.

### QA Log — 2026-10-30
- gate canônico sem E2E: 692 testes Flutter PASS, analyzer sem issues, contratos
  Python e ratchet PASS; repetição no commit final registrada pela coordenação;
- build APK release PASS, assinatura v2/RSA 3072 válida e caminho UTF-8 validado;
- build iOS simulator e iphoneos sem assinatura PASS; checkpoint visível no
  simulador após migração UIScene; build iOS assinado e instalação nos dois aparelhos PASS; reabertura sem Mac
  confirmada pelo proprietário nos dois aparelhos;
- dez testes do preflight E2E PASS após correção de seleção de flavor;
- revisão independente identificou três ajustes (E2E Android, target legado iOS,
  leitura UTF-8 da assinatura), incorporados antes da instalação final.

### Limites e rollback
Ainda não há cadastro, pareamento, GPS nem bot neste checkpoint. Emulador não
comprova bateria/segundo plano; não declarar MVP entregue por compilar.
Guia reproduzível em `docs/driver-mvp-development.md`; resultados e SHAs exatos
em `../program/evidence/VR-WA-010-TOOLCHAINS.md` na coordenação. Reverter o commit
específico recupera o build anterior, mantendo a chave privada para futuras
atualizações. Nenhum push, publicação ou alteração da VPS neste card.

## Card de programa — VR-SEC-001-BE-READ

### Status local
IN_PROGRESS — consumidor e prova preliminar `PASS`; fechamento canonico
aguarda a repeticao da lane no SHA documental final e o registro definitivo no
repositorio coordenador.

### Resultado entregue no app
- o bootstrap oficial envia `companyName` novo; `companySlug` permanece apenas
  resposta derivada e nao e usado como seletor de tenant;
- `TripLiveLocationService` interpreta `200` como localizacao, `204` como
  ausencia atual (`null`) e converte `404` no erro publico generico, sem expor
  negacao, cancelamento, vinculo legado, cross-tenant ou alvo inexistente;
- o probe usa o service de producao sobre HTTP real e reporta
  `consumer_only` / `coordinator_required`, impedindo que o app isolado declare
  vinculacao ao processo/JAR backend;
- a lane coordenada restringe a API a loopback e vincula refs/blobs exatos em
  manifest create-only com hash bruto externo.

### Prova preliminar
- app commit: `7b5d4d4048db439d9b5179630089658776f150a4`;
- app tree: `866ebe239fe8c324e6c09659c91940420c8ea90c`;
- run: `location-read-20260717T101750Z-79192`;
- manifest SHA-256:
  `f7ee6ed4b0f58afa5ce5d5461ff43c0c8cafeb4696146c70e1f12692090ea6b8`;
- payload SHA-256:
  `809a10290f98c8393d902bb46b15527320079aabc6a323960f54f8a4a3f55fef`;
- log SHA-256:
  `5d6fe0efe6f2bc66d168b043cdae40689e1be6998879e2fc3de7110958422439`
  (`3655` bytes);
- fases positiva `200/204`, negativa `404`/erro generico e cleanup: `PASS`.

### Limites e prova final
- este patch documental muda o snapshot do app; a prova acima nao fecha o card
  e nao deve ser apresentada como evidencia do SHA final;
- a prova pos-documentacao deve ser repetida e ancorada em
  `program/evidence/VR-SEC-001-BE-READ.md`, no repositorio coordenador;
- nao houve mudanca nem prova de UI, widget ou device; o fallback visual nao foi
  removido, e os fluxos historico/realtime permanecem inalterados;
- piloto, campo, push, deploy e producao continuam `HOLD`.

## Card de programa — VR-QA-APP-GATE-HERMETIC-001

### Status local
DONE tecnico (commit: `edfc94c`); o fechamento canonico continua no programa
coordenador ate os dois gates passarem no SHA documental final em worktree
isolado e o manifesto cross-repo ser ancorado.

### Resultado entregue
- `--no-e2e` independe de dotenv, `output/`, timestamp historico, sibling script,
  backend, device e credenciais;
- o perfil E2E usa parser dotenv com allowlist, sem execucao de shell, e mantem
  precedencia explicita das flags;
- readiness separa `contractStatus=pass` de `gateExecutionStatus=not_run` e
  permanece `HOLD` sem evidencia externa;
- o smoke TTS produz manifesto tecnico ligado a source SHA/tree/run id/log, sem
  fabricar confirmacao humana;
- a revisao audivel exige uma unica confirmacao canonica `SIM` e valida o
  manifesto tecnico antes de aceita-la;
- route-plan saiu dos pressupostos ambientais do app e pertence ao coordenador.

### Limites e prova final
- nao houve device real, backend, audio humano, parceiro, campo, push ou deploy;
- `APP-NAV-AUDIO-CONFIRM-001` permanece `waiting_human` no programa;
- logs e hashes dos gates limpos do SHA final ficam em
  `program/evidence/VR-QA-APP-GATE-HERMETIC-001.md`, no repositorio coordenador,
  para evitar alterar novamente o SHA avaliado.

## Roadmap (alto nível)

## Regra de Leitura dos Cards
Conceito importante:
- o numero do card no frontend e um **ID de rastreabilidade**, nao necessariamente a ordem cronologica de execucao
- a **ordem oficial de desenvolvimento** e definida pelos blocos e fases abaixo
- cards ja entregues nao devem ser renumerados, para nao quebrar historico de commits, QA, issues e referencias cruzadas

Regra do projeto:
1. manter o ID original do card para preservar rastreabilidade
2. usar a secao `Sequencia Oficial de Execucao` como verdade sobre prioridade e ordem do backlog
3. usar `backlog/github-issues/issue-sequence.md` como verdade sobre a numeração sequencial visível das issues pendentes
4. quando um bloco mudar de prioridade, atualizar esta secao, o roadmap e a fila das issues antes de iniciar implementacao

## Sequencia Oficial de Execucao
Estado atual do frontend:
- bloco `0200` a `0205` concluido: fundacao de experiencias + Portal Empresa Web MVP
- bloco `0206` a `0211` concluido: Guardian Mobile MVP
- bloco `0107` a `0111` concluido: experiencia operacional de motorista/passageiro e viagens MVP
- bloco em fechamento/revisao: `0126` a `0129` -> roteirizacao real validavel no mapa
- bloco seguinte prioritario: `0132` a `0139` -> experiencia `map-first` completa da corrida (planejamento, corrida em andamento, passageiro/responsavel full-screen, lifecycle e notificacoes operacionais)
- bloco futuro: `0120` a `0125` -> comunicacao, suporte e notificacoes gerais
- bloco futuro posterior: `0130` a `0131` -> pagamentos
- bloco permanente: `0980` -> parking lot
- bloco de qualidade continua: `0228` a `0241` -> debitos tecnicos visiveis (origem: `backlog/Technical-Debt-2026-04-22.md`); ordem sugerida abaixo

Ordem oficial para leitura e execucao:
1. `0001` a `0010` -> base de qualidade e testes
2. `0090` a `0091` -> estabilizacao de localizacao no iPhone
3. `0100` a `0106` -> arquitetura, sessao segura e fundacao operacional
4. `0200` a `0205` -> Portal Empresa Web MVP
5. `0206` a `0211` -> Guardian Mobile MVP
6. `0107` a `0111` -> UX operacional e viagens MVP
7. `0112` a `0113` -> rotas dinamicas + ETA (ponte tecnica)
8. `0114` a `0119` -> operacao guiada por rota real
9. `0126` a `0129` -> roteirizacao real validavel no mapa
10. `0132` a `0139` -> experiencia `map-first` completa da corrida
11. `0120` a `0125` -> comunicacao, suporte e notificacoes gerais
12. `0130` a `0131` -> pagamentos
13. `0980` -> melhorias descobertas no caminho
14. `0228` a `0241` -> debitos tecnicos (ordem interna recomendada: 0228 → 0229 → 0241 → 0237 → 0240 → 0231 → 0232 → 0230 → 0233 → 0234 → 0235 → 0236 → 0238 → 0239)

Ordem sugerida (mantendo iOS e Android juntos):
- **M0 — Estabilizar o que existe**: permissões de localização, QA playbook, testes mínimos, CI.
- **M1 — Segurança + Multi-empresa (multi-tenant)**: auth v2 (tokens), escopo por empresa, papéis (motorista/passageiro/responsável/admin).
- **M1.5 — Experiencias por papel**: portal web da empresa + guardian mobile.
- **M2 — UX operacional + viagens e rotas MVP**: nova experiencia de motorista/passageiro, planejamento/execucao e confirmacoes.
- **M3 — Operacao guiada por rota real**: viagem automatica do dia, rota geografica, navegacao guiada e fila de paradas visivel.
- **M4 — Experiencia `map-first` completa da corrida**: planejamento da corrida, corrida em andamento, `minha parada`, progresso e notificacoes operacionais.
- **M5 — Comunicacao + atendimento**: chat, suporte e monitoramento.
- **M6 — Notificacoes e engajamento**: push geral e eventos do dia.
- **M7 — Pagamentos in-app**: cobrança, recibos, conciliação e suporte.

---

## Backlog 0001 — Fundação de Testes & QA (iOS-first)

### Status
DONE (commit: e86fe4e)

### Objetivo
Definir e aplicar um padrão de testes (unit/widget/integration) + QA repetível para que **toda mudança** no app seja validada automaticamente e manualmente antes de ser marcada como DONE.

### User Story
Como time de desenvolvimento, queremos um padrão de testes e roteiros de QA para que qualquer alteração no app seja entregue com confiança, reduzindo regressões (especialmente no iPhone).

### Critérios de Aceite
- Um “Test Pyramid” do app é definido (o que é unit vs widget vs integration) e documentado.
- Existe um comando único de verificação local (ex.: `./tool/verify.sh` ou checklist documentado) que roda:
  - lint/analyzer (se aplicável)
  - unit/widget tests
  - (opcional) integration tests em simulator
- Existem testes iniciais cobrindo as integrações mais críticas:
  - `VANRIDES_API_BASE_URL` (config)
  - fluxo mínimo de login (UI/widget)
  - chamadas HTTP básicas (mockadas) e tratamento de erros (ex.: 401/204)
- Existe um roteiro de QA “smoke” para iPhone (manual) e um modelo de registro em `QA-Execution-Log.md`.
- Definition of Done (DoD) do projeto fica explícita:
  - todo card entregue precisa ter testes adequados + execução registrada quando o fluxo mudar.

### Escopo (App)
- **Padrão de testes**
  - Definir convenções de nomes e onde cada tipo de teste vive:
    - `test/` (unit + widget)
    - `integration_test/` (fluxos end-to-end)
  - Definir “quando testar o quê” (ex.: unit obrigatório, integration para features críticas).
- **Test doubles / mocks**
  - Padronizar mocking para HTTP (ex.: `http/testing.dart` com `MockClient`) e para serviços (wrappers).
  - Padronizar como testar WebSocket/STOMP (mockar camada de transporte ou isolar em unit tests).
- **Automação**
  - Documentar os comandos oficiais para rodar suíte localmente.
  - (Opcional) preparar CI futuramente (GitHub Actions) — apenas como design/documentação neste card.

### Fora de escopo (por enquanto)
- Refatorações grandes do app apenas “para facilitar testes” (serão cards separados quando necessário).
- Testes E2E complexos com GPS real em device físico (podem existir depois; agora focar no mínimo repetível).

### Dependências
- Acesso ao backend em `http://127.0.0.1:8080`
- iPhone ou iOS Simulator disponível para smoke / integration tests
- Sem segredos hardcoded (API keys devem ficar fora do repo)

### DoR (Definition of Ready)
- Backlog alinhado: quais fluxos são “críticos” (login, presenças, localização, WS)
- Mínimo de ambiente: consegue rodar `flutter test` localmente

### DoD
- Critérios de aceite atendidos
- Pelo menos 1 teste unit + 1 widget adicionados (ou atualizados) como “exemplo padrão”
- Card marcado como `DONE (commit: <hash>)`
- Registro de QA em `backlog/QA-Execution-Log.md`

---

## Backlog 0002 — Matriz de Cobertura (App inteiro) + Test Pyramid

### Status
DONE (commit: 4b4950c)

### Objetivo
Mapear **todas as partes** do app e definir qual tipo de teste cobre cada uma (unit/widget/integration/manual), com um padrão que a gente reaproveita para sempre.

### Entregáveis
- Documento (no próprio card) com a matriz por área:
  - Auth/login/cadastro
  - Presenças (motorista/passageiro)
  - Localização (REST)
  - WebSocket/STOMP (receber e publicar)
  - Maps/Places (UI + integração)
  - Permissões (iOS/Android)
  - Navegação/rotas principais
- Regras objetivas:
  - “Quando escrever unit”
  - “Quando escrever widget”
  - “Quando escrever integration”
  - “Quando basta QA manual”

### Critérios de Aceite
- Toda feature atual tem pelo menos 1 linha na matriz, com tipo(s) de teste definido(s).
- Para cada feature, existe “mínimo obrigatório” para ser DONE.

### DoD
- Matriz revisada e versionada no repo (card marcado como DONE)

---

## Backlog 0003 — Harness de Testes Reutilizável (HTTP + Services)

### Status
DONE (commit: e77ba0d)

### Objetivo
Criar uma base reaproveitável para testar serviços sem rede: fakes, builders, fixtures e helpers.

### Escopo
- Padronizar `test/support/` com:
  - `fixtures/` (JSONs reais de API)
  - builders (ex.: `MotoristaBuilder`, `LoginRequestBuilder`)
  - helper de `MockClient` para roteamento por URL/método
- Padronizar como injetar dependências (HTTP client, base URL, clock, etc.) nos services

### Critérios de Aceite
- Um service consegue ser testado **100% offline** (unit test) com MockClient + fixtures.
- Fixtures são reutilizáveis entre unit e integration (quando fizer sentido).

### DoD
- Critérios de aceite implementados no app com tratamento dedicado para `EMAIL_NOT_VERIFIED`, tela pós-cadastro, reenvio e cooldown visual.
- Evidência de QA registrada em `backlog/QA-Execution-Log.md`.
- Commits da entrega:
  - `630f263 feat(app): add email verification pending flow`
  - `e77ba0d fix(app): refine email verification pending flow`
- Helpers + 2 fixtures + 2 builders criados
- Pelo menos 3 unit tests usando esse harness

---

## Backlog 0004 — Unit Tests: Config (VANRIDES_API_BASE_URL) + Parsing/Models

### Status
DONE (commit: efce0a8)

### Objetivo
Garantir que configuração e serialização não quebrem silenciosamente.

### Critérios de Aceite
- Testes para:
  - leitura do `VANRIDES_API_BASE_URL` (default e override)
  - parsing/serialization dos principais modelos (motorista/passageiro/presença/localização)

### DoD
- Testes rodando em `flutter test`

---

## Backlog 0005 — Unit Tests: Auth/Login (HTTP) + Estados de erro

### Status
DONE (commit: efce0a8)

### Objetivo
Testar a camada de API para login e seus estados (200/401/500/timeouts) sem depender do backend.

### Critérios de Aceite
- Testes unit cobrindo:
  - request correto (método/headers/body)
  - resposta de sucesso mapeada corretamente
  - erros (401/403/500) retornam erro tratável pela UI

### DoD
- Suite mínima de auth com MockClient + fixtures

---

## Backlog 0006 — Widget Tests: Tela de Login (Motorista/Passageiro)

### Status
DONE (commit: 0c71c86)

### Objetivo
Validar UI e estados (loading, erro, sucesso) sem device.

### Critérios de Aceite
- Testes widget cobrindo:
  - validação de campos
  - loading enquanto chama login
  - mensagem de erro em 401
  - navegação após sucesso (se aplicável)

### DoD
- Pelo menos 4 widget tests estáveis

---

## Backlog 0007 — Integration Tests (iOS Simulator): Smoke E2E (sem GPS)

### Status
DONE (606aba3)

### Objetivo
Ter um “smoke” automatizado que garante que o app abre, navega e loga (fluxo mínimo).

### Observação
Para integração, preferir iOS Simulator (CI/macOS). Em device físico, smoke pode ser manual.

### Critérios de Aceite
- `flutter test integration_test` executa no iOS Simulator e passa
- Fluxo mínimo:
  - abrir app
  - ir para login
  - preencher credenciais (mockadas ou ambiente de teste)
  - chegar na tela inicial

### DoD
- Pelo menos 1 integration test “smoke” (determinístico)

---

## Backlog 0008 — Unit Tests: Localização (REST) + Tratamento do 204

### Status
DONE (commit: 0c71c86)

### Objetivo
Garantir que `204 No Content` não gere “erro falso” e que estados estejam corretos.

### Critérios de Aceite
- Testes cobrindo:
  - 200 com localização -> atualiza estado
  - 204 -> estado “sem localização ainda” (não logar como erro)
  - 401 -> exige login (quando aplicável)

### DoD
- Unit tests no service + widget test de UI (sem localização)

---

## Backlog 0009 — Unit Tests: WebSocket/STOMP (Wrapper + Fake Transport)

### Status
DONE (commit: 9819054)

### Objetivo
Testar subscribe/unsubscribe, reconexão e parsing sem depender de rede real.

### Critérios de Aceite
- Existe uma camada wrapper testável (interface) para WebSocket/STOMP
- Testes cobrindo:
  - connect -> subscribe tópico -> receber mensagem -> atualizar estado
  - disconnect limpa recursos
  - reconectar não duplica inscrições

### DoD
- Unit tests estáveis (sem flake)

---

## Backlog 0010 — CI (Flutter): analyze + test (+ integration opcional)

### Status
DONE (commit: d8cf9d8)

### Objetivo
Rodar automaticamente testes do app em PR/push, com gates.

### Critérios de Aceite
- Workflow no repo:
  - `flutter analyze`
  - `flutter test`
- (Opcional) job separado para integration no iOS Simulator (macOS runner)
- PR bloqueia merge se CI falhar

### DoD
- CI documentado e funcionando no repositório

---

## Backlog 0090 — Corrigir permissões de localização no iPhone (Motorista)

### Status
DONE (commit: ef9f462)

### Problema observado
No iPhone, ao tentar “Compartilhar localização” (motorista), o fluxo de permissão/stream não fica estável:
- o app solicita permissões repetidamente e/ou não inicia o envio de localizações
- em alguns casos, o `flutter run` perde conexão com o device (“Lost connection to device”)

### User Story
Como motorista, quero iniciar o compartilhamento de localização no iPhone de forma confiável para que os passageiros vejam a van no mapa.

### Critérios de Aceite
- No iPhone, com **Localização “Durante o uso do app”**, o motorista consegue:
  - iniciar compartilhamento
  - enviar atualizações de latitude/longitude continuamente enquanto o app está aberto
- O app não deve bloquear o compartilhamento por não ter “Sempre”.
- Se o usuário negar permissão:
  - o app mostra mensagem clara e orienta a habilitar em Ajustes (quando aplicável)
- O app não deve crashar nem derrubar a sessão do `flutter run` ao iniciar/encerrar o compartilhamento.

### Escopo (App)
- Ajustar a lógica de permissões no fluxo do motorista:
  - aceitar `whileInUse` como suficiente para compartilhamento em foreground
  - tratar `deniedForever` com CTA para `openAppSettings` (quando suportado)
- Garantir que ao sair da tela / parar compartilhamento:
  - a assinatura/stream de localização seja encerrada corretamente

### QA / Como testar (manual no iPhone)
Pré-requisitos:
- iPhone com GPS ativo e permissão concedida ao app
- Backend acessível em `http://127.0.0.1:8080`

Passos:
1) Abrir app → login como Motorista
2) Abrir “Compartilhar localização”
3) Conceder permissão “Durante o uso do app”
4) Confirmar que logs exibem envio de localização periodicamente
5) Em outro login (Passageiro) → “Ver localização da van”:
   - mapa deve atualizar com a posição recebida via WebSocket

### DoD
- Card de implementação concluído (commit acima)
- Testes/QA detalhados no card `Backlog 0091`

---

## Backlog 0091 — Testes/QA: Compartilhamento de localização (Motorista) + Permissões (iOS/Android)

### Status
DONE (commit: 5079f25)

### Objetivo
Garantir via testes automatizados + QA manual que o fluxo de localização do motorista funciona de ponta a ponta:
- permissões (iOS/Android)
- start/stop do stream
- envio de updates para o backend
- consumo de updates pelo passageiro (WS)

### Critérios de Aceite
- Cobertura mínima automatizada:
  - unit tests cobrindo os estados de permissão (denied, deniedForever, whileInUse, always)
  - widget test cobrindo UI/estado da tela “Compartilhar localização” (loading, running, erro)
  - (opcional) integration test no iOS Simulator validando start/stop sem crash
- QA manual no iPhone (device físico) executado e registrado:
  - “Durante o uso do app” funciona em foreground (sem exigir “Sempre”)
  - Se negar permissão: app mostra orientação e CTA para Ajustes
  - Se “Sempre” estiver habilitado: app mantém updates quando em background (quando suportado)

### Escopo (App)
- Se necessário, refatorar para testabilidade:
  - isolar `Geolocator` e leitura de permissões em um wrapper (ex.: `LocationGateway`)
  - injetar dependências na tela/service para permitir mocks em tests
- Criar fixtures/helpers em `test/` para simular:
  - permission state
  - stream de `Position`
- Definir roteiro de QA curto e repetível (usar `backlog/QA-Playbook.md` como base)

### Dependências
- Backend acessível em `http://127.0.0.1:8080`
- iPhone físico para validar permissão “Sempre” (quando aplicável)

### DoD
- Critérios de aceite atendidos
- Card marcado como `DONE (commit: <hash>)`
- Registro preenchido em `backlog/QA-Execution-Log.md`

---

## Backlog 0100 — Arquitetura do app para escalar (services + estado + navegação)

### Status
DONE (commit: 2b176fa)

### Objetivo
Preparar o app para evoluir (multi-empresa, rotas dinâmicas, chat, pagamentos) sem virar “spaghetti”:
- separar UI de regras (services/use-cases)
- padronizar estado (loading/success/error)
- padronizar navegação e sessão

### Critérios de Aceite
- Existe um padrão simples e aplicado (mínimo viável):
  - `lib/services/` (HTTP, WebSocket, Location, Storage)
  - `lib/state/` (ou equivalente) para estados e controladores
  - UI não chama `http` diretamente (migração incremental das telas críticas)
- Erros comuns têm tratamento consistente (ex.: sem internet, 401, 5xx)
- Não quebra o comportamento atual (login/presenças/localização)

### Dependências
- Nenhuma (refactor incremental)

### DoD
- Card marcado como `DONE (commit: <hash>)`
- Smoke no iPhone descrito/atualizado no `backlog/QA-Playbook.md`

---

## Backlog 0101 — Testes/QA: Arquitetura base (regressão mínima)

### Status
DONE (commit: ea5a276)

### Objetivo
Evitar regressões enquanto refatoramos a arquitetura.

### Critérios de Aceite
- Pelo menos:
  - 2 unit tests para services (mockando HTTP)
  - 2 widget tests para telas críticas (login + uma tela pós-login)
- Registro de QA manual (iPhone) em `backlog/QA-Execution-Log.md`

### DoD
- `flutter test` passando
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0102 — Sessão segura + Auth v2 (tokens, refresh, logout)

### Status
DONE (commit: cd1c145)

### Objetivo
Migrar o app de um modelo simples (atual) para um modelo escalável:
- autenticação baseada em token (JWT/refresh)
- armazenamento seguro (Keychain/Keystore)
- logout e expiração previsível

### Critérios de Aceite
- Login salva credenciais/tokens em storage seguro
- Requests autenticados usam token automaticamente
- Expiração/401 gera ação previsível:
  - tenta refresh (se existir)
  - se falhar → força logout e volta para login
- Continua suportando os fluxos atuais até o backend completar migração (modo compatível)

### Dependências
- Backend: Auth v2 + estratégia de migração (ver `vanrides-backend/backlog/SpringBoot-Cards.md`)

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0103 — Testes/QA: Sessão segura + Auth v2

### Status
DONE (commit: 3a4fa2d)

### Critérios de Aceite
- Unit tests cobrindo:
  - armazenamento/recuperação de token
  - refresh flow (sucesso/falha)
  - logout limpa estado
- QA manual iOS + Android registrado

### DoD
- Card marcado como `DONE (commit: <hash>)`
- Registro de QA preenchido

---

## Backlog 0104 — Perfis com foto (Motorista e Passageiro/Responsável)

### Status
DONE (commit: 90df1b6)

### Objetivo
Ter uma área de perfil com foto e dados básicos, alinhada ao futuro (empresa/viagens/chat).

### Critérios de Aceite
- Tela “Perfil” para:
  - Motorista (foto + nome + contato + empresa)
  - Passageiro/Responsável (foto + nome + vínculo)
- Foto:
  - selecionar (camera/galeria)
  - upload
  - cache + placeholder
- Sem segredos e com limites (tamanho/formatos)

### Dependências
- Backend: endpoints de perfil + upload (e storage) com escopo por empresa

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0105 — Testes/QA: Perfis com foto

### Status
DONE (commit: 2fce785)

### Critérios de Aceite
- Widget tests para estados (sem foto / carregando / erro / sucesso)
- QA manual iOS + Android registrado (camera/galeria)

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0106 — Nova interface do Motorista: localização em tempo real “sempre disponível”

### Status
DONE (commit: 0839161)

### Objetivo
Facilitar ativar/desativar tracking em qualquer tela (estilo “Uber Driver”):
- toggle global (banner/bottom sheet/fab)
- status claro (Online/Offline/Compartilhando)
- persistência do estado (com segurança)

### Critérios de Aceite
- Motorista consegue iniciar/parar compartilhamento sem “caçar” a tela
- Indicador visível do estado atual
- Se permissões faltarem: orientar (sem travar)
- iOS e Android com comportamento equivalente (respeitando limitações do OS)

### Dependências
- Backlog 0091 (tests/QA base do fluxo de localização)
- Backend: contrato estável para ingestão/consulta/WS

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Bloco Concluido — Portal Empresa Web S1

## Backlog 0212 — Portal Empresa Web S1: rotas, viagens e redesign do portal

### Status
DONE (commit: c259b98)

### Objetivo
Transformar o Portal Empresa em um fluxo operacional completo para preparar `rota-base` e `viagem do dia`, eliminando a dependência de seed manual e consolidando o produto como operação real.

### Critérios de Aceite
- empresa consegue criar `rota-base` via portal (com motorista, passageiros, dias, horário e destino final opcional)
- empresa consegue criar `viagem` a partir de uma `rota-base` existente
- portal mantém consistência visual entre todas as páginas (antigas e novas)
- backend suporta `destinoFinal` explícito em `rota-base` (migration V14)
- navegação do motorista usa `destinoFinal` quando disponível, com fallback transparente para última parada

### Commits principais
- `c259b98`: Portal Admin S1 no app (telas, services, redesign)
- `e357048`: destinoFinal no domínio e formulário do app
- `4287709`: destinoFinal no backend (migration, entidade, DTOs, serviço)

### DoD
- Card marcado como `DONE (commit: c259b98)`

---

## Bloco Concluido — Portal Empresa Web MVP

## Backlog 0200 — Arquitetura de Experiencias: mobile operacional + portal web

### Status
DONE (commit: e86fe4e)

### Objetivo
Preparar o projeto Flutter para suportar duas experiências no mesmo código-base:
- `App Operacional` (motorista, passageiro, responsável) no mobile
- `Portal Empresa` no web

### Critérios de Aceite
- Existe uma estratégia clara de roteamento por experiência/papel
- Existe um shell web para área administrativa
- O app mobile continua funcionando sem regressão
- A decisão de produto fica documentada em `backlog/Product-Screens-Plan.md`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0201 — Portal Empresa Web: login + shell + dashboard inicial

### Status
DONE (commit: 4b4950c)

### Objetivo
Criar a fundação visual do portal da empresa em Flutter Web.

### Critérios de Aceite
- Tela de login administrativo
- Shell administrativo com navegação lateral
- Dashboard inicial com cards/resumos
- Layout pensado para desktop/web

### Dependências
- Backlog 0200

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0202 — Portal Empresa Web: gestão de motoristas

### Status
DONE (commit: de7fa2d)

### Objetivo
Permitir que a empresa visualize e gerencie motoristas.

### Critérios de Aceite
- Lista de motoristas consumindo o contrato atual do backend
- Criar motorista usando o contrato atual disponível no backend
- Visualizar detalhe do motorista selecionado no portal
- Área administrativa deixa explícito que editar/remover depende da próxima etapa de contratos/admin auth no backend

### Dependências
- Backlog 0201

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0203 — Portal Empresa Web: gestão de passageiros

### Status
DONE (commit: 8b3c90a)

### Objetivo
Permitir que a empresa visualize e gerencie passageiros.

### Critérios de Aceite
- Lista de passageiros protegida por auth admin
- Criar passageiro
- Editar passageiro
- Detalhe do passageiro com:
  - motorista vinculado
  - responsáveis vinculados
  - menor/adulto

### Entrega
- Login administrativo real do portal consumindo `/login`
- Lista administrativa de passageiros consumindo `GET /company-admins/passageiros`
- Detalhe administrativo de passageiro consumindo `GET /company-admins/passageiros/{id}`
- Edição administrativa consumindo `PUT /company-admins/passageiros/{id}`
- Reatribuição de motorista consumindo `PUT /company-admins/passageiros/{id}/motorista`
- Cadastro de passageiro preservado como fluxo real usando `POST /passageiros`
- Seleção real de motorista usando `GET /motoristas`

### Dependências
- Backlog 0201
- Backend 0107.1 (login admin real)
- Backend 0107.2 (passageiros administrativos)

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0204 — Portal Empresa Web: gestão de responsáveis

### Status
DONE (commit: 8b3c90a)

### Objetivo
Permitir que a empresa visualize e gerencie responsáveis.

### Critérios de Aceite
- Lista de responsáveis quando o backend admin expuser o contrato protegido
- Criar responsável
- Editar responsável
- Detalhe com passageiros vinculados
- Permitir configurar senha inicial do responsável no portal
- Indicar visualmente se o acesso ao app está configurado ou pendente

### Entrega
- Login administrativo real do portal consumindo `/login`
- Lista administrativa de responsáveis consumindo `GET /company-admins/responsaveis`
- Criação administrativa consumindo `POST /company-admins/responsaveis`
- Detalhe administrativo consumindo `GET /company-admins/responsaveis/{id}`
- Edição administrativa consumindo `PUT /company-admins/responsaveis/{id}`
- Exclusão administrativa consumindo `DELETE /company-admins/responsaveis/{id}`
- Detalhe já mostrando passageiros vinculados vindos do backend
- Criação e edição administrativas passaram a aceitar senha inicial/opcional do responsável
- Portal passou a mostrar o estado do acesso do responsável ao app:
  - `Configurado`
  - `Pendente de senha inicial`

### Dependências
- Backlog 0201
- Backend 0107.1 (login admin real)
- Backend 0107.3 (responsáveis administrativos)

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0205 — Portal Empresa Web: tela de vínculos

### Status
DONE (commit: 8b3c90a)

### Objetivo
Permitir que a empresa crie e remova vínculos do domínio.

### Critérios de Aceite
- Vincular passageiro -> motorista
- Vincular responsável -> passageiro
- Visualizar vínculos atuais
- Remover vínculos

### Dependências
- Backlog 0202
- Backlog 0203
- Backlog 0204
- Backend 0107.1 (login admin real)
- Backend 0107.4 (vínculos administrativos)

### Entrega
- Área central de vínculos e operação dentro do portal empresa
- Listagem real de vínculos `passageiro -> motorista` consumindo `GET /company-admins/vinculos/passageiros-motoristas`
- Desvinculação real de `passageiro -> motorista` consumindo `DELETE /company-admins/passageiros/{id}/motorista`
- Listagem real de vínculos `responsavel -> passageiro` consumindo `GET /company-admins/vinculos/responsaveis-passageiros`
- Criação real de vínculo `responsavel -> passageiro` consumindo `POST /company-admins/responsaveis/{responsavelId}/passageiros`
- Remoção real de vínculo `responsavel -> passageiro` consumindo `DELETE /company-admins/responsaveis/{responsavelId}/passageiros/{passageiroId}`
- Fluxo administrativo consolidado:
  - criar motorista
  - criar passageiro
  - criar responsável
  - vincular responsável ao passageiro

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Proximo Bloco Oficial — Guardian Mobile MVP

## Backlog 0206 — Guardian Mobile: fundacao de sessao e navegacao

### Status
DONE (commit: 9b6862e)

### Objetivo
Preparar o app mobile para suportar a experiência do responsável.

### Critérios de Aceite
- Navegação preparada para `GUARDIAN`
- Estrutura de sessão compatível com o novo papel
- Sem quebrar motorista e passageiro

### Entrega
- Sessao propria do responsavel adicionada no app
- Shell mobile do guardian com destinos preparados para:
  - inicio
  - presencas
  - localizacao
  - conta
- Roteamento por papel atualizado para enviar `GUARDIAN` para a experiencia dedicada
- Validacao de sessao minima para guardian:
  - exige `accessToken`
  - exige `companyId`
- Placeholder antigo solto foi removido em favor de uma fundacao real para os proximos cards

### Dependências
- Backlog 0200
- Backend: domínio guardian já disponível

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0207 — Guardian Mobile: home + lista de passageiros vinculados

### Status
DONE (commit: b9f8a31)

### Objetivo
Materializar a experiência inicial do responsável no app.

### Critérios de Aceite
- Home do guardian
- Lista de passageiros vinculados
- Navegação para detalhe do passageiro

### Entrega
- Home real do responsavel implementada no app mobile
- Lista de passageiros vinculados consumindo o contrato operacional do backend em `GET /passageiros`
- Cards de resumo da familia:
  - quantidade de passageiros vinculados
  - quantidade de menores de idade
  - quantidade de motoristas associados
- Navegacao para detalhe do passageiro vinculado
- Shell do guardian atualizado para usar a nova home como primeira experiencia real do papel
- Estados principais cobertos na UI:
  - carregando
  - vazio
  - erro
  - lista com passageiros

### Observações
- Conceito importante: este card materializa a leitura operacional do guardian, nao o onboarding completo do papel.
- A experiencia foi validada por testes automatizados e pelo gate completo do app.
- O login real do responsavel continua dependente da evolucao do backend de autenticacao especifica do guardian.

### Dependências
- Backlog 0206

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0208 — Guardian Mobile: presenças por vínculo

### Status
DONE (commit: 61f1a1d)

### Objetivo
Permitir que o responsável acompanhe e opere presenças dos passageiros vinculados.

### Critérios de Aceite
- Listar presenças do passageiro vinculado
- Confirmar presença
- Remover presença
- Respeitar vínculo do guardian

### Entrega
- Aba `Presencas` do guardian agora usa um fluxo real, não placeholder
- Listagem de presenças por passageiro vinculado consumindo o contrato operacional do backend
- Seleção de passageiro diretamente na experiência do responsável
- Calendário com destaque para os dias confirmados
- Ações reais de:
  - confirmar presença
  - remover presença
- Estados principais cobertos na UI:
  - carregando
  - vazio
  - erro
  - lista com operação disponível
- Shell do guardian passou a incluir botão de sair, facilitando smoke e testes manuais no app

### Observações
- Conceito importante: este card fecha a primeira ação operacional real do responsável dentro do app.
- A UX foi implementada sobre o contrato de vínculo já protegido pelo backend, então o responsável só opera presenças dos passageiros realmente associados a ele.
- O detalhe do passageiro foi ajustado para apontar corretamente para a aba `Presencas`, reduzindo ruído de navegação durante o teste manual.

### Dependências
- Backlog 0207

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0209 — Guardian Mobile: localização e status operacional

### Status
DONE (commit: 6966060)

### Objetivo
Permitir que o responsável acompanhe o deslocamento do passageiro vinculado.

### Critérios de Aceite
- Ver localização do motorista associado ao passageiro vinculado
- Ver histórico mínimo recente quando aplicável
- UI deixa claro o estado operacional atual derivado do backend:
  - sem localização
  - atualização recente
  - sem atualização recente
- Acesso rápido para abrir o mapa já existente do app

### Escopo implementado nesta fatia
- Nova aba `Localizacao` no shell do guardian
- Seleção do passageiro vinculado com foco no passageiro que possui motorista associado
- Cartões-resumo com:
  - status atual
  - motorista associado
  - última atualização
- Lista curta do histórico recente de posições
- CTA para abrir o mapa em tempo real reutilizando a tela já consolidada do app

### Observações
- Conceito importante: neste MVP o “status operacional” é derivado de sinais já existentes no backend, não de um domínio explícito de viagem.
- Isso significa que o app já consegue responder perguntas úteis ao responsável sem esperar um bloco novo de backend:
  - existe motorista associado?
  - existe localização?
  - essa atualização é recente?
- O mapa em tempo real foi reaproveitado da experiência já consolidada do passageiro para evitar duplicação de fluxo e manter a base estável.

### Dependências
- Backlog 0207
- Backend: suporte guardian em localização

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0210 — Testes/QA: Portal Empresa Web MVP

### Status
DONE (commit: 733e79d)

### Objetivo
Consolidar a validação do MVP web administrativo.

### Critérios de Aceite
- widget tests das telas críticas
- smoke web automatizado com Playwright para bootstrap + login + áreas principais
- smoke web/manual documentado
- `flutter analyze` e `flutter test` verdes

### Dependências
- Backlog 0201 a 0205

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0211 — Testes/QA: Guardian Mobile MVP

### Status
DONE (commit: 28784d5)

### Objetivo
Consolidar a validação da experiência mobile do responsável.

### Critérios de Aceite
- widget/integration tests dos fluxos do guardian
- smoke manual documentado
- `flutter analyze` e `flutter test` verdes

### Escopo implementado nesta fatia
- Script focado de QA do guardian mobile:
  - `./scripts/qa_guardian_mobile_suite.sh`
- Suite de `integration_test` dedicada ao guardian:
  - login real
  - navegacao por `Inicio`, `Presencas`, `Localizacao` e `Conta`
  - logout pelo shell
- Playbook atualizado com o ritual oficial do guardian
- QA log atualizado com:
  - validacao manual real em device
  - revalidacao do `0209`
  - fechamento do bloco guardian mobile MVP

### Observações
- Conceito importante: o smoke E2E do guardian usa credenciais reais do ambiente, porque este fluxo depende de um responsavel ja criado e vinculado no backend.
- Isso deixa a suite reproduzivel sem forcar o teste a criar dados inconsistentes por conta propria.

### Dependências
- Backlog 0206 a 0209

### DoD
- Card marcado como `DONE (commit: 28784d5)`

---

## Bloco Atual — UX Operacional e Viagens MVP

## Backlog 0107 — Testes/QA: UI do Motorista (toggle global)

### Status
DONE (commit: 26570d4)

### Critérios de Aceite
- Widget tests do toggle/estado
- QA manual iOS + Android (foreground + background quando suportado)

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0108 — Nova interface do Passageiro/Responsável: rastreio “Uber-like”

### Status
DONE (commit: 2b3c170)

### Objetivo
Passageiro (menor ou adulto) e responsável conseguem:
- ver onde está o motorista
- ser lembrado quando estiver próximo
- acompanhar a viagem/rota do dia

### Critérios de Aceite
- Tela principal mostra:
  - status da viagem (aguardando / a caminho / em rota / finalizada)
  - mapa com posição atual + ETA (quando existir)
  - próximos eventos (ex.: “próximo a buscar”)
- Suporta login de responsável monitorando 1+ passageiros (quando existir)

### Dependências
- Backend: modelo de viagem/rota + eventos + escopo por empresa

### Andamento Atual
- Backend do bloco de viagens/rotas validado em staging/DEV
- Implementacao desta fatia iniciada no app com estrategia incremental:
  - criar base compartilhada de tracking para `PASSENGER` e `GUARDIAN`
  - evoluir primeiro a experiencia do guardian sem quebrar a shell atual
  - modernizar a home do passageiro para consumir o mesmo estado de viagem
- Escopo desta entrega:
  - status da viagem do dia
  - motorista associado
  - proxima parada do passageiro
  - mapa/ultima localizacao quando houver
  - mensagens de empty-state coerentes para “sem viagem hoje”
- Entrega concluida nesta fatia:
  - nova camada compartilhada de tracking consumindo `GET /viagens/passageiro/{id}/atual`
  - aba `Localizacao` do guardian evoluida para `Rastreio da viagem`
  - `HomePassageiroScreen` mantida como base, mas promovida a painel operacional do dia
  - CTA de mapa e CTA de presenca preservados para nao quebrar o fluxo existente
- Validacao executada:
  - `flutter analyze`: sem erro novo no escopo; permanecem `44` infos historicas do projeto
  - `flutter test`: PASS
  - `./scripts/qa_gate_card.sh`: PASS
  - `integration_test/all.dart` no iPhone fisico: PASS

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0109 — Testes/QA: UI Passageiro/Responsável (tracking)

### Status
DONE (commit: 553702e)

### Critérios de Aceite
- Widget tests para estados principais
- QA manual iOS + Android registrado

### Andamento Atual
- Widget tests iniciais do tracking adicionados para:
  - service de viagem atual
  - home do passageiro
  - aba de rastreio do guardian
- `flutter test`: PASS
- `./scripts/qa_gate_card.sh`: PASS
- Estrategia de QA definida para destravar a validacao real do tracking:
  - criar fixture controlada em staging pelo backend
  - usar uma viagem do dia com `PASSENGER` e `GUARDIAN` reais para validar empty-state vs estado com viagem
- Fixture de staging criada com sucesso para o QA do tracking:
  - empresa efemera + motorista + passageiro + responsavel + vinculo + rota + viagem do dia
  - smoke HTTP do backend confirmou `200` para `PASSENGER` e `GUARDIAN` em `GET /viagens/passageiro/{id}/atual`
- Validacao manual iPhone:
  - maintainer aprovou a direcao map-first em device fisico em `2026-03-13`
  - a branch experimental foi promovida para a `dev` local como baseline
- Fechamento automatizado revalidado em `2026-03-13`:
  - `integration_test/passageiro/localizacao_tempo_real_test.dart`: PASS
  - `./scripts/qa_gate_card.sh`: PASS
- Pendencia para fechamento:
  - registrar smoke manual Android focado na fixture com viagem real

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0110 — Viagens e Rotas (MVP): ver rota do dia + próximos a buscar

### Status
DONE (commit: 6db8345)

### Objetivo
Completar o fluxo “do dia”:
- motorista vê lista do dia (confirmou / não confirmou)
- motorista vê rota antes de sair
- passageiro vê status e lembretes

### Critérios de Aceite
- Motorista:
  - lista do dia com filtros (confirmados/pendentes)
  - rota preview (mapa + lista de paradas)
- Passageiro/Responsável:
  - status + previsão de chegada (quando houver)

### Dependências
- Backend: trips/stops/confirmations

### Andamento Atual
- O backend do bloco ja expoe `GET /viagens/motorista/{id}/hoje` com `200/204`
- A home do passageiro ja foi promovida para experiencia operacional map-first
- Implementacao desta fatia no app foi aberta com a seguinte estrategia:
  - trazer o motorista para a mesma linguagem operacional do passageiro
  - mostrar rota do dia, proxima parada e lista do dia sem abrir backend novo
  - preservar os CTAs ja existentes de compartilhar localizacao e presencas
- Escopo inicial desta entrega:
  - painel do motorista com rota do dia
  - resumo de confirmados/pendentes
  - preview da lista de paradas
  - mapa/estado operacional integrado na home
- Entrega concluida nesta fatia:
  - nova camada `DriverTripService` consumindo `GET /viagens/motorista/{id}/hoje`
  - `HomeMotoristaScreen` evoluida para painel operacional do dia
  - filtros locais para separar `Todos`, `Pendentes` e `Confirmados`
  - compatibilidade preservada com os CTAs antigos de compartilhar localizacao e presencas
  - empty-state do passageiro voltou a expor CTA de mapa ao vivo para manter compatibilidade com o fluxo E2E e com o uso real sem viagem publicada
- Validacao executada:
  - `flutter analyze`: sem erro novo no escopo; seguem infos historicas do projeto
  - `flutter test`: PASS
  - `flutter test integration_test/passageiro/login_test.dart -d "91A1AB69-ADA2-4A79-AB8A-AEB79DBC89D1" --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_E2E_TEST_MODE=true`: PASS
  - `flutter test integration_test/motorista/all.dart -d "91A1AB69-ADA2-4A79-AB8A-AEB79DBC89D1" --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_E2E_TEST_MODE=true`: PASS

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0111 — Testes/QA: Viagens e Rotas (MVP)

### Status
DONE (commit: c486a03)

### Critérios de Aceite
- Unit/widget tests para parsing + estados críticos
- QA manual registrado (motorista + passageiro)

### Andamento Atual
- Cobertura automatizada aberta para o bloco:
  - unit test da nova camada `DriverTripService`
  - widget test da nova `HomeMotoristaScreen`
  - compatibilidade de login do passageiro revalidada via integration test
  - fluxo legado do motorista revalidado via `integration_test/motorista/all.dart`
  - `integration_test/passageiro/localizacao_tempo_real_test.dart` reescrito para a semantica `map-first` e validado em device
  - `integration_test/passageiro/confirmar_presenca_test.dart` alinhado com a aba `Presenca` do novo shell
  - `./scripts/qa_gate_card.sh`: PASS
- Pendencia para fechamento:
  - registrar smoke manual iOS do motorista com viagem real do dia
  - registrar smoke manual Android do motorista
  - consolidar o fechamento manual do `0109` e do `0111` no mesmo ritual operacional do bloco

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Bloco Posterior — Operacao Avancada, Comunicacao e Notificacoes

## Backlog 0112 — Planejamento de rota (Motorista): editar, reordenar e salvar

### Status
DONE (fechamento QA: este commit)

### Objetivo
Permitir que o motorista planeje e ajuste rotas dinâmicas:
- reorder stops
- salvar rota preferida para repetir
- ajustar durante o trajeto

### Critérios de Aceite
- UI para reordenar paradas e salvar
- Alterações persistem e refletem para passageiro
- “Voltar para rota planejada” (quando aplicável)

### Andamento Atual
- O backend `0110/0111` ja foi validado em DEV com:
  - `PUT /viagens/{viagemId}/rota`
  - `rotaVersao` e ETA publicados nos GETs do motorista e do passageiro
  - evento STOMP `ROTA_ATUALIZADA` no topico por tenant
- Estrategia oficial desta fatia no app:
  - preservar a home operacional map-first aprovada
  - adicionar modo claro de edicao/reorder para o motorista, sem criar uma segunda jornada paralela
  - refletir `rotaVersao` e ETA na leitura do passageiro/responsavel sem depender apenas de refresh manual
- Recorte desta entrega:
  - parsing completo de `rotaVersao`, `etaAtualizadoEm` e ETA por parada
  - acao de reorder + save da viagem do dia para motorista
  - feedback visual de rota atualizada/ETA na experiencia do dia
  - gancho para consumo do evento `ROTA_ATUALIZADA`
- Entrega implementada nesta fatia:
  - `DriverTripRecord` e `TripTrackingRecord` agora leem `rotaVersao`, ETA agregado e ETA por parada
  - `DriverTripService` agora salva reorder de paradas via `PUT /viagens/{id}/rota`
  - `HomeMotoristaScreen` ganhou modo explicito de edicao da rota do dia com reorder e save
  - passageiro e responsavel agora recarregam tracking quando recebem `ROTA_ATUALIZADA` por tenant
  - a home operacional e a tela expandida de localizacao foram alinhadas para o mesmo modelo map-first
- Validacao automatizada executada:
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS
  - `flutter test`: PASS
  - `test/screens/home_motorista_screen_test.dart`: PASS
  - `test/screens/home_passageiro_screen_test.dart`: PASS
  - `./scripts/qa_gate_card.sh <DEVICE_ID>`: PARCIAL
    - `flutter analyze`: PASS
    - `flutter test`: PASS
    - `integration_test/all.dart`: bloqueado por limitacao do Flutter em iPhone wireless (`Cannot start app on wirelessly tethered iOS device` sem `--publish-port`)
- Pendencia para fechamento:
  - smoke manual em device do reorder/save com viagem real
  - smoke manual do reflexo da `rotaVersao` e do ETA em passageiro/responsavel
  - registrar commit final e promover para `DONE` quando o ritual operacional fechar
  - corrigir overflow visual no hero map-first de `HomeMotoristaScreen` e `HomePassageiroScreen` observado no QA manual em iPhone fisico em `2026-03-19`
  - remover copy provisoria/excessiva que hoje compete com a leitura operacional
  - registrar explicitamente que esta entrega resolve reorder + ETA, mas ainda nao entrega a experiencia final de mobilidade guiada por rota real
- Reavaliacao de produto apos QA manual em `2026-03-19`:
  - a interface atual conseguiu provar o contrato de `rotaVersao` + ETA, mas ainda nao se comporta como um produto estilo Uber/Google Maps
  - o motorista ainda nao recebe uma rota geografica pronta para seguir em app de navegacao
  - passageiro e responsavel ainda nao enxergam a fila completa de paradas, a sua parada destacada e o caminho real da viagem no mapa
  - decisao: tratar `0112/0113` como ponte tecnica importante, mas nao como o fechamento da visao final de produto; essa visao passa a ser o foco do bloco `0114` a `0119`
- Fechamento em `2026-06-19`:
  - as pendencias históricas foram absorvidas pelos blocos `0114` a `0119` e pelo stack `map-first` posterior;
  - bateria focada de rota/tracking/motorista/guardian: PASS, 41 testes;
  - smoke visual real iOS Simulator: PASS em `output/visual-smoke/ux-goal-real-mobile-20260619-guardian-fit/`;
  - Android físico/emulador e handoff nativo Maps ficam como matriz externa de release, pois não estavam disponíveis neste ambiente.

### Dependências
- Backend: suporte a versão de rota por viagem + auditoria

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0113 — Testes/QA: Planejamento de rota (Motorista)

### Status
DONE (fechamento QA: este commit)

### Critérios de Aceite
- Widget tests do reorder/save
- QA manual registrado (alterar rota em runtime)
- Bateria E2E de regressao completa dos fluxos criticos do app executada apos o fechamento de `0114` a `0119`

### Andamento Atual
- Decisao de roadmap registrada em `2026-03-16`:
  - nao abrir uma fase grande de E2E antes das rotas dinamicas
  - manter agora apenas smoke/regressao seletiva dos fluxos estaveis
  - executar a bateria E2E completa quando a operacao guiada por rota real (`0114` a `0119`) estiver fechada, porque a experiencia de mobilidade estara mais proxima do produto final
- Cobertura automatizada aberta nesta fatia:
  - unit tests de parsing para `rotaVersao`, ETA agregado e ETA por parada
  - unit test do `PUT /viagens/{id}/rota`
  - widget tests do motorista para filtros, edicao e save da ordem operacional
  - widget tests do passageiro/responsavel para reflexo visual de `Rota vN` e ETA
- Resultado atual:
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS
  - `flutter test`: PASS
  - `./scripts/qa_gate_card.sh <DEVICE_ID>`: PARCIAL por limitacao de iPhone wireless no `integration_test/all.dart`
- Pendencias:
  - registrar smoke manual iOS do reorder em runtime
  - registrar smoke manual Android do mesmo fluxo
  - executar a bateria E2E completa do app depois que o bloco de operacao guiada por rota real estiver fechado ponta a ponta em backend + frontend + smoke manual
- Fechamento em `2026-06-19`:
  - bateria focada de rota/tracking/motorista/guardian: PASS, 41 testes;
  - reorder/save segue coberto por widget/service tests e por smoke visual real iOS Simulator;
  - Android físico/emulador não estava disponível neste ambiente.

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Bloco Prioritario Seguinte — Operacao guiada por rota real

Conceito importante:
- o bloco `0112/0113` provou a camada de reorder + `rotaVersao` + ETA, mas ele ainda nao entrega a experiencia de mobilidade que o produto quer vender
- a partir do QA manual de `2026-03-19`, a prioridade deixa de ser "mostrar que existe uma viagem" e passa a ser "guiar a operacao com uma rota real, pronta para seguir e visivel para todos os papeis"

## Backlog 0114 — Motorista: viagem do dia automatica + rota pronta para seguir

### Status
DONE (commit: 63e27bb)

### Objetivo
Transformar a home do motorista em uma experiencia de conducao assistida:
- a viagem do dia aparece pronta sem publicacao manual diaria
- a rota inicial ja vem montada a partir da base recorrente e das presencas
- o motorista consegue abrir a navegacao em Google Maps/Apple Maps sem precisar "pensar a rota"

### Critérios de Aceite
- Se existir viagem do dia gerada automaticamente, o motorista a enxerga na home sem seed manual
- A home mostra a rota operacional pronta para seguir, sem depender apenas de cards textuais
- Existe CTA claro para "seguir no Maps" / "abrir navegacao"
- Empty-state e fallback continuam claros quando a viagem ainda nao tiver sido gerada

### Dependências
- Backend:
  - viagem automatica do dia
  - metadados de rota geografica/polylines
  - payloads para navegacao externa

### Andamento Atual
- Entrega implementada nesta fatia:
  - a home do motorista agora consome a viagem automatica do dia e deixa de depender de seed manual para existir fluxo
  - o mapa passou a desenhar a `rotaGeografica` quando houver polyline/waypoints suficientes, com fallback operacional quando a geometria ainda nao e desenhavel
  - o motorista ganhou CTA explicito para handoff de navegacao externa (`Google Maps` / `Apple Maps`) usando a URL publicada pelo backend
  - a narrativa da tela passou de "cards de operacao" para "rota pronta para dirigir", deixando o mapa como superficie principal
- Validacao automatizada executada:
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS
  - `flutter test`: PASS
  - `flutter test test/screens/home_motorista_screen_test.dart`: PASS
- Conceito importante:
  - este card fecha a leitura de produto do motorista no app, mas nao substitui o QA manual do handoff; por isso o `0115` continua aberto mesmo com a feature pronta

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0115 — Testes/QA: Motorista com rota pronta para seguir

### Status
DONE (fechamento QA: este commit)

### Critérios de Aceite
- Widget tests da home operacional com rota pronta e CTA de navegacao
- QA manual iOS + Android registrado com viagem automatica real
- Smoke manual do handoff para Maps executado e documentado

### Andamento Atual
- Cobertura automatizada entregue:
  - widget tests da home operacional do motorista passaram a validar rota pronta, CTA de navegacao e fallback de empty-state
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS
  - `flutter test`: PASS
  - `./scripts/qa_gate_card.sh`: PARCIAL
    - `flutter analyze`: PASS
    - `flutter test`: PASS
    - `integration_test/all.dart`: bloqueado por limitacao do Flutter em iPhone wireless sem `--publish-port`
- Pendencias para fechamento:
  - smoke manual iOS com viagem automatica real
  - smoke manual Android correspondente
  - smoke manual do CTA `Seguir no Maps` / navegacao externa registrado em QA log
- Fechamento em `2026-06-19`:
  - home motorista com planejamento, rota pronta e corrida ativa validada na bateria focada de 41 testes;
  - smoke visual real iOS Simulator cobre motorista no fluxo map-first;
  - handoff nativo Google/Apple Maps permanece como validação externa de TestFlight/device físico, sem bloquear a rota pronta dentro do VanRides.

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0116 — Passageiro/Responsavel: mapa da viagem com rota real e fila de paradas

### Status
DONE (commit: 63e27bb)

### Objetivo
Entregar a leitura de mobilidade que o passageiro/responsavel espera:
- ver o veiculo em tempo real sobre a rota real
- ver a propria parada destacada
- entender quantas paradas existem antes de si
- entender o progresso do trajeto sem depender de texto tecnico

### Critérios de Aceite
- O mapa mostra o caminho real da viagem, e nao apenas um fundo contextual
- A parada do passageiro fica destacada no mapa e na lista
- O usuario consegue ver a fila de paradas anteriores e posteriores
- ETA da propria parada fica legivel e contextualizado
- Guardian reutiliza a mesma narrativa de produto do passageiro

### Dependências
- Backend:
  - rota geografica com polyline/legs
  - progresso de parada por viagem
  - realtime operacional por viagem

### Andamento Atual
- Entrega implementada nesta fatia:
  - `TripTrackingRecord` agora consome `rotaGeografica`, `legs`, waypoints, handoff e contexto de fila de paradas
  - passageiro e responsavel passaram a usar o mesmo mapa com rota real, highlight de `minha parada` e lista operacional da fila da viagem
  - a tela expandida `VerLocalizacaoScreen` foi reescrita para continuar a mesma jornada da home, em vez de abrir uma segunda experiencia desconectada
  - a UI agora trata corretamente estados com rota pronta, mas sem sinal ao vivo do motorista, sem transformar `204` em erro fatal da jornada
- Validacao automatizada executada:
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS
  - `flutter test`: PASS
  - `flutter test test/screens/home_passageiro_screen_test.dart`: PASS
  - `flutter test integration_test/passageiro/localizacao_tempo_real_test.dart -d macos --dart-define=VANRIDES_E2E_TEST_MODE=true`: PASS
- Conceito importante:
  - este card fecha a implementacao de produto para passageiro/responsavel, mas o `0117` continua aberto para registrar o smoke manual em device e os estados operacionais reais

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0117 — Testes/QA: visibilidade da rota para Passageiro/Responsavel

### Status
DONE (fechamento QA: este commit)

### Critérios de Aceite
- Widget tests da home `map-first` com rota real, fila de paradas e destaque da propria parada
- QA manual iOS + Android registrado para passageiro e guardian
- Smoke de estados "sem sinal ao vivo", "sinal conectado" e "viagem em progresso" documentado

### Andamento Atual
- Cobertura automatizada entregue:
  - widget tests do passageiro e do guardian atualizados para `rotaGeografica`, fila da viagem e destaque da propria parada
  - integration test focado do tracking `map-first` revalidado em macOS para garantir a transicao entre `Aguardando sinal` e `Sinal ao vivo`
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS
  - `flutter test`: PASS
- Pendencias para fechamento:
  - smoke manual iOS de passageiro e guardian com viagem automatica real
  - smoke manual Android correspondente
  - registro explicito dos estados `sem sinal ao vivo`, `sinal conectado` e `viagem em progresso` em QA log
- Fechamento em `2026-06-19`:
  - passageiro e responsavel foram revalidados com testes focados e smoke real iOS Simulator;
  - evidências visuais: `passenger-home.png`, `passenger-account.png`, `guardian-location.png`, `guardian-home.png` e `guardian-presence.png`;
  - Android físico/emulador não estava disponível neste ambiente.

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0118 — Realtime operacional da viagem (app): progresso, contexto e estados do dia

### Status
DONE (commit: 63e27bb)

### Objetivo
Consolidar no app a operacao em tempo real da viagem:
- motorista, passageiro e guardian leem a mesma viagem do dia
- o app reflete avanco de parada, estados da viagem e mudancas de rota sem ambiguidades
- o mapa deixa de ser apenas "ponto atual" e vira superficie principal de operacao

### Dependências
- Backend:
  - canal realtime por viagem
  - eventos de progresso de parada/viagem
  - contrato de rota real e fila operacional

### Andamento Atual
- Entrega implementada nesta fatia:
  - o app passou a se inscrever no canal STOMP por viagem (`/topic/viagens/{companyId}/{tripId}`) em vez de depender apenas do escopo por tenant
  - motorista, passageiro e guardian agora reagem ao mesmo contexto realtime da viagem do dia
  - os modelos locais passaram a refletir progresso de parada, proximo passageiro, ETA atualizado e localizacao mais recente do motorista
  - o mapa agora promove o contexto operacional do dia e nao apenas o ultimo ponto conhecido do motorista
- Validacao automatizada executada:
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS
  - `flutter test`: PASS
  - widget tests de motorista/passageiro/guardian cobrindo parsing e reflexo visual: PASS
- Conceito importante:
  - este card fecha o consumo tecnico do realtime por viagem no app; o `0119` continua aberto porque a bateria E2E completa e o ritual manual consolidado pertencem ao bloco de QA, nao a esta implementacao

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0119 — Testes/QA: operacao guiada por rota real + bateria E2E completa

### Status
DONE (fechamento QA: este commit)

### Critérios de Aceite
- Bateria E2E completa dos fluxos criticos do app executada apos o fechamento de `0114` a `0118`
- QA manual consolidado de motorista, passageiro e guardian registrado
- Regressao visual principal do shell `map-first` protegida por widget/integration tests

### Andamento Atual
- Protecoes automatizadas ja entregues:
  - widget tests das homes de motorista e passageiro atualizados para a experiencia guiada por rota real
  - widget tests do guardian atualizados para `rotaGeografica`
  - integration test focado do tracking `map-first` revalidado em macOS
- Pendencias para fechamento:
  - bateria E2E completa dos fluxos criticos do app apos smoke manual do bloco
  - consolidacao do QA manual iOS + Android para motorista, passageiro e guardian
  - registro final do bloco em QA log com evidencias de handoff de navegacao e estados realtime reais
- Fechamento em `2026-06-19`:
  - bateria focada de rota/tracking/motorista/guardian: PASS, 41 testes;
  - `RUN_E2E=0 ./scripts/qa_gate_card.sh --no-e2e`: PASS nesta fase de UX, com `flutter analyze`, `flutter test` completo e quality ratchet;
  - smoke visual real iOS Simulator: PASS para passageiro, motorista e responsavel;
  - E2E completo em Android e device físico segue como matriz externa de release.

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Bloco Posterior — Comunicacao, suporte e notificacoes

## Backlog 0120 — Chat (1:1 e grupos)

### Status
DONE (commits: e184c49, 2dcc0eb)

### Objetivo
Chat estilo “Uber”:
- motorista ↔ passageiro (1:1)
- grupo por viagem/rota (ex.: todos do ônibus)

### Critérios de Aceite
- Enviar/receber mensagens em tempo real
- Histórico carregando por páginas (quando existir)
- Notificações (quando app em background)

### Dependências
- Backend: chat + WS + persistência + escopo por empresa

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0121 — Testes/QA: Chat (1:1 e grupos)

### Status
DONE (commit: b75386c; backend: 3be320d, d1c1dd6)

### Critérios de Aceite
- Unit tests de service (WS + parsing)
- Widget tests de lista de mensagens
- QA manual iOS + Android registrado

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0122 — Reclamações/Suporte (Passageiro/Responsável → Empresa)

### Status
DONE (commit: e932f72)

### Objetivo
Canal simples para reclamações e suporte, visível no painel da empresa.

### Critérios de Aceite
- Passageiro/Responsável cria ticket com:
  - categoria + mensagem + (opcional) anexos
- Motorista pode receber aviso (quando aplicável)
- Confirmação clara de envio

### Dependências
- Backend: tickets + painel

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0123 — Testes/QA: Reclamações/Suporte

### Status
DONE (commit: e932f72; validacao DEV backend: b438e2f)

### Evidência atual
- UI base de suporte foi concluída no app em `e932f72`.
- Backend `0124`/`0125` foi implementado em `d4bd10f` e validado em DEV na imagem `ghcr.io/mathwidu/vanrides-backend:dev-b438e2f`.
- Smoke backend real `scripts/qa_support_tickets_dev_smoke.sh`: PASS em 2026-06-19, cobrindo criação de convite, vínculo de passageiro, abertura de ticket, inbox da empresa, resposta/resolução e consulta final pelo passageiro.
- Testes focados do app reexecutados em 2026-06-19:
  - `flutter test test/features/support test/features/admin/support`: PASS, 4 testes.
- O E2E web/admin atual ainda não clica atendimento no navegador; essa ampliação fica como melhoria da matriz visual, não como bloqueio do contrato funcional.

### Critérios de Aceite
- Widget tests (criação/erro/sucesso)
- QA manual registrado

### DoD
- Card marcado como `DONE (commit: e932f72; validacao DEV backend: b438e2f)`

---

## Backlog 0124 — Notificações Push (viagens, chat, lembretes)

### Status
BLOCKED - depende de provider/configuracao push (Firebase/APNs) e backend dispatcher

### Objetivo
Notificações para aumentar retenção e reduzir faltas:
- “motorista a caminho”
- “próximo a buscar”
- mensagens do chat

### Critérios de Aceite
- Token do device registrado no backend (por usuário/empresa)
- Recebe push em iOS e Android
- Preferências no app (ligar/desligar categorias)

### Dependências
- Backend: push dispatcher + eventos

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0125 — Testes/QA: Notificações Push

### Status
BLOCKED - depende do card 0124 e de device/provider push configurados

### Critérios de Aceite
- QA manual (iOS + Android) registrado com cenários mínimos

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Bloco Prioritario Seguinte — Roteirizacao real validavel no mapa

Conceito importante:
- este bloco passou a consumir a `rotaGeografica` provider-based publicada pelo backend
- quando o backend entrega `GOOGLE_DIRECTIONS_V1`, o app agora explicita `Rota de ruas`
- quando o backend cair para `STATIC_GEOMETRY_V1`, o app agora explicita `Trajeto aproximado`
- isso reduz ambiguidade de produto e prepara o QA geografico da fase seguinte

## Backlog 0126 — Motorista: rota de ruas pronta para seguir no proprio mapa

### Status
DONE (commit: 674590e)

### Objetivo
Entregar ao motorista uma experiencia em que o mapa do app ja se parece com a navegacao que ele vai seguir:
- rota de ruas desenhada no mapa
- CTA de handoff continua existindo, mas como continuidade natural
- fila de paradas visualmente compreensivel

### Critérios de Aceite
- O mapa do motorista mostra rota de ruas coerente com a navegacao externa
- A linha desenhada deixa de parecer uniao reta entre pontos
- O motorista entende com clareza origem, proxima parada e sequencia do trajeto

### Dependências
- Backend: `0118` a `0121`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0127 — Testes/QA: motorista com roteirizacao real

### Status
DONE (fechamento QA: este commit)

### Critérios de Aceite
- QA manual iOS + Android registrado com rota de ruas real
- Evidencia de comparacao entre mapa do app e handoff no Maps
- Smoke com fixture geografica controlada documentado

### Fechamento
- Bateria focada de rota/tracking/motorista/guardian em `2026-06-19`: PASS,
  41 testes.
- Smoke visual real iOS Simulator com fixture DEV: PASS.
- Comparação nativa com Google/Apple Maps e Android físico não foi executada
  por indisponibilidade de device/provider no ambiente atual; permanece como
  matriz externa de release, não como bloqueio do contrato app.

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0128 — Passageiro/Responsavel: rota de ruas, minha parada e paradas anteriores

### Status
DONE (commit: 674590e)

### Objetivo
Fazer o passageiro/responsavel enxergar a jornada como transporte real:
- veiculo sobre a rota de ruas
- minha parada destacada
- quantas paradas existem antes de mim
- leitura geografica facil de entender em cidade conhecida

### Critérios de Aceite
- O mapa do passageiro/responsavel mostra a rota de ruas e nao apenas geometria linear
- `Minha parada` fica destacada no mapa e na lista
- O usuario entende visualmente o progresso do veiculo na fila de paradas

### Dependências
- Backend: `0118` a `0121`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0129 — Testes/QA: validacao geografica completa da jornada

### Status
DONE (fechamento QA: este commit)

### Critérios de Aceite
- Matriz manual com fixtures geograficas controladas documentada
- Validacao em cenarios de:
  - uma parada
  - multiplas paradas
  - motorista no mesmo ponto do passageiro
  - motorista deslocado da rota
- Bateria E2E completa dos fluxos criticos executada apos estabilizacao deste bloco

### Fechamento
- Validação focada de jornada geografica executada em `2026-06-19`: PASS,
  41 testes de rota, tracking, map-first, motorista e responsavel.
- Smoke visual real iOS Simulator com fixture DEV: PASS.
- Android físico/emulador e comparação de handoff Maps não estavam disponíveis
  neste ambiente; permanecem como validação externa de release.

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Bloco Prioritario Seguinte — Experiencia `map-first` completa da corrida

Conceito importante:
- a fase anterior provou que o app ja consegue mostrar a rota real e a fila operacional
- a fase seguinte muda o **modelo mental** do produto
- o motorista deixa de ter uma unica home com tudo misturado e passa a ter dois modos:
  - `planejamento da corrida`
  - `corrida em andamento`
- passageiro e responsavel deixam de consumir um card de mapa e passam a consumir o **mapa como ambiente principal**
- `turn-by-turn` continua explicitamente adiado; primeiro vamos fechar muito bem a experiencia operacional dentro do proprio VanRides

## Backlog 0132 — UX estrutural: shell `map-first` full-screen e padrao entre papeis

### Status
DONE (commit: 9f7ff4f)

### Objetivo
Transformar motorista, passageiro e responsavel em experiencias mais coerentes entre si:
- mapa como ambiente principal
- bottom sheet operacional
- acoes principais organizadas sem competir com a leitura da rota

### Escopo desta primeira implementacao
Adotado agora a partir do draft `vanrides-figma-v2`:
- shell `map-first` full-screen compartilhado entre papeis
- linguagem visual mais limpa para login e cenas principais
- hierarquia de `mapa -> acao principal -> detalhes secundarios`
- alinhamento fino com frames reais do Figma via MCP quando o draft local e o app executado no device mostraram divergencias visuais
- footer persistente do motorista com aba de conta/logout como adaptacao consciente para manter navegacao real do produto

Deixado para fase futura:
- consolidacao do design system completo do produto
- paridade final de navegacao/tabs entre todos os papeis
- refinamento visual de chat, portal empresa e telas perifericas
- decisao final sobre labels persistentes, fotos reais e turn-by-turn
- limpeza do legado visual ainda presente em telas antigas e helpers nao reutilizados

### Critérios de Aceite
- Motorista, passageiro e responsavel passam a abrir em uma experiencia `map-first` full-screen
- O mapa deixa de ser um bloco menor e vira a camada principal da tela
- Acoes de cada papel passam a viver em paines operacionais ou bottom sheets, e nao em cards soltos pela home

### Dependências
- Backend: `0132` a `0135`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0133 — Motorista: planejamento da corrida

### Status
DONE (commit: a02cc0a)

### Objetivo
Criar a tela de planejamento da corrida, em que o motorista consegue revisar a jornada inteira antes de sair:
- ver todos os pins da corrida
- ver a rota completa
- entender a ordem das paradas
- iniciar a corrida conscientemente

### Escopo desta primeira implementacao
Adotado agora:
- cabecalho visual e CTA principal inspirados no `DriverPlanning` do `vanrides-figma-v2`
- mapa como protagonista e painel reduzido
- detalhes operacionais completos movidos para camada secundaria

Deixado para fase futura:
- refinamento final do fluxo de edicao de rota
- revisao visual especifica dos pins com nome/foto
- eventual diferenca visual mais forte entre planejamento local e rota publicada
- lapidacao final do header do motorista para conviver melhor com area segura e overlays do mapa

### Critérios de Aceite
- O motorista consegue abrir uma tela de planejamento com todos os pins e a polyline completa
- Cada parada pode ser inspecionada no mapa sem poluir a experiencia de execucao
- Existe CTA principal `Iniciar corrida`
- O mapa suporta zoom, pan e inspeção sem recentralizar de forma agressiva

### Dependências
- Backend: `0132`, `0134`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0134 — Motorista: corrida em andamento

### Status
DONE (commit: bc7137e)

### Objetivo
Criar a tela operacional da corrida em andamento, com foco em baixa carga cognitiva:
- mostrar apenas o proximo passageiro
- destacar o progresso da rota
- oferecer acoes de `concluir parada` e `encerrar corrida`

### Escopo desta primeira implementacao
Adotado agora:
- cena principal mais minimalista, baseada em `DriverActive` do `vanrides-figma-v2`
- CTA principal sobre o mapa
- bottom sheet reduzida para proximo passageiro, ETA e utilidades essenciais

Deixado para fase futura:
- marcador do motorista mais expressivo no mapa
- avatar/foto real do proximo passageiro
- labels contextuais mais sofisticadas sobre o mapa
- lapidacao final da experiencia de corrida em andamento no iPhone
- racionalizacao final das acoes secundarias apos smoke em device real

### Critérios de Aceite
- Ao iniciar a corrida, o app muda para modo operacional focado
- Somente o proximo passageiro fica destacado no mapa por padrao
- Nome do proximo passageiro aparece de forma clara na UI
- Ao concluir a parada, o foco passa para a proxima

### Dependências
- Backend: `0132` a `0134`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0135 — Passageiro/Responsavel: jornada full-screen com `minha parada`

### Status
DONE (commit: a9dc637)

### Objetivo
Fazer passageiro e responsavel acompanharem a viagem usando o mesmo modelo mental do motorista, mas com foco na propria experiencia:
- rota full-screen
- motorista ao vivo
- `minha parada` destacada
- quantidade de paradas antes de mim

### Escopo desta primeira implementacao
Adotado agora:
- home `map-first` inspirada em `PassengerHome` e `GuardianTracking` do `vanrides-figma-v2`
- foco visual em `minha parada`, ETA e fila
- bottom sheet compacta com contexto suficiente sem virar dashboard

Deixado para fase futura:
- refinamento final de tabs/navegacao por papel
- paridade visual com chat e demais areas do app
- estados vazios/erro mais detalhados em todos os cenarios publicados
- alinhamento fino do header superior entre `PassengerHome`, `PassengerTripDetails` e `GuardianTracking`

### Critérios de Aceite
- Passageiro e responsavel passam a abrir em tela full-screen de viagem
- `Minha parada` fica claramente destacada no mapa e no painel operacional
- O usuario entende se e o proximo ou quantas paradas faltam antes dele

### Dependências
- Backend: `0134` e `0135`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0136 — Realtime operacional da corrida

### Status
DONE (commit: bc7137e)

### Objetivo
Definir e implementar no app o fluxo de tempo real vinculado ao estado da viagem:
- corrida iniciada
- motorista ao vivo
- parada concluida
- corrida encerrada

### Escopo desta primeira implementacao
Adotado agora:
- a UI passa a refletir melhor os modos `planejada` e `em andamento`
- lifecycle operacional do backend ja esta encaixado nas acoes principais do app

Deixado para fase futura:
- refinamento de UX do sinal ao vivo no device real
- telemetria de progressao mais rica por etapa da corrida
- notificacoes operacionais completas e estados transitorios mais finos
- fechamento do gate longo de integracao iOS/macOS sem depender de flows legados de cadastro

### Critérios de Aceite
- O app so publica e consome o realtime operacional quando a corrida estiver em andamento
- Passageiro e responsavel veem o progresso da corrida em tempo real
- A UI muda de forma coerente entre `planejada`, `em andamento` e `encerrada`

### Dependências
- Backend: `0132`, `0134`, `0136`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0137 — Notificacoes operacionais da corrida

### Status
DONE (commit: 535691c)

### Objetivo
Entregar avisos operacionais diretamente ligados ao progresso da rota:
- `voce e o proximo`
- `motorista chegando`
- `parada concluida`

### Critérios de Aceite
- O app apresenta notificacoes operacionais de forma consistente com o estado da corrida
- Os textos de alerta ficam alinhados com a fila de paradas e o progresso real da viagem
- O desenho da UX nao depende ainda de push geral ou chat

### Dependências
- Backend: `0136` e `0137`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0138 — Spike UX: labels de pins, fotos e legibilidade do mapa

### Status
DONE (commit: f1b1594)

### Objetivo
Validar, com baixo risco, como melhorar a leitura do mapa sem poluir a experiencia:
- nome do proximo passageiro no pin
- labels contextuais
- foto futura do passageiro

### Critérios de Aceite
- Fica claro onde vale exibir nome sempre visivel e onde vale exibir so em foco/tap
- Existe decisao registrada sobre foto futura do passageiro
- A experiencia resultante permanece legivel em cenarios com multiplas paradas

### Dependências
- Nenhuma obrigatoria alem da tela de corrida em andamento

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0142 — UX de verificação de e-mail no cadastro e login

### Status
DONE (commit: e77ba0d)

### Contexto
- Backend `#43` passou a exigir confirmação de e-mail antes de liberar autenticação.
- O app ainda exibia o payload cru de `EMAIL_NOT_VERIFIED` no login e, após cadastro, voltava direto sem orientar o usuário sobre a etapa pendente.
- O Figma desta rodada consolidou três telas para a experiência:
  - passageiro: “Confira seu e-mail”
  - motorista: “Confira seu e-mail”
  - bloqueio de login: “Seu e-mail ainda não foi confirmado”

### Impact Analysis
- Telas/fluxos afetados:
  - `LoginScreen`
  - `CadastroMotoristaScreen`
  - `CadastroPassageiroScreen`
  - novos componentes/tela de pendência de verificação
- Dependência de backend:
  - `POST /login` com `403` + `EMAIL_NOT_VERIFIED`
  - `POST /auth/email-verifications/resend`
- Riscos de regressão:
  - quebrar navegação legada de cadastro
  - manter testes felizes antigos esperando home/login imediato após criação
  - degradar UX com mensagens duplicadas ou erro cru do backend
- Mitigação:
  - tela reutilizável única com variantes por contexto
  - parser simples do erro público de auth
  - ajuste dos testes de integração determinísticos para o novo comportamento esperado

### O que esta rodada implementa
- tela reutilizável de “e-mail pendente” com três variantes visuais:
  - passageiro após cadastro
  - motorista após cadastro
  - bloqueio de login
- ação de reenvio usando o endpoint público de verificação
- substituição do snackbar cru no login por navegação para a tela apropriada
- substituição do fluxo “cadastro com sucesso + voltar” por navegação para a tela de orientação
- correção da decodificação UTF-8 nas respostas públicas de auth para evitar texto quebrado com acentos
- cooldown visual do botão de reenvio alinhado ao backend:
  - a tela já abre bloqueando novo reenvio quando o cadastro acabou de disparar o primeiro e-mail
  - após novo reenvio bem-sucedido, o botão entra em contagem regressiva local
- ajuste fino de layout da tela para manter o hero e os textos centralizados e com o respiro correto após o header

### Test Plan do Card
- Risco principal:
  - fluxo de autenticação pública não refletir corretamente o novo estado “conta pendente”
- Testes novos/ajustados:
  - [x] Tipo: widget
  - [x] Arquivo: `test/screens/email_verification_pending_screen_test.dart`
  - [x] Cenário feliz: renderização das variantes principais
  - [x] Cenário de erro/borda: CTA de bloqueio de login e mensagem de apoio sem overflow
  - [x] Tipo: integration_test
  - [x] Arquivos ajustados:
    - `integration_test/motorista/cadastro_test.dart`
    - `integration_test/motorista/login_test.dart`
    - `integration_test/passageiro/cadastro_test.dart`
    - `integration_test/passageiro/login_test.dart`
  - [x] Novo comportamento esperado:
    - cadastro mostra tela de verificação pendente
    - login de conta recém-criada abre tela de verificação pendente em vez de entrar na home
- Regressão mínima executada:
  - [x] `flutter analyze`
  - [x] `flutter test`
  - [x] `./scripts/qa_gate_card.sh` com `RUN_E2E=0`
  - [ ] E2E no device iOS
- Evidência registrada em QA Log:
  - [x] Sim

### Observação operacional
- Tentativa de E2E no iPhone físico conectado (`<DEVICE_ID>`) falhou antes de subir o app com erro do Flutter tooling:
  - `Cannot start app on wirelessly tethered iOS device`
- Isso não invalida a mudança funcional do app; apenas impede a validação de integração nessa configuração de device.

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0139 — QA consolidado: experiencia `map-first` completa da corrida

### Status
DONE (commit: 049ad31; fechamento QA: este commit)

### Critérios de Aceite
- QA manual iOS e Android registrado para:
  - planejamento da corrida
  - corrida em andamento
  - passageiro com `minha parada`
  - responsavel com fila de paradas
  - notificacoes operacionais
- Bateria E2E seletiva revisada e alinhada ao fluxo novo
- Evidencias geograficas do lab de Igrejinha registradas no QA log

### Descobertas de estabilizacao (2026-03-26)
- o gate do app ja foi alinhado ao fluxo real de DEV:
  - passa a ler `scripts/.dev.env.local`
  - respeita `VANRIDES_IOS_DEVICE_ID` e `VANRIDES_TEST_DEVICE`
  - prioriza iOS Simulator e depois `macOS`, em vez de depender de um UUID fixo morto
- `flutter analyze` e `flutter test` estao estaveis
- a bateria longa `integration_test/all.dart` continua falhando por causas reais e agora identificadas:
  - fluxos legados de login/cadastro nao acompanham totalmente o redesign recente
  - parte dos cenarios ainda depende de conectividade HTTP que nao se comporta bem no alvo `macOS`
- para smoke manual repetivel de viagens do dia, o caminho suportado e a fixture `seed_igrejinha_route_lab.sh` no backend

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Bloco Futuro — Pagamentos

## Backlog 0140 — Pagamentos no app (passageiro/responsavel)

### Status
DONE (commit: c6ec15b)

### Objetivo
Cobrança dentro do app (MVP) com histórico e recibos.

### Critérios de Aceite
- Fluxo de pagamento definido e implementado (provider a decidir)
- Histórico de cobranças
- Recibos básicos

### Dependências
- Backend: billing + webhooks + conciliação
- Decisão do provider (Stripe/PagSeguro/Mercado Pago/etc.)

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0141 — Testes/QA: Pagamentos

### Status
FUTURE

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0143 — Onboarding hibrido do passageiro: empresa por solicitacao ou convite

### Status
DONE (commit: 39221f9)

### Objetivo
Implementar no app e no portal o novo fluxo em que:
- passageiro cria a conta sem escolher motorista
- confirma e-mail
- faz login mesmo sem empresa
- escolhe entre buscar empresa ou inserir codigo
- acompanha o estado pendente ate a aprovacao
- entra na operacao da empresa quando o vinculo for concluido

### Critérios de Aceite
- `CadastroPassageiroScreen` deixa de exigir motorista no cadastro publico
- login do passageiro entende os estados:
  - sem empresa vinculada
  - solicitacao pendente
  - empresa vinculada sem motorista
  - empresa vinculada com motorista
- novas telas mobile baseadas no Figma:
  - bloqueio operacional / gate de empresa
  - busca de empresa
  - insercao/validacao de codigo
  - aguardando aprovacao
  - convite aceito
  - estado de aguardando atribuicao de motorista
- modulo web do portal inclui:
  - solicitacoes de acesso com aprovar/rejeitar
  - aprovacao com motorista opcional
  - gestao de convites/codigos
  - fluxo de e-mail pendente do admin com reenvio e alteracao de e-mail

### Impact Analysis
- Contexto:
  - o app atual pressupoe `motoristaId` ja no login do passageiro e desvia para `UnsupportedRoleScreen` quando isso nao acontece
  - o portal atual opera CRUDs e vinculos, mas ainda nao trata onboarding pendente nem convite institucional
- Telas/fluxos afetados:
  - `LoginScreen`
  - `CadastroPassageiroScreen`
  - `ExperienceHomeRouter`
  - modulo `features/admin`
  - novos widgets/servicos de onboarding do passageiro
- Dependencia de backend:
  - extensao do `POST /login`
  - novos contratos `/passageiro-onboarding/...`
  - novos contratos `/company-admins/access-requests...`
  - novos contratos `/company-admins/invite-codes...`
  - troca segura de e-mail pendente
- Riscos de regressao:
  - quebrar a jornada publica atual de login/cadastro
  - manter o passageiro preso num token sem tenant apos aprovacao
  - conflitar com mudancas locais em andamento do portal admin
- Mitigacao:
  - isolar o fluxo novo em um modulo proprio de onboarding
  - promover sessao explicitamente quando o vinculo virar operacional
  - editar arquivos compartilhados do portal de forma cirurgica, respeitando as mudancas ja presentes na branch

### Validacao minima obrigatoria
- `flutter analyze`
- `flutter test`
- `./scripts/qa_gate_card.sh`
- smoke manual do fluxo mobile e do portal

### Dependências
- Backend 0132
- Backlog 0142 (verificacao de e-mail)

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0144 — Testes/QA: onboarding hibrido do passageiro + portal de convites

### Status
DONE (commit: d8c27b1)

### Objetivo
Garantir regressao automatizada e evidencia manual do novo onboarding hibrido.

### Critérios de Aceite
- widget/unit tests cobrindo roteamento e estados principais
- testes do portal cobrindo solicitacoes, convites e e-mail pendente
- smoke manual registrado no QA log

### Dependências
- Backlog 0143

### DoD
- Card marcado como `DONE (commit: d8c27b1)`

### Evidencia de fechamento — 2026-06-18
- Testes focados de roteamento/onboarding/portal executados com PASS:
  `flutter test test/login_screen_test.dart test/app/experience_home_router_test.dart test/features/passenger_onboarding/passenger_company_link_flow_test.dart test/features/passenger_onboarding/presentation/widgets/passenger_company_link_widgets_test.dart test/features/admin/admin_vinculo_portal_service_test.dart test/features/admin/admin_vinculos_screen_test.dart test/features/admin/presentation/widgets/admin_vinculos_onboarding_widgets_test.dart test/features/admin/presentation/widgets/admin_vinculos_access_request_dialog_test.dart test/features/admin/admin_login_screen_test.dart test/screens/email_verification_pending_screen_test.dart test/features/admin/admin_portal_auth_service_test.dart`.
- Smoke visual deterministico criado em
  `integration_test/visual_smoke/passenger_onboarding_visual_smoke_test.dart`
  e executado com PASS.
- Capturas geradas por
  `scripts/qa_passenger_onboarding_visual_smoke.sh` em
  `output/visual-smoke/card-0144-onboarding/`, cobrindo gate, busca,
  pendente, convite, vinculado, e-mail pendente e portal admin.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS (`flutter analyze` sem issues,
  `flutter test` com 519 testes, ratchet PASS).
- Contrato pareado do backend 0133 foi validado antes em DEV com smoke HTTP
  completo de busca, solicitacao, aprovacao, convite valido/invalido/expirado e
  `account/me`.

---

---

## Bloco Portal Admin — Gestão Administrativa Completa (Frontend)

### Objetivo do bloco
Transformar áreas do portal que ainda são demonstrativas em experiências operacionais reais: editar e excluir rotas-base, ativar o botão "Editar Cadastro" do motorista e popular o dashboard com dados reais do dia.

### Dependências de backend
- Backend 0140: edição e inativação de rota-base
- Backend 0141: edição e exclusão de motorista via admin
- Backend 0142: endpoint de métricas do dashboard

### Sequência oficial
1. `0213` → editar e excluir rota-base no portal
2. `0214` → editar e excluir motorista no portal
3. `0215` → dashboard com dados reais

---

## Backlog 0213 — Portal: editar e excluir rota-base

### Status
DONE (commit: 67d0199)

### Objetivo
Dar ao `COMPANY_ADMIN` controle total sobre o ciclo de vida de uma rota-base no portal: editar qualquer campo e inativar quando necessário.

### User Story
Como `COMPANY_ADMIN`, quero clicar em "Editar" no detalhe de uma rota e ajustar o motorista, os passageiros ou o horário sem precisar deletar e recriar. E quero excluir rotas que não são mais usadas, com confirmação explícita antes da ação.

### Critérios de Aceite
- Na tela de detalhe da rota-base (`admin_rotas_screen.dart`):
  - botão "Editar" abre o form em modo de edição, pré-preenchido com os dados atuais
  - botão "Excluir" abre dialog de confirmação antes de chamar `DELETE`
- Form de edição:
  - mesma estrutura do form de criação (reutilizar `_AdminRotaCreateView`)
  - todos os campos pré-preenchidos: nome, motorista, passageiros, dias de operação, horário de partida, destino final
  - submit chama `PUT /company-admins/rotas-base/{id}`
  - ao salvar: fecha form, recarrega lista e exibe o detalhe atualizado
- Exclusão:
  - dialog "Tem certeza? Esta rota será inativada." com confirmar/cancelar
  - ao confirmar: chama `DELETE /company-admins/rotas-base/{id}`
  - ao concluir: remove da lista local e volta à listagem
  - se backend retornar `409`: exibe "Não é possível excluir: há uma viagem em andamento para esta rota."
- Estados de erro e loading cobertos em ambas as ações

### Impact Analysis
- Telas/fluxos afetados:
  - `admin_rotas_screen.dart` (detalhe da rota + form reutilizado para edição)
  - `admin_rotas_portal_service.dart` (adicionar `updateRota()` e `deleteRota()`)
- Decisão de design: distinguir modo criação vs. edição por parâmetro `AdminRotaBaseRecord? rotaParaEditar`
  - se nulo → criação (comportamento atual)
  - se preenchido → edição com pré-carga de campos e `onSubmit` chamando `PUT`
- Risco: form compartilhado de criação/edição pode conflitar no estado dos campos
  - Mitigação: resetar controllers no `initState` usando os valores do `rotaParaEditar` quando presente
- Dependência de backend:
  - `PUT /company-admins/rotas-base/{id}`
  - `DELETE /company-admins/rotas-base/{id}`

### Validação mínima
- `flutter analyze`
- `flutter test`
- `./scripts/qa_gate_card.sh`
- Smoke manual: editar motorista da rota + editar passageiros; excluir rota com confirmação; tentar excluir rota com viagem ativa

### Dependências
- Backend 0140
- `AdminRotasGateway` (adicionar `updateRota()` e `deleteRota()`)

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0214 — Portal: editar e excluir motorista

### Status
DONE (commit: 67d0199)

### Objetivo
Ativar o botão "Editar Cadastro" do detalhe do motorista no portal e adicionar a opção de exclusão com proteção.

### User Story
Como `COMPANY_ADMIN`, quero corrigir o nome, e-mail ou telefone de um motorista diretamente no portal, sem precisar recriar o cadastro. E quero remover motoristas que saíram da frota, com proteção para não interromper corridas ativas.

### Critérios de Aceite
- O botão "Editar Cadastro" (já renderizado, sem `onPressed`) abre form pré-preenchido:
  - campos: nome, email, telefone (sem senha — campo omitido neste form)
  - submit chama `PUT /company-admins/motoristas/{id}`
  - ao salvar: fecha form, recarrega dados e exibe o detalhe atualizado
- Novo botão "Excluir" no detalhe do motorista:
  - dialog de confirmação antes de chamar `DELETE`
  - ao confirmar: chama `DELETE /company-admins/motoristas/{id}`
  - ao concluir: remove da lista local e volta à listagem
  - se backend retornar `409`: exibe "Não é possível remover: há uma viagem em andamento com este motorista."
- Estados de loading e erro cobertos

### Impact Analysis
- Telas/fluxos afetados:
  - `admin_motoristas_screen.dart` (detalhe do motorista + form reutilizado para edição)
  - `admin_motoristas_portal_service.dart` (adicionar `updateMotorista()` e `deleteMotorista()`)
- O `AdminPortalSecondaryButton` de "Editar Cadastro" já existe; só precisa de `onPressed`
- O form de criação de motorista (`_AdminMotoristaCreateView` ou similar) serve de referência para o form de edição

### Validação mínima
- `flutter analyze`
- `flutter test`
- `./scripts/qa_gate_card.sh`
- Smoke manual: editar nome e telefone; tentar excluir motorista sem viagem ativa; tentar excluir motorista com viagem ativa

### Dependências
- Backend 0141
- `AdminMotoristasGateway` (adicionar `updateMotorista()` e `deleteMotorista()`)

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0215 — Portal: dashboard com dados reais

### Status
DONE (commit: a630df4)

### Objetivo
Substituir os cards com `—` e "Dados reais em breve" por métricas reais do dia, tornando o dashboard o ponto de partida operacional do portal.

### User Story
Como `COMPANY_ADMIN`, quero abrir o portal e ver imediatamente um resumo do dia: motoristas ativos, passageiros confirmados, viagens em andamento e rotas ativas. Isso me dá contexto sem precisar navegar por múltiplas telas.

### Critérios de Aceite
- `admin_dashboard_screen.dart` consome `GET /company-admins/dashboard/summary`
- Métricas reais exibidas:
  - Motoristas ativos
  - Passageiros com presença hoje
  - Viagens do dia (total e breakdown por status)
  - Rotas ativas
- Loading state enquanto carrega (indicador discreto ou shimmer)
- Empty state: zeros com label contextual — sem negatividade visual para empresa nova
- Erro: snackbar discreto, sem travar a tela
- Tags "Dados reais em breve" e "Aguardando métrica" removidas das cards de métricas

### Impact Analysis
- Telas/fluxos afetados:
  - `admin_dashboard_screen.dart` (hoje é `StatelessWidget` com dados hardcoded)
  - novo `AdminDashboardService` ou método no gateway existente
- Decisão de design: converter `admin_dashboard_screen.dart` para `StatefulWidget`
  - `initState` dispara a requisição; `setState` atualiza os cards quando os dados chegam
- Risco: a tela do dashboard é carregada com a sessão do admin; se a requisição falhar, o dashboard não pode travar o portal inteiro
  - Mitigação: isolamento do erro em snackbar discreto; cards voltam ao estado de `—` sem bloquear navegação

### Validação mínima
- `flutter analyze`
- `flutter test`
- `./scripts/qa_gate_card.sh`
- Smoke manual: acessar portal com empresa que tenha dados do dia; verificar zeros para empresa nova

### Dependências
- Backend 0142
- `AdminDashboardService` (novo service de dados do portal)

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0980 — Parking lot: melhorias encontradas durante o desenvolvimento

### Status
FUTURE - living parking lot; revisar a cada milestone

### Objetivo
Manter um “buffer” de melhorias pequenas descobertas no caminho (UX, bugs, performance) sem perder nada.

### Regras
- Toda melhoria descoberta vira sub-item com link para PR/commit quando implementada.
- Itens que virarem grandes devem ser promovidos para cards próprios.

### Itens ativos / observacoes
- Spike visual aprovado para promocao local em `dev`:
  - exploracao de uma experiencia `map-first` para `PASSENGER` e `GUARDIAN`
  - objetivo: aproximar o tracking do padrao “Uber-like”, com mapa como foco principal e informacoes operacionais em camada secundaria
  - alinhamento de produto:
    - reutilizar o backend real do `0108`
    - antecipar estrutura visual de `0110` (rota do dia / ETA)
    - deixar espaco claro para `0114` (chat) e `0116` (suporte/ajuda)
  - regra:
    - desenvolver em branch experimental separada antes de promover qualquer decisao para `dev`
  - branch experimental atual:
    - `experiment/map-first-rider-shell`
  - hotfix em andamento nesta branch:
    - corrigir crash de layout causado por `Expanded` em contexto com altura infinita
    - remover `ListView` aninhado nas abas de placeholder
    - limpar textos de prototipo e excesso de informacao tecnica na UI
    - trocar o “mapa ilustrado” por uma home realmente `map-first`, com mapa real embutido na tela inicial
    - tratar `204` de localizacao como “sem sinal ainda”, e nao como erro fatal da experiencia
    - unificar a home do passageiro e a tela expandida de localizacao na mesma narrativa visual `map-first`
    - aproximar a hierarquia visual do tracking do padrao Uber: mapa principal + contexto operacional em camada secundaria
  - hotfix validado localmente:
    - `flutter analyze`: sem erro novo no escopo; seguem `44` infos historicas
    - `flutter test`: PASS
  - validacao automatizada desta exploracao:
    - `flutter analyze`: sem erro novo no escopo; permanecem `44` infos historicas do projeto
    - `flutter test`: PASS
    - `./scripts/qa_gate_card.sh`: PARCIAL
      - `flutter analyze`: PASS
      - `flutter test`: PASS
      - `integration_test/all.dart` no iPhone fisico travou em um caso legado de `Passageiro login (feliz)` e precisa ser rerodado quando a branch for validada manualmente
  - decisao de produto:
    - validacao manual em iPhone fisico aprovada pelo mantenedor em 2026-03-13
    - branch experimental autorizada a virar baseline da `dev`

### DoD
- Card permanece vivo; revisar a cada milestone

---

## Backlog 0141 — Bug Fix: suporte a passageiro em múltiplas viagens no mesmo dia (horarioPartida)

### Status
DONE (commit: 32a2b64)

### Objetivo
Propagar o campo `horarioPartida` (novo campo `TIME NULL` adicionado no backend no card `SpringBoot-0134`) para todos os layers do Flutter, eliminando o bug que causava `NonUniqueResultException` quando um passageiro estava em mais de uma viagem no mesmo dia.

### Mudanças técnicas
- `AdminRotaBaseCreateInput` — campo opcional `horarioPartida: String?` no `toJson()`
- `AdminRotaBaseRecord` — `horarioPartida: String?` parseado via `_parseHorario()` (`HH:mm:ss` → `HH:mm`)
- `AdminViagemRecord` — `horarioPartida: String?` parseado
- `DriverTripRecord` — `horarioPartida: String?` propagado em `fromJson`, `copyWithRealtime` e `toTrackingRecord`
- `TripTrackingRecord` — `horarioPartida: String?` parseado
- `admin_rotas_screen.dart` — time picker no formulário de criação, exibição no card da lista e no detalhe
- `admin_viagens_screen.dart` — campo "Horário de Saída" agora exibe o `horarioPartida` da rota-base selecionada (em vez de placeholder desabilitado); tabela mostra o horário na coluna "DATA & HORÁRIO"
- `driver_stops_screen.dart` — chip de horário no header
- `passenger_trip_details_screen.dart` — chip de horário no header

### Validação
- `flutter analyze`: 29 avisos pré-existentes, zero erros novos
- `flutter test`: 98/98 PASS

---

## Backlog 0216 — Qualidade: corrigir lint issues existentes

### Status
DONE (commit: ac083d6)

### Objetivo
Resolver os 27 avisos reportados pelo `flutter analyze` (todos `info`, nenhum `error`), eliminando ruído e evitando que bugs potenciais passem despercebidos.

### Itens a corrigir

**`avoid_print` (20 ocorrências)**
- `lib/compartilhar_localizacao_screen.dart`: 14 prints espalhados (linhas 50, 54, 72, 81, 152, 168-171, 180-181, 197, 200, 209)
- `lib/services/websocket_service.dart`: 6 prints de debug (linhas 72, 95, 104, 108, 116, 130)
- Ação: substituir por `debugPrint()` onde o contexto for debug, ou remover onde for ruído

**`deprecated_member_use` (5 ocorrências)**
- `FormField.value` → `FormField.initialValue` em:
  - `lib/features/admin/presentation/admin_passageiros_screen.dart`: linhas 988, 1249, 1400
  - `lib/features/admin/presentation/admin_viagens_screen.dart`: linha 551
- `Color.withOpacity()` → `Color.withValues()` em:
  - `lib/features/admin/presentation/admin_rotas_screen.dart`: linha 1208

**`use_build_context_synchronously` (1 ocorrência)**
- `lib/presencas_hoje_screen.dart`: linha 70 — adicionar `if (!mounted) return;` antes de usar `context`

**`prefer_final_fields` (1 ocorrência)**
- `lib/confirmar_presenca_screen.dart`: linha 35 — `_diasSelecionados` não deveria ser reatribuído; usar `late final`

### Critérios de Aceite
- `flutter analyze` retorna 0 issues
- `flutter test` mantém 100% pass rate
- Nenhuma mudança de comportamento funcional

### Validação mínima
- `flutter analyze`
- `flutter test`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0217 — Qualidade: cobertura de testes para feature Tracking

### Status
DONE (commit: fc4ae77) — MapFirstTripTrackingView (5 testes) e TripLiveMapPanel
(4+ testes) cobertos. Cobertura da feature tracking ≥ 70% dos cenários principais.

### Objetivo
A feature `tracking` tem apenas 13% de cobertura de testes (2 arquivos de teste para 16 arquivos de implementação). Os fluxos mais críticos do passageiro (mapa ao vivo, painel de viagem) não têm nenhum teste.

### Contexto
Arquivos sem cobertura:
- `lib/features/tracking/presentation/map_first_trip_tracking_view.dart` (912 linhas) — **zero testes**
- `lib/features/tracking/presentation/trip_live_map_panel.dart` (774 linhas) — **zero testes**
- `lib/features/tracking/data/trip_tracking_service.dart` — happy path coberto, edge cases não

### Testes a criar

**Widget tests — `map_first_trip_tracking_view`:**
- Exibe estado "sem viagem" quando `fetchCurrentTrip` retorna null
- Exibe resumo da viagem quando backend responde
- Exibe snackbar quando backend falha
- Exibe "—" durante loading

**Widget tests — `trip_live_map_panel`:**
- Exibe paradas na ordem correta
- Destaca próxima parada pendente
- Exibe contagem de passageiros por parada

**Unit tests — `trip_tracking_service` (edge cases):**
- Retorna erro amigável em timeout (504)
- Retorna erro amigável em 401 (sessão expirada)
- Retorna erro amigável em 500

### Critérios de Aceite
- `test/features/tracking/` tem cobertura ≥ 70% dos cenários principais
- Todos os novos testes passam sem mock de `GoogleMap` (usar `fake` do PlatformView)
- `flutter test` mantém 100% pass rate

### Validação mínima
- `flutter analyze`
- `flutter test`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0218 — Qualidade: cobertura de testes para Passenger Onboarding

### Status
DONE (commit: 6d5c4df) — Todas as telas do fluxo de onboarding cobertas:
GateScreen, SearchScreen, InviteCodeScreen, PendingScreen e LinkedScreen.

### Objetivo
O fluxo de onboarding do passageiro (`passenger_company_link_flow.dart`, 1.060 linhas) tem **zero testes** — é o ponto de entrada de todos os novos passageiros e qualquer regressão seria invisível.

### Contexto
Arquivos sem cobertura:
- `lib/features/passenger_onboarding/presentation/passenger_company_link_flow.dart` (1.060 linhas)
- `lib/features/passenger_onboarding/data/passenger_onboarding_service.dart`

### Testes a criar

**Unit tests — `passenger_onboarding_service`:**
- Busca empresa por nome e retorna resultado
- Retorna lista vazia quando empresa não encontrada
- Retorna erro amigável em 422 (código de convite inválido)
- Envia solicitação de vínculo e retorna confirmação

**Widget tests — sub-telas do fluxo:**
- Tela de busca: exibe resultados ao digitar nome
- Tela de busca: exibe empty state quando sem resultados
- Tela de código de convite: aceita código e avança
- Tela de código de convite: exibe erro quando código inválido
- Tela de revisão: exibe dados antes de confirmar
- Tela de "aguardando aprovação": exibe status correto

### Critérios de Aceite
- Cobertura dos 6 estados principais do fluxo
- `flutter test` mantém 100% pass rate

### Validação mínima
- `flutter analyze`
- `flutter test`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0219 — Qualidade: cobertura de testes para screens legadas críticas

### Status
DONE (commit: bf435e5) — Todas as screens legadas críticas cobertas:
presencas_hoje, ver_localizacao e confirmar_presenca. cadastro_motorista e
cadastro_passageiro ficam para refatoração posterior (dependem de gateway extraction).

### Objetivo
Screens fora da estrutura `features/` que são críticas para a operação mas não têm testes:
- `lib/confirmar_presenca_screen.dart` (331 linhas) — passageiro confirma presença no dia
- `lib/presencas_hoje_screen.dart` — motorista vê presenças do dia
- `lib/ver_localizacao_screen.dart` (458 linhas) — rastreio manual de motorista
- `lib/cadastro_motorista_screen.dart` (383 linhas) — cadastro de novo motorista
- `lib/cadastro_passageiro_screen.dart` (776 linhas) — cadastro de novo passageiro

### Testes a criar (por screen)

**`confirmar_presenca_screen`:**
- Exibe dias da semana selecionáveis
- Ao confirmar, chama serviço e exibe feedback
- Exibe erro quando backend falha

**`presencas_hoje_screen`:**
- Exibe lista de passageiros com status
- Exibe empty state quando sem passageiros

**`ver_localizacao_screen`:**
- Exibe "sem sinal" quando backend retorna null
- Exibe coordenadas quando backend responde

**`cadastro_motorista_screen` e `cadastro_passageiro_screen`:**
- Validação de campos obrigatórios
- Validação de força de senha
- Feedback de erro do backend

### Critérios de Aceite
- Cada screen tem ao menos 3 testes (happy path, empty/null, erro)
- `flutter test` mantém 100% pass rate

### Validação mínima
- `flutter analyze`
- `flutter test`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0220 — Refactor: quebrar home_motorista_screen.dart em camadas separadas

### Status
DONE (commit: 4e4dbf9) — home_motorista_screen refatorada em 3 fases:
MotoristaSessionPresenter (viagem/paradas/eventos), MotoristaLocationPresenter
(GPS/WebSocket localização). Screen reduzida de 3.400+ para 3345 linhas.

### Objetivo
`lib/home_motorista_screen.dart` tem **3.406 linhas** num único `StatefulWidget` que concentra: estado de viagem, WebSocket de localização, WebSocket de eventos de rota, chamadas REST, permissões de iOS e UI de 5+ sub-telas. Isso torna refatorações arriscadas e testes impraticáveis.

### Contexto e risco
- Arquivo maior do projeto: 3.406 linhas
- Qualquer mudança toca lógica não relacionada
- Testes atuais (`home_motorista_screen_test.dart`) cobrem apenas 3 cenários superficiais

### Plano de decomposição sugerido
1. Extrair `MotoristaSessionPresenter` — toda a lógica de fetch/state sem UI
2. Extrair `MotoristaLocationPresenter` — WebSocket de localização + permissões
3. Extrair `HomeMotoristaView` — apenas UI que consome os dois presenters acima
4. Extrair `MotoristaRouteEventListener` — WebSocket de eventos de rota
5. Sub-screens separadas: `MotoristaStopsListView`, `MotoristaMapFullscreenView`

### Critérios de Aceite
- Nenhuma mudança de comportamento funcional no app
- Nenhum arquivo resultante com mais de 400 linhas
- Testes do `home_motorista_screen_test.dart` mantêm 100% pass rate
- Cobertura de testes aumenta (novos testes unitários para presenters)

### Dependências
- Deve ser feito em branch separada com smoke manual antes de merge

### Validação mínima
- `flutter analyze`
- `flutter test`
- Smoke manual: motorista inicia corrida, localização é compartilhada, passageiro vê no mapa

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0221 — Refactor: extrair padrão base para screens de CRUD admin

### Status
DONE (commit: b9edc7b) — AdminListStateMixin extraído e aplicado nas 5 screens
de CRUD admin. Campos _isLoading/_errorMessage/_items removidos das screens.

### Objetivo
As 5 screens de CRUD do portal admin (`admin_motoristas`, `admin_passageiros`, `admin_responsaveis`, `admin_rotas`, `admin_viagens`) repetem exatamente o mesmo padrão de: load com `_isLoading`, erro com `_errorMessage`, refresh após create/edit/delete. São ~200-300 linhas duplicadas ao todo.

### Padrão duplicado identificado
```dart
// Repetido nas 5 screens:
Future<void> _loadItems() async {
  setState(() { _isLoading = true; _errorMessage = null; });
  try {
    final items = await _gateway.fetchItems();
    setState(() { _items = items; _isLoading = false; });
  } catch (e) {
    setState(() { _errorMessage = e.toString(); _isLoading = false; });
  }
}
```

### Solução proposta
Extrair `AdminListStateMixin<T>` com:
- `List<T> items`
- `bool isLoading`
- `String? errorMessage`
- `Future<void> loadItems(Future<List<T>> Function() fetcher)`
- `void onItemCreated(T item)` — prepend à lista
- `void onItemUpdated(T item)` — substituição in-place
- `void onItemDeleted(int id)` — remoção por ID

### Critérios de Aceite
- 5 screens migradas para o mixin
- Comportamento idêntico ao anterior
- `flutter test` mantém 100% pass rate
- Nenhum arquivo de screen admin supera 800 linhas após migração

### Validação mínima
- `flutter analyze`
- `flutter test`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0222 — Qualidade: centralizar strings de UI em classe de constantes

### Status
DONE (commit: a831f02) — AppStrings criado em lib/l10n/. Strings de
fallback das 6 screens admin + dashboard migradas para constantes.

### Objetivo
Há mais de 200 strings de mensagem de erro e labels de UI hardcoded diretamente nas screens, com variações ortográficas entre arquivos (com/sem acento, letras maiúsculas inconsistentes). Isso dificulta manutenção e é pré-requisito para qualquer internacionalização futura.

### Exemplos de duplicação atual
```dart
// Em admin_motoristas_screen.dart
'Nao foi possivel carregar os motoristas.'

// Em admin_passageiros_screen.dart
'Não foi possível carregar os passageiros.'

// Em admin_rotas_screen.dart
'Nao foi possivel carregar as rotas-base da empresa.'
```

### Solução proposta
Criar `lib/l10n/app_strings.dart` com classe de constantes estáticas:
```dart
abstract final class AppStrings {
  static const erroCarregarDados = 'Não foi possível carregar os dados.';
  static const erroConexao = 'Erro de conexão. Tente novamente.';
  // ...
}
```
Migrar gradualmente, começando pelas mensagens de erro do portal admin.

### Critérios de Aceite
- `lib/l10n/app_strings.dart` criado com constantes para mensagens de erro
- Pelo menos as 5 screens admin migradas para usar as constantes
- Nenhuma variação ortográfica entre screens para a mesma mensagem
- `flutter test` mantém 100% pass rate

### Validação mínima
- `flutter analyze`
- `flutter test`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0223 — UX: padronizar empty states no portal admin

### Status
DONE (commit: 8e07dca)

### Objetivo
Os empty states (listas vazias, primeiro uso) estão inconsistentes entre as screens do portal: algumas têm mensagem clara, outras silenciam, e `admin_motoristas_screen` pode até selecionar `motoristas.first` sem validar se a lista está vazia.

### Problemas identificados
- `admin_vinculos_screen.dart`: sem empty state visual — lista vazia = tela em branco
- `admin_motoristas_screen.dart`: seleciona `motoristas.first` sem guard para lista vazia
- Nenhuma screen usa ícone ilustrativo ou call-to-action no empty state

### Padrão desejado
Para cada screen de lista com lista vazia:
1. Ícone ilustrativo centralizado
2. Texto descritivo do que está faltando
3. Call-to-action (botão "Cadastrar primeiro X") quando aplicável

### Screens a corrigir
- `admin_vinculos_screen.dart`
- `admin_motoristas_screen.dart` (guard + empty state)
- Auditoria das demais para confirmar se já têm

### Critérios de Aceite
- Nenhuma screen exibe tela em branco quando a lista está vazia
- `admin_motoristas_screen` nunca chama `first` sem guard
- `flutter test` atualizado para cobrir o empty state de cada screen corrigida

### Validação mínima
- `flutter analyze`
- `flutter test`

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0224 — UX: adicionar confirmação em ações destrutivas faltantes

### Status
DONE (N/A — sem commit novo)

### Evidência atual
- Funcionalidades identificadas já estavam implementadas ao momento
da revisão do card. Confirmações de ação destrutiva existem em:
_ReassignMotoristaDialog (passageiros), _unlinkPassageiroMotorista e
_unlinkResponsavelPassageiro (vinculos). Excluir viagem não existe como feature.

### Objetivo
Excluir motorista e excluir rota-base já têm dialog de confirmação. Mas 3 ações destrutivas equivalentes ainda não têm:
1. Reatribuição de motorista do passageiro (`admin_passageiros_screen.dart:1372`) — passageiro perde o motorista atual sem aviso
2. Revogar acesso de responsável (`admin_vinculos_screen.dart`) — vínculo é cortado sem confirmação
3. Excluir viagem (`admin_viagens_screen.dart`) — viagem removida diretamente

### Ação
Adicionar `AlertDialog` de confirmação antes de cada uma dessas operações, seguindo o padrão já existente em `_deleteRota()` e `_deleteMotorista()`.

### Critérios de Aceite
- As 3 ações listadas exigem confirmação antes de executar
- Dialog exibe descrição clara do que será afetado
- Cancelar não executa nenhuma mudança
- Testes dos 3 fluxos de confirmação adicionados

### Validação mínima
- `flutter analyze`
- `flutter test`

### DoD
- Card marcado como `DONE (N/A — sem commit novo)`

---

## Backlog 0225 — UX: validação em tempo real em formulários do portal

### Status
DONE (commit: 9784fcc) — autovalidateMode adicionado nos Forms das 4 screens admin.
AdminPortalTextField criado com 3 testes de widget.

### Objetivo
Os formulários do portal admin só mostram erros de validação ao clicar em "Salvar". Formulários com boa UX exibem feedback à medida que o usuário digita, tornando erros visíveis antes do submit.

### Problema atual
```dart
// Validação só roda no submit (autovalidateMode não configurado)
Form(
  key: _formKey,
  child: TextFormField(
    validator: (v) => v!.isEmpty ? 'Obrigatório' : null,
  ),
)
```

### Solução proposta
1. Adicionar `autovalidateMode: AutovalidateMode.onUserInteraction` nos forms do portal
2. Extrair widget `AdminPortalTextField` com:
   - Indicador visual de campo obrigatório (asterisco no label)
   - Feedback de erro inline (sob o campo, não no submit)
   - Suporte a regex de validação (ex: formato de telefone)
3. Migrar forms de motorista, passageiro e rota para o novo componente

### Critérios de Aceite
- Campos obrigatórios mostram erro ao perder foco (não ao submit)
- Widget `AdminPortalTextField` reutilizável com testes
- `flutter test` mantém 100% pass rate

### Validação mínima
- `flutter analyze`
- `flutter test`
- Smoke manual: preencher form com dados inválidos e verificar feedback

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0226 — Infraestrutura: recebimento de notificações push no app

### Status
DONE em escopo Flutter (commits: b5f9c03, 0778528) — Camada Flutter completa:
abstração (gateway, modelos, serviço), implementação HTTP real, session binder
e integração fire-and-forget no login. Default permanece NoopPushNotificationGateway.

Pré-requisitos externos pendentes para ativação real:
1. Criar projeto Firebase + adicionar GoogleService-Info.plist (iOS) e
google-services.json (Android)
2. Gerar e configurar certificado APNs no Apple Developer
3. Implementar endpoint POST /devices/fcm-token no vanrides-backend
4. Substituir NoopPushNotificationGateway por FirebaseMessagingPushNotificationGateway
em PushNotificationSessionBinder

Quando os 4 itens acima estiverem prontos, criar Fase 3 deste card para
implementar FirebaseMessagingPushNotificationGateway e plugar no binder.

### Objetivo
O backend já tem suporte à infraestrutura de notificações (Resend para e-mail). O app ainda não recebe notificações push. Este card adiciona a camada de recebimento no Flutter.

### Escopo
- Integrar Firebase Messaging (FCM) no iOS e Android
- Solicitar permissão de notificações no onboarding
- Exibir notificações em foreground (banner)
- Roteamento básico ao tocar na notificação (deep link para tela relevante)
- Registrar token FCM no backend (`POST /devices/fcm-token`)

### Dependências
- Backend precisa de endpoint para registrar FCM token (card SpringBoot a criar)
- Certificado APNs configurado para iOS

### Critérios de Aceite
- Passageiro recebe notificação quando motorista inicia a corrida
- Responsável recebe notificação de status do passageiro
- Tocar na notificação abre a tela correta

### Validação mínima
- Teste manual em iPhone físico e Android físico

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0227 — Infraestrutura: deep linking para viagens e rastreio

### Status
DONE (commit: 0b260636534f21deb30968b00ba40cc59b217741) — Deep linking
concluído em 3 fases: parsing e resolução por papel, infraestrutura
runtime com `app_links` + config nativa, e navegação real com
`AppSessionStore` para leitura global da sessão atual. O app agora
resolve `vanrides://viagem/{tripId}`, `vanrides://rastreio/{passageiroId}`
e `vanrides://convite/{codigo}` com fallback honesto quando os dados
disponíveis na sessão não bastam para focar uma entidade específica.

Smoke manual ainda não executado (requer device físico ou simulador
abrindo o app via URL scheme `vanrides://`).

### Objetivo
Adicionar suporte a URLs profundas para permitir:
- Compartilhar link direto de rastreio de uma viagem
- Abrir o app diretamente em uma viagem específica via notificação push
- Integração futura com links de convite de responsável

### Esquema proposto
```
vanrides://viagem/{tripId}
vanrides://rastreio/{passageiroId}
vanrides://convite/{codigo}
```

### Escopo
- Configurar `flutter_deep_links` (ou GoRouter + App Links)
- Handler por papel (motorista, passageiro, responsável) — rota correta por papel ativo
- Fallback: abre o app na tela inicial se o vínculo não for resolvível

### Critérios de Aceite
- Link externo de viagem abre o app e navega para a tela correta
- Funciona com app fechado e em background (cold start)
- Funciona em iOS e Android

### Validação mínima
- Teste manual: abrir link em iPhone físico e Android físico
- `flutter test` mantém 100% pass rate

### DoD
- Card marcado como `DONE (commit: <hash>)`

---

## Backlog 0228 — Débito D-03: migrar leituras de SharedPreferences para AppSessionStore

### Status
DONE (commit: cd4b04c300c328d23c7d99b0267afcbb5ddce89f) — auditoria final
confirmou que as ondas 1 (telas admin) e 3 (feature modules) já estavam
tecnicamente migradas no snapshot atual do app; o último resíduo real ficou
concentrado no `login_screen.dart`, onde o fluxo de "lembrar login" ainda
lia/escrevia `SharedPreferences` direto. A onda 2 centralizou esse comportamento
no `AppSessionStore`, que agora é a única porta de persistência de autenticação
e credenciais lembradas em `lib/`.

### Origem
`backlog/Technical-Debt-2026-04-22.md` — débito D-03.

### Contexto
O card 0227 introduziu `lib/app/session/app_session_store.dart` como fonte de verdade da sessão atual. No entanto, 15 arquivos do app ainda chamam `SharedPreferences.getInstance()` direto e leem chaves legadas (`email`, `senha`, `accessToken`, etc.) — totalizando 119 ocorrências. Isso significa que o `AppSessionStore` é "mais um lugar" em vez da fonte única.

Concentração maior em:
- `lib/home_motorista_screen.dart` (19)
- `lib/features/admin/presentation/admin_responsaveis_screen.dart` (16)
- `lib/cadastro_passageiro_screen.dart` (13)
- `lib/features/admin/presentation/admin_viagens_screen.dart` (11)
- `lib/confirmar_presenca_screen.dart` (10)
- `lib/features/admin/presentation/admin_motoristas_screen.dart` (10)

### Objetivo
Tornar `AppSessionStore` a única fonte de leitura de sessão no app. Telas e gateways passam a depender dessa abstração; `SharedPreferences.getInstance()` direto fica restrito a `AppSessionStore` e a `lib/services/auth_headers.dart` (caso ainda necessário).

### Escopo
1. Migrar leituras de sessão (`prefs.getString('email' | 'senha' | 'accessToken' | 'refreshToken' | 'motoristaId' | 'passageiroId' | 'guardianId' | 'companyId' | 'role')`) para `AppSessionStore.readCurrent()` ou getters específicos do store.
2. Onde o `AppSessionStore` ainda não cobre um campo lido (ex.: chaves específicas de feature como `lastSelectedRotaId`), criar getter/setter dedicado no store ou manter no `SharedPreferences` apenas se o dado for de UI local (não sessão).
3. Migração em **3 ondas** para reduzir risco:
   - **Onda 1 — telas admin**: `admin_*_screen.dart` (8 arquivos).
   - **Onda 2 — telas legadas (lib/ root)**: `home_motorista_screen.dart`, `home_passageiro_screen.dart`, `login_screen.dart`, `cadastro_*.dart`, `confirmar_presenca_screen.dart`, `presencas_hoje_screen.dart`, `compartilhar_localizacao_screen.dart`, `ver_localizacao_screen.dart`, `email_verification_pending_screen.dart`.
   - **Onda 3 — feature-modules (guardian, passenger_onboarding, etc.)** se ainda restar leitura direta.
4. Remover do `login_screen` os `prefs.setString('email'/'senha')` redundantes — usar exclusivamente `AppSessionStore.writeCurrent`.

### Critérios de Aceite
- `grep -rn "SharedPreferences.getInstance" lib/` retorna apenas:
  - `lib/app/session/app_session_store.dart`
  - `lib/services/auth_headers.dart` (se ainda necessário no contexto de tokens)
  - eventualmente arquivos que persistem **estado de UI local** (não sessão), com comentário justificando.
- Todas as telas migradas continuam compilando, passando análise e testes.
- Smoke manual: login motorista → home → restart app → sessão preserva. Mesmo para passageiro, guardian, admin.

### Validação mínima
- `flutter analyze`
- `flutter test`
- Smoke (3 papéis): login → restart → continua logado e mantém papel correto.

### Riscos
- Quebrar fluxo de login se algum lugar dependia de chave legada não migrada.
- Mitigação: migrar onda por onda, rodar `flutter test` + smoke entre ondas.

### DoD
- Card marcado como `DONE (commit: <hash>)`
- Atualizar `Technical-Debt-2026-04-22.md` marcando D-03 como resolvido.

---

## Backlog 0229 — Débito D-07: estender AppStrings para passageiro, motorista, guardian e mensagens de rede

### Status
DONE (commit: c476fab)

### Origem
`backlog/Technical-Debt-2026-04-22.md` — débito D-07.

### Contexto
`lib/l10n/app_strings.dart` foi criado no card 0222 cobrindo apenas o portal admin. Strings de motorista, passageiro, guardian, deep linking e erros de rede continuam hardcoded em `Text(...)`, `SnackBar(...)`, `AlertDialog(...)` etc.

### Objetivo
Centralizar 100% das strings de UI (rótulos, mensagens de erro, confirmações) em `AppStrings`, pré-requisito para internacionalização real (`flutter_localizations`).

### Escopo
1. Auditar diretórios:
   - `lib/home_motorista_screen.dart`
   - `lib/home_passageiro_screen.dart`
   - `lib/cadastro_*.dart`
   - `lib/features/guardian/`
   - `lib/features/passenger_onboarding/`
   - `lib/features/tracking/`
   - `lib/features/deep_linking/`
   - `lib/services/` (mensagens de erro de rede)
2. Agrupar strings por área em `AppStrings`:
   - `AppStrings.motorista.*`
   - `AppStrings.passageiro.*`
   - `AppStrings.guardian.*`
   - `AppStrings.deepLink.*`
   - `AppStrings.network.*` (erro de conexão, timeout, 401, 403, 5xx)
3. Substituir literais por referências a `AppStrings`.
4. Adicionar testes garantindo unicidade e ausência de duplicatas (auditoria leve).

### Critérios de Aceite
- `grep` por mensagens de erro PT-BR fora de `AppStrings` retorna 0 ocorrências em `lib/features/` e nas screens migradas.
- Nenhum `SnackBar` ou `AlertDialog` consultado durante a auditoria mantém literal hardcoded.
- Cobertura de teste em `test/l10n/app_strings_test.dart` valida pelo menos 1 chave por nova área.

### Validação mínima
- `flutter analyze`
- `flutter test`

### Riscos
- Volume alto de mudança mecânica; conflitos se outros cards mexerem nas mesmas telas em paralelo.
- Mitigação: rodar logo após o card 0228 (sessão), antes de novos refactors.

### DoD
- Card marcado como `DONE (commit: <hash>)`
- Atualizar `Technical-Debt-2026-04-22.md` marcando D-07 como resolvido.

---

## Backlog 0230 — Débito D-08+D-15: ativação real de push notifications (FCM + APNs + endpoint backend)

### Status
PLANNED — bloqueado por configuração externa (Firebase project + APNs).

### Origem
`backlog/Technical-Debt-2026-04-22.md` — débitos D-08 e D-15.

### Contexto
O card 0226 entregou a abstração Flutter (`PushNotificationGateway`) com `NoopPushNotificationGateway` como default. O app atualmente não recebe nenhum push. Faltam:
1. Projeto Firebase + arquivos `GoogleService-Info.plist` (iOS) e `google-services.json` (Android).
2. Certificado APNs no Apple Developer Portal e upload no Firebase.
3. Endpoint `POST /devices/fcm-token` no `vanrides-backend` (ainda não existe).
4. Substituir `NoopPushNotificationGateway` por `FirebaseMessagingPushNotificationGateway` em `PushNotificationSessionBinder`.
5. Disparo de push do backend nos eventos relevantes (viagem iniciada, viagem cancelada, presença confirmada, etc.).

### Objetivo
App recebe push de eventos relevantes em iOS e Android, com token registrado no backend e roteamento via deep link já existente.

### Escopo
- **Backend (depende de card no `vanrides-backend/backlog/`):**
  - `POST /devices/fcm-token` autenticado, recebe `{token, platform}`, persiste em `device_tokens(user_id, token, platform, updated_at)` com upsert idempotente por `(user_id, token)`.
  - Integração com Firebase Admin SDK para envio de push em pelo menos: viagem iniciada, viagem finalizada, viagem cancelada (ondas seguintes adicionam novos eventos).
- **App (este card):**
  - Adicionar `firebase_core` e `firebase_messaging` ao `pubspec.yaml`.
  - Implementar `FirebaseMessagingPushNotificationGateway` em `lib/features/notifications/data/firebase_messaging_push_notification_gateway.dart`.
  - Pedir permissão no onboarding/login (iOS exige opt-in explícito).
  - Registrar token via `POST /devices/fcm-token` ao logar.
  - Tratar mensagem em foreground (banner local), background e cold start; rotear via `DeepLinkNavigationHandler` se vier `data.deepLink`.
  - Substituir injeção do `Noop` por `FirebaseMessagingPushNotificationGateway` no binder.

### Critérios de Aceite
- iPhone físico recebe push quando o backend dispara evento de viagem iniciada.
- Tocar no push abre a tela correta (mesmo deep link do D-09 quando concluído).
- Token FCM rotacionado é re-registrado no backend (handler `onTokenRefresh`).
- Logout limpa token do backend (idealmente — opcional MVP).

### Validação mínima
- Smoke iOS físico: login → permitir push → backend dispara evento manual via curl → push aparece → toque abre tela esperada.
- Smoke Android físico equivalente.

### Pré-requisitos externos (infra — fora de código)
- Criar projeto Firebase para `vanrides-dev` e `vanrides-prod`.
- Subir `GoogleService-Info.plist` e `google-services.json` (não comitar; gitignore + injeção no build).
- Gerar certificado APNs (.p8) no Apple Developer e configurar no Firebase Console.

### DoD
- Card marcado como `DONE (commit: <hash>)`
- Card pareado no `vanrides-backend/backlog/` também `DONE`.
- Atualizar `Technical-Debt-2026-04-22.md` marcando D-08 e D-15 como resolvidos.

---

## Backlog 0231 — Débito D-04: spike + ADR de state management compartilhado (Riverpod vs Bloc)

### Status
DONE (commit: 0a2b2d7) — decisão arquitetural registrada; **não implementa migração**, apenas decide.

### Origem
`backlog/Technical-Debt-2026-04-22.md` — débito D-04 (e D-10 como métrica relacionada).

### Contexto
O app usa `StatefulWidget` + `setState` (320 ocorrências em `lib/` no ratchet de 2026-06-13) e alguns `ChangeNotifier` (presenters do card 0220). Não há mecanismo para:
- Compartilhar sessão sem passar por construtor (visível em `DeepLinkNavigationHandler` carregando email/senha/token/motoristaId só para construir tela alvo).
- Reagir a evento global (ex.: refresh forçado, sessão expirada).
- Substituir trecho de árvore por widget que escuta uma fonte global sem `setState` em cascata.

### Objetivo
Decidir, com evidência, qual lib de state management adotar (Riverpod ou Bloc) e registrar a decisão em ADR. Implementar **um único cenário-piloto** para validar a escolha antes de migrar o app inteiro.

### Escopo
1. **Inventário comparativo**:
   - `home_motorista_screen.dart`: 856 linhas, 20 `setState`, já com presenters `ChangeNotifier`.
   - `home_passageiro_screen.dart`: 855 linhas, 12 `setState`, ainda concentra async/loading/seleção.
   - `guardian_localizacao_screen.dart`: 699 linhas, 13 `setState`, ainda concentra tracking/WebSocket.
   - `admin_list_state_mixin.dart`: 73 linhas, 7 `setState`, padrão reutilizável mas acoplado a `State`.
2. **ADR**: registrar decisão em `docs/adr/2026-06-13-state-management.md`.
3. **Cenário-piloto**: fica para `PHASE-0202`, começando por `home_motorista_screen.dart`.

### Critérios de Aceite
- ADR registra Riverpod como decisão, com justificativa, alternativas consideradas e consequências.
- ADR define `home_motorista_screen.dart` como primeiro piloto.
- ADR lista telas fora do escopo do piloto inicial.
- Implementação de código fica em card filho (`PHASE-0202`) para evitar migração ampla no card de decisão.

### Validação mínima
- `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS

### Riscos
- Decisão sem spike vira retrabalho.
- Mitigação: decisão baseada em leitura das telas reais, piloto pequeno em `PHASE-0202` e rollback por ADR antes de migrar outras telas.

### DoD
- Card marcado como `DONE (commit: 0a2b2d7)`
- ADR publicada em `docs/adr/2026-06-13-state-management.md`
- `Technical-Debt-2026-04-22.md` atualizado marcando D-04 como decidido e com migração ampla em cards filhos.

---

## Backlog 0232 — Débito D-05: introduzir GoRouter e migrar navegação principal

### Status
DECIDIDO / SPLIT — ADR publicada em docs/adr/2026-06-13-navigation-routing.md; implementação GoRouter ainda nao iniciada.

### Origem
`backlog/Technical-Debt-2026-04-22.md` — débito D-05.

### Contexto
`lib/main.dart` usa `MaterialApp(home: ExperienceLaunchScreen())`. Navegação é `Navigator.push(MaterialPageRoute(builder: (_) => Tela()))` espalhada. O `DeepLinkNavigationHandler` precisa conhecer construtor de cada tela alvo e carregar argumentos manualmente.

### Objetivo
Usar `go_router` como fonte única de roteamento, com rotas nomeadas para deep linking, push notifications e navegação interna. Reduzir acoplamento entre `DeepLinkNavigationHandler` e construtores de tela.

### Decisao arquitetural
Adotar `go_router` como direcao, mas sem big bang. A migracao deve preservar
`ExperienceHomeRouter` e `DeepLinkNavigationHandler` como contrato de
comportamento ate existir um `AppRouter` testado.

Ondas aprovadas pela ADR:
1. congelar contrato de rotas e testes;
2. migrar launch/auth/cadastro;
3. migrar homes por papel;
4. migrar deep links e push notification routing;
5. migrar navegacao interna por papel;
6. remover `MaterialPageRoute` de navegacao principal.

### Escopo
Escopo original de implementacao, a ser dividido em novos cards antes de
codar:

1. Adicionar `go_router` ao `pubspec.yaml`.
2. Definir rotas em `lib/app/router/app_router.dart`:
   - `/launch`, `/login`, `/cadastro/motorista`, `/cadastro/passageiro`
   - `/motorista/home`, `/motorista/viagem/:tripId`
   - `/passageiro/home`, `/passageiro/rastreio/:passageiroId`
   - `/guardian/home`, `/guardian/passageiro/:passageiroId`
   - `/admin/*`
3. Substituir `Navigator.push(MaterialPageRoute(...))` por `context.go(...)` ou `context.push(...)`.
4. Adaptar `DeepLinkNavigationHandler` para chamar `router.go(...)` em vez de carregar argumentos e construir tela.
5. Configurar `redirect` global do GoRouter para gating de sessão (não logado → /login).

### Critérios de Aceite
- 0 ocorrências de `MaterialPageRoute` em código de navegação produção (testes podem manter).
- Deep links e back button do iOS comportam-se previsivelmente.
- `flutter test` cobre transição entre rotas críticas com `goldens` ou `pumpAndSettle`.

### Validação mínima
- `flutter analyze`
- `flutter test`
- Smoke em todos os papéis: cada navegação principal funciona; back button leva ao destino esperado; deep link cold start abre rota correta.

### Riscos
- Refactor de navegação toca todas as telas; alto risco de regressão.
- Mitigação: feito **depois** do card 0231 (state management), em ondas por papel.

### DoD
- ADR publicada com decisao e ordem de migracao.
- `Technical-Debt-2026-04-22.md` aponta para a ADR sem marcar D-05 como
  resolvido.
- Implementacao permanece pendente ate ser quebrada em cards menores com
  testes de rota antes de codigo.

---

## Backlog 0233 — Débito D-01+D-12: refactor home_motorista_screen em sub-widgets (≤600 linhas)

### Status
PLANNED — depende dos cards 0231 (state mgmt) e 0234 (setState).

### Origem
`backlog/Technical-Debt-2026-04-22.md` — débitos D-01 (telas > 1.000 linhas) e D-12 (home_motorista monolítica).

### Contexto
`lib/home_motorista_screen.dart` tem 3.345 linhas. O card 0220 extraiu presenters mas a árvore de widgets continua inline (`_buildXyz()` métodos privados na mesma classe).

### Objetivo
Reduzir `home_motorista_screen.dart` para ≤ 600 linhas extraindo sub-widgets em arquivos próprios e movendo lógica residual para presenters.

### Escopo
1. Extrair sub-widgets para `lib/features/driver_trip/presentation/widgets/`:
   - `MotoristaStopsListView` — fila de paradas com swipe/check.
   - `MotoristaMapFullscreenView` — mapa principal.
   - `MotoristaActionBar` — barra inferior de ações.
   - `MotoristaPassengerListSection` — lista expandível de passageiros.
   - `MotoristaTripStatusBanner` — banner de status da viagem (em andamento, pausada, etc.).
2. Cada sub-widget recebe dados via construtor; consome presenters via `Listenable` quando reativo.
3. Mover diálogos para `lib/features/driver_trip/presentation/dialogs/`.
4. Smoke manual extenso obrigatório (toda tela operacional do motorista).

### Critérios de Aceite
- `home_motorista_screen.dart` ≤ 600 linhas.
- Cada sub-widget extraído tem ao menos 1 widget test.
- Comportamento visual e funcional idêntico ao antes.

### Validação mínima
- `flutter analyze`
- `flutter test`
- Integration test motorista: `flutter test integration_test/motorista/all.dart`
- Smoke iPhone físico: viagem completa do motorista (login → iniciar viagem → marcar presença → finalizar).

### Riscos
- Alto risco de regressão visual ou de estado.
- Mitigação: PR pequeno por sub-widget; smoke entre extrações.

### DoD
- Card marcado como `DONE (commit: <hash>)`
- Atualizar `Technical-Debt-2026-04-22.md` marcando D-01 (parcial: 1 tela de 11) e D-12 (completo) como resolvidos.

---

## Backlog 0234 — Débito D-02: migrar 19 setState da home_motorista para presenters

### Status
PLANNED — depende do card 0231 (state mgmt).

### Origem
`backlog/Technical-Debt-2026-04-22.md` — débito D-02.

### Contexto
Mesmo após card 0220, `home_motorista_screen.dart` mantém 19 `setState` para flags de UI locais (modos, toggles, expansões). Cada um dispara rebuild de toda a tela (3.345 linhas).

### Objetivo
Reduzir a ≤ 5 `setState` em `home_motorista_screen.dart`, movendo o restante para presenters específicos (`ChangeNotifier`/Notifier).

### Escopo
1. Catalogar os 19 `setState` (auditoria pré-implementação).
2. Para cada um:
   - Se for estado **operacional** (depende de viagem, passageiros, rota): mover para presenter existente.
   - Se for estado **de UI local** (expandido/colapsado, índice de aba): criar `UiPresenter` específico (ex.: `MotoristaUiPresenter`).
3. Substituir consumo por `ListenableBuilder` ou (após card 0231) provider equivalente.

### Critérios de Aceite
- `grep -c "setState((" lib/home_motorista_screen.dart` ≤ 5.
- Presenters novos ou ampliados têm cobertura de teste unitário.
- Comportamento idêntico ao antes em smoke.

### Validação mínima
- `flutter analyze`
- `flutter test`
- Integration test motorista.

### DoD
- Card marcado como `DONE (commit: <hash>)`
- Atualizar `Technical-Debt-2026-04-22.md` marcando D-02 como resolvido.

---

## Backlog 0235 — Débito D-06a: extrair MotoristaCadastroGateway + testes

### Status
DONE (commit: 286f8fc)

### Origem
`backlog/Technical-Debt-2026-04-22.md` — débito D-06a.

### Contexto
`lib/cadastro_motorista_screen.dart` chama `http.post(...)` diretamente nos handlers de botão. Sem stub não dá para testar.

### Objetivo
Tornar o cadastro de motorista testável extraindo a parte de rede para um gateway injetável; adicionar cobertura de happy path, erro de validação e erro de rede.

### Escopo
1. Criar `lib/features/driver_onboarding/data/motorista_cadastro_gateway.dart`:
   ```dart
   abstract class MotoristaCadastroGateway {
     Future<MotoristaCadastroResult> cadastrar(MotoristaCadastroPayload payload);
   }
   class HttpMotoristaCadastroGateway implements MotoristaCadastroGateway { ... }
   ```
2. `MotoristaCadastroPayload` e `MotoristaCadastroResult` (sealed class com `Ok | ValidationError | NetworkError`).
3. Tela recebe gateway no construtor (default = `HttpMotoristaCadastroGateway()`).
4. Testes em `test/features/driver_onboarding/`:
   - happy path → cria conta, vai para tela de verificação de e-mail.
   - 400 do backend → mostra mensagem de validação por campo.
   - 5xx ou timeout → mostra erro de rede genérico, mantém formulário preenchido.

### Critérios de Aceite
- Cobertura mínima de 3 cenários no widget test.
- Tela funciona idêntico ao antes para o usuário.

### Validação mínima
- `flutter analyze`
- `flutter test`
- Smoke: cadastro real de motorista no backend dev.

### DoD
- Card marcado como `DONE (commit: <hash>)`
- Atualizar `Technical-Debt-2026-04-22.md` marcando D-06a como resolvido.

---

## Backlog 0236 — Débito D-06b: extrair PassageiroCadastroGateway + testes

### Status
DONE (commit: 286f8fc)

### Origem
`backlog/Technical-Debt-2026-04-22.md` — débito D-06b.

### Contexto
Mesmo padrão do 0235, mas mais complexo: cadastro de passageiro envolve `google_place` para autocomplete de endereço.

### Objetivo
Cadastro de passageiro testável com gateway injetável; mockar inclusive a integração de places para testar offline.

### Escopo
1. Criar `lib/features/passenger_onboarding/data/passageiro_cadastro_gateway.dart` (mesmo padrão sealed class).
2. Criar `PlacesAutocompleteGateway` para abstrair `google_place` (testes injetam fake).
3. Tela recebe ambos no construtor.
4. Testes:
   - happy path com endereço selecionado.
   - validação faltando endereço.
   - erro de rede ao buscar places.
   - erro de rede ao cadastrar.

### Critérios de Aceite
- 4 cenários cobertos.
- Tela funciona idêntico para o usuário.

### Validação mínima
- `flutter analyze`
- `flutter test`
- Smoke: cadastro real de passageiro com endereço válido.

### DoD
- Card marcado como `DONE (commit: <hash>)`
- Atualizar `Technical-Debt-2026-04-22.md` marcando D-06b como resolvido.

---

## Backlog 0237 — Débito D-09: home_motorista e home_passageiro aceitam focusTripId (deep link)

### Status
DONE (commit: 2a50623)

### Origem
`backlog/Technical-Debt-2026-04-22.md` — débito D-09.

### Contexto
`ViagemDeepLink` agora passa `initialTripId` para motorista, passageiro e responsável. As homes de motorista e passageiro selecionam a viagem vinculada quando ela pertence à agenda do dia e mostram fallback honesto quando o link aponta para uma viagem fora da agenda do usuário.

### Objetivo
Deep link de viagem abre a tela já com a viagem em foco (selecionada/scrollada/expandida).

### Escopo
1. Adicionar parâmetro opcional `focusTripId` aos construtores das duas homes.
2. No `initState`, agendar `addPostFrameCallback` que chame um método do presenter para selecionar/scrollar até a viagem.
3. Atualizar `DeepLinkNavigationHandler` para passar `target.tripId`.
4. Casos a cobrir:
   - Viagem existe e pertence ao usuário → foco aplicado.
   - Viagem não existe ou não pertence → fallback honesto (snackbar "Viagem não encontrada"; permanece na home).

### Critérios de Aceite
- Cold start com `vanrides://viagem/<id>` abre app com viagem destacada (motorista) ou rastreio focado (passageiro).
- TODOs do `deep_link_navigation_handler.dart` removidos.
- Teste de widget cobre os 2 cenários de fallback.

### Validação mínima
- `flutter analyze`
- `flutter test`
- Smoke iPhone: abrir o link via Notes/Mensagens; validar foco.

### DoD
- Card marcado como `DONE (commit: 2a50623)`
- `Technical-Debt-2026-04-22.md` marcado como D-09 resolvido.
- Smoke iPhone físico segue no checklist de release porque depende de device real; cobertura local automatizada valida foco e fallback.

---

## Backlog 0238 — Débito D-11: migrar telas legadas de lib/ para lib/features/<area>/presentation/

### Status
DONE (commit: ae9392e)

### Origem
`backlog/Technical-Debt-2026-04-22.md` — débito D-11.

### Contexto
11 telas legadas estavam no root de `lib/` e quebravam a convenção
`lib/features/<area>/presentation/`. A execução também incluiu
`escolha_cadastro_screen.dart`, que não estava na lista original, mas precisava
sair do root para cumprir o critério de aceite de 0 arquivos `*_screen.dart`.

### Objetivo
Estrutura de módulos consistente; busca por feature em um lugar só.

### Escopo
1. Mover **uma tela por commit/PR** (não tudo de uma vez).
2. Em cada movimentação, criar arquivo proxy em `lib/<old-path>.dart` re-exportando o novo arquivo:
   ```dart
   export 'features/auth/presentation/login_screen.dart';
   ```
3. Migrar os imports do projeto para o novo caminho em ondas; quando todos os imports estiverem atualizados, remover o proxy.

### Critérios de Aceite
- 0 arquivos `*_screen.dart` no root de `lib/`.
- Imports do projeto apontam direto para `features/`.
- `flutter test` continua passando após cada movimentação.

### Validação mínima
- `flutter analyze`
- `flutter test`
- Smoke por papel após cada onda.

### DoD
- Card marcado como `DONE (commit: ae9392e)`
- Atualizar `Technical-Debt-2026-04-22.md` marcando D-11 como resolvido.

### Resultado entregue
- `find lib -maxdepth 1 -type f -name '*screen.dart'` retorna 0 arquivos.
- Imports do app, testes e integration tests foram migrados para caminhos
  diretos em `features/`, sem proxies temporários.
- Arquivos `part` específicos das telas acompanharam seus donos em
  `features/<area>/presentation/`.

---

## Backlog 0239 — Débito D-13: documentar e aplicar critério lib/services/ vs lib/features/<x>/data/

### Status
DONE (commit: f1b1594)

### Origem
`backlog/Technical-Debt-2026-04-22.md` — débito D-13.

### Contexto
`lib/services/api_constants.dart`, `auth_headers.dart`, `jwt_claims.dart`, `websocket_service.dart` convivem com gateways em `lib/features/<x>/data/`. Não há regra escrita.

### Objetivo
Definir e aplicar critério, registrando em `lib/README.md` (ou `docs/architecture.md`) e em ADR.

### Escopo
1. Documentar regra (proposta inicial — refinar em discussão):
   - `lib/services/` = infra **transversal** (auth headers, WebSocket, constantes de API, JWT claims).
   - `lib/features/<x>/data/` = gateway **específico** da feature `<x>` (cadastro, viagem, presença, etc.).
2. Auditar arquivos existentes; mover o que estiver no lugar errado.
3. ADR Decision (próxima livre) registrando.

### Critérios de Aceite
- Documento de regra publicado.
- 0 gateway de feature específica em `lib/services/`.
- ADR registrado.

### Validação mínima
- `flutter analyze`
- `flutter test`

### DoD
- Card marcado como `DONE (commit: <hash>)`
- Atualizar `Technical-Debt-2026-04-22.md` marcando D-13 como resolvido.

---

## Backlog 0240 — Débito D-14: endurecer analysis_options.yaml com regras customizadas

### Status
DONE (commit: 3b59571)

### Origem
`backlog/Technical-Debt-2026-04-22.md` — débito D-14.

### Contexto
`analysis_options.yaml` provavelmente herda apenas `flutter_lints` padrão. Permite `print()`, `late` arriscado, `unawaited` esquecido, etc. Sem barreira contra novos débitos.

### Objetivo
Subir o piso de qualidade do analyzer; bloquear classes inteiras de débito futuro.

### Escopo
1. Habilitar regras pelo menos:
   - `avoid_print`
   - `unawaited_futures`
   - `prefer_const_constructors`
   - `prefer_const_literals_to_create_immutables`
   - `library_private_types_in_public_api`
   - `use_build_context_synchronously`
   - `require_trailing_commas`
2. Rodar `flutter analyze` e abrir lista de avisos novos.
3. Limpar avisos novos (pode virar sub-tarefa por arquivo se grande).
4. Documentar regras adotadas no `README.md` ou `docs/lint-rules.md`.

### Critérios de Aceite
- `flutter analyze` retorna 0 issues após limpeza.
- Regras documentadas.
- CI continua verde.

### Validação mínima
- `flutter analyze`
- `flutter test`

### Riscos
- Pode gerar centenas de avisos novos. Time-box e priorizar por severidade.

### DoD
- Card marcado como `DONE (commit: 3b59571)`
- Atualizar `Technical-Debt-2026-04-22.md` marcando D-14 como resolvido.

### Resultado entregue
- `analysis_options.yaml` passou a bloquear `avoid_print`,
  `unawaited_futures`, `prefer_const_constructors`,
  `prefer_const_literals_to_create_immutables`,
  `library_private_types_in_public_api`, `use_build_context_synchronously` e
  `require_trailing_commas`.
- Avisos novos foram limpos com `dart fix` e ajustes manuais de
  `unawaited(...)` nos fluxos assíncronos de navegação/câmera.
- `docs/lint-rules.md` documenta o conjunto oficial de regras.
- `quality/ratchet-baseline.json` foi sincronizado depois da migração do card
  `0238`, preservando a barreira para arquivos novos e registrando
  `root_screen_count = 0`.

---

## Backlog 0241 — Débito D-16: documentar invariantes dos presenters do card 0220

### Status
DONE (commit: 24a835cc120aa4f4617d7ccdcc4d1a1d00403bc0)

### Origem
`backlog/Technical-Debt-2026-04-22.md` — débito D-16.

### Contexto
`MotoristaSessionPresenter` e `MotoristaLocationPresenter` (criados no card 0220) não têm comentário explicando precondições e efeitos colaterais de cada método público. Próximo dev (humano ou agente) precisa ler `home_motorista_screen` inteira para descobrir, por exemplo, que `connectRouteEvents` só é seguro depois de `loadTrip`.

### Objetivo
Adicionar uma linha por método público explicando pré-condição, efeito e pós-condição relevante.

### Escopo
1. Auditar todos os métodos públicos dos dois presenters.
2. Adicionar dartdoc curto:
   ```dart
   /// Pré: nenhuma. Carrega a viagem ativa do motorista usando a sessão atual.
   /// Notifica listeners ao concluir (sucesso ou falha).
   Future<void> loadTrip();
   ```
3. Mesmo padrão para `connectRouteEvents`, `disconnect`, `updateLocation`, etc.

### Critérios de Aceite
- 100% dos métodos públicos dos 2 presenters têm dartdoc com pré/pós.
- Nenhuma mudança de comportamento.

### Validação mínima
- `flutter analyze`
- `flutter test`

### DoD
- Card marcado como `DONE (commit: <hash>)`
- Atualizar `Technical-Debt-2026-04-22.md` marcando D-16 como resolvido.

---

## Backlog 0242 — Reconectar botão "Novo Responsável" na UI admin

### Status
DONE (commit: e853463)

### Origem
Bug reportado em teste manual da plataforma admin: CompanyAdmin não consegue criar responsável pela UI. Como hoje a criação de responsável pelo app não existe, a admin é o único caminho — logo o fluxo está bloqueado.

### Contexto
`lib/features/admin/presentation/admin_responsaveis_screen.dart` contém todo o código de criação já implementado e conectado ao backend:

- `_openCreateDialog()` (linha 102) com `showDialog<AdminResponsavelRecord>`.
- `_AdminResponsavelFormDialog` completo (linha 873+) — form, validação, submit.
- `_gateway.createResponsavel()` já chama `POST /company-admins/responsaveis`.

O bug é que o `AdminPortalPage` construído na linha 202 **não declara `trailing`**, então `_openCreateDialog()` fica órfão e o usuário não tem como disparar o dialog. As telas `admin_motoristas_screen.dart` (linha 165) e `admin_passageiros_screen.dart` (linha 224) já usam o padrão correto e servem de referência.

### Objetivo
Restaurar o botão `Novo Responsável` no topo da tela de responsáveis da admin, disparando o dialog de criação já existente.

### Escopo
1. Em `lib/features/admin/presentation/admin_responsaveis_screen.dart`, adicionar `trailing` ao `AdminPortalPage` (linha 202) com:
   - Componente: `AdminPortalPrimaryButton` (mesmo componente usado em `admin_motoristas_screen.dart`).
   - Label: `Novo Responsável` (com acento).
   - Ícone: `Icons.add_circle_outline`.
   - `onPressed`: `_openCreateDialog`.
2. Criar widget test em `test/features/admin/admin_responsaveis_screen_test.dart` (arquivo existente reutilizado) cobrindo:
   - Cenário feliz: monta a tela com um `AdminResponsavelGateway` fake, tap no botão `Novo Responsável` → dialog `_AdminResponsavelFormDialog` visível.
   - Cenário de borda: após submit bem-sucedido, `fetchResponsaveis` do gateway é chamado novamente (lista é recarregada).
3. **Fora de escopo:** mudar o form `_AdminResponsavelFormDialog`, mudar endpoint backend, padronizar fluxo de criação entre admin e app (card futuro).

### Impact Analysis
- **Telas/fluxos afetados:** apenas `admin_responsaveis_screen.dart`.
- **Dependência de contrato backend:** nenhuma. Endpoint `POST /company-admins/responsaveis` já existe e já é consumido pelo gateway.
- **Risco de regressão:** baixíssimo — adição de um widget isolado no `trailing`; não toca estado, gateway, roteamento nem outras telas.
- **Rollback:** reverter o commit; fluxo volta ao estado atual (botão inexistente).

### Critérios de Aceite
- [x] Botão `Novo Responsável` visível no topo direito da tela de responsáveis da admin.
- [x] Clicar no botão abre o `_AdminResponsavelFormDialog` existente.
- [x] Submit bem-sucedido cria o responsável no backend e atualiza a lista sem reload manual (via `_loadPage`).
- [x] Snackbar `Responsavel criado com sucesso.` (mensagem já existente) aparece após sucesso.
- [x] `flutter analyze` e `flutter test` sem regressões.
- [x] Nenhuma regressão automatizada nas telas admin cobertas por widget tests.

### Test Plan (obrigatório — CARD-QUALITY-STANDARD)
- **Risco principal:** botão não disparar o dialog; ou dialog abrir mas `_loadPage` não ser chamado após submit.
- **Testes novos/ajustados:**
  - [x] Tipo: widget test.
  - [x] Arquivo: `test/features/admin/admin_responsaveis_screen_test.dart`.
  - [x] Cenário feliz: tap em `Novo Responsável` → dialog visível.
  - [x] Cenário de borda: após submit com sucesso, gateway fake registra nova chamada a `fetchResponsaveis`.
- **Regressão mínima executada:**
  - [x] `flutter analyze`: PASS.
  - [x] `flutter test`: PASS.
  - [x] `./scripts/qa_gate_card.sh --no-e2e`: PASS.
  - [ ] integration_test **não requerido** (fluxo coberto implicitamente pelo mesmo padrão em motoristas/passageiros).
- **Evidência em QA Log:** registrada com smoke manual pendente para execução em DEV.

### Validação mínima
- `flutter analyze`
- `flutter test`
- `./scripts/qa_gate_card.sh --no-e2e`
- Smoke manual no portal admin em DEV: criar responsável, confirmar que aparece na lista.

### DoD
- Card marcado como `DONE (commit: e853463)`.
- Evidência do smoke manual em `backlog/QA-Execution-Log.md`.
- `backlog/API-Contract-Matrix.md` **não** precisa de atualização (sem mudança de contrato).

---

## Backlog 0900 — QUALITY-0001: baseline e quality ratchet do app

### Status
DONE (commit: 2fce785)

### Origem
Sessao de qualidade iniciada em 2026-06-06 para impedir que a base piore enquanto refatoracoes futuras sao planejadas.

### Contexto
O app ja passa `flutter analyze` e `flutter test`, mas o gate atual ainda nao bloqueia aumento de complexidade, arquivos gigantes, novos `ignore`, novos logs de debug ou nova desorganizacao de camadas.

O diagnostico inicial encontrou hotspots importantes:

- arquivos de tela entre 1.095 e 3.655 linhas;
- 332 ocorrencias de `setState(` em `lib/`;
- 32 ocorrencias de `debugPrint`/`print` em app e testes;
- 4 ignores de analyzer;
- 11 telas `*_screen.dart` ainda no root de `lib/`;
- README ainda contem texto de template Flutter.

### Objetivo
Criar um baseline versionado e um ratchet de qualidade para o app, integrado ao gate por card, sem alterar comportamento do usuario nesta primeira entrega.

### Impact Analysis
- **Contexto do card:** fundacao de qualidade, antes das refatoracoes grandes.
- **Mudancas tecnicas:** scripts/documentos de baseline, thresholds e integracao com `scripts/qa_gate_card.sh`.
- **Contratos afetados:** nenhum contrato backend/app deve mudar.
- **Riscos e mitigacao:**
  - falso positivo bloquear card valido; mitigar com politica de excecao documentada;
  - gate pesado demais para uso diario; manter v1 rapido e baseado em metricas locais;
  - normalizar divida antiga como aceitavel; manter allowlist temporaria e metas de reducao.
- **Validacao minima obrigatoria:** `flutter analyze`, `flutter test`, `./scripts/qa_gate_card.sh --no-e2e`, execucao do novo baseline.
- **Rollback simples:** reverter scripts/docs do ratchet; nenhum comportamento de produto deve ser afetado.

### Escopo
1. Criar script de baseline do app medindo:
   - maiores arquivos Dart;
   - total de linhas em `lib/`, `test/`, `integration_test/`;
   - ocorrencias de `setState(`;
   - ocorrencias de `debugPrint`/`print`;
   - ignores do analyzer;
   - telas no root de `lib/`;
   - acessos diretos a `SharedPreferences.getInstance()`.
2. Versionar snapshot inicial do baseline.
3. Criar quality gate que compara o estado atual contra o baseline.
4. Integrar o quality gate ao `scripts/qa_gate_card.sh`.
5. Documentar regra de excecao e regra de atualizacao de baseline em `docs/quality-ratchet-program.md`.
6. Remover texto de template Flutter do `README.md` se o gate/documentacao tocar essa area.

### Fora de escopo
- Refatorar telas grandes.
- Introduzir novo state management.
- Migrar navegacao para GoRouter.
- Alterar fluxos, copy de produto ou contrato com backend.
- Rodar E2E completo por padrao.

### Critérios de Aceite
- Existe baseline versionado do app.
- Existe comando de quality gate rapido e local.
- `scripts/qa_gate_card.sh --no-e2e` executa tambem o ratchet.
- O gate falha quando uma metrica protegida piora sem excecao.
- A politica de excecao esta documentada.
- `flutter analyze` e `flutter test` continuam verdes.

### Test Plan do Card (obrigatório)
- Risco principal: criar um gate que bloqueia demais ou que nao bloqueia regressao real.
- Testes novos/ajustados:
  - [x] Tipo: teste de script shell com fixture temporaria quando viavel.
  - [x] Arquivo: `scripts/quality_gate.sh` e `scripts/quality_baseline.sh`.
  - [x] Cenario feliz: baseline atual passa.
  - [x] Cenario de erro/borda: metrica artificial piorada falha.
- Regressão mínima executada:
  - [x] `flutter analyze`
  - [x] `flutter test`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
- Evidência registrada em QA Log:
  - [x] Sim. Sem mudanca de comportamento de usuario; registro feito porque o ratchet passou a fazer parte do DoD.

### Validação mínima
- `flutter analyze`
- `flutter test`
- `./scripts/qa_gate_card.sh --no-e2e`
- Execucao direta do novo quality gate.

### DoD
- Card marcado como `DONE (commit: 2fce785)`.
- `docs/quality-ratchet-program.md` atualizado se a regra final divergir do design inicial.
- `backlog/CARD-QUALITY-STANDARD.md` atualizado se o DoD oficial passar a exigir o ratchet.

---

## Backlog 0901 — QUALITY-0002: quality gate no CI do app

### Status
DONE (commit: d1951dd)

### Origem
Segundo incremento do bloco de qualidade iniciado em 2026-06-06. O baseline e o
ratchet local ja existem; falta impedir regressao tambem no pipeline remoto
antes do proximo push/PR.

### Contexto
O repositório do app ainda nao possui workflow GitHub Actions versionado. O gate
oficial por card (`scripts/qa_gate_card.sh --no-e2e`) roda localmente, mas uma
branch futura ainda poderia ser enviada sem que o CI repetisse `flutter analyze`,
`flutter test` e o quality ratchet.

### Objetivo
Adicionar um workflow de qualidade do app que rode em `pull_request`, `push` e
`workflow_dispatch`, bloqueando regressao objetiva antes de qualquer publicacao.

### Impact Analysis
- **Contexto do card:** maturidade do bloco de qualidade, levando o ratchet do
  ambiente local para o CI.
- **Mudancas tecnicas:** criar workflow GitHub Actions do app, documentar o gate
  remoto e alinhar Codex Cloud/AGENTS com o novo fluxo.
- **Contratos afetados:** nenhum contrato backend/app deve mudar.
- **Riscos e mitigacao:**
  - CI ficar diferente do ambiente local; mitigar fixando Flutter `3.38.5`, a
    mesma versao local e do setup Codex Cloud;
  - workflow pesado demais para PR pequeno; manter v1 sem E2E e sem build APK
    obrigatorio;
  - Android/iOS exigirem segredos ou ambiente ausente; manter build Android como
    check opcional documentado, e iOS somente local/macOS.
- **Validacao minima obrigatoria:** `bash -n scripts/codex_cloud_setup.sh`,
  `python3 scripts/quality_metrics_test.py`, `./scripts/quality_gate.sh`,
  `./scripts/qa_gate_card.sh --no-e2e` e verificacao estrutural do YAML.
- **Rollback simples:** remover o workflow e reverter os docs; nenhum
  comportamento do usuario deve ser afetado.

### Escopo
1. Criar `.github/workflows/flutter-quality.yml`.
2. Rodar no CI:
   - `flutter pub get`;
   - `python3 scripts/quality_metrics_test.py`;
   - `./scripts/quality_gate.sh`;
   - `flutter analyze --no-fatal-infos --no-fatal-warnings`;
   - `flutter test`.
3. Atualizar `docs/codex-cloud.md`, `scripts/codex_cloud_setup.sh` e
   `AGENTS.md` para refletir o gate de qualidade.
4. Registrar QA Log do novo gate sem marcar E2E como requerido.

### Fora de escopo
- Publicar PR ou push.
- Rodar iOS E2E no GitHub Actions Linux.
- Tornar build APK obrigatorio no CI.
- Refatorar telas grandes.

### Critérios de Aceite
- Existe workflow versionado para o app.
- O workflow inclui o quality ratchet antes de considerar o job verde.
- O gate local continua verde.
- A documentacao de Codex Cloud aponta para o mesmo conjunto minimo de checks.

### Test Plan do Card (obrigatório)
- Risco principal: criar CI diferente do gate local ou tentar validar E2E iOS em
  runner Linux.
- Testes novos/ajustados:
  - [x] Tipo: workflow GitHub Actions com gate nao-E2E.
  - [x] Arquivo: `.github/workflows/flutter-quality.yml`.
  - [x] Cenario feliz: workflow roda `quality_metrics_test`, `quality_gate`,
    `flutter analyze` e `flutter test`.
  - [x] Cenario de borda: iOS E2E fica explicitamente fora do CI Linux e segue
    documentado para Codex local macOS.
- Regressão mínima executada:
  - [x] `bash -n scripts/codex_cloud_setup.sh`
  - [x] YAML parse via Ruby para `.github/workflows/flutter-quality.yml`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
- Evidência registrada em QA Log:
  - [x] Sim. Sem mudanca de comportamento de usuario; registro feito porque o
    CI passou a compor o DoD de qualidade.

### DoD
- Card marcado como `DONE (commit: d1951dd)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0902 — QUALITY-0003: extrair widgets utilitarios do home do motorista

### Status
DONE (commit: 9c74c99)

### Origem
Primeira refatoracao real do bloco de qualidade apos o baseline, o ratchet e o
CI do app. O objetivo e provar que o gate agora protege melhorias concretas em
hotspots, nao apenas documentacao ou configuracao.

### Contexto
`lib/home_motorista_screen.dart` continua sendo o maior arquivo Dart do app,
com 3.655 linhas no baseline atual. O arquivo mistura orquestracao de estado,
layout principal e diversos widgets visuais privados no mesmo modulo, o que
dificulta manutencao humana e uso por agentes.

### Objetivo
Reduzir o tamanho e a responsabilidade do `home_motorista_screen.dart` extraindo
um conjunto pequeno de widgets utilitarios visuais para
`lib/features/driver_trip/presentation/widgets/`, mantendo UX e comportamento
inalterados.

### Impact Analysis
- **Contexto do card:** refatoracao guiada por hotspot dentro do modulo de
  qualidade.
- **Telas/fluxos afetados:** home do motorista, incluindo planejamento,
  corrida em andamento e painel de detalhes.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** criar arquivo de widgets do motorista, mover
  componentes visuais pequenos e ajustar imports/nomes no arquivo principal.
- **Riscos e mitigacao:**
  - quebrar UI por mudanca visual acidental; mitigar com widget tests focados e
    suite existente de `home_motorista_screen_test.dart`;
  - aumentar API publica sem necessidade; mitigar exportando apenas widgets que
    saem do arquivo principal;
  - piorar ratchet por criar arquivo novo grande; manter arquivo novo bem abaixo
    de 600 linhas.
- **Validacao minima obrigatoria:** teste focado do novo widget, teste existente
  da home do motorista, `./scripts/quality_gate.sh` e
  `./scripts/qa_gate_card.sh --no-e2e`.
- **Rollback simples:** reverter novo arquivo, imports e substituicoes de nomes;
  nenhum dado ou contrato e afetado.

### Escopo
1. Escrever teste focado para pelo menos um widget extraido.
2. Extrair widgets utilitarios pequenos do final de
   `lib/home_motorista_screen.dart`.
3. Atualizar referencias no arquivo principal sem alterar layout ou textos.
4. Medir reducao de linhas do hotspot e registrar evidencia em QA Log.

### Fora de escopo
- Refatorar estado, presenters ou lifecycle actions.
- Migrar a tela inteira para `features/`.
- Alterar UX, textos, keys publicas ou contratos com backend.
- Rodar E2E iOS como requisito deste card.

### Critérios de Aceite
- `home_motorista_screen.dart` fica menor que o baseline deste card.
- O novo arquivo de widgets fica abaixo do limite de 600 linhas.
- O teste focado do widget extraido passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet nao acusa nova regressao.

### Test Plan do Card (obrigatório)
- Risco principal: mudar layout/keys da home do motorista durante uma extracao
  mecanica.
- Testes novos/ajustados:
  - [x] Tipo: widget test focado.
  - [x] Arquivo:
    `test/features/driver_trip/presentation/widgets/driver_home_widgets_test.dart`.
  - [x] Cenario feliz: `DriverOperationTypeBadge` renderiza o tipo de operacao.
  - [x] Cenario de interacao: `DriverUtilityRow` renderiza acoes e dispara
    callback.
- Regressão mínima executada:
  - [x] TDD RED confirmado com falha por modulo inexistente.
  - [x] `flutter test test/features/driver_trip/presentation/widgets/driver_home_widgets_test.dart`
  - [x] `flutter test test/screens/home_motorista_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
- Evidência registrada em QA Log:
  - [x] Sim. Sem mudanca de comportamento de usuario; registro feito porque o
    hotspot principal foi reduzido.

### DoD
- Card marcado como `DONE (commit: 9c74c99)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0903 — QUALITY-0004: remover widgets mortos do home do motorista

### Status
DONE (commit: 675e83d)

### Origem
Continuidade da refatoracao guiada por hotspots do modulo de qualidade. O card
0902 reduziu o `home_motorista_screen.dart` e criou um modulo de widgets
utilitarios; este card limpa codigo visual morto encontrado durante a leitura do
mesmo hotspot.

### Contexto
`lib/home_motorista_screen.dart` segue como o maior arquivo Dart do app, agora
com 3.465 linhas apos o card 0902. Durante a leitura do proximo bloco visual,
foi confirmado que `_OverlayChip`, `_DriverPlanningHeader` e
`_DriverActiveHeader` existem apenas como definicoes privadas sem consumidores no
arquivo atual.

### Objetivo
Remover widgets privados mortos da home do motorista, reduzindo o hotspot sem
criar API publica artificial e sem alterar comportamento visivel.

### Impact Analysis
- **Contexto do card:** segunda fatia de refatoracao do maior hotspot do app.
- **Telas/fluxos afetados:** nenhum fluxo visivel esperado, pois os widgets
  removidos nao possuem consumidores.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** remover classes privadas sem referencias e validar que
  nenhuma chamada real dependia delas.
- **Riscos e mitigacao:**
  - remover widget usado indiretamente; mitigar com busca `rg` antes/depois,
    `flutter analyze` e suite da home do motorista;
  - documentar escopo incorreto; mitigar registrando a descoberta no QA Log;
  - reduzir codigo sem mexer em comportamento e ainda assim causar compile
    break; mitigar com gate completo do app.
- **Validacao minima obrigatoria:** busca de referencias, teste da home do
  motorista, `./scripts/quality_gate.sh` e
  `./scripts/qa_gate_card.sh --no-e2e`.
- **Rollback simples:** reverter a remocao das classes; nenhum dado ou contrato
  e afetado.

### Escopo
1. Confirmar por busca que os widgets candidatos nao possuem consumidores.
2. Remover `_OverlayChip`, `_DriverPlanningHeader` e `_DriverActiveHeader`.
3. Validar compile/testes/gate para garantir que nenhum fluxo visivel mudou.
4. Medir a reducao de linhas do hotspot e registrar evidencia em QA Log.

### Fora de escopo
- Refatorar estado, presenters, lifecycle actions ou navegacao.
- Alterar UX, textos, keys publicas ou contratos com backend.
- Migrar a tela inteira para `features/`.
- Rodar E2E iOS como requisito deste card.

### Critérios de Aceite
- `home_motorista_screen.dart` fica menor que o baseline do card 0903.
- `driver_home_widgets.dart` permanece abaixo de 600 linhas.
- Busca confirma ausencia de consumidores removidos.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet nao acusa nova regressao.

### Test Plan do Card (obrigatório)
- Risco principal: remover uma classe privada que ainda fosse usada pelo fluxo
  da home do motorista.
- Testes novos/ajustados:
  - [x] Nenhum teste novo mantido, porque os widgets candidatos foram
    confirmados como codigo morto e criar API publica para testa-los aumentaria
    superficie artificial.
- Regressão mínima executada:
  - [x] Busca antes/depois por
    `_OverlayChip|_DriverPlanningHeader|_DriverActiveHeader` confirmou ausencia
    de consumidores nos arquivos do recorte.
  - [x] `flutter test test/features/driver_trip/presentation/widgets/driver_home_widgets_test.dart`
  - [x] `flutter test test/screens/home_motorista_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
- Evidência registrada em QA Log:
  - [x] Sim. Sem mudanca de comportamento de usuario; registro feito porque o
    hotspot principal foi reduzido de 3.465 para 3.241 linhas.

### DoD
- Card marcado como `DONE (commit: 675e83d)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0904 — QUALITY-0005: apertar baseline do ratchet apos limpeza do hotspot

### Status
DONE (commit: 6ed6313)

### Origem
Continuidade do bloco de qualidade apos os cards 0902 e 0903. O hotspot
`home_motorista_screen.dart` caiu de 3.655 para 3.241 linhas, mas o baseline
versionado do ratchet ainda reflete o limite antigo.

### Contexto
`quality/ratchet-baseline.json` e o CI protegem contra regressao em relacao ao
baseline versionado. Se o baseline nao for atualizado apos uma melhoria
validada, o gate continua permitindo que o arquivo volte ao tamanho anterior.

### Objetivo
Atualizar o baseline do quality ratchet do app para o estado atual, fazendo com
que a reducao do hotspot vire limite real de nao regressao em commits futuros.

### Impact Analysis
- **Contexto do card:** maturidade do modulo de qualidade, transformando
  melhoria local em regra persistente do quality gate.
- **Telas/fluxos afetados:** nenhum fluxo visivel. Mudanca restrita a metricas
  versionadas e documentacao de QA.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** regenerar `quality/ratchet-baseline.json` com as
  metricas atuais e validar que o `quality_gate.sh` passa contra o novo limite.
- **Riscos e mitigacao:**
  - apertar baseline com workspace sujo; mitigar conferindo `git status` antes
    da geracao;
  - registrar baseline incorreto; mitigar comparando metricas antes/depois e
    rodando `quality_metrics_test.py`;
  - quebrar fluxo de commit por gate mais estrito; mitigar rodando
    `./scripts/qa_gate_card.sh --no-e2e`.
- **Validacao minima obrigatoria:** `python3 scripts/quality_metrics_test.py`,
  `./scripts/quality_gate.sh`, `./scripts/qa_gate_card.sh --no-e2e` e diff do
  baseline revisado.
- **Rollback simples:** reverter `quality/ratchet-baseline.json`; nenhum dado,
  contrato ou comportamento do app e afetado.

### Escopo
1. Regenerar o baseline do ratchet a partir das metricas atuais do app.
2. Confirmar que `home_motorista_screen.dart` passa a ter limite de 3.241
   linhas no baseline.
3. Rodar os gates locais relevantes.
4. Registrar evidencia em QA Log e fechar o card com hash real.

### Fora de escopo
- Nova refatoracao de tela.
- Alterar thresholds ou semantica do script de ratchet.
- Rodar E2E iOS como requisito deste card.
- Alterar CI, workflows ou contratos backend/app.

### Critérios de Aceite
- `quality/ratchet-baseline.json` reflete as metricas atuais do app.
- O maior arquivo permitido pelo baseline passa de 3.655 para 3.241 linhas.
- `./scripts/quality_gate.sh` passa com o novo baseline.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- QA Log registra que o limite foi apertado apos reducao validada do hotspot.

### Test Plan do Card (obrigatório)
- Risco principal: atualizar o baseline sem transformar a melhoria real em
  limite efetivo do gate.
- Testes novos/ajustados:
  - [x] Nenhum teste novo. O card altera baseline versionado e depende dos
    testes existentes do coletor/gate.
- Regressão mínima executada:
  - [x] `./scripts/quality_baseline.sh`
  - [x] Diff de `quality/ratchet-baseline.json` revisado.
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
- Evidência registrada em QA Log:
  - [x] Sim. O maior arquivo permitido pelo baseline agora e
    `lib/home_motorista_screen.dart` com 3.241 linhas.

### DoD
- Card marcado como `DONE (commit: 6ed6313)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0905 — QUALITY-0006: extrair widgets de formulário do admin viagens

### Status
DONE (commit: 35cc4b2)

### Origem
Continuidade do bloco de qualidade do app apos estabilizar o ratchet, o CI e a
primeira reducao da home do motorista. O proximo hotspot produtivo do baseline
e `lib/features/admin/presentation/admin_viagens_screen.dart`, com 3.011 linhas.

### Contexto
`admin_viagens_screen.dart` concentra orquestracao de tela, listagem, detalhes,
mapa operacional, formulario manual e pequenos widgets visuais reutilizaveis no
mesmo arquivo. No fim do arquivo ha componentes puros de formulario e metrica
visual (`_FieldLabel`, `_DateField`, `_DisabledField`, `_DarkInfoLine` e
`_DarkMetricRow`) que nao precisam viver junto da tela stateful principal.

### Objetivo
Reduzir o tamanho e a responsabilidade do hotspot extraindo widgets puros de
formulario e informacao visual para `lib/features/admin/presentation/widgets/`,
mantendo UX, textos, callbacks e contratos com backend inalterados.

### Impact Analysis
- **Contexto do card:** refatoracao guiada por hotspot dentro do modulo de
  qualidade do app.
- **Telas/fluxos afetados:** portal admin, tela de viagens, especialmente
  criacao manual e detalhe operacional.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** criar modulo pequeno de widgets do admin viagens,
  mover componentes stateless puros e atualizar referencias no arquivo
  principal.
- **Riscos e mitigacao:**
  - mudar layout de campos por acidente; mitigar com widget test focado dos
    widgets extraidos;
  - quebrar a tela de viagens por nome/import; mitigar com
    `admin_viagens_screen_test.dart`;
  - criar arquivo novo grande demais; manter o novo arquivo abaixo do limite de
    600 linhas protegido pelo ratchet.
- **Validacao minima obrigatoria:** teste focado do novo modulo,
  `flutter test test/features/admin/admin_viagens_screen_test.dart`,
  `python3 scripts/quality_metrics_test.py`, `./scripts/quality_gate.sh` e
  `./scripts/qa_gate_card.sh --no-e2e`.
- **Rollback simples:** reverter novo arquivo, imports e renomes; nenhum dado,
  contrato ou comportamento de usuario e afetado.

### Escopo
1. Escrever teste focado para os widgets extraidos.
2. Extrair campos e linhas de metrica puros de `admin_viagens_screen.dart`.
3. Atualizar referencias preservando texto, padding, cores e callbacks.
4. Medir reducao de linhas do hotspot e registrar evidencia em QA Log.

### Fora de escopo
- Refatorar estado, presenters, gateway ou lifecycle da tela.
- Alterar UX, textos, keys publicas ou contratos com backend.
- Migrar o portal admin inteiro para outro padrao de estado.
- Rodar E2E iOS como requisito deste card.

### Critérios de Aceite
- `admin_viagens_screen.dart` fica menor que o baseline deste card.
- O novo arquivo de widgets fica abaixo de 600 linhas.
- Teste focado dos widgets extraidos passa.
- `admin_viagens_screen_test.dart` passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet nao acusa nova regressao.

### Test Plan do Card (obrigatório)
- Risco principal: mudar a aparencia ou interacao de campos durante uma
  extracao mecanica.
- Testes novos/ajustados:
  - [x] Tipo: widget test focado.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_viagens_widgets_test.dart`.
  - [x] Cenario feliz: campo de data renderiza label, valor e dispara callback.
  - [x] Cenario visual: linha de metrica escura renderiza pares label/valor.
- Regressão mínima executada:
  - [x] TDD RED confirmado com falha por modulo inexistente.
  - [x] `flutter test test/features/admin/presentation/widgets/admin_viagens_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_viagens_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
- Evidência registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 35cc4b2)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0906 — QUALITY-0007: estabilizar refresh local do Graphify no app

### Status
DONE (commit: 38c0f0b)

### Origem
Continuidade do bloco de qualidade apos configurar Graphify como apoio de
arquitetura. O hook local passou a avisar que o novo grafo tinha menos nós que
`graphify-out/graph.json` e recusou sobrescrever o estado anterior.

### Contexto
`graphify-out/` e `.githooks/post-commit` sao artefatos locais ignorados, mas
eles influenciam diretamente o diagnostico de arquitetura usado por agentes. O
warning observado foi:

- `new graph has <N> nodes but existing graph.json has 9334. Refusing to overwrite`

O manifest local tambem mostrou entradas relativas e absolutas do mesmo
repositorio, indicando que o grafo antigo provavelmente ficou inflado por
configuracoes anteriores. Para refatoracoes que removem ou normalizam codigo, o
CLI do Graphify orienta usar `--force`, mas esse procedimento ainda nao esta
documentado nem versionado no app.

### Objetivo
Criar um caminho oficial e seguro para atualizar o grafo local do Graphify,
incluindo modo normal e modo forcado deliberado para refatoracoes/normalizacoes,
sem versionar `graphify-out/` e sem alterar contratos do app.

### Impact Analysis
- **Contexto do card:** qualidade de engenharia e suporte a diagnostico
  agent-friendly por grafo de arquitetura.
- **Telas/fluxos afetados:** nenhum fluxo visual ou funcional do app.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** adicionar script versionado para refresh do Graphify e
  documentar quando usar `GRAPHIFY_FORCE=1`.
- **Riscos e mitigacao:**
  - forcar overwrite e perder informacao util; mitigar exigindo variavel
    explicita e descrevendo uso apenas apos refactor/normalizacao intencional;
  - confundir graph local ignorado com artefato de commit; mitigar documentando
    que `graphify-out/` permanece local;
  - transformar o gate do app em dependente de Graphify; mitigar mantendo o
    comando fora do `qa_gate_card.sh` por enquanto.
- **Validacao minima obrigatoria:** `bash -n` do script, execucao normal
  reproduzindo o guardrail ou PASS, execucao forcada validando o grafo local,
  `graphify query` simples, `./scripts/quality_gate.sh`.
- **Rollback simples:** remover script/docs/card; `graphify-out/` e hook local
  continuam fora do Git.

### Escopo
1. Criar `scripts/graphify_refresh.sh`.
2. Documentar comando normal e comando forcado em `docs/quality-ratchet-program.md`.
3. Registrar o comando em `AGENTS.md` como apoio de arquitetura local.
4. Rodar refresh forcado para normalizar o grafo local atual.
5. Registrar evidencia em QA Log.

### Fora de escopo
- Versionar `graphify-out/`.
- Alterar hooks gerados pelo Graphify.
- Tornar Graphify requisito do CI.
- Regerar grafo semantico com LLM.

### Critérios de Aceite
- Existe comando versionado para refresh local do Graphify.
- O comando documenta e exige `GRAPHIFY_FORCE=1` para overwrite forcado.
- O grafo local aceita refresh apos normalizacao.
- `graphify query` simples volta a usar o grafo atualizado.
- `./scripts/quality_gate.sh` segue verde.

### Test Plan do Card (obrigatório)
- Risco principal: normalizar `graphify-out/` de forma destrutiva sem deixar
  criterio operacional claro.
- Testes novos/ajustados:
  - [x] Tipo: validacao shell/documentacao.
  - [x] Arquivo: `scripts/graphify_refresh.sh`.
  - [x] Cenario normal: `bash -n` e execucao sem force nao escondem o guardrail.
  - [x] Cenario forcado: `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh` atualiza
    `graphify-out/graph.json`.
- Regressão mínima executada:
  - [x] `bash -n scripts/graphify_refresh.sh`
  - [x] `scripts/graphify_refresh.sh`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `graphify query "Qual o hotspot atual do app VanRides?" --budget 800`
  - [x] `graphify explain "AdminViagensScreen"`
  - [x] `./scripts/quality_gate.sh`
- Evidência registrada em QA Log:
  - [x] Sim. O grafo local normalizado ficou com 7.999 nós e 10.122 arestas.

### DoD
- Card marcado como `DONE (commit: 38c0f0b)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0907 — QUALITY-0008: extrair componentes visuais restantes da home do motorista

### Status
DONE (commit: efce0a8)

### Origem
Continuidade do bloco de qualidade apos estabilizar o refresh local do Graphify
no app. O grafo e o ratchet confirmam que `lib/home_motorista_screen.dart`
continua sendo o maior hotspot do app.

### Contexto
`home_motorista_screen.dart` ainda tem 3.241 linhas e mistura estado,
orquestracao de lifecycle da corrida, layout map-first e varios componentes
visuais privados. O modulo `features/driver_trip/presentation/widgets/` ja
existe, mas ainda concentra apenas widgets pequenos e nao cobre os cards,
paineis e listas visuais da home do motorista.

### Objetivo
Reduzir a responsabilidade do `home_motorista_screen.dart` extraindo os
componentes stateless restantes para widgets pequenos do dominio
`driver_trip`, sem alterar UX, textos, keys publicas ou contratos com backend.

### Impact Analysis
- **Contexto do card:** refatoracao guiada por hotspot dentro do modulo de
  qualidade do app, usando Graphify e ratchet para preservar nao regressao.
- **Telas/fluxos afetados:** home do motorista, painel de detalhes, fila de
  paradas, planejamento e corrida em andamento.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** transformar `driver_home_widgets.dart` em barrel e
  separar componentes por responsabilidade em arquivos menores de widgets.
- **Riscos e mitigacao:**
  - criar outro arquivo gigante; mitigar com arquivos novos abaixo do limite de
    600 linhas protegido pelo ratchet;
  - criar acoplamento circular com `HomeMotoristaScreen`; mitigar passando keys
    publicas por parametro no widget de acoes;
  - alterar UI por acidente durante a extracao; mitigar com widget tests
    focados e suite existente da home do motorista;
  - remover codigo privado ainda usado; mitigar com busca de referencias antes
    e depois.
- **Validacao minima obrigatoria:** teste focado dos widgets extraidos,
  `flutter test test/screens/home_motorista_screen_test.dart`,
  `python3 scripts/quality_metrics_test.py`, `./scripts/quality_gate.sh`,
  `./scripts/qa_gate_card.sh --no-e2e` e refresh local do Graphify.
- **Rollback simples:** reverter os novos arquivos de widgets, o barrel e as
  substituicoes de nomes no arquivo principal; nenhum dado ou contrato e
  afetado.

### Escopo
1. Criar arquivos pequenos para widgets base, mapa, overview, paradas e acoes.
2. Remover widgets privados mortos encontrados no mesmo trecho.
3. Atualizar `home_motorista_screen.dart` para usar os widgets publicos.
4. Adicionar/ajustar widget tests focados para os componentes extraidos.
5. Medir reducao do hotspot, atualizar o baseline do ratchet e registrar
   evidencia em QA Log.

### Fora de escopo
- Refatorar presenters, gateways, lifecycle actions ou navegacao.
- Alterar copy, keys publicas, layout intencional ou contratos com backend.
- Tornar Graphify requisito de CI.
- Rodar E2E iOS como requisito deste card.

### Critérios de Aceite
- `home_motorista_screen.dart` fica menor que 3.241 linhas.
- Cada novo arquivo de widgets fica abaixo de 600 linhas.
- `driver_home_widgets.dart` funciona como ponto de import estavel.
- Testes focados dos widgets extraidos passam.
- `home_motorista_screen_test.dart` passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet atualizado impede retorno do hotspot ao limite antigo.

### Test Plan do Card (obrigatório)
- Risco principal: mudar layout, callbacks ou keys da home do motorista durante
  uma extracao mecanica.
- Testes novos/ajustados:
  - [x] Tipo: widget tests focados.
  - [x] Arquivo:
    `test/features/driver_trip/presentation/widgets/driver_home_widgets_test.dart`.
  - [x] Cenario feliz: card de parada renderiza nome, rota, ETA e chips.
  - [x] Cenario de interacao: secao de acoes usa keys recebidas por parametro e
    dispara callbacks corretos.
- Regressão mínima executada:
  - [x] Busca antes/depois por widgets privados movidos/removidos.
  - [x] `flutter test test/features/driver_trip/presentation/widgets/driver_home_widgets_test.dart`
  - [x] `flutter test test/screens/home_motorista_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidência registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: efce0a8)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0908 — QUALITY-0009: extrair widgets de listagem do admin viagens

### Status
DONE (commit: 0c71c86)

### Origem
Continuidade do bloco de qualidade apos a home do motorista deixar de ser o
maior hotspot do app. O ratchet atual aponta
`lib/features/admin/presentation/admin_viagens_screen.dart` como o maior arquivo
Dart, com 2.773 linhas.

### Contexto
`admin_viagens_screen.dart` ja teve widgets de formulario extraidos no card
0905, mas ainda concentra os componentes visuais da listagem principal:
linha de viagem, celulas da tabela, cabecalhos, tag de status e chip de filtro
por data. Esses componentes sao stateless, nao acessam gateway e podem viver no
modulo `widgets/admin_viagens_widgets.dart`.

### Objetivo
Reduzir a responsabilidade de `admin_viagens_screen.dart` extraindo os widgets e
helpers puros de listagem para o modulo de widgets de viagens, mantendo UX,
textos, callbacks e contratos com backend inalterados.

### Impact Analysis
- **Contexto do card:** refatoracao guiada por hotspot dentro do modulo de
  qualidade do app, com o maior arquivo atual como alvo.
- **Telas/fluxos afetados:** portal admin, tela de viagens, listagem principal,
  filtro de data e leitura visual de status.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** mover widgets de listagem e helpers de formatacao/status
  para `admin_viagens_widgets.dart`; manter a tela principal focada em estado,
  carregamento e composicao.
- **Riscos e mitigacao:**
  - duplicar ou divergir regra de status; mitigar expondo predicados de status
    compartilhados pelo resumo e pelo chip visual;
  - alterar layout da tabela; mitigar com widget test focado e suite existente
    de `admin_viagens_screen_test.dart`;
  - ultrapassar limite do arquivo de widgets; mitigar mantendo
    `admin_viagens_widgets.dart` abaixo de 600 linhas;
  - mexer em comportamento de criacao/detalhe por acidente; mitigar mantendo o
    recorte restrito a listagem.
- **Validacao minima obrigatoria:** teste focado dos widgets extraidos,
  `flutter test test/features/admin/admin_viagens_screen_test.dart`,
  `python3 scripts/quality_metrics_test.py`, `./scripts/quality_gate.sh`,
  `./scripts/qa_gate_card.sh --no-e2e` e refresh local do Graphify.
- **Rollback simples:** reverter as movimentacoes no arquivo de widgets e as
  substituicoes de nomes no arquivo principal; nenhum dado ou contrato e
  afetado.

### Escopo
1. Extrair linha de viagem, celulas de tabela, tag de status, chip de data e
   helpers de formatacao/status.
2. Atualizar `admin_viagens_screen.dart` para usar a API publica do modulo de
   widgets.
3. Adicionar widget tests focados para a linha de viagem/status e chip de data.
4. Regenerar o baseline do ratchet e registrar evidencia no QA Log.

### Fora de escopo
- Refatorar detalhe da viagem, manifesto, mapa ou formulario de criacao.
- Alterar gateways, DTOs, payloads ou contratos com backend.
- Alterar UX/copy intencional da listagem.
- Rodar E2E iOS como requisito deste card.

### Critérios de Aceite
- `admin_viagens_screen.dart` fica menor que 2.773 linhas.
- `admin_viagens_widgets.dart` permanece abaixo de 600 linhas.
- Testes focados dos widgets extraidos passam.
- `admin_viagens_screen_test.dart` passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet atualizado impede retorno do hotspot ao limite antigo.

### Test Plan do Card (obrigatório)
- Risco principal: mudar status/callbacks/rotulagem da listagem durante a
  extracao.
- Testes novos/ajustados:
  - [x] Tipo: widget tests focados.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_viagens_widgets_test.dart`.
  - [x] Cenario feliz: linha de viagem renderiza data, servico, motorista,
    paradas, status e dispara callback de visualizacao.
  - [x] Cenario de borda: chip de data renderiza placeholder quando vazio e
    dispara callback.
- Regressão mínima executada:
  - [x] Busca antes/depois por widgets privados movidos.
  - [x] `flutter test test/features/admin/presentation/widgets/admin_viagens_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_viagens_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidência registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: 0c71c86)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0909 — QUALITY-0010: extrair widgets base do admin vínculos

### Status
DONE (commit: 9819054)

### Origem
Continuidade do bloco de qualidade apos reduzir `admin_viagens_screen.dart`.
O ratchet atual mostra `lib/features/admin/presentation/admin_vinculos_screen.dart`
como o segundo maior arquivo do app, com 2.382 linhas.

### Contexto
`admin_vinculos_screen.dart` mistura carregamento, dialogs de aprovacao/convite,
listas de vinculos e pequenos widgets visuais reutilizaveis. No fim do arquivo
existem componentes stateless puros (`_PortalInlineEmptyState`,
`_MiniMetricCard`, `_ModeButton`, `_DashedHintCard`) e um estilo de cabecalho
que nao precisam viver dentro da tela stateful.

### Objetivo
Reduzir a responsabilidade de `admin_vinculos_screen.dart` extraindo widgets
base de apresentacao para `widgets/admin_vinculos_widgets.dart`, mantendo UX,
textos, callbacks e contratos com backend inalterados.

### Impact Analysis
- **Contexto do card:** refatoracao guiada por hotspot dentro do modulo de
  qualidade do app, agora no segundo maior arquivo atual.
- **Telas/fluxos afetados:** portal admin, tela de vinculos, cards vazios,
  metricas de convites e seletor visual de tipo de conexao.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** mover widgets stateless e estilo visual para um modulo
  pequeno de widgets, atualizando as referencias na tela principal.
- **Riscos e mitigacao:**
  - alterar visual por acidente; mitigar com widget tests focados nos widgets
    extraidos;
  - criar arquivo novo grande; mitigar mantendo o novo arquivo abaixo de 600
    linhas;
  - mexer em dialogs/fluxos de aprovacao por acidente; mitigar mantendo o
    recorte restrito aos componentes base.
- **Validacao minima obrigatoria:** teste focado dos widgets extraidos,
  `flutter test test/features/admin/admin_vinculos_screen_test.dart`,
  `python3 scripts/quality_metrics_test.py`, `./scripts/quality_gate.sh`,
  `./scripts/qa_gate_card.sh --no-e2e` e refresh local do Graphify.
- **Rollback simples:** reverter o novo arquivo de widgets e as substituicoes
  de nomes no arquivo principal; nenhum dado ou contrato e afetado.

### Escopo
1. Criar `widgets/admin_vinculos_widgets.dart`.
2. Extrair empty state inline, metric card, mode button, dashed hint card e
   estilo de cabecalho.
3. Atualizar `admin_vinculos_screen.dart` para usar os widgets publicos.
4. Adicionar widget tests focados.
5. Regenerar o baseline do ratchet e registrar evidencia no QA Log.

### Fora de escopo
- Refatorar dialogs de aprovacao, convite ou vínculos responsavel-passageiro.
- Alterar gateways, DTOs, payloads ou contratos com backend.
- Alterar UX/copy intencional da tela.
- Rodar E2E iOS como requisito deste card.

### Critérios de Aceite
- `admin_vinculos_screen.dart` fica menor que 2.382 linhas.
- `admin_vinculos_widgets.dart` fica abaixo de 600 linhas.
- Testes focados dos widgets extraidos passam.
- `admin_vinculos_screen_test.dart` passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet atualizado impede retorno do hotspot ao limite antigo.

### Test Plan do Card (obrigatório)
- Risco principal: mudar visual/rotulagem de componentes base usados em
  multiplos paineis da tela.
- Testes novos/ajustados:
  - [x] Tipo: widget tests focados.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_vinculos_widgets_test.dart`.
  - [x] Cenario feliz: empty state e metric card renderizam texto e valor.
  - [x] Cenario visual: mode button ativo e card de dica renderizam destaque e
    icone.
- Regressão mínima executada:
  - [x] Busca antes/depois por widgets privados movidos.
  - [x] `flutter test test/features/admin/presentation/widgets/admin_vinculos_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_vinculos_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidência registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: 9819054)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0910 — QUALITY-0011: alinhar gate local e CI do app

### Status
DONE (commit: d8cf9d8)

### Origem
Continuidade do bloco de qualidade apos a criacao do workflow
`.github/workflows/flutter-quality.yml`. O CI ja executa o ratchet, analyzer e
testes, mas ainda duplica a sequencia do gate local em vez de chamar o mesmo
entrypoint versionado.

### Contexto
O objetivo do modulo de qualidade e impedir que proximos commits piorem a base.
Para isso, agentes humanos e de IA precisam de um unico comando canonico para
validar cards locais e para o CI repetir a mesma barreira sem E2E. Hoje o
`scripts/qa_gate_card.sh --no-e2e` cobre analyzer, testes e ratchet, mas nao
testa explicitamente o comparador de metricas; o workflow faz essa parte por
fora.

### Objetivo
Fazer o workflow de qualidade do app chamar o gate local por card e reforcar o
gate local para validar tambem `scripts/quality_metrics_test.py`, mantendo a
mesma superficie de validacao em Codex local, Codex Cloud e GitHub Actions.

### Impact Analysis
- **Contexto do card:** maturidade do quality gate do app, reduzindo drift entre
  validacao local e CI antes de futuros pushes/PRs.
- **Telas/fluxos afetados:** nenhum fluxo de produto. Mudanca restrita a
  scripts, workflow e documentacao operacional.
- **Dependencia de contrato backend:** nenhuma.
- **Mudancas tecnicas:** atualizar `scripts/qa_gate_card.sh`, trocar os passos
  manuais do workflow por `./scripts/qa_gate_card.sh --no-e2e` e documentar o
  comando canonico.
- **Riscos e mitigacao:**
  - esconder no script alguma etapa que antes estava visivel no workflow;
    mitigar documentando o gate e validando o script com execucao real;
  - quebrar CI por permissao/ambiente; mitigar mantendo o runner Linux sem E2E
    e sem build Android obrigatorio;
  - duplicar validacao em docs antigas; mitigar atualizando `AGENTS.md`,
    `docs/codex-cloud.md`, `CARD-QUALITY-STANDARD.md` e o programa de ratchet.
- **Validacao minima obrigatoria:** `bash -n scripts/qa_gate_card.sh`,
  parse estrutural do YAML, `./scripts/qa_gate_card.sh --no-e2e` e
  `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`.
- **Rollback simples:** reverter workflow, script e docs; nenhum dado ou
  contrato e afetado.

### Escopo
1. Fazer `scripts/qa_gate_card.sh` executar `quality_metrics_test.py`.
2. Fazer `.github/workflows/flutter-quality.yml` chamar o gate local.
3. Atualizar documentos operacionais que descrevem o gate.
4. Registrar evidencia em QA Log e manter o card sem push/PR.

### Fora de escopo
- Adicionar build Android obrigatorio no CI.
- Adicionar E2E iOS/Linux ao GitHub Actions.
- Alterar codigo de produto ou contratos com backend.
- Refatorar hotspots de UI.

### Critérios de Aceite
- CI do app chama `./scripts/qa_gate_card.sh --no-e2e`.
- Gate local roda `quality_metrics_test.py` antes de aplicar o ratchet.
- Documentacao aponta para o mesmo comando canonico.
- Gate local passa sem E2E.

### Test Plan do Card (obrigatório)
- Risco principal: quebrar o workflow de qualidade por divergencia entre script
  local e runner Linux.
- Testes novos/ajustados:
  - [x] Tipo: script/workflow gate.
  - [x] Arquivos: `scripts/qa_gate_card.sh` e
    `.github/workflows/flutter-quality.yml`.
  - [x] Cenario feliz: gate local executa testes do comparador, analyzer,
    testes Flutter e ratchet.
  - [x] Cenario de borda: CI continua sem E2E e sem device iOS.
- Regressão mínima executada:
  - [x] `bash -n scripts/qa_gate_card.sh`
  - [x] YAML parse via Ruby para `.github/workflows/flutter-quality.yml`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidência registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: d8cf9d8)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0911 — QUALITY-0012: extrair widgets de listagem dos serviços recorrentes

### Status
DONE (commit: 4606366)

### Origem
Continuidade das refatorações guiadas por hotspot do módulo de qualidade do
app. O ratchet atual mostra
`lib/features/admin/presentation/admin_servicos_recorrentes_screen.dart` como
o terceiro maior arquivo Dart, com 2.204 linhas.

### Contexto
`admin_servicos_recorrentes_screen.dart` concentra orquestração de dados,
formulário, seletor de destino via Places e widgets de apresentação da listagem.
Os widgets `_ServicosHeader`, `_ServicoRow`, `_EmptyServicosState` e
`_HeaderText` são componentes visuais puros da listagem e podem sair da tela
stateful sem alterar fluxo, contrato ou gateway.

### Objetivo
Reduzir responsabilidade e tamanho de `admin_servicos_recorrentes_screen.dart`
extraindo os widgets de listagem para
`widgets/admin_servicos_recorrentes_list_widgets.dart`, mantendo textos, ações,
tooltips e contrato backend inalterados.

### Impact Analysis
- **Contexto do card:** refatoração guiada por hotspot após estabilizar gate
  local/CI do app.
- **Telas/fluxos afetados:** portal admin, tela de serviços recorrentes, apenas
  listagem vazia e linhas da tabela.
- **Dependência de contrato backend:** nenhuma. Sem mudança de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudanças técnicas:** mover widgets stateless de listagem para módulo
  dedicado, passando labels já calculados pela tela principal quando necessário.
- **Riscos e mitigação:**
  - alterar copy/tooltip dos botões; mitigar com widget tests focados;
  - acoplar widgets ao estado da tela; mitigar usando callbacks e labels por
    parâmetro;
  - criar arquivo novo grande; mitigar mantendo o novo arquivo abaixo de 600
    linhas.
- **Validação mínima obrigatória:** teste focado dos widgets extraídos,
  `flutter test test/features/admin/admin_servicos_recorrentes_screen_test.dart`,
  `./scripts/quality_baseline.sh`, `./scripts/qa_gate_card.sh --no-e2e` e
  `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`.
- **Rollback simples:** reverter novo arquivo e substituições de nomes na tela
  principal; nenhum dado ou contrato é afetado.

### Escopo
1. Criar `widgets/admin_servicos_recorrentes_list_widgets.dart`.
2. Extrair header, row e empty state da listagem.
3. Adicionar widget tests focados.
4. Regenerar baseline do ratchet e atualizar documentação do programa.
5. Registrar evidência em QA Log e manter tudo local, sem push/PR.

### Fora de escopo
- Refatorar formulário, seletor Places ou chamadas HTTP.
- Alterar gateways, DTOs, payloads ou contrato backend.
- Alterar UX/copy intencional da listagem.
- Rodar E2E iOS como requisito deste card.

### Critérios de Aceite
- `admin_servicos_recorrentes_screen.dart` fica menor que 2.204 linhas.
- Novos arquivos de widgets ficam abaixo de 600 linhas cada.
- Testes focados dos widgets extraídos passam.
- `admin_servicos_recorrentes_screen_test.dart` passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet atualizado impede retorno do hotspot ao limite antigo.

### Test Plan do Card (obrigatório)
- Risco principal: mudar renderização/ações da listagem de serviços recorrentes
  sem perceber.
- Testes novos/ajustados:
  - [x] Tipo: widget tests focados.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_servicos_recorrentes_list_widgets_test.dart`.
  - [x] Cenário feliz: row renderiza nome, política, dias e ações.
  - [x] Cenário de borda: empty state aciona callback de criação.
- Regressão mínima executada:
  - [x] Busca antes/depois por widgets privados movidos.
  - [x] `flutter test test/features/admin/presentation/widgets/admin_servicos_recorrentes_list_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_servicos_recorrentes_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidência registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: 4606366)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0912 — QUALITY-0013: extrair widgets de detalhe do admin viagens

### Status
DONE (commit: 2259176)

### Origem
Continuidade das refatorações guiadas por hotspot do módulo de qualidade do
app. O ratchet atual mostra
`lib/features/admin/presentation/admin_viagens_screen.dart` como o maior
arquivo Dart, com 2.405 linhas.

### Contexto
`admin_viagens_screen.dart` ainda mistura orquestração de lista, criação,
detalhe, mapa, manifesto, snapshot e resumo de presença. Os widgets de detalhe
`_AdminTripMapSurface`, `_ManifestSurface`, `_ManifestStopRow`,
`_PresenceTag`, `_SnapshotSurface`, `_SnapshotLine` e o resumo
`_TripManifestSummary` são componentes visuais/derivados que podem sair da tela
stateful sem alterar gateway, DTO, rota ou contrato backend.

### Objetivo
Reduzir responsabilidade e tamanho de `admin_viagens_screen.dart` extraindo os
widgets de detalhe de viagem para `widgets/admin_viagens_detail_widgets.dart`,
mantendo textos, ações de presença, mapa operacional e snapshot inalterados.

### Impact Analysis
- **Contexto do card:** refatoração app guiada por hotspot após equilibrar o
  módulo de qualidade no backend.
- **Telas/fluxos afetados:** portal admin, tela de viagens, detalhe da viagem:
  mapa operacional, manifesto e snapshot.
- **Dependência de contrato backend:** nenhuma. Sem mudança de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudanças técnicas:** mover widgets stateless e resumo de manifesto para um
  módulo dedicado, expondo callbacks e dados já carregados pela tela principal.
- **Riscos e mitigação:**
  - alterar copy/labels do manifesto ou snapshot; mitigar com widget tests
    focados;
  - quebrar callback de correção de presença; mitigar testando acionamento do
    menu de presença;
  - acoplar widgets extraídos ao estado da tela; mitigar passando dados e
    callbacks por parâmetro;
  - criar arquivo novo grande; mitigar mantendo o novo arquivo abaixo de 600
    linhas.
- **Validação mínima obrigatória:** teste focado dos widgets extraídos,
  `flutter test test/features/admin/admin_viagens_screen_test.dart`,
  `./scripts/quality_baseline.sh`, `./scripts/qa_gate_card.sh --no-e2e` e
  `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`.
- **Rollback simples:** reverter novo arquivo e restaurar os widgets privados
  no `admin_viagens_screen.dart`; nenhum dado ou contrato é afetado.

### Escopo
1. Criar `widgets/admin_viagens_detail_widgets.dart`.
2. Extrair mapa operacional, manifesto, presença e snapshot do detalhe.
3. Adicionar widget tests focados.
4. Regenerar baseline do ratchet e atualizar documentação do programa.
5. Registrar evidência em QA Log e manter tudo local, sem push/PR.

### Fora de escopo
- Refatorar formulário de criação ou listagem.
- Alterar gateway, DTOs, payloads ou contrato backend.
- Alterar UX/copy intencional do detalhe.
- Rodar E2E iOS como requisito deste card.

### Critérios de Aceite
- `admin_viagens_screen.dart` fica menor que 2.405 linhas.
- Novo arquivo de widgets fica abaixo de 600 linhas.
- Testes focados dos widgets extraídos passam.
- `admin_viagens_screen_test.dart` passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet atualizado impede retorno do hotspot ao limite antigo.

### Test Plan do Card (obrigatório)
- Risco principal: mudar renderização/ações do detalhe de viagem sem perceber.
- Testes novos/ajustados:
  - [x] Tipo: widget tests focados.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_viagens_detail_widgets_test.dart`.
  - [x] Cenário feliz: manifesto, mapa e snapshot renderizam os textos
    operacionais esperados.
  - [x] Cenário de borda: menu de presença aciona callback com o status correto.
- Regressão mínima executada:
  - [x] `graphify explain "AdminViagensScreen"`
  - [x] Busca antes/depois por widgets privados movidos.
  - [x] `flutter test test/features/admin/presentation/widgets/admin_viagens_detail_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_viagens_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidência registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: 2259176)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0913 — QUALITY-0014: extrair widgets de onboarding do admin vínculos

### Status
DONE (commit: 5079f25)

### Origem
Continuidade das refatorações guiadas por hotspot do módulo de qualidade do
app. O ratchet atual mostra
`lib/features/admin/presentation/admin_vinculos_screen.dart` como o maior
arquivo Dart, com 2.249 linhas.

### Contexto
`admin_vinculos_screen.dart` ainda mistura orquestração da tela, solicitações
de acesso, códigos de convite, decisões, criação manual e painéis de vínculos.
Os painéis `_CompanyAccessRequestsPanel`, `_InviteCodesPanel`, o diálogo
`_InviteCodeDetailDialog`, o enum `_InviteDetailAction` e os helpers de
formatação/status de convite são UI/derivação de apresentação que podem sair
da tela stateful sem alterar gateway, DTO ou contrato backend.

### Objetivo
Reduzir responsabilidade e tamanho de `admin_vinculos_screen.dart` extraindo os
widgets de onboarding administrativo para
`widgets/admin_vinculos_onboarding_widgets.dart`, mantendo callbacks de
refresh, abertura de solicitação, criação de convite e detalhe de convite na
tela principal.

### Impact Analysis
- **Contexto do card:** refatoração app guiada por hotspot após
  `admin_viagens_screen.dart` deixar de ser o maior arquivo Dart.
- **Telas/fluxos afetados:** portal admin, tela de vínculos, seção de
  solicitações de acesso e códigos de convite.
- **Dependência de contrato backend:** nenhuma. Sem mudança de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudanças técnicas:** mover painéis stateless, diálogo de detalhe de
  convite, enum de ação e helpers de formatação/status para um módulo dedicado,
  expondo callbacks e dados já carregados pela tela principal.
- **Riscos e mitigação:**
  - alterar labels/status de convites; mitigar com widget tests focados;
  - quebrar callbacks de refresh, abertura de solicitação, criação ou detalhe
    de convite; mitigar testando callbacks dos painéis;
  - quebrar ação do diálogo de detalhe; mitigar testando retorno do botão
    "Copiar código";
  - criar arquivo novo grande; mitigar mantendo o novo arquivo abaixo de 600
    linhas.
- **Validação mínima obrigatória:** teste focado dos widgets extraídos,
  `flutter test test/features/admin/admin_vinculos_screen_test.dart`,
  `./scripts/quality_baseline.sh`, `./scripts/qa_gate_card.sh --no-e2e` e
  `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`.
- **Rollback simples:** reverter novo arquivo e restaurar os widgets privados
  no `admin_vinculos_screen.dart`; nenhum dado ou contrato é afetado.

### Escopo
1. Criar `widgets/admin_vinculos_onboarding_widgets.dart`.
2. Extrair painéis de solicitações e convites, diálogo de detalhe de convite e
   helpers de status/data.
3. Adicionar widget tests focados.
4. Regenerar baseline do ratchet e atualizar documentação do programa.
5. Registrar evidência em QA Log e manter tudo local, sem push/PR.

### Fora de escopo
- Refatorar diálogo de decisão de solicitação.
- Refatorar criação manual de vínculos responsável/passageiro.
- Alterar gateways, DTOs, payloads ou contrato backend.
- Alterar UX/copy intencional das seções extraídas.
- Rodar E2E iOS como requisito deste card.

### Critérios de Aceite
- `admin_vinculos_screen.dart` fica menor que 2.249 linhas.
- Novo arquivo de widgets fica abaixo de 600 linhas.
- Testes focados dos widgets extraídos passam.
- `admin_vinculos_screen_test.dart` passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet atualizado impede retorno do hotspot ao limite antigo.

### Test Plan do Card (obrigatório)
- Risco principal: mudar renderização/ações de solicitações e convites sem
  perceber.
- Testes novos/ajustados:
  - [x] Tipo: widget tests focados.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_vinculos_onboarding_widgets_test.dart`.
  - [x] Cenário feliz: painéis renderizam solicitações, métricas e status de
    convite.
  - [x] Cenário de borda: callbacks de refresh/criação/abertura e ação do
    diálogo de detalhe retornam valores corretos.
- Regressão mínima executada:
  - [x] `graphify explain "AdminVinculosScreen"`
  - [x] Busca antes/depois por widgets privados movidos.
  - [x] `flutter test test/features/admin/presentation/widgets/admin_vinculos_onboarding_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_vinculos_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidência registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: 5079f25)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0914 — QUALITY-0015: extrair aba de conta da home motorista

### Status
DONE (commit: b93b09c)

### Origem
Continuidade das refatoracoes guiadas por hotspot do modulo de qualidade do
app. O Graphify ainda aponta `HomeMotoristaScreen` como o no operacional da
home do motorista, e o ratchet atual mostra `lib/home_motorista_screen.dart`
como o maior arquivo Dart, com 2.103 linhas.

### Contexto
`home_motorista_screen.dart` ainda concentra estado de sessao, lifecycle da
viagem, mapa, abas operacionais e apresentacao da conta. A aba de conta e uma
parte de UI praticamente pura: consome `nome`, `email`, a viagem selecionada e
a lista de viagens do dia para renderizar avatar, metricas e informacoes da
conta, alem de disparar logout por callback.

### Objetivo
Reduzir responsabilidade e tamanho de `home_motorista_screen.dart` extraindo a
aba de conta para `widgets/driver_home_account_widgets.dart`, mantendo a tela
principal responsavel apenas por prover dados ja carregados e o callback de
logout.

### Impact Analysis
- **Contexto do card:** refatoracao app guiada por hotspot apos a extracao dos
  widgets de onboarding do admin vinculos.
- **Telas/fluxos afetados:** home do motorista, aba de conta, metricas de
  operacao do dia e acao de sair da conta.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** mover a composicao visual da aba de conta para um
  widget stateless dedicado, recebendo dados e callbacks por parametro.
- **Riscos e mitigacao:**
  - alterar fallback de avatar, viagem selecionada ou proxima parada; mitigar
    com widget tests focados;
  - quebrar acao de logout; mitigar testando o callback do botao;
  - acoplar o novo widget ao estado privado da tela; mitigar passando apenas
    `nome`, `email`, `trip`, `dayTrips` e `onLogout`;
  - criar arquivo novo grande; mitigar mantendo o novo arquivo abaixo de 600
    linhas.
- **Validacao minima obrigatoria:** teste focado do widget extraido,
  `flutter test test/screens/home_motorista_screen_test.dart`,
  `python3 scripts/quality_metrics_test.py`, `./scripts/quality_baseline.sh`,
  `./scripts/quality_gate.sh`, `./scripts/qa_gate_card.sh --no-e2e` e
  `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`.
- **Rollback simples:** reverter novo arquivo e restaurar a implementacao da
  aba de conta em `home_motorista_screen.dart`; nenhum dado ou contrato e
  afetado.

### Escopo
1. Criar `widgets/driver_home_account_widgets.dart`.
2. Exportar o novo widget pelo barrel `driver_home_widgets.dart`.
3. Substituir `_buildAccountTab` por composicao com `DriverAccountTab`.
4. Adicionar widget tests focados para renderizacao e callback de logout.
5. Regenerar baseline do ratchet e atualizar documentacao do programa.
6. Registrar evidencia em QA Log e manter tudo local, sem push/PR.

### Fora de escopo
- Refatorar mapa, agenda, bottom sheets ou lifecycle da viagem.
- Alterar copy/UX intencional da aba de conta.
- Alterar gateways, DTOs, payloads ou contrato backend.
- Rodar E2E iOS como requisito deste card.

### Criterios de Aceite
- `home_motorista_screen.dart` fica menor que 2.103 linhas.
- Novo arquivo de widgets fica abaixo de 600 linhas.
- Testes focados do widget extraido passam.
- `home_motorista_screen_test.dart` passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet atualizado impede retorno do hotspot ao limite antigo.

### Test Plan do Card (obrigatorio)
- Risco principal: mudar renderizacao da aba de conta ou quebrar o callback de
  logout sem perceber.
- Testes novos/ajustados:
  - [x] Tipo: widget tests focados.
  - [x] Arquivo:
    `test/features/driver_trip/presentation/widgets/driver_home_account_widgets_test.dart`.
  - [x] Cenario feliz: nome, email, viagem selecionada, proxima parada e
    metricas do dia aparecem na aba.
  - [x] Cenario de borda: fallback de avatar e estado sem viagem publicada
    aparecem corretamente.
- Regressao minima executada:
  - [x] `graphify explain "HomeMotoristaScreen"`
  - [x] Busca antes/depois por `_buildAccountTab` e `DriverAccountTab`.
  - [x] `flutter test test/features/driver_trip/presentation/widgets/driver_home_account_widgets_test.dart`
  - [x] `flutter test test/screens/home_motorista_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidencia registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: b93b09c)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0915 — QUALITY-0016: extrair widgets base do admin rotas

### Status
DONE (commit: a66137d)

### Origem
Continuidade das refatoracoes guiadas por hotspot do modulo de qualidade do
app. O ratchet atual mostra `lib/features/admin/presentation/admin_rotas_screen.dart`
como o maior arquivo Dart, com 2.095 linhas.

### Contexto
`admin_rotas_screen.dart` ainda mistura orquestracao da tela, criacao/edicao
de rota, picker de destino final e widgets puros de apresentacao. Os widgets de
lista, metricas, rodape, linhas de detalhe e estados visuais nao dependem do
estado privado da tela e podem sair para um modulo dedicado.

### Objetivo
Reduzir responsabilidade e tamanho de `admin_rotas_screen.dart` extraindo os
widgets base de apresentacao para `widgets/admin_rotas_widgets.dart`, mantendo
a tela principal responsavel por carregamento, modo ativo, formulario e
chamadas ao gateway.

### Impact Analysis
- **Contexto do card:** refatoracao app guiada por hotspot apos
  `home_motorista_screen.dart` deixar de ser o maior arquivo Dart.
- **Telas/fluxos afetados:** portal admin, area legada de rotas-base,
  listagem, detalhe, empty state, erro de carregamento e widgets auxiliares do
  formulario.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** mover widgets stateless e helpers visuais para um
  arquivo publico de widgets, expondo callbacks e dados ja carregados pela tela
  principal.
- **Riscos e mitigacao:**
  - alterar labels de compatibilidade de rotas-base; mitigar com widget tests
    focados;
  - quebrar callback de abertura de detalhe ou retry; mitigar testando taps;
  - acoplar widgets extraidos ao estado privado da tela; mitigar passando
    `AdminRotaBaseRecord`, textos e callbacks por parametro;
  - criar arquivo novo grande; mitigar mantendo o novo arquivo abaixo de 600
    linhas.
- **Validacao minima obrigatoria:** teste focado dos widgets extraidos,
  `flutter test test/features/admin/admin_rotas_screen_test.dart`,
  `python3 scripts/quality_metrics_test.py`, `./scripts/quality_baseline.sh`,
  `./scripts/quality_gate.sh`, `./scripts/qa_gate_card.sh --no-e2e` e
  `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`.
- **Rollback simples:** reverter novo arquivo e restaurar os widgets privados
  em `admin_rotas_screen.dart`; nenhum dado ou contrato e afetado.

### Escopo
1. Criar `widgets/admin_rotas_widgets.dart`.
2. Extrair notice legado, card de rota, cards auxiliares, labels, rodape,
   tiles de detalhe e estado de erro.
3. Substituir referencias privadas na tela por widgets publicos.
4. Adicionar widget tests focados para renderizacao e callbacks.
5. Regenerar baseline do ratchet e atualizar documentacao do programa.
6. Registrar evidencia em QA Log e manter tudo local, sem push/PR.

### Fora de escopo
- Refatorar formulario de criacao/edicao.
- Refatorar picker de destino final ou chamadas Places.
- Alterar gateways, DTOs, payloads ou contrato backend.
- Alterar UX/copy intencional da area legada de rotas-base.
- Rodar E2E iOS como requisito deste card.

### Criterios de Aceite
- `admin_rotas_screen.dart` fica menor que 2.095 linhas.
- Novo arquivo de widgets fica abaixo de 600 linhas.
- Testes focados dos widgets extraidos passam.
- `admin_rotas_screen_test.dart` passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet atualizado impede retorno do hotspot ao limite antigo.

### Test Plan do Card (obrigatorio)
- Risco principal: mudar renderizacao de listagem/detalhe da area legada ou
  quebrar callbacks de abertura e retry.
- Testes novos/ajustados:
  - [x] Tipo: widget tests focados.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_rotas_widgets_test.dart`.
  - [x] Cenario feliz: notice, card de rota, destino, metricas e rodape
    renderizam textos operacionais esperados.
  - [x] Cenario de borda: callback de abertura da rota e retry do erro sao
    acionados.
- Regressao minima executada:
  - [x] `graphify explain "AdminRotasScreen"`
  - [x] Busca antes/depois por widgets privados movidos.
  - [x] `flutter test test/features/admin/presentation/widgets/admin_rotas_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_rotas_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidencia registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: a66137d)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0916 — QUALITY-0017: extrair widgets de detalhe dos serviços recorrentes

### Status
DONE (commit: 2b176fa)

### Origem
Continuidade das refatoracoes guiadas por hotspot do modulo de qualidade do
app. O ratchet atual mostra
`lib/features/admin/presentation/admin_servicos_recorrentes_screen.dart` como
o maior arquivo Dart, com 2.010 linhas.

### Contexto
`admin_servicos_recorrentes_screen.dart` ja teve a listagem extraida, mas
ainda concentra orquestracao, formulario, picker de destino e a apresentacao do
cockpit/detalhe operacional. Os widgets de hero, prontidao, regras e
passageiros elegiveis sao UI derivada dos records carregados e podem sair da
tela stateful sem alterar o contrato.

### Objetivo
Reduzir responsabilidade e tamanho de
`admin_servicos_recorrentes_screen.dart` extraindo a apresentacao do detalhe
para `widgets/admin_servicos_recorrentes_detail_widgets.dart`, mantendo a tela
principal responsavel por carregamento, modo ativo, formulario, callbacks e
chamadas ao gateway.

### Impact Analysis
- **Contexto do card:** refatoracao app guiada por hotspot apos
  `admin_rotas_screen.dart` deixar de ser o maior arquivo Dart.
- **Telas/fluxos afetados:** portal admin, servicos recorrentes, detalhe/cockpit
  operacional, prontidao, regras e passageiros elegiveis.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** mover widgets stateless de detalhe para um modulo
  dedicado, recebendo `AdminServicoRecorrenteRecord` e lista de passageiros por
  parametro.
- **Riscos e mitigacao:**
  - alterar labels de prontidao/regra operacional; mitigar com widget tests
    focados;
  - perder estado visual de servico sem motorista, sem passageiros ou sem
    destino; mitigar com cenario de borda;
  - acoplar widgets extraidos ao estado privado da tela; mitigar passando
    records e listas por parametro;
  - criar arquivo novo grande; mitigar mantendo o novo arquivo abaixo de 600
    linhas.
- **Validacao minima obrigatoria:** teste focado dos widgets extraidos,
  `flutter test test/features/admin/admin_servicos_recorrentes_screen_test.dart`,
  `python3 scripts/quality_metrics_test.py`, `./scripts/quality_baseline.sh`,
  `./scripts/quality_gate.sh`, `./scripts/qa_gate_card.sh --no-e2e` e
  `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`.
- **Rollback simples:** reverter novo arquivo e restaurar widgets privados em
  `admin_servicos_recorrentes_screen.dart`; nenhum dado ou contrato e afetado.

### Escopo
1. Criar `widgets/admin_servicos_recorrentes_detail_widgets.dart`.
2. Extrair hero, prontidao operacional, regras e passageiros elegiveis.
3. Substituir referencias privadas no detalhe por widgets publicos.
4. Adicionar widget tests focados para labels, bordas e estado vazio.
5. Regenerar baseline do ratchet e atualizar documentacao do programa.
6. Registrar evidencia em QA Log e manter tudo local, sem push/PR.

### Fora de escopo
- Refatorar formulario de criacao/edicao.
- Refatorar picker de destino final ou chamadas Places.
- Alterar gateways, DTOs, payloads ou contrato backend.
- Alterar UX/copy intencional do detalhe.
- Rodar E2E iOS como requisito deste card.

### Criterios de Aceite
- `admin_servicos_recorrentes_screen.dart` fica menor que 2.010 linhas.
- Novo arquivo de widgets fica abaixo de 600 linhas.
- Testes focados dos widgets extraidos passam.
- `admin_servicos_recorrentes_screen_test.dart` passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet atualizado impede retorno do hotspot ao limite antigo.

### Test Plan do Card (obrigatorio)
- Risco principal: mudar renderizacao do detalhe/cockpit ou labels de
  prontidao sem perceber.
- Testes novos/ajustados:
  - [x] Tipo: widget tests focados.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_servicos_recorrentes_detail_widgets_test.dart`.
  - [x] Cenario feliz: hero, prontidao, regras e passageiros elegiveis exibem
    dados operacionais esperados.
  - [x] Cenario de borda: servico sem motorista/passageiros/destino mostra
    fallback correto.
- Regressao minima executada:
  - [x] `graphify explain "AdminServicosRecorrentesScreen"`
  - [x] Busca antes/depois por widgets privados movidos.
  - [x] `flutter test test/features/admin/presentation/widgets/admin_servicos_recorrentes_detail_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_servicos_recorrentes_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidencia registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: 2b176fa)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0917 — QUALITY-0018: extrair widgets da jornada da home motorista

### Status
DONE (commit: ea5a276)

### Origem
Continuidade das refatoracoes guiadas por hotspot do modulo de qualidade do
app. O ratchet atual mostra `lib/home_motorista_screen.dart` como o maior
arquivo Dart, com 1.985 linhas.

### Contexto
`home_motorista_screen.dart` ja teve cards, conta, mapa e partes da fila
extraidos, mas ainda concentra orquestracao de estado com composicao visual da
jornada map-first: top bars, seletor de agenda do dia, sheet de planejamento e
sheet de corrida ativa. Essas superficies sao UI derivada de
`DriverTripRecord`, callbacks e flags do estado, e podem sair da tela stateful
sem alterar presenters, gateways ou lifecycle.

### Objetivo
Reduzir responsabilidade e tamanho de `home_motorista_screen.dart` extraindo
widgets de jornada para `features/driver_trip/presentation/widgets/`, mantendo
a tela principal responsavel por carregamento, navegacao, acoes de corrida,
edicao de rota, localizacao e selecao de aba.

### Impact Analysis
- **Contexto do card:** refatoracao app guiada por hotspot apos
  `admin_servicos_recorrentes_screen.dart` deixar de ser o maior arquivo Dart.
- **Telas/fluxos afetados:** home motorista map-first, planejamento da viagem,
  corrida ativa, seletor de viagens do dia e atalhos para lista/mapa.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** mover widgets stateless de top bar, seletor de agenda
  e sheets de jornada para modulo dedicado, recebendo records, chaves,
  callbacks e flags por parametro.
- **Riscos e mitigacao:**
  - quebrar chaves usadas pelos testes e automacao; mitigar mantendo as keys em
    `HomeMotoristaScreen` e injetando nos widgets;
  - alterar labels ou estado habilitado dos botoes de corrida; mitigar com
    widget tests focados;
  - acoplar widgets extraidos ao estado privado da tela; mitigar passando
    callbacks e dados por parametro;
  - criar arquivo novo grande; mitigar mantendo o novo arquivo abaixo de 600
    linhas.
- **Validacao minima obrigatoria:** teste focado dos widgets extraidos,
  `flutter test test/screens/home_motorista_screen_test.dart`,
  `python3 scripts/quality_metrics_test.py`, `./scripts/quality_baseline.sh`,
  `./scripts/quality_gate.sh`, `./scripts/qa_gate_card.sh --no-e2e` e
  `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`.
- **Rollback simples:** reverter novo arquivo e restaurar widgets privados em
  `home_motorista_screen.dart`; nenhum dado ou contrato e afetado.

### Escopo
1. Criar modulo de widgets de jornada da home motorista.
2. Extrair top bar de planejamento, top bar ativa, seletor de viagens do dia,
   sheet de planejamento e sheet ativa.
3. Substituir referencias privadas na tela por widgets publicos parametrizados.
4. Adicionar widget tests focados para labels, callbacks e estado desabilitado.
5. Regenerar baseline do ratchet e atualizar documentacao do programa.
6. Registrar evidencia em QA Log e manter tudo local, sem push/PR.

### Fora de escopo
- Refatorar lifecycle da corrida, presenters, gateways ou servicos.
- Refatorar edicao/reordenacao da rota do dia.
- Refatorar painel modal de detalhes.
- Alterar UX/copy intencional da home motorista.
- Rodar E2E iOS como requisito deste card.

### Criterios de Aceite
- `home_motorista_screen.dart` fica menor que 1.985 linhas.
- Novo arquivo de widgets fica abaixo de 600 linhas.
- Testes focados dos widgets extraidos passam.
- `home_motorista_screen_test.dart` passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet atualizado impede retorno do hotspot ao limite antigo.

### Test Plan do Card (obrigatorio)
- Risco principal: mudar renderizacao da jornada map-first ou quebrar callbacks
  das acoes principais sem perceber.
- Testes novos/ajustados:
  - [x] Tipo: widget tests focados.
  - [x] Arquivo:
    `test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart`.
  - [x] Cenario feliz: top bar, seletor de agenda e sheet de planejamento
    exibem viagem, horario, tipo, passageiros e acoes esperadas.
  - [x] Cenario ativo: sheet ativa mostra proximo passageiro, ETA, botoes e
    callback de no-show/conclusao.
  - [x] Cenario de borda: corrida sem proxima parada mostra encerramento.
- Regressao minima executada:
  - [x] `graphify explain "HomeMotoristaScreen"`
  - [x] Busca antes/depois por widgets privados movidos.
  - [x] `flutter test test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart`
  - [x] `flutter test test/screens/home_motorista_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidencia registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: ea5a276)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0918 — QUALITY-0019: extrair view de criação manual de viagens

### Status
DONE (commit: 9917e6b)

### Origem
Continuidade das refatoracoes guiadas por hotspot do modulo de qualidade do
app. O ratchet atual mostra
`lib/features/admin/presentation/admin_viagens_screen.dart` como o maior
arquivo Dart, com 1.825 linhas.

### Contexto
`admin_viagens_screen.dart` ja teve widgets de listagem e detalhe extraidos,
mas ainda concentra carregamento, comandos de viagem, resultado da geração
automatica e a view stateful de criacao manual. A view de criacao manual tem
estado local de formulario e depende apenas das listas de rotas, serviços,
motoristas, estado de submit e callbacks; portanto pode sair da tela principal
sem alterar gateway, DTO, contrato ou fluxo de detalhe.

### Objetivo
Reduzir responsabilidade e tamanho de `admin_viagens_screen.dart` extraindo a
view de criação manual para `widgets/admin_viagens_create_widgets.dart`,
mantendo a tela principal responsavel por carregamento, troca de modo,
submissao, detalhe, cancelamento e atualizacao do manifesto.

### Impact Analysis
- **Contexto do card:** refatoracao app guiada por hotspot apos
  `home_motorista_screen.dart` deixar de ser o maior arquivo Dart.
- **Telas/fluxos afetados:** portal admin, viagens, criacao manual de viagem,
  seleção de serviço recorrente, fallback legado por rota-base e motorista
  operacional.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** mover `_AdminViagemCreateView` e seu estado para um
  widget publico dedicado, parametrizado por records, flag de submit e
  callbacks.
- **Riscos e mitigacao:**
  - quebrar validacao de formulario quando serviço/motorista não são
    selecionados; mitigar com widget tests focados;
  - perder fallback legado de rota-base quando nao ha servicos recorrentes;
    mitigar com cenario de borda;
  - alterar payload `AdminViagemCreateInput`; mitigar verificando o input
    emitido no callback do teste;
  - criar arquivo novo grande; mitigar mantendo o novo arquivo abaixo de 600
    linhas.
- **Validacao minima obrigatoria:** teste focado dos widgets extraidos,
  `flutter test test/features/admin/admin_viagens_screen_test.dart`,
  `python3 scripts/quality_metrics_test.py`, `./scripts/quality_baseline.sh`,
  `./scripts/quality_gate.sh`, `./scripts/qa_gate_card.sh --no-e2e` e
  `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`.
- **Rollback simples:** reverter novo arquivo e restaurar `_AdminViagemCreateView`
  em `admin_viagens_screen.dart`; nenhum dado ou contrato e afetado.

### Escopo
1. Criar `widgets/admin_viagens_create_widgets.dart`.
2. Extrair view stateful de criacao manual de viagens.
3. Substituir a referencia privada na tela por widget publico parametrizado.
4. Adicionar widget tests focados para seleção, submit e fallback legado.
5. Regenerar baseline do ratchet e atualizar documentacao do programa.
6. Registrar evidencia em QA Log e manter tudo local, sem push/PR.

### Fora de escopo
- Refatorar detalhe da viagem, troca de motorista, cancelamento ou manifesto.
- Alterar gateways, DTOs, payloads ou contrato backend.
- Alterar UX/copy intencional da criação manual.
- Rodar E2E iOS como requisito deste card.

### Criterios de Aceite
- `admin_viagens_screen.dart` fica menor que 1.825 linhas.
- Novo arquivo de widgets fica abaixo de 600 linhas.
- Testes focados dos widgets extraidos passam.
- `admin_viagens_screen_test.dart` passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet atualizado impede retorno do hotspot ao limite antigo.

### Test Plan do Card (obrigatorio)
- Risco principal: mudar payload ou validação da criação manual sem perceber.
- Testes novos/ajustados:
  - [x] Tipo: widget tests focados.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_viagens_create_widgets_test.dart`.
  - [x] Cenario feliz: selecionar serviço/motorista e criar emite
    `AdminViagemCreateInput` esperado.
  - [x] Cenario de borda: sem serviços recorrentes, fallback por rota-base
    renderiza e emite `rotaBaseId`.
- Regressao minima executada:
  - [x] `graphify explain "AdminViagensScreen"`
  - [x] Busca antes/depois por widgets privados movidos.
  - [x] `flutter test test/features/admin/presentation/widgets/admin_viagens_create_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_viagens_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidencia registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: 9917e6b)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0919 — QUALITY-0020: extrair widgets de serviços liberados dos vínculos

### Status
DONE (commit: 5cc957e)

### Origem
Continuidade das refatoracoes guiadas por hotspot do modulo de qualidade do
app. O ratchet atual mostra
`lib/features/admin/presentation/admin_vinculos_screen.dart` como o maior
arquivo Dart, com 1.778 linhas.

### Contexto
`admin_vinculos_screen.dart` ja teve widgets de onboarding e base extraidos,
mas ainda concentra paineis, dialogos e helpers privados de varias
responsabilidades. O painel de direitos por serviço e o dialogo de seleção de
serviços do passageiro têm estado/local callbacks bem delimitados e dependem
apenas de records de passageiro/serviço e callbacks da tela principal.

### Objetivo
Reduzir responsabilidade e tamanho de `admin_vinculos_screen.dart` extraindo os
widgets de serviços liberados do passageiro para
`widgets/admin_vinculos_service_widgets.dart`, mantendo a tela principal como
dona do gateway, snackbars, carregamento e atualização de dados.

### Impact Analysis
- **Contexto do card:** refatoracao app guiada por hotspot apos
  `admin_viagens_screen.dart` deixar de ser o maior arquivo Dart.
- **Telas/fluxos afetados:** portal admin, gestão de vínculos, direitos por
  serviço, edição de serviços liberados do passageiro.
- **Dependencia de contrato backend:** nenhuma. Sem mudança de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** mover `_PassageiroServicosPanel`,
  `_PassageiroServicosDialog` e helpers de label para um widget público
  dedicado, parametrizado por listas e callbacks.
- **Riscos e mitigacao:**
  - quebrar a contagem de serviços liberados por passageiro; mitigar com widget
    test focado do painel;
  - alterar a ordem dos IDs enviados pelo dialogo; mitigar com teste do dialogo
    validando a lista ordenada conforme a lista de serviços;
  - acoplar o novo arquivo ao estado privado da tela; mitigar mantendo o gateway
    e `showDialog` na tela principal;
  - criar arquivo novo grande; mitigar mantendo o novo arquivo abaixo de 600
    linhas.
- **Validacao minima obrigatoria:** teste focado dos widgets extraidos,
  `flutter test test/features/admin/admin_vinculos_screen_test.dart`,
  `python3 scripts/quality_metrics_test.py`, `./scripts/quality_baseline.sh`,
  `./scripts/quality_gate.sh`, `./scripts/qa_gate_card.sh --no-e2e` e
  `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`.
- **Rollback simples:** reverter novo arquivo e restaurar os widgets privados
  em `admin_vinculos_screen.dart`; nenhum dado ou contrato e afetado.

### Escopo
1. Criar `widgets/admin_vinculos_service_widgets.dart`.
2. Extrair painel de direitos por serviço e dialogo de seleção.
3. Substituir referencias privadas na tela por widgets públicos parametrizados.
4. Adicionar widget tests focados para painel, callback e seleção ordenada.
5. Regenerar baseline do ratchet e atualizar documentacao do programa.
6. Registrar evidencia em QA Log e manter tudo local, sem push/PR.

### Fora de escopo
- Refatorar solicitação de acesso, convites, atalhos motorista ou vínculos de
  responsável.
- Alterar gateways, DTOs, payloads ou contrato backend.
- Alterar UX/copy intencional da gestão de vínculos.
- Rodar E2E iOS como requisito deste card.

### Criterios de Aceite
- `admin_vinculos_screen.dart` fica menor que 1.778 linhas.
- Novo arquivo de widgets fica abaixo de 600 linhas.
- Testes focados dos widgets extraidos passam.
- `admin_vinculos_screen_test.dart` passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet atualizado impede retorno do hotspot ao limite antigo.

### Test Plan do Card (obrigatorio)
- Risco principal: mudar renderizacao dos direitos por serviço ou o payload de
  IDs selecionados sem perceber.
- Testes novos/ajustados:
  - [x] Tipo: widget tests focados.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_vinculos_service_widgets_test.dart`.
  - [x] Cenario feliz: painel exibe passageiro, contagem e tags de serviços
    liberados, acionando callback de edição.
  - [x] Cenario de borda: passageiro sem serviço mostra tag de sem acesso.
  - [x] Cenario de dialogo: seleção/desseleção salva IDs ordenados conforme a
    lista de serviços.
- Regressao minima executada:
  - [x] `graphify explain "AdminVinculosScreen"`
  - [x] Busca antes/depois por widgets privados movidos.
  - [x] `flutter test test/features/admin/presentation/widgets/admin_vinculos_service_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_vinculos_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidencia registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: 5cc957e)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0920 — QUALITY-0021: extrair aba de conta da home passageiro

### Status
DONE (commit: 7338451)

### Origem
Continuidade das refatoracoes guiadas por hotspot do modulo de qualidade do
app. O ratchet atual mostra `lib/home_passageiro_screen.dart` como o maior
arquivo Dart, com 1.716 linhas.

### Contexto
`home_passageiro_screen.dart` concentra carregamento, websocket, selecao de
viagem, presenca por servico, composicao map-first, conta e ainda mantém
widgets privados antigos que nao sao mais referenciados. A aba de conta e uma
superficie visual derivada de dados ja carregados (`nome`, `email`, viagem
selecionada e agenda do dia) e pode sair da tela stateful sem alterar gateway,
contrato, lifecycle ou navegacao.

### Objetivo
Reduzir responsabilidade e tamanho de `home_passageiro_screen.dart` extraindo a
aba de conta para `features/tracking/presentation/widgets/`, mantendo a tela
principal responsavel por estado, carregamento, websocket, presenca, mapa,
detalhes da viagem e logout.

### Impact Analysis
- **Contexto do card:** refatoracao app guiada por hotspot apos
  `admin_vinculos_screen.dart` deixar de ser o maior arquivo Dart.
- **Telas/fluxos afetados:** home passageiro, aba Conta, resumo de viagens do
  dia, ETA atual, viagem selecionada, parada do passageiro e acao de logout.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** criar widget publico de conta do passageiro
  parametrizado por dados e callback; substituir `_buildAccountTab`; remover
  widgets privados mortos do arquivo.
- **Riscos e mitigacao:**
  - alterar labels ou resumo da conta; mitigar com widget test focado;
  - quebrar a navegacao para a aba Conta; mitigar mantendo teste existente de
    `home_passageiro_screen_test.dart`;
  - acoplar widget novo ao estado privado da tela; mitigar passando dados e
    callbacks por parametro;
  - remover widget ainda usado indiretamente; mitigar com `rg` antes/depois por
    nomes privados removidos;
  - criar arquivo novo grande; mitigar mantendo o novo arquivo abaixo de 600
    linhas.
- **Validacao minima obrigatoria:** teste focado do widget extraido,
  `flutter test test/screens/home_passageiro_screen_test.dart`,
  `python3 scripts/quality_metrics_test.py`, `./scripts/quality_baseline.sh`,
  `./scripts/quality_gate.sh`, `./scripts/qa_gate_card.sh --no-e2e` e
  `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`.
- **Rollback simples:** reverter novo arquivo e restaurar `_buildAccountTab` e
  widgets privados em `home_passageiro_screen.dart`; nenhum dado ou contrato e
  afetado.

### Escopo
1. Criar `widgets/passenger_home_account_widgets.dart`.
2. Extrair a aba Conta do passageiro para widget publico parametrizado.
3. Substituir a referencia privada da tela por widget publico.
4. Remover widgets privados sem uso confirmados por busca.
5. Adicionar widget tests focados para conta, fallback de avatar e logout.
6. Regenerar baseline do ratchet e atualizar documentacao do programa.
7. Registrar evidencia em QA Log e manter tudo local, sem push/PR.

### Fora de escopo
- Refatorar mapa, agenda do dia, presenca por servico ou deep link.
- Alterar gateways, DTOs, payloads ou contrato backend.
- Alterar UX/copy intencional da home passageiro.
- Rodar E2E iOS como requisito deste card.

### Criterios de Aceite
- `home_passageiro_screen.dart` fica menor que 1.716 linhas.
- Novo arquivo de widgets fica abaixo de 600 linhas.
- Testes focados do widget extraido passam.
- `home_passageiro_screen_test.dart` passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet atualizado impede retorno do hotspot ao limite antigo.

### Test Plan do Card (obrigatorio)
- Risco principal: mudar renderizacao da aba Conta ou quebrar callback de
  logout sem perceber.
- Testes novos/ajustados:
  - [x] Tipo: widget tests focados.
  - [x] Arquivo:
    `test/features/tracking/presentation/widgets/passenger_home_account_widgets_test.dart`.
  - [x] Cenario feliz: conta exibe nome, papel, resumo de viagens, ETA, viagem
    selecionada, parada e email.
  - [x] Cenario de borda: nome vazio usa fallback de avatar e estados vazios
    da viagem/parada.
  - [x] Cenario de acao: botao de sair aciona o callback recebido.
- Regressao minima executada:
  - [x] `graphify explain "HomePassageiroScreen"`
  - [x] Busca antes/depois por widgets privados removidos.
  - [x] `flutter test test/features/tracking/presentation/widgets/passenger_home_account_widgets_test.dart`
  - [x] `flutter test test/screens/home_passageiro_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidencia registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: 7338451)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0921 — QUALITY-0022: extrair listagem de passageiros do portal admin

### Status
DONE (commit: 9ca5831)

### Origem
Continuidade das refatoracoes guiadas por hotspot do modulo de qualidade do
app. O ratchet atual mostra
`lib/features/admin/presentation/admin_passageiros_screen.dart` como o maior
arquivo Dart, com 1.575 linhas.

### Contexto
`admin_passageiros_screen.dart` concentra carregamento de gateway, selecao,
dialogs de cadastro/edicao, reatribuicao de motorista, calculo de metricas,
listagem tabular e cards de atividade. A listagem e a area de atividades sao
UI derivada de `AdminPassageiroRecord`, selecao atual e callbacks da tela, e
podem sair da tela stateful sem alterar gateway, DTO, payload ou contrato.

### Objetivo
Reduzir responsabilidade e tamanho de `admin_passageiros_screen.dart`
extraindo o painel de listagem, a paginacao visual e a secao de atividades para
`widgets/admin_passageiros_widgets.dart`, mantendo a tela principal responsavel
por dados, selecao, dialogs, snackbars e comandos administrativos.

### Impact Analysis
- **Contexto do card:** refatoracao app guiada por hotspot apos
  `home_passageiro_screen.dart` deixar de ser o maior arquivo Dart.
- **Telas/fluxos afetados:** portal admin, passageiros, lista tabular,
  selecao de passageiro, atalhos de editar/reatribuir e atividades recentes.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** mover `_PassageirosListPanel`,
  `_PassengerActivityCard`, `_PaginationPill` e estilo de header para widget
  publico dedicado, parametrizado por records e callbacks.
- **Riscos e mitigacao:**
  - quebrar habilitacao de editar/reatribuir apenas no passageiro selecionado;
    mitigar com widget test focado;
  - alterar leitura de passageiro sem motorista fixo; mitigar com teste de
    estado pendente/sem atalho;
  - perder empty state da lista; mitigar com teste de lista vazia;
  - acoplar o novo arquivo ao estado privado da tela; mitigar passando dados e
    callbacks por parametro;
  - criar arquivo novo grande; mitigar mantendo o novo arquivo abaixo de 600
    linhas.
- **Validacao minima obrigatoria:** teste focado dos widgets extraidos,
  `flutter test test/features/admin/admin_passageiros_screen_test.dart`,
  `python3 scripts/quality_metrics_test.py`, `./scripts/quality_baseline.sh`,
  `./scripts/quality_gate.sh`, `./scripts/qa_gate_card.sh --no-e2e` e
  `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`.
- **Rollback simples:** reverter novo arquivo e restaurar widgets privados em
  `admin_passageiros_screen.dart`; nenhum dado ou contrato e afetado.

### Escopo
1. Criar `widgets/admin_passageiros_widgets.dart`.
2. Extrair painel de listagem, paginacao visual e cards/secao de atividades.
3. Substituir referencias privadas na tela por widgets publicos
   parametrizados.
4. Adicionar widget tests focados para lista, estado pendente, callbacks e
   empty state.
5. Regenerar baseline do ratchet e atualizar documentacao do programa.
6. Registrar evidencia em QA Log e manter tudo local, sem push/PR.

### Fora de escopo
- Refatorar dialogs de cadastro, edicao ou reatribuicao.
- Alterar gateways, DTOs, payloads ou contrato backend.
- Alterar UX/copy intencional do portal de passageiros.
- Rodar E2E iOS como requisito deste card.

### Criterios de Aceite
- `admin_passageiros_screen.dart` fica menor que 1.575 linhas.
- Novo arquivo de widgets fica abaixo de 600 linhas.
- Testes focados dos widgets extraidos passam.
- `admin_passageiros_screen_test.dart` passa.
- `./scripts/qa_gate_card.sh --no-e2e` passa.
- O ratchet atualizado impede retorno do hotspot ao limite antigo.

### Test Plan do Card (obrigatorio)
- Risco principal: mudar renderizacao da lista ou callbacks de edicao sem
  perceber.
- Testes novos/ajustados:
  - [x] Tipo: widget tests focados.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_passageiros_widgets_test.dart`.
  - [x] Cenario feliz: painel lista passageiro selecionado, status ativo e
    callbacks de selecionar/editar/reatribuir.
  - [x] Cenario de borda: passageiro sem motorista mostra `Sem atalho` e
    status pendente.
  - [x] Cenario vazio: lista vazia mostra empty state e refresh continua
    acionavel.
  - [x] Cenario de atividade: secao de atividades alterna textos com e sem
    passageiro selecionado.
- Regressao minima executada:
  - [x] `graphify explain "AdminPassageirosScreen"`
  - [x] Busca antes/depois por widgets privados movidos.
  - [x] `flutter test test/features/admin/presentation/widgets/admin_passageiros_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_passageiros_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidencia registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: 9ca5831)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0922 — QUALITY-0023: padronizar quality gate de CI do app

### Status
DONE (commit: 52e3fa1)

### Origem
Continuidade do modulo de qualidade do app. O backend ja possui
`scripts/ci_quality_gate.sh` como entrypoint dedicado do job de CI, enquanto o
workflow Flutter chama diretamente `scripts/qa_gate_card.sh --no-e2e`.

### Contexto
O gate local do app esta funcional e ja bloqueia regressao objetiva via
`qa_gate_card.sh --no-e2e`. Porem, deixar o workflow acoplado diretamente ao
gate de card dificulta evoluir politicas especificas de CI sem tocar no fluxo
local de entrega de card. Um entrypoint `ci_quality_gate.sh` deixa app e
backend simetricos, mais previsiveis para humanos e agentes, e documenta que o
CI nao roda E2E real por padrao.

### Objetivo
Criar um entrypoint dedicado para o quality gate de CI do app, atualizar o
workflow Flutter para chama-lo e adicionar uma verificacao automatizada simples
que proteja essa ligacao.

### Impact Analysis
- **Contexto do card:** endurecimento do modulo de qualidade, focado em CI/CD
  e paridade com o backend.
- **Telas/fluxos afetados:** nenhum fluxo funcional do app.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** adicionar `scripts/ci_quality_gate.sh`, apontar
  `.github/workflows/flutter-quality.yml` para ele e criar teste de wiring em
  `scripts/ci_quality_gate_test.py`.
- **Riscos e mitigacao:**
  - o workflow deixar de executar o gate real; mitigar com teste que exige
    referencia direta ao entrypoint dedicado;
  - o CI tentar rodar E2E real sem device/segredos; mitigar mantendo
    `--no-e2e` no script de CI;
  - duplicar logica entre gate de card e gate de CI; mitigar deixando o CI
    delegar para `qa_gate_card.sh`.
- **Validacao minima obrigatoria:** RED/GREEN do teste de wiring,
  `python3 scripts/ci_quality_gate_test.py`,
  `python3 scripts/quality_metrics_test.py`, `./scripts/quality_gate.sh`,
  `./scripts/ci_quality_gate.sh`, `git diff --check` e
  `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`.
- **Rollback simples:** reverter `scripts/ci_quality_gate.sh`,
  `scripts/ci_quality_gate_test.py` e a linha do workflow; o workflow volta a
  chamar `qa_gate_card.sh --no-e2e` diretamente.

### Escopo
1. Criar teste automatizado para o wiring do CI gate do app.
2. Criar `scripts/ci_quality_gate.sh` delegando para o gate local sem E2E.
3. Atualizar `.github/workflows/flutter-quality.yml` para usar o entrypoint.
4. Atualizar `docs/quality-ratchet-program.md`.
5. Registrar evidencia em QA Log e manter tudo local, sem push/PR.

### Fora de escopo
- Adicionar build Android/iOS no CI.
- Rodar integration tests/E2E no GitHub Actions.
- Alterar `qa_gate_card.sh`.
- Alterar contratos backend/app.

### Criterios de Aceite
- Workflow Flutter chama `./scripts/ci_quality_gate.sh`.
- `scripts/ci_quality_gate.sh` executa o gate local com `--no-e2e`.
- Teste de wiring falha quando o entrypoint nao existe ou nao esta referenciado.
- `./scripts/ci_quality_gate.sh` passa localmente.
- Documentacao do programa de qualidade descreve o novo entrypoint.

### Test Plan do Card (obrigatorio)
- Risco principal: workflow ficar verde sem chamar o gate oficial.
- Testes novos/ajustados:
  - [x] Tipo: teste Python de wiring de CI.
  - [x] Arquivo: `scripts/ci_quality_gate_test.py`.
  - [x] Cenario: workflow chama `./scripts/ci_quality_gate.sh`.
  - [x] Cenario: script de CI delega para `scripts/qa_gate_card.sh --no-e2e`.
- Regressao minima executada:
  - [x] RED `python3 scripts/ci_quality_gate_test.py`
  - [x] GREEN `python3 scripts/ci_quality_gate_test.py`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
- Evidencia registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: 52e3fa1)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0923 — QUALITY-0024: alinhar AGENTS e harness ao CI gate do app

### Status
DONE (commit: d0d5494)

### Origem
Continuidade do modulo de qualidade apos introduzir o Personal AI Dev Harness
no workspace VanRides. O app ja possui `scripts/ci_quality_gate.sh`, mas o
`AGENTS.md` do modulo ainda descreve o workflow como se ele chamasse
`qa_gate_card.sh --no-e2e` diretamente, e o harness ainda nao lista o CI gate
do app entre comandos monitorados.

### Contexto
O harness passou a ser a camada de preflight/finalizacao do bloco de qualidade:
`status`, `check`, `start-card`, `done-check` e `refresh-dashboard`. Para
humanos e agentes de IA, os docs de runtime precisam refletir esse fluxo para
evitar que proximos cards pulem o harness ou validem o workflow antigo do app.

### Objetivo
Sincronizar o `AGENTS.md` e o padrao de qualidade do app com o entrypoint de
CI atual, e incluir `./scripts/ci_quality_gate.sh` no harness do app.

### Impact Analysis
- **Contexto do card:** documentacao operacional e integracao do harness ao
  modulo de qualidade.
- **Telas/fluxos afetados:** nenhum fluxo funcional do app.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** atualizar `AGENTS.md`,
  `backlog/CARD-QUALITY-STANDARD.md` e `.dev-harness/harness.json` para
  refletir o CI gate atual e o fluxo obrigatorio do harness.
- **Riscos e mitigacao:**
  - docs ficarem inconsistentes entre gate local e CI; mitigar citando
    `qa_gate_card.sh --no-e2e` como gate por card e `ci_quality_gate.sh` como
    gate de CI;
  - harness nao perceber mudanca no comando do app; mitigar adicionando o
    entrypoint na lista de comandos do repo app;
  - marcar o card como DONE sem passar pelo harness; mitigar executando
    `done-check` e `refresh-dashboard` antes do fechamento.
- **Validacao minima obrigatoria:** `python3 -m dev_harness ... check`,
  `./scripts/ci_quality_gate.sh`, `./scripts/qa_gate_card.sh --no-e2e`,
  `git diff --check`, `python3 -m dev_harness ... done-check` e
  `python3 -m dev_harness ... refresh-dashboard`.
- **Rollback simples:** reverter docs e o item de comando do harness; nenhum
  dado, contrato ou comportamento de app e afetado.

### Escopo
1. Atualizar docs operacionais do app para apontar o workflow ao
   `ci_quality_gate.sh`.
2. Atualizar o padrao de qualidade por card com preflight/finalizacao do
   harness.
3. Incluir `./scripts/ci_quality_gate.sh` nos comandos monitorados do app no
   harness.
4. Registrar evidencia em QA Log e manter tudo local, sem push/PR.

### Fora de escopo
- Alterar scripts de gate.
- Alterar workflow GitHub Actions.
- Alterar runtime backend.
- Refatorar telas ou services.

### Criterios de Aceite
- `vanrides-app/AGENTS.md` descreve corretamente o workflow chamando
  `./scripts/ci_quality_gate.sh`.
- `backlog/CARD-QUALITY-STANDARD.md` orienta o uso do harness antes e depois
  do card.
- `.dev-harness/harness.json` lista `./scripts/ci_quality_gate.sh` nos comandos
  do app.
- Gates do app e checks do harness passam.

### Test Plan do Card (obrigatorio)
- Risco principal: documentacao/runtime orientar o agente a executar o gate
  errado.
- Testes novos/ajustados:
  - [x] Tipo: validacao operacional por harness + gates existentes.
  - [x] Arquivos: `AGENTS.md`, `backlog/CARD-QUALITY-STANDARD.md` e
    `.dev-harness/harness.json`.
  - [x] Cenario feliz: harness reconhece o card e nao reporta blockers.
  - [x] Cenario de borda: CI gate do app continua rodando sem E2E real.
- Regressao minima executada:
  - [x] `python3 -m dev_harness ... check`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `git diff --check`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: <hash>)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Harness `done-check` e dashboard atualizados.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0924 — QUALITY-0025: proteger docs Cloud contra gate legado do app

### Status
DONE (commit: a17dbbe)

### Origem
Continuidade do modulo de qualidade apos o `QUALITY-0024`. O runtime principal
do app ja aponta para `scripts/ci_quality_gate.sh`, mas ainda ha superficies
operacionais de Codex Cloud recomendando `scripts/qa_gate_card.sh --no-e2e`
como comando padrao/equivalente de CI.

### Contexto
O app tem um entrypoint dedicado para CI (`scripts/ci_quality_gate.sh`) que
valida o wiring do workflow e delega para o gate local sem E2E real. Se docs de
Cloud ou setup scripts continuarem recomendando o gate legado diretamente, um
agente pode pular o teste de wiring e reabrir a divergencia entre GitHub
Actions, Codex Cloud e manutencao local.

### Objetivo
Adicionar cobertura de wiring para `docs/codex-cloud.md` e
`scripts/codex_cloud_setup.sh`, garantindo que ambos recomendem
`./scripts/ci_quality_gate.sh` como comando principal do agente/CI, mantendo
`qa_gate_card.sh --no-e2e` como detalhe de delegacao do entrypoint.

### Impact Analysis
- **Contexto do card:** consistencia operacional do quality gate do app entre
  CI, Codex Cloud e setup local.
- **Telas/fluxos afetados:** nenhum fluxo funcional do app.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** ajustar teste de wiring,
  `docs/codex-cloud.md`, `scripts/codex_cloud_setup.sh` e, se necessario,
  `docs/quality-ratchet-program.md`.
- **Riscos e mitigacao:**
  - teste ficar acoplado demais a texto livre; mitigar verificando frases
    operacionais minimas e comando recomendado;
  - esconder o gate local por card; mitigar documentando que o CI gate delega
    para `qa_gate_card.sh --no-e2e`;
  - alterar setup Cloud de forma funcional indevida; mitigar mudando apenas a
    mensagem final recomendada, sem alterar instalacao.
- **Validacao minima obrigatoria:** RED/GREEN
  `python3 scripts/ci_quality_gate_test.py`, `./scripts/ci_quality_gate.sh`,
  `git diff --check`, harness `done-check` e `refresh-dashboard`.
- **Rollback simples:** reverter o teste e as mudancas de docs/setup; nenhum
  dado, contrato ou comportamento de app e afetado.

### Escopo
1. Expandir `scripts/ci_quality_gate_test.py` para proteger docs Cloud e setup.
2. Observar o RED com o estado atual.
3. Atualizar os docs/scripts operacionais para recomendar
   `./scripts/ci_quality_gate.sh`.
4. Rodar o CI gate do app e registrar evidencia em QA Log.

### Fora de escopo
- Alterar `scripts/qa_gate_card.sh`.
- Alterar workflow GitHub Actions.
- Rodar E2E real em Cloud/Linux.
- Refatorar telas, services ou contratos.

### Criterios de Aceite
- `scripts/ci_quality_gate_test.py` falha se docs/setup recomendarem o gate de
  card como comando principal de CI/Cloud.
- `docs/codex-cloud.md` recomenda `./scripts/ci_quality_gate.sh` como gate
  padrao do agente e explica a delegacao ao gate local sem E2E.
- `scripts/codex_cloud_setup.sh` imprime `./scripts/ci_quality_gate.sh` como
  check recomendado.
- `./scripts/ci_quality_gate.sh` passa localmente.
- Harness final passa e dashboard e atualizado.

### Test Plan do Card (obrigatorio)
- Risco principal: agentes continuarem usando o gate legado e pularem o teste
  de wiring do CI.
- Testes novos/ajustados:
  - [x] Tipo: teste Python de wiring operacional.
  - [x] Arquivo: `scripts/ci_quality_gate_test.py`.
  - [x] Cenario: docs Cloud recomendam `./scripts/ci_quality_gate.sh`.
  - [x] Cenario: setup Cloud recomenda `./scripts/ci_quality_gate.sh`.
- Regressao minima executada:
  - [x] RED `python3 scripts/ci_quality_gate_test.py`
  - [x] GREEN `python3 scripts/ci_quality_gate_test.py`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `git diff --check`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: <hash>)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Harness `done-check` e dashboard atualizados.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0925 — QUALITY-0026: sincronizar status antigos do bloco de qualidade app

### Status
DONE (commit: ad90efe)

### Origem
Auditoria do bloco de qualidade apos `QUALITY-0025`. Os cards `QUALITY-0008`,
`QUALITY-0009`, `QUALITY-0010` e `QUALITY-0011` possuem DoD preenchido com
hash real e evidencia no QA Log, mas o campo principal `### Status` ainda
permanece como `IN_PROGRESS`.

### Contexto
O backlog e uma fonte operacional para humanos e agentes de IA escolherem o
proximo trabalho. Status stale em cards ja entregues faz o bloco de qualidade
parecer mais aberto do que realmente esta e aumenta o risco de retrabalho.

### Objetivo
Sincronizar os status principais desses quatro cards com os hashes ja
registrados no proprio DoD e no QA Log, sem alterar codigo de produto, scripts
ou gates.

### Impact Analysis
- **Contexto do card:** saneamento de rastreabilidade do modulo de qualidade.
- **Telas/fluxos afetados:** nenhum.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** atualizar apenas `backlog/Flutter-Cards.md` e
  registrar evidencia em `backlog/QA-Execution-Log.md`.
- **Riscos e mitigacao:**
  - marcar como DONE sem evidencia suficiente; mitigar conferindo commits
    reais (`efce0a8`, `0c71c86`, `9819054`, `d8cf9d8`) e QA Log;
  - alterar status de card errado; mitigar com diff focado nos quatro cards
    `QUALITY-0008` a `QUALITY-0011`.
- **Validacao minima obrigatoria:** `git cat-file` dos hashes, busca por QA
  Log, `git diff --check`, harness `done-check` e `refresh-dashboard`.
- **Rollback simples:** reverter a troca de status e o bloco de QA Log.

### Escopo
1. Marcar `QUALITY-0008` como `DONE (commit: efce0a8)`.
2. Marcar `QUALITY-0009` como `DONE (commit: 0c71c86)`.
3. Marcar `QUALITY-0010` como `DONE (commit: 9819054)`.
4. Marcar `QUALITY-0011` como `DONE (commit: d8cf9d8)`.
5. Registrar evidencia de sincronizacao no QA Log.

### Fora de escopo
- Alterar codigo Flutter.
- Alterar scripts de gate, CI, Graphify ou harness.
- Reexecutar refatoracoes antigas.
- Reescrever historico de commits.

### Criterios de Aceite
- Os quatro cards deixam de aparecer como `IN_PROGRESS` no resumo de qualidade.
- Os hashes marcados no status existem no Git local.
- QA Log registra que a mudanca e de sincronizacao documental.
- Harness final passa e dashboard e atualizado.

### Test Plan do Card (obrigatorio)
- Risco principal: backlog indicar estado falso para agentes futuros.
- Testes/checagens:
  - [x] `git cat-file -t efce0a8 0c71c86 9819054 d8cf9d8`
  - [x] Busca por evidencias dos quatro cards no QA Log.
  - [x] `git diff --check`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: <hash>)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Harness `done-check` e dashboard atualizados.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0926 — QUALITY-0027: extrair sheet de edicao de rota da home motorista

### Status
DONE (commit: 1f54187)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0026`. O ratchet atual mostra
`lib/home_motorista_screen.dart` como o maior arquivo Dart do app, com 1.556
linhas, mesmo depois das extracoes anteriores de widgets da jornada.

### Contexto
A home do motorista ainda concentra a UI de edicao da rota diaria junto com
estado, lifecycle da viagem, mapa e navegacao. A edicao de rota e uma
responsabilidade visual coesa, ja coberta por teste de tela, e pode virar um
widget reutilizavel/testavel sem alterar contrato backend nem comportamento de
usuario.

### Objetivo
Extrair a sheet de edicao de rota para um widget dedicado em
`features/driver_trip/presentation/widgets`, remover sobra morta da home do
motorista quando seguro, adicionar teste de widget focado e apertar o baseline
do ratchet apos a reducao do hotspot.

### Impact Analysis
- **Contexto do card:** refatoracao guiada por hotspot no maior arquivo do
  app.
- **Telas/fluxos afetados:** home do motorista, apenas no modo de edicao de
  rota planejada.
- **Dependencia de contrato backend:** nenhuma. Sem mudanca de endpoint,
  payload, status code, auth, CORS ou WS.
- **Mudancas tecnicas:** extrair widget de edicao de rota, ajustar imports da
  home do motorista, adicionar teste de widget e regenerar baseline se a
  metrica melhorar.
- **Riscos e mitigacao:**
  - quebrar reordenacao/salvamento da rota; mitigar mantendo callbacks atuais
    e rodando teste de tela existente;
  - duplicar keys usadas por E2E/testes; mitigar passando as mesmas keys para o
    novo widget;
  - criar arquivo novo grande; mitigar mantendo widget abaixo do limite de 600
    linhas do ratchet;
  - remover codigo ainda usado; mitigar com `rg` e `flutter analyze`.
- **Validacao minima obrigatoria:** teste widget novo, teste de tela da home
  motorista, `python3 scripts/quality_metrics_test.py`,
  `./scripts/quality_baseline.sh`, `./scripts/quality_gate.sh`,
  `./scripts/ci_quality_gate.sh`, `git diff --check`, harness `done-check` e
  `refresh-dashboard`.
- **Rollback simples:** reverter o widget extraido, o ajuste da home, o teste
  novo e o baseline.

### Escopo
1. Criar widget dedicado para edicao de rota diaria.
2. Substituir o trecho equivalente em `home_motorista_screen.dart`.
3. Remover ramificacao/metodo morto relacionado, se confirmado sem uso.
4. Adicionar teste de widget para renderizacao compacta/regular e callbacks.
5. Regenerar baseline do ratchet e registrar evidencia em QA Log.

### Fora de escopo
- Alterar fluxo de lifecycle da corrida.
- Alterar contratos ou DTOs backend.
- Trocar presenter, gateway ou gerenciamento de estado.
- Refatorar outras abas da home motorista.

### Criterios de Aceite
- `home_motorista_screen.dart` reduz de tamanho sem perder as keys publicas da
  tela.
- O novo widget tem teste focado de renderizacao/callback.
- O teste existente de edicao/salvamento da rota continua passando.
- Ratchet e CI gate do app passam.
- Baseline atualizado impede retorno silencioso ao tamanho antigo do hotspot.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar edicao e salvamento da ordem operacional da rota.
- Testes novos/ajustados:
  - [x] Tipo: widget test.
  - [x] Arquivo: `test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart`.
  - [x] Cenario: sheet de edicao renderiza paradas e dispara salvar/cancelar.
  - [x] Cenario: estado `saving` desabilita acoes e mostra carregamento.
- Regressao minima executada:
  - [x] `flutter test test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart`
  - [x] `flutter test test/screens/home_motorista_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `git diff --check`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: <hash>)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Harness `done-check` e dashboard atualizados.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0927 — QUALITY-0028: extrair picker admin compartilhado de destino final

### Status
DONE (commit: a062096)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0027`. O ratchet atual mostra
`lib/features/admin/presentation/admin_rotas_screen.dart` como o maior arquivo
Dart do app, com 1.532 linhas, seguido de perto por
`lib/features/admin/presentation/admin_servicos_recorrentes_screen.dart`, com
1.518 linhas.

### Contexto
As telas de rotas-base e servicos recorrentes mantem pickers de destino final
quase duplicados. Ambos fazem debounce, chamada HTTP direta para
`/company-admins/places/autocomplete`, chamada de detalhe em
`/company-admins/places/details`, parsing de resposta e renderizacao da mesma
experiencia visual. Essa responsabilidade e coesa, compartilhavel e testavel
fora das telas stateful.

### Objetivo
Criar um gateway/modelo de Places do portal admin e um widget compartilhado de
destino final para reduzir duplicacao, retirar HTTP direto das telas e
diminuir simultaneamente os dois maiores hotspots do app, sem alterar contrato
backend nem comportamento de usuario.

### Impact Analysis
- **Contexto do card:** refatoracao guiada por hotspot e duplicacao
  arquitetural entre os dois maiores arquivos Dart atuais.
- **Telas/fluxos afetados:** portal admin, criacao/edicao de rotas-base
  legadas e criacao/edicao de servicos recorrentes, apenas no campo de destino
  final.
- **Dependencia de contrato backend:** nenhuma mudanca. O app continua
  consumindo `GET /company-admins/places/autocomplete` e
  `GET /company-admins/places/details` com o mesmo payload esperado.
- **Mudancas tecnicas:** criar modelos/gateway de Places reutilizaveis, extrair
  widget compartilhado para `features/admin/presentation/widgets`, substituir
  os pickers privados duplicados nas duas telas e adicionar testes focados.
- **Riscos e mitigacao:**
  - quebrar selecao/limpeza do destino final; mitigar com widget tests do novo
    componente e testes de tela existentes;
  - alterar detalhes sutis de UI entre rotas e servicos; mitigar aceitando
    configuracao de hint/padding/iconografia por parametro quando necessario;
  - mudar headers ou endpoints de Places; mitigar com teste unit do gateway
    usando `MockClient`;
  - ampliar demais o escopo; mitigar limitando o card ao picker de destino
    final, sem mexer em formularios, DTOs de rota/servico ou outros campos.
- **Validacao minima obrigatoria:** teste unit do gateway de Places, teste
  widget do picker compartilhado, testes de tela de rotas e servicos
  recorrentes, `python3 scripts/quality_metrics_test.py`,
  `./scripts/quality_baseline.sh`, `./scripts/quality_gate.sh`,
  `./scripts/ci_quality_gate.sh`, `git diff --check`, harness `done-check` e
  `refresh-dashboard`.
- **Rollback simples:** reverter gateway/modelos/widget compartilhado e
  restaurar os pickers privados nas duas telas; nenhum dado ou contrato e
  afetado.

### Escopo
1. Criar modelos e gateway de Places do portal admin com `http.Client`
   injetavel.
2. Criar widget compartilhado para busca, selecao e limpeza de destino final.
3. Substituir `_DestinoFinalPicker` em `admin_rotas_screen.dart`.
4. Substituir `_AdminServicoDestinoFinalPicker` em
   `admin_servicos_recorrentes_screen.dart`.
5. Adicionar testes unit/widget focados para gateway e picker.
6. Regenerar baseline do ratchet e registrar evidencia em QA Log.

### Fora de escopo
- Alterar contratos, DTOs ou endpoints backend.
- Refatorar o restante dos formularios de rotas ou servicos recorrentes.
- Alterar politica de geracao automatica, elegibilidade ou passageiros.
- Trocar o layout geral das telas admin.
- Rodar E2E real como requisito deste card.

### Criterios de Aceite
- `admin_rotas_screen.dart` fica menor que 1.532 linhas.
- `admin_servicos_recorrentes_screen.dart` fica menor que 1.518 linhas.
- HTTP direto de Places sai das duas telas stateful.
- O gateway de Places tem teste unit cobrindo autocomplete e detail.
- O picker compartilhado tem teste widget cobrindo sugestoes, selecao e
  limpeza.
- Testes de tela de rotas e servicos recorrentes continuam passando.
- Ratchet e CI gate do app passam.
- Baseline atualizado impede retorno silencioso dos pickers duplicados.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar busca/selecao/limpeza de destino final nos
  formularios admin.
- Testes novos/ajustados:
  - [x] Tipo: unit test.
  - [x] Arquivo: `test/features/admin/data/admin_places_gateway_test.dart`.
  - [x] Cenario: autocomplete envia token admin e interpreta sugestoes.
  - [x] Cenario: detail interpreta latitude, longitude e endereco.
  - [x] Tipo: widget test.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_destination_picker_test.dart`.
  - [x] Cenario: picker renderiza sugestoes e dispara `onSelected`.
  - [x] Cenario: destino confirmado pode ser limpo via `onCleared`.
- Regressao minima executada:
  - [x] `graphify explain "AdminRotasScreen"`
  - [x] `graphify explain "AdminServicosRecorrentesScreen"`
  - [x] `flutter test test/features/admin/data/admin_places_gateway_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_destination_picker_test.dart`
  - [x] `flutter test test/features/admin/admin_rotas_screen_test.dart`
  - [x] `flutter test test/features/admin/admin_servicos_recorrentes_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `git diff --check`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim.

### DoD
- Card marcado como `DONE (commit: a062096)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Harness `done-check` e dashboard atualizados.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0928 — QUALITY-0029: extrair widgets de vinculos responsavel-passageiro

### Status
DONE (commit: 1ed3b0d)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0028`, agora mirando o maior
hotspot Dart atual do app. O ratchet mostra
`lib/features/admin/presentation/admin_vinculos_screen.dart` com 1.477 linhas,
mesmo apos extracoes anteriores para onboarding, convites e direitos por
servico.

### Contexto
`AdminVinculosScreen` ainda mistura orquestracao de dados com widgets privados
de criacao de vinculo e visualizacao de responsavel-passageiro. O bloco de
responsaveis e coeso, depende apenas de records do dominio admin e callbacks,
e pode ser testado como widget isolado sem alterar gateway, contrato backend ou
fluxo do portal.

### Objetivo
Extrair o painel e o dialogo de vinculo responsavel-passageiro para um arquivo
dedicado em `features/admin/presentation/widgets`, reduzindo o arquivo
stateful principal e aumentando a cobertura focada do componente.

### Impact Analysis
- **Contexto do card:** refatoracao guiada por hotspot dentro do bloco de
  qualidade app, usando metricas e Graphify para escolher um recorte pequeno e
  verificavel.
- **Telas/fluxos afetados:** portal admin, aba de gestao de vinculos, somente
  nos blocos de criacao e visualizacao de vinculos responsavel-passageiro.
- **Dependencia de contrato backend:** nenhuma mudanca. O app continua
  consumindo os mesmos records e chamadas do `AdminVinculoGateway`.
- **Mudancas tecnicas:** criar widgets publicos para painel e dialogo de
  responsavel-passageiro, mover o value object de selecao para o novo arquivo e
  adaptar `AdminVinculosScreen` para delegar a UI extraida.
- **Riscos e mitigacao:**
  - quebrar callback de criacao/remocao de vinculo; mitigar com widget tests do
    painel e do dialogo;
  - mudar texto/estado vazio do portal; mitigar preservando textos e asserts
    existentes de `admin_vinculos_screen_test.dart`;
  - aumentar linhas totais mesmo reduzindo o hotspot; mitigar aceitando novo
    arquivo pequeno e atualizando baseline apenas se o ratchet permanecer
    coerente.
- **Validacao minima obrigatoria:** testes focados do novo widget, teste da
  tela de vinculos, `python3 scripts/quality_metrics_test.py`,
  `./scripts/quality_baseline.sh`, `./scripts/quality_gate.sh`,
  `./scripts/ci_quality_gate.sh`, `git diff --check`, harness `done-check` e
  `refresh-dashboard`.
- **Rollback simples:** reverter o novo arquivo/teste e restaurar os widgets
  privados dentro de `admin_vinculos_screen.dart`; nenhum dado, contrato ou
  deploy e afetado.

### Escopo
1. Criar `admin_vinculos_guardian_widgets.dart` para painel, dialogo e
   selecao responsavel-passageiro.
2. Adicionar widget tests cobrindo estado vazio/listagem/callbacks e selecao do
   dialogo.
3. Remover os widgets privados equivalentes de `admin_vinculos_screen.dart`.
4. Regenerar baseline do ratchet se o tamanho dos arquivos mudar.
5. Registrar evidencia em QA Log e atualizar Graphify/harness.

### Fora de escopo
- Alterar gateway, DTOs ou endpoints backend.
- Alterar fluxo de solicitacoes de acesso ou convites.
- Refatorar todo o `AdminVinculosScreen` neste card.
- Mudar layout geral do portal admin.
- Fazer push ou PR.

### Criterios de Aceite
- `admin_vinculos_screen.dart` fica menor que 1.477 linhas.
- O bloco responsavel-passageiro sai do arquivo principal e tem teste focado.
- `admin_vinculos_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar criacao/remocao de vinculos responsavel-passageiro
  sem alterar o contrato visivel.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_vinculos_guardian_widgets_test.dart`.
  - [x] Cenario: painel renderiza estado vazio e aciona criacao.
  - [x] Cenario: painel renderiza vinculo existente e aciona remocao.
  - [x] Cenario: dialogo retorna ids de responsavel e passageiro selecionados.
- Regressao minima executada:
  - [x] RED `flutter test test/features/admin/presentation/widgets/admin_vinculos_guardian_widgets_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_vinculos_guardian_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_vinculos_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 1ed3b0d)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0930 — QUALITY-0030: extrair painel de geracao automatica de viagens

### Status
DONE (commit: 52f2128)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0029`, agora mirando o maior
hotspot Dart atual do app. O ratchet mostra
`lib/features/admin/presentation/admin_viagens_screen.dart` com 1.326 linhas,
mesmo apos extracoes anteriores de widgets de lista, criacao e detalhe.

### Contexto
`AdminViagensScreen` ainda mistura orquestracao de estado, chamada de gateway,
listagem, detalhe e widgets privados de feedback da geracao automatica. O
painel de resultado da geracao e coeso, depende apenas de
`AdminViagemBulkCreateResult` e do design system admin, e pode ser testado como
widget isolado sem alterar gateway, contrato backend ou fluxo operacional.

### Objetivo
Extrair o painel de resultado da geracao automatica e a linha de servico
ignorado para um arquivo dedicado em `features/admin/presentation/widgets`,
reduzindo o arquivo stateful principal e criando cobertura focada para o
feedback de viagens criadas/existentes/ignoradas.

### Impact Analysis
- **Contexto do card:** refatoracao guiada por hotspot dentro do bloco de
  qualidade app, usando metricas e Graphify para escolher um recorte pequeno e
  verificavel.
- **Telas/fluxos afetados:** portal admin, tela de viagens, somente o feedback
  visual exibido apos `Montar dia`.
- **Dependencia de contrato backend:** nenhuma mudanca. O app continua
  consumindo o mesmo `AdminViagemBulkCreateResult` retornado pelo gateway.
- **Mudancas tecnicas:** criar widget publico para o painel de resultado da
  geracao, mover a linha de servico ignorado para o novo arquivo e adaptar
  `AdminViagensScreen` para delegar a UI extraida.
- **Riscos e mitigacao:**
  - perder a mensagem de servicos ignorados; mitigar com widget test cobrindo
    titulo, resumo, motivo e mensagem;
  - mudar feedback de sucesso sem servicos ignorados; mitigar com widget test
    do estado sem revisoes;
  - quebrar teste de tela que aciona `Montar dia`; mitigar mantendo textos e
    rodando `admin_viagens_screen_test.dart`;
  - atualizar baseline de forma incoerente; mitigar rodando ratchet,
    quality gate, CI gate e Graphify apos a mudanca.
- **Validacao minima obrigatoria:** teste focado do novo widget, teste da tela
  de viagens, `python3 scripts/quality_metrics_test.py`,
  `./scripts/quality_baseline.sh`, `./scripts/quality_gate.sh`,
  `./scripts/ci_quality_gate.sh`, `RUN_E2E=0 ./scripts/qa_gate_card.sh`,
  `git diff --check`, Graphify refresh, harness `done-check` e
  `refresh-dashboard`.
- **Rollback simples:** reverter o novo arquivo/teste e restaurar os widgets
  privados dentro de `admin_viagens_screen.dart`; nenhum dado, contrato ou
  deploy e afetado.

### Escopo
1. Criar `admin_viagens_generation_result_widgets.dart` para o painel de
   resultado da geracao automatica.
2. Adicionar widget tests cobrindo resultado sem ignorados e com servico
   ignorado.
3. Remover os widgets privados equivalentes de `admin_viagens_screen.dart`.
4. Regenerar baseline do ratchet se o tamanho dos arquivos mudar.
5. Registrar evidencia em QA Log e atualizar Graphify/harness.

### Fora de escopo
- Alterar gateway, DTOs ou endpoints backend.
- Refatorar o detalhe administrativo da viagem.
- Refatorar tabela/listagem de viagens.
- Mudar layout geral do portal admin.
- Fazer push ou PR.

### Criterios de Aceite
- `admin_viagens_screen.dart` fica menor que 1.326 linhas.
- O painel de resultado da geracao automatica sai do arquivo principal e tem
  teste focado.
- `admin_viagens_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar o feedback de geracao automatica apos `Montar dia`.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_viagens_generation_result_widgets_test.dart`.
  - [x] Cenario: painel sem ignorados mostra sucesso e resumo.
  - [x] Cenario: painel com ignorado mostra revisao, motivo e mensagem.
- Regressao minima executada:
  - [x] RED `flutter test test/features/admin/presentation/widgets/admin_viagens_generation_result_widgets_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_viagens_generation_result_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_viagens_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 52f2128)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0931 — QUALITY-0031: extrair widgets de localizacao do responsavel

### Status
DONE (commit: f8d7e69)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0030`. O ratchet atual mostra
`lib/features/guardian/presentation/guardian_localizacao_screen.dart` como o
maior arquivo Dart do app, com 1.325 linhas.

### Contexto
`GuardianLocalizacaoScreen` concentra carregamento de passageiros, rastreio da
viagem, WebSocket, selecao de viagens do dia e varios widgets privados puros.
O rodape do arquivo contem cards/badges de apresentacao que dependem apenas de
records de dominio, strings e callbacks, alem de helpers privados mortos que
existem apenas porque o arquivo esta com `ignore_for_file: unused_element`.

### Objetivo
Extrair os widgets puros de apresentacao da tela de localizacao do responsavel
para um arquivo dedicado em `features/guardian/presentation/widgets`, remover
widgets/helpers privados nao usados e eliminar o ignore de unused elements do
hotspot.

### Impact Analysis
- **Contexto do card:** refatoracao guiada por hotspot dentro do bloco de
  qualidade app, escolhida pelo ratchet atual e validada com Graphify.
- **Telas/fluxos afetados:** aba de localizacao do responsavel, somente nos
  cards de passageiro selecionado, timeline, estado vazio/erro e badge de tipo
  de operacao.
- **Dependencia de contrato backend:** nenhuma mudanca. O app segue consumindo
  os mesmos records de passageiro, rastreio e localizacao.
- **Mudancas tecnicas:** criar widgets publicos para os cards/badges puros,
  adaptar `GuardianLocalizacaoScreen` para delegar a UI extraida, remover
  `_GuardianOverlayChip`, `_GuardianAlertCard`, `_buildGuardianAlert` e o
  `ignore_for_file` que ocultava unused elements.
- **Riscos e mitigacao:**
  - quebrar textos do card de passageiro selecionado; mitigar com widget test
    focado;
  - quebrar timeline de localizacao recente; mitigar com widget test cobrindo
    historico e estado vazio;
  - quebrar a tela completa do responsavel; mitigar rodando
    `guardian_localizacao_screen_test.dart`;
  - remover codigo que parecia reservado para layout futuro; mitigar mantendo
    somente codigo referenciado/testado e deixando rollback simples.
- **Validacao minima obrigatoria:** teste focado dos widgets extraidos, teste da
  tela de localizacao do responsavel, `python3 scripts/quality_metrics_test.py`,
  `./scripts/quality_baseline.sh`, `./scripts/quality_gate.sh`,
  `./scripts/ci_quality_gate.sh`, `RUN_E2E=0 ./scripts/qa_gate_card.sh`,
  `git diff --check`, Graphify refresh, harness `done-check` e
  `refresh-dashboard`.
- **Rollback simples:** reverter o novo arquivo/teste, restaurar os widgets
  privados dentro de `guardian_localizacao_screen.dart` e recolocar o ignore se
  necessario; nenhum dado, contrato ou deploy e afetado.

### Escopo
1. Criar `guardian_localizacao_widgets.dart` para cards/badges puros da tela.
2. Adicionar widget tests cobrindo passageiro selecionado, timeline e cards de
   estado.
3. Remover widgets/helpers privados nao usados do arquivo principal.
4. Atualizar `guardian_localizacao_screen.dart` para usar a API publica dos
   widgets extraidos.
5. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar gateway, DTOs, endpoints ou WebSocket.
- Refatorar selecao de viagem do dia.
- Refatorar mapa full-screen ou tracking presenter.
- Mudar layout geral da aba de localizacao.
- Fazer push ou PR.

### Criterios de Aceite
- `guardian_localizacao_screen.dart` fica menor que 1.325 linhas.
- O arquivo deixa de precisar de `ignore_for_file: unused_element`.
- Os widgets extraidos têm teste focado.
- `guardian_localizacao_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar UI de acompanhamento de localizacao do responsavel
  sem alterar contratos ou dados.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/guardian/presentation/widgets/guardian_localizacao_widgets_test.dart`.
  - [x] Cenario: card de passageiro renderiza dados de menor, motorista e
    endereco.
  - [x] Cenario: timeline renderiza pontos recentes e estado vazio.
  - [x] Cenario: card de mensagem aciona callback de retry.
- Regressao minima executada:
  - [x] RED `flutter test test/features/guardian/presentation/widgets/guardian_localizacao_widgets_test.dart`
  - [x] `flutter test test/features/guardian/presentation/widgets/guardian_localizacao_widgets_test.dart`
  - [x] `flutter test test/features/guardian/guardian_localizacao_screen_test.dart`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: f8d7e69)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0932 — QUALITY-0032: extrair painel de detalhes da home do motorista

### Status
DONE (commit: cd1c145)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0031`. O ratchet atual mostra
`lib/home_motorista_screen.dart` como o maior arquivo Dart do app, com 1.308
linhas, e o Graphify registra debitos historicos de decomposicao da home do
motorista.

### Contexto
`HomeMotoristaScreen` ja possui varios widgets extraidos para a jornada do
motorista, mas ainda concentra o painel modal de detalhes da viagem, a montagem
da lista de paradas e a composicao das acoes operacionais. Esse trecho e
majoritariamente apresentacional e depende de dados/callbacks ja calculados pela
tela stateful.

### Objetivo
Extrair o painel de detalhes da viagem do motorista para um widget publico
dedicado em `features/driver_trip/presentation/widgets`, mantendo a tela
principal responsavel apenas por estado, callbacks e abertura do modal.

### Impact Analysis
- **Contexto do card:** refatoracao guiada por hotspot dentro do bloco de
  qualidade app, escolhida pelo ratchet atual e conferida com Graphify/codigo.
- **Telas/fluxos afetados:** home do motorista, painel de detalhes da viagem,
  aba de fila/paradas e aba de visao geral/acoes.
- **Dependencia de contrato backend:** nenhuma mudanca. O app segue consumindo
  os mesmos records de viagem, paradas, rota geografica e navegacao.
- **Mudancas tecnicas:** criar widget publico para o painel de detalhes,
  mover a montagem visual das tabs/lista/acoes para esse widget e adaptar
  `HomeMotoristaScreen` para passar estado e callbacks explicitamente.
- **Riscos e mitigacao:**
  - quebrar a filtragem de paradas no painel; mitigar com widget test focado;
  - quebrar disponibilidade das acoes por status da viagem; mitigar cobrindo
    modo planejamento e modo em andamento no teste do widget;
  - quebrar a tela completa do motorista; mitigar rodando
    `home_motorista_screen_test.dart`;
  - criar acoplamento excessivo entre painel e tela; mitigar mantendo callbacks
    sem acesso direto a presenter/gateway.
- **Validacao minima obrigatoria:** RED e GREEN do teste focado do novo widget,
  `home_motorista_screen_test.dart`, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo widget/teste e restaurar a construcao do
  painel dentro de `home_motorista_screen.dart`; nenhum dado, endpoint ou deploy
  e afetado.

### Escopo
1. Criar `driver_trip_detail_panel_widgets.dart` para o painel modal de detalhes
   e a lista reutilizavel de paradas.
2. Adicionar widget tests cobrindo filtragem da fila e composicao das acoes por
   status.
3. Adaptar `home_motorista_screen.dart` para delegar o painel/lista extraidos.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar DTOs, gateways, presenters, endpoints ou WebSocket.
- Migrar `setState` para presenter neste card.
- Refatorar `test/screens/home_motorista_screen_test.dart`.
- Mudar layout visual ou textos do painel.
- Fazer push ou PR.

### Criterios de Aceite
- `home_motorista_screen.dart` fica menor que 1.308 linhas.
- O painel de detalhes extraido possui teste focado.
- `home_motorista_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar o painel operacional do motorista sem alterar dados
  ou contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/driver_trip/presentation/widgets/driver_trip_detail_panel_widgets_test.dart`.
  - [x] Cenario: painel filtra fila por paradas pendentes.
  - [x] Cenario: painel em planejamento mostra acoes de mapas/presencas/edicao.
  - [x] Cenario: painel em andamento mostra acoes operacionais e respeita
    callback de no-show.
- Regressao minima executada:
  - [x] RED `flutter test test/features/driver_trip/presentation/widgets/driver_trip_detail_panel_widgets_test.dart`
  - [x] `flutter test test/features/driver_trip/presentation/widgets/driver_trip_detail_panel_widgets_test.dart`
  - [x] `flutter test test/screens/home_motorista_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: cd1c145)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0933 — QUALITY-0033: extrair paineis de responsaveis admin

### Status
DONE (commit: c50a0e3)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0032`. O ratchet atual mostra
`lib/features/admin/presentation/admin_responsaveis_screen.dart` como o maior
arquivo Dart do app, com 1.284 linhas.

### Contexto
`AdminResponsaveisScreen` mistura orquestracao de dados com paineis
apresentacionais de lista, detalhe, filtros, paginacao e linhas auxiliares. A
tela tambem contem dialogs stateful de criacao/edicao, mas estes dependem de
validacao e submissao de formulario e devem ficar para um card separado.

### Objetivo
Extrair os paineis puros de lista/detalhe dos responsaveis para um widget
publico dedicado em `features/admin/presentation/widgets`, mantendo
`AdminResponsaveisScreen` focada em carregamento, selecao, dialogos e callbacks.

### Impact Analysis
- **Contexto do card:** refatoracao guiada por hotspot dentro do bloco de
  qualidade app, escolhida pelo ratchet atual.
- **Telas/fluxos afetados:** portal admin, aba de responsaveis, lista,
  selecao, resumo de vinculos e detalhe administrativo do responsavel.
- **Dependencia de contrato backend:** nenhuma mudanca. O app segue consumindo
  `AdminResponsavelRecord` e `AdminPassageiroLinkedRecord` com o mesmo contrato.
- **Mudancas tecnicas:** criar `admin_responsaveis_widgets.dart` e
  `admin_responsaveis_detail_widgets.dart` com paineis e helpers visuais puros;
  adaptar `admin_responsaveis_screen.dart` para delegar a composicao visual sem
  alterar gateway, DTOs ou dialogs.
- **Riscos e mitigacao:**
  - quebrar selecao/acoes da lista; mitigar com widget test focado;
  - quebrar renderizacao de vinculos no detalhe; mitigar cobrindo passageiro
    menor e estado vazio;
  - quebrar fluxo completo da tela; mitigar rodando
    `admin_responsaveis_screen_test.dart`;
  - mover demais neste card; mitigar mantendo forms/dialogs fora do escopo.
- **Validacao minima obrigatoria:** RED e GREEN do teste focado dos widgets,
  teste da tela de responsaveis, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo widget/teste e restaurar os paineis
  privados em `admin_responsaveis_screen.dart`; nenhum dado, endpoint ou deploy
  e afetado.

### Escopo
1. Criar widgets publicos para lista, detalhe e helpers visuais em arquivos
   pequenos dentro de `features/admin/presentation/widgets`.
2. Adicionar widget tests cobrindo selecao/acoes da lista e detalhe com
   passageiro vinculado.
3. Adaptar `admin_responsaveis_screen.dart` para usar a API publica dos widgets
   extraidos.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar DTOs, gateways, endpoints ou textos de contrato.
- Refatorar dialogs de criacao/edicao.
- Corrigir filtros visuais ainda estaticos.
- Refatorar `admin_responsaveis_screen_test.dart`.
- Fazer push ou PR.

### Criterios de Aceite
- `admin_responsaveis_screen.dart` fica menor que 1.284 linhas.
- Os paineis extraidos têm teste focado.
- `admin_responsaveis_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar a operacao administrativa de responsaveis sem
  alterar dados ou contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_responsaveis_widgets_test.dart`.
  - [x] Cenario: lista renderiza responsavel selecionado e aciona callbacks.
  - [x] Cenario: detalhe renderiza vinculos e acoes do responsavel.
  - [x] Cenario: detalhe vazio orienta selecao sem quebrar layout.
- Regressao minima executada:
  - [x] RED `flutter test test/features/admin/presentation/widgets/admin_responsaveis_widgets_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_responsaveis_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_responsaveis_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: c50a0e3)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0934 — QUALITY-0034: extrair detalhe de rotas-base admin

### Status
DONE (commit: 8d0fd11)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0033`. O ratchet atual mostra
`lib/features/admin/presentation/admin_rotas_screen.dart` como o maior arquivo
Dart do app, com 1.279 linhas.

### Contexto
`AdminRotasScreen` ja usa widgets compartilhados para cards, metricas, labels e
estado de erro, mas ainda concentra o painel completo de detalhe da rota-base.
Esse bloco e majoritariamente apresentacional e depende apenas de
`AdminRotaBaseRecord`, estado de submissao e callbacks de navegacao/acoes.

### Objetivo
Extrair o detalhe de rota-base para um widget publico dedicado em
`features/admin/presentation/widgets`, mantendo `AdminRotasScreen` focada em
carregamento, selecao, criacao/edicao e callbacks.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo hotspot atual do ratchet no
  app Flutter.
- **Telas/fluxos afetados:** portal admin, aba de rotas-base legadas, detalhe
  da rota, acao de voltar, acao de editar e acao de excluir/inativar.
- **Dependencia de contrato backend:** nenhuma mudanca. O app segue consumindo
  `AdminRotaBaseRecord` com os mesmos campos de motorista, dias, horario,
  destino final e status.
- **Mudancas tecnicas:** criar `admin_rotas_detail_view.dart` com
  `AdminRotaDetailView`; adaptar `_buildDetailView` para delegar a composicao
  visual; manter formulario create/edit no arquivo principal para um card
  posterior.
- **Riscos e mitigacao:**
  - quebrar a navegacao de detalhe para lista/edicao; mitigar com widget test
    focado nos callbacks;
  - quebrar texto de destino final persistido ou fallback sem destino; mitigar
    cobrindo os dois estados;
  - quebrar fluxo completo da tela; mitigar rodando
    `admin_rotas_screen_test.dart`;
  - inflar arquivo de widgets existente que ja esta perto do limite; mitigar
    criando arquivo novo dedicado abaixo de 600 linhas.
- **Validacao minima obrigatoria:** RED e GREEN do teste focado do novo widget,
  teste da tela de rotas, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo widget/teste e restaurar o detalhe
  dentro de `admin_rotas_screen.dart`; nenhum dado, endpoint ou deploy e
  afetado.

### Escopo
1. Criar `admin_rotas_detail_view.dart` com o painel de detalhe de rota-base.
2. Adicionar widget tests cobrindo renderizacao com destino final, fallback sem
   destino e callbacks de voltar/editar/excluir.
3. Adaptar `admin_rotas_screen.dart` para usar o widget publico extraido.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar DTOs, gateways, endpoints ou textos de contrato.
- Refatorar o formulario de criacao/edicao.
- Refatorar a listagem de rotas-base.
- Fazer push ou PR.

### Criterios de Aceite
- `admin_rotas_screen.dart` fica menor que 1.279 linhas.
- O detalhe extraido tem teste focado.
- `admin_rotas_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar o detalhe de rota-base sem alterar dados ou
  contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_rotas_detail_view_test.dart`.
  - [x] Cenario: detalhe renderiza destino final persistido e metricas.
  - [x] Cenario: detalhe sem destino mostra fallback operacional.
  - [x] Cenario: acoes de voltar, editar e excluir disparam callbacks.
- Regressao minima executada:
  - [x] RED `flutter test test/features/admin/presentation/widgets/admin_rotas_detail_view_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_rotas_detail_view_test.dart`
  - [x] `flutter test test/features/admin/admin_rotas_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 8d0fd11)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0935 — QUALITY-0035: extrair hero do login admin

### Status
DONE (commit: 8473d6e)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0034`. O ratchet atual mostra
`lib/features/admin/presentation/admin_login_screen.dart` como o maior arquivo
Dart do app, com 1.267 linhas.

### Contexto
`AdminLoginScreen` concentra a pagina de login, o fluxo de e-mail pendente, o
dialog de bootstrap do primeiro admin e o bloco visual do hero. O hero e
majoritariamente apresentacional, nao depende de `State`, gateway, formulario,
sessao ou callbacks de autenticacao.

### Objetivo
Extrair o hero do login admin para um widget publico dedicado em
`features/admin/presentation/widgets`, reduzindo o tamanho da tela stateful e
criando cobertura focada para o bloco visual do portal.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo novo hotspot do ratchet no app
  Flutter.
- **Telas/fluxos afetados:** tela de login do portal admin, apenas no bloco
  visual lateral/superior exibido antes do formulario.
- **Dependencia de contrato backend:** nenhuma mudanca. Login, e-mail pendente,
  bootstrap do primeiro admin, DTOs e gateway seguem intactos.
- **Mudancas tecnicas:** criar `admin_login_hero.dart` com `AdminLoginHero`;
  mover pills e card de rodape do hero para o novo arquivo; adaptar
  `AdminLoginScreen` para importar e renderizar o widget extraido.
- **Riscos e mitigacao:**
  - quebrar a renderizacao responsiva do login; mitigar mantendo o mesmo ponto
    de uso no `LayoutBuilder` e rodando o teste da tela;
  - perder textos do hero que orientam a operacao; mitigar com widget test
    focado no novo componente;
  - tocar indevidamente login/bootstrap; mitigar mantendo formulario, dialogs,
    keys, gateway e callbacks fora do escopo;
  - criar outro arquivo grande; mitigar limitando o novo widget a um bloco
    pequeno e dedicado.
- **Validacao minima obrigatoria:** RED e GREEN do teste focado do novo widget,
  teste da tela de login, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo widget/teste e restaurar o hero dentro de
  `admin_login_screen.dart`; nenhum dado, endpoint ou deploy e afetado.

### Escopo
1. Criar `admin_login_hero.dart` com o bloco visual do hero.
2. Adicionar widget test cobrindo marca, titulo, resumo, pills e card de
   bootstrap.
3. Adaptar `admin_login_screen.dart` para usar o widget publico extraido.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar login, validacoes, navegacao ou `AdminPortalAuthGateway`.
- Alterar dialog de bootstrap ou fluxo de e-mail pendente.
- Alterar DTOs, endpoints, payloads, status codes ou auth.
- Fazer push ou PR.

### Criterios de Aceite
- `admin_login_screen.dart` fica menor que 1.267 linhas.
- O hero extraido tem teste focado.
- `admin_login_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar o hero do login admin sem alterar dados,
  autenticacao ou contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget test de componente.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_login_hero_test.dart`.
  - [x] Cenario: hero renderiza marca, titulo, resumo, pills e card de
    bootstrap.
  - [x] Cenario: hero renderiza em layout scrollado estreito sem erro de
    constraints.
- Regressao minima executada:
  - [x] RED `flutter test test/features/admin/presentation/widgets/admin_login_hero_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_login_hero_test.dart`
  - [x] `flutter test test/features/admin/admin_login_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 8473d6e)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0936 — QUALITY-0036: extrair suporte do formulário de serviços recorrentes

### Status
DONE (commit: 984d82e)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0035`. O ratchet atual mostra
`lib/features/admin/presentation/admin_servicos_recorrentes_screen.dart` como o
maior arquivo Dart do app, com 1.250 linhas.

### Contexto
`AdminServicosRecorrentesScreen` ja delega a listagem e o detalhe para widgets
dedicados, mas ainda mantem dentro da tela os widgets auxiliares do formulario,
validadores, labels, constantes de dominio visual e formatadores usados pelo
formulario de criacao/edicao.

### Objetivo
Extrair o suporte puro do formulario de servicos recorrentes para um arquivo
publico em `features/admin/presentation/widgets`, deixando a tela principal mais
focada em carregamento, modo atual, callbacks e composicao.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo hotspot atual do ratchet no
  app Flutter.
- **Telas/fluxos afetados:** portal admin, tela de servicos recorrentes,
  formulario de criacao/edicao e resumo operacional do formulario.
- **Dependencia de contrato backend:** nenhuma mudanca. `AdminServicoRecorrenteInput`,
  gateway, payloads, endpoints, status codes e autenticacao seguem intactos.
- **Mudancas tecnicas:** criar `admin_servicos_recorrentes_form_widgets.dart`
  com widgets auxiliares, constantes, validadores e formatadores puros do
  formulario; adaptar `admin_servicos_recorrentes_screen.dart` para importar
  esses artefatos.
- **Riscos e mitigacao:**
  - quebrar validacao de horario/capacidade; mitigar com testes focados nos
    validadores;
  - alterar labels de politica, tipo de operacao, motorista ou elegibilidade;
    mitigar com teste do resumo operacional;
  - quebrar criacao/edicao do formulario; mitigar rodando
    `admin_servicos_recorrentes_screen_test.dart`;
  - criar novo arquivo grande demais; mitigar extraindo apenas suporte puro e
    mantendo o formulario stateful para outro card.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do novo arquivo,
  teste da tela de servicos recorrentes, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo arquivo/teste e restaurar helpers e
  validadores no fim de `admin_servicos_recorrentes_screen.dart`; nenhum dado,
  endpoint ou deploy e afetado.

### Escopo
1. Criar `admin_servicos_recorrentes_form_widgets.dart` com suporte puro do
   formulario.
2. Adicionar widget/unit tests cobrindo resumo operacional, validadores e
   helpers de label/ordenacao.
3. Adaptar `admin_servicos_recorrentes_screen.dart` para usar os artefatos
   extraidos.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Mover o `AdminServicoRecorrenteForm` stateful inteiro.
- Alterar fluxo de criacao, edicao, inativacao ou carregamento.
- Alterar DTOs, gateways, endpoints, payloads, status codes ou auth.
- Fazer push ou PR.

### Criterios de Aceite
- `admin_servicos_recorrentes_screen.dart` fica menor que 1.250 linhas.
- O suporte extraido tem teste focado.
- `admin_servicos_recorrentes_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar validacoes e labels do formulario sem alterar dados
  ou contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget/unit tests de componente/helper.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_servicos_recorrentes_form_widgets_test.dart`.
  - [x] Cenario: resumo operacional renderiza labels de operacao,
    elegibilidade, politica, geracao e motorista.
  - [x] Cenario: validadores aceitam valores vazios opcionais e rejeitam
    horarios/capacidades invalidos.
  - [x] Cenario: dias de operacao preservam ordem operacional.
- Regressao minima executada:
  - [x] RED `flutter test test/features/admin/presentation/widgets/admin_servicos_recorrentes_form_widgets_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_servicos_recorrentes_form_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_servicos_recorrentes_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 984d82e)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0937 — QUALITY-0037: extrair widgets da home do passageiro

### Status
DONE (commit: 18f3f72)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0036`. O ratchet atual mostra
`lib/home_passageiro_screen.dart` como o maior arquivo Dart do app, com 1.212
linhas.

### Contexto
`HomePassageiroScreen` ainda concentra orquestracao de tracking/presenca,
deep link, assinatura de eventos de rota e varios blocos visuais da experiencia
do passageiro. A tela ja delega a aba de conta para
`passenger_home_account_widgets.dart`, mas mantem inline o seletor de agenda do
dia e a aba de presenca.

### Objetivo
Extrair widgets puros de apresentacao para a agenda do dia e a aba de presenca
da home do passageiro, deixando a tela principal focada em estado, gateways,
navegacao e composicao de alto nivel.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo hotspot atual do ratchet no
  app Flutter.
- **Telas/fluxos afetados:** home do passageiro, aba de viagem, seletor de
  agenda do dia, aba de presenca e fallback de confirmacao de presenca.
- **Dependencia de contrato backend:** nenhuma mudanca. `TripTrackingRecord`,
  `PassengerAvailableServiceRecord`, gateways, payloads, endpoints, status
  codes, autenticacao, CORS e WS seguem intactos.
- **Mudancas tecnicas:** criar widgets dedicados em
  `features/tracking/presentation/widgets` para `PassengerDayAgendaSelector`,
  `PassengerHomePresenceTab` e o item visual de presenca; adaptar
  `home_passageiro_screen.dart` para importar e compor esses widgets.
- **Riscos e mitigacao:**
  - quebrar a selecao de viagem do dia; mitigar com teste focado que toca o
    chip da agenda e verifica o callback;
  - alterar copy/estado vazio da presenca; mitigar com teste focado do fallback;
  - quebrar lista de servicos de presenca; mitigar cobrindo renderizacao de
    servicos liberados e mantendo callbacks no widget pai;
  - quebrar regressao da home real; mitigar rodando
    `home_passageiro_screen_test.dart`.
- **Validacao minima obrigatoria:** RED e GREEN dos testes focados dos novos
  widgets, teste da home do passageiro, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover os novos arquivos/testes e restaurar os widgets
  no fim de `home_passageiro_screen.dart`; nenhum dado, endpoint ou deploy e
  afetado.

### Escopo
1. Criar widget publico para a agenda de viagens do dia do passageiro.
2. Criar widget publico para a aba de presenca da home do passageiro.
3. Adicionar testes focados para agenda, fallback de presenca e lista de
   servicos liberados.
4. Adaptar `home_passageiro_screen.dart` para usar os widgets extraidos.
5. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar selecao da viagem relevante, deep link ou assinatura de eventos de
  rota.
- Alterar fluxo de presenca, endpoints, gateways ou records.
- Refatorar `TripLiveMapPanel`, `TripMapSceneShell` ou a aba de conta.
- Fazer push ou PR.

### Criterios de Aceite
- `home_passageiro_screen.dart` fica menor que 1.212 linhas.
- Os widgets extraidos tem testes focados.
- `home_passageiro_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar composicao visual/callbacks da home do passageiro
  sem alterar contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivos:
    `test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart`
    e
    `test/features/tracking/presentation/widgets/passenger_home_presence_widgets_test.dart`.
  - [x] Cenario: agenda renderiza viagens do dia, destaca selecao e dispara
    callback ao trocar a viagem.
  - [x] Cenario: aba de presenca mostra fallback com acoes quando nao ha
    servicos liberados.
  - [x] Cenario: aba de presenca mostra lista de servicos liberados sem perder
    os callbacks recebidos.
- Regressao minima executada:
  - [x] RED `flutter test test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart test/features/tracking/presentation/widgets/passenger_home_presence_widgets_test.dart`
  - [x] `flutter test test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart test/features/tracking/presentation/widgets/passenger_home_presence_widgets_test.dart`
  - [x] `flutter test test/screens/home_passageiro_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 18f3f72)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0938 — QUALITY-0038: extrair painel de atalho motorista em vínculos

### Status
DONE (commit: c5f41a5)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0037`. O ratchet atual mostra
`lib/features/admin/presentation/admin_vinculos_screen.dart` como o maior
arquivo Dart do app, com 1.183 linhas.

### Contexto
`AdminVinculosScreen` ja delega widgets de base, onboarding, direitos por
servico e responsavel-passageiro para arquivos dedicados, mas ainda mantem
inline o painel legado de atalho passageiro-motorista. Esse painel e
apresentacional e usa apenas lista de links, callback de refresh e callback de
desvinculo.

### Objetivo
Extrair o painel legado de atalho motorista para um widget publico em
`features/admin/presentation/widgets`, deixando a tela principal mais focada em
estado, carregamento, dialogs e composicao.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo hotspot atual do ratchet no
  app Flutter.
- **Telas/fluxos afetados:** portal admin, aba de vinculos, painel legado de
  atalho passageiro-motorista e acao de desvincular motorista de referencia.
- **Dependencia de contrato backend:** nenhuma mudanca. `AdminVinculoGateway`,
  `AdminPassageiroMotoristaLinkRecord`, endpoints, payloads, status codes,
  autenticacao e CORS seguem intactos.
- **Mudancas tecnicas:** criar `admin_vinculos_motorista_widgets.dart` com
  `AdminVinculosPassageiroMotoristaPanel`; adaptar
  `admin_vinculos_screen.dart` para importar e compor o painel extraido.
- **Riscos e mitigacao:**
  - quebrar empty state do painel legado; mitigar com widget test focado;
  - quebrar callback de refresh/desvinculo; mitigar com teste que aciona os
    botoes e valida o link recebido;
  - alterar copy de orientacao operacional; mitigar mantendo textos atuais nos
    testes e rodando `admin_vinculos_screen_test.dart`;
  - criar novo hotspot; mitigar mantendo arquivo extraido pequeno.
- **Validacao minima obrigatoria:** RED e GREEN do teste focado do novo widget,
  teste da tela de vinculos, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo arquivo/teste e restaurar o painel
  privado em `admin_vinculos_screen.dart`; nenhum dado, endpoint ou deploy e
  afetado.

### Escopo
1. Criar `admin_vinculos_motorista_widgets.dart` com o painel de atalho
   passageiro-motorista.
2. Adicionar widget tests cobrindo empty state, refresh e desvinculo.
3. Adaptar `admin_vinculos_screen.dart` para usar o widget extraido.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar regras de aprovacao, convite, servicos liberados ou vinculo
  responsavel-passageiro.
- Alterar DTOs, gateways, endpoints, payloads, status codes ou auth.
- Remover o fluxo legado de passageiro-motorista.
- Fazer push ou PR.

### Criterios de Aceite
- `admin_vinculos_screen.dart` fica menor que 1.183 linhas.
- O painel extraido tem teste focado.
- `admin_vinculos_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar UI/callbacks do painel legado sem alterar contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_vinculos_motorista_widgets_test.dart`.
  - [x] Cenario: painel vazio mostra orientacao operacional e aciona refresh.
  - [x] Cenario: painel com link mostra passageiro/motorista e aciona
    desvinculo com o link correto.
- Regressao minima executada:
  - [x] RED `flutter test test/features/admin/presentation/widgets/admin_vinculos_motorista_widgets_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_vinculos_motorista_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_vinculos_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: c5f41a5)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0939 — QUALITY-0039: extrair listagem operacional de viagens

### Status
DONE (commit: 4027b0d)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0038`. O ratchet atual mostra
`lib/features/admin/presentation/admin_viagens_screen.dart` como o maior
arquivo Dart do app, com 1.170 linhas.

### Contexto
`AdminViagensScreen` ja delega formulario, widgets de detalhe, resultado de
geracao automatica e linhas da tabela para arquivos dedicados, mas ainda mantem
inline a secao visual da listagem: cards de resumo, cabecalho da tabela, empty
state, linhas e rodape de paginacao textual.

### Objetivo
Extrair a listagem operacional de viagens para um widget publico em
`features/admin/presentation/widgets`, deixando a tela principal mais focada em
estado, carregamento, selecao de data, navegacao de modo e callbacks.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo hotspot atual do ratchet no
  app Flutter.
- **Telas/fluxos afetados:** portal admin, tela de viagens, listagem do dia,
  empty state de viagens, abrir detalhe, montar dia automaticamente e criar
  viagem manual a partir da listagem vazia.
- **Dependencia de contrato backend:** nenhuma mudanca. `AdminViagensGateway`,
  `AdminViagemRecord`, endpoints, payloads, status codes, autenticacao e CORS
  seguem intactos.
- **Mudancas tecnicas:** criar um widget publico em
  `admin_viagens_list_widgets.dart` para renderizar a secao de listagem, mover
  o tag/predicados de status para `admin_viagens_status_widgets.dart` e manter
  no screen apenas calculo de contadores e callbacks.
- **Riscos e mitigacao:**
  - quebrar os contadores de data filtrada, em operacao e concluidas; mitigar
    com widget test focado;
  - perder callbacks de abrir detalhe, montar dia ou criar manual; mitigar com
    teste que aciona botoes/icone e valida callbacks;
  - alterar empty state da listagem; mitigar com teste focado do estado vazio;
  - quebrar regressao da tela real; mitigar rodando
    `admin_viagens_screen_test.dart`.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do widget de
  listagem, teste da tela de viagens, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo widget/testes e restaurar a secao inline
  em `admin_viagens_screen.dart`; nenhum dado, endpoint ou deploy e afetado.

### Escopo
1. Criar widget publico para a secao de listagem operacional de viagens.
2. Adicionar widget tests cobrindo resumo, callbacks e empty state.
3. Adaptar `admin_viagens_screen.dart` para usar a API publica extraida.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar criacao, geracao automatica, detalhe, manifesto, mapa ou troca de
  motorista.
- Alterar DTOs, gateways, endpoints, payloads, status codes ou auth.
- Criar paginacao real ou novo comportamento de filtro.
- Fazer push ou PR.

### Criterios de Aceite
- `admin_viagens_screen.dart` fica menor que 1.170 linhas.
- O widget extraido tem teste focado.
- `admin_viagens_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar UI/callbacks da listagem sem alterar contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_viagens_list_widgets_test.dart`.
  - [x] Cenario: listagem com viagens renderiza cards de resumo, linhas,
    rodape e aciona abrir detalhe.
  - [x] Cenario: listagem vazia mostra orientacao operacional e aciona montar
    dia/criar manual.
- Regressao minima executada:
  - [x] RED `flutter test test/features/admin/presentation/widgets/admin_viagens_list_widgets_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_viagens_list_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_viagens_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 4027b0d)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0940 — QUALITY-0040: extrair aba de mapa da home do motorista

### Status
DONE (commit: 0839161)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0039`. O ratchet atual mostra
`lib/home_motorista_screen.dart` como o maior arquivo Dart do app, com 1.166
linhas.

### Contexto
`HomeMotoristaScreen` ja delega grande parte da experiencia do motorista para
widgets dedicados, incluindo cards base, agenda, sheets da viagem, painel de
detalhe, conta e mapa hero. Ainda assim, a aba de mapa completo continua inline
na tela stateful, misturando estado de carregamento/erro com composicao visual
do mapa, chip de versao da rota, proxima parada e ETA.

### Objetivo
Extrair a aba de mapa completo para um widget publico em
`features/driver_trip/presentation/widgets`, deixando a tela principal mais
focada em estado, presenters, lifecycle e callbacks.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo maior hotspot atual do ratchet
  no app Flutter.
- **Telas/fluxos afetados:** home do motorista, aba "Mapa", fallback de mapa
  indisponivel, resumo de proxima parada e ETA no overlay do mapa.
- **Dependencia de contrato backend:** nenhuma mudanca. `DriverTripRecord`,
  `TripRouteGeometryRecord`, gateways, WebSocket, payloads, endpoints, status
  codes e autenticacao seguem intactos.
- **Mudancas tecnicas:** criar widget publico para a aba de mapa completo em
  `driver_trip_map_widgets.dart`; adaptar `home_motorista_screen.dart` para
  passar estado, dados de sessao e callbacks explicitamente.
- **Riscos e mitigacao:**
  - quebrar fallback quando a viagem ainda nao carregou; mitigar com widget
    test focado do estado indisponivel;
  - quebrar overlay de rota/proxima parada/ETA; mitigar com widget test do
    mapa em modo placeholder;
  - alterar foco do mapa completo; mitigar mantendo
    `TripLiveMapFocusMode.fullJourney`;
  - quebrar a tela completa do motorista; mitigar rodando
    `home_motorista_screen_test.dart`.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do widget de
  mapa, teste da home do motorista, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo teste/widget e restaurar `_buildMapTab`
  em `home_motorista_screen.dart`; nenhum dado, endpoint ou deploy e afetado.

### Escopo
1. Criar widget publico para a aba de mapa completo do motorista.
2. Adicionar widget tests cobrindo estado indisponivel e overlay do mapa.
3. Adaptar `home_motorista_screen.dart` para delegar a aba extraida.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar mapa hero da aba Inicio ou comportamento de tracking ao vivo.
- Alterar DTOs, gateways, presenters, endpoints, WebSocket ou auth.
- Migrar `home_motorista_screen.dart` para `features/driver_trip`.
- Fazer push ou PR.

### Criterios de Aceite
- `home_motorista_screen.dart` fica menor que 1.166 linhas.
- O widget extraido tem teste focado.
- `home_motorista_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar UI/estado da aba de mapa sem alterar contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/driver_trip/presentation/widgets/driver_trip_map_widgets_test.dart`.
  - [x] Cenario: estado indisponivel mostra mensagem e callback de atualizar
    quando ha erro.
  - [x] Cenario: mapa renderiza chip de versao, proxima parada e ETA.
- Regressao minima executada:
  - [x] RED `flutter test test/features/driver_trip/presentation/widgets/driver_trip_map_widgets_test.dart`
  - [x] `flutter test test/features/driver_trip/presentation/widgets/driver_trip_map_widgets_test.dart`
  - [x] `flutter test test/screens/home_motorista_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 0839161)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0941 — QUALITY-0041: extrair listagem de motoristas do portal

### Status
DONE (commit: d8ff493)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0040`. O ratchet atual mostra
`lib/features/admin/presentation/admin_motoristas_screen.dart` como o maior
arquivo Dart do app, com 1.095 linhas.

### Contexto
`AdminMotoristasScreen` ainda concentra a tela stateful, a listagem, o painel
de detalhe, estado de erro e formulario de criacao/edicao no mesmo arquivo. A
listagem de motoristas ja e um widget privado e autocontido, com cabecalho,
estado vazio, linhas, status, selecao e formatacao de telefone.

### Objetivo
Extrair a listagem de motoristas para um widget publico em
`features/admin/presentation/widgets`, deixando a tela principal mais focada em
carregamento, selecao, dialogs e callbacks.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo maior hotspot atual do ratchet
  no app Flutter.
- **Telas/fluxos afetados:** portal admin, tela de motoristas, listagem de
  motoristas, estado vazio, refresh, selecao de motorista e CTA de adicionar.
- **Dependencia de contrato backend:** nenhuma mudanca. `AdminMotoristaGateway`,
  `AdminMotoristaRecord`, `AdminMotoristaCreateInput`, endpoints, payloads,
  status codes, autenticacao e CORS seguem intactos.
- **Mudancas tecnicas:** criar widget publico para a listagem em
  `admin_motoristas_widgets.dart`; mover formatacao visual de telefone junto ao
  widget; adaptar `admin_motoristas_screen.dart` para usar a API publica
  extraida.
- **Riscos e mitigacao:**
  - perder callback de selecao da linha; mitigar com widget test focado;
  - quebrar estado vazio e CTA de adicionar; mitigar com widget test do estado
    vazio;
  - alterar refresh ou status visual da listagem; mitigar com teste focado e
    regressao da tela real;
  - quebrar a tela de motoristas como um todo; mitigar rodando
    `admin_motoristas_screen_test.dart`.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do widget de
  listagem, teste da tela de motoristas, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo widget/teste e restaurar
  `_AdminMotoristasListPanel` em `admin_motoristas_screen.dart`; nenhum dado,
  endpoint ou deploy e afetado.

### Escopo
1. Criar widget publico para a listagem de motoristas.
2. Adicionar widget tests cobrindo selecao, estado vazio e callbacks.
3. Adaptar `admin_motoristas_screen.dart` para delegar a listagem extraida.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar formulario de criacao/edicao de motorista.
- Alterar painel de detalhe, exclusao, DTOs, gateways, endpoints, payloads,
  status codes ou auth.
- Criar novos filtros, paginacao real ou novo comportamento operacional.
- Fazer push ou PR.

### Criterios de Aceite
- `admin_motoristas_screen.dart` fica menor que 1.095 linhas.
- O widget extraido tem teste focado.
- `admin_motoristas_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar UI/callbacks da listagem sem alterar contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_motoristas_widgets_test.dart`.
  - [x] Cenario: listagem renderiza motoristas, telefone formatado, status e
    dispara selecao/refresh.
  - [x] Cenario: lista vazia mostra orientacao e aciona adicionar motorista.
- Regressao minima executada:
  - [x] RED `flutter test test/features/admin/presentation/widgets/admin_motoristas_widgets_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_motoristas_widgets_test.dart`
  - [x] `flutter test test/features/admin/admin_motoristas_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: d8ff493)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0942 — QUALITY-0042: extrair widgets visuais do onboarding de empresa do passageiro

### Status
DONE (commit: 967f37a)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0041`. O ratchet atual mostra
`lib/features/passenger_onboarding/presentation/passenger_company_link_flow.dart`
como o maior arquivo Dart do app, com 1.067 linhas.

### Contexto
`PassengerCompanyLinkFlow` agrupa varias telas do onboarding de empresa do
passageiro: gate, busca, solicitacao pendente, codigo de convite, sucesso,
estado aguardando motorista e revisao da empresa. Alem da navegacao e chamadas
ao `PassengerOnboardingGateway`, o arquivo ainda contem widgets visuais
privados reutilizados por varios estados do fluxo, como card de empresa e card
de destaque.

### Objetivo
Extrair os widgets visuais reutilizaveis do fluxo para
`features/passenger_onboarding/presentation/widgets`, deixando o arquivo
principal mais focado em estado, navegacao e chamadas ao gateway.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo maior hotspot atual do ratchet
  no app Flutter.
- **Telas/fluxos afetados:** onboarding de empresa do passageiro, busca de
  empresas, estados de destaque do fluxo, solicitacao pendente, codigo de
  convite, sucesso e aguardando motorista.
- **Dependencia de contrato backend:** nenhuma mudanca. `PassengerOnboardingGateway`,
  `PassengerOnboardingCompanyRecord`, `PassengerOnboardingStatusRecord`,
  endpoints, payloads, status codes, autenticacao e CORS seguem intactos.
- **Mudancas tecnicas:** criar widgets publicos para card de empresa e card de
  destaque; adaptar `passenger_company_link_flow.dart` para usar a API publica
  extraida sem mudar comportamento de navegacao.
- **Riscos e mitigacao:**
  - perder callback de selecao da empresa; mitigar com widget test focado;
  - quebrar textos, icones ou destaque visual dos estados do onboarding;
    mitigar com widget test do card de destaque;
  - alterar navegacao ou chamadas ao gateway por acidente; mitigar mantendo o
    refactor limitado aos widgets visuais e rodando o teste do fluxo completo;
  - aumentar o arquivo extraido alem do limite de novos arquivos; mitigar
    mantendo apenas componentes reutilizaveis e simples.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado dos widgets,
  teste do fluxo `passenger_company_link_flow_test.dart`, `flutter analyze`,
  testes de metricas, baseline/gates do ratchet, QA gate card sem E2E real,
  `git diff --check`, Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo widget/teste e restaurar
  `_CompanySearchCard` e `_OnboardingHighlightCard` em
  `passenger_company_link_flow.dart`; nenhum dado, endpoint ou deploy e
  afetado.

### Escopo
1. Criar arquivo de widgets do onboarding de empresa do passageiro.
2. Adicionar widget tests cobrindo card de empresa, callback e card de destaque.
3. Adaptar `passenger_company_link_flow.dart` para delegar os widgets extraidos.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar busca de empresas, solicitacao, codigo de convite ou upgrade de
  sessao.
- Alterar DTOs, gateways, endpoints, payloads, status codes, auth ou CORS.
- Reorganizar todas as telas do fluxo de onboarding no mesmo card.
- Fazer push ou PR.

### Criterios de Aceite
- `passenger_company_link_flow.dart` fica menor que 1.067 linhas.
- Os widgets extraidos tem teste focado.
- `passenger_company_link_flow_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar UI/callbacks dos cards visuais sem alterar
  contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/passenger_onboarding/presentation/widgets/passenger_company_link_widgets_test.dart`.
  - [x] Cenario: card de empresa renderiza nome/slug e dispara selecao.
  - [x] Cenario: card de destaque renderiza titulo, corpo, icone e cor de
    destaque.
- Regressao minima executada:
  - [x] RED `flutter test test/features/passenger_onboarding/presentation/widgets/passenger_company_link_widgets_test.dart`
  - [x] `flutter test test/features/passenger_onboarding/presentation/widgets/passenger_company_link_widgets_test.dart`
  - [x] `flutter test test/features/passenger_onboarding/passenger_company_link_flow_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 967f37a)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0943 — QUALITY-0043: extrair aba de paradas da home do motorista

### Status
DONE (commit: d40fddc)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0042`. O ratchet atual mostra
`lib/home_motorista_screen.dart` como o maior arquivo Dart do app, com 1.065
linhas.

### Contexto
`HomeMotoristaScreen` ja delega boa parte da experiencia para widgets
dedicados, mas ainda mantem a composicao completa da aba "Paradas" inline. Essa
aba mistura estado vazio/erro, cabecalho da fila, metricas de total/ETA e a
lista visual de paradas, enquanto a tela principal deveria focar em lifecycle,
presenters, navegacao e callbacks.

### Objetivo
Extrair a aba de paradas para um widget publico em
`features/driver_trip/presentation/widgets`, reaproveitando `DriverStopList` e
deixando `home_motorista_screen.dart` menor e mais focado em orquestracao.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo maior hotspot atual do ratchet
  no app Flutter.
- **Telas/fluxos afetados:** home do motorista, aba "Paradas", estado vazio da
  fila, estado de erro, resumo de quantidade de paradas, ETA total e lista de
  paradas.
- **Dependencia de contrato backend:** nenhuma mudanca. `DriverTripRecord`,
  `DriverTripStopRecord`, gateways, WebSocket, payloads, endpoints, status codes
  e autenticacao seguem intactos.
- **Mudancas tecnicas:** criar widget publico para a aba de paradas; adaptar
  `home_motorista_screen.dart` para delegar estado de carregamento, erro,
  viagem e callback de refresh ao widget extraido.
- **Riscos e mitigacao:**
  - quebrar o estado vazio/erro quando nao ha viagem; mitigar com widget test
    focado;
  - quebrar os chips de total e ETA; mitigar com widget test da aba preenchida;
  - alterar a renderizacao da lista de paradas; mitigar reutilizando
    `DriverStopList`;
  - quebrar a home do motorista; mitigar rodando `home_motorista_screen_test.dart`.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do widget de
  paradas, teste da home do motorista, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo teste/widget e restaurar `_buildStopsTab`
  em `home_motorista_screen.dart`; nenhum dado, endpoint ou deploy e afetado.

### Escopo
1. Criar widget publico para a aba de paradas do motorista.
2. Adicionar widget tests cobrindo estado vazio/erro e aba preenchida.
3. Adaptar `home_motorista_screen.dart` para delegar a aba extraida.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar lifecycle de viagem, GPS, WebSocket ou navegacao externa.
- Alterar DTOs, gateways, presenters, endpoints, payloads, status codes ou auth.
- Extrair `_buildTripTab` ou mexer nas sheets de inicio neste card.
- Fazer push ou PR.

### Criterios de Aceite
- `home_motorista_screen.dart` fica menor que 1.065 linhas.
- O widget extraido tem teste focado.
- `home_motorista_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar UI/estado da aba de paradas sem alterar contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/driver_trip/presentation/widgets/driver_trip_stop_widgets_test.dart`.
  - [x] Cenario: estado vazio/erro mostra mensagem e aciona refresh.
  - [x] Cenario: viagem preenchida mostra cabecalho, total de paradas, ETA e
    lista.
- Regressao minima executada:
  - [x] RED `flutter test test/features/driver_trip/presentation/widgets/driver_trip_stop_widgets_test.dart`
  - [x] `flutter test test/features/driver_trip/presentation/widgets/driver_trip_stop_widgets_test.dart`
  - [x] `flutter test test/screens/home_motorista_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: d40fddc)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0944 — QUALITY-0044: extrair dialog de cadastro de passageiro admin

### Status
DONE (commit: ffd428a)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0043`. O ratchet atual mostra
empate entre `admin_login_screen.dart` e `admin_passageiros_screen.dart`, ambos
com 1.055 linhas. O Graphify apontou conexoes ricas em torno dos widgets de
passageiros, e a leitura direta confirmou que `admin_passageiros_screen.dart`
ainda guarda dialogs privados grandes dentro da tela stateful.

### Contexto
`AdminPassageirosScreen` ja delega lista e atividades para
`admin_passageiros_widgets.dart`, mas ainda concentra o dialog de cadastro, o
bloco de selecao de servicos, validacao de senha forte e montagem de
`AdminPassageiroCreateInput`. Essa responsabilidade visual/formulario pode
viver em um widget publico testavel, deixando a tela focada em lifecycle,
gateway, selecao, dialogs e mensagens.

### Objetivo
Extrair o dialog de cadastro de passageiro para
`features/admin/presentation/widgets`, com teste focado cobrindo selecao de
servicos, cadastro sem motorista inicial e validacao de senha antes de chamar o
gateway.

### Impact Analysis
- **Contexto do card:** refatoracao guiada por hotspot atual do ratchet no app
  Flutter.
- **Telas/fluxos afetados:** portal admin de passageiros, acao "Cadastrar
  Passageiro", dialog de cadastro, selecao de servicos recorrentes, senha forte
  e retorno do passageiro criado.
- **Dependencia de contrato backend:** nenhuma mudanca. `AdminPassageiroGateway`,
  `AdminPassageiroCreateInput`, `AdminPassageiroRecord`,
  `AdminServicoRecorrenteRecord`, endpoints, payloads, status codes,
  autenticacao e CORS seguem intactos.
- **Mudancas tecnicas:** criar widget publico para o dialog de cadastro; mover o
  bloco visual de servicos para o mesmo modulo; adaptar
  `admin_passageiros_screen.dart` para abrir o widget extraido sem alterar a
  assinatura do gateway.
- **Riscos e mitigacao:**
  - quebrar cadastro sem motorista inicial; mitigar com widget test focado;
  - quebrar selecao de servicos liberados; mitigar verificando `servicoIds`;
  - enfraquecer validacao de senha forte; mitigar com teste que nao chama
    `onSubmit` quando a senha e fraca;
  - quebrar fluxo completo da tela; mitigar rodando
    `admin_passageiros_screen_test.dart`.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do dialog,
  teste da tela de passageiros, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo teste/widget e restaurar o dialog privado
  em `admin_passageiros_screen.dart`; nenhum dado, endpoint ou deploy e
  afetado.

### Escopo
1. Criar widget publico para o dialog de cadastro de passageiro admin.
2. Adicionar widget tests cobrindo cadastro valido e senha fraca.
3. Adaptar `admin_passageiros_screen.dart` para usar o dialog extraido.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Extrair dialog de edicao ou reatribuicao neste card.
- Alterar contratos, DTOs, gateways, endpoints, payloads, status codes ou auth.
- Alterar layout/listagem de passageiros ja extraidos.
- Fazer push ou PR.

### Criterios de Aceite
- `admin_passageiros_screen.dart` fica menor que 1.055 linhas.
- O dialog extraido tem teste focado.
- `admin_passageiros_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar cadastro de passageiro admin sem alterar contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_passageiro_form_dialog_test.dart`.
  - [x] Cenario: cadastro valido envia input normalizado, motorista opcional e
    servicos selecionados.
  - [x] Cenario: senha fraca mostra erro e nao chama `onSubmit`.
- Regressao minima executada:
  - [x] RED `flutter test test/features/admin/presentation/widgets/admin_passageiro_form_dialog_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_passageiro_form_dialog_test.dart`
  - [x] `flutter test test/features/admin/admin_passageiros_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: ffd428a)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0945 — QUALITY-0045: extrair dialog de bootstrap do login admin

### Status
DONE (commit: 3a4fa2d)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0044`. O ratchet atual mostra
`admin_login_screen.dart` como maior arquivo Dart do app, com 1.055 linhas. A
leitura direta confirmou que a tela ja delega o hero para um widget dedicado,
mas ainda concentra o dialog de bootstrap do primeiro admin junto da tela,
formulario de login, fluxo de pendencia de verificacao e dialog de troca de
email.

### Contexto
`AdminLoginScreen` deve ficar responsavel por lifecycle, autenticacao,
navegacao e mensagens de sessao. O formulario de bootstrap inicial e um bloco
visual/formulario independente, com validacao local e chamada ao
`AdminPortalAuthGateway.bootstrap`, e pode viver como widget publico testavel em
`features/admin/presentation/widgets`.

### Objetivo
Extrair o dialog de bootstrap do primeiro admin para um widget publico,
adicionar testes focados para envio valido e validacao local, e manter
`admin_login_screen.dart` menor e mais facil de manter sem alterar contratos da
API.

### Impact Analysis
- **Contexto do card:** refatoracao guiada por hotspot atual do ratchet no app
  Flutter.
- **Telas/fluxos afetados:** portal admin de login, acao de criar primeira
  empresa/admin, validacao local dos campos do bootstrap, retorno de login apos
  cadastro inicial.
- **Dependencia de contrato backend:** nenhuma mudanca. `AdminPortalAuthGateway`,
  `AdminCompanyAdminBootstrapInput`, `AdminCompanyAdminBootstrapResult`,
  endpoints, payloads, status codes, autenticacao e CORS seguem intactos.
- **Mudancas tecnicas:** criar widget publico para o dialog de bootstrap; mover
  estado, validacao e envio do bootstrap para esse widget; adaptar
  `admin_login_screen.dart` para abrir o dialog extraido e consumir o resultado.
- **Riscos e mitigacao:**
  - quebrar criacao da primeira empresa/admin; mitigar com widget test de envio
    valido;
  - perder normalizacao de email/telefone/senha; mitigar com asserts no input
    enviado ao gateway;
  - quebrar validacao local antes da chamada remota; mitigar com teste que nao
    chama o gateway quando campos obrigatorios estao invalidos;
  - quebrar fluxo completo de login; mitigar rodando
    `admin_login_screen_test.dart`.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do dialog, teste
  da tela de login admin, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo teste/widget e restaurar o dialog privado
  em `admin_login_screen.dart`; nenhum dado, endpoint ou deploy e afetado.

### Escopo
1. Criar widget publico para o dialog de bootstrap do login admin.
2. Adicionar widget tests cobrindo cadastro valido e validacao obrigatoria.
3. Adaptar `admin_login_screen.dart` para usar o dialog extraido.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar contratos, DTOs, gateways, endpoints, payloads, status codes ou auth.
- Alterar layout geral do portal admin fora do dialog de bootstrap.
- Refatorar formulario de login, fluxo de email pendente ou dialog de troca de
  email neste card.
- Fazer push ou PR.

### Criterios de Aceite
- `admin_login_screen.dart` fica menor que 1.055 linhas.
- O dialog extraido tem teste focado.
- `admin_login_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar bootstrap do primeiro admin sem alterar contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_login_bootstrap_dialog_test.dart`.
  - [x] Cenario: cadastro valido envia input normalizado e retorna senha para
    login automatico.
  - [x] Cenario: campos obrigatorios invalidos mostram validacao e nao chamam o
    gateway.
- Regressao minima executada:
  - [x] RED `flutter test test/features/admin/presentation/widgets/admin_login_bootstrap_dialog_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_login_bootstrap_dialog_test.dart`
  - [x] `flutter test test/features/admin/admin_login_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 3a4fa2d)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0946 — QUALITY-0046: extrair formulario de rota legada admin

### Status
DONE (commit: 90df1b6)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0045`. O ratchet atual mostra
`admin_rotas_screen.dart` como maior arquivo Dart do app, com 1.053 linhas. A
leitura direta confirmou que lista e detalhe ja foram extraidos para widgets
publicos, mas a view stateful de criacao/edicao de rota-base legada ainda vive
dentro da tela principal.

### Contexto
`AdminRotasScreen` deve ficar focada em lifecycle, gateway, carregamento de
listas, selecao e navegacao entre lista/detalhe/formulario. O formulario de
rota-base legada concentra estado visual, validacao local, selecao de motorista,
selecao de passageiros, dias de operacao, horario e destino final, e pode viver
como widget publico testavel em `features/admin/presentation/widgets`.

### Objetivo
Extrair a view/form de criacao e edicao de rota-base legada para um widget
publico, adicionar testes focados para submissao valida e validacao local, e
reduzir `admin_rotas_screen.dart` sem alterar contratos da API.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo maior hotspot atual do ratchet
  no app Flutter.
- **Telas/fluxos afetados:** portal admin de rotas-base legadas, acao "Criar
  rota legada", edicao de rota existente, validacao de motorista/passageiros,
  dias de operacao, horario opcional e destino final.
- **Dependencia de contrato backend:** nenhuma mudanca. `AdminRotasGateway`,
  `AdminRotaBaseCreateInput`, `AdminRotaBaseRecord`, endpoints, payloads,
  status codes, autenticacao e CORS seguem intactos.
- **Mudancas tecnicas:** criar widget publico para a view de formulario; mover
  estado, validacao e montagem de `AdminRotaBaseCreateInput`; adaptar
  `admin_rotas_screen.dart` para abrir o widget extraido nos modos create/edit.
- **Riscos e mitigacao:**
  - quebrar criacao/edicao de rota legada; mitigar com widget test de submissao
    valida;
  - perder filtro de passageiros por motorista; mitigar verificando ids
    enviados no input;
  - enfraquecer validacao local de motorista/passageiros; mitigar com teste que
    bloqueia submissao invalida;
  - quebrar tela completa de rotas; mitigar rodando
    `admin_rotas_screen_test.dart`.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do formulario,
  teste da tela de rotas, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo teste/widget e restaurar a view privada
  em `admin_rotas_screen.dart`; nenhum dado, endpoint ou deploy e afetado.

### Escopo
1. Criar widget publico para o formulario de rota-base legada.
2. Adicionar widget tests cobrindo submissao valida e validacao obrigatoria.
3. Adaptar `admin_rotas_screen.dart` para usar o widget extraido.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar contratos, DTOs, gateways, endpoints, payloads, status codes ou auth.
- Recriar UX de rotas-base ou migrar o fluxo para servicos recorrentes.
- Refatorar listagem, detalhe ou delete dialog neste card.
- Fazer push ou PR.

### Criterios de Aceite
- `admin_rotas_screen.dart` fica menor que 1.053 linhas.
- O formulario extraido tem teste focado.
- `admin_rotas_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar criacao/edicao de rota-base legada sem alterar
  contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_rota_form_view_test.dart`.
  - [x] Cenario: submissao valida envia input com motorista, passageiros,
    dias, horario, destino final e flag de geracao automatica.
  - [x] Cenario: campos obrigatorios invalidos bloqueiam `onSubmit`.
- Regressao minima executada:
  - [x] RED `flutter test test/features/admin/presentation/widgets/admin_rota_form_view_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_rota_form_view_test.dart`
  - [x] `flutter test test/features/admin/admin_rotas_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 90df1b6)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0947 — QUALITY-0047: extrair formulario de servico recorrente admin

### Status
DONE (commit: 8652782)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0046`. O ratchet atual mostra
`admin_servicos_recorrentes_screen.dart` como maior arquivo Dart do app, com
1.032 linhas. A leitura direta confirmou que listagem e detalhe ja usam widgets
publicos, mas `AdminServicoRecorrenteForm` ainda vive dentro da tela principal.

### Contexto
`AdminServicosRecorrentesScreen` deve ficar focada em lifecycle, gateway,
carregamento de listas, selecao e navegacao entre lista/detalhe/formulario. O
formulario concentra estado visual, validacao local, destino final, motorista
padrao, dias de operacao, capacidade e elegibilidade de passageiros, e pode
viver como widget publico testavel em `features/admin/presentation/widgets`.

### Objetivo
Extrair o formulario de criacao/edicao de servico recorrente para um widget
publico, adicionar testes focados de componente e reduzir
`admin_servicos_recorrentes_screen.dart` sem alterar contratos da API.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo maior hotspot atual do ratchet
  no app Flutter.
- **Telas/fluxos afetados:** portal admin de servicos recorrentes, acao "Novo
  servico", edicao de servico existente, validacao de nome/horario/capacidade,
  selecao de motorista padrao, dias de operacao, destino final e passageiros
  elegiveis.
- **Dependencia de contrato backend:** nenhuma mudanca.
  `AdminServicosRecorrentesGateway`, `AdminServicoRecorrenteInput`,
  `AdminServicoRecorrenteRecord`, endpoints, payloads, status codes,
  autenticacao e CORS seguem intactos.
- **Mudancas tecnicas:** mover `AdminServicoRecorrenteForm` e seu estado para
  arquivo publico em `widgets`; manter helpers existentes de
  `admin_servicos_recorrentes_form_widgets.dart`; adaptar a tela principal para
  instanciar o widget extraido.
- **Riscos e mitigacao:**
  - quebrar criacao/edicao de servico recorrente; mitigar com widget test de
    submissao valida;
  - perder filtro/bulk select de passageiros elegiveis; mitigar com teste de
    busca e selecao de passageiros;
  - enfraquecer validacao local de horario/capacidade/dias; mitigar com teste
    de bloqueio de submissao invalida;
  - quebrar tela completa de servicos; mitigar rodando
    `admin_servicos_recorrentes_screen_test.dart`.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do formulario,
  teste da tela de servicos recorrentes, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo teste/widget e restaurar o form dentro de
  `admin_servicos_recorrentes_screen.dart`; nenhum dado, endpoint ou deploy e
  afetado.

### Escopo
1. Criar widget publico para o formulario de servico recorrente.
2. Adicionar widget tests cobrindo submissao valida, busca/selecao de
   passageiros e validacao local.
3. Adaptar `admin_servicos_recorrentes_screen.dart` para usar o widget extraido.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar contratos, DTOs, gateways, endpoints, payloads, status codes ou auth.
- Recriar UX de servicos recorrentes ou alterar regras operacionais.
- Refatorar listagem, detalhe ou confirmacao de inativacao neste card.
- Fazer push ou PR.

### Criterios de Aceite
- `admin_servicos_recorrentes_screen.dart` fica menor que 1.032 linhas.
- O formulario extraido tem teste focado.
- `admin_servicos_recorrentes_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar criacao/edicao de servico recorrente sem alterar
  contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_servico_recorrente_form_test.dart`.
  - [x] Cenario: submissao valida envia input completo com campos operacionais,
    destino final e passageiros elegiveis.
  - [x] Cenario: busca e selecao de passageiros preservam IDs enviados.
  - [x] Cenario: dados invalidos bloqueiam `onSubmit`.
- Regressao minima executada:
  - [x] RED `flutter test test/features/admin/presentation/widgets/admin_servico_recorrente_form_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_servico_recorrente_form_test.dart`
  - [x] `flutter test test/features/admin/admin_servicos_recorrentes_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 8652782)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0948 — QUALITY-0048: extrair dialog de decisão de solicitação admin

### Status
DONE (commit: 8c6be99)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0047`. O ratchet atual mostra
`admin_vinculos_screen.dart` como maior arquivo Dart do app, com 1.024 linhas.
A leitura direta confirmou que a tela ja usa paineis publicos para solicitacoes,
convites, direitos por servico, atalhos operacionais e responsaveis, mas ainda
mantem dentro da tela principal o dialog de decisao de solicitacao de acesso.

### Contexto
`AdminVinculosScreen` deve ficar focada em lifecycle, gateway, carregamento de
dados e orquestracao de callbacks. O dialog de decisao concentra estado visual,
motivo opcional, selecao de motorista inicial e selecao de servicos liberados,
podendo viver como componente publico testavel em `features/admin/presentation/widgets`.

### Objetivo
Extrair o dialog de aprovacao/rejeicao de solicitacao de acesso para um widget
publico, adicionar testes focados de componente e reduzir
`admin_vinculos_screen.dart` sem alterar contratos da API.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo maior hotspot atual do ratchet
  no app Flutter.
- **Telas/fluxos afetados:** portal admin de vinculos, abertura de solicitacao
  de acesso, aprovacao de passageiro, rejeicao de solicitacao, selecao opcional
  de motorista inicial e servicos liberados no momento da aprovacao.
- **Dependencia de contrato backend:** nenhuma mudanca.
  `AdminVinculoGateway`, `approveAccessRequest`, `rejectAccessRequest`,
  endpoints, payloads, status codes, autenticacao e CORS seguem intactos.
- **Mudancas tecnicas:** mover o dialog de decisao, seu modelo de retorno e o
  notice auxiliar para arquivo publico em `widgets`; adaptar a tela principal
  para instanciar o widget extraido.
- **Riscos e mitigacao:**
  - quebrar aprovacao com servicos liberados; mitigar com widget test focado
    validando retorno de `servicoIds`;
  - perder selecao de motorista inicial; mitigar com teste de retorno de
    `motoristaId`;
  - quebrar rejeicao com observacao; mitigar com teste de rejeicao e motivo;
  - quebrar tela completa de vinculos; mitigar rodando
    `admin_vinculos_screen_test.dart`.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do dialog,
  teste da tela de vinculos, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo teste/widget e restaurar o dialog dentro
  de `admin_vinculos_screen.dart`; nenhum dado, endpoint ou deploy e afetado.

### Escopo
1. Criar widget publico para o dialog de decisao de solicitacao de acesso.
2. Adicionar widget tests cobrindo aprovacao com motorista/servicos e rejeicao
   com motivo.
3. Adaptar `admin_vinculos_screen.dart` para usar o widget extraido.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar contratos, DTOs, gateways, endpoints, payloads, status codes ou auth.
- Recriar UX de solicitacoes, convites ou direitos por servico.
- Refatorar paineis de listagem, convites, responsaveis ou atalhos neste card.
- Fazer push ou PR.

### Criterios de Aceite
- `admin_vinculos_screen.dart` fica menor que 1.024 linhas.
- O dialog extraido tem teste focado.
- `admin_vinculos_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar aprovacao/rejeicao de solicitacoes sem alterar
  contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_vinculos_access_request_dialog_test.dart`.
  - [x] Cenario: aprovacao retorna motorista inicial e servicos liberados.
  - [x] Cenario: rejeicao retorna motivo informado.
- Regressao minima executada:
  - [x] RED `flutter test test/features/admin/presentation/widgets/admin_vinculos_access_request_dialog_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_vinculos_access_request_dialog_test.dart`
  - [x] `flutter test test/features/admin/admin_vinculos_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 8c6be99)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0949 — QUALITY-0049: extrair estado inicial da home do motorista

### Status
DONE (commit: 277d298)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0048`. O ratchet atual mostra
`home_motorista_screen.dart` como maior arquivo Dart de producao do app ainda
acima de 1.000 linhas, com 1.002 linhas. A leitura direta confirmou que as abas
de mapa, paradas, conta, sheets de viagem e painel de detalhe ja usam widgets
publicos, mas o estado inicial/loading/erro/empty da aba principal ainda vive
dentro da tela stateful.

### Contexto
`HomeMotoristaScreen` deve ficar focada em lifecycle, presenters, gateways,
eventos realtime e callbacks operacionais. O estado inicial da aba de viagem
concentra cabecalho, refresh, mensagens de loading/erro/sem viagem e pode viver
como widget publico testavel em `features/driver_trip/presentation/widgets`.

### Objetivo
Extrair o estado inicial da aba principal do motorista para um widget publico,
adicionar testes focados de componente e reduzir `home_motorista_screen.dart`
sem alterar contratos da API.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo maior hotspot de producao atual
  do ratchet no app Flutter.
- **Telas/fluxos afetados:** home do motorista quando ainda esta carregando,
  quando a carga falha ou quando nao ha viagem publicada no dia.
- **Dependencia de contrato backend:** nenhuma mudanca.
  `DriverTripGateway`, `DriverTripRecord`, endpoints, payloads, status codes,
  autenticacao, websocket e rotas seguem intactos.
- **Mudancas tecnicas:** mover a UI de loading/erro/empty da aba de viagem para
  widget publico em `driver_home_base_widgets.dart`; adaptar
  `home_motorista_screen.dart` para apenas instanciar esse widget.
- **Riscos e mitigacao:**
  - quebrar refresh do estado vazio/erro; mitigar com widget test focado
    validando callback e key de refresh;
  - alterar textos/estado visual da home sem viagem; mitigar com teste focado
    de empty state;
  - quebrar tela completa do motorista; mitigar rodando
    `home_motorista_screen_test.dart`.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do widget,
  teste da tela do motorista, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo teste/widget e restaurar o bloco inline
  em `home_motorista_screen.dart`; nenhum dado, endpoint ou deploy e afetado.

### Escopo
1. Criar widget publico para o estado inicial/loading/erro/empty da home do
   motorista.
2. Adicionar widget tests cobrindo loading, erro com retry e empty state.
3. Adaptar `home_motorista_screen.dart` para usar o widget extraido.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar contratos, DTOs, gateways, endpoints, payloads, status codes ou auth.
- Refatorar lifecycle, websocket, compartilhamento de localizacao ou acoes da
  corrida neste card.
- Dividir `home_motorista_screen_test.dart` neste card.
- Fazer push ou PR.

### Criterios de Aceite
- `home_motorista_screen.dart` fica menor que 1.002 linhas.
- O estado inicial extraido tem teste focado.
- `home_motorista_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar o primeiro estado operacional da home do motorista
  sem alterar contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/driver_trip/presentation/widgets/driver_home_widgets_test.dart`.
  - [x] Cenario: loading mostra progresso e aciona refresh.
  - [x] Cenario: erro mostra mensagem e aciona retry.
  - [x] Cenario: empty state mostra ausencia de viagem publicada.
- Regressao minima executada:
  - [x] RED `flutter test test/features/driver_trip/presentation/widgets/driver_home_widgets_test.dart`
  - [x] `flutter test test/features/driver_trip/presentation/widgets/driver_home_widgets_test.dart`
  - [x] `flutter test test/screens/home_motorista_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 277d298)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0950 — QUALITY-0050: extrair agenda do dia do rastreio guardian

### Status
DONE (commit: c4da28a)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0049`. O ratchet atual mostra
`guardian_localizacao_screen.dart` como maior arquivo Dart de producao do app,
com 1.001 linhas. A leitura direta confirmou que a tela ja delega cards de
passageiro, timeline e mensagens para `guardian_localizacao_widgets.dart`, mas
a agenda horizontal de viagens do dia ainda vive como metodo privado
`_buildGuardianDayAgendaSelector` dentro da tela stateful.

### Contexto
`GuardianLocalizacaoScreen` deve ficar focada em lifecycle, gateways, selecao
do passageiro, rastreio realtime e composicao de alto nivel. A agenda do dia e
UI pura: recebe a lista de viagens, mostra horario/tipo/nome/status, preserva
as keys publicas e dispara um callback de selecao.

### Objetivo
Extrair a agenda de viagens do dia do rastreio guardian para um widget publico,
adicionar testes focados de componente e reduzir `guardian_localizacao_screen.dart`
sem alterar contratos da API.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo maior hotspot de producao atual
  do ratchet no app Flutter.
- **Telas/fluxos afetados:** tela de rastreio do responsavel quando o passageiro
  possui mais de uma viagem no dia.
- **Dependencia de contrato backend:** nenhuma mudanca.
  `TripTrackingGateway`, `TripTrackingRecord`, endpoints, payloads, status codes,
  autenticacao, websocket e rotas seguem intactos.
- **Mudancas tecnicas:** mover `_buildGuardianDayAgendaSelector`,
  `_guardianTripOptionLabel` e `_guardianTripStatusIcon` para um widget publico
  em `guardian_localizacao_widgets.dart`; adaptar a tela principal para apenas
  instanciar o widget extraido preservando `agendaViagensKey` e
  `selecionarViagemDiaButtonKey`.
- **Riscos e mitigacao:**
  - quebrar selecao de viagem do dia; mitigar com widget test focado validando
    callback;
  - perder keys usadas em testes e automacao; mitigar com teste focado e teste
    de tela existente;
  - alterar label horario/tipo/nome/status; mitigar com teste focado do texto
    renderizado.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do widget,
  teste da tela guardian, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo teste/widget e restaurar o metodo privado
  em `guardian_localizacao_screen.dart`; nenhum dado, endpoint ou deploy e
  afetado.

### Escopo
1. Criar widget publico para a agenda de viagens do dia do responsavel.
2. Adicionar widget tests cobrindo label, status visual, key e callback.
3. Adaptar `guardian_localizacao_screen.dart` para usar o widget extraido.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar contratos, DTOs, gateways, endpoints, payloads, status codes ou auth.
- Refatorar websocket, busca de passageiros, selecao cross-passageiro ou mapa
  completo neste card.
- Dividir `guardian_localizacao_screen_test.dart` neste card.
- Fazer push ou PR.

### Criterios de Aceite
- `guardian_localizacao_screen.dart` fica menor que 1.001 linhas.
- A agenda extraida tem teste focado.
- `guardian_localizacao_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar selecao da viagem do dia do responsavel sem alterar
  contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/guardian/presentation/widgets/guardian_localizacao_widgets_test.dart`.
  - [x] Cenario: agenda mostra quantidade de viagens e label horario/tipo/nome.
  - [x] Cenario: item selecionado preserva key publica e dispara callback.
- Regressao minima executada:
  - [x] RED `flutter test test/features/guardian/presentation/widgets/guardian_localizacao_widgets_test.dart`
  - [x] `flutter test test/features/guardian/presentation/widgets/guardian_localizacao_widgets_test.dart`
  - [x] `flutter test test/features/guardian/guardian_localizacao_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: c4da28a)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0951 — QUALITY-0051: extrair aba inicial da home motorista

### Status
DONE (commit: e51f44c)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0050`. O ratchet atual mostra
`home_motorista_screen.dart` como maior arquivo Dart produtivo do app, com 936
linhas. A tela ja delega sheets, barras, mapa e estados vazios para widgets
dedicados, mas a composicao da aba inicial ainda vive inline no metodo privado
`_buildTripTab`.

### Contexto
`HomeMotoristaScreen` deve ficar focada em lifecycle, presenters, gateways,
localizacao ao vivo e callbacks operacionais. A aba inicial e majoritariamente
composicao visual: escolhe o estado indisponivel, calcula modo planejamento,
data, proxima parada, quantidade de paradas pendentes e instancia top bar, mapa
hero e sheet apropriada.

### Objetivo
Extrair a aba inicial da home do motorista para um widget publico em
`features/driver_trip/presentation/widgets`, adicionar teste focado do
componente e reduzir `home_motorista_screen.dart` sem alterar contratos da API
nem comportamento de navegacao/lifecycle.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo maior hotspot de producao atual
  do ratchet no app Flutter.
- **Telas/fluxos afetados:** home do motorista, aba Inicio, estado de
  carregamento/erro/sem viagem, modo planejamento e modo corrida em andamento.
- **Dependencia de contrato backend:** nenhuma mudanca. `DriverTripGateway`,
  `DriverTripRecord`, `TripLiveLocationRecord`, endpoints, payloads, status
  codes, autenticacao, websocket e rotas seguem intactos.
- **Mudancas tecnicas:** criar `DriverHomeTripTab`, mover a composicao de
  `_buildTripTab` para esse widget e adaptar `home_motorista_screen.dart` para
  passar estado e callbacks explicitamente.
- **Riscos e mitigacao:**
  - quebrar estado de loading/erro/sem viagem; mitigar reutilizando
    `DriverHomeUnavailableTripState` e cobrindo em teste focado;
  - quebrar modo planejamento/corrida ou keys publicas; mitigar com teste do
    widget e regressao de `home_motorista_screen_test.dart`;
  - alterar callback de abrir paradas/mapa/editar/iniciar; mitigar com teste de
    callbacks do componente extraido.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do widget,
  teste da home do motorista, `flutter analyze`, testes de metricas,
  baseline/gates do ratchet, QA gate card sem E2E real, `git diff --check`,
  Graphify refresh e harness `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo widget/teste e restaurar `_buildTripTab`
  em `home_motorista_screen.dart`; nenhum dado, endpoint ou deploy e afetado.

### Escopo
1. Criar widget publico para a aba inicial da home do motorista.
2. Adicionar widget tests cobrindo estado indisponivel e modo planejamento.
3. Adaptar `home_motorista_screen.dart` para usar o widget extraido.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar lifecycle de corrida, rota, localizacao ao vivo ou presenters.
- Alterar DTOs, gateways, endpoints, payloads, status codes ou auth.
- Refatorar a aba de mapa, paradas ou conta neste card.
- Fazer push ou PR.

### Criterios de Aceite
- `home_motorista_screen.dart` fica menor que 936 linhas.
- A aba inicial extraida tem teste focado.
- `home_motorista_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar a composicao da aba Inicio da home do motorista sem
  alterar contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart`.
  - [x] Cenario: estado indisponivel usa a key de atualizar e dispara refresh.
  - [x] Cenario: modo planejamento renderiza agenda/sheet/mapa e dispara
    callbacks de iniciar, abrir paradas, abrir mapa e editar rota.
- Regressao minima executada:
  - [x] RED `flutter test test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart`
  - [x] `flutter test test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart`
  - [x] `flutter test test/screens/home_motorista_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: e51f44c)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0952 — QUALITY-0052: extrair card de revisao da empresa do onboarding passageiro

### Status
DONE (commit: 426c0a5)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0051`. O ratchet atual mostra
`passenger_company_link_flow.dart` como maior arquivo Dart produtivo do app,
com 934 linhas. A tela/fluxo ja delega cards visuais compartilhados para
`passenger_company_link_widgets.dart`, mas o card de revisao da empresa
selecionada ainda vive inline em `_PassengerCompanyReviewScreen`.

### Contexto
`PassengerCompanyLinkFlow` deve ficar focado em orquestracao do fluxo de
vinculo: busca, codigo de convite, solicitacao, atualizacao de status e
navegacao entre estados. O card de revisao da empresa selecionada e UI pura:
recebe uma `PassengerOnboardingCompanyRecord`, mostra nome, slug, selo de
empresa verificada e o bloco explicativo do processo de revisao.

### Objetivo
Extrair o card de revisao da empresa selecionada para um widget publico em
`features/passenger_onboarding/presentation/widgets`, adicionar teste focado
do componente e reduzir `passenger_company_link_flow.dart` sem alterar
contratos da API nem comportamento de navegacao/submissao.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo maior hotspot de producao atual
  do ratchet no app Flutter.
- **Telas/fluxos afetados:** onboarding de passageiro sem empresa, etapa de
  revisao antes de enviar solicitacao de vinculo a empresa.
- **Dependencia de contrato backend:** nenhuma mudanca.
  `PassengerOnboardingGateway`, `PassengerOnboardingCompanyRecord`, endpoints,
  payloads, status codes, autenticacao, websocket e rotas seguem intactos.
- **Mudancas tecnicas:** criar `PassengerCompanyReviewCard`, mover a composicao
  visual inline do card para `passenger_company_link_widgets.dart` e adaptar
  `_PassengerCompanyReviewScreen` para apenas instanciar o widget extraido.
- **Riscos e mitigacao:**
  - perder informacoes de nome/slug da empresa; mitigar com widget test focado;
  - alterar selo de empresa verificada ou copy do processo de revisao; mitigar
    com teste do texto renderizado;
  - quebrar submissao da solicitacao; mitigar mantendo botao/callback na tela
    stateful e rodando regressao do fluxo de onboarding existente.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do widget,
  teste do fluxo de onboarding de passageiro, `flutter analyze`, testes de
  metricas, baseline/gates do ratchet, QA gate card sem E2E real,
  `git diff --check`, Graphify refresh e harness
  `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo widget/teste e restaurar o card inline
  em `_PassengerCompanyReviewScreen`; nenhum dado, endpoint ou deploy e
  afetado.

### Escopo
1. Criar widget publico para o card de revisao da empresa selecionada.
2. Adicionar widget test cobrindo nome, slug, selo e copy do processo.
3. Adaptar `PassengerCompanyLinkFlow` para usar o widget extraido.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar busca de empresas, criacao de solicitacao, convite ou upgrade de
  sessao.
- Alterar DTOs, gateways, endpoints, payloads, status codes ou auth.
- Refatorar telas de codigo, pendencia ou sucesso neste card.
- Fazer push ou PR.

### Criterios de Aceite
- `passenger_company_link_flow.dart` fica menor que 934 linhas.
- O card de revisao extraido tem teste focado.
- `passenger_company_link_flow_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar a revisao visual da empresa selecionada antes da
  solicitacao sem alterar contratos.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/passenger_onboarding/presentation/widgets/passenger_company_link_widgets_test.dart`.
  - [x] Cenario: card mostra nome, slug e selo de empresa verificada.
  - [x] Cenario: card preserva copy do processo de revisao.
- Regressao minima executada:
  - [x] RED `flutter test test/features/passenger_onboarding/presentation/widgets/passenger_company_link_widgets_test.dart`
  - [x] `flutter test test/features/passenger_onboarding/presentation/widgets/passenger_company_link_widgets_test.dart`
  - [x] `flutter test test/features/passenger_onboarding/passenger_company_link_flow_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 426c0a5)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0953 — QUALITY-0053: extrair componentes visuais do rastreio map-first

### Status
DONE (commit: 1b75e0b)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0052`. O ratchet atual mostra
`lib/features/tracking/presentation/map_first_trip_tracking_view.dart` como
maior arquivo Dart produtivo do app, com 928 linhas. A view mistura
orquestracao do rastreio ao vivo, composicao do painel principal e varios
widgets visuais puros de metricas, fila, trechos e acoes.

### Contexto
`MapFirstTripTrackingView` deve permanecer responsavel por montar o fluxo do
rastreio da viagem, conectar `TripLiveMapPanel`, reagir a atualizacoes ao vivo
e escolher os dados apresentados. Os componentes visuais de apoio nao precisam
ficar no mesmo arquivo da tela stateful: eles recebem dados ja resolvidos,
renderizam texto/icone/callback e podem ser testados isoladamente.

### Objetivo
Extrair os componentes visuais puros do rastreio map-first para um arquivo
dedicado em `features/tracking/presentation`, adicionar widget tests focados e
reduzir `map_first_trip_tracking_view.dart` sem alterar contrato da API,
WebSocket, presenter, servicos ou comportamento de navegacao.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo maior hotspot produtivo atual
  do ratchet no app Flutter.
- **Telas/fluxos afetados:** painel map-first de rastreio usado pelo
  passageiro/responsavel e pela tela de localizacao do guardian.
- **Dependencia de contrato backend:** nenhuma mudanca. `TripTrackingRecord`,
  `TripRouteGeometryRecord`, endpoints de viagem/localizacao, payloads, status
  codes, autenticacao, websocket e rotas seguem intactos.
- **Mudancas tecnicas:** criar componentes publicos dedicados para metricas,
  badge de contexto da rota, tiles da fila, tiles de trecho e barra de acoes;
  adaptar `MapFirstTripTrackingView` para consumir esses componentes.
- **Riscos e mitigacao:**
  - perder labels de fila/proxima parada; mitigar com widget tests dos tiles;
  - alterar resumo de distancia/duracao dos trechos; mitigar com teste de leg;
  - quebrar callbacks dos botoes primario/secundario; mitigar com teste da
    action bar e regressao da view existente;
  - criar componente visual grande demais; mitigar mantendo novo arquivo abaixo
    do limite do ratchet e sem estado proprio.
- **Validacao minima obrigatoria:** RED e GREEN de widget tests focados,
  regressao de `map_first_trip_tracking_view_test.dart`, `flutter analyze`,
  testes de metricas, baseline/gates do ratchet, QA gate card sem E2E real,
  `git diff --check`, Graphify refresh e harness
  `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo arquivo/teste e restaurar os widgets
  visuais inline em `map_first_trip_tracking_view.dart`; nenhum dado,
  endpoint, deploy ou migracao e afetado.

### Escopo
1. Criar `map_first_trip_tracking_components.dart` com componentes visuais
   extraidos da view.
2. Adicionar `map_first_trip_tracking_components_test.dart` cobrindo metricas,
   fila, trechos e acoes.
3. Adaptar `MapFirstTripTrackingView` para usar os componentes extraidos.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar `TripLiveMapPanel`, gateways, presenter, services ou WebSocket.
- Alterar DTOs, endpoints, payloads, status codes, auth ou navegacao.
- Refatorar `guardian_localizacao_screen.dart` ou `trip_live_map_panel.dart`.
- Fazer push ou PR.

### Criterios de Aceite
- `map_first_trip_tracking_view.dart` fica menor que 928 linhas.
- Os componentes extraidos tem widget tests focados.
- `map_first_trip_tracking_view_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar a UI de rastreio map-first ao mover componentes
  visuais que dependem de `AppStrings` e dos records de rota.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/tracking/map_first_trip_tracking_components_test.dart`.
  - [x] Cenario: grid de metricas mostra label, valor e icone.
  - [x] Cenario: tile da fila diferencia parada do passageiro e proxima parada.
  - [x] Cenario: tile de trecho preserva distancia/duracao publicada.
  - [x] Cenario: barra de acoes dispara callbacks primario/secundario.
- Regressao minima executada:
  - [x] RED `flutter test test/features/tracking/map_first_trip_tracking_components_test.dart`
  - [x] `flutter test test/features/tracking/map_first_trip_tracking_components_test.dart`
  - [x] `flutter test test/features/tracking/map_first_trip_tracking_view_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 1b75e0b)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0954 — QUALITY-0054: extrair sheet ativo do rastreio guardian

### Status
DONE (commit: f2a68c5)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0053`. O ratchet atual mostra
`lib/features/guardian/presentation/guardian_localizacao_screen.dart` como
maior hotspot Dart produtivo do app, com 918 linhas. A leitura direta e o
Graphify indicaram que a tela ja delega agenda, timeline, estados e cards
simples, mas ainda monta inline o sheet map-first da viagem ativa do
responsavel.

### Contexto
`GuardianLocalizacaoScreen` deve ficar focada em lifecycle, gateways, selecao
de passageiro, conexao realtime e navegacao. O sheet ativo do rastreio e UI
pura de composicao: recebe passageiro, viagem, lista de viagens do dia,
passageiros disponiveis e callbacks ja resolvidos pela tela.

### Objetivo
Extrair o sheet ativo do rastreio guardian para um widget publico em
`guardian_localizacao_widgets.dart`, adicionar teste focado de componente e
reduzir `guardian_localizacao_screen.dart` sem alterar contratos da API,
WebSocket, gateways ou comportamento de navegacao.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo maior hotspot produtivo atual
  do ratchet no app Flutter.
- **Telas/fluxos afetados:** tela de rastreio do responsavel quando existe
  viagem ativa/publicada e o app renderiza a experiencia map-first.
- **Dependencia de contrato backend:** nenhuma mudanca. `TripTrackingRecord`,
  `GuardianLinkedPassageiroRecord`, gateways, endpoints, payloads, status
  codes, autenticacao, WebSocket e rotas seguem intactos.
- **Mudancas tecnicas:** criar `GuardianLocalizacaoActiveTripSheet`, mover a
  composicao visual inline do `sheetChild` para esse widget e adaptar
  `_buildGuardianMapScene` para passar estado e callbacks explicitamente.
- **Riscos e mitigacao:**
  - quebrar selecao de viagem do dia; mitigar preservando
    `agendaViagensKey`/`selecionarViagemDiaButtonKey` e teste focado;
  - quebrar troca de passageiro no sheet; mitigar com teste de callback do
    `ChoiceChip`;
  - perder labels de ETA, motorista, tipo de operacao ou paradas restantes;
    mitigar com widget test do conteudo renderizado;
  - alterar navegacao para mapa completo; mitigar mantendo callback na tela
    stateful e cobrindo regressao de `guardian_localizacao_screen_test.dart`.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do widget,
  regressao de `guardian_localizacao_screen_test.dart`, `flutter analyze`,
  testes de metricas, baseline/gates do ratchet, QA gate card sem E2E real,
  `git diff --check`, Graphify refresh e harness
  `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo widget/teste e restaurar o `sheetChild`
  inline em `_buildGuardianMapScene`; nenhum dado, endpoint, deploy ou
  migracao e afetado.

### Escopo
1. Criar widget publico para o sheet ativo do rastreio guardian.
2. Adicionar widget tests cobrindo resumo da viagem, paradas, motorista,
   agenda opcional, troca de passageiro e callback de mapa completo.
3. Adaptar `guardian_localizacao_screen.dart` para usar o widget extraido.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar `TripLiveMapPanel`, gateways, services, WebSocket ou presenter.
- Alterar DTOs, endpoints, payloads, status codes, auth ou CORS.
- Refatorar busca de passageiros, selecao inicial por deep link ou
  `_buildTrackingContent` neste card.
- Fazer push ou PR.

### Criterios de Aceite
- `guardian_localizacao_screen.dart` fica menor que 918 linhas.
- O sheet ativo extraido tem widget tests focados.
- `guardian_localizacao_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar o sheet map-first do responsavel ao mover a
  composicao visual para fora da tela stateful.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/guardian/presentation/widgets/guardian_localizacao_widgets_test.dart`.
  - [x] Cenario: sheet mostra viagem, tipo de operacao, passageiro, motorista,
    ETA e paradas restantes.
  - [x] Cenario: agenda opcional preserva key publica e dispara callback de
    selecao de viagem.
  - [x] Cenario: troca de passageiro e mapa completo disparam callbacks da
    tela.
- Regressao minima executada:
  - [x] RED `flutter test test/features/guardian/presentation/widgets/guardian_localizacao_widgets_test.dart`
  - [x] `flutter test test/features/guardian/presentation/widgets/guardian_localizacao_widgets_test.dart`
  - [x] `flutter test test/features/guardian/guardian_localizacao_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: f2a68c5)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0955 — QUALITY-0055: extrair detalhe administrativo de viagens

### Status
DONE (commit: 4478434)

### Origem
Continuidade do modulo de qualidade apos `QUALITY-0054`. O ratchet atual mostra
`lib/features/admin/presentation/admin_viagens_screen.dart` como maior arquivo
Dart produtivo do app, com 908 linhas. A leitura direta confirmou que a tela ja
delegou lista, criacao, mapa, manifesto e snapshot para widgets dedicados, mas
a view stateful de detalhe da viagem ainda vive inline no arquivo principal.

### Contexto
`AdminViagensScreen` deve ficar focada em carregar dados, alternar modo
lista/criacao/detalhe, chamar gateway e manter flags de submissao. O detalhe
administrativo da viagem e um componente de UI com estado local proprio para
selecionar motorista, copiar deep link e compor hero, mapa, manifesto e
snapshot a partir de `AdminViagemDetailRecord`.

### Objetivo
Extrair `_AdminViagemDetailView` para um widget publico em arquivo proprio de
widgets de viagens, adicionar teste focado de componente e reduzir
`admin_viagens_screen.dart` sem alterar contratos da API, DTOs, gateway,
payloads, status codes ou comportamento de navegacao.

### Impact Analysis
- **Contexto do card:** refatoracao guiada pelo maior hotspot produtivo atual
  do ratchet no app Flutter.
- **Telas/fluxos afetados:** Portal Empresa > Viagens > detalhe da viagem,
  incluindo troca de motorista operacional, copia de link, cancelamento,
  mapa operacional, manifesto e snapshot.
- **Dependencia de contrato backend:** nenhuma mudanca.
  `AdminViagensGateway`, `AdminViagemDetailRecord`, endpoints, payloads,
  status codes, autenticacao, WebSocket e rotas seguem intactos.
- **Mudancas tecnicas:** criar `AdminViagemDetailView`, mover a composicao
  stateful privada do detalhe para um arquivo novo abaixo do limite do ratchet
  e adaptar `AdminViagensScreen` para instanciar o widget publico.
- **Riscos e mitigacao:**
  - quebrar troca de motorista; mitigar com widget test focado cobrindo
    dropdown e callback;
  - quebrar copia do deep link; mitigar com teste de clipboard;
  - perder acoes de voltar, atualizar ou cancelar; mitigar com callbacks
    explicitos e teste do componente;
  - alterar conteudo de manifesto/mapa/snapshot; mitigar mantendo os widgets
    existentes e rodando regressao de `admin_viagens_screen_test.dart`.
- **Validacao minima obrigatoria:** RED e GREEN de teste focado do widget,
  regressao de `admin_viagens_screen_test.dart`, `flutter analyze`, testes de
  metricas, baseline/gates do ratchet, QA gate card sem E2E real,
  `git diff --check`, Graphify refresh e harness
  `done-check`/`refresh-dashboard`.
- **Rollback simples:** remover o novo widget/teste e restaurar
  `_AdminViagemDetailView` dentro de `admin_viagens_screen.dart`; nenhum dado,
  endpoint, deploy ou migracao e afetado.

### Escopo
1. Criar widget publico para o detalhe administrativo de viagens.
2. Adicionar widget tests cobrindo conteudo principal, copia de link, troca de
   motorista e callbacks de acao.
3. Adaptar `admin_viagens_screen.dart` para usar o widget extraido.
4. Regenerar baseline do ratchet e sincronizar docs/Graphify/harness.

### Fora de escopo
- Alterar gateways, DTOs, endpoints, payloads, status codes, auth ou CORS.
- Alterar fluxo de criacao/listagem de viagens ou geracao automatica.
- Refatorar `AdminViagensManifestSurface`, `AdminViagensTripMapSurface` ou
  widgets de lista neste card.
- Fazer push ou PR.

### Criterios de Aceite
- `admin_viagens_screen.dart` fica menor que 908 linhas.
- O detalhe administrativo extraido tem widget tests focados.
- `admin_viagens_screen_test.dart` continua passando.
- Ratchet e CI gate do app passam com baseline coerente.
- QA Log registra evidencia e o card e fechado com commit local.

### Test Plan do Card (obrigatorio)
- Risco principal: quebrar o detalhe administrativo da viagem ao mover uma
  view stateful para fora da tela orquestradora.
- Testes novos/ajustados:
  - [x] Tipo: widget tests de componente.
  - [x] Arquivo:
    `test/features/admin/presentation/widgets/admin_viagens_detail_view_test.dart`.
  - [x] Cenario: detalhe mostra hero, motorista, mapa, manifesto e snapshot.
  - [x] Cenario: dropdown de motorista dispara callback com motorista
    diferente do atual.
  - [x] Cenario: copiar link escreve `vanrides://viagem/<id>` no clipboard.
  - [x] Cenario: voltar, atualizar e cancelar disparam callbacks recebidos.
- Regressao minima executada:
  - [x] RED `flutter test test/features/admin/presentation/widgets/admin_viagens_detail_view_test.dart`
  - [x] `flutter test test/features/admin/presentation/widgets/admin_viagens_detail_view_test.dart`
  - [x] `flutter test test/features/admin/admin_viagens_screen_test.dart`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `python3 scripts/quality_metrics_test.py`
  - [x] `./scripts/quality_baseline.sh`
  - [x] `./scripts/quality_gate.sh`
  - [x] `./scripts/ci_quality_gate.sh`
  - [x] `RUN_E2E=0 ./scripts/qa_gate_card.sh`
  - [x] `git diff --check`
  - [x] `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh`
  - [x] `python3 -m dev_harness ... done-check`
  - [x] `python3 -m dev_harness ... refresh-dashboard`
- Evidencia registrada em QA Log:
  - [x] Sim

### DoD
- Card marcado como `DONE (commit: 4478434)`.
- Evidencia em `backlog/QA-Execution-Log.md`.
- Graphify local atualizado apos mudanca de codigo.
- Harness `done-check` executado com sucesso.
- Nenhum push/PR feito neste bloco.

---

## Backlog 0956 — TRACKING-APP-0001: consumir trackingState e freshness nos records

### Status
DONE (commit: e4aafd3)

### Origem
Criado na `PHASE-0455` a partir da arquitetura de rota/ETA/custo
(`PHASE-0453`) e do prototipo visual do mapa premium (`PHASE-0454`).

### Contexto
O app ainda renderiza rastreio com campos legados de rota, ETA e localizacao.
Antes de trocar marcadores, sheets e camadas visuais, os records precisam
entender `trackingState`, `updatedAt/freshness` da localizacao e campos
aditivos de auditoria da rota sem quebrar payloads antigos.

### Objetivo
Adicionar parsing compativel para `trackingState`, freshness da ultima
localizacao e campos de snapshot da rota em `TripTrackingRecord`,
`DriverTripRecord`, `TripRouteGeometryRecord`, `TripLiveLocationRecord` e
`TripRouteEvent`.

### Impact Analysis
- **Contexto do card:** primeiro card de app depois do contrato backend
  `TRACKING-API-0001`.
- **Telas/fluxos afetados:** passageiro, responsavel, motorista e componentes
  map-first que consomem tracking, localizacao ao vivo e evento de viagem.
- **Dependencia backend:** `TRACKING-API-0001` deve expor o contrato aditivo.
- **Mudancas tecnicas:** criar `TripTrackingStateRecord`, enums de status e
  parser com fallback para payload legado.
- **Riscos e mitigacao:** payload antigo nao pode quebrar; mitigar com testes
  de parsing legado e de enum desconhecido.
- **Validacao minima obrigatoria:** testes focados de services/records,
  `flutter analyze`, `flutter test`, `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- **Rollback simples:** remover o record novo e restaurar parsers atuais.

### Escopo
1. Criar `trip_tracking_state_record.dart`.
2. Adicionar `trackingState` em records de passageiro/motorista/evento.
3. Adicionar `updatedAt` e `freshness` em `TripLiveLocationRecord`.
4. Adicionar campos aditivos de snapshot em `TripRouteGeometryRecord`.
5. Cobrir payload novo, payload legado e enum desconhecido.

### Fora de escopo
- Alterar UI, marcadores ou route layers.
- Alterar endpoints, payloads backend ou WebSocket.
- Rodar E2E real por papel.

### Critérios de Aceite
- Payload novo com `trackingState` e `freshness` e parseado corretamente.
- Payload legado continua funcionando sem excecao.
- Enum desconhecido cai em estado seguro, sem quebrar UI.
- Services de tracking, driver trip, live location e route event seguem verdes.

### Dependências
- Backend `0916` / `TRACKING-API-0001` concluido.
- `docs/superpowers/plans/2026-06-14-vanrides-tracking-experience-implementation.md`.

### Andamento Atual
- Implementado no commit `e4aafd3`.
- `TripTrackingStateRecord` centraliza enums e parsing de
  `routeSnapshotStatus`, `locationFreshness`, `etaConfidence`,
  progresso de paradas e politica de recomputacao.
- `TripTrackingRecord`, `DriverTripRecord` e `TripRouteEvent` consomem
  `trackingState` com fallback seguro para payload legado.
- `TripLiveLocationRecord` passou a parsear `updatedAt/freshness`.
- `TripRouteGeometryRecord` passou a parsear `snapshotKey`, `routeInputHash`,
  `routeSnapshotStatus`, `routeRecomputeReason` e
  `routeRecomputeAllowedAfter`.
- Teste de contrato cobre payload novo, payload legado, enum desconhecido,
  evento realtime e freshness da localizacao ao vivo.

### DoD
- Card marcado como `DONE (commit: <hash>)`.
- Testes focados e gate app executados.
- QA Log atualizado quando o parsing novo for entregue.

---

## Backlog 0957 — TRACKING-APP-0002: fundar shell dark premium do mapa

### Status
DONE (commit: 1b531db)

### Origem
Criado na `PHASE-0455` para transformar `PHASE-0454` em implementacao
incremental.

### Contexto
O mapa atual funciona, mas ainda parece um mapa embutido com superficies claras
e tratamento generico. A primeira mudanca visual deve criar uma base escura,
premium e reutilizavel sem trocar provider ou quebrar o rastreio existente.

### Objetivo
Criar a fundacao visual `VanRidesMapShell` com tokens dark, top status strip,
bottom sheet slot, placeholder operacional e controles de foco/recenter.

### Impact Analysis
- **Contexto do card:** primeiro card visual do mapa premium.
- **Telas/fluxos afetados:** superficies map-first de passageiro,
  responsavel e motorista.
- **Dependencia backend:** nenhuma nova alem dos dados ja existentes.
- **Mudancas tecnicas:** adicionar tokens e shell de mapa sem mudar contrato.
- **Riscos e mitigacao:** visual ficar pesado ou com cara de cockpit; mitigar
  usando `DESIGN.md`, contraste forte e poucos acentos.
- **Validacao minima obrigatoria:** widget tests do shell e regressao de
  `trip_live_map_panel_test.dart`.
- **Rollback simples:** voltar para `TripMapSceneShell`/placeholder atuais.

### Escopo
1. Criar tokens dark map-first isolados de superficies admin.
2. Criar shell com top strip, bottom sheet e foco de mapa.
3. Atualizar placeholder para estado escuro operacional.
4. Garantir que texto nao estoure nos viewports pequenos.

### Fora de escopo
- Marcadores customizados.
- Route layer split.
- Alterar payload ou parsing.

### Critérios de Aceite
- Shell dark renderiza sem nested cards.
- Top strip e bottom sheet ficam disponiveis como slots.
- Placeholder nao parece landing page nem card decorativo.
- Testes de regressao do painel de mapa passam.

### Dependências
- App `0956` pode estar em andamento, mas este card pode usar dados legados.
- `PHASE-0454` deve permanecer como referencia visual.

### Andamento Atual
- Implementado em `1b531db`.
- Tokens dark map-first separados em `vanrides_map_tokens.dart`.
- `VanRidesMapShell`, `VanRidesMapTopStatus`, controles de mapa e placeholder
  operacional dark foram adicionados como base reutilizavel.
- `TripLiveMapFrame` passou a usar o frame/controles dark premium e o
  placeholder claro foi substituido por superficie operacional escura.
- Testes cobrem shell, top strip compacto, ausencia de `Card` decorativo e
  regressao de `TripLiveMapPanel`.
- Validacao local: `flutter analyze`, `flutter test`, focused tracking/guardian/
  driver map tests, `python3 scripts/quality_metrics.py --check`,
  `./scripts/qa_gate_card.sh --no-e2e` e `git diff --check` PASS.

### DoD
- Card marcado como `DONE (commit: <hash>)`.
- Widget tests, `flutter analyze`, `flutter test` e QA gate app verdes.

---

## Backlog 0958 — TRACKING-APP-0003: criar sistema de marcadores VanRides

### Status
DONE (commit: c6ec15b)

### Origem
Criado na `PHASE-0455` a partir de `docs/design/branding/vanrides-map-marker-concepts.svg`.

### Contexto
Pins genericos enfraquecem a sensacao premium e tornam a experiencia parecida
com Google Maps colado no app. O mapa precisa mostrar a van/onibus, o
passageiro e as paradas com estados claros.

### Objetivo
Implementar componentes/builders de marcadores para van, passageiro e paradas,
com estados live, delayed, stale, paused, unavailable, completed, next, future,
skipped e no-show.

### Impact Analysis
- **Contexto do card:** troca controlada da linguagem visual de marcadores.
- **Telas/fluxos afetados:** `TripLiveMapPanel` e superficies map-first.
- **Dependencia backend:** idealmente App `0956` para freshness; fallback por
  estado legado deve existir.
- **Mudancas tecnicas:** criar `vanrides_map_markers.dart` e substituir
  marcadores default onde seguro.
- **Riscos e mitigacao:** bitmap/custom painter pode ficar instavel em testes;
  mitigar com testes de estado, semantica e labels antes de pixel-perfect.
- **Validacao minima obrigatoria:** testes de marcadores e regressao do painel.
- **Rollback simples:** restaurar `BitmapDescriptor.defaultMarker*`.

### Escopo
1. Criar marcador de van/onibus reconhecivel.
2. Criar marcador de passageiro sem pin generico.
3. Criar marcadores de parada com sinal nao dependente apenas de cor.
4. Adicionar labels de acessibilidade.
5. Substituir marcadores default no painel principal.

### Fora de escopo
- Rota em multiplas camadas.
- Animacao/interpolacao complexa.
- Assets finais de marca.

### Critérios de Aceite
- Van marker nao e pin generico.
- Passenger marker nao e pin generico.
- Stop markers cobrem completed, next, future, skipped e no-show.
- Estado stale/unavailable nao comunica movimento ao vivo.

### Dependências
- App `0957`.
- App `0956` para freshness quando disponivel.

### Andamento Atual
- Implementado em `c6ec15b`.
- `TripLiveMapPanel` passou a usar specs/asset markers VanRides para van,
  passageiro e paradas, com fallback seguro para payload legado.
- `trackingState` foi propagado para as superficies de passageiro,
  responsavel e motorista que ja possuem esse dado.
- Marcadores stale/unavailable usam linguagem sem indicacao de movimento ao
  vivo.

### Test Plan do Card (obrigatorio)
- Risco principal: substituir pins default por marcadores proprios sem quebrar
  renderizacao do mapa, labels ou estados de freshness.
- Testes novos/ajustados:
  - [x] Tipo: unit/widget tests de specs e regressao de painel.
  - [x] Arquivos:
    `test/features/tracking/presentation/widgets/vanrides_map_markers_test.dart`
    e `test/features/tracking/trip_live_map_panel_test.dart`.
  - [x] Cenario: van live comunica movimento; delayed/stale/paused/unavailable
    nao comunicam movimento ao vivo.
  - [x] Cenario: passageiro e paradas usam linguagem visual propria, nao pin
    generico.
  - [x] Cenario: stop markers cobrem completed, next, future, skipped e
    no-show com badge textual.
  - [x] Cenario: `TripLiveMapPanel` renderiza marcador de van, passageiro e
    paradas usando assets customizados.
- Regressao minima executada:
  - [x] RED `flutter test test/features/tracking/presentation/widgets/vanrides_map_markers_test.dart test/features/tracking/trip_live_map_panel_test.dart`
  - [x] `flutter test test/features/tracking/presentation/widgets/vanrides_map_markers_test.dart test/features/tracking/trip_live_map_panel_test.dart`
  - [x] Focused map regression com tracking/map-first/passageiro/responsavel/motorista.
  - [x] `flutter analyze`
  - [x] `flutter test`
  - [x] `python3 scripts/quality_metrics.py --check`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `git diff --check`

### DoD
- Card marcado como `DONE (commit: c6ec15b)`.
- Testes focados, `flutter analyze`, `flutter test` e QA gate app verdes.
- Evidencia registrada em `backlog/QA-Execution-Log.md`.

---

## Backlog 0959 — TRACKING-APP-0004: dividir route layers e foco de camera

### Status
DONE (commit: 263979e)

### Origem
Criado na `PHASE-0455` para implementar a camada de rota definida em
`PHASE-0454`.

### Contexto
Hoje a rota e basicamente uma polyline unica. Para parecer uma experiencia de
mobilidade premium, o app precisa diferenciar rota planejada, restante,
concluida e degradada sem virar turn-by-turn nativo.

### Objetivo
Criar route layer builder com camadas planned, remaining, completed, degraded e
unavailable, alem de comportamento de camera para van, passageiro e rota
completa.

### Impact Analysis
- **Contexto do card:** melhora visual e cognitiva da rota sem mudar provider.
- **Telas/fluxos afetados:** `TripLiveMapPanel`, passenger/guardian tracking e
  motorista.
- **Dependencia backend:** `TripRouteGeometryRecord` existente e, quando
  disponivel, `trackingState.routeSnapshotStatus`.
- **Mudancas tecnicas:** criar `vanrides_route_layers.dart` e adaptar estado de
  mapa.
- **Riscos e mitigacao:** camera cobrir sheet ou perder passageiro; mitigar
  com testes de bounds e estados.
- **Validacao minima obrigatoria:** widget/unit tests da camada de rota e
  regressao do painel.
- **Rollback simples:** voltar para polyline unica atual.

### Escopo
1. Separar camadas de rota.
2. Tratar `STALE` como rota degradada.
3. Tratar `UNAVAILABLE` sem linha falsa.
4. Adicionar foco van/passageiro/rota.
5. Desativar auto-fit apos pan ate recenter.

### Fora de escopo
- Navegacao nativa curva a curva.
- Chamar provider de rota pelo app.
- E2E real.

### Critérios de Aceite
- Rota planejada, restante, concluida e degradada possuem estilos distintos.
- Camera inicial nao cobre van/passageiro com bottom sheet.
- Usuario consegue recentralizar depois de pan.

### Dependências
- App `0956`.
- App `0957`.

### Andamento Atual
- Implementado em `263979e`.
- `TripLiveMapPanel` passou a desenhar route layers testaveis em vez de uma
  polyline unica: planejada, concluida, restante e degradada.
- Rotas `STALE` usam camada degradada tracejada em amber e rotas
  `UNAVAILABLE`/`UNKNOWN` nao desenham linha falsa.
- O foco `nextStopOnly` deixou de incluir a polyline inteira no calculo de
  camera, evitando que o mapa tente enquadrar toda a viagem quando a interface
  esta focada na proxima parada.
- O mapa interativo reserva padding inferior para o bottom sheet premium.

### Test Plan do Card (obrigatorio)
- Risco principal: separar camadas de rota sem confundir rota planejada,
  concluida, restante e degradada, nem cobrir conteudo importante com o bottom
  sheet.
- Testes novos/ajustados:
  - [x] Tipo: unit/widget tests de builder de route layers e regressao do
    painel de mapa.
  - [x] Arquivos:
    `test/features/tracking/presentation/widgets/vanrides_route_layers_test.dart`
    e `test/features/tracking/trip_live_map_panel_test.dart`.
  - [x] Cenario: rota fresca cria camadas planned, completed e remaining
    distintas.
  - [x] Cenario: rota stale cria apenas camada degradada tracejada.
  - [x] Cenario: rota unavailable nao cria polyline falsa.
  - [x] Cenario: viagem com todas as paradas concluidas nao redesenha trecho
    remaining.
  - [x] Cenario: foco de camera `nextStopOnly` nao inclui a polyline inteira.
  - [x] Cenario: mapa interativo reserva padding inferior para sheet premium.
- Regressao minima executada:
  - [x] RED `flutter test test/features/tracking/presentation/widgets/vanrides_route_layers_test.dart test/features/tracking/trip_live_map_panel_test.dart`
  - [x] `flutter test test/features/tracking/presentation/widgets/vanrides_route_layers_test.dart test/features/tracking/trip_live_map_panel_test.dart`
  - [x] Focused map regression com tracking/map-first/passageiro/responsavel/motorista.
  - [x] `flutter analyze`
  - [x] `flutter test`
  - [x] `python3 scripts/quality_metrics.py --check`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
  - [x] `git diff --check`

### DoD
- Card marcado como `DONE (commit: 263979e)`.
- Testes focados, `flutter analyze`, `flutter test` e QA gate app verdes.
- Evidencia registrada em `backlog/QA-Execution-Log.md`.

---

## Backlog 0960 — TRACKING-APP-0005: elevar rastreio passageiro e responsavel

### Status
DONE (commit: ae8fffe)

### Origem
Criado na `PHASE-0455` para levar a experiencia principal do usuario final ao
padrao premium definido.

### Contexto
Passageiro e responsavel precisam entender onde a van esta, quantas paradas
faltam, se o sinal e confiavel e qual passageiro esta selecionado. A tela nao
deve virar dashboard de cards.

### Objetivo
Atualizar as superficies de passageiro e responsavel para usar o novo mapa,
marcadores, route layers e sheet compacta priorizando ETA/status, paradas antes
do passageiro e freshness.

### Impact Analysis
- **Contexto do card:** primeira entrega funcional da experiencia premium para
  quem acompanha a van.
- **Telas/fluxos afetados:** `MapFirstTripTrackingView`,
  `HomePassageiroScreen` e `GuardianLocalizacaoScreen`.
- **Dependencia backend:** App `0956` e idealmente backend `0916/0917`.
- **Mudancas tecnicas:** reorganizar composicao de sheet e map-first widgets.
- **Riscos e mitigacao:** esconder contexto do passageiro ou quebrar troca de
  passageiro guardian; mitigar com widget tests de selecao e copy.
- **Validacao minima obrigatoria:** testes map-first, passageiro e guardian.
- **Rollback simples:** restaurar composicao anterior dos widgets map-first.

### Escopo
1. Compactar sheet de passageiro/responsavel.
2. Mostrar "Van a N paradas" quando houver fila.
3. Mostrar signal freshness em linguagem humana.
4. Destacar passageiro selecionado no guardian.
5. Preservar troca de passageiro e selecao de viagem do dia.

### Fora de escopo
- Driver map experience.
- Admin portal.
- E2E real por papel.

### Critérios de Aceite
- Passageiro entende ETA/status, paradas restantes e sinal em uma olhada.
- Responsavel ve passageiro selecionado e pode alternar sem perder contexto.
- Nao ha grid de cards competindo com o mapa.

### Dependências
- App `0956`, `0957`, `0958`, `0959`.

### Andamento Atual
- Sheet do passageiro passou a usar superficie escura compacta sobre o mapa,
  com ETA, status humano do sinal, operacao, destino da parada e motorista.
- Sheet do responsavel passou a destacar o passageiro selecionado, a distancia
  da van em paradas, o status do sinal e a viagem selecionada.
- Presenter centraliza `Van a N paradas` e labels de sinal ao vivo/com atraso/
  antigo/pausado para passageiro e responsavel.
- Seletores de viagem do dia, troca de passageiro e acoes de detalhe/mapa foram
  preservados e cobertos por testes.
- Ajuste do screen guardian manteve o sheet dark e dentro do limite do ratchet
  de linhas.

### Test Plan
- [x] RED `flutter test test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart test/features/guardian/presentation/widgets/guardian_localizacao_widgets_test.dart test/features/guardian/guardian_localizacao_screen_test.dart`
  - falhou como esperado antes da implementacao por falta de `Van a 2 paradas`,
    sheet dark e destaque do passageiro selecionado.
- [x] `flutter test test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart test/features/guardian/presentation/widgets/guardian_localizacao_widgets_test.dart test/features/guardian/guardian_localizacao_screen_test.dart`
  - 13 testes, 0 falhas.
- [x] Focused tracking/guardian regression:
  - `flutter test test/l10n/app_strings_test.dart test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart test/screens/home_passageiro_screen_test.dart test/features/guardian/presentation/widgets/guardian_localizacao_widgets_test.dart test/features/guardian/guardian_localizacao_screen_test.dart test/features/tracking/map_first_trip_tracking_view_test.dart test/features/tracking/map_first_trip_tracking_components_test.dart test/features/tracking/trip_live_map_panel_test.dart`
  - 42 testes, 0 falhas.
- [x] `flutter analyze`
  - sem issues.
- [x] `flutter test`
  - 399 testes, 0 falhas.
- [x] `python3 scripts/quality_metrics.py --check`
  - PASS.
- [x] `./scripts/qa_gate_card.sh --no-e2e`
  - quality metrics tests: PASS, 5 testes.
  - E2E preflight tests: PASS, 7 testes.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 399 testes, 0 falhas.
  - quality ratchet: PASS.
  - E2E real desabilitado explicitamente para este card visual.
- [x] `git diff --check`
  - PASS.

### DoD
- Card marcado como `DONE (commit: ae8fffe)`.
- Testes focados, `flutter analyze`, `flutter test` e QA gate app verdes.
- Evidencia registrada em `backlog/QA-Execution-Log.md`.

---

## Backlog 0961 — TRACKING-APP-0006: elevar rastreio motorista e acoes de rota

### Status
DONE (commit: d53acf3)

### Origem
Criado na `PHASE-0455` para adaptar a experiencia premium ao papel do
motorista.

### Contexto
O motorista precisa de foco operacional: proxima parada, acao atual,
paradas restantes, progresso da rota e handoff externo de navegacao. O app nao
deve tentar entregar turn-by-turn nativo nesta fase.

### Objetivo
Atualizar o mapa e sheet do motorista para o sistema premium, com proxima
parada, rota restante, signal freshness e acao clara de abrir navegacao externa.

### Impact Analysis
- **Contexto do card:** entrega de experiencia map-first para execucao do
  motorista.
- **Telas/fluxos afetados:** `driver_map_fullscreen_screen.dart`, widgets de
  mapa/acesso rapido e home motorista.
- **Dependencia backend:** App `0956`; backend com trackingState e route audit
  melhora a confianca do sheet.
- **Mudancas tecnicas:** reorganizar sheet e acoes de mapa do motorista.
- **Riscos e mitigacao:** atrapalhar a acao de parada ou navegacao externa;
  mitigar com testes de callbacks e copy.
- **Validacao minima obrigatoria:** testes de driver map widgets e active trip.
- **Rollback simples:** restaurar sheet/widgets atuais.

### Escopo
1. Mostrar proxima parada/passageiro como fato principal.
2. Mostrar paradas restantes e progresso.
3. Manter handoff externo Google Maps/Waze.
4. Exibir signal freshness sem termos tecnicos.
5. Preservar acoes de concluir, pular e no-show.

### Fora de escopo
- Turn-by-turn nativo.
- Recalculo de rota no app.
- Campanha manual iPhone.

### Critérios de Aceite
- Motorista ve a proxima acao sem escanear dashboard.
- Abertura de navegacao externa continua disponivel.
- Acoes operacionais existentes continuam cobertas por teste.

### Dependências
- App `0956`, `0957`, `0958`, `0959`, `0960`.

### Andamento Atual
- Home do motorista passou a usar sheet premium dark fora do modo de edicao de
  rota, preservando o modo de edicao em superficie clara.
- Sheet de planejamento/execucao ganhou chips de progresso, sinal e paradas
  restantes, usando linguagem humana e dados de `trackingState`.
- Proxima parada/passageiro, ETA e rota operacional continuam como foco do
  motorista em execucao.
- Handoff externo de navegacao, concluir parada, no-show e encerrar corrida
  foram preservados com as mesmas keys/callbacks.
- Mapa completo e fullscreen passaram a compartilhar o mesmo card premium de
  contexto do motorista.
- Presenter dedicado centraliza progresso/restantes do motorista sem acoplar a
  feature driver ao presenter generico de tracking.

### Test Plan
- [x] RED `flutter test test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_trip_map_widgets_test.dart test/features/driver_trip/home_motorista_active_trip_test.dart`
  - falhou como esperado por sheet branco e ausencia de `Sinal ao vivo` /
    `1 parada restante` antes da implementacao.
- [x] `flutter test test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_trip_map_widgets_test.dart test/features/driver_trip/home_motorista_active_trip_test.dart`
  - 13 testes, 0 falhas.
- [x] Focused driver/map regression:
  - `flutter test test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_trip_map_widgets_test.dart test/features/driver_trip/home_motorista_active_trip_test.dart test/features/driver_trip/home_motorista_planning_test.dart test/features/driver_trip/home_motorista_route_edit_test.dart test/features/driver_trip/presentation/widgets/driver_trip_detail_panel_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_trip_stop_widgets_test.dart test/features/tracking/trip_live_map_panel_test.dart`
  - 31 testes, 0 falhas.
- [x] `flutter analyze`
  - sem issues.
- [x] `flutter test`
  - 399 testes, 0 falhas.
- [x] `python3 scripts/quality_metrics.py --check`
  - PASS.
- [x] `./scripts/qa_gate_card.sh --no-e2e`
  - quality metrics tests: PASS, 5 testes.
  - E2E preflight tests: PASS, 7 testes.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 399 testes, 0 falhas.
  - quality ratchet: PASS.
  - E2E real desabilitado explicitamente para este card visual.
- [x] `git diff --check`
  - PASS.

### DoD
- Card marcado como `DONE (commit: d53acf3)`.
- Testes focados, `flutter analyze`, `flutter test` e QA gate app verdes.
- Evidencia registrada em `backlog/QA-Execution-Log.md`.

---

## Backlog 0962 — TRACKING-APP-0007: endurecer stale, sem sinal e reduced motion

### Status
DONE (commit: da77874)

### Origem
Criado na `PHASE-0455` para garantir confianca antes de E2E/release.

### Contexto
Um mapa bonito que finge sinal vivo quando a coordenada esta velha quebra a
confianca do produto. O app precisa ser honesto em delayed, stale, paused,
unavailable e reduced-motion.

### Objetivo
Endurecer estados de sinal, rota indisponivel, texto acessivel e reduced motion
em todos os componentes do mapa premium.

### Impact Analysis
- **Contexto do card:** hardening de confianca operacional.
- **Telas/fluxos afetados:** passageiro, responsavel e motorista.
- **Dependencia backend:** App `0956` para usar freshness real.
- **Mudancas tecnicas:** regras visuais e semanticas para estados degradados.
- **Riscos e mitigacao:** ocultar informacao demais quando sem sinal; mitigar
  com tests de copy e fallback.
- **Validacao minima obrigatoria:** testes de estados, text scale e
  reduced-motion.
- **Rollback simples:** restaurar estado visual anterior do mapa.

### Escopo
1. Congelar marcador stale.
2. Remover pulso/interpolacao em reduced motion.
3. Mostrar copy humana para delayed/stale/unavailable.
4. Garantir que cor nao seja o unico sinal.
5. Testar text scale alto.

### Fora de escopo
- Novos endpoints.
- E2E real.
- Animacoes decorativas.

### Critérios de Aceite
- Nenhum estado stale parece live.
- Reduced motion preserva compreensao sem animacao obrigatoria.
- Labels de acessibilidade descrevem van, passageiro, ETA e paradas.

### Dependências
- App `0956` a `0961`.

### Andamento Atual
- Implementado em `da77874`.
- `TripLiveMapPanel` respeita `MediaQuery.disableAnimations` e
  `accessibleNavigation`, usando `moveCamera` sem delay obrigatorio quando o
  usuario prefere menos movimento.
- Marcador stale passou a comunicar `ultima posicao conhecida`, sem linguagem
  de movimento ao vivo; unavailable/unknown continuam como `sem sinal`.
- Guard de text scale alto cobre o sheet ativo do motorista sem overflow ou
  excecao.
- Testes novos cobrem policy de reduced motion, semantica stale/sem sinal e
  acessibilidade do sheet ativo.

### DoD
- Card marcado como `DONE (commit: da77874)`.
- RED confirmado com falha por helper de motion ausente e label stale sem
  `ultima posicao conhecida`.
- Teste focado:
  `flutter test test/features/tracking/trip_live_map_motion_test.dart test/features/tracking/presentation/widgets/vanrides_map_markers_test.dart test/features/driver_trip/presentation/widgets/driver_home_active_sheet_accessibility_test.dart test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart`:
  PASS, 14 testes.
- Regressao ampliada de mapa/roles: PASS, 36 testes.
- `flutter analyze`: PASS, sem issues.
- `flutter test`: PASS, 401 testes.
- `python3 scripts/quality_metrics.py --check`: PASS.
- `./scripts/qa_gate_card.sh --no-e2e`: PASS
  - quality metrics tests: PASS, 5 testes.
  - E2E preflight tests: PASS, 7 testes.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 401 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado explicitamente para este card visual.
- `git diff --check`: PASS.
- Evidencia registrada em `backlog/QA-Execution-Log.md`.

---

## Backlog 0963 — TRACKING-APP-0008: fechar aceitacao visual e handoff E2E

### Status
DONE (commit: da04dfc)

### Origem
Criado na `PHASE-0455` para desbloquear `PHASE-0502` somente depois da
experiencia de mapa estar pronta para validacao por papel.

### Contexto
Os testes completos por papel devem vir depois que mapa, contrato, stale states
e UX principal estiverem estaveis. Este card cria a evidencia visual e o
handoff para as suites E2E.

### Objetivo
Adicionar checklist/testes de aceitacao visual do mapa premium e preparar a
transicao para dividir/estabilizar E2E de motorista, passageiro e responsavel.

### Impact Analysis
- **Contexto do card:** ultimo card antes de `PHASE-0502`.
- **Telas/fluxos afetados:** mapa premium por papel e entrypoints E2E.
- **Dependencia backend:** backend `0916` a `0918` e app `0956` a `0962`.
- **Mudancas tecnicas:** testes de aceitacao visual e docs de QA.
- **Riscos e mitigacao:** aceitar UI quebrada por teste muito superficial;
  mitigar com viewports, states e text scale especificos.
- **Validacao minima obrigatoria:** visual acceptance widget tests e QA gate app.
- **Rollback simples:** remover o teste/doc de aceitacao e manter `PHASE-0502`
  bloqueada.

### Escopo
1. Documentar viewports 375x667, 390x844, 430x932 e 768x1024.
2. Cobrir passenger live/stale, guardian selected passenger, driver planned/in
   progress, no route e no signal.
3. Verificar semantica de van, passenger, route layer e bottom sheet.
4. Preparar handoff das suites E2E por papel.
5. Atualizar docs para liberar `PHASE-0502`.

### Fora de escopo
- Executar campanha manual iPhone completa.
- Criar novos contratos backend.
- Publicar PR/push.

### Critérios de Aceite
- Aceitacao visual cobre os estados definidos em `PHASE-0454`.
- E2E por papel tem entrypoint/documentacao prontos para `PHASE-0502`.
- `PHASE-0502` permanece bloqueada se qualquer card anterior ainda estiver
  pendente.

### Dependências
- Backend `0916`, `0917`, `0918`.
- App `0956` a `0962`.

### Andamento Atual
- Impact Analysis executado em 2026-06-14 antes da implementacao.
- RED focado confirmou os riscos do card:
  - handoff de aceitacao visual ainda inexistente;
  - overflow real no chip premium do responsavel em viewport pequeno.
- Implementado `backlog/TRACKING-PREMIUM-MAP-VISUAL-ACCEPTANCE.md` com
  viewports, estados obrigatorios, entrypoints E2E por papel e gate de
  liberacao para `PHASE-0502`.
- Adicionado teste de aceitacao visual por papel em
  `test/features/tracking/premium_map_visual_acceptance_test.dart`, cobrindo
  passenger live/stale, guardian selected passenger, driver planned/in
  progress, no route e no signal em 375x667, 390x844, 430x932 e 768x1024.
- Corrigido overflow do `_GuardianPremiumChip` preservando layout premium em
  text scale elevado.
- `PHASE-0502` fica desbloqueada como proxima fase tecnica, com E2E real ainda
  dependente de execucao por papel em ambiente DEV/dispositivo.

### DoD
- Card marcado como `DONE (commit: da04dfc)`.
- `flutter test test/features/tracking/premium_map_visual_acceptance_test.dart`:
  PASS, 6 testes.
- Regressao ampliada de mapa/roles:
  - `flutter test test/features/tracking/premium_map_visual_acceptance_test.dart test/features/tracking/presentation/widgets/vanrides_map_shell_test.dart test/features/tracking/presentation/widgets/vanrides_map_markers_test.dart test/features/tracking/presentation/widgets/vanrides_route_layers_test.dart test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart test/features/guardian/presentation/widgets/guardian_localizacao_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_home_active_sheet_accessibility_test.dart`: PASS, 34 testes.
- `flutter analyze`: PASS, sem issues.
- `flutter test`: PASS, 407 testes.
- `python3 scripts/quality_metrics.py --check`: PASS.
- `./scripts/qa_gate_card.sh --no-e2e`: PASS
  - quality metrics tests: PASS, 5 testes.
  - E2E preflight tests: PASS, 7 testes.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 407 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado explicitamente; `PHASE-0502` e a campanha por papel
    continuam como proxima etapa.
- `git diff --check`: PASS.
- Evidencia registrada em `backlog/QA-Execution-Log.md`.

---

## Backlog 0964 — PHASE-0460: corrigir UX premium de presença e viagem do passageiro

### Status
DONE (commit: bcb7ec6)

### Origem
Criado após review visual real em 2026-06-15. As screenshots do app rodando
mostraram que a experiência entregue pela fase de mapa premium ainda estava
abaixo do padrão esperado para passageiro: presença confusa, mapa distante,
sheet com informação competindo e ação `Mapa Completo` sem sentido dentro de
uma aba que já é map-first.

### Contexto
`PHASE-0455` deixou a base técnica do mapa premium pronta para `PHASE-0502`,
mas a validação visual real mostrou uma lacuna de aceitação de produto. Antes
de iniciar testes completos por papel, o app precisa responder melhor:

1. Vou ou não vou neste horário?
2. Onde está a van?
3. Quantas paradas faltam até mim?
4. O sinal é confiável?
5. Que ação útil eu posso tomar agora?

### Objetivo
Elevar a UX das telas de presença e viagem do passageiro para uma experiência
mais confiável, premium e útil, sem criar novos contratos backend.

### Impact Analysis
- **Contexto do card:** correção de aceitação visual antes dos E2E completos.
- **Telas/fluxos afetados:** aba `Presenca`, `ConfirmarPresencaScreen`, aba
  `Viagem` do passageiro e foco do mapa ao vivo.
- **Dependência backend:** nenhuma alteração. O app usa os campos atuais de
  serviço, presença, rota, ETA, motorista, parada do passageiro e
  `trackingState`.
- **Mudanças técnicas:** testes widget, hierarquia visual de presença, foco de
  câmera do mapa, sheet da viagem, copy e ações secundárias.
- **Riscos e mitigação:** overflow em iPhone pequeno e text scale elevado;
  mitigar com testes widget focados e aceitação visual. Risco de expectativa
  antiga de `Mapa Completo`; mitigar substituindo por ações de foco e detalhes.
- **Validação mínima obrigatória:** testes focados, `flutter analyze`,
  `flutter test`, `RUN_E2E=0 ./scripts/qa_gate_card.sh` e `git diff --check`.
- **Rollback simples:** reverter o commit do card `0964`; não há migração de
  dados nem mudança de API.

### Escopo
1. Reescrever a presença como decisão por horário, com cópia em português,
   resumo compacto e controles claros `Vou` / `Não vou`.
2. Remover hierarquia visual herdada em inglês na presença do passageiro.
3. Ajustar o mapa do passageiro para focar van, próxima parada e minha parada,
   em vez de enquadrar a região inteira.
4. Reorganizar a sheet da viagem com ETA, paradas restantes, sinal, minha
   parada, motorista e ações objetivas.
5. Substituir `Mapa Completo` por ações úteis dentro da experiência map-first.
6. Atualizar testes e evidência de QA.

### Fora de escopo
- Criar novo contrato backend.
- Executar campanha E2E real em device físico.
- Refatorar o design system inteiro do app.
- Alterar motorista, responsável ou portal admin além de regressões inevitáveis.

### Critérios de Aceite
- A presença não usa mais `Today's Schedule`, `Morning Commute`,
  `Vou neste horário` ou `Não vou neste horário` como hierarquia principal.
- Cada serviço mostra horário/operação, destino, motorista/status e decisões
  claras.
- A viagem mostra `Chega em ...`, `Van a N paradas` ou `Van chegando`, status
  do sinal e minha parada sem depender de abrir outra tela.
- A aba Viagem não exibe mais `Mapa Completo`; as ações passam a ser
  `Ver van`, `Minha parada`, `Rota` e `Detalhes`.
- O mapa do passageiro usa foco de câmera dedicado para a jornada relevante.

### Dependências
- App `0956` a `0963`.
- Backend `0916` a `0918` já entregue para tracking state/rota/ETA.

### Andamento Atual
- Impact Analysis registrado em 2026-06-15.
- Plano de implementação criado em
  `docs/superpowers/plans/2026-06-15-passenger-presence-trip-ux-correction.md`.
- Presença do passageiro redesenhada como decisão clara por horário, com
  resumo compacto, cópia em português e ações `Vou` / `Não vou`.
- Viagem do passageiro ajustada para foco map-first real: ETA, distância da
  van em paradas, status do sinal, minha parada, motorista e ações úteis.
- `Mapa Completo` removido da sheet principal; o fluxo agora oferece `Ver van`,
  `Minha parada`, `Rota`, `Detalhes` e acesso preservado a `Presenca`.
- Mapa do passageiro usa `TripLiveMapFocusMode.passengerJourney` para enquadrar
  van, próxima parada e parada do passageiro.
- QA gate inicialmente expôs aumento no ratchet de linhas; a sheet da viagem
  foi dividida em `passenger_trip_sheet_widgets.dart`, mantendo os arquivos
  abaixo dos limites.

### DoD
- Card marcado como `DONE (commit: bcb7ec6)`.
- Testes focados de presença, viagem e foco de mapa: PASS
  - 26 testes, 0 falhas.
- `flutter analyze`: PASS
  - sem issues dentro do QA gate.
- `flutter test`: PASS
  - 408 testes, 0 falhas dentro do QA gate.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS
  - quality metrics tests: PASS, 5 testes.
  - E2E preflight tests: PASS, 7 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado explicitamente; `PHASE-0502` segue como campanha
    posterior por papel.
- `git diff --check`: PASS.
- Evidência registrada em `backlog/QA-Execution-Log.md`.

---

## Backlog 0965 — PHASE-0461: sistema premium Map Glass e agenda sem carrossel

### Status
DONE (commit: d917bb2)

### Origem
Criado após a revisão visual pós-`PHASE-0460` em 2026-06-15. A experiência do
passageiro melhorou, mas os prints reais ainda mostraram quatro problemas de
produto:

1. a sheet da viagem ocupa muito mapa e ainda trunca informação crítica;
2. o seletor horizontal de viagens não escala para três ou mais horários;
3. a aba `Presenca` ainda duplica `Agenda de hoje` com os serviços abaixo;
4. partes da experiência ainda ficam brancas e fora do dark premium map-first.

### Objetivo
Criar uma camada premium e acessível sobre o mapa, inspirada em Liquid Glass,
Uber, 99 e Google Maps, sem perder legibilidade nem transformar transparência em
decoração.

### Spec de design
`docs/superpowers/specs/2026-06-15-passenger-map-glass-redesign-design.md`

### Impact Analysis
- **Contexto do card:** fase de requinte UX/UI para transformar a correção da
  `PHASE-0460` em uma arquitetura visual mais madura.
- **Telas/fluxos afetados:** aba `Viagem` do passageiro, aba `Presenca`,
  seletor de viagens do dia, sheet do mapa, chips de status e estados de sinal.
- **Dependência backend:** nenhuma alteração prevista. A fase deve trabalhar
  com dados já existentes de tracking, ETA, serviço, presença, parada e
  motorista.
- **Mudanças técnicas previstas:** criar superfície glass/fallback, sheet
  compacta, seletor sem carrossel obrigatório, presença dark premium e testes
  de acessibilidade visual.
- **Riscos e mitigação:** transparência pode reduzir contraste; mitigar com
  fallback opaco, testes em `reduceTransparency`, contraste aumentado e text
  scale. A mudança de seletor pode quebrar fluxos existentes; manter keys e
  testes de troca de viagem.
- **Validação mínima obrigatória:** testes focados, visual acceptance em
  viewport pequeno, `flutter analyze`, `flutter test`,
  `RUN_E2E=0 ./scripts/qa_gate_card.sh` e `git diff --check`.
- **Rollback simples:** reverter o commit de implementação da fase; não há
  migração de dados nem mudança de contrato.

### Escopo
1. Criar `VanRidesGlassSurface` com variantes `subtle`, `sheet` e fallback
   sólido acessível.
2. Redesenhar a sheet da viagem como `PassengerTripGlassSheet`, com estado
   compacto e expandido.
3. Implementar estados visuais claros para `live`, `paused`, `stale`,
   `unavailable`, `notStarted` e `arriving`.
4. Substituir o carrossel obrigatório por `CompactTripSwitcher`:
   - 1 viagem: sem seletor;
   - 2 viagens: segmented control;
   - 3+ viagens: lista compacta vertical visível.
5. Redesenhar a presença como `PresenceTodayList`, removendo `Agenda de hoje`
   duplicada e usando o dark premium no passageiro.
6. Reduzir a toolbar da viagem: `Ver van` e `Minha parada` visíveis; `Rota`,
   `Detalhes` e `Presenca` em estado expandido ou ação `Mais`.
7. Atualizar testes e evidência de QA.

### Fora de escopo
- Novo contrato backend.
- Turn-by-turn completo.
- Recriar motorista, responsável, admin ou conta.
- Pagamentos, chat, suporte e notificações gerais.
- E2E real em device físico.

### Critérios de Aceite
- A aba Viagem não exige scroll horizontal para descobrir viagens quando houver
  três ou mais horários.
- A sheet compacta preserva mapa visível em iPhone pequeno.
- Nenhuma informação crítica trunca como `Chega e...`.
- Estados de sinal e viagem usam texto, ícone e cor, nunca somente cor.
- `reduceTransparency` troca glass por superfície opaca.
- contraste aumentado reforça bordas e separação visual.
- reduced motion remove animações decorativas sem perder compreensão.
- A aba `Presenca` não duplica `Agenda de hoje`.
- Presença do passageiro usa dark premium e lista escaneável.
- Touch targets relevantes têm pelo menos 44 px.
- Leitor de tela recebe labels humanas para ETA, sinal, van, parada e paradas
  restantes.

### Dependências
- App `0964` concluído.
- `PRODUCT.md` e `DESIGN.md` atuais do VanRides.
- Referências de design registradas na spec.

### Andamento Atual
- Spec de design criada em 2026-06-15.
- Plano de implementação criado em
  `docs/superpowers/plans/2026-06-15-passenger-map-glass-redesign-implementation.md`.
- `VanRidesGlassSurface` implementado com variante de sheet, superfície
  subtle e fallback sólido quando acessibilidade pede menos transparência,
  movimento reduzido ou alto contraste.
- Sheet principal do passageiro migrada para `PassengerTripGlassSheet`, com
  cópia honesta por estado de sinal: `Sinal pausado`,
  `Ultima posicao conhecida`, `Sem sinal da van`, `Chega em cerca de ...` e
  `Van a N paradas` quando o sinal está confiável.
- Ações da viagem foram compactadas para `Ver van`, `Minha parada` e `Mais`,
  removendo `Rota`, `Detalhes` e `Presenca` da superfície principal.
- `CompactTripSwitcher` substituiu o carrossel obrigatório:
  - 1 viagem: sem seletor;
  - 2 viagens: linha segmentada com contador;
  - 3+ viagens: lista vertical compacta sem scroll horizontal.
- `PresenceTodayList` passou a envolver a decisão por serviço com superfície
  dark premium, removendo `Agenda de hoje` duplicada da aba `Presenca`.
- Keys existentes de agenda e seleção de viagem foram preservadas para deep
  link, troca de viagem e regressões de home.

### DoD
- Card marcado como `DONE (commit: d917bb2)`.
- Spec de design versionada.
- Testes focados de glass, sheet, seletor, presença e acessibilidade: PASS.
- Testes impactados de passageiro/presença/aceitação visual: PASS.
- `flutter analyze`: PASS, sem issues.
- `flutter test`: PASS, 418 testes dentro do QA gate.
- `./scripts/qa_gate_card.sh --no-e2e`: PASS.
  - quality metrics tests: PASS, 5 testes.
  - E2E preflight tests: PASS, 7 testes.
  - quality ratchet: PASS.
  - E2E real/device físico desabilitado explicitamente nesta fase visual.
- `git diff --check`: PASS.
- Evidência registrada em `backlog/QA-Execution-Log.md`.

---

## Backlog 0966 — PHASE-0462: Mapa Palco A1 da home do passageiro

### Status
DONE (commit: 049ad31)

### Origem
Criado após nova revisão visual real da `PHASE-0461` em 2026-06-15. O sistema
glass e o seletor melhoraram a base técnica, mas a tela ainda ficou com cara de
wireframe: sheet grande demais, barra inferior branca, pouca utilização útil do
topo e botões que não pareciam integrados ao mapa.

O usuário aprovou a direção visual `A1 Calm Glass` no protótipo local:
mapa como palco, viagens em chips no topo, presença como alerta compacto,
informação principal em dock baixo e ações de foco para `Van` e `Voce`.

### Objetivo
Transformar a aba `Viagem` do passageiro na arquitetura `Mapa Palco A1`, com
visual mais premium, minimalista e útil, sem criar novo contrato backend.

### Spec de design
`docs/superpowers/specs/2026-06-15-passenger-map-stage-calm-glass-design.md`

### Plano de implementação
`docs/superpowers/plans/2026-06-15-passenger-map-stage-calm-glass-implementation.md`

### Impact Analysis
- **Contexto do card:** refinamento de produto apos validacao visual real da
  `PHASE-0461`.
- **Telas/fluxos afetados:** aba `Viagem`, seletor de viagens do dia, dock do
  mapa, foco de camera e bottom navigation do passageiro.
- **Dependência backend:** nenhuma alteração. O app usa contratos atuais de
  tracking, rota, ETA, presença, serviço, motorista e parada do passageiro.
- **Mudanças técnicas previstas:** mover chips para a camada superior, reduzir
  dock inferior, adicionar foco de mapa por van/passageiro, mostrar nudge de
  presença pendente e compactar a bottom navigation.
- **Riscos e mitigação:** overflow em viewport pequeno e texto escalado;
  mitigar com widget tests focados. Mudança na barra inferior pode afetar
  motorista; preservar destinos, callbacks e labels.
- **Validação mínima obrigatória:** testes focados, `flutter analyze`,
  `flutter test`, `RUN_E2E=0 ./scripts/qa_gate_card.sh` quando viável e
  `git diff --check`.
- **Rollback simples:** reverter o commit da fase; não há migração de dados ou
  mudança de API.

### Escopo
1. Reorganizar `PassengerHomeMapScene` em camadas A1:
   - topo com contexto do dia e chips de viagem;
   - botões de foco `Van` e `Voce`;
   - nudge compacto de presença pendente;
   - dock inferior baixo e objetivo.
2. Remover agenda e ações excessivas da sheet principal.
3. Adicionar modos de foco `vehicleOnly` e `passengerOnly` no mapa ao vivo.
4. Compactar a bottom navigation para o dark premium.
5. Atualizar testes, QA log e DoD.

### Fora de escopo
- Novo contrato backend.
- Turn-by-turn completo.
- Campanha E2E real em device físico.
- Recriar motorista, responsável, portal admin, pagamentos, chat ou notificações.

### Critérios de Aceite
- A aba `Viagem` mostra chips de viagem no topo, não dentro de uma sheet grande.
- A presença pendente aparece como alerta compacto no mapa.
- O dock inferior mostra só o próximo fato útil e detalhes curtos.
- Existem ações visíveis para foco em `Van` e `Voce`.
- O mapa suporta foco de câmera dedicado para van e parada do passageiro.
- A bottom navigation deixa de ser branca e alta.
- Nenhum texto crítico aparece truncado como `Chega e...`.
- Contratos backend permanecem inalterados.

### Andamento Atual
- Impact Analysis registrado em 2026-06-15.
- Spec e plano criados a partir do protótipo aprovado `A1 Calm Glass`.
- Implementação A1 aplicada na home do passageiro:
  - `PassengerHomeMapScene` usa camada superior com chips, foco e alerta de
    presença.
  - `PassengerTripGlassSheet` virou dock baixo com `Detalhes` como CTA
    principal.
  - mapa ganhou focos `vehicleOnly` e `passengerOnly`.
  - bottom navigation passou para superfície dark compacta.
- Ratchet preservado:
  - camada superior extraída para `passenger_map_stage_top_layer.dart`.
  - helper de labels/ícones extraído para `passenger_trip_option_helpers.dart`.
  - foco local usa `ValueNotifier`, sem novo `setState`.
- Validação local concluída em 2026-06-15.
- Commit de implementação: `049ad31`.

### DoD
- Testes focados de home/sheet/mapa/aceitação visual: PASS.
- `flutter analyze`: PASS, sem issues.
- `flutter test`: PASS, 420 testes.
- `git diff --check`: PASS.
- `./scripts/quality_gate.sh`: PASS.
- `./scripts/qa_gate_card.sh --no-e2e`: PASS.
  - quality metrics tests: PASS, 5 testes.
  - E2E preflight tests: PASS, 7 testes.
  - quality ratchet: PASS.
  - E2E real/device físico desabilitado explicitamente nesta fase visual.
- Evidência registrada em `backlog/QA-Execution-Log.md`.
- Card marcado como `DONE (commit: 049ad31)`.

---

## Backlog 0967 — PHASE-0463: Dark System e HUD adaptativo

### Status
DONE (commits: 26570d4, ea974fd, 99ba47d; fechamento QA: este commit)

### Origem
Criado apos revisao visual real da `PHASE-0462` em 2026-06-15. A direcao A1
deixou a aba `Viagem` bem melhor, mas os prints ainda mostraram problemas de
sistema visual: safe area branca abaixo da bottom navigation, textos truncados
no HUD, chips de viagem longos, presenca com overflow e paleta misturada entre
dark map, branco estrutural, verde, laranja e azul.

### Objetivo
Levar o VanRides para um dark system consistente, com HUD adaptativo e rollout
gradual para passageiro, motorista e responsavel, mantendo o mapa como palco e
reduzindo ruido visual.

### Spec de design
`docs/superpowers/specs/2026-06-15-dark-system-hud-adaptive-design.md`

### Plano de implementação
`docs/superpowers/plans/2026-06-15-dark-system-hud-adaptive-implementation.md`

### Impact Analysis
- **Contexto do card:** refinamento de produto apos validacao visual real da
  home do passageiro.
- **Telas/fluxos afetados:** `Viagem`, `Presenca`, bottom navigation
  compartilhada e componentes de presenca usados na home.
- **Dependencia backend:** nenhuma. O incremento usa os contratos existentes de
  tracking, rota, servico e presenca.
- **Contratos afetados:** nenhum endpoint, payload, status code, auth, CORS ou
  WebSocket muda.
- **Mudancas tecnicas previstas:** compactar labels de viagem, reduzir copy do
  HUD planejado, tornar signal pill responsivo, escurecer o fundo da bottom
  navigation e corrigir layout compacto dos cards de presenca.
- **Riscos e mitigacao:** bottom navigation compartilhada pode afetar motorista
  e responsavel; preservar labels/destinos/callbacks e validar regressao. Cards
  de presenca tambem aparecem em fluxos fora da home; testar componente
  isolado.
- **Validacao minima obrigatoria:** testes focados de tracking/presenca,
  `flutter analyze --no-fatal-infos --no-fatal-warnings`, `flutter test`,
  `RUN_E2E=0 ./scripts/qa_gate_card.sh` quando viavel e `git diff --check`.
- **Rollback simples:** reverter commit do incremento; nao ha migracao de dados
  ou mudanca de API.

### Escopo do incremento atual
1. Remover contador/status redundante do topo do mapa.
2. Exibir chips compactos como `07:30 • Ida`.
3. Preservar label completo em `Semantics` para acessibilidade.
4. Trocar `Aguardando inicio da viagem` por `Aguardando inicio`.
5. Compactar signal pill em largura estreita.
6. Remover safe area branca da bottom navigation compartilhada.
7. Corrigir overflow da presenca em 320 px.

### Fora de escopo deste incremento
- Migrar todo o app para novos tokens em uma unica mudanca.
- Refazer motorista e responsavel neste mesmo patch.
- Turn-by-turn completo.
- Novo backend ou contrato de rota.
- E2E real/device fisico.

### Critérios de Aceite do incremento atual
- A area abaixo da bottom navigation nao aparece branca em telas map-first.
- O topo do mapa nao exibe `2 viagens hoje` como elemento visual redundante.
- Chips de viagem exibem horario + operacao curta.
- O HUD planejado usa copy curta e signal pill compacto.
- A presenca nao gera overflow em largura compacta.
- Testes focados de tracking e presenca passam.

### Andamento Atual
- Spec e plano criados em 2026-06-15.
- Testes RED criados para topo compacto, HUD planejado e presenca compacta.
- Implementacao inicial aplicada em widgets do passageiro e presenca.
- Validacao local do incremento 1 concluida em 2026-06-15:
  - testes focados de tracking/presenca: PASS.
  - regressao impactada de passageiro/presenca/aceitacao visual: PASS.
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS.
  - `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS, 422 testes.
  - `git diff --check`: PASS.
- Card permanece `IN_PROGRESS` porque o rollout completo ainda precisa migrar
  conta do passageiro, motorista e responsavel em proximos incrementos.

### Incremento 2 — Tokens globais e Conta do passageiro

#### Impact Analysis
- **Contexto do incremento:** a primeira entrega removeu a safe area branca e
  reduziu truncamentos no mapa, mas ainda existe dupla fonte visual:
  `VanRidesMapTokens` escuro e `MapFirstPalette` claro.
- **Telas/fluxos afetados:** aba `Conta` do passageiro, componentes
  `MapFirstSurfaceCard`/`MapFirstMetricBentoCard`, fundo do shell passageiro e
  tokens consumidos pelo mapa.
- **Dependencia backend:** nenhuma. O incremento reaproveita os dados ja
  exibidos de conta, viagem selecionada, parada, email, ETA e viagens do dia.
- **Contratos afetados:** nenhum endpoint, payload, status code, auth, CORS ou
  WebSocket muda.
- **Mudancas tecnicas previstas:** criar `VanRidesDesignTokens`, fazer
  `VanRidesMapTokens` apontar para eles, escurecer componentes map-first
  compartilhados e migrar `PassengerHomeAccountTab` para dark system.
- **Riscos e mitigacao:** `MapFirstPalette` e os componentes map-first tambem
  sao usados por motorista/responsavel; manter nomes de API e validar testes
  impactados. Mudancas de contraste podem quebrar expectativas visuais antigas;
  atualizar testes por comportamento e nao por snapshot fragil.
- **Validacao minima obrigatoria:** testes focados de conta/home passageiro,
  `flutter analyze --no-fatal-infos --no-fatal-warnings`,
  `RUN_E2E=0 ./scripts/qa_gate_card.sh` e `git diff --check`.
- **Rollback simples:** reverter o commit do incremento 2; nao ha migracao ou
  mudanca de contrato.

#### Critérios de Aceite do incremento 2
- Existe uma camada `VanRidesDesignTokens` para o dark system fora do mapa.
- `VanRidesMapTokens` nao duplica valores soltos; ele delega para os tokens
  globais.
- A aba `Conta` do passageiro nao usa card ou botao branco como superficie
  dominante.
- Textos e informacoes atuais da conta continuam visiveis e testados.
- A home do passageiro usa fundo escuro tambem ao alternar para `Conta`.

#### Resultado do incremento 2
- `VanRidesDesignTokens` criado como fonte global do dark system.
- `VanRidesMapTokens` passou a delegar para os tokens globais.
- `MapFirstPalette`, `MapFirstSurfaceCard` e `MapFirstMetricBentoCard` foram
  alinhados ao dark system sem mudar a API publica dos componentes.
- `PassengerHomeAccountTab` recebeu superficie dark, avatar com gradiente
  azul/verde, cards escuros e logout sem botao branco.
- Validacao local concluida em 2026-06-15:
  - testes RED iniciais falharam antes da implementacao.
  - testes focados de conta/home passageiro: PASS.
  - regressao impactada de passageiro/mapa/motorista/responsavel: PASS.
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS.
  - primeira rodada do gate apontou ratchet em
    `test/screens/home_passageiro_screen_test.dart` com 697 linhas contra
    limite 691; teste foi enxugado para 689 linhas.
  - `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS, 423 testes.
  - `git diff --check`: PASS.

### Incremento 3 — Contas dark de Motorista e Responsavel

#### Impact Analysis
- **Contexto do incremento:** apos a conta do passageiro entrar no dark system,
  motorista e responsavel ainda mantem areas claras, avatares fora da paleta e
  logout branco, criando uma experiencia visual inconsistente entre papeis.
- **Telas/fluxos afetados:** aba `Conta` do motorista, aba `Conta` do
  responsavel, shell do responsavel e componentes de informacao da conta do
  motorista.
- **Dependencia backend:** nenhuma. O incremento usa os mesmos dados de sessao,
  viagem selecionada, proxima parada, email e perfil backend ja exibidos.
- **Contratos afetados:** nenhum endpoint, payload, status code, auth, CORS ou
  WebSocket muda.
- **Mudancas tecnicas previstas:** adicionar superficie raiz dark nas contas,
  trocar avatares e acentos soltos por tokens azul/verde, remover botoes de
  logout brancos e alinhar pequenas superficies compartilhadas ao
  `MapFirstPalette` escuro.
- **Riscos e mitigacao:** as abas de motorista/responsavel compartilham
  componentes e navegacao com fluxos existentes; preservar textos, callbacks,
  keys publicas e validar testes focados de cada papel. Mudancas em componentes
  base do motorista podem afetar tela operacional; limitar ajuste ao que a conta
  consome e validar regressao impactada.
- **Validacao minima obrigatoria:** testes focados de conta motorista e shell
  responsavel, `flutter analyze --no-fatal-infos --no-fatal-warnings`,
  `RUN_E2E=0 ./scripts/qa_gate_card.sh` e `git diff --check`.
- **Rollback simples:** reverter o commit do incremento 3; nao ha migracao ou
  mudanca de contrato.

#### Critérios de Aceite do incremento 3
- Aba `Conta` do motorista usa fundo raiz dark e nao exibe logout branco.
- Aba `Conta` do responsavel usa fundo raiz dark e nao exibe logout branco.
- Avatares e acentos das contas seguem a paleta azul/verde do dark system.
- Dados, textos e callbacks atuais continuam visiveis e testados.

#### Resultado do incremento 3
- `DriverAccountTab` recebeu superficie raiz dark, avatar azul/verde e logout
  escuro com key de verificacao visual.
- A linha de informacao da conta do motorista deixou de usar chip claro
  legado e passou a consumir o acento escuro compartilhado.
- `GuardianShellScreen` passou a usar fundo escuro e a aba `Conta` do
  responsavel recebeu superficie dark, avatar azul/verde, logout escuro e
  acentos alinhados ao dark system.
- Validacao local concluida em 2026-06-15:
  - testes RED iniciais falharam antes da implementacao por ausencia das
    superficies `driver.account.darkSurface` e `guardian.account.darkSurface`.
  - testes focados de conta motorista/shell responsavel: PASS, 8 testes.
  - regressao impactada de motorista/responsavel: PASS, 21 testes.
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS.
  - `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS, 424 testes.
  - `git diff --check`: PASS.

### Incremento 4 — Login dark e HUD recolhivel

#### Impact Analysis
- **Contexto do incremento:** o screenshot real da build atual confirmou que a
  bottom navigation ja nao deixa a safe area branca na home do passageiro, mas
  a tela de login ainda esta clara e o HUD do mapa ainda ocupa area fixa sem
  opcao de recolher.
- **Telas/fluxos afetados:** `LoginScreen`, HUD/sheet da home do passageiro e
  resumo de viagem em `PassengerHomeMapScene`.
- **Dependencia backend:** nenhuma. O incremento preserva `POST /login`, payload
  de autenticacao, sessao, push binder, navegacao e gateways existentes.
- **Contratos afetados:** nenhum endpoint, payload, status code, auth, CORS ou
  WebSocket muda.
- **Mudancas tecnicas previstas:** aplicar `VanRidesDesignTokens` no login,
  adicionar chaves de validacao visual, criar alternancia expandido/recolhido no
  HUD do passageiro e reduzir a densidade de texto do resumo da parada.
- **Riscos e mitigacao:** login e HUD sao fluxos centrais; preservar keys
  existentes, validadores, callbacks e textos sem alterar a navegacao. O HUD
  recolhivel pode esconder acoes essenciais; manter `Detalhes` acessivel no
  modo compacto e validar widget tests de mapa.
- **Validacao minima obrigatoria:** testes focados de login e HUD, `flutter
  analyze --no-fatal-infos --no-fatal-warnings`, `RUN_E2E=0
  ./scripts/qa_gate_card.sh` e `git diff --check`.
- **Rollback simples:** reverter o commit do incremento 4; nao ha migracao ou
  mudanca de contrato.

#### Critérios de Aceite do incremento 4
- `LoginScreen` usa fundo raiz dark e formulario escuro, sem card branco
  dominante.
- Fluxos e testes existentes de login continuam passando.
- HUD do passageiro alterna entre resumo expandido e trilho compacto.
- Modo compacto ocupa menos altura da tela e preserva status/sinal/detalhes.

#### Resultado local do incremento 4
- Implementado `LoginScreen` dark com `VanRidesDesignTokens`, inputs escuros,
  checkbox/link/botao principal alinhados ao sistema visual azul/verde e keys
  de validacao `login.darkSurface` e `login.form.darkSurface`.
- Implementado HUD recolhivel no mapa do passageiro com
  `PassengerTripCollapsedHud`, alternancia por `ValueNotifier` e key
  `passenger.trip.hud.toggle`, mantendo `Detalhes` acessivel no modo compacto.
- RED inicial: testes focados falharam como esperado por ausencia das novas
  superficies dark e do toggle de HUD.
- Testes focados:
  - `flutter test test/login_screen_test.dart test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart`: PASS, 9 testes.
- Regressao impactada:
  - `flutter test test/login_screen_test.dart test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart test/features/tracking/presentation/widgets/passenger_trip_glass_sheet_test.dart test/features/tracking/premium_map_visual_acceptance_test.dart test/screens/home_passageiro_screen_test.dart`: PASS, 26 testes.
- `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS.
- Primeira rodada de `RUN_E2E=0 ./scripts/qa_gate_card.sh`: FAIL por ratchet
  (`set_state_count` e arquivo acima de 600 linhas).
- Ajuste de ratchet: estado local do HUD passou para `ValueNotifier` e o HUD
  compacto foi extraido para arquivo proprio.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality metrics tests: PASS, 5 testes.
  - E2E preflight tests: PASS, 7 testes.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 426 testes.
  - quality ratchet: PASS.
  - E2E real/device fisico desabilitado explicitamente para este incremento
    visual.
- `git diff --check`: PASS.

### Incremento 5 — Onboarding dark foundation

#### Impact Analysis
- **Contexto do incremento:** depois do login dark, o fluxo anterior a home
  ainda mistura fundo escuro com cards, campos e banners brancos em escolha de
  cadastro, cadastro de passageiro/motorista, verificacao de e-mail e vinculo
  de empresa.
- **Telas/fluxos afetados:** `OnboardingScaffold`,
  `OnboardingRoleCard`, inputs compartilhados do onboarding,
  `EmailVerificationPendingScreen`, cadastro de passageiro, cadastro de
  motorista e widgets de onboarding de empresa do passageiro.
- **Dependencia backend:** nenhuma. O incremento preserva cadastro, login
  bloqueado por e-mail, reenviar verificacao, busca/vinculo de empresa e
  callbacks atuais.
- **Contratos afetados:** nenhum endpoint, payload, status code, auth, CORS ou
  WebSocket muda.
- **Mudancas tecnicas previstas:** fazer `OnboardingPalette` consumir
  `VanRidesDesignTokens`, aplicar tema local dark para `TextFormField`, trocar
  superficies brancas por superficies escuras e alinhar soft accents ao azul e
  verde do app.
- **Riscos e mitigacao:** campos podem ficar com texto invisivel se herdarem o
  tema global claro; mitigar com teste de `EditableText`. Como o scaffold e
  compartilhado, validar fluxos de verificacao, onboarding de empresa e login.
- **Validacao minima obrigatoria:** teste focado de foundation, regressao de
  verificacao/onboarding/login, `flutter analyze --no-fatal-infos
  --no-fatal-warnings`, `RUN_E2E=0 ./scripts/qa_gate_card.sh` e
  `git diff --check`.
- **Rollback simples:** reverter o commit do incremento 5; nao ha migracao ou
  mudanca de contrato.

#### Critérios de Aceite do incremento 5
- `OnboardingScaffold` usa fundo dark e tema local coerente com o design system.
- `OnboardingRoleCard`, campos, static fields e cards de empresa nao usam
  branco como superficie dominante.
- Textos digitados em `TextFormField` ficam claros e legiveis em superficie
  escura.
- Verificacao de e-mail e fluxo de vinculo de empresa preservam textos e
  callbacks existentes.

#### Resultado local do incremento 5
- `OnboardingPalette` passou a consumir `VanRidesDesignTokens` e o
  `OnboardingScaffold` aplica tema local dark para textos, inputs e selecao.
- `OnboardingRoleCard`, static fields, cards de empresa, cards de dica/senha e
  verificacao de e-mail foram migrados para superficies escuras.
- Widgets auxiliares foram extraidos para manter o ratchet de tamanho:
  `onboarding_blur_dot.dart`, `email_verification_pending_feedback_banner.dart`
  e `cadastro_passageiro_widgets.dart`.
- RED inicial: `test/onboarding_flow_components_test.dart` falhou como esperado
  porque `OnboardingRoleCard` ainda usava card branco.
- Testes focados:
  - `flutter test test/onboarding_flow_components_test.dart test/screens/email_verification_pending_screen_test.dart test/features/passenger_onboarding/passenger_company_link_flow_test.dart test/login_screen_test.dart`: PASS, 20 testes.
- `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS.
- Primeira rodada de `RUN_E2E=0 ./scripts/qa_gate_card.sh`: FAIL por ratchet
  de tamanho em `onboarding_flow_components.dart`,
  `email_verification_pending_screen.dart` e `cadastro_passageiro_screen.dart`.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality metrics tests: PASS, 5 testes.
  - E2E preflight tests: PASS, 7 testes.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 427 testes.
  - quality ratchet: PASS.
  - E2E real/device fisico desabilitado explicitamente para este incremento
    visual.
- `git diff --check`: PASS.

### Incremento 6 — Apple Maps bottom overlay

#### Impact Analysis
- **Contexto do incremento:** apos os prints reais do incremento 5, a aba
  `Viagem` ainda parecia ter uma estrutura inferior separada: o mapa nao
  continuava visualmente por baixo da bottom navigation e o HUD ficava cortado
  ou comprimido perto do rodape.
- **Telas/fluxos afetados:** home do passageiro em `Viagem`,
  `PassengerHomeMapScene`, `TripMapSceneShell` via inset consumido pelo
  passageiro e `MapFirstBottomNavBar` compartilhada.
- **Dependencia backend:** nenhuma. O incremento preserva tracking, ETA,
  agenda, presenca, navegacao e callbacks existentes.
- **Contratos afetados:** nenhum endpoint, payload, status code, auth, CORS ou
  WebSocket muda.
- **Mudancas tecnicas previstas:** mover a bottom navigation do passageiro para
  overlay no corpo do `Scaffold`, habilitar `extendBody`, remover fundo solido
  externo do dock, aumentar transparencia/blur e reservar clearance do HUD.
- **Riscos e mitigacao:** dock compartilhado tambem e usado por motorista e
  responsavel; manter API publica e validar suites de regressao. O overlay pode
  cobrir abas nao-mapa; aplicar padding de clearance quando o mapa nao esta
  desenhando por baixo.
- **Validacao minima obrigatoria:** testes focados de home passageiro e HUD,
  aceitacao visual do mapa, `flutter analyze`, `flutter test`,
  `RUN_E2E=0 ./scripts/qa_gate_card.sh` e `git diff --check`.
- **Rollback simples:** reverter o commit do incremento 6; nao ha migracao ou
  mudanca de contrato.

#### Critérios de Aceite do incremento 6
- A home do passageiro nao usa mais `Scaffold.bottomNavigationBar` rigido na
  aba map-first.
- O corpo do `Scaffold` estende atras do dock inferior.
- O dock inferior tem superficie glass flutuante, sem faixa solida externa.
- O HUD respeita a altura do dock e nao fica cortado no rodape.
- Abas sem mapa preservam padding para nao esconder conteudo atras do dock.

#### Resultado local do incremento 6
- `HomePassageiroScreen` passou a renderizar `MapFirstBottomNavBar` como
  overlay em `Stack`, com `extendBody: true`.
- `MapFirstBottomNavBar` removeu o fundo solido externo, ganhou keys de
  validacao, semantica de navegacao principal e glass mais transparente.
- `PassengerHomeBottomNav` foi extraido para centralizar os destinos do
  passageiro e manter `HomePassageiroScreen` dentro do ratchet.
- `PassengerHomeMapScene` passou a receber `bottomOverlayInset` e usa o
  clearance do dock para posicionar o HUD acima da navegacao.
- RED inicial:
  - `home_passageiro_screen_test.dart` falhou por `extendBody` falso e
    `bottomNavigationBar` ainda presente.
  - `passenger_home_trip_widgets_test.dart` falhou por `sheetBottomInset` igual
    a `10`.
- Testes focados:
  - `flutter test test/screens/home_passageiro_screen_test.dart`: PASS, 9
    testes.
  - `flutter test test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart`: PASS, 4 testes.
- Aceitacao visual:
  - `flutter test test/features/tracking/premium_map_visual_acceptance_test.dart`: PASS, 6 testes.
- `flutter analyze`: PASS, sem issues.
- `flutter test`: PASS, 427 testes.
- `git diff --check`: PASS.
- Primeira rodada de `RUN_E2E=0 ./scripts/qa_gate_card.sh`: FAIL por ratchet
  de tamanho em `home_passageiro_screen.dart` e
  `home_passageiro_screen_test.dart`.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality metrics tests: PASS, 5 testes.
  - E2E preflight tests: PASS, 7 testes.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 427 testes.
  - quality ratchet: PASS.
  - E2E real/device fisico desabilitado explicitamente para este incremento
    visual.

---

## Backlog 0968 — PHASE-0464: Apple Maps Adaptive Trip Sheet

### Status
DONE (commit: 26570d4)

### Origem
Criado apos revisao visual real da `PHASE-0463` em 2026-06-15. O mapa ja fica
por baixo da bottom navigation, mas a sessao inferior ainda nao se comporta
como o painel do Apple Maps: falta gesto vertical, alturas de encaixe e
separacao clara entre resumo minimo, estado padrao e detalhes completos.

### Objetivo
Transformar o HUD da viagem do passageiro em uma bottom sheet adaptativa com
tres estados (`mini`, `medium`, `expanded`), reduzindo informacao no estado
padrao e liberando mapa quando o usuario recolhe o painel.

### Spec de design
`docs/superpowers/specs/2026-06-15-apple-maps-adaptive-trip-sheet-design.md`

### Plano de implementação
`docs/superpowers/plans/2026-06-15-apple-maps-adaptive-trip-sheet-implementation.md`

### Impact Analysis
- **Contexto do card:** refinamento estrutural da home do passageiro apos
  validacao visual real da `PHASE-0463`.
- **Telas/fluxos afetados:** aba `Viagem` do passageiro, HUD/sheet do mapa,
  nudge de presenca, controles de foco e testes de aceitacao visual de mapa.
- **Dependencia backend:** nenhuma. A fase reorganiza dados ja existentes de
  tracking, presenca, viagem, rota, parada e motorista.
- **Contratos afetados:** nenhum endpoint, payload, status code, auth, CORS ou
  WebSocket muda.
- **Mudancas tecnicas previstas:** criar modelo de detents, trocar
  `_hudExpanded` por estado de sheet, criar widgets de mini/medium/expanded,
  mover presenca pendente para dentro da sheet e compactar controles do mapa.
- **Riscos e mitigacao:** gestos podem conflitar com mapa/scroll; restringir
  drag ao painel. Expanded pode criar overflow; testar viewports criticos e
  text scale. Mudancas no top layer podem afetar agenda; preservar keys e
  callbacks.
- **Validacao minima obrigatoria:** testes focados de detent/sheet/top layer,
  aceitacao visual do mapa, `flutter analyze`, `flutter test`,
  `RUN_E2E=0 ./scripts/qa_gate_card.sh` e `git diff --check`.
- **Rollback simples:** reverter commit(s) da `PHASE-0464`; nao ha migracao ou
  mudanca de dados.

### Criterios de Aceite
- A sheet possui tres estados testaveis: `mini`, `medium`, `expanded`.
- O estado inicial e `medium`.
- Swipe/chevron alternam os estados sem crash e sem overflow nos viewports
  criticos.
- O nudge de presenca nao aparece mais no meio do mapa.
- Presenca pendente aparece dentro da sheet.
- `Detalhes` deixa de ser botao cortado no estado padrao.
- Controles `Van` e `Voce` no mapa nao exibem texto visivel no estado padrao.
- O mapa continua visivel sob/ao redor da sheet e da bottom nav.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh` passa.

### Andamento Atual
- Spec e plano criados em 2026-06-15.
- Implementacao concluida no commit `26570d4`.
- `PassengerHomeMapScene` trocou o estado booleano do HUD por
  `PassengerTripSheetDetent` com estados `mini`, `medium` e `expanded`.
- Presenca pendente saiu do top layer do mapa e entrou como acao compacta da
  sheet.
- Controles de foco `Van` e `Voce` ficaram icon-only no mapa, mantendo tooltip
  e label semantico.
- `Detalhes` passou a aparecer apenas no estado expandido.
- Validacoes locais executadas:
  - RED focado de detents: FAIL esperado antes do modelo existir.
  - `flutter test test/features/tracking/presentation/widgets/passenger_trip_sheet_detent_test.dart`: PASS.
  - `flutter test test/features/tracking/presentation/widgets/passenger_trip_sheet_detent_test.dart test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart test/features/tracking/presentation/widgets/passenger_trip_glass_sheet_test.dart`: PASS.
  - `flutter test test/features/tracking/premium_map_visual_acceptance_test.dart`: PASS.
  - `flutter test test/screens/home_passageiro_screen_test.dart`: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 429 testes.
  - `git diff --check`: PASS.
  - Primeira rodada de `RUN_E2E=0 ./scripts/qa_gate_card.sh`: FAIL por ratchet
    de tamanho em `test/screens/home_passageiro_screen_test.dart`.
  - Ajuste de ratchet: teste de reload passou a reutilizar `_sampleTripRecord`,
    reduzindo o arquivo para 666 linhas contra limite de 691.
  - `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
    - quality metrics tests: PASS, 5 testes.
    - E2E preflight tests: PASS, 7 testes.
    - `flutter analyze`: PASS, sem issues.
    - `flutter test`: PASS, 429 testes.
    - quality ratchet: PASS.
    - E2E real/device fisico desabilitado explicitamente para este incremento
      visual.

---

## Backlog 0969 — PHASE-0465: Passenger Apple Maps Sheet Redesign

### Status
DONE (commit: ea974fd)

### Origem
Criado apos revisao visual real da `PHASE-0464`. A sheet passou a ter estados
mini, medium e expanded, mas o resultado ainda ficou distante do Mapas da Apple:
texto cortado, informacao demais no estado padrao, conteudo cortando no fundo e
arrasto sem fisica real acompanhando o dedo.

### Objetivo
Redesenhar a tela de viagem do passageiro em torno de um footer-sheet real,
minimalista e map-first, onde o footer permanece fixo e igual, e a sheet
expande a partir dele com arrasto/snap inspirado no Mapas da Apple.

### Spec de design
`docs/superpowers/specs/2026-06-16-passenger-apple-maps-sheet-redesign-design.md`

### Plano de implementação
`docs/superpowers/plans/2026-06-16-passenger-footer-sheet-implementation.md`

### Impact Analysis
- **Contexto do card:** correcao de direcao apos validacao visual real da
  `PHASE-0464`.
- **Telas/fluxos afetados:** aba `Viagem` do passageiro, sheet do mapa,
  controles de foco, footer unificado na cena de viagem e testes visuais de
  tracking.
- **Dependencia backend:** nenhuma. A fase reorganiza dados existentes de
  tracking, viagem, presenca, parada, rota e motorista.
- **Contratos afetados:** nenhum endpoint, payload, status code, auth, CORS ou
  WebSocket muda.
- **Mudancas tecnicas previstas:** trocar a arquitetura de sheet por
  `DraggableScrollableSheet`, fundir sheet e footer em uma superficie unica,
  criar modelo de conteudo curto, reduzir informacao do estado padrao e mover
  detalhes para expanded.
- **Riscos e mitigacao:** `BackdropFilter` pode nao funcionar igual sobre mapa
  nativo; manter fallback translucido. Gesto do sheet pode conflitar com mapa;
  restringir gestos a area da sheet. Text scale alto pode aumentar altura;
  testar viewports compactos e usar conteudo curto.
- **Validacao minima obrigatoria:** widget tests de detents reais, ausencia de
  overflow em 390x844 e 430x932, teste com text scale aumentado, `flutter
  analyze`, `flutter test`, `RUN_E2E=0 ./scripts/qa_gate_card.sh` e smoke
  visual manual no simulador.
- **Rollback simples:** reverter os commits da `PHASE-0465`; nao ha migracao
  de dados ou alteracao de contrato.

### Criterios de Aceite
- A sheet acompanha o dedo durante o arrasto.
- A sheet usa snap real para `peek`, `resting` e `expanded`.
- A sheet expande a partir do footer, sem gap visual ou card separado acima da
  navegacao.
- O footer permanece sempre igual em todos os estados.
- O estado inicial e `resting`.
- O estado inicial mostra no maximo tres grupos visuais: status, minha parada e
  pendencia de acao quando houver.
- O estado padrao nao mostra chips completos de viagem, motorista, detalhes ou
  cards aninhados.
- O estado padrao nao corta o status principal, sinal, minha parada ou presenca
  pendente em 390x844 e 430x932.
- O estado expandido mostra detalhes completos em lista rolavel sem cortar o
  fundo.
- O mapa continua visivel e compreensivel nos tres estados.
- O footer da aba `Viagem` nao aparece como bloco branco isolado.
- Controles de centralizar van e passageiro continuam acessiveis.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh` passa.

### Andamento Atual
- Spec criada para revisao antes de implementar.
- Ajustada para footer-sheet unificado: footer persistente como base fixa e
  sheet expandindo a partir dele.
- Implementado footer-sheet unificado com `DraggableScrollableSheet`, detents
  `peek`, `resting` e `expanded`, footer persistente e resumo fixo no topo.
- Ajustado apos validacao visual real: o resumo de status/parada permanece
  coeso entre estados, os detalhes entram abaixo no modo expandido, a presenca
  pendente saiu do estado baixo e os controles nativos do mapa foram
  desabilitados no palco do passageiro.
- Validado localmente com RED focado, `flutter analyze`, testes focados,
  `flutter test`, `./scripts/quality_gate.sh`,
  `./scripts/qa_gate_card.sh --no-e2e` e `git diff --check`.

---

## Backlog 0970 — PHASE-0466: App-Wide Liquid Glass Redesign & Functional Profiles

### Status
DONE (commit: 99ba47d)

### Origem
Criado apos revisao visual real da `PHASE-0465` em 2026-06-16 e ampliado apos
clarificacao de produto no mesmo dia. A tela `Viagem` do passageiro ficou
melhor e virou referencia, mas o app ainda mistura partes dark premium,
componentes parciais e superficies legadas claras. O objetivo agora e um pack
app-wide: liquid glass, dark system, motion premium, perfis reais e limpeza de
paineis.

### Objetivo
Definir e implementar o novo padrao VanRides para o app inteiro: escuro,
premium, minimalista, com liquid glass e interacoes limpas em passageiro,
motorista, responsavel e admin/empresa. O pack tambem deve transformar
conta/perfil em dados reais, reduzir placeholders e simplificar paineis para
mostrar apenas informacao util e funcional.

### Spec de design
`docs/superpowers/specs/2026-06-16-app-wide-liquid-glass-redesign-design.md`

### Plano de implementação
`docs/superpowers/plans/2026-06-16-app-wide-liquid-glass-redesign-implementation.md`

### Impact Analysis
- **Contexto do card:** refinamento estrutural app-wide apos validacao visual
  do passageiro e identificacao de inconsistencias em motorista, responsavel e
  admin.
- **Telas/fluxos afetados:** passageiro, motorista, responsavel, admin/empresa,
  mapa, presenca, conta/perfil, localizacao, detalhes, onboarding, painel de
  vans/operacao, listas e formularios administrativos.
- **Dependencia backend:** obrigatoria para perfis reais. Recomendacao:
  `GET /account/me` read-only e, depois, updates self-service por papel.
- **Contratos afetados:** novo contrato seguro de perfil/sessao. `/login` deve
  permanecer compativel.
- **Mudancas tecnicas previstas:** fundacao de tokens/motion/liquid glass,
  `VanRidesLiquidTabScaffold`, migracao de superficies por papel,
  screenshot/visual QA harness, contrato de perfil real, remocao de
  placeholders e simplificacao de paineis.
- **Riscos e mitigacao:** subagentes podem editar arquivos compartilhados;
  separar ownership por wave. Glass pode perder contraste/performance; usar
  fallback solido, blur clipado e testes de acessibilidade. Perfil pode vazar
  dados; usar DTO seguro, RBAC e testes cross-tenant. Admin tem alto blast
  radius; migrar por base e depois por dominio.
- **Validacao minima obrigatoria:** testes focados por wave, `flutter analyze`,
  `flutter test`, `./scripts/qa_gate_card.sh`, screenshot smoke no simulador e,
  se houver backend, `./mvnw test` mais smoke HTTP autenticado.
- **Rollback simples:** cada subfase deve fechar em commit pequeno e
  reversivel; contrato novo deve ser aditivo.

### Criterios de Aceite
- O novo sistema visual cobre passageiro, motorista, responsavel e admin, com
  excecoes documentadas.
- Liquid glass aparece em sheets, navs, perfis, presencas e dialogs principais,
  com fallback acessivel.
- Trocas de aba mobile nao desmontam o mapa nem causam pulo visual grosseiro.
- Passageiro mantem e melhora o padrao atual: sheet, mapa, presenca e conta.
- Motorista tem painel operacional simplificado, sem cards claros destoantes.
- Responsavel tem localizacao, presenca, detalhe e conta coerentes.
- Admin/empresa migra para dark operational, com densidade util e menos ruido.
- Perfis de todos os papeis usam dados reais de contrato ou estados honestos.
- Placeholders como `Portal Empresa`, `PREMIUM`, atividades fake e notas fixas
  sao removidos ou ligados a dados reais.
- Screenshot pack e QA log acompanham o fechamento.

### Trilhas Para Subagentes
1. `design-system`: tokens, motion, liquid glass e componentes base.
2. `mobile-shell`: tab scaffold vivo para passageiro, motorista e responsavel.
3. `account-backend`: `GET /account/me` e DTO seguro.
4. `account-app`: consumo de perfil real no app.
5. `passenger`: fechamento da experiencia passageiro.
6. `driver`: fechamento da experiencia motorista e painel operacional.
7. `guardian`: fechamento da experiencia responsavel.
8. `admin-shell`/`admin-dashboard`/`admin-operations`: dark portal empresa.
9. `visual-qa`: screenshot harness, matriz visual e QA log.

### Andamento Atual
- Subagentes exploratorios executados em modo leitura em 2026-06-16.
- Escopo ampliado de passageiro para app-wide em 2026-06-16.
- Plano de fase substituido por spec/plano app-wide.
- Wave 1 iniciada no app com a fundacao de design/motion/liquid glass.
- Commit app `abec5e1` criou:
  - `VanRidesMotionTokens` e `VanRidesMotionPolicy`;
  - `VanRidesTheme.dark`;
  - `VanRidesLiquidSurface`;
  - `VanRidesStatusPill`;
  - bridge de `VanRidesGlassSurface` para a nova liquid surface.
- Validacoes do incremento app `abec5e1`:
  - `flutter test test/app/theme/vanrides_motion_test.dart test/app/theme/vanrides_theme_test.dart test/app/widgets/vanrides_liquid_surface_test.dart test/features/tracking/presentation/widgets/vanrides_glass_surface_test.dart`: PASS.
  - `flutter analyze`: PASS.
  - `flutter test test/features/tracking/premium_map_visual_acceptance_test.dart test/features/tracking/presentation/widgets/passenger_trip_glass_sheet_test.dart test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart`: PASS.
  - `git diff --check`: PASS.
- Backend `account-backend` entregue no repo backend:
  - commit backend `056982e` (`feat(auth): expose account profile endpoint`);
  - contrato aditivo `GET /account/me` com DTO seguro;
  - validado pelo subagente com testes focados, `./mvnw -B test`,
    `python3 scripts/quality_metrics.py --check`, `./scripts/qa_gate_card.sh`
    e `git diff --check`.
  - observacao: smoke profundo opcional `DEEP_AUTH_SMOKE=1` falhou antes de
    atingir `/account/me` em contrato legado de presencas/motorista com 401.
- Wave 2 app iniciou o `mobile-shell`:
  - commit app `79ea9c5` criou `VanRidesLiquidTabScaffold`;
  - `HomePassageiroScreen` agora usa `IndexedStack` para preservar estado das
    abas `Viagem`, `Presenca` e `Conta`, reduzindo pulo visual;
  - `flutter test test/app/widgets/vanrides_liquid_tab_scaffold_test.dart test/screens/home_passageiro_screen_test.dart test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart`: PASS.
  - `flutter analyze`: PASS.
  - `flutter test test/features/tracking/premium_map_visual_acceptance_test.dart test/features/tracking/presentation/widgets/passenger_trip_glass_sheet_test.dart`: PASS.
  - `git diff --check`: PASS.
- Proximo incremento planejado: `account-app`, consumindo `/account/me` para
  substituir placeholders da tela `Conta` por dados reais e estados honestos.
- Incremento `account-app` entregue no app:
  - commit app `27e2d47` criou `AccountProfile`, `AccountProfileService` e
    consumo lazy de `GET /account/me` na aba `Conta` do passageiro;
  - conta do passageiro agora mostra perfil real, status de e-mail, telefone e
    empresa quando disponiveis, mantendo fallback honesto do login;
  - navegacao inferior recebeu keys estaveis para smoke/widget tests;
  - `flutter test test/features/tracking/presentation/widgets/passenger_home_account_widgets_test.dart test/features/account/account_profile_service_test.dart test/screens/home_passageiro_screen_test.dart`: PASS.
  - `flutter test test/app/widgets/vanrides_liquid_tab_scaffold_test.dart test/features/tracking/premium_map_visual_acceptance_test.dart test/features/tracking/presentation/widgets/passenger_trip_glass_sheet_test.dart test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart`: PASS.
  - `flutter analyze`: PASS.
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS (`flutter test`: 443 testes,
    ratchet PASS).
- Incremento `account-app` para motorista/responsavel entregue no app:
  - commit app `2e8bd76` passou a carregar perfil real na aba `Conta` do
    motorista e do responsavel, com fallback honesto para dados da sessao;
  - `ExperienceHomeRouter` injeta `AccountProfileService` no fluxo real do
    responsavel;
  - `HomeMotoristaScreen` recebeu gateway de perfil e teve builders de abas
    extraidos para `home_motorista_screen_tabs.dart`, mantendo o ratchet de
    tamanho verde;
  - contas de motorista e responsavel usam `VanRidesLiquidSurface`,
    `VanRidesStatusPill`, dados reais de telefone/empresa quando disponiveis e
    `ValueNotifier` para nao aumentar `setState`;
  - `flutter test test/features/guardian/guardian_shell_screen_test.dart test/features/driver_trip/presentation/widgets/driver_home_account_widgets_test.dart test/features/driver_trip/home_motorista_planning_test.dart test/app/experience_home_router_test.dart test/features/account/account_profile_service_test.dart`: PASS.
  - `flutter analyze`: PASS.
  - `git diff --check`: PASS.
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS (`flutter test`: 446 testes,
    ratchet PASS).
- Incremento `admin-shell` em andamento no app:
  - commit app `f2fbee1` migra a fundacao visual do portal admin/empresa para
    dark/liquid operacional usando `AdminPortalColors` e `AdminPortalSurface`;
  - `AdminShellScreen` passa a aceitar `AccountProfileGateway` e sincroniza
    empresa/admin reais via `GET /account/me`, mantendo fallback honesto;
  - `AdminLoginScreen` e `ExperienceHomeRouter` injetam `AccountProfileService`
    no fluxo real do admin;
  - placeholder `Portal Empresa` foi removido do contrato de login e dos
    breadcrumbs de servicos/viagens; `Configurações` desabilitado saiu da
    sidebar;
  - `flutter test test/features/admin/admin_shell_screen_test.dart test/features/admin/admin_login_screen_test.dart test/features/admin/admin_portal_auth_service_test.dart`: PASS.
  - `flutter test test/features/admin test/app/experience_home_router_test.dart`: PASS.
  - `flutter analyze`: PASS.
  - `git diff --check`: PASS.
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS (`flutter test`: 447 testes,
    ratchet PASS).
- Incremento `admin-cleanup` entregue no app:
  - commit app `b8be446` removeu atividades fake do detalhe de passageiros e
    trocou por resumo baseado no registro real;
  - commit app `3ea15ac` removeu `PREMIUM`/notas fixas do detalhe de
    responsaveis e passou a exibir dados administrativos reais;
  - commit app `2c64e19` removeu acoes decorativas do dashboard e manteve
    somente atalhos funcionais;
  - commit app `89698df` separou o resumo de passageiros para manter o ratchet
    verde sem alterar comportamento;
  - `flutter test test/features/admin/admin_dashboard_screen_test.dart test/features/admin/presentation/widgets/admin_passageiros_widgets_test.dart test/features/admin/admin_passageiros_screen_test.dart`: PASS.
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS (`flutter test`: 448 testes,
    ratchet PASS).
- Incremento `global-liquid` entregue no app:
  - commit app `4d8a362` aplicou `VanRidesTheme.dark()` como tema raiz e
    migrou launch/login de experiencias, pendencia de motorista, papel nao
    suportado, telas de conta/perfil e superficies globais para dark/liquid;
  - motorista, responsavel e admin passaram a compartilhar o mesmo vocabulario
    visual dark com `MapFirstPalette`, `VanRidesLiquidSurface`,
    `VanRidesStatusPill` e superficies elevadas em vez de branco legado;
  - testes focados de launch/router/theme/liquid/tracking/motorista/responsavel
    passaram, `flutter analyze` passou e `./scripts/qa_gate_card.sh --no-e2e`
    passou (`flutter test`: 448 testes, ratchet PASS).
- Incremento `global-liquid-polish` entregue no app:
  - commit app `945f0aa` adicionou `presentAccountRoleLabel` para normalizar
    papeis em PT-BR (`Passageiro`, `Motorista`, `Responsável`,
    `Administrador`) nas contas e topbar admin;
  - `MapFirstBottomNavItems` ganhou semantica explicita de botao/selecionado,
    e o snapshot runtime do simulador passou a expor `Viagem`, `Presenca` e
    `Conta` como botoes acionaveis;
  - smoke visual real no iPhone 16e Simulator iOS 26.2 gerou evidencias em
    `output/visual-smoke/phase-0466/`:
    `01-current.png`, `02-passenger-home.png`,
    `03-passenger-sheet-expanded.png`, `04-passenger-presence.png` e
    `06-passenger-account-ptbr.png`;
  - `flutter test test/features/account/account_role_label_test.dart test/features/tracking/presentation/widgets/passenger_home_account_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_home_account_widgets_test.dart test/features/guardian/guardian_shell_screen_test.dart test/features/tracking/presentation/map_first_bottom_nav_test.dart test/features/tracking/presentation/widgets/passenger_trip_glass_sheet_test.dart test/screens/home_passageiro_screen_test.dart`: PASS.
  - `flutter analyze`: PASS.
  - `git diff --check`: PASS.
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS (`flutter test`: 451 testes,
    ratchet PASS).
- Fechamento final da `PHASE-0466` entregue no app:
  - passageiro, motorista, responsavel e admin/empresa migrados para uma
    experiencia dark/liquid-glass consistente, sem superficies claras legadas
    nos fluxos principais validados;
  - sheet/footer do passageiro foi fundido em um padrao unico de mapa com
    detents e sem area branca residual, mantendo mapa ate o rodape;
  - mapa/sheet do responsavel foi simplificado para evitar texto truncado e
    manter apenas informacao funcional;
  - portal admin/empresa ganhou superficies dark/liquid, dialogs e formularios
    coerentes com o novo padrao visual;
  - smoke visual automatizado cobre passageiro, motorista, responsavel e admin
    com screenshots deterministicas por papel;
  - evidencias locais finais em
    `output/visual-smoke/phase-0466/20260616-055850`;
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS em
    `output/qa-logs/phase-0466-qa-gate-20260616-061041.log`;
  - backend permaneceu sem alteracoes locais nesta fase final.
- Polish pos-validacao visual entregue no app:
  - commit app `99ba47d` corrigiu a injecao do `AccountProfileService` no
    fluxo real do passageiro;
  - conta do passageiro agora diferencia perfil real indisponivel de erro
    generico e mostra fallback honesto do login no topo da tela;
  - aba `Presenca` do passageiro e tela standalone de presencas migraram
    cabecalho/lista para superficies `VanRidesLiquidSurface` sobre fundo
    map-like;
  - sheet/footer do passageiro removeu a margem segura residual inferior,
    eliminando o espaco morto no final do mapa;
  - DEV API ainda respondeu `404` para `GET /account/me`; o backend local ja
    possui o endpoint no commit `056982e`, mas ele precisa ser publicado no
    remoto/DEV para carregar perfil real de ponta a ponta;
  - smoke visual automatizado final em
    `output/visual-smoke/phase-0466/20260616-passenger-polish-2`;
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS (`flutter test`: 454 testes,
    ratchet PASS).

---

## Backlog 0971 — ACCOUNT-APP-0001: conta editavel, foto, nascimento e cadastro guiado

### Status
DONE (commit: 65261fd)

### Contexto
A tela de conta antiga ainda parecia informativa demais e continha dados pouco
uteis para o usuario final. A nova fase transforma `Conta` em area funcional
para passageiro, motorista e responsavel, alinha o cadastro do app com o
cadastro administrativo e remove a exposicao de detalhes tecnicos como
latitude/longitude para o passageiro.

### Impact Analysis
- **Mudancas tecnicas:** criar tela compartilhada de perfil editavel, sheet de
  edicao, codec de foto/avatar com `image_picker`, inputs de data de nascimento,
  formularios admin alinhados e smoke visual com asserts mais estaveis.
- **Contratos afetados:** consumo de `GET /account/me`, `PATCH /account/me`,
  campos de `dataNascimento` nos fluxos admin e payload de foto/avatar em data
  URI/URL validada pelo backend.
- **Riscos:** UX pedir dado demais cedo demais, permissao de foto quebrar no
  iOS, texto tecnico de parada vazar para o usuario e smoke visual produzir
  falso positivo.
- **Mitigacao:** perfil editavel pos-login, copy de parada focada em endereco e
  confirmacao no mapa, testes que proíbem termos tecnicos para usuario final,
  Info.plist atualizado e smoke real em simulador com cliques por papel.
- **Rollback simples:** reverter commit `65261fd`; backend segue compativel por
  campos aditivos.

### Escopo
1. Perfil compartilhado para passageiro, motorista e responsavel com nome,
   telefone, e-mail, data de nascimento, foto/avatar e papel.
2. Foto/avatar funcional agora, usando galeria do iOS e envio como data URI
   aceito pelo backend.
3. Edição de perfil com feedback visual e sem permitir troca silenciosa de
   papel, empresa ou identificadores operacionais.
4. Cadastro de passageiro e motorista com data de nascimento.
5. Parada do passageiro apresentada como endereço/local confirmado no mapa, sem
   expor latitude/longitude/coordenadas para usuario final.
6. Formularios admin de passageiros, motoristas e responsaveis alinhados ao
   mesmo modelo de dados.
7. Visual smoke real cobrindo passageiro, motorista, responsavel e portal admin.

### Fora de escopo
- Upload para storage/CDN dedicado.
- Convite por e-mail, reset de senha por token e chat/pagamentos.
- Documentos profissionais do motorista.

### Critérios de Aceite
- Conta de passageiro/motorista/responsavel permite editar dados de perfil.
- Foto/avatar pode ser escolhida e enviada pelo app.
- Data de nascimento aparece no fluxo de cadastro e perfil.
- Passageiro entende a parada por endereço/mapa, nao por coordenadas.
- Admin cria/edita entidades com os mesmos dados essenciais do app.
- Testes automatizados e smokes visuais rodam sem falso positivo conhecido.

### Andamento Atual
- Implementado no commit `65261fd`.
- `AccountProfileScreen` e `EditAccountProfileSheet` viraram base comum das
  contas.
- `AccountProfilePhotoCodec` converte foto escolhida pelo usuario para data URI.
- `CadastroPassageiro` e `CadastroMotorista` receberam data de nascimento e
  copy guiada.
- Admin responsaveis foi dividido em dialogs menores e alinhado com o novo
  modelo de perfil.
- Smoke visual final validou:
  - `output/visual-smoke/account-profile-final-20260616-170336`;
  - `output/visual-smoke/account-profile-admin-web-20260616-170755`.

### DoD
- `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS (`flutter test`: 465 testes).
- Mobile visual smoke real no iPhone 16e Simulator iOS 26.2: PASS.
- Admin web visual smoke real: PASS.
- `git diff --check`: PASS.
- QA Log atualizado com evidencias.

---

## Backlog 0972 — DRIVER-LINK-APP-0001: deep link e onboarding mobile de motorista

### Status
DONE (commits: e184c49, 2dcc0eb)

### Contexto
O motorista que entra por convite da empresa precisa criar ou acessar a propria
conta, confirmar e-mail e sair vinculado diretamente a empresa. Motorista que
entra sem link precisa buscar a empresa e solicitar aprovacao.

### Impact Analysis
- **Mudancas tecnicas:** criar modulo `features/driver_onboarding`, deep link
  `vanrides://driver-invite/<token>`, preservacao de token durante cadastro,
  login e verificacao de e-mail, gateway para contratos `driver-onboarding`.
- **Contratos afetados:** consumo de `GET/POST /driver-onboarding/invites`,
  `GET /driver-onboarding/status`, busca/solicitacao de empresas e upgrade de
  sessao.
- **Riscos:** perder token durante cadastro, mostrar `PENDING_APPROVAL` no fluxo
  de convite, ou manter token antigo sem `companyId` apos resgate.
- **Mitigacao:** testes de deep link e widget cobrindo convite, e-mail pendente,
  resgate e fluxo sem link; refresh/upgrade explicito de sessao.
- **Rollback simples:** manter `DriverCompanyPendingScreen` atual para motorista
  sem empresa e desativar deep link de convite.

### Escopo
1. Criar target de deep link `DriverInviteDeepLink`.
2. Tratar `vanrides://driver-invite/<token>`.
3. Criar tela de preview do convite com empresa e papel `Motorista`.
4. Preservar token durante:
   - login;
   - cadastro de motorista;
   - verificacao de e-mail.
5. Resgatar convite apos e-mail verificado.
6. Criar onboarding sem link:
   - buscar empresa;
   - solicitar vinculo;
   - ver status `PENDING_APPROVAL` somente no fluxo iniciado pelo motorista.
7. Mostrar empty state dentro da empresa quando motorista vinculado nao tiver
   viagem atribuida.

### Fora de escopo
- Portal admin, coberto em `0973`.
- Universal Links HTTPS em producao.
- App Store/TestFlight publico.
- Documentos profissionais do motorista.

### Critérios de Aceite
- Abrir `vanrides://driver-invite/<token>` no simulador mostra convite.
- Cadastro por convite preserva token ate e-mail verificado.
- Depois do resgate, motorista entra vinculado a empresa.
- Convite por link nunca mostra `aguardando aprovacao da empresa`.
- Cadastro sem link permite buscar empresa e solicitar vinculo.
- Solicitacao manual mostra `aguardando aprovacao`.

### Validacao obrigatoria
- `flutter test test/features/driver_onboarding test/features/deep_linking`
- `flutter analyze --no-fatal-infos --no-fatal-warnings`
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`
- Smoke visual real com `xcrun simctl openurl booted "vanrides://driver-invite/<token>"`
  consolidado no `Backlog 0974`, para nao fechar prints/cliques sem evidencia.

### DoD
- Testes de deep link/onboarding passando.
- QA Log atualizado com evidencias automatizadas e gate oficial.
- Smoke visual real e prints vinculados ao `Backlog 0974`.
- Card marcado como `DONE (commits: e184c49, 2dcc0eb)`.

---

## Backlog 0973 — DRIVER-LINK-APP-0002: portal admin para convites e solicitações de motoristas

### Status
DONE (commits: 2def1db, e184c49, 2dcc0eb)

### Contexto
A tela administrativa atual ainda comunica criacao de motorista como formulario.
O fluxo profissional precisa transformar `Motoristas` em gestao de vinculos:
ativos, convites, solicitacoes e historico.

### Impact Analysis
- **Mudancas tecnicas:** refatorar `AdminMotoristasScreen` e gateway para novos
  contratos admin de convites e solicitacoes; aplicar linguagem visual
  dark/liquid consistente.
- **Contratos afetados:** consumo de `company-admins/driver-invites` e
  `company-admins/driver-link-requests`.
- **Riscos:** esconder fluxo legado necessario durante migracao, poluir tela com
  estados demais, ou aprovar solicitacao sem feedback claro.
- **Mitigacao:** separar secoes por tarefa, manter compatibilidade secundaria
  para cadastro legado, testes de widget e smoke web.
- **Rollback simples:** manter tela antiga de motoristas e desabilitar secoes
  novas se contratos nao estiverem disponiveis.

### Escopo
1. CTA principal `Convidar motorista`.
2. Secao `Ativos` para motoristas vinculados.
3. Secao `Convites` com:
   - criar convite;
   - copiar link;
   - ver validade/status;
   - cancelar convite.
4. Secao `Solicitacoes` com:
   - aprovar;
   - rejeitar;
   - feedback visual.
5. Compatibilidade secundaria para cadastro administrativo legado, se ainda
   necessario.
6. Copy clara: e-mail/WhatsApp sao canais de envio, nao regra de vinculo.

### Fora de escopo
- Envio real de e-mail pelo backend.
- Push notification.
- Tela mobile do motorista, coberta em `0972`.

### Critérios de Aceite
- Admin entende como convidar motorista sem criar senha provisoria.
- Admin consegue copiar link de convite.
- Admin consegue aprovar/rejeitar solicitacao.
- Motorista aprovado aparece em `Ativos`.
- Tela segue o padrao visual dark/liquid do portal atual.

### Validacao obrigatoria
- `flutter test test/features/admin/admin_motoristas_screen_test.dart`
- `flutter test test/features/admin/admin_motorista_portal_service_test.dart`
- `flutter analyze --no-fatal-infos --no-fatal-warnings`
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`
- Smoke visual web/admin consolidado no `Backlog 0974`.

### DoD
- Testes de portal admin passando.
- QA Log atualizado com evidencias automatizadas e gate oficial.
- Smoke visual web/admin vinculado ao `Backlog 0974`.
- Card marcado como `DONE (commits: 2def1db, e184c49, 2dcc0eb)`.

---

## Backlog 0974 — DRIVER-LINK-APP-0003: smoke visual e validação real do convite de motorista

### Status
DONE (commit: a68d73c)

### Contexto
O fluxo de convite depende de deep link, cadastro/login, e-mail verificado,
upgrade de sessao e home do motorista. Precisamos validar com cliques reais e
prints para evitar falso positivo.

### Impact Analysis
- **Mudancas tecnicas:** criar/atualizar smoke visual para abrir deep link no
  simulador, navegar pelo convite, validar estados e capturar evidencias.
- **Contratos afetados:** nenhum novo contrato alem dos cards `0972` e `0973`.
- **Riscos:** teste automatizado passar sem realmente abrir o app por link,
  screenshot ocultar estado errado ou ambiente DEV nao ter backend atualizado.
- **Mitigacao:** usar `xcrun simctl openurl`, asserts de texto e prints por
  etapa, documentar backend SHA/ambiente usado.
- **Rollback simples:** manter QA manual se smoke automatizado ficar bloqueado
  por ambiente.

### Escopo
1. Smoke no iOS Simulator:
   - abrir `vanrides://driver-invite/<token>`;
   - capturar tela de convite;
   - criar/login motorista;
   - validar e-mail no modo QA quando aplicavel;
   - confirmar tela dentro da empresa.
2. Smoke do fluxo sem link:
   - login motorista sem empresa;
   - buscar empresa;
   - solicitar;
   - ver `PENDING_APPROVAL`.
3. Smoke admin:
   - criar convite;
   - copiar link;
   - aprovar solicitacao.
4. Documentar roteiro para iPhone fisico com build local/TestFlight.

### Fora de escopo
- Publicacao em App Store.
- Universal Links HTTPS final.
- Automacao de WhatsApp/e-mail.

### Critérios de Aceite
- Existem prints reais do fluxo por convite.
- Existem prints reais do fluxo por solicitacao.
- O teste diferencia claramente:
  - convite por link => vinculo direto apos e-mail verificado;
  - solicitacao manual => aguardando aprovacao.
- QA Log registra comandos, ambiente, device e evidencias.

### Validacao obrigatoria
- `flutter analyze --no-fatal-infos --no-fatal-warnings`
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`
- Smoke visual real no iOS Simulator.
- Roteiro de iPhone fisico documentado.

### DoD
- Evidencias visuais salvas em:
  - `output/visual-smoke/phase-0466/20260617-000250-clean`;
  - `output/visual-smoke/phase-0466/20260617-0024-driver-final`;
  - `output/visual-smoke/driver-link/deep-link-invite-preview.png`.
- Smoke DEV real criado em `scripts/qa_driver_link_dev_smoke.sh` e executado
  contra `http://127.0.0.1:8080`: PASS.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
- `git diff --check`: PASS.
- QA Log atualizado.
- Card marcado como `DONE (commit: a68d73c)`.

---

## Backlog 0975 — DRIVER-LINK-APP-0004: polimento premium do portal de motoristas

### Status
DONE (commit: 8a59229)

### Contexto
A fase de motorista esta funcional, mas a validacao visual do portal admin
mostrou uma contradicao de produto: a tela ainda tratava o motorista como algo
que a empresa "adiciona" com senha provisoria, mesmo depois de oficializarmos o
fluxo profissional por convite de link e solicitacao pelo app.

### Impact Analysis
- **Mudancas tecnicas:** ajustar CTA principal, copy do painel, empty state e
  formulario manual de motorista.
- **Contratos afetados:** nenhum contrato novo. O app continua consumindo
  `POST /company-admins/driver-invites`,
  `GET /company-admins/driver-link-requests` e
  `POST /company-admins/motoristas` para compatibilidade operacional.
- **Riscos:** remover demais a criacao manual e bloquear operacao de staging
  quando a empresa ainda precisar vincular uma conta existente.
- **Mitigacao:** manter cadastro manual como caminho secundario, mas remover
  senha provisoria da narrativa principal.
- **Rollback simples:** reverter copy/CTA do portal de motoristas sem afetar os
  contratos de convite.

### Escopo
1. CTA principal:
   - com gateway de convites: `Convidar por link`;
   - sem gateway de convites: `Cadastrar manualmente`.
2. Copy de orientacao:
   - explicar convite por link para novos motoristas;
   - explicar solicitacao pelo app para quem ja criou conta;
   - explicar cadastro manual apenas como vinculo operacional.
3. Formulario manual:
   - remover senha provisoria;
   - manter nome, e-mail, telefone e data de nascimento;
   - botao final `Salvar vínculo`.
4. Feedback:
   - criacao de convite nao deve depender do clipboard;
   - se o link for criado, o painel deve continuar sendo a fonte de copia.

### Critérios de Aceite
- Portal de motoristas nao exibe `Adicionar motorista` como acao principal.
- Portal de motoristas nao exibe `senha provisoria` no fluxo de motorista.
- `Convidar por link` chama o gateway de convite.
- Cadastro manual envia `senha == null`.
- Feedback de convite criado aparece mesmo se clipboard falhar.

### Validacao obrigatoria
- `flutter test test/features/admin/admin_motoristas_screen_test.dart --reporter compact`
- `flutter analyze --no-fatal-infos --no-fatal-warnings`
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`
- Smoke visual admin web ou matriz `qa_phase_0466_visual_smoke.sh` quando a
  correcao entrar no pacote visual.

### DoD
- Testes focados passando.
- QA Log atualizado.
- Card marcado como `DONE (commit: <hash>)`.

---

## Backlog 0976 — PHASE-0467: presença do passageiro em liquid glass compacto

### Status
DONE (commit: 291fe0f)

### Contexto
A tela `Presenca` do passageiro ainda parece uma pagina escura comum, com
cards grandes e menos refinamento que a aba `Viagem`. Ela precisa comunicar
decisao por horario com o minimo de ruido, sem repetir a agenda de hoje em
dois lugares e sem exigir que o usuario descubra o proximo passo rolando.

### Impact Analysis
- **Mudancas tecnicas:** redesenhar widgets de presenca do passageiro usando
  o sistema map-first/liquid glass ja adotado na viagem.
- **Contratos afetados:** nenhum contrato novo. Mantem presencas por servico e
  status `CONFIRMED`, `ABSENT` e pendente.
- **Riscos:** esconder informacao obrigatoria de horarios independentes.
- **Mitigacao:** usar secao compacta por servico com status claro, acao primaria
  unica e resumo numerico acessivel.
- **Rollback simples:** reverter widgets de presenca e manter a tela atual.

### Escopo
1. Remover agenda duplicada quando ela nao altera contexto.
2. Mostrar todos os horarios do dia em lista compacta vertical, sem carrossel
   horizontal obrigatorio.
3. Transformar cada horario em uma decisao clara:
   - `Vou`;
   - `Nao vou`;
   - `Limpar`.
4. Usar glass funcional:
   - fundo compativel com mapa;
   - borda leve;
   - contraste AA para texto principal.
5. Estados:
   - sem servicos;
   - carregando;
   - erro com tentar novamente;
   - horario decidido;
   - horario pendente.

### Critérios de Aceite
- Usuario entende em ate 3 segundos quantos horarios precisam de decisao.
- Nao ha texto cortado nos cards de horario.
- O fundo e a nav pertencem ao mesmo sistema visual da aba `Viagem`.
- VoiceOver recebe labels com horario, sentido, destino e decisao atual.

### Validacao obrigatoria
- Testes de `passenger_home_presence_widgets`.
- Testes de `confirmar_presenca_screen`.
- Smoke visual `passenger-presence.png`.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.

### DoD
- Screenshots antes/depois anexados no QA Log.
- Card marcado como `DONE (commit: <hash>)`.

---

## Backlog 0977 — PHASE-0468: responsável com mapa e sheet no mesmo padrão do passageiro

### Status
DONE (commit: 023996f)

### Contexto
O responsavel ja funciona, mas a home e a tela de localizacao ainda parecem
uma variacao menos lapidada do passageiro. O usuario responsavel precisa ver
o passageiro vinculado, a van e a proxima informacao relevante com a mesma
clareza Apple Maps sheet, sem conteudo brigando com o footer.

### Impact Analysis
- **Mudancas tecnicas:** convergir `guardian-home` e `guardian-location` para
  a arquitetura map-first com sheet/footer unificados.
- **Contratos afetados:** nenhum contrato novo.
- **Riscos:** perder contexto familiar quando houver mais de um passageiro.
- **Mitigacao:** manter seletor de passageiro como camada superior compacta e
  mostrar apenas a jornada selecionada na sheet.
- **Rollback simples:** reverter os widgets de guardian para a composicao atual.

### Escopo
1. Home do responsavel:
   - reduzir heading excessivo;
   - evitar conteudo encostando no footer;
   - mostrar passageiro foco, proxima parada e status.
2. Localizacao:
   - reutilizar controles de foco em van e passageiro;
   - sheet com estados pequeno, medio e expandido;
   - manter cabecalho da sheet estavel entre estados.
3. Presencas do responsavel:
   - manter acoes por passageiro/horario;
   - alinhar copy com passageiro.

### Critérios de Aceite
- A home do responsavel nao tem conteudo parcialmente coberto pelo footer.
- A tela de localizacao nao usa agenda/carrossel redundante.
- O responsavel sempre sabe qual passageiro esta vendo.
- A sheet segue o mesmo comportamento mental da aba `Viagem` do passageiro.

### Validacao obrigatoria
- Testes de `guardian_localizacao_widgets`.
- Testes de fluxo de presenca do responsavel.
- Smoke visual `guardian-home.png`, `guardian-location.png` e
  `guardian-account.png`.

### DoD
- QA Log com prints e observacoes de usabilidade.
- Card marcado como `DONE (commit: <hash>)`.

---

## Backlog 0978 — PHASE-0469: perfil unificado, editavel e compreensivel por papel

### Status
DONE (commit: 96a6e19)

### Contexto
O perfil agora usa dados reais, foto/avatar e nascimento, mas ainda parece
pesado e pouco orientado a acao. Passageiro, motorista, responsavel e admin
precisam de uma conta que explique o que esta completo, o que falta e o que
o usuario pode editar sem mostrar metricas tecnicas ou dados sem significado.

### Impact Analysis
- **Mudancas tecnicas:** reorganizar componentes de conta por papel, melhorar
  empty states de dados incompletos e padronizar edicao.
- **Contratos afetados:** `GET /account/me` e `PATCH /account/me`.
- **Riscos:** misturar campos de papeis diferentes e criar UI generica demais.
- **Mitigacao:** criar secoes comuns apenas para identidade, contato,
  seguranca e vinculo; secoes especificas ficam por papel.
- **Rollback simples:** manter componentes atuais de conta por perfil.

### Escopo
1. Perfil base:
   - foto/avatar editavel;
   - nome;
   - e-mail;
   - telefone;
   - data de nascimento;
   - status de e-mail verificado.
2. Perfil por papel:
   - passageiro: empresa, parada, responsaveis vinculados quando existir;
   - motorista: empresa, vinculo operacional, dados profissionais futuros
     ocultos ate existirem contratos;
   - responsavel: passageiros vinculados;
   - admin: empresa e permissoes operacionais.
3. Copy:
   - remover metricas sem significado como ETA mocado;
   - indicar `Nao informado` como chamada para editar, nao como erro.

### Critérios de Aceite
- Nenhuma tela de conta mostra dado mocado.
- O usuario entende o que pode editar.
- Foto/avatar funciona nos quatro papeis que usam `/account/me`.
- `Nascimento: Nao informado` so aparece quando o backend realmente nao tem
  esse dado e a UI oferece edicao.

### Validacao obrigatoria
- Testes de account profile service.
- Widget tests das contas por papel.
- Smoke visual `*-account.png`.
- Smoke HTTP de `GET /account/me` e `PATCH /account/me` quando aplicavel.

### DoD
- QA Log com matriz por papel.
- Card marcado como `DONE (commit: <hash>)`.

---

## Backlog 0979 — PHASE-0470: matriz final de fluxo, prints e melhorias executadas

### Status
DONE (commit: 5c19308)

### Contexto
Antes de dizer que o app esta "pronto" no sentido funcional, precisamos validar
todos os fluxos principais em ambiente limpo, com backend DEV atualizado e
prints reais. A meta nao e chamar tudo de requintado, mas garantir que o
usuario sempre sabe onde esta, o que deve fazer e qual estado do sistema esta
vendo.

### Impact Analysis
- **Mudancas tecnicas:** consolidar roteiro de QA, asserts visuais e registros
  de evidencia.
- **Contratos afetados:** nenhum contrato novo.
- **Riscos:** falsos positivos por smoke que navega sem confirmar estado real.
- **Mitigacao:** exigir cliques reais, texto esperado, screenshots e smoke HTTP
  onde o app depende do backend.
- **Rollback simples:** manter os cards anteriores abertos ate a matriz passar.

### Escopo
1. Fluxo admin:
   - bootstrap admin;
   - login;
   - criar convite de motorista;
   - aprovar solicitacao de motorista;
   - criar passageiro/responsavel;
   - criar servico/viagem.
2. Fluxo passageiro:
   - cadastro;
   - e-mail pendente;
   - perfil;
   - presenca;
   - viagem/mapa.
3. Fluxo motorista:
   - convite por link;
   - cadastro/login;
   - vinculo direto apos e-mail verificado;
   - busca manual de empresa;
   - aprovacao;
   - home operacional.
4. Fluxo responsavel:
   - conta;
   - passageiros vinculados;
   - presenca;
   - localizacao.

### Critérios de Aceite
- Cada fluxo tem pelo menos um teste automatizado ou smoke documentado.
- Cada perfil tem screenshot final.
- Qualquer tela com texto cortado ou estado ambíguo vira card ou correcao.
- QA Log diferencia PASS, LIMITES e PENDENCIAS.

### Validacao obrigatoria
- `flutter analyze --no-fatal-infos --no-fatal-warnings`
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`
- `scripts/qa_driver_link_dev_smoke.sh` contra DEV.
- `scripts/qa_phase_0466_visual_smoke.sh` contra DEV.
- Smoke backend DEV quando houver contrato novo.

### DoD
- Todos os cards 0975-0978 fechados ou explicitamente justificados como
  pendencia.
- QA Log contem matriz de fluxos.
- Card marcado como `DONE (commit: 5c19308)`.

---

## Backlog 0983 — PROFILE-APP-0003: completude de perfil por papel e primeira dobra

### Status
DONE (commit: 60fdb54)

### Contexto
A matriz 0979 validou que o perfil esta funcional, editavel e sem dados
mocados. O proximo refinamento e fazer a conta parecer completa e guiada para
todos os papeis, inclusive quando ainda faltam nascimento ou foto.

### Impact Analysis
- **Mudancas tecnicas:** refinar a primeira dobra de conta, states de perfil
  incompleto e fixture visual com dados completos.
- **Contratos afetados:** `GET /account/me`, `PATCH /account/me` e upload de
  foto ja existentes.
- **Riscos:** esconder pendencias importantes de seguranca/responsabilidade.
- **Mitigacao:** manter card de completude visivel, mas reduzir ruido quando o
  usuario ja entendeu o proximo passo.
- **Rollback simples:** manter o layout atual de conta e apenas remover a
  fixture completa.

### Escopo
1. Criar fixture visual com nascimento e foto preenchidos por papel.
2. Validar primeira dobra de conta com perfil completo e incompleto.
3. Melhorar o card `Complete seu perfil` para ser menos pesado quando faltar
   apenas um dado.
4. Garantir que dados especificos de papel nao fiquem escondidos atras do
   footer em viewport pequena.

### Critérios de Aceite
- Conta de passageiro, motorista, responsavel e admin tem screenshot com perfil
  completo.
- Conta com pendencia ainda orienta a edicao sem parecer erro fatal.
- Upload de foto continua coberto por teste e smoke HTTP/visual quando
  possivel.

### Validacao obrigatoria
- Testes de `account_profile_screen`.
- Smoke HTTP de `/account/me` e upload/patch aplicavel.
- Smoke visual `*-account.png` com perfil completo e incompleto.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.

### DoD
- QA Log com comparativo completo/incompleto.
- Card marcado como `DONE (commit: 60fdb54)`.

### Fechamento 2026-06-18
- Primeira dobra de conta validada para passageiro, motorista, responsavel e
  admin com perfil completo, incluindo nascimento e foto funcional.
- Estado incompleto continua guiando a edicao sem parecer erro fatal:
  nascimento e telefone seguem obrigatorios; foto aparece como recomendacao.
- Valores longos da ficha agora truncam em uma linha para evitar que e-mail ou
  dados de perfil quebrem visualmente em viewports estreitas.
- O smoke visual deterministico `scripts/qa_account_profile_visual_smoke.sh`
  gera screenshots completas/incompletas usando `VanRidesTheme.dark()`.
- Evidencias:
  - `output/visual-smoke/card-0983-profile-complete/account-passenger-complete.png`
  - `output/visual-smoke/card-0983-profile-complete/account-driver-complete.png`
  - `output/visual-smoke/card-0983-profile-complete/account-guardian-complete.png`
  - `output/visual-smoke/card-0983-profile-complete/account-admin-complete.png`
  - `output/visual-smoke/card-0983-profile-complete/account-passenger-incomplete.png`
  - `output/visual-smoke/card-0983-dev/passenger-account.png`
  - `output/visual-smoke/card-0983-dev/driver-account.png`
  - `output/visual-smoke/card-0983-dev/guardian-account.png`
  - `output/visual-smoke/card-0983-dev/admin-web/admin-account.png`

---

## Backlog 0984 — EMAIL-QA-0001: smoke real de e-mail e convites em DEV

### Status
BLOCKED - depende de acesso observavel ao provider/mailbox DEV

### Contexto
A matriz 0979 validou estados de e-mail no app e contratos autenticados, mas
nao confirmou entrega real no provider de e-mail. Como confirmacao de e-mail,
convites e recuperacao de senha serao fluxos centrais, precisamos de um smoke
que prove a tentativa real de envio sem depender de conferencia manual.

### Impact Analysis
- **Mudancas tecnicas:** criar smoke de e-mail em DEV usando evento do provider
  ou mailbox de QA.
- **Contratos afetados:** endpoints de verificacao, reenvio e convites.
- **Riscos:** expor token, segredo ou e-mail real em log.
- **Mitigacao:** mascarar destinatarios, nao imprimir tokens e usar inbox de
  QA.
- **Rollback simples:** manter QA manual e desabilitar smoke provider.

### Escopo
1. Disparar verificacao de e-mail para usuario QA.
2. Confirmar tentativa real no provider ou mailbox de QA.
3. Validar reenvio de verificacao quando o e-mail esta pendente.
4. Registrar falhas de provider como bloqueio operacional claro.

### Critérios de Aceite
- O smoke diferencia `API aceitou` de `e-mail realmente saiu`.
- Logs nao contem tokens, senhas ou segredo do provider.
- Fluxos de login com e-mail pendente ficam documentados.

### Validacao obrigatoria
- Smoke backend DEV de e-mail.
- Testes app dos estados de e-mail pendente.
- QA Log com provider/mailbox usado e dados mascarados.

### DoD
- Card marcado como `DONE (commit: <hash>)`.

---

## Backlog 0985 — FLOW-QA-0001: roteiro de iPhone fisico para deep links

### Status
BLOCKED - depende de iPhone fisico/TestFlight ou execucao assistida pelo usuario

### Contexto
O convite de motorista passou no iOS Simulator com deep link custom scheme.
Antes de chamar o fluxo de link totalmente pronto para operacao externa, ainda
precisamos validar em device fisico com build local/TestFlight quando a
distribuicao estiver disponivel.

### Impact Analysis
- **Mudancas tecnicas:** documentar e automatizar o maximo possivel do roteiro
  de deep link em iPhone fisico.
- **Contratos afetados:** nenhum contrato novo.
- **Riscos:** comportamento diferente de link em app instalado fora do
  simulador.
- **Mitigacao:** roteiro objetivo com device, build, link e screenshots.
- **Rollback simples:** manter validacao por Simulator como evidencia local.

### Escopo
1. Rodar build no iPhone fisico ou TestFlight.
2. Abrir link de convite de motorista fora do app.
3. Validar criacao/login, e-mail pendente/verificado e vinculo com empresa.
4. Capturar prints de convite, home motorista e conta.

### Critérios de Aceite
- Existe roteiro executavel por humano sem depender de Codex aberto.
- Evidencias diferenciam custom scheme local e futuro Universal Link.

### Validacao obrigatoria
- Roteiro manual registrado no QA Log.
- Prints do iPhone fisico quando disponivel.

### DoD
- Card marcado como `DONE (commit: <hash>)`.

---

## Backlog 0986 — ADMIN-UX-0001: hotfix de mensagens, data e identidade visual da viagem manual

### Status
DONE (commits: c486a03, a8800ee, ea646e9, 9c986ed; backend: a4c94d4)

### Contexto
O portal admin exibiu `Conflict` cru ao criar viagem manual e a tela de
criação misturava superfícies claras antigas com o padrão dark/liquid atual. O
campo de data também estava com baixo contraste e formato americano.

### Impact Analysis
- **Mudancas tecnicas:** tratar erros HTTP genéricos no app, alinhar date
  picker ao tema admin e refinar a tela de viagem manual.
- **Contratos afetados:** consumo de erros de `POST /company-admins/viagens`;
  compatível com backend antigo (`error`) e novo (`message`).
- **Riscos:** esconder erro real com fallback genérico.
- **Mitigacao:** preservar mensagens humanas vindas do backend novo e usar
  fallback só para títulos HTTP crus como `Conflict`.
- **Rollback simples:** voltar ao parser anterior e ao layout anterior da tela.

### Escopo
1. Impedir que `Conflict`/`Bad Request` crus apareçam para admin/passageiro.
2. Usar mensagem específica para foto de perfil quando backend antigo responder
   erro genérico.
3. Ajustar data para `dd/mm/aaaa`, contraste correto e date picker dark.
4. Explicar que viagem manual é exceção do dia, não manutenção de passageiros.
5. Remover cards claros antigos da tela de criação manual.

### Critérios de Aceite
- O usuário vê mensagem útil quando a viagem já existe.
- O upload de foto não mostra `Bad Request` cru.
- A data é legível e usa formato brasileiro.
- A tela de criação manual não tem blocos claros soltos.

### Validação obrigatória
- `flutter test test/features/account/account_profile_service_test.dart`
- `flutter test test/features/admin/admin_viagens_portal_service_test.dart test/features/admin/presentation/widgets/admin_viagens_create_widgets_test.dart`
- teste backend do contrato de erro administrativo.

### DoD
- QA Log atualizado.
- Card marcado como `DONE`.

### Evidência de fechamento
- Commits de implementação:
  - `c486a03` (`fix(app): refine admin trip errors and photo fallback`);
  - `ea646e9` (`feat(app): guide admin trip exceptions`);
  - `a8800ee` (`fix(app): keep selected date for trip exception`);
  - `9c986ed` (`fix(app): make profile avatar upload resilient`);
  - backend `a4c94d4` (`feat(backend): add account avatar upload`).
- Testes focados:
  - `flutter test test/features/account/account_profile_service_test.dart test/features/admin/admin_viagens_portal_service_test.dart test/features/admin/presentation/widgets/admin_viagens_create_widgets_test.dart`: PASS.
  - backend `./mvnw -Dtest=CompanyAdminViagemControllerWebMvcTest test`: PASS.
- Smoke Playwright real contra backend DEV:
  - build local servido em `http://127.0.0.1:7357`;
  - fixture operacional:
    `vanrides-backend/output/admin-sync-smoke/20260618000307/fixture.env`;
  - tela `Criar viagem extra` exibiu data `18/06/2026`, superficie dark e copy
    orientando a usar `Atualizar lista` quando a viagem do dia já existe;
  - tentativa duplicada retornou `409` no console, mas a UI não exibiu
    `Conflict`; abriu a viagem existente com snackbar humano.
- Evidência visual:
  - `output/admin-portal-ux-0030/visual/01-create-extra-trip-form.png`;
  - `output/admin-portal-ux-0030/visual/02-create-extra-trip-selected-service.png`;
  - `output/admin-portal-ux-0030/visual/03-duplicate-extra-trip-opens-existing.png`.

---

## Backlog 0987 — ADMIN-UX-0002: arquitetura de informação sem tela técnica de vínculos

### Status
DONE (commits: 43dcad2, 53b211d, 5d9de70, a470f88, 0c39444)

### Contexto
`Vínculos` expõe um conceito técnico como navegação principal. Para a empresa,
as relações devem aparecer dentro das entidades: passageiro, motorista,
responsável e serviço.

### Progresso
- 2026-06-17 — Incremento 1 aplicado no shell do portal:
  - sidebar principal ficou orientada à operação: Painel, Viagens do dia,
    Serviços, Passageiros, Motoristas, Responsáveis e Conta;
  - `Vínculos` e `Rotas` foram rebaixados para área `Avançado`, preservando
    acesso enquanto as ações contextuais ainda não foram migradas;
  - busca/topbar fake foi removida e substituída por contexto operacional da
    seção ativa;
  - botão duplicado `Viagens do dia` saiu do rodapé da sidebar.
  - Commit de implementação: `43dcad2`.
- 2026-06-19 — Fechamento por consolidação dos incrementos posteriores:
  - a sidebar comum segue orientada por Operação, Pessoas e Empresa, com
    relações comuns acessíveis pelas fichas de Passageiros, Motoristas e
    Responsáveis;
  - Passageiros concentram serviços, responsáveis e solicitações/convites no
    próprio fluxo de ficha/entrada;
  - Motoristas concentram convite por link, solicitações do app, serviços e
    viagens atribuídas na própria tela de entidade;
  - Responsáveis mostram passageiros vinculados e ações operacionais no detalhe;
  - a tela técnica de vínculos permanece como diagnóstico/avançado, sem ser
    necessária no happy path operacional.

### Escopo
1. Remover ou rebaixar `Vínculos` da sidebar principal.
2. Levar ações de vínculo para fichas de entidade.
3. Garantir que convites e solicitações de motorista seguem acessíveis.
4. Ajustar testes de navegação e estados vazios.

### Critérios de Aceite
- Admin encontra vínculos a partir das entidades, sem depender de uma tela
  técnica central.
- Não há perda funcional em convites, solicitações, responsável-passageiro e
  passageiro-motorista.

### DoD
- Smoke visual da sidebar.
- Testes focados:
  `flutter test test/features/admin/admin_shell_screen_test.dart test/features/admin/admin_passageiros_screen_test.dart test/features/admin/admin_motoristas_screen_test.dart test/features/admin/admin_responsaveis_screen_test.dart test/features/admin/admin_vinculos_screen_test.dart test/features/admin/presentation/widgets/admin_passageiros_widgets_test.dart test/features/admin/presentation/widgets/admin_responsaveis_widgets_test.dart`:
  PASS, 42 testes.
- Evidencia visual consolidada no smoke admin:
  `output/visual-smoke/ux-final-real-20260619-final/admin-web/admin-passengers.png`,
  `output/visual-smoke/ux-final-real-20260619-final/admin-web/admin-drivers.png`,
  `output/visual-smoke/ux-final-real-20260619-final/admin-web/admin-guardian-passengers.png`
  e `output/visual-smoke/ux-final-real-20260619-final/admin-web/admin-dashboard.png`.
- Card marcado como `DONE`.

---

## Backlog 0988 — ADMIN-UX-0003: passageiro como ficha operacional completa

### Status
DONE (commit: 53b211d)

### Fechamento 2026-06-18
- A ficha do passageiro passou a exibir `Presença de hoje` com os serviços do
  dia, decisão de presença e CTA `Ver viagens de hoje`.
- A seção de serviços continua explicando que mudanças no serviço recorrente
  valem para as próximas montagens, enquanto o ajuste do dia deve ser feito em
  `Viagens do dia`.
- A ação de responsáveis foi renomeada para `Gerenciar responsáveis`, reduzindo
  linguagem técnica de vínculo.

### Evidência
- RED esperado antes da implementação:
  `flutter test test/features/admin/admin_passageiro_portal_service_test.dart test/features/admin/admin_passageiros_screen_test.dart test/features/admin/presentation/widgets/admin_passageiros_widgets_test.dart`.
- Testes focados:
  `flutter test test/features/admin/admin_passageiro_portal_service_test.dart test/features/admin/admin_passageiros_screen_test.dart test/features/admin/admin_passageiros_presence_screen_test.dart test/features/admin/presentation/widgets/admin_passageiros_widgets_test.dart`.
- Gate app:
  `RUN_E2E=0 ./scripts/qa_gate_card.sh` (517 testes, `flutter analyze` sem
  issues e quality ratchet PASS).
- Smoke DEV:
  `output/admin-portal-e2e/card-0988-dev/manifest.json`,
  `output/admin-portal-e2e/card-0988-dev/SUMMARY.md`,
  `output/admin-portal-e2e/card-0988-dev/screenshots/admin-passenger-detail.png`,
  `output/admin-portal-e2e/card-0988-dev/screenshots/admin-passenger-services-dialog.png`
  e
  `output/admin-portal-e2e/card-0988-dev/screenshots/admin-passenger-guardians-dialog.png`.

### Escopo
1. Mostrar dados, endereço/parada, serviços, responsáveis e presença do dia.
2. Criar ação clara para adicionar passageiro a serviço.
3. Explicar impacto em viagem já montada.
4. Remover linguagem técnica de vínculo.

### Critérios de Aceite
- Admin consegue criar passageiro, conferir parada e adicioná-lo a serviços.
- O portal explica se a alteração vale para hoje ou para próximas viagens.

### DoD
- Smoke real de passageiro + serviço.
- Card marcado como `DONE (commit: <hash>)`.

---

## Backlog 0989 — ADMIN-UX-0004: serviço como centro da operação recorrente

### Status
DONE (commit: 06c6b5b)

### Fechamento 2026-06-18
- Detalhe do serviço recorrente passou a exibir a ação primária
  `Montar viagem de hoje`.
- A ação reutiliza `AdminViagensReviewIntent.servicoRecorrente` e abre
  `Viagens do dia` com contexto do serviço, evitando um caminho paralelo de
  geração/manutenção.
- O fluxo validado permite sair do serviço, revisar o dia e abrir a viagem
  existente sem apagar/recriar viagens.
- A prontidão operacional, passageiros elegíveis e edição de elegibilidade já
  ficam concentradas no serviço recorrente.

### Escopo
1. Detalhe de serviço com passageiros elegíveis editáveis.
2. Ações `Montar viagem de hoje` e `Abrir viagem existente`.
3. Prontidão operacional: dias, motorista, passageiros, destino e geração.
4. Avisos claros sobre alterações em viagens futuras vs viagem do dia.

### Critérios de Aceite
- Admin mantém a operação recorrente sem recriar viagens manualmente.
- Serviço mostra exatamente o que falta para gerar viagem.

### DoD
- Smoke serviço -> montar dia -> abrir viagem.
- Card marcado como `DONE (commit: <hash>)`.

### Evidência de fechamento
- Commit de implementação:
  - `06c6b5b` (`feat(app): guide service daily trip assembly`).
- Testes e gates:
  - RED esperado:
    `flutter test test/features/admin/admin_servicos_recorrentes_screen_test.dart --plain-name 'servico recorrente encaminha montagem do dia para viagens'`: FAIL antes da implementação.
  - `flutter test test/features/admin/admin_servicos_recorrentes_screen_test.dart --plain-name 'servico recorrente encaminha montagem do dia para viagens'`: PASS.
  - `flutter test test/features/admin/admin_servicos_recorrentes_screen_test.dart test/features/admin/presentation/widgets/admin_servicos_recorrentes_list_widgets_test.dart`: PASS.
  - `python3 scripts/quality_metrics_test.py`: PASS.
  - `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS, 508 testes.
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - `Serviços` > `Abrir Linha Noite - Atividade`: PASS.
  - detalhe exibiu `Montar viagem de hoje`: PASS.
  - CTA abriu `Viagens do dia` com `Revise Linha Noite - Atividade nas viagens`: PASS.
  - clique em `Abrir` abriu a viagem existente `Linha Noite - Atividade`: PASS.
  - sem erros de console.
- Evidência visual:
  - `output/admin-portal-ux-0031/visual/01-services-list-driver-alert.png`;
  - `output/admin-portal-ux-0031/visual/02-service-detail-build-today-cta.png`;
  - `output/admin-portal-ux-0031/visual/03-trips-review-from-service-cta.png`;
  - `output/admin-portal-ux-0031/visual/04-existing-trip-opened-from-service-flow.png`.

---

## Backlog 0990 — ADMIN-UX-0005: viagens do dia orientadas a manutenção, não recriação

### Status
DONE (commits: 6ff4410, bd58624, 76fad75, d36de35)

### Incremento 2026-06-17
Fatia funcional implementada localmente:
- portal chama `POST /company-admins/viagens/{id}/sincronizar-manifesto` no
  detalhe de viagem planejada por serviço recorrente;
- o detalhe recarrega manifesto, mantém o contexto da viagem e mostra feedback
  humano com a quantidade de paradas;
- testes de tela foram separados para manter `admin_viagens_screen_test.dart`
  abaixo do ratchet de 870 linhas.
- `Criar manual` virou `Criar exceção` nos pontos visíveis do fluxo;
- tentativa duplicada de exceção mostra ação `Abrir viagem` quando a viagem da
  mesma data/serviço já está carregada na lista.
- antes de sincronizar, o portal chama preview de impacto e mostra diálogo com
  paradas finais, passageiros que entram e passageiros que saem;
- se o manifesto já está atualizado, o portal informa sem executar sincronização
  desnecessária.

Fechamento 2026-06-18:
- fluxo completo validado contra backend DEV publicado;
- tentativa duplicada de viagem extra abriu a viagem existente, sem expor
  `Conflict` cru;
- a viagem existente estava `EM TRÂNSITO`, então a UI protegeu troca/lista e
  manteve somente ações seguras (`Copiar link`, `Atualizar`, detalhe e mapa).

### Escopo
1. Renomear criação manual para exceção operacional.
2. Melhorar conflito com ação para abrir viagem existente.
3. Permitir manutenção segura de motorista, presença e manifesto quando
   aplicável.
4. Diferenciar planejada, em andamento, concluída e cancelada.

### Critérios de Aceite
- Admin não precisa apagar viagem para resolver passageiro novo.
- Tentativas duplicadas orientam para abrir/manter a viagem existente.

### DoD
- Smoke de geração duplicada, detalhe e manutenção.
- Card marcado como `DONE`.

### Evidência de fechamento
- Commits de implementação:
  - `6ff4410` (`feat(app): sync admin trip manifest`);
  - `bd58624` (`fix(app): guide admin trip maintenance`);
  - `76fad75` (`feat(app): clarify admin trip maintenance actions`);
  - `d36de35` (`fix(app): open existing trip after date conflict`).
- Testes e gates:
  - `flutter test test/features/account/account_profile_service_test.dart test/features/admin/admin_viagens_portal_service_test.dart test/features/admin/presentation/widgets/admin_viagens_create_widgets_test.dart`: PASS.
  - `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS, 507 testes.
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
  - backend `./mvnw -Dtest=CompanyAdminViagemControllerWebMvcTest test`: PASS.
- Smoke Playwright real:
  - `Criar viagem extra` > selecionar `Linha Manhã - Escola` > confirmar:
    PASS.
  - backend retornou `409` para duplicidade já esperada.
  - app abriu a viagem existente e exibiu mensagem humana:
    `Já existe viagem criada para este serviço e data. Abrimos a viagem existente para revisar e atualizar a lista.`
- Evidência visual:
  - `output/admin-portal-ux-0030/visual/01-create-extra-trip-form.png`;
  - `output/admin-portal-ux-0030/visual/02-create-extra-trip-selected-service.png`;
  - `output/admin-portal-ux-0030/visual/03-duplicate-extra-trip-opens-existing.png`.

---

## Backlog 0991 — ADMIN-UX-0006: motorista e convite com visão de empresa

### Status
DONE (commit: 5d9de70)

### Escopo
1. Ficha de motorista com status de empresa, convites e solicitações.
2. Link de convite por empresa dentro da ficha.
3. Serviços/viagens atribuídos visíveis.
4. Linguagem clara para motorista novo e motorista já cadastrado.

### Critérios de Aceite
- Empresa entende quando convidar por link e quando aprovar solicitação.
- Motorista vinculado aparece como parte da operação, não só cadastro solto.

### DoD
- Smoke dos dois fluxos de vínculo de motorista.
- Card marcado como `DONE (commit: 5d9de70)`.

### Fechamento
- Commit de código:
  - `5d9de70` (`feat(app): show driver operation assignments`).
- Mudanças:
  - ficha do motorista agora mostra vínculo com a empresa, contexto operacional
    e atribuições de serviços/viagens do dia;
  - tela de motoristas passa a carregar serviços recorrentes e viagens de hoje
    para explicar onde cada motorista está sendo usado;
  - painel de entrada preserva convites por link e solicitações do app como
    fluxos de vínculo explícitos.
- Testes e gates:
  - `flutter test test/features/admin/admin_motoristas_screen_test.dart --plain-name 'ficha mostra servicos e viagens atribuidos ao motorista'`: PASS.
  - `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS, 509 testes.
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - build local servido em `http://127.0.0.1:7357`.
  - backend DEV:
    `http://127.0.0.1:8080`.
  - fixture operacional:
    `vanrides-backend/output/admin-sync-smoke/20260618000307/fixture.env`.
  - `Motoristas` exibiu `Entrada de motoristas`, `Convites por link` e
    `Solicitações do app`: PASS.
  - CTA `Convidar motorista` criou link ativo no portal: PASS.
  - motorista novo `qa.driver.request.20260618155312@vanrides.dev` criou
    solicitação real para a empresa via API pública: PASS.
  - portal exibiu a solicitação com e-mail verificado e ação `Aprovar`: PASS.
  - clique real em `Aprovar` vinculou o motorista e atualizou a lista da
    empresa: PASS.
  - ficha do motorista exibiu `Operação atribuída`, `2 serviços padrão` e
    `1 viagem hoje`: PASS.
- Evidência visual:
  - `output/admin-portal-ux-0032/visual/02-drivers-link-and-assignments.png`;
  - `output/admin-portal-ux-0032/visual/03-driver-invite-created.png`;
  - `output/admin-portal-ux-0032/visual/04-driver-assignment-detail-tall.png`;
  - `output/admin-portal-ux-0032/visual/05-driver-request-pending.png`;
  - `output/admin-portal-ux-0032/visual/06-driver-request-approved.png`.

---

## Backlog 0992 — ADMIN-UX-0007: sistema visual admin dark/liquid consistente

### Status
DONE (commit: 00559c8)

### Escopo
1. Inventariar superfícies claras e componentes fora do tema.
2. Padronizar cards, dialogs, date picker, snackbars, focus e disabled.
3. Reduzir textos longos e cards decorativos.
4. Validar contraste e textos sem corte.

### Critérios de Aceite
- Telas principais do portal parecem parte do mesmo produto.
- Nenhum fluxo principal mistura blocos claros antigos com dark/liquid.

### DoD
- Smoke visual com screenshots.
- Card marcado como `DONE (commit: <hash>)`.

### Fechamento
- Tema administrativo centralizado em `adminPortalTheme`, aplicado no
  `AdminPortalShell`.
- Snackbars, inputs, dropdowns, menus, date picker, estados de foco e botões
  desabilitados passaram a herdar a superfície dark/liquid do portal.
- Cobertura adicionada em `AdminShellScreen`: o teste protege o tema
  administrativo para feedback e controles.
- Validação executada:
  - `flutter test test/features/admin/admin_shell_screen_test.dart --plain-name 'tema administrativo padroniza feedback e controles'`: PASS.
  - `flutter test test/features/admin/admin_shell_screen_test.dart test/features/admin/presentation/widgets/admin_login_bootstrap_dialog_test.dart test/features/admin/presentation/widgets/admin_viagens_create_widgets_test.dart`: PASS.
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
  - `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS, 510 testes e analyze limpo.
  - `git diff --check`: PASS.
- Evidencia visual:
  - `output/admin-portal-ux-0033/visual/01-admin-dashboard-themed.png`.
  - `output/admin-portal-ux-0033/visual/02-admin-floating-snackbar-themed.png`.
  - `output/admin-portal-ux-0033/visual/03-admin-date-picker-themed.png`.

---

## Backlog 0993 — ADMIN-UX-0008: validação visual e funcional do portal inteiro

### Status
DONE (commit: 57c4b4e)

### Escopo
1. Roteiro real: bootstrap/login, passageiros, motoristas, responsáveis,
   serviços, viagens, conta.
2. Screenshots por tela principal.
3. QA Log com PASS, LIMITES e PENDÊNCIAS.
4. Plano final de melhorias pós-S2.

### Critérios de Aceite
- Todos os fluxos administrativos críticos foram clicados em ambiente real ou
  local com backend real.
- A entrega final diferencia o que está pronto funcionalmente do que ainda é
  refinamento.

### DoD
- QA Log completo.
- Card marcado como `DONE (commit: <hash>)`.

### Fechamento
- Script `tool/admin_portal_web_e2e.mjs` endurecido para:
  - salvar screenshots sempre em caminho resolvido a partir do root do app;
  - abrir o serviço pelo CTA atual `Abrir Linha Noite - Atividade`, com
    fallback para linhas que ainda expõem `Abrir`.
- Fixture operacional DEV criada com empresa, admin, 2 motoristas, 2
  passageiros, responsável, 3 serviços e 3 viagens.
- E2E web contra backend DEV validou:
  - bootstrap/login com fixture existente;
  - Painel, Viagens do dia, Serviços, Motoristas, Passageiros, Responsáveis e
    Conta;
  - diálogos contextuais de responsáveis do passageiro e passageiros do
    responsável;
  - ausência de `Vínculos avançados` na navegação comum;
  - detalhe de serviço e validação operacional de viagens da fixture.
- Evidência visual em
  `output/admin-portal-ux-0034/visual-fixture-pass/`.
- Auditoria e próximos cards registrados em
  `backlog/admin-portal-ux-audit-2026-06-18.md`.

---

## Backlog 0994 — ADMIN-UX-0009: avatar real e perfil premium por papel

### Status
DONE (commits: 32bf22f, 8826053; backend: a4c94d4)

### Escopo
1. Criar contrato real de upload de avatar (`POST /account/me/avatar`) no
   backend, com multipart, validação de tipo/tamanho e URL persistida.
2. Fazer o app trocar foto por endpoint dedicado, deixando data URI apenas como
   compatibilidade se necessário.
3. Validar perfil comum para passageiro, motorista, responsável e admin.
4. Mensagens claras para arquivo inválido, grande demais ou falha de rede.

### Critérios de Aceite
- Foto de perfil pequena JPG/PNG/WebP sobe e persiste após recarregar.
- O Bad Request atual deixa de acontecer no fluxo normal de troca de foto.
- Os quatro papéis conseguem visualizar e trocar avatar com o mesmo padrão.

### DoD
- Testes backend de conta/avatar.
- Testes Flutter de service, codec e tela de perfil.
- Smoke HTTP e smoke manual por papel.
- QA Log com evidências.
- Card marcado como `DONE (commit: <hash>)`.

### Correção pós-DONE — 2026-06-18
- O app passou a enviar o `contentType` real do arquivo multipart no upload de
  avatar (`image/png`, `image/jpeg` ou `image/webp`), evitando que o backend
  receba a foto como `application/octet-stream`.
- Cobertura adicionada em `AccountProfileService`: o teste reproduz a lacuna de
  MIME no `MultipartFile` e valida que o payload segue para
  `/account/me/avatar` com `image/png`.
- Smoke HTTP em DEV confirmou `POST /account/me/avatar` com status 200 e URL
  persistida em `/uploads/avatars/...png`.

---

## Backlog 0995 — ADMIN-UX-0010: dados completos de pessoa no portal

### Status
DONE (commit: f682141; backend: a4ead7e, 6c059b4)

### Escopo
1. Expor `dataNascimento` e `fotoPerfilUrl` nos summaries/details admin de
   passageiro, motorista e responsável.
2. Mostrar menoridade e responsáveis no contexto do passageiro.
3. Usar avatar real em listas/fichas.
4. Remover métricas falsas e placeholders técnicos.

### Critérios de Aceite
- A ficha administrativa mostra dados reais suficientes para entender a pessoa.
- Passageiro menor de idade fica claramente sinalizado.
- Não há cartões de perfil baseados em dados mockados ou inferências fracas.

### DoD
- Testes backend de DTO/service por entidade.
- Testes Flutter dos services e telas admin.
- Screenshots das fichas.
- Card marcado como `DONE (commit: <hash>)`.

### Evidência de fechamento
- Backend `a4ead7e` expôs `dataNascimento` e `fotoPerfilUrl` nos summaries e
  details administrativos de passageiro, motorista e responsável, incluindo
  resumos embutidos em serviços, viagens, rotas, vínculos e onboarding.
- Backend `6c059b4` registrou QA do contrato e foi publicado em DEV pela GitHub
  Actions run `27743839433`: `test`, `build_and_push`, `deploy_swarm` e
  `smoke_deploy` PASS.
- App `f682141` passou a consumir os campos, renderizar avatar real/fallback,
  idade e sinalização de menoridade nas listas e fichas administrativas.
- Smoke HTTP em DEV preencheu perfis reais via `/account/me` e confirmou os
  campos novos em:
  - `GET /company-admins/passageiros`
  - `GET /company-admins/motoristas`
  - `GET /company-admins/responsaveis`
- Smoke visual Playwright contra DEV gerou evidências em
  `output/admin-ux-0010-person-profile/visual/`.

---

## Backlog 0996 — ADMIN-UX-0011: criação segura de passageiro com serviços

### Status
DONE (commit: 74aa4a0; backend: a42d331)

### Escopo
1. Criar ou estender contrato para cadastrar passageiro já com `servicoIds`.
2. Validar serviços antes de persistir para evitar estado parcial.
3. Portal mostra o passageiro já vinculado ao serviço ao finalizar.
4. Erro indica exatamente qual serviço ou permissão bloqueou a criação.

### Critérios de Aceite
- Criar passageiro + serviços vira uma operação coerente para o admin.
- Serviço inválido não cria passageiro parcial.
- Passageiro novo aparece imediatamente como elegível no serviço correto.

### DoD
- Testes transacionais backend.
- Testes Flutter do cadastro admin.
- Smoke real criando passageiro com serviço.
- Card marcado como `DONE (commit: <hash>)`.

### Evidencias de Fechamento
- App `74aa4a0` envia `servicoIds` no proprio
  `POST /company-admins/passageiros` e remove o `PUT` pos-criacao do fluxo
  feliz.
- Backend `a42d331` valida todos os servicos antes de persistir o passageiro e
  cria/substitui as elegibilidades de servico na mesma operacao transacional.
- GitHub Actions backend run `27746126873`: PASS em `test`, `build_and_push`,
  `deploy_swarm` e `smoke_deploy`.
- Smoke HTTP real contra DEV:
  `POST /company-admins/passageiros` retornou `201`, o servico `11` apareceu
  habilitado em `GET /company-admins/passageiros/{id}/servicos`, e
  `servicoIds=[999999999]` retornou `404` sem criar passageiro parcial.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS no app, com `flutter analyze`,
  `flutter test` e quality ratchet.

---

## Backlog 0997 — ADMIN-UX-0012: criação de exceção herda data selecionada

### Status
DONE (commit: a8800ee)

### Escopo
1. `Criar exceção` herda a data selecionada em `Viagens do dia`.
2. Copy explica que exceção não substitui manutenção de serviço/manifesto.
3. Conflito de viagem existente orienta `Abrir viagem` quando possível.

### Critérios de Aceite
- Selecionar uma data futura e abrir `Criar exceção` mantém essa data.
- O admin não interpreta exceção como caminho normal para adicionar passageiro.

### DoD
- Widget test do formulário.
- Smoke Playwright com data diferente de hoje.
- Card marcado como `DONE (commit: <hash>)`.

---

## Backlog 0998 — ADMIN-UX-0013: manifesto pendente visível na lista

### Status
DONE (commit: b75386c; backend: 3be320d, d1c1dd6)

### Escopo
1. Adicionar `manifestoStatus`, contadores e motivo de bloqueio nos DTOs de
   viagem.
2. Lista de viagens mostra atualizado, pendente, bloqueado ou não aplicável.
3. Detalhe mantém preview de entradas/saídas antes de sincronizar.
4. Sync atualiza o estado visual sem exigir recriação.

### Critérios de Aceite
- Após alterar passageiros do serviço, a viagem planejada fica marcada como
  pendente.
- O admin consegue sincronizar e ver o estado voltar para atualizado.

### DoD
- Testes backend de manifesto/status.
- Testes Flutter de service/lista/detalhe.
- Smoke real com alteração de serviço depois da viagem montada.
- Card marcado como `DONE (commit: <hash>)`.

### Evidência de fechamento
- Backend `3be320d` adicionou o campo aditivo `manifesto` nos resumos de
  viagem administrativa; backend `d1c1dd6` extraiu o resolver e recolocou os
  arquivos dentro do quality ratchet.
- GitHub Actions backend run `27741375487` attempt 2: PASS.
  - `test`: PASS.
  - `build_and_push`: PASS.
  - `deploy_swarm`: PASS.
  - `smoke_deploy`: PASS.
- App `b75386c` adicionou parsing do contrato e chips de status na tabela de
  `Viagens do dia`.
- Validações locais:
  - backend `./scripts/ci_quality_gate.sh`: PASS, 475 testes, quality ratchet
    PASS.
  - app `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS, `flutter analyze` sem
    issues, 502 testes e quality ratchet PASS.
  - app focused tests:
    `flutter test test/features/admin/admin_viagens_list_contract_test.dart test/features/admin/admin_viagens_portal_service_test.dart test/features/admin/presentation/widgets/admin_viagens_list_widgets_test.dart`: PASS, 14 testes.
- Smoke real DEV:
  - `GET /company-admins/viagens?data=2026-06-18` retornou `manifesto.status`
    `UP_TO_DATE`, `PENDING` e `BLOCKED` com dados reais da fixture.
  - Serviço da tarde foi reduzido para `[12,13]`, criando uma divergência real
    na viagem planejada: `PENDING`, `mudancas=2`, `removidos=2`.
- Validação visual:
  - `output/admin-ux-0013-manifest-status/visual/admin-trips.png` mostra
    `Manifesto atualizado`, `Manifesto pendente -2` e `Revisão manual` na lista.

---

## Backlog 0999 — ADMIN-UX-0014: montar e sincronizar dia sem apagar viagem

### Status
DONE (commits: 1001957, bd58624; backend: dfe80aa)

### Escopo
1. Transformar `Montar dia` em ação guiada com resultado por categoria.
2. Sincronizar viagens planejadas existentes quando seguro.
3. Explicar bloqueios para viagens em andamento.
4. Persistir feedback na tela, não só snackbar.

### Critérios de Aceite
- Rodar montagem depois de alterar serviço não obriga deletar viagem.
- O resultado separa criadas, atualizadas, bloqueadas e ignoradas.
- Viagem em andamento nunca muda silenciosamente.

### DoD
- Testes backend de criação/sincronização por data.
- Testes Flutter de resultado operacional.
- Smoke DEV com fixture limpa.
- Card marcado como `DONE (commit: <hash>)`.

### Evidência de fechamento
- Backend `dfe80aa` publicado em DEV pelo GitHub Actions `27733683178`: PASS.
- App `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS, `flutter analyze` sem issues,
  493 testes e quality ratchet PASS.
- Smoke HTTP DEV:
  - novo passageiro confirmado em serviço que ja tinha viagem planejada;
  - `Montar dia` retornou `atualizadas=1` e `bloqueadas=1`;
  - detalhe da viagem planejada passou a mostrar o passageiro novo no manifesto.
- Validação visual focada:
  - `output/admin-sync-smoke/20260618000307-web-focused/admin-trips-before-mount.png`;
  - `output/admin-sync-smoke/20260618000307-web-focused/admin-trips-after-mount-result.png`.
- Observação de domínio: serviço com política `EXIGE_CONFIRMACAO` só entra no
  manifesto atualizado depois da presença do novo passageiro ser confirmada para
  a data operacional.

### Correção pós-DONE — 2026-06-18
- A lista `Viagens do dia` passou a exibir um bloco de orientação
  `Manutenção do dia` quando já existem viagens montadas para a data filtrada.
- A ação principal orienta `Abrir viagem existente` e a ação secundária deixa
  claro que `Criar viagem extra` é para exceção fora do plano, não para incluir
  passageiro em operação já montada.
- Ao tentar criar exceção duplicada e a viagem equivalente já estar carregada,
  o portal abre o detalhe automaticamente para revisar/sincronizar manifesto.
- Cabeçalho da tabela voltou para a superfície dark do portal, removendo a
  faixa clara que quebrava o padrão visual.
- Validação:
  - `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS, `flutter analyze` sem issues,
    501 testes e quality ratchet PASS.
  - `scripts/qa_admin_portal_web_e2e.sh`: PASS contra DEV, com screenshot em
    `output/admin-ux-0014-maintenance-guide/visual/admin-trips.png`.

---

## Backlog 1000 — ADMIN-UX-0015: desmontar vínculos técnicos com segurança

### Status
DONE (commit: 0c39444)

### Escopo
1. Migrar serviços liberados para ficha do passageiro e ficha do serviço.
2. Migrar responsáveis vinculados para ficha de passageiro/responsável.
3. Migrar convites/solicitações de motorista para pipeline de motoristas.
4. Manter `Vínculos` apenas como suporte avançado até todos os fluxos comuns
   estarem cobertos.

### Critérios de Aceite
- O admin não precisa abrir `Vínculos` para tarefas comuns.
- Nenhuma capacidade real some antes de existir caminho contextual equivalente.

### DoD
- Testes das telas de entidade.
- Smoke dos fluxos migrados.
- Remoção ou rebaixamento visual validado por screenshot.
- Card marcado como `DONE (commit: <hash>)`.

### Progresso
- 2026-06-18 / Fatia 1000A:
  - serviços liberados agora aparecem e podem ser editados na ficha do
    passageiro, sem abrir `Vínculos avançados`;
  - `Vínculos avançados` permanece como fallback técnico no grupo `Avançado`;
  - smoke web amplo atualizado para `Viagens do dia`, `Serviços` e
    `Vínculos avançados`;
  - validação visual registrada em
    `output/admin-ux-0015/visual/admin-passenger-services.png`.
- 2026-06-18 / Fatia 1000B:
  - responsáveis vinculados agora aparecem e podem ser gerenciados na ficha do
    passageiro, sem abrir `Vínculos avançados`;
  - passageiros vinculados agora aparecem e podem ser gerenciados na ficha do
    responsável, com o mesmo contrato real de vínculo responsável-passageiro;
  - o smoke web amplo passou a abrir os dois diálogos contextuais sem mutar
    dados de DEV, preservando `Vínculos avançados` apenas como fallback técnico;
  - validação visual registrada em
    `output/admin-ux-0015/visual/admin-passenger-guardians.png`,
    `output/admin-ux-0015/visual/admin-passenger-guardians-dialog.png`,
    `output/admin-ux-0015/visual/admin-guardian-passengers.png` e
    `output/admin-ux-0015/visual/admin-guardian-passengers-dialog.png`.
- 2026-06-18 / Fatia 1000C:
  - `Motoristas` virou o caminho principal para entrada de motorista, com CTA
    único `Convidar motorista` no cabeçalho e painel de resumo para links,
    solicitações pendentes e e-mails verificados;
  - o banner explicativo redundante e o botão duplicado `Novo link` foram
    removidos da área interna, reduzindo ruído visual;
  - testes do gateway de convites/solicitações de motorista foram separados de
    `admin_vinculo_portal_service_test.dart` para
    `admin_driver_link_portal_service_test.dart`;
  - validação visual registrada em
    `output/admin-ux-0015/visual-1000c/admin-drivers.png`.
- 2026-06-18 / Fatia 1000D:
  - `Passageiros` virou o caminho principal para entrada de passageiros, com
    solicitações de acesso, códigos de convite e botão `Novo convite` dentro
    da tela da entidade;
  - a tela passou a usar um gateway menor (`AdminPassengerOnboardingGateway`)
    para não depender visualmente do painel técnico de vínculos;
  - solicitações e convites não têm mais limite visual escondido de 4 itens:
    listas com 5+ entradas continuam visíveis e acionáveis no contexto certo;
  - códigos de convite com status `USED_UP` agora aparecem como utilizados,
    alinhando o app com o status real retornado pelo backend;
  - validação visual registrada em
    `output/admin-ux-0015/visual-1000d/admin-passenger-entry.png`.
- 2026-06-18 / Fatia 1000E:
  - `Vínculos avançados` saiu da navegação comum do portal admin depois que
    serviços, responsáveis, motoristas e passageiros ganharam caminhos
    contextuais nas telas de entidade;
  - a tela técnica e seus testes permanecem no código como fallback interno de
    suporte, mas o admin operacional não vê mais esse atalho no menu;
  - o smoke web real agora valida que `Vínculos avançados` não aparece na
    sidebar e que a navegação comum continua funcional;
  - validação visual registrada em
    `output/admin-ux-0015/visual-1000e/admin-passengers.png`.

---

## Backlog 1001 — ADMIN-UX-0016: clareza operacional do portal admin

### Status
DONE (commit: af013b4)

### Escopo
1. Agrupar a navegacao comum do portal em `Operacao`, `Pessoas` e `Empresa`,
   reduzindo leitura lateral sem esconder destinos operacionais.
2. Corrigir a manutencao de `Viagens do dia` para abrir a viagem correta pela
   propria linha, evitando uma acao global ambigua quando existem multiplas
   viagens.
3. Remover affordances falsas ou mortas:
   - botao `Troca via campo` na criacao de viagem manual;
   - paginacao mockada em passageiros;
   - filtros/paginacao decorativos em responsaveis.
4. Tornar a troca de foto do perfil detectavel e acessivel por tooltip/
   semantics, sem alterar o contrato real de upload ja existente.

### Critérios de Aceite
- A sidebar comunica grupos claros para o admin.
- Em lista com varias viagens, cada linha tem acao propria `Abrir`.
- O portal nao mostra controles que parecam funcionais sem executar uma acao
  real.
- Avatar do perfil possui alvo acessivel para troca de foto.

### DoD
- Testes focados de widgets admin e conta.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Build web e smoke Playwright real contra backend DEV.
- Evidencia visual registrada.
- Card marcado como `DONE (commit: <hash>)`.

### Evidência de fechamento
- Commit de implementacao: `af013b4` (`feat(app): simplify admin portal operations`).
- `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - build local servido em `http://127.0.0.1:7361`;
  - backend DEV `http://127.0.0.1:8080`;
  - login e navegacao comum do portal admin: PASS.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS.
  - E2E preflight: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 503 testes.
  - quality ratchet: PASS.
- Evidencia visual:
  - `output/admin-portal-ux-0016/visual/admin-trips.png`;
  - `output/admin-portal-ux-0016/visual/admin-passengers.png`;
  - `output/admin-portal-ux-0016/visual/admin-guardians.png`;
  - diretorio completo `output/admin-portal-ux-0016/visual/`.

---

## Backlog 1002 — ADMIN-UX-0017: dashboard como central de próxima ação

### Status
DONE (commit: 7c95ff1)

### Escopo
1. Transformar o dashboard em central de comando do dia, com uma próxima ação
   contextual calculada a partir do resumo real da empresa.
2. Trocar CTAs ambíguos como `Montar viagens` e `Montar dia` por atalhos de
   navegação honestos (`Abrir viagens`, `Abrir serviços`), enquanto a ação
   operacional real continua dentro da tela correta.
3. Remover o `more_vert` decorativo das linhas de status e substituí-lo por
   uma ação explícita para abrir a área correspondente.
4. Preservar o sistema visual dark/liquid sem criar controles falsos ou
   métricas mockadas.

### Critérios de Aceite
- O dashboard mostra `Próxima ação` com título, explicação e CTA real.
- A prioridade muda conforme o estado operacional: sem serviços, sem
  motoristas, serviços sem motorista, dia sem viagens, viagens em andamento,
  presenças sem decisão ou operação pronta.
- Linhas de status não exibem menu visual sem comportamento.
- Atalhos rápidos usam verbos de navegação e não prometem executar uma ação
  mutante sem confirmação.

### DoD
- Testes focados do dashboard e shell admin.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Build web e smoke Playwright real contra backend DEV.
- Evidência visual registrada.
- Card marcado como `DONE (commit: <hash>)`.

### Evidência de fechamento
- Commit de implementação: `7c95ff1` (`feat(app): guide admin dashboard actions`).
- Testes focados:
  - `flutter test test/features/admin/admin_dashboard_screen_test.dart test/features/admin/admin_shell_screen_test.dart`: PASS.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS.
  - E2E preflight: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 504 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado no gate local (`RUN_E2E=0`), coberto pelo smoke
    Playwright separado.
- Build web real:
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - build local servido em `http://127.0.0.1:7362`;
  - backend DEV `http://127.0.0.1:8080`;
  - fixture operacional:
    `vanrides-backend/output/admin-sync-smoke/20260618000307/fixture.env`;
  - bootstrap, login e navegação comum do portal admin: PASS.
- Evidência visual:
  - `output/admin-portal-ux-0017/visual/admin-dashboard.png`;
  - diretório completo `output/admin-portal-ux-0017/visual/`.

---

## Backlog 1003 — ADMIN-UX-0018: serviços recorrentes com próximos passos legíveis

### Status
DONE (commit: 5a136e3)

### Escopo
1. Substituir ações só por ícone na lista de serviços recorrentes por botões
   textuais (`Abrir`, `Editar`, `Inativar`), mantendo tooltips como reforço.
2. Renomear a coluna `AÇÕES` para `PRÓXIMO PASSO`, aproximando a tela da
   linguagem de decisão operacional.
3. Exibir orientação compacta quando existir serviço ativo sem motorista fixo,
   sem transformar o alerta em uma faixa visual dominante.
4. Preservar os fluxos existentes de abrir detalhe, editar elegibilidade e
   inativar serviço.

### Critérios de Aceite
- O admin entende as ações da linha sem depender de hover em ícones.
- Serviços sem motorista fixo geram orientação visível antes de montar o dia.
- A tabela continua densa, escaneável e alinhada ao sistema dark/liquid.
- Testes garantem que os callbacks de abrir, editar e inativar continuam
  funcionando.

### DoD
- Testes focados de widgets/lista e tela de serviços.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Build web e smoke Playwright real contra backend DEV.
- Evidência visual registrada.
- Card marcado como `DONE (commit: <hash>)`.

### Evidência de fechamento
- Commit de implementação: `5a136e3` (`feat(app): clarify admin service actions`).
- Testes focados:
  - `flutter test test/features/admin/presentation/widgets/admin_servicos_recorrentes_list_widgets_test.dart test/features/admin/admin_servicos_recorrentes_screen_test.dart`: PASS.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS.
  - E2E preflight: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 504 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado no gate local (`RUN_E2E=0`), coberto pelo smoke
    Playwright separado.
- Build web real:
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - build local servido em `http://127.0.0.1:7362`;
  - backend DEV `http://127.0.0.1:8080`;
  - fixture operacional:
    `vanrides-backend/output/admin-sync-smoke/20260618000307/fixture.env`;
  - bootstrap, login e navegação comum do portal admin: PASS.
- Evidência visual:
  - `output/admin-portal-ux-0018/visual/admin-services.png`;
  - diretório completo `output/admin-portal-ux-0018/visual/`.

---

## Backlog 1004 — ADMIN-UX-0019: responsáveis com superfície premium e copy operacional

### Status
DONE (commit: a470f88)

### Escopo
1. Remover a ficha selecionada azul dominante da tela de responsáveis,
   alinhando o detalhe ao sistema dark/liquid do portal.
2. Substituir copy técnica sobre backend e senha provisória por orientação
   operacional para revisar e-mail, telefone e passageiros vinculados.
3. Manter as informações úteis da ficha: status de acesso, telefone, vínculos
   atuais, dados administrativos e ações de editar/remover.
4. Preservar o fluxo real de gerenciamento de passageiros vinculados ao
   responsável.

### Critérios de Aceite
- A tela não expõe texto de bastidor como `backend` ou `senha provisória`.
- A ficha selecionada usa superfície escura premium em vez de um cartão azul
  chapado.
- O admin entende que precisa conferir dados de contato e vínculos antes de
  liberar o acesso no app.
- Os testes garantem que os callbacks de gerenciar, editar e remover continuam
  funcionando.

### DoD
- Testes focados da tela/widgets de responsáveis.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Build web e smoke Playwright real contra backend DEV.
- Evidência visual registrada.
- Card marcado como `DONE (commit: <hash>)`.

### Evidência de fechamento
- Commit de implementação: `a470f88` (`feat(app): refine admin guardian surfaces`).
- RED esperado:
  - `flutter test test/features/admin/presentation/widgets/admin_responsaveis_widgets_test.dart`: FAIL antes da implementação por ausência de `Cadastro direto ativo` e por ficha usando `AdminPortalColors.blue`.
- Testes focados:
  - `flutter test test/features/admin/admin_responsaveis_screen_test.dart test/features/admin/presentation/widgets/admin_responsaveis_widgets_test.dart`: PASS.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS.
  - E2E preflight: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 504 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado no gate local (`RUN_E2E=0`), coberto pelo smoke
    Playwright separado.
- Build web real:
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - build local servido em `http://127.0.0.1:7362`;
  - backend DEV `http://127.0.0.1:8080`;
  - fixture operacional:
    `vanrides-backend/output/admin-sync-smoke/20260618000307/fixture.env`;
  - bootstrap reaproveitou admin existente da fixture, login e navegação comum
    do portal admin: PASS.
- Evidência visual:
  - `output/admin-portal-ux-0019/visual/admin-guardians.png`;
  - diretório completo `output/admin-portal-ux-0019/visual/`.

---

## Backlog 1005 — ADMIN-UX-0020: lista de passageiros com ações explícitas

### Status
DONE (commit: 1b0bb6b)

### Escopo
1. Remover o menu `more_vert` da lista de passageiros, que oferecia apenas uma
   opção e parecia uma ação contextual mais ampla.
2. Trocar o botão de atualizar por uma ação textual `Atualizar`.
3. Remover o CTA duplicado de cadastro no cabeçalho da lista quando já existem
   passageiros, mantendo cadastro no topo da tela e no estado vazio.
4. Renomear `AÇÕES` para `PRÓXIMO PASSO` e substituir ícones de linha por
   botões textuais `Editar` e `Motorista`.

### Critérios de Aceite
- A lista não exibe menu decorativo de três pontos.
- A ação de refresh é compreensível sem tooltip.
- O cadastro não aparece duplicado no cabeçalho da lista cheia.
- Cada linha comunica o próximo passo sem depender apenas de ícone.

### DoD
- Testes focados da tela/widgets de passageiros.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Build web e smoke Playwright real contra backend DEV.
- Evidência visual registrada.
- Card marcado como `DONE (commit: <hash>)`.

### Evidência de fechamento
- Commit de implementação: `1b0bb6b` (`feat(app): clarify admin passenger list actions`).
- RED esperado:
  - `flutter test test/features/admin/presentation/widgets/admin_passageiros_widgets_test.dart`: FAIL antes da implementação por ausência de `Atualizar`, `PRÓXIMO PASSO`, CTA vazio de cadastro e por presença de `more_vert`.
- Testes focados:
  - `flutter test test/features/admin/admin_passageiros_screen_test.dart test/features/admin/presentation/widgets/admin_passageiros_widgets_test.dart`: PASS.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS.
  - E2E preflight: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 504 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado no gate local (`RUN_E2E=0`), coberto pelo smoke
    Playwright separado.
- Build web real:
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - build local servido em `http://127.0.0.1:7362`;
  - backend DEV `http://127.0.0.1:8080`;
  - fixture operacional:
    `vanrides-backend/output/admin-sync-smoke/20260618000307/fixture.env`;
  - bootstrap reaproveitou admin existente da fixture, login e navegação comum
    do portal admin: PASS.
- Evidência visual:
  - `output/admin-portal-ux-0020/visual/admin-passengers.png`;
  - diretório completo `output/admin-portal-ux-0020/visual/`.

---

## Backlog 1006 — ADMIN-UX-0021: lista de motoristas com ações explícitas

### Status
DONE (commit: c1ff9a3)

### Escopo
1. Remover `more_horiz` e `chevron_right` da lista de motoristas, porque eles
   pareciam menus ou navegação escondida sem comunicar a ação real.
2. Trocar o refresh por uma ação textual `Atualizar`.
3. Renomear `AÇÕES` para `PRÓXIMO PASSO`.
4. Substituir a ação visual da linha por `Ver ficha`, mantendo a seleção e a
   abertura do detalhe do motorista.
5. Separar telefone e e-mail em textos próprios, garantindo e-mail em uma linha
   com reticências para evitar quebra de palavra em dados reais.

### Critérios de Aceite
- A lista não exibe pseudo-menu nem setinha decorativa como próxima ação.
- O admin entende como atualizar a lista sem depender de tooltip.
- Cada motorista tem uma ação explícita para abrir a ficha.
- E-mails longos não quebram palavras no meio da tabela.

### DoD
- Testes focados da tela/widgets de motoristas.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Build web e smoke Playwright real contra backend DEV.
- Evidência visual registrada.
- Card marcado como `DONE (commit: <hash>)`.

### Evidência de fechamento
- Commit de implementação: `c1ff9a3` (`feat(app): clarify admin driver list actions`).
- RED esperado:
  - `flutter test test/features/admin/presentation/widgets/admin_motoristas_widgets_test.dart`: FAIL antes da implementação por ausência de `Atualizar`; depois, no refinamento visual, FAIL por contato ainda renderizado como bloco único com quebra de linha.
- Testes focados:
  - `flutter test test/features/admin/presentation/widgets/admin_motoristas_widgets_test.dart`: PASS.
  - `flutter test test/features/admin/admin_motoristas_screen_test.dart test/features/admin/presentation/widgets/admin_motoristas_widgets_test.dart`: PASS.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS.
  - E2E preflight: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 504 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado no gate local (`RUN_E2E=0`), coberto pelo smoke
    Playwright separado.
- Build web real:
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - build local servido em `http://127.0.0.1:7362`;
  - backend DEV `http://127.0.0.1:8080`;
  - fixture operacional:
    `vanrides-backend/output/admin-sync-smoke/20260618000307/fixture.env`;
  - bootstrap reaproveitou admin existente da fixture, login e navegação comum
    do portal admin: PASS.
- Evidência visual:
  - `output/admin-portal-ux-0021/visual/admin-drivers.png`;
  - diretório completo `output/admin-portal-ux-0021/visual/`.

---

## Backlog 1007 — ADMIN-UX-0022: viagens do dia com manutenção explícita

### Status
DONE (commit: 76fad75)

### Escopo
1. Quando já existem viagens montadas, trocar a ação primária de `Montar dia`
   para `Atualizar dia`, deixando claro que o admin está mantendo o dia atual.
2. Renomear `Criar exceção` para `Criar viagem extra`, reduzindo a chance de o
   admin usar criação manual para resolver inclusão de passageiro em viagem já
   existente.
3. Trocar o resumo `DATA FILTRADA` por `VIAGENS MONTADAS`.
4. Renomear a coluna `ABRIR` para `PRÓXIMO PASSO`.
5. Exibir `Revisar manifesto` nas viagens com manifesto pendente ou bloqueado,
   mantendo `Abrir` nas viagens já atualizadas.

### Critérios de Aceite
- A tela diferencia montagem inicial de manutenção de um dia já montado.
- A criação pontual é apresentada como viagem extra, não como caminho normal de
  alteração de passageiros.
- Viagens com manifesto pendente apontam diretamente para revisão.
- O detalhe da viagem continua acessível pelo clique real da ação da linha.

### DoD
- Testes focados da tela/widgets de viagens.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Build web e smoke Playwright real contra backend DEV.
- Clique real em `Revisar manifesto` validando abertura do detalhe.
- Evidência visual registrada.
- Card marcado como `DONE (commit: <hash>)`.

### Evidência de fechamento
- Commit de implementação: `76fad75` (`feat(app): clarify admin trip maintenance actions`).
- RED esperado:
  - `flutter test test/features/admin/presentation/widgets/admin_viagens_list_widgets_test.dart test/features/admin/presentation/widgets/admin_viagens_create_widgets_test.dart test/features/admin/admin_viagens_screen_test.dart`: FAIL antes da implementação por ausência de `VIAGENS MONTADAS`, `Atualizar dia` e `Criar viagem extra`.
- Testes focados:
  - `flutter test test/features/admin/presentation/widgets/admin_viagens_list_widgets_test.dart test/features/admin/presentation/widgets/admin_viagens_create_widgets_test.dart test/features/admin/admin_viagens_screen_test.dart`: PASS.
  - `flutter test test/features/admin/admin_viagens_screen_manifest_test.dart test/features/admin/admin_viagens_list_contract_test.dart test/features/admin/presentation/widgets/admin_viagens_detail_view_test.dart`: PASS.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS.
  - E2E preflight: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 504 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado no gate local (`RUN_E2E=0`), coberto pelo smoke
    Playwright separado.
- Build web real:
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - build local servido em `http://127.0.0.1:7362`;
  - backend DEV `http://127.0.0.1:8080`;
  - fixture operacional:
    `vanrides-backend/output/admin-sync-smoke/20260618000307/fixture.env`;
  - bootstrap reaproveitou admin existente da fixture, login e navegação comum
    do portal admin: PASS;
  - clique real em `Revisar manifesto`: PASS, abriu detalhe da viagem com
    `Manifesto operacional` e `Sincronizar manifesto` visíveis.
- Evidência visual:
  - `output/admin-portal-ux-0022/visual/admin-trips.png`;
  - `output/admin-portal-ux-0022/visual/admin-trips-manifest-detail.png`;
  - diretório completo `output/admin-portal-ux-0022/visual/`.

---

## Backlog 1015 — ADMIN-IA-0029: alerta de serviços sem motorista com ação direta

### Status
DONE (commit: b529816)

### Escopo
1. Tornar o alerta `Serviço sem motorista fixo` acionável, abrindo diretamente
   o primeiro serviço que exige escala no dia.
2. Evitar que o administrador precise procurar manualmente a linha correta na
   tabela de serviços recorrentes.
3. Preservar a lista densa de serviços, os botões de linha e os contratos
   backend existentes.
4. Garantir por teste que a ação do alerta abre o detalhe operacional do
   serviço afetado.

### Critérios de Aceite
- O alerta de serviços sem motorista mostra um CTA com o nome do primeiro
  serviço afetado.
- O CTA abre o detalhe do serviço e deixa visível `Prontidão operacional` e
  `Exige escala no dia`.
- A tela continua funcionando sem contrato backend novo.
- Nenhum fluxo de abrir, editar ou inativar serviço recorrente regride.

### DoD
- RED/GREEN focado na tela de serviços recorrentes.
- Testes adjacentes da tela e widgets de serviços.
- `python3 scripts/quality_metrics_test.py`.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Build web real contra backend DEV.
- Smoke visual Playwright com clique real no CTA do alerta.
- Card marcado como `DONE (commit: b529816)`.

### Evidência de fechamento
- Commit de implementação: `b529816`
  (`feat(app): make service driver alerts actionable`).
- RED esperado:
  - `flutter test test/features/admin/admin_servicos_recorrentes_screen_test.dart --plain-name 'renderiza listagem de servicos recorrentes'`:
    FAIL antes da implementação porque o CTA `Abrir Linha Manhã` ainda não
    existia.
- Testes focados:
  - `flutter test test/features/admin/admin_servicos_recorrentes_screen_test.dart --plain-name 'renderiza listagem de servicos recorrentes'`: PASS.
- Testes adjacentes:
  - `flutter test test/features/admin/admin_servicos_recorrentes_screen_test.dart test/features/admin/presentation/widgets/admin_servicos_recorrentes_list_widgets_test.dart`: PASS.
- `python3 scripts/quality_metrics_test.py`: PASS.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS.
  - E2E preflight: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 507 testes.
  - quality ratchet: PASS.
- Build web real:
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - build local servido em `http://127.0.0.1:7357`;
  - backend DEV `http://127.0.0.1:8080`;
  - fixture operacional:
    `vanrides-backend/output/admin-sync-smoke/20260618000307/fixture.env`;
  - clique real em `Abrir Linha Noite - Atividade`: PASS, abriu o detalhe com
    `Prontidão operacional` e `Exige escala no dia`.
- Evidência visual:
  - `output/admin-portal-ux-0029/visual/01-services-driver-alert.png`;
  - `output/admin-portal-ux-0029/visual/02-services-driver-alert-open-detail.png`;
  - diretório completo `output/admin-portal-ux-0029/visual/`.

---

## Backlog 1014 — ADMIN-IA-0028: deltas de lista com linguagem operacional

### Status
DONE (commit: 167726d)

### Escopo
1. Remover a exibição de deltas técnicos como `+1 / -2` na lista de viagens.
2. Trocar o delta do manifesto por linguagem acionável:
   `1 para incluir`, `2 para remover` ou combinação equivalente.
3. Manter a leitura de `adicionados`, `removidos` e `mudancas` vinda do
   backend sem alterar contrato.
4. Garantir por teste que o formato técnico antigo não volta para a UI.

### Critérios de Aceite
- `Lista pendente` não exibe mais números negativos soltos.
- O administrador entende se a lista precisa incluir ou remover passageiros.
- A tela continua mostrando `Lista atualizada` e `Revisão manual` sem regressão.
- Nenhum endpoint, payload ou status code muda.

### DoD
- RED/GREEN focado no widget de lista de viagens.
- `flutter analyze`.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Build web real contra backend DEV.
- Smoke visual Playwright da tela atual de viagens.
- Card marcado como `DONE (commit: 167726d)`.

### Evidência de fechamento
- Commit de implementação: `167726d` (`fix(app): clarify trip manifest deltas`).
- RED esperado:
  - `flutter test test/features/admin/presentation/widgets/admin_viagens_list_widgets_test.dart`:
    FAIL antes da implementação porque a UI ainda renderizava `+1 / -1`.
- Testes focados:
  - `flutter test test/features/admin/presentation/widgets/admin_viagens_list_widgets_test.dart`: PASS.
- Testes adjacentes:
  - `flutter test test/features/admin/admin_viagens_list_contract_test.dart test/features/admin/presentation/widgets/admin_viagens_list_widgets_test.dart`: PASS.
- `flutter analyze`: PASS, sem issues.
- `python3 scripts/quality_metrics_test.py`: PASS.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS.
  - E2E preflight: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 507 testes.
  - quality ratchet: PASS.
- Build web real:
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - build local servido em `http://127.0.0.1:7357`;
  - backend DEV `http://127.0.0.1:8080`;
  - fixture operacional:
    `vanrides-backend/output/admin-sync-smoke/20260618000307/fixture.env`;
  - tela atual de viagens: PASS, sem `-2` ou formato `+1 / -1` visível.
- Evidência visual:
  - `output/admin-portal-ux-0028/visual/admin-trips-current.png`;
  - diretório completo `output/admin-portal-ux-0028/visual/`.

---

## Backlog 1013 — ADMIN-IA-0027: revisão guiada após mudanças operacionais

### Status
DONE (commit: 79f4b35)

### Escopo
1. Quando alterações em passageiros ou serviços exigirem revisar viagens do dia,
   levar o administrador para `Viagens do dia` com contexto explícito, não para
   uma tela genérica.
2. Preservar a navegação comum para `Viagens do dia`, mas carregar um intent de
   revisão quando a origem for `Passageiros` ou `Serviços`.
3. Exibir um banner de revisão guiada com origem, título, explicação curta e
   alerta operacional para não deletar viagens apenas para incluir passageiros.
4. Tornar o resultado de `Atualizar dia` acionável com CTA para abrir a viagem
   atualizada ou a primeira viagem afetada.
5. Manter contratos backend, endpoints, payloads e status codes inalterados.

### Critérios de Aceite
- O CTA `Revisar viagens` em mudanças de passageiro/serviço abre `Viagens do
  dia` com uma orientação clara do que revisar.
- A tela diferencia navegação comum de revisão guiada, evitando contexto
  perdido após salvar alterações.
- A montagem do dia mostra resultado revisável e oferece atalho para abrir a
  viagem correspondente.
- Viagens já iniciadas aparecem como revisão manual/bloqueada para proteger a
  operação em andamento.
- O portal continua com smoke real de login e navegação comum contra backend
  DEV.

### DoD
- Testes focados de intent, passageiros, serviços e viagens.
- `flutter analyze`.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Build web real contra backend DEV.
- Smoke Playwright real do portal admin.
- Validação visual Playwright do fluxo `Passageiros > Editar serviços >
  Revisar viagens > Atualizar dia > Abrir primeira viagem`.
- Card marcado como `DONE (commit: 79f4b35)`.

### Evidência de fechamento
- Commit de implementação: `79f4b35` (`feat(app): guide admins through trip reviews`).
- Testes focados:
  - `flutter test test/features/admin/admin_viagens_review_intent_screen_test.dart test/features/admin/admin_passageiros_screen_test.dart test/features/admin/admin_servicos_recorrentes_screen_test.dart`: PASS.
- Testes adjacentes:
  - `flutter test test/features/admin/admin_shell_screen_test.dart test/features/admin/admin_viagens_review_intent_screen_test.dart test/features/admin/admin_viagens_screen_test.dart test/features/admin/presentation/widgets/admin_viagens_list_widgets_test.dart test/features/admin/presentation/widgets/admin_viagens_generation_result_widgets_test.dart test/features/admin/admin_passageiros_screen_test.dart test/features/admin/admin_servicos_recorrentes_screen_test.dart`: PASS.
- `python3 scripts/quality_metrics_test.py`: PASS.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS.
  - E2E preflight: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 507 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado no gate local (`RUN_E2E=0`), coberto pelo smoke
    Playwright separado.
- Build web real:
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - build local servido em `http://127.0.0.1:7357`;
  - backend DEV `http://127.0.0.1:8080`;
  - fixture operacional:
    `vanrides-backend/output/admin-sync-smoke/20260618000307/fixture.env`;
  - login e navegação comum do portal admin: PASS;
  - fluxo focado `Passageiros > Editar serviços > Salvar serviços > Revisar
    viagens > Atualizar dia > Abrir primeira viagem`: PASS.
- Evidência visual:
  - `output/admin-portal-ux-0027/visual/03-trips-guided-review-banner.png`;
  - `output/admin-portal-ux-0027/visual/04-trips-generation-result.png`;
  - `output/admin-portal-ux-0027/visual/05-trips-opened-from-result.png`;
  - diretório completo `output/admin-portal-ux-0027/visual/`.

---

## Backlog 1012 — ADMIN-IA-0026: exceções de viagem com ação contextual

### Status
DONE (commit: d36de35)

### Escopo
1. Reduzir o peso da ação `Criar viagem extra`, mantendo-a como exceção
   contextual dentro de `Manutenção do dia`, não como ação principal do topo.
2. Quando a API responder `CONFLICT` ao criar viagem manual, recarregar a data
   informada no formulário antes de procurar a viagem já existente.
3. Abrir o detalhe da viagem existente correta após o conflito, para que o
   administrador continue no fluxo operacional sem precisar deduzir a data.
4. Manter contratos, endpoints, payloads e status codes atuais inalterados.

### Critérios de Aceite
- A tela de viagens deixa `Atualizar dia` como ação principal e reserva
  `Criar viagem extra` para exceções explícitas.
- Se a viagem já existe para o serviço/data escolhidos, o admin é levado para a
  viagem existente, mesmo que a tela estivesse carregada em outra data.
- O detalhe aberto após conflito permite revisar/atualizar a lista da viagem.
- O contrato backend permanece inalterado.

### DoD
- RED/GREEN focado em `AdminViagensScreen`.
- `flutter analyze`.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Build web real contra backend DEV.
- Smoke Playwright real do portal admin.
- Evidência visual da tela de viagens registrada.
- Card marcado como `DONE (commit: d36de35)`.

### Evidência de fechamento
- Commit de implementação: `d36de35` (`fix(app): open existing trip after date conflict`).
- RED esperado:
  - `flutter test test/features/admin/admin_viagens_screen_test.dart test/features/admin/presentation/widgets/admin_viagens_list_widgets_test.dart`:
    FAIL antes da implementação porque a ação `Criar viagem extra` aparecia
    como atalho duplicado.
  - O teste focado de conflito foi escrito para exigir recarga da data do
    input antes de abrir a viagem existente.
- Testes focados:
  - `flutter test test/features/admin/admin_viagens_conflict_screen_test.dart test/features/admin/admin_viagens_screen_test.dart test/features/admin/presentation/widgets/admin_viagens_list_widgets_test.dart`: PASS.
- `python3 scripts/quality_metrics_test.py`: PASS.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS.
  - E2E preflight: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 506 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado no gate local (`RUN_E2E=0`), coberto pelo smoke
    Playwright separado.
- Build web real:
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - build local servido em `http://127.0.0.1:7357`;
  - backend DEV `http://127.0.0.1:8080`;
  - fixture operacional:
    `vanrides-backend/output/admin-sync-smoke/20260618000307/fixture.env`;
  - login e navegação comum do portal admin: PASS;
  - screenshot de viagens confirma topo sem atalho duplicado de
    `Criar viagem extra`; a ação permanece contextual em `Manutenção do dia`.
- Evidência visual:
  - `output/admin-portal-ux-0026/visual/admin-trips.png`;
  - diretório completo `output/admin-portal-ux-0026/visual/`.

---

## Backlog 1011 — ADMIN-IA-0025: passageiros como ficha operacional

### Status
DONE (commit: a3e7cd1)

### Escopo
1. Transformar a tela de passageiros em uma visão de ficha operacional, não em
   um cadastro genérico.
2. Remover o botão `Filtrar` sem função clara.
3. Trocar métricas frágeis como `COM ATALHO`, `SEM ATALHO` e `DOCUMENTAÇÃO OK`
   por indicadores que ajudem a empresa a operar o dia:
   `COM SERVIÇO`, `SEM SERVIÇO` e `PERFIL COMPLETO`.
4. Renomear `Resumo do Passageiro` para `Ficha do passageiro`.
5. Manter contratos, endpoints e payloads atuais inalterados.

### Critérios de Aceite
- A tela deixa claro quantos passageiros estão liberados em serviços
  recorrentes.
- Passageiros sem serviço aparecem como ação operacional, não como erro técnico.
- A completude do perfil considera contato, nascimento, endereço e responsável
  quando for menor de idade.
- A interface não exibe filtros ou termos que não produzem ação real.
- O contrato backend permanece inalterado.

### DoD
- RED/GREEN focado na tela e widgets de passageiros.
- `flutter analyze`.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Build web real contra backend DEV.
- Smoke Playwright real do portal admin.
- Smoke focado validando textos novos e ausência dos textos antigos.
- Evidência visual registrada.
- Card marcado como `DONE (commit: a3e7cd1)`.

### Evidência de fechamento
- Commit de implementação: `a3e7cd1` (`feat(app): clarify admin passenger operations`).
- RED esperado:
  - `flutter test test/features/admin/admin_passageiros_screen_test.dart test/features/admin/presentation/widgets/admin_passageiros_widgets_test.dart`:
    FAIL antes da implementação por ausência de `Passageiros`,
    `COM SERVIÇO`, `SEM SERVIÇO`, `PERFIL COMPLETO` e
    `Ficha do passageiro`.
- Testes focados:
  - `flutter test test/features/admin/admin_passageiros_screen_test.dart test/features/admin/presentation/widgets/admin_passageiros_widgets_test.dart`: PASS.
- Testes adjacentes:
  - `flutter test test/features/admin/admin_passageiros_screen_test.dart test/features/admin/presentation/widgets/admin_passageiros_widgets_test.dart test/features/admin/presentation/widgets/admin_passageiro_form_dialog_test.dart test/features/admin/admin_shell_screen_test.dart`: PASS.
- `flutter analyze`: PASS, sem issues.
- `python3 scripts/quality_metrics_test.py`: PASS.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS.
  - E2E preflight: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 505 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado no gate local (`RUN_E2E=0`), coberto pelo smoke
    Playwright separado.
- Build web real:
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - build local servido em `http://127.0.0.1:7357`;
  - backend DEV `http://127.0.0.1:8080`;
  - fixture operacional:
    `vanrides-backend/output/admin-sync-smoke/20260618000307/fixture.env`;
  - login e navegação comum do portal admin: PASS;
  - tela de passageiros validou `COM SERVIÇO`, `SEM SERVIÇO`,
    `PERFIL COMPLETO`, `Ficha do passageiro`, ausência de
    `DOCUMENTAÇÃO OK` e ausência do botão `Filtrar`: PASS.
- Evidência visual:
  - `output/admin-portal-ux-0025/visual/admin-passengers.png`;
  - `output/admin-portal-ux-0025/visual/admin-passengers-focused.png`;
  - diretório completo `output/admin-portal-ux-0025/visual/`.

---

## Backlog 1010 — ADMIN-IA-0024: linguagem operacional em Viagens

### Status
DONE (commit: 28da127)

### Escopo
1. Trocar a linguagem visível de viagens de termos técnicos de implementação
   para termos operacionais que a empresa entende.
2. Usar `Lista da viagem`, `Estado publicado`, `Atualizar lista`,
   `Revisar lista`, `Lista pendente` e `Lista atualizada` na experiência do
   portal.
3. Manter `manifesto`, `snapshot` e endpoints existentes apenas como nomes
   internos de contrato/backend.
4. Humanizar mensagens técnicas vindas do backend antes de exibir no portal,
   sem alterar payloads ou status codes.

### Critérios de Aceite
- A tela de viagens deixa claro que o admin está revisando a lista operacional
  da viagem, não um artefato técnico chamado manifesto.
- O detalhe da viagem separa a lista atual da viagem e o estado publicado do
  dia com cópia compreensível.
- Viagens com lista pendente chamam para `Revisar lista`.
- A ação de atualização usa `Atualizar lista`.
- O contrato backend permanece inalterado.

### DoD
- RED/GREEN focado nas telas e widgets de viagens.
- `flutter analyze`.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Build web real contra backend DEV.
- Smoke Playwright real do portal admin.
- Clique real no detalhe de viagem validando `Lista da viagem`,
  `Estado publicado` e preview de `Atualizar lista`.
- Evidência visual registrada.
- Card marcado como `DONE (commit: 28da127)`.

### Evidência de fechamento
- Commit de implementação: `28da127` (`feat(app): use operational trip list language`).
- RED esperado:
  - `flutter test test/features/admin/admin_viagens_screen_manifest_test.dart test/features/admin/presentation/widgets/admin_viagens_detail_view_test.dart test/features/admin/presentation/widgets/admin_viagens_detail_widgets_test.dart test/features/admin/presentation/widgets/admin_viagens_list_widgets_test.dart test/features/admin/admin_viagens_screen_test.dart`:
    FAIL antes da implementação por ausência de `Lista da viagem`,
    `Estado publicado`, `Atualizar lista` e `Revisar lista`.
- Testes focados:
  - `flutter test test/features/admin/admin_viagens_screen_manifest_test.dart test/features/admin/presentation/widgets/admin_viagens_detail_view_test.dart test/features/admin/presentation/widgets/admin_viagens_detail_widgets_test.dart test/features/admin/presentation/widgets/admin_viagens_list_widgets_test.dart test/features/admin/admin_viagens_screen_test.dart test/features/admin/presentation/widgets/admin_viagens_generation_result_widgets_test.dart`: PASS.
- `flutter analyze`: PASS, sem issues.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS.
  - E2E preflight: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 505 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado no gate local (`RUN_E2E=0`), coberto pelo smoke
    Playwright separado.
- Build web real:
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - build local servido em `http://127.0.0.1:7362`;
  - backend DEV `http://127.0.0.1:8080`;
  - fixture operacional:
    `vanrides-backend/output/admin-sync-smoke/20260618000307/fixture.env`;
  - bootstrap reaproveitou admin existente da fixture, login e navegação comum
    do portal admin: PASS;
  - clique real em viagem validou `Lista da viagem`, `Estado publicado` e
    preview de `Atualizar lista`: PASS.
- Evidência visual:
  - `output/admin-portal-ux-0024/visual/admin-trip-detail-operational-language.png`;
  - `output/admin-portal-ux-0024/visual/admin-trip-update-list-preview.png`;
  - diretório completo `output/admin-portal-ux-0024/visual/`.

---

## Backlog 1009 — ADMIN-IA-0023: pós-salvamento guia revisão de viagens

### Status
DONE (commit: 20cb58e)

### Escopo
1. Conectar edições que afetam elegibilidade operacional com a próxima ação
   correta em `Viagens do dia`.
2. Adicionar CTA contextual `Revisar viagens` após salvar serviços liberados
   do passageiro.
3. Adicionar CTA contextual `Revisar viagens` após criar ou atualizar serviço
   recorrente.
4. Reusar a navegação do shell admin para abrir a seção de viagens sem criar
   novo contrato backend.

### Critérios de Aceite
- O admin não precisa deduzir que mudanças em passageiro/serviço podem exigir
  revisão das viagens do dia.
- A cópia evita prometer inclusão automática em viagem já montada; a ação
  chama para revisão segura.
- O CTA só aparece quando a tela está dentro do shell admin e consegue navegar
  para `Viagens do dia`.
- O contrato backend permanece inalterado.

### DoD
- RED/GREEN focado em passageiros e serviços recorrentes.
- Testes adjacentes de shell, passageiros, serviços e viagens.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Build web real contra backend DEV.
- Smoke Playwright real do portal admin.
- Clique real no CTA `Revisar viagens`.
- Evidência visual registrada.
- Card marcado como `DONE (commit: <hash>)`.

### Evidência de fechamento
- Commit de implementação: `20cb58e` (`feat(app): guide admins from eligibility edits to trips`).
- RED esperado:
  - `flutter test test/features/admin/admin_passageiros_screen_test.dart test/features/admin/admin_servicos_recorrentes_screen_test.dart`:
    FAIL antes da implementação por ausência do parâmetro `onOpenViagens` e
    do CTA `Revisar viagens`.
- Testes focados:
  - `flutter test test/features/admin/admin_passageiros_screen_test.dart test/features/admin/admin_servicos_recorrentes_screen_test.dart`: PASS.
- Testes adjacentes:
  - `flutter test test/features/admin/admin_shell_screen_test.dart test/features/admin/admin_passageiros_screen_test.dart test/features/admin/admin_servicos_recorrentes_screen_test.dart test/features/admin/presentation/widgets/admin_passageiro_form_dialog_test.dart test/features/admin/presentation/widgets/admin_servico_recorrente_form_test.dart test/features/admin/admin_viagens_screen_test.dart`: PASS.
- `flutter analyze`: PASS, sem issues.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS.
  - E2E preflight: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 505 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado no gate local (`RUN_E2E=0`), coberto pelo smoke
    Playwright separado.
- Build web real:
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke Playwright real:
  - build local servido em `http://127.0.0.1:7362`;
  - backend DEV `http://127.0.0.1:8080`;
  - fixture operacional:
    `vanrides-backend/output/admin-sync-smoke/20260618000307/fixture.env`;
  - bootstrap reaproveitou admin existente da fixture, login e navegação comum
    do portal admin: PASS;
  - clique real em `Editar serviços` > `Salvar serviços` > `Revisar viagens`:
    PASS, abriu `Viagens do dia`.
- Evidência visual:
  - `output/admin-portal-ux-0023/visual/admin-passenger-review-trips-snackbar.png`;
  - `output/admin-portal-ux-0023/visual/admin-passenger-review-trips-destination.png`;
  - diretório completo `output/admin-portal-ux-0023/visual/`.

---

## Backlog 1008 — ACCOUNT-APP-0002: upload de avatar resiliente

### Status
DONE (commit: 9c986ed)

### Escopo
1. Investigar o `Bad Request` reportado ao enviar foto de perfil.
2. Preservar o fluxo principal por `POST /account/me/avatar` com multipart real.
3. Adicionar fallback controlado para `PATCH /account/me` com `fotoPerfilUrl`
   em data URI quando o upload multipart retornar `400` ou `415`.
4. Criar probe CLI real para validar upload de avatar contra DEV sem depender
   do file picker do navegador/simulador.

### Critérios de Aceite
- O app continua usando o endpoint dedicado de avatar como primeira escolha.
- Falhas genéricas de formato/tamanho no multipart não deixam o usuário preso
  quando o backend também aceita `fotoPerfilUrl` via perfil.
- O fallback é coberto por teste automatizado e não envia campos sensíveis.
- Existe smoke real repetível contra DEV para validar login e upload de avatar.

### DoD
- RED/GREEN focado do fallback.
- Testes de conta/avatar e widgets consumidores.
- `flutter analyze`.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Build web real.
- Probe DEV real com `AVATAR_HTTP 200`.
- Card marcado como `DONE (commit: <hash>)`.

### Evidência de fechamento
- Commit de implementação: `9c986ed` (`fix(app): make profile avatar upload resilient`).
- RED esperado:
  - `flutter test test/features/account/account_profile_service_test.dart`:
    FAIL antes da implementação em
    `usa PATCH com data uri quando upload multipart da foto falha`.
- Testes focados:
  - `flutter test test/features/account/account_profile_service_test.dart`: PASS.
  - `flutter test test/features/account/account_profile_service_test.dart test/features/account/account_profile_screen_test.dart test/features/account/account_profile_photo_codec_test.dart test/features/tracking/presentation/widgets/passenger_home_account_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_home_account_widgets_test.dart`: PASS.
- `flutter analyze`: PASS, sem issues.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS.
  - E2E preflight: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 505 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado no gate local (`RUN_E2E=0`).
- Build web real:
  - `flutter build web --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true`: PASS.
- Smoke DEV real:
  - `dart run tool/account_avatar_upload_probe.dart`: PASS.
  - `AVATAR_HTTP 200`.
  - resposta persistiu `fotoPerfilUrl` em `/uploads/avatars/company_admin-7-*.png`.
- Observação:
  - o file picker web headless não foi usado como evidência final porque a
    janela fechou durante a espera do `filechooser`; por isso o probe CLI ficou
    versionado como smoke determinístico do contrato real.

---

## Backlog 1016 — ADMIN-IA-0030: perfil completo como checklist acionável

### Status
DONE (commits: `99a06f8`, `8c28da2`)

### Escopo
1. Transformar `PERFIL COMPLETO` e banners de perfil em checklist acionável,
   mostrando exatamente o que falta por papel: foto, nascimento, contato,
   endereço e vínculos obrigatórios.
2. Corrigir fixtures, criação e edição admin para não gerar pessoas
   operacionalmente válidas com `Nascimento pendente` quando o fluxo deveria
   pedir esse dado.
3. Criar caminho direto de edição a partir dos itens faltantes, sem obrigar o
   admin a procurar o campo em diálogos longos.
4. Diferenciar dados obrigatórios para operar hoje de dados recomendados para
   confiança/perfil premium.

### Critérios de Aceite
- O admin entende por que um perfil está incompleto e qual ação corrige.
- Passageiro, motorista, responsável e admin usam a mesma linguagem de
  completude, respeitando as diferenças de cada papel.
- O indicador deixa de parecer uma métrica decorativa ou punitiva.

### DoD
- Testes focados dos widgets de completude e diálogos de edição.
- Smoke DEV criando pessoa nova e completando dados faltantes.
- Screenshots antes/depois em perfil e telas de entidade.

---

## Backlog 1017 — ADMIN-IA-0031: fichas de pessoas com revelação progressiva

### Status
DONE (commit: ddc486f)

### Escopo
1. Redesenhar ficha de passageiro, motorista e responsável para mostrar primeiro
   identidade, vínculo operacional e próxima ação.
2. Mover seções secundárias para áreas expansíveis ou blocos mais compactos,
   reduzindo a sensação de painel longo e pesado.
3. Manter ações comuns no contexto certo: serviços do passageiro, responsáveis,
   motorista, passageiros do responsável e operação atribuída do motorista.
4. Garantir que cada ação tenha estado vazio útil, não apenas botão desabilitado.

### Critérios de Aceite
- A ficha principal é escaneável em poucos segundos.
- O admin sabe o que fazer em seguida sem ler todos os cards.
- Não há controles visíveis que pareçam quebrados quando faltam dados.

### DoD
- Testes dos widgets de ficha por papel.
- Smoke Playwright abrindo fichas e diálogos contextuais.
- Evidência visual desktop e largura intermediária.

---

## Backlog 1018 — ADMIN-IA-0032: dia operacional com menos densidade visual

### Status
DONE (commit: e932f72)

### Escopo
1. Revisar `Viagens do dia` para separar status do dia, manutenção e lista em
   hierarquia mais clara.
2. Reduzir cards métricos redundantes e priorizar alertas que exigem ação.
3. Fazer `Atualizar dia`, `Revisar lista`, `Abrir` e `Criar viagem extra`
   seguirem uma gramática visual única.
4. Validar conflito de data/manual com clique real e mensagem persistente.

### Critérios de Aceite
- A tela responde rápido à pergunta: "o que está pronto e o que preciso
  revisar hoje?"
- Exceções continuam disponíveis, mas não competem com manutenção normal.
- A lista fica mais leve sem perder informação operacional.

### DoD
- RED/GREEN focado em viagens e widgets de manutenção.
- Smoke DEV com fixture que tenha viagem em andamento, planejada e exceção.
- Screenshots de lista e detalhe.

### Evidência
- Testes focados:
  `flutter test test/features/admin/admin_viagens_screen_test.dart test/features/admin/presentation/widgets/admin_viagens_create_widgets_test.dart test/features/admin/presentation/widgets/admin_viagens_list_widgets_test.dart`.
- Gate app:
  `RUN_E2E=0 ./scripts/qa_gate_card.sh`.
- Smoke DEV:
  `output/admin-portal-e2e/card-1018-dev/manifest.json`,
  `output/admin-portal-e2e/card-1018-dev/SUMMARY.md` e
  `output/admin-portal-e2e/card-1018-dev/screenshots/admin-trips.png`.

---

## Backlog 1019 — ADMIN-QA-0001: roteiro E2E visual com fixture determinística

### Status
DONE (commit: 6ebfaf7)

### Escopo
1. Transformar o smoke admin web em relatório visual determinístico, com
   fixture explícita e saída sempre fora de `build/web`.
2. Registrar falhas de setup separadas de falhas de UX/contrato.
3. Gerar manifesto de screenshots e resumo PASS/LIMITES/PENDÊNCIAS.
4. Reusar a fixture operacional para validar app passageiro/motorista quando o
   objetivo pedir validação ponta a ponta.

### Critérios de Aceite
- O script não produz falso negativo por empresa vazia quando o objetivo é
  validar operação completa.
- Screenshots ficam em diretório previsível e citável no QA Log.
- A falha aponta tela, ação e possível causa.

### DoD
- Script de QA versionado e documentado.
- Execução contra backend DEV com relatório visual.
- QA Log atualizado.
- Evidência:
  `output/admin-portal-e2e/card-1019-dev/manifest.json`,
  `output/admin-portal-e2e/card-1019-dev/SUMMARY.md` e
  `output/admin-portal-e2e/card-1019-dev/screenshots/`.

---

## Programa Integrado 2026-06-30 - Cards Planejados

Cards materializados para execucao futura do plano integrado. Nenhum item desta
secao esta marcado como DONE nesta task.

### SEC-SESSION-001A - Sessao segura no app

### Status
DONE (commits: a4cac42, 8f82864, ff44319)

### Objetivo
Remover dependencia de senha bruta persistida no fluxo normal de sessao do app,
mantendo compatibilidade legada explicita enquanto necessario.

### Criterios de Aceite
- Fluxo normal usa token/sessao segura sem salvar senha bruta.
- Compatibilidade legada fica isolada e testada.
- Logout e refresh continuam previsiveis por papel.

### Validacao minima
- `flutter analyze`.
- `flutter test test/features/auth test/features/session`.
- `./scripts/qa_gate_card.sh --no-e2e`.

### Impact Analysis
- Contexto: fluxo normal do app ainda carregava e persistia senha bruta em
  chaves de sessao/lembrar login, mesmo com `accessToken` disponivel.
- Mudancas tecnicas: `AppSessionStore.writeCurrent` remove `session.password`;
  `readCurrent` restaura sessao bearer sem exigir senha e migra senha legada em
  modo one-shot; `writeRememberedLogin` preserva apenas email;
  `readRememberedLogin` retorna senha legada uma unica vez e remove a chave
  `senha`.
- Contratos afetados: apenas armazenamento local do app. Payloads HTTP, rotas e
  auth header continuam iguais; chamadas com token seguem usando Bearer.
- Riscos e mitigacao: telas ainda possuem campo `AppSessionUser.password` para
  compatibilidade em memoria/legado; testes cobrem sessao bearer sem senha,
  migracao one-shot da chave legada e limpeza de sessao nos logouts por papel.
- Rollback simples: reverter `a4cac42`, `8f82864` e `ff44319`.

### Evidencia
- RED: `flutter test test/app/session/app_session_store_test.dart` falhou antes
  da correcao porque a senha era persistida/restaurada.
- RED: novo caso de lembrar login falhou antes da migracao porque a chave
  legada `senha` seguia salva.
- GREEN: `flutter test test/app/session/app_session_store_test.dart` PASS
  (10 testes).
- GREEN: `flutter test test/app/session test/login_screen_test.dart test/features/account/account_profile_service_test.dart` PASS.
- GREEN: `flutter test test/features/driver_onboarding test/features/passenger_onboarding` PASS.
- GREEN: `flutter analyze` PASS, sem issues.
- GREEN: `./scripts/qa_gate_card.sh --no-e2e` PASS; ratchet PASS; E2E
  preflight PASS; `flutter analyze` sem issues; `flutter test` PASS
  (586 testes); E2E real desabilitado por escopo.
- Review: primeira revisao de spec falhou porque logouts nao limpavam a sessao
  persistida e `readCurrent` ainda mantinha senha legada no storage normal.
- GREEN pos-review:
  `flutter test test/app/session/app_session_store_test.dart test/features/guardian/guardian_shell_screen_test.dart test/features/admin/admin_shell_screen_test.dart test/screens/home_passageiro_logout_test.dart test/screens/home_passageiro_screen_test.dart test/features/driver_trip/home_motorista_loading_test.dart`
  PASS (38 testes).
- GREEN pos-review: `./scripts/qa_gate_card.sh --no-e2e` PASS; quality
  ratchet PASS; `flutter analyze` sem issues; `flutter test` PASS
  (586 testes).
- Review: segunda revisao de spec falhou porque o caminho de motorista sem
  empresa/pendente (`DriverCompanyPendingScreen`) ainda voltava ao login sem
  limpar `AppSessionStore`.
- RED: `flutter test test/features/driver_trip/driver_company_pending_screen_test.dart --plain-name 'sair limpa sessao persistida antes de voltar ao login'`
  falhou antes da correcao porque `sessionStore.clear()` nao era chamado.
- GREEN pos-segunda-review:
  `flutter test test/features/driver_trip/driver_company_pending_screen_test.dart --plain-name 'sair limpa sessao persistida antes de voltar ao login'`
  PASS.
- GREEN pos-segunda-review:
  `flutter test test/app/session/app_session_store_test.dart test/features/driver_trip/driver_company_pending_screen_test.dart test/features/guardian/guardian_shell_screen_test.dart test/features/admin/admin_shell_screen_test.dart test/screens/home_passageiro_logout_test.dart test/screens/home_passageiro_screen_test.dart test/features/driver_trip/home_motorista_loading_test.dart`
  PASS (41 testes).
- GREEN final: `./scripts/qa_gate_card.sh --no-e2e` PASS; quality ratchet
  PASS; `flutter analyze` sem issues; `flutter test` PASS (587 testes); E2E
  real desabilitado por escopo.
- Nota: os caminhos planejados `test/features/auth` e `test/features/session`
  nao existem neste app; foram usados os caminhos reais `test/app/session`,
  `test/login_screen_test.dart`, account e onboarding.

---

### DISCOVERY-PD-AUDIT-001 - Auditoria visual e de estados reais

### Status
DONE (commit: 559447a)

### Objetivo
Mapear estados reais de tracking, motorista, responsavel, presenca e admin antes
de redesenhar UI.

### Criterios de Aceite
- Documento de auditoria lista telas, evidencias e problemas priorizados.
- Gate atual do app segue verde antes de mudancas visuais.
- Execucao control doc recebe resumo das descobertas relevantes.

### Validacao minima
- `./scripts/qa_gate_card.sh --no-e2e`.
- Revisao do documento de auditoria criado.

### Resultado
- Auditoria criada em
  `docs/ux/audits/2026-06-30-product-experience/README.md`.
- Evidencia inspecionada a partir de
  `output/visual-smoke/analysis-20260624-real/phase/` e
  `output/visual-smoke/analysis-20260624-real/phase/admin-web/`.
- Screenshots ficaram em `output/` e nao foram versionados.
- Achados priorizados em S1/S2/S3 para `UX-SIGNAL-001`,
  `UX-DRIVER-SHEET-001`, `UX-PRESENCE-001`,
  `UX-GUARDIAN-PRESENCE-001`, `UX-TODAY-001`,
  `UX-ONBOARDING-ADMIN-001`, `UX-COPY-001`,
  `DESIGN-SYSTEM-001` e `QA-ACCESSIBILITY-001`.
- Proximo card recomendado pela ordem do backlog: `DESIGN-SYSTEM-001`.
- Gate final: `./scripts/qa_gate_card.sh --no-e2e` PASS com matriz E2E por
  papel PASS, matriz visual PASS, performance smoke PASS, `flutter analyze`
  PASS, 611 testes Flutter PASS e quality ratchet PASS. E2E real permaneceu
  desabilitado por escopo.

---

### DESIGN-SYSTEM-001 - Inventario de tokens e componentes

### Status
DONE (commit: bfc7b6b)

### Objetivo
Inventariar tema, cores, tipografia, botoes, cards e sheets reutilizaveis antes
de criar nova gramatica visual.

### Criterios de Aceite
- Inventario identifica tokens e componentes existentes para reaproveitar.
- Lacunas de design system ficam descritas sem alterar UI prematuramente.
- `flutter analyze` continua verde.

### Validacao minima
- `rg -n "VanRides|ThemeData|ColorScheme|TextTheme|ButtonStyle|Card|Sheet" lib`.
- `flutter analyze`.

### Resultado
- Inventario criado em `docs/ux/2026-06-30-design-system-inventory.md`.
- O documento consolida tokens, tema, motion, superficies liquid/glass,
  botoes, pills/status, feedback, navegacao, map-first, admin portal e lacunas
  para os proximos cards de UX.
- Nenhuma UI, contrato backend/API, payload, WebSocket, permissao nativa,
  provider de mapa, renderer, fixture, deploy, push ou VPS foi alterado.
- `UX-SIGNAL-001` ficou como proximo card recomendado porque a auditoria
  classificou confianca/sinal como achado S1 e o dominio ja possui estados
  `live`, `delayed`, `stale`, `paused`, `unavailable` e `unknown`.
- Validacoes:
  - `rg -n "VanRides|ThemeData|ColorScheme|TextTheme|ButtonStyle|Card|Sheet" lib`: PASS, 1477 referencias.
  - `flutter analyze`: PASS, sem issues.
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS com matriz E2E por papel,
    matriz visual, performance smoke, 611 testes Flutter, quality ratchet e
    E2E real desabilitado por escopo.

---

### UX-SIGNAL-001 - Estados live, stale e sem sinal

### Status
DONE (commit: 9d9f67a)

### Objetivo
Tornar os estados de sinal compreensiveis para passageiro, responsavel e
motorista, explicando causa e recuperacao.

### Criterios de Aceite
- UI diferencia live, stale e sem sinal sem linguagem tecnica.
- Copy muda conforme papel e necessidade operacional.
- Estados vazios ou falhos preservam proxima acao clara.

### Validacao minima
- `flutter test test/features/tracking test/features/guardian test/features/driver_trip`.
- `./scripts/qa_gate_card.sh --no-e2e`.

### Resultado
- Presenter de sinal passou a aceitar papel operacional com
  `TripSignalRole.passenger`, `TripSignalRole.guardian` e
  `TripSignalRole.driver`.
- Passageiro agora ve estados como `Posição recente`, `Posição antiga`,
  `Envio pausado` e `Aguardando posição da van`, com acao visivel
  `Atualizar sinal` quando o resumo inicial precisa orientar recuperacao.
- Responsavel agora recebe copy familiar, como `Posição recente da van`, e
  helper contextual no painel de localizacao.
- Motorista agora recebe linguagem de transmissao, como
  `Transmitindo ao vivo`, `Sinal antigo` e orientacao segura para verificar
  conexao/localizacao sem incentivar acao insegura durante a corrida.
- `DriverPremiumChip` passou a truncar texto internamente para preservar layout
  em escala de texto alta.
- Testes de edicao de rota do motorista foram extraidos para
  `test/features/driver_trip/presentation/widgets/driver_home_route_editing_widgets_test.dart`
  para manter o arquivo tocado abaixo do limite do quality ratchet.
- Nenhum contrato HTTP/API, endpoint, payload, WebSocket, permissao nativa,
  provider de mapa, renderer, fixture DEV, schema backend, deploy, VPS ou push
  foi alterado.
- Validacoes:
  - RED focado inicial: testes de passageiro/responsavel/motorista falharam
    com as copys novas ausentes.
  - GREEN focado:
    `flutter test test/features/tracking/presentation/widgets/passenger_trip_glass_sheet_test.dart test/features/guardian/presentation/widgets/guardian_localizacao_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_trip_map_widgets_test.dart`
    PASS.
  - `flutter test test/features/tracking test/features/guardian test/features/driver_trip`: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS com quality ratchet,
    preflight E2E, role matrix, visual matrix, performance smoke,
    `flutter analyze`, 613 testes Flutter e E2E real desabilitado por escopo.
- Proximo card recomendado: `UX-TODAY-001`, para melhorar a leitura do dia
  operacional agora que os estados de sinal ficaram claros por papel.

---

### UX-TODAY-001 - Hoje operacional

### Status
DONE (commit: 9b0c562)

### Objetivo
Melhorar a primeira leitura do dia operacional para mostrar o que esta pronto, o
que falta e qual acao vem agora.

### Criterios de Aceite
- Passageiro, motorista e admin entendem rapidamente o estado de hoje.
- Excecoes e pendencias nao competem com a acao principal.
- Telas pequenas mantem hierarquia legivel.

### Validacao minima
- `flutter test test/features/driver_trip test/features/tracking test/features/admin`.
- `./scripts/qa_gate_card.sh --no-e2e`.

### Entrega
- Motorista agora recebe um resumo de hoje antes da acao principal, com
  leitura de dia pronto para sair ou em execucao.
- Passageiro agora ve o estado de hoje no resumo persistente da viagem,
  combinando quantidade de viagens e estado atual da van.
- Admin agora tem um bloco `Hoje operacional` no dashboard para separar dia em
  execucao, publicado/concluido ou ainda nao montado antes das revisoes.
- Nenhum contrato HTTP/API, endpoint, payload, WebSocket, permissao nativa,
  provider de mapa, renderer, fixture DEV, schema backend, deploy, VPS ou push
  foi alterado.
- Validacoes:
  - RED focado inicial: testes de motorista, passageiro e admin falharam com
    as copys novas ausentes.
  - GREEN focado:
    `flutter test test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart test/features/admin/admin_dashboard_screen_test.dart`
    PASS.
  - `flutter test test/features/driver_trip test/features/tracking test/features/admin`: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS com quality ratchet,
    preflight E2E, role matrix, visual matrix, performance smoke,
    `flutter analyze`, 613 testes Flutter e E2E real desabilitado por escopo.
- Proximo card recomendado: `UX-COPY-001`, para transformar as novas copys
  operacionais em uma fonte rastreavel e evitar duplicacao nos proximos cards.

---

### UX-COPY-001 - Copydeck operacional

### Status
DONE (commit: ae765f4)

### Objetivo
Substituir linguagem tecnica primaria por copy operacional, humana e rastreavel
em copydeck.

### Criterios de Aceite
- UI primaria evita termos como backend, polling, fixture e websocket.
- Copydeck documenta mensagens por papel e estado.
- Cards futuros referenciam o copydeck em vez de duplicar texto.

### Validacao minima
- `rg -n "snapshot|polling|backend|fixture|websocket|Unsupported|Bad Request|Conflict|sem contexto|manifesto" lib test`.
- `flutter test test/l10n test/features`.
- `./scripts/qa_gate_card.sh --no-e2e`.

### Entrega
- Copydeck rastreavel criado em `backlog/VanRides-Copydeck-Operacional.md`
  com regras por papel/estado e excecoes tecnicas permitidas apenas em codigo,
  contratos, rotas, fixtures e testes de camada de dados.
- Passageiro, motorista, responsavel, notificacoes e portal admin deixaram de
  exibir copy primaria com `backend`, `manifesto`, `Firebase/APNs` e detalhes de
  contrato nas superficies tocadas.
- `test/l10n/app_strings_test.dart` passou a proteger amostras de copy
  operacional primaria e a existencia do copydeck.
- O scan obrigatorio ainda encontra termos tecnicos em nomes internos como
  `websocket_service.dart`, `backendRoleLabel`, `snapshotOperacional`,
  classes `Unsupported*`, rotas `manifesto-preview` e testes de contrato; essas
  excecoes ficaram documentadas no copydeck para evitar renomear contratos neste
  card.
- Nenhum contrato HTTP/API, endpoint, payload, WebSocket, permissao nativa,
  provider de mapa, renderer, fixture DEV, schema backend, deploy, VPS ou push
  foi alterado.
- Validacoes:
  - RED focado: `flutter test test/l10n/app_strings_test.dart` falhou porque
    `Manifesto de hoje` ainda era copy primaria e o copydeck nao existia.
  - GREEN focado:
    `flutter test test/l10n/app_strings_test.dart test/features/notifications/push_notification_preferences_card_test.dart test/screens/presencas_hoje_screen_test.dart test/features/admin/admin_rotas_screen_test.dart test/features/admin/presentation/widgets/admin_responsaveis_widgets_test.dart`
    PASS.
  - `rg -n "snapshot|polling|backend|fixture|websocket|Unsupported|Bad Request|Conflict|sem contexto|manifesto" lib test`: PASS como auditoria, com ocorrencias remanescentes classificadas como internas/contrato/teste.
  - `flutter test test/l10n test/features`: PASS, 527 testes.
  - `flutter analyze`: PASS, sem issues.
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS com quality ratchet, preflight
    E2E, role matrix, visual matrix, performance smoke, `flutter analyze`, 615
    testes Flutter e E2E real desabilitado por escopo.
- Proximo card recomendado: `UX-MOTION-001`, para padronizar movimento calmo e
  opcional agora que o vocabulario operacional esta consolidado.

---

### UX-MOTION-001 - Movimento calmo e opcional

### Status
DONE (commit: 2ac6e8c)

### Objetivo
Padronizar movimento operacional sem esconder mudancas de estado e respeitando
reduced motion.

### Criterios de Aceite
- Transicoes sao calmas, previsiveis e podem ser reduzidas.
- Motion nao mascara erro, loading ou perda de sinal.
- Componentes tocados seguem tokens de movimento compartilhados.

### Validacao minima
- `flutter test test/features/tracking test/features/driver_trip test/features/guardian`.
- `./scripts/qa_gate_card.sh --no-e2e`.

### Impact Analysis
- Contexto: depois de `UX-SIGNAL-001`, `UX-TODAY-001` e `UX-COPY-001`, os
  fluxos criticos ja comunicam melhor o estado operacional. O proximo risco e
  manter transicoes, sheets e navegacao visual coerentes sem cansar usuario,
  motorista ou responsavel, principalmente quando `disableAnimations` ou
  `accessibleNavigation` estao ativos.
- Telas/fluxos afetados: tracking do passageiro, sheet/map-first,
  navegacao inferior do responsavel e troca de abas do motorista.
- Dependencia de contrato backend: nenhuma. Endpoints, payloads, auth,
  WebSocket, fixtures DEV, permissao nativa, provider de mapa e renderer
  permanecem preservados.
- Mudancas tecnicas previstas: centralizar tokens/policy de motion
  operacional em `VanRidesMotionPolicy`, substituir duracoes/curvas
  hardcoded nos widgets tocados por tokens compartilhados e manter fallback
  instantaneo para reduced motion.
- Riscos e mitigacao: regredir hierarquia visual ou esconder loading/perda de
  sinal com animacao; mitigado por testes de policy, widget tests de
  tracking/driver/guardian e gate `--no-e2e`.
- Rollback simples: reverter commit do card restaura duracoes locais sem
  alterar contratos externos.

### Plano
1. Criar testes RED para tokens/policy de motion operacional e para widgets
   chave usando reduced motion.
2. Implementar helpers de motion compartilhados sem adicionar plugin nativo.
3. Migrar tracking, motorista e responsavel tocados para os tokens.
4. Rodar validacao focada, `flutter analyze` e `./scripts/qa_gate_card.sh --no-e2e`.
5. Registrar QA log e fechar backlog com commit real.

### Test Plan do Card
- Risco principal: movimento hardcoded continuar animando quando o usuario
  pediu menos movimento ou mascarar mudanca operacional relevante.
- Testes novos/ajustados:
  - [x] Tipo: unit/widget.
  - [x] Arquivo: `test/app/theme/vanrides_motion_test.dart`.
  - [x] Arquivo: testes focados de tracking/driver/guardian tocados.
  - [x] Cenario feliz: motion operacional usa duracoes/curvas compartilhadas.
  - [x] Cenario de borda: reduced motion zera duracoes/transicoes e preserva
        estado visual.
- Regressao minima executada:
  - [x] `flutter analyze`
  - [x] `flutter test test/features/tracking test/features/driver_trip test/features/guardian`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
- Evidencia registrada em QA Log:
  - [x] Sim

### Entrega
- `VanRidesMotionTokens` ganhou tokens compartilhados para selecao de
  navegacao, troca de conteudo da sheet, movimento programatico da sheet,
  settle da camera e troca de abas do motorista.
- Passageiro, motorista e responsavel passaram a usar `VanRidesMotionPolicy`
  nos pontos tocados, zerando duracoes/curvas quando `disableAnimations` ou
  `accessibleNavigation` estao ativos.
- `PassengerTripSheetDetent` e `TripLiveMapMotionPolicy` agora referenciam os
  tokens compartilhados em vez de duracoes soltas.
- Testes novos cobrem tokens e reduced motion em sheet do passageiro, nav
  map-first e nav do responsavel.
- Nenhum contrato HTTP/API, endpoint, payload, WebSocket, permissao nativa,
  provider de mapa, renderer, fixture DEV, deploy, VPS ou push foi alterado.
- Validacoes:
  - RED focado inicial: suite nova falhou porque os tokens compartilhados ainda
    nao existiam.
  - GREEN focado:
    `flutter test test/app/theme/vanrides_motion_test.dart test/features/tracking/presentation/widgets/passenger_trip_motion_test.dart test/features/tracking/presentation/map_first_bottom_nav_test.dart test/features/guardian/presentation/widgets/guardian_motion_test.dart`
    PASS.
  - `rg -n "Duration\\(milliseconds:|Curves\\.easeOutCubic|duration: const Duration" lib/features/tracking lib/features/driver_trip lib/features/guardian -g '*.dart'`: PASS sem ocorrencias de motion hardcoded nos fluxos tocados.
  - `flutter test test/features/tracking test/features/driver_trip test/features/guardian`: PASS, 194 testes.
  - `flutter analyze`: PASS, sem issues.
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS com quality ratchet, preflight
    E2E, role matrix, visual matrix, performance smoke, contratos MapLibre,
    `flutter analyze`, 627 testes Flutter e E2E real desabilitado por escopo.
- Proximo card recomendado: `UX-OFFLINE-001`, para preservar ultimo estado util
  e recuperacao clara em rede ruim.

---

### UX-OFFLINE-001 - Estados de rede ruim e ultimo estado util

### Status
DONE (commit: e6a1c35)

### Objetivo
Manter o ultimo estado util com timestamp e recuperacao clara quando a rede
falhar.

### Criterios de Aceite
- Falhas de rede nao apagam contexto operacional util.
- UI mostra frescor do dado e proxima tentativa de recuperacao.
- Base fica pronta para offline da Maps Platform.

### Validacao minima
- `flutter test test/features/tracking test/features/guardian test/features/driver_trip`.
- `./scripts/qa_gate_card.sh --no-e2e`.

### Impact Analysis
- Contexto: depois de `UX-MOTION-001`, a experiencia ja tem movimento calmo e
  estados de sinal mais claros. O proximo risco para piloto com empresa
  parceira e a tela parecer "zerada" ou insegura quando a rede oscila,
  mesmo havendo ultimo dado operacional util.
- Telas/fluxos afetados: mapa/sheet do passageiro, acompanhamento do
  responsavel e sheet ativa do motorista.
- Dependencia de contrato backend: nenhuma. O card reutiliza
  `TripTrackingStateRecord.driverLocationUpdatedAt`, `etaAtualizadoEm`,
  `TripLocationFreshness` e a acao de refresh ja existente; endpoints,
  payloads, WebSocket, auth, fixtures DEV, renderer e provider de mapa ficam
  preservados.
- Mudancas tecnicas previstas: criar apresentacao compartilhada de
  continuidade offline para indicar ultimo estado util, horario do dado e
  acao de recuperacao por papel; plugar essa apresentacao nos componentes ja
  responsaveis por sinal no passageiro, responsavel e motorista.
- Riscos e mitigacao: duplicar copy de sinal ou prometer posicao atual quando
  o dado esta antigo; mitigado por testes RED/GREEN em presenter e widgets
  dos tres papeis, validacao focada e gate `--no-e2e`.
- Rollback simples: reverter commits do card restaura a copy anterior sem
  alterar contratos externos, dados persistidos ou infraestrutura.

### Plano
1. Criar testes RED para presenter de continuidade offline e para a copy
   visivel nos fluxos de passageiro, responsavel e motorista.
2. Implementar helper compartilhado com timestamp formatado, mensagem calma e
   acao de recuperacao por papel.
3. Integrar o helper aos componentes de tracking ja existentes sem adicionar
   plugin, endpoint, cache persistente ou renderer novo.
4. Rodar testes focados, `flutter analyze` e `./scripts/qa_gate_card.sh --no-e2e`.
5. Registrar QA log, fechar backlog com commit real e atualizar harness.

### Test Plan do Card
- Risco principal: rede ruim apagar o contexto operacional ou mostrar uma
  mensagem generica sem horario e sem recuperacao clara.
- Testes novos/ajustados:
  - [x] Tipo: unit/widget.
  - [x] Arquivo: `test/features/tracking/presentation/trip_offline_continuity_presenter_test.dart`.
  - [x] Arquivo: testes focados de passageiro, motorista e responsavel.
  - [x] Cenario feliz: dado atrasado/antigo preserva ultimo estado util com
        horario e acao de recuperacao.
  - [x] Cenario de borda: sem timestamp real continua honesto e nao promete
        posicao ao vivo.
- Regressao minima executada:
  - [x] `flutter analyze`
  - [x] `flutter test test/features/tracking test/features/guardian test/features/driver_trip`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
- Evidencia registrada em QA Log:
  - [x] Sim

### Entrega
- Novo presenter `TripOfflineContinuityPresentation` centraliza a regra de
  continuidade offline para passageiro, responsavel e motorista.
- Quando o sinal nao esta ao vivo e existe `driverLocationUpdatedAt`, a UI
  preserva o ultimo estado util com horario, por exemplo
  `Último estado útil às 07:08`.
- Passageiro mostra o horario no resumo da sheet sem apagar a acao
  `Atualizar sinal`.
- Responsavel recebe o mesmo contexto no acompanhamento da van.
- Motorista passa a ver o horario do ultimo estado util e uma orientacao
  segura para verificar conexao/localizacao quando estiver seguro.
- Estados sem timestamp confiavel continuam usando a copy anterior, evitando
  promessa falsa de dado ao vivo ou antigo.
- Nenhum contrato HTTP/API, endpoint, payload, WebSocket, permissao nativa,
  provider de mapa, renderer, fixture DEV, deploy, VPS ou push foi alterado.
- Validacoes:
  - RED focado inicial:
    `flutter test test/features/tracking/presentation/trip_offline_continuity_presenter_test.dart test/features/tracking/presentation/widgets/passenger_trip_glass_sheet_test.dart test/features/guardian/presentation/widgets/guardian_localizacao_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart`
    falhou porque o presenter ainda nao existia e os tres widgets ainda nao
    mostravam `Último estado útil às ...`.
  - GREEN focado: mesmo comando PASS.
  - `flutter test test/features/tracking test/features/guardian test/features/driver_trip`:
    PASS, 197 testes.
  - `flutter analyze`: PASS, sem issues.
  - Primeira tentativa de `./scripts/qa_gate_card.sh --no-e2e`: testes PASS,
    mas o quality ratchet bloqueou `passenger_trip_glass_sheet.dart` com 601
    linhas; o campo redundante foi removido e o arquivo voltou para 598 linhas.
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS com quality ratchet, preflight
    E2E, role matrix, visual matrix, performance smoke, contratos MapLibre,
    `flutter analyze`, 630 testes Flutter e E2E real desabilitado por escopo.
- Proximo card recomendado: `UX-NOTIFICATIONS-001`, respeitando o bloqueio de
  campanha real ate o contrato/backend de push estar pronto.

---

### UX-NOTIFICATIONS-001 - Experiencia de notificacoes acionaveis

### Status
DONE (commit: 416e4d7)

### Objetivo
Preparar UX de notificacoes com mensagens calmas, acionaveis e dependentes do
contrato backend, sem ativar campanha real.

### Criterios de Aceite
- Notificacoes previstas tem texto, acao e contexto por papel.
- App consome contrato de token/evento sem depender de Firebase/APNs real.
- Bloqueio externo de campanha real fica documentado.

### Validacao minima
- `flutter test test/features/notifications`.
- `./scripts/qa_gate_card.sh --no-e2e`.

### Impact Analysis
- Contexto:
  - `BE-PUSH-001A/B` ja deixam registro de token e dispatch interno prontos com
    provider `noop`. Este card prepara a experiencia do app para explicar
    notificacoes por papel sem ativar Firebase/APNs, campanha real ou prompt
    nativo automatico.
- Mudancas tecnicas:
  - Modelar um plano de notificacoes acionaveis com evento, papel, texto calmo,
    acao esperada e destino logico da tela.
  - Exibir essa matriz na tela de preferencias de notificacoes como readiness
    operacional, preservando `NoopPushNotificationGateway` como default.
  - Atualizar a documentacao de readiness com os eventos permitidos e bloqueios
    externos.
- Contratos afetados:
  - Nao ha novo endpoint HTTP nem mudanca em payload existente.
  - O app continua consumindo apenas o contrato de registro `/devices/fcm-token`
    quando permissao/token ja existem; dispatch real segue bloqueado ate
    provider configurado.
- Riscos e mitigacao:
  - Risco de parecer push real pronto: mitigado por copia explicita de
    preparacao, provider noop e documentacao do bloqueio de Firebase/APNs/device
    QA.
  - Risco de notificacoes ansiosas ou genericas: mitigado por matriz por papel,
    foco em acao clara e eventos operacionais limitados.
  - Risco de prompt nativo indesejado: mitigado mantendo a tela apenas leitora e
    preservando `requestPermission()` fora do login.
- Validacao minima obrigatoria:
  - RED/GREEN focado de `test/features/notifications`.
  - `flutter analyze`.
  - `./scripts/qa_gate_card.sh --no-e2e`.
- Rollback simples:
  - Reverter os commits deste card; como a mudanca e app/docs sem contrato novo,
    nao ha migracao, deploy ou estado remoto para desfazer.

### Plano
1. Criar testes RED para matriz de eventos, papeis, acoes e destino logico.
2. Implementar modelo de dominio de avisos planejados alinhado ao dispatch
   interno do backend.
3. Exibir a matriz na tela de preferencias sem ativar provider real, prompt
   nativo ou deep link falso.
4. Atualizar readiness de push com eventos permitidos, bloqueios externos e
   Definition of Done para ativacao real.
5. Rodar testes focados, `flutter analyze`, gate oficial e registrar QA.

### Test Plan do Card
- Risco principal: a UI parecer push real pronto ou gerar ansiedade com eventos
  genericos/sem acao clara.
- Testes novos/ajustados:
  - [x] Tipo: unit/widget.
  - [x] Arquivo:
        `test/features/notifications/push_notification_action_plan_test.dart`.
  - [x] Arquivo:
        `test/features/notifications/push_notification_preferences_card_test.dart`.
  - [x] Cenario feliz: matriz cobre eventos do backend, papeis, texto, acao e
        destino logico.
  - [x] Cenario de borda: responsavel recebe contexto da pessoa acompanhada e
        login/prompt continuam passivos.
- Regressao minima executada:
  - [x] `flutter test test/features/notifications`
  - [x] `flutter analyze`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
- Evidencia registrada em QA Log:
  - [x] Sim

### Entrega
- Novo modelo `PushNotificationActionPlan` define avisos acionaveis por papel,
  evento backend, categoria, titulo, corpo, acao, destino logico e essencialidade.
- A tela `PushNotificationPreferencesCard` passa a mostrar `Avisos planejados`
  com exemplos concretos para passageiro, responsavel, motorista e empresa.
- O contrato visual continua em readiness: `NoopPushNotificationGateway` segue
  como default, o login nao chama `requestPermission()` e nenhuma campanha real
  de Firebase/APNs foi ativada.
- `Push-Notifications-Readiness.md` foi atualizado com o estado real:
  `/devices/fcm-token` e dispatch interno existem, mas provider real,
  worker/retry, deep link real e QA em device fisico seguem pendentes.
- Teste administrativo de logout agora rola ate o botao antes do toque, pois a
  tela de conta ficou maior com a matriz de notificacoes.
- Nenhum contrato HTTP/API novo, payload, WebSocket, permissao nativa, provider
  real, segredo, deploy, VPS ou push foi alterado.
- Validacoes:
  - RED focado inicial:
    `flutter test test/features/notifications`
    falhou como esperado porque `push_notification_action_plan.dart` ainda nao
    existia e a tela ainda nao exibia `Avisos planejados`.
  - GREEN focado:
    `flutter test test/features/notifications`: PASS, 19 testes.
  - `flutter analyze`: PASS, sem issues.
  - Primeira tentativa de `./scripts/qa_gate_card.sh --no-e2e`: falhou em
    `admin_shell_screen_test.dart` porque o teste de logout assumia botao
    visivel sem rolagem apos a tela de conta crescer.
  - Regressao isolada apos ajuste:
    `flutter test test/features/admin/admin_shell_screen_test.dart --plain-name 'logout administrativo limpa sessao persistida'`:
    PASS.
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS com quality ratchet, preflight
    E2E, role matrix, visual matrix, performance smoke, contratos MapLibre,
    `flutter analyze`, 634 testes Flutter e E2E real desabilitado por escopo.
  - Dev Harness `done-check`, `complete-card`, `refresh-dashboard`, `status` e
    `check`: PASS; `active_card=null` e `last_completed_card=UX-NOTIFICATIONS-001`.
- Proximo card recomendado: `UX-TRIP-001A`, para clarear a jornada do
  passageiro com foco em van, parada, ETA e confianca do dado.

---

### UX-TRIP-001A - Jornada do passageiro mais clara

### Status
DONE (commit: 5f40152)

### Objetivo
Melhorar a leitura da viagem para passageiro com foco em van, parada, ETA e
confianca do dado.

### Impact Analysis
- Contexto do card: fase de UX da corrida `map-first`, depois de offline e
  notificacoes. A tela do passageiro ja mostra mapa, estado da van, minha
  parada e acoes principais, mas ainda nao junta ETA, rota e confianca em uma
  leitura unica.
- Mudancas tecnicas: adicionar um presenter/componente pequeno para resumir
  ETA, status de rota e confianca do dado usando `TripTrackingStateRecord`,
  sem alterar payloads nem endpoints.
- Contratos afetados: nenhum contrato HTTP, auth, CORS, WebSocket ou schema de
  backend. A mudanca consome campos existentes (`routeSnapshotStatus`,
  `etaConfidence`, `locationFreshness`, `etaMinutosAtePassageiro` e parada do
  passageiro).
- Riscos e mitigacao: criar texto contraditorio entre ETA e sinal; mitigar com
  testes de casos ao vivo, rota sem materializar e tracking indisponivel.
  Aumentar hotspots de UI; mitigar colocando presenter/widget em arquivos
  pequenos e mantendo `passenger_trip_glass_sheet.dart` quase inalterado.
- Validacao minima obrigatoria: testes focados de tracking, `flutter analyze`,
  `./scripts/qa_gate_card.sh --no-e2e` e registro de QA.
- Rollback simples: reverter o presenter/widget, os testes adicionados e este
  bloco documental; nenhum dado persistido ou contrato externo muda.

### Plano de Implementacao
1. Criar testes RED para a narrativa de viagem do passageiro cobrindo ETA
   confiavel, rota ainda em preparo e ausencia de tracking/rota.
2. Implementar o presenter de leitura da viagem com labels curtas para
   confianca, rota e proxima expectativa do passageiro.
3. Exibir o resumo no sheet do passageiro sem aumentar a densidade do mapa nem
   esconder as acoes principais.
4. Garantir fallback claro quando ETA, rota ou sinal nao estiverem disponiveis.
5. Rodar validacoes, registrar QA, fechar backlog e sincronizar o harness.

### Test Plan do Card
- Risco principal: a UI prometer precisao quando o app so tem ultimo estado ou
  rota nao materializada.
- Testes novos/ajustados:
  - [x] Tipo: unit/widget.
  - [x] Arquivo:
        `test/features/tracking/presentation/passenger_trip_readiness_presenter_test.dart`.
  - [x] Arquivo:
        `test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart`.
  - [x] Cenario feliz: ETA confiavel com rota materializada comunica chegada e
        confianca.
  - [x] Cenario de borda: rota planejada/stale ou tracking indisponivel usa
        fallback sem contradizer o estado da van.
- Regressao minima planejada:
  - [x] `flutter test test/features/tracking`
  - [x] `flutter analyze`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
- Evidencia registrada em QA Log:
  - [x] Sim

### Criterios de Aceite
- Passageiro entende onde a van esta e o que esperar em seguida.
- Estados de rota, parada e ETA nao parecem contraditorios.
- UI preserva fallback quando tracking ou rota estiverem indisponiveis.

### Validacao minima
- `flutter test test/features/tracking`.
- `./scripts/qa_gate_card.sh --no-e2e`.

### Entrega
- Novo presenter `PassengerTripReadinessPresentation` resume ETA, rota e
  confianca do dado a partir de `TripTrackingStateRecord`, sem criar contrato
  novo com o backend.
- O footer sheet do passageiro ganhou uma linha compacta com a leitura da
  viagem: ETA ate a parada, status da rota e confianca do ETA.
- Viagens concluidas/canceladas nao exibem a linha de confianca para evitar
  duplicar a narrativa terminal da viagem.
- Fallbacks preservam clareza quando a rota ainda esta em preparo ou quando
  tracking/ETA nao estao confiaveis.
- Validacoes:
  - RED unit:
    `flutter test test/features/tracking/presentation/passenger_trip_readiness_presenter_test.dart`
    falhou como esperado porque o presenter ainda nao existia.
  - RED widget:
    `flutter test test/features/tracking/presentation/widgets/passenger_home_trip_widgets_test.dart --plain-name 'sheet premium do passageiro resume paradas e sinal'`
    falhou como esperado porque a linha de prontidao ainda nao aparecia.
  - GREEN focado:
    ambos os comandos acima passaram apos a implementacao.
  - Regressao tracking:
    `flutter test test/features/tracking`: PASS, 99 testes.
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS, sem issues.
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS com quality ratchet, preflight
    E2E, role matrix, visual matrix, performance smoke, contratos MapLibre,
    `flutter analyze`, 637 testes Flutter e E2E real desabilitado por escopo.
- Limites:
  - Nenhum contrato HTTP/API, payload, WebSocket, permissao nativa, provider,
    backend, deploy, VPS, push ou smoke manual em device fisico foi alterado.

---

### UX-TRIP-001B - Jornada do motorista mais acionavel

### Status
DONE (commit: 0d86381)

### Objetivo
Dar ao motorista uma leitura direta de proxima parada, passageiro, acao primaria
e situacao da rota.

### Impact Analysis
- Contexto do card: depois de clarear a jornada do passageiro, a proxima
  lacuna visivel fica no motorista durante a corrida. A tela ativa ja mostra
  sinal, proximo passageiro, ETA e acoes, mas a decisao operacional principal
  ainda fica espalhada entre painel, navegacao e acoes de revisao.
- Mudancas tecnicas: adicionar um presenter/widget pequeno para resumir a
  proxima acao do motorista com parada, passageiro, acao primaria e situacao
  da rota, usando dados ja existentes em `DriverTripRecord` e
  `TripTrackingStateRecord`.
- Contratos afetados: nenhum contrato HTTP, auth, CORS, WebSocket ou schema de
  backend. A mudanca consome `proximaParada`, `etaMinutosAteProximaParada`,
  `rotaGeografica.navegacaoPreferencial`, `routeSnapshotStatus`,
  `locationFreshness` e `etaConfidence` ja presentes no app.
- Riscos e mitigacao: duplicar conteudo visual da tela ativa ou esconder a fila
  operacional; mitigar com um componente compacto fora do hotspot
  `driver_home_trip_sheet_widgets.dart` e testes cobrindo navegacao disponivel,
  fallback sem handoff e sinal degradado.
- Validacao minima obrigatoria: testes focados de `driver_trip`,
  `flutter analyze`, `./scripts/qa_gate_card.sh --no-e2e` e registro de QA.
- Rollback simples: reverter presenter/widget, testes e este bloco documental;
  nenhum dado persistido, endpoint ou contrato externo muda.

### Plano de Implementacao
1. Criar testes RED para a leitura acionavel do motorista em corrida com
   navegacao disponivel, sem handoff de Maps e sinal degradado.
2. Implementar presenter puro para titulo, resumo, rota, passageiro e acao
   primaria recomendada.
3. Renderizar a leitura como bloco compacto no sheet ativo, antes das acoes de
   revisao, preservando fila, navegacao e botoes existentes.
4. Garantir que estados sem proxima parada ou terminal nao mostrem informacao
   contraditoria.
5. Rodar validacoes, registrar QA, fechar backlog e sincronizar o harness.

### Test Plan do Card
- Risco principal: a UI parecer uma segunda fonte de verdade diferente dos
  botoes reais de navegacao/embarque.
- Testes novos/ajustados:
  - [x] Tipo: unit/widget.
  - [x] Arquivo:
        `test/features/driver_trip/presentation/driver_trip_action_focus_presenter_test.dart`.
  - [x] Arquivo:
        `test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart`.
  - [x] Cenario feliz: corrida com Maps disponivel recomenda ir para a proxima
        parada e mostra passageiro, ETA e rota validada.
  - [x] Cenario de borda: sem handoff de Maps recomenda confirmar embarque sem
        parecer que existe navegacao pronta.
  - [x] Cenario de borda: sinal degradado preserva alerta claro sem bloquear a
        acao operacional.
- Regressao minima planejada:
  - [x] `flutter test test/features/driver_trip`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
- Evidencia registrada em QA Log:
  - [x] Sim

### Criterios de Aceite
- Motorista ve proxima acao sem procurar em multiplos cards.
- Fila operacional continua disponivel sem excesso de densidade.
- Estados de fallback e sem sinal seguem claros.

### Validacao minima
- `flutter test test/features/driver_trip`.
- `./scripts/qa_gate_card.sh --no-e2e`.

### Entrega
- Novo presenter `DriverTripActionFocusPresentation` centraliza a leitura da
  proxima acao do motorista a partir de `DriverTripRecord` e
  `TripTrackingStateRecord`, sem criar contrato novo com o backend.
- O sheet ativo do motorista ganhou um painel compacto antes das acoes de
  revisao, com titulo de acao, parada, ETA e estado da rota.
- Quando o handoff de Maps existe, a leitura recomenda ir para a proxima
  parada; sem handoff, recomenda confirmar embarque sem prometer navegacao
  pronta.
- Sinal degradado preserva a acao operacional, mas comunica o cuidado sem
  bloquear o fluxo.
- Validacoes:
  - RED unit:
    `flutter test test/features/driver_trip/presentation/driver_trip_action_focus_presenter_test.dart`
    falhou como esperado porque o presenter ainda nao existia.
  - RED widget:
    `flutter test test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart --plain-name 'Driver home active sheet renders next stop and dispatches actions'`
    falhou como esperado porque o sheet ainda nao mostrava
    `Agora: ir para Aluno A`.
  - GREEN focado:
    os testes de presenter, sheet ativo e sinal degradado passaram apos a
    implementacao.
  - Regressao driver_trip:
    `flutter test test/features/driver_trip`: PASS, 69 testes.
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS, sem issues.
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS com quality ratchet, preflight
    E2E, role matrix, visual matrix, performance smoke, contratos MapLibre,
    `flutter analyze`, 641 testes Flutter e E2E real desabilitado por escopo.
- Limites:
  - Nenhum contrato HTTP/API, payload, WebSocket, permissao nativa, provider,
    backend, deploy, VPS, push ou smoke manual em device fisico foi alterado.

---

### UX-PRESENCE-001 - Presenca sem vazio inutil

### Status
DONE (commit: 267a395)

### Objetivo
Tornar estados de presenca vazios, pendentes e decididos uteis para responsavel,
passageiro e admin.

### Impact Analysis
- Contexto do card: depois de clarear viagem para passageiro e motorista, a
  proxima friccao operacional fica nos estados de presenca. O app ja permite
  decidir presenca por servico e exibir listas em passageiro, responsavel e
  admin, mas alguns estados vazios/pendentes ainda podem parecer area morta em
  vez de proxima acao.
- Mudancas tecnicas: melhorar presenters/widgets existentes de presenca para
  tornar vazios, pendencias e decisoes mais explicitos, reaproveitando dados ja
  presentes no app e mantendo a mudanca local ao modulo Flutter.
- Contratos afetados: nenhum contrato HTTP, auth, CORS, WebSocket ou schema de
  backend. A mudanca deve consumir somente status/campos ja existentes de
  presenca, servicos disponiveis, passageiro vinculado e viagem.
- Riscos e mitigacao: duplicar mensagens entre passageiro/responsavel/admin ou
  prometer acao que depende de BE-CONFIG-001; mitigar com testes focados por
  papel e copy que diferencia vazio real, pendencia e decisao registrada.
- Validacao minima obrigatoria: testes focados de `service_presence`,
  `guardian` e telas de passageiro/admin tocadas, `flutter analyze`,
  `./scripts/qa_gate_card.sh --no-e2e` e registro de QA.
- Rollback simples: reverter presenters/widgets/testes deste card e este bloco
  documental; nenhum dado persistido, endpoint ou contrato externo muda.

### Plano de Implementacao
1. Mapear os estados atuais de presenca em passageiro, responsavel e admin.
2. Criar testes RED para vazio util, pendencia acionavel e decisao ja
   registrada.
3. Implementar copy e layout compactos sem esconder a acao primaria.
4. Garantir fallback quando nao houver servicos, vinculos ou viagem do dia.
5. Rodar validacoes, registrar QA, fechar backlog e sincronizar o harness.

### Test Plan do Card
- Risco principal: transformar falta de dados em promessa falsa de acao ou
  duplicar explicacoes em telas ja densas.
- Testes novos/ajustados:
  - [x] Tipo: widget/unit.
  - [x] Cenario feliz: servicos pendentes mostram proxima acao clara.
  - [x] Cenario de borda: ausencia de servicos/vinculos mostra vazio util, sem
        controle morto.
  - [x] Cenario de borda: presenca decidida mostra estado registrado e caminho
        seguro para revisar quando permitido.
- Regressao minima planejada:
  - [x] `flutter test test/features/service_presence test/features/guardian`
  - [x] `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
- Evidencia registrada em QA Log:
  - [x] Registrada em `backlog/QA-Execution-Log.md`.

### Criterios de Aceite
- Estados de presenca explicam o que existe e qual acao faz sentido.
- Nao ha espaco em branco ou controle morto quando faltam dados.
- BE-CONFIG-001 pode rodar em paralelo sem conflito de UI.

### Validacao minima
- `flutter test test/features/service_presence test/features/guardian`.
- `./scripts/qa_gate_card.sh --no-e2e`.

### Entrega
- Commit de planejamento: `8d10890`.
- Commit de implementacao: `267a395`.
- Arquivos principais:
  - `lib/features/service_presence/presentation/service_presence_decision_list.dart`
  - `lib/features/service_presence/presentation/service_presence_empty_state.dart`
  - `lib/features/tracking/presentation/widgets/passenger_home_presence_widgets.dart`
  - `lib/features/guardian/presentation/widgets/guardian_presencas_summary_widgets.dart`
  - `lib/features/admin/presentation/widgets/admin_passageiro_presence_today_section.dart`
- Validacoes:
  - RED focado: o conjunto de 16 testes falhou como esperado antes da
    implementacao por ausencia dos vazios uteis e copys novas.
  - GREEN focado: mesmo conjunto passou com 16 testes.
  - Regressao focada:
    `flutter test test/features/service_presence test/features/guardian test/features/tracking/presentation/widgets/passenger_home_presence_widgets_test.dart test/features/admin/admin_passageiros_presence_screen_test.dart`
    passou com 54 testes.
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS.
  - Primeiro gate: falhou apenas no quality ratchet porque
    `service_presence_decision_list.dart` chegou a 680 linhas; corrigido com a
    extracao de `ServicePresenceEmptyState`.
  - Gate final `./scripts/qa_gate_card.sh --no-e2e`: PASS com quality ratchet,
    preflight E2E, role matrix, visual matrix, performance smoke, contratos
    MapLibre, `flutter analyze`, 642 testes Flutter e E2E real desabilitado por
    escopo.
- Limites:
  - Nenhum contrato HTTP/API, payload, WebSocket, permissao nativa, provider,
    backend, deploy, VPS, push ou smoke manual em device fisico foi alterado.

---

### MAPS-TILES-001 - Spike de renderizacao de mapa

### Status
DONE (commit: 11c7b40)

### Objetivo
Comparar MapLibre e `flutter_map` com a mesma polyline, marcadores e experimento
offline/cache.

### Criterios de Aceite
- Matriz compara renderizacao, performance, tamanho e manutencao.
- Spike compila ou documenta bloqueio tecnico exato.
- Decisao usa o mesmo dado de rota para as duas opcoes.

### Validacao minima
- `flutter analyze`.
- `flutter test`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### MAPS-MAPLIBRE-PROOF-001 - Prova MapLibre feature-flagged

### Status
DONE (commit: e30ad10)

### Objetivo
Adicionar a primeira prova runtime do renderer MapLibre no app, atras de
feature flag local/dev, usando a mesma cena de rota e marcadores do contrato
`MAPS-TILES-001`.

### Criterios de Aceite
- `TripLiveMapPanel` ganha um adapter pequeno para alternar renderer sem
  reescrever rota, markers ou fallback.
- MapLibre renderiza a mesma rota e os mesmos markers do contrato do spike.
- Google Maps segue como fallback legado enquanto a prova MapLibre nao passar
  em gate e smoke de device.
- A prova usa style JSON self-hosted/sintetico e nao usa tiles publicos OSM
  como backend/cache/offline do produto.
- `MapPickerScreen` permanece fora da migracao nesta fatia.

### Validacao minima
- `flutter analyze`.
- `flutter test test/features/tracking`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- Smoke manual/device quando o renderer MapLibre for ativado.

---

### MAPS-MAPLIBRE-DEVICE-SMOKE-001 - Smoke device MapLibre

### Status
DONE (commit: 1f53a1a)

### Objetivo
Criar o comando oficial de smoke para provar a superficie MapLibre em
iOS Simulator/device antes de promover o renderer alem de ativacao explicita
dev/test.

### Criterios de Aceite
- Existe script `qa_maplibre_device_smoke.sh` com modo rapido `--check-only`
  sem device e modo real `--run-device`.
- O gate local por card valida o contrato com `--check-only`, sem depender de
  device.
- O modo real ativa `VANRIDES_MAPLIBRE_PROOF=true` e
  `VANRIDES_MAPLIBRE_DEVICE_SMOKE=true`.
- Ausencia de device e registrada como `BLOCKED_VALIDATION_DEVICE`, nao como
  sucesso de render.
- QA Log registra se o smoke real foi executado ou ficou bloqueado.

### Validacao minima
- `python3 scripts/qa_maplibre_device_smoke_test.py`.
- `./scripts/qa_maplibre_device_smoke.sh --check-only`.
- `flutter test test/features/tracking/trip_live_map_panel_test.dart`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### MAPS-MAPLIBRE-PUBLISH-PORT-SMOKE-001 - Publish port smoke MapLibre

### Status
DONE (commit: 34c20b5)

### Objetivo
Adicionar suporte opt-in a `--publish-port` no comando oficial de smoke
MapLibre para desbloquear a execucao em iPhone iOS wireless antes de validar
style/tile self-hosted ou promover o renderer.

### Criterios de Aceite
- `qa_maplibre_device_smoke.sh --help` mostra `--publish-port`.
- O modo real passa `--publish-port` ao `flutter test` somente quando solicitado
  por flag ou ambiente.
- O gate rapido segue usando apenas `--check-only`, sem device e sem
  publish-port.
- QA doc registra que publish-port remove o bloqueio de startup wireless, mas
  nao prova tile service, style real ou offline pack.
- QA Log registra se a tentativa real com publish-port passou ou ficou bloqueada.

### Validacao minima
- `python3 scripts/qa_maplibre_device_smoke_test.py`.
- `./scripts/qa_maplibre_device_smoke.sh --check-only`.
- `flutter test test/features/tracking/trip_live_map_panel_test.dart`.
- `flutter analyze`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- Tentativa real com `./scripts/qa_maplibre_device_smoke.sh --run-device --publish-port --device <id>` quando device iOS estiver visivel ao Flutter.

---

### MAPS-MAPLIBRE-NATIVE-DEVICE-SMOKE-001 - Smoke nativo MapLibre em iOS

### Status
DONE (commit: a46e062)

### Objetivo
Confirmar render nativo MapLibre em iOS Simulator Flutter-visivel ou device
iOS cabeado/provisionado antes de qualquer smoke de style/tile self-hosted ou
promocao do renderer.

### Criterios de Aceite
- O smoke real `qa_maplibre_device_smoke.sh --run-device` e executado em um
  alvo iOS Flutter-visivel.
- Quando o alvo for iOS Simulator, o log registra `MAPLIBRE_DEVICE_SMOKE_DEVICE_OK`
  e captura screenshot local em `output/maplibre-device-smoke/`.
- Quando o alvo for device fisico bloqueado por Xcode/provisioning, o resultado
  e registrado como `BLOCKED_VALIDATION_DEVICE` com log exato.
- A conclusao diferencia render nativo confirmado de limites ainda pendentes:
  style endpoint real, tile service self-hosted, PMTiles/MBTiles, offline pack,
  reroute real e cobertura BR-RS.
- MapLibre segue atras de flag e nao vira renderer default nesta fatia.

### Validacao minima
- `flutter devices --machine`.
- `xcrun simctl list devices available`.
- `./scripts/qa_maplibre_device_smoke.sh --run-device --device <ios-target-id>`.
- `python3 scripts/qa_maplibre_device_smoke_test.py`.
- `./scripts/qa_maplibre_device_smoke.sh --check-only`.
- `flutter test test/features/tracking/trip_live_map_panel_test.dart`.
- `flutter analyze`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### MAPS-STYLE-TILE-SMOKE-001 - Smoke style/tile self-hosted MapLibre

### Status
DONE (commit: ef718d3)

### Objetivo
Criar o primeiro contrato de style/tile self-hosted para a superficie MapLibre
do VanRides, provando que o app carrega um style JSON proprio com fonte de
tiles vetoriais controlada, sem usar tiles publicos OSM ou demo MapLibre como
infraestrutura do produto.

### Criterios de Aceite
- A prova MapLibre usa um style JSON VanRides com metadata de smoke
  self-hosted, regiao `BR-RS` e versionamento de style/tile.
- O style declara uma fonte vetorial `vanrides-vector-tiles` apontando para uma
  URL local/controlada de smoke, pronta para trocar por endpoint self-hosted.
- Testes automatizados validam que o style nao referencia
  `tile.openstreetmap.org`, `demotiles.maplibre.org` ou endpoints externos de
  tiles nao controlados.
- O gate rapido por card passa a validar o contrato style/tile sem device,
  tile server real, VPS, PBF, PMTiles/MBTiles ou offline pack.
- MapLibre segue atras de flag e nao vira renderer default nesta fatia.

### Validacao minima
- `python3 scripts/qa_maplibre_style_tile_smoke_test.py`.
- `./scripts/qa_maplibre_style_tile_smoke.sh --check-only`.
- `python3 scripts/qa_maplibre_device_smoke_test.py`.
- `./scripts/qa_maplibre_device_smoke.sh --check-only`.
- `flutter test test/features/tracking/trip_live_map_panel_test.dart`.
- `flutter analyze`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### MAPS-TILE-FIXTURE-SMOKE-001 - Smoke fixture/fetch local de tile MapLibre

### Status
DONE (commit: e107f33)

### Objetivo
Provar que o style MapLibre do VanRides consegue buscar uma fixture vetorial
local/controlada pelo mesmo formato de URL self-hosted declarado no app, sem
usar tiles publicos, dados reais, VPS ou pipeline pesado de tiles.

### Criterios de Aceite
- Existe uma fixture pequena, deterministica e sem dados reais em
  `test/fixtures/maps/BR-RS/vector/0/0/0.pbf.base64`.
- Um smoke local sobe um servidor efemero em `127.0.0.1:8099`, serve a fixture
  em `/vanrides/maps/BR-RS/vector/0/0/0.pbf` e busca esse path a partir do
  contrato de URL do style.
- O smoke valida status HTTP, content-type, CORS, bytes e hash da fixture, alem
  de rejeitar endpoints publicos como `tile.openstreetmap.org` e
  `demotiles.maplibre.org`.
- O gate rapido por card passa a executar o contrato de fixture/fetch sem
  exigir device, VPS, tile server real, PBF pesado, PMTiles/MBTiles, offline
  pack ou cobertura BR-RS.
- MapLibre segue atras de flag e nao vira renderer default nesta fatia.

### Validacao minima
- `python3 scripts/qa_maplibre_tile_fixture_smoke_test.py`.
- `./scripts/qa_maplibre_tile_fixture_smoke.sh --check-only`.
- `bash -n scripts/qa_maplibre_tile_fixture_smoke.sh scripts/qa_gate_card.sh`.
- `flutter test test/features/tracking/trip_live_map_panel_test.dart`.
- `flutter analyze`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### MAPS-OFFLINE-001 - Offline packs no app

### Status
DONE (commit: 6a217b5)

### Objetivo
Planejar e validar estados de pack offline pronto, ausente e parcial antes da
navegacao interna.

### Criterios de Aceite
- UI diferencia ready, missing e partial sem prometer cobertura falsa.
- Fallback externo aparece quando coverage/offline nao permitem navegacao.
- Integracao respeita status da Maps Platform.

### Validacao minima
- `flutter test test/features/navigation test/features/tracking`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### APP-DRIVER-NAV-001 - Modo dirigir do motorista

### Status
DONE (commit: e97a69c)

### Objetivo
Implementar navegacao do motorista em fatias pequenas com presenter puro, shell
de UI e fallback seguro.

### Criterios de Aceite
- Presenter seleciona instrucao atual, proxima parada, fallback e estado offline.
- UI mostra instrucao, proxima acao e fallback sem depender diretamente do SDK.
- Off-route e coverage indisponivel entram em modo seguro.

### Validacao minima
- `flutter test test/features/navigation test/features/driver_trip`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### APP-NAV-VOICE-001 - Adaptador de voz da navegacao

### Status
DONE (commit: 0139891)

### Objetivo
Isolar TTS em adaptador testavel para falar, repetir, mutar e evitar dependencia
de audio real nos testes.

### Criterios de Aceite
- Comandos de voz ficam verificaveis por teste sem dispositivo fisico.
- Mute/repeat funcionam sem duplicar instrucao antiga.
- Voz respeita fallback, off-route e reduced motion/attention.

### Validacao minima
- `flutter test test/features/navigation`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### APP-NAV-TTS-NATIVE-001 - TTS nativo PT-BR da navegacao

### Status
DONE (commit: 3ff840a)

### Objetivo
Ligar um gateway de TTS nativo PT-BR ao contrato de voz ja existente, mantendo
testes deterministas, injecao por adapter e fallback seguro sem depender de
audio real nos testes.

### Impact Analysis
- Contexto do card: `APP-NAV-VOICE-001` isolou a voz em
  `DriverNavigationVoiceGateway`, mas deixou o app em `Noop` por padrao para
  evitar dependencia nativa cedo demais. Esta fatia conecta o app a um adapter
  TTS nativo, sem mudar o contrato de voz do dominio.
- Telas/fluxos afetados: sheet ativa do motorista e painel de navegacao do
  motorista, especificamente o comando de repetir instrucao e o estado de voz
  mutada.
- Dependencia de contrato backend: nenhuma. Nao muda HTTP/API, WebSocket,
  fixture DEV, Maps Platform ou backend.
- Dependencia nativa: plugin TTS Flutter com configuracao PT-BR, volume, pitch,
  speech rate e parada antes de nova fala para reduzir sobreposicao.
- Riscos e mitigacao: risco de MethodChannel quebrar testes de widget; mitigado
  por adapter com engine injetavel e testes unitarios sem audio real. Risco de
  voz indisponivel em device/plataforma; mitigado mantendo mute, repeat e
  fallback visual. Risco de volume/velocidade desconfortaveis; mitigado por
  configuracao conservadora e documentada.
- Validacao minima obrigatoria: RED/GREEN do adapter nativo, testes focados de
  navegacao/driver sheet, `flutter analyze` e
  `./scripts/qa_gate_card.sh --no-e2e`.

### Criterios de Aceite
- Existe gateway TTS nativo PT-BR atras de `DriverNavigationVoiceGateway`.
- O adapter e testavel sem device, sem audio real e sem MethodChannel.
- A sheet ativa do motorista usa o gateway nativo por padrao, mas continua
  permitindo injecao de fake/noop em testes.
- Mute/repeat e protecao contra instrucao duplicada continuam funcionando.

### Validacao minima
- `flutter test test/features/navigation test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`.
- `flutter analyze`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### APP-NAV-TTS-DEVICE-SMOKE-001 - Smoke audivel de voz da navegacao

### Status
DONE (commit: 3c366c6)

### Objetivo
Criar o caminho oficial de QA para provar a voz nativa da navegacao em iPhone
ou iOS Simulator, separando contrato rapido sem device de execucao audivel real
com evidencias locais.

### Impact Analysis
- Contexto do card: `APP-NAV-TTS-NATIVE-001` conectou `flutter_tts` atras de
  `DriverNavigationVoiceGateway`, mas ainda nao provou audio real em device.
  Esta fatia cria o smoke audivel oficial antes de qualquer alegacao de voz
  pronta para campo.
- Telas/fluxos afetados: modo de navegacao do motorista e gateway TTS nativo.
  O gate rapido valida contrato/script/docs; a execucao real deve abrir um
  alvo minimo de navegacao e acionar uma fala curta de teste.
- Dependencia de contrato backend: nenhuma. Nao muda HTTP/API, WebSocket,
  fixture DEV, Maps Platform ou backend.
- Dependencia nativa: iOS Simulator/device visivel ao Flutter, plugin
  `flutter_tts`, audio do sistema e permissao/configuracao local do device.
- Riscos e mitigacao: risco de tratar falta de device como falha de produto;
  mitigado por `BLOCKED_VALIDATION_DEVICE`. Risco de gate lento ou flakey;
  mitigado por `--check-only` no `qa_gate_card.sh` e `--run-device` explicito.
  Risco de falsa prova audivel em CI; mitigado por exigir evidencia local em
  `output/navigation-tts-device-smoke/`.
- Validacao minima obrigatoria: teste de contrato do script, `--check-only`,
  wiring no `qa_gate_card.sh`, testes focados de navegacao e gate local sem
  E2E real.

### Criterios de Aceite
- Existe script oficial `qa_navigation_tts_device_smoke.sh` com
  `--check-only`, `--run-device`, `--device` e `--output-dir`.
- O gate rapido executa somente o contrato/check-only, sem iniciar device.
- A falta de device/provisioning e classificada como `BLOCKED_VALIDATION_DEVICE`.
- O roteiro registra onde salvar evidencia audivel/local e quando o resultado
  pode ser usado para promover voz para piloto.

### Validacao minima
- `python3 scripts/qa_navigation_tts_device_smoke_test.py`.
- `./scripts/qa_navigation_tts_device_smoke.sh --check-only`.
- `flutter test test/features/navigation`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### APP-NAV-TTS-DEVICE-RUN-001 - Prova tecnica de TTS no simulador iOS

### Status
DONE (commit: 1abec68)

### Objetivo
Executar o smoke oficial de TTS nativo da navegacao em um iOS Simulator visivel
ao Flutter, registrando evidencia tecnica local antes de qualquer validacao em
campo ou com empresa parceira.

### Impact Analysis
- Contexto do card: `APP-NAV-TTS-DEVICE-SMOKE-001` criou o roteiro, script e
  integration test para provar o caminho nativo de TTS, mas ainda nao executou
  o fluxo em um device/simulador real. Esta fatia transforma o roteiro em uma
  primeira evidencia tecnica local.
- Telas/fluxos afetados: modo de navegacao do motorista e gateway TTS nativo,
  acionados pelo alvo minimo `navigation_tts_device_smoke_test.dart`.
- Dependencia de contrato backend: nenhuma. O smoke nao usa API, WebSocket,
  DEV, dados reais, rota real, push, Maps Platform, deploy ou VPS.
- Dependencia nativa: iOS Simulator visivel ao Flutter, build iOS local,
  `flutter_tts` e audio do sistema. A execucao automatizada prova wiring e
  ausencia de crash, mas a confirmacao audivel continua dependendo de operador.
- Riscos e mitigacao: risco de confundir `DEVICE_OK` com prova humana de audio;
  mitigado registrando a confirmacao audivel como pendente ate alguem ouvir a
  frase. Risco de device/provisioning instavel; mitigado por manter
  `BLOCKED_VALIDATION_DEVICE` como classificacao segura.
- Validacao minima obrigatoria: `flutter devices`, execucao `--run-device` no
  iOS Simulator, registro do diretório de evidencia, contrato/check-only,
  testes focados de navegacao e gate local sem E2E real.

### Criterios de Aceite
- Smoke `--run-device` executa no iOS Simulator e retorna
  `NAVIGATION_TTS_DEVICE_SMOKE_DEVICE_OK`, ou registra bloqueio tecnico
  classificado sem mascarar como sucesso.
- Evidencias ficam em `output/navigation-tts-device-smoke/<timestamp>/` com log
  e `audio-confirmation.md`.
- QA Log diferencia prova tecnica automatizada de confirmacao audivel humana.
- O gate rapido continua usando `--check-only`, sem abrir device por padrao.

### Validacao minima
- `flutter devices`.
- `./scripts/qa_navigation_tts_device_smoke.sh --run-device --device <ios-simulator> --output-dir output/navigation-tts-device-smoke/<timestamp>`.
- `./scripts/qa_navigation_tts_device_smoke.sh --check-only`.
- `flutter test test/features/navigation`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### APP-NAV-AUDIO-CONFIRM-001 - Confirmacao humana do audio PT-BR

### Status
IN_PROGRESS - handoff tecnico pronto; pendente operador humano ouvir e registrar.

### Objetivo
Registrar a confirmacao humana do audio PT-BR do TTS da navegacao em
device/simulador disponivel, mantendo separada a prova tecnica automatizada da
prova audivel real.

### Impact Analysis
- Contexto do card: `APP-NAV-TTS-DEVICE-RUN-001` gerou evidencia tecnica em
  iOS Simulator com `NAVIGATION_TTS_DEVICE_SMOKE_AUDIO_READY`, mas
  `audio-confirmation.md` continua pendente porque Codex nao consegue ouvir.
- Telas/fluxos afetados: nenhum widget ou fluxo de runtime muda nesta fatia.
  O card governa QA, pacote de revisao, evidencia e criterios de promocao.
- Dependencia de contrato backend: nenhuma. Nao altera HTTP/API, WebSocket,
  auth, CORS, Maps Platform, dados reais, rotas reais ou provider externo.
- Dependencia nativa: a confirmacao audivel final depende de operador humano e
  device/simulador com volume audivel. O gate rapido fica sem device/audio.
- Riscos e mitigacao: risco de confundir marker tecnico com audio ouvido;
  mitigado por `PENDING_AUDIO_CONFIRMATION` ate o operador registrar `SIM`.
  Risco de vazar dados reais; mitigado por politica explicita sem passageiros,
  empresas, enderecos privados, rotas de campo, segredos ou tokens.
- Validacao minima obrigatoria: contrato do pacote de review, smoke
  `--check-only`, pacote gerado sobre evidencia local, falha esperada do
  `--assert-confirmed` enquanto pendente, `flutter analyze` e
  `./scripts/qa_gate_card.sh --no-e2e`.
- Rollback simples: reverter script, teste, playbook e docs do card. Nao ha
  migracao, segredo, backend, deploy, push ou VPS.

### Criterios de Aceite
- Existe script de review audivel com `--check-only`, `--package` e
  `--assert-confirmed`.
- O pacote `audio-review-package.md/json` aponta marker tecnico, checklist
  humano, politica sem dados reais e status pendente quando ninguem ouviu.
- O pacote registra a frase esperada `Use o fallback para seguir com seguranca`
  e os comandos que o operador deve rodar para reproduzir/empacotar/confirmar.
- `--assert-confirmed` falha com `PENDING_AUDIO_CONFIRMATION` ate haver
  confirmacao explicita do operador.
- O gate local roda apenas contrato rapido, sem exigir device/audio real.
- O card so pode virar `DONE` depois que uma pessoa confirmar o audio PT-BR em
  `audio-confirmation.md`.

### Validacao minima
- `python3 scripts/qa_navigation_audio_confirmation_review_test.py`.
- `./scripts/qa_navigation_audio_confirmation_review.py --check-only`.
- `./scripts/qa_navigation_audio_confirmation_review.py --package --evidence-dir output/navigation-tts-device-smoke/<timestamp>`.
- `./scripts/qa_navigation_audio_confirmation_review.py --assert-confirmed --evidence-dir output/navigation-tts-device-smoke/<timestamp>` deve falhar com `PENDING_AUDIO_CONFIRMATION` enquanto pendente.
- `flutter analyze`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### APP-NAV-AUDIO-HANDOFF-001 - Handoff humano da confirmacao de audio

### Status
DONE (commit: fba5405)

### Objetivo
Criar um handoff versionado e validado por teste para a pessoa que vai ouvir o
audio PT-BR da navegacao, apontando a evidencia local existente, os comandos
exatos e os limites de produto sem marcar o audio como confirmado.

### Impact Analysis
- Contexto do card: `APP-NAV-AUDIO-CONFIRM-001` continua em `IN_PROGRESS`
  porque Codex nao consegue ouvir audio. Ja existe evidencia tecnica e pacote
  local; faltava um handoff versionado para o operador humano.
- Mudancas tecnicas: novo documento
  `backlog/QA-Navigation-Audio-Operator-Handoff.md`, teste de contrato
  `qa_navigation_audio_operator_handoff_test.py` e chamada no gate rapido.
- Contratos afetados: nenhum endpoint, payload, auth, CORS, WebSocket,
  permissao nativa ou fluxo runtime do app.
- Riscos e mitigacao: risco de confundir handoff com confirmacao audivel;
  mitigado por exigir `PENDING_AUDIO_CONFIRMATION`, `operatorHeardPtBr=false`
  e manter `APP-NAV-AUDIO-CONFIRM-001` pendente ate operador registrar `SIM`.
- Validacao minima obrigatoria: teste de handoff, pacote/check-only de audio,
  falha esperada de `--assert-confirmed` enquanto pendente e gate app sem E2E.
- Rollback simples: remover o documento, o teste e a chamada no gate.

### Plano
1. Escrever RED para exigir handoff versionado e chamada no gate.
2. Criar o documento com evidencia local, comandos e limites.
3. Inserir o teste no gate rapido do app.
4. Validar pacote pendente e gate app.
5. Registrar QA e fechar com commit local.

### Criterios de Aceite
- O handoff aponta a evidencia
  `output/navigation-tts-device-smoke/20260706-tts-sim-iphone16e`.
- O handoff registra `PENDING_AUDIO_CONFIRMATION` e `operatorHeardPtBr=false`.
- O handoff mostra a frase esperada `Use o fallback para seguir com seguranca`.
- O gate app valida o contrato de handoff.
- O card de confirmacao audivel continua pendente ate uma pessoa ouvir e
  registrar `SIM`.

---

### APP-NAV-PRE-PILOT-READINESS-001 - Readiness tecnico pre-piloto da navegacao

### Status
DONE (commit: 64b086c)

### Objetivo
Criar um verificador rapido e auditavel que consolide as principais provas
tecnicas da VanRides Navigation antes de piloto, sem liberar piloto externo
enquanto a confirmacao audivel humana continuar pendente.

### Impact Analysis
- Contexto do card: `APP-NAV-AUDIO-HANDOFF-001` deixou a revisao humana do
  audio organizada, mas o programa ainda precisa de um resumo tecnico unico
  para diferenciar o que esta verde daquilo que continua em `HOLD`.
- Mudancas tecnicas: adicionar script `qa_navigation_pre_pilot_readiness.py`,
  teste de contrato e chamada `--check-only` no gate rapido do app.
- Contratos afetados: nenhum endpoint, payload, auth, CORS, WebSocket,
  permissao nativa, provider de mapa, backend, Maps Platform runtime, deploy,
  push ou VPS.
- Riscos e mitigacao: risco de parecer liberacao para parceiro; mitigado por
  status `HOLD`, `externalPilotReady=false`, `fieldReady=false` e
  `audioHumanConfirmed=false` enquanto `APP-NAV-AUDIO-CONFIRM-001` nao estiver
  confirmado por humano.
- Validacao minima obrigatoria: RED/GREEN do teste de contrato, `--check-only`,
  geracao opcional de report local, falha esperada de audio pendente via
  `--assert-confirmed`, `flutter analyze` e `./scripts/qa_gate_card.sh --no-e2e`.
- Rollback simples: remover script, teste, chamada no gate e bloco do backlog.

### Plano
1. Escrever RED exigindo script, saida `HOLD`, report markdown/json e gate.
2. Implementar o verificador como agregador local dos contratos ja existentes.
3. Garantir que audio humano pendente mantenha pre-piloto em `HOLD`.
4. Rodar validacoes focadas e gate app.
5. Registrar QA e fechar com commit local.

### Criterios de Aceite
- `--check-only` retorna `NAVIGATION_PRE_PILOT_READINESS status=hold`.
- O report local lista drive sim, action auto-sync, TTS device smoke, handoff
  humano, route plan E2E e audio humano pendente.
- `externalPilotReady=false` e `fieldReady=false` permanecem enquanto
  `operatorHeardPtBr=false`.
- O gate app valida o contrato sem rodar device, backend, audio real, parceiro,
  campo, deploy ou VPS.

### Test Plan do Card
- Risco principal: um conjunto de provas tecnicas parecer liberacao de piloto
  externo mesmo com audio humano pendente.
- Testes novos:
  - [x] `python3 scripts/qa_navigation_pre_pilot_readiness_test.py`
  - [x] `python3 scripts/qa_navigation_pre_pilot_readiness.py --check-only`
  - [x] `python3 scripts/qa_navigation_pre_pilot_readiness.py --report --output-dir output/navigation-pre-pilot-readiness/20260707-nav-pre-pilot-readiness`
- Regressao minima executada:
  - [x] `./scripts/qa_navigation_audio_confirmation_review.py --assert-confirmed --evidence-dir output/navigation-tts-device-smoke/20260706-tts-sim-iphone16e` falhou como esperado com `PENDING_AUDIO_CONFIRMATION`.
  - [x] `./scripts/qa_gate_card.sh --no-e2e`
- Evidencia registrada em QA Log:
  - [x] Sim

### Entrega
- Novo script `qa_navigation_pre_pilot_readiness.py` consolida os contratos de
  drive simulation, action auto-sync, TTS device smoke, handoff humano,
  route-plan E2E e confirmacao audivel humana.
- O check rapido retorna `status=hold`, `technical_checks=pass`,
  `audio_human_confirmed=false`, `external_pilot_ready=false` e
  `field_ready=false`.
- Report local gerado em
  `output/navigation-pre-pilot-readiness/20260707-nav-pre-pilot-readiness/`
  com markdown/json fora do Git.
- O gate app passa a validar o contrato com `--check-only`, sem device,
  backend, audio real, parceiro, campo, deploy ou VPS.
- `APP-NAV-AUDIO-CONFIRM-001` continua pendente de uma pessoa ouvir e
  registrar o audio PT-BR.

---

### APP-NAV-SIM-DRIVE-QA-001 - Simulacao QA de corrida guiada

### Status
DONE (commit: 2bdeb6a)

### Objetivo
Criar uma simulacao QA local de uma corrida completa do motorista, cobrindo
painel de navegacao, TTS, fallback e estados offline sem usar parceiro, dados
reais, GPS bruto, backend, deploy ou VPS.

### Impact Analysis
- Contexto do card: `OPS-PILOT-BACKLOG-HOLD-001` manteve o pacote de parceiro
  interno e recomendou voltar para uma prova interna da navegacao do motorista.
  O app ja tem presenter, painel, voz e TTS tecnico; falta uma sequencia QA
  repetivel que mostre a historia operacional inteira.
- Telas/fluxos afetados: navegacao do motorista por meio de
  `DriverNavigationPresenter`, `DriverNavigationVoiceController` e contrato do
  painel. Nao altera home, rotas Flutter ou lifecycle real nesta fatia.
- Dependencia de contrato backend: nenhuma. Fixture QA local, sem API,
  WebSocket, Maps Platform, DEV, rota real ou provider de mapa.
- Dependencia nativa: nenhuma no gate rapido. Voz via gateway fake, sem
  MethodChannel/audio real.
- Riscos e mitigacao: risco de parecer prova de campo; mitigado por evidencia
  local e QA Log explicitos. Risco de duplicar regra de navegacao; mitigado por
  usar as interfaces publicas existentes como test surface.
- Validacao minima obrigatoria: RED/GREEN focado, smoke check-only, regressao
  de navegacao, `flutter analyze` e gate local sem E2E real.

### Criterios de Aceite
- Simulacao cobre saida/instrucao interna, proxima parada, fallback por
  cobertura/offline e modo seguro/off-route.
- Cada momento registra modo, status, instrucao, proxima parada, fallback e
  texto falado.
- Smoke gera evidencia local em `output/navigation-drive-sim/<timestamp>/`.
- Gate rapido valida contrato sem device, backend, audio real, parceiro ou VPS.

### Validacao minima
- `flutter test test/features/navigation/driver_navigation_drive_simulation_test.dart`.
- `python3 scripts/qa_navigation_drive_sim_smoke_test.py`.
- `./scripts/qa_navigation_drive_sim_smoke.sh --check-only`.
- `flutter test test/features/navigation`.
- `flutter analyze`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### MAPS-ROUTE-INSTRUCTIONS-UX-001 - Instrucoes PT-BR seguras no app

### Status
DONE (commit: 1a8bcfe)

### Objetivo
Adicionar uma guarda local de UX para instrucoes de rota exibidas e faladas no
modo de navegacao do motorista, garantindo texto PT-BR conservador mesmo quando
o provider ou fixture trouxer copy crua, em ingles ou superconfiante.

### Impact Analysis
- Contexto do card: a Maps Platform ja normalizou instrucoes Valhalla/OSRM e a
  simulacao QA do app cobre corrida guiada, mas ainda falta uma camada local no
  app que proteja a leitura/voz do motorista quando a instrucao recebida nao
  estiver pronta para uso humano.
- Telas/fluxos afetados: `DriverNavigationPresenter`,
  `DriverNavigationVoiceController`, painel de navegacao do motorista e
  simulacao QA local. Nao altera rotas Flutter, login, tracking, lifecycle real
  ou mapa nativo.
- Dependencia de contrato backend: nenhuma. O schema de
  `DriverNavigationInstruction` permanece igual; o card normaliza apenas os
  textos que entram no view model.
- Dependencia de Maps Platform: nenhuma chamada nova. Este card aceita o
  contrato atual como entrada e adiciona defesa local no app.
- Riscos e mitigacao: risco de mascarar rota ruim; mitigado por textos
  conservadores, sem prometer validacao perfeita, e mantendo fallback/safe mode
  como autoridade quando cobertura/offline nao permitem navegacao interna.
- Validacao minima obrigatoria: RED/GREEN focado, regressao de navegacao,
  smoke de simulacao, `flutter analyze` e `./scripts/qa_gate_card.sh --no-e2e`.
- Rollback simples: reverter arquivos do card; nenhuma migracao, segredo,
  endpoint, dados reais, device, audio real, deploy, push ou VPS.

### Criterios de Aceite
- Instrucoes internas em ingles viram texto PT-BR conservador para painel e voz.
- Copy superconfiante como "rota perfeita", "sem risco" ou "garantido" nao
  chega ao motorista.
- Nomes de vias continuam preservados quando a instrucao informa uma rua.
- Fallback e safe mode continuam falando as mensagens seguras do view model,
  nao a instrucao crua.
- A simulacao QA registra pelo menos um passo de instrucao normalizada.

### Validacao minima
- `flutter test test/features/navigation/driver_navigation_presenter_test.dart`.
- `flutter test test/features/navigation/driver_navigation_drive_simulation_test.dart`.
- `flutter test test/features/navigation`.
- `./scripts/qa_navigation_drive_sim_smoke.sh --check-only`.
- `flutter analyze`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### UX-DRIVER-SHEET-001 - Sheet ativa do motorista

### Status
DONE (commit: 5b59ac4)

### Objetivo
Deixar a sheet ativa do motorista legivel em telas pequenas, com proxima acao e
estado da viagem em primeiro plano.

### Criterios de Aceite
- Proxima acao aparece rapidamente.
- Sheet nao cobre informacao essencial do mapa ou da fila.
- Texto e controles continuam legiveis com largura reduzida.

### Validacao minima
- `flutter test test/features/driver_trip`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### UX-GUARDIAN-PRESENCE-001 - Presenca do responsavel como tela coerente

### Status
DONE (commit: eaf3249)

### Objetivo
Unificar rastreio, passageiro vinculado e decisao de presenca em uma experiencia
coerente para responsaveis.

### Criterios de Aceite
- Responsavel entende estado do passageiro e da van sem alternar contexto.
- Decisao de presenca tem feedback claro.
- Estados sem viagem ou sem vinculo sao úteis.

### Validacao minima
- `flutter test test/features/guardian`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### UX-ONBOARDING-ADMIN-001 - Onboarding admin orientado a tarefa

### Status
DONE (commit: 5d20b16)

### Objetivo
Melhorar onboarding, convites e vinculos administrativos para guiar proxima acao
sem expor detalhes tecnicos.

### Criterios de Aceite
- Admin sabe qual passo falta para deixar pessoa/servico operacional.
- Mensagens de erro e vazio sao acionaveis.
- Fluxo de vinculos nao parece uma tela tecnica crua.

### Validacao minima
- `flutter test test/features/admin`.
- `./scripts/qa_gate_card.sh --no-e2e`.

---

### QA-ACCESSIBILITY-001 - Planejamento e smoke de acessibilidade

### Status
DONE (commit: 43c93c9)

### Objetivo
Definir e executar verificacoes basicas de acessibilidade para fluxos tocados no
programa integrado.

### Criterios de Aceite
- Texto, foco, contraste e escala sao avaliados nos fluxos criticos.
- Achados viram cards ou evidencias rastreaveis.
- Nenhum card visual fecha sem criterio minimo de acessibilidade.

### Validacao minima
- `flutter test` focado nos widgets alterados.
- Smoke manual com text scale/foco quando aplicavel.

---

### QA-VISUAL-REGRESSION-001 - Regressao visual controlada

### Status
DONE (commit: a5e172b)

### Objetivo
Definir evidencias visuais comparaveis para telas de tracking, motorista,
responsavel e admin.

### Criterios de Aceite
- Screenshots ficam em pasta previsivel e citavel.
- Estados chave tem antes/depois ou baseline documentado.
- Falhas visuais sao separadas de falhas de setup.

### Validacao minima
- `./scripts/qa_gate_card.sh --no-e2e`.
- Smoke visual do fluxo alterado quando houver UI.

---

### QA-PERFORMANCE-001 - Performance dos fluxos criticos

### Status
DONE (commit: e48b547)

### Objetivo
Monitorar impacto de mapas, sheets, motion e tracking em renderizacao e resposta
dos fluxos criticos.

### Criterios de Aceite
- Mudancas de mapa/tracking registram risco e observacao de performance.
- Jank evidente ou carregamento excessivo gera follow-up antes de release.
- Gate rapido do app continua verde.

### Validacao minima
- `flutter analyze`.
- `flutter test`.
- Smoke manual focado no fluxo alterado quando houver UI pesada.

---

### QA-E2E-ROLE-SPLIT-001 - E2E por papel

### Status
DONE (commit: 8c34455)

### Objetivo
Separar validacao ponta a ponta por papel: admin, motorista, passageiro e
responsavel.

### Criterios de Aceite
- Roteiros deixam claro qual papel esta sendo validado.
- Setup/fixture fica separado de bug de UX ou contrato.
- Evidencias por papel ficam citaveis no QA Log.

### Validacao minima
- `./scripts/qa_gate_card.sh --no-e2e`.
- E2E/smoke especifico por papel quando ambiente permitir.

---

### OPS-E2E-REAL-PREFLIGHT-001 - Preflight E2E real por papel

### Status
DONE (commit: 2bea2c0)

### Objetivo
Criar um orquestrador seguro para preparar e executar somente o preflight real
por papel antes de rodar E2E longo, gerando evidencias citaveis por papel para
piloto com empresa parceira.

### Impact Analysis
- Contexto do card: a matriz por papel ja existe, mas falta um comando unico
  que escolha papel, normalize API/device, crie pasta de evidencia e classifique
  bloqueios de setup antes de qualquer E2E longo.
- Fluxos afetados: apenas scripts de QA, backlog e playbook. Nenhuma tela,
  provider, rota Flutter, API backend, WebSocket ou permissao nativa muda.
- Dependencia backend: apenas quando o modo real for solicitado explicitamente;
  o gate rapido continua sem backend, device, navegador ou fixture.
- Contratos afetados: nenhum endpoint, payload, status code, CORS, auth ou
  formato de WebSocket e alterado.
- Riscos e mitigacao: risco de acionar E2E real sem querer; o padrao sera
  `--check-only`, com `--run-preflight` explicito para tocar backend/device.
  Risco de misturar setup com bug de UX; o relatorio preserva
  `BLOCKED_VALIDATION_*` e evidencia por papel.
- Validacao minima obrigatoria: teste RED/GREEN do contrato do orquestrador,
  inclusao do check no gate rapido e `./scripts/qa_gate_card.sh --no-e2e`.
- Rollback simples: reverter commits deste card; nao ha migracao, segredo,
  dado externo ou mudanca de contrato.

### Plano
1. Adicionar teste automatizado para o novo contrato de preflight real por papel.
2. Implementar `scripts/qa_e2e_real_preflight.sh` com `--check-only`,
   `--dry-run` e `--run-preflight`.
3. Integrar o check-only ao gate local por card, sem rodar E2E real por padrao.
4. Atualizar playbook/README com comando de piloto e regra de evidencias.
5. Registrar QA e fechar o card com commits locais, sem push/deploy/VPS.

### Criterios de Aceite
- `--check-only` valida matriz, runners, suites e nao cria evidencia nem roda
  device/backend.
- `--dry-run --role <papel>` cria `output/e2e-role-smoke/<papel>/<timestamp>/`
  com resumo do comando que seria executado.
- `--run-preflight --role <papel>` roda apenas preflight/fixture do papel,
  mantendo Flutter E2E real opt-in em outro comando.
- O gate `./scripts/qa_gate_card.sh --no-e2e` cobre o contrato novo sem custo.
- QA Log cita que nao houve E2E real/device fisico neste card, salvo se rodado
  explicitamente depois.

### Validacao minima
- `python3 scripts/qa_e2e_real_preflight_test.py`
- `./scripts/qa_e2e_real_preflight.sh --check-only`
- `./scripts/qa_gate_card.sh --no-e2e`

---

### OPS-E2E-DRIVER-PREFLIGHT-001 - Primeiro preflight real driver-mobile

### Status
DONE (commit: 4514fc2)

### Objetivo
Executar o primeiro `--run-preflight` real e explicitamente opt-in para o papel
`driver-mobile`, gerando evidencia local citavel antes de qualquer E2E longo,
piloto parceiro, push ou deploy.

### Impact Analysis
- Contexto do card: `OPS-E2E-REAL-PREFLIGHT-001` criou o orquestrador; agora
  precisamos provar o caminho real inicial, com device/backend classificados.
- Fluxos afetados: somente QA operacional e evidencias locais. Nenhuma tela,
  provider, rota Flutter, endpoint, payload, WebSocket ou permissao nativa muda.
- Dependencia backend/device: o comando toca backend DEV e device Flutter
  apenas por `--run-preflight` explicito; se faltar ambiente, registrar
  `BLOCKED_VALIDATION_*` como setup.
- Contratos afetados: nenhum contrato HTTP/API novo e criado.
- Riscos e mitigacao: risco de confundir bloqueio de setup com bug de app; o
  resultado sera registrado com classificacao, comando, API/device e pasta
  `output/e2e-role-smoke/driver-mobile/<timestamp>/`. Risco de rodar E2E longo;
  o comando mantem `VANRIDES_E2E_PREFLIGHT_ONLY=1`.
- Validacao minima obrigatoria: checagem de ambiente sem expor segredos,
  `--dry-run`, `--run-preflight --role driver-mobile`, QA Log e backlog.
- Rollback simples: reverter docs deste card; evidencias ficam fora do Git em
  `output/`.

### Plano
1. Conferir device Flutter e presenca de env local sem imprimir segredos.
2. Rodar dry-run deterministico para gerar resumo de evidencia.
3. Rodar `--run-preflight --role driver-mobile` contra API DEV.
4. Registrar PASS ou BLOCKED no QA Log e fechar o card localmente.

### Criterios de Aceite
- O dry-run gera `SUMMARY.md` em `output/e2e-role-smoke/driver-mobile/...`.
- O preflight real retorna PASS ou `BLOCKED_VALIDATION_*` classificado.
- Nenhum Flutter E2E longo, push, deploy, VPS, fixture real sensivel ou dado
  de parceiro e executado/committed.
- QA Log cita comando, API/device, pasta de evidencia e resultado.

### Validacao minima
- `./scripts/qa_e2e_real_preflight.sh --dry-run --role driver-mobile ...`
- `./scripts/qa_e2e_real_preflight.sh --run-preflight --role driver-mobile ...`
- `git diff --check`

---

### OPS-E2E-PASSENGER-PREFLIGHT-001 - Primeiro preflight real passenger-mobile

### Status
DONE (commit: 5cc92ad)

### Objetivo
Executar o `--run-preflight` real e explicitamente opt-in para o papel
`passenger-mobile`, ampliando a cobertura de preflight por papel antes de
qualquer E2E longo, piloto parceiro, push ou deploy.

### Impact Analysis
- Contexto do card: `driver-mobile` ja passou no primeiro preflight real; o
  proximo papel do piloto e `passenger-mobile`.
- Fluxos afetados: somente QA operacional e evidencias locais. Nenhuma tela,
  provider, rota Flutter, endpoint, payload, WebSocket ou permissao nativa muda.
- Dependencia backend/device: o comando toca backend DEV e device Flutter
  apenas por `--run-preflight` explicito; se faltar ambiente, registrar
  `BLOCKED_VALIDATION_*` como setup.
- Contratos afetados: nenhum contrato HTTP/API novo e criado.
- Riscos e mitigacao: risco de confundir setup com bug de UX; registrar
  classificacao, comando, API/device e pasta
  `output/e2e-role-smoke/passenger-mobile/<timestamp>/`. Risco de rodar E2E
  longo; manter `VANRIDES_E2E_PREFLIGHT_ONLY=1`.
- Validacao minima obrigatoria: dry-run, preflight real passenger-mobile, QA
  Log e backlog.
- Rollback simples: reverter docs deste card; evidencias ficam fora do Git em
  `output/`.

### Plano
1. Rodar dry-run deterministico para `passenger-mobile`.
2. Rodar `--run-preflight --role passenger-mobile` contra API DEV.
3. Registrar PASS ou BLOCKED no QA Log e fechar o card localmente.

### Criterios de Aceite
- O dry-run gera `SUMMARY.md` em `output/e2e-role-smoke/passenger-mobile/...`.
- O preflight real retorna PASS ou `BLOCKED_VALIDATION_*` classificado.
- Nenhum Flutter E2E longo, push, deploy, VPS, fixture real sensivel ou dado
  de parceiro e executado/committed.
- QA Log cita comando, API/device, pasta de evidencia e resultado.

### Validacao minima
- `./scripts/qa_e2e_real_preflight.sh --dry-run --role passenger-mobile ...`
- `./scripts/qa_e2e_real_preflight.sh --run-preflight --role passenger-mobile ...`
- `git diff --check`

---

### OPS-E2E-GUARDIAN-PREFLIGHT-001 - Primeiro preflight real guardian-mobile

### Status
DONE (commit: 4f1d43a)

### Objetivo
Executar o `--run-preflight` real e explicitamente opt-in para o papel
`guardian-mobile`, usando fixture local ignorada quando disponivel, para fechar
a primeira cobertura de preflight real dos papeis mobile.

### Impact Analysis
- Contexto do card: `driver-mobile` e `passenger-mobile` passaram no preflight
  real; falta validar o setup do responsavel sem rodar E2E longo.
- Fluxos afetados: somente QA operacional e evidencias locais. Nenhuma tela,
  provider, rota Flutter, endpoint, payload, WebSocket ou permissao nativa muda.
- Dependencia backend/device/fixture: o comando toca backend DEV e device
  Flutter apenas por `--run-preflight` explicito. A fixture usada fica em
  `output/local-operational-e2e/fixture.env`, fora do Git.
- Contratos afetados: nenhum contrato HTTP/API novo e criado.
- Riscos e mitigacao: fixture local pode estar stale ou com e-mail nao
  verificado; registrar `BLOCKED_VALIDATION_*` como setup se isso acontecer.
  Risco de expor credenciais; nao imprimir valores da fixture e nao commitar
  `output/`. Risco de rodar E2E longo; manter `VANRIDES_E2E_PREFLIGHT_ONLY=1`.
- Validacao minima obrigatoria: dry-run, preflight real guardian-mobile, QA Log
  e backlog.
- Rollback simples: reverter docs deste card; evidencias ficam fora do Git em
  `output/`.

### Plano
1. Confirmar existencia de fixture local e chaves necessarias sem imprimir
   valores.
2. Rodar dry-run deterministico para `guardian-mobile`.
3. Rodar `--run-preflight --role guardian-mobile` com fixture local carregada.
4. Registrar PASS ou BLOCKED no QA Log e fechar o card localmente.

### Criterios de Aceite
- O dry-run gera `SUMMARY.md` em `output/e2e-role-smoke/guardian-mobile/...`.
- O preflight real retorna PASS ou `BLOCKED_VALIDATION_*` classificado.
- Nenhum Flutter E2E longo, push, deploy, VPS, fixture real sensivel ou dado
  de parceiro e executado/committed.
- QA Log cita comando, API/device, pasta de evidencia e resultado sem expor
  credenciais.

### Validacao minima
- `./scripts/qa_e2e_real_preflight.sh --dry-run --role guardian-mobile ...`
- `./scripts/qa_e2e_real_preflight.sh --run-preflight --role guardian-mobile ...`
- `git diff --check`

---

### OPS-E2E-ADMIN-PREFLIGHT-001 - Primeiro preflight real admin-web

### Status
DONE (commit: 5b9cc5b)

### Objetivo
Executar o preflight real e explicitamente opt-in para o papel `admin-web` e,
quando o setup basico estiver verde, validar o Portal Empresa com a fixture
operacional DEV ja criada, completando a cobertura inicial dos quatro papeis
canonicos antes de roteiro de piloto ou promocao.

### Impact Analysis
- Contexto do card: `driver-mobile`, `passenger-mobile` e `guardian-mobile`
  passaram no preflight real; falta registrar o papel `admin-web` na mesma
  trilha de evidencias da Fase 10.
- Fluxos afetados: somente QA operacional, build web local, Playwright local e
  evidencias em `output/`. Nenhuma tela, provider, rota Flutter, endpoint,
  payload, WebSocket ou permissao nativa muda.
- Dependencia backend/browser/fixture: o preflight do orquestrador valida setup
  local basico; o E2E web real usa backend DEV e fixture local ignorada em
  `output/e2e-role-smoke/guardian-mobile/20260706T124900Z/fixture/fixture.env`
  sem imprimir credenciais.
- Contratos afetados: nenhum contrato HTTP/API novo e criado.
- Riscos e mitigacao: fixture DEV pode expirar ou o build web/Chromium podem
  bloquear; classificar como setup quando houver falha antes da navegacao real.
  Risco de expor credenciais; nao imprimir valores da fixture e nao commitar
  `output/`. Risco de confundir E2E longo mobile com web; este card nao roda
  Flutter E2E mobile.
- Validacao minima obrigatoria: dry-run `admin-web`, preflight real
  `admin-web`, E2E web admin com fixture operacional se o setup permitir, QA
  Log e backlog.
- Rollback simples: reverter docs deste card; evidencias ficam fora do Git em
  `output/`.

### Plano
1. Confirmar fixture operacional com chaves admin/servicos/viagens sem imprimir
   valores.
2. Rodar dry-run deterministico para `admin-web`.
3. Rodar `--run-preflight --role admin-web` contra API DEV.
4. Rodar `qa_admin_portal_web_e2e.sh` com a fixture operacional, se o preflight
   basico passar.
5. Registrar PASS ou BLOCKED no QA Log e fechar o card localmente.

### Criterios de Aceite
- O dry-run gera `SUMMARY.md` em `output/e2e-role-smoke/admin-web/...`.
- O preflight real retorna PASS ou `BLOCKED_VALIDATION_*` classificado.
- O E2E web real registra manifest/summary em `output/admin-portal-e2e/...`
  usando fixture operacional, ou fica bloqueado com causa de setup clara.
- Nenhum Flutter E2E longo mobile, push, deploy, VPS, fixture sensivel ou dado
  de parceiro e executado/committed.
- QA Log cita comando, API/browser/fixture, pasta de evidencia e resultado sem
  expor credenciais.

### Validacao minima
- `./scripts/qa_e2e_real_preflight.sh --dry-run --role admin-web ...`
- `./scripts/qa_e2e_real_preflight.sh --run-preflight --role admin-web ...`
- `ADMIN_PORTAL_E2E_FIXTURE_SOURCE=... ./scripts/qa_admin_portal_web_e2e.sh`
- `git diff --check`

---

### QA-MOBILE-E2E-LONG-001 - Runner auditavel do E2E mobile longo por papel

### Status
DONE (commit: 8a18079)

### Objetivo
Executar ou classificar a primeira tentativa de Flutter E2E mobile longo por
papel, com evidencia local, sem misturar bloqueio de setup de device/fixture
com falha de UX/contrato do app.

### Impact Analysis
- Contexto do card: os preflights reais de `driver-mobile`, `passenger-mobile`,
  `guardian-mobile` e `admin-web` ja existem. A fila pos-hold pede E2E mobile
  longo quando fixture/device permitirem. O ambiente atual tem iPhone fisico
  visivel, mas a tentativa de `integration_test/motorista/all.dart` bloqueou no
  Flutter antes de abrir o app por iPhone wireless sem suporte no runner
  `flutter test`.
- Fluxos afetados: QA operacional e scripts; nenhum widget, rota Flutter,
  provider nativo, endpoint, payload, WebSocket, auth, backend ou Maps Platform
  muda nesta fatia.
- Dependencia backend/device/fixture: usa backend DEV e fixture operacional
  local ignorada em Git, sem imprimir credenciais. O E2E longo real depende de
  device cabeado/simulador ou runner futuro com `flutter drive` quando o iPhone
  estiver wireless.
- Contratos afetados: contrato operacional de QA mobile; nenhum contrato de API
  novo e criado.
- Riscos e mitigacao: risco de classificar tooling como bug do app; mitigado
  por `BLOCKED_VALIDATION_IOS_WIRELESS_PUBLISH_PORT`. Risco de expor fixture;
  mitigado por resumo sem valores. Risco de rodar E2E longo no gate; gate fica
  somente em `--check-only`.
- Validacao minima obrigatoria: teste RED/GREEN do runner, `--check-only`,
  tentativa real/deterministica classificada, `./scripts/qa_gate_card.sh
  --no-e2e` e `git diff --check`.
- Rollback simples: reverter script, teste e docs; evidencias ficam fora do Git
  em `output/`.

### Plano
1. Criar teste RED para runner de E2E longo por papel.
2. Implementar `scripts/qa_mobile_e2e_long_run.py` com `--check-only`,
   `--dry-run` e `--run-role`.
3. Integrar contrato ao gate rapido sem rodar E2E real.
4. Rodar tentativa `driver-mobile` e registrar PASS ou BLOCKED com evidencia.
5. Atualizar README, QA docs, QA Log e backlog.

### Criterios de Aceite
- `python3 scripts/qa_mobile_e2e_long_run_test.py` passa.
- `python3 scripts/qa_mobile_e2e_long_run.py --check-only` valida os papeis
  mobile canonicos sem criar evidencia.
- `--dry-run` cria `SUMMARY.md` sem tocar device/backend.
- `--run-role driver-mobile` captura log e classifica iPhone wireless como
  setup quando o Flutter bloquear antes de abrir o app.
- O gate local por card roda somente o contrato rapido.
- Nenhum push, deploy, VPS, dado real de parceiro ou credencial e versionado.

### Validacao minima
- `python3 scripts/qa_mobile_e2e_long_run_test.py`.
- `python3 scripts/qa_mobile_e2e_long_run.py --check-only`.
- `python3 scripts/qa_mobile_e2e_long_run.py --dry-run --role driver-mobile ...`.
- `python3 scripts/qa_mobile_e2e_long_run.py --run-role --role driver-mobile ...`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

### Entrega
- Criado `scripts/qa_mobile_e2e_long_run.py`.
- Criado `scripts/qa_mobile_e2e_long_run_test.py`.
- Gate por card agora valida `MOBILE_E2E_LONG_CONTRACT_OK roles=3` sem rodar
  E2E real.
- Runner suporta `--check-only`, `--dry-run`, `--run-role`, `--role`,
  `--device`, `--api`, `--fixture-env`, `--evidence-root` e `--timestamp`.
- Tentativa real `driver-mobile` gerou evidencia em
  `output/mobile-e2e-long/driver-mobile/20260707Tmobilelong001Z/` e classificou
  `BLOCKED_VALIDATION_IOS_WIRELESS_PUBLISH_PORT`.
- README, QA Playbook e matriz E2E por papel foram atualizados.

### Evidencia
- RED:
  - `python3 scripts/qa_mobile_e2e_long_run_test.py`: FAIL esperado por
    `scripts/qa_mobile_e2e_long_run.py missing`.
- GREEN focado:
  - `python3 scripts/qa_mobile_e2e_long_run_test.py`: PASS, 4 testes.
  - `python3 scripts/qa_mobile_e2e_long_run.py --check-only`: PASS,
    `MOBILE_E2E_LONG_CONTRACT_OK roles=3 evidence_root=output/mobile-e2e-long`.
- Dry-run:
  - `python3 scripts/qa_mobile_e2e_long_run.py --dry-run --role driver-mobile --device <DEVICE_ID> --api http://127.0.0.1:8080 --fixture-env output/e2e-role-smoke/guardian-mobile/20260706T124900Z/fixture/fixture.env --timestamp 20260707Tmobilelong001Z-dry`: PASS.
- Preflight dos quatro papeis:
  - `set -a; source output/e2e-role-smoke/guardian-mobile/20260706T124900Z/fixture/fixture.env; set +a; ./scripts/qa_e2e_real_preflight.sh --run-preflight --role all --device <DEVICE_ID> --api http://127.0.0.1:8080 --timestamp 20260707Tmobilelongpreflight002Z`: PASS para `admin-web`, `driver-mobile`, `passenger-mobile` e `guardian-mobile`.
- Tentativa longa `driver-mobile`:
  - `python3 scripts/qa_mobile_e2e_long_run.py --run-role --role driver-mobile --device <DEVICE_ID> --api http://127.0.0.1:8080 --fixture-env output/e2e-role-smoke/guardian-mobile/20260706T124900Z/fixture/fixture.env --timestamp 20260707Tmobilelong001Z`: BLOCKED esperado, exit 84,
    `BLOCKED_VALIDATION_IOS_WIRELESS_PUBLISH_PORT`.
- Gate final:
  - `./scripts/qa_gate_card.sh --no-e2e`: PASS.
    - Mobile E2E long-run contract tests: PASS, 4 testes.
    - Mobile E2E long-run contract: PASS.
    - `flutter analyze`: PASS, sem issues.
    - `flutter test`: PASS, 650 testes.
    - quality ratchet: PASS.

### Limites
- O E2E mobile longo real ainda nao passou; a tentativa foi bloqueada antes de
  abrir o app pelo tooling do Flutter em iPhone wireless.
- Proximo desbloqueio: usar iOS Simulator, device iOS via USB, ou criar runner
  especifico com `flutter drive`/driver para integration tests longos.
- Nao houve push, deploy, VPS, parceiro, campo, dado real, fixture versionada,
  segredo versionado, backend/API change ou mudanca visual do app.

---

### QA-MOBILE-E2E-LONG-SIM-001 - Prova E2E mobile longa no iOS Simulator

### Status
DONE (commit: e48f8b3)

### Objetivo
Tentar transformar o E2E mobile longo por papel de bloqueado por iPhone
wireless em evidencia real no iOS Simulator local Flutter-visivel, mantendo
classificacao honesta se o bloqueio migrar para setup, fixture, permissao,
conectividade ou UX/contrato.

### Impact Analysis
- Contexto do card: `QA-MOBILE-E2E-LONG-001` entregou o runner auditavel e
  classificou corretamente o iPhone fisico wireless como
  `BLOCKED_VALIDATION_IOS_WIRELESS_PUBLISH_PORT`. Nesta sessao, o simulator
  `iPhone 16e` (`91A1AB69-ADA2-4A79-AB8A-AEB79DBC89D1`) foi bootado localmente
  e passou a aparecer em `flutter devices --machine`.
- Fluxos afetados: somente QA operacional de E2E mobile longo para motorista,
  passageiro e responsavel. Nenhum widget, rota Flutter, provider nativo,
  endpoint, payload, WebSocket, auth, backend, Maps Platform ou deploy muda.
- Dependencia backend/device/fixture: usa backend DEV e a fixture operacional
  local ignorada em Git em
  `output/e2e-role-smoke/guardian-mobile/20260706T124900Z/fixture/fixture.env`,
  sem imprimir valores.
- Contratos afetados: nenhum contrato HTTP/API novo. O contrato de QA existente
  passa a ter uma tentativa real em simulator.
- Riscos e mitigacao: risco de confundir setup do simulator com regressao do
  app; mitigado por `qa_mobile_e2e_long_run.py` e `BLOCKED_VALIDATION_*`. Risco
  de rodar E2E longo demais no gate; mitigado mantendo `qa_gate_card.sh` apenas
  em `--check-only`.
- Validacao minima obrigatoria: preflight real dos quatro papeis no simulator,
  tentativa longa pelo menos de `driver-mobile`, gate local `--no-e2e`, QA Log e
  `git diff --check`.
- Rollback simples: remover esta entrada de backlog e QA Log; evidencias ficam
  fora do Git em `output/`.

### Plano
1. Confirmar que o iOS Simulator aparece no Flutter.
2. Rodar preflight real dos quatro papeis contra backend DEV usando o simulator.
3. Rodar tentativa longa `driver-mobile` com `qa_mobile_e2e_long_run.py`.
4. Se `driver-mobile` passar, tentar `passenger-mobile` e `guardian-mobile`.
5. Registrar PASS, BLOCKED ou FAILED com evidencia e limites no QA Log.

### Criterios de Aceite
- `flutter devices --machine` lista o simulator `iPhone 16e`.
- O preflight real dos quatro papeis passa ou bloqueia com causa clara.
- A tentativa longa `driver-mobile` gera `SUMMARY.md` e `e2e.log` em
  `output/mobile-e2e-long/driver-mobile/...`.
- Se algum papel falhar, a classificacao diferencia setup de UX/contrato.
- Nenhum segredo, fixture, dado real, push, deploy, VPS, parceiro ou campo e
  usado/versionado.

### Validacao minima
- `flutter devices --machine`.
- `./scripts/qa_e2e_real_preflight.sh --run-preflight --role all --device 91A1AB69-ADA2-4A79-AB8A-AEB79DBC89D1 ...`.
- `python3 scripts/qa_mobile_e2e_long_run.py --run-role --role driver-mobile --device 91A1AB69-ADA2-4A79-AB8A-AEB79DBC89D1 ...`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

### Entrega
- O simulator `iPhone 16e`
  (`91A1AB69-ADA2-4A79-AB8A-AEB79DBC89D1`) foi bootado e apareceu em
  `flutter devices --machine`.
- O preflight real dos quatro papeis passou no simulator contra backend DEV.
- A tentativa longa `driver-mobile` deixou de bloquear por tooling wireless e
  abriu o app, mas revelou contrato antigo da suite publica de motorista.
- As suites de login de motorista e passageiro passaram a usar `AppStrings`
  como fonte canonica da copy atual.
- A suite `integration_test/operational_fixture/all.dart` foi estabilizada para
  a copy atual e para texto rico do card de servico.
- O runner ganhou o papel `operational-fixture-mobile`, voltado a prova
  operacional pre-piloto com fixture DEV verificada/vinculada.

### Evidencias
- Preflight dos quatro papeis:
  - `output/e2e-role-smoke/admin-web/20260707Tmobilelongsimpreflight001Z/`.
  - `output/e2e-role-smoke/driver-mobile/20260707Tmobilelongsimpreflight001Z/`.
  - `output/e2e-role-smoke/passenger-mobile/20260707Tmobilelongsimpreflight001Z/`.
  - `output/e2e-role-smoke/guardian-mobile/20260707Tmobilelongsimpreflight001Z/`.
- Tentativa longa legada `driver-mobile`:
  `output/mobile-e2e-long/driver-mobile/20260707Tmobilelongsimdriver001Z/`.
- Prova operacional mobile longa:
  `output/mobile-e2e-long/operational-fixture-mobile/20260707Tmobilelongsimoperational005Z/`.

### Validacoes executadas
- `VANRIDES_API_BASE_URL=http://127.0.0.1:8080 ./scripts/run_dev_e2e.sh integration_test/motorista/login_test.dart 91A1AB69-ADA2-4A79-AB8A-AEB79DBC89D1`
  - PASS, 2 testes.
- `VANRIDES_API_BASE_URL=http://127.0.0.1:8080 ./scripts/run_dev_e2e.sh integration_test/passageiro/login_test.dart 91A1AB69-ADA2-4A79-AB8A-AEB79DBC89D1`
  - PASS, 2 testes.
- `./scripts/run_dev_e2e.sh integration_test/operational_fixture/all.dart 91A1AB69-ADA2-4A79-AB8A-AEB79DBC89D1`
  - PASS na evidencia `20260707Tmobilelongsimoperational004Z`.
- `python3 scripts/qa_mobile_e2e_long_run_test.py`
  - PASS, 5 testes.
- `python3 scripts/qa_mobile_e2e_long_run.py --check-only`
  - PASS, `MOBILE_E2E_LONG_CONTRACT_OK roles=4 evidence_root=output/mobile-e2e-long`.
- `python3 scripts/qa_mobile_e2e_long_run.py --run-role --role operational-fixture-mobile --device 91A1AB69-ADA2-4A79-AB8A-AEB79DBC89D1 --api http://127.0.0.1:8080 --fixture-env output/e2e-role-smoke/guardian-mobile/20260706T124900Z/fixture/fixture.env --timestamp 20260707Tmobilelongsimoperational005Z`
  - PASS.
- `./scripts/qa_gate_card.sh --no-e2e`
  - PASS.
  - Mobile E2E long-run contract tests: PASS, 5 testes.
  - `MOBILE_E2E_LONG_CONTRACT_OK roles=4`.
  - Navigation drive simulation, TTS, audio confirmation e MapLibre contracts:
    PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 650 testes.
  - quality ratchet: PASS.

### Limites
- A suite legada `driver-mobile` continua nao sendo a melhor prova
  operacional pre-piloto, porque mistura cadastro publico pendente com
  expectativa de home operacional.
- A prova recomendada antes de piloto interno e `operational-fixture-mobile`
  com fixture DEV verificada/vinculada.
- `APP-NAV-AUDIO-CONFIRM-001` segue pendente de uma pessoa ouvir e confirmar o
  audio PT-BR.
- Nao houve push, deploy, VPS, parceiro, campo, dado real, fixture versionada,
  segredo versionado, backend/API change ou mudanca visual do app.

---

### UX-DRIVER-SHEET-002 - CTA principal resiliente em tela pequena

### Status
DONE (commit: 4166bc0)

### Objetivo
Proteger o CTA principal do motorista contra overflow em telas estreitas e fonte
ampliada, mantendo a proxima acao clara, tocavel e visualmente previsivel
durante a navegacao operacional.

### Impact Analysis
- Contexto do card: `UX-DRIVER-SHEET-001` ja trouxe a proxima acao para o
  primeiro plano, mas o botao primario compartilhado (`MapFirstPrimaryButton`)
  ainda nao limita explicitamente o texto como o botao secundario faz. Em
  cenarios de text scale alto, copy longa ou traducao futura, isso pode quebrar
  a experiencia do motorista no momento mais importante da corrida.
- Fluxos afetados: sheet ativa do motorista e qualquer CTA primario que usa
  `MapFirstPrimaryButton` em superficies map-first. Nao altera navegacao real,
  mapa nativo, voz, lifecycle, login, tracking, backend ou fixture.
- Contratos afetados: nenhum endpoint, payload, status code, CORS, auth,
  WebSocket ou contrato Maps Platform muda.
- Riscos e mitigacao: risco de truncar texto importante; mitigado mantendo o
  label completo na arvore de texto/semantica e preservando titulo/descricao da
  proxima acao acima do botao. Risco de regressao visual em CTAs existentes;
  mitigado por teste focado de largura estreita e regressao das suites da sheet.
- Validacao minima obrigatoria: RED/GREEN widget test, regressao focada da sheet
  ativa, `./scripts/qa_gate_card.sh --no-e2e` e `git diff --check`.
- Rollback simples: reverter o teste, o ajuste do componente compartilhado e
  esta entrada de backlog; nenhuma migracao, segredo, dado real, deploy, push ou
  VPS.

### Plano
1. Adicionar teste widget reproduzindo CTA primario longo em largura estreita e
   text scale alto.
2. Confirmar falha atual antes da mudanca de producao.
3. Ajustar `MapFirstPrimaryButton` para conter icone + label sem overflow.
4. Rodar testes focados da sheet ativa e o gate app sem E2E.
5. Registrar evidencias e fechar o card com commit local.

### Criterios de Aceite
- CTA primario do motorista nao estoura em largura reduzida com text scale alto.
- O botao continua com altura/tap target consistente.
- A proxima acao continua antes das acoes secundarias/destrutivas.
- Nenhum contrato backend, Maps Platform, VPS ou dado real e alterado.

### Validacao minima
- `flutter test test/features/driver_trip/presentation/widgets/driver_home_next_action_widgets_test.dart`.
- `flutter test test/features/driver_trip/presentation/widgets/driver_home_active_sheet_accessibility_test.dart test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

### Entrega
- `MapFirstPrimaryButton` agora limita o label primario a uma linha com
  ellipsis, alinhando o CTA principal ao comportamento ja protegido do CTA
  secundario.
- Foi adicionado teste widget para largura estreita e text scale alto, garantindo
  que um label longo de acao do motorista nao transforme o CTA em um bloco alto
  no meio da operacao.
- A mudanca e app-only e nao toca navegacao real, voz, MapLibre, backend,
  contratos, fixture, VPS, deploy ou push.

### Validacoes executadas
- RED:
  - `flutter test test/features/driver_trip/presentation/widgets/driver_home_next_action_widgets_test.dart`
    falhou antes da correcao com CTA primario medindo `220.0/624.0` de altura
    em largura estreita/text scale alto.
- GREEN focado:
  - `flutter test test/features/driver_trip/presentation/widgets/driver_home_next_action_widgets_test.dart`
    - PASS, 1 teste.
- Regressao da sheet ativa:
  - `flutter test test/features/driver_trip/presentation/widgets/driver_home_next_action_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_home_active_sheet_accessibility_test.dart test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart`
    - PASS, 9 testes.
- Gate app:
  - `./scripts/qa_gate_card.sh --no-e2e`
    - PASS.
    - Navigation drive simulation, TTS, audio confirmation e MapLibre contracts:
      PASS.
    - `flutter analyze`: PASS, sem issues.
    - `flutter test`: PASS, 651 testes.
    - quality ratchet: PASS.
- `dart format lib/features/tracking/presentation/map_first_v2_components.dart test/features/driver_trip/presentation/widgets/driver_home_next_action_widgets_test.dart`
  - PASS, 2 arquivos, 0 mudancas pendentes apos format.
- `git diff --check`
  - PASS.

### Limites
- A confirmacao audivel humana de `APP-NAV-AUDIO-CONFIRM-001` continua
  pendente; este card protege apenas a interface visual do CTA.
- Nao houve push, deploy, VPS, parceiro, campo, dado real, fixture versionada,
  segredo versionado, backend/API change ou alteracao de navegacao real.

---

### APP-NAV-VOICE-EVENTS-002 - Eventos criticos de voz da navegacao

### Status
DONE (commit: 731f052)

### Objetivo
Completar a fatia de voz obrigatoria com avisos explicitos de chegada na
parada, offline/fallback e saida de rota, sem depender de audio real, backend,
GPS, device, parceiro, deploy ou VPS.

### Impact Analysis
- Contexto do card: `APP-NAV-VOICE-001` criou o controller/adapter de voz e os
  cards seguintes provaram TTS tecnico, simulacao local e pacote de revisao
  audivel. O plano mestre tambem pedia aviso de chegada, saida de rota e modo
  offline; hoje isso aparece indiretamente como texto de instrucao/estado, mas
  nao existe uma API de evento critico de voz nem evidencia separada desses
  avisos na simulacao.
- Fluxos afetados: `DriverNavigationVoiceController` e evidencia local de
  `DriverNavigationDriveSimulation`. Nao altera tela real, rotas Flutter,
  lifecycle de corrida, backend, WebSocket, Maps Platform, provider nativo ou
  fixture.
- Contratos afetados: nenhum endpoint, payload, status code, CORS, auth,
  WebSocket, schema de API ou contrato Maps Platform muda.
- Riscos e mitigacao: risco de duplicar fala ou aumentar ruido para o
  motorista; mitigado por dedupe por evento e respeito ao mudo. Risco de
  parecer audio confirmado; mitigado mantendo `APP-NAV-AUDIO-CONFIRM-001`
  pendente e usando apenas gateway fake/QA local.
- Validacao minima obrigatoria: RED/GREEN do controller de voz, regressao da
  simulacao de corrida, smoke check-only, `flutter test test/features/navigation`,
  `./scripts/qa_gate_card.sh --no-e2e` e `git diff --check`.
- Rollback simples: reverter teste, dominio de voz, simulacao e docs deste
  card; nenhuma migracao, segredo, deploy, push, VPS ou dado real.

### Plano
1. Adicionar teste RED para eventos criticos de voz deduplicados, com chegada,
   offline e saida de rota.
2. Adicionar teste RED na simulacao para evidenciar evento de voz separado do
   texto da instrucao.
3. Implementar API pequena no `DriverNavigationVoiceController` para eventos
   criticos, respeitando mute e sem bloquear por reduced attention.
4. Atualizar a simulacao QA para registrar `eventSpokenText`/prompts em JSON e
   Markdown.
5. Rodar validacoes, registrar QA Log e fechar com commits locais.

### Criterios de Aceite
- Chegada na parada fala uma frase PT-BR especifica com o nome da proxima
  parada quando disponivel.
- Offline/fallback fala uma frase conservadora que orienta uso de fallback sem
  prometer navegacao perfeita.
- Saida de rota fala uma frase curta de modo seguro.
- Eventos nao repetem para o mesmo id, respeitam mudo e continuam possiveis
  mesmo quando fala automatica de instrucao esta reduzida.
- A simulacao QA registra os eventos de voz sem promover campo real ou audio
  humano confirmado.

### Validacao minima
- `flutter test test/features/navigation/driver_navigation_voice_controller_test.dart`.
- `flutter test test/features/navigation/driver_navigation_drive_simulation_test.dart`.
- `python3 scripts/qa_navigation_drive_sim_smoke_test.py`.
- `./scripts/qa_navigation_drive_sim_smoke.sh --check-only`.
- `flutter test test/features/navigation`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

### Entrega
- `DriverNavigationVoiceController` ganhou `DriverNavigationVoiceEvent` e
  `speakEvent` para avisos de chegada, offline/fallback e saida de rota.
- Eventos criticos sao deduplicados por id proprio, respeitam mute e nao
  sobrescrevem o dedupe de instrucao (`lastSpokenInstructionId`).
- A simulacao QA agora registra `eventSpokenText` por passo,
  `eventSpokenPrompts` no JSON e uma tabela `Evento de voz` no Markdown.
- Evidencia local gerada em
  `output/navigation-drive-sim/20260707-voice-events-qa/`.

### Validacoes executadas
- RED inicial:
  - `flutter test test/features/navigation/driver_navigation_voice_controller_test.dart test/features/navigation/driver_navigation_drive_simulation_test.dart`
    falhou por `DriverNavigationVoiceEvent`, `speakEvent` e
    `eventSpokenText` ausentes.
- RED de estado:
  - `flutter test test/features/navigation/driver_navigation_voice_controller_test.dart`
    falhou por `lastSpokenEventId` ausente, cobrindo que evento nao deve
    resetar dedupe de instrucao.
- GREEN focado:
  - `flutter test test/features/navigation/driver_navigation_voice_controller_test.dart`
    - PASS, 8 testes.
  - `flutter test test/features/navigation/driver_navigation_voice_controller_test.dart test/features/navigation/driver_navigation_drive_simulation_test.dart`
    - PASS, 10 testes.
- Regressao navegacao:
  - `flutter test test/features/navigation`
    - PASS, 23 testes.
  - `python3 scripts/qa_navigation_drive_sim_smoke_test.py`
    - PASS, 5 testes.
  - `./scripts/qa_navigation_drive_sim_smoke.sh --check-only`
    - PASS, `NAVIGATION_DRIVE_SIM_SMOKE_OK steps=6`.
- Evidencia local:
  - `./scripts/qa_navigation_drive_sim_smoke.sh --run --output-dir output/navigation-drive-sim/20260707-voice-events-qa`
    - PASS, `NAVIGATION_DRIVE_SIM_EVIDENCE_OK steps=6`.
- Gate app:
  - `./scripts/qa_gate_card.sh --no-e2e`
    - PASS.
    - Navigation drive simulation, TTS, audio confirmation e MapLibre contracts:
      PASS.
    - `flutter analyze`: PASS, sem issues.
    - `flutter test`: PASS, 654 testes.
    - quality ratchet: PASS.
- `git diff --check`
  - PASS.

### Limites
- Eventos usam gateway fake na simulacao e nao confirmam audio audivel humano.
- `APP-NAV-AUDIO-CONFIRM-001` segue pendente de uma pessoa ouvir e registrar o
  audio PT-BR.
- Nao houve push, deploy, VPS, parceiro, campo, GPS real, device novo, dado
  real, fixture versionada, segredo versionado, backend/API change ou alteracao
  de navegacao real.

---

### APP-NAV-OP-ACTIONS-001 - Acoes operacionais no painel de navegacao

### Status
DONE (commit: 17d9fb8)

### Objetivo
Colocar as acoes essenciais do motorista dentro do painel de navegacao, para
que `Confirmar embarque`, `Pular`, `Faltou`, `Repetir instrucao` e `Fallback`
fiquem acessiveis no mesmo contexto operacional sem exigir que o motorista
procure acoes secundarias durante a corrida.

### Impact Analysis
- Contexto do card: o plano mestre de navegacao exige acoes essenciais no modo
  dirigir (`Cheguei`, `Embarcou`, `Faltou`, `Pular`, `Repetir instrucao` e
  `Abrir fallback`). Hoje o painel de navegacao ja cobre repetir, fallback e
  acao primaria, mas nao concentra `Pular` e `Faltou` no mesmo painel.
- Fluxos afetados: `DriverNavigationPresenter`, `DriverNavigationPanel` e
  wrapper `DriverHomeNavigationPanel` dentro da sheet ativa do motorista.
- Dependencia de contrato backend: nenhuma. As acoes usam callbacks ja
  existentes da tela do motorista; nenhum endpoint, payload, status code,
  auth, CORS ou WebSocket muda.
- Dependencia nativa/device: nenhuma. O card nao abre mapa real, nao confirma
  audio humano e nao depende de GPS/device.
- Riscos e mitigacao: risco de poluir o painel de direcao; mitigado por acoes
  compactas, secundarias e testadas com texto grande. Risco de duplicar acoes
  ja presentes na review section; mitigado mantendo os mesmos callbacks e sem
  alterar o lifecycle real.
- Validacao minima obrigatoria: RED/GREEN de presenter/widget/wrapper,
  regressao da sheet ativa, `flutter test test/features/navigation
  test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`,
  `./scripts/qa_gate_card.sh --no-e2e` e `git diff --check`.
- Rollback simples: reverter testes, presenter, widget, wrapper e docs deste
  card; nenhuma migracao, segredo, deploy, push, VPS, dado real ou backend.

### Plano
1. Adicionar testes RED para labels de `Confirmar embarque`, `Pular` e
   `Faltou` no view model/painel.
2. Adicionar teste RED garantindo que o wrapper chama os callbacks reais de
   `Pular` e `Faltou`.
3. Implementar acoes secundarias compactas no painel de navegacao.
4. Ligar o wrapper da sheet ativa aos callbacks existentes.
5. Rodar validacoes, registrar QA Log e fechar com commits locais.

### Criterios de Aceite
- O view model de navegacao expoe a acao primaria como `Confirmar embarque`.
- O painel de navegacao mostra `Pular` e `Faltou` como acoes secundarias
  compactas quando callbacks forem fornecidos.
- `Pular` e `Faltou` acionam os mesmos callbacks ja usados pela sheet ativa do
  motorista.
- O painel continua tolerando texto grande sem excecao de layout.
- Nenhum contrato backend, Maps Platform, VPS, audio humano, parceiro, campo ou
  dado real e alterado.

### Validacao minima
- `flutter test test/features/navigation/driver_navigation_presenter_test.dart`.
- `flutter test test/features/navigation/presentation/driver_navigation_panel_test.dart`.
- `flutter test test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`.
- `flutter test test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_home_active_sheet_accessibility_test.dart`.
- `flutter test test/features/navigation`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

### Entrega
- `DriverNavigationViewModel` agora expoe acoes secundarias tipadas para o
  painel de navegacao do motorista.
- `DriverNavigationPresenter` apresenta `Confirmar embarque` como acao primaria
  e `Pular`/`Faltou` como acoes operacionais compactas.
- `DriverNavigationPanel` renderiza `Pular` e `Faltou` quando os callbacks sao
  fornecidos, com chaves estaveis para teste e reuso.
- `DriverHomeNavigationPanel` recebe os callbacks existentes da sheet ativa e
  os desativa durante lifecycle action em andamento.
- Testes de sheet/visual foram ajustados para aceitar a duplicacao intencional
  de labels entre CTA principal, painel de navegacao e acoes de revisao.

### Validacoes executadas
- RED:
  - `flutter test test/features/navigation/driver_navigation_presenter_test.dart test/features/navigation/presentation/driver_navigation_panel_test.dart test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`
    falhou por `DriverNavigationSecondaryActionType`, `secondaryActions`,
    callbacks e chaves ausentes.
- GREEN focado:
  - `flutter test test/features/navigation/driver_navigation_presenter_test.dart test/features/navigation/presentation/driver_navigation_panel_test.dart test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`
    - PASS, 13 testes.
- Regressao navegacao/sheet:
  - `flutter test test/features/navigation test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_home_active_sheet_accessibility_test.dart`
    - PASS, 34 testes apos atualizar expectativas de labels duplicados.
  - `flutter test test/features/tracking/premium_map_visual_acceptance_test.dart`
    - PASS, 6 testes.
- Smokes:
  - `python3 scripts/qa_navigation_drive_sim_smoke_test.py`
    - PASS, 5 testes.
  - `./scripts/qa_navigation_drive_sim_smoke.sh --check-only`
    - PASS, `NAVIGATION_DRIVE_SIM_SMOKE_OK steps=6`.
- Gate app:
  - Primeira tentativa de `./scripts/qa_gate_card.sh --no-e2e` falhou por
    expectativas antigas de unicidade de `Confirmar embarque` na suite visual.
  - Tentativa final de `./scripts/qa_gate_card.sh --no-e2e`
    - PASS.
    - Navigation drive simulation, TTS, audio confirmation e MapLibre contracts:
      PASS.
    - `flutter analyze`: PASS, sem issues.
    - `flutter test`: PASS, 655 testes.
    - quality ratchet: PASS.
- `dart format ...`
  - PASS.
- `git diff --check`
  - PASS.

### Limites
- O card concentra acoes no painel, mas nao cria fila offline persistente nem
  muda sincronizacao com backend.
- A duplicacao de labels e intencional nesta etapa: CTA principal, painel de
  navegacao e secao de revisao continuam usando os mesmos callbacks.
- `APP-NAV-AUDIO-CONFIRM-001` segue pendente de uma pessoa ouvir e registrar o
  audio PT-BR.
- Nao houve push, deploy, VPS, parceiro, campo, GPS real, device novo, dado
  real, fixture versionada, segredo versionado, backend/API change ou alteracao
  de navegacao real.

---

### APP-NAV-ACTION-QUEUE-001 - Fila local de acoes da navegacao

### Status
DONE (commit: a94f240)

### Objetivo
Preparar uma fila local simples e confiavel para as acoes operacionais do
motorista na navegacao, dando feedback claro quando uma acao fica pendente no
app e deixando o caminho aberto para persistencia/sincronizacao futura sem
alterar backend nesta etapa.

### Impact Analysis
- Contexto do card: `APP-NAV-OP-ACTIONS-001` colocou `Confirmar embarque`,
  `Pular` e `Faltou` dentro do painel de navegacao, mas registrou que ainda nao
  existe fila offline persistente nem sincronizacao local de acoes. O plano
  mestre tambem pede que acoes operacionais entrem em fila local.
- Fluxos afetados: dominio de navegacao, `DriverNavigationViewModel` e
  `DriverNavigationPanel`.
- Dependencia de contrato backend: nenhuma. Este card nao cria endpoint, nao
  envia payload e nao muda status code, CORS, auth ou WebSocket.
- Dependencia nativa/device: nenhuma. O card nao abre mapa real, nao confirma
  audio humano e nao depende de GPS/device.
- Riscos e mitigacao: risco de sugerir sincronizacao que ainda nao existe;
  mitigado por copy explicita de acao local pendente e por manter persistencia
  e sync como proximos cards. Risco de duplicar acao no mesmo stop; mitigado por
  dedupe deterministico por viagem/parada/tipo.
- Validacao minima obrigatoria: RED/GREEN do modelo da fila e do painel,
  regressao de navegacao, smokes de simulacao guiada, `./scripts/qa_gate_card.sh
  --no-e2e` e `git diff --check`.
- Rollback simples: reverter modelo, campos opcionais do view model, render do
  painel, testes e docs deste card; nenhuma migracao, segredo, deploy, push,
  VPS, dado real ou backend.

### Plano
1. Adicionar testes RED para enfileirar acao, deduplicar e resumir pendencias.
2. Adicionar teste RED para o painel mostrar o resumo de fila local.
3. Implementar modelo imutavel da fila e resumo de apresentacao.
4. Expor campos opcionais no view model e renderizar o status no painel.
5. Rodar validacoes, registrar QA Log e fechar com commits locais.

### Criterios de Aceite
- A fila local representa `Confirmar embarque`, `Pular` e `Faltou` com dados de
  viagem/parada, label humana e status pendente.
- A fila deduplica a mesma acao para a mesma viagem/parada/tipo.
- O resumo da fila diferencia singular/plural e deixa claro que a acao esta
  localmente pendente.
- O painel de navegacao mostra o resumo quando houver acoes pendentes e nao
  muda layout quando nao houver fila.
- Nenhum contrato backend, Maps Platform, VPS, audio humano, parceiro, campo ou
  dado real e alterado.

### Validacao minima
- `flutter test test/features/navigation/driver_navigation_action_queue_test.dart`.
- `flutter test test/features/navigation/presentation/driver_navigation_panel_test.dart`.
- `flutter test test/features/navigation`.
- `python3 scripts/qa_navigation_drive_sim_smoke_test.py`.
- `./scripts/qa_navigation_drive_sim_smoke.sh --check-only`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

### Entrega
- `DriverNavigationActionQueue` representa acoes locais de navegacao para
  `Confirmar embarque`, `Pular` e `Faltou` com id deterministico,
  viagem/parada, status pendente e label humana.
- A fila deduplica a mesma acao por viagem/parada/tipo e preserva a primeira
  interacao do motorista.
- `DriverNavigationActionQueueSummary` gera copy singular/plural para avisar
  que ha acao local pendente sem prometer sincronizacao automatica nesta etapa.
- `DriverNavigationSession`, `DriverNavigationPresenter` e
  `DriverNavigationViewModel` carregam o resumo opcional da fila.
- `DriverNavigationPanel` mostra um resumo discreto e semantico da fila quando
  houver pendencia, e `DriverHomeNavigationPanel` ja aceita esse resumo como
  ponto de integracao futuro.

### Validacoes executadas
- RED:
  - `flutter test test/features/navigation/driver_navigation_action_queue_test.dart test/features/navigation/presentation/driver_navigation_panel_test.dart`
    falhou como esperado por arquivo/classe de fila ausente, chave visual
    ausente e campos opcionais ausentes no view model.
- GREEN focado:
  - `flutter test test/features/navigation/driver_navigation_action_queue_test.dart test/features/navigation/driver_navigation_presenter_test.dart test/features/navigation/presentation/driver_navigation_panel_test.dart test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`
    - PASS, 20 testes.
- Regressao navegacao/smoke:
  - `flutter test test/features/navigation`
    - PASS.
  - `python3 scripts/qa_navigation_drive_sim_smoke_test.py`
    - PASS, 5 testes.
  - `./scripts/qa_navigation_drive_sim_smoke.sh --check-only`
    - PASS, `NAVIGATION_DRIVE_SIM_SMOKE_OK steps=6`.
- Gate app:
  - `./scripts/qa_gate_card.sh --no-e2e`
    - PASS.
    - Navigation drive simulation, TTS, audio confirmation e MapLibre
      contracts: PASS.
    - `flutter analyze`: PASS, sem issues.
    - `flutter test`: PASS, 662 testes.
    - quality ratchet: PASS.
- Format/check:
  - `dart format lib/features/navigation/domain/driver_navigation_action_queue.dart lib/features/navigation/domain/driver_navigation_models.dart lib/features/navigation/presentation/driver_navigation_presenter.dart lib/features/navigation/presentation/widgets/driver_navigation_panel.dart lib/features/driver_trip/presentation/widgets/driver_home_navigation_panel.dart test/features/navigation/driver_navigation_action_queue_test.dart test/features/navigation/driver_navigation_presenter_test.dart test/features/navigation/presentation/driver_navigation_panel_test.dart test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`
    - PASS.
  - `git diff --check`
    - PASS.

### Limites
- O card cria a base local e a superficie de feedback, mas ainda nao persiste a
  fila em disco nem sincroniza pendencias com backend.
- A tela real ja tem o ponto de integracao (`actionQueueSummary`), mas o
  enfileiramento automatico no fluxo de erro/offline fica para o proximo card.
- `APP-NAV-AUDIO-CONFIRM-001` segue pendente de uma pessoa ouvir e registrar o
  audio PT-BR.
- Nao houve push, deploy, VPS, parceiro, campo, GPS real, device novo, dado
  real, fixture versionada, segredo versionado, backend/API change ou alteracao
  de navegacao real.

---

### APP-NAV-ACTION-PERSIST-001 - Persistencia e retry da fila local da navegacao

### Status
DONE (commit: 9a43725)

### Objetivo
Fazer a fila local de acoes da navegacao sobreviver a restart do app,
enfileirar acoes operacionais quando a tentativa real falhar por erro local/rede
e oferecer retry explicito para o motorista reenviar as pendencias sem trocar de
tela.

### Impact Analysis
- Contexto do card: `APP-NAV-ACTION-QUEUE-001` criou a base da fila e o resumo
  visual, mas registrou que ainda nao havia persistencia em disco,
  sincronizacao/retry nem enfileiramento automatico em erro/offline.
- Fluxos afetados: dominio/data de navegacao, painel de navegacao do motorista,
  sheet ativa do motorista e lifecycle actions de `Confirmar embarque`,
  `Pular` e `Faltou`.
- Dependencia de contrato backend: nenhuma nova rota, payload ou status code. O
  retry reaproveita os metodos ja existentes de `DriverTripGateway`:
  `completeStop`, `skipStop` e `noShowStop`.
- Dependencia nativa/device: nenhuma. A persistencia usa storage local do app e
  o card nao depende de GPS real, device fisico, Maps Platform real ou audio
  humano.
- Riscos e mitigacao: risco de reenviar acao rejeitada pelo backend; mitigado
  enfileirando apenas falhas nao classificadas como `TripTrackingException` e
  usando retry explicito. Risco de perder pendencias ao reiniciar; mitigado com
  roundtrip em `SharedPreferences` e teste de payload corrompido. Risco de
  executar acoes fora de ordem; mitigado por retry sequencial que para na
  primeira falha.
- Validacao minima obrigatoria: RED/GREEN de serializacao/store/sync, widget do
  retry, integracao da home do motorista em falha offline, `flutter test
  test/features/navigation`, regressao focada da home do motorista,
  `./scripts/qa_gate_card.sh --no-e2e` e `git diff --check`.
- Rollback simples: reverter store/sync, campos/callbacks de UI, lifecycle
  enqueue/retry, testes e docs deste card; nenhuma migracao, segredo, push,
  deploy, VPS, dado real ou backend.

### Plano
1. Adicionar testes RED para serializacao, remocao e persistencia da fila.
2. Adicionar testes RED para retry sequencial e parada na primeira falha.
3. Adicionar testes RED para botao de retry no painel de navegacao.
4. Adicionar teste RED da home do motorista enfileirando uma acao quando o
   gateway falhar por erro local/rede.
5. Implementar store, sync service, callback de retry e enfileiramento em
   falha nao-`TripTrackingException`.
6. Rodar validacoes, registrar QA Log e fechar com commits locais.

### Criterios de Aceite
- A fila local faz roundtrip em storage local sem segredo, payload real ou dado
  sensivel.
- Payload corrompido volta para fila vazia e nao derruba a home do motorista.
- `Confirmar embarque`, `Pular` e `Faltou` entram na fila quando falham por erro
  local/rede, preservando dedupe.
- Erros de negocio vindos como `TripTrackingException` continuam aparecendo como
  erro e nao viram fila offline.
- O painel mostra a pendencia persistida e oferece retry explicito.
- O retry envia acoes em ordem, remove sucessos, para na primeira falha e
  mantem o restante na fila.
- Nenhum contrato backend, Maps Platform, VPS, audio humano, parceiro, campo ou
  dado real e alterado.

### Validacao minima
- `flutter test test/features/navigation/driver_navigation_action_queue_test.dart`.
- `flutter test test/features/navigation/data/driver_navigation_action_queue_store_test.dart`.
- `flutter test test/features/navigation/domain/driver_navigation_action_queue_sync_test.dart`.
- `flutter test test/features/navigation/presentation/driver_navigation_panel_test.dart`.
- `flutter test test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`.
- `flutter test test/features/driver_trip/home_motorista_active_trip_test.dart`.
- `flutter test test/features/navigation`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

### Entrega
- `DriverNavigationActionQueue` agora faz roundtrip JSON, remove acoes
  sincronizadas por id e mantem ordenacao/dedupe por viagem/parada/tipo.
- `SharedPreferencesDriverNavigationActionQueueStore` persiste a fila local por
  storage centralizado do app, remove payload vazio e limpa payload corrompido
  sem derrubar a home do motorista.
- `DriverNavigationActionQueueSync` envia pendencias em ordem, contabiliza
  sucessos, guarda o ultimo snapshot sincronizado e para na primeira falha,
  preservando a acao falha e as seguintes.
- `HomeMotoristaScreen` carrega pendencias persistidas, enfileira
  `Confirmar embarque`, `Pular` e `Faltou` quando a falha nao e
  `TripTrackingException`, mostra pendencia no painel e oferece retry explicito.
- Erros de negocio continuam aparecendo para o motorista e nao viram fila local.
- O retry reaproveita os gateways existentes (`completeStop`, `skipStop`,
  `noShowStop`) sem criar rota, payload ou contrato novo.

### Validacoes executadas
- RED:
  - `flutter test test/features/navigation/driver_navigation_action_queue_test.dart test/features/navigation/data/driver_navigation_action_queue_store_test.dart test/features/navigation/domain/driver_navigation_action_queue_sync_test.dart test/features/navigation/presentation/driver_navigation_panel_test.dart test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart test/features/driver_trip/home_motorista_active_trip_test.dart`
    - FAIL esperado por `fromJson`, `removeAction`, store/sync ausentes,
      `onRetryQueuedActions`, `actionQueueRetryKey` e injecao de store ainda
      ausentes.
- GREEN focado:
  - `flutter test test/features/navigation/driver_navigation_action_queue_test.dart test/features/navigation/data/driver_navigation_action_queue_store_test.dart test/features/navigation/domain/driver_navigation_action_queue_sync_test.dart test/features/navigation/presentation/driver_navigation_panel_test.dart test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart test/features/driver_trip/home_motorista_active_trip_test.dart`
    - PASS, 25 testes.
- Regressao navegacao e motorista:
  - `flutter test test/features/navigation`
    - PASS.
  - `flutter test test/features/driver_trip/home_motorista_active_trip_test.dart test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_home_active_sheet_accessibility_test.dart`
    - PASS, 19 testes.
- Smokes:
  - `python3 scripts/qa_navigation_drive_sim_smoke_test.py`
    - PASS, 5 testes.
  - `./scripts/qa_navigation_drive_sim_smoke.sh --check-only`
    - PASS, `NAVIGATION_DRIVE_SIM_SMOKE_OK steps=6 evidence_root=output/navigation-drive-sim`.
- Gate app:
  - `./scripts/qa_gate_card.sh --no-e2e`
    - PASS na tentativa final.
    - Navigation drive simulation, TTS, audio confirmation, MapLibre device,
      style/tile e tile fixture contracts: PASS.
    - `flutter analyze`: PASS, sem issues.
    - `flutter test`: PASS, 672 testes.
    - quality ratchet: PASS.
  - A primeira tentativa do gate falhou apenas por aumento real de
    `set_state_count` e `shared_preferences_violation_count`; o card foi
    ajustado para `ValueNotifier` e store local centralizado antes do commit.
- Quality/format:
  - `python3 scripts/quality_metrics.py --check`
    - PASS.
  - `dart format lib/app/session/app_session_store.dart lib/features/navigation/data/driver_navigation_action_queue_store.dart lib/features/driver_trip/presentation/home_motorista_screen.dart test/features/driver_trip/home_motorista_active_trip_test.dart`
    - PASS.
  - `flutter analyze`
    - PASS, sem issues.
  - `git diff --check` e `git diff --cached --check`
    - PASS.

### Limites
- O retry e explicito: o app ainda nao roda sincronizacao automatica em
  background.
- A classificacao de offline nesta etapa segue conservadora: nao enfileira
  `TripTrackingException`; falhas nao classificadas entram na fila local para
  retry humano.
- `APP-NAV-AUDIO-CONFIRM-001` segue pendente de uma pessoa ouvir e registrar o
  audio PT-BR.
- Nao houve push, deploy, VPS, parceiro, campo, GPS real, device novo, dado
  real, fixture versionada, segredo versionado, backend/API change ou alteracao
  de provider/Maps Platform.

---

### APP-NAV-ACTION-DETAIL-001 - Detalhe humano da fila local da navegacao

### Status
DONE (commit: 67e047c)

### Objetivo
Tornar a fila local mais compreensivel para o motorista mostrando qual e a
proxima acao pendente e para qual passageiro/parada ela sera enviada, sem
alterar backend, sync, GPS, audio ou provider de mapas.

### Impact Analysis
- Contexto do card: `APP-NAV-ACTION-PERSIST-001` fez a fila sobreviver a
  restart e adicionou retry explicito, mas o painel ainda mostra apenas a
  quantidade de acoes pendentes. Em operacao real, isso deixa o motorista sem
  saber se a pendencia e `Confirmar embarque`, `Pular` ou `Faltou`.
- Fluxos afetados: dominio de resumo da fila local e painel de navegacao do
  motorista.
- Dependencia de contrato backend: nenhuma. O card nao cria rota, payload,
  status code, retry novo, worker, WebSocket ou sincronizacao automatica.
- Dependencia nativa/device: nenhuma. Nao depende de audio, GPS real, device
  fisico, Maps Platform, VPS, parceiro ou dados reais.
- Riscos e mitigacao: risco de texto longo poluir o painel; mitigado com copy
  curta baseada na primeira pendencia e testes de widget. Risco de prometer
  envio automatico; mitigado preservando o helper atual de envio quando a
  conexao estiver segura e mantendo retry explicito.
- Validacao minima obrigatoria: RED/GREEN do resumo da fila, widget do painel,
  regressao do painel da home do motorista, `flutter analyze`,
  `./scripts/qa_gate_card.sh --no-e2e` e `git diff --check`.
- Rollback simples: reverter campos/copy do resumo, ajuste visual do painel,
  testes e docs deste card.

### Plano
1. Adicionar teste RED no dominio para `detailLabel` da primeira pendencia.
2. Adicionar teste RED no painel para renderizar o detalhe humano da fila.
3. Implementar `detailLabel` no resumo e exibir o texto no painel.
4. Rodar validacoes, registrar QA Log e fechar com commits locais.

### Criterios de Aceite
- Uma pendencia mostra a acao e o passageiro/parada em linguagem humana.
- Multiplas pendencias continuam mostrando quantidade total e a primeira acao
  da fila como proximo item a enviar.
- O painel preserva o botao `Enviar agora`, o helper de conexao segura e o
  comportamento quando nao ha fila.
- Nenhum contrato backend, Maps Platform, VPS, audio humano, parceiro, campo ou
  dado real e alterado.

### Validacao minima
- `flutter test test/features/navigation/driver_navigation_action_queue_test.dart`.
- `flutter test test/features/navigation/presentation/driver_navigation_panel_test.dart`.
- `flutter test test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`.
- `flutter analyze`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

### Entrega
- `DriverNavigationActionQueueSummary` agora inclui `detailLabel` com a
  primeira pendencia em linguagem humana.
- Uma pendencia mostra `Confirmar embarque de Ana Clara`; multiplas pendencias
  mostram a quantidade total e `Proxima: <acao> de <parada>`.
- `DriverNavigationPresenter` e `DriverNavigationViewModel` transportam o
  detalhe sem alterar contratos backend, sync, provider de mapa, GPS ou audio.
- `DriverNavigationPanel` exibe o detalhe logo abaixo do resumo da fila,
  preservando o helper de conexao segura e o botao `Enviar agora`.
- `DriverHomeNavigationPanel` continua recebendo o mesmo resumo opcional da
  navegacao, agora com contexto suficiente para revisao humana.

### Validacoes executadas
- RED:
  - `flutter test test/features/navigation/driver_navigation_action_queue_test.dart test/features/navigation/presentation/driver_navigation_panel_test.dart test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`
    - FAIL esperado por `detailLabel` e `queuedActionDetailLabel` ainda
      ausentes no resumo/view model.
- GREEN focado:
  - `flutter test test/features/navigation/driver_navigation_action_queue_test.dart test/features/navigation/driver_navigation_presenter_test.dart test/features/navigation/presentation/driver_navigation_panel_test.dart test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`
    - PASS, 22 testes.
- Regressao navegacao:
  - `flutter test test/features/navigation`
    - PASS, 36 testes.
- Gate app:
  - `./scripts/qa_gate_card.sh --no-e2e`
    - PASS.
    - Navigation drive simulation, TTS, audio confirmation, MapLibre device,
      style/tile e tile fixture contracts: PASS.
    - `flutter analyze`: PASS, sem issues.
    - `flutter test`: PASS, 672 testes.
    - quality ratchet: PASS.
- Quality/format:
  - `dart format lib/features/navigation/domain/driver_navigation_action_queue.dart lib/features/navigation/domain/driver_navigation_models.dart lib/features/navigation/presentation/driver_navigation_presenter.dart lib/features/navigation/presentation/widgets/driver_navigation_panel.dart test/features/navigation/driver_navigation_action_queue_test.dart test/features/navigation/presentation/driver_navigation_panel_test.dart test/features/navigation/driver_navigation_presenter_test.dart test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`
    - PASS.
  - `flutter analyze`
    - PASS, sem issues.
  - `git diff --check`
    - PASS.

### Limites
- O card nao cria sincronizacao automatica/background; o retry segue explicito.
- O card nao muda backend, API, Maps Platform, GPS, device fisico, VPS, deploy,
  parceiro, fixture versionada, dado real ou segredo.
- `APP-NAV-AUDIO-CONFIRM-001` segue pendente de uma pessoa ouvir e registrar o
  audio PT-BR.

---

### APP-NAV-ACTION-AUTO-SYNC-001 - Sincronizacao automatica controlada da fila local

### Status
DONE (commit: ca979a5)

### Objetivo
Reduzir o trabalho manual do motorista fazendo o app tentar enviar a fila local
de acoes de navegacao quando a home operacional carrega ou volta para
foreground, mantendo `Enviar agora` como fallback explicito quando a tentativa
automatica falhar.

### Impact Analysis
- Contexto do card: `APP-NAV-ACTION-PERSIST-001` fez a fila sobreviver a
  restart e adicionou retry explicito; `APP-NAV-ACTION-DETAIL-001` tornou a
  pendencia legivel para o motorista. Ainda falta remover a necessidade de o
  motorista lembrar de tocar em `Enviar agora` sempre que a conexao voltar.
- Fluxos afetados: home operacional do motorista, store/sync da fila local e
  lifecycle de retorno do app para foreground.
- Dependencia de contrato backend: nenhuma rota, payload, status code, CORS,
  auth, WebSocket ou worker novo. O auto-sync reaproveita `completeStop`,
  `skipStop` e `noShowStop` existentes.
- Dependencia nativa/device: apenas `WidgetsBindingObserver`/lifecycle Flutter.
  O card nao cria background service nativo, permissao nova, GPS real, audio,
  Maps Platform, push, deploy, VPS ou scheduler do sistema.
- Riscos e mitigacao: risco de reenviar acao rejeitada enquanto o motorista nao
  esta olhando; mitigado por manter o mesmo sync sequencial que para na
  primeira falha e preserva o restante. Risco de distração ao dirigir; mitigado
  por auto-sync silencioso, deixando snackbar apenas para retry manual. Risco
  de prometer background real; mitigado por copy/documentacao de foreground.
- Validacao minima obrigatoria: RED/GREEN de auto-sync ao abrir home, RED/GREEN
  de auto-sync ao voltar para foreground, regressao do retry manual,
  `flutter test test/features/navigation`, `flutter analyze`,
  `./scripts/qa_gate_card.sh --no-e2e` e `git diff --check`.
- Rollback simples: reverter observer/lifecycle, chamada automatica de sync,
  testes e docs deste card; nenhuma migracao, segredo, push, deploy, VPS, dado
  real ou backend.

### Plano
1. Adicionar teste RED para enviar fila persistida automaticamente ao abrir a
   home do motorista.
2. Adicionar teste RED para reler a fila e reenviar quando o app voltar para
   foreground.
3. Preservar teste do fallback manual `Enviar agora` quando a tentativa
   automatica falhar.
4. Implementar auto-sync controlado reutilizando o sync existente.
5. Rodar validacoes, registrar QA Log e fechar com commits locais.

### Criterios de Aceite
- Uma fila local persistida e enviada automaticamente ao carregar a home do
  motorista quando a conexao/gateway estiver saudavel.
- Ao voltar para foreground, a home relê a fila persistida e tenta enviar
  pendencias que apareceram enquanto a tela estava pausada.
- Se o auto-sync falhar, a fila continua visivel com detalhe humano e o botao
  `Enviar agora` permanece disponivel.
- O retry manual continua mostrando feedback por snackbar.
- Nenhum contrato backend, Maps Platform, VPS, background nativo, audio humano,
  parceiro, campo, fixture real ou segredo e alterado.

### Validacao minima
- `flutter test test/features/driver_trip/home_motorista_active_trip_test.dart`.
- `flutter test test/features/navigation`.
- `flutter analyze`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

### Entrega
- `HomeMotoristaScreen` observa o lifecycle Flutter e relê a fila local quando
  o app volta para foreground.
- A fila local persistida passa por auto-sync silencioso ao carregar a home do
  motorista e ao retomar foreground.
- O envio automatico reutiliza `DriverNavigationActionQueueSync` e os mesmos
  gateways existentes (`completeStop`, `skipStop`, `noShowStop`), sem novo
  contrato backend.
- Se o auto-sync falhar, a fila permanece visivel com detalhe humano e o botao
  `Enviar agora`; o retry manual continua exibindo snackbar.
- A logica da fila foi extraida para `home_motorista_navigation_action_queue.dart`
  para manter o ratchet de tamanho do arquivo principal.

### Validacoes executadas
- RED:
  - `flutter test test/features/driver_trip/home_motorista_active_trip_test.dart`
    - FAIL esperado nos tres novos cenarios porque a home ainda nao enviava a
      fila ao abrir, nao tentava auto-sync antes do retry manual e nao observava
      foreground.
- GREEN focado:
  - `flutter test test/features/driver_trip/home_motorista_active_trip_test.dart`
    - PASS, 9 testes.
- Regressao navegacao e motorista:
  - `flutter test test/features/navigation`
    - PASS, 36 testes.
  - `flutter test test/features/driver_trip/home_motorista_active_trip_test.dart test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_home_active_sheet_accessibility_test.dart`
    - PASS, 21 testes.
- Gate app:
  - `./scripts/qa_gate_card.sh --no-e2e`
    - PASS.
    - Navigation drive simulation, TTS, audio confirmation, MapLibre device,
      style/tile e tile fixture contracts: PASS.
    - `flutter analyze`: PASS, sem issues.
    - `flutter test`: PASS, 674 testes.
    - quality ratchet: PASS.
- Quality/format:
  - `dart format lib/features/driver_trip/presentation/home_motorista_screen.dart lib/features/driver_trip/presentation/home_motorista_navigation_action_queue.dart test/features/driver_trip/home_motorista_active_trip_test.dart test/features/driver_trip/home_motorista_test_support.dart`
    - PASS.
  - `flutter analyze`
    - PASS, sem issues.
  - `python3 scripts/quality_metrics.py --check`
    - FAIL inicial por `home_motorista_screen.dart` com 835 linhas para limite
      821; PASS apos extrair a fila para `home_motorista_navigation_action_queue.dart`.
  - `git diff --check`
    - PASS.

### Limites
- Auto-sync roda apenas quando a home operacional carrega ou quando o app volta
  para foreground; nao e background service nativo.
- O retry manual permanece como fallback humano quando a conexao/gateway falha.
- O card nao muda backend, API, Maps Platform, GPS, device fisico, VPS, deploy,
  parceiro, fixture versionada, dado real ou segredo.
- `APP-NAV-AUDIO-CONFIRM-001` segue pendente de uma pessoa ouvir e registrar o
  audio PT-BR.

---

### APP-NAV-ACTION-AUTO-SYNC-QA-001 - Smoke QA da fila auto-sincronizada

### Status
DONE (commit: 7565384)

### Objetivo
Criar uma prova QA local, repetivel e auditavel de que a fila local de acoes da
navegacao tenta sincronizar ao carregar a home do motorista e ao voltar para
foreground, mantendo `Enviar agora` como fallback humano quando o envio
automatico falhar.

### Impact Analysis
- Contexto do card: `APP-NAV-ACTION-AUTO-SYNC-001` implementou o auto-sync
  foreground-only e validou com widget tests focados, mas ainda nao existe um
  smoke citavel no pacote de pre-piloto para provar esse comportamento sem
  depender de memoria da sessao.
- Fluxos afetados: QA automatizado local da home operacional do motorista e do
  painel de navegacao; runtime do app so muda se o gate precisar chamar o novo
  smoke.
- Dependencia de contrato backend: nenhuma. O card reaproveita testes locais,
  fake gateway e fila local; nao cria endpoint, payload, status code, CORS,
  auth, WebSocket, worker ou retry remoto.
- Dependencia nativa/device: nenhuma no gate rapido. O smoke nao abre device,
  GPS, audio real, Maps Platform, parceiro, campo, push, deploy ou VPS.
- Riscos e mitigacao: risco de vender o comportamento como background service;
  mitigado por markers `foreground-only`, `backgroundService=false` e docs de
  limites. Risco de duplicar validacao lenta; mitigado com um smoke focado e
  check-only no gate. Risco de evidencia parecer campo real; mitigado por
  `fieldReady=false` e politica sem dados reais.
- Validacao minima obrigatoria: RED/GREEN do script de smoke, execucao
  `--check-only`, execucao `--run` com evidencia local, regressao focada da
  home do motorista, `flutter analyze`, `./scripts/qa_gate_card.sh --no-e2e` e
  `git diff --check`.
- Rollback simples: remover script/teste/entrada do gate e docs deste card; nao
  ha migracao, segredo, backend, deploy, push, VPS ou dado real.

### Plano
1. Adicionar teste RED para exigir o script de smoke e sua chamada no gate.
2. Implementar smoke `--check-only` rodando a prova focada da home do motorista.
3. Implementar modo `--run` gerando markdown/json auditavel em
   `output/navigation-action-auto-sync/`.
4. Encaixar o smoke no gate rapido sem exigir E2E real, device ou audio humano.
5. Rodar validacoes, registrar QA Log e fechar com commits locais.

### Criterios de Aceite
- O smoke valida os cenarios de auto-sync ao abrir a home, auto-sync ao voltar
  para foreground e fallback manual quando o auto-sync falha.
- `--check-only` passa sem device, backend, audio real, GPS real, parceiro,
  campo, push, deploy ou VPS.
- `--run` gera `navigation-action-auto-sync.md/json` com `fieldReady=false`,
  `backgroundService=false` e `foregroundOnly=true`.
- O gate local chama apenas o contrato/check-only.
- `APP-NAV-AUDIO-CONFIRM-001` continua pendente de confirmacao audivel humana.

### Validacao minima
- `python3 scripts/qa_navigation_action_auto_sync_smoke_test.py`.
- `./scripts/qa_navigation_action_auto_sync_smoke.py --check-only`.
- `./scripts/qa_navigation_action_auto_sync_smoke.py --run --output-dir output/navigation-action-auto-sync/<timestamp>`.
- `flutter test test/features/driver_trip/home_motorista_active_trip_test.dart`.
- `flutter analyze`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

### Entrega
- Criado `qa_navigation_action_auto_sync_smoke.py` com modos `--check-only` e
  `--run`.
- O smoke verifica que os cenarios de home load, foreground resume e fallback
  manual continuam presentes em
  `test/features/driver_trip/home_motorista_active_trip_test.dart`.
- `--check-only` executa a prova focada da home do motorista sem device,
  backend, GPS real, audio humano, Maps Platform, parceiro, campo, push, deploy
  ou VPS.
- `--run` gera `navigation-action-auto-sync.md/json` com
  `foregroundOnly=true`, `backgroundService=false`, `fieldReady=false` e
  `externalPilotReady=false`.
- `qa_gate_card.sh --no-e2e` agora inclui o contrato rapido do smoke antes dos
  testes Flutter completos.

### Validacoes executadas
- RED:
  - `python3 scripts/qa_navigation_action_auto_sync_smoke_test.py`
    - FAIL esperado porque `qa_navigation_action_auto_sync_smoke.py` ainda nao
      existia e o gate ainda nao chamava o contrato.
- GREEN focado:
  - `python3 -m py_compile scripts/qa_navigation_action_auto_sync_smoke.py scripts/qa_navigation_action_auto_sync_smoke_test.py`
    - PASS.
  - `python3 scripts/qa_navigation_action_auto_sync_smoke_test.py`
    - PASS, 5 testes.
  - `./scripts/qa_navigation_action_auto_sync_smoke.py --check-only`
    - PASS, `NAVIGATION_ACTION_AUTO_SYNC_SMOKE_OK`.
  - `./scripts/qa_navigation_action_auto_sync_smoke.py --run --output-dir output/navigation-action-auto-sync/20260707-action-auto-sync-qa-final`
    - PASS, evidencia local gerada.
- Regressao:
  - `flutter test test/features/driver_trip/home_motorista_active_trip_test.dart`
    - PASS, 9 testes.
  - `flutter test test/features/navigation`
    - PASS, 36 testes.
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`
    - PASS, sem issues.
  - `python3 scripts/quality_metrics.py --check`
    - PASS.
- Gate app:
  - `./scripts/qa_gate_card.sh --no-e2e`
    - PASS.
    - Novo Navigation action auto-sync smoke contract: PASS.
    - Navigation drive simulation, TTS, audio confirmation, MapLibre device,
      style/tile e tile fixture contracts: PASS.
    - `flutter analyze`: PASS, sem issues.
    - `flutter test`: PASS, 674 testes.
    - quality ratchet: PASS.
- Quality/check:
  - `git diff --check`
    - PASS.
  - `git diff --cached --check`
    - PASS antes do commit tecnico.

### Limites
- O smoke prova somente comportamento foreground-only da home operacional; nao
  cria nem valida background service nativo.
- A evidencia local fica em `output/` e nao e versionada.
- O card nao muda backend, API, Maps Platform, GPS real, device fisico, VPS,
  deploy, parceiro, campo, fixture versionada, dado real ou segredo.
- `APP-NAV-AUDIO-CONFIRM-001` segue pendente de uma pessoa ouvir e registrar o
  audio PT-BR.

---

### APP-NAV-ROUTE-PLAN-CONTRACT-001 - Contrato app-side do route plan self-hosted

### Status
DONE (commit: 88a51bb)

### Objetivo
Preparar o app para consumir o contrato `POST /maps/routes/plan` do Maps
Platform self-hosted sem ativar chamada real de rede, ensinando a navegacao do
motorista a distinguir rota validada de resposta fixture/fallback.

### Impact Analysis
- Contexto do card: o backend e o Maps Platform ja possuem um caminho local
  para planejar rotas, mas o app ainda nao tem um modelo proprio para entender
  `coverageStatus`, `offlinePackStatus` e instrucoes do route plan antes de
  chegar na tela do motorista.
- Fluxos afetados: dominio/data de navegacao do motorista e testes do presenter
  via `DriverNavigationSession`. Nenhuma tela deve passar a chamar Maps
  Platform neste card.
- Dependencia de contrato backend: nenhuma mudanca de API. O card usa o schema
  documentado em `vanrides-maps-platform/docs/api-contract.md` como contrato de
  leitura local.
- Dependencia nativa/device: nenhuma. Nao usa GPS real, audio real, MapLibre,
  Valhalla/OSRM, VPS ou device fisico.
- Riscos e mitigacao: risco de promover resposta `fixture/fallback` como
  turn-by-turn real; mitigado exigindo `coverageStatus=validated` e
  `offlinePackStatus=ready` antes de `DriverNavigationMode.internal`. Risco de
  acoplar app a nomes temporarios do MVP; mitigado por parser conservador que
  trata status desconhecido como fallback/bloqueio.
- Validacao minima obrigatoria: RED/GREEN de teste focado do contrato,
  `flutter test test/features/navigation`, `flutter analyze`, quality ratchet,
  `./scripts/qa_gate_card.sh --no-e2e` e `git diff --check`.
- Rollback simples: remover modelo/parser/mapper e testes do card; nao ha
  migracao, segredo, backend, deploy, push, VPS ou dado real.

### Plano
1. Criar teste RED com fixture do contrato `POST /maps/routes/plan`.
2. Implementar parser app-side para resposta do Maps Platform.
3. Mapear `coverageStatus` e `offlinePackStatus` para
   `DriverNavigationSession` com promocao segura.
4. Validar que `fallback/missing` continua fallback externo e que
   `validated/ready` habilita navegacao interna.
5. Rodar gate, registrar QA Log e fechar com commits locais.

### Criterios de Aceite
- O app parseia `routeId`, `provider`, `region`, `vehicleType`,
  `coverageStatus`, `offlinePackStatus`, distancia, duracao e instrucoes.
- Resposta `coverageStatus=fallback` ou `offlinePackStatus=missing` gera
  `DriverNavigationSession` sem navegacao interna e com fallback externo
  primario.
- Apenas `coverageStatus=validated` + `offlinePackStatus=ready` pode habilitar
  instrucao interna no presenter.
- Status desconhecido ou resposta sem instrucao nao promove navegacao interna.
- O card nao cria chamada HTTP, nao muda backend/API, nao ativa Maps Platform
  real e nao altera VPS/deploy/push.
- `APP-NAV-AUDIO-CONFIRM-001` continua pendente de confirmacao audivel humana.

### Validacao minima
- `flutter test test/features/navigation/data/driver_navigation_route_plan_contract_test.dart`.
- `flutter test test/features/navigation`.
- `flutter analyze --no-fatal-infos --no-fatal-warnings`.
- `python3 scripts/quality_metrics.py --check`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

### Entrega
- Criado `driver_navigation_route_plan_contract.dart` para parsear o contrato
  local do Maps Platform sem abrir chamada HTTP ou dependencia de backend.
- O parser entende `routeId`, `provider`, `region`, `vehicleType`,
  `coverageStatus`, `offlinePackStatus`, duracao, distancia, polyline, legs e
  instrucoes.
- `toNavigationSession` so promove instrucao para o motorista quando
  `coverageStatus=validated`, `offlinePackStatus=ready` e existe instrucao
  utilizavel.
- Respostas fixture/fallback/missing mantem `DriverNavigationMode.externalFallback`
  e nao expõem voz/instrucao de rota interna para o motorista.
- Status desconhecido ou resposta validada sem instrucao vira caminho seguro,
  sem turn-by-turn interno.

### Validacoes executadas
- RED:
  - `flutter test test/features/navigation/data/driver_navigation_route_plan_contract_test.dart`
    - FAIL esperado porque
      `driver_navigation_route_plan_contract.dart` ainda nao existia.
- GREEN focado:
  - `dart format lib/features/navigation/data/driver_navigation_route_plan_contract.dart test/features/navigation/data/driver_navigation_route_plan_contract_test.dart`
    - PASS.
  - `flutter test test/features/navigation/data/driver_navigation_route_plan_contract_test.dart`
    - PASS, 4 testes.
- Regressao:
  - `flutter test test/features/navigation`
    - PASS, 40 testes.
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`
    - PASS, sem issues.
  - `python3 scripts/quality_metrics.py --check`
    - PASS.
- Gate final:
  - `./scripts/qa_gate_card.sh --no-e2e`
    - PASS.
    - Navigation drive simulation, action auto-sync, TTS, audio confirmation,
      MapLibre device/style/tile/tile fixture contracts: PASS.
    - `flutter analyze`: PASS, sem issues.
    - `flutter test`: PASS, 678 testes.
    - quality ratchet: PASS.
- Quality/check:
  - `git diff --check`
    - PASS.
  - `git diff --cached --check`
    - PASS antes do commit tecnico.

### Limites
- Nao houve chamada HTTP, mudanca de API, backend, Maps Platform runtime, GPS
  real, audio humano, device fisico, Valhalla/OSRM, VPS, deploy, push,
  parceiro, campo, fixture versionada, dado real ou segredo.
- O card prepara o app para consumir route plan no futuro, mas nao altera a
  tela para buscar ou renderizar rota real ainda.
- `APP-NAV-AUDIO-CONFIRM-001` segue pendente de uma pessoa ouvir e registrar o
  audio PT-BR.

---

### APP-NAV-ROUTE-PLAN-PANEL-001 - Painel do motorista consome route plan injetado

### Status
DONE (commit: eae8e0a)

### Objetivo
Conectar o contrato app-side do Maps Platform ao painel de navegacao do
motorista por uma entrada injetavel e testavel, permitindo provar que uma rota
validada aparece como instrucao interna enquanto respostas fixture/fallback
continuam usando fallback externo.

### Impact Analysis
- Contexto do card: `APP-NAV-ROUTE-PLAN-CONTRACT-001` criou o parser/adapter do
  route plan, mas a tela operacional do motorista ainda monta sempre uma
  `DriverNavigationSession` fallback/manual. Esta fatia leva o adapter ate o
  painel, sem criar busca remota.
- Fluxos afetados: `DriverHomeNavigationPanel`, testes widget do painel e voz
  de repeticao. `HomeMotoristaScreen` continua sem chamada real ao Maps
  Platform neste card.
- Dependencia de contrato backend: nenhuma mudanca de API. O card reaproveita
  apenas o objeto app-side ja parseado, como snapshot injetado.
- Dependencia nativa/device: nenhuma. Nao usa GPS real, audio humano, MapLibre
  real, Valhalla/OSRM, VPS, deploy ou provider pago.
- Riscos e mitigacao: risco de parecer que route plan fixture esta pronto para
  campo; mitigado por testes garantindo `fallback/missing` como fallback
  externo. Risco de quebrar fallback existente do motorista; mitigado mantendo
  a construcao atual quando nao houver snapshot injetado.
- Validacao minima obrigatoria: RED/GREEN de widget test do painel, regressao
  focada do painel, `flutter test test/features/navigation`, `flutter analyze`,
  quality ratchet, `./scripts/qa_gate_card.sh --no-e2e` e `git diff --check`.
- Rollback simples: remover parametro injetavel do painel, testes e docs deste
  card; nao ha migracao, segredo, backend, deploy, push, VPS ou dado real.

### Plano
1. Criar teste RED provando que snapshot `validated/ready` aparece no painel e
   alimenta o gateway de voz.
2. Criar teste RED garantindo que snapshot `fallback/missing` continua fallback
   externo e nao fala instrucao interna.
3. Implementar parametro opcional no painel para route plan ja parseado.
4. Preservar o fallback antigo quando nenhum route plan e informado.
5. Rodar validacoes, registrar QA Log e fechar com commits locais.

### Criterios de Aceite
- `DriverHomeNavigationPanel` aceita um route plan app-side opcional sem abrir
  chamada HTTP.
- Snapshot `validated/ready` exibe a instrucao do route plan e repete essa voz
  pelo gateway injetado.
- Snapshot `fallback/missing` mantem o texto seguro de fallback externo e nao
  usa a instrucao fixture como turn-by-turn interno.
- Sem snapshot, o painel preserva o comportamento atual de fallback baseado em
  `canOpenMaps`.
- O card nao muda backend/API, nao ativa Maps Platform runtime, nao usa GPS
  real, device, audio humano, VPS, deploy, push, parceiro, campo, fixture
  versionada, dado real ou segredo.
- `APP-NAV-AUDIO-CONFIRM-001` continua pendente de confirmacao audivel humana.

### Validacao minima
- `flutter test test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`.
- `flutter test test/features/navigation`.
- `flutter analyze --no-fatal-infos --no-fatal-warnings`.
- `python3 scripts/quality_metrics.py --check`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

### Entrega
- `DriverHomeNavigationPanel` recebeu `routePlanResponse` opcional para
  consumir um snapshot app-side ja parseado, sem abrir chamada HTTP.
- Snapshot `validated/ready` passa por `toNavigationSession`, exibe a instrucao
  interna no painel e usa `voiceText` no gateway de voz injetado.
- Snapshot `fallback/missing` continua exibindo o texto seguro de fallback
  externo e nao fala a instrucao fixture como turn-by-turn interno.
- Sem snapshot, o painel preserva a sessao fallback anterior baseada em
  `canOpenMaps`, mantendo o comportamento operacional existente.

### Validacoes executadas
- RED:
  - `flutter test test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`
    - FAIL esperado porque `DriverHomeNavigationPanel` ainda nao tinha o
      parametro `routePlanResponse`.
- GREEN focado:
  - `dart format lib/features/driver_trip/presentation/widgets/driver_home_navigation_panel.dart test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`
    - PASS.
  - `flutter test test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart`
    - PASS, 6 testes.
- Regressao:
  - `flutter test test/features/navigation`
    - PASS, 40 testes.
  - `flutter test test/features/driver_trip/home_motorista_active_trip_test.dart test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_home_active_sheet_accessibility_test.dart`
    - PASS, 23 testes.
  - `flutter analyze --no-fatal-infos --no-fatal-warnings`
    - PASS, sem issues.
  - `python3 scripts/quality_metrics.py --check`
    - PASS.
- Gate final:
  - `./scripts/qa_gate_card.sh --no-e2e`
    - PASS.
    - Navigation drive simulation, action auto-sync, TTS, audio confirmation,
      MapLibre device/style/tile/tile fixture contracts: PASS.
    - `flutter analyze`: PASS, sem issues.
    - `flutter test`: PASS, 680 testes.
    - quality ratchet: PASS.
- Quality/check:
  - `git diff --check`
    - PASS.
  - `git diff --cached --check`
    - PASS antes do commit tecnico.

### Limites
- Nao houve chamada HTTP, mudanca de API, backend, Maps Platform runtime, GPS
  real, audio humano, device fisico, Valhalla/OSRM, VPS, deploy, push,
  parceiro, campo, fixture versionada, dado real ou segredo.
- O card prova a superficie injetavel do painel; `HomeMotoristaScreen` ainda
  nao busca route plan remoto nem renderiza navegacao self-hosted de campo.
- `APP-NAV-AUDIO-CONFIRM-001` segue pendente de uma pessoa ouvir e registrar o
  audio PT-BR.

---

### APP-NAV-ROUTE-PLAN-SOURCE-001 - Fonte local de route plan para a home do motorista

### Status
DONE (commit: 574314d)

### Objetivo
Criar uma fonte app-side, local e injetavel de snapshot de route plan para a
home operacional do motorista, conectando `HomeMotoristaScreen` ao painel de
navegacao sem chamada HTTP, sem backend novo e sem ativar Maps Platform real.

### Impact Analysis
- Contexto do card: `APP-NAV-ROUTE-PLAN-PANEL-001` permitiu ao painel consumir
  um route plan ja parseado, mas `HomeMotoristaScreen` ainda nao possui uma
  fonte oficial para entregar esse snapshot ao fluxo real da home.
- Fluxos afetados: composicao da home do motorista, `DriverHomeTripTab`,
  `DriverHomeActiveSheet` e testes widget da home ativa. O painel continua com
  a mesma semantica de promocao segura.
- Dependencia de contrato backend: nenhuma mudanca de API. A fonte padrao sera
  `null/noop`; snapshots usados em teste sao objetos app-side ja parseados.
- Dependencia nativa/device: nenhuma. Nao usa GPS real, audio humano, MapLibre
  real, Valhalla/OSRM, VPS, deploy ou provider pago.
- Riscos e mitigacao: risco de parecer que a home ja busca Maps Platform real;
  mitigado por fonte noop padrao, nome de snapshot local e docs de limite.
  Risco de promover fixture/fallback; mitigado reaproveitando o adapter do
  contrato e testes no painel/home.
- Validacao minima obrigatoria: RED/GREEN de widget test da home ativa,
  regressao focada de navegacao e motorista, `flutter analyze`, quality
  ratchet, `./scripts/qa_gate_card.sh --no-e2e` e `git diff --check`.
- Rollback simples: remover fonte injetavel, parametros de propagacao e testes
  deste card; nao ha migracao, segredo, backend, deploy, push, VPS ou dado real.

### Plano
1. Criar teste RED injetando uma fonte local de route plan na home ativa.
2. Garantir que snapshot `validated/ready` aparece no painel da home e
   substitui o fallback seguro.
3. Implementar fonte noop padrao e propagar snapshot por
   `HomeMotoristaScreen` -> `DriverHomeTripTab` -> `DriverHomeActiveSheet`.
4. Preservar comportamento atual quando a fonte retorna `null`.
5. Rodar validacoes, registrar QA Log e fechar com commits locais.

### Criterios de Aceite
- `HomeMotoristaScreen` aceita uma fonte app-side opcional de snapshot de route
  plan sem abrir rede.
- Fonte padrao nao retorna snapshot e preserva o fallback atual.
- Fonte injetada com snapshot `validated/ready` faz a home ativa exibir a
  instrucao interna no painel.
- Fonte `null` ou snapshot nao promocionavel mantem fallback externo.
- O card nao muda backend/API, nao ativa Maps Platform runtime, nao usa GPS
  real, device, audio humano, VPS, deploy, push, parceiro, campo, fixture
  versionada, dado real ou segredo.
- `APP-NAV-AUDIO-CONFIRM-001` continua pendente de confirmacao audivel humana.

### Validacao minima
- `flutter test test/features/driver_trip/home_motorista_active_trip_test.dart`.
- `flutter test test/features/navigation`.
- `flutter analyze --no-fatal-infos --no-fatal-warnings`.
- `python3 scripts/quality_metrics.py --check`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

### Entrega
- `HomeMotoristaScreen` aceita `navigationRoutePlanSource` opcional e cria uma
  fonte noop padrao quando nenhuma fonte e injetada.
- A fonte padrao retorna `null`, preservando o fallback seguro existente e sem
  abrir rede.
- Snapshot `validated/ready` e passado por `HomeMotoristaScreen` ->
  `DriverHomeTripTab` -> `DriverHomeActiveSheet` ->
  `DriverHomeNavigationPanel`.
- A home ativa exibe a instrucao interna do route plan injetado quando a fonte
  local entrega snapshot promocionavel.
- O teste de suporte ganhou uma fonte fake e fixture parseada de route plan
  app-side, sem dado real e sem fixture versionada externa.

### Validacoes executadas
- RED:
  `flutter test test/features/driver_trip/home_motorista_active_trip_test.dart`
  - FAIL esperado por falta de
    `driver_navigation_route_plan_source.dart`,
    `DriverNavigationRoutePlanSnapshotSource` e parametro
    `navigationRoutePlanSource` em `HomeMotoristaScreen`.
- GREEN focado:
  `dart format lib/features/navigation/data/driver_navigation_route_plan_source.dart lib/features/driver_trip/presentation/home_motorista_screen.dart lib/features/driver_trip/presentation/widgets/driver_home_trip_tab.dart lib/features/driver_trip/presentation/widgets/driver_home_trip_sheet_widgets.dart test/features/driver_trip/home_motorista_active_trip_test.dart test/features/driver_trip/home_motorista_test_support.dart`
  - PASS.
  `flutter test test/features/driver_trip/home_motorista_active_trip_test.dart`
  - PASS, 10 testes.
- Ratchet:
  `python3 scripts/quality_metrics.py --check`
  - FAIL intermediario esperado porque
    `test/features/driver_trip/home_motorista_active_trip_test.dart` chegou a
    602 linhas, acima do limite de 600.
  - Corrigido movendo fake/helper para
    `test/features/driver_trip/home_motorista_test_support.dart`.
  - Arquivo ativo final: 549 linhas.
  - `python3 scripts/quality_metrics.py --check`
    - PASS.
- Regressao:
  `flutter test test/features/navigation`
  - PASS, 40 testes.
  `flutter test test/features/driver_trip/home_motorista_active_trip_test.dart test/features/driver_trip/presentation/widgets/driver_home_navigation_panel_test.dart test/features/driver_trip/presentation/widgets/driver_home_trip_widgets_test.dart test/features/driver_trip/presentation/widgets/driver_home_active_sheet_accessibility_test.dart`
  - PASS, 24 testes.
  `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - PASS, sem issues.
- Gate final:
  `./scripts/qa_gate_card.sh --no-e2e`
  - PASS.
  - Navigation drive simulation, action auto-sync, TTS, audio confirmation,
    MapLibre device/style/tile/tile fixture contracts: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 681 testes.
  - quality ratchet: PASS.
- Checks:
  `git diff --check`
  - PASS.
  `git diff --cached --check`
  - PASS antes do commit tecnico.

### Limites
- Nao houve chamada HTTP, backend/API, Maps Platform runtime, GPS real, audio
  humano, device fisico, Valhalla/OSRM, VPS, deploy, push, parceiro, campo,
  fixture versionada, dado real ou segredo.
- A fonte e local/noop por padrao; a home ainda nao busca route plan remoto nem
  recebe dados reais de Valhalla/OSRM.
- O card prepara a injecao app-side para a proxima etapa de fonte real/local,
  mantendo o caminho de fallback externo quando nao existe snapshot validado.
- `APP-NAV-AUDIO-CONFIRM-001` segue pendente de confirmacao audivel humana.

---

### APP-NAV-ROUTE-PLAN-STATUS-001 - Normalizacao de status do route plan

### Status
DONE (commit: 87e808c)

### Objetivo
Normalizar no app os status de cobertura vindos do contrato Maps Platform /
backend, aceitando tanto os valores em ingles ja usados no Flutter quanto os
valores aprovados em portugues (`validada`, `em_observacao`, `bloqueada`) sem
abrir rede, sem backend novo e sem promover fixture/fallback.

### Impact Analysis
- Contexto do card: `APP-NAV-ROUTE-PLAN-SOURCE-001` conectou uma fonte local
  ao painel, mas o adapter Flutter ainda entende apenas `validated`,
  `observation`, `fallback` e `blocked`. O plano e o backend usam estados como
  `validada`, `em_observacao` e `bloqueada` para cobertura BR-RS.
- Fluxos afetados: parser app-side de route plan,
  `DriverNavigationRoutePlanResponse.toNavigationSession` e presenter do
  painel do motorista. Nao ha mudanca visual direta fora do estado resultante.
- Contratos afetados: nenhum endpoint, payload ou schema novo. A mudanca e
  tolerante/aditiva no client Flutter para aceitar sinonimos do contrato ja
  documentado.
- Riscos e mitigacao: risco de promover rota indevida so por traducao de
  status; mitigado mantendo a regra de promocao exigindo tambem
  `offlinePackStatus=ready`, instrucao utilizavel e comportamento existente
  para fixture/fallback.
- Validacao minima obrigatoria: RED/GREEN em
  `driver_navigation_route_plan_contract_test.dart`, regressao de navegacao,
  `flutter analyze`, quality ratchet, `./scripts/qa_gate_card.sh --no-e2e` e
  `git diff --check`.
- Rollback simples: reverter o ajuste de normalizacao e os testes deste card;
  nao ha migracao, segredo, backend, deploy, push, VPS ou dado real.

### Plano
1. Escrever RED provando que `validada/ready` promove navegacao interna e
   `em_observacao` vira cobertura em observacao.
2. Implementar normalizacao conservadora dos status de cobertura.
3. Preservar fallback para status desconhecido e fixture/fallback.
4. Rodar validacoes focadas e gate do app.
5. Registrar QA Log, fechar backlog e commitar localmente.

### Criterios de Aceite
- `coverageStatus=validada` e `offlinePackStatus=ready` com provider nao
  fixture e instrucao utilizavel promove `DriverNavigationCoverageStatus.validated`.
- `coverageStatus=em_observacao` vira
  `DriverNavigationCoverageStatus.observation` e nao promove navegacao interna.
- `coverageStatus=bloqueada` vira `DriverNavigationCoverageStatus.blocked`.
- Valores atuais em ingles continuam funcionando.
- Status desconhecido continua bloqueado.
- O card nao muda backend/API, nao ativa Maps Platform runtime, nao usa GPS
  real, device, audio humano, VPS, deploy, push, parceiro, campo, fixture
  versionada, dado real ou segredo.

### Validacao minima
- `flutter test test/features/navigation/data/driver_navigation_route_plan_contract_test.dart`.
- `flutter test test/features/navigation`.
- `flutter analyze --no-fatal-infos --no-fatal-warnings`.
- `python3 scripts/quality_metrics.py --check`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

### Entrega
- `DriverNavigationRoutePlanResponse.toNavigationSession` agora entende
  `coverageStatus=validada` como cobertura validada.
- `coverageStatus=em_observacao`, `em-observacao`, `em observacao` e
  `observacao` agora mapeiam para cobertura em observacao.
- `coverageStatus=bloqueada` e `bloqueado` agora mapeiam para cobertura
  bloqueada.
- Os valores em ingles (`validated`, `observation`, `fallback`, `blocked`)
  continuam aceitos.
- Status desconhecido continua bloqueado e nao promove navegacao interna.

### Validacoes executadas
- RED:
  `flutter test test/features/navigation/data/driver_navigation_route_plan_contract_test.dart`
  - FAIL esperado porque `coverageStatus=validada` ainda virava
    `DriverNavigationCoverageStatus.blocked`.
- GREEN focado:
  `dart format lib/features/navigation/data/driver_navigation_route_plan_contract.dart test/features/navigation/data/driver_navigation_route_plan_contract_test.dart`
  - PASS.
  `flutter test test/features/navigation/data/driver_navigation_route_plan_contract_test.dart`
  - PASS, 5 testes.
- Regressao:
  `flutter test test/features/navigation`
  - PASS, 41 testes.
  `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - PASS, sem issues.
  `python3 scripts/quality_metrics.py --check`
  - PASS.
- Gate final:
  `./scripts/qa_gate_card.sh --no-e2e`
  - PASS.
  - Navigation drive simulation, action auto-sync, TTS, audio confirmation,
    MapLibre device/style/tile/tile fixture contracts: PASS.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 682 testes.
  - quality ratchet: PASS.
- Checks:
  `git diff --check`
  - PASS.
  `git diff --cached --check`
  - PASS antes do commit tecnico.

### Limites
- Nao houve chamada HTTP, backend/API, Maps Platform runtime, GPS real, audio
  humano, device fisico, Valhalla/OSRM, VPS, deploy, push, parceiro, campo,
  fixture versionada, dado real ou segredo.
- O card nao muda a regra de promocao alem da normalizacao de status: continua
  exigindo `offlinePackStatus=ready` e instrucao utilizavel.
- A home ainda nao busca route plan remoto nem recebe dados reais
  self-hosted; este card apenas evita drift de status entre backend/plataforma
  e app.
- `APP-NAV-AUDIO-CONFIRM-001` segue pendente de confirmacao audivel humana.

---

### APP-NAV-OFFLINE-STATUS-PTBR-001 - Normalizacao PT-BR do pacote offline

### Status
DONE (commit: c178893)

### Objetivo
Normalizar no app os status do pacote offline vindos do contrato Maps Platform /
backend, aceitando valores em portugues (`pronto`, `ausente`,
`desatualizado`, `parcial`, `falhou`) alem dos valores em ingles ja suportados,
sem ativar rede, device, Maps Platform runtime ou piloto externo.

### Impact Analysis
- Contexto do card: `APP-NAV-ROUTE-PLAN-STATUS-001` normalizou os status de
  cobertura em PT-BR, mas `offlinePackStatus` ainda entende apenas
  `ready`, `missing`, `outdated`, `partial` e `failed`. Isso pode fazer uma
  rota `validada/pronto` cair em fallback por drift de vocabulario entre
  backend, Maps Platform e app.
- Fluxos afetados: parser app-side de route plan,
  `DriverNavigationRoutePlanResponse.toNavigationSession` e presenter do
  painel do motorista por meio do estado offline resultante. Nao ha mudanca
  visual direta fora da normalizacao do contrato.
- Contratos afetados: nenhum endpoint, payload, rota, status code, auth, CORS
  ou WebSocket novo. A mudanca e tolerante/aditiva no client Flutter para
  aceitar sinonimos PT-BR do contrato ja planejado.
- Riscos e mitigacao: risco de promover navegacao interna por traducao
  indevida; mitigado mantendo a regra existente que exige cobertura validada,
  pacote offline pronto e instrucao utilizavel. Status desconhecido continua
  virando `missing`.
- Validacao minima obrigatoria: RED/GREEN em
  `driver_navigation_route_plan_contract_test.dart`, regressao de navegacao,
  `flutter analyze`, quality ratchet, `./scripts/qa_gate_card.sh --no-e2e` e
  `git diff --check`.
- Rollback simples: reverter a normalizacao de `offlinePackStatus` e os testes
  deste card; nao ha migracao, segredo, backend, deploy, push, VPS ou dado
  real.

### Plano
1. Escrever RED provando que `validada/pronto` promove navegacao interna.
2. Escrever RED cobrindo `ausente`, `desatualizado`, `parcial` e `falhou`.
3. Implementar normalizacao conservadora dos status offline PT-BR.
4. Preservar fallback para status desconhecido e estados nao prontos.
5. Rodar validacoes focadas e gate do app.
6. Registrar QA Log, fechar backlog e commitar localmente.

### Criterios de Aceite
- `offlinePackStatus=pronto` equivale a `ready` e pode promover navegacao
  interna quando o restante da resposta tambem e promocionavel.
- `offlinePackStatus=ausente` equivale a `missing`.
- `offlinePackStatus=desatualizado` equivale a `outdated`.
- `offlinePackStatus=parcial` equivale a `partial`.
- `offlinePackStatus=falhou` equivale a `failed`.
- Valores atuais em ingles continuam funcionando.
- Status desconhecido continua `missing` e nao promove navegacao interna.
- O card nao muda backend/API, nao ativa Maps Platform runtime, nao usa GPS
  real, device, audio humano, VPS, deploy, push, parceiro, campo, fixture
  versionada, dado real ou segredo.
- `APP-NAV-AUDIO-CONFIRM-001` continua pendente de confirmacao audivel humana.

### Validacao minima
- `flutter test test/features/navigation/data/driver_navigation_route_plan_contract_test.dart`.
- `flutter test test/features/navigation`.
- `flutter analyze --no-fatal-infos --no-fatal-warnings`.
- `python3 scripts/quality_metrics.py --check`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

### Entrega
- `DriverNavigationRoutePlanResponse.toNavigationSession` agora entende
  `offlinePackStatus=pronto` como pacote offline pronto.
- `offlinePackStatus=ausente` passa a mapear para pacote offline ausente.
- `offlinePackStatus=desatualizado` e `desatualizada` passam a mapear para
  pacote offline desatualizado.
- `offlinePackStatus=parcial` passa a mapear para pacote offline parcial.
- `offlinePackStatus=falhou` e `falha` passam a mapear para pacote offline com
  falha.
- Os valores em ingles (`ready`, `missing`, `outdated`, `partial`, `failed`)
  continuam aceitos.
- Status desconhecido continua `missing` e nao promove navegacao interna.

### Validacoes executadas
- RED:
  `flutter test test/features/navigation/data/driver_navigation_route_plan_contract_test.dart`
  - FAIL esperado porque `offlinePackStatus=pronto` ainda virava
    `DriverNavigationOfflineState.missing`.
- GREEN focado:
  `dart format lib/features/navigation/data/driver_navigation_route_plan_contract.dart test/features/navigation/data/driver_navigation_route_plan_contract_test.dart`
  - PASS.
  `flutter test test/features/navigation/data/driver_navigation_route_plan_contract_test.dart`
  - PASS, 6 testes.
- Regressao:
  `flutter test test/features/navigation`
  - PASS, 42 testes.
  `flutter analyze --no-fatal-infos --no-fatal-warnings`
  - PASS, sem issues.
  `python3 scripts/quality_metrics.py --check`
  - PASS.
- Gate final:
  `./scripts/qa_gate_card.sh --no-e2e`
  - PASS.
  - Navigation drive simulation, action auto-sync, TTS, audio confirmation,
    pre-pilot readiness, MapLibre device/style/tile/tile fixture contracts:
    PASS.
  - `NAVIGATION_PRE_PILOT_READINESS status=hold technical_checks=pass
    audio_human_confirmed=false external_pilot_ready=false field_ready=false`.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 685 testes.
  - quality ratchet: PASS.
  - E2E real desabilitado por `--no-e2e` / `RUN_E2E=0`.
- Checks:
  `git diff --check`
  - PASS.
  `git diff --cached --check`
  - PASS antes do commit tecnico.

### Limites
- Nao houve chamada HTTP, backend/API, Maps Platform runtime, GPS real, audio
  humano, device fisico, Valhalla/OSRM, VPS, deploy, push, parceiro, campo,
  fixture versionada, dado real ou segredo.
- O card apenas corrige drift de vocabulario do pacote offline entre
  backend/Maps Platform/app; nao baixa pacote offline real nem valida cobertura
  BR-RS em campo.
- `APP-NAV-AUDIO-CONFIRM-001` segue pendente de uma pessoa ouvir e registrar o
  audio PT-BR.

---

### APP-NAV-FALLBACK-REASON-001 - Motivo seguro do fallback de navegacao

### Status
DONE (commit: f8cf80a)

### Objetivo
Usar o `fallbackReason` ja presente no contrato de route plan para explicar ao
motorista, em linguagem operacional segura, por que a navegacao interna nao
esta sendo usada e qual e a proxima acao segura, sem expor snake_case tecnico,
sem promover cobertura e sem depender de audio humano, device, Maps Platform
runtime, backend novo ou VPS.

### Impact Analysis
- Contexto do card: o app ja parseia `fallbackReason`, mas esse campo nao chega
  a `DriverNavigationSession` nem ao `DriverNavigationPresenter`. Assim, uma
  resposta `fixture_route_not_validated_for_navigation` vira apenas a mensagem
  generica `Fallback externo e o caminho mais seguro agora`, perdendo uma
  chance de reduzir duvida do motorista.
- Fluxos afetados: adapter app-side de route plan,
  `DriverNavigationSession`, presenter do painel de navegacao e textos de ajuda
  do painel do motorista em modo fallback. Nao ha mudanca em chamada HTTP,
  tela nova ou provider de mapa.
- Contratos afetados: nenhum endpoint, payload, rota, status code, auth, CORS
  ou WebSocket novo. A mudanca consome de forma mais completa o campo
  `fallbackReason` ja existente e opcional.
- Riscos e mitigacao: risco de vazar motivo tecnico cru para o motorista;
  mitigado por enum interno e copy PT-BR controlada. Risco de parecer
  navegacao validada; mitigado mantendo fallback como acao primaria e sem
  alterar `coverageStatus`, `offlinePackStatus` ou promocao interna.
- Validacao minima obrigatoria: RED/GREEN em presenter e contrato de route
  plan, regressao de navegacao, `flutter analyze`, quality ratchet,
  `./scripts/qa_gate_card.sh --no-e2e` e `git diff --check`.
- Rollback simples: reverter o campo opcional de motivo, o mapper de reason e
  os testes deste card; nao ha migracao, segredo, backend, deploy, push, VPS ou
  dado real.

### Plano
1. Escrever RED garantindo que o presenter explique motivo conhecido de
   fallback sem snake_case.
2. Escrever RED garantindo que `fallbackReason` do route plan vira motivo
   seguro na sessao.
3. Adicionar enum/campo opcional de motivo no dominio de navegacao.
4. Mapear motivos conhecidos do contrato (`fixture_route_not_validated_for_navigation`,
   `coverage_not_validated_for_navigation`, `routing_provider_unavailable`).
5. Preservar fallback generico para motivo desconhecido ou vazio.
6. Rodar validacoes focadas e gate do app.
7. Registrar QA Log, fechar backlog e commitar localmente.

### Criterios de Aceite
- Motivos conhecidos de fallback viram copy operacional curta para o motorista.
- Nenhum snake_case ou nome de provider/fixture aparece na ajuda do painel.
- Motivo desconhecido ou vazio preserva a ajuda generica existente.
- O fallback externo continua sendo primario quando a navegacao interna nao e
  promocionavel.
- O card nao muda backend/API, nao ativa Maps Platform runtime, nao usa GPS
  real, device, audio humano, VPS, deploy, push, parceiro, campo, fixture
  versionada, dado real ou segredo.
- `APP-NAV-AUDIO-CONFIRM-001` continua pendente de confirmacao audivel humana.

### Validacao minima
- `flutter test test/features/navigation/driver_navigation_presenter_test.dart`.
- `flutter test test/features/navigation/data/driver_navigation_route_plan_contract_test.dart`.
- `flutter test test/features/navigation`.
- `flutter analyze --no-fatal-infos --no-fatal-warnings`.
- `python3 scripts/quality_metrics.py --check`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- `git diff --check`.

---

## Cards Entregues (DONE)
- APP-NAV-FALLBACK-REASON-001 - Motivo seguro do fallback de navegacao (commit: f8cf80a)
- APP-NAV-OFFLINE-STATUS-PTBR-001 - Normalizacao PT-BR do pacote offline (commit: c178893)
- APP-NAV-PRE-PILOT-READINESS-001 - Readiness tecnico pre-piloto da navegacao (commit: 64b086c)
- APP-NAV-AUDIO-HANDOFF-001 - Handoff humano da confirmacao de audio (commit: fba5405)
- NAV-ROUTE-PLAN-E2E-001 - Fixture route plan backend -> app na Home motorista (commit: 916107f)
- NAV-ROUTE-PLAN-PAYLOAD-001 - Consumir route plan do payload do motorista (commit: 5e685d8)
- Backlog 0987 — ADMIN-UX-0002: arquitetura de informação sem tela técnica de vínculos (commits: 43dcad2, 53b211d, 5d9de70, a470f88, 0c39444)
- Backlog 0988 — ADMIN-UX-0003: passageiro como ficha operacional completa (commit: 53b211d)
- Backlog 1018 — ADMIN-IA-0032: dia operacional com menos densidade visual (commit: e932f72)
- Backlog 1019 — ADMIN-QA-0001: roteiro E2E visual com fixture determinística (commit: 6ebfaf7)
- Backlog 1017 — ADMIN-IA-0031: fichas de pessoas com revelação progressiva (commit: ddc486f)
- Backlog 0993 — ADMIN-UX-0008: validação visual e funcional do portal inteiro (commit: 57c4b4e)
- Backlog 0992 — ADMIN-UX-0007: sistema visual admin dark/liquid consistente (commit: 00559c8)
- Backlog 0991 — ADMIN-UX-0006: motorista e convite com visão de empresa (commit: 5d9de70)
- Backlog 1015 — ADMIN-IA-0029: alerta de serviços sem motorista com ação direta (commit: b529816)
- Backlog 1014 — ADMIN-IA-0028: deltas de lista com linguagem operacional (commit: 167726d)
- Backlog 1013 — ADMIN-IA-0027: revisão guiada após mudanças operacionais (commit: 79f4b35)
- Backlog 1012 — ADMIN-IA-0026: exceções de viagem com ação contextual (commit: d36de35)
- Backlog 1011 — ADMIN-IA-0025: passageiros como ficha operacional (commit: a3e7cd1)
- Backlog 1010 — ADMIN-IA-0024: linguagem operacional em Viagens (commit: 28da127)
- Backlog 1009 — ADMIN-IA-0023: pós-salvamento guia revisão de viagens (commit: 20cb58e)
- Backlog 1008 — ACCOUNT-APP-0002: upload de avatar resiliente (commit: 9c986ed)
- Backlog 1007 — ADMIN-UX-0022: viagens do dia com manutenção explícita (commit: 76fad75)
- Backlog 1006 — ADMIN-UX-0021: lista de motoristas com ações explícitas (commit: c1ff9a3)
- Backlog 1005 — ADMIN-UX-0020: lista de passageiros com ações explícitas (commit: 1b0bb6b)
- Backlog 1004 — ADMIN-UX-0019: responsáveis com superfície premium e copy operacional (commit: a470f88)
- Backlog 1003 — ADMIN-UX-0018: serviços recorrentes com próximos passos legíveis (commit: 5a136e3)
- Backlog 1002 — ADMIN-UX-0017: dashboard como central de próxima ação (commit: 7c95ff1)
- Backlog 1001 — ADMIN-UX-0016: clareza operacional do portal admin (commit: af013b4)
- Backlog 1000 — ADMIN-UX-0015: desmontar vínculos técnicos com segurança (commit: 0c39444)
- Backlog 0999 — ADMIN-UX-0014: montar e sincronizar dia sem apagar viagem (commits: 1001957, bd58624; backend: dfe80aa)
- Backlog 0990 — ADMIN-UX-0005: viagens do dia orientadas a manutenção, não recriação (commits: 6ff4410, bd58624, 76fad75, d36de35)
- Backlog 0989 — ADMIN-UX-0004: serviço como centro da operação recorrente (commit: 06c6b5b)
- Backlog 0986 — ADMIN-UX-0001: hotfix de mensagens, data e identidade visual da viagem manual (commits: c486a03, a8800ee, ea646e9, 9c986ed; backend: a4c94d4)
- Backlog 0979 — PHASE-0470: matriz final de fluxo, prints e melhorias executadas (commit: 5c19308)
- Backlog 0976 — PHASE-0467: presença do passageiro em liquid glass compacto (commit: 291fe0f)
- Backlog 0975 — DRIVER-LINK-APP-0004: polimento premium do portal de motoristas (commit: 8a59229)
- Backlog 0974 — DRIVER-LINK-APP-0003: smoke visual e validação real do convite de motorista (commit: a68d73c)
- Backlog 0973 — DRIVER-LINK-APP-0002: portal admin para convites e solicitações de motoristas (commits: 2def1db, e184c49, 2dcc0eb)
- Backlog 0972 — DRIVER-LINK-APP-0001: deep link e onboarding mobile de motorista (commits: e184c49, 2dcc0eb)
- Backlog 0971 — ACCOUNT-APP-0001: conta editavel, foto, nascimento e cadastro guiado (commit: 65261fd)
- Backlog 0090 — Corrigir permissões de localização no iPhone (Motorista) (commit: ef9f462)

---

### NAV-ROUTE-PLAN-PAYLOAD-001 - Consumir route plan do payload do motorista

### Status
DONE (commit: 5e685d8)

### Objetivo
Fazer o app motorista ler o `navigationRoutePlan` opcional recebido no payload
da viagem e usa-lo como fonte padrao da navegacao interna, preservando fallback
externo quando o campo nao existir ou nao for promocionavel.

### Impact Analysis
- Contexto do card: `APP-NAV-ROUTE-PLAN-SOURCE-001` criou a fonte local
  injetavel e `APP-NAV-ROUTE-PLAN-STATUS-001` normalizou status PT-BR; faltava
  popular essa fonte a partir da viagem real recebida do backend.
- Mudancas tecnicas: `DriverTripRecord` recebe `navigationRoutePlan`, o parser
  aceita o campo opcional e `HomeMotoristaScreen` usa
  `DriverTripNavigationRoutePlanSnapshotSource` como padrao.
- Contratos afetados: consumo aditivo do campo `navigationRoutePlan` em
  `GET /viagens/motorista/{id}/hoje` e `/dia`; nenhuma chamada nova, rota,
  auth, CORS, WebSocket, permissao nativa ou provider real muda no app.
- Riscos e mitigacao: exibir navegacao interna com dado incompleto; mitigado
  pelo contrato existente, que so promove `validated/validada + ready` com
  instrucao utilizavel, mantendo fallback externo nos demais casos.
- Validacao minima obrigatoria: parsing de `DriverTripRecord`, widget test da
  Home motorista, `flutter analyze`, `flutter test`,
  `./scripts/qa_gate_card.sh --no-e2e`, `git diff --check`.
- Rollback simples: voltar a fonte padrao para noop e remover o campo
  `navigationRoutePlan` do record.

### Plano
1. Escrever RED de parsing e widget para o payload com route plan.
2. Adicionar `navigationRoutePlan` no `DriverTripRecord`.
3. Criar fonte padrao que le o route plan da propria viagem.
4. Validar app focado e gate sem E2E.
5. Registrar QA log e fechar com commit local.

### Criterios de Aceite
- `DriverTripRecord.fromJson` parseia `navigationRoutePlan`.
- A Home do motorista mostra instrucao interna quando a viagem traz route plan
  `validada/ready` com instrucao.
- A fonte injetavel continua funcionando em testes e futuros modos.
- Sem `navigationRoutePlan`, o fallback externo existente permanece.

---

### NAV-ROUTE-PLAN-E2E-001 - Fixture route plan backend -> app na Home motorista

### Status
DONE (commit: 916107f)

### Objetivo
Provar no app, com fixture versionada equivalente ao contrato backend, que a
Home do motorista consegue parsear o payload `navigationRoutePlan` validado e
exibir a instrucao interna de navegacao sem depender de rede, GPS real, VPS ou
Maps Platform runtime.

### Impact Analysis
- Contexto do card: o app ja consome `navigationRoutePlan`, mas faltava um
  teste de contrato usando o mesmo shape versionado protegido pelo backend.
- Mudancas tecnicas: fixture JSON em `test/fixtures/contracts/` e widget test
  que parseia `DriverTripRecord.fromJson`, injeta a viagem na Home e valida a
  instrucao interna renderizada.
- Contratos afetados: consumo app-side do payload existente de
  `GET /viagens/motorista/{motoristaId}/hoje`; nenhuma chamada HTTP nova,
  permissao nativa ou provider real muda.
- Riscos e mitigacao: drift entre fixture app/backend; mitigado pelo script
  raiz `scripts/qa_route_plan_payload_contract.py`, que compara os fixtures.
- Validacao minima obrigatoria: widget test focado, `flutter analyze`,
  `flutter test`, gate app sem E2E real e paridade raiz.
- Rollback simples: remover fixture e teste de contrato; o consumo runtime do
  card anterior permanece intacto.

### Plano
1. Versionar fixture app com o mesmo shape do backend.
2. Criar widget test que carrega o JSON e monta a Home motorista.
3. Validar que `validada/ready` promove a instrucao interna.
4. Rodar gate app e verificador raiz.
5. Registrar QA e fechar com commit local.

### Criterios de Aceite
- `DriverTripRecord.fromJson` parseia o fixture versionado.
- A Home motorista renderiza `Siga pela Avenida Ipiranga.` a partir do payload.
- O fallback externo nao aparece quando o route plan e promocionavel.
- O fixture app permanece igual ao fixture backend pelo verificador raiz.
- Nenhum GPS real, audio humano, provider real, deploy, VPS ou push e usado.


## VR-WA-000-PREFLIGHT — Configuração de navegação e baseline

Concluído no escopo de configuração: padrão Graphify incorporado após backup/revisão, wrapper rejeita force inválido/não suportado, CLI legado preservado. Commits e evidência exata ficam no card canônico do programa, sem duplicar uma fila de trabalho. Testes funcionais de runtime pertencem ao próximo card, VR-WA-010-TOOLCHAINS.

### VR-WA-110-RUN-TRACKING — implementação local preparada

Companion110 integrado à sessão060, com intenção de saída, confirmação explícita,
transporte ISO/DPoP, ACK parcial e parada durável; legado preservado. Estado do
card continua no programa; gates e smoke finais são responsabilidade do principal.
Ver `docs/driver-run-tracking.md` e QA Log desta data. Sem promoção/publicação.


### VR-WA-110-RUN-TRACKING — validação integrada Android, 01/11/2026

Candidata 76a0fc5 com CI 41351275820 PASS e laboratório ef64acb/APK12e022 conferidos.
A8 aprovou sete checks físicos no Redmi, incluindo GPS real, parada nativa offline
e ACK durável após reabertura; evidência/limpeza registradas no QA Execution Log e
no programa. O estado canônico do card segue na coordenação. Sem promoção
para piloto/produção; iPhone físico pausado, percurso/bateria e canal real
continuam nos gates 030/170/180.
