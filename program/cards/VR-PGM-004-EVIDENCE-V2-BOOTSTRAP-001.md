# VR-PGM-004-EVIDENCE-V2-BOOTSTRAP-001 — Instalar bootstrap verificável da evidência v2

## Resultado esperado

O repositório coordenador passa a conter, antes da ativação do ratchet v2, um
bootstrap mínimo, versionado e testado capaz de preparar/verificar a cerimônia
da raiz humana, aplicar o scanner sensível mínimo sobre buffers selados,
validar o patch estrito `P→A` e construir o commit de ativação sem reler paths
mutáveis. Assim, `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` não fornece a
ferramenta que autoriza a própria ativação.

## Estado

`analyzed` — contrato técnico congelado por
`VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001`; implementação e provas ainda não
iniciadas. Este card deverá ser o próximo card vertical depois do fechamento de
PRIV-002.

## Dono operacional

`PGM/QA`, com revisão obrigatória de `SEC` e `PRIV`.

## Risco ou objetivo associado

`F-PRIV-010`, `R-010`, `R-012`, `VR-PGM-004` e G0.

## Impact Analysis

### Limite de confiança

Este é deliberadamente o último card entregue pelo protocolo estrutural v1.1.
Sua ativação, evidência e conclusão não podem ser apresentadas como prova v2.
Seu único resultado é instalar em um commit predecessor limpo as primitivas
mínimas que tornarão executável e auditável a primeira ativação v2.

O bootstrap não gera, importa, lê ou persiste chave privada. A raiz humana é
criada e mantida fora do workspace. Testes podem gerar chaves efêmeras apenas
em diretório temporário, nunca em Git, evidência ou log. A primeira assinatura
real só será solicitada na ativação separada do ratchet semântico; ausência,
cancelamento ou assinatura inválida preserva aquele card em `ready`.

### Superfície implementada

- `scripts/evidence_v2_bootstrap.py`: decoder fechado, JCS para os tipos
  permitidos, DSSE PAE, verificação ED25519 por binário OpenSSL pinado por
  versão/capability, scanner mínimo, buffers selados, alternate index,
  `hash-object --stdin`, `write-tree`, `commit-tree` e compare-and-swap da ref;
- `scripts/evidence_v2_bootstrap_test.py`: fixtures positivas e negativas,
  inclusive assinatura, replay, expiração, path/symlink, TOCTOU simulado,
  scanner, patch, metadata e troca concorrente da ref;
- `scripts/program_control.py`: somente o comando
  `evidence-root-ceremony prepare|finalize` e a validação da disponibilidade
  do bootstrap; regras v2 completas permanecem no sucessor;
- `scripts/program_control_test.py`: integração do comando e regressão do
  programa existente;
- `program/evidence/EVIDENCE_V2_BOOTSTRAP_POLICY.json`: política fechada,
  limites, versão OpenSSL/capabilities aceitas, regras mínimas de sanitização,
  schemas da request/assertion/root pin e patch permitido;
- documentação operacional, card e evidência canônica v1.1 deste card.

Nenhum path de backend, app ou Maps é alterado.

### Cerimônia em duas fases

`prepare` recebe o card alvo `ready`, o SHA `P` esperado, a public key ED25519
PEM e um diretório de saída fora do workspace. Ele valida que a chave é pública
e ED25519, normaliza seu descriptor, gera 32 bytes aleatórios pelo CSPRNG do
sistema e grava, com modo privado, uma request fechada contendo exatamente
`schemaVersion`, `cardId`, `parentRevision`, `subjectId`, `keyId`,
`publicKey`, `publicKeySha256`, `challengeNonce`, `issuedAt`, `expiresAt`,
`payloadType`, `payloadJcsSha256` e `bootstrapPolicySha256`. O payload DSSE é
JCS e liga todos esses campos; o diretório temporário não é autoridade e pode
ser descartado.

O titular assina a PAE fora do workspace com sua chave privada. `finalize`
recebe request, public key e assinatura pública, mas nenhum argumento ou
descritor de chave privada. Ele exige a mesma `P`, HEAD/ref sem corrida,
validade temporal, nonce ausente do corpus rastreado e não rastreado em escopo,
policy/tool bytes iguais aos digests esperados, assinatura válida e patch
`P→A` exato. Erro não imprime payload, chave, assinatura, path externo ou
match sensível.

