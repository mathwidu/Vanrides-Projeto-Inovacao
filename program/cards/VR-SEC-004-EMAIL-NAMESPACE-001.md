# VR-SEC-004-EMAIL-NAMESPACE-001 — Tornar o namespace de e-mail global e atômico

## Resultado esperado

Um e-mail autenticável identifica no máximo uma conta em todo o sistema,
independentemente de papel. Cadastro, tentativa de criação administrativa,
bootstrap, troca de e-mail, reenvio, confirmação e login deixam de depender de
ordem de busca e não permitem identidades concorrentes com o mesmo endereço
normalizado. Conhecer o e-mail não autoriza adotar nem transferir uma conta.

## Estado

`done` — implementação final congelada no backend `4824f54`, com gates, smoke
same-JAR, consumidor Flutter e revisão independente em `GO`; evidência
versionada no checkpoint coordenador `2fe351b`. Promoção da migration em
qualquer banco persistente continua em `HOLD` até preflight específico.

## Risco ou objetivo associado

`R-017`, `R-015`, épico `VR-SEC-004` e gate `G1 — TRUSTED`.

## Contexto e baseline

Admin, passageiro, responsável e motorista possuem tabelas e unicidades
separadas. Em `guardian`, a unicidade é apenas `(company_id, email)`. Login e
reenvio consultam repositórios em sequência, e os preflights parciais dos
writers são sujeitos a TOCTOU. Há ainda troca direta de e-mail no legado
`PUT /passageiros/{id}`.

A decisão estrutural está congelada em
`program/decisions/ADR-001-global-login-email-namespace.md`. O baseline de
implementação é o backend limpo
`528a4c40d4eb76c1eacf66334ba3b51a38eef650`, tree
`54262de3cc21db909d5c5308aa3e09df7f6cc453`. O checkpoint coordenador que
comprova o gate PostgreSQL fail-closed é
`29257a35a22128a4937bae4cd8dcf9f63c7e9098`. Os paths preexistentes do app e
da coordenação permanecem preservados e fora do escopo.

## Decisões congeladas

- o PostgreSQL é a autoridade da invariável por meio de `login_identity`,
  checks e triggers nas quatro tabelas de conta;
- todos os `guardian`, inclusive sem senha, reservam o endereço; contato
  reutilizável deverá ser separado do conceito de conta em trabalho futuro;
- normalização remove somente espaço ASCII externo, aceita apenas ASCII
  imprimível, exige um único `@`, limita a 254 bytes e aplica lowercase
  determinístico (`Locale.ROOT`/collation `C`), sem regras de provedor;
- a aplicação não duplica a reserva: `LoginIdentityService` normaliza, resolve
  owner, verifica ownership e traduz somente constraints conhecidas;
- colisão sequencial ou concorrente retorna `409 EMAIL_ALREADY_IN_USE` com
  mensagem genérica; demais erros de integridade continuam falhando;
- a V25 faz preflight e backfill em uma transação, sem escolher vencedor,
  mesclar, apagar ou renomear contas e sem incluir e-mail bruto no erro;
- hard delete confirmado revoga artefatos de sessão/verificação e libera a
  reserva no mesmo commit; rollback preserva tudo;
- login, Basic Auth e reenvio resolvem primeiro a identidade global e carregam
  exatamente um papel/ID, sem fallback; confirmação e refresh verificam a
  correspondência entre owner, papel, ID e e-mail;
- depois de aplicada, a V25 é forward-only: rollback de código preserva
  tabela, checks e triggers.

## Impact Analysis

### Mudanças técnicas

- criar entidade/repositório do registry, normalizador canônico, exceção tipada
  e a interface profunda `LoginIdentityService`;
- criar `V25__global_login_email_namespace.sql` com lock de escrita, preflight
  sanitizado, verificação canônica, backfill 1:1 sem reescrita, checks, triggers
  e validação final;
- tornar todos os writers de criação, bootstrap, alteração e deleção
  transacionais e usar `saveAndFlush` para observar conflito no boundary;
- impedir adoção por e-mail nos endpoints administrativos; contas já
  existentes só mudam vínculo por fluxos autenticados de convite/request ou
  por update tenant-scoped do ID já autorizado;
- proteger registro/reativação de `device_token` com owner exato e lock do
  registry, inclusive para writer antigo em rolling deploy;
- substituir a precedência em `LoginUserDirectoryService`, `AuthService`,
  Basic Auth e reenvio por lookup do registry;
- proteger confirmação, refresh e o `PUT /passageiros/{id}` contra owner
  inconsistente ou bypass;
