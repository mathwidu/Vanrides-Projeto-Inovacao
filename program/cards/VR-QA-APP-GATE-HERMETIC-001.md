# VR-QA-APP-GATE-HERMETIC-001 — Separar gate hermético do app de readiness externo

## Resultado esperado

O gate técnico do app passa ou falha exclusivamente com arquivos versionados no
próprio checkout e resultados gerados na execução. Evidências de dispositivo,
áudio humano, campo e contrato cross-repo continuam explícitas e podem manter a
readiness em `HOLD`, mas nunca fabricam um `PASS` técnico nem quebram o perfil
sem E2E por simples ausência ambiental.

## Estado

`done`

## Risco ou objetivo associado

`R-003`, `R-011`, épico `VR-QA-001` e integridade dos gates `G1` a `G7`.

## Contexto

O fechamento de `VR-SEC-001-BE-READ` reproduziu duas falhas em um worktree
limpo do app no SHA `e716732efe5853731dab6ed401bad981cea09a70`:
`navigationTtsDeviceSmoke` e `routePlanPayloadE2e`. O primeiro lê um diretório
`output/` ignorado pelo Git e com timestamp histórico hardcoded; o segundo
procura um script no diretório pai, fora do repositório do app.

Uma segunda execução só passou após copiar manualmente esses artefatos para o
worktree. Esse resultado comprova os testes funcionais restantes, mas não prova
que o gate é reproduzível a partir do commit. A análise negativa também mostrou
que procurar a string `ROUTE_PLAN_PAYLOAD_CONTRACT_OK` não executa o contrato e
que um JSON local arbitrário com `operatorHeardPtBr=true` pode promover o
scorecard sem SHA, digest ou confirmação humana canônica.

## Impact Analysis

### Mudanças técnicas

- separar o gate hermético de contratos do agregador de readiness;
- fazer `--check-only` ler apenas fixtures e contratos versionados no app;
- representar evidência externa ausente como `NOT_PROVIDED`, `EXTERNAL` ou
  `WAITING_HUMAN`, sem convertê-la em falha do gate técnico;
- remover timestamps fixos, leitura implícita de `output/` e busca por scripts
  no diretório pai;
- impedir que o perfil sem E2E carregue `scripts/.dev.env.local`; configuração
  local de backend/device só pode ser lida depois da escolha explícita de E2E;
- aceitar evidência TTS somente por caminho explícito e com proveniência
  validada, sem alterar a confirmação humana canônica;
- gerar um manifest técnico separado no fim do smoke de device; ele vincula
  SHA/tree, run id, limpeza, marker e digest do log, mas não contém nem decide
  qualquer campo de confirmação humana;
- impedir rebind de log histórico a um HEAD novo e impedir reexecução destrutiva
  no mesmo diretório de evidência;
- vincular o pacote de revisão humana ao manifest técnico create-only e aceitar
  confirmação somente pelo valor exato `SIM`, nunca por tokens ambíguos ou
  frases negativas;
- manter o contrato route-plan no workspace coordenador e executá-lo ali com
  os SHAs exatos do produtor e consumidor;
- separar a proveniência do gerador da proveniência do verificador executado,
  ignorar `refs/replace` e exigir anchor/commits esperados externos no modo de
  decisão;
- publicar o manifesto cross-repo de forma atômica, create-only e somente
  leitura, sem chamar permissão `0444` de imutabilidade;
- adicionar regressões negativas para checkout isolado e artefatos forjados.

### Contratos afetados

- interface de CLI e exit code dos scripts de QA do app;
- schema/semântica do scorecard de readiness;
- composição do `ci_quality_gate.sh` e `qa_gate_card.sh --no-e2e`;
- nenhuma API REST, payload de produto, autenticação, CORS, WebSocket, schema de
  banco ou UX de runtime muda neste card.

### Riscos

- transformar ausência de evidência de campo em falso verde;
- continuar aceitando evidência antiga ou copiada como se pertencesse ao SHA;
- fazer o gate técnico depender do layout do workspace local;
- permitir que fixture sintética confirme uma decisão humana;
- quebrar os fluxos explícitos que hoje agregam evidência real de dispositivo.

### Mitigação

- estados separados para gate técnico e readiness de produto;
- defaults fail-closed para promoção: `fieldReady=false` e
  `externalPilotReady=false`;
- fixtures negativas em diretório temporário isolado;
- SHA/digest e origem obrigatórios para qualquer evidência externa aceita;
- execução cross-repo somente pelo coordenador com manifest de versões;
- auditoria independente em checkout limpo antes do encerramento.

### Validação mínima

- testes Python do readiness e do wiring dos gates;
- testes negativos em checkout isolado com diretório pai vazio;
- `./scripts/qa_gate_card.sh --no-e2e` no worktree limpo do SHA final;
- `./scripts/ci_quality_gate.sh` no mesmo worktree, sem cópias ou symlinks;
- contrato route-plan executado separadamente no workspace coordenador;
- `python3 scripts/program_control_test.py` e
  `python3 scripts/program_control.py check`.

### Rollback

