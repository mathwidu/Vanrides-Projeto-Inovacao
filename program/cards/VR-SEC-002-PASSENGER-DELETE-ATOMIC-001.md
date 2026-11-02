# VR-SEC-002-PASSENGER-DELETE-ATOMIC-001 — Tornar delete de passageiro tenant-scoped atômico

## Resultado esperado

A decisão de autorização tenant-scoped e o hard delete usam o mesmo snapshot
transacional. Uma mudança concorrente de empresa nunca permite que o tenant
anterior exclua o passageiro por ID global.

## Estado

`done` — implementação e prova local concluídas no backend limpo
`6efd2e2f66cb5ac3784085d317ba98174750c815`, tree
`142c51caebbc0eca9b93ed828c78924963c7f7c6`, com evidência coordenadora
`0b316fa4d307c7250bf5b207c7748b550ad0e175` e `R-020` encerrado em
`371e9f93390d05a014bd9433c6a547b8b99a4006`.

## Risco ou objetivo associado

`R-020`, épico `VR-SEC-002` e gate `G1 — TRUSTED`.

## Contexto

O fluxo atual pode fazer `buscar por id+companyId` no controller e depois
`deletar por id` em outra transação. A empresa pode mudar entre as operações.
O namespace global e a revogação do owner não substituem essa autorização por
objeto.

## Impact Analysis

### Boundary técnico congelado

- o controller delega uma única vez `passageiroId + companyId`, sem lookup ou
  decisão de autorização anterior;
- o repository executa uma única DML nativa autoritativa:
  `DELETE FROM passageiro WHERE id = :id AND company_id = :companyId`;
- método derivado `deleteBy...` não será usado, pois poderia materializar a
  entidade antes do delete e recriar a janela TOCTOU;
- o service é `@Transactional`, interpreta exatamente uma linha como sucesso e
  zero linhas como `404`; qualquer outro resultado falha fechado;
- o trigger V25 da tabela `passageiro` libera `login_identity` e invalida
  refresh, confirmação e device no mesmo statement/transação;
- a revogação Java prévia será removida deste caminho: além de redundante, ela
  usa ordem diferente da autoridade V25;
- o persistence context é descarregado antes e limpo depois da DML para não
  manter entidade removida stale na mesma transação.

### Contrato HTTP e RBAC

- rota e payload permanecem `DELETE /passageiros/{id}`, sem body;
- apenas `COMPANY_ADMIN` pode concluir a mutação tenant-scoped;
- delete autorizado retorna `204` sem body;
- inexistente, `company_id IS NULL`, outro tenant ou reassignment vencedor
  retornam o mesmo `404`, sem confirmar existência ou empresa atual;
- `GUARDIAN` continua recebendo `403 ACTION_UNAVAILABLE`; `DRIVER` e
  `PASSENGER` permanecem proibidos pelo filtro/política existente;
- Basic e Bearer permanecem compatíveis; não há mudança de CORS ou payload de
  sucesso.

### Concorrência e ordem de locks

Contrato observado: PostgreSQL `READ COMMITTED`. O primeiro DML que alcança a
linha vence; depois de aguardar um writer, o predicado tenant-scoped é
reavaliado na versão confirmada.

Ordem do delete V25:

1. tuple de `passageiro`;
2. owner exato em `login_identity`;
3. `refresh_token`;
4. `email_verification_token`;
5. `device_token`;
6. confirmação do delete e liberação do namespace.

Fluxos de confirmação/troca que também começam pela conta ficam serializados
na primeira linha. Registro de device começa no registry e não volta para a
conta, portanto não fecha ciclo com o delete.

### Writer adversarial congelado

A prova de reassignment usa o efeito real do `PassengerCompanyLinker.link` —
um `UPDATE passageiro SET company_id = B` — e força flush dentro de uma
transação real. Também existe uma asserção SQL direta do row count para provar
a semântica profunda independentemente do timing do Hibernate.

- reassignment `A -> B` que grava primeiro faz o delete de A esperar e terminar
  em `404`, preservando conta e artefatos;
- delete de A que grava primeiro faz o reassignment esperar e perder sem
  ressuscitar a linha;
- nenhuma das duas provas aceita apenas futures incompletos: o teste observa o
  bloqueio no PostgreSQL antes de liberar o vencedor.

### FKs e rollback

O card não cria cascade nem apaga histórico. As tabelas `presenca`,
`guardian_passageiro`, `rota_base_parada`, `viagem_parada`,
`company_link_request`, `company_invite_code_usage`, `servico_passageiro`,
`presenca_servico` e `support_ticket` podem bloquear o hard delete. Uma FK
`23503` deve reverter integralmente passageiro, registry e todas as famílias de
artefatos. A decisão de reter, anonimizar ou eliminar esses dados é um trabalho
de privacidade separado e não será inferida neste card.

## Escopo autorizado

- controller, service e repository do passageiro;
- assinatura única tenant-scoped e remoção do método global inseguro;
- testes WebMvc, unitários, suportes de fixture/lock extraídos para respeitar o
  ratchet de 350 linhas e atualização dos testes antigos consumidores da
  assinatura;
- integração PostgreSQL determinística nos dois ordenamentos e rollback por FK;
- ratchet obrigatório do perfil, smoke same-JAR, contratos, RBAC, runbooks,
  inventário de consumidores do bootstrap, backlog, QA Log e evidência
  coordenadora.

## Fora de escopo

- soft delete, anonimização, cascades ou política legal de retenção;
- exclusão em massa ou deleção iniciada por passageiro/responsável;
- redesenhar onboarding, convite, guardian ou vínculo com serviços;
- mudar permissões de `GUARDIAN`;
- resolver atomicidade geral de todos os writers de associação;
- push, deploy, banco remoto, staging, piloto ou produção.

