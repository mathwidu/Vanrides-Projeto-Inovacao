# VR-BE-OPERATIONAL-DATE-ADOPTION-001 — Adotar data operacional no restante do backend

## Resultado esperado

Dashboard, serviço recorrente e elegibilidade usam a mesma data operacional
configurada, sem depender do timezone implícito da JVM. Criação, reativação,
repetição ativa e remoção de `ServicoPassageiro` seguem uma única regra de
domínio, transacional e comprovada na mesma versão.

## Estado

`analyzed` — análise técnica, contratos, arquivos, testes, smoke, rollback e
evidência estão congelados. Falta apenas Produto aceitar ou substituir a
ADR-003; nenhum código de produto foi iniciado.

## Risco ou objetivo associado

`R-022`, épico `VR-BE-008`, gates `G2 — CONTRACTED` e `G5 — OPERABLE`.

## Contexto

A análise temporal de `VR-SEC-001-BE-READ` encontrou três usos genuinamente
operacionais de `LocalDate.now()` fora da fronteira de localização. A
descoberta aprofundada mostrou também que os dois caminhos que gerenciam
`ServicoPassageiro` duplicam transições e divergem:

- assignment pode criar ou reativar sem `dataInicio`;
- replace preserva o início antigo ao reativar e limpa o fim;
- essa combinação pode autorizar retroativamente o gap inativo;
- V16 contém linhas legadas com início nulo, interpretadas como ilimitadas;
- presença, paradas e viagens consomem `ativoNaData()`.

O modelo atual mantém uma linha por serviço/passageiro. Ele representa o
período corrente e não preserva histórico multi-intervalo. A documentação que
afirma o contrário deverá ser corrigida.

## Impact Analysis congelada

### Mudanças técnicas

- injetar `OperationalDateProvider` no fallback do dashboard;
- injetar o provider nos dois services de elegibilidade;
- capturar a data uma vez no request do dashboard e uma vez por transação nos
  writers;
- concentrar iniciar/reiniciar, manter ativo e encerrar o período em
  `ServicoPassageiro`;
- tornar as mutações de
  `PassengerServiceEligibilityManagementService` explicitamente
  transacionais;
- resolver os inputs tenant-scoped antes das mutações de elegibilidade e
  provar rollback do workflow externo quando ele já tiver mutado outro estado;
- manter o parâmetro explícito do dashboard como autoridade;
- corrigir API Matrix e textos que prometem histórico inexistente.

### Contratos afetados

Não há mudança de rota, payload, tipo, status, CORS, WebSocket ou papel
autorizado. `dataInicio` e `dataFim` continuam ISO `YYYY-MM-DD` anuláveis. A
mudança é semântica nas datas gravadas e na autorização temporal downstream.

Superfícies observadas:

- `GET /company-admins/dashboard/summary?data=`;
- `POST /company-admins/passageiros`;
- `GET/PUT /company-admins/passageiros/{id}/servicos`;
- `POST/PUT /company-admins/servicos-recorrentes`;
- `GET/PUT /company-admins/servicos-recorrentes/{id}/passageiros`;
- `POST /company-admins/access-requests/{id}/approve`;
- presença, resolução de paradas e provisionamento/sync de viagens.

Devem permanecer `401` sem auth, `403` para papel incorreto, `404` cross-tenant
sem mutação, `400` para IDs inválidos/duplicados e `409` para solicitação já
processada. `servicoIds` omitido na aprovação continua no-op.

O Flutter já aceita as datas como `String?`; nenhuma UI atual as interpreta.
Os fixtures dos dois consumidores administrativos serão ampliados com datas
ISO não nulas em worktree limpa. Não há mudança produtiva do app.

### Dados e schema

Este card adota writers corretos, mas não edita V16, não cria migration, não
faz backfill e não muda a finalidade de dados. Nulls legados, constraints,
coerência cross-tenant, concorrência e eventual histórico imutável foram
separados em `VR-BE-ELIGIBILITY-PERIOD-INTEGRITY-001`.

### Riscos

- timezone da JVM mascarar o provider;
- múltiplas chamadas ao provider atravessarem a virada de dia;
- reativação reabrir período inativo;
- domain methods mudarem inadvertidamente serialização ou auth;
- transação parcial deixar lote inconsistente;
- app aceitar o wire, mas fixture regressar;
- rollback de código não desfazer datas já persistidas;
- legado nulo permanecer fail-open e ser confundido com problema resolvido.

### Mitigação

- provider falso e datas exatas em testes;
- uma captura por transação;
- transições centralizadas no domínio;
- testes de domínio, services, controllers e consumidores downstream;
- smoke com JVM e operação em dias diferentes;
- PostgreSQL descartável e JAR único com hash;
- risco legado e entrega estrutural separados no programa;
- promoção em `HOLD` até gate, smoke e evidência final.

### Validação mínima

1. testes focados Maven definidos no DOR;
2. contract test do runner de smoke;
3. gates oficiais do backend e do app limpos, sem skip/failure/error;
4. smoke HTTP same-JAR com PostgreSQL descartável;
5. fixtures, testes focados e gate Flutter em checkout limpo;
6. API Matrix, backlog, QA Log, README e evidência sincronizados;
7. revisão independente de consistência da evidência.

### Rollback

Reverter provider injection, transação, métodos de domínio, testes e docs
backend em conjunto. Reverter separadamente o commit test-only do app. Não
editar V16 nem apagar evidências. Datas já gravadas por uma reativação não
voltam com rollback de código; uma promoção futura exige backup ou forward-fix
explícito. Dúvida de versão, dado ou timezone mantém piloto/produção em
`HOLD`.

