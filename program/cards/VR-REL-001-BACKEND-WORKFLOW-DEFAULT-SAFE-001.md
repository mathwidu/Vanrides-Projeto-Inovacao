# VR-REL-001-BACKEND-WORKFLOW-DEFAULT-SAFE-001 — Impedir mutação remota pelo workflow padrão do backend

## Resultado esperado

O workflow canônico do backend no default branch `prod` mantém validação em
`pull_request`, `push` autorizado e `workflow_dispatch`, mas não permite que
uma execução manual construa imagem, publique no registry, acesse VPS ou
implante serviço. Os jobs mutáveis possuem guards explícitos de evento e branch,
as permissões de escrita ficam limitadas ao job que publica imagem e a
aterrissagem da própria correção ocorre com o workflow individual pausado,
sem run de `push`, build, publicação ou deploy.

## Estado

`analyzed` em 2026-07-30. Matheus selecionou explicitamente a **Rota A**. O
card permanente `VR-QA-BE-GATE-PROD-COMPATIBLE-001` é agora dependência deste
card de workflow; B, waiver e adapter temporário permanecem rejeitados e nunca
serão implementados por esta rota.

O lower bound A continua factual e quantitativamente corrigido, mas não é uma
ordem de copiar 166 paths. A capability spike bounded
`VR-QA-BE-GATE-CAPABILITY-SPIKE-001` demonstrou a seam em
`e3ea8a8c45c3aa5fb8c87b1c12b67637d8848b29`, com `63/63`, doubles
sintéticos, guard de lifecycle pós-ownership, limites inteiros e receipts
booleanos exatos antes da execução/depois do release, sem
Maven/Docker/PostgreSQL real. O card permanente segue
`analyzed` até executar adapters reais e bindar toolchains por digest.

`allowedDirtyPaths.backend=[]`; Gate A, Gate B e Gate C estão
`NOT_RUN / NOT_AUTHORIZED`. Nenhuma implementação deste card, publicação, PR,
run, imagem, deploy ou configuração remota foi criada ou alterada.

## Risco ou objetivo associado

Mitigação prioritária de `R-003`, vinculada ao épico `VR-REL-001`. O risco
permanece `mitigando`; este card não o encerra e não muda `G1` a `G7`,
`pilot=HOLD` ou `production=HOLD`.

## Contexto

O default branch remoto é `prod` no SHA
`7231574ec15f91adad1ab8382a5a97d565b922dc`. Seu workflow ativo
`.github/workflows/swarm-ghcr-cicd.yml` tem `7.507` bytes e SHA-256
`f3e0551e1e3dae62e647abab130614a478066eb0399b1b1024e3eb7d64cb735a`.
Ele aceita `workflow_dispatch`, concede `packages: write` no workflow inteiro
e não possui guard por evento nos jobs `build_and_push` e `deploy_swarm`.
Logo, um dispatch manual autenticado pode alcançar build/push/deploy.

A ref comparativa
`codex/qa-integration-provenance-e304199` aponta para
`e304199b1359a44ba15b441d85c5363404528b6f`. Seu workflow tem `14.970`
bytes, SHA-256
`f05e8c9c6ee5eafcc9a16ef92fcc18468840b582a14cc4cd5b48843beb246897`,
job `test`, permissões menores e guards de evento nos jobs mutáveis. Essa ref
está divergente de `prod`: `ahead_by=419`, `behind_by=6`, merge base
`802d87a78beb95bb9cb818e54301d92f06e40550`. Ela é evidência comparativa,
nunca base de merge, cherry-pick em bloco ou prova do futuro SHA de `prod`.

O tree remoto de `prod` contém `mvnw`, `pom.xml`, o workflow e apenas
`VanridesApplicationTests.java` sob `src/test`; não contém
`scripts/qa_gate_card.sh` nem `scripts/ci_quality_gate.sh`. O profile default é
`local`, usa o datasource
`jdbc:postgresql://localhost:5432/vanrides_db`, `ddl-auto=update` e o único
teste é `@SpringBootTest`; não há H2 nem serviço PostgreSQL de CI. Portanto
`./mvnw test` não é um substituto hermético: pode falhar ou alterar um banco
ambiente. O diff futuro não pode copiar o workflow de `e304199`, chamar um gate
ausente ou criar um waiver implícito.

## Impact Analysis

### Primeira fronteira incorreta

A primeira fronteira incorreta é o contrato do workflow no default branch:
`workflow_dispatch` compartilha os mesmos jobs mutáveis de `push` sem um guard
de evento/branch. O problema ocorre antes de Docker, registry, Tailscale, SSH,
Swarm ou aplicação; a correção mínima pertence ao YAML e ao teste estrutural
que o torna falsificável.

### Mudanças técnicas futuras

- criar uma worktree backend isolada a partir do remoto
  `prod@7231574ec15f91adad1ab8382a5a97d565b922dc`;
- adicionar `test` ao workflow, com Java 17 e o gate hermético que ainda
  precisa ser congelado no refreeze;
- adicionar `pull_request` para targets `dev` e `prod`;
- tornar a permissão default `contents: read`;
- limitar `packages: write` a `build_and_push` e declarar no mesmo job
  `contents: read`;
- exigir em `build_and_push` e `deploy_swarm` exatamente um guard equivalente a
  `github.event_name == 'push' && (github.ref == 'refs/heads/dev' ||
  github.ref == 'refs/heads/prod')`;
- manter `needs: test` em `build_and_push` e `needs: build_and_push` em
  `deploy_swarm`;
- adicionar um teste JUnit/SnakeYAML que faça parse real do arquivo e valide
  triggers, branches, jobs, `needs`, guards, permissões e a matriz de eventos.

### Hipótese de três paths rejeitada, ainda não autorizada

1. `.github/workflows/swarm-ghcr-cicd.yml`;
2. `scripts/qa_workflow_safety_gate.sh`;
3. `src/test/java/com/vanrides/vanrides/WorkflowSafetyContractTest.java`.

O commit `25117518...` tratou esses três paths como módulo profundo suficiente
na seam local/CI. A revalidação demonstrou o contrário: trust root, at-most-once
local/remoto, bootstrap/distribuição Maven, binding de CI, closure de testes e
enforcement de rede permanecem fora da interface e sem ownership operacional.
Logo, a localidade de três paths não foi provada; qualquer quarto path ou
mutação remota necessária invalida a premissa B.

Os paths acima são somente o registro da hipótese rejeitada. Não formam
allowlist nem candidato executável. `allowedDirtyPaths.backend=[]` permanece
vazio; B só pode ser reconsiderada por capability spike/refreeze separado,
explicitamente autorizado e com paths/efeitos remotos reais. Esta task não cria
esse card nem abre essa autorização.

### Branch e worktree históricas propostas, não autorizadas

- branch: `codex/vr-rel-001-backend-workflow-default-safe-001`;
- worktree isolada:
  `<CAMINHO_LOCAL_PRIVADO>`;
- base obrigatória: objeto remoto exato
  `7231574ec15f91adad1ab8382a5a97d565b922dc`;
- commit técnico sugerido:
  `fix(ci): make manual backend workflow non-mutating`.

Esta task não cria branch nem worktree.

## Contratos

### Invariantes falsificáveis

1. `workflow_dispatch` executa somente validação/teste; não constrói imagem,
   não publica no registry, não acessa VPS e não implanta serviço.
2. `build_and_push` e `deploy_swarm` só executam em evento `push` e ref
   `refs/heads/dev` ou `refs/heads/prod`, ambas expressas no próprio job.
3. `packages: write` existe somente em `build_and_push`; o workflow e os jobs
   não mutáveis usam `contents: read`, e `build_and_push` declara
   `contents: read` junto da escrita de package. O teste contratual deve recusar
   a omissão: quando um job declara qualquer `permissions`, as permissões não
   declaradas ficam `none`, o que pode quebrar o checkout do repositório privado.
4. `build_and_push` depende de `test`; `deploy_swarm` depende de
   `build_and_push`.
5. Uma prova só vale para o mesmo SHA backend e o mesmo digest de workflow
   presentes no default branch. Run, artifact ou teste de outro SHA/digest não
   fecha o card.
6. Landing da própria correção produz zero run de `push`: o workflow individual
   deve estar `disabled_manually` durante o fast-forward e voltar a `active`
   somente depois de SHA, digest e ausência de runs serem verificados.

### Matriz estrutural de eventos congelada

