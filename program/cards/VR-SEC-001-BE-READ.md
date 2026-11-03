# VR-SEC-001-BE-READ — Restringir localização e fechar fronteiras públicas de identidade

## Resultado esperado

Um passageiro autenticado só consegue consultar a localização mais recente do
motorista da sua própria `ViagemParada` no dia operacional e tenant atuais.
Vínculo legado, motorista não relacionado, alvo inexistente ou cross-tenant
permanecem ocultos com `404` antes da consulta ou retorno de GPS.
Cadastros anônimos de passageiro/motorista e retries do bootstrap administrativo
não aceitam campos internos nem alteram contas pendentes preexistentes. Todos os
consumidores oficiais do bootstrap enviam `companyName` novo por execução e
nenhum deles depende do `companySlug` legado para selecionar ou criar tenant.

## Estado

`done`

## Risco ou objetivo associado

`R-001`, `R-015`, épico `VR-SEC-001` e gate `G1 — TRUSTED`.

## Contexto

`GET /api/localizacao/{idMotorista}` exige autenticação e `companyId`, mas
`ensureCanReadMotorista` restringe apenas `DRIVER` e `GUARDIAN`. Um usuário
`PASSENGER` pode informar qualquer motorista existente no mesmo tenant e obter
seu GPS mais recente. Essa é a segunda fatia alta da baseline de autorização.

O consumidor Flutter usa o motorista da viagem selecionada e, quando não há
viagem, ainda possui um fallback legado para `Passageiro.motorista`. A revisão
independente demonstrou que esse campo não é uma origem confiável de
autorização: o cadastro público aceita `motorista` escolhido pelo cliente,
deriva a empresa desse motorista e todos os writers convergem na mesma FK sem
`source`, `approvedBy` ou outra proveniência. Por isso, vínculo direto não pode
conceder GPS. O fallback visual será removido no card app dependente; nesta
fatia, ausência de viagem governada deve negar por padrão.

Durante a implementação foi identificado um pré-requisito bloqueante: o app
seleciona a viagem por uma data local explícita, enquanto o endpoint de
localização não recebe essa data e precisa recalcular "hoje". Em runtimes UTC,
entre 21h e 24h de São Paulo, `Clock.systemDefaultZone()` poderia negar o
motorista legítimo do dia local e antecipar acesso ao motorista de uma viagem
do dia seguinte. O card foi reanalisado antes do commit técnico para incluir
uma única fonte de data operacional no núcleo viagem/localização.

A revisão final encontrou a mesma classe de falha nas outras entradas públicas
de identidade. `POST /motoristas` ainda fazia binding da entidade JPA e podia
executar merge sobre o `id` de uma vítima; além disso, repetição anônima do
e-mail de uma conta pendente sobrescrevia suas credenciais. Em
`POST /company-admins/bootstrap`, uma empresa com administrador ainda pendente
também aceitava substituição de nome, e-mail, telefone e senha. Os dois caminhos
podem terminar em takeover ou negação operacional e são pré-requisitos
bloqueantes descobertos antes do commit técnico, incorporados sem criar segundo
WIP.

O primeiro freeze técnico revelou outro bloqueante antes do encerramento: três
fixtures oficiais do backend, o E2E web do portal, seu mock e o modelo tipado do
app ainda permitiam ou enviavam apenas `companySlug`. Portanto, o commit
`4ca16df` é somente um snapshot intermediário, não evidência de fechamento. O
mesmo card passa a cobrir esses consumidores e só poderá ser aceito após novo
freeze, gates e smoke no mesmo JAR/SHA.

O freeze do consumidor revelou um bloqueante de esteira independente do
comportamento do card: no SHA app
`e716732efe5853731dab6ed401bad981cea09a70`, o gate falhava em checkout puro
porque lia evidência TTS ignorada por Git e um script localizado no diretório
pai. O pré-requisito `VR-QA-APP-GATE-HERMETIC-001` foi concluído: os dois gates
passaram no app `b84c7d547be2237a0c0ae4f0cf3a0a0128fc40fe`, em checkout isolado,
e o contrato route-plan foi ancorado pela coordenação em `d209ab2` com
evidência em `3c7c88f`. Depois da retomada, o runner backend, o probe do app e o
coordenador foram versionados e endurecidos. A prova final foi verificada nos
commits backend `e93a69f`, app `b53296b` e coordenação `9893bab`, com o mesmo
JAR, PostgreSQL por digest imutável e manifest funcional ancorado em
`program/evidence/VR-SEC-001-BE-READ.md`.

