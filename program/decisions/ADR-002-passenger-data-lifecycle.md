# ADR-002 — Lifecycle de dados do passageiro referenciado

- status: `PROPOSED / INCOMPLETE / NOT_ACCEPTED`
- data da worksheet: `2026-07-18`
- card: `VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-001`
- donos da decisão: Produto, Privacidade e Jurídico/DPO
- inventário pai: commit
  `a3f7140e0a25fe5c440700146a42401500a87e4a`, SHA-256
  `d1dc70c65a0d03ff23950c55c6433378b4294a31c896fd095764dfe1d4be9891`
- matriz PRIV-002:
  `program/privacy/MINIMIZATION_CONSENT_AUDIT_MATRIX.md`
- commit/blob/SHA-256 da matriz PRIV-002:
  `830238cccda4b089fe7ef33fff9b755544dc0acc` /
  `dd863a45a5931a883277af73dbfc90a19c075f9e` /
  `0e4ddcb0af05158f4d3f1c6fffe7dd7c518c46b02d587b17823c62a87b14602a`
- ADR-005: `PROPOSED / INCOMPLETE / REFREEZE_REQUIRED / NOT_ACCEPTED`
- revisão histórica ADR-005, `SUPERSEDED / DO_NOT_ACCEPT`: commit
  `7d12d250f50a1e2a3a8a9a045da603f9c77a339a`, SHA-256
  `b8387d219eccfe09c2da04ff9a75e2070df3c1fada9d514448a4babf3d870f8b`;
  serve apenas como proveniência dos candidatos, não como pedido humano
- revisão/digest desta ADR para aceite: `PENDING`

## Contexto

O hard delete tenant-scoped atual é atômico e falha fechado quando uma das nove
relações contém referência. Isso impede exclusão parcial, porém não define uma
política de retenção, anonimização, exclusão, exportação ou auditoria. Escolher
essas regras exige finalidade e obrigação validadas; esta worksheet não é
parecer jurídico e não contém recomendação aceita.

## Invariantes técnicas

- nunca usar cascade ad hoc para fazer uma solicitação “passar”;
- nunca retornar sucesso se uma etapa obrigatória reverteu ou ficou pendente;
- autorização é tenant-scoped e fail-closed;
- logs, backups, exportações e cada provider permanecem classificados;
- menor, autoridade GUA e GPS exigem decisão explícita e separada;
- aceite desta ADR não autoriza código, migration, deploy, piloto ou produção.

## Semântica normalizada da decisão

Cada decisão é um registro com quatro campos obrigatórios:
o contrato normalizado é `value/status/owner/source`.

- `value`: valor candidato ou `PENDING`;
- `status`: estado da decisão;
- `owner`: autoridade responsável;
- `source`: D-ID, F-ID, matriz, ADR, card e revisão que sustentam o registro.

| Estado | Significado |
|---|---|
| `TECHNICAL_RECOMMENDATION` | candidato técnico; não é política ou aprovação humana |
| `PENDING_PRODUCT` | Produto define/valida finalidade, necessidade ou experiência |
| `PENDING_PRIVACY` | Privacidade define/valida minimização, audiência e transparência |
| `PENDING_LEGAL_DPO` | Jurídico/DPO valida a decisão aplicável sem inferência técnica |
| `PENDING_SECURITY_OPS` | SEC/OPS fecha viabilidade, controle, store ou operação |
| `NOT_APPLICABLE_CURRENT` | superfície ausente no corte; não autoriza implementação futura |
| `ACCEPTED` | somente após autoridade, revisão, digest e timestamp imutáveis |

Uma revisão pode estar completa para pedido humano mantendo `PENDING_*`: a
pergunta, owner, fonte, alternativas e impacto estão completos, mas a resposta
não foi presumida. Enquanto existir valor humano `PENDING_*`, o veredito desta
ADR permanece `NOT_ACCEPTED`.

## Schema obrigatório por família

Cada ID de família da próxima seção possui exatamente estes registros:

