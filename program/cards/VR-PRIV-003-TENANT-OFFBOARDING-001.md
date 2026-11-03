# VR-PRIV-003-TENANT-OFFBOARDING-001 — Fechar lifecycle e offboarding do tenant

## Resultado esperado

Uma empresa possui state machine `ACTIVE → CLOSING → CLOSED`, com autoridade,
preflight, idempotência, janela e reconciliação explícitos. Membros, vínculos,
serviços, viagens, GPS, suporte, tokens/outboxes, providers, logs, backups e
restores seguem decisões aceitas sem cascade destrutiva, falso sucesso ou
retenção indefinida. Fechar tenant é separado de excluir uma conta ADM.

## Estado

`analyzed` — o produto cria tenant no bootstrap e mantém pelo menos 14 grupos
de referências persistidas, mas não possui fluxo de fechamento/reabertura.

## Risco ou objetivo associado

`R-001`, `R-007`, `R-008`, `R-026`, `F-PRIV-008`, G6 e G7.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` e
  `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluídos;
- `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` e
  `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` concluídos;
- `VR-PRIV-003-COMPANY-ADMIN-DATA-LIFECYCLE-001`,
  `VR-PRIV-003-GUARDIAN-DATA-LIFECYCLE-001`,
  `VR-PRIV-003-DRIVER-DATA-LIFECYCLE-001` e
  `VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-IMPLEMENTATION-001` concluídos;
- `VR-SEC-001-LOCATION-DOMAIN-001`, `VR-SEC-003-LOCATION-REALTIME-001` e
  `VR-MAP-001-LOCATION-S2S-001` concluídos;
- `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001`,
  `VR-BE-005-EMAIL-VERIFICATION-OUTBOX-001`,
  `VR-BE-005-NOTIFICATION-OUTBOX-MINIMIZATION-001` e
  `VR-SEC-002-SUPPORT-MINIMUM-PROJECTION-001` concluídos;
- `VR-OPS-001-SENSITIVE-LOG-REDACTION-001`,
  `VR-OPS-002-BACKUP-RETENTION-RESTORE-PURGE-001`,
  `VR-REL-001-DEMO-MEDIA-DATA-PROVENANCE-001` e
  `VR-PGM-004-FUTURE-DATA-COLLECTION-GATE-001` concluídos.

## Aplicabilidade D01–D34

Todas as famílias D01–D34 são `APPLICABILITY_REQUIRED` no preflight: o tenant
é boundary de autorização ou cópia para quase todos os dados. D31 não é dado do
titular, mas credenciais S2S/provider do tenant precisam ser revogadas pelo
owner de segredo; D32 permanece future-gated; D24 é estado do SO e não pode ser
revogado remotamente, embora sessão/cache sejam limpos. Nenhuma dessas
distinções permite omitir a família do plano.

## Escopo

- state machine e autoridade de solicitar, aprovar, cancelar, fechar/reabrir;
- preflight referencial por schema/store/provider e ordem de drain/revogação;
- suspensão de novas operações, término seguro de viagens/GPS e comunicação;
- exportação/portabilidade aplicável, retenção/hold, purge e restore isolado;
- auditoria AE1, observabilidade agregada e reconciliação por lote/idempotência.

## Fora de escopo

- escolher política durante implementação, cascade, operação remota sem
  autorização, apagar prova/hold, deploy, piloto ou produção.

## Definition of Ready

- [ ] autoridade, estados, cancelamento/reabertura e último ADM aceitos;
- [ ] matriz D01–D34 por store/provider e ordem de dependências congelada;
- [ ] contrato, compatibilidade, concorrência, idempotência e falha parcial definidos;
- [ ] paths/migrations/jobs, preflight, métricas e kill switches congelados;
- [ ] testes, smoke same-version, rollout, restore-purge e rollback definidos.

## Critérios de aceite preliminares

- [ ] `CLOSING` bloqueia novas coletas/operações sem perder reconciliação;
- [ ] viagens/localização encerram fail-closed antes de remover autoridades;
- [ ] lifecycles PAX/DRV/GUA/ADM são orquestrados sem cascade ou cross-tenant;
- [ ] provider/token/outbox/backup/restore seguem decisões e owners próprios;
- [ ] falha parcial nunca declara `CLOSED`; retry converge com auditoria mínima;
- [ ] fechamento e eventual reabertura não ressuscitam sessão, GPS ou dado purgado.

## Rollout e rollback

Iniciar report-only em tenant sintético, depois habilitar state machine atrás de
feature flag, com batches pequenos e abort condition. Rollback antes de purge
volta a `ACTIVE` somente após reconciliação autorizada; dado já purgado nunca é
recriado, e após contração física aplica-se forward-fix.
