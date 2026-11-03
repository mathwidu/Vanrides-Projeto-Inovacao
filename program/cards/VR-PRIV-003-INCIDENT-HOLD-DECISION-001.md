# VR-PRIV-003-INCIDENT-HOLD-DECISION-001 — Decidir hold de dados para incidente sem ampliar finalidade ou acesso

## Resultado esperado

Existe uma decisão humana imutável que define se um incidente pode suspender
temporariamente purge, exclusão, anonimização ou transformação e, quando puder,
define gatilho, autoridade, escopo, stores, revisão, término, release,
comunicação e auditoria.

A decisão pode concluir explicitamente que não existe hold neste ciclo. Nenhuma
alternativa é presumida como obrigação jurídica e nenhuma decisão documental
autoriza implementação, consulta a dado real, deploy, piloto ou produção.

## Estado

`analyzed` — derivado de `H-011` da matriz de minimização. A política
cross-family ainda não está aceita, as autoridades humanas não estão registradas
e não existe hold autorizado. ADR-005 permanece
`PROPOSED / INCOMPLETE / REFREEZE_REQUIRED / NOT_ACCEPTED`, e sua revisão
histórica está `SUPERSEDED / DO_NOT_ACCEPT`; nenhum prazo ou exceção de GPS é
importado como política.

## Dono operacional

`PRIV/SEC/OPS`. A autoridade do hold é Privacidade + Jurídico/DPO + SEC/OPS.
Produto participa somente da célula de experiência/comunicação ao titular;
não habilita, amplia ou libera hold. SEC/OPS respondem por viabilidade,
isolamento, acesso e prova; não inferem obrigação jurídica.

## Risco ou objetivo associado

`R-007`, `R-008`, `R-021`, épico `VR-PRIV-003`, G6 e G7.

## Impact Analysis preliminar

### Contexto

Hoje não existe modelo aprovado de hold, autoridade, dupla revisão, expiração,
release ou auditoria capaz de justificar suspensão de purge. Ticket de suporte,
investigação informal, erro operacional, restore ou flag manual não constituem
autorização.

### Invariantes técnicas candidatas

- hold nunca amplia finalidade, audiência ou acesso de produto;
- escopo é positivo por D-ID, tenant, objeto, store e cópia, sem wildcard;
- backup, restore, log, provider, extração forense, solicitação do titular e
  mídia nunca ficam implícitos nem compartilham canal por inferência;
- dado já eliminado não é recriado e coleta nova não é autorizada;
- conservação não concede leitura; operação parcial não retorna falso sucesso;
- auditoria usa IDs opacos, classes e contagens, nunca payload, GPS, contato,
  token, texto livre ou mídia;
- ADR-005 continua `NOT_ACCEPTED`; qualquer mecanismo futuro terá card técnico.

Todos esses itens são `TECHNICAL_RECOMMENDATION`, não política aceita.

### Contratos possivelmente afetados após aceite

Backend, banco, jobs, backup/PITR, restore, logs, evidência, providers,
extração forense, conflito com solicitação do titular, suporte, comunicação e
observabilidade. Nenhum path de produto será aberto neste card.

### Riscos e mitigação

- hold indefinido: exigir prazo, revisão, expiração e owner humanos;
- hold amplo: escopo positivo por família/store;
- ampliar acesso: separar conservação de autorização de leitura;
- restore reintroduzir dado: reconciliar hold e purge antes de tráfego;
- provider divergir: decisão e evidência próprias por provider;
- auditoria virar cópia sensível: envelope AE1 minimizado;
- decisão jurídica inferida: manter valores humanos `PENDING_*`.

### Validação mínima

- cobertura D01–D34 e stores/cópias, com N/A justificado;
- cada decisão possui `value`, `status`, `owner` e `source`;
- zero `ACCEPTED` sem autoridade, revisão, digest e timestamp;
- matriz, ADR, card e evidência referem o mesmo predecessor;
- `program check`, JSON, `git diff --check` e scan de PII/segredo;
- nenhuma allowlist de backend/app/Maps aberta.

## Contrato de decisão