- ratchetear as novas integrações Testcontainers no perfil obrigatório e criar
  smoke funcional durável do namespace.

### Contratos e consumidores afetados

- rotas e payloads de sucesso permanecem compatíveis;
- cadastro público, criação administrativa, bootstrap e troca de e-mail passam
  a produzir `409` genérico também para colisão cross-role;
- e-mail sintaticamente fora da regra congelada produz `400` antes de gravar;
- login não autentica conta escolhida por ordem; reenvio continua retornando
  `202` sem enumerar existência, papel, tenant, senha ou verificação;
- o Flutter não deve precisar de mudança produtiva: seus parsers existentes de
  `message`, `error` e texto serão revalidados em checkout limpo do SHA
  `b53296b67dd0dc6ddb4b1125dcc64caadb8c7c6c`.

### Riscos e mitigação

- **legado inválido ou ambíguo:** V25 aborta integralmente, publica somente
  contagens/categorias e encaminha a um runbook humano; promoção fica `HOLD`;
- **corrida entre tabelas:** primary key global e triggers fazem exatamente uma
  transação vencer e revertem a conta perdedora sem órfãos;
- **regra Java/SQL divergente:** matriz parametrizada compara as duas funções,
  inclusive locale turco, limites e caracteres rejeitados;
- **rolling deploy:** triggers mantêm o registry para writers antigos que ainda
  escrevem diretamente nas tabelas existentes;
- **erro de integridade mascarado:** somente nomes de constraints conhecidos
  viram `409`; todo erro diferente é relançado;
- **reuso após delete:** revogações, delete e liberação compartilham a mesma
  transação; tokens antigos continuam vinculados ao owner anterior;
- **contenção:** a serialização ocorre apenas no índice do mesmo endereço, com
  seção crítica de aplicação mínima.

## Escopo

- cadastro público e criação administrativa de `COMPANY_ADMIN`, `PASSENGER`,
  `GUARDIAN` e `DRIVER`;
- rejeição genérica de tentativa de adoção administrativa por e-mail; vínculo
  de conta pública existente permanece nos fluxos explícitos de
  convite/request e aceite do próprio usuário;
- bootstrap público atual, sem ampliar seu nível de assurance;
- todas as trocas de e-mail, inclusive fluxo pendente e
  `PUT /passageiros/{id}`;
- deleção física existente, revogação transacional e reuso seguro do endereço;
- login, Basic Auth, refresh, reenvio e confirmação de e-mail;
- normalização/validação compartilhada, migration, preflight, backfill,
  constraints, triggers e reconciliação operacional;
- contrato de erro genérico, testes, smoke, documentação, backlogs, QA Log e
  evidência same-version.

## Fora de escopo

- mudar payloads de sucesso ou padronizar todos os erros como Problem Details;
- provar posse organizacional ou redesenhar o bootstrap —
  `VR-SEC-004-BOOTSTRAP-TRUST-001`;
- alterar entropia ou ciclo de vida de convites;
- executar reconciliação automática, escolher vencedor ou mutar dados pessoais
  de ambientes persistentes;
- promover migration, fazer push, deploy, piloto ou produção;
- suportar e-mail EAI/Unicode — rastreado em `VR-A11Y-003`;
- definir retenção/eliminação completa ou tombstones de auditoria —
  `VR-PRIV-003`;
- reestruturar contato compartilhado como entidade separada.

## Dependências

- `VR-SEC-001-BE-READ`: `done`;
- `VR-QA-BE-GATE-NO-SKIP-001`: `done` neste checkpoint, com gate PostgreSQL
  obrigatório comprovado no backend final `528a4c40`;
- ADR-001: `accepted`;
- backend limpo e paths autorizados congelados;
- nenhuma credencial, decisão humana ou consulta a banco real é necessária para
  a implementação e validação locais;
- preflight e eventual reconciliação de cada banco persistente são dependências
  da promoção, não da execução deste card.

## Definition of Ready

- [x] resultado e critérios observáveis definidos;
- [x] arquitetura, normalização, elegibilidade de `guardian`, delete/reuso e
  rollback aceitos no ADR-001;
- [x] escopo, não-escopo, contratos, consumidores e repositórios afetados
  congelados;
- [x] riscos, mitigação, dependências e decisão fail-closed documentados;
- [x] plano de implementação, testes PostgreSQL, smoke e evidências definido;
- [x] backend baseline limpo; dirty paths preexistentes do app e coordenação
  identificados e excluídos do card;
- [x] nenhuma dependência local pendente ou blocker oculto.

## Ativação

- ativado em 2026-07-17 como único WIP global;
- card anterior `VR-QA-BE-GATE-NO-SKIP-001`: `done`; `R-014` encerrado apenas
  no escopo estrutural/local;
