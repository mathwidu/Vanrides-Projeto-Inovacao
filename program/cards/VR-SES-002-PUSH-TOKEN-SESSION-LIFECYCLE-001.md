# VR-SES-002-PUSH-TOKEN-SESSION-LIFECYCLE-001 — Vincular push token ao lifecycle da sessão

## Resultado esperado

Device token é registrado, renovado, invalidado e desvinculado de forma atômica
com a sessão/conta atual; logout, revogação, troca de conta e refresh do SO não
permitem entrega à conta anterior em aparelho compartilhado.

## Estado

`analyzed` — finding latente `F-PRIV-013`; provider real permanece bloqueado
até contrato, migração e provas deste card estarem concluídos.

## Dono operacional

`SES`, com `APP`, `BE`, `SEC`, `PRIV`, `OPS` e `QA` como revisores.

## Risco ou objetivo associado

`F-PRIV-013`, `R-002`, `R-007`, `VR-SES-002`, G1 e G6.

## Impact Analysis preliminar

### Mudanças técnicas

- assinar stream de refresh do token e registrar somente sob sessão válida;
- consumir o `SessionLifecyclePort` e a generation monotônica do coordenador,
  sem implementar logout/troca de usuário em push;
- criar endpoint/semântica idempotente de unregister e invalidar no logout;
- resolver troca de conta, reinstall, múltiplos devices e corrida login/logout;
- reagir aos entrypoints centralizados de teardown sem lifecycle paralelo;
- minimizar identidade armazenada e impedir token bruto em logs/evidências;
- tornar qualquer adapter/provider real dependente deste gate.

### Contratos afetados

Gateway de push Flutter, sessão/logout, endpoints de device token, schema/outbox
e composição runtime do provider.

### Riscos e mitigação

Retry pode reativar token antigo ou logout offline deixar vínculo ativo. Usar
versão/owner monotônicos, operações idempotentes, tombstone e retry limitado.

### Validação mínima

Testes de concorrência refresh/logout, duas contas no mesmo device, token
rotacionado, offline/retry, gate app/backend e smoke com provider fake no mesmo
build/JAR; nenhum push externo real.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-BE-RUNTIME-PUSH-001` concluído e runtime ainda em noop;
- `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001` concluído;
- `VR-SES-003-APP-CREDENTIAL-AND-SECURE-STORAGE-001` concluído para bearer e
  refresh vigentes em storage protegido;
- contrato de sessão/revogação e identidade de device congelado;
- provider real e card runtime declaram dependência explícita deste card.

## Escopo

- registro/refresh/unregister, vínculo conta-device e bloqueio de ativação.

## Fora de escopo

- conteúdo/UX final de notificações, credencial FCM/APNs real ou deploy.

## Definition of Ready

- [ ] subject/generation, instalação, versão/idempotência e política offline definidos;
- [ ] schema/API/compatibilidade, paths, testes, smoke e rollback congelados;
- [ ] provider fake e fixtures sintéticas disponíveis.
- [ ] paths de push não sobrepõem ownership do coordenador ou do storage.

## Critérios de aceite preliminares

- [ ] refresh do SO invalida vínculo anterior antes de usar o novo token;
- [ ] logout/revogação/troca de conta impedem entrega à sessão anterior;
- [ ] retry concorrente não ressuscita associação obsoleta;
- [ ] token bruto não aparece em log, métrica, erro ou evidência;
- [ ] provider real falha fechado enquanto este card não estiver `done`.

## Rollback

Desabilitar push no runtime e invalidar associações afetadas. Nunca manter
provider ativo sem unregister/refresh confiáveis para preservar entrega.
