# VanRides Backend Quality Ratchet Program

Data: 2026-06-06

## Objetivo

Criar uma etapa de qualidade incremental para o backend Spring Boot em que cada card preserve contratos existentes e melhore, ou pelo menos nao piore, legibilidade, seguranca operacional, testabilidade e previsibilidade dos endpoints.

O programa nao substitui o padrao atual de card. Ele adiciona um quality ratchet antes do commit para impedir regressao em metricas objetivas.

## Principios

1. Clean as you code: codigo novo ou alterado deve obedecer a um padrao mais alto que a base historica.
2. Contrato primeiro: rotas, payloads, status codes, auth, CORS, WS e validacoes continuam protegidos por Impact Analysis.
3. Baseline versionado: dividas atuais ficam visiveis e nao podem crescer por acidente.
4. Mudanca pequena: limpeza estrutural vira card proprio, com teste e rollback simples.
5. Agent-friendly: scripts e docs devem ser diretos o suficiente para um agente saber o que medir, validar e nao piorar.

## Baseline inicial

Medido localmente em 2026-06-06.

| Metrica | Valor atual |
|---|---:|
| `./mvnw -B test` | PASS, 273 testes, 9 skipped |
| Linhas Java em `src/main/java` | 13.214 |
| Linhas Java em `src/test/java` | 8.675 |
| Maior arquivo Java produtivo | `PassengerOnboardingService.java`, 507 linhas |
| Migrations Flyway | 15 |
| Ocorrencias de `@MockBean` em testes | 150 |
| Ocorrencias de `System.out`, `System.err` ou `printStackTrace` | 15 |
| Warnings relevantes observados nos testes | `@MockBean` deprecated; `org.json.JSONObject` duplicado no classpath |

Hotspots produtivos acompanhados atualmente:

- `src/main/java/com/vanrides/vanrides/service/ViagemRotaGeograficaService.java` — 335 linhas.
- `src/main/java/com/vanrides/vanrides/service/CompanyAdminGuardianService.java` — 326 linhas.
- `src/main/java/com/vanrides/vanrides/service/PassengerOnboardingService.java` — 264 linhas.
- `src/main/java/com/vanrides/vanrides/service/ViagemOperacionalService.java` — 308 linhas.
- `src/main/java/com/vanrides/vanrides/service/CompanyAdminPassageiroService.java` — 276 linhas.

## Estado atual do ratchet

Atualizado em 2026-06-13 apos a reconciliacao local do backend com
`origin/dev` e `PHASE-0404`.

| Metrica | Valor atual |
|---|---:|
| Linhas Java em `src/main/java` | 18.165 |
| Linhas Java em `src/test/java` | 13.695 |
| Linhas Java totais | 31.860 |
| Arquivos Java monitorados | 282 |
| Migrations Flyway | 18 |
| Ocorrencias de `@MockBean` em testes | 156 |
| Ocorrencias de `System.out`, `System.err` ou `printStackTrace` | 0 |
| Dependencias `org.json` duplicadas no `pom.xml` | 0 |
| Arquivos Java produtivos acima de 350 linhas | 6 |
| Cards quality stale (`IN_PROGRESS` com DoD `DONE`) | 0 |
| Testcontainers skipped em Surefire reports | 10 |

Mudancas estruturais recentes:

- `PassengerOnboardingService.java`: 416 -> 364 linhas apos extrair
  `CompanyInviteCodePolicy` e `CompanyInviteCodeGenerator`.
- `PassengerOnboardingService.java`: 364 -> 350 linhas apos extrair
  `PassengerCompanyLinker` e simplificar a normalizacao de busca por empresa.
- `PassengerCompanyLinker.java`: 34 linhas, componente dedicado a aplicar
  vinculo passageiro-empresa e preservar/limpar motorista conforme tenant.
- `PassengerOnboardingService.java`: 350 -> 323 linhas apos extrair
  `PassengerOnboardingStatusResolver`.
- `PassengerOnboardingStatusResolver.java`: 60 linhas, componente dedicado a
  resolver `NO_COMPANY`, `PENDING_APPROVAL` e `COMPANY_LINKED` com teste
  unitario direto.
