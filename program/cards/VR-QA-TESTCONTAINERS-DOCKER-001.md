# VR-QA-TESTCONTAINERS-DOCKER-001 — Eliminar skips por incompatibilidade Docker

## Resultado esperado

Os testes de integração PostgreSQL executam de fato no gate suportado, com zero
skip por detecção/compatibilidade do Docker, e o gate deixa explícito quando o
ambiente deveria ter Docker mas não conseguiu inicializá-lo.

## Estado

`done`

## Risco ou objetivo associado

`R-014` e épico `VR-QA-001`.

## Contexto

O gate de `VR-SEC-001-BE-WRITE` reportou 558 testes aprovados e os gates de
`VR-BE-RUNTIME-PUSH-001` chegaram a 571, mas os mesmos dez testes de integração
ficaram `SKIPPED`. O Testcontainers tentou negociar a API Docker
`1.32`, enquanto o daemon local aceita a partir de `1.44`.

O DoR read-only comprovou a origem: Spring Boot 3.4.5 resolve Testcontainers
1.20.6 e docker-java 3.4.1; quando `api.version` não existe, o caminho usado por
`DockerClientProviderStrategy` aplica explicitamente o fallback `1.32`. O Docker
29.2.0 local expõe API `1.53` e `MinAPIVersion=1.44`. `DOCKER_API_VERSION` não
alimenta esse caminho; a propriedade relevante é a Java `api.version`.

Como as classes usam `@Testcontainers(disabledWithoutDocker = true)`, o build
pode permanecer verde sem executar migrations e repositórios reais. Isso reduz
a confiança de todos os cards seguintes e precisa ser corrigido separadamente
do boot runtime.

## Impact Analysis

### Mudanças técnicas

- confirmar no SHA atual, por A/B, o fallback sem `api.version` e o perfil
  versionado que passa `-Dapi.version`;
- conectar o perfil Docker-required existente ao job de CI suportado;
- tornar os relatórios do perfil frescos e exigir a matriz exata `7 + 1 + 2`;
- preservar `postgres:16-alpine` e o isolamento dos testes;
- tornar skip inesperado visível e bloqueante no gate onde Docker é requisito;
- evitar dependência de configuração global não versionada do Mac.

### Contratos afetados

Nenhum contrato do produto muda. O contrato de qualidade muda: no ambiente de
integração suportado, os testes PostgreSQL precisam executar ou o gate falha.

### Riscos

- corrigir apenas esta máquina e manter CI não reproduzível;
- atualizar dependências sem verificar compatibilidade com Spring Boot;
- tornar desenvolvimento sem Docker inutilizável sem separar gates;
- contabilizar testes skipped como executados;
- aceitar XML Surefire antigo ou uma redução silenciosa da matriz esperada;
- alterar configuração global ou daemon fora do repositório.

### Mitigação

- manter causa, versões e propriedade efetiva registradas em evidência;
- executar o A/B antes de editar dependência, testes ou script existente;
- mudança mínima e versionada;
- cenário explícito para ambiente com Docker obrigatório;
- asserção automatizada de relatórios frescos, contagem exata e zero skip no
  CI/gate de integração;
- teste em execução limpa sem variável global ad hoc.

### Validação mínima

- reproduzir os dez skips sem `api.version` no mesmo SHA;
- executar `./scripts/qa_integration_profile.sh` no mesmo SHA;
- executar `RepositoryIntegrationTest`, `RefreshTokenRepositoryIntegrationTest`
  e `LegacyCompanyMigrationIntegrationTest` com PostgreSQL real;
- confirmar zero skip e todos os testes esperados executados;
- confirmar por suíte `Repository=7`, `RefreshToken=1` e `Legacy=2`;
- `./mvnw test` e `./scripts/ci_quality_gate.sh`;
- testar o comportamento do gate quando Docker obrigatório está indisponível;
- registrar versões do Maven, Java, Testcontainers, docker-java e daemon;
- `git diff --check`.

### Rollback

Reverter o wiring do workflow, o hardening do verificador e sua
documentação/teste de contrato. Não há alteração persistente no banco.

## Escopo

- wiring versionado do gate Docker-required no workflow;
- limpeza e verificação exata dos relatórios do perfil existente;
- teste estrutural que exige esse wiring;
- execução das três suítes PostgreSQL Testcontainers sem alterá-las inicialmente;
- QA Log, backlog e evidência.

## Fora de escopo

- alterar configuração global do Docker Desktop;
- atualizar Testcontainers/docker-java sem o A/B demonstrar necessidade;
- alterar as três classes de integração sem falha funcional comprovada;
- criar outro script antes de revalidar `qa_integration_profile.sh`;
- manter containers persistentes;
- trocar PostgreSQL de produção;
- corrigir o boot de `PushDeliveryGateway`;
- deploy ou VPS.

## Dependências

- `GOV-PIPELINE-001` concluído.
- `VR-BE-RUNTIME-PUSH-001` concluído como dependência de sequenciamento; não há
  dependência técnica entre o fallback de push e o cliente Docker.

## Critérios de aceite

