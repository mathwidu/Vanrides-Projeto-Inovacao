# VR-PRIV-003-DRIVER-DATA-LIFECYCLE-001 — Fechar lifecycle e delete de dados do motorista

## Resultado esperado

`DELETE /motoristas/{id}` e
`DELETE /company-admins/motoristas/{id}` usam a mesma política tenant-scoped,
atômica e non-enumerating. A operação trata identidade, sessões, viagens,
localização, avatar, device tokens, outboxes, referências, exportação, retenção,
backup e auditoria conforme decisões aceitas, sem cascade ou prazo inferidos.

## Estado

`analyzed` — não existe política aceita nem ownership/path freeze suficiente
para implementar ou promover o delete.

## Dono operacional

`PRIV/SEC/BE`, com `APP`, `SES`, `OPS`, `Produto`, `Jurídico/DPO` e `QA`.

## Risco ou objetivo associado

`R-002`, `R-007`, `R-008`, `R-023`, `F-PRIV-016`, épico `VR-PRIV-003`, G1 e G6.

## Impact Analysis preliminar

### Mudanças técnicas

- inventariar referências do motorista por tenant e separar bloquear,
  anonimizar, excluir ou reter somente conforme decisão aceita;
- unificar autorização/erro dos dois endpoints, sem enumeração ou delete global;
- coordenar identidade/auth epoch, sessão/storage, refresh, convites/deep links,
  fila offline, device token e outboxes;
- encerrar/reatribuir viagens e GPS somente pelo domínio de localização aceito;
- tratar avatar/store, backups/restores e providers sem falso sucesso;
- versionar estados/idempotência e produzir auditoria AE1 sem payload.

### Contratos afetados

Endpoints de delete de motorista, login/sessão, viagens/localização, avatar,
push/e-mail outboxes, app/admin e contratos de solicitação do titular.

### Riscos e mitigação

Cascade pode apagar dado necessário ou deixar cópia órfã; retry pode duplicar
efeito; restore pode reintroduzir dado. Usar preflight, estado/idempotency key,
transações por store, reconciliação explícita, non-enumeration e rollout
fail-closed por endpoint.

### Validação mínima

