# VanRides Backend

API Java 17 / Spring Boot 3.4.5 / PostgreSQL / Flyway. A raiz Maven é esta pasta;
a raiz Git está dois níveis acima.

Comece pelo [guia da equipe](../../docs/desenvolvimento/backend.md), pelo
[índice técnico](docs/README.md) e pelo [AGENTS.md](AGENTS.md).

Da raiz Git: `bash scripts/check_backend.sh --light` para contratos locais;
`bash scripts/check_backend.sh` para o gate completo com Docker, package e
jornada sintética com o worker real. Daqui, `./scripts/ci_quality_gate.sh`
continua sendo o gate canônico Maven/PostgreSQL sem smoke externo.

Fontes em `src/`, contratos em `contracts/`, gates em `scripts/` e limites
em `quality/`. O código original foi transferido de `mathwidu/vanrides-backend`
no commit `e25ed4e0131bc253b7ccea2fa4ab793e69a89af9`, com adaptações de caminhos,
CI e exemplos locais. Prioridades pertencem ao [programa](../../program/README.md).
