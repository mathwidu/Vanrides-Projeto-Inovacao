# Registro de Riscos

| ID | Severidade | Risco | Mitigação principal | Dono | Estado |
|---|---|---|---|---|---|
| R-001 | alta | leitura/escrita indevida de localização e viagem | VR-SEC-001, VR-SEC-001-LOCATION-READ-SCOPE-001, VR-SEC-002, VR-SEC-003 | SEC | mitigando |
| R-002 | alta | sessão expira ou permanece válida sem coordenação/revogação | VR-SES-001-REFRESH-ROTATION-001, VR-SES-001-AUTH-EPOCH-001, VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001, VR-SES-003-APP-PASSWORD-BASIC-REMOVAL-001, VR-SES-003-APP-CREDENTIAL-AND-SECURE-STORAGE-001, VR-SES-002-PUSH-TOKEN-SESSION-LIFECYCLE-001 | SES | aberto |
| R-003 | alta | gate verde testa versão diferente da promovida ou workflow manual do default branch produz imagem/deploy sem intenção explícita | VR-QA-APP-GATE-HERMETIC-001, VR-QA-INTEGRATION-PROVENANCE-001, VR-REL-001-BACKEND-WORKFLOW-DEFAULT-SAFE-001, VR-QA-BE-GATE-PROD-COMPATIBLE-001, VR-QA-BE-GATE-CAPABILITY-SPIKE-001, APP-NAV-AUDIO-PROVENANCE-RERUN-001, VR-REL-001 | QA/REL | mitigando |
| R-004 | alta | jornadas críticas quebram em tamanhos suportados | VR-UX-001 | UX | aberto |
| R-005 | alta | produto inacessível apesar de testes automatizados verdes | VR-A11Y-001, VR-A11Y-002, APP-NAV-AUDIO-CONFIRM-001 | A11Y | aberto |
| R-006 | alta | maps platform pública, degradada ou semanticamente insegura | VR-MAP-001 a VR-MAP-005 | MAP | aberto |
| R-007 | alta | tratamento excessivo ou sem finalidade de GPS/dados de menores | VR-SEC-001-LOCATION-READ-SCOPE-001, VR-SEC-002-CORE-ENTITY-PROJECTIONS-001, VR-SEC-002-REST-MINIMUM-PROJECTIONS-001, VR-MAP-001-LOCATION-S2S-001, VR-PRIV-001-DATA-INVENTORY-PURPOSE-001, VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001, VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001, VR-PGM-004-FUTURE-DATA-COLLECTION-GATE-001 | PRIV | aberto |
| R-008 | alta | perda de dados ou restore não executável | VR-OPS-002-BACKUP-RETENTION-RESTORE-PURGE-001, VR-PRIV-003-INCIDENT-HOLD-DECISION-001 | OPS | aberto |
| R-009 | média | estado em memória impede escala e recuperação | VR-BE-004, VR-BE-005-EMAIL-VERIFICATION-OUTBOX-001, VR-BE-005-NOTIFICATION-OUTBOX-MINIMIZATION-001, VR-OPS-004 | BE/OPS | aberto |
| R-010 | alta | planos, dashboard e backlogs divergem | GOV-PIPELINE-001, GOV-WAITING-STATE-INTEGRITY-001, GOV-DASHBOARD-RUNTIME-FRESHNESS-001, VR-PGM-002 | PGM | mitigando |
| R-011 | média | trabalho preexistente do app contamina cards/release | VR-QA-APP-GATE-HERMETIC-001, isolamento em worktree e validador de dirty paths | PGM/APP | mitigando |
| R-012 | alta | credenciais aparecem em summaries ou evidências | VR-SEC-005-SECRET-SCOPE-ROTATION-001, VR-REL-001-ANDROID-RELEASE-BOUNDARY-001 e sanitização imediata | REL/SEC | aberto |
| R-013 | alta | JAR do backend não inicia na composição padrão sem adapter externo de push | VR-BE-RUNTIME-PUSH-001 | BE | encerrado |
| R-014 | alta | gate permanece verde apesar de testes PostgreSQL skipped por incompatibilidade Docker | VR-QA-TESTCONTAINERS-DOCKER-001, VR-QA-BE-GATE-NO-SKIP-001 | QA/BE | encerrado |
| R-015 | crítica | entrada pública altera identidade pendente ou permite claim administrativo sem prova | VR-SEC-001-BE-READ, VR-SEC-004-EMAIL-NAMESPACE-001, VR-SEC-004-BOOTSTRAP-TRUST-001 | SEC/SES | mitigando |
| R-016 | crítica | rate limit aceita chave controlada pelo cliente, cresce memória sem teto, deixa rotas sensíveis fora da cobertura e aceita corpo sem limite antes do parser | VR-SEC-005-RATE-LIMIT-TRUST-001 | SEC/OPS | mitigando |
| R-017 | crítica | mesmo e-mail pode identificar papéis distintos ou vencer corrida cross-role | VR-SEC-004-EMAIL-NAMESPACE-001, VR-SEC-004-BOOTSTRAP-TRUST-001 | SEC/SES | mitigando |
| R-018 | alta | convite curto, enumerável ou consumido por duas transações cria vínculo indevido | VR-SEC-004-INVITE-REDEMPTION-001, VR-SEC-005-RATE-LIMIT-TRUST-001 | SEC | aberto |
| R-019 | alta | prefetch/preview de GET confirma e-mail sem intenção e consome o token | VR-SES-003-EMAIL-CONFIRM-INTENT-001 | SES/SEC | mitigando |
| R-020 | alta | checagem tenant e hard delete em transações separadas podem excluir passageiro após mudança concorrente de empresa | VR-SEC-002-PASSENGER-DELETE-ATOMIC-001 | SEC/BE | encerrado |
| R-021 | alta | hard delete de passageiro referenciado é rejeitado pelas nove FKs e, sem lifecycle aprovado, dados pessoais podem permanecer indefinidamente ou ser removidos por cascade ad hoc sem finalidade, retenção e prova auditável | VR-PRIV-001-DATA-INVENTORY-PURPOSE-001, VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001, VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001, VR-PRIV-003-INCIDENT-HOLD-DECISION-001, VR-OPS-002-BACKUP-RETENTION-RESTORE-PURGE-001, VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-001, VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-IMPLEMENTATION-001 | PRIV/BE | aberto |
| R-022 | alta | timezone implícito, transições divergentes e estado temporal sem constraints podem autorizar passageiro fora do período correto ou perder atualização concorrente | VR-BE-OPERATIONAL-DATE-ADOPTION-001, VR-BE-ELIGIBILITY-PERIOD-INTEGRITY-001 | BE/SEC/QA | aberto |
| R-023 | alta | dois endpoints de delete de motorista podem deixar ou remover por inferência identidade, sessão, viagens/GPS, outboxes, app local, providers e cópias sem política ou reconciliação completas | VR-PRIV-003-DRIVER-DATA-LIFECYCLE-001, VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001, VR-PRIV-003-INCIDENT-HOLD-DECISION-001 | PRIV/BE/SES | aberto |
| R-024 | alta | delete de responsável apaga somente a linha guardian após checar vínculos e pode deixar sessão, tickets, device/outboxes, avatar, backups ou audiência GPS órfãos | VR-PRIV-003-GUARDIAN-DATA-LIFECYCLE-001, VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001, VR-PRIV-003-INCIDENT-HOLD-DECISION-001 | PRIV/BE/SES | aberto |
| R-025 | alta | conta COMPANY_ADMIN não possui offboarding/transferência; autoridade, credenciais, referências de reviewer/creator e cópias podem persistir indefinidamente | VR-PRIV-003-COMPANY-ADMIN-DATA-LIFECYCLE-001, VR-SEC-004-BOOTSTRAP-TRUST-001 | PRIV/SEC/BE | aberto |
| R-026 | alta | tenant criado no bootstrap não possui lifecycle; cascade ou fechamento parcial pode apagar membros/operação indevidamente, manter coleta ativa ou tornar restore/purge incoerentes | VR-PRIV-003-TENANT-OFFBOARDING-001, VR-OPS-002-BACKUP-RETENTION-RESTORE-PURGE-001 | PRIV/OPS/BE | aberto |
| R-WA-01 | alta | GPS interrompido em background/MIUI/iOS ou coleta persiste após expiração | VR-WA-020-GPS-SPIKE, VR-WA-030-GPS-PROOF, VR-WA-060-DRIVER-APP, VR-WA-180-E2E-DEVICES | APP/QA | aberto |
| R-WA-02 | alta | proximidade geográfica produz aviso/fim incorreto em ruas paralelas ou sequência ambígua | VR-WA-030-GPS-PROOF, VR-WA-130-APPROACH, VR-WA-140-FINISH-SUPPORT | BE/QA | aberto |
| R-WA-03 | alta | bootstrap/link/LLM troca ator ou expõe credenciais/dados | VR-WA-040-DOMAIN-AUTH, VR-WA-060-DRIVER-APP, VR-WA-070-AGENT-WORKFLOWS, VR-WA-150-OPERATIONS | SEC/BE/APP | aberto |
| R-WA-04 | alta | callback/retry/UNKNOWN provoca duplicação, perda ou aviso vencido | VR-WA-050-WHATSAPP-TRANSPORT, VR-WA-120-DELIVERY-RECOVERY, VR-WA-180-E2E-DEVICES | BE/OPS | aberto |
| R-WA-05 | alta | assinatura gratuita iOS expira ou capabilities/links diferem do planejado | VR-WA-010-TOOLCHAINS, VR-WA-060-DRIVER-APP, VR-WA-190-HANDOFF | APP/REL | aberto |
| R-WA-06 | alta | versão local diverge da VPS, push publica indevidamente ou teste afeta serviço compartilhado | VR-WA-000-PREFLIGHT, VR-WA-150-OPERATIONS, VR-WA-160-RELEASE-PREP, VR-WA-170-TEST-ENVIRONMENT | REL/OPS | aberto |

