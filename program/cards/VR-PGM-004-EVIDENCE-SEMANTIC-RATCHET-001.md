# VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001 — Validar semanticamente evidência canônica

## Resultado esperado

O programa só conclui um card novo quando a narrativa Markdown, o sidecar JSON
v2, o Gate Plan congelado e os manifests de saída provam exatamente critérios,
run, ambiente, comandos/confirmações, versões, bytes, retenção, sanitização e
limites. Path, ID, texto declaratório, checksum sem bytes ou `PASS` isolado não
satisfazem o ratchet.

## Estado

`analyzed` — contrato técnico e Gate Plan bootstrap estão congelados abaixo.
Antes de qualquer transição separada para `ready`, precisam estar concluídos
`VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` e o predecessor técnico
`VR-PGM-004-EVIDENCE-V2-BOOTSTRAP-001`.

## Dono operacional

`PGM/QA`, com `SEC`, `PRIV` e owners de repositório como revisores.

## Risco ou objetivo associado

`F-PRIV-010`, `R-010`, `VR-PGM-004`, G0 e todos os gates.

## Impact Analysis

### Protocolo imutável de ativação, implementação, evidência e fechamento

O card usa a cadeia direta `A → I → E → C` e nunca calcula hash
autorreferente ou depende de CI externo como único trust anchor:

1. `A` é o commit de ativação;
2. a proposta de `I` contém parser, testes, schema, registry, Gate Plan,
   challenges por tentativa, template, batch, aresta G7, migração de allowlists
   e código final. Antes de
   qualquer `git add`, `write-tree` ou commit, o runner varre todos os bytes
   novos/alterados de `A..I-prospectivo` no filesystem e exige zero matches;
3. somente após esse preflight `PASS`, os mesmos buffers selados entram em
   blobs/index/tree pelo protocolo abaixo, sem reler paths mutáveis. `I` é filho
   direto de `A`, não contém evidência própria nem referência ao próprio SHA. Os
   manifests de challenge comprometidos em `I` são controles pre-signature, não
   outputs nem evidência de aceite;
4. cada versão normativa é reconstruída no SHA de `I` ou produto declarado.
   Gates rodam nesses snapshots limpos, com caches/outputs fora das worktrees;
5. a proposta de `E` é construída primeiro em `${RUN_TMP}/evidence-proposal`,
   fora do Git/worktrees, e contém somente Markdown, sidecar, manifests,
   index/report, seção QA Log e metadata preclosure derivada: estado `validating`,
   evidências registradas e `card.commits` igual à lista ordenada de versões
   `IMPLEMENTATION`. Schema, digests e todos os bytes são varridos fail-closed;
6. somente se o preflight de `E` passar, seus buffers selados são materializados
   pelo mesmo protocolo e comparados ao manifest externo. `E` é filho direto
   de `I`; o sidecar aponta para `I`/produto, nunca para `E`, e seus hashes são
   acíclicos. O validador então reconstrói e revalida os bytes exatos de `E`;
7. a proposta de `C` também passa por scan/patch preflight antes do index selado.
   Depois, `C` é filho direto de `E` e muda somente o patch semântico fechado
   abaixo: closure manifest, cutoff/ref/policy v2, commit de evidência,
   checklists/estado e projeções determinísticas;
8. auditoria posterior reconstrói `I` e `E`, valida `A→I→E→C` e os diffs
   restritos `I..E`/`E..C` apenas com Git. CI pós-`C` é defesa adicional, não a
   única prova de fechamento.

### Ativação fechada

O auditor também reconstrói `P=A^`. Uma ativação ordinária `P..A` possui
exatamente `program/program.json`, o card-alvo e `.dev-harness/dashboard.md`:
no JSON mudam somente `updatedAt=activationAt`, status `ready→in_progress`,
`activeCardId:null→cardId` e `nextCardId=cardId`; no Markdown somente Estado;
dashboard é projeção. Blockers precisam estar vazios e todo código, registry,
policy, evidência, allowlist, dependency, outro card e repositório de produto
permanece byte-idêntico.

Este bootstrap PGM acrescenta uma única mutação de ativação, ainda no mesmo
`program.json`: `/policy/evidenceV2BootstrapRoot`, definida pela cerimônia
humana abaixo. Sem assertion humana válida, `A` não é criado e o card permanece
`ready`; não existe fallback de agente. As primitivas que criam `A` já estão
versionadas em `P` pelo predecessor
`VR-PGM-004-EVIDENCE-V2-BOOTSTRAP-001`; `A` usa exatamente o staging selado e o
scanner pinados nesse predecessor, e o auditor valida `P→A→I`.

Retry não transforma implementação falha em baseline. `A2` é filho da tentativa
abandonada e é construído a partir de todos os blobs da tree do `A` inicial, não
da worktree nem da tree abandonada. A única diferença deliberada em relação a
`A` é `program/program.json:/updatedAt=retryAt`; o objeto do card fica exatamente
com status `in_progress`, blockers/commits/evidence vazios e active/next iguais
ao card, o Markdown é o blob exato de `A` e o dashboard é a projeção do novo
JSON. Todo implementation/evidence/policy/registry/Gate Plan/challenge, outro
card e path de produto volta byte-idêntico a `A`. Assim `A2..I2` contém novamente
toda a implementação corrigida e novos challenges da tentativa. `A2` usa a
primitiva selada já presente em `I` antes de restaurar sua tree, valida o parent
abandonado e compara a tree final a `A`; path residual falha.

Os símbolos sempre designam a tentativa canônica que chegou a `C`. Proposta que
falha no preflight de sanitização nunca entra no index, object database ou
história: o diretório externo é removido sem logar conteúdo, e match de
credencial aciona incidente/rotação somente com autoridade humana. Se uma falha
não sensível surgir depois de `I`/`E`, a tentativa fica imutável e um novo
commit `A2` restaura deterministicamente a tree de `A` conforme o patch fechado
acima; o diagnóstico permanece nos commits abandonados e no resultado
out-of-band sanitizado, nunca em campo livre ou locator novo dentro de `A2`. O
`I2` corretivo é filho direto de `A2`, seguido por `E2` e `C`. A evidência não
sensível falha permanece apenas na história da tentativa abandonada. Não há
amend/reset/rebase nem fix intercalado. Se scanner novo descobrir dado sensível
já commitado, o programa entra em `FAIL_CLOSED_HOLD`, inicia incidente SEC/PRIV
e exige autorização explícita para rotação e purge seguro de refs/objetos/cópias;
essa tentativa jamais é preservada como diagnóstico. Cutoff/closure usam apenas
`I2`/`E2`; tentativas abandonadas não sensíveis são diagnóstico não normativo.

`workspaceContentSha256` nunca usa a tree `E` que contém o próprio sidecar,
Markdown ou output. Gate normativo exige `sourceKind=GIT_COMMIT_CLEAN`, status e
diff vazios e zero untracked no snapshot reconstruído. Dirty worktree pode ser
diagnóstico suplementar, mas jamais sustenta `PASS` v2.

O sanitizer candidato nunca valida sozinho o próprio bootstrap. `P` contém o
manifest e o scanner mínimo versionados pelo predecessor, com ID
`VANRIDES_BOOTSTRAP_SENSITIVE_V1`; `A` copia seus digests para o pin bootstrap e
o Gate Plan de `I` precisa reproduzi-los. Preflights de `A` usam exclusivamente
o scanner de `P`; preflights de `I`, `E` e `C` usam a união desse scanner pinado
com a proposta v2. Fixtures de
segredo/credencial/PII/GPS do baseline precisam continuar falhando; `I` não pode
remover nem enfraquecer esse mínimo. Cards pós-cutoff usam o ruleset do registry
mais o mesmo piso ratcheteado.

### Raiz humana e migração do registry

O predecessor `VR-PGM-004-EVIDENCE-V2-BOOTSTRAP-001` deixa em `P`, já testados e
imutáveis, `scripts/evidence_v2_bootstrap.py`, seu teste e
`program/evidence/EVIDENCE_V2_BOOTSTRAP_MANIFEST.json`. O manifest é JSON fechado
exatamente `{schemaVersion:1,cardId,tool,test,sanitizer,
sealedCommitProtocolVersion:1}`; `cardId` é o ID do predecessor, `tool` e `test`
são `{path,sha256}`, e `sanitizer` é `{id,sha256}` com o ID mínimo acima. O
auditor resolve os blobs em `P`, recalcula todos os digests, exige evidência
`PASS` e closure revision do predecessor na história first-parent e rejeita
qualquer mutação desses três paths em `P..A` e `A..I`.

Antes de `A`, `python3 scripts/evidence_v2_bootstrap.py root-ceremony` gera nonce
aleatório de 32 bytes, mostra source/digests do manifest e fingerprint da chave
pública Ed25519 escolhida pelo usuário e exige confirmação interativa humana. O
usuário assina fora do workspace o payload DSSE JCS de tipo
`application/vnd.vanrides.evidence-root-bootstrap.v1+json` exatamente
`{cardId,parentRevision,subjectId,keyId,publicKeySha256,challengeNonce,
approvedAt}`. A chave privada nunca é lida. O comando verifica self-signature,
fingerprint, nonce ausente de todo corpus, parent corrente e seu próprio
tool/manifest/sanitizer digest antes de construir `A` com a primitiva selada de
`P`; cancelamento/ausência preserva o card `ready`.