- `ViagemRotaGeograficaService.java`: 335 -> 298 linhas apos extrair
  `ViagemRotaStopPointResolver`.
- `ViagemRotaStopPointResolver.java`: 52 linhas, componente package-private
  dedicado a filtrar paradas geocodificadas, ordenar por `ordem` e marcar
  proxima parada/passageiro alvo.
- `ViagemOperacionalService.java`: 362 -> 308 linhas apos extrair
  `ViagemLifecyclePolicy`.
- `ViagemLifecyclePolicy.java`: 81 linhas, classe pura para transicoes de
  lifecycle da corrida/paradas.
- `GoogleDirectionsRoutingService.java`: 345 -> 255 linhas apos extrair
  `GooglePolylineCodec`.
- `GooglePolylineCodec.java`: 100 linhas, classe pura para decode, encode e
  merge de polylines usadas no fallback hibrido de Google Directions.
- A reconciliacao local com `origin/dev` incorporou a fundacao de servicos
  recorrentes, presencas por servico e snapshots operacionais de viagem; esses
  contratos elevaram a baseline aceita antes de novos cards de reducao.
- `CompanyAdminDashboardControllerWebMvcTest` migrou de `@MockBean` para
  `@MockitoBean` como piloto de baixo risco, reduzindo a divida depreciada de
  162 para 156 ocorrencias sem alterar contratos.
- `CompanyAdminGuardianService.java`: 326 -> 311 linhas apos extrair
  `CompanyAdminGuardianPolicy`.
- `CompanyAdminGuardianPolicy.java`: 33 linhas, classe pura para decidir
  reaproveitamento de responsavel pendente por email e conflito entre empresas,
  com teste unitario direto.
- `PassengerOnboardingService.java`: 379 -> 264 linhas apos delegar busca de
  empresas, criacao de solicitacao, resgate de convite e atribuicao de
  elegibilidades para servicos dedicados.
- `PassengerCompanySearchService.java`: 25 linhas, componente dedicado a
  normalizar a busca de empresas visiveis ao passageiro sem alterar payload.
- `PassengerCompanyRequestService.java`: 69 linhas, componente dedicado a
  criar solicitacoes de vinculo por busca e reaproveitar solicitacao pendente.
- `PassengerInviteRedemptionService.java`: 93 linhas, componente dedicado ao
  resgate de codigo, cancelamento de solicitacoes pendentes e registro de uso.
- `PassengerServiceEligibilityAssignmentService.java`: 69 linhas, componente
  dedicado a validar ids de servico e criar/reativar elegibilidades no aceite
  administrativo.

## Ratchet v1

O primeiro gate deve bloquear regressao objetiva, sem exigir que toda a divida historica seja resolvida no mesmo card.

Artefatos versionados da v1:

- `scripts/quality_metrics.py` coleta metricas e compara baseline.
- `scripts/quality_metrics_test.py` cobre regras centrais do comparador.
- `scripts/quality_baseline.sh` atualiza `quality/ratchet-baseline.json`.
- `scripts/quality_gate.sh` valida o estado atual contra `quality/ratchet-baseline.json`.
- `scripts/ci_quality_gate_test.py` protege o wiring entre workflow,
  entrypoint de CI e etapas obrigatorias do quality gate.
- `scripts/ci_quality_gate.sh` delega uma única suíte Maven ampla ao perfil
  Docker-required, executa os demais checks e o ratchet sem smoke HTTP externo,
  servindo como entrypoint canônico do CI/CD.
- `scripts/qa_gate_card.sh` compõe `scripts/ci_quality_gate.sh` e acrescenta
  apenas o smoke HTTP DEV como parte do gate oficial por card.
- `.github/workflows/swarm-ghcr-cicd.yml` chama somente
  `scripts/ci_quality_gate.sh` no job `test`, antes de build/deploy; não repete
  o perfil em outro Maven run.
- `docs/codex-cloud.md` e `scripts/codex_cloud_setup.sh` recomendam
  `scripts/ci_quality_gate.sh` como check principal de agentes em Codex Cloud,
  mantendo smoke HTTP DEV no gate local de card/contrato.