## Regras

- risco alto aberto bloqueia G6, salvo waiver humano válido;
- todo risco deve apontar para mitigação executável;
- risco sem dono é erro de governança;
- evidência pode reduzir probabilidade ou impacto, mas não apaga histórico;
- risco encerrado registra card, commit e evidência que sustentam o encerramento.

`R-010` também cobre o conflito observado entre o dashboard gerado pelo harness
legado e o dashboard canônico de `scripts/program_control.py`. A reconciliação
em `VR-PGM-002` deve estabelecer um único escritor ou separar os dois artefatos.
`GOV-WAITING-STATE-INTEGRITY-001` adiciona o ratchet que recusa blockers,
contrato ou DoR inválidos em qualquer estado executável e faz `next` validar a
fonte estática completa. Freshness dos dados runtime do dashboard foi separado
em `GOV-DASHBOARD-RUNTIME-FRESHNESS-001`, ainda `analyzed`; portanto R-010 não
é encerrado.

`R-016` teve sua mitigação local concluída em 2026-07-17 no backend
`46cd5e8a8dc7d2a3f76771fb7d65aba286a71d06`; a prova canônica está em
`program/evidence/VR-SEC-005-RATE-LIMIT-TRUST-001.md`. A implementação usa
confiança de proxy vazia, store limitado sem eviction de contador ativo, body
limit anterior a auth e métricas sem identificadores. O risco permanece
`mitigando`: não pode ser encerrado nem promovido enquanto configuração real do
Traefik, CIDRs/subnet/peers, ausência de `forwardedHeaders.insecure`, portas
diretas, buffering/deadline total, HTTP/2, réplica única e smoke pelo ingress da
mesma imagem não forem comprovados.