## Impact Analysis

### Mudanças técnicas

- introduzir uma policy pequena de leitura de localização do passageiro;
- consultar por `exists` exclusivamente a `ViagemParada` do
  passageiro em viagem de hoje, no mesmo tenant/motorista e somente nos status
  explicitamente permitidos `PLANNED`, `IN_PROGRESS` e `COMPLETED`;
- neutralizar `motorista` e `company` enviados em `POST /passageiros`, para que
  identidade pública volte a nascer sem tenant e sem vínculo operacional, como
  definido no fluxo híbrido canônico;
- substituir o binding direto da entidade por DTO público allowlist, impedindo
  mass assignment de `id`, flags de verificação, vínculo, tenant e demais
  campos internos;
- impedir que novo cadastro sobrescreva senha/dados de uma conta ainda pendente
  com o mesmo e-mail; duplicidade verificada ou não retorna o mesmo `409`;
- aplicar a mesma fronteira allowlist a `POST /motoristas`, construindo uma nova
  entidade no servidor sem `id`, empresa, foto ou flags internas;
- impedir que e-mail de motorista já existente, pendente ou verificado, seja
  reutilizado ou mutado por cadastro anônimo;
- fazer o bootstrap rejeitar qualquer empresa que já possua administrador,
  pendente ou verificado, antes de hash, save, revogação ou envio de e-mail;
- restringir o bootstrap público a `companyName` realmente novo: ausência de
  nome, tenant `default`, slug legado ou colisão com empresa preexistente não
  podem reivindicar um tenant;
- remover `companySlug` do DTO público de entrada e provar em WebMvc/smoke que
  um payload legado completo, sem `companyName`, recebe `400`;
- impor no PostgreSQL um único `company_admin` por empresa, após preflight de
  duplicados, para que duas requisições concorrentes não atravessem o check da
  aplicação;
- introduzir `OperationalDateProvider`, configurado por fuso IANA e com default
  versionado `America/Sao_Paulo`;
- alinhar atomicamente a policy, o default REST de `ViagemController`, o acesso
  de guardian e o provisionamento realtime ao mesmo dia operacional;
- falhar no startup quando o fuso configurado estiver vazio ou inválido;
- executar a policy antes de `MotoristaService` e
  `LocalizacaoMotoristaService`;
- manter as regras atuais de `DRIVER` e `COMPANY_ADMIN`; `GUARDIAN` também passa
  a exigir a viagem operacional, porque seu fallback direto deriva da mesma FK
  sem proveniência.
- tornar as suítes RBAC e Testcontainers resistentes a relatórios misturados,
  com limpeza seletiva, identificador de execução, ratchet de contagem e
  arquivamento local imutável por run antes de liberar o lock;
- extrair validação/arquivamento XML para um módulo profundo compartilhado e
  cobri-lo com testes negativos de run-id, freshness, contagem e mistura.
- migrar as três fixtures oficiais de bootstrap do backend para `companyName`
  único por execução e proteger esse contrato com teste de inventário;
- tornar `companyName` obrigatório no input tipado de bootstrap do app e remover
  `companySlug` do request público;
- rejeitar resposta `201` sem `companySlug` não vazio, em vez de assumir
  silenciosamente o tenant `default` no parser Dart ou aceitar `null` no E2E;
- usar no E2E web o builder canônico de payload e alinhar o mock por meio dos
  mesmos validadores de `companyName` e normalização de `companySlug`, sem
  alegar que processos Node distintos compartilham a mesma função em memória;
- ligar as regressões de contrato dos consumidores aos respectivos quality
  gates de backend e app.
- versionar o runner do smoke HTTP no backend, derivar o repositório do próprio
  script e exigir o SHA esperado externamente, sem caminho ou commit local
  hardcoded;
- executar, por hook explícito e sem registrar credenciais, um probe do app que
  usa `TripLiveLocationService` real contra o loopback do mesmo processo/JAR e
  prova `200`, `204` e a negação genérica de `404`;
- coordenar a prova em worktrees limpas dos commits exatos e publicar manifest
  funcional create-only com commits, trees, blobs, JAR, schema, resultados e
  checksums dos logs.

