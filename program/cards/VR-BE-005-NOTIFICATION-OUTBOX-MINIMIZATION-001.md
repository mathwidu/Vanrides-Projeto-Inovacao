# VR-BE-005-NOTIFICATION-OUTBOX-MINIMIZATION-001 — Minimizar conteúdo e lifecycle da outbox de notificações

## Resultado esperado

Outbox persiste somente recipient interno, type/template, variáveis allowlisted,
estado/tentativas e timestamps necessários. Título, corpo e `data_json` não
carregam PII/token/GPS livre; TTL/purge, acesso operacional e contrato do
provider ficam explícitos antes de push real.

## Estado

`analyzed` — gap D19/F-PRIV-013; consumidores, templates, campos, provider e
retenção ainda precisam de freeze.

## Dono operacional

`BE/SES`, com `APP`, `PRIV`, `SEC`, `OPS` e `QA`.

## Risco ou objetivo associado

`F-PRIV-013`, `R-002`, `R-007`, `VR-BE-005`, G5 e G6.

## Impact Analysis

### Mudanças técnicas

- inventariar notification types, templates, variables e consumidores app;
- substituir texto/JSON livre por schema allowlisted e versionado;
- vincular recipient por referência interna, nunca e-mail/token bruto;
- definir TTL/purge, retry/idempotência e dead-letter sanitizada;
- manter provider noop; D19 pode ser minimizada independentemente de D18.

### Contratos afetados

Outbox/tabelas, dispatcher, push payload e parser/navegação do app.

### Riscos e mitigação

Minimização pode quebrar deep link/notificação; retry pode reter conteúdo. Usar
template versionado, compatibility fixtures, payload size/schema gate e purge
idempotente.

### Validação mínima

Migration/preflight, contract tests template↔app, casos de campo proibido,
retry/dead-letter/TTL/purge, gate backend/app e smoke provider noop.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` concluído com decisão
  aplicável à outbox aceita.
- `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` concluído com decisão aplicável a
  D19, inclusive a alternativa explícita de não aplicar hold.

## Ownership D18/D19

D18/bind-unbind pertence a
`VR-SES-002-PUSH-TOKEN-SESSION-LIFECYCLE-001`; D19/schema-template-recipient-
retry-TTL-purge pertence a este card. Nenhum depende do outro para a
implementação local. Qualquer ativação de provider real permanece noop e
depende conjuntamente de ambos pelo card
`VR-BE-005-PUSH-PROVIDER-ACTIVATION-001`.

## Escopo

- D19: schema/conteúdo/outbox/retry/TTL e contrato app.

## Fora de escopo

- UX final, marketing, provider real, dado remoto ou deploy.

## Definition of Ready

- [ ] types/templates/consumidores/campos e paths congelados;
- [ ] migration, TTL/purge/retry/dead-letter e compatibilidade definidos;
- [ ] testes/smoke/evidência/rollout/rollback definidos;
- [ ] provider e decisão humana permanecem bloqueados quando pendentes.

## Critérios de aceite

- [ ] zero texto/JSON livre ou classe proibida sem schema/consumer explícito;
- [ ] outbox não armazena token, e-mail, GPS, endereço ou URI secreta;
- [ ] TTL/purge/retry/dead-letter são idempotentes e auditáveis sem conteúdo;
- [ ] parser app rejeita versão/campo desconhecido com fallback seguro;
- [ ] provider noop e lifecycle de sessão permanecem fail-closed;
- [ ] gate e smoke usam a mesma versão backend/app.

## Rollback

Preservar leitura temporária de schema anterior por adapter versionado e parar
dispatch novo; nunca restaurar campo livre como fallback. Migration destrutiva
só após janela/decisão e prova de compatibilidade.
