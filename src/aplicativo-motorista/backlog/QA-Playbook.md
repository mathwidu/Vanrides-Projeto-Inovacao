# QA Playbook — VanRides App (Flutter)

Objetivo: roteiro mínimo e repetível para validar cards do app no iPhone.

## Pré-checagens (sempre)
1) Dependências:
   - `flutter --version`
   - `cd ios && pod install && cd ..`
   - iOS: `cp ios/Flutter/Secrets.xcconfig.example ios/Flutter/Secrets.xcconfig` (se ainda nao existir) e preencher `GOOGLE_MAPS_API_KEY`
   - Android (quando aplicável): definir `GOOGLE_MAPS_API_KEY` em `android/local.properties` (arquivo local)
2) Dispositivos:
   - `flutter devices` (iPhone listado)
3) Rodar app:
   - `flutter run -d "<Nome do iPhone>" --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_GOOGLE_PLACES_API_KEY=<SUA_GOOGLE_PLACES_KEY>`

## Suíte automatizada (sempre que mudar código)
1) Análise (lint/analyzer):
   - `flutter analyze`
2) Unit + Widget tests:
   - `flutter test`
3) Integration tests (quando existirem):
   - iOS Simulator (recomendado): `flutter test integration_test -d "iPhone 16"`
   - iPhone físico (opcional): `flutter test integration_test -d "<Nome do iPhone>"`
   - Com backend da VPS (E2E): `flutter test integration_test -d "<device>" --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_E2E_TEST_MODE=true`
   - Rodar só Motorista: `flutter test integration_test/motorista/all.dart -d "<device>" --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_E2E_TEST_MODE=true`
   - Rodar só Passageiro: `flutter test integration_test/passageiro/all.dart -d "<device>" --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_E2E_TEST_MODE=true`
   - Rodar tudo (Motorista + Passageiro): `flutter test integration_test/all.dart -d "<device>" --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_E2E_TEST_MODE=true`
   - Observação: `integration_test` **não roda no Chrome/Web** (para Web usar Playwright).
   - Observação: device iOS **wireless** pode não ser suportado para `integration_test` (prefira Simulator ou USB).
   - Observação: em E2E, a tela de localização do passageiro usa um placeholder de mapa (evita flakiness do PlatformView do GoogleMap); validar pelas coordenadas.

Observação:
- Para testes que dependem do backend, padronizar “modo mock” (unit/widget) e “modo real” (integration).
- `VANRIDES_E2E_TEST_MODE=true` habilita atalhos de teste (ex.: auto-preencher endereço no cadastro de passageiro) para evitar flakiness no Simulator.
- As chaves Google devem ficar fora do versionamento e com restricoes por plataforma (bundle/package/SHA).
- Gate automático recomendado por card: `./scripts/qa_gate_card.sh`
- Padrão de qualidade por card: `backlog/CARD-QUALITY-STANDARD.md`

## Gate hermetico, readiness e evidencias externas
Conceito: o gate tecnico responde apenas pelo checkout avaliado. Device, backend,
audio humano, campo e contratos cross-repo sao provas separadas e nunca podem
ser inferidos de arquivos ignorados ou do layout do workspace.

1) Perfil tecnico sem E2E:
   - `./scripts/qa_gate_card.sh --no-e2e`
   - nao le `scripts/.dev.env.local`, `output/`, timestamp historico nem arquivo
     fora do repositorio;
   - flags conflitantes, opcoes desconhecidas e valores ausentes falham com
     exit `2`.
2) Perfil E2E explicito:
   - `./scripts/qa_gate_card.sh --e2e [device]`
   - o dotenv local e parseado por allowlist, sem `source`/`eval`; valores com
     command substitution, namespace `CLI_*` ou booleano invalido sao rejeitados;
   - flags de CLI prevalecem sobre o dotenv, e este nao pode desligar um E2E ja
     escolhido pelo operador/processo.