| Sufixo | Value atual | Status atual | Owner | Source |
|---|---|---|---|---|
| `.TECH` | recomendação da matriz PRIV-002 | `TECHNICAL_RECOMMENDATION` | SEC/OPS + repo afetado | matriz PRIV-002 + D/F/card |
| `.PRODUCT` | `PENDING` | `PENDING_PRODUCT` | Produto | `H-001`, `H-007` quando aplicável |
| `.PRIVACY` | `PENDING` | `PENDING_PRIVACY` | Privacidade | `H-001`, `H-002`, `H-005`, `H-008` |
| `.LEGAL` | `PENDING` | `PENDING_LEGAL_DPO` | Jurídico/DPO | `H-002`, `H-003`, `H-005`, `H-007` |
| `.LIFECYCLE` | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` | Produto + Privacidade + Jurídico/DPO | prazo, marco, destino, exceção e comunicação |
| `.EVIDENCE` | `PENDING` | `PENDING_PRIVACY + PENDING_SECURITY_OPS` | Privacidade + SEC/OPS | envelope AE1 e evidência canônica |

Nenhum registro `.TECH` pode preencher ou alterar os valores humanos.

## Famílias de lifecycle e rastreabilidade

| ID base | Família/escopo | D-IDs principais | Findings/cards de origem |
|---|---|---|---|
| `LIFE-PASSENGER-001` | passageiro, perfil e avatar | D01, D06, D21, D34 | F-PRIV-007, 015, 016, 017 |
| `LIFE-IDENTITY-001` | `login_identity`, e-mail e tombstone | D01, D02, D04, D22 | F-PRIV-001 |
| `LIFE-AUTH-001` | access/refresh/verificação/device/outbox/auth | D03, D04, D18, D19, D22, D26 | F-PRIV-001, 013 |
| `LIFE-PRESENCE-001` | `presenca` | D13, D34 | F-PRIV-016 |
| `LIFE-GUARDIAN-001` | `guardian_passageiro` e autoridade GUA | D06, D08 | F-PRIV-017 |
| `LIFE-BASE-STOP-001` | `rota_base_parada` | D11, D14, D17 | F-PRIV-003, 005, 009 |
| `LIFE-TRIP-STOP-001` | `viagem_parada` e projeções de viagem | D11, D12, D14–D17 | F-PRIV-003, 005, 009, 018 |
| `LIFE-LINK-REQUEST-001` | `company_link_request` | D09 | F-PRIV-006 |
| `LIFE-INVITE-USAGE-001` | `company_invite_code_usage` | D09 | F-PRIV-006 |
| `LIFE-SERVICE-PASSENGER-001` | `servico_passageiro` | D10, D13 | F-PRIV-008, 018 |
| `LIFE-SERVICE-PRESENCE-001` | `presenca_servico` | D10, D13 | F-PRIV-008 |
| `LIFE-SUPPORT-001` | `support_ticket` | D20 | F-PRIV-008 |
| `LIFE-LOG-AUDIT-001` | logs, telemetria e evidência | D27, D28, D30 | F-PRIV-004, 010 |

## Roteamento explícito D01–D34

Esta tabela roteia perguntas e implementação futura; não atribui finalidade,
prazo, base, consentimento ou aceite. `CROSS` identifica decisão transversal,
e `VERTICAL` identifica card técnico que só executa uma decisão já aceita.

| ID | Rota de decisão/implementação | Estado |
|---|---|---|
| D01 | `LIFE-PASSENGER-001` + `LIFE-IDENTITY-001` | `PENDING_*` |
| D02 | `LIFE-IDENTITY-001` + password/Basic removal | `PENDING_*` |
| D03 | `LIFE-AUTH-001` + refresh/auth epoch/storage | `PENDING_*` |
| D04 | `LIFE-IDENTITY-001` + `LIFE-AUTH-001` + email outbox/Resend | `PENDING_*` |
| D05 | `CROSS` retention/tenant access + lifecycle ADM/tenant | `PENDING_*` |
| D06 | `LIFE-PASSENGER-001` + `LIFE-GUARDIAN-001` + `GPS-001` | `PENDING_*` |
| D07 | `LIFE-PASSENGER-001` + avatar lifecycle | `PENDING_*` |
| D08 | `LIFE-GUARDIAN-001` + `MINOR-001` | `PENDING_*` |
| D09 | `LIFE-LINK-REQUEST-001` + `LIFE-INVITE-USAGE-001` | `PENDING_*` |
| D10 | `LIFE-SERVICE-PASSENGER-001` | `PENDING_*` |
| D11 | `LIFE-BASE-STOP-001` + `LIFE-TRIP-STOP-001` + provider | `PENDING_*` |
| D12 | `LIFE-TRIP-STOP-001` + provider | `PENDING_*` |
| D13 | `LIFE-PRESENCE-001` + `LIFE-SERVICE-PRESENCE-001` | `PENDING_*` |
| D14 | `LIFE-BASE-STOP-001` + `LIFE-TRIP-STOP-001` + `GPS-001` | `PENDING_*` |
| D15 | `LIFE-TRIP-STOP-001` + `GPS-001` + realtime | `PENDING_*` |
| D16 | `LIFE-TRIP-STOP-001` + `GPS-001` + app adoption | `PENDING_*` |
| D17 | `LIFE-BASE-STOP-001` + `LIFE-TRIP-STOP-001` + Maps provider | `PENDING_*` |
| D18 | `LIFE-AUTH-001` + push-token lifecycle | `PENDING_*` |
| D19 | `LIFE-AUTH-001` + notification-outbox minimization | `PENDING_*` |
| D20 | `LIFE-SUPPORT-001`; canal candidato de `EXPORT-SUBJECT-001` | `PENDING_*` |
| D21 | `LIFE-PASSENGER-001` + avatar lifecycle + backup propagation | `PENDING_*` |
| D22 | `LIFE-IDENTITY-001` + `LIFE-AUTH-001` + secure storage | `PENDING_*` |
| D23 | `VERTICAL` offline-queue session boundary + `CROSS` retention | `PENDING_*` |
| D24 | `VERTICAL` mobile permissions + verified link + release boundary | `PENDING_*` |
| D25 | `PROVIDER-GOOGLE-001` + `GPS-001` + log redaction | `PENDING_*` |
| D26 | `LIFE-AUTH-001` + email outbox + `PROVIDER-RESEND-001` | `PENDING_*` |
| D27 | `LIFE-LOG-AUDIT-001` | `PENDING_*` |
| D28 | `LIFE-LOG-AUDIT-001` + `INCIDENT-HOLD-001` | `PENDING_*` |
| D29 | `BACKUP-001` + `INCIDENT-HOLD-001` | `PENDING_*` |
| D30 | `LIFE-LOG-AUDIT-001` + evidence ratchet/Maps guard | `PENDING_*` |
| D31 | `SECRET-SCOPE-001` + `VERTICAL` secret-scope/rotation | `PENDING_*`; valor/derivado é `TECH_EXCLUDE` da exportação |
| D32 | future-data gate; mecanismo atual ausente | `NOT_APPLICABLE_CURRENT / PENDING_FUTURE` |
| D33 | `MEDIA-001` + `INCIDENT-HOLD-001` | `PENDING_*` |
| D34 | owner REST vertical por endpoint + ratchet terminal | `PENDING_*` |

## Decisões cross-cutting separadas

| ID | Value | Status | Owner | Source |
|---|---|---|---|---|
| `MINOR-001` | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` | Produto + Privacidade + Jurídico/DPO | H-003; D06, D08, D11–D17, D24, D25, D28–D30, D33, D34 |
| `GPS-001` | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Produto + Privacidade + Jurídico/DPO + SEC/OPS | H-004; D06, D11–D17, D24, D25, D28–D30, D33, D34; D30 é cópia possível até F-PRIV-014; ADR-005 `NOT_ACCEPTED` |
| `BACKUP-001` | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Produto + Privacidade + Jurídico/DPO + SEC/OPS | H-009; D29 overlay de D01–D14, D18–D20 e URL D21; D34 no boundary restaurado |
| `EXPORT-SUBJECT-001` | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` | Produto + Privacidade + Jurídico/DPO | H-007; D01–D34 por aplicabilidade; D20 é canal candidato; D32 registra mecanismo ausente |
| `PROVIDER-MAPS-001` | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Produto + Privacidade + Jurídico/DPO + SEC/OPS | H-006; D11, D12, D14, D17 |
| `PROVIDER-GOOGLE-001` | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Produto + Privacidade + Jurídico/DPO + SEC/OPS | H-006; D06, D11, D12, D25 |
| `PROVIDER-RESEND-001` | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Produto + Privacidade + Jurídico/DPO + SEC/OPS | H-006; D04, D26 |
| `PROVIDER-PUSH-001` | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Produto + Privacidade + Jurídico/DPO + SEC/OPS | H-006; D18, D19; provider real N/A no corte |
| `INCIDENT-HOLD-001` | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | H-011; `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` |
| `INCIDENT-FORENSIC-EXTRACTION-001` | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | H-011; cópia forense separada de `EXPORT-SUBJECT-001` |
| `INCIDENT-COMMUNICATION-001` | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` | Produto + Privacidade + Jurídico/DPO | H-011; somente experiência/comunicação, não autoridade do hold |
| `EVIDENCE-EXTERNAL-STORES-001` | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | D30; F-PRIV-010/014; GitHub Actions artifacts/GHCR: acesso, retenção, publicação e delete |
| `SECRET-SCOPE-001` | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | D31; F-PRIV-011; owner/escopo/metadata/auditoria; valor e derivado são `TECH_EXCLUDE` |
| `MEDIA-001` | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` | Produto + Privacidade + Jurídico/DPO | H-010; D33 |

Exportação do titular, extração forense, comunicação de incidente, egress de
cada provider, backup/PITR/restore e incidente/hold são decisões diferentes.
Uma não preenche nem aprova outra.

ADR-005 permanece `PROPOSED / INCOMPLETE / REFREEZE_REQUIRED / NOT_ACCEPTED`.
Sua revisão histórica está `SUPERSEDED / DO_NOT_ACCEPT`; os valores podem ser
citados somente como candidatos de proveniência, nunca como revisão congelada
vigente. Nenhum prazo, papel ou exceção é importado com status `ACCEPTED`.

## Contrato da solicitação a decidir

| Campo | Value | Status | Owner | Source |
|---|---|---|---|---|
| atores autorizados e prova de autoridade | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` | Produto + Privacidade + Jurídico/DPO | H-003, H-007 |
| menor e participação/revogação do responsável | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` | Produto + Privacidade + Jurídico/DPO | `MINOR-001` |
| exportação prévia, escopo, formato e canal | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` | Produto + Privacidade + Jurídico/DPO | `EXPORT-SUBJECT-001` |
| estados, idempotência e falha parcial | `PENDING` | `PENDING_SECURITY_OPS` | SEC/OPS | Impact Analysis técnico futuro |
| SLA, deadline e retry | `PENDING` | `PENDING_PRODUCT + PENDING_SECURITY_OPS` | Produto + SEC/OPS | H-007 |
| reuso de e-mail, tombstone e reidentificação | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` | Produto + Privacidade + Jurídico/DPO | `LIFE-IDENTITY-001` |
| envelope mínimo de auditoria | `PENDING` | `PENDING_PRIVACY + PENDING_SECURITY_OPS` | Privacidade + SEC/OPS | H-008, AE1 |
| backup, PITR e restore | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Produto + Privacidade + Jurídico/DPO + SEC/OPS | `BACKUP-001` |
| egress e lifecycle por provider | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Produto + Privacidade + Jurídico/DPO + SEC/OPS | `PROVIDER-*` |
| incidente, hold, release e conservação | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | `INCIDENT-HOLD-001` |
| extração/cópia forense e canal restrito | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | `INCIDENT-FORENSIC-EXTRACTION-001`; não é exportação do titular |
| comunicação ao titular e exceções | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` | Produto + Privacidade + Jurídico/DPO | `INCIDENT-COMMUNICATION-001`; H-007, H-011 |