Reverter os commits do app e da coordenação por repositório. A readiness volta a
`HOLD`; nenhuma evidência copiada ou histórica pode ser usada para liberar o
card bloqueado, piloto ou produção.

## Escopo

- scripts de readiness e seus testes no app;
- wiring dos gates do app;
- modelo explícito de estados técnicos, externos e humanos;
- contrato route-plan no coordenador com manifest de SHAs;
- backlog, QA Log, evidência e programa.

## Fora de escopo

- confirmar humanamente o áudio de `APP-NAV-AUDIO-CONFIRM-001`;
- executar teste de campo ou liberar piloto externo;
- alterar navegação, TTS ou UI de runtime;
- fechar a integração funcional de `VR-SEC-001-BE-READ`, que será retomada
  depois deste pré-requisito;
- deploy, push ou promoção.

## Dependências

- `GOV-PIPELINE-001` concluído;
- nenhuma decisão humana ou credencial é necessária para o gate técnico;
- `APP-NAV-AUDIO-CONFIRM-001` permanece independente em `waiting_human`.

## Definition of Ready

- [x] falha reproduzida em checkout isolado;
- [x] causas raiz e falso verde reproduzidos;
- [x] contratos técnicos e de readiness separados;
- [x] caminhos previstos, riscos, rollback e validação definidos;
- [x] dirty paths preexistentes do app inventariados e fora do escopo;
- [x] card bloqueado e ordem de retomada identificados.

## Critérios de aceite

- [x] `qa_gate_card.sh --no-e2e` e `ci_quality_gate.sh` passam em worktree limpo
  do SHA final, com diretório pai vazio, sem copiar arquivos, criar symlinks ou
  reutilizar `output/`.
- [x] O perfil sem E2E não lê arquivos ignorados, timestamps fixos nem caminhos
  fora do app.
- [x] `--check-only` valida apenas contratos versionados.
- [x] Ausência de device, áudio humano e field evidence mantém readiness em
  `HOLD`, mas não quebra o gate técnico.
- [x] `APP-NAV-AUDIO-CONFIRM-001` permanece `waiting_human`.
- [x] Nenhum teste automatizado ou fixture sintética altera confirmação humana
  canônica.
- [x] `fieldReady=false` e `externalPilotReady=false` permanecem invariantes.
- [x] Evidência TTS só é lida por caminho explicitamente informado; nenhum
  timestamp histórico fica hardcoded.
- [x] Evidência ausente, malformada, copiada ou sem SHA/digest não é aceita como
  prova da versão.
- [x] O marker produzido dentro do `flutter drive` carrega SHA, tree e run id da
  execução; um log de outra versão não pode ser associado ao HEAD atual.
- [x] Reexecutar no mesmo output falha antes de escrever e preserva integralmente
  log, checklist e manifesto existentes.
- [x] O produtor imprime o SHA-256 do manifesto para anchor externo; a
  documentacao nao apresenta digest auto-recalculavel ou chmod local como selo
  adversarial/assinatura.
- [x] O produtor real do smoke gera o manifest técnico que o readiness valida;
  fixture autoconsistente nunca é descrita como attestation física de device.
- [x] A revisão humana valida o manifest técnico e só `SIM` exato confirma; `NAO
  OUVIU`, `não ouviu`, `NAO FOI AUDIVEL` e texto livre continuam pendentes.
- [x] O scorecard executa contratos locais ou os classifica como inventário; a
  mera presença de tokens em scripts que retornam erro nunca produz `PASS`.
- [x] O contrato route-plan é removido dos pressupostos ambientais do app.
- [x] `python3 scripts/qa_route_plan_payload_contract_test.py` e
  `python3 scripts/qa_route_plan_payload_contract.py` passam separadamente no
  workspace coordenador, com backend/app SHAs registrados.
- [x] O manifesto cross-repo registra commits/trees/blobs exatos e seu SHA-256
  bruto e ancorado na evidencia versionada do programa.
- [x] A verificação decisória exige SHA-256 bruto e commits esperados do backend,
  app, gerador e verificador vindos da evidência externa; substituição coerente
  do manifesto, `refs/replace` e verificador dirty falham fechados.
- [x] A publicação do manifesto é atômica, create-only e `0444`; falha não deixa
  artefato parcial, e a documentação descreve o modo como somente leitura, não
  como selo imutável ou assinatura.
- [x] Testes negativos cobrem checkout isolado, artefato legado, sibling script
  falso, JSON `operatorHeardPtBr=true` sem proveniência e evidência malformada.
- [x] Backlog, QA Log, card, evidência e rollback ficam sincronizados.
- [x] Commits locais separados por repositório, sem push.

## Arquivos previstos

