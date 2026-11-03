# QA Visual Regression Control - VanRides App

Baseline: 2026-07-06

Objetivo: manter regressao visual comparavel para tracking, motorista,
responsavel e admin sem transformar o gate rapido em um fluxo dependente de
device, backend ou credenciais.

## Baseline Oficial

- Matriz historica: `backlog/UX-Visual-QA-Matrix-2026-06-19.md`
- Runner canonico: `scripts/qa_visual_matrix_smoke.sh`
- Check rapido no gate: `./scripts/qa_visual_matrix_smoke.sh --check-only`
- Saida atual esperada: `VISUAL_MATRIX_OK total=27 deterministic=14 real=13 planned=0`

O check rapido valida estrutura da matriz, paths `.png`, duplicidade de
screenshots, tipo de cobertura e permissao de execucao dos runners. Ele nao
captura imagens e nao precisa de iOS Simulator, backend DEV ou fixture.

## Pasta De Evidencias

Screenshots e logs devem ficar fora do Git em:

```text
output/visual-smoke/<card-ou-fase>/<timestamp>/
```

Exemplos:

```text
output/visual-smoke/qa-visual-regression-001/20260706-visual-matrix/
output/visual-smoke/qa-visual-regression-001/20260706-real-dev/
```

Essas pastas sao evidencias locais citaveis no `backlog/QA-Execution-Log.md`.
Nao versionar PNGs, videos, fixtures, secrets, logs com credenciais ou artefatos
pesados.

## Modos De Execucao

### Gate rapido

```bash
./scripts/qa_visual_matrix_smoke.sh --check-only
```

Uso: sempre que um card visual muda tracking, motorista, responsavel, admin,
onboarding ou conta.

### Capturas deterministicas

```bash
VANRIDES_TEST_DEVICE=<ios-simulator-id> \
  ./scripts/qa_visual_matrix_smoke.sh --run-deterministic \
  --output-dir output/visual-smoke/<card>/deterministic
```

Uso: estados fake/estaveis sem backend, como conta, onboarding e estados vazios.

### Capturas reais DEV/fixture

```bash
VANRIDES_TEST_DEVICE=<ios-simulator-id> \
VANRIDES_VISUAL_FIXTURE_ENV=../vanrides-backend/output/<fixture>/fixture.env \
VANRIDES_API_BASE_URL=http://127.0.0.1:8080 \
  ./scripts/qa_visual_matrix_smoke.sh --run-real \
  --output-dir output/visual-smoke/<card>/real-dev
```

Uso: somente quando fixture/API/device estao estaveis e o card precisa provar
fluxo real. Se falhar por setup, registrar como bloqueio de validacao, nao como
regressao visual.

## Classificacao De Falhas

| Sinal | Classificacao | Acao |
| --- | --- | --- |
| `VISUAL_MATRIX_OK` ausente no `--check-only` | Regressao de contrato QA | Corrigir matriz, runner ausente, path duplicado ou coverage invalida antes de fechar card. |
| Runner sem permissao de execucao | Setup de repo/QA | Corrigir permissao do script e rerodar `--check-only`. |
| `BLOCKED_VALIDATION_MISSING_DEVICE` ou device `macos` | Setup local | Registrar limite no QA log; nao chamar de bug visual. |
| Fixture inexistente, login 401 ou backend 5xx | Setup/ambiente DEV | Registrar API/fixture/log; nao retentar em loop. |
| Screenshot gerado com texto cortado, card sobreposto ou estado errado | Regressao visual | Abrir/fixar card com path do PNG, papel, estado e reproducao. |
| Screenshot ausente mas teste Flutter passou sem marcador `*_VISUAL_READY` | Regressao do runner | Corrigir marcador ou parser antes de aceitar evidencia. |

## Regra Para Cards Futuros

Todo card que muda UI deve declarar no QA log:

- estados da matriz afetados;
- comando visual executado ou justificativa de nao execucao;
- pasta de evidencia quando houver screenshot;
- classificacao de qualquer falha como setup, ambiente ou regressao visual;
- card follow-up quando uma regressao visual real nao for corrigida no mesmo
  patch.

Para mudancas sem UI, basta registrar que a matriz visual nao se aplica.
