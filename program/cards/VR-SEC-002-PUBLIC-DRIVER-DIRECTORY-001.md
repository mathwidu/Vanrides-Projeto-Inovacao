# VR-SEC-002-PUBLIC-DRIVER-DIRECTORY-001 — Fechar diretório público de motoristas

## Resultado esperado

`GET /motoristas` deixa de publicar ID/nome de motoristas anonimamente por
default. Qualquer diretório que sobreviva possui consumidor, finalidade,
audiência, projeção, anti-enumeração e compatibilidade explicitamente aprovados
e testados.

## Estado

`analyzed` — finding `F-PRIV-015` validado; não há consumidor GET observado no
Flutter e a decisão remover versus substituir ainda precisa de freeze.

## Dono operacional

`SEC/BE`, com `APP`, `Produto`, `PRIV` e `QA` como revisores.

## Risco ou objetivo associado

`F-PRIV-015`, `R-001`, `R-007`, `VR-SEC-002`, G1 e G6.

## Impact Analysis preliminar

### Mudanças técnicas

- remover a exceção anônima do filtro ou substituir por fluxo autorizado;
- inventariar consumidores externos/legados antes de mudar status/payload;
- aplicar projeção mínima e non-enumeration ao contrato necessário;
- atualizar testes, matriz de API, app e rollout de compatibilidade.

### Contratos afetados

`GET/HEAD /motoristas`, autenticação, status, payload `{id,nome}`, CORS/cache e
qualquer bootstrap que ainda dependa do diretório.

### Riscos e mitigação

Cliente legado pode quebrar ou exceção alternativa reabrir exposição. Fazer
busca de consumidores, contrato versionado, default deny e testes negativos.

### Validação mínima

WebMvc por papel/tenant/anônimo, non-enumeration byte a byte quando aplicável,
busca de consumidor, gate backend/app e smoke HTTP no mesmo JAR.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- Produto decide se existe jornada legítima e sua audiência mínima;
- matriz de compatibilidade identifica consumidores ou prova sua ausência.

## Escopo

- exceção do filtro, endpoint de lista, DTO mínimo, docs e compatibilidade.

## Fora de escopo

- ampliar perfil público, publicar diretório pesquisável ou promover runtime.

## Definition of Ready

- [ ] remover/substituir, público mínimo, status e transição definidos;
- [ ] consumidores, paths, testes, smoke, rollout e rollback congelados;
- [ ] decisão humana não é inferida da existência do endpoint.

## Critérios de aceite preliminares

- [ ] anônimo e papel sem necessidade recebem resposta fail-closed uniforme;
- [ ] nenhuma lista/ID/nome é entregue antes da autorização apropriada;
- [ ] consumidor legítimo, se houver, usa projeção mínima e tenant correto;
- [ ] HEAD, CORS, cache e endpoints alternativos não contornam a política;
- [ ] compatibilidade e remoção da exceção passam no mesmo SHA/JAR.

## Rollback

Desabilitar a jornada dependente e manter o endpoint fechado. Nunca restaurar
acesso anônimo amplo para recuperar compatibilidade.