Contract/RBAC/tenant tests dos dois endpoints; matriz de referências positiva e
negativa; concorrência/retry/falha parcial; integração de sessão, localização,
avatar e outboxes; restore-purge; gates backend/app e smoke same-version.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` concluído;
- `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` concluído;
- `VR-SEC-004-EMAIL-NAMESPACE-001` concluído;
- `VR-SES-001-AUTH-EPOCH-001` concluído;
- `VR-SES-001-REFRESH-ROTATION-001` concluído;
- `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001` concluído;
- `VR-SES-003-APP-PASSWORD-BASIC-REMOVAL-001` concluído;
- `VR-SES-003-APP-CREDENTIAL-AND-SECURE-STORAGE-001` concluído;
- `VR-SEC-004-INVITE-REDEMPTION-001` concluído;
- `VR-SES-003-VERIFIED-DEEP-LINK-LIFECYCLE-001` concluído;
- `VR-APP-002-OFFLINE-QUEUE-SESSION-BOUNDARY-001` concluído;
- `VR-SEC-001-LOCATION-DOMAIN-001` concluído;
- `VR-SEC-003-LOCATION-REALTIME-001` concluído;
- `VR-MAP-001-EVIDENCE-DATA-GUARD-001` concluído;
- `VR-MAP-001-LOCATION-S2S-001` concluído;
- `VR-APP-001-LOCATION-ADOPTION-001` concluído;
- `VR-UX-002-MOBILE-PRIVACY-PERMISSIONS-001` concluído;
- `VR-BE-004-AVATAR-PRIVACY-LIFECYCLE-001` concluído;
- `VR-SES-002-PUSH-TOKEN-SESSION-LIFECYCLE-001` concluído;
- `VR-BE-005-NOTIFICATION-OUTBOX-MINIMIZATION-001` concluído;
- `VR-BE-005-EMAIL-VERIFICATION-OUTBOX-001` concluído;
- `VR-SEC-002-PUBLIC-DRIVER-DIRECTORY-001`,
  `VR-SEC-002-CORE-ENTITY-PROJECTIONS-001` e
  `VR-SEC-002-DRIVER-PASSENGER-MINIMUM-PROJECTION-001` concluídos, para que
  diretório, DTOs centrais e contato DRV→PAX não mantenham audiência/campos ou
  referências incompatíveis com o lifecycle;
- `VR-BE-ELIGIBILITY-PERIOD-INTEGRITY-001` concluído, pois D10 inclui a
  referência do motorista padrão e eventual modelo histórico precisa estar
  estável antes de remover, reter ou reatribuir essas referências;
- `VR-OPS-001-SENSITIVE-LOG-REDACTION-001` concluído;
- `VR-OPS-002-BACKUP-RETENTION-RESTORE-PURGE-001` concluído;
- `VR-REL-001-DEMO-MEDIA-DATA-PROVENANCE-001` concluído;
- `VR-PGM-004-FUTURE-DATA-COLLECTION-GATE-001` concluído.

## Aplicabilidade congelada D01–D34

Nenhuma família é omitida por silêncio. O Impact Analysis final precisa provar
a classificação abaixo contra schema, código, providers, app e restore:

| Estado | Famílias | Regra para este lifecycle |
|---|---|---|
| `APPLICABLE` | D01–D05, D07, D09–D12, D14–D15, D18–D19, D21–D23, D26, D28–D30, D33–D34 | identidade/credencial/sessão/tenant/perfil, referências operacionais, GPS/realtime, tokens/outboxes, cópias locais, logs, backup, evidência, mídia e REST precisam de destino e owner explícitos |
| `CONDITIONAL / MUST_PROVE` | D06, D08, D13, D16–D17, D20, D25, D27 | pode existir referência indireta ao motorista, egress ou digest; só pode virar `N/A` com consulta/schema/provider e prova de não vinculação/reidentificação |
| `NOT_ACCOUNT_DATA / EXPLICIT` | D24, D31 | delete não revoga permissão do SO nem gira segredo operacional; o coordinator limpa estado do app, e os owners de permissão/segredo preservam sua própria política |
| `FUTURE_GATED` | D32 | qualquer nova família/store ligada ao motorista deve entrar no lifecycle antes da coleta pelo future-data gate |

## Escopo

- executar lifecycle e delete das famílias `APPLICABLE` e das condicionais que
  o preflight provar ligadas ao motorista, sempre pela decisão aceita;
- ambos os endpoints de delete e seus consumidores;
- documentar `N/A` por família/store/provider com evidência negativa, nunca por
  ausência de uma FK óbvia.

## Fora de escopo

- escolher prazo/base/hold, operar dado remoto, cascade ad hoc, deploy, piloto,
  produção ou reescrever histórico.

## Definition of Ready

- [ ] decisões de retenção/export/hold aceitas e vinculadas por digest;
- [ ] referências, stores, providers, endpoints, consumidores e paths congelados;
- [ ] D01–D34 classificados como aplicável, condicional comprovado ou `N/A`
  explícito, incluindo overlays de log, backup, evidência e mídia;
- [ ] estados/idempotência, migration/jobs e falha parcial definidos;
- [ ] testes, smoke, rollout, rollback e evidência same-version definidos;
- [ ] nenhum path sobrepõe outro card ativo.

## Critérios de aceite preliminares

- [ ] autorização é tenant-scoped e os dois endpoints têm contrato canônico;
- [ ] nenhuma referência é ignorada, apagada ou retida por inferência;
- [ ] sessão/storage/convite/deep link/fila/token/outbox/avatar/GPS/realtime/
  Maps/log/evidência/mídia seguem owners e decisões correspondentes;
- [ ] falha parcial não retorna sucesso e retry não duplica efeitos;
- [ ] restore reconcilia purge/hold antes de servir tráfego;
- [ ] auditoria é mínima e non-enumeration é byte-canônica;
- [ ] gates e smoke usam o mesmo manifest backend/app.

## Rollout

Começar em preflight/report-only, ativar um endpoint por vez com kill switch e
reconciliação observável, e só contrair adapters após janela aceita e prova de
restore. Divergência mantém delete bloqueado.

## Rollback

Desabilitar novas solicitações e preservar estado/referências para reconciliação.
Nunca restaurar cascade, apagar auditoria ou ressuscitar sessão/GPS para simular
rollback.
