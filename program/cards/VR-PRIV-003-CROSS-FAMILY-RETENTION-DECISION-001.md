# VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001 — Decidir retenção e exclusão entre famílias

## Resultado esperado

Uma decisão revisada e aceita define, para cada família D01–D34, finalidade,
marco inicial, prazo, estado final, exceções, titular, owner e propagação a
banco, app, logs, cache, providers, mídia e backup. A decisão não altera dados;
ela habilita cards técnicos separados.

## Estado

`analyzed` — finding `F-PRIV-008`; nenhuma política humana geral está aprovada.
Este card deve virar `waiting_human`, não implementação, depois do DoR técnico.

## Dono operacional

`PRIV`, com `Produto`, `Jurídico/DPO`, `SEC`, `OPS`, `BE`, `APP` e `MAP`.

## Risco ou objetivo associado

`F-PRIV-008`, `R-007`, `R-021`, `VR-PRIV-003`, G6 e G7.

## Impact Analysis preliminar

### Mudanças técnicas

- produzir ADR/matriz imutável derivada do inventário e do card PRIV-002;
- modelar referências, deletes, anonimização, retenção normal e restore-purge;
- manter incidente/hold em decisão própria, sem importar exceção implicitamente;
- separar política de implementação e criar slices por store/agregado;
- registrar aprovadores, timestamp, commit e digest sem PII.

### Contratos afetados

Decisão documental nesta etapa. API, schema, jobs, exportação e UX somente em
cards sucessores após aceite.

### Riscos e mitigação

Prazo arbitrário ou decisão incompleta pode causar perda/excesso. Manter
alternativas, impacto, owner por célula e `PENDING` fail-closed.

### Validação mínima

Cobertura 100% D01–D34/destinos, revisão cruzada, consistência com ADR-002/005,
digest/aceite imutável, `program check` e scan de PII/segredo.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- inventário pai congelado e matriz de minimização versionada;
- autoridades de Produto, Privacidade e Jurídico/DPO identificadas.

## Escopo

- baseline de retenção/exclusão/export e propagação entre stores;
- handoff explícito ao card de incidente/hold, que não é aceito por esta decisão.

## Fora de escopo

- migration, delete real, consulta remota, aceite presumido ou promoção.

## Definition of Ready

- [ ] template, alternativas, famílias, owners e evidência definidos;
- [ ] dependências/digests congelados e nenhum campo técnico desconhecido;
- [ ] pedido humano é específico, reversível antes do aceite e auditável.

## Critérios de aceite preliminares

- [ ] 100% das famílias/destinos têm política ou `NOT_APPLICABLE` justificado;
- [ ] prazo possui marco inicial, ação final, exceção e owner;
- [ ] backup/restore/provider/app/log/mídia propagam a decisão explicitamente;
- [ ] menores, GPS, endereço e credencial recebem tratamento específico;
- [ ] incident hold permanece decisão separada, com status e autoridade próprios;
- [ ] aceite identifica autoridade, revisão, digest e não concede GO técnico.

## Rollback

Antes de código, substituir/revogar a decisão por nova revisão e novo aceite.
Nunca executar remoção para “reverter” documentação.
