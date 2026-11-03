# VR-PGM-004-FUTURE-DATA-COLLECTION-GATE-001 — Bloquear nova coleta sem decisão e card

## Resultado esperado

O programa falha fechado quando analytics, crash reporting externo, exportação,
telemetria de campo, offline packs ou nova categoria/provider são adicionados
sem Data Impact Analysis, owner, minimização, decisão humana aplicável, card,
evidência e rollout/rollback. D32 permanece `NOT_APPLICABLE_CURRENT`.

## Estado

`analyzed` — gap D32; schema do manifest, superfícies detectáveis e política de
waiver ainda precisam ser congelados.

## Dono operacional

`PGM/PRIV`, com `SEC`, `APP`, `BE`, `MAP`, `OPS`, `QA` e `Produto`.

## Risco ou objetivo associado

`R-007`, `R-010`, `VR-PGM-004`, G0 e G6.

## Impact Analysis

### Mudanças técnicas

- definir manifest/versionamento para nova categoria, SDK, endpoint e provider;
- detectar dependências/configs/permissions/export/telemetry conhecidas;
- exigir D-ID proposto, titular, fields, consumers, stores/copies e owner;
- exigir decisão/ADR/card antes de feature flag/build habilitar coleta;
- testar ausência/malformação, bypass, rename e waiver expirado.

### Contratos afetados

Governança/CI e futuros manifests de app/backend/Maps. Não instrumenta produto.

### Riscos e mitigação

Heurística pode falso-positivo ou SDK ocultar coleta. Combinar manifest
declarativo, scanners múltiplos, review de lockfiles/config/permissões e waiver
humano versionado/expirável.

### Validação mínima

Unit fixtures positivas/negativas, rename/obfuscation conhecida, manifest
missing/malformed, waiver/expiry, program gate e CI em checkout limpo.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `GOV-PIPELINE-001` concluído;
- schema/owners/waiver e superfícies iniciais definidos.

## Escopo

- guard documental/automatizado antes de nova coleta D32.

## Fora de escopo

- instalar SDK, coletar/exportar dado, definir base/consentimento ou deploy.

## Definition of Ready

- [ ] schema, detecções, owners, waiver/expiry e paths congelados;
- [ ] integração program/CI, compatibilidade e rollback definidos;
- [ ] fixtures/testes/evidência/dirty paths exatos definidos;
- [ ] nenhuma coleta real necessária para validar.

## Critérios de aceite

- [ ] nova categoria/SDK/provider/config falha sem manifest e card;
- [ ] manifest exige titular, fields, consumers, stores/copies e owner;
- [ ] decisão humana aplicável permanece separada e verificável;
- [ ] waiver possui autoridade, escopo, justificativa, digest e expiração;
- [ ] testes positivos/negativos impedem bypass conhecido;
- [ ] D32 continua N/A no corte até entrega aprovada e comprovada.

## Rollback

Reverter o gate somente com waiver explícito e temporário; rollback não ativa
coleta. Falso-positivo recebe regra estreita/testada, nunca exclusão ampla do
repositório ou provider.
