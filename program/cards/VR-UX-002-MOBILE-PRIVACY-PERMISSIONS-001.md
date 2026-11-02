# VR-UX-002-MOBILE-PRIVACY-PERMISSIONS-001 — Minimizar permissões e chaves mobile

## Resultado esperado

Android/iOS solicitam localização, background, push e fotos somente no momento e
papel necessários, com copy acessível, fallback e revogação. Places/geocoding
residencial usam boundary e chaves separadas/restritas; configuração local
ignorada não vira fonte de verdade nem vaza credencial.

## Estado

`analyzed` — finding `F-PRIV-011`; necessidade por jornada, arquitetura Places,
restrições de chave e estratégia de background ainda precisam ser congeladas.

## Dono operacional

`UX/PRIV`, com `APP`, `SEC`, `BE`, `OPS`, `A11Y` e `QA` como revisores.

## Risco ou objetivo associado

`F-PRIV-011`, `R-007`, `VR-UX-002`, G1, G3 e G6.

## Impact Analysis preliminar

### Mudanças técnicas

- mapear permissão × papel × ação e remover declarações não necessárias;
- pedir just-in-time, explicar uso, tratar deny/revogado e settings;
- separar chave SDK por plataforma da chave server-side e aplicar restrictions;
- impedir chamada residencial direta quando boundary backend for requerido;
- inventariar configuração ignorada sem registrar valor e definir rotação à parte;
- testar manifests/plists, copy, reflow, leitor de tela e lifecycle do app.

### Contratos afetados

AndroidManifest/Info.plist, UX de permissão, GPS/background, fotos/push, Google
Places/Geocoding, secrets de build e potencial proxy backend.

### Riscos e mitigação

Remover permissão pode quebrar jornada; chave mal restrita pode ser abusada.
Usar matriz por papel, flavors, deny tests, quotas/alerts e nenhum valor em Git.

### Validação mínima

Testes unit/widget, inspeção de manifests por build, matriz Android/iOS de grant/
deny/revoke/background, key-scope preflight sanitizado, gates e smokes de device.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-SEC-005-SECRET-SCOPE-ROTATION-001` concluído para metadata/escopo;
- decisão Produto/Privacidade por permissão e arquitetura Places congeladas;
- autoridade separada antes de restringir/rotacionar credencial externa.

## Escopo

- permissões mobile, copy/acessibilidade, Places/geocoding e escopo de chaves.

## Fora de escopo

- exibir ou rotacionar chave real agora, alterar console Google ou publicar app.

## Definition of Ready

- [ ] matriz jornada/papel/permissão e boundary Google definidos;
- [ ] IDs de bundle/package, owners, restrictions e ambientes inventariados;
- [ ] paths, devices, testes, smokes, rollout e rollback congelados.

## Critérios de aceite preliminares

- [ ] cada permissão declarada possui jornada/owner e é pedida just-in-time;
- [ ] deny/revoke/background têm fallback funcional e copy acessível;
- [ ] app não recebe chave server-side e chaves mobile são separadas/restritas;
- [ ] configuração ignorada não é logada, commitada nem usada entre ambientes;
- [ ] Places/endereço não contorna boundary, minimização ou rate limit aprovados;
- [ ] smokes por papel passam em Android/iOS na mesma versão.

## Rollback

Desabilitar a feature que exige permissão/provider e manter entrada manual segura.
Nunca ampliar permissão ou liberar chave irrestrita para recuperar a jornada.
