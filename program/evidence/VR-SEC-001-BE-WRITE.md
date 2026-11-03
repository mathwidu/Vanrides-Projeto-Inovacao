# Recibo histórico — VR-SEC-001-BE-WRITE

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-SEC-001-BE-WRITE.md`. SHA-256 do original: `aa0d0ea97806cccf16bbae4a6579c2c879c3e91a7a0025fb9568ba9b879a24e7`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, FAIL, SKIPPED pertencem às respectivas revisões históricas e não aprovam este checkout.

## Identidade da execução

- run ID: `VR-SEC-001-BE-WRITE-20260716-LOCAL`;
- data: 2026-07-16, `America/Sao_Paulo`;
- repositório: `vanrides-backend`;
- branch: `dev`;
- baseline vulnerável: `138558662c61468036840e7381fdbd67b2c73ff6`;
- SHA técnico: `2784e068f9e73eb39bade4ceaa6bd5ff611efedc`;
- SHA de QA/backlog: `48e0870f36203eb62b646b6a02dd61fec7e08d63`;
- SHA da correção factual de evidência:
  `34b0cd02c199b74c4c86cb741a61f8cbb0329cf0`;
- ambiente: macOS local, JAR Spring Boot e PostgreSQL 16 descartável;
- estado do backend no encerramento: limpo.

## Escopo comprovado

Esta execução comprova que um `DRIVER` não consegue usar o endpoint REST de
localização para escrever em nome de outro motorista e que campos de entidade
enviados no body não controlam ID, timestamp ou relação persistida. Também
comprova que o caminho legítimo continua funcional.

## Revisões e reprodução históricas

- `2784e068f9e73eb39bade4ceaa6bd5ff611efedc`
- `48e0870f36203eb62b646b6a02dd61fec7e08d63`
- `34b0cd02c199b74c4c86cb741a61f8cbb0329cf0`
- `e92787239181e39cacd5b7341dc6e0874b2f9fe1`
- `e79a8700440ce7174e1a2edb8165770ab2322cd6`
- `ab68d763fec761dc943af08350a6e0a2b97592f7`
- `2de5dedacaeef78129608d62f412b362cd7da927`
- `4ca16dfd406666bb15aaa3c2b8754d769cbaf7dd`
- `84ed225aba3aa6ebdd7326ef4d1cb19aa1614dd4`
- `99b482c224d82a8ddd2537fad58451854a3ac954`
- `38b5489fb20af694b2b2d7f0eabab6e506b90ea1`
- `32d658d6c5eb5175c2875cda2d3620c5ba9c1058`
- `634c615337a0d33e856c8c64a8e274af52e42b07`
- `e716732efe5853731dab6ed401bad981cea09a70`
- `edfc94c707ca02b3a5e996fef6f45696e04da2bc`
- `b84c7d547be2237a0c0ae4f0cf3a0a0128fc40fe`
- `d209ab223268bd63a190558a1b34175616e198d0`
- `3c7c88ffca87ea921314fceeb68e02f51d0ef7ee`
- `e33eaef209e6af943fc18efd50f372f38ee2d093`
- `e4406ca28e073f4bf47fff92e865eefe3ea6f4c3`
- `447930bac10f9fb64b72dde45899e3f606249c43`
- `b21a1fb0fd4300a57b86546092e11802948bb3e5`
- `7b5d4d4048db439d9b5179630089658776f150a4`
- `37cbd307c867407b120385672bc6d1c4a47c3deb`
- `f45d632ddb900ba9297f6b04bf9f670a2a105877`
- `9893babc9aa4c25f33b6354804b12a5d717ec4dd`
- `e93a69f2ee1caac4e3d1e57561d05ae6921390f6`
- `b53296b67dd0dc6ddb4b1125dcc64caadb8c7c6c`
- `385548867be80b0c744edff29126fde839818692`
- `bb2d04edd7061442959eec480aedecf25e6aea6c`

- Comando registrado na origem: `./scripts/qa_rbac_suite.sh`.
- Comando registrado na origem: `./scripts/ci_quality_gate.sh`.
- Comando registrado na origem: `./mvnw dependency:tree -Dincludes=org.flywaydb:flyway-core`.
- Comando registrado na origem: `git diff --check`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