- `scripts/graphify_refresh.sh` atualiza o grafo local usado para diagnostico
  de arquitetura e hotspots por agentes.

Regras propostas para o backend:

1. `./scripts/ci_quality_gate.sh` deve passar com Docker e matriz PostgreSQL
   de 23 classes/`125/125`; `./mvnw -B test` isolado é apenas diagnóstico.
2. Mudanca de contrato deve continuar exigindo smoke HTTP do endpoint alterado.
3. Nenhum arquivo Java novo pode nascer acima de 350 linhas sem justificativa no Impact Analysis.
4. Arquivos produtivos acima de 500 linhas entram em allowlist temporaria e so podem aumentar com justificativa explicita.
5. Nenhum novo `System.out`, `System.err` ou `printStackTrace`; usar logging estruturado.
6. Nenhum novo uso de `@MockBean`; migracoes existentes devem ser reduzidas em cards dedicados.
7. Nenhuma nova dependencia JSON duplicada ou alerta de classpath sem registro no Impact Analysis.
8. Toda mudanca em auth, CORS, WS, validacao ou payload deve ter teste de contrato correspondente.
9. Toda migration Flyway deve ter rollback operacional descrito, mesmo quando o rollback tecnico nao for automatico.

## Ratchet v2

O v2 separa metricas em tres categorias para manter o gate forte sem bloquear
trabalho por divida historica ja conhecida:

- `blocking`: regressao nova que deve falhar localmente e no CI.
- `warning`: sinal de arquitetura, teste ou governanca que deve aparecer no relatorio.
- `report-only`: informacao de tendencia para planejamento.

Checks `blocking` do backend:

1. `./scripts/ci_quality_gate.sh` continua obrigatório e fail-closed para a
   matriz PostgreSQL; Maven cru com skips não satisfaz o check.
2. `system_print_count` permanece em `0`.
3. `org_json_dependency_count` permanece em `0`.
4. Nenhum novo `System.out`, `System.err` ou `printStackTrace`.
5. `mockbean_count` nao aumenta.
6. `stale_completed_card_count` permanece em `0`.
7. Nenhum arquivo Java produtivo novo nasce acima de 350 linhas sem excecao explicita.

Checks `warning` do backend:

1. Quantidade de arquivos Java produtivos acima de 350 linhas aumenta.
2. Testcontainers skipped count aumenta quando os surefire reports estiverem disponiveis.
3. `mockbean_count` permanece acima de zero e deve seguir caindo em lotes
   dedicados com `@MockitoBean`.
4. Graphify reporta codigo stale.

Checks `report-only` do backend:

- maiores arquivos por categoria;
- tendencia de linhas de producao e teste;
- quantidade de migrations;
- lista de arquivos que ainda usam `@MockBean`;
- `productive_java_files_over_limit_count` e lista dos arquivos acima de 350 linhas;
- `testcontainers_skipped_count` e lista de Surefire reports com skips;
- freshness do grafo quando so docs mudaram.

Observacao sobre Testcontainers:
- No caminho Maven padrao, os testes de repository integration podem aparecer
  como skipped quando Docker/Testcontainers nao esta disponivel.
- Essa contagem e report-only no ratchet v2 para nao tornar o gate instavel.
- Independente do ratchet, o gate canônico chama
  `scripts/qa_integration_profile.sh --all-tests`, exige Docker e falha se a
  matriz ficar skipped, ausente ou incompleta.
- O mesmo contrato vale localmente, no Codex Cloud e no job `test`; ausência de
  Docker bloqueia fechamento em vez de produzir um verde parcial.

Perfil Testcontainers:

