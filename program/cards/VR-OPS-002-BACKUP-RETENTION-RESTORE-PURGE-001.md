# VR-OPS-002-BACKUP-RETENTION-RESTORE-PURGE-001 — Fechar retenção, restore e purge de backups

## Resultado esperado

Backups possuem escopo, criptografia, acesso, prazo, prune, inventário e restore
ensaiado. Exclusões aprovadas propagam-se ao restore sem ressuscitar registros;
GPS e outras classes proibidas seguem a política aceita.

## Estado

`analyzed` — finding `F-PRIV-008`; política humana, ambiente de ensaio e
estratégia restore-purge ainda precisam ser congelados.

## Dono operacional

`OPS`, com `PRIV`, `SEC`, `BE`, `DBA` e `QA` como revisores.

## Risco ou objetivo associado

`F-PRIV-008`, `R-007`, `R-021`, `VR-OPS-002`, G5, G6 e G7.

## Impact Analysis preliminar

### Mudanças técnicas

- definir catálogo, criptografia, chaves, acesso, prazo e prune verificável;
- criar restore isolado com checksum, compatibilidade e dados sintéticos;
- aplicar ledger/tombstones aprovados após restore antes de liberar o banco;
- impedir GPS/classes proibidas quando a política exigir;
- emitir evidência sanitizada sem nomes, dumps, paths secretos ou contagens raras.

### Contratos afetados

Scripts/runbooks DEV/staging, secret management, restore, retenção e promoção.
Qualquer operação remota exige autorização separada.

### Riscos e mitigação

Prune apaga último backup, restore ressuscita PII ou chave fica junto do dump.
Usar retenção em camadas, dry-run, dois-person rule para produção e restore gate.

### Validação mínima

Testes de script, backup/restore sintético isolado, checksum/corrupção, prune por
relógio, restore-purge e scan de evidência; nenhum banco remoto neste preparo.

## Dependências

- `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` aceito;
- `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` aceito ou N/A por decisão explícita;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-001` concluído com ADR-002 aceita;
- `VR-SEC-001-LOCATION-READ-SCOPE-001` concluído com checkpoint ADR-005;
- arquitetura de storage/chaves e ambiente isolado aprovados;
- autorização separada antes de tocar staging/produção.

## Escopo

- backup PostgreSQL e stores associados, restore, prune e purge pós-restore.

## Fora de escopo

- executar contra banco remoto, deletar dump existente ou rotacionar chave agora.

## Definition of Ready

- [ ] política, catálogo, RPO/RTO, chaves, ambiente e rollback definidos;
- [ ] paths, scripts, testes, smoke e evidência congelados;
- [ ] preflight prova isolamento e autorização do ambiente.

## Critérios de aceite preliminares

- [ ] dump é criptografado, checksummed, access-controlled e inventariado;
- [ ] prune respeita camadas e nunca remove conjunto mínimo recuperável;
- [ ] restore isolado prova versão e não libera dados antes do purge aprovado;
- [ ] exclusão não é ressuscitada por restore posterior;
- [ ] evidência não contém dump, segredo, GPS ou PII.

## Rollback

Parar prune/promoção e preservar último conjunto conhecido enquanto se corrige o
processo. Restore nunca é feito no lugar do banco atual como rollback cego.
