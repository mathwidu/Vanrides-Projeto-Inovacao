# Ambiente remoto Flutter

Este módulo faz parte de `mathwidu/Vanrides-Projeto-Inovacao`. No ambiente
Linux remoto, entre em `src/aplicativo-motorista`. O bootstrap de ferramentas
é opcional e deve ser revisado antes de executar `scripts/codex_cloud_setup.sh`;
use Flutter 3.38.5 e dependências travadas.

Default gate for normal app cards:

```bash
./scripts/ci_quality_gate.sh
```

The CI entrypoint validates the workflow wiring and delegates to
`qa_gate_card.sh --no-e2e`. Sem E2E físico, o gate valida contratos dos scripts,
analyzer, unit/widget tests e ratchet. A CI canônica da raiz é
`../../../.github/workflows/aplicativo.yml`; da raiz execute
`bash scripts/check_app.sh`.

Não há deploy nem secrets no workflow. O teste físico do iPhone permanece
pausado. O guia da equipe está em `../../../docs/desenvolvimento/aplicativo.md`.
