# VR-SEC-005-SECRET-SCOPE-ROTATION-001 — Inventariar escopo, owner e rotação de segredos

## Resultado esperado

Cada categoria D31 possui owner, ambiente, workload/provider, escopo/restrição,
store, criação/última rotação verificável, procedimento de revoke e break-glass.
O inventário registra apenas metadata/digest de configuração, nunca valor, e
separa banco, JWT, Google, Resend, GHCR, SSH/Tailscale e Swarm.

## Estado

`analyzed` — gap D31/F-PRIV-011; nenhum valor foi autorizado para leitura,
rotação ou teste. Fontes seguras, owners e ambientes precisam de freeze.

## Dono operacional

`OPS/SEC`, com `BE`, `APP`, `MAP`, `REL`, `PRIV` e owners de provider.

## Risco ou objetivo associado

`F-PRIV-011`, `R-012`, `VR-SEC-005`, G1, G5, G6 e G7.

## Impact Analysis

### Mudanças técnicas

- catalogar metadata por categoria/ambiente/workload sem exibir valor;
- separar chaves mobile/backend/CI/provider e aplicar menor escopo/restrição;
- definir owner, rotação, revoke, grace/dual-key e break-glass auditável;
- impedir segredo em log/evidence/build/backup e configuração versionada;
- criar preflight read-only; qualquer rotação real exige autorização separada.

### Contratos afetados

GitHub/Swarm secrets, config local, deploy, banco, JWT, Google, Resend, GHCR,
SSH/Tailscale e Maps. Pode exigir rollout coordenado por consumer.

### Riscos e mitigação

Inventário pode vazar valor ou rotação pode derrubar runtime. Metadata only,
redaction automática, two-person rule, dual-key quando suportado e rollback por
versão anterior ainda válida sob janela explícita.

### Validação mínima

Scanner de valor high-confidence sem output, check de scope/restriction/owner,
fixtures sintéticas, dry-run, gates por repo e smoke somente após autorização.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- decisão global `SECRET-SCOPE-001` resolvida no checkpoint próprio
  `program/decisions/ADR-secret-scope-rotation.md`, com revisão/digest/autoridade
  imutáveis e sem transformar metadata em permissão para ler valor. A linha da
  ADR-002 é somente handoff preliminar para PAX e não governa todos os workloads;
- owners/ambientes/fontes metadata definidos;
- autorização separada antes de tocar store/provider real.

## Escopo

- D31 global: metadata, escopo, owner, rotação/revoke e evidência sanitizada.

## Fora de escopo

- ler/registrar valor, rotacionar agora, deploy, mailbox, banco ou produção.

## Definition of Ready

- [ ] categorias/owners/ambientes/workloads/fontes metadata congelados;
- [ ] checkpoint próprio `ADR-secret-scope-rotation.md` contém
  `SECRET-SCOPE-001`, value/status/owner/source, alternativas, autoridade,
  revisão/digest/timestamp e aceite aplicável;
- [ ] política de acesso, redaction, dual-key, revoke e rollback definida;
- [ ] paths/scripts/testes/smokes/evidência exatos definidos;
- [ ] autorização humana por ambiente definida separadamente.

## Critérios de aceite

- [ ] 100% das categorias D31 têm owner/store/escopo/ambiente/rotação/revoke;
- [ ] chaves mobile/backend/CI/provider não compartilham escopo indevido;
- [ ] scanner/evidência nunca imprimem valor ou path pessoal;
- [ ] break-glass e acesso/rotação são auditáveis por ID/categoria;
- [ ] rotação/revoke têm preflight, dual-key/ordem e rollback testados;
- [ ] nenhuma operação remota ocorre sem autorização específica.

## Rollback

Reverter somente metadata/automação local no card. Em rotação futura, rollback
usa versão anterior dentro da janela aprovada; nunca copiar valor para arquivo,
log ou Git. Se revoke já ocorreu, emitir novo material pelo store autorizado.