### Contrato afetado

- protocolo: REST/HTTP;
- endpoint: `GET /api/localizacao/{idMotorista}`;
- request: path param e autenticação permanecem iguais;
- sucesso autorizado: `200` com `latitude`, `longitude`, `updatedAt` e
  `freshness`, ou `204` quando não existe localização;
- negação nova: passageiro autenticado, porém não relacionado, recebe `404`
  genérico para não enumerar motorista/GPS;
- um `Passageiro.motorista` direto, sem `ViagemParada` operacional, também
  recebe `404`, ainda que tenha sido persistido no mesmo tenant;
- `POST /passageiros` mantém rota, status, campos públicos legítimos e resposta,
  mas usa DTO allowlist e ignora `id`, `motorista`, `company`, flags de
  verificação e qualquer propriedade fora desse contrato; vínculo posterior
  continua pelos fluxos de solicitação, convite e administração;
- `POST /passageiros` com e-mail já existente responde `409` sem mutar a conta,
  independentemente do estado de verificação; reenvio/troca de e-mail usam os
  endpoints dedicados;
- `POST /motoristas` mantém rota, `200`, payload e resposta legítimos, mas passa
  a aceitar somente nome, e-mail, telefone, senha e data de nascimento; campos
  internos desconhecidos são ignorados e qualquer e-mail existente retorna o
  mesmo `409` sem mutação;
- `POST /company-admins/bootstrap` mantém `201` apenas para nova empresa criada
  por `companyName`; empresa preexistente (com admin pendente, verificado ou sem
  admin) retorna `409`, e ausência de `companyName` retorna `400`; `companySlug`
  legado não pode selecionar tenant existente;
- colisão concorrente no primeiro bootstrap também retorna `409` e deixa apenas
  um administrador persistido para a empresa;
- fixtures e E2E que criam empresa enviam `companyName` novo a cada execução;
  `companySlug` continua sendo apenas dado derivado de resposta/sessão e nunca
  seletor no request de bootstrap;
- omissão de `data` nos endpoints de viagem passa a usar a data operacional de
  São Paulo; uma data explícita continua tendo precedência;
- `401`, CORS/preflight e shape de erro existente não mudam;
- `GET /api/localizacao/{idMotorista}/historico` continua proibido para
  `PASSENGER` e fica fora de escopo.

### Consumidores mapeados

- `TripLiveLocationService.fetchLatestLocation` aceita `200` e `204`;
- `HomePassageiroScreen` prioriza o motorista da viagem atual, mas ainda usa o
  vínculo de sessão como fallback não governado; a remoção e o estado sem
  viagem ficam rastreados em `VR-SEC-001-APP-DRIVER-SWITCH`;
- `TripLiveMapPanel` transforma outras respostas em erro genérico de leitura;
- nenhum DTO ou parser Flutter precisa mudar para o caso autorizado;
- a retenção de localização antiga quando `motoristaId` muda no painel foi
  identificada como defeito separado e será rastreada em card próprio.
- o cadastro Flutter de motorista já converte `409` em erro de validação e não
  precisa alterar o payload;
- o portal admin já transforma respostas não `201` e o campo `error` em erro de
  bootstrap; reenvio de verificação continua no fluxo dedicado;
- `AdminCompanyAdminBootstrapInput` passa a tornar impossível construir um
  request sem `companyName` no código Dart;
- as fixtures operacionais, geográfica e Igrejinha, além do E2E web real/mock,
  foram identificadas como consumidores diretos e entram no freeze deste card.

### Riscos

- exibir erro no app legado quando não existe viagem operacional, pois o
  fallback direto deixará de conceder localização;
- manter novos vínculos autoatribuídos no cadastro público e contaminar outras
  decisões operacionais mesmo após fechar o endpoint de GPS;
- criar split-brain perto da meia-noite se autorização e seleção de viagens
  usarem timezones diferentes;
- cristalizar o timezone do host em testes e produzir falso verde em UTC;
- autorizar automaticamente um status de viagem adicionado no futuro;
- autorizar por empresa em vez de por objeto e manter o achado aberto;
- consultar/retornar GPS antes da policy;
- vazar existência por diferença entre motorista inexistente e não relacionado;
- permitir mass assignment ou merge JPA por `id` em cadastro anônimo de
  motorista;
