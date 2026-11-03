# VanRides UX Visual QA Matrix

Status: ACTIVE BASELINE
Date: 2026-06-19
Related plan: `UX-UI-Complete-Improvement-Plan-2026-06-19.md`
Related card: `UX-0901 - QA visual deterministico completo`
Runner: `scripts/qa_visual_matrix_smoke.sh`

## 1. Purpose

This file defines the visual QA coverage for the UX/UI refinement phase. The
goal is not just to know that tests pass; it is to keep stable screenshots for
the states a real user sees across admin, passenger, driver and guardian
experiences.

The runner has three modes:
- `--check-only`: validates the matrix without device/backend.
- `--run-deterministic`: captures fake deterministic mobile states that do not
  require backend.
- `--run-real`: captures real DEV/fixture states for admin web and operational
  mobile flows.

## 2. Impact Analysis

### Context

There were already several visual smoke scripts, but no single matrix saying
which role/state each screenshot represents, which runner owns it and which
states are still gaps. UX-0901 creates that stable index.

### Technical changes

- Create `scripts/qa_visual_matrix_smoke.sh`.
- Create this visual QA matrix.
- Update QA log with validation evidence.
- No runtime Flutter code changes.
- No backend contract changes.

### Affected contracts

No API contract changed. The affected contract is QA naming:
- screenshot names must stay stable;
- every visual state must have role, state, output path, runner and coverage
  type;
- planned gaps must be explicit when they exist.

### Risks and mitigation

- Risk: visual smoke promises screenshots that existing tests do not emit.
  - Mitigation: matrix paths match current runner output directories and ready
    markers.
- Risk: real DEV screenshots fail because fixture/API/device is not available.
  - Mitigation: `--check-only` is the fast gate; `--run-real` is explicit.
- Risk: planned gaps are forgotten.
  - Mitigation: gaps are counted by the runner; the current baseline has no
    planned gaps.

### Minimum validation

