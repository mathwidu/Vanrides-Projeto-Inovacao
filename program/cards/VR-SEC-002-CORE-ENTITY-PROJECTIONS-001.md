# VR-SEC-002-CORE-ENTITY-PROJECTIONS-001 — Substituir entidades JPA nas projeções REST centrais

## Resultado esperado

Endpoints autenticados centrais de motorista, passageiro e presença usam DTOs
allowlisted por papel/tenant/objeto e nunca serializam entidades JPA ou relações
aninhadas. Rotas públicas, GUA, payload DRV→PAX e localização permanecem sob
slices proprietários separados.

## Estado

`analyzed` — slice de `F-PRIV-016`; ownership por endpoint, consumidores,
compatibilidade e paths ainda precisam ser congelados.

## Dono operacional

`SEC/BE`, com `APP`, `PRIV`, `QA` e owners dos slices vizinhos.

## Risco ou objetivo associado

`F-PRIV-016`, `R-001`, `R-007`, `VR-SEC-002`, G1 e G6.

## Impact Analysis

### Mudanças técnicas

- mapear endpoints autenticados de `MotoristaController`,
  `PassageiroController` e `PresencaController` a DTOs explícitos;
- validar papel, tenant e objeto antes do mapping;
- impedir relações lazy/aninhadas, contato/endereço/GPS não allowlisted;
- versionar compatibilidade de request/response e erro sem enumeração;
- adicionar ratchet que recusa entidade JPA no boundary desses owners.

### Contratos afetados

REST backend e parsers/telas app desses endpoints. `GET /motoristas` anônimo é
do card de diretório; GUA/DRV→PAX/localização têm owners próprios.

A tabela preliminar de ownership é produzida por
`VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` em
`program/privacy/MINIMIZATION_CONSENT_AUDIT_MATRIX.md`. O card REST terminal
valida e impede regressão; ele não é predecessor da implementação vertical.

### Riscos e mitigação

DTO pode quebrar cliente ou deixar campo equivalente escapar por nesting. Usar
ownership table, contract fixtures por papel, diff de schema e rollout por
endpoint sem dois cards editarem o mesmo handler.

### Validação mínima

Contract tests positivos/negativos por papel/tenant, non-enumeration, busca por
retorno/argumento JPA, gate backend/app e smoke same-version dos consumidores.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-SEC-002-GUARDIAN-PASSENGER-MINIMUM-PROJECTION-001` concluído, pois
  compartilha methods de passageiro e seu contrato precisa ser preservado;
- tabela de endpoint→owner sem sobreposição congelada.

## Escopo

- endpoints autenticados centrais de DRV/PAX/presença definidos no freeze.

## Fora de escopo

- rota pública, GUA, DRV→PAX, GPS/realtime, suporte, deletes PAX/DRV,
  upload/download/lifecycle físico de avatar e código antes dos paths.

## Definition of Ready

- [ ] endpoint/método/DTO/consumer/owner e paths exatos congelados;
- [ ] campos mínimos, compatibilidade, versionamento e rollout definidos;
- [ ] contract/RBAC/non-enumeration/smoke e rollback definidos;
- [ ] nenhum path pertence simultaneamente a outro slice ativo.
- [ ] branches GUA e diretório público são fixtures protegidas, não escopo deste card.

## Critérios de aceite

- [ ] zero entidade JPA em boundary REST do escopo;
- [ ] DTOs contêm somente campos com consumidor comprovado por papel;
- [ ] tenant/objeto são autorizados antes do mapping;
- [ ] relações aninhadas e campos sensíveis ausentes são provados por contrato;
- [ ] clientes suportados passam no mesmo manifest de versões;
- [ ] ratchet impede regressão para serialização JPA.

## Rollback

Reverter endpoint por endpoint mantendo versão contratual anterior autenticada;
nunca reativar entidade JPA ampla como fallback. Consumidor incompatível bloqueia
rollout até adapter DTO temporário e versionado.
