# VR-SES-003-APP-PASSWORD-BASIC-REMOVAL-001 — Remover senha da sessão e fallback Basic pós-login

## Resultado esperado

Senha bruta existe somente no request transitório de autenticação e deixa de
entrar em `AppSessionUser`, routers, services, headers, navegação, storage ou
logs. Consumidores autenticados usam bearer; endpoints Basic remanescentes são
inventariados, migrados ou explicitamente isolados antes da remoção.

## Estado

`analyzed` — slice urgente de `F-PRIV-001` separado do consumo/rotação de
refresh. Paths e matriz Basic→bearer ainda precisam ser congelados.

## Dono operacional

`SES/APP`, com `BE`, `SEC`, `PRIV` e `QA`.

## Risco ou objetivo associado

`F-PRIV-001`, `R-002`, `R-007`, `VR-SES-003` e G1.

## Impact Analysis

### Mudanças técnicas

- remover `password`/`senha` de modelos, serialização, routers e storage;
- eliminar fallback Basic depois de autenticação bem-sucedida;
- migrar consumidores para bearer sem logar header/token;
- apagar resíduos legados idempotentemente sem regravar segredo;
- falhar fechado se um endpoint ainda exigir credencial bruta inesperada.

### Contratos afetados

Login/cadastro, `AppSessionUser`, session store, auth headers e endpoints backend
que aceitam Basic. Refresh/secure token storage permanecem em cards próprios.

### Riscos e mitigação

Remoção prematura pode quebrar endpoint legado ou upgrade. Congelar matriz de
consumidores, contract tests Basic/bearer, migração one-way e logout controlado.

### Validação mínima

Busca estrutural e testes negativos de senha; contract tests de todos os
endpoints; upgrade sobre fixtures legadas sintéticas; gate app/backend e smoke
same-build por papel.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001` concluído;
- matriz completa de writers/readers Basic e bearer.

## Escopo

- senha bruta e fallback Basic pós-login no app/contrato backend.

## Fora de escopo

- refresh rotation, escolha de vault, segredo real, deploy ou rotação remota.

## Definition of Ready

- [ ] consumidores/paths Basic-bearer e compatibilidade congelados;
- [ ] migração de resíduos, telemetry safe e rollback definidos;
- [ ] testes negativos, contract suite, smoke e evidência definidos;
- [ ] dirty paths exatos por repo congelados.

## Critérios de aceite

- [ ] senha não existe no modelo de sessão, router, service, storage ou log;
- [ ] raw password vive somente no request transitório e não é retido;
- [ ] resíduos `senha`/`session.password` são removidos idempotentemente;
- [ ] chamadas pós-login usam bearer e endpoints legados têm decisão explícita;
- [ ] falha não reativa Basic nem persiste senha como fallback;
- [ ] login/cadastro/upgrade por papel passam na mesma versão.

## Rollback

Rollback pode reabilitar temporariamente um adapter bearer compatível, nunca
reintroduzir senha no estado/storage. Incompatibilidade exige logout e correção
do endpoint, não retenção de credencial bruta.