- `bash -n scripts/qa_visual_matrix_smoke.sh scripts/qa_role_empty_visual_smoke.sh`
- `./scripts/qa_visual_matrix_smoke.sh --check-only`
- `./scripts/qa_visual_matrix_smoke.sh --list`
- `./scripts/qa_role_empty_visual_smoke.sh --device <ios-device-id> --output-dir <path>`
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`

## 3. Coverage Summary

Current baseline:
- Total visual states tracked: 27.
- Deterministic states: 14.
- Real DEV/fixture states: 13.
- Planned gaps: 0.

The former planned gaps for `driver|sem-empresa` and
`guardian|sem-passageiro` are now covered by deterministic screenshots.

## 4. Canonical Screenshot Matrix

| Role | State | Stable screenshot | Runner | Coverage |
| --- | --- | --- | --- | --- |
| admin | dashboard-completo | `admin-web/admin-dashboard.png` | `qa_admin_portal_web_e2e.sh` | real |
| admin | viagens-do-dia | `admin-web/admin-trips.png` | `qa_admin_portal_web_e2e.sh` | real |
| admin | servico-operacional | `admin-web/admin-services.png` | `qa_admin_portal_web_e2e.sh` | real |
| admin | passageiro-completo | `admin-web/admin-passenger-detail.png` | `qa_admin_portal_web_e2e.sh` | real |
| admin | motorista-equipe | `admin-web/admin-drivers.png` | `qa_admin_portal_web_e2e.sh` | real |
| admin | responsavel-vinculado | `admin-web/admin-guardian-passengers.png` | `qa_admin_portal_web_e2e.sh` | real |
| admin | conta | `admin-web/admin-account.png` | `qa_admin_portal_web_e2e.sh` | real |
| admin | login-bootstrap | `onboarding/admin-onboarding-portal.png` | `qa_passenger_onboarding_visual_smoke.sh` | deterministic |
| passenger | sem-empresa | `onboarding/passenger-onboarding-gate.png` | `qa_passenger_onboarding_visual_smoke.sh` | deterministic |
| passenger | busca-empresa | `onboarding/passenger-onboarding-search.png` | `qa_passenger_onboarding_visual_smoke.sh` | deterministic |
| passenger | solicitacao-pendente | `onboarding/passenger-onboarding-pending.png` | `qa_passenger_onboarding_visual_smoke.sh` | deterministic |
| passenger | convite | `onboarding/passenger-onboarding-invite.png` | `qa_passenger_onboarding_visual_smoke.sh` | deterministic |
| passenger | empresa-vinculada | `onboarding/passenger-onboarding-linked.png` | `qa_passenger_onboarding_visual_smoke.sh` | deterministic |
| passenger | email-pendente | `onboarding/passenger-onboarding-email-pending.png` | `qa_passenger_onboarding_visual_smoke.sh` | deterministic |
| passenger | viagem-real | `phase/passenger-trip.png` | `qa_phase_0466_visual_smoke.sh` | real |
| passenger | presenca-real | `phase/passenger-presence.png` | `qa_phase_0466_visual_smoke.sh` | real |
| passenger | perfil-completo | `account/account-passenger-complete.png` | `qa_account_profile_visual_smoke.sh` | deterministic |
| passenger | perfil-incompleto | `account/account-passenger-incomplete.png` | `qa_account_profile_visual_smoke.sh` | deterministic |
| driver | sem-empresa | `role-empty/driver-onboarding-pending.png` | `qa_role_empty_visual_smoke.sh` | deterministic |
| driver | viagem-real | `phase/driver-home.png` | `qa_phase_0466_visual_smoke.sh` | real |
| driver | conta | `account/account-driver-complete.png` | `qa_account_profile_visual_smoke.sh` | deterministic |
| guardian | sem-passageiro | `role-empty/guardian-empty.png` | `qa_role_empty_visual_smoke.sh` | deterministic |
| guardian | home-com-passageiro | `phase/guardian-home.png` | `qa_phase_0466_visual_smoke.sh` | real |
| guardian | localizacao | `phase/guardian-location.png` | `qa_phase_0466_visual_smoke.sh` | real |
| guardian | presencas | `phase/guardian-presence.png` | `qa_phase_0466_visual_smoke.sh` | real |
| guardian | conta | `account/account-guardian-complete.png` | `qa_account_profile_visual_smoke.sh` | deterministic |
| guardian | perfil-admin | `account/account-admin-complete.png` | `qa_account_profile_visual_smoke.sh` | deterministic |

## 5. How To Run

Fast validation:

```bash
./scripts/qa_visual_matrix_smoke.sh --check-only
```

List matrix:

```bash
./scripts/qa_visual_matrix_smoke.sh --list
```

Deterministic screenshots with iOS Simulator:

```bash
VANRIDES_TEST_DEVICE=<ios-device-id> \
  ./scripts/qa_visual_matrix_smoke.sh --run-deterministic \
  --output-dir output/visual-smoke/ux-0901/manual
```

Focused deterministic screenshots for the role-empty states:

```bash
./scripts/qa_role_empty_visual_smoke.sh \
  --device <ios-device-id> \
  --output-dir output/visual-smoke/ux-0901/role-empty
```

Real DEV/fixture screenshots:

```bash
VANRIDES_TEST_DEVICE=<ios-device-id> \
VANRIDES_VISUAL_FIXTURE_ENV=../vanrides-backend/output/<fixture>/fixture.env \
VANRIDES_API_BASE_URL=http://127.0.0.1:8080 \
  ./scripts/qa_visual_matrix_smoke.sh --run-real \
  --output-dir output/visual-smoke/ux-0901/dev
```

## 6. Acceptance Rules For Future UX Cards

Every future UX card must:
- declare which visual matrix states it affects;
- update this matrix if a new state is introduced;
- run `--check-only` at minimum;
- run deterministic or real screenshots when the card changes a visual surface;
- record screenshot folder paths in `QA-Execution-Log.md`;
- turn any visual failure into a card or an explicit limitation.
