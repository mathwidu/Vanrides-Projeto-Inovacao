# VR-SEC-001-BE-WRITE — Restringir publicação de localização ao próprio motorista

## Resultado esperado

Um usuário `DRIVER` autenticado só pode publicar localização para o motorista
ligado à própria identidade. Tentar usar o identificador de outro motorista,
inclusive da mesma empresa, é negado sem persistir nem publicar evento.

## Estado

`done`

## Risco mitigado

`R-001`

## Contexto

A autorização atual em
`LocalizacaoMotoristaController.registrarLocalizacao(...)` valida apenas o papel
`DRIVER`. Em seguida, usa o `idMotorista` da URL e o `companyId` autenticado para
buscar qualquer motorista da empresa, salvar a coordenada e publicar o snapshot
da viagem. O `userId` autenticado não é comparado ao alvo.

O teste `RbacWebMvcTest.shouldAllowPostingLocationForDriverRole` autentica o
usuário `16` e publica para o motorista `4`, codificando acidentalmente o
comportamento vulnerável como sucesso. A prova foi reexecutada no HEAD
`138558662c61468036840e7381fdbd67b2c73ff6` com
`./mvnw -q -Dtest=RbacWebMvcTest#shouldAllowPostingLocationForDriverRole test` e
passou, confirmando o achado no código atual.

O endpoint também recebe a entidade JPA `LocalizacaoMotorista` diretamente. O
controller sobrescreve `motorista` e `dataHora`, mas não limpa um eventual `id`
enviado pelo cliente. Isso permite mass assignment e pode transformar o `save`
em merge de um registro existente.

Localização é dado sensível e alimenta persistência e tempo real; a checagem deve
ocorrer antes da busca do alvo e de qualquer efeito colateral.

## Impact Analysis

### Mudanças técnicas previstas

- usar `AuthorizationGuard.getRequiredUserId(request)` como identidade do driver;
- comparar a identidade autenticada ao `idMotorista` antes da busca;
- criar `DriverLocationWritePolicy` como seam pequeno e testável;
- trocar o request da entidade JPA por `DriverLocationPublishRequest`, aceitando
  apenas latitude e longitude;
- criar uma nova entidade no servidor, garantindo `id == null`;
- negar mismatch com `404`, evitando enumeração e mantendo a política usada nas
  demais operações de posse;
- garantir ausência de `save` e de publicação WebSocket em falha;
- corrigir o teste RBAC que hoje permite IDs diferentes;
- adicionar testes de sucesso, negação same-tenant e ausência de interações;
- adicionar POST negativo à matriz multi-tenant, que antes cobria apenas leitura;
- atualizar as matrizes de contrato e RBAC.

### Contratos afetados

- endpoint REST de publicação de localização;
- shape efetivo do request: latitude/longitude continuam válidos; campos extras
  deixam de controlar identidade, timestamp ou relacionamento;
- política de autorização do domínio;
- `404` com mensagem estável para driver autenticado que usa outro ID;
- evento de localização emitido após sucesso.

O caminho feliz continua `200`. O app já publica usando o próprio ID e não deve
precisar de alteração. A matriz de contrato deve passar de “DRIVER” para
“DRIVER somente no próprio id”.

### Riscos

- bloquear driver legítimo se `userId` e `motoristaId` tiverem semânticas
  diferentes em alguma autenticação legada;
- corrigir REST e transmitir falsa sensação de que o caminho STOMP também foi
  corrigido;
- introduzir DTO/policy grandes demais para uma fatia de segurança;
- persistir antes de negar;
- alterar `404` para `403` e criar enumeração/comportamento inconsistente;
- manter um teste antigo que continue legitimando IDs divergentes.

### Mitigação

- confirmar em Basic e Bearer que `AuthenticatedUser.userId` representa o ID do
  motorista para o papel `DRIVER`;
- policy dedicada apenas à escrita de localização; política central completa
  permanece em `VR-SEC-002`;
- DTO mínimo, sem generalização prematura;
- testes no boundary HTTP e no serviço;
- ordem de chamada verificada;
- `verifyNoInteractions`/`never()` para busca, persistência e publisher;
- nenhum fallback silencioso por tenant.

### Validação mínima

- teste MockMvc: próprio motorista publica com `200`;
- teste MockMvc: outro motorista do mesmo tenant recebe `404`;
- teste RBAC: driver com IDs divergentes deixa de receber `200`;
- teste multi-tenant: tentativa de escrita para ID externo recebe `404` antes do lookup;
- teste: alvo cross-tenant/não pertencente continua oculto;
- teste: body com `id`, `dataHora` e `motorista` não controla a entidade salva;
- teste: entidade salva possui `id == null`, motorista autenticado e timestamp
  do servidor;
- teste: mismatch não persiste localização;
- teste: mismatch não publica evento;
- teste focado:
  `./mvnw -B
  -Dtest=DriverLocationWritePolicyTest,LocalizacaoMotoristaControllerWebMvcTest,RbacWebMvcTest,TenantIsolationWebMvcTest,LocalizacaoSocketControllerTest
  test`;
- `./scripts/qa_rbac_suite.sh`;
- gate determinístico do backend;
- `VANRIDES_BACKEND_BASE_URL=http://127.0.0.1:<porta>
  ./scripts/qa_gate_card.sh` com a mesma árvore local em execução;
