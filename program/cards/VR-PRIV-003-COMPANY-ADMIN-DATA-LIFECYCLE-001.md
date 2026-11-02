# VR-PRIV-003-COMPANY-ADMIN-DATA-LIFECYCLE-001 — Fechar lifecycle da conta de administrador da empresa

## Resultado esperado

A conta pessoal `COMPANY_ADMIN` possui offboarding explícito, tenant-scoped e
auditável: transferência/substituição de autoridade, revogação de sessões e
credenciais, tratamento de referências de revisão/criação, avatar, device,
outboxes, suporte, app local, providers, backups e exportação seguem política
aceita. A ausência atual de endpoint não pode equivaler a retenção indefinida.

## Estado

`analyzed` — bootstrap cria `company` + `company_admin`, mas não existe
delete/deactivate/transfer. Não há política nem path freeze para implementar.

## Risco ou objetivo associado

`R-002`, `R-008`, `R-025`, `F-PRIV-008`, `F-PRIV-016`, G6 e G7.

## Evidência e Impact Analysis preliminar

- `POST /company-admins/bootstrap` cria tenant e primeira conta ADM;
- `company_admin` guarda identidade, contato, hash, perfil/avatar e é owner de
  login/sessão;
- pedidos/códigos/convites guardam referências de reviewer/creator ADM;
- token/device/outbox usam papel + ID e podem sobreviver sem FK;
- offboarding precisa distinguir conta pessoal, autoridade administrativa e
  continuidade do tenant; nunca apaga a empresa por cascade.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` e
  `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluídos;
- `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` e
  `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` concluídos;
- `VR-SEC-004-BOOTSTRAP-TRUST-001`, `VR-SEC-004-EMAIL-NAMESPACE-001`,
  `VR-SES-001-AUTH-EPOCH-001` e `VR-SES-001-REFRESH-ROTATION-001` concluídos;
- `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001`,
  `VR-SES-003-APP-PASSWORD-BASIC-REMOVAL-001` e
  `VR-SES-003-APP-CREDENTIAL-AND-SECURE-STORAGE-001` concluídos;
- `VR-SES-003-EMAIL-CONFIRM-INTENT-001`,
  `VR-BE-005-EMAIL-VERIFICATION-OUTBOX-001`,
  `VR-SES-002-PUSH-TOKEN-SESSION-LIFECYCLE-001` e
  `VR-BE-005-NOTIFICATION-OUTBOX-MINIMIZATION-001` concluídos;
- `VR-SEC-004-INVITE-REDEMPTION-001` concluído, pois o schema final de
  convite/revisão define referências de emissor/reviewer ADM;
- `VR-SEC-002-CORE-ENTITY-PROJECTIONS-001`,
  `VR-SEC-002-SUPPORT-MINIMUM-PROJECTION-001` e
  `VR-BE-004-AVATAR-PRIVACY-LIFECYCLE-001` concluídos;
- `VR-SEC-001-LOCATION-DOMAIN-001` e
  `VR-SEC-003-LOCATION-REALTIME-001` concluídos, pois o offboarding precisa
  encerrar leitura HTTP, conexão e subscriptions GPS já autorizadas ao ADM;
- `VR-APP-001-LOCATION-ADOPTION-001` concluído para limpar marker, cache,
  reconnect e estado local de localização da sessão ADM;
- `VR-OPS-001-SENSITIVE-LOG-REDACTION-001`,
  `VR-OPS-002-BACKUP-RETENTION-RESTORE-PURGE-001`,
  `VR-REL-001-DEMO-MEDIA-DATA-PROVENANCE-001` e
  `VR-PGM-004-FUTURE-DATA-COLLECTION-GATE-001` concluídos.

## Aplicabilidade D01–D34

| Estado | Famílias | Regra |
|---|---|---|
| `APPLICABLE` | D01–D05, D07, D09, D12, D18–D22, D26, D28–D30, D33–D34 | conta/autoridade, revisão/convite, sessão/outbox/suporte/avatar, logs/backups/evidência/mídia/REST precisam de destino explícito |
| `CONDITIONAL / MUST_PROVE` | D06, D08, D10–D11, D13–D17, D25, D27 | dados operacionais não pertencem automaticamente ao ADM e rate-limit usa digest de origem; referências de autoria, ACL, snapshots, provider ou digest só viram `N/A` com prova |
| `NOT_ADMIN_ACCOUNT_DATA / EXPLICIT` | D23–D24, D31 | fila DRV, permissão do SO e segredo operacional não são apagados/rotacionados pelo offboarding ADM |
| `FUTURE_GATED` | D32 | nova coleta/store de ADM entra no lifecycle antes de habilitação |

## Definition of Ready

- [ ] autoridade mínima, último administrador, substituição e break-glass decididos;
- [ ] D01–D34 e todas as referências/store/providers classificados com prova;
- [ ] contrato de estados, auth, idempotência, concorrência e non-enumeration definido;
- [ ] paths/migrations/jobs, consumers, compatibilidade e rollback congelados;
- [ ] testes, smoke same-version, rollout, restore-purge e evidência definidos.

## Critérios de aceite preliminares

- [ ] último ADM não pode abandonar tenant ativo sem decisão de transferência/fechamento;
- [ ] sessões, credenciais, device/outboxes e autoridade são revogados atomicamente;
- [ ] referências de reviewer/creator preservam apenas o mínimo aceito, sem PII órfã;
- [ ] nenhum membro, viagem, suporte ou tenant é apagado por cascade;
- [ ] retry/falha parcial/restore convergem e auditoria AE1 é mínima;
- [ ] nenhuma família é omitida ou `N/A` sem evidência negativa.

## Rollout e rollback

Começar report-only e ativar por estado/feature flag. Divergência bloqueia o
offboarding. Rollback desliga novas solicitações e reconcilia estado; nunca
ressuscita sessão ou remove outro administrador/tenant para simular sucesso.