- O checkout limita o heap do Maven a384MiB e metaspace a256MiB em
  `.mvn/jvm.config`; o Surefire mantém um único fork reutilizável com heap512MiB
  e metaspace256MiB. Isso não altera a matriz, não permite skips e não substitui
  monitoramento da pressão total do Mac. O limite foi motivado por uma rodada
  interrompida durante040. Não usar `MAVEN_OPTS` para contornar o gate.
  Referências: [configuração Maven por projeto](https://maven.apache.org/configure.html)
  e [forks do Surefire](https://maven.apache.org/surefire/maven-surefire-plugin/examples/fork-options-and-parallel-execution.html);
  parâmetros conferidos também no descritor instalado3.5.3, sem atualização do plugin.

- `scripts/qa_integration_profile.sh` e o comando Docker-required para validar
  a matriz PostgreSQL versionada completa. A fonte executável atual contém 23
  classes e exige `125/125`, incluindo repository/Flyway, refresh, legado,
  bootstrap concorrente, identidade/e-mail/device, delete tenant-scoped e
  fundação WhatsApp (schema, pareamento, leases, HTTP e operação isolada).
- O script executa `clean test`, eliminando bytecode incremental anterior, e
  exige exatamente a matriz declarada em `EXPECTED_TEST_CLASSES` — atualmente
  23 classes/`125/125` — além de zero skips, falhas e erros.
- Sem argumento, o perfil executa somente essas 23 classes. Com
  `--all-tests`, omite `-Dtest` e executa toda a suíte no mesmo run-id; o gate
  canônico usa esse modo para evitar uma segunda execução Maven.
- Um lock advisory persistente no git common dir impede duas suítes Maven de
  evidência simultâneas em qualquer worktree. Um keeper Python mantém
  `fcntl.flock` e um canal herdável: `SIGKILL` sem filho libera pelo kernel;
  com Maven ainda vivo, o lock permanece até o último processo da run encerrar.
  O lock file nunca é removido, evitando corrida de inode/unlink. Cada execução
  grava `run-id` e SHA inicial nos XMLs; o parser rejeita relatórios antigos,
  misturados, de outro SHA/repo ou quando HEAD/tree/fingerprint de conteúdo
  tracked, staged e untracked muda durante a suíte. Overrides `GIT_*` são
  rejeitados nos runners e a captura usa ambiente mínimo, `-C` e top-level real.
- `scripts/qa_report_evidence.py` captura um snapshot estável de cada XML e
  revalida inode, mtime, tamanho e bytes contra alteração concorrente. O raw é
  apenas input: o bundle recebe XML mínimo determinístico, sem properties,
  testcase names, stdout/stderr, comando livre ou `user.home`.
- O archive é bifásico. `prepare` grava somente manifesto
  `status=prepared`/`eligibleForRelease=false` e XML sanitizado fora do glob; o
  runner confirma o release do lock; `finalize` recaptura o source e o rename
  terminal para `status=passed` é a última mutação classificatória.
  Exceção/drift capturável converte para `status=failed`; `SIGKILL` pré-commit
  pode deixar `prepared`, nunca PASS. Falha anterior à identidade/trap seguro
  pode deixar zero bundle.
- Evidência final usa `VANRIDES_QA_REQUIRE_CLEAN=1`; qualquer path dirty bloqueia
  o archive, e o manifesto registra `cleanRequired=true` e `clean=true`.
- Manifesto `status=passed` arquiva somente XMLs validados; `status=failed` é
  diagnóstico best-effort e manifest-only de preflight/Maven/validation/signal;
  nunca autoriza release. `eligibleForRelease=true` também exige classe
  `release`, SHA/tree exatos, fonte limpa, zero skip/falha/erro e
  `allReports.requiredGreen=true`.
- Retenção local preserva 30 dias e pelo menos 20 runs por suite; o pruner é
  dry-run por padrão, nunca remove release/unknown/symlink/estado não terminal e
  só muda o filesystem com `--apply`; run terminal ainda ativa exige
  `--active-run-id`. O inode validado é movido para quarantine privado no mesmo
  filesystem e fora do glob antes da remoção; corrida preserva o run recriado e
  divergência fica para recovery manual. O CI tenta publicar o bundle disponível
  somente em push/workflow_dispatch por 14 dias, ou 90 na lane prod/release, com
  nome por run/attempt/SHA; pull request não publica artifact. Workflow local
  não substitui run/download remoto no SHA final.
- O script consulta `Server.MinAPIVersion` e `Server.APIVersion`, valida a faixa
  e passa `-Dapi.version` ao Maven; override malformado/incompatível ou falha de
  descoberta encerra o preflight sem fallback silencioso.
- O wiring test compara a matriz versionada com todas as classes dos fontes que
  usam `disabledWithoutDocker`, inclusive anotação qualificada, expressão
  constante e herança. Valores que não sejam o literal `false` são tratados de
  forma fail-closed, impedindo que uma classe futura fique fora da evidência.
- No modo amplo, o validador também lê todos os `TEST-*.xml` frescos do run e
  exige zero skip/falha/erro global, cobrindo formatação alternativa ou herança
  que uma descoberta estática futura não reconheça.
- Antes de capturar provenance/consultar o daemon, o perfil rejeita overrides
  herdados de Git, Docker, Testcontainers, Java e Maven; somente `GIT_PAGER` e
  `TESTCONTAINERS_API_VERSION` validado são tolerados. O contexto corrente deve
  ser `unix://` ou `npipe://` local.
- Maven/Testcontainers usa `user.home` temporário isolado, sem configuração
  do usuário, `MAVEN_SKIP_RC=1` e um repositório explícito em
  `$HOME/.m2/repository`; o validador exige que todos os XMLs registrem o mesmo
  home esperado. O `EXIT` remove o diretório somente após release confirmado;
  em estado ambíguo ele é preservado sem imprimir path. O setup Codex Cloud não
  executa Docker; toda mutação fica atrás desses guards.
- `INT`/`TERM` encerram o Maven nos adapters PostgreSQL e RBAC com código de
  sinal e impedem o validador. O módulo compartilhado sinaliza o keeper no
  encerramento normal; em encerramento abrupto, descritores/kernel preservam a
  exclusão até a run realmente terminar.

Politica de baseline v2:

- divida existente fica versionada;
- melhora intencional deve apertar a baseline;
- aumento intencional exige Impact Analysis, card dono, rollback e evidencia do gate.

## Politica de excecao

Excecao so pode entrar com:

- motivo explicito no Impact Analysis;
- prazo ou card de remocao;
- evidencia de que nao ha alternativa menor para aquele card;
- validacao minima executada.

Excecao silenciosa e regressao.

## Fases

### Fase 1: Baseline + gate

- Criar script de baseline que calcula as metricas acima.
- Versionar snapshot inicial em formato legivel para humanos e facil de comparar por script.
- Integrar o ratchet ao `scripts/qa_gate_card.sh`.
- Integrar o gate sem smoke externo ao job `test` do CI/CD.
- Documentar como atualizar baseline apenas quando uma metrica melhora ou quando uma excecao foi aprovada.

### Fase 2: Higiene estrutural

- Substituir logs diretos por logger.
- Planejar migracao de `@MockBean` depreciado nos testes WebMvc.
- Resolver duplicidade de `org.json.JSONObject` no classpath.
- Manter `graphify-out/` local e atualizado apos refatoracoes relevantes:
  - usar `scripts/graphify_refresh.sh` para refresh normal;
  - usar `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh` apenas quando uma
    refatoracao/normalizacao intencional reduzir o numero de nos e o Graphify
    recusar overwrite por seguranca.
- Avaliar JaCoCo/Checkstyle/Spotless/PMD como gates de maturidade, nao como primeira barreira.

### Fase 3: Contratos mais fortes

- Evoluir `backlog/API-Contract-Matrix.md` para apontar testes concretos por endpoint critico.
- Avaliar OpenAPI ou contrato gerado quando o app e o backend estabilizarem os fluxos principais.
- Adicionar limite de cobertura por camada apenas depois do baseline de cobertura.

## Fora de escopo do primeiro card

- Refatorar services existentes.
- Trocar arquitetura de pacotes.
- Alterar contrato HTTP.
- Adicionar novas features.
- Sincronizar ou publicar issues no GitHub.

## Validacao minima do card de fundacao

- `./mvnw -B test`.
- `./scripts/qa_gate_card.sh` quando o ambiente DEV estiver acessivel para smoke HTTP.
- Execucao manual do novo script de baseline.
