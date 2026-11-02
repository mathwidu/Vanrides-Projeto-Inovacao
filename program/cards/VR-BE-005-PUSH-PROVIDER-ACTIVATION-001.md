# VR-BE-005-PUSH-PROVIDER-ACTIVATION-001 — Ativar provider real de push com egress mínimo

## Resultado esperado

Um provider real de push só sai do modo `none/noop` quando D18 possui
bind/unbind seguro, D19 possui template/outbox minimizados e provider,
credencial, audiência, opt-in/out aplicável, retenção, observabilidade, kill
switch e rollback estão aceitos e provados na mesma versão.

## Estado

`analyzed` — nenhum provider, credencial ou envio real está autorizado; todos os
product paths permanecem fechados.

## Dono operacional

`BE/SES/OPS`, com `APP`, `SEC`, `PRIV`, `Produto`, `Jurídico/DPO` e `QA`.

## Risco ou objetivo associado

`F-PRIV-013`, `R-002`, `R-007`, `R-009`, `R-012`, `VR-BE-005`, G5 e G6.

## Impact Analysis preliminar

### Mudanças técnicas

- selecionar adapter FCM/APNs por configuração fail-closed e identidade exata;
- usar credencial por ambiente/workload/provider, sem valor em docs/evidência;
- materializar template D19 no boundary e resolver device token D18 somente no
  envio, sem duplicá-lo na outbox;
- implementar retry/idempotência/resultado incerto, rate limit e dead-letter
  sanitizados;
- respeitar unregister/revogação/troca de conta antes de cada claim;
- adicionar canary sintético, métricas agregadas, kill switch e rollout gradual.

### Contratos afetados

`PushDeliveryGateway`, configuração runtime, D18/D19, payload app, provider
externo, secret store e runbooks. Nenhuma mudança fica autorizada neste card
enquanto `analyzed`.

### Riscos e mitigação

Entrega à conta anterior, payload excessivo, credential leak, retry duplicado ou
downgrade silencioso. Exigir join D18+D19, schemas fechados, provider identity,
dual-key/revoke, canary sintético e kill switch para `none`.

### Validação mínima

Fixtures/provider fake; concorrência logout/refresh/claim; campos proibidos;
config ausente/incorreta; canary sintético sem PII; gates backend/app, smoke
same-version e prova de rollback para noop. Envio externo real exige autorização
separada.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-PGM-004-FUTURE-DATA-COLLECTION-GATE-001` concluído;
- `VR-BE-RUNTIME-PUSH-001` concluído;
- `VR-SES-002-PUSH-TOKEN-SESSION-LIFECYCLE-001` concluído;
- `VR-BE-005-NOTIFICATION-OUTBOX-MINIMIZATION-001` concluído;
- `VR-SEC-005-SECRET-SCOPE-ROTATION-001` concluído;
- `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` concluído;
- `VR-PRIV-003-TENANT-OFFBOARDING-001` concluído, com hooks fail-closed para
  permitir claim/entrega somente em `ACTIVE`, drenar em `CLOSING` e impedir
  qualquer novo claim/egress em `CLOSED`;
- `VR-UX-002-MOBILE-PRIVACY-PERMISSIONS-001` concluído.

## Escopo

- provider real, egress, credencial, delivery semantics, observabilidade,
  ativação e kill switch após decisões/autorizações próprias.

## Fora de escopo

- marketing, mensagem livre, analytics, credencial real neste preparo, envio
  externo, deploy, piloto ou produção.

## Definition of Ready

- [ ] provider/ambientes/owners/decisões e credencial metadata congelados;
- [ ] D18 e D19 concluídos com manifest compatível;
- [ ] payload, retry, idempotência, SLA, opt-in/out e purge definidos;
- [ ] paths, provider fake, canary, smokes, rollout, rollback e evidência definidos;
- [ ] autorização separada existe para qualquer credencial ou envio externo.

## Critérios de aceite preliminares

- [ ] runtime falha fechado para provider ausente/incompatível/múltiplo;
- [ ] token D18 nunca é persistido na outbox D19;
- [ ] logout/revogação/troca de conta impedem claim/entrega obsoleta;
- [ ] tenant `ACTIVE/CLOSING/CLOSED` governa claim, drain e zero entrega após
  fechamento, inclusive em retry, restore e resultado incerto;
- [ ] payload/template não contém PII/GPS/segredo fora da allowlist aceita;
- [ ] retry/idempotência e resultado incerto não duplicam evento lógico;
- [ ] canary, métricas e logs permanecem sintéticos/sanitizados;
- [ ] kill switch retorna a `none/noop` sem perder a capacidade de reconciliação.

## Rollout

Provider fake e shadow sem envio; canary sintético autorizado; percentual por
ambiente/audiência; ativação total somente após sinais estáveis e decisão de
promoção. Qualquer divergência executa kill switch para noop.

## Rollback

Selecionar `none`, parar claims e preservar eventos para reconciliação conforme
retenção aceita. Revogar credencial somente com autorização e nunca reativar
token/session stale.
