# QA E2E Role Split - VanRides App

Baseline: 2026-07-06

Objetivo: separar validacao ponta a ponta por papel para que admin, motorista,
passageiro e responsavel tenham comandos, setup, falhas e evidencias citaveis
sem misturar erro de ambiente com bug de UX/contrato.

## Gate rapido

- Runner canonico: `scripts/qa_e2e_role_matrix_smoke.sh`
- Check rapido no gate: `./scripts/qa_e2e_role_matrix_smoke.sh --check-only`
- Preflight operacional: `./scripts/qa_e2e_real_preflight.sh`
- Runner do E2E mobile longo: `python3 scripts/qa_mobile_e2e_long_run.py`
- Saida atual esperada: `E2E_ROLE_MATRIX_OK roles=4 mobile=3 web=1`
  e `E2E_REAL_PREFLIGHT_CONTRACT_OK roles=4 mobile=3 web=1`;
  para mobile longo: `MOBILE_E2E_LONG_CONTRACT_OK roles=3`.

O check rapido valida a matriz, runners, suites, pasta de evidencias e
classificacao de falhas. Ele nao roda Flutter E2E real, nao abre navegador e
nao precisa de device, backend ou fixture.

O preflight operacional possui tres modos:

- `--check-only`: contrato rapido para gate, sem efeito externo.
- `--dry-run --role <papel>`: cria resumo em
  `output/e2e-role-smoke/<papel>/<timestamp>/` sem tocar device/backend.
- `--run-preflight --role <papel>`: roda somente setup/preflight antes do E2E
  longo. Nos papeis mobile, chama `scripts/run_dev_e2e.sh` com
  `VANRIDES_E2E_PREFLIGHT_ONLY=1`.

Para `admin-web`, o preflight operacional checa setup local basico. A validacao
web real continua sendo o runner Playwright completo do papel.

O runner do E2E mobile longo possui tres modos:

- `--check-only`: contrato rapido para gate, sem device/backend/fixture.
- `--dry-run --role <papel>`: cria resumo em
  `output/mobile-e2e-long/<papel>/<timestamp>/` sem executar Flutter.
- `--run-role --role <papel>`: chama `scripts/run_dev_e2e.sh`, captura
  `e2e.log`, escreve `SUMMARY.md` e classifica PASS, setup bloqueado ou
  falha de UX/contrato.

O runner tambem possui o papel `operational-fixture-mobile`, que nao substitui
a matriz de preflight por papel. Ele roda a suite operacional multi-papel
`integration_test/operational_fixture/all.dart` quando existe fixture DEV
verificada/vinculada e o objetivo e provar a jornada pre-piloto no app real.

## Evidencias por papel

Evidencias reais ficam fora do Git em:

```text
output/e2e-role-smoke/<papel>/<timestamp>/
output/mobile-e2e-long/<papel>/<timestamp>/
```

Cada execucao real deve registrar no `backlog/QA-Execution-Log.md`:

- papel validado;
- comando executado;
- API/backend e device/browser usados;
- path da pasta em `output/e2e-role-smoke/`;
- se a falha foi `setup` ou `UX/contrato`;
- resumo curto do resultado.

## Matriz canonica

| ID | Papel | Plataforma | Runner | Suite/alvo | Evidencia | Setup |
| --- | --- | --- | --- | --- | --- | --- |
| `admin-web` | Admin | Web | `scripts/qa_admin_portal_web_e2e.sh` | `tool/admin_portal_web_e2e.mjs` | `output/e2e-role-smoke/admin-web/<timestamp>/` | build web, Playwright, bootstrap ou fixture operacional. |
| `driver-mobile` | Motorista | Mobile | `scripts/run_dev_e2e.sh` | `integration_test/motorista/all.dart` | `output/e2e-role-smoke/driver-mobile/<timestamp>/` | device Flutter, backend DEV e dados de cadastro/login. |
| `passenger-mobile` | Passageiro | Mobile | `scripts/run_dev_e2e.sh` | `integration_test/passageiro/all.dart` | `output/e2e-role-smoke/passenger-mobile/<timestamp>/` | device Flutter, backend DEV e dados de cadastro/login. |
| `guardian-mobile` | Responsavel | Mobile | `scripts/run_dev_e2e.sh` | `integration_test/guardian/all.dart` | `output/e2e-role-smoke/guardian-mobile/<timestamp>/` | device Flutter, backend DEV e fixture guardian com passageiro vinculado. |
| `operational-fixture-mobile` | Operacional multi-papel | Mobile | `scripts/run_dev_e2e.sh` | `integration_test/operational_fixture/all.dart` | `output/mobile-e2e-long/operational-fixture-mobile/<timestamp>/` | device Flutter, backend DEV e fixture operacional verificada/vinculada. |

