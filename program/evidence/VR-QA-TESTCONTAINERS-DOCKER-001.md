# Recibo histórico — VR-QA-TESTCONTAINERS-DOCKER-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-QA-TESTCONTAINERS-DOCKER-001.md`. SHA-256 do original: `408f6746949232a47c4ae6284ea2db5beba67f29159cc434d87a6854cd83d430`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS pertencem às respectivas revisões históricas e não aprovam este checkout.

## Identidade da execução

- run ID: `VR-QA-TESTCONTAINERS-DOCKER-001-20260716-LOCAL`;
- data: 2026-07-16, `America/Sao_Paulo`;
- repositório: `vanrides-backend`;
- branch: `dev`;
- baseline A/B: `5c52f00aa37a253eb214e91c62b9d32c5008002e`;
- commits técnicos: `e92787239181e39cacd5b7341dc6e0874b2f9fe1`,
  `e79a8700440ce7174e1a2edb8165770ab2322cd6` e
  `ab68d763fec761dc943af08350a6e0a2b97592f7`;
- commit de QA/backlog:
  `2de5dedacaeef78129608d62f412b362cd7da927`;
- ambiente: macOS local, Docker Desktop e PostgreSQL 16 descartável;
- estado do backend no encerramento: limpo.

## Escopo comprovado

O job `test` do workflow versionado executa, em ordem, o gate geral e o perfil
Docker-required. O segundo estágio exige que as três suítes PostgreSQL criem
relatórios frescos com matriz exata `7 + 1 + 2`, zero skip, zero failure e zero
error. Falha de Docker, relatório ausente, contagem divergente ou bypass impede
a cadeia `test -> build_and_push -> deploy_swarm`.

## Revisões e reprodução históricas

- `e92787239181e39cacd5b7341dc6e0874b2f9fe1`
- `e79a8700440ce7174e1a2edb8165770ab2322cd6`
- `ab68d763fec761dc943af08350a6e0a2b97592f7`
- `2de5dedacaeef78129608d62f412b362cd7da927`

- Comando registrado na origem: `python3 scripts/ci_quality_gate_test.py`.
- Comando registrado na origem: `./scripts/ci_quality_gate.sh`.
- Comando registrado na origem: `bash -n`.
- Comando registrado na origem: `git diff --check`.
- Comando registrado na origem: `./mvnw -q -DskipTests clean package`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
