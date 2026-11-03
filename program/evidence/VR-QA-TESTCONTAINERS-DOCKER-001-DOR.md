# Recibo histórico — VR-QA-TESTCONTAINERS-DOCKER-001-DOR

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-QA-TESTCONTAINERS-DOCKER-001-DOR.md`. SHA-256 do original: `738e3970b96c25efd095f07968c47a1fea233c068bff0f9d59502d642adf309f`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores do documento original pertencem às respectivas revisões históricas e não aprovam este checkout.

## Identidade

- data: 2026-07-16, `America/Sao_Paulo`;
- modo: diagnóstico read-only;
- repositório: `vanrides-backend`;
- branch: `dev`;
- HEAD observado: `5c52f00aa37a253eb214e91c62b9d32c5008002e`;
- resultado: causa comprovada; experimento A/B ainda pendente.

## Causa comprovada

- Spring Boot 3.4.5 resolve Testcontainers 1.20.6;
- Testcontainers resolve `docker-java-api` e
  `docker-java-transport-zerodep` 3.4.1;
- no artefato local, `DockerClientProviderStrategy#getClientForConfig` usa
  `RemoteApiVersion.VERSION_1_32` quando a versão é desconhecida;
- nenhuma configuração local observada define a propriedade Java
  `api.version`;
- `~/.testcontainers.properties` define somente a estratégia Unix socket;
- não há override relevante em ambiente, `.docker-java.properties` ou
  configuração Maven do projeto;
- Docker client/server 29.2.0 expõe API 1.53 e exige
  `MinAPIVersion=1.44`;
- os relatórios Surefire registram `400 Bad Request` e dez skips, distribuídos
  entre as três suítes como 7 + 1 + 2.

## Revisões e reprodução históricas

- `e92787239181e39cacd5b7341dc6e0874b2f9fe1`
- `e79a8700440ce7174e1a2edb8165770ab2322cd6`
- `ab68d763fec761dc943af08350a6e0a2b97592f7`
- `2de5dedacaeef78129608d62f412b362cd7da927`

- Comando registrado na origem: `./scripts/ci_quality_gate.sh`.
- Comando registrado na origem: `./scripts/qa_integration_profile.sh`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