## Escopo

- os três usos operacionais inventariados;
- semântica aceita de criação/reativação/repetição/remoção;
- interface profunda no domínio para impedir nova divergência;
- transação explícita do management service;
- contratos HTTP e compatibilidade Flutter;
- fixtures Flutter com datas não nulas, sem mudança de produção no app;
- testes, gate, smoke, documentação, QA Log e evidência.

## Fora de escopo

- nascimento, idade e outras datas civis/legais;
- clocks de duração, freshness, rate limit, TTL, token e auditoria;
- timezone por empresa;
- migration, backfill ou alteração de V16;
- constraints, lock, `@Version`, retry ou modelo histórico;
- OpenAPI ausente dessas rotas, já rastreado por `VR-BE-002`;
- push, CI remoto, deploy, banco persistente, piloto ou produção.

## Dependências

- `VR-SEC-001-BE-READ` concluído;
- `OperationalDateProvider` disponível e testado;
- backend baseline limpo em `6efdb618...`;
- aceite explícito da ADR-003.

## Definition of Ready

- [x] resultado esperado e critérios observáveis definidos;
- [x] escopo, fora de escopo e Impact Analysis fechados;
- [x] contratos, consumidores e auth/tenant mapeados;
- [x] dirty paths e conflitos preexistentes mapeados;
- [x] testes, gate, smoke, evidência e rollback definidos;
- [x] dívida estrutural separada em card canônico;
- [ ] ADR-003 aceita ou substituída por decisão equivalente.

## Critérios de aceite

- [ ] dashboard sem `data` usa exatamente o dia do provider;
- [ ] parâmetro explícito mantém precedência sem consultar o provider;
- [ ] nova elegibilidade começa no dia operacional;
- [ ] reativação preserva linha/ID, reinicia o período e não reabre o gap;
- [ ] repetição ativa segue exatamente a política aceita para início conhecido
  e legado nulo;
- [ ] remoção registra o mesmo dia operacional e repetição inativa é no-op;
- [ ] os dois caminhos administrativos usam a mesma invariável de domínio;
- [ ] o lote é transacional, usa uma única captura de data e um teste
  PostgreSQL prova rollback após a primeira mutação;
- [ ] `401/403/404/400/409`, tenant, payload, status, CORS e ISO permanecem;
- [ ] presença, stops e provisioning passam com a nova fronteira;
- [ ] datas civis/legais e clocks de duração permanecem fora do provider;
- [ ] fixtures, regressão e gate oficial Flutter passam em checkout limpo;
- [ ] gates backend/app limpos e smoke same-JAR passam sem skip/falha/erro;
- [ ] manifest contém SHA/tree/JAR hash, zonas/datas, schema e cleanup;
- [ ] API Matrix não promete histórico inexistente;
- [ ] README, backlog, QA Log, evidência e programa ficam sincronizados;
- [ ] revisão independente retorna zero finding P1/P2 aberto;
- [ ] piloto e produção permanecem `HOLD`.

## Arquivos previstos

A lista exata e executável está em `program/program.json`. Os grupos são:

- controller do dashboard;
- entidade `ServicoPassageiro` e os dois services de gestão;
- testes de provider, domínio, services, controllers, onboarding e downstream;
- `qa_operational_date_adoption_smoke.sh` e seu contract test;
- teste PostgreSQL de rollback intermediário e ratchet da matriz;
- gate e contract tests somente onde a composição mudar;
- duas fixtures Flutter e seus registros locais de backlog/QA;
- README, API Matrix, fluxo híbrido, QA Playbook, SpringBoot Cards e QA Log;
- card, ADR, DOR, evidência, risco e dashboard coordenador.

## Plano sequencial

1. obter aceite explícito e atualizar a ADR-003 para `accepted`;
2. revalidar backend SHA/tree limpos e ausência de dirty path conflitante;
3. transicionar atomicamente o card anterior para `waiting_external` e este
   card para único sucessor `ready`/`in_progress`;
4. escrever testes RED da matriz temporal e de contrato;
5. implementar provider, transação e interface de domínio em incrementos;
6. executar suíte focada e regressões downstream;
7. implementar e provar o runner de smoke sem Maven/JAR falso no contract test;
8. sincronizar documentação e fazer commits separados backend/app antes do
   freeze;
9. executar gates canônicos nos SHAs finais limpos;
10. executar smoke no mesmo JAR e regressão Flutter no SHA congelado;
11. registrar manifest cross-repo/evidência, revisão independente e commit
    coordenador;
12. concluir o card sem inferir release, piloto ou produção.

## Evidências

| Evidência | Local | Estado |
|---|---|---|
| baseline | `program/BASELINE.md` | observado |
| DOR e Impact Analysis | `program/evidence/VR-BE-OPERATIONAL-DATE-ADOPTION-001-DOR.md` | `NOT_READY`, decisão pendente |
| decisão | `program/decisions/ADR-003-operational-eligibility-period.md` | `proposed` |
| prova final | `program/evidence/VR-BE-OPERATIONAL-DATE-ADOPTION-001.md` | ainda não criada |

## Encerramento

- commits: pendentes;
- QA Logs backend/app: pendentes;
- backlogs locais backend/app: pendentes;
- documentação: análise coordenadora preparada; backend pendente;
- riscos: `R-022` aberto;
- blocker atual: aceite de Produto da ADR-003.