`R-013` foi encerrado pelo commit backend
`117b8ca27353164814b5af602dba5f97d23fb655` e pela evidência
`program/evidence/VR-BE-RUNTIME-PUSH-001.md`. O encerramento cobre o boot padrão
com fallback noop; não declara provider real de push pronto para produção. A
ativação real permanece separada em
`VR-BE-005-PUSH-PROVIDER-ACTIVATION-001`, dependente de D18 e D19.

`R-014` havia sido encerrado pelos commits backend
`e92787239181e39cacd5b7341dc6e0874b2f9fe1` a
`ab68d763fec761dc943af08350a6e0a2b97592f7`, pelo registro de QA
`2de5dedacaeef78129608d62f412b362cd7da927` e pela evidência
`program/evidence/VR-QA-TESTCONTAINERS-DOCKER-001.md`. O encerramento cobre o
wiring versionado e a execução local comprovada daquela matriz. Em 2026-07-17,
o gate geral voltou a terminar `PASS` com 13 testes PostgreSQL skipped por
negociação de API Docker, embora o perfil estrito separado tenha passado os
13/13 no mesmo SHA. O risco voltou para `mitigando` e a lacuna residual está em
`VR-QA-BE-GATE-NO-SKIP-001`. No commit documental final
`e93a69f2ee1caac4e3d1e57561d05ae6921390f6`, o perfil estrito voltou a passar
os 13/13, sem skip/falha/erro, no run
`integration-20260717T102931Z-96231`; isso confirma o substituto local, mas não
corrigia o falso verde do gate geral.

Em 2026-07-17, `VR-QA-BE-GATE-NO-SKIP-001` encerrou `R-014` no escopo
estrutural/local. O backend final
`528a4c40d4eb76c1eacf66334ba3b51a38eef650` executou o gate canônico limpo
com 605 testes em 116 XMLs e matriz PostgreSQL 13/13, o perfil focado 13/13 e
RBAC 120/120, todos sem skip/falha/erro. O gate local por card e a lane
cross-repo da mesma versão também passaram; manifests, hashes XML, cleanup e
limites estão em
`program/evidence/VR-QA-BE-GATE-NO-SKIP-001.md`. A revisão independente
retornou `GO`, e o checkpoint coordenador
`29257a35a22128a4937bae4cd8dcf9f63c7e9098` congelou a evidência antes da
transição atômica do card para `done`. Esse encerramento não declara runner
remoto, release, piloto ou produção prontos; essas promoções continuam em
gates próprios e `HOLD`.