- permitir que retry anônimo assuma conta pendente de passageiro, motorista ou
  administrador;
- manter fixtures ou E2E verdes contra um mock permissivo enquanto o backend
  real rejeita o payload legado;
- reutilizar nome fixo em fixture e colidir com a empresa criada na execução
  anterior;
- confundir atomicidade com prova de posse: bootstrap de empresa preexistente
  permanece negado; sua ativação segura continua no card
  `VR-SEC-004-BOOTSTRAP-TRUST-001`;
- deixar a prova cross-repo executar por engano contra API remota, repositório
  sujo, outro JAR ou um probe temporário não pertencente ao commit registrado;
- ampliar o card para subscriptions STOMP ou política central sem a respectiva
  análise de contrato.

### Mitigação

- autorizar somente a viagem operacional, única fonte existente que representa
  passageiro e motorista efetivos com governança administrativa;
- limpar `motorista` e `company` antes de salvar todo cadastro público, inclusive
  quando reutiliza uma conta pendente ainda não verificada;
- construir a entidade no servidor para contas novas, sem aceitar ID do cliente;
- nunca reutilizar entidade pendente por mera repetição anônima do e-mail;
- construir também a entidade pública de motorista no servidor e rejeitar todo
  e-mail preexistente antes de qualquer save ou efeito de verificação;
- rejeitar bootstrap assim que qualquer admin da empresa-alvo for encontrado,
  com resposta idêntica para pending/verified e sem mutação;
- criar publicamente somente empresa com slug ainda inexistente e negar o
  fallback `default`/slug legado, evitando o modelo first-request-wins;
- proteger a mesma invariável no banco e traduzir colisão concorrente para o
  contrato genérico, eliminando o TOCTOU da checagem isolada;
- gerar nome de empresa único por execução em cada fixture/E2E e testar que
  nenhum payload oficial de bootstrap contém `companySlug`;
- fazer o mock rejeitar ausência de `companyName`, reproduzindo o contrato do
  backend em vez de mascarar regressões;
- restringir o probe funcional a URL de loopback, validar SHA/tree/limpeza dos
  dois repositórios e executar somente scripts versionados, com credenciais
  efêmeras transmitidas por ambiente e nunca impressas;
- gerar o manifest funcional somente depois do cleanup e de todos os resultados
  `PASS`, usando publicação atômica/create-only e anchor SHA-256 externo;
- usar uma fonte compartilhada e configurada de data operacional, com teste na
  fronteira `2026-07-17T02:30Z == 2026-07-16 America/Sao_Paulo`;
- usar allowlist de status e sempre escopar passageiro, viagem e motorista por
  `companyId`, inclusive contra relações persistidas inconsistentes;
- provar as novas consultas JPQL em PostgreSQL Testcontainers e atualizar
  deliberadamente a matriz exata do perfil;
- exigir o mesmo `run-id` nos XMLs das suítes e rejeitar artefatos ausentes,
  antigos, concorrentes ou de outra invocação;
- negar com o mesmo `404` antes dos serviços do alvo e de localização;
- cobrir same-tenant, cross-tenant, ausência, vínculos legados negados para
  passageiro/guardian, viagem permitida e regressão dos demais papéis;
- manter realtime e autorização central em cards separados do programa.

### Rollback

Antes de qualquer publicação compartilhada, reverter atomicamente os commits do
app/backend pode remover a policy, hardening, provider, wiring, testes e `V24`.
Depois que `V24` entrar no histórico de um ambiente compartilhado, rollback de
schema é exclusivamente forward-fix por nova migration; nunca apagar, renomear
ou editar `V24`. DTO, proteção de duplicidade e unicidade não podem ser
revertidos parcialmente. Em qualquer rollback, piloto e produção permanecem
`HOLD`.

## Escopo

- backend e app;
- leitura REST da última localização por `PASSENGER`;
- policy de relacionamento;
- queries `exists` e prova PostgreSQL;
- fronteiras de confiança de `POST /passageiros`, `POST /motoristas` e retry de
  `POST /company-admins/bootstrap`;
- data operacional compartilhada no núcleo viagem/localização;
- testes de policy, WebMvc, RBAC e tenant;
- proveniência e ratchet das execuções RBAC/Testcontainers;
- matrizes, backlog, QA Log e evidência.
- fixtures oficiais e tooling E2E que consomem o bootstrap público.