- checkpoints ready: `78bd4cda105eab64b6745f801493c344859be6f9` e dashboard
  `c6b6303b29840d025118c933acd617a3f138267a`;
- o dashboard ready foi gerado sobre `78bd4cd` e versionado por `c6b6303`; essa
  diferença auto-referencial da projeção é esperada e não altera o estado
  canônico;
- revisão independente: `GO`, sem P1/P2, controle 9/9 e `check` com zero erro;
- baseline backend limpo: commit
  `528a4c40d4eb76c1eacf66334ba3b51a38eef650`, tree
  `54262de3cc21db909d5c5308aa3e09df7f6cc453`;
- consumidor app congelado em
  `b53296b67dd0dc6ddb4b1125dcc64caadb8c7c6c`; seus paths preexistentes não
  serão usados como evidência nem alterados;
- paths não rastreados conhecidos da coordenação (`.playwright-mcp/`,
  `docs/analysis/` e `docs/faculdade/entrega-final-2026-06/`) permanecem fora
  do card;
- 74 paths backend e 6 paths de coordenação autorizados foram congelados em
  `program/program.json`; expansão exige reanálise antes da alteração;
- ADR-001, Impact Analysis, dependências, plano sequencial, validação e
  rollback foram reconfirmados;
- rollback antes da V25: reverter somente commits do card e retornar a
  `ready`; após V25 compartilhada, somente código compatível e migrations
  forward-only;
- push, reconciliação real, deploy, piloto e produção: `HOLD`.

## Reanálise de implementação — 2026-07-17

- a proteção de rolling deploy foi aprofundada: os triggers da V25 movem ou
  liberam primeiro o owner exato no registry e, ainda na mesma transação,
  revogam refresh tokens, consomem verificações pendentes e invalidam device
  tokens em troca de e-mail ou hard delete, inclusive para writers JDBC;
- a aplicação normaliza, valida ownership, mantém o estado pendente em memória,
  executa `saveAndFlush` e só depois emite uma nova verificação, evitando que o
  token recém-criado seja consumido pelo trigger da própria troca;
- somente `pk_login_identity_email` e as quatro constraints de e-mail das
  contas podem virar `409`; `uk_login_identity_owner`, FKs, checks e mensagens
  textuais semelhantes permanecem falhas fechadas;
- a prova PostgreSQL foi dividida em helper package-private e três classes
  concretas de quatro testes cada: migration, triggers e concorrência. O perfil
  estrito passa de 13 para 25 testes obrigatórios;
- o `PUT /passageiros/{id}` será movido para um boundary transacional do
  service; por isso seus testes de acesso e RBAC e o teste de construção do
  writer administrativo entram explicitamente no escopo;
- a revisão independente confirmou o grafo sem ciclos e indicou os quatro
  paths adicionais antes de qualquer alteração. O escopo autorizado passa de
  63 para 67 paths backend; coordenação permanece em 6.

## Reanálise de segurança da implementação — 2026-07-17

- três revisões independentes emitiram `NO-GO` antes do primeiro commit de
  implementação; nenhum achado foi aceito como dívida silenciosa do card;
- a premissa de adoção administrativa foi invalidada: o registry prova qual
  identidade ocupa o endereço, não autorização para transferir tenant,
  sobrescrever perfil ou senha. `create` administrativo passa a exigir e-mail
  globalmente livre; qualquer owner existente recebe o mesmo
  `409 EMAIL_ALREADY_IN_USE`. Motorista e passageiro existentes usam os fluxos
  autenticados de convite/request já disponíveis; guardian existente usa o
  update tenant-scoped por ID;
- a V25 passa a reconciliar devices ativos históricos e instala uma guarda
  `BEFORE INSERT OR UPDATE` que normaliza aliases, exige `(email, role, id)`
  exato e segura o registry com `FOR KEY SHARE`. O service novo adquire o lock
  do registry antes da linha de device, prevenindo a ordem inversa no código
  atual; writer antigo permanece fail-closed durante rolling deploy;
- uma classe PostgreSQL adicional prova, com timeouts de bloqueio e ambos os
  ordenamentos, registro/reativação de device contra troca, delete, commit e
  rollback. As corridas existentes também passam a demonstrar bloqueio, a
  migration prova writer V24 concorrente e a matriz Java/SQL cobre locale
  turco, limites e rejeições. O perfil estrito sobe de 25 para 31 testes;
- login executa exatamente uma verificação BCrypt de custo 12 para qualquer
  tentativa não vazia, usando hash fictício quando não há credencial real;
  isso remove o sinal de existência/acesso sem teste frágil de tempo;