`R-003` e `R-011` foram elevados a mitigação ativa após a reprodução do gate do
app em checkout puro: o SHA `e716732efe5853731dab6ed401bad981cea09a70` falhava
sem evidência ignorada em `output/` e sem script externo no diretório pai. Em
2026-07-17, `VR-QA-APP-GATE-HERMETIC-001` comprovou no SHA final
`b84c7d547be2237a0c0ae4f0cf3a0a0128fc40fe` os dois gates em worktree isolado,
com logs ancorados em
`program/evidence/VR-QA-APP-GATE-HERMETIC-001.md`; o commit coordenador
`d209ab223268bd63a190558a1b34175616e198d0` também passou 13 regressões de
proveniência e gerou manifest cross-repo com anchor externo. A lacuna local que
bloqueava `VR-SEC-001-BE-READ` está controlada. Os riscos permanecem
`mitigando`, não encerrados, porque promoção same-version e isolamento de
release ainda dependem de `VR-REL-001`, e o trabalho de vídeo preexistente segue
preservado no worktree principal do app. O freeze documental final do app
`b53296b67dd0dc6ddb4b1125dcc64caadb8c7c6c` repetiu o gate em checkout limpo:
wiring 15/15, probe 11/11, Flutter 689/689, analyze e ratchet `PASS`; o log
`83eb17bc5c667c89006e5196da62b670fce035c97c9fd2cf0f71aa374345f290`
mantém explicitamente piloto, campo, device e confirmação humana em `HOLD`.

Em 2026-07-18, `VR-QA-INTEGRATION-PROVENANCE-001` fortaleceu a mitigação de
`R-003` no backend final
`6efdb61846d0e4dcc59fd5cab921d275ad9eca98`: lifecycle
PREPARED/PASS/FAIL atômico, lock compartilhado recuperável, fingerprint da
fonte Git, XML sanitizado e retenção segura passaram `130/130` testes focados.
No mesmo SHA/tree limpos, o gate canônico passou Maven `944/944` e PostgreSQL
`49/49`; o smoke HTTP passou no mesmo JAR, com cleanup `PASS`. Manifestos,
hashes e limites estão em
`program/evidence/VR-QA-INTEGRATION-PROVENANCE-001.md`. O risco permanece
`mitigando`, não encerrado. Em 2026-07-29, a task orquestradora
`019f6950-6e3e-78e2-8cb0-724e2c4c1cad` autorizou excepcionalmente publicar
esse SHA somente na branch temporária e disparar uma run manual; a task
`019fadc7-1d8f-7eb1-a795-a14ddb6716ab` executou a autorização. A run
`30450775307`, attempt `1`, falhou antes do Maven porque os testes Python
anteriores ao preflight criaram três arquivos `scripts/__pycache__/*.pyc`;
`build_and_push` e `deploy_swarm` ficaram `skipped`, e o artifact diagnóstico
sanitizado confirmou `status=failed` e `eligibleForRelease=false`.

Em 2026-07-29, a geração de bytecode dentro da worktree foi corrigida e
validada localmente na branch `codex/vr-qa-provenance-bytecode-clean`, backend
`e304199b1359a44ba15b441d85c5363404528b6f`, tree
`5f4a4c737d23839af5774c7fdc64d48a91a8076e`. O gate canônico passou Maven
`944/944`, PostgreSQL `49/49` e smoke HTTP `200/200/400` no mesmo JAR; o
manifesto schema v2 registrou fonte limpa, zero dirty path e SHA/tree estáveis.

A prova remota final do mesmo SHA ocorreu na run `30466145519`, attempt `1`,
branch `codex/qa-integration-provenance-e304199`: `test=success`,
`build_and_push=skipped` e `deploy_swarm=skipped`. O artifact
`8729735066`, digest
`sha256:60913f2531479bb0475e2a8765a490bd8a58ab958e786e480379409ed58331fc`,
passou schema v2, source clean, Maven `944/944`, PostgreSQL `49/49`, hashes dos
160 XMLs e sanitização. `R-003` permanece `mitigando`, não encerrado: o
workflow do default branch `prod`, digest
`f3e0551e1e3dae62e647abab130614a478066eb0399b1b1024e3eb7d64cb735a`,
ainda não contém guards de evento equivalentes nos jobs mutáveis e é dívida
prioritária de segurança de release. Esta task não o corrigiu nem criou novo
card/contagem. Promoção por digest permanece separada em `VR-REL-001`; retry,
remoção das branches diagnósticas, push em `dev`/`prod`, force-push, PR, merge
e deploy continuam proibidos.

