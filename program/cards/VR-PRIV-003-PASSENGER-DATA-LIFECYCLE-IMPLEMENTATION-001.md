# VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-IMPLEMENTATION-001 — Implementar lifecycle aprovado de dados do passageiro

## Resultado esperado

Uma solicitação autorizada executa exatamente a política ADR-002 aceita para
conta, identidade, autenticação e referências, com autorização tenant-scoped,
idempotência, estados observáveis, auditoria mínima, exportação e propagação
controlada. Não há `23503` inesperado, cascade implícita, falso sucesso,
retenção sem prazo ou remoção fora da finalidade aprovada.

## Estado

`analyzed` — sucessor técnico deliberadamente não executável. A ADR-002 não
está aceita, o checkpoint pai não está concluído e nenhum path de produto,
migration ou contrato pode ser congelado com segurança.

## Risco ou objetivo associado

`R-021`, `VR-PRIV-003`, G6 e G7.

## Impact Analysis preliminar

A decisão poderá exigir backend, PostgreSQL, jobs, app, exportação, auditoria,
backup/restore e provedores. A ADR precisa conter análise preliminar suficiente
para o aceite da política; este sucessor produz o Impact Analysis final/exato
derivado da revisão aceita, com produtores/consumidores, paths, compatibilidade,
auth, schema, migrations, concorrência, rollout, observabilidade, testes e
rollback antes de `ready`.

## Dependências

- `VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-001` concluído com ADR-002 aceita;
- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído e comprovado no freeze;
- `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` concluído;
- `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` concluído com política aceita ou
  N/A por decisão explícita, cada uma por revisão/digest próprio;
- `VR-SEC-004-EMAIL-NAMESPACE-001` e `VR-SES-001-AUTH-EPOCH-001` concluídos;
- `VR-SES-001-REFRESH-ROTATION-001` concluído;
- `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001` e
  `VR-SES-003-APP-CREDENTIAL-AND-SECURE-STORAGE-001` concluídos;
- `VR-SES-003-APP-PASSWORD-BASIC-REMOVAL-001` concluído;
- `VR-SEC-004-INVITE-REDEMPTION-001` concluído;
- `VR-SEC-001-LOCATION-DOMAIN-001` concluído;
- `VR-SEC-003-LOCATION-REALTIME-001`, `VR-MAP-001-EVIDENCE-DATA-GUARD-001`,
  `VR-MAP-001-LOCATION-S2S-001` e `VR-APP-001-LOCATION-ADOPTION-001`
  concluídos;
- `VR-UX-002-MOBILE-PRIVACY-PERMISSIONS-001` concluído;
- `VR-BE-004-AVATAR-PRIVACY-LIFECYCLE-001` concluído;
- `VR-SES-002-PUSH-TOKEN-SESSION-LIFECYCLE-001`,
  `VR-BE-005-NOTIFICATION-OUTBOX-MINIMIZATION-001` e
  `VR-BE-005-EMAIL-VERIFICATION-OUTBOX-001` concluídos;
- `VR-SEC-002-CORE-ENTITY-PROJECTIONS-001` e
  `VR-SEC-002-DRIVER-PASSENGER-MINIMUM-PROJECTION-001` concluídos; o primeiro
  já exige o slice GUA↔PAX e, juntos, estabilizam as projeções REST ligadas ao
  lifecycle antes de exportação, anonimização ou remoção;
- `VR-SEC-002-SUPPORT-MINIMUM-PROJECTION-001` concluído;
- `VR-BE-ELIGIBILITY-PERIOD-INTEGRITY-001` concluído, pois
  `servico_passageiro`/`presenca_servico` e suas constraints, tenant boundary e
  concorrência precisam estar estáveis antes do destino lifecycle dessas
  referências ser implementado;
- `VR-OPS-001-SENSITIVE-LOG-REDACTION-001` e
  `VR-OPS-002-BACKUP-RETENTION-RESTORE-PURGE-001` concluídos;
