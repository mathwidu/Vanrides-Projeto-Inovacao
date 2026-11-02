# VR-SES-003-VERIFIED-DEEP-LINK-LIFECYCLE-001 — Verificar deep links e fechar lifecycle de convites

## Resultado esperado

Convites usam link verificado e contrato de token minimizado, com expiração,
uso único, redaction, clipboard/cópia controlada e limpeza local; URI/token
jamais aparece integralmente em log nem é aceito por app/host não autorizado.

## Estado

`analyzed` — findings `F-PRIV-006` e `F-PRIV-011` validados; domínio, fallback,
compatibilidade e lifecycle ainda precisam de freeze.

## Dono operacional

`SES/SEC`, com `APP`, `BE`, `UX`, `PRIV` e `QA` como revisores.

## Risco ou objetivo associado

`F-PRIV-006`, `F-PRIV-011`, `R-002`, `R-007`, `VR-SES-003`, G1 e G6.

## Impact Analysis preliminar

### Mudanças técnicas

- adotar Universal Links/App Links com associação verificável e host allowlist;
- manter custom scheme somente em transição limitada ou removê-lo;
- validar tipo, origem, estado, expiração, uso e vínculo antes de navegar;
- impedir token completo em path/log/analytics e limpar storage/clipboard;
- compor com `VR-SEC-004-INVITE-REDEMPTION-001` sem duplicar redemption.

### Contratos afetados

URL pública, configuração Android/iOS, parser/resolver, convite backend,
clipboard/cópia, navegação e mensagens de erro.

### Riscos e mitigação

Link antigo pode quebrar ou app malicioso pode sequestrar o scheme. Usar rollout
de compatibilidade curto, allowlist exata, token de uso único e casos negativos.

### Validação mínima

Testes parser/resolver/redaction, arquivos de associação, links válidos e hosts/
schemes falsos em Android/iOS, replay/expiração, gate app/backend e smoke do
mesmo convite sintético sem expor token na evidência.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-SEC-004-INVITE-REDEMPTION-001` concluído ou contrato compatível congelado;
- `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001` concluído;
- `VR-SES-003-APP-CREDENTIAL-AND-SECURE-STORAGE-001` concluído;
- `VR-REL-001-ANDROID-RELEASE-BOUNDARY-001` concluído;
- domínio/hosting e estratégia de links antigos aprovados tecnicamente.

## Escopo

- links de convite/vínculo, associação de plataforma, storage, logs e UX.

## Fora de escopo

- enviar convite real, publicar domínio ou habilitar produção neste card local.

## Definition of Ready

- [ ] domínio, schemes/hosts, expiração, replay e transição definidos;
- [ ] application/bundle IDs, Team ID, certificado Android, AASA e
  `assetlinks.json` finais congelados;
- [ ] arquivos/consumidores, testes de dispositivo, smoke e rollback congelados;
- [ ] nenhum segredo ou token real necessário à validação.

## Critérios de aceite preliminares

- [ ] host/app não associado, scheme arbitrário, replay e token expirado falham;
- [ ] URI/token completos não entram em log, erro, evidência ou analytics;
- [ ] convite consumido/invalidado é removido do app e clipboard documentado;
- [ ] link válido chega ao papel correto sem bypass de autenticação/autorização;
- [ ] compatibilidade antiga possui prazo, telemetria minimizada e remoção.
- [ ] prova local não encerra o card: associação HTTPS real e devices físicos
  release-signed são exigidos ou o estado permanece `waiting_external`.

## Rollback

Desabilitar abertura automática e direcionar para entrada manual segura. Nunca
restaurar aceitação irrestrita de custom scheme nem logging de URI para depurar.
