# VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001 — Centralizar o lifecycle de sessão do app

## Resultado esperado

O app possui um único contrato `SessionLifecyclePort` para login, restore,
refresh de identidade, troca de conta, revogação e logout. Cada transição
carrega somente subject/tenant/role/generation internos e notifica consumidores
registrados em ordem definida, sem senha, token, e-mail ou payload de produto.

## Estado

`analyzed` — dependency inversion identificada em `F-PRIV-001/002/013`;
consumidores, paths, ordering, compatibilidade e testes ainda precisam ser
congelados antes de `ready`.

## Dono operacional

`SES/APP`, com `SEC`, `PRIV`, `QA` e owners de push/storage/fila como revisores.

## Risco ou objetivo associado

`F-PRIV-001`, `F-PRIV-002`, `F-PRIV-013`, `R-002`, `VR-SES-002` e G1.

## Impact Analysis

### Mudanças técnicas

- definir subject, tenant, role, session generation e lifecycle event version;
- emitir login/restore/account-switch/revoke/logout exatamente uma vez;
- registrar hooks fail-closed de push, storage, fila offline e deep links;
- ordenar limpeza antes de ativar nova conta e impedir callback reentrante;
- manter evento em memória, sem payload/segredo, e zerar hooks no shutdown.

### Contratos afetados

Bootstrap/sessão Flutter, DI, push binder, secure storage, fila offline, deep
links e qualquer cache vinculado ao titular. Nenhum contrato HTTP muda aqui.

### Ownership por seam

| Superfície | Owner exclusivo |
|---|---|
| `SessionLifecyclePort`, evento, generation, state machine e ordering | session coordinator |
| registro/ordenação de hooks e composition root | session coordinator; consumers registram adapters próprios |
| `AppSessionUser.password`, `senha`, `session.password` e Basic | password/Basic removal |
| access/refresh vault, envelope e migração de token | credential/secure storage |
| queue envelope, owner, tenant e TTL | offline-queue boundary |
| push binder/gateway e `/devices/fcm-token` | push-token lifecycle |
| pending invite, parser/resolver, URI/clipboard/storage | verified deep-link lifecycle |
| família e rotação backend | refresh rotation |

O coordinator não persiste senha, token, convite, push token ou payload de
fila. Consumers não implementam logout/account switch, não alteram a state
machine e registram mudanças no composition root sequencialmente no manifest
do card.

### Riscos e mitigação

Evento duplicado, ordem errada ou callback falho pode manter dado da conta
anterior. Usar generation monotônica, estado explícito, hooks idempotentes,
timeout/circuit local e falha fechada antes de ativar nova sessão.

### Validação mínima

Unit tests de state machine/ordering/reentrancy; integração login-restore-
switch-revoke-logout com consumers sintéticos; ausência de material sensível;
gate app e smoke same-build em upgrade/troca de conta.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- inventário de todos os consumidores e estado de sessão congelado.

## Escopo

- lifecycle local de sessão e contrato de hooks; nenhuma implementação de push,
  refresh, storage ou fila dentro do coordenador.

## Fora de escopo

- guardar token/senha, chamar provider, alterar API/backend, dado real ou deploy.

## Definition of Ready

- [ ] paths/consumidores e owners congelados sem sobreposição;
- [ ] state machine, ordering, generation, timeout e falha definidos;
- [ ] compatibilidade/migração e rollback definidos;
- [ ] testes, smoke, evidência e dirty paths exatos definidos.

## Critérios de aceite

- [ ] login/restore/switch/revoke/logout emitem uma transição versionada única;
- [ ] nova conta só ativa depois da limpeza idempotente da anterior;
- [ ] push, storage, fila e deep link consomem o mesmo port, sem depender entre si;
- [ ] evento não contém senha, token, e-mail, GPS, nome ou payload;
- [ ] falha de hook bloqueia ou degrada de modo explícito, nunca silencioso;
- [ ] upgrade/troca de papel/tenant passam no mesmo build validado.

## Rollback

Manter adapter de compatibilidade somente para chamar o novo port a partir do
entrypoint antigo. Rollback não restaura callbacks fragmentados capazes de
deixar dados da conta anterior; pode exigir logout controlado.