Em 2026-07-29,
`VR-REL-001-BACKEND-WORKFLOW-DEFAULT-SAFE-001` foi rebaixado de `ready` para
`analyzed` após validação independente da primeira proposta, para tratar a
dívida ainda aberta no default branch sem prontidão artificial. A análise
revalidou `prod@7231574ec15f91adad1ab8382a5a97d565b922dc`, workflow ativo
`241444909`, blob `395ce850c962fca562ca833444e80bc1edb7ae4a`,
`7.507` bytes e SHA-256
`f3e0551e1e3dae62e647abab130614a478066eb0399b1b1024e3eb7d64cb735a`.
O YAML aceita `workflow_dispatch`, concede `packages: write` no topo e não
guarda `build_and_push`/`deploy_swarm` por evento/branch. A implementação
futura deve partir exclusivamente do SHA de `prod` e separa Gate A local,
Gate B de landing com o workflow individual pausado e Gate C de dispatch não
mutante. O card não está `ready`: `prod` não contém o gate oficial
`./scripts/qa_gate_card.sh`, e Maven cru usa profile local/PostgreSQL com
`ddl-auto=update`. É necessário refreeze com backport mínimo do gate ou waiver
humano para substituto hermético, PostgreSQL efêmero, credenciais sintéticas e
cleanup. A autorização atual cobre somente este refreeze documental; não aprova
waiver, expansão de paths ou Gate A/B/C. Nenhum gate foi autorizado ou
executado. `R-003` permanece `mitigando`; push/merge em `prod`, dispatch
default, release, piloto e produção permanecem `HOLD`.

Na correção bounded v3 do mesmo dia, o inventário A foi retificado para **ao
menos 166 candidatos conhecidos, ainda não fechado**. Além dos 15 roots + seis
helpers documentados, `--all-tests` descobre o
`VanridesApplicationTests.java` legado de `prod`, que usa
`@SpringBootTest`, profile local/localhost e `ddl-auto=update`. O path
`EmailVerificationLandingPageRenderer.java` também é dependência literal sem
guard, presente em `e304199` e ausente em `prod`.
`backlog/SpringBoot-Cards.md` é leitura opcional protegida por `exists()` e
`application-prod.properties` é contexto conservador, não aresta obrigatória
observada. A continua `A_CLOSURE_NOT_CLOSED`; os ao menos 166 candidatos não
são ordem de cópia nem justificam importar os 419 commits.

A revalidação do commit `25117518...` rejeitou B como contrato fechado. O
suposto adapter de três paths é shallow: sua interface força coordenação,
workflow e operadores a conhecer PKI/trust root, ledger/CAS, distribuição,
binding por evento e enforcement de rede. Sete grupos permanecem abertos:

- `AUTH_ROOT_AND_REPLAY_UNRESOLVED`: trust root autoancorado, signer/tool/schema
  indefinidos, claim histórico reutilizável, claims irmãos, autorreferência e
  ausência de ref canônica/CAS/lease/predecessor único;
- `REMOTE_DISPATCH_AT_MOST_ONCE_UNRESOLVED`: bearer/nonce reutilizável em dois
  dispatches e ausência de shared state/idempotency/concurrency/censo;
- `CLAIM_VALIDITY_CONTRADICTION`: claim encerra a validade que o runner
  pós-claim exige ativa;
- `CI_EVENT_BINDING_UNRESOLVED`: PR/push não podem satisfazer o binding fixo do
  hotfix;
- `MAVEN_BUNDLE_AND_BOOTSTRAP_UNRESOLVED`: bundle sem produtor/formato/object/
  storage/canais e toolchain/verifiers/monitor incompletamente bound;
- `PREBINDING_TEST_CLOSURE_UNPROVEN`: focused/full Maven, Surefire/runtime e
  PostgreSQL só surgiriam depois do claim;
- `NETWORK_ENFORCEMENT_UNRESOLVED`: não há primitiva/default-deny/start barrier/
  loss counters/prova contra conexões curtas.

No snapshot histórico superseded de 2026-07-29, o card retornava a `analyzed`
com `ROUTE_SELECTION_NOT_OPEN`, `EXECUTION_NOT_AUTHORIZED`,
`allowedDirtyPaths.backend=[]` e Gate A/B/C `NOT_RUN / NOT_AUTHORIZED`. B foi
depois rejeitada pela seleção da Rota A e nunca implementada.

Snapshot histórico de 2026-07-29, superseded pela seleção abaixo: a
recomendação técnica era o investimento permanente A pelo card existente
`VR-QA-BE-GATE-PROD-COMPATIBLE-001`, ainda `analyzed`, sem seleção humana
naquele snapshot. O desenho de rollback B permaneceu rejeitado e nunca foi
implementado. `R-003` continuou `mitigando`.

