# VR-QA-BE-GATE-CAPABILITY-SPIKE-001 — Falsificar a seam prod-compatible do gate backend

## Resultado esperado

Uma capability spike executável, criada sobre o objeto remoto exato de
`prod`, demonstra a menor interface entre um `gate core` profundo e adapters
de produto sem copiar automaticamente os ao menos 166 candidatos conhecidos,
sem importar os 419 commits divergentes e sem tocar produto, API, schema,
migrations, workflow ou deploy.

O resultado é uma prova de design para o card permanente
`VR-QA-BE-GATE-PROD-COMPATIBLE-001`. Não é o gate oficial, não aprova Gate A,
Gate B ou Gate C e não autoriza promoção.

## Estado

`done` em 2026-07-30, aguardando revalidação orquestradora. A seam executável
corrigida passou `63/63` testes em ambiente normal e `63/63` com `env -i`; o
backend está commitado e limpo. O card conclui somente a capability spike
local.

## Dono operacional

`QA/REL`, com revisão da seam por `BE` e `SEC` no handoff à task orquestradora.

## Risco ou objetivo associado

Mitigação experimental de `R-003`, sob o épico `VR-QA-001`. A spike reduz
incerteza de implementação; não encerra o risco nem altera gates de release.

## Impact Analysis

### Contexto e seam

Na ref comparativa, `qa_gate_card.sh` e `ci_quality_gate.sh` misturam
proveniência, Maven, Docker, PostgreSQL, produto, fixtures, migrations, smoke,
retenção e CI. A base `prod` não possui esses entrypoints e seu teste legado
ativa o profile `local`, com PostgreSQL em `localhost` e
`ddl-auto=update`.

A spike testa uma seam diferente:

- o core recebe somente `profile`, capabilities declaradas e adapters;
- o core possui lifecycle, validação de capabilities, terminalização,
  manifesto/evidência e cleanup;
- cinco doubles sintéticos exercitam os papéis futuros de Git/env, Maven,
  Docker, PostgreSQL e smoke; nenhum adapter de produto real foi executado;
- local e CI são envelopes do mesmo request, nunca implementações distintas;
- produto escolhe capabilities e suites fora do core.

### Superfícies de produto

Nenhum endpoint, payload, status, auth, CORS, WebSocket, model, repository,
service, resource de aplicação, schema ou migration pode mudar. O app Flutter
e Maps não são consumidores desta spike e devem manter bytes/status idênticos.

### Dependências e risco

- dependências satisfeitas: `GOV-PIPELINE-001` e
  `VR-QA-INTEGRATION-PROVENANCE-001`;
- base backend obrigatória:
  `7231574ec15f91adad1ab8382a5a97d565b922dc`, tree
  `c5b64ac27c61e33f219e3e5f4c995d0a0c4f0111`;
- ref `e304199b1359a44ba15b441d85c5363404528b6f` é comparação somente;
- risco de abstração shallow: mutation tests e falhas observáveis pela
  interface;
- risco de efeito local: nenhum Maven, JVM, container, rede ou DataSource será
  iniciado pela spike; Docker/toolchains serão apenas inventariados;
- risco de dirty work: worktree e branch próprias, allowlists literais e
  auditoria dos quatro repositórios.

## Contratos

1. Interface pública pequena: request imutável com `profile`, capabilities e
   adapters; retorno terminal `PASS` ou `FAIL` com manifesto profundamente
   imutável e representação canônica.
2. `PREPARED` é estado intermediário persistível, nunca verde.
3. Capability ausente, desconhecida, duplicada ou sem adapter termina em
   `FAIL`, nunca skip.
4. Adapter não pode declarar ownership de outra capability nem vazar campos
   fora do schema allowlisted.
5. `PASS` só é produzido depois de todas as capabilities e cleanup terminarem.
6. Falha de cleanup substitui qualquer resultado anterior por `FAIL`.
7. Envelopes `local` e `ci` com o mesmo subject/profile/command-set produzem o
   mesmo resultado sem bifurcar a implementação.