3) Readiness pre-piloto:
   - `python3 scripts/qa_navigation_pre_pilot_readiness.py --check-only`
   - `contractStatus=pass` significa que os contratos locais foram executados;
     `gateExecutionStatus=not_run` deixa explicito que o gate completo nao rodou;
   - sem evidencia externa, `audioHumanConfirmed=false`, `fieldReady=false`,
     `externalPilotReady=false` e a decisao permanece `HOLD`.
4) Evidencia tecnica TTS:
   - somente `--report --tts-evidence-dir <diretorio-explicito>` pode agrega-la;
   - o smoke real vincula source commit/tree, run id, marker e digest do log em
     manifesto tecnico create-only;
   - o SHA-256 bruto impresso pelo produtor deve ser ancorado fora do diretorio
     de output. Digest autocontido e permissao local `0444` detectam acidentes,
     mas nao sao assinatura nem prova adversarial de device fisico.
5) Confirmacao humana:
   - o pacote de revisao valida primeiro o manifesto tecnico;
   - somente uma unica linha canonica com o valor exato `SIM` confirma audio;
     minusculas, frases negativas, texto livre, ausencia ou duplicata ficam
     pendentes.
6) Contrato route-plan:
   - pertence ao workspace coordenador, nao ao gate hermetico do app;
   - deve ser executado separadamente com commits exatos de produtor/consumidor
     e manifesto cujo hash bruto fica ancorado na evidencia do programa.

## Prova funcional de leitura de localizacao
Conceito: `VR-SEC-001-BE-READ` exige observar o service real do app contra o
mesmo backend/JAR provado pela coordenacao. Teste mockado ou probe isolado do app
nao fecha essa ligacao.

1) Contrato do consumidor:
   - `TripLiveLocationService` interpreta `200` como
     `TripLiveLocationRecord` e `204` como ausencia atual (`null`);
   - os cenarios negados pelo backend retornam `404` indistinguivel, e o app os
     converte na mesma `TripLiveLocationException` generica;
   - a fixture de bootstrap usa `companyName` novo por execucao;
     `companySlug` e somente resposta derivada, nunca seletor de tenant.
2) Probe do app:
   - `scripts/qa_trip_live_location_real_backend.sh` executa
     `tool/qa_trip_live_location_real_backend_test.dart` com o service de
     producao e HTTP real;
   - a URL precisa ser `http` loopback com porta explicita;
   - credenciais permanecem no ambiente e nao entram em `--dart-define`, logs
     ou manifest;
   - `attestation_scope=consumer_only` e
     `backend_binding=coordinator_required` sao resultados obrigatorios: o
     probe sozinho nao pode declarar qual processo ou JAR respondeu.
3) Lane coordenada:
   - executar a partir do repositorio raiz com worktrees limpas e refs exatas de
     app/backend/coordenador;
   - o coordenador sobe PostgreSQL descartavel e o JAR exato, expoe somente
     loopback, executa as fases positiva (`200/204`) e negativa
     (`404` + erro generico) e confirma o mesmo digest de JAR antes/depois;
   - o manifest e create-only, inclui commits, trees, blobs, digests, run id e
     cleanup; seu SHA-256 bruto deve ser ancorado fora do diretorio de output;
   - cleanup de processo, container e worktrees precisa terminar em `PASS`.
4) Registro preliminar de 2026-07-17:
   - app `7b5d4d4048db439d9b5179630089658776f150a4`, tree
     `866ebe239fe8c324e6c09659c91940420c8ea90c`;
   - run `location-read-20260717T101750Z-79192`;
   - manifest SHA-256
     `f7ee6ed4b0f58afa5ce5d5461ff43c0c8cafeb4696146c70e1f12692090ea6b8`;
   - payload SHA-256
     `809a10290f98c8393d902bb46b15527320079aabc6a323960f54f8a4a3f55fef`;
   - log SHA-256
     `5d6fe0efe6f2bc66d168b043cdae40689e1be6998879e2fc3de7110958422439`,
     `3655` bytes; cleanup `PASS`.