Em 2026-07-30, Matheus selecionou explicitamente a Rota A. O card
`VR-QA-BE-GATE-PROD-COMPATIBLE-001` foi congelado como investimento permanente,
enquanto `VR-QA-BE-GATE-CAPABILITY-SPIKE-001` abriu uma única prova local
bounded sobre o SHA remoto exato de `prod`. B, waiver e adapter temporário
permanecem rejeitados. A spike não aprova Gate A/B/C, não altera produto ou
schema e não muda Pilot/Production de `HOLD`.

A spike backend inicial `2cd9c279aeea56ce6a535734bb4575f00b02fae1`,
parent direto de `prod@7231574...`, recebeu a correção linear
`b15837c5ec3ebf4e73ae546efe2d00704947322c` e a correção runtime
`fbddba1847e13707b7301b6afdc5a71122e56075`, seguidas pela correção de
protocolos `29f2309c92cf8d43ecd350d997c577069ea50cb2` e pela correção de
input/lifecycle `69aa6c34baa18918c0bdd04bc4d84207008da148`, seguida pelo snapshot
único de evidência `b58942d914c16dc7fd4e413c7949c59e50587811` e pelo cleanup
conservador `31c0dbbd99c2c3cb810711fd7d8b84ae02feefab`, pelo receipt pré-execução
`8ad2fca45f1c7e50b010aaaa4647c3f32e7b205a` e pelo receipt pós-release
`66301f5686487f54ed3b3f1077a131eb18b162da`, seguida pela correção de
lifecycle/inteiros/control-flow
`e3ea8a8c45c3aa5fb8c87b1c12b67637d8848b29`; o HEAD passou `63/63`
testes em ambiente normal e `63/63` com `env -i`. Ela provou que snapshots
únicos de request/output, bindings, lifecycle, lock real, manifesto imutável,
contenção de `Exception`, cleanup de acquire conservador, ownership/liberação
com receipts booleanos exatos, guard desde o primeiro efeito pós-ownership,
contagens serializáveis limitadas e cleanup/release antes de relançar a primeira
`BaseException` podem ficar no core; os papéis
futuros de Git/env, Maven, Docker, PostgreSQL e smoke foram exercitados por
cinco doubles sintéticos. A
classificação dos 166 candidatos permaneceu `reuse=1`,
`rewrite=12`, `adapt=1`, `exclude=152`. `R-003` continua `mitigando`:
adapters reais, bindings por digest e equivalência ambiental local/CI não
foram executados. O card bounded foi concluído sem WIP residual; o programa
passou a `16/70 = 22,9%`, enquanto os gates permaneceram `1/8 = 12,5%`.

`R-001` passou de `aberto` para `mitigando` com as duas fatias REST de
localização. A leitura final foi comprovada nos commits backend
`e93a69f2ee1caac4e3d1e57561d05ae6921390f6`, app
`b53296b67dd0dc6ddb4b1125dcc64caadb8c7c6c` e coordenador
`9893babc9aa4c25f33b6354804b12a5d717ec4dd`, pelo manifest externo
`ffb324e6a22b90049bfc19c27d96b70174a561cc767afdf3bdc1ad2b40d27e94`.
O risco não está encerrado: histórico/latest temporal, realtime/STOMP,
contrato trip-scoped e política central por objeto continuam em
`VR-SEC-001-LOCATION-READ-SCOPE-001`, `VR-SEC-002` e `VR-SEC-003`.

Em 2026-07-18, o freeze de prontidão desse sucessor confirmou que a lacuna é
cross-repo e mais ampla que o REST: STOMP aceita subscriptions por tenant sem
autorizar a viagem/objeto, o app filtra mensagens depois de recebê-las, o
snapshot pode carregar dados de outro passageiro e, quando o adapter está
habilitado, o backend pode enviar coordenadas/labels ao route planner de Maps
sem autenticação de serviço. A
A revisão histórica da ADR-005 propôs janela `IN_PROGRESS`, lease, retenção
ativa, backup/PITR, autorização STOMP e Maps interno, mas foi marcada
`SUPERSEDED / DO_NOT_ACCEPT` porque reservava migrations antes do freeze de
`ready`. Esses valores permanecem apenas candidatos com proveniência; o card
está `analyzed / REFREEZE_REQUIRED` e não há resposta humana válida até nova
ADR/DOR/revisão independente imutáveis. Como nenhum controle foi implementado,
este registro não reduz R-001 nem R-007 e G1/G6 continuam em `HOLD` para GPS
trip-scoped. O broker/user registry STOMP segue local à JVM; a entrega
single-replica não encerra R-009, e escala horizontal continua condicionada a
`VR-OPS-004` como gate de promoção separado. Backup/restore ainda depende de
`VR-OPS-002` e mantém R-008 aberto.

