# VR-QA-INTEGRATION-PROVENANCE-001 — Completar lifecycle da evidência Maven

## Resultado esperado

As suítes Maven de evidência possuem lock compartilhado reutilizável,
recuperação segura após processo morto, testes negativos do lifecycle e
retenção/publicação definida para execução local e CI, com provenance
same-SHA/tree, XML público sanitizado e transição atômica que impede falso
verde ou bloqueio órfão permanente.

## Estado

`done` — a autorização humana excepcional e limitada foi consumida em
2026-07-29. O SHA
`e304199b1359a44ba15b441d85c5363404528b6f`, tree
`5f4a4c737d23839af5774c7fdc64d48a91a8076e`, foi publicado uma única vez em
`codex/qa-integration-provenance-e304199`, sem force e sem alterar `dev` ou
`prod`. A única execução manual é a run GitHub Actions `30466145519`, attempt
`1`, evento `workflow_dispatch`, na ref e SHA exatos acima. A run terminou
`success`, com `test=success`, `build_and_push=skipped` e
`deploy_swarm=skipped`. O artifact único passou identidade, digest, allowlist,
schema v2, provenance, contagens, hashes XML e sanitização. Não houve retry,
rerun ou segundo dispatch, e não resta blocker deste card.

O estado anterior foi `waiting_human`, após a remediação local do bytecode ser
concluída sob autorização explícita da task orquestradora
`019f6950-6e3e-78e2-8cb0-724e2c4c1cad`. Antes disso, o card esteve `blocked`: a
primeira autorização excepcional foi executada pela task
`019fadc7-1d8f-7eb1-a795-a14ddb6716ab`. O backend SHA exato
`6efdb61846d0e4dcc59fd5cab921d275ad9eca98`, tree
`237586b41e991d0ca5d896c8be900c5635f2c0c2`, foi publicado na branch temporária
`codex/qa-integration-provenance-6efdb618`; `dev` e `prod` permaneceram
intocados em `3f89126a7b9d5ad4569df438c2dc5e39c4e2d8aa` e
`7231574ec15f91adad1ab8382a5a97d565b922dc`. A execução manual
`30450775307`, attempt `1`, falhou no preflight de fonte limpa antes do Maven:
três bytecodes Python foram criados sob `scripts/__pycache__` pelos testes que
antecedem a suíte. O artifact diagnóstico foi inspecionado e sanitizado, mas
não contém a prova Maven verde exigida. Retry no mesmo SHA é proibido e
repetiria a falha.

Em 2026-07-18, a descoberta do sucessor
`VR-BE-OPERATIONAL-DATE-ADOPTION-001` foi congelada em DOR e ADR proposta. O
sucessor continua corretamente `analyzed`: uma decisão de Produto sobre o
período de elegibilidade ainda impede `ready`.

O preflight remoto read-only de `2026-07-18T11:08:17Z` é um snapshot histórico
superseded: naquele momento o SHA e as runs estavam ausentes. Em 2026-07-29, a
primeira exceção publicou o SHA anterior e produziu a run falha
`30450775307`; o card voltou a `blocked`. A correção local em `e304199b...`
levou o card a `waiting_human`; a autorização final publicou a nova ref e o
card ficou `waiting_external` durante a run `30466145519`. A validação terminal
do artifact encerrou o blocker e sustenta `done`. Nenhuma exceção autorizou
`dev`, `prod`, force-push, PR, merge, retry, deploy ou promoção.

## Risco ou objetivo associado

`R-003`, épico `VR-QA-001` e gates `G1 — TRUSTED`/`G6 — PILOT_READY`.

## Contexto

`VR-SEC-001-BE-READ` tornou a prova imediata segura e durável: run-id, matriz
exata, bytes XML validados e manifesto por execução. Este card fechou a dívida
remanescente: unificou o lock, tornou `SIGKILL` recuperável sem inode race,
adicionou lifecycle PREPARED/PASS/FAIL, sanitizou o bundle público, amarrou a
provenance ao conteúdo Git e definiu retenção/publicação segura. O contrato
remoto está implementado e testado localmente, mas somente uma run GitHub verde
com artifact validado pode fechar o critério; a primeira run remota falhou
antes do Maven.

## Impact Analysis

### Primeira fronteira e fluxo afetado

- o primeiro limite incorreto está em `scripts/ci_quality_gate.sh`: testes
  Python executados antes de `qa_integration_profile.sh` importam módulos
  locais e, com Python 3.11/Linux e `sys.pycache_prefix=None`, criam três
  `scripts/__pycache__/*.pyc`;
