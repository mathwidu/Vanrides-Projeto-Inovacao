# VR-PRIV-003-GUARDIAN-DATA-LIFECYCLE-001 — Fechar lifecycle e delete de dados do responsável

## Resultado esperado

`DELETE /company-admins/responsaveis/{id}` deixa de apagar somente a linha
`guardian` e passa a executar uma política tenant-scoped, atômica,
non-enumerating e idempotente para identidade, credencial, verificação de
e-mail, sessões, vínculos com passageiros, acesso a viagens/GPS, tickets,
avatar, device tokens, outboxes, app local, providers, logs, backups,
evidências e mídia. Nenhuma FK, cópia ou audiência permanece sem destino
aceito; nenhuma cascade ou retenção é inferida.

## Estado

`analyzed` — o endpoint real existe, mas hoje só exige zero vínculos, revoga
artefatos de verificação e apaga `guardian`. Política, referências e paths ainda
não estão aceitos/congelados; portanto implementação e promoção são `NO_GO`.

## Dono operacional

`PRIV/SEC/BE`, com `APP`, `SES`, `OPS`, `Produto`, `Jurídico/DPO` e `QA`.

## Risco ou objetivo associado

`R-002`, `R-007`, `R-008`, `R-024`, `F-PRIV-008`, `F-PRIV-016`, G6 e G7.

## Impact Analysis preliminar

### Evidência observada

- `CompanyAdminGuardianController` expõe
  `DELETE /company-admins/responsaveis/{id}`;
- `CompanyAdminGuardianService.removerResponsavel` valida tenant, exige zero
  vínculos GUA↔PAX, revoga apenas artefatos de verificação e executa hard delete;
- `SupportTicket.createdByGuardian`, identidade/login, sessão, device/outbox,
  avatar/URL, app local, backups e overlays não são reconciliados pelo fluxo;
- GUA também é audiência de viagem/localização e precisa perder autorização e
  subscriptions antes de o delete poder retornar sucesso.

### Mudanças técnicas

- congelar todas as referências/store/providers por tenant e separar bloquear,
  anonimizar, excluir ou reter somente pela política aceita;
- definir contrato de vínculo ativo: bloquear, transferir ou revogar nunca é
  escolhido implicitamente pelo endpoint;
- coordenar auth epoch, refresh, storage, teardown app, device/outboxes,
  e-mail, deep links, suporte, avatar e realtime com estados/idempotência;