## Comandos por papel

### Admin web

```bash
ADMIN_PORTAL_E2E_REPORT_DIR=output/e2e-role-smoke/admin-web/<timestamp> \
ADMIN_PORTAL_E2E_SCREENSHOT_DIR=output/e2e-role-smoke/admin-web/<timestamp>/screenshots \
./scripts/qa_admin_portal_web_e2e.sh
```

### Motorista mobile

```bash
./scripts/qa_e2e_real_preflight.sh --run-preflight --role driver-mobile --device <device> --api http://127.0.0.1:8080
./scripts/run_dev_e2e.sh integration_test/motorista/all.dart <device>
```

### Passageiro mobile

```bash
./scripts/qa_e2e_real_preflight.sh --run-preflight --role passenger-mobile --device <device> --api http://127.0.0.1:8080
./scripts/run_dev_e2e.sh integration_test/passageiro/all.dart <device>
```

### Responsavel mobile

```bash
GUARDIAN_E2E_EMAIL=<email> \
GUARDIAN_E2E_PASSWORD=<senha> \
GUARDIAN_E2E_PASSAGEIRO=<nome> \
./scripts/qa_e2e_real_preflight.sh --run-preflight --role guardian-mobile --device <device> --api http://127.0.0.1:8080

GUARDIAN_E2E_EMAIL=<email> \
GUARDIAN_E2E_PASSWORD=<senha> \
GUARDIAN_E2E_PASSAGEIRO=<nome> \
./scripts/run_dev_e2e.sh integration_test/guardian/all.dart <device>
```

### Operacional mobile com fixture

```bash
python3 scripts/qa_mobile_e2e_long_run.py \
  --run-role \
  --role operational-fixture-mobile \
  --device <device> \
  --api http://127.0.0.1:8080 \
  --fixture-env <fixture.env>
```

## Classificacao de falhas

| Sinal | Classificacao | Acao |
| --- | --- | --- |
| `BLOCKED_VALIDATION_MISSING_DEVICE` | setup | Registrar device ausente e nao classificar como bug do app. |
| `BLOCKED_VALIDATION_BACKEND_CONNECTIVITY` | setup | Registrar API/backend, status HTTP e log; nao retentar em loop. |
| `BLOCKED_VALIDATION_MISSING_FIXTURE_ENV` | setup | Seedar fixture ou carregar `scripts/.dev.env.local` antes de rodar o papel. |
| `BLOCKED_VALIDATION_MISSING_FIXTURE` | setup | Corrigir login, agenda ou dados esperados da fixture. |
| `BLOCKED_VALIDATION_EMAIL_VERIFICATION` | setup | Publicar DEV com auto-verificacao QA ou confirmar e-mail da fixture. |
| `BLOCKED_VALIDATION_IOS_WIRELESS_PUBLISH_PORT` | setup | Usar iOS Simulator, device iOS via USB ou criar runner `flutter drive`; nao classificar como bug do app. |
| Falha em step Playwright classificado como setup | setup | Corrigir build web, servidor estatico, browser ou bootstrap. |
| Falha em step Playwright classificado como UX/contrato | UX/contrato | Abrir ou corrigir bug com screenshot, step e papel `admin-web`. |
| Falha Flutter depois do preflight verde | UX/contrato | Corrigir ou abrir follow-up com papel, suite, device, log e reproducao. |

## Regra de uso

- Rodar um papel isolado quando o card mexer em fluxo daquele papel.
- Rodar `integration_test/all.dart` somente como agregado depois de os papeis
  individuais estarem compreensiveis.
- Registrar evidencias por papel, nao apenas uma linha "E2E falhou".
- Para cards sem E2E real, registrar que o gate rapido validou a matriz e que
  o smoke real depende de device/backend/fixture.