8. A spike não lê ambiente sensível nem persiste valores de credencial.
9. `prod-compatible-v1` está ligado a um contrato confiável com nome, digest
   do command-set e sequência exata das cinco capabilities; vazio, parcial,
   reordenado ou digest divergente falha fechado.
10. Toda falha de aquisição do lock terminaliza em
    `FAIL/LOCK_ACQUIRE_FAILED`, sem exception leak ou descriptor residual.

## Riscos

- abstração shallow ou seam hipotética: exercitar os cinco papéis com doubles
  sintéticos, mutations e comportamento observável pela interface, sem
  chamá-los de adapters reais;
- falso verde antecipado: `PREPARED` nunca é terminal e cleanup pode rebaixar
  qualquer resultado para `FAIL`;
- expansão para produto/schema: abortar para qualquer terceiro path backend;
- equivalência local/CI alegada sem runner real: rotular a prova desta spike
  como equivalência de interface sintética, não equivalência ambiental;
- resíduo local: inventariar e remover por identidade todo temporário da task;
- confusão com gate oficial: nome, evidência e handoff usam `NOT_A_GATE`.

## Paths congelados

### Backend

- `scripts/qa_gate_core_spike.py`;
- `scripts/qa_gate_core_spike_test.py`.

### Coordenação

- `.dev-harness/dashboard.md`;
- `.dev-harness/status.json`;
- `program/program.json`;
- `program/RISK_REGISTER.md`;
- `program/cards/VR-QA-BE-GATE-CAPABILITY-SPIKE-001.md`;
- `program/evidence/VR-QA-BE-GATE-CAPABILITY-SPIKE-001.md`;
- `program/cards/VR-QA-BE-GATE-PROD-COMPATIBLE-001.md`;
- `program/evidence/VR-QA-BE-GATE-PROD-COMPATIBLE-001.md`;
- `program/cards/VR-REL-001-BACKEND-WORKFLOW-DEFAULT-SAFE-001.md`;
- `program/evidence/VR-REL-001-BACKEND-WORKFLOW-DEFAULT-SAFE-001.md`.

Qualquer terceiro path backend, path de produto/schema/migration ou quarto
efeito remoto encerra a spike sem expansão implícita.

## Escopo

- criar uma worktree backend isolada e branch local sem upstream a partir do
  SHA remoto exato de `prod`;
- implementar somente o core experimental e seus testes de contrato;
- inventariar versões/digests locais sem executar Maven/Docker/PostgreSQL;
- classificar path a path a closure futura como `reuse`, `rewrite`, `adapt` ou
  `exclude`;
- congelar achados, blockers e próximo experimento do card permanente;
- commitar localmente por repositório, com staging explícito.

## Fora de escopo

- implementar o gate permanente ou o workflow;
- importar/cherry-pick/mesclar código da ref comparativa;
- API, domínio, schema, migration, profile de produto ou fixture;
- Maven, Spring, DataSource, container, rede, banco ou smoke HTTP real;
- B, waiver ou adapter temporário;
- push, PR, merge, tag, release, workflow write, registry, SSH, VPS, deploy,
  piloto ou produção.

## Validação

- `PYTHONDONTWRITEBYTECODE=1 python3 scripts/qa_gate_core_spike_test.py`;
- testes positivos, negativos, mutations, cleanup e equivalência local/CI;
- `git diff --check` e `git show --check`;
- auditoria de parent/tree/paths/status/branch/upstream;
- `python3 scripts/program_control_test.py`;
- `python3 scripts/program_control.py check`, `summary` e `next`;
- scanner de material sensível nos commits, sem exibir valores;
- inventário antes/depois de processo/container/rede/temporário da task.

Resultado intermediário:

- backend inicial `2cd9c279aeea56ce6a535734bb4575f00b02fae1`, tree
  `77ff003c4db2858c9ad8fe96c6114ed781822b87`, parent direto do SHA remoto de
  `prod`;