Somente então `A` grava `/policy/evidenceV2BootstrapRoot` exatamente
`{schemaVersion:1,subjectId,keyId,algorithm:ED25519,publicKey,
publicKeySha256,challengeNonce,parentRevision,approvedAt,assertionSha256,
bootstrapManifestSha256,bootstrapToolSha256,bootstrapSanitizerSha256}`. A
assertion externa reaparece byte-exata no `trustRoots[]` do registry em `I`; o
digest/payload precisam bater o pin de `A`. O registry bootstrap possui esse
único root e `humanAuthorities=[]`. Public key/assertion são públicas e passam
sanitização; nenhuma identidade operacional é inferida do root.

Em `I`, `program_control evidence-root-ceremony` delega para a mesma primitiva
imutável de `P`, e fixtures replayam seu transcript. Source/digest/resultado
divergente falha; não existe harness gerado fora do repositório nem primitive
prospectiva usada para criar o próprio baseline.

Cards ordinários exigem `sidecar.registry.sha256` exatamente igual a
`policy.evidenceV2.registrySha256` e não podem alterar registry/pin. Enrollment,
revogação ou rotação só ocorre em card dedicado cujo Gate Plan declara
`policyMutation=REGISTRY_MIGRATION` e congela
`{oldRegistrySha256,newRegistrySha256,oldRootSha256,newRootSha256,changedSections,
challengeEntryId,rootAssertionArtifactId,
newRootAssertionArtifactId,adminAssertionArtifactIds}`. Nonces são 32 bytes,
globally unique e comprometidos pelo manifest por tentativa desse entry em `I`
antes de qualquer assinatura.
Root SHAs usam JCS do descriptor `humanTrustRoot`; `newRootSha256` e sua
assertion são null quando não há rotação.

O root antigo assina DSSE de tipo
`application/vnd.vanrides.evidence-registry-migration.v1+json` ligando todos os
campos, card/run e diff canônico old→new. Primeiro enrollment, com zero admins,
exige esse root. Quando houver ao menos dois subjects distintos com capability
`TRUST_REGISTRY_ADMIN`, também exige quorum de dois; root rotation exige ainda
assinatura da nova root sobre o mesmo challenge. `C` da migração atualiza
atomicamente apenas `registrySha256` e, se aplicável, `humanTrustRoot`; todo o
restante da policy permanece byte-idêntico. Assertion ausente, expirada,
replayed, root/admin divergente ou diff de registry fora de
`changedSections` falha; cada item do array é único e pertence a
`trustRoots`, `humanAuthorities`, `roles`, `sanitizationRulesets`,
`retentionPolicies` ou `deletableStores`. Assim um writer não cadastra a própria
chave, regras/policies
também podem evoluir de forma assinada e a primeira chave pode ser legitimamente
enrolada pelo root humano.

### Staging sem TOCTOU

O preflight não faz “scan do path e `git add` depois”. Para cada path regular,
o runner abre com no-follow, valida stat/fstat, lê uma única vez para buffer de
memória size-bounded e executa os dois scanners nesse buffer e no path relativo
normalizado; a mensagem fixa também é varrida. Paths, modos, deleções e SHA-256
formam um manifest selado exatamente `{schemaVersion:1,phase,cardId,
parentRevision,phaseTimestamp,paths,commitMessage,author,committer}`; cada path é
`{path,operation,mode,size,sha256,gitObjectId}` e author/committer são
`{name,email,date}`.

Metadata é fixa, varrida e não vem de config local: name
`VanRides Program Pipeline`, email `pipeline@localhost`, author=committer
e date=`phaseTimestamp` UTC; esse timestamp é o `updatedAt` do patch da phase,
em `E` também é `recordedAt` e em `C` é `validatedAt`. Mensagens são exatamente
`chore(program): activate <CARD_ID>`, `chore(program): retry <CARD_ID>`,
`chore(program): implement <CARD_ID>`, `test(program): evidence <CARD_ID>`,
`chore(program): close <CARD_ID>` ou `fix(program): recover evidence v2` conforme
a phase. Após `PASS`, sem reabrir arquivo,
o runner:

1. cria `GIT_INDEX_FILE` temporário fora da worktree e o inicializa com a tree
   do parent exato;
2. envia cada buffer já escaneado por stdin a `git hash-object -w --stdin` e usa
   somente o OID retornado em `git update-index --cacheinfo`; aplica deleções do
   manifest e aceita apenas modos `100644|100755`;
3. executa `write-tree`, compara a tree resultante ao manifest path/modo/blob e
   cria o commit com `env -i`, author/committer explícitos, HOME temporário,
   `GIT_CONFIG_NOSYSTEM=1`, `GIT_CONFIG_GLOBAL=/dev/null`,
   `git -c commit.gpgSign=false commit-tree -p <parent>` e mensagem por stdin;
4. move a ref por compare-and-swap somente se o parent ainda for o esperado,
   reconstrói o commit, exige somente headers tree/um parent/author/committer,
   sem gpgsig/encoding/mergetag, compara todos os bytes ao manifest e repete
   scan/digest/patch completo.

Assim, swap de inode/path após o scan não alcança o object database. Divergência
de worktree pode deixar diagnóstico não staged, mas nunca substitui buffers
aprovados nem entra no commit. O algoritmo é obrigatório em `A`, `A2`, `I`,
`E`, `C` e recovery; `git add`, `git commit -a` e `hash-object` lendo path são
proibidos.

### Arquivos, módulos e integração

- `scripts/evidence_v2.py` concentra decode, schema, Git/bytes, Gate Plan,
  critérios, cutoff, truth table e sanitização; `program_control` apenas chama a
  API e traduz o código sem duplicar regra;
- `scripts/qa_evidence_v2_gate.sh` executa exatamente o Gate Plan e produz
  manifests size-bounded; seu teste prova ordem, aborto e propagação de exit;
- `program/evidence/<CARD_ID>.md` continua sendo a narrativa canônica;
  `program/evidence/<CARD_ID>.json` é o sidecar; outputs normativos vivem apenas
  em `program/evidence/artifacts/<CARD_ID>/`;
- todo card v2 possui antes de `ready`
  `program/evidence/gate-plans/<CARD_ID>.json`, com digest, critérios e política
  de challenge estáveis. Nonce e janela temporal nunca ficam no Gate Plan:
  pertencem ao manifest renovável da tentativa em `I`;
  Este PGM é a única exceção bootstrap: a tabela abaixo é congelada pelo
  fechamento do predecessor, integra o commit `A` e deve ser materializada
  byte-equivalente em `I` antes dos gates.
- o closure manifest fica em
  `program/evidence/artifacts/<CARD_ID>/closure-validation.json`; ele referencia
  `I` e `E`, nunca `C`, e não integra o sidecar criado em `E`.

### Limites e primitivas comuns do schema v2

O sidecar é UTF-8/JSON de no máximo 256 KiB; Markdown no máximo 1 MiB; Gate Plan
no máximo 128 KiB; manifest normativo individual no máximo 10 MiB e soma no
máximo 50 MiB. O decoder preserva pares e rejeita chave duplicada antes de criar
o objeto. Todo object, inclusive nested/union, usa `additionalProperties=false`;
arrays possuem `minItems`/`maxItems`, IDs são únicos e referências são totais.

- SHA Git: `^[0-9a-f]{40}$`; SHA-256: `^[0-9a-f]{64}$`;
- card ID: `^[A-Z0-9]+(?:-[A-Z0-9]+)+$`, 3–128 bytes;
- run/entry ID: `^[A-Z0-9][A-Z0-9._-]{2,127}$`;
- timestamp: UTC estrito `YYYY-MM-DDTHH:MM:SSZ`, segundo real e ordem temporal
  validada; strings não livres têm 1–4096 bytes conforme o schema;
- path: relativo POSIX, NFC, sem NUL, vazio, absoluto, `.`/`..`, backslash ou
  componente symlink; resolução usa no-follow e precisa permanecer sob a raiz;
- leitura de arquivo rejeita non-regular, troca inode/dev entre stat/open/fstat,
  tamanho acima do limite e digest alterado durante a leitura.

Valor ou enum novo exige `schemaVersion` novo; não existe fallback
`OTHER` para prova normativa. Strings potencialmente sensíveis nunca aparecem
na mensagem de erro.

### Top-level e tipos fechados

O sidecar possui exatamente: `schemaVersion`, `cardId`, `runId`, `recordedAt`,
`evidenceKind`, `gatePlan`, `registry`, `evidenceMarkdownSha256`, `environment`,
`versions`, `commands`, `confirmations`, `artifacts`, `result`, `sanitization`,
`retention`, `scope` e `waiver`.

- `schemaVersion`: integer const `2`;
- `evidenceKind`: `CODE|DOCUMENT|SMOKE|DEVICE|RELEASE|HUMAN_CONFIRMATION`;
- `result`: `PASS|FAIL|BLOCKED|HOLD`;
- `gatePlan`: object exato `{path, sha256}`; path canônico
  `program/evidence/gate-plans/<cardId>.json` e bytes iguais ao digest;
- `registry`: object exato `{path,sha256}`; path canônico
  `program/evidence/EVIDENCE_V2_REGISTRY.json`, e todas as refs de role,
  ruleset, policy e store resolvem exclusivamente nesses bytes;
- `evidenceMarkdownSha256`: bytes exatos de
  `program/evidence/<cardId>.md`, nunca hash do sidecar;
- collections: `versions` 1–16, `commands` 1–64, `confirmations` 0–32 e
  `artifacts` 1–128.

### Environment e versões

`environment` contém exatamente `kind`, `timezone`, `sanitized`, `runner` e
`toolchains`.

- `kind`: `LOCAL|CI|SIMULATOR|DEVICE|STAGING|PRODUCTION`; `sanitized=true`;
- `timezone`: IANA, 1–64 bytes;
- `runner`: `{kind,name,version,imageDigest}`; kind
  `LOCAL_PROCESS|CI_RUNNER|DEVICE_HARNESS`, e `imageDigest` é `null` ou
  `sha256:<64hex>`;
