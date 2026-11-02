# VR-QA-BE-GATE-PROD-COMPATIBLE-001 — Tornar o gate canônico do backend executável e prod-compatible no default branch

## Resultado esperado

O backend possui um `gate core` profundo, versionado e prod-compatible, com
adapters de produto explícitos. `./scripts/qa_gate_card.sh` e
`./scripts/ci_quality_gate.sh` tornam-se executáveis a partir do default branch
sem importar os 419 commits divergentes, usam a mesma interface local/CI e
produzem evidência hermética de Git, Maven, Docker, PostgreSQL e artefato.

Se a rota B de
`VR-REL-001-BACKEND-WORKFLOW-DEFAULT-SAFE-001` for implementada, este card
substitui e remove `scripts/qa_workflow_safety_gate.sh` e todas as referências
executáveis/fallbacks/configs reutilizáveis do adapter, torna o waiver
terminalmente inaplicável e migra o workflow para o gate canônico. Ledgers,
assertions, approvals, claims, receipts e tombstones sanitizados permanecem
imutáveis no programa como audit trail; sua remoção não integra o resultado.

## Estado

`analyzed` em 2026-07-30. Matheus selecionou explicitamente a **Rota A** e
congelou este card como investimento permanente, não como removal successor de
B. A autorização atual cobre somente
`VR-QA-BE-GATE-CAPABILITY-SPIKE-001`; não torna este card `ready` nem autoriza
sua implementação.

O resultado, a base, a interface sintética e o pacote v1 são conhecidos, mas
adapters reais, digests de toolchain e equivalência ambiental local/CI ainda
não foram provados. Nunca esteve `ready`, `in_progress` ou `done`; Gate A, Gate
B e Gate C permanecem `NOT_RUN`.

## Dono operacional

`QA/REL`, com revisão obrigatória de `BE` e `SEC`.

## Risco ou objetivo associado

Mitigação permanente de `R-003`, sob o épico `VR-QA-001`. Este card não
encerra o risco até o gate canônico passar local/CI na mesma versão e o adapter
temporário, se existente, ser removido sem apagar o histórico da exceção.

## Impact Analysis

### Módulo e interface

O módulo público atual é `qa_gate_card.sh`, que compõe
`ci_quality_gate.sh` e smoke. A interface aparente é pequena, mas a
implementação importa políticas de produto, matriz de testes, migrations,
fixtures, DEV ops, retenção, lock e manifesto. Na base antiga, esse leak
transforma uma chamada simples numa closure de ao menos 166 candidatos
conhecidos e ainda não fechada.

O desenho alvo separa:

- `gate core`: proveniência, isolamento, lifecycle, execução, evidence schema,
  cleanup e estados terminais;
- adapters de produto: seleção de suíte/capacidade, schema/migrations, smoke e
  critérios específicos;
- entrypoints públicos: `qa_gate_card.sh` e `ci_quality_gate.sh`, ambos
  chamando o mesmo core e profile versionado;
- workflow: caller fino, sem Maven/Docker/PostgreSQL/YAML validation inline.

A interface final precisa expor somente profile/capacidades declaradas e
retornar manifesto sanitizado com estado terminal. Git, Maven, Docker,
PostgreSQL, locks, temporários e cleanup ficam escondidos no módulo.

### Seam e migração

A seam varia entre política comum e adapter de produto, não entre execução
local e CI. Local e CI usam o mesmo path, profile e command-set. O adapter
temporário `qa_workflow_safety_gate.sh`, caso exista, é uma implementação
provisória nessa seam e deve ser removido no mesmo commit que troca o workflow
para o gate canônico. A remoção alcança chamadas, flags, fallbacks, profiles e
configuração executável; os carriers históricos sob `program/waivers/` e
`program/authorizations/` ficam como tombstones terminais não executáveis.

A seleção humana de 2026-07-30 encerrou a bifurcação: este card é o
investimento da Rota A. O card de workflow depende deste card, e este card não
depende do workflow, evitando circularidade. B, waiver e adapter temporário
permanecem rejeitados e não são parte da migração.

### Dependências técnicas

