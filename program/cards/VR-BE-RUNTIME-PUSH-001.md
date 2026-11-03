# VR-BE-RUNTIME-PUSH-001 — Restaurar boot padrão com fallback de push

## Resultado esperado

O JAR do backend inicia em uma instalação limpa, sem configuração externa de
teste, quando nenhum provider real de push está configurado. Exatamente um
`PushDeliveryGateway` fallback fica disponível e um provider futuro pode
substituí-lo por seleção explícita, sem ambiguidade ou dependência da ordem de
registro dos beans.

## Estado

`done`

## Risco ou objetivo associado

`R-013` e épico `VR-BE-007`.

## Contexto

O smoke de `VR-SEC-001-BE-WRITE` demonstrou que o JAR atual falha no startup:
`NotificationDispatchService` não encontra `PushDeliveryGateway`. A classe
`NoopPushDeliveryGateway` é simultaneamente `@Service` e
`@ConditionalOnMissingBean(PushDeliveryGateway.class)`; durante o scan, sua
própria definição satisfaz a condição e o bean não é criado.

O smoke anterior só pôde prosseguir com uma configuração externa descartável.
Sem corrigir esse baseline, gates e smokes futuros dependeriam de um adapter
não versionado e não reproduziriam a execução normal do produto.

## Impact Analysis

### Mudanças técnicas

- reproduzir a ausência do bean em teste de contexto antes da correção;
- separar a implementação noop de sua configuração condicional;
- dar a cada `PushDeliveryGateway` uma identidade explícita de provider;
- registrar o fallback em uma configuração Spring selecionada por
  `vanrides.push.provider=none`, com `none` como default quando a propriedade
  estiver ausente;
- mapear `VANRIDES_PUSH_PROVIDER` para a propriedade canônica e documentar o
  default seguro no `.env.example` e no README;
- provar que existe exatamente um fallback no modo `none`, que outra opção não
  registra o noop e que um bean explícito ocupa o seam sem ambiguidade;
- falhar o contexto quando uma opção diferente de `none` é declarada sem um
  adapter de identidade compatível, evitando downgrade silencioso;
- validar cardinalidade e identidade sem depender de `@Primary` ou da ordem de
  registro dos beans;
- adicionar uma prova de boot do JAR com PostgreSQL limpo e configuração
  padrão, sem classpath ou bean externo.

### Contratos afetados

Nenhum contrato HTTP, payload, status, auth, CORS, WebSocket ou schema muda. A
mudança é de composição runtime: `vanrides.push.provider` passa a selecionar a
capacidade, com default `none`. Nesse modo, notificações continuam seguindo o
estado `SKIPPED_PROVIDER_NOT_CONFIGURED` já existente. A propriedade não contém
credencial; providers futuros continuam obrigados a usar secrets próprios.

### Riscos

- criar dois gateways e causar ambiguidade de injeção;
- permitir que `@Primary` contorne o modo `none` ou masque gateways duplicados;
- impedir que um provider real substitua o noop;
- aceitar valor desconhecido ou incompatível com o adapter e iniciar em estado
  enganoso;
- documentar a chave canônica sem integrá-la ao `.env` local;
- mascarar outro erro de startup usando mocks ou profile exclusivo de teste;
- validar classes diretamente e não o JAR empacotado;
- introduzir configuração de produção ou segredo fora de escopo.

### Mitigação

- seleção por propriedade, sem depender da ordem de processamento de
  `@ConditionalOnMissingBean`;
- invariante de startup exige exatamente um gateway e igualdade entre a
  identidade do adapter e o provider selecionado;
- testes de contexto para default/`none`, provider explícito, provider ausente,
  identidade incompatível, múltiplos adapters e tentativas com `@Primary`;
- mapeamento explícito de `VANRIDES_PUSH_PROVIDER` e documentação do default
  `none`;
- asserção de contagem, identidade, tipo e falha clara do contexto;
- teste do comportamento noop existente;
- JAR construído uma vez e iniciado sem configuração externa;
- PostgreSQL descartável com migrations reais;
- checksum e comando de startup registrados.

### Validação mínima

- RED que reproduza o contexto sem `PushDeliveryGateway`;
- testes de configuração: fallback único em `none`, provider explícito sem noop,
  opção sem adapter, identidade incompatível, múltiplos adapters e cenários com
  `@Primary`;
- testes de `NotificationDispatchService`;
- `./mvnw test` e `./scripts/ci_quality_gate.sh`;
- build do JAR e startup contra `postgres:16-alpine` limpo;
- health/smoke HTTP da mesma versão sem classpath, bean ou adapter externo;
- `VANRIDES_BACKEND_BASE_URL=http://127.0.0.1:<porta> ./scripts/qa_gate_card.sh`;
- `git diff --check`.