- reenvio sempre entra na mesma fila local limitada e retorna antes de lookup,
  token ou provedor; rejeição/saturação continua opaca e sem PII em log. O
  worker mantém a validação global e o endpoint continua sempre `202`;
- a matriz WebMvc negativa será completada para todos os boundaries `400/409`,
  confirmação inválida, troca pendente e reenvio vazio/inválido/desconhecido;
- sete paths foram acrescentados antes de edição: service/test da fila,
  service/test de device token, nova integração concorrente e remoção do
  policy/test de reutilização de guardian. O escopo passa de 67 para 74 paths
  backend; coordenação permanece em 6.

## Reanálise pós-validação — 2026-07-17

- a revisão do boundary de autorização encontrou uma corrida residual: um JWT
  ou Basic Auth emitido/autenticado para o e-mail anterior ainda poderia seguir
  quando o registry já apontasse o mesmo owner para outro endereço;
- `AuthorizationService` passa a exigir que o e-mail autenticado coincida
  exatamente com o snapshot atual carregado pelo diretório, inclusive quando o
  endpoint admite acesso ainda não verificado; os testes cobrem Bearer stale e
  a troca concorrente entre autenticação Basic e o segundo snapshot;
- o primeiro `program check` após esse hardening detectou corretamente o path
  produtivo fora da allowlist. A expansão foi registrada imediatamente, antes
  de qualquer nova alteração ou evidência final; o teste já fazia parte do
  escopo. A allowlist passa de 74 para 75 paths backend e permanece em 6 paths
  de coordenação;
- a fila agora compacta e normaliza o endereço antes de capturá-lo no trabalho,
  possui capacidade e conjunto de pendências limitados, coalesce duplicatas e
  limita avisos agregados. Falha do provedor consome apenas o token recém-criado
  e preserva links anteriores; sucesso só então revoga os anteriores;
- hashes BCrypt reais são aceitos apenas no custo 12 congelado. Valor ausente,
  malformado ou com custo diferente usa o mesmo hash fictício de custo 12 e não
  amplia trabalho criptográfico controlável pelo banco;
- o rate limit com chave confiável, teto de cardinalidade e limite de corpo
  continua pertencendo a `VR-SEC-005-RATE-LIMIT-TRUST-001`; durabilidade,
  coordenação entre réplicas e entrega após commit pertencem a `VR-BE-005`.
  Ambos são dependências de promoção da composição, não serão declarados
  resolvidos por este card e mantêm piloto/produção em `HOLD`.

## Reanálise de concorrência de device — 2026-07-17

- a releitura independente aprovou a V25 isolada, mas bloqueou o escopo
  combinado ao demonstrar uma corrida no primeiro registro do mesmo
  `token_hash`: duas transações podiam observar ausência e a perdedora no
  índice `uk_device_token_hash` propagava `500`;
- a correção preserva a ordem global `identity -> token`: após travar o owner
  exato, um insert idempotente no índice real cria ou aguarda a chave, e uma
  leitura pessimista da linha serializa a atualização. Mesmo owner e owners
  diferentes serão provados em PostgreSQL, sem traduzir integridade
  desconhecida como conflito genérico;
- `DeviceTokenRepository.java` foi acrescentado antes da implementação dessa
  correção. A allowlist passa de 75 para 76 paths backend; nenhuma nova rota,
  tabela, migration ou resposta de sucesso entra no escopo;
- a matriz de aliases SQL será ampliada para formas portuguesas decompostas e
  whitespace ASCII de boundary reconhecido pelo Java. A documentação limitará
  a alegação de paridade ao conjunto versionado, mantendo formas desconhecidas
  em fail-closed;
- o run incremental `integration-20260717T183747Z-8954` permanece evidência
  diagnóstica: `31/31`, sem skip/falha/erro, mas `clean=false` e sem digest do
  diff. A contagem será ratcheteada e repetida somente após commit limpo para a
  evidência de fechamento.

## Reanálise dos débitos extraídos — 2026-07-17

- a revisão de fechamento confirmou quatro problemas que precisam de cards
  verticais próprios, sem ampliar silenciosamente esta implementação: limite
  de request body antes do parser, outbox de verificação durável, delete de
  passageiro tenant-scoped atômico e epoch de autenticação/sessão;
- os executáveis são `VR-SEC-005-RATE-LIMIT-TRUST-001`,
  `VR-BE-005-EMAIL-VERIFICATION-OUTBOX-001`,
  `VR-SEC-002-PASSENGER-DELETE-ATOMIC-001` e
  `VR-SES-001-AUTH-EPOCH-001`, associados respectivamente a `R-016`, `R-009`,
  `R-020` e `R-002`;