5) Regra de fechamento:
   - o registro acima nao pode ser reutilizado como prova do patch documental;
   - repetir a lane no SHA documental final e ancorar a prova definitiva em
     `program/evidence/VR-SEC-001-BE-READ.md`, no repositorio coordenador;
   - esta lane nao prova UI, widget, device, fallback visual, WebSocket,
     historico, piloto ou producao. Essas superficies permanecem inalteradas e
     readiness continua `HOLD`.

## Smoke de acessibilidade por card visual
Conceito: todo card que altera experiencia visual precisa registrar uma
evidencia minima de acessibilidade antes de virar `DONE`.

1) Matriz oficial:
   - `backlog/QA-Accessibility-Smoke.md`
2) Suite focada:
   - `flutter test test/features/accessibility/integrated_accessibility_smoke_test.dart`
3) Quando a mudanca tocar motorista, responsavel ou admin, combinar o smoke
   integrado com a suite focada do widget alterado.
4) Criterios minimos:
   - texto aumentado nao pode causar overflow ou esconder CTA essencial;
   - regioes compostas precisam de label semantica clara;
   - status e decisoes nao podem depender apenas de cor;
   - high contrast, accessible navigation e reduced motion precisam preservar a
     informacao principal.
5) Registro:
   - achados corrigidos ficam no QA log do card;
   - achados nao corrigidos viram card rastreavel com severidade e reproducao.

## Smoke de regressao visual controlada
Conceito: screenshots devem ser comparaveis e citaveis, mas o gate rapido nao
pode depender de device, backend ou credenciais.

1) Controle oficial:
   - `backlog/QA-Visual-Regression-Control.md`
2) Baseline/matriz:
   - `backlog/UX-Visual-QA-Matrix-2026-06-19.md`
3) Gate rapido:
   - `./scripts/qa_visual_matrix_smoke.sh --check-only`
4) Capturas:
   - deterministicas: `./scripts/qa_visual_matrix_smoke.sh --run-deterministic`
   - reais DEV/fixture: `./scripts/qa_visual_matrix_smoke.sh --run-real`
5) Evidencias:
   - salvar em `output/visual-smoke/<card-ou-fase>/<timestamp>/`;
   - registrar paths no `backlog/QA-Execution-Log.md`;
   - nao versionar screenshots, videos, fixtures ou logs com credenciais.
6) Falhas:
   - device/fixture/backend ausente = setup ou ambiente;
   - texto cortado, estado visual errado ou sobreposicao em PNG gerado =
     regressao visual.

## Smoke de performance dos fluxos criticos
Conceito: mapa, tracking, sheets e motion precisam ter risco de performance
registrado sem transformar todo card em benchmark pesado.

1) Controle oficial:
   - `backlog/QA-Performance-Control.md`
2) Gate rapido:
   - `./scripts/qa_performance_smoke.sh --check-only`
3) Evidencias pesadas, quando houver UI pesada:
   - `output/performance-smoke/<card-ou-fase>/<timestamp>/`
4) Superficies monitoradas:
   - tracking/mapa do passageiro;
   - sheet da viagem do passageiro;
   - painel de navegacao do motorista;
   - sheet ativa do motorista;
   - localizacao do responsavel;
   - politicas compartilhadas de motion;
   - camada de marcadores/mapa deterministico.
5) Regra de release:
   - jank evidente, carregamento excessivo ou resposta lenta vira correcao no
     mesmo card ou follow-up antes de release;
   - falta de device/profile/backend e setup, nao regressao de performance.
6) Registro:
   - todo card que mexer em mapa, tracking, sheet, motion ou marker deve citar
     as superficies afetadas e o comando/smoke executado no QA log;
   - cards sem UI pesada registram que o check rapido cobriu o contrato e que
     smoke manual/profile nao se aplica.

## Portal Empresa Web (Playwright)
Conceito: o portal administrativo roda em Flutter Web, então a validacao ponta a ponta dele precisa acontecer em navegador real.

