# Recibo histórico — VR-BE-RUNTIME-PUSH-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-BE-RUNTIME-PUSH-001.md`. SHA-256 do original: `06d685b549aff8144a60dcd3501ac97690882544d087e4c92a5c515d10a7c9ca`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, FAIL, SKIPPED pertencem às respectivas revisões históricas e não aprovam este checkout.

## Identidade da execução

- run ID: `VR-BE-RUNTIME-PUSH-001-20260716-LOCAL`;
- data: 2026-07-16, `America/Sao_Paulo`;
- repositório: `vanrides-backend`;
- branch: `dev`;
- baseline com falha: `34b0cd02c199b74c4c86cb741a61f8cbb0329cf0`;
- SHA técnico: `117b8ca27353164814b5af602dba5f97d23fb655`;
- SHA de QA/backlog: `5c52f00aa37a253eb214e91c62b9d32c5008002e`;
- ambiente: macOS local, JAR Spring Boot e PostgreSQL 16 descartável;
- estado do backend no encerramento: limpo.

## Escopo comprovado

Esta execução comprova que o JAR inicia na composição padrão quando não há
provider real de push, registra exatamente um gateway noop e mantém o dispatch
sem saída externa. Também comprova que cardinalidade ou identidade inválidas
falham o contexto independentemente de `@Primary`.

## Revisões e reprodução históricas

- `117b8ca27353164814b5af602dba5f97d23fb655`
- `5c52f00aa37a253eb214e91c62b9d32c5008002e`
- `e92787239181e39cacd5b7341dc6e0874b2f9fe1`
- `e79a8700440ce7174e1a2edb8165770ab2322cd6`
- `ab68d763fec761dc943af08350a6e0a2b97592f7`
- `2de5dedacaeef78129608d62f412b362cd7da927`

- Comando registrado na origem: `./mvnw test`.
- Comando registrado na origem: `./scripts/ci_quality_gate.sh`.
- Comando registrado na origem: `python3 scripts/program_control.py check`.
- Comando registrado na origem: `git diff --check`.
- Comando registrado na origem: `VANRIDES_PUSH_PROVIDER=none`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