## Fora de escopo

- escrita REST, já fechada em `VR-SEC-001-BE-WRITE`;
- histórico de localização para passageiro;
- subscription/publicação STOMP e tópico amplo por empresa;
- alterar o contrato para um recurso trip-scoped;
- política central completa de autorização por objeto;
- retenção/minimização de GPS;
- data civil/legal de idade em contas, passageiros e motoristas;
- usos de data operacional em dashboard admin, serviço recorrente e
  elegibilidade, que serão tratados em card próprio após este pré-requisito;
- timezone por empresa/dispositivo e contrato REST trip-scoped;
- mudança visual ou de parser de resposta no app;
- modelar grant direto com proveniência/aprovação explícita ou sanear vínculos
  históricos; até isso existir, vínculo direto nunca autoriza GPS;
- prova de posse para criar o primeiro admin de empresa preexistente, rastreada
  em `VR-SEC-004-BOOTSTRAP-TRUST-001`;
- deploy, VPS, piloto ou produção.

## Dependências

- `VR-SEC-001-BE-WRITE` concluído;
- `VR-QA-TESTCONTAINERS-DOCKER-001` concluído;
- `VR-QA-APP-GATE-HERMETIC-001` concluído e comprovado em checkout isolado;
- smoke do consumidor real e manifest de backend SHA/tree/JAR, app SHA/tree e
  coordenador exato concluídos e verificados;
- não há decisão humana, credencial ou provedor pendente para o fechamento
  local deste card.

## Definition of Ready

- [x] resultado e critérios observáveis definidos;
- [x] contrato REST e consumidores Flutter mapeados;
- [x] fonte de vínculo decidida: somente `ViagemParada` operacional governada;
- [x] escopo, não-escopo e arquivos previstos definidos;
- [x] riscos, mitigação e rollback definidos;
- [x] dependências satisfeitas na transição que torna este card o próximo;
- [x] plano de testes, smoke e evidência definido;
- [x] trabalho preexistente do app foi inventariado e não será editado; apenas
  os caminhos explicitamente permitidos serão staged.
- [x] validação final do consumidor será feita em worktree limpa do app.
- [x] pré-requisito temporal reanalisado e incorporado sem abrir segundo WIP.
- [x] as três fronteiras públicas foram inventariadas e os P1 de merge/retry
  incorporados antes do freeze técnico.

## Critérios de aceite

- [x] Vínculo legado direto, isoladamente, não concede acesso à localização.
- [x] Guardian vinculado ao passageiro também não herda o motorista legado;
  somente a viagem operacional concede leitura.
- [x] Passageiro de viagem operacional de hoje lê o motorista dessa viagem com
  `200/204`, mesmo quando o vínculo legado diverge ou está ausente.
- [x] Cadastro público aceita somente o DTO allowlist; `id`, `motorista`,
  `company` e flags internas não chegam à entidade, que nasce com ID gerado,
  tenant/vínculo nulos e sem lookup global de motorista.
- [x] E-mail existente verificado ou pendente retorna o mesmo `409`; senha,
  perfil, vínculos e sessões da conta preexistente permanecem intactos.
- [x] Cadastro público de motorista usa DTO allowlist; payload com `id`,
  `company`, `emailVerified` e `fotoPerfilUrl` não alcança a entidade, que nasce
  com ID gerado, empresa/foto nulas e flags controladas pelo servidor.
- [x] E-mail de motorista existente, verificado ou pendente, retorna o mesmo
  `409` sem mudar nome, e-mail, telefone, hash, foto, empresa, verificação ou
  sessões e sem save/envio de e-mail.
- [x] Bootstrap de empresa com admin pendente ou verificado retorna o mesmo
  `409` genérico antes de hash/save/revogação/e-mail; bootstrap realmente novo
  continua `201`.
- [x] Bootstrap sem `companyName`, por `companySlug` legado, tenant `default` ou
  empresa preexistente não cria nem reivindica admin; empresa nova continua
  self-service com `201`.
- [x] As três fixtures oficiais do backend enviam `companyName` único, não
  enviam `companySlug` e são protegidas por regressão ligada ao quality gate.
- [x] O input tipado do app exige `companyName`; o service envia o campo e omite
  `companySlug`, com asserção do corpo HTTP.
