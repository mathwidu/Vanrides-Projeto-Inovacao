# VR-APP-002-OFFLINE-QUEUE-SESSION-BOUNDARY-001 — Isolar fila offline por sessão e tenant

## Resultado esperado

A fila de ações de navegação pertence explicitamente a uma conta, empresa e
viagem; não é lida nem sincronizada por outra sessão, expira por política e é
eliminada no logout, revogação ou troca de vínculo incompatível.

## Estado

`analyzed` — finding `F-PRIV-002` validado; contrato de owner, TTL, migração e
idempotência remota ainda não foi congelado.

## Dono operacional

`APP`, com `SES`, `SEC`, `BE`, `PRIV` e `QA` como revisores.

## Risco ou objetivo associado

`F-PRIV-002`, `R-007`, `VR-APP-002`, G1 e G6.

## Impact Analysis preliminar

### Mudanças técnicas

- versionar envelope com `userId`, `companyId`, `tripId`, criação e expiração;
- consumir o subject/generation central da sessão, sem implementar logout local;
- particionar chave/índice e validar owner antes de exibir ou sincronizar;
- migrar fila v1 apenas quando a posse puder ser provada; caso contrário apagar;
- remover nome da parada/passageiro persistido e derivá-lo do snapshot autorizado;
- limpar no logout/revogação/troca de tenant e limitar tamanho/retries;
- alinhar idempotência, conflito e tombstone com o endpoint backend.

### Contratos afetados

Persistência local, sessão/logout, UX de retry e comandos operacionais de
parada. O wire backend só muda se a idempotência atual for insuficiente.

### Riscos e mitigação

Descartar ação válida ou reaplicar ação antiga. Falhar fechado para fila sem
owner, usar IDs idempotentes, relógio injetável e testes de troca de conta.

### Validação mínima

Testes de serialização/migração/TTL, matriz duas contas/dois tenants, retry
offline/online, logout/revogação, gate app e smoke de dispositivo sem PII real.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001` concluído;
- contrato de sessão/logout do app congelado;
- semântica backend de idempotência das ações confirmada.

## Escopo

- fila de navegação DRV, lifecycle local, UX de descarte/retry e sync seguro.

## Fora de escopo

- ampliar ações offline, persistir nomes adicionais ou alterar regra de viagem.

## Definition of Ready

- [ ] owner/tenant/TTL/idempotência e formato de migração definidos;
- [ ] consumidores, dirty paths, testes, smoke e rollback congelados;
- [ ] paths não sobrepõem push, storage ou implementação do coordenador;
- [ ] dependências concluídas sem dados reais.

## Critérios de aceite preliminares

- [ ] conta/tenant B nunca lê, exibe ou envia fila criada por A;
- [ ] fila sem owner, expirada, malformada ou de vínculo antigo falha fechado;
- [ ] logout/revogação removem a fila antes de trocar a sessão;
- [ ] retry não duplica ação confirmada e conflito tem estado explícito;
- [ ] upgrade da v1 é provado com casos migrável e não migrável.
- [ ] v1 sem owner é descartada e o payload v2 não persiste nome de passageiro.

## Rollback

Desligar enqueue/sync offline e operar somente online. Não reativar a chave
global v1 nem reaproveitar fila cuja posse não possa ser provada.