- `toolchains[]`: 1–32 objetos `{name,version,digest}`, nomes únicos; digest é
  `null` ou SHA-256. O conjunto precisa cobrir OS e todo Python/JDK/Flutter/Dart,
  Docker/image, browser ou device consumido pelo Gate Plan.

Cada `versions[]` contém exatamente `{id,repository,role,sourceKind,revision,
tree,statusSha256,dirtyCount,workspaceContentSha256}`.

- `repository`: `coordination|backend|app|maps`; `role`:
  `IMPLEMENTATION|ACTIVATION_BASELINE|CONSUMER_BASELINE`;
  `sourceKind=GIT_COMMIT_CLEAN`;
- `dirtyCount=0`, `statusSha256` é SHA-256 dos bytes vazios, e revision/tree são
  resolvidos no repositório declarado;
- na validação preclosure de `E`, toda e somente versão `IMPLEMENTATION` precisa
  existir como `repository:<40hex>` em `card.commits`, na ordem estável das
  versões no Gate Plan; o validador resolve esses refs diretamente e não exige
  referência futura a `E`;
- em `C`, `card.commits` precisa ser exatamente a lista preclosure seguida por
  `coordination:<E>`; `ACTIVATION_BASELINE`/`CONSUMER_BASELINE` permanecem
  congeladas no Gate Plan e nunca podem se passar por implementação/evidência;
- todos os repositórios do card e todo produtor/consumidor do Gate Plan possuem
  versão, sem versão de repositório fora do escopo.

O fingerprint é recalculado numa worktree limpa do SHA: frames binários
`tag + NUL + tamanho-decimal + NUL + payload`, nesta ordem: `HEAD`; `STATUS` de
`git status --porcelain=v1 -z --untracked-files=all`; `DIFF_HEAD` de
`git diff --binary HEAD --`; depois, para cada path emitido por
`git ls-files --others --exclude-standard -z`, `UNTRACKED_PATH`,
`UNTRACKED_MODE` (`mode & 07777`) e `UNTRACKED_CONTENT`. Qualquer status/diff,
untracked, symlink ou non-regular bloqueia a prova normativa. O digest e os
counts são persistidos; bytes/path sensível não são logados.

### Gate Plan, comandos, confirmações e critérios

O Gate Plan JSON também é recursivamente fechado e contém exatamente
`schemaVersion=1`, `cardId`, `criteria`, `execution`, `bootstrapSanitizer`,
`trustAnchor`, `policyMutation`, `registryMigration` e `entries`.
`trustAnchor` é exatamente `{source,keyId,publicKeySha256,assertionSha256,
registrySha256}`; source é `BOOTSTRAP_POLICY|EVIDENCE_V2_POLICY`. Com
`EVIDENCE_V2_POLICY`, key/assertion/registry precisam bater exatamente
`policy.evidenceV2.humanTrustRoot` e `policy.evidenceV2.registrySha256` vigentes.
Com `BOOTSTRAP_POLICY`, permitido somente neste PGM, key/publicKey/assertion
precisam bater `A:/policy/evidenceV2BootstrapRoot`; `registrySha256` é o digest
dos bytes do registry em `I`, que precisa conter exatamente um `trustRoots[]`
reproduzindo essa assertion/pin e `humanAuthorities=[]`. Não se exige um registry
digest inexistente em `A`, e nenhuma outra divergência recebe essa exceção.
`bootstrapSanitizer` é
`{id,sha256}` e, neste bootstrap, referencia
`VANRIDES_BOOTSTRAP_SENSITIVE_V1` congelado por `A`. `policyMutation` é
`EVIDENCE_V2_BOOTSTRAP|NONE|REGISTRY_MIGRATION|RECOVERY`; este PGM usa o
primeiro, card ordinário usa `NONE`, o plan reservado usa `RECOVERY`, e somente
`REGISTRY_MIGRATION` aceita o object fechado descrito acima (nos demais,
`registryMigration=null`). `criteria[]`
registra
`{id,textSha256}`: o hash usa UTF-8/NFC do texto do critério após remover apenas
o marcador checkbox e colapsar whitespace. `entries[]` registra `{id,type,
commandOrPrompt,cwd,versionRoles,outputKind,provesCriteria,authorityRoles,
challenge}`; type é
`COMMAND|HUMAN_CONFIRMATION`. IDs e critérios são únicos; a união
`provesCriteria` é exatamente o conjunto de critérios do card, sem extra ou
órfão, e cada critério possui ao menos uma prova. `COMMAND` exige
`authorityRoles=[]` e `challenge=null`; `HUMAN_CONFIRMATION` exige 1–8 roles
presentes no registry com capability `CONFIRMATION` e challenge policy fechada
`{mode,path,ttlSeconds}`. `mode=PER_ATTEMPT_MANIFEST`; path é literalmente
`program/evidence/artifacts/<cardId>/challenges/<entryId>.json`; ttlSeconds é
integer `300..604800`. A confirmação usa uma das roles.

Para cada entry humana, a tentativa cria em sua proposta `I` um manifest JSON
fechado exatamente `{schemaVersion:1,cardId,runId,entryId,activationRevision,
gatePlanSha256,nonce,issuedAt,expiresAt}` no path congelado. Nonce é base64 de 32
bytes e único em todo o corpus Git; activationRevision é `A` ou `A2`, e
`expiresAt=issuedAt+ttlSeconds`. O manifest entra no buffer/preflight/tree de `I`
antes de qualquer assinatura, aparece em `artifacts[]` como `SOURCE_MANIFEST`
normativo com `SOURCE_GIT_BLOB` em `COORD-I` e é referenciado pelo locator/digest
abaixo. Gate Plan e challenge manifest nunca se autorreferenciam.

Se o challenge expirar antes de `E`, a tentativa não produz sidecar nem
assertion: segue o retry fechado `A2→I2`, que restaura `A` e compromete novo
runId/nonce/janela no mesmo path sem alterar um byte do Gate Plan. Nonce anterior
permanece revogado pela unicidade do corpus e pela activationRevision antiga.

`execution` contém exatamente `{env,externalOutputRoot,prePostSnapshot,
timeoutSeconds,killGraceSeconds}`.
`externalOutputRoot=${RUN_TMP}` resolve fora de todas as worktrees e
`prePostSnapshot=true`; `timeoutSeconds` é integer 1–3600 e
`killGraceSeconds` integer 1–30. O runner parte de `env -i` e materializa somente
`PATH=${TOOLCHAIN_PATH}`, `LANG=C.UTF-8`, `LC_ALL=C.UTF-8`, `TZ=UTC`,
`PYTHONDONTWRITEBYTECODE=1`, `PYTHONPYCACHEPREFIX=${RUN_TMP}/pycache`,
`TMPDIR=${RUN_TMP}/tmp`, `XDG_CACHE_HOME=${RUN_TMP}/xdg` e
`HOME=${RUN_TMP}/home`, `GIT_CONFIG_NOSYSTEM=1`,
`GIT_CONFIG_GLOBAL=/dev/null`, `GIT_TERMINAL_PROMPT=0` e `NO_COLOR=1`.
Substituições e digest do env ficam no manifest.
Neste bootstrap, `timeoutSeconds=900` e `killGraceSeconds=10`; alteração exige
refreeze do card antes de `A`.
Cada row recebe snapshot fresco, sem outputs ignorados preexistentes, e roda em
process group próprio. O root do repositório é read-only quando a plataforma
permite; em qualquer plataforma o runner calcula antes/depois um Merkle de todos
os entries do filesystem, inclusive ignored, diretórios, modos e symlinks, sem
excluir `target/`, `build/`, `.dart_tool/` ou `output/`. Também recomputa
HEAD/tree/status/diff/fingerprint de todas as versionRefs. Só `${RUN_TMP}` é
gravável; qualquer mutação do Merkle/repo aborta. No timeout envia TERM ao
process group completo, aguarda `killGraceSeconds`, envia KILL e prova ausência
de filhos antes da row seguinte.

`commands[]` contém exatamente `{id,cwd,command,result,exitCode,startedAt,
finishedAt,termination,blockReason,versionRefs,provesCriteria,outputArtifactId,
outputSha256}`.

- IDs/conteúdo/cwd/roles/output kind/criteria correspondem 1:1 a todas e somente
  as entries `COMMAND` do plano após substituir refs simbólicas por versões;
- cwd resolve sob o repositório de uma versão referenciada; `versionRefs`,
  `provesCriteria` e IDs não aceitam duplicata;
- result é `PASS|FAIL|BLOCKED`; `termination` é exatamente
  `{kind,signal,timedOut}` com kind `EXITED|SIGNALED|TIMEOUT|NOT_STARTED`.
  `PASS` exige `EXITED`, exit code zero, timestamps UTC monotônicos,
  `signal=null`, `timedOut=false` e `blockReason=null`; signal/timeout/nonzero
  implica `FAIL`. `NOT_STARTED` exige `BLOCKED`, exitCode/timestamps/signal null,
  `timedOut=false` e `blockReason=PRIOR_ENTRY_FAILED|RUNNER_UNAVAILABLE`;
- `outputArtifactId` referencia manifest normativo local cujo SHA e conjunto
  `sourceVersionRefs` são exatamente `outputSha256`/`versionRefs` do comando.