- o preflight `VANRIDES_QA_REQUIRE_CLEAN=1` detecta corretamente essa mutação e
  bloqueia antes do Maven; clean-required, provenance e sanitização não serão
  alterados;
- a correção fica no processo pai do gate, antes da primeira execução Python,
  para que todos os subprocessos herdem `PYTHONDONTWRITEBYTECODE=1`;
- não há impacto em API, payload, status, auth, CORS, WebSocket, schema,
  migration, banco, app, Maps, workflow ou lógica do produto.

## Contratos

- API, payload, status, auth, CORS, WebSocket, migration, schema e lógica do
  produto permanecem semanticamente inalterados;
- `VANRIDES_QA_REQUIRE_CLEAN`, provenance, sanitização, retenção, locks, matriz
  Maven/PostgreSQL e smoke mantêm os mesmos contratos fail-closed;
- o único contrato ajustado é interno ao processo do gate: execuções Python
  pré-Maven não podem materializar bytecode dentro da fonte avaliada.

### Baseline, riscos e conflitos

- baseline obrigatório: backend
  `6efdb61846d0e4dcc59fd5cab921d275ad9eca98`, tree
  `237586b41e991d0ca5d896c8be900c5635f2c0c2`, limpo;
- branch backend local exclusiva:
  `codex/vr-qa-provenance-bytecode-clean`, ausente no startup;
- app mantém 51 alterações preexistentes conhecidas e não será tocado; Maps
  permanece limpo; a worktree backend paralela
  `fix/qa-public-signup-auto-verify` não usa os paths deste card;
- risco de regressão: proteger apenas presença textual da variável ou limpar
  bytecode depois da mutação. A regressão deve executar as etapas Python
  pré-Maven em checkout Git independente e limpo, com cache dentro da fonte,
  e observar zero `.pyc` e porcelain vazio no limite do Maven;
- Docker local está disponível com API `1.44..1.53`; indisponibilidade posterior
  bloqueia o gate, sem skip ou redução de matriz.

### Arquivos permitidos nesta remediação

- backend técnico: `scripts/ci_quality_gate.sh` e
  `scripts/ci_quality_gate_test.py`;
- backend documental, somente se necessário após GREEN:
  `backlog/SpringBoot-Cards.md` e `backlog/QA-Execution-Log.md`;
- coordenação no fechamento: este card,
  `program/evidence/VR-QA-INTEGRATION-PROVENANCE-001.md`,
  `program/program.json`, `program/RISK_REGISTER.md` e a projeção
  `.dev-harness/dashboard.md`.

## Validação

1. reproduzir o RED no SHA base em clone temporário descartável com
   `/opt/homebrew/bin/python3.11`, `sys.pycache_prefix=None` e exatamente os
   três bytecodes esperados;
2. criar regressão comportamental que alcance o limite pré-Maven em checkout
   isolado sem recursão do próprio teste;
3. exportar `PYTHONDONTWRITEBYTECODE=1` no gate antes da primeira execução
   Python e provar GREEN, `bash -n`, zero bytecode e zero dirty path;
4. executar contratos/evidência afetados, atualizar o QA Log/backlog e criar
   um único commit backend explícito;
5. executar `./scripts/qa_gate_card.sh` e o smoke local same-SHA/JAR no commit
   final limpo; qualquer alteração posterior invalida a prova e exige repetir
   o gate;
6. fechar somente em `waiting_human`, preservando o critério remoto pendente,
   percentuais `14/67` e `1/8`, sem push.

## Resultado da remediação local

- RED reproduzido fora da worktree com Python `3.11.14`,
  `sys.pycache_prefix=None`, exatamente três `.pyc` e três dirty paths;
- correção mínima: `PYTHONDONTWRITEBYTECODE=1` exportado pelo processo pai antes
  da primeira etapa Python, sem ignore ou limpeza posterior;
- regressão comportamental em checkout Git isolado: etapas Python pré-Maven
  verdes, zero bytecode na fonte e porcelain vazio no limite do Maven;
- commit backend `e304199b1359a44ba15b441d85c5363404528b6f`, tree
  `5f4a4c737d23839af5774c7fdc64d48a91a8076e`;
- gate oficial no SHA limpo: Python `40/40`, Maven `944/944`, matriz
  PostgreSQL `49/49`, ratchets e guardrails verdes;
- smoke HTTP no mesmo JAR: `GET=200`, `OPTIONS=200`, refresh vazio `400`,
  Flyway `25/25`, JAR imutável e cleanup `PASS`;