- produzir auditoria AE1 mínima, reconciliação de falha parcial e restore-purge;
- manter erro opaco e byte-canônico para outro tenant/ID inexistente.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` concluído;
- `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` concluído;
- `VR-SEC-004-EMAIL-NAMESPACE-001`, `VR-SES-001-AUTH-EPOCH-001` e
  `VR-SES-001-REFRESH-ROTATION-001` concluídos;
- `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001`,
  `VR-SES-003-APP-PASSWORD-BASIC-REMOVAL-001` e
  `VR-SES-003-APP-CREDENTIAL-AND-SECURE-STORAGE-001` concluídos;
- `VR-SES-003-EMAIL-CONFIRM-INTENT-001`,
  `VR-BE-005-EMAIL-VERIFICATION-OUTBOX-001` e
  `VR-SES-003-VERIFIED-DEEP-LINK-LIFECYCLE-001` concluídos;
- `VR-SEC-002-GUARDIAN-PASSENGER-MINIMUM-PROJECTION-001`,
  `VR-SEC-002-CORE-ENTITY-PROJECTIONS-001` e
  `VR-SEC-002-SUPPORT-MINIMUM-PROJECTION-001` concluídos;
- `VR-SEC-001-LOCATION-DOMAIN-001`, `VR-SEC-003-LOCATION-REALTIME-001`,
  `VR-MAP-001-EVIDENCE-DATA-GUARD-001`, `VR-MAP-001-LOCATION-S2S-001` e
  `VR-APP-001-LOCATION-ADOPTION-001` concluídos;
- `VR-UX-002-MOBILE-PRIVACY-PERMISSIONS-001`,
  `VR-BE-004-AVATAR-PRIVACY-LIFECYCLE-001`,
  `VR-SES-002-PUSH-TOKEN-SESSION-LIFECYCLE-001` e
  `VR-BE-005-NOTIFICATION-OUTBOX-MINIMIZATION-001` concluídos;
- `VR-OPS-001-SENSITIVE-LOG-REDACTION-001`,
  `VR-OPS-002-BACKUP-RETENTION-RESTORE-PURGE-001`,
  `VR-REL-001-DEMO-MEDIA-DATA-PROVENANCE-001` e
  `VR-PGM-004-FUTURE-DATA-COLLECTION-GATE-001` concluídos.

## Aplicabilidade congelada D01–D34

| Estado | Famílias | Regra para este lifecycle |
|---|---|---|
| `APPLICABLE` | D01–D05, D07–D08, D12, D14–D15, D18–D22, D25–D26, D28–D30, D33–D34 | conta/tenant/vínculo, autenticação, audiência GPS, suporte/avatar, device/outboxes, providers, logs, backup, evidência, mídia e REST precisam de destino/owner explícitos |
| `CONDITIONAL / MUST_PROVE` | D06, D09–D11, D13, D16–D17, D27 | convite hoje não referencia GUA e rate-limit usa digest de origem; dados PAX/serviço/rota/presença, refs, snapshots, ACLs ou digest só viram `N/A` após prova negativa |
| `NOT_GUARDIAN_DATA / EXPLICIT` | D23, D31 | D23 é fila offline DRV; segredo operacional não é apagado/rotacionado por delete de GUA |
| `NOT_ACCOUNT_MUTATION / EXPLICIT` | D24 | delete não revoga permissão do SO; teardown limpa sessão/cache e o usuário/SO controla a permissão |
| `FUTURE_GATED` | D32 | nova coleta/store ligada ao GUA entra neste lifecycle antes de habilitação |

## Escopo

- o endpoint de delete GUA e todos os produtores/consumidores afetados;
- preflight D01–D34 por schema, consulta, provider, app e restore;
- estado/idempotency key, passos atômicos por store e reconciliação explícita;
- política aceita para vínculos, tickets, auditoria, exportação e comunicação.

## Fora de escopo

- escolher base/prazo/hold, apagar dados de passageiro por vínculo, revogar
  permissão do SO, girar segredo operacional, operar dado remoto, deploy,
  piloto ou produção.

## Definition of Ready

- [ ] decisões de retenção/export/hold e vínculo GUA↔PAX aceitas por digest;
- [ ] D01–D34 classificados com prova e referências/stores/providers congelados;
- [ ] contrato de autorização/non-enumeration/idempotência/falha parcial definido;
- [ ] paths, migrations/jobs, compatibilidade e consumers congelados;
- [ ] testes, smoke same-version, rollout, rollback e restore-purge definidos;
- [ ] dirty paths preexistentes inventariados e nenhum overlap com outro WIP.

## Critérios de aceite preliminares

- [ ] outro tenant/ID ausente têm resposta canônica indistinguível e zero efeito;
- [ ] vínculo ativo segue decisão explícita; nunca apaga PAX ou contorna autoridade;
- [ ] sessão, storage, e-mail, token, outbox, suporte, avatar e GPS/realtime são
  revogados/reconciliados antes do sucesso;
- [ ] falha parcial não retorna sucesso; retry converge sem duplicar efeito;
- [ ] nenhuma família/cópia/provider é omitida ou marcada `N/A` sem prova;
- [ ] restore reaplica purge/hold antes de tráfego e auditoria AE1 não contém PII;
- [ ] gates de produtores/consumidores e smoke usam o mesmo manifest.

## Rollout

Começar em report-only/preflight, depois ativar o endpoint por feature flag e
kill switch. Divergência de referência, provider ou reconciliação bloqueia o
delete e mantém a conta intacta. Contração física só ocorre após janela aceita e
prova de restore.

## Rollback

Desabilitar novas solicitações e preservar o estado para reconciliação. Nunca
ressuscitar sessão/acesso GPS, apagar auditoria, excluir PAX vinculado ou
restaurar o hard delete parcial como rollback.
