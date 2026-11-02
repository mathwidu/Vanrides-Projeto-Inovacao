# VR-REL-001-ANDROID-RELEASE-BOUNDARY-001 — Criar boundary Android de release seguro

## Resultado esperado

Build Android distribuível usa signing de release controlado, rede declarada no
manifest final, base URL/flavor explícitos e separação DEV/staging/produção.
Build falha se herdar debug key, endpoint remoto default ou configuração ausente.

## Estado

`analyzed` — finding latente `F-PRIV-012`; qualquer distribuição Android
permanece bloqueada até este card e seus smokes estarem concluídos.

## Dono operacional

`REL/APP`, com `SEC`, `OPS`, `QA` e `PRIV` como revisores.

## Risco ou objetivo associado

`F-PRIV-012`, `R-005`, `R-010`, `VR-REL-001`, G5, G6 e G7.

## Impact Analysis preliminar

### Mudanças técnicas

- separar signing debug/release e carregar segredo fora do Git/log;
- definir flavors e `API_BASE_URL` obrigatória por ambiente;
- declarar/verificar permissões no merged manifest da variante final;
- bloquear cleartext/default remoto e configuração cruzada;
- gerar manifest de build com commit/digest/config não sensível;
- adicionar CI/gates e smoke de pacote assinado em device/emulator.

### Contratos afetados

Gradle, manifests, build flavors, runtime API base, CI/artifacts, signing e
compatibilidade de instalação/upgrade.

### Riscos e mitigação

Keystore pode vazar, flavor apontar ao ambiente errado ou release ficar sem rede.
Usar secret store, fail-closed, merged-manifest test e smoke de endpoint canário.

### Validação mínima

Testes de configuração negativa, inspeção APK/AAB/assinatura/manifest, secret
scan, build reproduzível, instalação/upgrade e smoke sem acessar produção.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-UX-002-MOBILE-PRIVACY-PERMISSIONS-001` concluído;
- `VR-SEC-005-SECRET-SCOPE-ROTATION-001` concluído para ownership/escopo;
- matriz de ambientes/domínios e owner de signing definidos;
- autorização separada para criar/rotacionar material de assinatura real.

## Escopo

- boundary de build/release Android, flavors, manifest, signing e API base.

## Fora de escopo

- publicar em loja, usar credencial real nesta fase ou promover backend.

## Definition of Ready

- [ ] variantes/domínios/signing/upgrade e política de segredo definidos;
- [ ] paths, CI, devices, testes, smoke, rollout e rollback congelados;
- [ ] artifact de teste não aponta a produção.

## Critérios de aceite preliminares

- [ ] variante release rejeita debug key e signing/config ausentes;
- [ ] merged manifest possui rede necessária e nenhuma permissão não aprovada;
- [ ] API base é obrigatória, allowlisted e não possui default remoto silencioso;
- [ ] DEV/staging/produção não compartilham segredo/config acidentalmente;
- [ ] APK/AAB assinado é instalável, manifestado por digest e smokeado;
- [ ] logs/artifacts não expõem keystore, senha ou chave.

## Rollback

Revogar o candidato e bloquear distribuição; corrigir uma nova variante. Nunca
assinar release com debug key nem apontar build desconhecido a produção.