`Ausente` significa que o filtro do trigger não cria workflow run. `Skipped`
significa que existe run, mas o job é rejeitado por guard/`needs`.
Esta tabela é prova estrutural futura por parsing/teste contratual. Gate C
comprova remotamente somente `workflow_dispatch`; linhas de PR e `push` só
viram prova remota se esses eventos forem realmente executados e registrados no
mesmo SHA/digest.

| Evento/ref | Workflow | `test` | `build_and_push` | `deploy_swarm` | Efeito remoto permitido |
|---|---|---|---|---|---|
| `pull_request` para `dev` ou `prod` | run | run | skipped | skipped | checkout/logs/testes; registry, VPS e deploy proibidos |
| `push` em branch comum | ausente | ausente | ausente | ausente | nenhum |
| `push` em `dev` | run | run | run após `test` | run após build | imagem `dev` e serviço `vanrides-dev_api`, somente em release futura autorizada |
| `push` em `prod` | run | run | run após `test` | run após build | imagem `prod` e serviço `vanrides-prod_api`, somente em release futura autorizada |
| `workflow_dispatch` no default `prod` | run | run | skipped | skipped | teste/logs; imagem, registry, VPS e deploy proibidos |
| `workflow_dispatch` na branch de correção | run | run | skipped | skipped | teste/logs; imagem, registry, VPS e deploy proibidos |

O comportamento mutável de `push` em `dev`/`prod` continua uma capacidade de
release, não uma autorização desta task nem autorização automática futura.
Enquanto este card não estiver concluído e os gates de release não existirem,
push/merge em `prod`, dispatch default e promoção permanecem bloqueados.

## Riscos

### Bootstrap do workflow

Uma alteração do próprio YAML aterrissada em `prod` gera um evento `push`. Não
é aceitável confiar que o YAML novo será avaliado a tempo, nem permitir que o
guard novo execute build/deploy em `push` de `prod`: pela matriz proposta,
`push/prod` é deliberadamente mutável.

### Drift ou integração ampla

Usar a ref `e304199` como base mistura 419 commits e pode reintroduzir mudanças
de produto, schema ou gate não avaliadas. A implementação precisa nascer do
SHA remoto exato de `prod` e tocar somente os paths explicitamente aprovados no
refreeze que resolver o blocker de gate.

### Guard insuficiente

Verificar apenas `github.event_name == 'push'` deixa o contrato de branch
implícito no trigger e facilita regressão futura. Os dois jobs devem repetir
evento e refs autorizadas explicitamente.

### Prova estática fraca

`grep` ou presença textual isolada não prova estrutura YAML, escopo de
permissões, dependências ou matriz. O teste deve parsear YAML e falhar RED na
base insegura antes de ficar GREEN.

### Gate oficial ausente e Maven não hermético

`program/repositories.json` e o `AGENTS.md` backend definem
`./scripts/qa_gate_card.sh` como gate por card; Maven cru é diagnóstico. Esse
script não existe em `prod@7231574...`. Além disso, `./mvnw test` ativa
PostgreSQL local com `ddl-auto=update` e `@SpringBootTest`. Sem backport do gate
ou waiver humano, PostgreSQL efêmero, credenciais sintéticas, isolamento e
cleanup, o DoD é inalcançável e o teste pode produzir efeito local indevido.

### Pausa não restaurada

Falha entre disable e enable pode deixar CI indisponível. Esse é um impacto
operacional reversível e fail-closed; o procedimento registra estado, janela,
owner e recuperação antes do write.

### Corrida no default branch

Outro write em `prod` entre preflight e landing invalida SHA/digest. O landing
usa push fast-forward sem force a partir do parent exato; qualquer avanço
divergente deve rejeitar a operação. Nenhum rebase ou merge automático ocorre.
Existem três contas com permissão de push: `mathwidu`,
`RafaelAugustScherer` e `jorgeeberhard`. O Gate B exige uma janela exclusiva
reconhecida pelas três contas, sem push, dispatch ou enable concorrente;
ausência dessa confirmação aborta antes do disable.

### Limitação das regras remotas

Em 2026-07-29 a API de branch informou `protected=false`, mas endpoints de
protection/rulesets responderam `403` exigindo plano superior. O procedimento
não assume regras ocultas: revalida `protected`, permissões e comportamento do
push; qualquer mudança ou rejeição aborta e exige refreeze.

## Mitigação

### Alternativas de aterrissagem avaliadas

| Alternativa | Segurança | Confiabilidade/operabilidade | Reversibilidade | Decisão |
|---|---|---|---|---|
| confiar somente no YAML novo durante `push/prod` | não impede a mutação autorizada para `push/prod` | simples, porém falha o requisito de zero build/deploy | revert posterior já seria tardio | rejeitada |
| commit com `[skip ci]`/`skip-checks` | oficialmente suportado para `push`/`pull_request` | sensível à mensagem final/forma de merge; checks podem ficar pendentes | novo commit sem skip | fallback, não escolhida |
| environment approval no deploy | pode segurar somente job ligado ao environment | não impede `build_and_push`; exige configuração remota adicional | removível | insuficiente |
| desabilitar Actions do repositório inteiro | impede triggers | blast radius maior que o necessário | reativável | rejeitada |
| pausar somente workflow `241444909` | impede qualquer trigger desse workflow durante landing | estado `disabled_manually` auditável; janela curta e fail-closed | endpoint oficial `enable` | escolhida |

Fontes oficiais consultadas em 2026-07-29:

- [Disabling and enabling a workflow](https://docs.github.com/en/actions/managing-workflow-runs-and-deployments/managing-workflow-runs/disabling-and-enabling-a-workflow);
- [REST API endpoints for workflows](https://docs.github.com/en/rest/actions/workflows);
- [Workflow syntax for GitHub Actions](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax);
- [Get the authenticated user](https://docs.github.com/en/rest/users/users#get-the-authenticated-user);
- [Skipping workflow runs](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/skip-workflow-runs);
- [Manually running a workflow](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow);
- [Controlling deployments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/control-deployments).

### Estratégia escolhida

Somente Matheus Duarte, owner/admin do repositório pela conta GitHub
`mathwidu`, pode autorizar os writes do Gate B e, separadamente, o dispatch do
Gate C. A autorização deve ser explícita na task orquestradora
`019f6950-6e3e-78e2-8cb0-724e2c4c1cad` e citar base, head, workflow ID,
digest, janela e ações permitidas. Um executor bounded pode operar sob essa
autorização; ela não autoriza force, tag, release, imagem, deploy ou outro card.

O owner também deve declarar a janela exclusiva de mudança e coordenar as outras
duas contas com permissão de push, `RafaelAugustScherer` e `jorgeeberhard`.
Todas as writes devem ser precedidas por `GET /user` com
`login=mathwidu`; o perfil global atualmente distinto não pode ser usado por
engano. O fast-forward e as recapturas de SHA são os controles detectivos;
nenhuma remoção de permissão está implícita.

Sequência de landing:

1. validar Gate A e congelar SHA/tree/digest do commit técnico;
2. revalidar default `prod`, SHA base, workflow ID/path/state/digest, branch
   de correção, PR com head SHA/revisão congelados e resultado
   `PASS/skipped` ou `NOT_RUN` explícito, permissões, `GET /user=mathwidu`,
   ausência de runs ativas, janela exclusiva e paths autorizados no refreeze;
3. capturar lista de runs e metadata de pacote antes da janela;
4. desabilitar somente workflow `241444909`;
5. provar `state=disabled_manually`; imediatamente antes do push, revalidar
   `GET /user=mathwidu`, `prod=<base>`, workflow ID/path/blob/digest,
   `state=disabled_manually` e zero runs não concluídas;
6. executar push fast-forward sem `--force` do commit congelado para
   `refs/heads/prod`; rejeição aborta;
7. antes do enable, provar `GET /user=mathwidu`, `prod=<head>`, workflow
   ID/path/bytes/digest esperado, `state=disabled_manually`, zero run
   `event=push` com `head_sha=<head>` e metadata de package inalterada;
8. reabilitar workflow `241444909` e provar `state=active`;
9. repetir SHA/digest, state e metadata de pacote;
10. observar por janela bounded de no mínimo 60 segundos, com três consultas
    em torno de 0, 30 e 60 segundos por workflow ID, `event=push` e
    `head_sha=<head>`, exigindo zero run de qualquer status;
11. encerrar Gate B sem dispatch. Gate C requer autorização nova.

### Preflights que abortam Gate B

- `prod` diferente da base congelada;
- workflow diferente de ID `241444909`, path
  `.github/workflows/swarm-ghcr-cicd.yml`, estado `active`, bytes ou digest
  esperados;
- run `queued`, `in_progress`, `waiting`, `requested` ou `pending`;
- `GET /user` diferente de `mathwidu`, writers diferentes de `mathwidu`,
  `RafaelAugustScherer` e `jorgeeberhard`, ou janela exclusiva não reconhecida
  pelos três;
- branch remota de correção ausente ou diferente do head validado;
- diff fora dos paths aprovados no refreeze;
- teste RED/GREEN, parse YAML, gate oficial/waiver, `git diff --check` ou fonte limpa não
  comprovados no mesmo SHA;
- `protected=true`, permissão admin/push ausente ou API de workflow indisponível;
- PR sem head SHA/revisão congelados, resultado da run de PR não registrado
  como `PASS/skipped` ou `NOT_RUN`, ou autorização explícita ausente;
- incapacidade de capturar o baseline de runs/pacote sem ler segredo;
- qualquer instrução que exija force, merge amplo, imagem, deploy ou segredo.

### Prova de ausência de efeitos

No Gate B, ausência é demonstrada por workflow `disabled_manually` durante o
fast-forward, zero run do workflow filtrada por `event=push` e
`head_sha=<head>` durante toda a janela bounded, jobs inexistentes para esse
evento e metadata de pacote inalterada.

No Gate C, ausência é demonstrada pela única run `workflow_dispatch` no SHA e
digest exatos: `test=success`, `build_and_push=skipped`,
`deploy_swarm=skipped`, nenhum step dos jobs mutáveis e metadata de pacote
inalterada. Não é necessário ler segredo, conectar à VPS ou executar smoke de
produção para provar que jobs skipped não rodaram.

## Validação

### Gate A — implementação local

`NOT_RUN / NOT_AUTHORIZED` nesta task. O gate permanece bloqueado pelos itens
de DoR abaixo.

## Apêndice histórico superseded — snapshot anterior à seleção da Rota A

Todo o conteúdo desta seção, inclusive os desenhos de Gate A/B/C abaixo,
preserva o snapshot de 2026-07-29 que rejeitou B. Seus estados, autorização,
plano e conclusão foram superseded pela seleção humana da Rota A e pela spike
de 2026-07-30. Nada nesta seção é allowlist ou instrução executável.

### Refreeze documental histórico — rota humana ainda não aberta no snapshot

O decision frame v3 foi rejeitado. B não está fechada e não pode sustentar
`waiting_human`, seleção, preparação ou implementação. A próxima decisão humana
fica com a task orquestradora **depois** da revalidação deste refreeze; esta task
não seleciona A em nome de Matheus.

Decisão humana vigente: **A**. O investimento permanente ocorre no card
`VR-QA-BE-GATE-PROD-COMPATIBLE-001`, desenhando um `gate core` e adapters
mínimos a partir de `prod`. Isso não autoriza copiar automaticamente os ao
menos 166 candidatos nem importar os 419 commits divergentes, e não torna o
card permanente `ready` ou ativo.

#### Opção A — lower bound conhecido, ainda não fechado

O módulo público é `qa_gate_card.sh`; sua interface promete compor o gate
canônico e depois executar smoke HTTP. O seam real está em
`ci_quality_gate.sh`, porém a implementação atual vaza políticas de produto,
fixtures, operações DEV, ratchet, retenção, lock, manifesto e a matriz
PostgreSQL para o caller.

Na ref comparativa `e304199`, o inventário conhecido contém:

- 35 scripts ausentes em `prod`: 17 shell e 18 Python;
- seis contratos/configs candidatos: workflow, `pom.xml`, baseline de ratchet,
  documento Codex Cloud e dois scripts `deploy/ops`;
- 21 arquivos Java que são somente os 15 roots fixos + seis helpers da matriz
  de 49 casos; eles não representam toda a suíte descoberta por
  `--all-tests`;
- 25 migrations Flyway ausentes;
- 75 arquivos Java produtivos ausentes/divergentes na closure estática
  import/type dos roots;
- três `application*.properties` divergentes, sendo
  `application-prod.properties` apenas contexto conservador: o caminho
  observado usa profile não-prod e não torna esse arquivo uma aresta
  obrigatória;
- o path obrigatório adicional
  `src/main/java/com/vanrides/vanrides/service/EmailVerificationLandingPageRenderer.java`,
  lido por `qa_email_confirmation_intent_smoke_test.py` com `read_text()` sem
  guard, presente em `e304199` e ausente em `prod`;
- dois prerequisites Maven já presentes e idênticos em `prod`: `mvnw` e
  `.mvn/wrapper/maven-wrapper.properties`.

A soma é **ao menos 166 candidatos conhecidos, ainda não fechado**, mais dois
prerequisites Maven reutilizados. `backlog/SpringBoot-Cards.md` é uma leitura
literal opcional de `quality_metrics.py`: está ausente em `prod`, mas é
protegido por `exists()` e não aumenta sozinho o total.

Há ainda uma aresta runtime fora desses 21 arquivos: `--all-tests` remove o
filtro Maven `-Dtest` e descobre
`src/test/java/com/vanrides/vanrides/VanridesApplicationTests.java`, presente
em `prod` e removido em `e304199`. Esse teste usa `@SpringBootTest`; a base
ativa por default o profile `local`, `localhost` e `ddl-auto=update`. Antes de
qualquer execução hermética ele precisa ser executado sob profile isolado
diferente, adaptado ou excluído por regra explícita. A rota B escolhe profile
isolado diferente; A deve provar sua própria adaptação.

`A_CLOSURE_NOT_CLOSED` significa que component scan, binding JPA/Flyway, SQL
dinâmico, a descoberta completa de testes e a compatibilidade da base antiga
não permitem afirmar quais candidatos são o mínimo executável. Fechar A exige
task futura autorizada a criar worktree isolada em `prod`, adaptar a matriz e
executar Docker/Maven. Esta task não possui essa autorização.

Blast radius de A: alto em schema, domínio, testes e operação. O rollback Git é
simples apenas antes de landing; migrations e comportamento importado impedem
tratar o conjunto amplo como autocontido. O caminho permanente correto é
separar um `gate core` profundo de adapters de produto, resultado do removal
card, em vez de importar 419 commits.

#### Opção B — proposta v3 rejeitada, tecnicamente não fechada

O texto v3 abaixo permanece somente como registro histórico da hipótese
rejeitada. Não é contrato, waiver válido, candidato executável, allowlist ou
autorização. Os blockers materiais são:

1. `AUTH_ROOT_AND_REPLAY_UNRESOLVED`: public key/fingerprint pinados no runner
   ou binding futuros são autoancorados e não provam a identidade de Matheus.
   Signer, autoridade, tool e schema de `APPROVED`, `CLAIMED` e `CONSUMED` não
   estão definidos operacionalmente. Um blob histórico `CLAIMED` não observa
   receipts descendentes, permite reuso/claims irmãos e não resolve
   autorreferência. Faltam ref canônica, CAS/lease e predecessor único
   verificável para at-most-once.
2. `REMOTE_DISPATCH_AT_MOST_ONCE_UNRESOLVED`: o mesmo bearer carrier/nonce pode
   iniciar dois `workflow_dispatch`, ambos com `run_attempt=1`.
   `maxDispatches=1` é política declarada, não garantia: faltam shared remote
   state, idempotency key, concurrency/run-name/censo Actions ou controlador
   que deixe somente o primeiro run ID entrar no core.
3. `CLAIM_VALIDITY_CONTRADICTION`: a proposta encerra validade no primeiro
   claim e depois exige validade ativa. Uma implementação literal recusaria
   Gate A. Separar `approvalClaimableUntil` de lease/deadline de execução é
   trabalho futuro não desenhado nem fechado nesta task.
4. `CI_EVENT_BINDING_UNRESOLVED`: `ci-pr`/`ci-push` recusam os ledgers Gate A/C,
   mas “todas as fases” exigem o SHA/tree/três paths/closure/JAR fixos do
   hotfix. Outro PR/push possui outro SHA/JAR. Falta contrato run-specific ou
   congelamento explícito da esteira até o removal card.
5. `MAVEN_BUNDLE_AND_BOOTSTRAP_UNRESOLVED`: o bundle Maven não possui produtor,
   formato, archive digest/size, object/artifact ID, armazenamento, autorização
   de upload, retenção, origem de download ou recuperação; PR/push não têm
   canal equivalente. Shell/interpreter, verifier JCS/DSSE/ED25519,
   hash/archive/OCI tools e monitor de rede também não estão integralmente
   bound por path/version/digest/origem/transporte. Remote mutation oculta ou
   quarto path invalida B.
6. `PREBINDING_TEST_CLOSURE_UNPROVEN`: antes do binding houve somente resolução
   e package sem testes. Focused/full Maven, Surefire providers/plugins/runtime
   e PostgreSQL surgiriam pela primeira vez após claim irreversível. Provar a
   closure command-specific exigiria ampliar autorização, o que não ocorreu.
7. `NETWORK_ENFORCEMENT_UNRESOLVED`: “monitor contínuo” não define primitiva,
   enforcement default-deny, capabilities/security options, start barrier,
   loss/drop counters ou prova contra conexões curtas. Polling/inventário não
   sustenta não interferência absoluta.

B deixou de ser apenas um adapter de três paths: depende de PKI/trust root,
ledger/CAS, distribuição content-addressed, binding por evento,
reprodutibilidade e enforcement de rede, recriando um control plane temporário
tão ou mais complexo que o gate permanente. Só pode ser reconsiderada por
capability spike/refreeze separado e explicitamente autorizado, com escopo,
paths e efeitos remotos reais. Nenhum spike/card é criado aqui.

##### Registro histórico da proposta V3 — `REJECTED / DO_NOT_IMPLEMENT`

Identidade: `VR-REL-001-BACKEND-WORKFLOW-DEFAULT-SAFE-001-WAIVER-GATE-A-V3`.
Owner operacional proposto: `QA/REL`. Aprovador humano obrigatório: **Matheus
Duarte**. Motivo: corrigir o dispatch mutante na base antiga sem importar
produto/schema da ref comparativa.

O prazo `2026-08-05T23:59:59-03:00` e a identidade acima permanecem apenas
históricos/propostos. Não foram aprovados, não foram estendidos e não
representam waiver válido.

O waiver substitui somente o **Gate A** oficial para um hotfix, uma base, um
head SHA/tree, um workflow blob/digest e `maxUses=1`. Não dispensa Gate B, Gate
C, mesma versão, rollback, evidência ou removal card.

##### Sequência humana não circular

1. `ROUTE_SELECTION` **histórico e inválido**: v3 propunha Matheus selecionar B
   ou autorizar investimento para fechar A. Essa seleção não está aberta e não
   autoriza refreeze, implementação, qualificação ou Gate A.
2. `LOCAL_CANDIDATE_PREPARATION`: task nova cria worktree na base exata de
   `prod`, implementa e commita somente os três paths e para antes de executar
   o waiver.
3. `DEPENDENCY_TOOLCHAIN_PREPARATION`: antes do binding, preparar wrapper,
   closure Maven, toolchain e PostgreSQL por digest. Pode resolver bytes, mas
   não executa teste, aplicação, DataSource ou JAR.
4. `REPRODUCIBILITY_QUALIFICATION`: ainda antes da aprovação, duas builds
   package-only, sem testes/boot/DataSource, em diretórios independentes usam a
   closure selada e precisam produzir JAR byte a byte idêntico.
5. `BINDING_FREEZE`: o candidato limpo congela head SHA/tree, JAR, closure
   Maven, ambientes por fase, envelopes e manifest completo, sem placeholders.
   O estado é persistido no registro imutável de binding da evidência
   coordenadora; nenhum carrier de waiver existe ainda.
6. `WAIVER_APPROVAL`: após aprovação humana explícita de Matheus, um novo
   commit coordenador cria o carrier Gate A já em `APPROVED`, referencia o
   commit/blob/digest exatos de `BINDING_FROZEN` e grava provenance e assertion
   humana verificável; o runner nunca fabrica esse estado.
7. `GATE_A_CLAIM`: antes de Maven, Docker, DataSource ou JAR, outro commit
   coordenador monotônico grava `CLAIMED`, `uses=1` e nonce. Esse limite vale
   para a execução Gate A; a qualificação pré-binding é uma fase anterior,
   não usa o waiver e não pode ser reinterpretada como Gate A. Falha ao
   persistir ou revalidar o claim aborta sem efeitos. `CLAIMED` já é
   irreversivelmente consumido.
8. `GATE_A_ONE_SHOT`: o runner local executa `gate-a` lendo o blob do commit
   claimado, nunca uma cópia mutável. `PASS`, `FAIL`, abort, crash ou falha de
   cleanup não permitem retry.
9. `GATE_A_RECEIPT`: um commit terminal liga approval, claim, candidato,
   execução e cleanup em `CONSUMED_PASS`, `CONSUMED_FAIL` ou
   `CONSUMED_ABORT`. Crash/SIGKILL antes do receipt deixa `CLAIMED/uses=1`,
   consumido e `HOLD`.
10. Gate B exige autorização humana nova e o receipt
    `GATE_A_PASS/CONSUMED`.
11. Gate C usa ledger, nonce, subject, claim e autorização próprios, com
    `maxDispatches=1`; não reabre, altera nem consome novamente o ledger Gate
    A. O claim Gate C é persistido antes do único dispatch.

##### Módulo, interface e teste

Os únicos paths candidatos da rota B são:

1. `.github/workflows/swarm-ghcr-cicd.yml`;
2. `scripts/qa_workflow_safety_gate.sh`;
3. `src/test/java/com/vanrides/vanrides/WorkflowSafetyContractTest.java`.

`qa_workflow_safety_gate.sh` absorve validação de envelope, preflight, core,
manifesto, smoke e cleanup. A interface versionada é:

- Gate A local:
  `./scripts/qa_workflow_safety_gate.sh --profile workflow-hotfix-v1 --phase gate-a --authorization-repository <explicit-coordination-repository> --authorization-commit <claim> --authorization-path program/waivers/VR-REL-001-BACKEND-WORKFLOW-DEFAULT-SAFE-001-GATE-A.json --authorization-sha256 <digest>`;
- job remoto `test` para todos os eventos:
  `./scripts/qa_workflow_safety_gate.sh --profile workflow-hotfix-v1 --phase ci-event`.

`ci-event` não é um waiver genérico. O runner valida o payload GitHub,
`GITHUB_EVENT_NAME`, SHA/ref/run ID/attempt e resolve internamente:
`workflow_dispatch -> gate-c`, `pull_request -> ci-pr` e `push -> ci-push`.
Somente `gate-c` aceita o carrier Gate C vindo dos inputs congelados; `ci-pr` e
`ci-push` aceitam apenas o envelope do evento GitHub e recusam qualquer
approval/claim de waiver. Após validar o envelope, todas as fases chamam o
mesmo `run_core(subjectDigest, profileDigest, commandSetDigest)`. A única
variação fica na seam de autorização, não na implementação do gate.

O workflow não contém Maven, Git, Docker, PostgreSQL ou validação YAML inline;
setup/upload pinados podem preparar o ambiente e publicar somente o manifesto
sanitizado produzido pelo runner.

`WorkflowSafetyContractTest` faz parse YAML real e valida o path/chamada exatos,
triggers, `permissions`, `needs`, guards e a matriz dos seis eventos. Também
exercita fixtures positivas/negativas dos envelopes `gate-a`, `gate-c`,
`ci-pr` e `ci-push`, incluindo estado consumido, nonce errado, dispatch
duplicado e phase/event mismatch. Mutations para `true`, `echo`, outro
script/profile, chamada direta do core, lógica inline paralela ou reutilização
de approval/claim consumido precisam falhar.

##### Binding, carriers e trust anchors

O manifest aprovado deve conter, no mínimo:

- waiver ID, base SHA, head SHA e tree;
- workflow path, blob e SHA-256;
- lista exata dos três paths, modos Git, blobs e SHA-256;
- runner path `scripts/qa_workflow_safety_gate.sh`, bytes e SHA-256;
- profile `workflow-hotfix-v1`, `profileDigest` e digest do command-set
  ordenado embutido no script;
- blob/digest de `WorkflowSafetyContractTest`;
- schema/digest e Merkle root da closure Maven completa, além do digest do JAR
  reprodutível pré-binding;
- distribuição, versão e digest do JDK interno, OCI index digest, child
  manifest digest e plataforma obrigatórios da imagem de toolchain;
- blobs de todos os paths Maven wrapper, versão e digest da distribuição
  Maven;
- PostgreSQL por OCI index digest, child manifest digest e plataforma, nunca
  só tag;
- versão/digest do Git, identidade/versão do daemon Docker e schema/digest da
  env allowlist;
- constraints/predicados humanos por fase para runner image, host OS/arch, Git
  e Docker, sem alegar igualdade entre hosts diferentes;
- validade, `maxUses=1`, estado, approval/claim nonces, signer key ID/public key
  digest e removal card
  `VR-QA-BE-GATE-PROD-COMPATIBLE-001`.

O registro `BINDING_FROZEN` fica no path já canônico
`program/evidence/VR-REL-001-BACKEND-WORKFLOW-DEFAULT-SAFE-001.md`, em commit,
blob e digest exatos. Ele não é aprovação. O carrier canônico futuro do Gate A
é exatamente
`program/waivers/VR-REL-001-BACKEND-WORKFLOW-DEFAULT-SAFE-001-GATE-A.json` e
**só é criado depois da aprovação humana explícita de Matheus**, já em
`APPROVED`, referenciando aquele binding imutável. De `APPROVED` em diante,
cada transição altera esse path em um commit coordenador novo, de parent único
e estado monotônico. A state machine lógica completa é:

`BINDING_FROZEN -> APPROVED -> CLAIMED/uses=1 ->
CONSUMED_PASS|CONSUMED_FAIL|CONSUMED_ABORT`.

O registro contém a provenance da decisão e uma assertion JCS/DSSE ED25519
sobre card, escopo, binding, expiração, nonce e transição; a chave pública/key
ID e seu digest ficam pinados no runner e no binding, enquanto a chave privada
nunca entra no workspace. O runner exige o repositório coordenador explícito,
revalida sua identidade e verifica assertion, commit, parent, path, Git blob e
SHA-256. Ele lê com
`git -C <explicit-coordination-repository> show <claimCommit>:<path>` e rejeita
a worktree, commit sem ancestor `APPROVED`, transição regressiva ou estado não
`CLAIMED/uses=1`.

O carrier Gate C é separado:
`program/authorizations/VR-REL-001-BACKEND-WORKFLOW-DEFAULT-SAFE-001-GATE-C.json`.
O `DISPATCH_FROZEN` é antes registrado na evidência coordenadora, com subject,
constraints, commit/blob/digest e nenhum carrier. Somente após aprovação humana
Gate C o carrier é criado em `APPROVED`, referenciando esse freeze. Ele possui
assertion humana, nonce/subject próprios e state machine lógica
`DISPATCH_FROZEN -> APPROVED -> CLAIMED/dispatches=1 ->
CONSUMED_PASS|CONSUMED_FAIL|CONSUMED_ABORT`, com `maxDispatches=1`. O claim é
commitado antes do dispatch. Seus bytes/assertion, commit/path/blob/digest e o
receipt terminal `GATE_A_PASS/CONSUMED` são transportados pelos inputs
allowlisted do único `workflow_dispatch`; o runner remoto valida a assinatura e
os digests sem depender de uma cópia mutável. A auditoria coordenadora confirma
o mesmo blob no commit exato. Esse ledger nunca escreve no ledger Gate A.

PR/push usam apenas o carrier do evento GitHub validado e não aceitam esses
ledgers. No momento da aprovação não pode existir `PENDING`, digest ausente,
tag mutável ou range de versão. Qualquer divergência de bytes, versão,
identidade, plataforma, env, SHA/tree/path, envelope ou command-set aborta.

##### Closure Maven e matriz de equivalência

`DEPENDENCY_TOOLCHAIN_PREPARATION` ocorre antes de `BINDING_FREEZE`. Em
diretório temporário exclusivo, o command-set resolve wrapper, distribuição
Maven, dependências e plugins necessários ao JUnit focado, full test e package,
sem executar teste, aplicação, DataSource ou JAR. Após a resolução:

- todos os arquivos regulares efetivos, incluindo JARs, POMs, plugins,
  extensions, checksums e bytes executáveis, entram num manifest Merkle
  allowlisted e ordenado por path relativo normalizado, tipo, modo, tamanho e
  SHA-256;
- o manifest possui schema/version/digest e Merkle root; symlink, special file,
  path traversal, arquivo extra, ausente ou divergente falha;
- metadata mutável de transporte (`*.lastUpdated`, `_remote.repositories`,
  `resolver-status.properties`, locks e tracking equivalente) é removida antes
  do seal e proibida no cache executável; timestamps/uid/gid de diretório não
  participam do root, enquanto todo byte regular participa;
- o repo Maven é verificado, marcado read-only e montado read-only. Gate A e
  Gate C reconstroem o manifest/root completo antes e depois, em modo offline,
  e abortam para qualquer byte extra, ausente ou divergente;
- Gate C recebe a closure como bundle imutável content-addressed ligado ao
  carrier; setup pode transportar esses bytes para cache temporário antes do
  core, sem invocar Maven/JVM, mas o runner só atravessa o seam de execução
  depois de reconstruir exatamente o manifest/root aprovado e selar o cache
  read-only.

Wrapper/distribuição, toolchain OCI index/child/plataforma, JDK/Maven internos,
PostgreSQL OCI index/child/plataforma, env schema e network-monitor bytes ficam
no mesmo binding.

| Campo | Gate A x Gate C |
|---|---|
| base/head/tree, três paths, workflow/test/runner blobs | igualdade exata |
| subject, core/profile/command-set e env schema | igualdade exata |
| Maven closure schema/root, wrapper/distribuição | igualdade exata |
| toolchain child digest/plataforma, JDK/Maven internos | igualdade exata |
| PostgreSQL child digest/plataforma e network-monitor digest | igualdade exata |
| epoch/build inputs e JAR SHA-256 | igualdade exata |
| runner image/release, host OS/arch, Git binary/version/digest | registrados e validados contra predicado humano específico de `gate-a` ou `gate-c` |
| Docker client/daemon identity/version/context/endpoint/transport | registrados e validados contra predicado humano específico da fase |
| GitHub run ID/attempt/ref/event/actor | obrigatório em CI; `NOT_APPLICABLE` explícito no Gate A, nunca omitido |

Campos host-specific nunca são omitidos nem comparados falsamente como se
macOS e GitHub runner fossem iguais. Cada fase possui uma matriz allowlisted de
predicados exatos/ranges fechados aprovada no binding; ambiente fora dela
aborta antes de efeitos.

##### Hermeticidade falsificável

- o runner e todos os filhos iniciam por `env -i`; a entrada é validada contra
  env allowlist versionada/digerida e rejeita `JAVA_TOOL_OPTIONS`,
  `_JAVA_OPTIONS`, `JDK_JAVA_OPTIONS`, `MAVEN_OPTS`, `MAVEN_ARGS`, `MVNW_*`,
  `DOCKER_HOST`, `DOCKER_CONTEXT`, `DOCKER_TLS*`, `DOCKER_CERT_PATH`,
  `TESTCONTAINERS_*`, `SPRING_DATASOURCE_*`, `DATABASE_URL`, `JDBC_*`, `PG*`
  e `.env` herdados;
- Git usa repo explícito, `GIT_CONFIG_NOSYSTEM=1`,
  `GIT_CONFIG_SYSTEM=/dev/null`, `GIT_CONFIG_GLOBAL=/dev/null`,
  `GIT_NO_REPLACE_OBJECTS=1`, hooks em diretório vazio e
  `core.fsmonitor=false`/`core.untrackedCache=false`; source, diff e blobs são
  rechecados antes e depois;
- o diretório temporário nasce `0700`; `HOME`, `MAVEN_USER_HOME`,
  `-Duser.home`, settings Maven vazio/controlado e
  `-Dmaven.repo.local` são exclusivos; `MAVEN_SKIP_RC=1`; o wrapper e a
  distribuição são digeridos; cleanup só ocorre após Maven/JAR e todos os
  filhos estarem terminais;
- Docker herdado é rejeitado. O context e endpoint efetivos são inspecionados
  e aceitam somente transporte local; identity/version do daemon entram no
  manifest. Nenhum Docker socket entra no container de execução;
- a closure Maven aprovada é remontada read-only e revalidada por manifest/
  Merkle root; Maven usa `--offline` e falha para tentativa de resolução;
- a fase de teste/package/smoke usa Maven offline dentro da toolchain pinada,
  em rede Docker `--internal` exclusiva. A rede contém somente a toolchain e o
  PostgreSQL pinado; não publica porta de banco nem possui rota externa;
- PostgreSQL usa index/child digest/plataforma imutáveis, database, schema,
  usuário e senha sintéticos exclusivos, healthcheck `pg_isready`, labels com
  phase/subject/run/SHA e cleanup idempotente pelos IDs de container/rede
  congelados;
- o profile de aplicação é `workflow-safety`, nunca o `local` default;
  `SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop`, SQL logging desativado e
  DataSource aponta somente ao alias/porta do PostgreSQL na rede interna.
  `update`, host/DB conhecido, bind público, container extra ou egress aborta.

##### Não interferência de rede e banco

Antes de qualquer JVM, o runner exige e registra:

- `docker network inspect` com `Internal=true`, exatamente toolchain +
  PostgreSQL, uma attachment por container, zero terceira attachment, zero
  port publish, zero rede `host`/`bridge`/extra, zero `extra-host`,
  `host-gateway`, volume/socket Docker ou namespace compartilhado;
- route table, resolver, `/etc/hosts` e DNS config do namespace; probes
  negativos para gateway, `host.docker.internal`, `gateway.docker.internal`,
  metadata link-local, egress e endpoints compartilhados conhecidos congelados
  no denylist;
- um monitor de conexão allowlisted, cujos bytes/digest estão no binding,
  iniciado dentro do namespace da toolchain antes da JVM e encerrado somente
  após Maven, JAR e smoke. Ele registra metadata sanitizada de conexão, não payload:
  phase, PID, protocolo, destino IP/porta, classe/hash de DNS e timestamp;
- allowlist runtime limitada ao DNS interno necessário, IP efêmero do
  PostgreSQL na porta congelada e HTTP loopback. Qualquer outro destino,
  tentativa, conexão ou DNS fora da lista aborta;
- provas DataSource/connection de `current_database`, `current_schema`,
  `current_user`, server address/port e container ID do PostgreSQL efêmero,
  com valores sintéticos hasheados/allowlisted e sem persistir senha ou JDBC
  completo.

O monitor permanece ativo durante JUnit focado, full Maven, package e smoke; o
manifest registra início anterior à JVM, cobertura contínua, resultado e
digest do log sanitizado. Inspeções de rede/attachments/routes/listeners são
repetidas durante e depois. Se kernel/runner/toolchain não oferecer cobertura
que garanta essa política, Gate A ou Gate C falha.

A frase literal “nenhum banco compartilhado foi tocado” só pode ser emitida
quando topologia, probes, monitor contínuo e evidence DataSource passam. Dois
containers ou `Internal=true` isoladamente nunca bastam; sem prova completa a
frase é proibida e o gate termina `FAIL`.

##### JAR reprodutível e binding pré-aprovação

`prod` não declara `project.build.outputTimestamp`; portanto B só permanece
válida se os três paths conseguirem impor reprodutibilidade sem alterar
`pom.xml`. O binding congela:

- epoch inteiro derivado do timestamp do candidate commit e repetido como
  `SOURCE_DATE_EPOCH` e `-Dproject.build.outputTimestamp=<epoch>`;
- `TZ=UTC`, `LANG=C.UTF-8`, `LC_ALL=C.UTF-8`, `umask 0022`;
- toolchain child digest/plataforma, JDK/Maven internos e closure Maven
  read-only;
- checkout canônico materializado do candidate tree, sem `.git`, com paths,
  modos e mtimes normalizados ao epoch; nenhuma metadata do diretório original
  entra na build;
- command-set package-only e todos os inputs/modes que afetam ZIP/JAR.

Antes de `WAIVER_APPROVAL`, duas builds package-only, sem testes, boot ou
DataSource, em diretórios temporários independentes geram JARs. Ambos devem ser
byte a byte idênticos; o SHA-256 resultante entra em `BINDING_FROZEN`. Gate A e
Gate C recriam checkout canônico e JAR com os mesmos inputs e exigem o mesmo
digest. Se plugin/base ignorar `project.build.outputTimestamp`, houver byte
instável ou for necessário `pom.xml`/quarto path, B aborta, retorna a
`analyzed` e não relaxa para equivalência semântica.

##### Maven, smoke, cleanup e evidência

O command-set fechado executa o JUnit focado e depois a suíte completa, sem
`-Dtest`, sob o profile isolado; assim o legado
`VanridesApplicationTests.java` também é descoberto hermeticamente. Maven
focado/completo não aceita skip, failure, error ou ausência de teste. O package
ocorre em checkout canônico novo e deve reproduzir o JAR SHA-256 congelado em
`BINDING_FROZEN`.

O JAR inicia dentro da toolchain com `SERVER_ADDRESS=127.0.0.1`,
`SERVER_PORT=0`/porta efêmera capturada sem corrida e PID próprio congelado.
Readiness e cada chamada HTTP possuem connect/total timeout e deadline global;
o adapter inspeciona listeners e exige somente loopback/porta/PID esperados.
O smoke usa o mesmo JAR e executa `GET /motoristas=200`,
`OPTIONS /motoristas=200` e `POST /auth/refresh {}` `=400`.

Em sucesso, falha, abort ou sinal, traps interrompem novas chamadas, enviam
`TERM` ao PID proprietário, aguardam bounded, usam `KILL` somente como
fallback, executam `wait` e provam zero processo/listener residual. Depois
removem por IDs congelados os containers e a rede, confirmam ausência por
labels e somente então removem temporários. Falha em qualquer etapa torna o
resultado terminal `FAIL`: em `gate-a`, o claim já consumiu o waiver; em
`gate-c`, o claim já consumiu o único dispatch; em PR/push, a run falha sem
ler ou alterar qualquer ledger. Todos mantêm `HOLD`.

O manifesto de execução allowlisted liga o manifest aprovado aos resultados:
totais Maven, workflow/test/runner/profile/JDK/Maven/toolchain/PostgreSQL/JAR
digests, HTTP, rede/listeners/PID, cleanup e timestamps. A denylist proíbe
segredo, token, `Authorization`, senha, JDBC completo, env dump, `.env`,
e-mail, telefone, GPS, body livre, home path, stdout/stderr Maven raw, XML
Surefire raw e dump de banco. Reter somente manifesto e XML mínimo sanitizados
por no mínimo 90 dias após expiração ou fechamento; raw temporário é destruído.
Os ledgers, assertions, commits de binding/approval/claim/terminal e tombstones
sanitizados pertencem ao audit trail canônico do programa e nunca são apagados
quando o adapter sai.

O adapter executa `git diff --check` e um path/diff ratchet contra a base:
exatamente os três paths, modos/blobs esperados e zero untracked. Rechecagens de
SHA/tree/source/runner/profile antes e depois impedem reutilizar evidência de
outros bytes.

##### Preflights, abortos e rollback por fase

Antes de qualquer efeito, `gate-a` exige o blob imutável do commit
`CLAIMED/uses=1`, ancestor `APPROVED`, assertion humana válida, validade ativa,
nonce inédito e receipt terminal ausente. Ele **não** aceita
`APPROVED/uses=0`; claim inexistente, worktree mutável ou estado já terminal
aborta sem Maven/Docker/DataSource/JAR.

`gate-c` exige carrier próprio `CLAIMED/dispatches=1`, nonce/subject/run
constraints, assertion humana Gate C, `maxDispatches=1`, run attempt `1` e
receipt terminal `GATE_A_PASS/CONSUMED` cujo estado de ledger é
`CONSUMED_PASS`. Ele rejeita qualquer approval/claim Gate A como autorização
Gate C, qualquer claim Gate C já terminal e qualquer segundo dispatch.
`ci-pr`/`ci-push` recusam ambos os carriers.

Todas as fases exigem base/head/tree e três paths iguais ao binding, checkout
limpo, workflow/runner/profile/test/closure/toolchains/imagens/env schema/JAR
com digests congelados, host dentro da matriz da fase, daemon Docker local,
temporário `0700`, cache selado e capacidade de monitorar/limpar por IDs.

Abortam antes ou durante Gate A: anchor/drift, placeholder, path extra, source
dirty, tool/version/digest diferente, env herdada, Git config/replace/hook,
Maven cache/settings/home fora do escopo, Docker remoto/context/TLS herdado,
imagem por tag, Maven closure extra/ausente, JAR não reprodutível,
container/rede/route/attachment extra, monitor incompleto, egress, banco/bind
compartilhado, DataSource/profile/`ddl-auto` divergentes, healthcheck falho,
teste ausente/skip/failure/error, mutation inconclusiva,
smoke/listener/PID divergente, leak na denylist, processo residual ou
cleanup/evidência incompletos. Em Gate A ou Gate C, o claim já está consumido;
não há retry.

Rollback por fase:

- antes de iniciar Maven: remover somente recursos efêmeros pelos IDs
  congelados e registrar receipt terminal coerente com o claim;
- durante/depois de teste, package ou smoke: terminar filhos, comprovar
  listeners/processos ausentes, remover rede/containers e abandonar o
  candidato local;
- após Gate A `PASS`: registrar `CONSUMED_PASS`; qualquer edição invalida o
  resultado e exige novo refreeze/decisão, nunca retorno a `APPROVED`;
- Gate B e Gate C mantêm os rollbacks fail-closed próprios e exigem
  autorizações humanas novas.

##### Equivalência local/remota e gates preservados

- Gate A `gate-a` e workflow `ci-event` chamam o mesmo runner/core/profile/
  command-set do mesmo SHA e produzem o mesmo subject determinístico; somente
  os envelopes e campos host-specific variam conforme a tabela aprovada;
- Gate B preserva a landing fail-closed com workflow individual pausado,
  mesma versão/digest, zero run de push, zero imagem/deploy e autorização nova;
- Gate C exige receipt terminal `GATE_A_PASS/CONSUMED` (`CONSUMED_PASS` no
  ledger), carrier Gate C
  `CLAIMED/dispatches=1`, nonce único e autorização humana própria. Não aceita
  approval Gate A consumido, não altera aquele ledger e não aceita só
  `test=success`;
- o artifact Gate C exige igualdade dos campos determinísticos e validação dos
  campos host-specific contra a matriz da fase, incluindo SHA/tree/workflow/
  runner/core/profile, closure Maven, JDK/Maven, toolchain/PostgreSQL, env,
  JAR, monitor/rede e cleanup;
- PR/push usam `ci-pr`/`ci-push`, não recebem carrier de waiver/dispatch e não
  podem produzir receipt Gate A/C;
- `contents: read`, `packages: write` somente no build, guards, `needs`,
  matriz, `build_and_push=skipped`, `deploy_swarm=skipped` e package metadata
  inalterada continuam obrigatórios;
- o removal card remove adapter, chamadas, fallbacks e configs executáveis,
  mas preserva ledgers/assertions/commits/receipts como audit trail terminal.
  Waiver expirado/consumido não pode ser herdado por outro hotfix.

Regra histórica de validade, rejeitada por contradição: v3 usava como primeiro
evento `2026-08-05T23:59:59-03:00`, claim, execução terminal (`PASS`, `FAIL`,
abort ou crash), drift, cleanup/evidência inválidos ou gate oficial
prod-compatible disponível, enquanto também exigia validade ativa após claim.
O prazo não é estendido e não representa waiver válido.

Risco residual sujeito a aceite posterior de Matheus: a base antiga possui
cobertura de produto menor que o gate canônico; o adapter temporário aumenta
dívida e exige identidade de runner/toolchain disponível em local e CI. O
removal card torna a remoção explícita, mas não elimina o risco antes de sua
execução.

O waiver **não autoriza** push, PR, merge, workflow dispatch, retry,
disable/enable, cancel, pacote, imagem, registry, VPS, staging, piloto,
produção, alteração de backend nesta task ou início de implementação.

#### Comparação de design

| Critério | A — gate canônico | B — adapter temporário |
|---|---|---|
| módulo/interface | pode criar `gate core` profundo e permanente | interface shallow força callers a conhecer PKI, ledger, bundle, CI e rede |
| seam/leak | hoje vaza produto/fixtures/ops; o card permanente deve aprofundar a seam sem trazer 419 commits | leaks atravessam coordenação, Actions, artifact store, runner e daemon |
| localidade | lower bound de ao menos 166 ainda aberto; não implica copiá-los | “três paths” refutado; closure/control plane desconhecidos |
| dependências | precisam ser reduzidas e provadas permanentemente | trust root, CAS/lease, at-most-once remoto, bootstrap e enforcement indefinidos |
| testabilidade | canônica após adaptação executável | replay, dispatch concorrente, closure pré-binding e conexões curtas não provados |
| observabilidade | manifesto/lock/retention maduros no head comparativo | ledgers/claims descritos, mas sem CAS, predecessor único ou censo remoto |
| segurança | melhor estado permanente quando prod-compatible | não há trust/at-most-once/default-deny demonstráveis |
| acessibilidade operacional | exige análise e redução honesta da closure | exige um control plane temporário sem owner/toolchain operacional |
| escala do processo | reutilizável pelo backend/default branch | exceção one-shot recria infraestrutura permanente |
| rollback | Git simples antes do schema; amplo depois | rollback de código passou, mas não resolve replay/control-plane |
| migração | termina no gate canônico | depende do mesmo card permanente e adiciona dívida antes dele |

Recomendação: **A**, via investimento futuro no card permanente existente,
porque concentra a complexidade num módulo profundo reutilizável e remove a
necessidade de inventar um control plane temporário. A recomendação não é
seleção nem autorização. `allowedDirtyPaths.backend=[]`; todos os gates e ambos
os caminhos continuam não autorizados nesta task.

### Gate B — publicação, PR e landing

`NOT_RUN / NOT_AUTHORIZED` e inalcançável neste refreeze. B não possui contrato
válido para produzir o candidato/Gate A exigido. O desenho de landing abaixo
permanece histórico e fail-closed, não um plano executável.

### Gate C — prova remota controlada

`NOT_RUN / NOT_AUTHORIZED` e inalcançável neste refreeze. O at-most-once remoto
não foi provado; o desenho v3 abaixo é histórico e não pode ser usado para
dispatch:

- evento `workflow_dispatch`, ref `prod` e head SHA exato;
- actor, nonce, claim commit/path/blob/digest e `run_attempt=1` iguais ao
  carrier aprovado;
- envelope resolvido pelo runner explicitamente como `gate-c`, nunca `gate-a`,
  `ci-pr` ou `ci-push`;
- digest do YAML em `prod` igual ao validado;
- `test=success`;
- artifact/manifest sanitizado com igualdade dos campos determinísticos e
  constraints host-specific satisfeitas, incluindo SHA/tree, workflow,
  runner/core/profile, closure Maven, JDK/Maven, toolchain, PostgreSQL, JAR,
  network monitor e cleanup;
- `build_and_push=skipped`;
- `deploy_swarm=skipped`;
- package metadata antes/depois inalterada;
- zero imagem, registry mutation, VPS, deploy, piloto ou produção;
- evidência coordenadora atualizada com a mesma versão.

### Abort conditions históricas da prova remota

Se qualquer job mutável deixar de ser `skipped`, o Gate C deve cancelar
somente a run capturada e desabilitar o workflow sob a autorização de
emergência já formulada para esse Gate. O claim permanece consumido e recebe
receipt `CONSUMED_FAIL|CONSUMED_ABORT`; não há retry. Registry deletion,
rollback de deploy, SSH ou VPS não são implícitos; qualquer efeito observado
vira incidente/HOLD e exige autorização humana separada.

## Dependências

- `GOV-PIPELINE-001` — `done`;
- `VR-BE-RUNTIME-PUSH-001` — `done`;
- `VR-QA-BE-GATE-NO-SKIP-001` — `done`;
- `VR-QA-INTEGRATION-PROVENANCE-001` — `done`;
- `VR-QA-BE-GATE-CAPABILITY-SPIKE-001` — `done`, seam sintética corrigida e
  provada em `63/63`;
- `VR-QA-BE-GATE-PROD-COMPATIBLE-001` — `analyzed`, predecessor permanente
  ainda não executável.

Essas dependências sustentam governança e evidência comparativa, mas não
substituem prova do futuro SHA baseado em `prod`.

`VR-QA-BE-GATE-PROD-COMPATIBLE-001` permanece `analyzed` e é o investimento
selecionado da Rota A: separar `gate core`/adapters mínimos e tornar o gate
canônico executável no default branch sem importar os 419 commits. Ele não
está `ready`, ativo ou autorizado.

## Escopo

- registrar a seleção humana da Rota A sem converter os 166 candidatos
  classificados em ordem de cópia;
- preservar B como rejeitada e nunca implementada, com seu snapshot somente no
  apêndice histórico;
- registrar a capability spike bounded concluída/corrigida, sem chamá-la de
  gate ou de execução de adapters reais;
- manter o card permanente `analyzed`, com blocker objetivo e allowlist backend
  vazia;
- preservar rollback e audit trail;
- atualizar somente programa/evidência necessários e preservar o dashboard
  coordenador byte a byte.

## Fora de escopo

- implementar ou testar o diff do workflow nesta task;
- criar branch/worktree, push, PR, merge, tag ou release;
- disparar, repetir, cancelar ou remover workflow run;
- construir/publicar imagem ou alterar package/registry;
- alterar Actions, workflow, environment, secret, variable ou permissão remota;
- Tailscale, SSH, VPS, Swarm, staging, piloto ou produção;
- copiar/cherry-pick/mesclar em bloco a ref `e304199`;
- alterar API, payload, status, auth, CORS, WebSocket, schema, migration ou
  dados do produto;
- regenerar o dashboard depois do commit coordenador final, GPS ou qualquer
  outro card;
- implementar o card permanente nesta task;
- encerrar `R-003` ou promover gates.

## Critérios de aceite

- [x] A permanece lower bound de ao menos 166, não ordem de cópia;
- [x] Matheus selecionou a Rota A em 2026-07-30;
- [x] B está rejeitada, nunca foi implementada e seu desenho permanece apenas
  como histórico não executável;
- [x] a capability spike bounded foi criada e concluída; sua correção
  fail-closed passa `63/63` com doubles sintéticos;
- [x] o card permanente segue `analyzed`, com primeiro blocker
  `REAL_ADAPTER_EXECUTION_UNPROVEN`;
- [x] `allowedDirtyPaths.backend=[]`; Gate A/B/C
  `NOT_RUN / NOT_AUTHORIZED`;
- [x] Rota A, card permanente e spike estão reconciliados entre
  Escopo/Plano/DoR/conclusão;
- [x] rollback e audit trail foram preservados sem inventar adapter/waiver
  executável;
- [ ] nova validação explícita da task orquestradora.

## Plano sequencial

1. registrar a seleção humana da Rota A e manter B rejeitada;
2. concluir a spike bounded no objeto exato de `prod`, sem adapters reais;
3. corrigir fail-closed, lock, immutabilidade, nomenclatura, wrapper,
   snapshots únicos de request/output, bindings e cleanup/lock de
   `BaseException`, incluindo cleanup de acquire conservador e receipts
   booleanos exatos antes da execução/depois do release, guard imediato após
   ownership, limites inteiros serializáveis e precedência de control-flow no
   `FileRunLock`, na mesma task, sem amend/rebase;
4. manter o card permanente `analyzed` e adicionar a spike concluída como
   dependência canônica;
5. validar programa, preservar a projeção já commitada do dashboard sem
   regeneração, commitar coordenação e pedir revalidação;
6. parar antes do experimento de adapters reais.

Após aceite, qualquer experimento de adapters reais exige decisão/autorização
separada. B não será reconsiderada por este plano.

## Rollback

### Coordenação desta task

Na coordenação, reverter primeiro o commit corretivo final filho de
`7f30ac4` (SHA exato enumerado no handoff final), depois
`7f30ac4de119941e0f5e865a1194b6ab0e77bdd9`,
`1078c6ddf6c173d9c1e14e8c77a7e5fcade80dd1`,
`8442d8c076c7de9ef17288115320e20287470b92`,
`b4830d33fe028916892cf5865bb5a2fd13fa2268` e então
`45b4f27771336f9a76ccedfd8986d9cd3e6af82c`. Isso preserva `dc5f5f55`,
`c72b85d8`, `ba6bc072` e o snapshot histórico.

No backend, em rollback separado, reverter primeiro
`e3ea8a8c45c3aa5fb8c87b1c12b67637d8848b29`, depois
`66301f5686487f54ed3b3f1077a131eb18b162da`, depois
`8ad2fca45f1c7e50b010aaaa4647c3f32e7b205a`, depois
`31c0dbbd99c2c3cb810711fd7d8b84ae02feefab`, depois
`b58942d914c16dc7fd4e413c7949c59e50587811`, então
`69aa6c34baa18918c0bdd04bc4d84207008da148`, então
`29f2309c92cf8d43ecd350d997c577069ea50cb2`, então
`fbddba1847e13707b7301b6afdc5a71122e56075` e então
`b15837c5ec3ebf4e73ae546efe2d00704947322c` para retornar ao protótipo
`2cd9c279`. Os 170 untracked e as 51 mudanças do app permanecem preservados.

### Implementação local futura

As instruções históricas de B não se aplicam: B nunca criou adapter, waiver,
claim ou ledger executável. O texto abaixo só se torna aplicável a recursos
realmente materializados por uma futura Rota A autorizada.

Antes do claim, abandonar a worktree/branch isolada ou reverter o commit
técnico. Depois de `CLAIMED`, nunca voltar o ledger a `APPROVED`: persistir
receipt terminal/`HOLD` e remover somente recursos efêmeros. Nenhum estado
remoto ou dado do produto muda.

### Publicação/PR futura

Se Gate A ou revisão falhar, não aterrissar. Branch e PR permanecem sem merge;
remoção/fechamento exige autorização separada. Evidência de outro SHA não pode
ser reutilizada.

### Landing remoto futuro

Se o disable falhar, não fazer push. Se o push fast-forward falhar, reabilitar
o workflow antigo somente se `prod`, blob e digest ainda forem exatamente a
base congelada; qualquer drift mantém o workflow `disabled_manually` e
`production=HOLD`. Se o push suceder mas SHA/digest divergir ou o enable
falhar, manter o workflow desabilitado e `production=HOLD`.

Antes do Gate B, preparar e validar localmente um commit de reversão filho do
head que restaure exatamente o blob antigo
`395ce850c962fca562ca833444e80bc1edb7ae4a` **e remova ambos os novos paths**:
`scripts/qa_workflow_safety_gate.sh` e
`src/test/java/com/vanrides/vanrides/WorkflowSafetyContractTest.java`. Se
existir blob seguro canônico aprovado, o revert pode restaurá-lo em vez do blob
vulnerável, sempre por digest congelado. Se restaurar o blob vulnerável, o
workflow não pode ser reabilitado: permanece `disabled_manually`, com
dispatch/push/release bloqueados, `production=HOLD` e `R-003` mitigando.

O revert nunca apaga nem reabre ledgers/assertions/claims/receipts. Eles ficam
terminalmente `CONSUMED`, `EXPIRED` ou `REVOKED` e não reutilizáveis. O commit
só pode ser publicado sem force enquanto o workflow estiver desabilitado e sob
autorização explícita de Matheus.

### Prova remota futura

Se Gate C divergir, não retry/rerun. Cancelar a run específica e desabilitar o
workflow somente se isso estiver incluído na autorização de emergência do
Gate C. Persistir receipt terminal Gate C, sem alterar o ledger Gate A.
Preservar run/logs/metadata sanitizados e audit trail; não apagar package,
alterar VPS ou reinterpretar evidência de outro SHA.

## Definition of Ready

- [x] resultado, prioridade P1 e vínculo com `VR-REL-001`/`R-003` definidos;
- [x] baseline local e remoto, bytes/digests e divergência revalidados;
- [x] Observed, Inferred e Proposed separados na evidência canônica;
- [x] corrigir A para ao menos 166 candidatos conhecidos e registrar renderer,
  legado `--all-tests`, backlog opcional e config prod conservadora;
- [x] registrar que B não é módulo profundo: sua interface vaza trust root,
  ledger/CAS, remote at-most-once, bundle/bootstrap, CI binding, test closure e
  network enforcement;
- [x] congelar `AUTH_ROOT_AND_REPLAY_UNRESOLVED`,
  `REMOTE_DISPATCH_AT_MOST_ONCE_UNRESOLVED`,
  `CLAIM_VALIDITY_CONTRADICTION`, `CI_EVENT_BINDING_UNRESOLVED`,
  `MAVEN_BUNDLE_AND_BOOTSTRAP_UNRESOLVED`,
  `PREBINDING_TEST_CLOSURE_UNPROVEN` e
  `NETWORK_ENFORCEMENT_UNRESOLVED`;
- [x] congelar rollback dos dois novos paths e preservação do audit trail;
- [x] criar `VR-QA-BE-GATE-PROD-COMPATIBLE-001` e depois reconcilá-lo como
  investimento permanente da Rota A, não como removal successor de B;
- [x] registrar a seleção explícita da Rota A por Matheus;
- [x] criar e concluir `VR-QA-BE-GATE-CAPABILITY-SPIKE-001`, com correção
  fail-closed `63/63`, sem adapters reais;
- [x] manter backend allowlist do card permanente vazia;
- [x] Gate A, Gate B e Gate C separados e não autorizados nesta task;
- [x] dependências de governança e a spike bounded concluídas;
- [x] app dirty, untracked da coordenação, backend e Maps não conflitam;
- [ ] validação orquestradora explícita deste refreeze;
- [ ] DoR completo e autorização humana futura antes da implementação do card
  permanente, de adapters reais ou de qualquer efeito remoto.

## Evidências

| Evidência | Comando/local | Resultado | SHA/digest |
|---|---|---|---|
| refreeze após rejeição v3 | `program/evidence/VR-REL-001-BACKEND-WORKFLOW-DEFAULT-SAFE-001.md` | `ANALYZED / B_NOT_CLOSED` | coordenação desta task |
| default remoto | GitHub Contents API em `prod` | inseguro observado | `f3e0551e...` |
| ref comparativa | GitHub Contents API em `e304199` | guards comparativos | `f05e8c9c...` |
| workflow futuro | Gate A/B/C | `NOT_RUN / NOT_AUTHORIZED` | ainda inexistente |