- Git explícito e hermético, sem config/replace/hooks/fsmonitor herdados;
- JDK e Maven wrapper/distribuição por versão e digest;
- Maven home/settings/repository isolados e execução offline após preparação;
- Docker somente por transporte local e daemon identificado;
- PostgreSQL por digest/plataforma, database/schema/user sintéticos e rede
  interna;
- evidence schema allowlisted, digests, lock, retenção e cleanup terminal;
- adapters de produto compatíveis com a base default, sem copiar funcionalidades
  ou migrations não exigidas.

### Contratos afetados

Somente gates, workflow CI e evidência backend. O card não deve mudar API,
payload, status, auth, CORS, WebSocket, comportamento de produto, schema ou
migration salvo quando uma dependência for provada mínima e receber refreeze
explícito.

## Contratos

1. `qa_gate_card.sh`, `ci_quality_gate.sh` e CI chamam o mesmo `gate core` e
   command-set do mesmo SHA.
2. Cada adapter declara capabilities e dependências; ausência falha com erro
   localizável, sem importar outro produto silenciosamente.
3. A interface produz estados `PREPARED`, `PASS` ou `FAIL`; `PASS` só existe
   após processos terminais, lock liberado e cleanup comprovado.
4. Manifesto liga SHA/tree, paths/blobs, profile/command-set, Git, JDK, Maven,
   Docker, PostgreSQL, testes, JAR/smoke e cleanup.
5. CI não aceita `test=success` sem artifact/manifest sanitizado equivalente.
6. Se a rota B tivesse sido implementada, seu adapter e qualquer referência
   executável/fallback/config reutilizável teriam de deixar de existir, com
   waiver/autorizações/claims terminais. B nunca foi implementada; nesta base,
   esse requisito é `N/A`, e o histórico rejeitado permanece audit trail.
7. A mesma interface é provada em checkout limpo local e runner CI suportado.
8. Audit trail preserva aprovador/provenance, binding, commits de approval/
   claim/terminal, uso, resultado, expiração e este card permanente, quando
   esses registros existirem.

## Riscos

- transformar core em agregador shallow: exigir interface pequena, adapters
  declarativos e testes pela interface;
- importar os 419 commits: partir do SHA default revalidado e justificar cada
  path/bloco por dependência observável;
- reduzir cobertura para caber na base: registrar capabilities ausentes como
  `FAIL/UNSUPPORTED`, nunca skip verde;
- contaminar local/CI por config e caches: ambientes mínimos, digests,
  repos/settings isolados e probes negativos;
- remover adapter antes da equivalência: troca e remoção atômicas, com rollback
  preparado;
- apagar a exceção junto do código: ratchet exige tombstone/ledgers históricos
  e recusa deleção ou retorno a estado aplicável;
- migrations ou produto entrarem como efeito colateral: refreeze separado e
  blast-radius review antes de qualquer path de schema/domínio.

## Freeze da capability spike — 2026-07-30

### Interface e profile

A spike `VR-QA-BE-GATE-CAPABILITY-SPIKE-001` provou a interface:

```text
GateCore(run_lock).run(
  GateRequest(subject, profile, runner_class),
  Mapping[capability, GateAdapter]
) -> GateResult(
  status, transitions, error_code,
  manifest (deeply immutable), canonical_manifest
)
```

O profile `prod-compatible-v1` declara command-set por SHA-256 e as
capabilities ordenadas `source`, `maven`, `docker`, `postgresql`, `smoke`.
`runner_class=local|ci` muda somente o envelope. O core possui lifecycle
`PREPARED -> PASS|FAIL`, lock, manifesto allowlisted, binding cruzado e cleanup
reverso; adapters possuem as ferramentas e a política de produto.

### Ownership

- core: validação do request/profile/adapters, estados, lock,
  manifesto/evidência, equivalence digest, cleanup e terminalização;
- `source`: Git e env schema;
- `maven`: wrapper/distribuição/closure, testes e artefato;
- `docker`: daemon/transporte local/plataforma/imagens;
- `postgresql`: imagem, rede interna, database/schema/user sintéticos e zero
  porta publicada;
- `smoke`: mesmo artefato, loopback, probes e processo terminal;
- profile de produto: seleção de suites/capabilities, sem entrar no core.

### Pacote backend v1 candidato