- sete cards existentes ou planejados foram acrescentados à allowlist antes de
  qualquer edição. O escopo permanece em 76 paths backend e passa de 6 para 13
  paths de coordenação;
- a sequência numérica histórica de seis migrations foi invalidada: card `analyzed` não reserva
  número Flyway. Cada card revalida o maior número presente/aplicado e aloca o
  próximo contínuo somente em seu próprio freeze de `ready`; dependências
  existem apenas por precondição real de domínio/schema;
- a extração desses débitos não os resolve, não autoriza promoção e não cria
  outro WIP. Todos permanecerão `analyzed`; piloto e produção continuam
  `HOLD`.

## Reanálise de compatibilidade bcrypt — 2026-07-17

- a revisão independente encontrou um P2 de integridade/disponibilidade:
  `AuthService` aceita bcrypt `$2a$`, `$2b$` e `$2y$` com custo 12, mas o
  persistidor de passageiro tratava apenas `$2a$` como hash armazenado. Um
  update sem troca de senha podia hashear novamente um `$2b$`/`$2y$` legado e
  bloquear a conta;
- a mesma heurística permitia que cadastro de passageiro, motorista, primeiro
  admin ou responsável enviasse uma string com aparência de `$2a$` e ela fosse
  confiada como hash pronto, contornando a política de senha do servidor;
- a correção separa origem em vez de inferir pelo prefixo: criação ou troca
  explícita sempre trata o valor como senha bruta e aplica bcrypt custo 12;
  update sem senha preserva a credencial já carregada da entidade;
- testes parametrizados cobrem autenticação e preservação de `$2a$`, `$2b$` e
  `$2y$`, entrada com aparência de hash nos quatro fluxos e update sem troca de
  senha. A execução focada inicial passou 78/78, ainda em árvore dirty; o gate
  limpo da mesma versão continua obrigatório para fechamento.
- a V25 agora também conta formatos/custos bcrypt incompatíveis antes de
  qualquer DDL. Guardian sem acesso configurado continua permitido; qualquer
  outro hash fora de `$2[aby]$12$` aborta com `23514` e somente uma contagem
  sanitizada. A matriz PostgreSQL dirty passou `35/35` no run
  `integration-20260717T195718Z-99828`; isso ainda não substitui a prova limpa.
- o runner same-JAR foi ampliado para criação/colisão de guardian, registro e
  reatribuição serializada de device, owner SQL exato, invalidação após troca e
  delete e rejeição de Bearer stale. Seu contrato estático passou `23/23`; a
  execução real fica reservada ao SHA commitado e worktree limpa.
- o preflight bcrypt fez a matriz executar também migrations legadas até a
  V25. Duas fixtures históricas usavam hashes deliberadamente fake; a primeira
  execução canônica falhou 2/35 e provou o novo fail-closed. O controle detectou
  que `LegacyCompanyMigrationIntegrationTest.java` ainda estava fora da
  allowlist; nenhum novo backend edit foi feito até registrar esta expansão de
  76 para 77 paths. As fixtures passaram a usar o hash de teste custo 12 e a
  repetição canônica terminou 35/35, sem skip/falha/erro.

## Reanálise de proveniência da credencial — 2026-07-17

- a revisão independente posterior confirmou zero P0/P1/P2, mas rejeitou como
  fechamento completo a validação por regex de uma credencial recebida em um
  save genérico: formato bcrypt prova compatibilidade, não prova que o valor
  veio do banco;
- o persistidor genérico será restrito a criação e sempre aplicará bcrypt a
  qualquer entrada. Atualizações carregarão a entidade internamente e nunca
  aceitarão uma credencial persistida fornecida pelo chamador; senha nova será
  recebida como entrada bruta separada e sempre re-hasheada;
- reatribuição e remoção de motorista usarão operações tenant-scoped sem campo
  de senha. Isso elimina também a possibilidade latente de uma futura chamada
  preservar como hash uma senha bruta deliberadamente formatada como bcrypt;
- `CompanyAdminVinculoService.java` e seu teste entram na allowlist antes da
  alteração necessária para remover o último call site de update pelo save
  genérico. O escopo autorizado passa de 77 para 79 paths backend; coordenação
  permanece em 13 paths;
- a bateria focada do refactor passou `69/69` em seis classes e a superfície
  modificada ampliada passou `290/290` em 28 classes, sem falha, erro ou skip.
  Ambas ainda foram executadas na árvore dirty e serão substituídas por gates
  limpos da versão commitada; os checks estáticos permanecem `34/34` e o
  contrato estático do smoke `23/23`;