O pin inserido em `A` é um objeto fechado
`/policy/evidenceV2BootstrapRoot` com `schemaVersion`, `subjectId`, `keyId`,
`algorithm=ED25519`, `publicKey`, `publicKeySha256`, `challengeNonce`,
`parentRevision`, `approvedAt`, `expiresAt`, `assertion`, `assertionSha256`,
`bootstrapToolPath`, `bootstrapToolSha256`, `bootstrapPolicyPath`,
`bootstrapPolicySha256` e `bootstrapSanitizerSha256`. A assertion DSSE completa
é locatável nesse objeto; digest isolado não satisfaz o contrato.

### Scanner mínimo e buffers selados

O scanner `VANRIDES_BOOTSTRAP_SENSITIVE_V1` é definido por dados na policy e
ratcheteado por digest. Ele cobre, no mínimo, chaves privadas, tokens e
credenciais de formatos conhecidos, assignment de segredo, e-mail/telefone de
fixture não allowlisted, coordenadas/GPS e paths/URLs potencialmente sensíveis.
Fixtures positivas e negativas impedem remoção ou broad allowlist silenciosa.
O sucessor poderá adicionar regras, nunca remover esse piso.

Cada arquivo proposto é aberto com no-follow, validado por stat/open/fstat,
lido uma vez em buffer limitado e escaneado junto do path relativo e metadata
fixa. Somente os buffers aprovados chegam a `git hash-object -w --stdin` e ao
alternate index; deleções e modos são allowlisted. A tree e o commit são
reconstruídos e comparados antes do compare-and-swap. O runner não usa
`git add`, `git commit -a`, config/identidade local nem `hash-object` por path.

### Patch de ativação permitido

O bootstrap só pode construir `A` para
`VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001`. `P..A` contém exatamente:

1. `program/program.json`: `updatedAt`, status `ready→in_progress`,
   `activeCardId:null→cardId`, `nextCardId=cardId` e o root pin acima;
2. o card alvo: somente a seção Estado, registrando ativação e SHA `P`;
3. `.dev-harness/dashboard.md`: projeção determinística do mesmo JSON.

Dependências, blockers vazios, allowlists, commits/evidence, policy restante,
outros cards e repositórios precisam ficar byte-idênticos. Metadata é fixa e
sanitizada: autor/committer `VanRides Program Pipeline <pipeline@localhost>`,
datas iguais a `updatedAt`, mensagem
`chore(program): activate VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001`, um único
parent `P`, sem assinatura ou headers adicionais. O root pin não é policy v2
completa e não autoriza nenhum outro card.

### Contratos afetados

Somente coordenação local, formato do root pin de bootstrap e comando de
ativação especial do sucessor. Não muda API, auth, payload, schema de banco,
mobile, Maps, CI remoto, deploy, piloto ou produção.

### Riscos e mitigação

- bootstrap autorizar a si mesmo: o card termina antes da cerimônia e o
  sucessor exige esses bytes no parent `P`;
- chave privada vazar: interface não aceita private key e scanner/testes
  rejeitam PEM privada e argumentos equivalentes;
- OpenSSL variar: policy registra versão/capabilities mínimas, o comando testa
  ED25519 antes de preparar e falha fechado fora da matriz suportada;
- TOCTOU/Git config contaminar commit: buffers únicos, alternate index,
  ambiente hermético, metadata fixa, reconstrução e CAS;
- scanner mínimo ser enfraquecido: digest pinado, canários e união obrigatória
  no ratchet sucessor;
- ferramenta ficar sem proveniência: path e digest dos bytes versionados são
  gravados no root pin e revalidados em `I`, `E`, `C` e auditoria posterior;
- card legado parecer prova v2: evidência, card e programa declaram
  explicitamente `LEGACY_V1_1_BOOTSTRAP_ONLY`.

### Validação mínima

