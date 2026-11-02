# Backend no CI e em ambientes de agente

A raiz Git é o repositório acadêmico; a raiz Maven é `src/backend`.
Prepare Java 17, Python 3.10+ e Docker local; use o Maven Wrapper 3.9.9.

Default gate for normal backend cards:

```bash
./scripts/ci_quality_gate.sh
```

The CI entrypoint validates the workflow wiring, runs every gate contract test
and the mandatory PostgreSQL matrix. Docker is required; skipped tests never
count as a passing gate. The exact class/count matrix is in
`scripts/qa_integration_profile.sh`.

O gate rejeita overrides herdados de Git, Docker, Testcontainers, Java e Maven;
preserva Git mínimo, `user.home` isolado, cache Maven explícito e `MAVEN_SKIP_RC=1`.
SHA, tree e fingerprint de alterações cobrem todo o repositório acadêmico.
PASS só é materializado após validar os relatórios e confirmar o release do lock.
XML raw, stdout/stderr, dumps e tokens não são artefatos públicos.

O workflow `../../../.github/workflows/backend.yml` executa o gate completo e
smoke sintético do mesmo checkout em Ubuntu padrão, somente `contents: read`.
Ele não publica pacotes nem implanta. Resultados só comprovam o SHA da execução.

Do not make the CI/CD `test` job depend on the published DEV API.

Project gate with HTTP smoke against DEV é uma operação separada: use somente
um ambiente explicitamente autorizado, passando `VANRIDES_BACKEND_BASE_URL`.
O default do helper agora é `http://127.0.0.1:8080`.

Para aquecer dependências num ambiente já preparado, dentro do módulo:

```bash
bash scripts/codex_cloud_setup.sh
```

Isso não substitui Docker nem o gate. Guia local:
[desenvolvimento](../../../docs/desenvolvimento/backend.md).
