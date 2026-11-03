# VR-QA-BE-GATE-NO-SKIP-001 — Impedir falso verde de integração no gate geral do backend

## Resultado esperado

O gate geral do backend não termina `PASS` quando a matriz PostgreSQL obrigatória
é pulada por incompatibilidade ou indisponibilidade do Docker. O resultado deve
ser executado com zero skips ou falhar de forma explícita e acionável.

## Estado

`done` — implementação, gates, smoke same-version, evidência e revisão
independente concluídos; lifecycle fechado no checkpoint coordenador seguinte.

## Risco ou objetivo associado

`R-014`, épico `VR-QA-001` e integridade de release.

## Contexto

No SHA `38b5489fb20af694b2b2d7f0eabab6e506b90ea1`, o
`ci_quality_gate.sh` terminou verde com `605` testes e `13` skips após o cliente
Testcontainers negociar uma API Docker antiga. O perfil estrito
`qa_integration_profile.sh` executou depois com API `1.44` e provou os `13/13`,
mas o gate geral isolado ainda satisfaz exatamente o falso verde descrito por
`R-014`.

## Impact Analysis

### Mudanças técnicas

- tornar a lane PostgreSQL obrigatória e zero-skip no gate canônico;
- reutilizar o preflight/negociação de API do perfil estrito, sem duplicar a
  lógica de forma divergente;
- fazer `ci_quality_gate.sh` e `qa_gate_card.sh` delegarem uma única execução
  ampla ao perfil estrito em modo explícito `--all-tests`;
- remover do workflow a segunda execução redundante do perfil focado;
- rejeitar argumentos desconhecidos e validar formato/faixa da API Docker,
  sem fallback silencioso;
- testar Docker ausente, daemon indisponível, API inválida/incompatível, skip,
  relatório residual e execução verde real;
- descobrir nos fontes todas as classes `disabledWithoutDocker` e exigir que a
  matriz de evidência permaneça exatamente sincronizada;
- no modo amplo, validar zero skips em todos os relatórios Surefire do run, não
  apenas nas quatro classes atualmente conhecidas;
- preservar o lock compartilhado de evidência sob `INT`/`TERM`, com saída não
  zero tanto no perfil PostgreSQL quanto na suíte RBAC;
- rejeitar `DOCKER_HOST`/`DOCKER_CONTEXT` herdados e contextos cujo endpoint não
  seja `unix://` ou `npipe://`, antes de qualquer operação no daemon;
- registrar resultado e proveniência sem depender de relatório Maven residual.

### Contratos afetados

CLI/exit code dos scripts de QA do backend. Nenhum contrato de runtime muda.

### Riscos

Falso verde, duplicação cara de testes, flakiness de Docker e divergência entre
gate local/CI.

### Mitigação

Um único perfil estrito compartilhado pelos entrypoints, lock de evidência,
matriz exata, zero skips, parser explícito e testes de wiring/negativos.

### Validação mínima

- `python3 scripts/ci_quality_gate_test.py`;
- cenários Docker ausente/daemon indisponível/API inválida ou incompatível
  falham antes do Maven;
- `./scripts/ci_quality_gate.sh` executa a matriz PostgreSQL com zero skips;
- `qa_gate_card.sh` usa o mesmo caminho estrito antes do smoke remoto;
- `VANRIDES_QA_REQUIRE_CLEAN=1 ./scripts/qa_integration_profile.sh` passa;
- QA Log e evidência registram SHA/tree/run-id.

### Rollback

Reverter o commit do backend; manter `R-014` aberto e qualquer promoção em
`HOLD` até uma lane estrita substituta estar comprovada.

## Escopo

- gates geral e local, perfil Testcontainers e testes de wiring;
- workflow CI/CD, setup Codex Cloud e documentação operacional que ainda
  descreve a lane antiga tolerante a skips;
- QA Log, backlog e evidência.

## Fora de escopo

- mudar testes de domínio ou banco;
- operar runner remoto, deploy ou produção.

## Dependências

- `VR-QA-TESTCONTAINERS-DOCKER-001` concluído;
- executar após a retomada e conclusão de `VR-SEC-001-BE-READ`.

## Definition of Ready

- [x] falso verde reproduzido e contagens registradas;
- [x] perfil estrito substituto comprovado no mesmo SHA;
- [x] escopo, riscos, rollback e critérios definidos;
- [x] selecionar o card como próximo WIP após as dependências, preservando o
  checkpoint idle antes da ativação.

## Critérios de aceite

- [x] O gate geral falha se qualquer teste PostgreSQL obrigatório for skipped.
- [x] O gate local por card usa o mesmo caminho fail-closed antes do smoke.
- [x] Docker indisponível ou API incompatível produz diagnóstico acionável e
  exit code não zero.