- unitários positivos/negativos do módulo e integração do CLI;
- duas execuções herméticas em cópia temporária, com tree/commit determinísticos
  para os mesmos inputs e recusa de parent/ref divergente;
- chave/assinatura ED25519 efêmera válida e casos de assinatura, keyId,
  payload, nonce, relógio e replay inválidos;
- canários de segredo/PII/GPS, arquivo grande, binário, symlink, troca de inode,
  path traversal, modo e deleção fora da allowlist;
- teste que o commit `A` simulado possui somente o patch e metadata permitidos;
- `python3 scripts/program_control_test.py`, `program check`, scanner do próprio
  diff e `git diff --check`;
- revisão adversarial independente do código e do protocolo.

## Dependências

- `GOV-EVIDENCE-RATCHET-001` concluído;
- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- parent, allowed dirty paths, contrato, testes e rollback congelados;
- OpenSSL com suporte ED25519 disponível e identificado, sem instalar ou
  alterar dependência global;
- nenhuma cerimônia ou chave real necessária para implementar/testar este card.

## Escopo

- primitives mínimas e versionadas para a primeira ativação v2;
- policy/bootstrap schemas fechados, CLI, testes e documentação;
- prova v1.1 honesta de que o predecessor contém os bytes executáveis.

## Fora de escopo

- criar ou custodiar chave privada, responder por autoridade humana ou executar
  a cerimônia real do sucessor;
- implementar schema/registry/runner/evidence v2 completos;
- migrar cards/evidências legados ou fechar o ratchet sucessor;
- alterar produto, CI remoto, Git remoto, deploy, dados ou credenciais reais.

## Definition of Ready

- [ ] contrato de request, assertion, root pin e patch `P→A` revisado;
- [ ] módulos, policy, limites, CLI e paths exatos congelados;
- [ ] matriz de testes positivos/negativos e OpenSSL suportado confirmados;
- [ ] declaração de trust boundary legado e handoff ao sucessor registrada;
- [ ] rollback e evidência v1.1 definidos sem depender de assinatura real.

## Critérios de aceite

- [ ] código versionado no parent do sucessor prepara e verifica a cerimônia
  sem aceitar ou ler chave privada;
- [ ] assertion ED25519 liga payload DSSE, nonce, parent, card, chave, policy e
  janela temporal e falha para qualquer divergência/replay;
- [ ] root pin contém assertion completa e paths/digests reprodutíveis de tool,
  policy e sanitizer;
- [ ] scanner mínimo e canários positivos/negativos são ratcheteados;
- [ ] buffers escaneados são os únicos bytes enviados ao Git, com alternate
  index, metadata fixa, reconstrução e compare-and-swap;
- [ ] patch/metadata de `P→A` aceitam somente a ativação especial congelada;
- [ ] testes exercitam assinatura, scanner, filesystem, Git, concorrência e
  falhas sem vazar conteúdo sensível;
- [ ] `program_control`, programa e documentação continuam regressivamente
  verdes;
- [ ] evidência canônica declara `LEGACY_V1_1_BOOTSTRAP_ONLY`, commit/tree,
  comandos, resultados, sanitização, escopo e rollback;
- [ ] revisão independente não encontra bypass P1/P2 executável;
- [ ] nenhuma cerimônia real, chave privada, path de produto, push, deploy ou
  autorização de piloto/produção integra a entrega.

## Rollout

Implementar e validar em incrementos locais; fechar este card pelo protocolo
v1.1 em commit próprio. Somente após o fechamento e um novo checkpoint limpo o
ratchet pode ir a `ready`. Na ativação dele, o usuário executa a assinatura
externa e o comando versionado constrói `A`; sem isso, a esteira permanece
fail-closed em `ready`.

## Rollback

Antes de qualquer root real, reverter os commits deste card restaura o programa
v1.1. Depois que um `A` usar os digests, não se reverte ou substitui o bootstrap
em silêncio: abandona-se a tentativa, revoga-se/rota-se a raiz somente por
decisão humana e reabre-se card dedicado. Rollback nunca remove objetos, refs,
assinaturas ou credenciais reais sem autorização explícita e plano de
incidente.