- manifesto local schema v2 `passed`, `clean=true`, `dirtyPathCount=0`,
  SHA/tree inicial e final idênticos e `eligibleForRelease=false`;
- lock readquirido/liberado após a execução; zero container, processo Java/JAR,
  bytecode ou dirty path residual;
- nenhum push, workflow remoto, imagem, deploy ou promoção foi executado.

## Rollback

Para rollback local, deixar de usar a branch
`codex/vr-qa-provenance-bytecode-clean` e voltar à branch local `dev` no commit
`6efdb61846d0e4dcc59fd5cab921d275ad9eca98`, isto é,
`dev@6efdb61846d0e4dcc59fd5cab921d275ad9eca98`. Isso não remove a branch de
remediação e não afirma nem altera o estado de `origin/dev`; nenhuma limpeza
destrutiva, remoção de bytecode, alteração remota ou cutover será necessária.

## Histórico técnico congelado

### Mudanças técnicas

- extrair aquisição/liberação para o módulo sourceable único
  `qa_maven_evidence_lock.sh`;
- usar um keeper Python com `fcntl.flock` sobre lock file persistente no git
  common dir, compartilhado por todos os worktrees;
- registrar owner diagnóstico versionado com PID, identidade de início, token
  de ownership, suite, run-id, início e SHA;
- manter o canal de controle herdável pelo Maven, de modo que `SIGKILL` libere
  automaticamente apenas quando a run inteira encerrar;
- fazer release validar keeper/token e nunca sinalizar lock de outro processo;
- distinguir `status=prepared`, `status=passed` e `status=failed`; publicar
  PASS somente no último replace atômico após release do lock e recaptura da
  fonte;
- usar Git explícito e sanitizado e fingerprint de porcelain, diffs e
  untracked para detectar drift de bytes mesmo com os mesmos paths;
- tratar Surefire bruto apenas como input e publicar XML mínimo determinístico,
  sem properties, nomes de casos, stdout/stderr ou paths privados;
- expor `eligibleForRelease` somente para sucesso release/limpo/same-SHA/tree,
  ambiente allowlisted e todos os relatórios verdes;
- adicionar classe de retenção `local`, `ci` ou `release` e pruner seguro,
  dry-run por padrão;
- publicar no CI manifestos/XMLs allowlisted por run/attempt/SHA apenas em
  eventos confiáveis, com 14 dias para CI e 90 para release;
- remover impressão de TXT/dumps e limitar `packages: write` ao job publicador;
- cobrir lock, corrida, signals, manifesto, retenção e workflow sem Maven real
  nos testes focados.
- corrigir o lifecycle coordenador para representar estado ocioso legítimo,
  mantendo `nextCardId` obrigatório quando houver card `ready`.

### Contrato

Não há mudança de API do produto. O contrato operacional completo — interface,
owner schema, estados, tempos, retenção e artifact — está congelado no DOR. Um
diagnóstico `failed` nunca é prova verde; release exige fonte limpa, SHA/tree
exatos e relatórios integralmente verdes.

### Riscos e rollback

- recuperação por PID/rename sofrer ABA: eliminada ao tornar o lock do kernel a
  autoridade e nunca unlinkar o lock file;
- corrida entre adquirentes: `flock(LOCK_EX|LOCK_NB)` permite no máximo um
  keeper; owner é apenas diagnóstico;
- lixo local crescer sem limite: política de 30 dias e piso de 20 por suite,
  aplicada somente por comando explícito, com active run protegida e quarantine
  privado no mesmo filesystem;
- CI perder diagnóstico: manifesto `failed` atômico e upload condicionado a
  evento confiável; falha precoce sem bundle não é mascarada;
- artifact vazar segredo: paths allowlisted, sem env/log/TXT/dump e sem
  conteúdo integral no console;
- artifact efêmero ser tratado como release: manifesto/digest coordenador e
  prova externa continuam gates separados.

Rollback: reverter módulo, consumidores, manifesto/retenção e workflow juntos,
sem apagar evidências já arquivadas. Lock ambíguo permanece fail-closed e
piloto/produção voltam ou continuam em `HOLD`; nenhum dado do produto muda.

## Escopo

- lock compartilhado e teste dedicado;
- runners RBAC e integração;
- manifesto de sucesso/falha e testes;
- retenção local segura e testes em diretórios temporários;
- composição no gate canônico e contratos do workflow;
- permissions e artifacts no workflow backend;
- README, programa de quality ratchet, backlog e QA Log;
- card, risco, dashboard e evidência coordenadora.

## Fora de escopo