## Critérios de aceite

- [x] Não existe mais boundary `buscar tenant -> deletar ID` em transações
  separadas.
- [x] Inexistente, sem empresa, tenant incorreto e reassignment vencedor recebem
  `404` indistinguível e não alteram conta ou artefatos.
- [x] Reassignment que vence a corrida impede o delete pelo tenant anterior.
- [x] Delete que obtém o lock primeiro bloqueia reassignment e mantém decisão
  consistente até o commit.
- [x] Sucesso retorna `204`; `GUARDIAN` permanece `403 ACTION_UNAVAILABLE`.
- [x] FK `23503` reverte tudo e não libera e-mail nem invalida artefatos.
- [x] PostgreSQL observa bloqueio real nos dois ordenamentos e no rollback.
- [x] Registry é removido, refresh é revogado, confirmação é consumida e device
  é invalidado apenas no delete confirmado.
- [x] Unitários, WebMvc, matriz estrita ratcheteada e smoke same-JAR passam na
  mesma versão limpa.
- [x] Contrato, RBAC, backlog, runbook e QA Log ficam sincronizados.

## Dependências

- `VR-SEC-004-EMAIL-NAMESPACE-001` concluído;
- V25 e seus triggers são pré-condição estrutural do runtime;
- operação concorrente e ordem global de locks estão congeladas acima;
- política de lifecycle/retention para passageiros referenciados será
  registrada separadamente e não bloqueia a correção da autorização atômica.

## Definition of Ready

- [x] resultado observável, risco e contrato HTTP definidos;
- [x] DML condicional, row count e autoridade V25 congelados;
- [x] writer concorrente e isolamento PostgreSQL definidos;
- [x] ordem de locks revisada contra confirmação, troca de e-mail e device;
- [x] três provas PostgreSQL determinísticas definidas;
- [x] regra sem cascade e rollback por FK explícita;
- [x] escopo, não-escopo, dependências, rollback e consumidores identificados;
- [x] paths adicionais conhecidos antes da implementação;
- [x] backend baseline limpo e trabalhos preexistentes fora do card preservados;
- [x] nenhum blocker humano ou externo impede implementação e prova locais.

## Plano sequencial

1. congelar testes de contrato `204/404/403` e unitários RED;
2. introduzir DML nativa condicional e remover o precheck/controller e a
   assinatura global;
3. alinhar consumidores de teste e provar rollback unitário;
4. adicionar as três provas PostgreSQL e ratchetear o perfil obrigatório de 43
   para 46 testes;
5. criar smoke same-JAR com tenant correto, cross-tenant e invariantes do banco;
6. executar gate limpo, Graphify, revisão independente e registrar evidência;
7. concluir apenas o card local; promoção permanece separada.

## Checkpoint de readiness — 2026-07-17

- card anterior `VR-SEC-005-RATE-LIMIT-TRUST-001` concluído tecnicamente no
  backend `46cd5e8a8dc7`, com evidência coordenadora `afaf31f8b337`;
- revisão de código confirmou o TOCTOU entre controller e service;
- revisão PostgreSQL independente comparou lock pessimista e DML condicional;
  a DML foi escolhida por reduzir o boundary a uma única decisão autoritativa;
- nenhum arquivo de código do delete foi alterado durante a análise;
- app `b53296b67dd0` e Maps `a0c2a659eee2` permanecem fora do escopo;
- push, deploy e bancos persistentes: `HOLD`.

## Ativação — 2026-07-17

- ativado a partir do checkpoint coordenador limpo
  `e4f5b395e77640078759fba6979c2f86b21b9260`;
- backend permanece limpo em `46cd5e8a8dc7d2a3f76771fb7d65aba286a71d06`,
  tree `eb504aee161f3529c1da105ad6681c2e0667c80e`;
- Impact Analysis, DML condicional, V25, contratos, três provas PostgreSQL,
  smoke, docs e rollback reconfirmados antes de código;
- rollback local antes de schema/dados: reverter somente commits deste card e
  retornar a `ready`; nenhuma migration nova está prevista;
- app com 51 alterações preexistentes e paths coordenadores conhecidos serão
  preservados e nunca usados como evidência deste card;
- um único WIP; nenhum push, deploy ou banco persistente autorizado.

## Encerramento local — 2026-07-17

- backend final: `6efd2e2f66cb5ac3784085d317ba98174750c815`, tree
  `142c51caebbc0eca9b93ed828c78924963c7f7c6`, limpo;
- commits: implementação `2ce65d09ba65`, documentação `2e106dda4390` e split
  de testes `6efd2e2f66cb`;
- gate completo: 921/921; integração PostgreSQL estrita: 46/46;
- smoke same-JAR: `passenger-delete-20260718T023942Z-30786`, resultado e
  cleanup `PASS`, sem mutação remota;
- o primeiro gate limpo expôs dois testes acima do ratchet de 350 linhas; os
  helpers foram extraídos, revisados e toda a prova foi repetida no SHA final;
- revisões independentes da implementação/smoke e do split de testes: `GO`,
  sem P1/P2;
- evidência canônica:
  `program/evidence/VR-SEC-002-PASSENGER-DELETE-ATOMIC-001.md`;
- `R-020` foi encerrado no escopo local; lifecycle referenciado foi
  separado em `R-021` e `VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-001`;
- nenhum push, deploy, banco remoto ou configuração viva foi alterado;
- piloto e produção permanecem `HOLD`.

## Rollback

Reverter o card reabre `R-020`; não autoriza promover enquanto o delete por ID
global puder ocorrer depois de uma checagem tenant stale.