- [x] Parser Dart e E2E falham se a resposta `201` omitir ou esvaziar
  `companySlug`; nenhum fallback `default` é fabricado no cliente.
- [x] O E2E web usa o builder canônico e o mock compartilha as regras de
  validação/normalização: nome único por run, ausência de `companySlug` no
  request e `400` quando `companyName` falta.
- [x] PostgreSQL garante um admin por empresa; duas tentativas concorrentes
  deixam uma única row e uma delas recebe o `409` genérico.
- [x] Viagem cancelada não concede acesso.
- [x] Viagem de outra data não concede acesso.
- [x] Qualquer status fora da allowlist explícita não concede acesso.
- [x] Passageiro não relacionado do mesmo tenant recebe `404` genérico.
- [x] Passageiro cross-tenant ou inexistente recebe exatamente o mesmo status,
  code, mensagem, content type e corpo do `404` same-tenant.
- [x] Negação ocorre antes de lookup do alvo e leitura de localização.
- [x] `DRIVER` e `COMPANY_ADMIN` mantêm seus contratos; `GUARDIAN` autorizado
  por viagem mantém `200/204`, enquanto o fallback legado é deliberadamente
  removido e recebe `404`.
- [x] Policy, viagem REST, guardian e realtime usam o mesmo dia operacional.
- [x] Fuso inválido ou vazio impede startup; o default é
  `America/Sao_Paulo` e está documentado.
- [x] Suítes RBAC e Testcontainers rejeitam relatórios de outra execução.
- [x] Cada suíte arquiva os bytes XML efetivamente validados, manifesto,
  hashes e SHA do código em `output/qa-evidence/<run-id>` antes de liberar o
  lock; Maven posterior não invalida essa prova.
- [x] App continua interpretando `200/204` sem mudança de código.
- [x] Gate backend, perfil PostgreSQL real e smoke HTTP no mesmo JAR/SHA passam.
- [x] QA Log, backlog, matrizes, commits e evidência ficam sincronizados.

## Arquivos previstos

