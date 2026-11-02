# VR-SES-001-REFRESH-ROTATION-001 — Tornar a rotação de refresh token atômica

## Resultado esperado

Cada refresh token pode gerar no máximo um sucessor. Requisições concorrentes,
retries e falhas parciais não criam duas sessões válidas nem deixam o token
antigo revogado sem o novo token persistido.

## Estado

`analyzed`

## Risco ou objetivo associado

`R-002`, épico `VR-SES-001` e gate `G1 — TRUSTED`.

## Contexto

A rotação atual valida, revoga e cria tokens em passos que não possuem uma
invariável concorrente comprovada. Duas requisições podem observar o mesmo token
ativo antes de qualquer uma persistir a revogação.

## Impact Analysis preliminar

### Mudanças técnicas

- consumir o token por compare-and-set, update condicional ou lock pessimista
  dentro da mesma transação que persiste o sucessor;
- representar `family_id`, `rotated_from_id UNIQUE`, `revoked_at/reason` e
  predecessor/sucessor de modo auditável, sem armazenar token bruto;
- garantir constraint para no máximo um sucessor por predecessor;
- revogar família diante de replay confirmado conforme política documentada;
- tornar falha antes do commit totalmente reversível;
- cobrir concorrência real no PostgreSQL e isolamento suportado em produção.

### Contratos afetados

- rota e payload de refresh permanecem compatíveis;
- apenas uma de duas rotações concorrentes recebe sucesso; a outra recebe erro
  genérico de token inválido/reutilizado sem revelar estado interno;
- retries de transporte não podem criar sucessores adicionais.

### Riscos e mitigação

- lock causar contenção: índice por hash e transação mínima;
- política de replay encerrar sessão legítima: documentar e testar retry versus
  reuso malicioso;
- migration com famílias incompletas: preflight e backfill determinístico.

## Critérios de aceite

- [ ] Duas rotações concorrentes do mesmo token produzem `1x200`, `1x401`
  genérico e exatamente um sucessor válido.
- [ ] Falha transacional preserva o token antigo e não cria sucessor órfão.
- [ ] Replay não amplia a família e segue política de revogação documentada.
- [ ] Banco impede dois sucessores para o mesmo predecessor.
- [ ] Tokens brutos não aparecem em banco, logs, métricas ou evidências.
- [ ] Unitários, WebMvc, PostgreSQL concorrente e smoke passam no mesmo SHA.
- [ ] Contrato, `README`, `DEPLOY`, `VPS_RUNBOOK`, backlog e QA Log ficam
  sincronizados para rotação, replay e rollback de sessão.

## Dependências

- `VR-SEC-001-BE-READ` concluído;
- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- número Flyway não reservado enquanto `analyzed`; no freeze de `ready`,
  revalidar o maior número presente/aplicado, alocar o próximo contínuo,
  atualizar a allowlist e provar ausência de collision/out-of-order;
- fechar política de replay/família antes de mover para `ready`.

## Freeze PRIV-002

O inventário pai está congelado em `a3f7140e0a25fe5c440700146a42401500a87e4a`
(SHA-256 `d1dc70c65a0d03ff23950c55c6433378b4294a31c896fd095764dfe1d4be9891`).
Este card é owner backend da rotação/revogação de D03/F-PRIV-001;
coordenador, senha/Basic e storage Flutter pertencem aos seus slices. PRIV-002
só congela ownership/Impact Analysis e não abre novos paths de produto.

## Rollback

Rollback deve revogar famílias emitidas pelo formato novo ou manter leitura
compatível. Voltar à rotação read-check-write reabre `R-002` e bloqueia promoção.