- a revalidação independente final emitiu `GO` estático com
  `P0=0`, `P1=0`, `P2=0` e `P3=0`. O scan encontrou zero assinatura antiga;
  os testes adicionais provam que o parâmetro de senha domina qualquer valor
  isca na entidade, que senha ausente falha antes do write e que passageiro ou
  motorista fora do tenant não atravessam a nova operação de vínculo.
- a primeira execução do gate sobre o commit limpo passou `769/769` testes e
  arquivou a matriz PostgreSQL `35/35`, mas o contrato de fixtures detectou que
  `qa_email_namespace_smoke.sh` ainda não constava no inventário fechado dos
  consumidores locais do bootstrap. `scripts/bootstrap_fixture_contract_test.py`
  entra na allowlist antes da correção; o escopo autorizado passa de 79 para 80
  paths backend. A execução falha não vale como gate final e será repetida por
  inteiro sobre o novo commit limpo.
- a segunda execução limpa passou novamente `769/769`, arquivou a matriz
  PostgreSQL `35/35` e atravessou o contrato corrigido de fixtures, mas o
  quality ratchet bloqueou 14 arquivos que cresceram além do teto congelado ou
  nasceram acima de 350 linhas. O baseline não será relaxado para aceitar a
  regressão estrutural;
- antes de qualquer refactor, entram na allowlist um preparador produtivo,
  suportes pequenos e testes separados por credencial, lifecycle, contrato
  HTTP, criação, deleção, confirmação, concorrência e preflight de migration,
  além de `qa_rbac_suite.sh` para preservar a cobertura temática dos testes
  movidos. O escopo autorizado passa de 80 para 97 paths backend; cada arquivo
  novo deve ficar abaixo de 350 linhas e os arquivos existentes devem voltar
  ao teto já protegido. A execução falha continua apenas diagnóstica.
- o refactor foi commitado em `e4d08aa697a1dcb1f40c82b2cd768dba609a6120`
  e fechou gate limpo `769/769`, PostgreSQL `35/35` e RBAC limpo `156/156`.
  O smoke same-JAR, porém, encerrou antes da readiness nos runs
  `namespace-20260717T210136Z-59662`, `namespace-20260717T210325Z-60478` e
  `namespace-20260717T210439Z-61889`, sempre com cleanup `PASS`. A observação
  sanitizada do último run localizou a causa: `AuthService` possui dois
  construtores, mas o construtor produtivo não estava marcado para injeção; o
  Spring tentou um default inexistente;
- a correção de DI será acompanhada por um `@SpringBootTest` com PostgreSQL e
  web server real, pois os slices, unit tests e `@DataJpaTest` não provaram o
  startup completo. O novo
  `src/test/java/com/vanrides/vanrides/VanridesApplicationContextIntegrationTest.java`
  entra na allowlist antes da edição; o escopo autorizado passa de 97 para 98
  paths backend e a matriz PostgreSQL passa de 35 para 36 cenários. Todas as
  evidências do SHA anterior permanecem diagnósticas e serão repetidas no SHA
  corrigido.

## Reanálise de confirmação concorrente — 2026-07-17

- a revisão independente final emitiu `NO-GO`, com `P0=0`, `P1=0`, `P2=1` e
  `P3=0`: `confirm` lia token, registry e conta sem lock e só depois marcava a
  conta como verificada;
- uma troca A→B poderia commitar entre a validação e o flush da confirmação.
  O contexto JPA ainda podia manter snapshots de A, permitindo resposta de
  sucesso sobre estado já movido, flush stale ou falha não determinística;
- a correção deve serializar confirmação na ordem usada pelos writers:
  primeiro a linha da conta, depois o owner exato no registry e por fim o token
  ainda não consumido. A confirmação só pode marcar verificado depois de
  reler, sob lock, os três vínculos e provar que permanecem idênticos;
- uma integração Spring/PostgreSQL observará bloqueio real e provará os dois
  ordenamentos `confirm × change-email`, incluindo commit e rollback. Teste
  mock também verificará a ordem causal dos locks antes do write;
- `EmailVerificationTokenRepository.java`, a nova
  `EmailVerificationConcurrencyIntegrationTest.java` e seu suporte não
  descoberto pelo Surefire entram na allowlist antes de qualquer edição
  backend. O escopo autorizado passa de 98 para 101 paths; os demais paths
  necessários já estavam congelados;
- os gates e o smoke verdes de `47157c2` ficam preservados como evidência
  diagnóstica, mas deixam de ser candidatos de fechamento. Todos serão
  repetidos integralmente sobre um novo commit limpo; piloto e produção
  continuam `HOLD`.