`R-015` permanece `mitigando`. `VR-SEC-001-BE-READ` fechou mass assignment,
retry de identidade pendente e claim anônimo por slug nas três entradas
públicas, com `400/409` sem mutação e unicidade concorrente no PostgreSQL.
`VR-SEC-004-EMAIL-NAMESPACE-001` acrescentou autoridade global local e rejeição
genérica de adoção por e-mail. A prova de posse do bootstrap de empresa
preexistente continua em `VR-SEC-004-BOOTSTRAP-TRUST-001`.

`R-017` passou de `aberto` para `mitigando` no escopo estrutural/local com o
backend `4824f54abab0ad978502cb2e056115c5c9348a28`: V25, quatro papéis,
writers antigos, troca/delete, device, refresh e confirmação concorrente foram
provados no gate `776/776`, matriz PostgreSQL `41/41`, RBAC `156/156` e smoke
same-JAR. O risco não está encerrado porque nenhum banco persistente recebeu
preflight ou promoção da V25, e a confiança do bootstrap continua em
`VR-SEC-004-BOOTSTRAP-TRUST-001`. Piloto e produção permanecem `HOLD`.

`R-019` passou de `aberto` para `mitigando` no escopo automatizado/local com o
backend `b2dd00cf1e4e7cbfe6a7d50d606ee948d8b7f871`, tree
`f79e34a1c96939979046626e7700628fc7b09875` e JAR SHA-256
`0e5ea6c61ca0277d74736dbc74fc6149de598d88693217420ebdc66087946e7f`.
GET e HEAD são não mutantes, o segredo chega no fragmento e é removido da URL,
e somente um POST explícito pode confirmar. Gate, concorrência, smoke same-JAR,
replay, respostas adversas e reflow estão registrados em
`program/evidence/VR-SES-003-EMAIL-CONFIRM-INTENT-001.md`. O risco não é
encerrado enquanto Tab, Enter, Espaço e VoiceOver não forem confirmados
humanamente no mesmo artefato; perda ou alteração desse JAR invalida a prova
sensorial pendente. Piloto e produção permanecem `HOLD`.

`R-020` foi encerrado no escopo estrutural/local pelo backend
`6efd2e2f66cb5ac3784085d317ba98174750c815`, tree
`142c51caebbc0eca9b93ed828c78924963c7f7c6`. A prova canônica está em
`program/evidence/VR-SEC-002-PASSENGER-DELETE-ATOMIC-001.md`: DML única por
`id + company_id`, 921/921 testes, matriz PostgreSQL 46/46 e smoke same-JAR
`PASS`. O checkpoint coordenador versionado é
`0b316fa4d307c7250bf5b207c7748b550ad0e175`. O encerramento não resolve
`R-021`, não prova deploy e não autoriza piloto ou produção.

`R-021` foi identificado durante
`VR-SEC-002-PASSENGER-DELETE-ATOMIC-001`. O rollback integral por SQLSTATE
`23503` é a atomicidade correta, mas não define como atender uma solicitação
legítima de lifecycle quando existem referências em `presenca`,
`guardian_passageiro`, `rota_base_parada`, `viagem_parada`,
`company_link_request`, `company_invite_code_usage`, `servico_passageiro`,
`presenca_servico` e `support_ticket`. Nenhuma cascade, anonimização, retenção
ou liberação de e-mail será inferida antes de ADR aceita. O checkpoint atual
foi corrigido para decisão somente, com ADR-002
`PROPOSED / INCOMPLETE / NOT_ACCEPTED` e DOR
`NOT_READY`. No estado atual, `VR-PRIV-001` está `done` com inventário técnico
congelado, e `VR-PRIV-002` está `in_progress/active` preparando a matriz de
minimização, decisões humanas e auditoria sem abrir paths de produto.
A implementação foi separada
em `VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-IMPLEMENTATION-001`, permanece
`analyzed` e não possui allowlist de produto até a decisão aceita. O risco
permanece aberto e G6/G7 em `HOLD` até política, implementação e evidência
same-version.

Em 2026-07-18, `VR-PRIV-001-DATA-INVENTORY-PURPOSE-001` concluiu o
inventário técnico read-only no commit coordenador
`a3f7140e0a25fe5c440700146a42401500a87e4a`, tree
`64cadefba19a3343d5423a46d70da3203f96f0a6`. O inventário tem SHA-256
`d1dc70c65a0d03ff23950c55c6433378b4294a31c896fd095764dfe1d4be9891`,
34 famílias e 19 findings: 11 P1 e oito P2. Três revisões independentes
retornaram zero residual P1/P2, e 17 cards verticais `analyzed` foram criados
para lacunas sem fatia canônica anterior.