`confirmations[]` contém exatamente `{id,result,authorityRole,subjectId,
challengeRef,recordedAt,versionRefs,provesCriteria,artifactId}` e corresponde
1:1 às entries
`HUMAN_CONFIRMATION`. Result é `CONFIRMED|REJECTED|UNAVAILABLE`.
`challengeRef` é exatamente `{path,sha256,versionRef}`, precisa resolver o
manifest da própria entry no `COORD-I` desta tentativa e é obrigatório mesmo em
`UNAVAILABLE`, que exige subject/artifact null e nunca prova critério. Os outros
resultados exigem subject não nulo e `artifactId` apontando artifact normativo
`HUMAN_ATTESTATION`: envelope DSSE fechado exatamente
`{payloadType,payload,signatures}`, payloadType
`application/vnd.vanrides.human-confirmation.v1+json`, payload base64 do JCS
exatamente
`{cardId,runId,entryId,result,authorityRole,subjectId,recordedAt,versionRefs,
provesCriteria,challengeSha256,challengeNonce}` e `signatures=[{keyid,sig}]`
base64 Ed25519 sobre DSSE PAE. O SHA vem de `challengeRef`, o nonce vem dos bytes
referenciados e precisa valer `issuedAt<recordedAt<expiresAt`.
O validator verifica assinatura,
nonce único no corpus, validade/revogação da chave e igualdade integral entre
sidecar, manifest, Gate Plan estático e payload. Screenshot, áudio, writer do
repo ou agente sem chave humana nunca substitui essa assertion.

#### Gate Plan v2 bootstrap deste card

Refs simbólicas: `COORD-A=coordination:ACTIVATION_BASELINE` e
`COORD-I=coordination:IMPLEMENTATION`. `${ACTIVATION_REF}` e
`${IMPLEMENTATION_REF}` são substituídos por SHAs completos antes de executar e
o manifest registra o comando resolvido.

| ID | Tipo | Comando | CWD | Version roles | Output kind | Proves criteria | Authority roles | Challenge |
|---|---|---|---|---|---|---|---|---|
| `PGM-G01` | `COMMAND` | `python3 -m py_compile scripts/evidence_v2.py scripts/evidence_v2_test.py scripts/qa_evidence_v2_gate_test.py scripts/program_control.py scripts/program_control_test.py` | `.` | `COORD-I` | `GATE_MANIFEST` | `PGM-A01,PGM-A02` | `[]` | `null` |
| `PGM-G02` | `COMMAND` | `python3 scripts/evidence_v2_test.py` | `.` | `COORD-I` | `TEST_REPORT` | `PGM-A01,PGM-A02,PGM-A03,PGM-A04` | `[]` | `null` |
| `PGM-G03` | `COMMAND` | `python3 scripts/qa_evidence_v2_gate_test.py` | `.` | `COORD-I` | `TEST_REPORT` | `PGM-A02,PGM-A03,PGM-A06` | `[]` | `null` |
| `PGM-G04` | `COMMAND` | `python3 scripts/program_control_test.py` | `.` | `COORD-I` | `TEST_REPORT` | `PGM-A01,PGM-A04,PGM-A05,PGM-A06` | `[]` | `null` |
| `PGM-G05` | `COMMAND` | `python3 scripts/program_control.py check` | `.` | `COORD-I` | `GATE_MANIFEST` | `PGM-A04,PGM-A05,PGM-A06` | `[]` | `null` |
| `PGM-G06` | `COMMAND` | `git diff --check ${ACTIVATION_REF} ${IMPLEMENTATION_REF} --` | `.` | `COORD-A,COORD-I` | `GATE_MANIFEST` | `PGM-A02,PGM-A06` | `[]` | `null` |

O runner executa as seis rows na ordem. Após a primeira `FAIL`, não executa rows
posteriores: sintetiza para cada uma manifest diagnóstico e streams vazios com
`BLOCKED`, `termination.kind=NOT_STARTED` e
`blockReason=PRIOR_ENTRY_FAILED`. Se o runner não puder iniciar, usa
`RUNNER_UNAVAILABLE` desde a primeira row. Assim o sidecar contém sempre
exatamente seis commands e seis outputs; omitir, duplicar, alterar, adicionar ou
executar row após falha bloqueia.

### Schema fechado dos manifests de gate

Todo output `GATE_MANIFEST|TEST_REPORT` é JSON UTF-8 de até 1 MiB, usa
`schemaVersion=1` e contém exatamente `{cardId,runId,gatePlanSha256,entryId,
command,cwd,versionRefs,sourceFingerprints,environmentSha256,registrySha256,
startedAt,finishedAt,exitCode,termination,blockReason,result,stdoutArtifactId,
stderrArtifactId,stdoutBytes,stderrBytes,stdoutSha256,stderrSha256,
rulesetSha256}`. Cada valor precisa ser idêntico ao Gate Plan, sidecar e execução
observada. `sourceFingerprints` é array 1–16, na ordem de `versionRefs`, de
objetos fechados `{versionRef,revision,tree,statusSha256,
workspaceContentSha256}` iguais às versões. `environmentSha256` é SHA-256 dos
bytes UTF-8 da serialização RFC 8785/JCS do object `environment`; o registry usa
seus bytes crus. Não há canonicalização implícita de manifest: artifact digest
sempre usa os bytes crus armazenados.

