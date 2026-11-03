# VR-SES-003-APP-CREDENTIAL-AND-SECURE-STORAGE-001 — Proteger tokens locais e consumir refresh com rotação

## Resultado esperado

O app persiste somente o material de sessão necessário em storage protegido e
versionado. Access/refresh tokens obedecem expiração, rotação, revogação,
backup/reinstall e teardown do coordenador; resíduos de formatos antigos são
migrados sem ressuscitar sessão ou copiar segredo.

Remoção de senha/fallback Basic pertence ao card
`VR-SES-003-APP-PASSWORD-BASIC-REMOVAL-001`; este card não reabre essa superfície.

## Estado

`analyzed` — slice de storage/consumo de refresh de `F-PRIV-001`. Vault,
envelope, migração, política de backup/reinstall e paths ainda precisam de freeze.

## Dono operacional

`SES/APP`, com `SEC`, `PRIV`, `QA` e o owner do contrato backend de refresh.

## Risco ou objetivo associado

`F-PRIV-001`, `R-002`, `R-007`, `VR-SES-003`, G1 e G6.

## Impact Analysis preliminar

### Mudanças técnicas

- adotar Keychain/Keystore com accessibility/backup policy explícita;
- persistir envelope versionado mínimo, sem perfil, senha ou payload arbitrário;
- consumir refresh rotativo com single-flight e retry que não reutiliza token;
- migrar/remover tokens/resíduos legados de modo idempotente e fail-closed;
- integrar restore/refresh/logout/revogação ao `SessionLifecyclePort`;
- limpar Keychain sobrevivente de reinstall e impedir Android backup indevido;
- nunca registrar material em log, erro, analytics, teste ou evidência.

### Contratos afetados

Storage Flutter, restauração, bearer/refresh, logout/revogação e upgrade/reinstall.
O endpoint backend é do card `VR-SES-001-REFRESH-ROTATION-001`.

### Riscos e mitigação

Upgrade pode corromper envelope, deslogar usuário ou ressuscitar credencial.
Versionar formato, migrar uma vez, usar relógio/controlador de concorrência,
falhar fechado e provar fixtures sintéticas de upgrade/downgrade/reinstall.

### Validação mínima

Testes de vault/envelope/migração, concorrência refresh/logout, ausência em
SharedPreferences/backup/log, gate app e smoke no mesmo build sobre estado
legado sintético. Nenhum valor real é necessário.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001` concluído;
- `VR-SES-003-APP-PASSWORD-BASIC-REMOVAL-001` concluído;
- `VR-SES-001-REFRESH-ROTATION-001` concluído;
- decisão técnica de vault, backup e suporte mínimo de SO congelada.

## Escopo

- access/refresh token, envelope seguro, migração, restore e teardown local.

## Fora de escopo

- senha, Basic, implementação backend do refresh, push, fila offline;
- credencial real, rotação remota, deploy ou promoção.

## Definition of Ready

- [ ] writers/readers e paths exatos de material de sessão congelados;
- [ ] envelope, vault, accessibility, backup e reinstall definidos;
- [ ] rotação/single-flight/retry/revogação e state machine integrados;
- [ ] migração/rollback e suporte mínimo de SO definidos;
- [ ] testes, smoke, evidência e dirty paths definidos sem sobreposição.

## Critérios de aceite preliminares

- [ ] somente campos allowlisted do envelope existem em storage protegido;
- [ ] SharedPreferences, navegação, log, backup e evidência não contêm token;
- [ ] refresh rotaciona/revoga/expira e retry nunca reutiliza token gasto;
- [ ] callback stale não escreve token da geração anterior;
- [ ] logout/revogação/troca limpam todo material antes da nova geração;
- [ ] iOS reinstall não ressuscita Keychain e Android não restaura sessão;
- [ ] envelope ausente/malformado/antigo falha fechado ou migra uma vez;
- [ ] login/restore/refresh/logout passam no mesmo build por papel.

## Rollout

Introduzir vault/envelope atrás de adapter, migrar fixtures sintéticas, ativar
leitura nova e remover writer legado após smoke same-build. Nenhum dual-write
pode copiar token para storage menos protegido.

## Rollback

Preservar leitura segura do envelope migrado ou forçar logout controlado. Nunca
restaurar SharedPreferences, senha ou material antigo como fallback.