O roteamento cobre sessão/storage, push, fila offline, deep link, projeções
REST/GUA/DRV, diretório público, logs, permissões/release mobile, mídia, Maps,
evidência, retenção cross-family, backup e avatar. Os 17 cards novos mantêm
allowlists de produto vazias até o freeze de seus DoR; cards canônicos
preexistentes preservam allowlists e dependências já governadas. Esse
inventário reduz desconhecimento, mas não aprova finalidade, base,
consentimento, retenção ou exclusão; portanto
não reduz `R-007`, não reduz `R-021` e não muda G6/G7.
`VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` foi concluído sem reduzir esses
riscos ou autorizar promoção, usando o commit/digest imutável registrado na
evidência canônica.

Durante PRIV-002, quatorze gaps adicionais foram separados em cards `analyzed`:
coordenador de sessão, remoção de senha/Basic, projeções centrais, suporte,
outbox, ativação real de push, governança global de segredos, gate de coleta
futura, bootstrap verificável da evidência v2, incident hold e lifecycle/delete de motorista, responsável,
administrador da empresa e tenant. O freeze agora cobre 31
sucessores com allowlists de produto vazias. Os riscos `R-023` a `R-026`
preservam esses quatro lifecycle gaps como trabalho explícito, sem inferir
cascade, anonimização, transferência ou purge. Isso
melhora ownership e dependências, mas não encerra R-002/R-007/R-008/R-009/
R-012/R-021, não registra aceite humano e mantém G6/G7 em `HOLD`.

`R-022` foi identificado durante a prontidão de
`VR-BE-OPERATIONAL-DATE-ADOPTION-001`. O dashboard e dois caminhos de gestão
de `servico_passageiro` ainda dependem do dia da JVM; assignment pode criar sem
`dataInicio`, e reativar preservando um início antigo enquanto limpa o fim
reabre retroativamente o gap inativo. Presença, paradas e provisionamento de
viagens consomem essa decisão. V16 também admite início/fim nulos, não protege
coerência temporal ou tenant e migrou legado com início nulo; replaces não têm
proteção contra lost update. A ADR-003 e o DOR separam writer adoption da
entrega de migration/constraints/concorrência. O risco permanece aberto e
bloqueia G6: nenhuma política de legado, backfill, histórico ou promoção será
inferida sem decisão humana, preflight sanitizado, gate e smoke da mesma
versão.

## Acompanhamento R-WA-01 — 30/10/2026

O ensaio estacionado do coletor0218327 confirmou GPS real nos dois aparelhos.
No iPhone,15min bloqueado mantiveram frescor nas consultas; no Redmi, posições
válidas ficaram intermitentes em segundo plano e também com o app aberto.
R-WA-01 permanece aberto, sem atribuir causa à MIUI nem calibrar limiares para
produzir aprovação. A próxima revisão expõe metadados sanitizados de idade e
precisão e marcos nativos de parada; precisa de novo build e repetição física.
A chamada de parada iOS no lease foi observada, mas rede/percursos/bateria e
condições de tela/boot ainda têm aceites próprios. Ver a
[evidência030](evidence/VR-WA-030-GPS-PROOF.md).

Em 31/10, a mesma revisão03 apresentou posição inicial após reboot, mas voltou
a STALE e terminou a janela de 182,2 s sem novos recibos. Credencial renovada,
rede, relógio e foreground foram conferidos. O risco permanece aberto. Ensaio
Android B retirou a espera inicial e C manteve um pedido finito único; ambos
falharam em produzir posição elegível nas respectivas janelas de180s. O coletor
original foi restaurado e instalado como build06. Não se reduziram os limiares.
Metadados globais não comprovam defeito de hardware nem causa MIUI; está em
preparação um diagnóstico temporário de GNSS direto. Registro específico em
[aquisição Android](evidence/VR-WA-030-GPS-PROOF-android-acquisition.json).

Atualização posterior de31/10: GNSS direto produziu166 posições elegíveis em180s;
A06 perdeu continuidade. D07/FLP1s manteve15min de Home/FRESH, recuperou a mesma
sessão em14s após interrupção curta e comprovou parada nativa52ms após deadline
offline. Force-stop e revogação de permissão interromperam os envios; a tela de
laboratório reconciliou o servidor explicitamente. Aquisição inicial permanece
inconsistente, com rodadas acima30s. USB/estacionado não validam bateria/rota;
risco permanece aberto e D07 é candidato, não M1 aprovado.

O proprietário pausou o iPhone físico. A auditoria host da automação encontrou
marcadores fictícios em logs AX/diagnósticos XCTest: credenciais reais continuam
bloqueadas nesse executor. Não houve exposição de credencial real nesse ensaio.
Preparação iOS e evidências em
[automação030](evidence/VR-WA-030-GPS-PROOF-ios-automation.md). A
[ADR-007](decisions/ADR-007-whatsapp-android-first-development.md) permite
desenvolvimento independente, preservando030 como gate obrigatório antes160/180.
