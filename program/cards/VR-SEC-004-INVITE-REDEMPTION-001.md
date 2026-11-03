# VR-SEC-004-INVITE-REDEMPTION-001 — Fortalecer e consumir convites atomicamente

## Resultado esperado

Convites de passageiro e motorista são bearers imprevisíveis, expirados e de
uso único. Tentativas concorrentes ou brute force não produzem dois vínculos,
não excedem limites e não revelam empresa, pessoa ou validade do convite.

## Estado

`analyzed`

## Risco ou objetivo associado

`R-018`, épicos `VR-SEC-004`/`VR-SEC-005` e gate `G1 — TRUSTED`.

## Contexto

O código curto de passageiro possui cerca de 20 bits (`32^4`) e a validação de
limite/uso ainda depende de read-check-write. O convite de motorista também
precisa comprovar consumo único sob concorrência real, não apenas no fluxo
sequencial da aplicação.

## Impact Analysis preliminar

### Mudanças técnicas

- convergir ambos os convites para CSPRNG de pelo menos 128 bits;
- persistir somente SHA-256/HMAC, expiração, finalidade, emissor e estado de
  uso; o segredo bruto aparece apenas na criação/regeneração;
- consumir convite e criar vínculo/usage na mesma transação com constraint ou
  update condicional;
- impor limites de emissão, tentativas e usos no banco e no rate limiter;
- invalidar/revogar sem deixar janela de replay;
- sanitizar logs, erros, telemetria e artefatos de QA.

### Contratos afetados

- preview/redemption mantêm resposta genérica para inválido, expirado,
  revogado, usado ou acima do limite;
- a migration alocada no freeze de `ready` migra/encerra códigos legados com
  cutoff curto e remove plaintext após a janela; list/detail posteriores
  retornam apenas máscara ou `null`;
- nenhuma resposta pública confirma empresa ou passageiro antes da prova válida.

### Riscos e mitigação

- quebrar links/códigos emitidos: janela curta de compatibilidade com limite e
  data final documentados;
- segredo vazar em URL/log: preferir fragment/POST e redaction obrigatória;
- contenção na redemption: chave/índice único e transação pequena;
- enumeração distribuída: depender do rate limit confiável e observar métricas
  sem armazenar segredo/IP bruto.

## Critérios de aceite

- [ ] Convite novo possui CSPRNG e entropia documentada de pelo menos 128 bits.
- [ ] Banco armazena somente derivação não reversível do segredo.
- [ ] Duas redemptions concorrentes com `maxUses=1` geram exatamente um vínculo/uso;
  o retry do vencedor é idempotente e não consome uso adicional.
- [ ] Limites de uso/emissão não podem ser ultrapassados por corrida.
- [ ] Respostas não enumeram estado, pessoa ou tenant.
- [ ] Rate limit cobre preview e redemption na topologia confiável.
- [ ] Unitários, WebMvc, PostgreSQL concorrente, carga controlada e smoke passam no mesmo SHA.
- [ ] Contratos, expiração legada, backlog e QA Log ficam sincronizados.

## Dependências

- `VR-SEC-005-RATE-LIMIT-TRUST-001` concluído;
- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- número Flyway não reservado enquanto `analyzed`; no freeze de `ready`,
  revalidar o maior número presente/aplicado, alocar o próximo contínuo,
  atualizar a allowlist e provar ausência de collision/out-of-order;
- fechar formato e compatibilidade dos convites antes de mover para `ready`.

## Freeze PRIV-002

O inventário pai está congelado em `a3f7140e0a25fe5c440700146a42401500a87e4a`
(SHA-256 `d1dc70c65a0d03ff23950c55c6433378b4294a31c896fd095764dfe1d4be9891`).
Este card é owner de entropia, hash, consumo atômico e contrato server-side de
D09/F-PRIV-006. Associação de plataforma, clipboard, storage e UX ficam no
deep-link lifecycle. PRIV-002 não abre path nem aceita prazo humano.

## Rollback

Revogar convites do formato novo/legado afetado e desabilitar redemption até
restaurar consumo atômico. Rollback inseguro reabre `R-018`.