- `VR-SES-003-VERIFIED-DEEP-LINK-LIFECYCLE-001` concluído;
- `VR-REL-001-DEMO-MEDIA-DATA-PROVENANCE-001` e
  `VR-PGM-004-FUTURE-DATA-COLLECTION-GATE-001` concluídos;
- validação Jurídico/DPO registrada sem transformar o card em parecer legal.

## Aplicabilidade congelada D01–D34

| Estado | Famílias | Regra para este lifecycle |
|---|---|---|
| `APPLICABLE` | D01–D06, D08–D22, D25–D26, D28–D30, D33–D34 | conta/menor/tenant, relações, serviço/viagem/presença/GPS, sessão/outboxes/suporte/avatar, providers, logs, backup, evidência, mídia e REST precisam de destino e owner explícitos |
| `CONDITIONAL / MUST_PROVE` | D07, D27 | perfil/avatar está rotulado DRV/GUA/ADM no inventário e rate-limit usa digest de origem; cópia/DTO/file ou digest reidentificável ligado ao PAX entra, e `N/A` exige prova negativa |
| `NOT_PASSENGER_DATA / EXPLICIT` | D23, D31 | D23 é fila offline DRV; segredo operacional D31 não é apagado nem rotacionado por delete de conta |
| `NOT_ACCOUNT_MUTATION / EXPLICIT` | D24 | o estado de permissão do SO não é revogado automaticamente; o coordinator limpa sessão/cache e o owner mobile governa prompt/revogação pelo usuário/SO |
| `FUTURE_GATED` | D32 | qualquer coleta/store futura ligada ao PAX entra neste lifecycle antes de ser habilitada |

Cada `CONDITIONAL`/`N/A` deve citar schema, consulta, provider e consumidor
inspecionados. A lista não autoriza exportar segredos nem material autenticador.

## Escopo preliminar

- implementar somente famílias e destinos aprovados;
- contrato/API, auth, persistence, jobs, app e auditoria necessários;
- migrations e preflight sanitizado quando aplicável;
- testes PostgreSQL/concorrência, gate de consumidores e smoke same-version;
- rollout, rollback/forward-fix e evidência por repositório.

## Fora de escopo

- escolher ou alterar a política durante a implementação;
- adicionar cascade ad hoc ou prazo não aprovado;
- usar dados reais em fixture/evidência;
- expandir a allowlist antes do Impact Analysis final;
- push, deploy, piloto ou produção no gate local do card.

## Definition of Ready

- [ ] checkpoint pai `done` com ADR-002 aceita e digest imutável;
- [ ] Impact Analysis final e matriz produtor/consumidor completas;
- [ ] aplicabilidade D01–D34 provada por família/store/provider, sem overlay
  implícito de log, backup, evidência ou mídia;
- [ ] paths backend/app/coordenação e migrations congelados;
- [ ] contrato, compatibilidade, autorização, idempotência e concorrência
  definidos;
- [ ] testes, gates, smoke, observabilidade e evidências definidos;
- [ ] preflight, rollout, backup/restore e rollback/forward-fix aceitos;
- [ ] dirty paths preexistentes inventariados e isolados.

## Critérios de aceite preliminares

- [ ] política aceita aplicada integralmente e nenhuma família omitida ou
  marcada `N/A` sem evidência negativa;
- [ ] autorização e concorrência falham fechadas;
- [ ] estados/retries são idempotentes e falsos sucessos impossíveis;
- [ ] exportação/auditoria preservam minimização e não vazam PII;
- [ ] gates de produtores/consumidores e smoke same-version passam;
- [ ] rollback/forward-fix e restore são comprovados;
- [ ] R-021 e gates G6/G7 são atualizados com evidência, não por inferência.

## Rollback

Será definido contra a ADR aceita antes de `ready`. Até lá, o rollback é não
implementar: preservar o hard delete atômico fail-closed e não abrir paths de
produto.
