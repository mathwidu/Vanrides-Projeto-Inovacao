# VR-SEC-004-BOOTSTRAP-TRUST-001 — Comprovar posse e assurance no bootstrap de empresa

## Resultado esperado

Conhecer ou registrar nome/slug não basta para ativar uma organização. Empresa
nova ou preexistente só se torna tenant operacional e recebe seu primeiro
`COMPANY_ADMIN` após prova organizacional curta, expirável, de uso único e
auditável, sem expor o segredo.

## Estado

`analyzed`

## Risco ou objetivo associado

`R-015`, `R-017`, épico `VR-SEC-004` e gate `G1 — TRUSTED`.

## Contexto

`VR-SEC-001-BE-READ` bloqueia overwrite, impõe um admin por empresa e faz o
bootstrap público negar tenant `default`, slug legado e qualquer empresa
preexistente. Entretanto, criar imediatamente uma empresa nova por nome ainda
permite squatting/impersonação, e falta ativação segura para empresas legadas
sem reabrir o modelo first-request-wins.

## Impact Analysis preliminar

- fazer `POST /company-admins/bootstrap` criar apenas `CompanyClaim` pendente e
  responder `202`, sem criar tenant/admin;
- separar reserva de nome, criação e ativação operacional da empresa; claim tem
  TTL/status e unicidade apenas enquanto pendente;
- exigir convite/setup token aleatório, expirável, de uso único e persistido
  apenas por hash para a empresa preexistente;
- aplicar política de nomes reservados e claims que expiram sem bloquear o nome
  indefinidamente;
- um endpoint separado consome prova e cria `Company + primeiro Admin +
  LoginIdentity` na mesma transação;
- produzir resposta genérica para prova inválida, expirada ou reutilizada;
- adaptar o app sem persistir ou registrar o segredo;
- depender do namespace global de e-mail e do rate limit confiável; reutilizar
  primitives de segredo somente por contrato compartilhado, sem dependência de
  produto ou ordem Flyway com o card de convite.

## Critérios de aceite

- [ ] Nome/slug público isolado nunca autoriza claim de empresa existente.
- [ ] Empresa nova não fica operacional antes da prova organizacional.
- [ ] Duas claims do mesmo nome produzem no máximo uma pendente; expiração
  libera o nome sem apagar auditoria.
- [ ] Reserva/claim expira e possui política auditável de nomes reservados.
- [ ] Prova possui entropia adequada, expiração, uso único e hash em repouso.
- [ ] Consumo, ativação e criação do admin são atômicos; replay não muta estado.
- [ ] Logs, erros, analytics e evidências não contêm a prova.
- [ ] API `202/complete`, PostgreSQL concorrente, estado pending/retry no app e
  smoke do mesmo SHA passam.
- [ ] Recuperação/reenvio usam fluxos dedicados, sem reabrir bootstrap.

## Blocker de análise

Escolher e documentar o nível/método de assurance, quem emite a prova e como
empresas novas ou legadas a recebem. Até essa decisão, piloto e produção
permanecem `HOLD`.

## Dependências

- `VR-SEC-001-BE-READ` concluído;
- `VR-SEC-004-EMAIL-NAMESPACE-001` concluído;
- `VR-SEC-005-RATE-LIMIT-TRUST-001` concluído;
- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- número Flyway não reservado enquanto `analyzed`; no freeze de `ready`,
  revalidar o maior número presente/aplicado, alocar o próximo contínuo,
  atualizar a allowlist e provar ausência de collision/out-of-order.

## Rollback

Rollback reabre `R-015`; deve invalidar provas emitidas e manter promoção em
`HOLD`.
