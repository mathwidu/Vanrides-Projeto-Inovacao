# GOV-EVIDENCE-RATCHET-001 — Exigir evidência canônica por card concluído

## Resultado esperado

O programa rejeita qualquer card `done` que tente usar somente baseline,
evidência de outro card ou referência inexistente. Cada conclusão precisa listar
e possuir `program/evidence/<CARD_ID>.md`, e o arquivo deve identificar o card.

## Estado

`done`

## Risco ou objetivo associado

Integridade da fonte de verdade, `VR-PGM-004` e gates `G0 — CONTROLLED` e
`G7 — PRODUCTION_READY`.

## Contexto

O validador atual exige apenas que `evidence` seja truthy. Como vários cards em
análise já carregam `program/BASELINE.md`, uma transição incorreta para `done`
com qualquer commit passaria sem uma evidência específica da entrega. A revisão
independente do pacote ADR-005 classificou a lacuna como P2.

## Impact Analysis

### Mudanças técnicas

- para `done`, exigir `evidence` como lista de strings;
- exigir a entrada exata `program/evidence/<CARD_ID>.md`;
- exigir arquivo regular existente e conteúdo contendo o ID do card;
- manter baseline e evidências relacionadas apenas como referências adicionais;
- adicionar testes negativos para baseline-only, arquivo ausente e arquivo de
  outro conteúdo, além do caminho positivo;
- atualizar o DoD e a documentação operacional.

### Contratos afetados

Somente o contrato machine-readable de conclusão do programa. Nenhuma API,
schema de produto, app, Maps, infraestrutura remota ou dado real muda.

### Riscos e mitigação

- cards históricos falharem: auditoria prévia confirmou que todos os cards já
  `done` possuem o arquivo canônico e o ID no conteúdo;
- arquivo vazio satisfazer o gate: presença do ID também é obrigatória;
- bloquear trabalho em análise: o ratchet é aplicado somente a `done`;
- mascarar prova fraca com baseline: baseline continua suplementar e nunca
  substitui a evidência do card.

## Dependências

- `GOV-PIPELINE-001` concluído;
- root sem alterações não relacionadas além dos paths conhecidos preservados;
- pacote documental ADR-005 checkpointado antes da ativação deste card.

## Escopo

- `scripts/program_control.py` e testes;
- `program/OPERATING_MODEL.md`, `program/README.md`, este card, evidência,
  programa e dashboard/status.

## Fora de escopo

- validar semanticamente cada comando dentro da evidência;
- assinar artefatos, buscar CI remoto ou reexecutar provas históricas;
- alterar código de backend, app ou Maps;
- push, deploy ou promoção.

## Critérios de aceite

- [x] `done` com apenas `program/BASELINE.md` falha.
- [x] `done` que lista o arquivo canônico ausente falha.
- [x] `done` cujo arquivo canônico não contém o ID falha.
- [x] `done` com commit e evidência canônica existente/identificada passa.
- [x] todos os cards históricos `done` continuam válidos.
- [x] testes do controlador, JSON, `program check` e `git diff --check` passam.
- [x] evidência registra comandos, resultados e SHA sem dados sensíveis.

## Evidência

Ver `program/evidence/GOV-EVIDENCE-RATCHET-001.md`.

Commit de implementação:

- `c45b53e9cbddfc7b004d9b315b6cf1bdda6d5886`

## Rollback

Reverter o commit do validador e a regra documental. Isso reabre explicitamente
o risco de falso `done`; nenhum dado ou contrato de produto precisa de rollback.