As condicionais de `termination`, timestamps, exit code e blockReason são as
mesmas de `commands[]`. Row `NOT_STARTED` ainda possui manifest e dois artifacts
`SANITIZED_STREAM` de zero bytes, com
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`; uma row
que iniciou sempre retém streams parciais sanitizados, inclusive em
timeout/sinal.

Stdout/stderr sanitizados são artifacts `SANITIZED_STREAM` locais separados,
limitados a 1 MiB cada; bytes/count/SHA precisam bater o manifest. Stream acima
do limite ou match sensível aborta e não pode resultar `PASS`; não existe
truncation permissivo. Bytes arbitrários digestados, Markdown ou sidecar nunca
substituem o manifest e seus dois streams.

### Artefatos e locators

Cada `artifacts[]` contém exatamente `{id,kind,storage,sha256,
sourceVersionRefs,retentionClass,retentionPolicyRef,retentionStartsAt,
retentionExpiresAt,locallyVerified,normative,sanitizationMethod}`.

- kind: `GATE_MANIFEST|TEST_REPORT|SANITIZED_STREAM|BUILD_ARTIFACT|SOURCE_MANIFEST|DEVICE_MANIFEST|HUMAN_ATTESTATION|SCREENSHOT|AUDIO|SUPPLEMENTARY`;
- retentionClass: `GIT_PERMANENT|RELEASE_LIFETIME|SECURITY_RETENTION|TEMPORARY_SUPPLEMENTARY`;
- `retentionPolicyRef={id,sha256}` resolve policy do registry;
  `retentionStartsAt` é UTC e `retentionExpiresAt` é UTC ou null;
- sanitizationMethod: `TEXT_RULESET|METADATA_RULESET|HUMAN_REVIEW`;
- `sourceVersionRefs` é 1–16 IDs únicos e precisa resolver; normative exige
  `locallyVerified=true` e nunca usa kind `SUPPLEMENTARY`.

`storage` é uma union pelo campo `kind`, sem chaves extras:

- `EVIDENCE_TREE_PATH`: `{kind,path}`; resolve path no contexto externo `E` ou
  working tree de evidência, nunca armazena SHA de `E`, e path precisa ficar sob
  `program/evidence/artifacts/<cardId>/`. A única exceção bootstrap são os IDs
  `LEGACY_INDEX` e `LEGACY_REPORT`, ligados respectivamente aos paths literais
  `program/evidence/LEGACY_EVIDENCE_V1_INDEX.json` e
  `program/evidence/LEGACY_EVIDENCE_V1_REPORT.md`; nenhum outro path de controle
  pode ser artifact;
- `SOURCE_GIT_BLOB`: `{kind,versionRef,path}`; resolve blob na versão limpa;
- `EXTERNAL_SUPPLEMENTARY`: `{kind,referenceId,storeRef}`;
  `storeRef={id,sha256}` resolve um `deletableStores[]` do registry;
  `normative=false`, `locallyVerified=false` e nunca satisfaz command/critério.

Output normativo fica sob `program/evidence/artifacts/<cardId>/`, não pode ser o
Markdown, sidecar ou Gate Plan, e precisa ter exatamente o mesmo conjunto
`sourceVersionRefs` do comando/confirmação que o referencia.

### Sanitização, retenção, escopo, waiver e truth table

`sanitization` contém exatamente `{result,rulesetRef,
scannedArtifactIds,matches}`. Result é `PASS|FAIL`; matches contém exatamente
`{secrets,credentials,pii,rawGps}`, integers >=0. `PASS` exige todos zero.
`scannedArtifactIds` é a união exata, ordenada e sem duplicata de
`IMPLEMENTATION_DIFF_OUT_OF_BAND`, `EVIDENCE_DIFF_OUT_OF_BAND`, `GATE_PLAN` e
todos os artifacts cujo storage não seja `EXTERNAL_SUPPLEMENTARY`. Os dois
primeiros IDs representam todos os bytes regulares novos/alterados nos
baselines→`I` de cada repositório e em `I..E`, respectivamente; incluem código,
sidecar, Markdown, metadata preclosure, index/report, QA Log e artifacts locais.
O terceiro representa os bytes do Gate Plan em `I`; `SOURCE_GIT_BLOB` é varrido
no blob de origem. Assim, artifact local não normativo também é varrido. A única
exclusão de bytes permitida é
`EXTERNAL_SUPPLEMENTARY`, que nunca é importado, normativo ou elegível a prova.

A matriz fechada kind↔método é: `GATE_MANIFEST`, `TEST_REPORT`,
`SANITIZED_STREAM`, `SOURCE_MANIFEST`, `DEVICE_MANIFEST` e
`HUMAN_ATTESTATION` usam
`TEXT_RULESET`; `BUILD_ARTIFACT` usa `METADATA_RULESET`, que lê o byte stream
completo e também nomes/metadados internos permitidos; `SCREENSHOT` e `AUDIO`
usam `HUMAN_REVIEW` com confirmação humana; `SUPPLEMENTARY` só pode usar
`EXTERNAL_SUPPLEMENTARY+METADATA_RULESET`. Combinação diferente falha fechada.
O parser faz o preflight antes dos commits, depois varre out-of-band o sidecar
final e todos os diffs baseline→`I`, `I..E` e `E..C`; o sidecar apenas declara
os targets reservados e não tenta armazenar hash de si mesmo.

`rulesetRef` contém exatamente `{id,sha256}` e resolve no registry versionado.

`retention` contém exatamente `{policyRef,class,startsAt,expiresAt}`;
`policyRef={id,sha256}` resolve no registry versionado. Class usa o enum acima;
como Markdown/sidecar/Gate Plan entram no Git, o top-level exige sempre
`class=GIT_PERMANENT`, policy `PROGRAM_GIT_PERMANENT_V1` e `expiresAt=null`;
`startsAt=recordedAt`;
classes expiráveis aparecem somente em `artifacts[].retentionClass` externos.
Cada artifact local herda a policy top-level; artifact externo precisa de classe
permitida pelo próprio `storeRef`. Todo
`EVIDENCE_TREE_PATH` ou `SOURCE_GIT_BLOB`, normativo ou não, exige
`retentionClass=GIT_PERMANENT`, `retentionPolicyRef` do
`PROGRAM_GIT_PERMANENT_V1`, `retentionStartsAt=recordedAt` e
`retentionExpiresAt=null`, porque Git não oferece expiração lógica. Classes
`RELEASE_LIFETIME`, `SECURITY_RETENTION` e
`TEMPORARY_SUPPLEMENTARY` são permitidas somente em `EXTERNAL_SUPPLEMENTARY`
cujo store do registry declara exatamente `{id,sha256,deletionCapable=true,
allowedRetentionClasses,deletionVerificationCommand}` e inclui a classe.
Cada retention policy é exatamente `{id,sha256,class,durationSeconds}`;
`GIT_PERMANENT` exige duration null, as outras integer positivo. Em artifact
externo, policy/class/store precisam coincidir, `retentionStartsAt=recordedAt` e
`retentionExpiresAt` precisa ser exatamente início + duration em segundos.
Expiração exige store realmente deletável e execução futura do comando de
verificação; `TEMPORARY_SUPPLEMENTARY` continua restrito a artifact não
normativo. Campo ausente ou outra combinação storage↔retention falha fechada.

`scope` contém exatamente `{provedCriteria,notProved,promotion}`.
`provedCriteria` contém, na ordem do Gate Plan, somente o subset de critérios
para os quais todas as entries atribuídas terminaram `PASS`/`CONFIRMED`, todos
os artifacts/refs associados são válidos e a sanitização global passou.
`notProved` é o complemento exato, também na ordem do plano, em objetos fechados
`{criterionId,reasonCode}`. `reasonCode` é
`SANITIZATION_FAILED|COMMAND_FAILED|CONFIRMATION_REJECTED|COMMAND_BLOCKED|CONFIRMATION_UNAVAILABLE`;
quando há mais de uma causa, vale essa precedência e depois a ordem da entry no
Gate Plan. Frase livre, critério presente nas duas listas ou ausente em ambas
falha fechado. `promotion` é `NONE|PILOT|PRODUCTION`; valor diferente de `NONE`
só é aceito em card de release com confirmação humana e permanece `HOLD`, sem
autorizar promoção.

`waiver` é `null` ou `{id,authorityRole,subjectId,decisionArtifactId,scope,
approvedAt,expiresAt,decisionSha256}`. Waiver exige owner, escopo e expiração
humanos; `decisionArtifactId` aponta `HUMAN_ATTESTATION` DSSE com payloadType
`application/vnd.vanrides.human-waiver.v1+json` e payload JCS que liga todos
esses campos exceto o próprio `decisionSha256`, que precisa igualar o SHA dos
bytes do envelope. Assinatura/subject/role/capability são
verificados como nas confirmações; nunca é criado pelo agente. Para este programa,
waiver não transforma ausência/falha em `PASS`: waiver não nulo implica `HOLD`
e impede `done` até política/card humano separado resolver a exceção.

`authorityRole` é fechado em
`PRODUCT|PRIVACY|LEGAL_DPO|SECURITY_OPS|QA|RELEASE_MANAGER|DEVICE_TESTER`.
`program/evidence/EVIDENCE_V2_REGISTRY.json` contém exatamente
`schemaVersion`, `trustRoots`, `roles`, `sanitizationRulesets`,
`retentionPolicies`, `deletableStores` e `humanAuthorities`, com IDs/digests
únicos. Cada `trustRoots[]` é exatamente `{subjectId,keyId,algorithm,publicKey,
publicKeySha256,trustAssertion}` e o envelope precisa satisfazer o pin/
cerimônia ou uma root migration válida. Cada role é objeto fechado
`{id,capabilities}`; capability é
`CONFIRMATION|WAIVER_APPROVAL|PILOT_PROMOTION|PRODUCTION_PROMOTION|TRUST_REGISTRY_ADMIN`.
A matriz
inicial é exata:

| Role | Capabilities |
|---|---|
| `PRODUCT` | `CONFIRMATION,PILOT_PROMOTION,PRODUCTION_PROMOTION,TRUST_REGISTRY_ADMIN` |
| `PRIVACY` | `CONFIRMATION,WAIVER_APPROVAL,PRODUCTION_PROMOTION` |
| `LEGAL_DPO` | `CONFIRMATION,WAIVER_APPROVAL` |
| `SECURITY_OPS` | `CONFIRMATION,WAIVER_APPROVAL,PRODUCTION_PROMOTION,TRUST_REGISTRY_ADMIN` |
| `QA` | `CONFIRMATION` |
| `RELEASE_MANAGER` | `CONFIRMATION,PILOT_PROMOTION,PRODUCTION_PROMOTION` |
| `DEVICE_TESTER` | `CONFIRMATION` |

Cada `humanAuthorities[]` é exatamente `{subjectId,authorityRole,keyId,
algorithm,publicKey,validFrom,validUntil,revokedAt}`; algorithm é `ED25519`,
publicKey é base64 canônico, validade usa UTC e revokedAt é null ou UTC. IDs e
keys são únicos. Assertion só vale quando `recordedAt` está no intervalo, não há
revogação vigente e subject/role/key coincidem. Chaves privadas nunca entram em
repo, evidence, log ou registry.
O bootstrap nasce com `humanAuthorities=[]`: este card não exige confirmação
humana e nenhum agente gera identidade/chave. Enrollment posterior exige card e
decisão humana explícitos; até existir chave confiável, confirmação obrigatória
fica `UNAVAILABLE/BLOCKED`, nunca `CONFIRMED` por fallback.

Waiver exige capability `WAIVER_APPROVAL`. `PILOT` exige confirmações
`PRODUCT+RELEASE_MANAGER`; `PRODUCTION` exige
`PRODUCT+PRIVACY+SECURITY_OPS+RELEASE_MANAGER`, todas presentes no Gate Plan e
`CONFIRMED`; mesmo assim `promotion!=NONE` continua `HOLD` até a esteira de
release separada. O ruleset inicial é
`VANRIDES_EVIDENCE_SANITIZATION_V1`; policies iniciais são
`PROGRAM_GIT_PERMANENT_V1`, `RELEASE_EVIDENCE_V1`,
`SECURITY_EVIDENCE_V1` e `TEMPORARY_SUPPLEMENTARY_V1`. Alterar role, rule ou
policy/store exige novo ID/digest e compatibilidade explícita; referência
inventada, capability ausente ou digest divergente falha fechada.
O `sha256` de cada ruleset/policy/store é calculado sobre a serialização JCS do
próprio object após remover somente o campo `sha256`; o digest do registry no
sidecar usa os bytes integrais. Assim nenhum item depende do próprio hash.

Truth table fechada, com precedência `FAIL > BLOCKED > HOLD > PASS`:

- erro estrutural, digest/ref/path/artifact/retention inválido ou contradição
  interrompe a validação antes da truth table e nunca vira resultado válido;
- `FAIL`: sidecar estruturalmente válido, mas sanitização `FAIL`, algum command
  `FAIL` ou alguma confirmação `REJECTED`;
- `BLOCKED`: sem causa de `FAIL`, mas algum command `BLOCKED` ou confirmação
  `UNAVAILABLE`; `UNAVAILABLE` nunca é reinterpretado como `HOLD`;
- `HOLD`: todas as entries passaram, todos os critérios estão provados, mas
  waiver é não nulo ou `promotion!=NONE`; trata-se de decisão de política
  separada, não de confirmação ausente;
- `PASS`: todas as entries passaram, todos os critérios estão provados,
  sanitização/retenção são válidas, waiver é null e `promotion=NONE`.

O `result` declarado precisa ser exatamente o derivado. Somente `PASS` pode
sustentar `done`; `FAIL`, `BLOCKED` e `HOLD` preservam seus subsets/complementos
de critérios para diagnóstico sem declarar prova inexistente.

Metadata parseável no Markdown (`card`, `run ID`, `result`, versões e critérios)
precisa ser idêntica ao sidecar; o sidecar liga os bytes pelo
`evidenceMarkdownSha256`. Divergência bilateral é `CONTRADICTION`.

O diff `I..E` também é fechado. Neste bootstrap PGM contém somente canonical
Markdown/sidecar, os dois legacy index/report literais, paths enumerados em
`artifacts[]` sob o prefixo exato deste card, este card,
`program/program.json` e `.dev-harness/dashboard.md`. Em card v2 ordinário, o
mesmo conjunto é usado sem legacy index/report e com ID/card/prefixo do alvo.
Scripts, schema, templates, registry, Gate Plan, policy ou qualquer outro path
permanecem byte-idênticos a `I`, mesmo que alguém tente declará-los artifact.
No JSON, somente `updatedAt=recordedAt`, status do card-alvo
`in_progress→validating`,
`commits=[IMPLEMENTATION...]` e append exato do Markdown/sidecar em `evidence`
mudam; active/next continuam no ID-alvo. No card, somente o Estado muda para
`validating`; DoR/aceite/texto permanecem. Dashboard é projeção do JSON. Path
extra, artifact declarado sem path no diff ou mudança sem artifact falha.

O trust anchor de closure, criado somente depois de validar o commit `E`, é
`closure-validation.json`. Seu schema é um `oneOf` discriminado por `kind`,
com três variantes completas e recursivamente fechadas; não existe branch
catch-all. Toda variante contém exatamente `{schemaVersion=1,kind,cardId,
implementationRevision,evidenceRevision,evidenceTree,sidecarBlob,
sidecarSha256,sanitizationRulesetRef,validatorCommand,validatedAt,result,
registryMigration,recovery}` e obedece à seguinte matriz:

- `kind=STANDARD`: `registryMigration=null` e `recovery=null`; é a única
  variante aceita quando `policyMutation=EVIDENCE_V2_BOOTSTRAP|NONE`;
- `kind=REGISTRY_MIGRATION`: `registryMigration` é o object fechado abaixo e
  `recovery=null`; exige `policyMutation=REGISTRY_MIGRATION`;
- `kind=RECOVERY`: `registryMigration=null` e `recovery` é o object fechado
  abaixo; exige `policyMutation=RECOVERY` e o card ID fixo de recovery.

O object `registryMigration` contém exatamente `{oldRegistrySha256,
newRegistrySha256,oldRootSha256,newRootSha256,changedSections,challengeNonce,
issuedAt,expiresAt,rootAssertionArtifactId,rootAssertionSha256,
newRootAssertionArtifactId,newRootAssertionSha256,adminAssertionArtifactIds,
adminAssertionSha256s}`. Todos os campos homônimos, incluindo IDs e arrays de
IDs, são byte-equivalentes aos do Gate Plan. `rootAssertionArtifactId`
referencia exatamente um artifact `HUMAN_ATTESTATION` do sidecar e
`rootAssertionSha256` é o SHA-256 de seus bytes. Os arrays admin têm a mesma
cardinalidade e ordem estável, sem IDs ou digests repetidos, e cada par
referencia/verifica uma assertion do quorum.
`newRootSha256`, `newRootAssertionArtifactId` e `newRootAssertionSha256` são
simultaneamente null sem rotação ou simultaneamente não null com rotação; neste
último caso, ID e digest referenciam a assertion válida da nova root. Nenhuma
assertion referenciada pode ficar fora do sidecar ou ser reutilizada em outro
papel.

O object `recovery` contém exatamente `{recoveryCardId,
recoveryGatePlanSha256,failureRef,snapshotSha256,suspendedCardId,
priorNextCardId,enforcementBefore,enforcementAfter}`. `recoveryCardId` é igual a
`cardId`; `recoveryGatePlanSha256`, `snapshotSha256`, `suspendedCardId` e
`priorNextCardId` precisam ser iguais aos valores autenticados pela policy e
pelo estado `policy.evidenceV2.recovery` criado em `R`; o digest do Recovery
Gate Plan também precisa bater seus bytes congelados, e IDs opcionais são null
ou card IDs válidos. `failureRef` é a ref normalizada extraída do Markdown de
recovery cujos bytes são ligados pelo sidecar; o Gate Plan precisa cobrir esse
critério. `enforcementBefore=FAIL_CLOSED_HOLD` e
`enforcementAfter=FAIL_CLOSED` são constantes. Campo ausente, extra, branch
incompatível, object especial no `kind` errado ou null no `kind` que o exige
falha em `EVIDENCE_V2_SCHEMA` antes de qualquer validação semântica.

Em todas as variantes, `result=PASS`; refs/tree/blob, SHA e ruleset são
recalculados. Antes de criar `C`, e novamente em toda auditoria de `C`,
`program_control` varre com esse ruleset todos os bytes regulares de `I..E` e
`E..C`, inclusive o próprio closure manifest, metadata/policy/card/dashboard e
index/report. Esse resultado é deliberadamente out-of-band para não criar
autorreferência em `C`. `program_control` também executa novamente o validator
contra `E`, exige `A→I→E→C` e compara o command canônico.

O diff `E..C` possui exatamente quatro paths: o novo closure manifest,
`program/program.json`, o card-alvo Markdown e `.dev-harness/dashboard.md`.
Para este bootstrap PGM, o patch semântico JSON fechado é somente:

1. `/updatedAt` recebe exatamente `closure.validatedAt`;
2. `/policy/evidenceV2` nasce exatamente `{schemaVersion:2,
   cutoffRevision:I,registrySha256:<registry de E>,enforcement:FAIL_CLOSED,
   humanTrustRoot:{keyId,publicKeySha256,trustAssertionSha256},
   recoveryTemplateSha256:<CARD.md em I>,
   recoveryGatePlanSha256:<Gate Plan recovery em I>,recovery:null}` e
   `/policy/evidenceV2BootstrapRoot` é removido após igualdade byte/digest;
3. no card identificado por este ID: `status validating→done`, append único de
   `coordination:E` em `commits` e append único do closure manifest em
   `evidence`;
4. `/activeCardId` muda deste ID para null; `/nextCardId` recebe o resultado do
   selector puro congelado/testado em `I` sobre o programa pós-patch, ordenado
   por elegibilidade, prioridade e ID.

Todos os demais valores de policy, cards, dependencies, allowlists, milestones,
workstreams e epics preservam valor e ordem byte-equivalentes. No Markdown,
somente estado `validating→done` e os seis checkboxes de aceite mudam de `[ ]`
para `[x]`; DoR/textos/dependências/escopo permanecem idênticos. Dashboard é a
projeção byte-exata gerada desse JSON; `.dev-harness/status.json` pode ser
recomputado localmente, mas não integra `C`. Fixtures alteram
`requireHumanPromotionApproval`, WIP, G7, outro card/critério/allowlist ou o next
derivado e precisam falhar. Alterar evidência normativa em `C`, omitir `E`,
inserir commit intermediário, deixar byte novo sem scan ou usar output de outro
run também falha.

Para card v2 ordinário pós-cutoff, os mesmos quatro paths são usados e o closure
usa `kind=STANDARD`, com os dois objects especiais null, mas
`/policy/evidenceV2` inteiro precisa permanecer byte-idêntico: o patch JSON
permite somente `updatedAt=closure.validatedAt`; no card-alvo,
`validating→done`, append único `coordination:E` em commits e append do closure
manifest em evidence; `activeCardId→null`; e `nextCardId` pelo mesmo selector.
No Markdown-alvo mudam somente Estado e todos/e somente os checkboxes cujos IDs
estão em `GatePlan.criteria`; cardinalidade não é fixa em seis. Todos os demais
bytes/campos/cards/gates/policies permanecem iguais. Este bootstrap também usa
`kind=STANDARD`; recovery usa somente `kind=RECOVERY` e segue exclusivamente o
protocolo `R` abaixo. Fixtures executam bootstrap e ordinary closure e rejeitam
criação, remoção ou mutação de policy no ordinary.

Para `REGISTRY_MIGRATION`, vale a variante já autenticada acima: além do patch
ordinary, somente `registrySha256` old→new e, em root rotation, o object
`humanTrustRoot` old→new podem mudar. O closure usa
`kind=REGISTRY_MIGRATION`; os campos homônimos de seu object
`registryMigration` precisam coincidir com o Gate Plan, e seus campos de digest
com os bytes dos artifacts do sidecar e as assertions DSSE verificadas. O
validator usa root/pin antigos antes de aceitar a troca atômica.
Nenhum outro campo de policy muda.

### Ordem fail-closed e códigos

O primeiro erro segue ordem fixa: (1) size/UTF-8/duplicate key; (2) schema,
chaves/tipos/patterns/enums/limits; (3) card/Gate Plan/criteria/cutoff; (4)
refs/ancestry/repositórios/paths; (5) bytes/digests; (6) cobertura Gate Plan e
integridade referencial; (7) truth table; (8) sanitização/retenção; (9)
legado/waiver/promotion.

Códigos começam por `EVIDENCE_V2_`: `SIZE`, `UTF8`, `DUPLICATE_KEY`, `SCHEMA`,
`CARD_ID`, `RUN_ID`, `TIMESTAMP`, `GATE_PLAN`, `CRITERIA`, `CUTOFF`,
`VERSION_REF`, `TREE`, `STATUS`, `WORKSPACE_CONTENT`, `PATH`, `COMMAND`,
`CONFIRMATION`, `RESULT`, `OUTPUT_DIGEST`, `ARTIFACT_DIGEST`, `SANITIZATION`,
`RETENTION`, `SCOPE`, `WAIVER`, `LEGACY` ou `CONTRADICTION`. A mensagem contém
somente código e JSON pointer, sem valor rejeitado.

### Cutoff, candidate e corpus legado

O cutoff é exclusivamente o commit de coordenação `I` deste PGM e é registrado
em `program.policy.evidenceV2.cutoffRevision` dentro de `C`. Na transição
prospectiva a `done`, candidate é o `HEAD=E` de coordenação que contém
implementação e evidência e precede a working tree de fechamento. Candidate
igual/descendente do cutoff exige v2; candidate estritamente ancestral é
legado; história divergente/unknown falha fechada.

Para auditoria histórica, `transitionRevision` é o único commit na história
`--first-parent` de coordenação cuja tree muda o card de não-done para `done`;
zero ou múltiplas transições classificam `REVALIDATION_REQUIRED`, emitem
`EVIDENCE_V2_LEGACY` e nunca escolhem uma revisão. SHAs de backend/app/maps
nunca classificam cutoff: permanecem somente em `versions[]`.

`LEGACY_EVIDENCE_V1_INDEX.json` é fechado, ordenado por cardId e contém
`{schemaVersion=1,cutoffRevision,entries}`. Cada entry contém exatamente
`{cardId,transitionRevision,evidenceBlob,evidenceSha256,commits,classification,
reason,revalidationCardId}`. Classification é
`LEGACY_LIMITED|V2_VALID|REVALIDATION_REQUIRED|HUMAN_WAIVER_REQUIRED`; commits
é array ordenado de refs normalizados `repository:<40hex>` e `reason` é enum
`V2_VALIDATED|LEGACY_MARKDOWN_ONLY|NO_DONE_TRANSITION|MULTIPLE_DONE_TRANSITIONS|EVIDENCE_MISSING|EVIDENCE_INVALID|COMMIT_UNRESOLVED|NON_REPRODUCIBLE_HUMAN_PROOF`.
A matriz fechada é:

- `V2_VALID`: reason `V2_VALIDATED`, uma transition, evidence blob/SHA válidos,
  ao menos um commit e `revalidationCardId=null`;
- `LEGACY_LIMITED`: reason `LEGACY_MARKDOWN_ONLY`, uma transition, evidence
  blob/SHA válidos, ao menos um commit e revalidation igual ao batch;
- `REVALIDATION_REQUIRED`: `NO_DONE_TRANSITION` e
  `MULTIPLE_DONE_TRANSITIONS` exigem transition/evidence blob/SHA null e
  `commits=[]`; `COMMIT_UNRESOLVED` exige uma transition, evidence blob/SHA null
  e `commits=[]`; `EVIDENCE_MISSING` exige uma transition, commits resolvidos
  não vazios e ambos evidence fields null; `EVIDENCE_INVALID` exige uma
  transition, commits resolvidos não vazios e blob/SHA dos bytes encontrados;
  todos apontam ao batch;
- `HUMAN_WAIVER_REQUIRED`: reason `NON_REPRODUCIBLE_HUMAN_PROOF`, uma
  transition, evidence blob/SHA válidos, ao menos um commit e batch; o batch
  somente abre decisão humana, nunca fabrica waiver.

Classificação segue precedência exclusiva: contar transitions; com exatamente
uma, resolver todos os commits; só então verificar presença/validade da
evidência; por fim distinguir v2, legacy e prova humana não reproduzível.
Portanto `COMMIT_UNRESOLVED` vence missing/invalid e deliberadamente não lê nem
registra evidence. Combinação/nullability diferente falha fechada. O Markdown histórico é
read-only e nunca promovido por inferência. Gap aponta para o único batch card
`VR-PGM-004-LEGACY-EVIDENCE-REVALIDATION-001`, preparado fora da worktree no
report-only e criado canonicamente por `I`, que reexecuta/importa prova por
versão e bloqueia G7; não gera waiver. Seu
objeto nasce `analyzed`, no epic `VR-PGM-004`, com repositórios
`coordination/backend/app/maps`, allowlists de produto vazias, dependência deste
PGM e blocker/DoR próprios. Em `I`, `G7.dependsOn` passa a ser exatamente
`[G6, VR-PGM-004-LEGACY-EVIDENCE-REVALIDATION-001]`; o controller testa e
ratcheteia essa aresta até o batch ficar `done`.

Após o fechamento do predecessor, o baseline possui 66 cards, 14 finais e 52
não finais. O report-only apenas deriva o batch fora da worktree e `I` o cria;
portanto `I` possui 67 cards, 14 finais e 53 não finais. Todos os 53 têm
exatamente, na allowlist de
coordenação, paths derivados `<ID>.md`, `<ID>.json`,
`gate-plans/<ID>.json` e `artifacts/<ID>/`: este PGM já possui os quatro, os
outros 51 existentes recebem somente os três ausentes e o batch nasce com os
quatro. O controller rejeita ausência, path de outro ID e prefixo amplo
`program/evidence/`. Card criado após o cutoff nasce pelo mesmo template. Em
`C`, somente o PGM vira final, preservando 67 cards, 15 finais e 52 não finais.
O Recovery Gate Plan global reservado existe em `I` em
`program/evidence/EVIDENCE_V2_RECOVERY_GATE_PLAN.json`, mas não corresponde a
card do programa nem entra nessas contagens até o comando emergencial `R`; seu
path/digest são ratcheteados pela policy e ele não pode provar card comum.
Se contagens/IDs divergirem, a implementação aborta e o contrato é refrozen
antes de código; não ajusta silenciosamente.

### Contratos afetados

`program_control`, schema/template/Gate Plan de evidência, transição a `ready` e
`done`, allowlists, CI de coordenação e prontidão de release. Não muda código,
schema ou runtime do produto.

### Riscos e mitigação

Riscos: fixed point de hash, plano incompleto, parser permissivo, path escape,
evidência antiga falsamente promovida, output arbitrário e erro que exibe dado.
Mitigações: protocolo `A→I→E→C`, worktrees limpas, schema recursivo fechado,
Gate Plan e criteria por digest, no-follow/size caps, manifests locais, scan de
todos os bytes de evidência/closure, cutoff por first-parent, fixtures sintéticas
e erros sem valores.

### Validação mínima

- fixtures positivas v2 para card coordination-only e cross-repo;
- uma fixture negativa/mutação por campo, enum, duplicate key/ID, ref órfã,
  gate/critério omitido-extra-duplicado-alterado, `PASS` contraditório, output
  sem bytes, Markdown divergente, locator absoluto/traversal/symlink/non-regular,
  tamanho/TOCTOU, sanitização/retention/waiver/promotion;
- worktree com mesmo status e bytes diferentes, sidecar presente fora do
  snapshot `I`, e prova de que fingerprint reconstrói `I` sem self-reference;
- `A→I→E→C` com ancestralidade direta, diffs exatos, metadata preclosure,
  `card.commits=[IMPLEMENTATION...,coordination:E]` somente em `C` e closure
  manifest revalidado; fixtures rejeitam commit intermediário ou ref futuro em
  `E`;
- `P→A` activation-only e retry `A2` restaurando a tree de `A`; fixtures tentam
  esconder script/registry/evidência em A/A2 e falham;
- propostas `I`, `E` e `C` com match sintético falham antes de add/write-tree;
  nenhum blob/commit aparece; fixture troca inode após scan e prova que somente
  o buffer selado chega a hash-object/index/tree. Recheck pós-commit não sensível
  usa retry `A2`, e descoberta sensível tardia entra no incidente/purge;
- author/committer/config/message maliciosos são ignorados; cat-file confirma
  metadata fixa, um parent e ausência de headers extras;
- scanner candidato vazio/permissivo não consegue remover fixtures nem o piso
  `VANRIDES_BOOTSTRAP_SENSITIVE_V1` congelado por `A`;
- resultado `FAIL`, `BLOCKED`, `HOLD` e `PASS` com subset/complemento exatos de
  critérios; `UNAVAILABLE` não vira `HOLD` e sanitização falha desprova tudo;
- Gate Plan, streams, sidecar, Markdown, artifact local não normativo,
  `SOURCE_GIT_BLOB`, index/report/QA Log e todo byte dos diffs baseline→`I`,
  `I..E` e `E..C` são sanitizados; somente supplementary externo não importado
  fica excluído;
- cada row usa cache/output externo e snapshot/Merkle incluindo ignored;
  fixture escreve em `target/`, `build/`, `.dart_tool/` e `output/` e falha;
  timeout mata process group/filhos após grace period;
- primeira row FAIL produz prefixo executado e sufixo exato
  `BLOCKED+NOT_STARTED`, seis manifests e streams vazios; manifest fechado
  rejeita bytes arbitrários, truncation, sourceFingerprint, env ou registry
  divergentes;
- registry, roles, digests, storage↔retention e kind↔sanitizationMethod são
  exercitados com uma mutação negativa por combinação; blob/tree Git com classe
  expiração, artifact sem policy/início/fim e store externo sem delete
  verificável falham; `CONFIRMED` forjado/unsigned/replayed/revoked ou challenge
  divergente falha;
- root ceremony cancela sem humano, writer não autoenrola chave, primeiro
  enrollment root-signed funciona, ordinary preserva pin, quorum/rotação e
  registry migration atualizam somente os campos permitidos;
- cutoff parent estrito, igual, child, first-parent merge, divergent e unknown;
- corpus dos 14 cards legados, baseline de 52 allowlists, 53 em `I`, 52 em `C`,
  matriz/precedência completa classification/reason/nullability, criação do
  batch e mutação negativa da aresta exata `G7→batch`;
- drift de OS/Python/JDK/Flutter/Docker/browser/device, cwd e command;
- closure bootstrap/ordinary muta somente os quatro paths/pointers/checkboxes
  exatos; ordinary preserva policy. Fixtures cobrem os três `kind`, rejeitam
  discriminante ausente/desconhecido, chave extra, object especial null ou no
  branch errado, pares de assertion inconsistentes e divergência entre
  migration/recovery e Gate Plan/sidecar/policy. Também tentam relaxar aprovação
  humana/WIP/G7, alterar outro card e escolher next arbitrário;
- report-only prova zero write em worktree, index e object database;
- rollback `R` sobre `C` preserva evidências, suspende o card anterior, mantém
  WIP=1 e libera somente o recovery ID; fixtures provam forward-fix e rejeitam
  deadlock, segundo active, path de produto, delete ou revert;
- pycompile, testes de módulos/runner/programa, `program check`, diff check e
  mutações adversariais; nenhuma fixture contém PII, segredo ou GPS real.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `GOV-EVIDENCE-RATCHET-001` concluído e preservado como baseline;
- `VR-PGM-004-EVIDENCE-V2-BOOTSTRAP-001` concluído, com tool, policy,
  sanitizer e evidência v1.1 imutáveis no parent `P`;
- schema, Gate Plan, compatibilidade, allowlist migration e protocolo
  `A→I→E→C`
  acima revisados por QA, SEC e PRIV antes de marcar este DoR.

## Escopo

- parser/validador v2, runner, integração no programa, schema/template, Gate
  Plan, fixtures, migração exata de allowlists, index/report legado, sidecar e
  manifests deste card;
- paths: `scripts/evidence_v2.py`, `scripts/evidence_v2_test.py`,
  `scripts/qa_evidence_v2_gate.sh`, `scripts/qa_evidence_v2_gate_test.py`,
  `scripts/program_control.py`, `scripts/program_control_test.py`,
  `program/evidence/QA_EVIDENCE_V2_SCHEMA.json`,
  `program/evidence/EVIDENCE_V2_REGISTRY.json`,
  `program/evidence/EVIDENCE_V2_RECOVERY_GATE_PLAN.json`,
  `program/evidence/LEGACY_EVIDENCE_V1_INDEX.json`,
  `program/evidence/LEGACY_EVIDENCE_V1_REPORT.md`,
  `program/cards/VR-PGM-004-LEGACY-EVIDENCE-REVALIDATION-001.md`,
  `program/evidence/gate-plans/VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001.json`,
  `program/evidence/VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001.json`,
  `program/evidence/artifacts/VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001/`,
  `program/templates/CARD.md`, `program/templates/QA_EVIDENCE.md` e documentos
  de governança já allowlisted.

## Fora de escopo

- interpretar screenshot/áudio humanamente, acessar serviço remoto, fabricar
  checksum/output, reexecutar gate histórico por inferência, autoaprovar waiver
  ou promover piloto/produção;
- alterar backend/app/maps, evidência legada original, dado, segredo ou
  configuração viva; liberar `program/evidence/` de forma ampla.

## Definition of Ready

- [ ] schema recursivo v2, protocolo `A→I→E→C`, Gate Plan/criteria, códigos,
  cutoff e index legado estão congelados acima;
- [ ] migração derivada baseline 52 → `I` 53 → `C` 52, paths, testes, rollout,
  observabilidade, aborto e rollback estão definidos sem path de produto;
- [ ] fixtures são sintéticas; sanitização/retention/waiver não exigem PII,
  segredo ou GPS real; cerimônia root/challenge está definida sem permitir que
  agente fabrique identidade.

## Critérios de aceite

- [ ] `PGM-A01` — schema e parser rejeitam ausência, malformação, extensão,
  contradição ou bypass de todo campo/union com código/pointer seguro;
- [ ] `PGM-A02` — protocolo `A→I→E→C` liga revision/tree/status/fingerprint,
  Gate Plan, comando, output e checksum aos bytes/versão corretos sem
  self-reference;
- [ ] `PGM-A03` — retenção, sanitização, escopo, confirmação e waiver seguem a
  truth table; DSSE/root/pin/challenge são autenticados e não aceitam frase,
  negação, expiração ou artifact não varrido;
- [ ] `PGM-A04` — cutoff/candidate/index classificam 14 legados sem falsa
  promoção, e gaps entram no batch de revalidação explícito que bloqueia G7;
- [ ] `PGM-A05` — os 53 cards não finais em `I` e os 52 restantes em `C` têm
  somente sidecar, Gate Plan e artifact prefix derivados do próprio ID;
- [ ] `PGM-A06` — Gate Plan cobre exatamente estes critérios; runner, corpus,
  programa e mutações passam sem relaxar WIP, gates, HOLDs ou dado sensível.

## Rollout

1. ainda em `ready`, executar a cerimônia root; somente após confirmação humana,
   criar `A` pelo patch activation-only e staging selado;
2. em modo report-only, derivar somente em `${RUN_TMP}` as propostas de Gate
   Plan, migração de allowlists, batch, aresta G7 e index/report, sem escrever
   worktree/index/object database; abortar se card count, blob, candidate,
   classificação ou path divergir;
3. preparar `I`, fazer preflight de todos os bytes baseline→proposta antes de
   construir o index selado, comparar manifest de bytes e só então commitar
   código/template/batch/allowlists;
4. reconstruir `I` e executar Gate Plan em snapshots herméticos por row, com
   timeout/process-group, Merkle incluindo ignored, caches e outputs externos;
5. construir `E` fora da worktree, fazer preflight completo, usar somente os
   buffers selados, commitar e revalidar exact-`E` out-of-band;
6. preparar `C`, validar patch semântico e scan prospectivos, e só então
   commitar closure manifest/cutoff/policy/refs/checklists/projeções; revalidar
   `I..E` e `E..C` e ativar v2 fail-closed desde candidate igual ao cutoff.
   Legado permanece
   `promotionEligible=false` até sidecar verificável ou revalidação;
7. habilitar release ratchet somente com divergência zero. Métricas: contagens
   por schema/classificação/código, sem conteúdo. Abortar por falso positivo,
   parser crash, digest instável, output ausente, drift local/CI ou tentativa de
   path amplo.

## Rollback

Antes de `C`, permanecer em `validating`; se `I` ou `E` precisar de correção,
preservar somente tentativa falha não sensível e iniciar `A2→I2→E2→C`, sem
reescrever história.
Falha sensível segue o incidente/purge autorizado acima e nunca é preservada.

Depois de `C`, nunca reverter/apagar evidência. O único comando emergencial é
`program_control.py evidence-recovery-start`, congelado/testado em `I`; ele cria
atomicamente commit `R` descendente e o card fixo
`VR-PGM-004-EVIDENCE-V2-RECOVERY-001`, a partir do blob `CARD.md` em `I` cujo
digest está na policy v2 e do Recovery Gate Plan já versionado em `I`. Se houver
card ativo,
`R` registra em
`policy.evidenceV2.recovery` exatamente `{recoveryCardId,suspendedCard,
priorNextCardId,snapshotSha256}`; `suspendedCard` é null ou o object fechado
`{id,status,blockers}` e o digest usa JCS dos três primeiros campos. O card
suspenso muda somente para `blocked` com o único blocker
`EVIDENCE_V2_RECOVERY_HOLD:<recoveryCardId>`; todos os demais campos permanecem.

O objeto recovery nasce exatamente com epic `VR-PGM-004`, título `Recuperar
ratchet de evidência v2`, status `in_progress`, prioridade 0, repositories
`[coordination]`, dependsOn `[VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001]`, cardFile
literal do ID, blockers/commits/evidence vazios e a allowlist literal abaixo. O
Markdown gerado possui Impact Analysis/rollback e DoR apenas para failure ref,
digest do Recovery Gate Plan, suspensão WIP e escopo; o comando verifica e marca
esses quatro itens antes de ativar. ID já existente, dado não derivável ou DoR
incompleto aborta. Esse é o único bypass de ativação e torna recovery o único
`activeCardId`; WIP permanece 1. O plano global contém o token literal
`${RECOVERY_ID}` apenas no campo `cardId`; `R` substitui-o pelo ID fixo,
serializa por JCS e materializa o resultado no path canônico do recovery.
Qualquer segundo token ou outra diferença falha.

Enquanto `enforcement=FAIL_CLOSED_HOLD`, toda promoção e transição
`ready|in_progress|validating|done` é bloqueada, exceto as transições
`in_progress→validating→done` desse ID sob o Recovery Gate Plan congelado em
`I`. Sua allowlist exata contém somente `.dev-harness/dashboard.md`,
`.dev-harness/status.json`, `program/program.json`,
`program/cards/VR-PGM-004-EVIDENCE-V2-RECOVERY-001.md`,
`program/evidence/VR-PGM-004-EVIDENCE-V2-RECOVERY-001.md`,
`program/evidence/VR-PGM-004-EVIDENCE-V2-RECOVERY-001.json`,
`program/evidence/gate-plans/VR-PGM-004-EVIDENCE-V2-RECOVERY-001.json`,
`program/evidence/artifacts/VR-PGM-004-EVIDENCE-V2-RECOVERY-001/`,
`scripts/evidence_v2.py`,
`scripts/evidence_v2_test.py`, `scripts/qa_evidence_v2_gate.sh`,
`scripts/qa_evidence_v2_gate_test.py`, `scripts/program_control.py`,
`scripts/program_control_test.py`, `program/evidence/QA_EVIDENCE_V2_SCHEMA.json`
e `program/evidence/EVIDENCE_V2_REGISTRY.json`. Nenhum path de produto. O
fechamento forward-fix revalida corpus e todos os bytes e só aceita closure
`kind=RECOVERY`, com `registryMigration=null` e object `recovery` exato e
consistente com a policy, o Recovery Gate Plan e o snapshot de `R`. Então marca
o recovery `done`, restaura atomicamente o card suspenso a seu status/blockers
exatos e `priorNextCardId` (ou deixa active null se não havia), limpa `recovery`
e volta enforcement a `FAIL_CLOSED`. Todo outro card continua sem exceção. Nunca
desabilitar o
ratchet, converter legado em v2/waiver, editar evidência histórica ou declarar
promoção durante recovery.