- API, auth, schema, migrations ou dados do produto;
- mudar a matriz Maven, aceitar skip ou reduzir ratchet;
- executar `--apply` nas evidências reais existentes;
- no escopo original de implementação: push, GitHub remoto, deploy, VPS, banco
  remoto, piloto ou produção;
- primeira exceção limitada consumida: a task orquestradora
  `019f6950-6e3e-78e2-8cb0-724e2c4c1cad` autorizou em 2026-07-29 somente
  publicar o SHA congelado na branch temporária e disparar uma run manual; a
  task `019fadc7-1d8f-7eb1-a795-a14ddb6716ab` executou essas duas ações;
- exceção final consumida: a mesma task orquestradora autorizou somente publicar
  `e304199b...` em `codex/qa-integration-provenance-e304199` e executar a run
  manual `30466145519`, sem ampliar o objetivo do programa;
- continuam fora de escopo e proibidos: novo push sem autorização, push em
  `dev`/`prod`, force-push, PR, merge, tag, release, retry, build/push de
  imagem, deploy, VPS, banco remoto, piloto ou produção;
- criar armazenamento externo/GitHub Release;
- considerar workflow local como prova de artifact remoto.

## Dependências

- `VR-SEC-001-BE-READ` concluído;
- backend limpo em `b2dd00cf...` e sem dirty conflitante;
- nenhuma decisão humana ou credencial pendente para implementação local;
- a primeira execução GitHub real falhou; a correção, o novo SHA, a autorização
  final, a run única e o artifact remoto validado foram concluídos.

## Definition of Ready

- [x] dependência concluída e baseline limpo;
- [x] interface e owner schema congelados;
- [x] keeper, handshake, herança de FD, SIGKILL e metadata stale definidos;
- [x] retenção numérica local/CI/release definida;
- [x] manifesto PASS/FAIL e elegibilidade de release definidos;
- [x] permissions e artifact do workflow definidos;
- [x] escopo, fora de escopo, riscos, mitigação e rollback definidos;
- [x] matriz de testes/gates/evidência definida;
- [x] nenhuma decisão humana impede começar localmente.

## Critérios de aceite

- [x] dois scripts usam uma única implementação de lock;
- [x] lock com PID vivo/identidade igual bloqueia e identifica owner sem dado
  sensível;
- [x] lock persistente no git common dir é compartilhado entre worktrees e
  nunca sofre unlink/inode race;
- [x] owner ausente/corrompido com lock ocupado bloqueia sem forçar release;
- [x] release com keeper/token divergente não sinaliza processo alheio;
- [x] dois adquirentes concorrentes nunca obtêm ownership duplo;
- [x] `SIGKILL` sem filho libera automaticamente; com Maven vivo preserva o
  lock até a run encerrar; `INT`/`TERM` mantêm 130/143;
- [x] testes de lock, concorrência e sinais não executam Maven real;
- [x] PREPARED/PASS/FAIL são distintos; falha/interrupção nunca cria falso
  PASS nem autoriza release;
- [x] XML publicado é mínimo, determinístico e remove properties, nomes,
  stdout/stderr, ambiente livre e paths privados;
- [x] pruner mantém 30 dias e no mínimo 20 runs locais por suite, protege run
  ativa/release/unknown/symlink e usa quarantine revalidado;
- [x] CI publica somente manifestos/XMLs allowlisted por run/attempt/SHA em
  eventos confiáveis, com `if: always()` e 14/90 dias;
- [x] job de teste não possui `packages: write` nem imprime reports/dumps;
- [x] evidência release exige classe release, SHA/tree exatos, worktree limpa,
  schemas allowlisted e todos os relatórios verdes;
- [x] gate backend canônico passa no SHA final limpo e deixa zero lock órfão;
- [x] controlador aceita estado ocioso somente sem ativo/ready, rejeita
  `nextCardId=null` quando houver trabalho executável e `next` retorna mensagem
  informativa;
- [x] autorização humana identifica SHA, branch temporária e limites da
  publicação, sem conceder push em `dev`/`prod` ou deploy;
- [x] após nova autorização explícita, publicar o SHA local validado uma única
  vez em nova branch remota derivada de `e304199` e executar uma única run
  GitHub real que comprove artifact Maven verde antes de `done`; durante a run o
  status é `waiting_external`, e falha terminal retorna a `blocked`;
- [x] docs, evidência e programa distinguem `waiting_human` de
  `waiting_external` e registram a prova remota terminal.

## Arquivos previstos

