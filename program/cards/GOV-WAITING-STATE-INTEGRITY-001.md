# GOV-WAITING-STATE-INTEGRITY-001 — Tornar estados de espera realmente acionáveis

## Resultado esperado

Nenhum card pode ser anunciado como executável enquanto ainda possui blocker ou
Definition of Ready incompleta. Cards em espera apontam para a ação, artefato e
evidência que realmente os liberam; contratos que misturam decisão e
implementação são separados antes de chegarem a `ready`.

## Estado

`done`

## Risco ou objetivo associado

Integridade da fonte de verdade, `R-003`, `R-010`, `R-021`, `VR-PGM-004` e
gates `G0 — CONTROLLED`, `G6 — PILOT_READY` e `G7 — PRODUCTION_READY`.

## Impact Analysis

### Contexto

As auditorias do estado ocioso encontraram sete falhas de controle:

- `program_control.py next` carrega somente `program.json` e pode anunciar um
  card `ready` inválido sem rodar a validação estática;
- `ready` com `blockers` ou DoR Markdown incompleta não falha automaticamente;
- `APP-NAV-AUDIO-CONFIRM-001` aponta para evidência inexistente e não autoriza a
  evidência coordenadora exigida pelo DoD;
- `VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-001` promete decisão e implementação,
  mas permite apenas coordenação e nem possui ADR-002 preparada;
- `VR-QA-INTEGRATION-PROVENANCE-001` está `waiting_external`, embora a ação
  imediata seja autorização humana de push; GitHub só vira o blocker depois;
- provas humanas de e-mail/app dependem de artefatos imutáveis que precisam ser
  revalidados antes de agendar a pessoa.
- `VR-PRIV-001/002` existem somente como épicos abstratos, sem card, owner,
  artefato ou evidência capazes de concluir a dependência do lifecycle.

### Mudanças técnicas

- validar tipo/conteúdo de `blockers` e proibir blocker em estado executável;
- para todo estado executável, exigir seções contratuais substantivas e
  checklist DoR íntegro, descrito e sem item pendente;
- fazer `next` carregar repositórios e recusar qualquer erro estático;
- adicionar testes negativos e positivos do ratchet;
- registrar o pacote TTS legado como tecnicamente inválido, criar evidência
  canônica bloqueada e separar a nova run proveniente da audição humana;
- tornar o card atual de lifecycle um checkpoint de decisão e criar sucessor de
  implementação ainda `analyzed`;
- classificar provenance como `waiting_human` até autorização explícita;
- registrar presença/hash dos artefatos humanos congelados.

### Contratos afetados

Somente estados, cards, evidências e o comando de seleção do programa. Nenhuma
API, schema, app, backend, Maps, dado real, CI remoto ou infraestrutura muda.

### Riscos e mitigação

- bloquear card legítimo por título variável: seções são reconhecidas por
  prefixo Markdown em `ready`, `in_progress` e `validating`;
- confundir evidência pendente com PASS: arquivos usam veredito explícito de
  espera, bloqueio ou `NOT_READY`;
- expandir lifecycle antes da decisão: sucessor permanece `analyzed`, sem
  allowlist de produto, até ADR-002 aceita e DoR completa;
- reclassificar GitHub como falha: o card preserva toda a prova local e só muda
  a etapa humana atual do blocker.

### Validação mínima

- testes unitários positivos/negativos do controlador e do `next`;
- `py_compile`, parse JSON, `program check`, `summary`, `next` e
  `git diff --check`;
- reprodução read-only dos hashes/paths e da falha TTS;
- revisão independente do ratchet, estados humanos e decisões congeladas.

## Dependências

- `GOV-EVIDENCE-RATCHET-001` concluído;
- nenhum card de produto ativo;
- artefatos e worktrees inspecionados somente leitura antes da sincronização.

## Definition of Ready

- [x] `GOV-EVIDENCE-RATCHET-001` está concluído e rastreado;
- [x] não havia outro card `in_progress` ou `validating` na ativação;
- [x] Impact Analysis, dependências, escopo, rollback e allowlist foram
  congelados antes das mudanças;
- [x] dirty paths preexistentes de coordenação e app foram inventariados e
  permanecem fora do escopo;
- [x] nenhum aceite humano, push, workflow, deploy ou código de produto é
  necessário para executar este card;
- [x] ADR-005 permanece `waiting_human` na revisão e no digest congelados.

## Escopo

- controlador/testes e documentação operacional do programa;
- este card, template, `program.json`, risco, dashboard/status e evidência;
- cards/evidências de áudio, lifecycle, provenance e provas humanas congeladas;
- novo card de implementação do lifecycle, ainda não executável;
- card sucessor do dashboard runtime stale, sem implementar a correção aqui;
- cards executáveis de `VR-PRIV-001/002`, removendo a dependência abstrata que
  impedia chegar à ADR-002.
- contrato executável do registro de aceite da ADR-005, sem preencher ou
  aceitar qualquer campo humano.
- semântica não volátil do WIP no card `waiting_human` da ADR-005.

## Fora de escopo

- confirmar áudio, teclado ou leitor de tela em nome de uma pessoa;
- aceitar ADR-002/ADR-005 ou definir obrigação jurídica;
- alterar backend/app/Maps, executar push, workflow, deploy, banco ou promoção;
- corrigir freshness do dashboard rastreado, que receberá card próprio.

## Critérios de aceite

- [x] `ready` com blocker falha automaticamente.
- [x] `ready` sem contrato/DoR completa falha; contrato completo passa.
- [x] `next` recusa programa estaticamente inválido antes de anunciar card.
- [x] áudio aponta para evidência canônica existente, preserva worksheet local
  e separa rerun proveniente da confirmação humana.
- [x] lifecycle separa decisão de implementação, sem fingir DoR de produto.
- [x] `VR-PRIV-001/002` possuem cadeia de cards, owner, artefato e evidência
  concretos antes do lifecycle.
- [x] provenance descreve autorização humana antes do blocker GitHub.
- [x] JAR de e-mail e worktree/app/JAR de signup continuam nos hashes congelados.
- [x] ADR-005 continua `waiting_human`; revisão, digest, status, veredito e os
  sete campos humanos continuam `PENDING`, enquanto o validador passa a rejeitar
  aceite futuro ausente, duplicado, placeholder ou incoerente.
- [x] testes do controlador, JSON, `program check` e `git diff --check` passam.
- [x] evidência registra comandos, resultados e SHA sem dados sensíveis.

## Rollback

Reverter os commits deste card. Isso restaura os estados/documentos anteriores e
remove o ratchet adicional, reabrindo explicitamente o risco de selecionar
trabalho bloqueado; nenhum dado ou contrato de produto precisa de rollback.
