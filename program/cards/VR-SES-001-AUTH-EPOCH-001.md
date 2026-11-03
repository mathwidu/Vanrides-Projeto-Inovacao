# VR-SES-001-AUTH-EPOCH-001 — Invalidar sessões por auth epoch monotônico

## Resultado esperado

Access e refresh tokens só admitem uma requisição quando carregam a versão
atual da autoridade do owner. Troca de e-mail, senha, tenant, verificação ou
revogação invalida tokens anteriores sem depender apenas de TTL.

## Estado

`analyzed`

## Risco ou objetivo associado

`R-002`, épico `VR-SES-001` e gate `G1 — TRUSTED`.

## Contexto

O card do namespace exige owner/e-mail exatos no boundary de autenticação,
mas a conta pode mudar depois desse snapshot e antes do fim da operação. O
epoch reduz a janela de sessão stale; não substitui autorização tenant/object
transacional em cada mutação.

Nenhuma versão Flyway futura fica reservada enquanto este card estiver
`analyzed`. No freeze de `ready`, ele revalida o maior número presente/aplicado,
aloca o próximo contínuo, atualiza a allowlist e prova ausência de
collision/out-of-order.

## Impact Analysis preliminar

- `auth_epoch` monotônico pertence ao owner estável `(role,userId)`;
- JWT e refresh persistem o epoch da emissão;
- autenticação rejeita epoch ausente, inválido ou divergente com `401`
  genérico;
- e-mail, senha, tenant/company, verificação e revogação global incrementam o
  epoch no mesmo commit;
- troca de e-mail preserva e incrementa o valor; nunca reinicia em zero;
- hard delete invalida por ausência do owner;
- refresh stale não produz sucessor/família;
- rollout em duas fases aceita tokens legados por no máximo um TTL de access
  token e depois ativa modo estrito.

## Critérios de aceite

- [ ] Epoch é monotônico e dois increments concorrentes não perdem update.
- [ ] Access e refresh novos carregam o epoch emitido.
- [ ] Epoch ausente/malformado/divergente retorna `401` sem revelar estado.
- [ ] Toda mutação de autoridade incrementa no mesmo commit.
- [ ] Troca de e-mail não reinicia epoch e invalida tokens anteriores.
- [ ] Refresh stale não cria nova família.
- [ ] Hard delete torna todos os tokens do owner inutilizáveis.
- [ ] Rollout compatível e corte estrito possuem prazo, métrica e rollback.
- [ ] PostgreSQL cobre increments concorrentes e rolling mode.
- [ ] Smoke aceita token novo e rejeita token anterior na mesma JAR.
- [ ] App trata `401` pelo coordenador de sessão sem loop de refresh.
- [ ] ADR, contrato, runbook, backlog e QA Log ficam sincronizados.

## Dependências

- `VR-SEC-004-EMAIL-NAMESPACE-001` concluído;
- `VR-SES-001-REFRESH-ROTATION-001` concluído;
- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- antes de `ready`, aprovar ADR do rollout de tokens legados sem `authEpoch`.

## Rollback

Durante a fase compatível, voltar a imagem preservando a coluna/claims. Depois
do modo estrito, rollback exige revogação controlada ou compatibilidade
explícita; aceitar silenciosamente token sem epoch reabre `R-002` e bloqueia
promoção.
