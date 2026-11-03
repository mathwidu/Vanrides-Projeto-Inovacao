# VR-SEC-002-REST-MINIMUM-PROJECTIONS-001 — Coordenar ownership e impedir regressão das projeções REST

## Resultado esperado

Existe uma tabela canônica endpoint+método+papel→owner e um ratchet que impede
entidade JPA, relação aninhada ou campo proibido de reaparecer nos boundaries
REST após os slices verticais. Este card coordena e valida; não possui controller,
DTO, parser ou tela de produto.

## Estado

`analyzed` — coordenador de `F-PRIV-016/F-PRIV-003`. Slices, schema do manifest,
regras, fixtures e integração aos gates ainda precisam de freeze.

## Dono operacional

`SEC/PGM`, com `BE`, `APP`, `PRIV`, `Produto`, `QA` e owners verticais.

## Risco ou objetivo associado

`F-PRIV-003`, `F-PRIV-016`, `R-001`, `R-007`, `VR-SEC-002`, G1 e G6.

## Impact Analysis preliminar

### Mudanças técnicas

- versionar ownership por endpoint, método, papel, tenant e DTO;
- agregar allowlist/denylist e versão dos contratos dos slices;
- validar que cada boundary possui exatamente um owner de implementação;
- detectar request/response JPA, relações persistentes e campos proibidos;
- exigir fixtures de produtor/consumidor e manifest same-version;
- integrar o ratchet aos gates sem editar produto neste card.

### Slices proprietários

- diretório público: `VR-SEC-002-PUBLIC-DRIVER-DIRECTORY-001`;
- GUA→PAX: `VR-SEC-002-GUARDIAN-PASSENGER-MINIMUM-PROJECTION-001`;
- DRV→PAX: `VR-SEC-002-DRIVER-PASSENGER-MINIMUM-PROJECTION-001`;
- entidades centrais DRV/PAX/presença: `VR-SEC-002-CORE-ENTITY-PROJECTIONS-001`;
- suporte: `VR-SEC-002-SUPPORT-MINIMUM-PROJECTION-001`;
- REST de GPS: `VR-SEC-001-LOCATION-DOMAIN-001`;
- realtime: `VR-SEC-003-LOCATION-REALTIME-001`;
- avatar: `VR-BE-004-AVATAR-PRIVACY-LIFECYCLE-001`;
- delete/lifecycle de PAX:
  `VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-IMPLEMENTATION-001`;
- delete/lifecycle de DRV: `VR-PRIV-003-DRIVER-DATA-LIFECYCLE-001`;
- delete/lifecycle de GUA: `VR-PRIV-003-GUARDIAN-DATA-LIFECYCLE-001`;
- offboarding ADM: `VR-PRIV-003-COMPANY-ADMIN-DATA-LIFECYCLE-001`;
- offboarding do tenant: `VR-PRIV-003-TENANT-OFFBOARDING-001`.

Realtime/STOMP permanece fora deste terminal REST e sob
`VR-SEC-003-LOCATION-REALTIME-001`; sua menção na matriz serve apenas para
separar boundaries, não cria herança ou cobertura implícita.

### Contratos afetados

Somente governança, manifestos e gates. Cada mudança de API/app continua no
owner vertical e precisa de commit, gate, smoke e rollback próprios.

### Riscos e mitigação

Um ratchet genérico pode gerar falso positivo ou encobrir lacuna. Usar tabela
positiva, fixtures por linguagem, ownership cardinality=1, schema versionado e
waiver humano escopado/expirável. WIP global não substitui ownership contratual.

### Validação mínima

Fixtures positivas/negativas de JPA, nesting, campo proibido, owner ausente/
duplicado e versão divergente; `program check`, gates e manifest same-version.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-SEC-002-PUBLIC-DRIVER-DIRECTORY-001` concluído;
- `VR-SEC-002-GUARDIAN-PASSENGER-MINIMUM-PROJECTION-001` concluído;
- `VR-SEC-002-DRIVER-PASSENGER-MINIMUM-PROJECTION-001` concluído;
- `VR-SEC-002-CORE-ENTITY-PROJECTIONS-001` concluído;
- `VR-SEC-002-SUPPORT-MINIMUM-PROJECTION-001` concluído;
- `VR-SEC-001-LOCATION-DOMAIN-001` concluído;
- `VR-BE-004-AVATAR-PRIVACY-LIFECYCLE-001` concluído;
- `VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-IMPLEMENTATION-001` concluído;
- `VR-PRIV-003-DRIVER-DATA-LIFECYCLE-001` concluído;
- `VR-PRIV-003-GUARDIAN-DATA-LIFECYCLE-001` concluído;
- `VR-PRIV-003-COMPANY-ADMIN-DATA-LIFECYCLE-001` concluído;
- `VR-PRIV-003-TENANT-OFFBOARDING-001` concluído;
- tabela endpoint+método+papel sem sobreposição congelada.

## Escopo

- ownership, contract manifest, regra arquitetural e integração aos gates.

## Fora de escopo

- backend/app/Maps product paths, implementar DTO, mudar endpoint/payload/status;
- aceitar campo, audiência ou compatibilidade em nome do owner vertical.

## Definition of Ready

- [ ] boundaries/slices e cardinalidade de ownership congelados;
- [ ] schema/versionamento/waiver e detecções definidos;
- [ ] fixtures, gates, manifest, evidência e rollback definidos;
- [ ] arrays de produto permanecem vazios.

## Critérios de aceite preliminares

- [ ] todo boundary coberto possui exatamente um owner vertical;
- [ ] zero controller/DTO/parser/tela é alterado por este card;
- [ ] entidade JPA/relação persistente/campo proibido falha no ratchet;
- [ ] owner ausente/duplicado e digest divergente falham fechados;
- [ ] fixtures válidas passam sem allowlist ampla ou self-attestation;
- [ ] produtor e consumidor usam o mesmo manifest de versões;
- [ ] gates backend/app executam a mesma revisão do ratchet.

## Rollout

Ativar primeiro em report-only sobre baseline congelado, corrigir classificação
com fixtures e depois tornar fail-closed. Waiver exige owner, escopo, digest e
expiração; não pode autorizar campo de produto.

## Rollback

Fixar a última revisão conhecida ou aplicar waiver estreito/temporário para
falso positivo. Nunca desligar o ratchet para permitir serialização ampla.