## Alternativas obrigatórias

1. hard delete coordenado após remoção/transformação explícita das referências;
2. anonimização irreversível de famílias selecionadas e exclusão das demais;
3. retenção por prazo/finalidade aceita, seguida de job assíncrono;
4. rejeição/hold aceito e estritamente scoped, com prazo, comunicação e revisão.

Nenhuma alternativa está recomendada ou aceita nesta revisão.

## Maturidade para pedido humano

A ADR pode ficar `COMPLETE_FOR_HUMAN_REVIEW / NOT_ACCEPTED` quando:

- todo ID possui pergunta, impacto, alternativas, owner e fonte;
- recomendações técnicas estão fechadas ou registradas como blocker;
- todos os valores humanos continuam explicitamente `PENDING_*`;
- matriz, ADR e DOR apontam para a mesma revisão/digest;
- não existe valor `ACCEPTED` nem implementação autorizada.

O estado `waiting_human` solicita resposta sobre uma revisão imutável; não exige
que a resposta humana já exista.

## Evidência exigida para aceite

Para mudar o veredito para `ACCEPTED`:

- todos os valores humanos precisam deixar `PENDING_*`;
- cada resposta identifica valor, autoridade, papel e justificativa;
- alternativas rejeitadas e impactos permanecem registrados;
- `MINOR-001`, `GPS-001`, `BACKUP-001`, `EXPORT-SUBJECT-001`,
  `PROVIDER-*`, `INCIDENT-HOLD-001`,
  `INCIDENT-FORENSIC-EXTRACTION-001`, `INCIDENT-COMMUNICATION-001` e
  `EVIDENCE-EXTERNAL-STORES-001`, `SECRET-SCOPE-001` e `MEDIA-001` estão
  resolvidos ou N/A com justificativa;
- ADR-005 continua `NOT_ACCEPTED` ou possui aceite próprio exato;
- commit/blob/SHA-256 e timestamp identificam a revisão aceita;
- o aceite declara que não concede GO a código, migration, deploy, piloto ou
  produção.

Depois, `VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-IMPLEMENTATION-001` ainda precisa
congelar paths, contratos, migrations, jobs, testes, smokes, rollout e rollback.

## Registro de decisão

- recomendação candidata: `PENDING`;
- status de maturidade: `INCOMPLETE`;
- aprovador/autoridade de Produto: `PENDING`;
- aprovador/autoridade de Privacidade: `PENDING`;
- Jurídico/DPO e autoridade: `PENDING`;
- SEC/OPS responsável pela viabilidade: `PENDING`;
- matriz PRIV-002 commit/blob/SHA-256: `PENDING`;
- revisão e digest desta ADR: `PENDING`;
- timestamp: `PENDING`;
- ADR-005: `NOT_ACCEPTED`;
- veredito: `NOT_ACCEPTED`.