| Campo | Value | Status | Owner | Source |
|---|---|---|---|---|
| classes/gatilhos | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | `H-011` |
| solicitante e prova de autoridade | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | `H-011` |
| aprovadores e segregação de funções | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | `H-011` |
| famílias, tenant, objetos e stores | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | D01–D34 |
| menor/GUA | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | `H-003`, `H-011` |
| GPS e relação com ADR-005 | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | `H-004`, `H-011`; ADR-005 `NOT_ACCEPTED` |
| início, duração, revisão, expiração e vencimento | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | `H-005`, `H-011` |
| release/revogação e retomada do lifecycle | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | `H-011` |
| acesso durante hold | `PENDING` | `PENDING_PRIVACY + PENDING_SECURITY_OPS` | Privacidade + SEC/OPS | `H-011` |
| backup/PITR/restore | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | `H-009`, `H-011` |
| providers e cópias externas | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | `H-006`, `H-011` |
| extração forense e canal restrito | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | `INCIDENT-FORENSIC-EXTRACTION-001`; `H-011` |
| conflito com solicitação/exportação do titular | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | `EXPORT-SUBJECT-001`; `H-007`, `H-011` |
| experiência e comunicação ao titular | `PENDING` | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` | Produto + Privacidade + Jurídico/DPO | `INCIDENT-COMMUNICATION-001`; `H-007`, `H-011` |
| envelope/retenção da auditoria | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | `H-008`, `H-011` |
| falha parcial, retry e falso sucesso | `PENDING` | `PENDING_SECURITY_OPS` | SEC/OPS | `H-011` |
| habilitar, restringir ou rejeitar hold | `PENDING` | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Privacidade + Jurídico/DPO + SEC/OPS | `INCIDENT-HOLD-001`; registro final |

## Alternativas obrigatórias

1. não implementar hold e preservar lifecycle padrão aceito;
2. hold temporal e estritamente scoped por família/store;
3. conservar somente evidência derivada e minimizada;
4. hold distinto por store/provider;
5. conservação ampla, analisada explicitamente e nunca presumida.

Nenhuma alternativa está recomendada ou aceita neste estado.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído por revisão imutável;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` concluído com baseline aceita;
- autoridades de Privacidade, Jurídico/DPO e SEC/OPS identificadas; Produto
  identificado somente para experiência/comunicação;
- ADR-005 separada e `NOT_ACCEPTED` até aceite próprio.

## Escopo

- decisão de incidente/hold, conservação, release e propagação entre stores;
- alternativas, impactos, owners, evidência e aceite imutável.

## Fora de escopo

- operar incidente real ou acessar dado, backup, log, provider, GPS ou mídia;
- migration, job, API, UI, schema, purge, restore ou obrigação inferida;
- push, deploy, piloto ou produção.

## Definition of Ready

- [ ] matriz PRIV-002 congelada por commit/blob/SHA-256;
- [ ] baseline cross-family aceita e imutável;
- [ ] D01–D34/stores/cópias, perguntas, alternativas e owners completos;
- [ ] SEC/OPS concluiu viabilidade sem abrir paths de produto;
- [ ] aprovadores, paths de ADR/aceite/evidência e invalidação definidos.

## Critérios de aceite preliminares

- [ ] decisão habilita, restringe ou rejeita hold explicitamente;
- [ ] cada campo tem valor aceito ou `NOT_APPLICABLE` justificado;
- [ ] finalidade, audiência e acesso nunca são ampliados;
- [ ] menor, GPS, backup, restore, provider, extração forense,
  `EXPORT-SUBJECT-001` e auditoria são explícitos e separados;
- [ ] prazo, revisão, expiração, release e falha parcial estão definidos;
- [ ] aceite registra autoridades, revisão, digest e timestamp;
- [ ] ADR-005 segue separada; decisão não concede GO técnico ou de promoção.

## Rollback

Antes de implementação, substituir, rejeitar ou revogar a decisão por nova
revisão/digest e novo aceite. Rollback documental nunca executa purge, restore,
retenção ou remoção de dados.