- `src/aplicativo-motorista/scripts/qa_navigation_pre_pilot_readiness.py`
- `src/aplicativo-motorista/scripts/qa_navigation_pre_pilot_readiness_test.py`
- `src/aplicativo-motorista/scripts/qa_navigation_tts_device_smoke.sh`
- `src/aplicativo-motorista/scripts/qa_navigation_tts_device_smoke_test.py`
- `src/aplicativo-motorista/scripts/qa_navigation_tts_evidence.py`
- `src/aplicativo-motorista/scripts/qa_navigation_tts_evidence_test.py`
- `src/aplicativo-motorista/scripts/qa_navigation_audio_confirmation_review.py`
- `src/aplicativo-motorista/scripts/qa_navigation_audio_confirmation_review_test.py`
- `src/aplicativo-motorista/scripts/qa_navigation_audio_operator_handoff_test.py`
- `src/aplicativo-motorista/scripts/ci_quality_gate.sh`
- `src/aplicativo-motorista/scripts/ci_quality_gate_test.py`
- `src/aplicativo-motorista/scripts/qa_gate_card.sh`
- `src/aplicativo-motorista/integration_test/navigation_tts_device_smoke_test.dart`
- `src/aplicativo-motorista/backlog/QA-Navigation-TTS-Device-Smoke.md`
- `src/aplicativo-motorista/backlog/QA-Navigation-TTS-Audio-Confirmation.md`
- `src/aplicativo-motorista/backlog/QA-Navigation-Audio-Operator-Handoff.md`
- `src/aplicativo-motorista/backlog/QA-Playbook.md`
- `src/aplicativo-motorista/backlog/Flutter-Cards.md`
- `src/aplicativo-motorista/backlog/QA-Execution-Log.md`
- `scripts/qa_route_plan_payload_contract.py`
- `scripts/qa_route_plan_payload_contract_test.py`
- `program/program.json`
- `program/RISK_REGISTER.md`
- `program/cards/VR-QA-APP-GATE-HERMETIC-001.md`
- `program/evidence/VR-QA-APP-GATE-HERMETIC-001.md`
- `program/cards/VR-SEC-001-BE-READ.md`
- `program/evidence/VR-SEC-001-BE-READ.md`

## Plano sequencial

1. congelar a reprodução negativa e escrever testes contra os falsos verdes;
2. separar o cálculo do gate técnico do agregador de readiness;
3. remover dependências de `output/`, timestamp e diretório pai;
4. endurecer a proveniência de evidência externa sem simular confirmação humana;
5. manter e testar o contrato route-plan no coordenador com SHAs exatos;
6. executar os gates em checkout realmente isolado;
7. revisar independentemente, registrar evidência e concluir o card;
8. retomar `VR-SEC-001-BE-READ` e repetir o gate/manifest da versão final.

## Evidências

| Evidência | Comando/local | Resultado | SHA/digest |
|---|---|---|---|
| reprodução em checkout puro | worktree isolado do app | FAIL esperado em 2 checks | `e716732efe5853731dab6ed401bad981cea09a70` |
| execução com inputs copiados | worktree condicionado | PASS condicionado, não aceito como fechamento | log `ee020ab0e4cc9346b5c1280ae1a2697490ed62f8f9fc1eb98521e20609231d09` |
| gate hermético | worktree isolado, sem output inicial | PASS: analyzer, Flutter 689/689, ratchet | app `b84c7d5`; log `777650ebe9085c7434a8fe66af7f8ef0a1aa37fcca166ee2882c8b789af2abad` |
| gate CI | mesmo worktree e SHA | PASS: wiring 15/15, Node 5/5 e repetição 689/689 | log `b01f5ef2bbc50a5b7db91cc1419a1bde7ac785250910c07fec6c8d64119f53da` |
| contrato route-plan | coordenador, 13/13 + verify explícito | PASS com refs/anchors externos | manifest `476f1e6de8b018edbe85dc29475e283878952a6e34af97646ace33383c374784` |
| revisão adversarial | dois ciclos independentes | GO, sem P0/P1/P2 remanescente | diff final `2a91823a1b2264de7244d4e75163fdbe8aa1b1322d890664bcb766faf74843c2` |

## Encerramento

- estado: `DONE`; o fechamento é exclusivamente técnico e não promove
  readiness, piloto ou produção;
- commits do app: `edfc94c707ca02b3a5e996fef6f45696e04da2bc`
  (implementação) e `b84c7d547be2237a0c0ae4f0cf3a0a0128fc40fe`
  (QA Log, playbook e backlog);
- commits da coordenação: `d209ab223268bd63a190558a1b34175616e198d0`
  (contrato e manifesto route-plan) e
  `3c7c88ffca87ea921314fceeb68e02f51d0ef7ee` (evidência versionada);
- QA Log e backlog local: `src/aplicativo-motorista/backlog/QA-Execution-Log.md`,
  `src/aplicativo-motorista/backlog/QA-Playbook.md` e
  `src/aplicativo-motorista/backlog/Flutter-Cards.md`, congelados em `b84c7d5`;
- documentação e evidência:
  `program/evidence/VR-QA-APP-GATE-HERMETIC-001.md`;
- rollback: reverter separadamente os commits do app e da coordenação; a
  readiness permanece `HOLD` e nenhuma evidência histórica passa a valer;
- riscos: `R-003` e `R-011` permanecem sistêmicos/em mitigação, mas o blocker
  hermético deste card está controlado;
- limites: nenhum push, deploy, device físico, confirmação humana, campo,
  piloto ou produção foi executado ou autorizado.
