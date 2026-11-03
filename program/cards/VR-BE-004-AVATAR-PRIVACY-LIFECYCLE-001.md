# VR-BE-004-AVATAR-PRIVACY-LIFECYCLE-001 — Tornar avatar privado, sanitizado e durável

## Resultado esperado

Upload de avatar valida tipo/conteúdo/tamanho, remove metadados, usa identificador
opaco, armazenamento durável e acesso autorizado. Substituição, exclusão,
órfãos, backup e retenção seguem lifecycle explícito sem URL pública previsível.

## Estado

`analyzed` — finding `F-PRIV-007`; política de acesso, store e lifecycle ainda
dependem da matriz de privacidade e de arquitetura de uploads.

## Dono operacional

`BE/PRIV`, com `SEC`, `APP`, `OPS` e `QA` como revisores.

## Risco ou objetivo associado

`F-PRIV-007`, `R-007`, `VR-BE-004`, G1, G5 e G6.

## Impact Analysis preliminar

### Mudanças técnicas

- decodificar/re-encodar imagem e remover EXIF/metadados;
- validar magic bytes, dimensões, tamanho e limites de recurso;
- trocar nome role/ID por chave opaca e servir via boundary autenticado;
- prover volume/object store durável, backup e restore coerentes;
- apagar anterior/órfão de modo idempotente e auditar sem path pessoal.

### Contratos afetados

Upload/download/URL de perfil, cache, payload de conta, filesystem/Swarm,
backup/restore e UX de avatar/fallback.

### Riscos e mitigação

Re-encode pode falhar, URL antiga quebrar ou delete perder referência. Usar
staging atômico, chave opaca, compatibilidade curta e reconciliador de órfãos.

### Validação mínima

Testes de formato falso/bomba/metadado/path, autorização por papel/tenant,
substituição/delete concorrentes, restart/restore, gate backend/app e smoke.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` aceito;
- `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` concluído com decisão aplicável a
  D07/D21, inclusive a alternativa explícita de não aplicar hold;
- política de acesso/retenção e store durável escolhidos;

## Escopo

- avatars de contas, storage, acesso, sanitização e lifecycle físico.

## Fora de escopo

- fotos em pins/mapa, galeria social ou migração remota sem autorização.

## Definition of Ready

- [ ] store, ameaça, formatos/limites, acesso, retenção e compatibilidade definidos;
- [ ] contrato de composição com
  `VR-OPS-002-BACKUP-RETENTION-RESTORE-PURGE-001` congelado;
- [ ] paths, testes, smoke, rollout, migração e rollback congelados;
- [ ] fixtures de imagem são públicas/sintéticas e com proveniência.

## Critérios de aceite preliminares

- [ ] arquivo aceito é re-encodado sem EXIF/metadado e com limites aplicados;
- [ ] URL/chave não revela role, user ID, nome ou path do host;
- [ ] outro tenant/anônimo não obtém avatar privado;
- [ ] replace/delete removem anterior e órfãos idempotentemente;
- [ ] restart/backup/restore preservam referência e lifecycle aprovados.

## Rollback

Desabilitar novos uploads e manter fallback visual; preservar blobs existentes
sob acesso restrito até migração/decisão. Nunca reabrir `/uploads/**` público.