- correção backend `b15837c5ec3ebf4e73ae546efe2d00704947322c`, tree
  `cef519ea9ed64e693abf78d11b19ffb42c28e0cb`, filha linear de `2cd9c279`;
- correção runtime `fbddba1847e13707b7301b6afdc5a71122e56075`, tree
  `41416507a7661382f84171c8dd9f829b38b007e0`, filha linear de `b15837c5`;
- correção de protocolos `29f2309c92cf8d43ecd350d997c577069ea50cb2`,
  tree `e906202b47430f14bdc953675be9e5dea732f40a`, filha linear de `fbddba1`;
- correção de input/lifecycle `69aa6c34baa18918c0bdd04bc4d84207008da148`,
  tree `e5e48d2faa7688b014c345fa68a1a5259e3b8b02`, filha linear de `29f2309`;
- correção de snapshot de evidência `b58942d914c16dc7fd4e413c7949c59e50587811`,
  tree `976156ea95fd90ef087eea5c49f6507f5146d4b9`, filha linear de `69aa6c3`;
- correção de cleanup conservador
  `31c0dbbd99c2c3cb810711fd7d8b84ae02feefab`, tree
  `b756338d90797e908b1e2a69d2dc08a8c75ab6e6`, filha linear de `b58942d`;
- correção de snapshot/ownership
  `8ad2fca45f1c7e50b010aaaa4647c3f32e7b205a`, tree
  `f3bfd229f190078d48c771908108c70a22b3ec0f`, filha linear de `31c0dbb`;
- correção de receipt pós-release
  `66301f5686487f54ed3b3f1077a131eb18b162da`, tree
  `fcde826805843e26de82712461d2c81fa3bfce02`, filha linear de `8ad2fca`;
- correção de lifecycle/inteiros/control-flow
  `e3ea8a8c45c3aa5fb8c87b1c12b67637d8848b29`, tree
  `0da58b2f22c56ac091dee64bfd0861ca879ca717`, filha linear de `66301f5`;
- coordenação freeze
  `c72b85d8c2c99638a0439099e93376240e29a1ef`, tree
  `da57f5841ce0b5248355e27c973038fbc2c2e2e0`;
- duas execuções `63/63`, `PASS`, zero bytecode/dirty/resíduo;
- controles finais: `program_control_test.py` `59/59` e
  `program_control.py check` `PASS` com 17 warnings conhecidos;
- nenhum Maven, JVM de produto, Docker, PostgreSQL, DataSource ou smoke real;
- equivalência local/CI limitada à interface sintética, não aos runners reais.

## Critérios de aceite

- [x] branch/worktree nascem do SHA/tree remoto exato de `prod`;
- [x] somente os dois paths backend congelados são alterados;
- [x] interface, capabilities, profiles e adapters são falsificáveis;
- [x] lifecycle e cleanup falham fechado;
- [x] profile confiável, lock acquire e manifesto imutável falham fechado;
- [x] tipos runtime inválidos do request terminalizam antes do lock em
  `FAIL/REQUEST_SCHEMA_INVALID`;
- [x] campos ausentes ou leitura com `Exception` em dataclasses exatas usam
  snapshot inválido sem reler o request, tocar lock ou chamar adapters;
- [x] exceções operacionais inesperadas do adapter container, adapter output
  e RunLock terminalizam sem detalhe; o container é materializado uma vez em
  bindings internos e `CLEANUP_FAILED` prevalece quando cleanup/release não é
  comprovado;
- [x] cada adapter output `Mapping` é materializado uma única vez em plain
  `dict`; somente esse snapshot é validado/persistido e chave extra falha
  `ADAPTER_OUTPUT_SCHEMA` sem vazar valor;
- [x] `KeyboardInterrupt`/`SystemExit` não viram `GateResult`: a primeira
  `BaseException` é relançada somente após cleanup reverso e release/held
  best-effort, inclusive no `FileRunLock` real;
