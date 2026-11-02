# VR-BE-005-EMAIL-VERIFICATION-OUTBOX-001 — Persistir e entregar verificação de e-mail por outbox

## Resultado esperado

Criação, troca e reenvio de e-mail registram trabalho durável na mesma
transação da mutação. Nenhum envio ocorre antes do commit; restart, retry e
múltiplas réplicas não perdem o trabalho nem expõem o segredo do link.

## Estado

`analyzed`

## Risco ou objetivo associado

`R-009`, épico `VR-BE-005` e gates `G1 — TRUSTED` e `G5 — OPERABLE`.

## Contexto

O boundary atual responde `202` de forma opaca e limita memória, mas usa fila
local. `EmailVerificationService` persiste o token e chama o provider dentro da
transação chamadora. Um crash, rollback posterior ou segunda réplica pode
perder, duplicar ou antecipar a entrega.

Nenhuma versão Flyway futura fica reservada enquanto este card estiver
`analyzed`. No freeze de `ready`, ele revalida o maior número presente/aplicado,
aloca o próximo contínuo, atualiza a allowlist e prova ausência de
collision/out-of-order.

## Impact Analysis preliminar

- persistir token e evento outbox na mesma transação da conta;
- cifrar o material necessário para montar o link; banco, logs e evidências
  nunca armazenam token bruto utilizável;
- fazer claim curto e recuperável por `FOR UPDATE SKIP LOCKED`/lease;
- coordenar cooldown e coalescing no PostgreSQL, não na memória da réplica;
- retry com backoff, limite, dead-letter e métricas sanitizadas;
- manter semântica `at-least-once`; usar idempotency key estável quando o
  provider suportar;
- revogar links anteriores somente depois de entrega confirmada; falha preserva
  o link anterior;
- manter `POST /auth/email-verifications/resend` opaco para JSON
  desserializável, inclusive conta ausente ou fila saturada.

## Critérios de aceite

- [ ] Token e evento entram no mesmo commit da mutação que os originou.
- [ ] Nenhum gateway externo é chamado antes do commit.
- [ ] Crash antes do commit não envia; crash depois do commit preserva trabalho.
- [ ] Duas réplicas não processam simultaneamente o mesmo lease.
- [ ] Lease expirado é recuperado sem criar outro evento lógico.
- [ ] Retry possui backoff, teto, dead-letter e observabilidade sem PII.
- [ ] Cooldown/coalescing sobrevive a restart e funciona entre réplicas.
- [ ] Segredo fica cifrado em repouso e nunca aparece em log, erro ou evidência.
- [ ] Sucesso invalida links anteriores; falha preserva os anteriores.
- [ ] Teste PostgreSQL cobre duas réplicas e crash antes/depois do envio.
- [ ] Smoke da mesma JAR prova persistência, retomada e contrato `202` opaco.
- [ ] Runbook, ADR, contrato, backlog e QA Log ficam sincronizados.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` concluído com D04/D26;
- `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` concluído com decisão aplicável a
  D04/D26, inclusive a alternativa explícita de não aplicar hold;
- `VR-SEC-005-SECRET-SCOPE-ROTATION-001` concluído para proteção do material;
- antes de `ready`, aprovar ADR para proteção do segredo, retenção,
  idempotência e resultado incerto do provider.

## Validação mínima

- unitários do payload cifrado, claim, lease, retry e dead-letter;
- WebMvc do resend opaco;
- PostgreSQL real com concorrência, restart e relógio controlado;
- gate backend limpo, smoke same-JAR e programa/checkpoint coordenador.

## Rollback

Desabilitar workers preservando tabela/eventos e impedir novas emissões. Não
voltar ao envio antes do commit. Uma correção de schema ocorre por migration
forward-only; rollback inseguro reabre `R-009` e mantém promoção em `HOLD`.