```text
.github/workflows/swarm-ghcr-cicd.yml
scripts/qa_gate_core.py
scripts/qa_gate_core_test.py
scripts/qa_gate_product_adapter.py
scripts/qa_gate_product_adapter_test.py
scripts/qa_gate_card.sh
scripts/ci_quality_gate.sh
README.md
docs/codex-cloud.md
backlog/SpringBoot-Cards.md
backlog/QA-Execution-Log.md
```

Esse freeze não altera `allowedDirtyPaths.backend=[]` ainda. Os 166 candidatos
foram classificados individualmente na evidência da spike:
`reuse=1`, `rewrite=12`, `adapt=1`, `exclude=152`. `mvnw` e wrapper properties
são prerequisites reutilizados; produto, migrations V1–V25, 21 testes e 75
classes Java da ref comparativa foram excluídos.

### Limite experimental

O protótipo inicial `2cd9c279aeea56ce6a535734bb4575f00b02fae1` e as correções
`b15837c5ec3ebf4e73ae546efe2d00704947322c`,
`fbddba1847e13707b7301b6afdc5a71122e56075` e
`29f2309c92cf8d43ecd350d997c577069ea50cb2` e
`69aa6c34baa18918c0bdd04bc4d84207008da148` e
`b58942d914c16dc7fd4e413c7949c59e50587811` e
`31c0dbbd99c2c3cb810711fd7d8b84ae02feefab` e
`8ad2fca45f1c7e50b010aaaa4647c3f32e7b205a` e
`66301f5686487f54ed3b3f1077a131eb18b162da` e
`e3ea8a8c45c3aa5fb8c87b1c12b67637d8848b29` formam cadeia linear. O HEAD
passou `63/63` testes duas vezes, inclusive com `env -i`, mas usou somente
doubles sintéticos. O core materializa request, adapter bindings e cada
adapter output uma vez, contém campos ausentes/leituras operacionais do request,
protege todo efeito após ownership, limita contagens antes de serialização e
exige receipts booleanos exatos de ownership/liberação; isso não substitui o
experimento com adapters reais.
Nenhum Maven, Docker, PostgreSQL, DataSource, JAR ou smoke real foi executado.
Portanto a spike prova a seam, não equivalência ambiental nem gate oficial.

Primeiro blocker: `REAL_ADAPTER_EXECUTION_UNPROVEN`. O próximo experimento
mínimo deve materializar somente os adapters reais em nova worktree do mesmo
`prod`, bindar wrapper/distribuição/imagem/env schema e executar o teste legado
existente sob properties injetadas e PostgreSQL interno. Necessidade de path de
produto/schema/migration aborta e exige novo refreeze.

## Validação

`NOT_RUN / ANALYZED` para o card permanente.

A capability spike é `PASS / NOT_A_GATE`; ela não satisfaz o gate permanente.

Validação futura mínima:

- testes de contrato/mutation do core, adapters e entrypoints;
- execução local e CI do mesmo path/profile/command-set;
- checkout limpo e diff/path ratchet;
- Git/Maven/Docker/PostgreSQL herméticos, com testes negativos de herança;
- Maven focado/completo sem skips indevidos;
- smoke HTTP no mesmo JAR e manifesto sanitizado equivalente;
- prova de remoção de `scripts/qa_workflow_safety_gate.sh` e de qualquer
  referência executável/fallback/config reutilizável do waiver, somente se
  uma futura verificação encontrar implementação real de B;
- na base atual, prova de ausência do adapter/waiver de B e preservação do
  histórico textual rejeitado;
- gate de coordenação, revisão independente e rollback ensaiado.

## Dependências

- `GOV-PIPELINE-001` — `done`;
- `VR-QA-INTEGRATION-PROVENANCE-001` — `done`, como referência comparativa,
  não como prova da base default;
- `VR-QA-BE-GATE-CAPABILITY-SPIKE-001` — `done`, seam bounded provada com
  adapters sintéticos, sem equivalência ambiental;
- inventário/refreeze prod-compatible próprio, ainda não concluído.

## Escopo

- desenhar e implementar o `gate core` e adapters mínimos;
- tornar os dois entrypoints canônicos executáveis no default branch;
- provar mesma interface local/CI com isolamento e evidência;
- remover adapter e superfícies executáveis do waiver quando existentes;
- preservar o audit trail canônico terminal e sua provenance;
- atualizar workflow, docs, QA Log e backlog somente nos paths congelados.