- a análise da ordem completa encontrou dois ciclos secundários se apenas
  `confirm` fosse alterado: reenvio e hard delete ainda acessavam token antes
  de conta. Todos os writers de artefatos de verificação foram normalizados
  para `conta -> registry -> token`, antes da execução dos testes;
- a prova Spring/PostgreSQL dirty passou `5/5`: confirmação primeiro com
  commit, troca primeiro com commit, troca primeiro com rollback, confirmação
  primeiro com rollback e confirmação contra hard delete. Todos os casos
  observaram bloqueio real; não houve falha, skip, erro nem deadlock;
- o teste do wiring bloqueou corretamente o primeiro checkpoint porque o total
  congelado ainda era `36`. O ratchet foi atualizado para `41` e os contratos
  estáticos passaram `34/34`, `6/6` e `24/24`; execuções com Docker API sem o
  override e duas falhas de fixture durante a construção permanecem apenas
  diagnósticas;
- manter locks de conta e registry durante o envio ao provedor é correto para
  serialização, mas aumenta contenção. A remoção desse I/O da transação segue
  no card de outbox `VR-BE-005-EMAIL-VERIFICATION-OUTBOX-001` e não será
  declarada resolvida aqui.
- a revisão independente final emitiu `GO`, com P0/P1/P2/P3 `0/0/0/1`. O P3
  não bloqueante é a possível releitura stale de verificação/cooldown no
  reenvio concorrente e fica explicitamente associado ao outbox, que deverá
  reavaliar o estado sob lock e retirar o provedor da seção crítica;
- o backend final `4824f54`, tree `56eeceea`, passou gate limpo `776/776`,
  matriz PostgreSQL `41/41`, RBAC `156/156` e smoke same-JAR com JAR
  `ddb7f701...`, Flyway 25/25, invariantes e cleanup em `PASS`;
- o app congelado `b53296b` passou `44/44` consumidores em worktree detached
  limpo, removido ao final. Nenhum path produtivo ou preexistente do app foi
  alterado.

## Critérios de aceite

- [x] O PostgreSQL permite no máximo um owner global por endereço normalizado e
  no máximo um endereço por `(role, userId)`.
- [x] Todos os quatro papéis, inclusive `guardian` sem senha, reservam o
  endereço e writes JDBC diretos também mantêm a invariável.
- [x] Conhecer um e-mail existente nunca transfere conta, tenant, perfil ou
  senha; vínculos de contas públicas exigem convite/request autenticado.
- [x] Corridas ADMIN×PASSENGER, DRIVER×GUARDIAN e change-email×create produzem
  exatamente um owner; a operação perdedora retorna `409`, não `500`.
- [x] Falha ao trocar e-mail preserva conta, endereço anterior, tokens e
  registry sem órfãos.
- [x] Login, Basic Auth e reenvio não usam precedência entre repositórios e não
  fazem fallback quando o owner está inconsistente.
- [x] Criação/troca de senha nunca confia em hash fornecido pelo cliente, e
  updates sem troca preservam hashes bcrypt `$2a$`, `$2b$` e `$2y$` custo 12
  sem rehash silencioso.
- [x] Confirmação e refresh exigem correspondência atual entre token, papel, ID,
  e-mail e identidade global; token do owner removido não adere à nova conta.
- [x] Device ativo sempre corresponde ao owner global atual; registro ou
  reativação concorrente com troca/delete bloqueia e termina válido ou
  fail-closed, sem token ativo órfão.
- [x] A V25 faz backfill limpo 1:1 e aborta integralmente em inválido, colisão
  intra-role ou cross-role, sem DDL parcial e sem e-mail bruto no erro.
- [x] A V25 aborta antes do DDL quando encontra credencial configurada fora do
  bcrypt `$2[aby]$12$`, publicando apenas contagem e sem expor o hash.
- [x] `PUT /passageiros/{id}` e todos os demais writers não contornam a
  autoridade global.
- [x] Colisão sequencial ou concorrente usa `409 EMAIL_ALREADY_IN_USE` genérico
  sem revelar papel, tenant, senha ou verificação e sem deixar conta/empresa,
  sessão ou token órfão.
- [x] Login e reenvio não revelam existência, papel, senha ou verificação por
  caminho síncrono de custo materialmente diferente.
- [x] Hard delete só libera o endereço ao confirmar o commit e reverte
  revogações/liberação junto com uma falha.
- [x] Rotas/payloads de sucesso e os consumidores Flutter congelados permanecem
  compatíveis.
- [x] Matriz de contratos, runbook, README, backlog e QA Log ficam sincronizados.