- `src/backend/src/main/java/com/vanrides/vanrides/controller/LocalizacaoMotoristaController.java`
- `src/backend/src/main/java/com/vanrides/vanrides/controller/ViagemController.java`
- `src/backend/src/main/java/com/vanrides/vanrides/controller/PassageiroController.java`
- `src/backend/src/main/java/com/vanrides/vanrides/dto/PublicPassengerRegistrationRequest.java`
- `src/backend/src/main/java/com/vanrides/vanrides/controller/MotoristaController.java`
- `src/backend/src/main/java/com/vanrides/vanrides/dto/PublicDriverRegistrationRequest.java`
- `src/backend/src/main/java/com/vanrides/vanrides/service/MotoristaService.java`
- `src/backend/src/main/java/com/vanrides/vanrides/service/CompanyAdminService.java`
- `src/backend/src/main/java/com/vanrides/vanrides/service/CompanyService.java`
- `src/backend/src/main/java/com/vanrides/vanrides/controller/CompanyAdminController.java`
- `src/backend/src/main/java/com/vanrides/vanrides/dto/CompanyAdminBootstrapRequest.java`
- `src/backend/src/main/java/com/vanrides/vanrides/model/CompanyAdmin.java`
- `src/backend/src/main/resources/db/migration/V24__company_admin_one_per_company.sql`
- `src/backend/src/main/java/com/vanrides/vanrides/service/PassengerLocationReadPolicy.java`
- `src/backend/src/main/java/com/vanrides/vanrides/service/PassageiroService.java`
- `src/backend/src/main/java/com/vanrides/vanrides/service/OperationalDateProvider.java`
- `src/backend/src/main/java/com/vanrides/vanrides/service/GuardianAccessService.java`
- `src/backend/src/main/java/com/vanrides/vanrides/service/ViagemRealtimePublisherService.java`
- `src/backend/src/main/java/com/vanrides/vanrides/repository/ViagemRepository.java`
- `src/backend/src/main/java/com/vanrides/vanrides/repository/GuardianPassageiroRepository.java`
- `src/backend/src/main/resources/application.properties`
- `src/backend/.env.example`
- `src/backend/src/test/java/com/vanrides/vanrides/controller/LocalizacaoMotoristaControllerWebMvcTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/controller/PassageiroControllerWebMvcTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/controller/MotoristaControllerWebMvcTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/controller/CompanyAdminControllerWebMvcTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/controller/PassengerLocationReadWebMvcContract.java`
- `src/backend/src/test/java/com/vanrides/vanrides/controller/RbacWebMvcTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/controller/TenantIsolationWebMvcTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/service/PassengerLocationReadPolicyTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/service/PassageiroServiceTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/service/MotoristaServiceTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/service/CompanyAdminServiceTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/service/CompanyServiceTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/service/OperationalDateProviderTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/service/GuardianAccessServiceTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/service/ViagemRealtimePublisherServiceTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/controller/ViagemControllerWebMvcTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/controller/ViagemOperationalDateWebMvcTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/controller/ViagemTrackingContractWebMvcTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/controller/ViagemMotoristaNavigationRoutePlanControllerWebMvcTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/repository/RepositoryIntegrationTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/repository/CompanyAdminBootstrapConcurrencyIntegrationTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/repository/PassengerLocationReadRepositoryContract.java`
- `src/backend/scripts/qa_integration_profile.sh`
- `src/backend/scripts/qa_rbac_suite.sh`
- `src/backend/scripts/qa_report_evidence.py`
- `src/backend/scripts/qa_report_evidence_test.py`
- `src/backend/scripts/ci_quality_gate.sh`
- `src/backend/scripts/ci_quality_gate_test.py`
- `src/backend/scripts/seed_operational_service_day_fixture.sh`
- `src/backend/scripts/seed_igrejinha_route_lab.sh`
- `src/backend/scripts/seed_geographic_route_fixture.sh`
- `src/backend/scripts/bootstrap_fixture_contract_test.py`
- `src/backend/scripts/qa_admin_portal_smoke.sh`
- `src/backend/scripts/qa_location_read_smoke.sh`
- `src/backend/scripts/qa_location_read_smoke_test.py`
- `src/backend/docs/quality-ratchet-program.md`
- `src/backend/README.md`
- `src/backend/backlog/API-Contract-Matrix.md`
- `src/backend/backlog/RBAC-Permission-Matrix.md`
- `src/backend/backlog/QA-Playbook.md`
- `src/backend/backlog/SpringBoot-Cards.md`
- `src/backend/backlog/QA-Execution-Log.md`
- `src/aplicativo-motorista/lib/features/admin/domain/admin_company_admin_bootstrap_input.dart`
- `src/aplicativo-motorista/lib/features/admin/domain/admin_company_admin_bootstrap_result.dart`
- `src/aplicativo-motorista/test/features/admin/admin_portal_auth_service_test.dart`
- `src/aplicativo-motorista/tool/admin_portal_bootstrap_contract.mjs`
- `src/aplicativo-motorista/tool/admin_portal_bootstrap_contract_test.mjs`
- `src/aplicativo-motorista/tool/admin_portal_web_e2e.mjs`
- `src/aplicativo-motorista/tool/admin_portal_web_e2e_mock_server.mjs`
- `src/aplicativo-motorista/scripts/qa_admin_portal_web_e2e.sh`
- `src/aplicativo-motorista/scripts/qa_trip_live_location_real_backend.sh`
- `src/aplicativo-motorista/scripts/qa_trip_live_location_real_backend_test.py`
- `src/aplicativo-motorista/tool/qa_trip_live_location_real_backend_test.dart`
- `src/aplicativo-motorista/scripts/ci_quality_gate.sh`
- `src/aplicativo-motorista/scripts/ci_quality_gate_test.py`
- `src/aplicativo-motorista/docs/api-contract-consumption.md`
- `src/aplicativo-motorista/backlog/QA-Playbook.md`
- `src/aplicativo-motorista/backlog/Flutter-Cards.md`
- `src/aplicativo-motorista/backlog/QA-Execution-Log.md`
- `scripts/qa_location_read_functional_integration.py`
- `scripts/qa_location_read_functional_integration_test.py`

## Plano sequencial

1. escrever RED que reproduz leitura same-tenant por passageiro não relacionado;
2. implementar o provider de data operacional e alinhar policy, defaults REST,
   guardian e realtime no mesmo incremento;
3. implementar query `exists` com tenant triplo e allowlist de status somente
   para viagem do dia operacional, provando que vínculo direto isolado nega;