- `src/backend/scripts/qa_maven_evidence_lock.sh`
- `src/backend/scripts/qa_maven_evidence_lock.py`
- `src/backend/scripts/qa_maven_evidence_lock_test.py`
- `src/backend/scripts/qa_rbac_suite.sh`
- `src/backend/scripts/qa_integration_profile.sh`
- `src/backend/scripts/qa_report_evidence.py`
- `src/backend/scripts/qa_report_evidence_test.py`
- `src/backend/scripts/qa_evidence_retention.py`
- `src/backend/scripts/qa_evidence_retention_test.py`
- `src/backend/scripts/ci_quality_gate.sh`
- `src/backend/scripts/ci_quality_gate_test.py`
- `src/backend/.github/workflows/swarm-ghcr-cicd.yml`
- `src/backend/AGENTS.md`
- `src/backend/README.md`
- `src/backend/docs/codex-cloud.md`
- `src/backend/docs/quality-ratchet-program.md`
- `src/backend/backlog/SpringBoot-Cards.md`
- `src/backend/backlog/QA-Playbook.md`
- `src/backend/backlog/QA-Execution-Log.md`
- `scripts/program_control.py`, `scripts/program_control_test.py`;
- `program/README.md`, `program/OPERATING_MODEL.md`, `program/program.json`,
  `program/RISK_REGISTER.md`, este card e evidências.

## Plano sequencial

1. ativar como único WIP e congelar SHA/dirty paths;
2. escrever testes RED do keeper/lock com processos falsos e diretórios
   temporários;
3. implementar módulo shell + keeper Python e migrar os dois runners sem mudar
   a matriz Maven;
4. escrever testes RED de manifesto FAIL/PASS e retenção;
5. implementar schema/retention/pruner e compor o gate;
6. endurecer workflow, permissions e artifacts com testes de contrato;
7. executar testes focados, `bash -n`, smoke de `SIGKILL`, sincronizar toda a
   documentação/QA Log backend e criar o último commit antes do freeze;
8. executar o gate canônico no SHA final limpo e registrar resultados/hashes
   somente na evidência coordenadora, sem criar commit backend posterior;
9. corrigir e testar o lifecycle ocioso do controlador;
10. registrar `waiting_human` até a primeira autorização, transicionar para
    `waiting_external` durante a run e, após a falha terminal, registrar
    `blocked` sem retry;
11. concluir a remediação local em `e304199b1359a44ba15b441d85c5363404528b6f`
    e retornar a `waiting_human`, sem publicação;
12. sob autorização final, publicar o novo SHA uma única vez, executar uma única
    run, validar o artifact remoto e fechar em `done` ou `blocked`.

## Evidência de prontidão

- freeze: `program/evidence/VR-QA-INTEGRATION-PROVENANCE-001-DOR.md`;
- backend baseline: SHA `b2dd00cf1e4e7cbfe6a7d50d606ee948d8b7f871`,
  tree `f79e34a1c96939979046626e7700628fc7b09875`, limpo;
- backend congelado da primeira run: SHA
  `6efdb61846d0e4dcc59fd5cab921d275ad9eca98`,
  tree `237586b41e991d0ca5d896c8be900c5635f2c0c2`, limpo;
- prova local final:
  `program/evidence/VR-QA-INTEGRATION-PROVENANCE-001.md`;
- prontidão do sucessor: coordenação
  `16739204ad0cb16193aae8a0acb91b1ed4864cd3`;
- lifecycle ocioso: coordenação
  `af7262ec9d9f48c074a120b3c623055eb88cbf00`, `18/18` testes;
- remoto: run `30450775307`, attempt `1`, executada e bloqueada antes do Maven;
  artifact diagnóstico inspecionado, sem prova verde;
- remediação local: branch `codex/vr-qa-provenance-bytecode-clean`, backend
  `e304199b1359a44ba15b441d85c5363404528b6f`, tree
  `5f4a4c737d23839af5774c7fdc64d48a91a8076e`, manifesto
  `integration-20260729T133551Z-6088` e smoke HTTP `PASS`;
- prova remota final: branch
  `codex/qa-integration-provenance-e304199`, run `30466145519`, attempt `1`,
  `test=success`, jobs mutáveis `skipped`, artifact `8729735066`, digest
  `sha256:60913f2531479bb0475e2a8765a490bd8a58ab958e786e480379409ed58331fc`,
  manifesto `integration-20260729T153116Z-6686`, Maven `944/944` e PostgreSQL
  `49/49`, integralmente verdes;
- preflight remoto read-only de `2026-07-18T11:08:17Z`: snapshot histórico
  superseded pela publicação e run de 2026-07-29; detalhes na evidência final.