## Validação mínima

### Unidade e WebMvc

- normalizador Java, lookup/ownership/tradução de constraints, diretório,
  autenticação, reenvio/confirmação/refresh e writers;
- controladores de login, auth, passageiro, motorista, bootstrap e criações
  administrativas;
- testes explícitos de mensagem genérica, `400`, `409`, ausência de fallback e
  preservação do erro não relacionado.

### PostgreSQL obrigatório

- V24 limpa -> V25 com backfill e pointers 1:1;
- inválido/case/trim legado, duplicidade normalizada intra-role e colisão
  cross-role incluindo `guardian` sem senha -> rollback integral;
- corridas ADMIN×PASSENGER, DRIVER×GUARDIAN e change-email×create com bloqueio
  observado por timeout, não apenas largada simultânea;
- rollback após insert/update, delete×create, JDBC direto, writer V24 durante
  a V25 e registro/reativação de device contra troca/delete;
- paridade Java/SQL para locale turco, comprimentos-limite, controles, espaço
  não ASCII, Unicode e aliases legados de papel;
- as novas classes entram em `EXPECTED_TEST_CLASSES` e executam com zero
  skip/failure/error no perfil estrito.

### Gates, smoke e consumidor

- testes focados Maven durante os incrementos;
- `VANRIDES_QA_REQUIRE_CLEAN=1 ./scripts/ci_quality_gate.sh`;
- `./scripts/qa_gate_card.sh` e `scripts/qa_email_namespace_smoke.sh` sobre o
  mesmo JAR final, cobrindo create/login/resend/change-email/delete/reuse;
- testes Flutter focados em checkout limpo do SHA congelado, sem tocar o
  worktree principal sujo;
- `python3 scripts/program_control_test.py` e
  `python3 scripts/program_control.py check` no checkpoint coordenador.

## Arquivos planejados

- backend produtivo: migration V25, `LoginIdentity`, repository, normalizer,
  service/exception do namespace, diretório/autenticação/verificação/refresh,
  writers e controladores afetados;
- backend de prova: testes unitários/WebMvc/integração, perfil estrito, smoke,
  README, matrizes, QA Playbook, backlog e QA Log;
- coordenação: card, ADR-001, risco, dashboard, estado do programa e evidência.

Os caminhos exatos autorizados ficam congelados em `program/program.json`.
Arquivo novo adicional exige reanálise e atualização coordenadora antes da
alteração, não depois.

## Plano sequencial

1. ativar o card como único WIP e congelar SHA/tree/dirty paths/rollback;
2. escrever RED para normalização, lookup único e erros públicos;
3. implementar a V25 e provar preflight/backfill/trigger em PostgreSQL;
4. implementar a seam e substituir resolução por precedência;
5. integrar todos os writers, mudança de e-mail, confirmação, refresh e delete;
6. completar WebMvc, matriz concorrente e testes de compatibilidade;
7. criar o smoke same-JAR e runbook de reconciliação sanitizado;
8. sincronizar documentos e executar gates limpos;
9. registrar SHA/tree/JAR/digest/Flyway/run IDs/hashes e obter revisão
   independente antes de `done`.

## Evidências

| Evidência | Estado | Referência |
|---|---|---|
| baseline backend limpo | comprovado | `528a4c40`, tree `54262de3` |
| gate PostgreSQL fail-closed | comprovado | `program/evidence/VR-QA-BE-GATE-NO-SKIP-001.md`; checkpoint `29257a3` |
| decisão arquitetural | aceita | `program/decisions/ADR-001-global-login-email-namespace.md` |
| implementação e testes focados | comprovado | backend `4824f54`; concorrência `5/5` |
| gate canônico limpo | comprovado | `integration-20260717T220353Z-24021`; `776/776`, PostgreSQL `41/41` |
| smoke same-JAR | comprovado | `namespace-20260717T220601Z-25674`; JAR `ddb7f701...`, Flyway 25 |
| consumidor Flutter congelado | comprovado | app `b53296b`; `44/44` em worktree limpo removido |
| revisão independente | `GO` | P0/P1/P2/P3 `0/0/0/1`; P3 rastreado no outbox |

## Rollback

Antes de promover a V25, reverter somente os commits do card mantém `R-017`
aberto e todas as promoções em `HOLD`. Depois que a V25 existir em ambiente
compartilhado, rollback é apenas de código compatível: tabela, funções, checks
e triggers permanecem. Qualquer correção de schema ocorre em nova migration
forward-only. Colisão encontrada em banco persistente interrompe a promoção e
exige decisão humana auditável; não há waiver para ignorá-la.