- [x] qualquer `Exception`/receipt não-bool no acquire mantém
  `LOCK_ACQUIRE_FAILED` e `cleanup=failed` conservadoramente, pois a seam não
  possui receipt explícito que prove reversão de efeitos parciais;
- [x] `acquire=True` só entra em `PREPARED` após `held is True` com bool exato;
  `acquire=False` continua `LOCK_UNAVAILABLE` sem held/release de lock alheio;
- [x] depois de release, somente `final_held is False` com bool exato comprova
  cleanup; `True`, não-bool ou `Exception` falham cleanup, e `BaseException`
  atravessa apenas depois das tentativas best-effort;
- [x] o ownership guard cobre construção de contexto/estado; falha operacional
  pré-contexto termina `PREPARE_FAILED` com transição `FAIL`, e control-flow só
  atravessa depois de release comprovado;
- [x] `maven.testCount` e `smoke.probeCount` aceitam somente
  `1..2^31-1`; `max+1` e inteiros com 5.000 dígitos falham
  `CAPABILITY_INVARIANT_FAILED` antes de serialização;
- [x] `FileRunLock` sempre tenta close/unlock e prioriza a primeira
  control-flow `BaseException` sobre exceções operacionais; entre falhas da
  mesma classe preserva a primeira;
- [x] testes positivos, negativos e mutations passam;
- [x] equivalência sintética local/CI é demonstrada pela mesma interface;
- [x] closure futura fica classificada path a path;
- [x] toolchains/imagens e limitações de digest ficam registradas;
- [x] card permanente recebe fatos comprovados e blocker honesto;
- [x] zero processo/container/rede/temporário residual da task;
- [x] commits locais são atômicos e nenhum efeito remoto ocorre.

## Plano sequencial

1. congelar card, paths, base e rollback;
2. criar worktree/branch sem upstream no objeto remoto de `prod`;
3. escrever primeiro os testes de contrato/mutation;
4. implementar o menor core que satisfaça a interface;
5. executar somente a suíte Python hermética;
6. fechar closure, evidência, blocker e cleanup;
7. commitar backend e coordenação localmente;
8. entregar à task orquestradora e parar.

## Rollout

Local-only. O protótipo vive apenas na branch da spike e não é conectado a
`qa_gate_card.sh`, `ci_quality_gate.sh`, workflow ou aplicação.

## Rollback

Antes do commit, descartar apenas os dois paths na worktree isolada. Depois do
commit, reverter o commit backend e o commit coordenador separadamente ou
remover branch/worktree após validação humana. Nenhum rollback toca audit
trail, produto, banco ou remoto.

Rollback backend linear desta correção: reverter primeiro
`e3ea8a8c45c3aa5fb8c87b1c12b67637d8848b29`, depois `66301f56...`,
`8ad2fca...`,
`31c0dbbd...`,
`b58942d9...`,
`69aa6c34...`,
`29f2309c...`,
`fbddba184...`, `b15837c5...` e, somente para descartar toda a spike,
`2cd9c279...`.

## Definition of Ready

- [x] Rota A selecionada explicitamente por Matheus em 2026-07-30;
- [x] card permanente identificado como investimento da Rota A;
- [x] base remota SHA/tree e divergência 6/419 revalidadas;
- [x] escopo e dois paths backend congelados antes de editar;
- [x] coordenação, app, backend atual e Maps inventariados;
- [x] dependências satisfeitas e WIP disponível;
- [x] API/schema/migrations/workflow/deploy fora de escopo;
- [x] plano positivo/negativo/mutation/equivalência definido;
- [x] cleanup e rollback bounded definidos;
- [x] autorização limitada a local e proibição de remoto registradas.

## Evidência

Evidência canônica:
`program/evidence/VR-QA-BE-GATE-CAPABILITY-SPIKE-001.md`.