- [x] Causa raiz da negociação API `1.32` está documentada.
- [x] A/B no mesmo SHA comprova o baseline e o perfil Docker-required.
- [x] As três classes Testcontainers executam sem skip no ambiente suportado.
- [x] Relatórios são frescos e comprovam exatamente `7 + 1 + 2` testes.
- [x] Workflow executa o perfil Docker-required além do gate padrão.
- [x] Gate falha em vez de silenciar quando Docker é obrigatório e indisponível.
- [x] Execução sem Docker continua com semântica explícita, não falso PASS.
- [x] Nenhuma configuração global do Mac é necessária.
- [x] Gate completo, QA Log, backlog, commits e evidência estão sincronizados.

## Arquivos previstos

- `.github/workflows/swarm-ghcr-cicd.yml`
- `AGENTS.md`
- `scripts/ci_quality_gate_test.py`
- `scripts/qa_integration_profile.sh`
- `README.md`
- `docs/quality-ratchet-program.md`
- `backlog/SpringBoot-Cards.md`
- `backlog/QA-Execution-Log.md`

O A/B no HEAD `5c52f00aa37a253eb214e91c62b9d32c5008002e` comprovou dez
skips sem `api.version` e dez execuções reais com o perfil. A revisão do
resultado encontrou que o verificador não limpava os XMLs anteriores nem exigia
a matriz exata; por isso `scripts/qa_integration_profile.sh` entra no escopo.
`pom.xml` e as três classes de integração continuam fora. Qualquer falha que
exija editá-los demanda atualizar esta Impact Analysis antes.

## Plano sequencial

1. executar o A/B no mesmo SHA: baseline sem propriedade versus perfil existente;
2. tornar os relatórios frescos, exigir `7 + 1 + 2` e testar a falha sem Docker;
3. conectar o perfil existente ao job de CI sem alterar o gate local padrão;
4. ajustar o teste estrutural para exigir job, unicidade e ordem dos passos;
5. executar as três suítes, testes de wiring e gate completo;
6. atualizar documentação, QA Log, backlog, evidência e risco.

## Evidências

| Evidência | Comando/local | Resultado | SHA/digest |
|---|---|---|---|
| descoberta | `program/evidence/VR-SEC-001-BE-WRITE.md` | SKIPPED — 10 testes | `2784e068f9e73eb39bade4ceaa6bd5ff611efedc` |
| reprodução atual | `program/evidence/VR-BE-RUNTIME-PUSH-001.md` | SKIPPED — mesmos 10 testes | `117b8ca27353164814b5af602dba5f97d23fb655` |
| DoR read-only | `program/evidence/VR-QA-TESTCONTAINERS-DOCKER-001-DOR.md` | causa comprovada; A/B pendente | `5c52f00aa37a253eb214e91c62b9d32c5008002e` |
| wiring e perfil bloqueante | workflow, script e docs | PASS | `e92787239181e39cacd5b7341dc6e0874b2f9fe1` |
| testes anti-bypass | 10 testes estruturais/negativos | PASS | `ab68d763fec761dc943af08350a6e0a2b97592f7` |
| gate geral final | `./scripts/ci_quality_gate.sh` | PASS — 571, 0 falhas/erros, 10 skips tolerantes | `ab68d763fec761dc943af08350a6e0a2b97592f7` |
| perfil final isolado | PostgreSQL 16 + 23 migrations | PASS — `7/1/2`, zero skip/falha/erro | `ab68d763fec761dc943af08350a6e0a2b97592f7` |
| JAR/HTTP da mesma versão | `qa_gate_card.sh` + smoke local | PASS — `200/200/400` | `e0b70284b11eea263f1e80e5e8f15d2bb4565933b2cc8f9d19d80ab6ab94a19b` |
| QA e backlog local | backend QA Log e SpringBoot Cards | DONE | `2de5dedacaeef78129608d62f412b362cd7da927` |
| evidência final | `program/evidence/VR-QA-TESTCONTAINERS-DOCKER-001.md` | PASS no escopo | `ab68d763fec761dc943af08350a6e0a2b97592f7` |

## Encerramento

- commits backend:
  `e92787239181e39cacd5b7341dc6e0874b2f9fe1`,
  `e79a8700440ce7174e1a2edb8165770ab2322cd6`,
  `ab68d763fec761dc943af08350a6e0a2b97592f7` e
  `2de5dedacaeef78129608d62f412b362cd7da927`;
- QA Log: `src/backend/backlog/QA-Execution-Log.md`;
- backlog local: `src/backend/backlog/SpringBoot-Cards.md`;
- documentação: workflow, AGENTS, README e quality-ratchet sincronizados;
- evidência: `program/evidence/VR-QA-TESTCONTAINERS-DOCKER-001.md`;
- risco `R-014`: encerrado no escopo local/versionado; runner remoto continua
  não comprovado e não autoriza promoção;
- próximo card: `VR-SEC-001-BE-READ` pronto para a próxima fatia vertical;
- não houve push, GitHub Actions remoto, imagem publicada, deploy, VPS ou dado real.