### Rollback

Reverter os commits do card. Não há migration ou alteração de dados.

## Escopo

- composição Spring do fallback de push;
- testes de contexto e boot;
- backlog e QA Log do backend;
- evidência reproduzível do JAR.

## Fora de escopo

- integrar FCM/APNs ou qualquer provider real;
- enviar notificações externas;
- criar ou alterar segredos;
- implementar um provider real ou definir credenciais de push;
- mudar payload/status de notificação;
- resolver a incompatibilidade Testcontainers/Docker;
- deploy ou VPS.

## Dependências

- `VR-SEC-001-BE-WRITE` concluído.

## Critérios de aceite

- [x] Falha atual é reproduzida por teste antes da implementação.
- [x] Contexto padrão possui exatamente um `PushDeliveryGateway` noop.
- [x] `vanrides.push.provider=none` seleciona exatamente o noop.
- [x] Provider explícito ocupa o seam sem noop ou ambiguidade.
- [x] Opção sem adapter, identidade incompatível e múltiplos adapters falham
      claramente, inclusive quando um adapter é `@Primary`.
- [x] `VANRIDES_PUSH_PROVIDER` integra o runtime e o `.env` com default `none`.
- [x] Comportamento `SKIPPED_PROVIDER_NOT_CONFIGURED` permanece válido.
- [x] JAR inicia com PostgreSQL limpo sem configuração externa de código.
- [x] Gate e smoke da mesma versão passam.
- [x] Skips e limites permanecem explícitos.
- [x] QA Log, backlog, commits e evidência estão sincronizados.

## Arquivos previstos

- `src/main/java/com/vanrides/vanrides/service/PushDeliveryGateway.java`
- `src/main/java/com/vanrides/vanrides/service/NoopPushDeliveryGateway.java`
- `src/main/java/com/vanrides/vanrides/config/PushDeliveryConfiguration.java`
- `src/main/resources/application.properties`
- `src/test/java/com/vanrides/vanrides/config/PushDeliveryConfigurationTest.java`
- `.env.example`
- `README.md`
- `backlog/SpringBoot-Cards.md`
- `backlog/QA-Execution-Log.md`

Qualquer arquivo adicional exige atualizar esta Impact Analysis e o
`allowedDirtyPaths` antes da edição.

## Plano sequencial

1. capturar a falha atual em teste;
2. criar seleção pequena por propriedade, identidade explícita e remover a
   auto-condição inválida;
3. provar fallback, provider explícito, cardinalidade e falhas de configuração,
   inclusive com `@Primary`;
4. executar testes focados e gate backend;
5. construir e iniciar o JAR sem adapter externo;
6. executar smoke da mesma versão;
7. registrar evidência, limites, backlog e commits.

## Evidências

| Evidência | Comando/local | Resultado | SHA/digest |
|---|---|---|---|
| descoberta | `program/evidence/VR-SEC-001-BE-WRITE.md` | FAIL do boot padrão | `2784e068f9e73eb39bade4ceaa6bd5ff611efedc` |
| testes focados | Maven, configuração + dispatch | PASS — 18 testes, 0 skips | `117b8ca27353164814b5af602dba5f97d23fb655` |
| suíte e gate CI | Maven + ratchet + guardrails | PASS — 571 testes, 10 skips declarados | `117b8ca27353164814b5af602dba5f97d23fb655` |
| JAR/PostgreSQL no mesmo SHA | `program/evidence/VR-BE-RUNTIME-PUSH-001.md` | PASS — 23 migrations, HTTP `200/200/400` | `edcc061f4a093db625e0cdadc44b9454c03fe042f26f6186867b5ea69af9cd53` |
| QA e backlog local | backend QA Log e SpringBoot Cards | DONE | `5c52f00aa37a253eb214e91c62b9d32c5008002e` |

## Encerramento

- commits backend:
  `117b8ca27353164814b5af602dba5f97d23fb655` e
  `5c52f00aa37a253eb214e91c62b9d32c5008002e`;
- QA Log: `src/backend/backlog/QA-Execution-Log.md`;
- backlog local: `src/backend/backlog/SpringBoot-Cards.md`;
- documentação: `src/backend/README.md`, `.env.example` e
  `application.properties`;
- evidência canônica: `program/evidence/VR-BE-RUNTIME-PUSH-001.md`;
- risco `R-013`: encerrado no escopo de boot padrão;
- débito preservado: `VR-QA-TESTCONTAINERS-DOCKER-001`;
- nenhum provider real, envio externo, push, deploy, VPS, staging, produção ou
  segredo foi usado.