## Fora de escopo

- executar este card durante a presente correção documental;
- copiar/cherry-pick/mesclar os 419 commits em bloco;
- relaxar teste, evidence schema, isolamento ou cleanup para obter verde;
- push, PR, merge, workflow dispatch, imagem, registry, deploy, VPS, piloto ou
  produção sem gates e autorizações próprios;
- mudança de produto/schema sem dependência comprovada e refreeze separado.

## Critérios de aceite

- [ ] interface do core e adapters é pequena, versionada e revisada;
- [ ] closure prod-compatible está fechada path a path e não importa os 419
  commits em bloco;
- [ ] `qa_gate_card.sh`, `ci_quality_gate.sh` e CI chamam o mesmo core/profile;
- [ ] Git, Maven, Docker e PostgreSQL são herméticos e falsificáveis;
- [ ] full Maven, smoke same-JAR e manifesto equivalente passam local/CI;
- [ ] skips/unsupported falham fechado e ficam observáveis;
- [x] `N/A — B never implemented`: não existe adapter, chamada, flag, profile,
  fallback ou config executável de B na base `prod`/spike para remover;
- [x] `N/A — B never implemented`: não existe waiver/autorização/claim
  executável de B para terminalizar; o histórico rejeitado permanece
  inaplicável;
- [x] a ausência acima não apaga o audit trail textual de aprovação/rejeição,
  provenance e decisão da Rota A;
- [x] `N/A — B never implemented`: não há artifact operacional de waiver para
  reter por 90 dias; o audit trail canônico do programa não é apagado;
- [ ] docs/backlog/QA Log e evidência canônica registram a migração;
- [ ] rollback restaura uma versão segura sem reativar workflow vulnerável;
- [ ] nenhuma promoção ou ação remota ocorre por implicação deste card.

## Plano sequencial

1. revalidar default SHA/tree e inventariar o lower bound;
2. congelar interface/capabilities e paths mínimos;
3. obter revisão de design/segurança e transicionar separadamente para
   `ready`;
4. implementar core + adapters em worktree isolada;
5. provar local/CI, smoke, evidência e cleanup na mesma versão;
6. se uma checagem futura encontrar qualquer superfície executável de B,
   parar e refazer o freeze; na base atual, provar ausência e preservar o
   histórico rejeitado;
7. commitar/evidenciar sem push;
8. solicitar gates remotos somente em cards/autorizações separados.

## Rollout

Entrega local e CI por capability, com comparação contra o adapter temporário
quando aplicável. Nenhum default branch muda antes de Gate A, revisão, Gate B e
Gate C próprios. O workflow permanece fail-closed durante qualquer landing.

## Rollback

Antes de ação remota, abandonar/reverter apenas a worktree/commit do card.
Durante migração futura, manter commit de reversão pré-validado que restaure a
última versão segura sem restaurar dispatch mutante ou waiver expirado. Se a
remoção do adapter falhar, manter `HOLD`, não reabilitar fallback e retornar a
`analyzed/blocked` com evidência do SHA exato. Rollback nunca deleta ou reabre
ledgers/assertions/claims/receipts; tombstones terminais permanecem no programa.

## Definition of Ready

- [x] `ROUTE_SELECTION` registrada: Rota A; este é o card permanente e B foi
  rejeitada;
- [x] default SHA/tree e divergência/lower bound revalidados em 2026-07-30;
- [ ] gate core, adapters, capabilities e interface aprovados após execução
  real e revisão independente; a seam sintética já foi provada;
- [ ] lista exata de paths backend/coordenação congelada;
- [ ] profiles, toolchains, imagens, env schema e manifests por digest
  congelados;
- [x] testes positivos/negativos/mutations, local/CI e smoke definidos;
- [x] migração/remoção de B é `N/A — B never implemented`; rollback não pode
  inventar ou reativar adapter/waiver;
- [x] prova de ausência de superfície executável de B e preservação do audit
  trail foram congeladas; o ratchet do gate permanente ainda será revisado;
- [ ] dependências e dirty paths revalidados;
- [ ] autorização humana separada para executar o card.

## Evidência

Evidência canônica inicial:
`program/evidence/VR-QA-BE-GATE-PROD-COMPATIBLE-001.md`.