- revisão do contrato consumido pelo app.

O smoke padrão contra DEV remoto não serve como prova deste card enquanto o DEV
não estiver no mesmo SHA.

### Rollback

Reverter o commit do backend. Não há migração de banco prevista.

## Escopo

- backend;
- autorização do caminho de escrita REST;
- DTO seguro de entrada e criação server-side da entidade;
- testes e documentação diretamente relacionados.

## Fora de escopo

- leitura de localização por passageiro;
- autorização de subscriptions STOMP;
- tópico amplo por empresa;
- refresh token;
- validação completa de faixa/null de coordenadas;
- atomicidade entre persistência e publisher;
- mudança do response de sucesso;
- refatoração geral de RBAC.

Esses itens permanecem no épico `VR-SEC-001` ou em épicos dependentes.

## Critérios de aceite

- [x] Driver publica apenas para a própria identidade.
- [x] Mismatch same-tenant é oculto com `404`.
- [x] Cross-tenant/não pertencente permanece oculto.
- [x] Nenhum efeito colateral ocorre antes da autorização.
- [x] Request não pode controlar ID, timestamp ou motorista da entidade.
- [x] Contrato de erro está documentado e testado.
- [x] Gate e smoke locais aprovados.
- [x] QA Log e backlog do backend atualizados.
- [x] Commit e evidências registrados no programa.

## Dependências

- `GOV-PIPELINE-001` concluído.

## Arquivos previstos

- `src/main/java/com/vanrides/vanrides/controller/LocalizacaoMotoristaController.java`
- `src/main/java/com/vanrides/vanrides/dto/DriverLocationPublishRequest.java`
- `src/main/java/com/vanrides/vanrides/service/DriverLocationWritePolicy.java`
- `src/test/java/com/vanrides/vanrides/controller/LocalizacaoMotoristaControllerWebMvcTest.java`
- `src/test/java/com/vanrides/vanrides/controller/RbacWebMvcTest.java`
- `src/test/java/com/vanrides/vanrides/controller/TenantIsolationWebMvcTest.java`
- `src/test/java/com/vanrides/vanrides/service/DriverLocationWritePolicyTest.java`
- `backlog/API-Contract-Matrix.md`
- `backlog/RBAC-Permission-Matrix.md`
- `backlog/QA-Execution-Log.md`
- `backlog/SpringBoot-Cards.md`

Qualquer arquivo adicional exige atualizar a Impact Analysis antes da edição.

## Plano sequencial

1. escrever testes da policy, mismatch, mass assignment e ausência de efeitos;
2. corrigir o teste RBAC que autentica `16` e publica para `4`;
3. criar DTO mínimo e policy de ownership;
4. aplicar a policy antes do lookup e construir nova entidade server-side;
5. executar testes focados e suíte RBAC;
6. atualizar contrato, RBAC, backlog e QA Log;
7. executar gate e smoke local no mesmo SHA;
8. registrar evidência, commit e conclusão no programa.

## Evidências

| Evidência | Comando/local | Resultado | SHA/digest |
|---|---|---|---|
| reprodução do comportamento vulnerável | teste RBAC focado no baseline | PASS como prova do achado | `138558662c61468036840e7381fdbd67b2c73ff6` |
| regressão focada | Maven, 5 classes | PASS — 36 testes, 0 skips | `2784e068f9e73eb39bade4ceaa6bd5ff611efedc` |
| matriz RBAC | `./scripts/qa_rbac_suite.sh` | PASS — 68 testes, 0 skips | `2784e068f9e73eb39bade4ceaa6bd5ff611efedc` |
| gate backend | `./scripts/ci_quality_gate.sh` | PASS com 10 skips Testcontainers declarados | `2784e068f9e73eb39bade4ceaa6bd5ff611efedc` |
| smoke HTTP/PostgreSQL | `program/evidence/VR-SEC-001-BE-WRITE.md` | PASS no escopo do card | `05e107e8a9280052a246a8bf92851bbb6aa4ef28d02e1694120290a994133fe0` |
| QA e backlog local | backend QA Log e SpringBoot Cards | DONE | `48e0870f36203eb62b646b6a02dd61fec7e08d63` |
| precisão da versão Flyway/schema | dependency tree + QA Log | PASS | `34b0cd02c199b74c4c86cb741a61f8cbb0329cf0` |

## Encerramento

- commits backend:
  `2784e068f9e73eb39bade4ceaa6bd5ff611efedc` e
  `48e0870f36203eb62b646b6a02dd61fec7e08d63`, com correção factual de
  evidência em `34b0cd02c199b74c4c86cb741a61f8cbb0329cf0`;
- QA Log: `src/backend/backlog/QA-Execution-Log.md`;
- backlog local: `src/backend/backlog/SpringBoot-Cards.md`;
- evidência canônica: `program/evidence/VR-SEC-001-BE-WRITE.md`;
- risco `R-001`: parcialmente mitigado; leituras e realtime permanecem em cards
  posteriores do épico;
- débitos descobertos: `VR-BE-RUNTIME-PUSH-001` e
  `VR-QA-TESTCONTAINERS-DOCKER-001`;
- não houve push, deploy, VPS, ambiente remoto ou dado real.
