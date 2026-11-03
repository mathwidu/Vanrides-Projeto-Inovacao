# VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-001 — Preparar e obter decisão do lifecycle de dados do passageiro

## Resultado esperado

Produto, Privacidade e Jurídico/DPO aprovam uma política rastreável para conta,
identidade, artefatos de autenticação e cada família que referencia passageiro.
A decisão registra finalidade, prazo, marco inicial, destino final, atores,
menores, exportação, auditoria e cópias secundárias sem inferir cascade,
anonimização, exclusão ou retenção. A implementação fica em card separado.

## Estado

`analyzed` — `VR-PRIV-001` está `done` com inventário congelado e
`VR-PRIV-002` está `in_progress/active`, mas sua matriz ainda precisa ser
concluída por revisão imutável. A ADR-002 ainda está
`PROPOSED / INCOMPLETE / NOT_ACCEPTED` e não há
Definition of Ready completa. Portanto ainda não existe uma ação humana madura
que justifique `waiting_human`.

## Risco ou objetivo associado

`R-021`, épico `VR-PRIV-003` e gates G6 e G7.

## Impact Analysis

### Contexto

`VR-SEC-002-PASSENGER-DELETE-ATOMIC-001` comprovou que uma FK `23503` reverte
passageiro, identidade e artefatos integralmente. Esse fail-closed é correto
para a mutação atual, mas não define como atender uma solicitação legítima de
exclusão, anonimização, retenção ou exportação quando há referências.

### Artefato de decisão

`program/decisions/ADR-002-passenger-data-lifecycle.md` é uma worksheet
`PROPOSED / INCOMPLETE / NOT_ACCEPTED`. Ela usa registros
`value/status/owner/source`, torna perguntas e aprovadores explícitos,
mas não escolhe finalidade, base legal, prazo ou destino em nome dos papéis
humanos responsáveis.

### Contratos afetados

Neste card, apenas decisão e rastreabilidade. A futura política poderá afetar
API, RBAC, jobs, schema, migrations, app, exportação, logs, backups e
provedores; esses contratos só serão congelados no sucessor
`VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-IMPLEMENTATION-001` depois do aceite.

### Riscos e mitigação

- implementar antes da política: sucessor fica `analyzed`, sem paths de produto;
- tratar retenção como parecer jurídico: ADR exige aprovadores e validação
  Jurídico/DPO, e registra limites da decisão técnica;
- esquecer cópias secundárias: worksheet separa banco, logs, backup, exportação
  e provedores;
- misturar checkpoint e código: este card autoriza somente coordenação.

## Relações conhecidas

- `presenca`;
- `guardian_passageiro`;
- `rota_base_parada`;
- `viagem_parada`;
- `company_link_request`;
- `company_invite_code_usage`;
- `servico_passageiro`;
- `presenca_servico`;
- `support_ticket`.

## Dependências

- `VR-SEC-002-PASSENGER-DELETE-ATOMIC-001` concluído;
- `VR-PRIV-001-DATA-INVENTORY-PURPOSE-001` concluído no commit
  `a3f7140e0a25fe5c440700146a42401500a87e4a`, com inventário SHA-256
  `d1dc70c65a0d03ff23950c55c6433378b4294a31c896fd095764dfe1d4be9891`;
- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` está `in_progress/active` e
  deve ser concluído antes deste checkpoint amadurecer para `waiting_human`;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` e
  `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` concluídos por revisões próprias;
- decisões cross-family e incident hold precisam ter perguntas, owners, fontes,
  alternativas e revisão congelados; seus valores humanos podem seguir pendentes;
- recomendação ADR-002 revisada antes de solicitar aceite humano.

## Escopo

- classificar conta, identidade, autenticação e nove relações por finalidade,
  prazo, marco e destino;
- definir atores, exportação, estados, SLA e prova auditável;
- definir menor/responsável, tombstone/reuso de e-mail e reidentificação;
- mapear banco, logs, backups, exportações e provedores;
- registrar recomendação, alternativas, aprovadores e aceite imutável.

## Fora de escopo

- cascade, soft delete, anonimização, migration ou código;
- alteração de endpoint, payload, RBAC, app, banco ou provedor;
- inferência de obrigação legal, prazo ou base legal;
- liberação de e-mail ou remoção de histórico sem política aceita;
- piloto, produção ou uso de dado real.

## Definition of Ready

- [x] conflito técnico, conta, identidade e nove relações inventariados;
- [x] worksheet ADR-002 e DOR `NOT_READY` criados;
- [x] decisão e implementação separadas em cards dependentes;
- [ ] `VR-PRIV-002` concluído;
- [ ] ADR-002 normalizada com pergunta, `value/status/owner/source`, impacto e
  alternativas para toda decisão, ainda que valores humanos sigam `PENDING_*`;
- [ ] `MINOR-001`, `GPS-001`, `BACKUP-001`, `EXPORT-SUBJECT-001`,
  `PROVIDER-*` e `INCIDENT-HOLD-001` presentes e rastreáveis;
- [ ] recomendação, alternativas, riscos, rollout e rollback descritos;
- [ ] aprovadores de Produto, Privacidade e Jurídico/DPO disponíveis;
- [ ] registro de aceite imutável e paths finais de evidência definidos.

## Critérios de aceite

- [ ] ADR-002 aceita identifica revisão/digest, timestamp e autoridade dos
  aprovadores;
- [ ] cada família registra finalidade, prazo, marco e destino;
- [ ] atores, menores, exportação, estados, SLA e auditoria estão definidos;
- [ ] identidade, e-mail/tombstone, auth, logs, backups, exportações e
  provedores estão cobertos;
- [ ] limitações jurídicas e de promoção permanecem explícitas;
- [ ] evidência canônica registra o aceite sem dados pessoais desnecessários;
- [ ] a decisão contém Impact Analysis preliminar suficiente para aceite, e o
  sucessor recebe o freeze/allowlist técnicos exatos somente depois do aceite.

## Rollback

Enquanto a decisão não for aceita, o hard delete continua falhando fechado e
revertendo tudo diante de `23503`. Reverter documentos deste checkpoint não
autoriza cascade nem remoção; após aceite, qualquer substituição exige nova
revisão/digest e novo aceite antes de implementação.
