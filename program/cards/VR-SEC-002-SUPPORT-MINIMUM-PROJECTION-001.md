# VR-SEC-002-SUPPORT-MINIMUM-PROJECTION-001 — Minimizar mensagem e acesso do suporte

## Resultado esperado

Tickets de suporte aceitam categorias/estrutura mínima, desestimulam e
rejeitam classes proibidas quando possível, e só são acessíveis ao solicitante
ou ao `COMPANY_ADMIN` autorizado do mesmo tenant no contrato atual. Papel
dedicado/assignment de suporte permanece decisão `PENDING_PRODUCT_SECURITY` e
não é inventado por este card. Projeções não duplicam perfil, viagem, contato
ou texto além da necessidade aprovada.

## Estado

`analyzed` — gap D20 sem slice dedicado; campos livres, RBAC, consumidores e
retenção ainda precisam ser congelados.

## Dono operacional

`SEC/BE`, com `APP`, `PRIV`, `Produto`, `OPS` e `QA`.

## Risco ou objetivo associado

`R-007`, `R-021`, `VR-SEC-002`, G1 e G6.

## Impact Analysis

### Mudanças técnicas

- inventariar request/response, papéis existentes e telas de suporte;
- separar categoria/assunto de descrição livre e limitar tamanho/classes;
- DTO por solicitante/COMPANY_ADMIN, sem entidade ou snapshot amplo;
- autorizar tenant+ticket+papel/owner antes da leitura/mutação;
- definir redaction, retenção/export e evento auditável sem mensagem.

### Contratos afetados

`POST /support/tickets`, `GET /support/tickets`,
`GET /support/tickets/{id}`, `GET /company-admins/support/tickets`,
`GET /company-admins/support/tickets/{id}`,
`POST /company-admins/support/tickets/{id}/responses` e
`POST /company-admins/support/tickets/{id}/status`, além de services/tables e
telas PAX/GUA/ADM.

A tabela preliminar de ownership é produzida por
`VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` em
`program/privacy/MINIMIZATION_CONSENT_AUDIT_MATRIX.md`. O card REST terminal
valida e impede regressão; ele não é predecessor da implementação vertical.

### Riscos e mitigação

Filtro pode apagar contexto legítimo ou texto livre pode vazar segredo. Usar UX
clara, validation size/type, redaction determinística, least privilege e testes
de non-enumeration.

### Validação mínima

Contract/RBAC tests por papel/tenant/assignment, limites/body, PII/secret
canaries, export/retention fixtures, gate backend/app e smoke same-version.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` concluído com decisão de
  retenção/exportação D20 aceita;
- `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` concluído para que o contrato D20
  conheça hold/release aplicável antes de congelar auditoria e lifecycle;
- matriz endpoint→owner congelada.

## Escopo

- D20 support ticket/message/projection/access e auditoria mínima.

## Fora de escopo

- decisão legal, operação real de suporte, conteúdo de produção ou deploy;
- purge/delete físico de D20. Este card apenas consome a decisão cross-family
  no contrato; execução destrutiva exige owner/lifecycle próprio e decisão de
  incident hold quando aplicável.

## Definition of Ready

- [ ] fields/endpoints/consumers/RBAC/paths congelados;
- [ ] categorias, limites, redaction e UX de erro definidos;
- [ ] retenção/export/auditoria e decisão humana referenciadas;
- [ ] testes/smoke/evidência/rollout/rollback definidos.

## Critérios de aceite

- [ ] solicitante e `COMPANY_ADMIN` autorizado do próprio tenant são as únicas
  audiências atuais; papel/assignment futuro exige decisão/card separado;
- [ ] DTO não duplica perfil/contato/viagem sem consumidor aprovado;
- [ ] body/attachment/classes proibidas têm limites fail-closed;
- [ ] logs/auditoria nunca contêm mensagem ou anexos;
- [ ] non-enumeration e isolamento tenant passam adversarialmente;
- [ ] app/backend passam no mesmo manifest de versões.

## Rollback

Manter adapter DTO anterior somente para leitura autorizada durante transição;
nunca reabrir ticket para admin genérico ou remover limites/redaction. Mudança
destrutiva aguarda retenção/export aprovadas.