- [x] O caminho verde executa os `13/13` testes de integração, sem relatórios
  residuais e no SHA/tree declarados.
- [x] Não há lógica divergente de negociação de API entre os dois scripts.
- [x] O workflow executa uma única suíte Maven canônica e não repete o perfil
  PostgreSQL em um segundo passo.
- [x] Argumentos desconhecidos e override de API malformado ou fora da faixa
  suportada falham antes do Maven.
- [x] Uma nova classe Testcontainers tolerante a Docker ausente não pode ficar
  silenciosamente fora da matriz obrigatória.
- [x] O modo amplo rejeita qualquer relatório Surefire skipped, inclusive de
  classe futura, e registra o total global validado no manifesto.
- [x] `INT`/`TERM` não podem liberar o lock e deixar a suíte continuar; os
  adapters PostgreSQL e RBAC encerram com código de sinal e cleanup via `EXIT`.
- [x] O gate nunca usa daemon Docker remoto por ambiente/contexto implícito;
  somente transporte local inspecionado é aceito antes de `docker info`.
- [x] Testes negativos protegem skip, relatório antigo e Docker ausente.
- [x] Backlog, QA Log, card, evidência e risco ficam sincronizados.

## Ativação

- ativado em 2026-07-17 como único WIP global;
- baseline backend limpo:
  `e93a69f2ee1caac4e3d1e57561d05ae6921390f6`;
- checkpoint coordenador anterior à ativação:
  `bb2d04edd7061442959eec480aedecf25e6aea6c`;
- dependências `VR-SEC-001-BE-READ` e
  `VR-QA-TESTCONTAINERS-DOCKER-001`: `done`;
- contrato afetado: somente CLI/exit code da esteira backend; nenhuma rota,
  payload, auth, CORS, WebSocket, schema ou UI muda;
- backend sem dirty paths; trabalho preexistente do app e paths conhecidos da
  coordenação permanecem fora de escopo;
- rollback confirmado: reverter somente os commits deste card e manter
  `R-014`, piloto e produção em `HOLD`.

### Reanálise de escopo — 2026-07-17

- uma execução limpa no baseline confirmou que `clean test` sem `-Dtest`
  executa `605` testes, com zero skip/falha/erro, e que o validador atual
  comprova as quatro classes obrigatórias (`13/13`) no mesmo run-id;
- `qa_gate_card.sh` também foi identificado como entrypoint oficial com Maven
  cru e, portanto, integrante obrigatório da correção;
- workflow, `AGENTS.md`, README, documentação do quality ratchet, Codex Cloud,
  setup e QA Playbook foram incluídos porque descrevem ou recomendam a lane
  tolerante que este card substitui;
- documentos históricos permanecem imutáveis; novas entradas no backlog e QA
  Log registrarão explicitamente a superação do comportamento anterior.

### Hardening após revisão independente — 2026-07-17

- a comparação aritmética inicial de API não preservava a ordem para segmentos
  extremos; a correção deve comparar major/minor canônicos sem empacotamento;
- o scanner literal de `disabledWithoutDocker` não cobria formatação multiline,
  atributos reordenados ou herança; além do scanner tolerante a whitespace, o
  validador deve rejeitar qualquer skip em todos os XMLs do modo amplo;
- `trap cleanup_lock EXIT INT TERM` podia consumir `TERM`, remover o lock e
  continuar a suíte. O perfil e a suíte RBAC compartilham esse lock e devem usar
  handlers de sinal fail-closed, testados antes do encerramento.
- como o perfil passou a integrar todo gate canônico, herdar um
  `DOCKER_HOST=tcp://...` poderia criar/puxar PostgreSQL em daemon remoto. O
  preflight deve rejeitar overrides, inspecionar o contexto corrente e fixar
  somente endpoint local antes de qualquer mutação Docker.

### Reanálise após smoke same-version — 2026-07-17

- o primeiro smoke funcional sobre o SHA documental `68e231e` falhou de forma
  segura antes do smoke HTTP porque o coordenador isola o build inicial com
  `MAVEN_USER_HOME`/`MAVEN_OPTS`, enquanto o novo perfil canônico rejeita todo
  override Maven herdado;
- processo, container e worktrees foram limpos e nenhum manifesto `PASS` foi
  publicado; o log de falha permaneceu fora do repositório para diagnóstico;
- `qa_location_read_smoke.sh` e seu teste de contrato entram no escopo para
  remover as variáveis Maven/MVNW somente na fronteira de delegação ao gate. O
  build inicial continua hermético, e o gate continua dono exclusivo de seu
  runtime Maven fail-closed;