1) Pré-condição de backend
   - O request de bootstrap exige `companyName` novo e único por execução.
   - `companySlug` é apenas resposta derivada; nunca deve ser enviado como seletor de tenant.
   - Payload ausente ou `companySlug`-only deve receber `400`; nome de empresa já existente deve receber `409`.
2) Smoke web automatizado
   - `./scripts/qa_admin_portal_web_e2e.sh`
   - O script:
     - gera um `flutter build web` com semântica forçada para E2E
     - serve o build estático localmente
     - abre Chromium headless com Playwright
     - executa bootstrap do primeiro admin por API com empresa única
     - realiza login administrativo
     - abre as áreas principais do portal:
       - Motoristas
       - Passageiros
       - Responsaveis
       - Vinculos
3) Conceito importante
   - Para Web, nosso equivalente de `integration_test` é Playwright.
   - Para Mobile, continuamos com `integration_test`.
   - O gate CI resolve Node em `NODE_BIN`, no `PATH` ou no runtime local do
     Codex sob o cache do usuário; fora desses ambientes, instale Node ou
     informe `NODE_BIN=/caminho/para/node` explicitamente.

## E2E por papel
Conceito: E2E precisa dizer qual papel esta validando e separar setup/fixture
de bug de UX/contrato.

1) Controle oficial:
   - `backlog/QA-E2E-Role-Split.md`
2) Gate rapido:
   - `./scripts/qa_e2e_role_matrix_smoke.sh --check-only`
   - `./scripts/qa_e2e_real_preflight.sh --check-only`
   - `python3 scripts/qa_mobile_e2e_long_run.py --check-only`
3) Evidencias:
   - `output/e2e-role-smoke/<papel>/<timestamp>/`
   - `output/mobile-e2e-long/<papel>/<timestamp>/`
4) Papeis canonicos:
   - `admin-web`: `./scripts/qa_admin_portal_web_e2e.sh`
   - `driver-mobile`: `./scripts/run_dev_e2e.sh integration_test/motorista/all.dart <device>`
   - `passenger-mobile`: `./scripts/run_dev_e2e.sh integration_test/passageiro/all.dart <device>`
   - `guardian-mobile`: `./scripts/run_dev_e2e.sh integration_test/guardian/all.dart <device>`
   - `operational-fixture-mobile`: `./scripts/run_dev_e2e.sh integration_test/operational_fixture/all.dart <device>` via `qa_mobile_e2e_long_run.py`, para prova operacional multi-papel com fixture verificada/vinculada.
5) Preflight operacional antes de piloto:
   - dry-run sem backend/device: `./scripts/qa_e2e_real_preflight.sh --dry-run --role <papel> --device <device> --api <url>`
   - setup real sem Flutter E2E longo: `./scripts/qa_e2e_real_preflight.sh --run-preflight --role <papel> --device <device> --api <url>`
   - para todos os papeis conhecidos, usar `--role all`
   - o modo `--run-preflight` mobile força `VANRIDES_E2E_PREFLIGHT_ONLY=1`
     para checar device, backend, fixture e viagens antes do E2E longo
   - `admin-web` neste preflight valida setup local basico; o E2E web real
     continua sendo `./scripts/qa_admin_portal_web_e2e.sh`
6) Tentativa longa mobile auditavel:
   - dry-run: `python3 scripts/qa_mobile_e2e_long_run.py --dry-run --role <papel> --device <device> --api <url>`
   - execucao: `python3 scripts/qa_mobile_e2e_long_run.py --run-role --role <papel> --device <device> --api <url> --fixture-env <fixture.env>`
   - o runner escreve `SUMMARY.md` e `e2e.log` sem imprimir valores de fixture.
   - use `operational-fixture-mobile` quando o objetivo for provar a jornada
     operacional pre-piloto com a fixture DEV pronta, em vez dos fluxos
     publicos de cadastro/login isolados.