4. introduzir DTOs allowlist nos cadastros públicos, rejeitar toda duplicidade
   sem mutação, bloquear retry de admin pendente e chamar a policy antes de
   qualquer serviço do alvo/GPS;
5. provar a JPQL em PostgreSQL, ajustar a matriz exata e fechar regressões de
   papéis, tenant e consumidor Flutter;
6. migrar e testar todos os consumidores oficiais do bootstrap no backend e
   app, incluindo mock e E2E;
7. endurecer a proveniência das suítes e executar testes focados, RBAC, gates,
   perfil PostgreSQL, smoke do JAR exato e probe Flutter real em worktrees
   limpas;
8. atualizar matrizes, backlog, QA Log, evidência e programa.

## Validação mínima

- `PassengerLocationReadPolicyTest`;
- `OperationalDateProviderTest`, `GuardianAccessServiceTest` e
  `ViagemRealtimePublisherServiceTest`;
- `LocalizacaoMotoristaControllerWebMvcTest`, `RbacWebMvcTest` e
  `TenantIsolationWebMvcTest`;
- testes focados de controller/service para passageiro, motorista e company
  admin, além de `SecurityFilterWebMvcTest`, `FilterAuthBasicTest` e
  `BasicRateLimitFilterTest`;
- slices WebMvc do `ViagemController`, incluindo data omitida e explícita;
- `./scripts/qa_rbac_suite.sh`;
- `./scripts/ci_quality_gate.sh`;
- `./scripts/qa_integration_profile.sh` com matriz real e zero skips;
- `python3 scripts/bootstrap_fixture_contract_test.py` e prova de wiring no gate
  do backend;
- `node --test tool/admin_portal_bootstrap_contract_test.mjs`, teste Dart do
  service e prova de wiring no gate do app;
- `flutter test test/features/tracking/data/trip_live_location_service_test.dart`
  e testes de home/tracking/admin em worktree limpa;
- `qa_gate_card.sh` e smoke específico no JAR do SHA técnico contra PostgreSQL
  descartável;
- testes de contrato não-Docker dos runners e do manifest funcional, seguidos
  da execução cross-repo real com URL exclusivamente loopback;
- `git diff --check`.

## Evidência esperada

- baseline e RED do acesso same-tenant indevido;
- matriz de casos autorizados/negados e ausência de interação após negação;
- RED e prova de não mutação para `id` de motorista, e-mails pending/verified e
  bootstrap repetido/concorrente, incluindo row/hash/vínculos invariáveis no
  smoke;
- consulta JPQL real com escopo triplo de tenant, data operacional controlada,
  vínculo direto isolado negado e nova contagem exata do perfil nos scripts,
  ratchet e README;
- prova de fronteira UTC/São Paulo e de fuso inválido no startup;
- `run-id` único e comum em todos os XMLs usados como evidência;
- comandos, contagens, SHA, JAR/digest/schema e ambiente;
- smoke autorizado `200/204` e não relacionado `404` na mesma versão;
- cleanup de processo/container/portas;
- inventário completo dos consumidores de bootstrap e regressões que falham se
  um payload oficial voltar a usar `companySlug`;
- limites explícitos para realtime, histórico e ambiente remoto.

## Encerramento

- estado: `DONE`; C1–C28 comprovados e sincronizados;
- commits backend finais: `e33eaef`, `e4406ca`, `447930b`, `e93a69f`;
- commits app finais: `b21a1fb`, `7b5d4d4`, `b53296b`;
- commits finais da coordenação: `37cbd30`, `f45d632`, `9893bab`, `3855488`;
- QA Logs, backlogs e matrizes: sincronizados nos commits documentais finais;
- evidência: `program/evidence/VR-SEC-001-BE-READ.md`;
- prova final: run `location-read-20260717T103049Z-97670`, manifest SHA-256
  `ffb324e6a22b90049bfc19c27d96b70174a561cc767afdf3bdc1ad2b40d27e94`;
- bloqueantes atuais: nenhum;
- próximo card após o checkpoint idle:
  `VR-QA-BE-GATE-NO-SKIP-001`;
- riscos: `R-001`, `R-003`, `R-011`, `R-014` e `R-015` permanecem
  `mitigando`; nenhum PASS local autoriza piloto ou produção.