- a correção deve preservar `HOME`, cache versionado por symlink, SHA/tree,
  JAR, endpoint loopback e todas as demais garantias do coordenador; a lane
  funcional completa deve ser repetida antes do fechamento.

### Feedback adicional da lane same-version — 2026-07-17

- no SHA `8f44ab4`, o runner já rejeitava `DOCKER_HOST` externo, mas o endpoint
  local que ele próprio validava e exportava atravessava a fronteira do gate;
  o perfil canônico recusou corretamente o override. `6ccb0e0` passou a remover
  somente esse valor runner-owned dentro da subshell de delegação;
- no SHA `e645762`, o gate e os checks HTTP iniciais passaram, mas o guard do
  mesmo JAR recusou o run porque `clean test` removeu o JAR em `target/`;
  `cc4b46a` criou uma cópia privada vinculada por hash fora de `target/`, e
  `7972241` fixou por teste a ordem cópia -> servidor -> gate;
- as três falhas intermediárias terminaram com cleanup `PASS` e sem manifesto
  `PASS`. A lane completa foi repetida no SHA final `528a4c40` e verificada
  externamente antes do fechamento.

## Plano sequencial

1. ampliar formalmente as superfícies após a reprodução independente;
2. escrever RED para delegação única, parser e falhas de preflight/API;
3. adicionar `--all-tests` ao perfil estrito sem duplicar negociação/evidência;
4. integrar o perfil aos gates geral/local e deduplicar o workflow;
5. endurecer comparação de API, varredura global de skips e sinais do lock;
6. sincronizar a documentação operacional e preservar o histórico;
7. provar falha e sucesso com Docker real no SHA final;
8. registrar evidência e revisar independentemente.

## Evidências

| Evidência | Comando/local | Resultado | SHA/digest |
|---|---|---|---|
| gate geral observado | `./scripts/ci_quality_gate.sh` | PASS com 13 skips, não aceitável como prova PostgreSQL | `38b5489fb20af694b2b2d7f0eabab6e506b90ea1` |
| perfil estrito substituto | `qa_integration_profile.sh` | 13/13, zero skips | `integration-20260717T035351Z-45309` |
| composição ampla independente | `clean test` + `qa_report_evidence.py` | 605/605 e matriz 13/13, zero skip/falha/erro, uma execução Maven | `e93a69f2ee1caac4e3d1e57561d05ae6921390f6`; run `all-tests-audit-20260717T105655Z-22969` |
| gate canônico final limpo | `VANRIDES_QA_REQUIRE_CLEAN=1 ./scripts/ci_quality_gate.sh` | 605/605 em 116 XMLs; matriz 13/13; zero skip/falha/erro | `528a4c40`; run `integration-20260717T125054Z-35322`; manifest `819e141c...` |
| perfil focado final limpo | `VANRIDES_QA_REQUIRE_CLEAN=1 ./scripts/qa_integration_profile.sh` | 13/13; zero skip/falha/erro | run `integration-20260717T125131Z-35746`; manifest `ced71ab8...` |
| RBAC final limpo | `VANRIDES_QA_REQUIRE_CLEAN=1 ./scripts/qa_rbac_suite.sh` | 120/120; zero skip/falha/erro | run `rbac-20260717T125155Z-36126`; manifest `97e39319...` |
| same-version cross-repo | coordenador isolado + backend/app detached + PostgreSQL por digest | gate/fixtures/HTTP/Flutter/mesmo JAR/cleanup `PASS` | run `location-read-20260717T124841Z-32622`; manifest `a6ce5dd5...` |
| checkpoint de evidência | revisão independente + controle do programa | `GO`, 9/9 testes e check sem erro | coordenação `29257a35a22128a4937bae4cd8dcf9f63c7e9098` |

## Encerramento

- commits: `07a3493` a `528a4c4` no backend; ativação/reanálises coordenadoras
  `6b36830`, `157a803`, `d501d3c`, `3e72c05` e checkpoint de evidência
  `29257a3`;
- QA Log: feedbacks fail-closed e limites registrados no backend;
- backlog local: `SpringBoot-Cards.md` e `QA-Playbook.md` sincronizados;
- evidência: `program/evidence/VR-QA-BE-GATE-NO-SKIP-001.md`;
- riscos atualizados: `R-014` encerrado no escopo estrutural/local pelo
  checkpoint atômico de lifecycle; CI remota, release, piloto e produção
  continuam não comprovados e em `HOLD`;
- conclusão: 2026-07-17, backend final
  `528a4c40d4eb76c1eacf66334ba3b51a38eef650`, tree
  `54262de3cc21db909d5c5308aa3e09df7f6cc453`.