7) Classificacao:
   - `BLOCKED_VALIDATION_*`, device ausente, backend fora, fixture ausente,
     browser/build web ou e-mail nao verificado = setup;
   - `BLOCKED_VALIDATION_IOS_WIRELESS_PUBLISH_PORT` = setup/tooling de iPhone
     wireless no `flutter test`, nao bug do app;
   - preflight verde seguido de falha Flutter/Playwright de comportamento =
     UX/contrato.
8) Registro:
   - citar papel, comando, API/device/browser, pasta de evidencia e
     classificacao no `backlog/QA-Execution-Log.md`;
   - nao fechar card com "E2E falhou" sem dizer se foi setup ou UX/contrato.

## Guardian Mobile MVP
Conceito: o bloco do responsavel fecha quando combinamos tres camadas:
- widget/service tests focados
- smoke real do guardian no device
- evidencia registrada no QA log

### Suite automatizada focada
1) Rodar a suite focada do guardian:
   - `./scripts/qa_guardian_mobile_suite.sh`
2) Se quiser incluir o smoke real no device:
   - `GUARDIAN_E2E_EMAIL=<email> GUARDIAN_E2E_PASSWORD=<senha> GUARDIAN_E2E_PASSAGEIRO=<nome opcional> ./scripts/qa_guardian_mobile_suite.sh`
3) O script cobre:
   - `flutter analyze` focado em guardian
   - widget/service tests do guardian
   - `integration_test/guardian/all.dart` quando credenciais de smoke forem fornecidas

### Smoke manual minimo
1) Portal:
   - criar responsavel com senha inicial
   - vincular responsavel a um passageiro
2) App mobile:
   - login do responsavel
   - validar home com passageiros vinculados
   - abrir `Presencas`
   - abrir `Localizacao`
   - abrir `Conta`
   - sair pelo botao de logout do shell

### Regra de encerramento do bloco
- Se o backend do guardian auth estiver publicado e o smoke manual acima estiver verde, o bloco pode ser fechado mesmo que a suite E2E dependa de credenciais controladas por ambiente.

## Checklist de Smoke (iPhone)
1) Login
   - Motorista: consegue logar e navegar para Home
   - Passageiro: consegue logar e navegar para Home
2) Cadastro
   - Motorista: cadastro cria usuário e permite login
   - Passageiro: cadastro cria usuário e vincula a motorista (quando aplicável)
3) Presenças
   - Passageiro: confirmar presença
   - Motorista: ver presenças do dia
4) Localização (principal)
   - Motorista: iniciar compartilhamento com permissão “Durante o uso”
   - Passageiro: ver mapa com localização inicial e updates via WebSocket

### Localização — permissões (Backlog 0091)
No iPhone (device físico), validar os cenários abaixo no fluxo **Motorista → Compartilhar localização**:
- **Durante o uso do app**: inicia e mantém updates em foreground (sem pedir “Sempre”).
- **Negar permissão**: app exibe mensagem “Permissão de localização negada” e não inicia.
- **Negado permanentemente**: app exibe CTA **“Abrir Ajustes”** e abre configurações.
- **Sempre** (quando habilitado): validar comportamento em background (se o iOS permitir no build atual):
  - iniciar compartilhamento → mandar app para background por ~30s → voltar e confirmar que não “travou”.

## Observabilidade (logs)
- Se a UI não mostrar o erro, usar logs do Flutter (console do `flutter run`) e logs do backend (VPS) para identificar:
  - status HTTP (ex.: `204` em localização inicial)
  - erros de WebSocket
  - exceções de permissões/geolocator

## Checklist do backend (rápido)
- HTTP (rota pública):
  - `curl -sS http://127.0.0.1:8080/motoristas`
- CORS (preflight):
  - `curl -i -X OPTIONS http://127.0.0.1:8080/motoristas -H 'Origin: https://app.exemplo' -H 'Access-Control-Request-Method: GET'`
