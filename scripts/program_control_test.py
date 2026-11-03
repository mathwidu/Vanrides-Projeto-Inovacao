#!/usr/bin/env python3
"""Unit tests for scripts/program_control.py."""

from __future__ import annotations

import hashlib
import io
import json
import subprocess
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

import program_control


READY_CARD = """# CARD-001

## Resultado esperado

Resultado observável.

## Impact Analysis

Impacto conhecido.

### Contratos afetados

Contrato conhecido.

### Riscos e mitigação

Risco e mitigação conhecidos.

### Validação mínima

Teste, gate e evidência definidos.

## Dependências

Nenhuma.

## Escopo

Escopo conhecido.

## Fora de escopo

Não escopo conhecido.

## Critérios de aceite

- [ ] critério a implementar durante o card.

## Rollback

Rollback conhecido.

## Definition of Ready

- [x] contrato completo.
"""

VALID_DONE_SHA = "0123456789abcdef0123456789abcdef01234567"


def valid_done_evidence(sha: str = VALID_DONE_SHA) -> str:
    return f"""# Evidência CARD-001

## Resultado

PASS local registrado em 2026-07-18 para a versão `{sha}`. A validação
comprovou os critérios do card e preservou os limites operacionais declarados.

## Comando e saída

Comando executado: `python3 scripts/program_control.py check`.

O resultado foi PASS, sem erro ou waiver. A evidência liga a execução ao commit
completo `{sha}` e descreve explicitamente o que foi observado no ambiente
local. Este texto adicional representa a síntese dos checks, da revisão e da
proveniência, sem substituir os logs ou atribuir uma promoção inexistente.

## Limites e rollback

O rollback permanece documentado no card. Este resultado não autoriza push,
deploy, piloto ou produção e não amplia o fora de escopo. Nenhum segredo ou
dado pessoal foi registrado. A conclusão vale apenas para o commit indicado e
para os comandos listados nesta evidência canônica.
"""


def valid_fixture(root: Path) -> tuple[dict, dict]:
    card_path = root / "program" / "cards"
    card_path.mkdir(parents=True)
    (card_path / "CARD-001.md").write_text(READY_CARD, encoding="utf-8")
    program = {
        "schemaVersion": 1,
        "policy": {"maxActiveCards": 1},
        "workstreams": [{"id": "WS", "name": "Workstream", "owner": "owner"}],
        "milestones": [
            {"id": "G0", "name": "Gate", "status": "in_progress", "dependsOn": []}
        ],
        "epics": [
            {
                "id": "EPIC-001",
                "workstream": "WS",
                "name": "Epic",
                "status": "in_progress",
                "dependsOn": [],
            }
        ],
        "cards": [
            {
                "id": "CARD-001",
                "epic": "EPIC-001",
                "title": "Card",
                "status": "in_progress",
                "repositories": ["coordination"],
                "dependsOn": [],
                "cardFile": "program/cards/CARD-001.md",
                "allowedDirtyPaths": {"coordination": ["program/"]},
                "blockers": [],
                "commits": [],
                "evidence": [],
            }
        ],
        "activeCardId": "CARD-001",
        "nextCardId": "CARD-001",
    }
    repositories = {
        "schemaVersion": 1,
        "repositories": [
            {
                "id": "coordination",
                "path": ".",
                "cardGate": "check",
                "ciGate": "test",
                "knownDirtyPaths": [],
            }
        ],
    }
    return program, repositories


def write_control_fixture(root: Path, program: dict, repositories: dict) -> None:
    program_path = root / "program"
    program_path.mkdir(parents=True, exist_ok=True)
    (program_path / "program.json").write_text(
        json.dumps(program), encoding="utf-8"
    )
    (program_path / "repositories.json").write_text(
        json.dumps(repositories), encoding="utf-8"
    )


def adr005_acceptance_record(
    *,
    status: str,
    overrides: dict[str, str] | None = None,
    omitted: set[str] | None = None,
) -> str:
    values = {
        "aprovador de Produto": "Ada Produto",
        "autoridade de Produto": "Head de Produto",
        "aprovador de Privacidade": "Bia Privacidade",
        "autoridade de Privacidade": "DPO",
        "frase recebida": (
            "Aceito a recomendação completa da ADR-005 na revisão "
            f"{program_control.ADR005_REVISION}, SHA-256 "
            f"{program_control.ADR005_DIGEST}. "
            "Aprovador de Produto: Ada Produto, Head de Produto. "
            "Aprovador de Privacidade: Bia Privacidade, DPO. "
            "Declaro ciência das declarações obrigatórias deste registro, "
            "inclusive das pendências jurídica/DPO e de que este aceite não "
            "concede GO a piloto ou produção"
        ),
        "timestamp do registro": "2026-07-18T13:30:00Z",
        "declarações obrigatórias": "CIENTE",
    }
    if status == "WAITING_HUMAN":
        values = {label: "PENDING" for label in values}
    values.update(overrides or {})
    omitted = omitted or set()
    metadata_lines = "\n".join(
        f"- {label}: `{value}`;"
        for label, value in values.items()
        if label not in omitted
    )
    verdict = "WAITING_HUMAN" if status == "WAITING_HUMAN" else "ACCEPTED"
    return f"""# Registro de aceite — ADR-005

- status: `{status}`
- commit de coordenação: `{program_control.ADR005_REVISION}`
- SHA-256 da ADR: `{program_control.ADR005_DIGEST}`

## Declarações obrigatórias

- base legal continua pendente de validação jurídica/DPO;
- retenção ativa é 72 horas;
- nenhum aceite desta ADR autoriza push, deploy, banco remoto, dado real, piloto;
- `Pilot` e `Production` permanecem `HOLD`.

## Preenchimento após a resposta

{metadata_lines}
- veredito: `{verdict}`.
"""


def write_adr005_fixture(root: Path, *, card_status: str, record: str) -> dict:
    decisions = root / "program" / "decisions"
    decisions.mkdir(parents=True)
    if card_status == program_control.ADR005_REFREEZE_CARD_STATUS:
        frozen_bytes = (
            program_control.ROOT
            / "program"
            / "decisions"
            / "ADR-005-trip-scoped-location-access.md"
        ).read_bytes()
    else:
        # Coordination-only CI snapshots deliberately omit unpublished history.
        frozen_fixture = program_control.ROOT / ".github/ci/fixtures/ADR-005-superseded.md"
        if frozen_fixture.is_file():
            frozen_bytes = frozen_fixture.read_bytes()
        else:
            frozen_bytes = subprocess.run(
                [
                    "git",
                    "show",
                    f"{program_control.ADR005_REVISION}:program/decisions/"
                    "ADR-005-trip-scoped-location-access.md",
                ],
                cwd=program_control.ROOT,
                check=True,
                capture_output=True,
            ).stdout
        if hashlib.sha256(frozen_bytes).hexdigest() != program_control.ADR005_DIGEST:
            raise ValueError("ADR005_TEST_FIXTURE_HASH_MISMATCH")
    (decisions / "ADR-005-trip-scoped-location-access.md").write_bytes(frozen_bytes)
    (decisions / "ADR-005-acceptance.md").write_text(record, encoding="utf-8")
    blockers = []
    if card_status == "waiting_human":
        blockers = [
            "Aceite humano da revisão "
            f"{program_control.ADR005_REVISION}, SHA-256 "
            f"{program_control.ADR005_DIGEST}."
        ]
    elif card_status == program_control.ADR005_REFREEZE_CARD_STATUS:
        blockers = [
            "A revisão histórica da ADR-005 está SUPERSEDED/DO_NOT_ACCEPT por "
            "reservar Flyway antes de ready; produzir novo freeze antes de "
            "retornar a waiting_human."
        ]
    return {
        "cards": [
            {
                "id": program_control.ADR005_CARD_ID,
                "status": card_status,
                "blockers": blockers,
                "commits": [f"coordination:{program_control.ADR005_REVISION}"],
            }
        ]
    }


def validate_non_superseded_adr005(program: dict, root: Path):
    """Exercise the generic future acceptance flow without revoked bindings."""
    with mock.patch.object(
        program_control, "ADR005_SUPERSEDED_BINDINGS", frozenset()
    ):
        return program_control.validate_adr005_acceptance(program, root)


class ProgramControlTest(unittest.TestCase):
    def test_valid_static_fixture(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            issues = program_control.validate_static(program, repositories, root)
            self.assertEqual(
                [], [issue for issue in issues if issue.level == "ERROR"]
            )

    def test_wip_limit_is_enforced(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            second = dict(program["cards"][0])
            second["id"] = "CARD-002"
            second["cardFile"] = "program/cards/CARD-002.md"
            (root / second["cardFile"]).write_text(
                READY_CARD.replace("CARD-001", "CARD-002"), encoding="utf-8"
            )
            program["cards"].append(second)
            issues = program_control.validate_static(program, repositories, root)
            self.assertIn("WIP_LIMIT", {issue.code for issue in issues})

    def test_missing_dependency_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["dependsOn"] = ["CARD-MISSING"]
            issues = program_control.validate_static(program, repositories, root)
            self.assertIn("MISSING_DEPENDENCY", {issue.code for issue in issues})

    def test_done_requires_commit_and_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "done"
            program["activeCardId"] = None
            program["nextCardId"] = "CARD-001"
            issues = program_control.validate_static(program, repositories, root)
            codes = {issue.code for issue in issues}
            self.assertIn("DONE_WITHOUT_COMMIT", codes)
            self.assertIn("DONE_WITHOUT_EVIDENCE", codes)

    def test_done_rejects_baseline_as_card_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            card = program["cards"][0]
            card["status"] = "done"
            card["commits"] = ["coordination:abc123"]
            card["evidence"] = ["program/BASELINE.md"]
            program["activeCardId"] = None
            program["nextCardId"] = None

            issues = program_control.validate_static(program, repositories, root)

            self.assertIn(
                "DONE_WITHOUT_CARD_EVIDENCE", {issue.code for issue in issues}
            )

    def test_done_rejects_non_list_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            card = program["cards"][0]
            card["status"] = "done"
            card["commits"] = ["coordination:abc123"]
            card["evidence"] = "program/evidence/CARD-001.md"
            program["activeCardId"] = None
            program["nextCardId"] = None

            issues = program_control.validate_static(program, repositories, root)

            self.assertIn("DONE_EVIDENCE_TYPE", {issue.code for issue in issues})

    def test_done_rejects_missing_canonical_evidence_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            card = program["cards"][0]
            card["status"] = "done"
            card["commits"] = ["coordination:abc123"]
            card["evidence"] = ["program/evidence/CARD-001.md"]
            program["activeCardId"] = None
            program["nextCardId"] = None

            issues = program_control.validate_static(program, repositories, root)

            self.assertIn(
                "DONE_CARD_EVIDENCE_MISSING", {issue.code for issue in issues}
            )

    def test_done_rejects_canonical_evidence_without_card_id(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            evidence_path = root / "program" / "evidence" / "CARD-001.md"
            evidence_path.parent.mkdir(parents=True)
            evidence_path.write_text("# Evidência sem identidade\n", encoding="utf-8")
            card = program["cards"][0]
            card["status"] = "done"
            card["commits"] = ["coordination:abc123"]
            card["evidence"] = ["program/evidence/CARD-001.md"]
            program["activeCardId"] = None
            program["nextCardId"] = None

            issues = program_control.validate_static(program, repositories, root)

            self.assertIn("DONE_CARD_EVIDENCE_ID", {issue.code for issue in issues})

    def test_done_rejects_canonical_evidence_with_invalid_utf8(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            evidence_path = root / "program" / "evidence" / "CARD-001.md"
            evidence_path.parent.mkdir(parents=True)
            evidence_path.write_bytes(b"# CARD-001\n\xff\xfe")
            card = program["cards"][0]
            card["status"] = "done"
            card["commits"] = ["coordination:abc123"]
            card["evidence"] = ["program/evidence/CARD-001.md"]
            program["activeCardId"] = None
            program["nextCardId"] = None

            issues = program_control.validate_static(program, repositories, root)

            self.assertIn(
                "DONE_CARD_EVIDENCE_UNREADABLE", {issue.code for issue in issues}
            )

    def test_done_accepts_existing_card_specific_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            evidence_path = root / "program" / "evidence" / "CARD-001.md"
            evidence_path.parent.mkdir(parents=True)
            evidence_path.write_text(valid_done_evidence(), encoding="utf-8")
            card_path = root / "program" / "cards" / "CARD-001.md"
            card_path.write_text(
                READY_CARD.replace(
                    "- [ ] critério a implementar durante o card.",
                    "- [x] critério implementado e comprovado.",
                ),
                encoding="utf-8",
            )
            card = program["cards"][0]
            card["status"] = "done"
            card["commits"] = [f"coordination:{VALID_DONE_SHA}"]
            card["evidence"] = ["program/evidence/CARD-001.md"]
            program["activeCardId"] = None
            program["nextCardId"] = None

            issues = program_control.validate_static(program, repositories, root)
            done_errors = {
                issue.code
                for issue in issues
                if issue.code.startswith("DONE_")
            }

            self.assertEqual(set(), done_errors)

    def test_done_rejects_pending_acceptance_criterion(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            evidence_path = root / "program" / "evidence" / "CARD-001.md"
            evidence_path.parent.mkdir(parents=True)
            evidence_path.write_text(valid_done_evidence(), encoding="utf-8")
            card = program["cards"][0]
            card["status"] = "done"
            card["commits"] = [VALID_DONE_SHA]
            card["evidence"] = ["program/evidence/CARD-001.md"]
            program["activeCardId"] = None
            program["nextCardId"] = None

            issues = program_control.validate_static(program, repositories, root)

            codes = {issue.code for issue in issues}
            self.assertIn("DONE_ACCEPTANCE_PENDING", codes)
            self.assertIn("DONE_CHECKLIST_PENDING", codes)

    def test_done_rejects_malformed_commit_reference(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            card = program["cards"][0]
            card["status"] = "done"
            card["commits"] = ["coordination:abc123"]
            program["activeCardId"] = None
            program["nextCardId"] = None

            issues = program_control.validate_static(program, repositories, root)

            self.assertIn("DONE_COMMIT_FORMAT", {issue.code for issue in issues})

    def test_done_rejects_weak_canonical_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            evidence_path = root / "program" / "evidence" / "CARD-001.md"
            evidence_path.parent.mkdir(parents=True)
            evidence_path.write_text("# CARD-001\n", encoding="utf-8")
            card = program["cards"][0]
            card["status"] = "done"
            card["commits"] = [VALID_DONE_SHA]
            card["evidence"] = ["program/evidence/CARD-001.md"]
            program["activeCardId"] = None
            program["nextCardId"] = None

            issues = program_control.validate_static(program, repositories, root)
            codes = {issue.code for issue in issues}

            self.assertIn("DONE_EVIDENCE_TOO_SHORT", codes)
            self.assertIn("DONE_EVIDENCE_HEADINGS", codes)
            self.assertIn("DONE_EVIDENCE_PASS", codes)
            self.assertIn("DONE_EVIDENCE_COMMAND", codes)

    def test_done_rejects_evidence_without_registered_commit(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            evidence_path = root / "program" / "evidence" / "CARD-001.md"
            evidence_path.parent.mkdir(parents=True)
            evidence_path.write_text(
                valid_done_evidence("fedcba9876543210fedcba9876543210fedcba98"),
                encoding="utf-8",
            )
            card = program["cards"][0]
            card["status"] = "done"
            card["commits"] = [VALID_DONE_SHA]
            card["evidence"] = ["program/evidence/CARD-001.md"]
            program["activeCardId"] = None
            program["nextCardId"] = None

            issues = program_control.validate_static(program, repositories, root)

            self.assertIn(
                "DONE_EVIDENCE_COMMIT_REF", {issue.code for issue in issues}
            )

    def test_dependency_cycle_is_detected(self) -> None:
        graph = {"A": ["B"], "B": ["C"], "C": ["A"]}
        cycles = program_control.detect_cycles(graph)
        self.assertTrue(cycles)

    def test_ready_card_requires_done_dependencies(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "ready"
            program["cards"][0]["dependsOn"] = ["G0"]
            program["activeCardId"] = None
            program["nextCardId"] = "CARD-001"
            issues = program_control.validate_static(program, repositories, root)
            self.assertIn("READY_DEPENDENCY", {issue.code for issue in issues})

    def test_blockers_must_be_non_empty_text_list(self) -> None:
        invalid_values = ("blocked", None, [""], ["   "], [42])
        for blockers in invalid_values:
            with self.subTest(blockers=blockers):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    program, repositories = valid_fixture(root)
                    program["cards"][0]["blockers"] = blockers

                    issues = program_control.validate_static(
                        program, repositories, root
                    )

                    self.assertIn(
                        "BLOCKER_TYPE", {issue.code for issue in issues}
                    )

    def test_each_waiting_status_requires_blocker(self) -> None:
        for status in program_control.BLOCKED_STATUSES:
            with self.subTest(status=status):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    program, repositories = valid_fixture(root)
                    program["cards"][0]["status"] = status
                    program["activeCardId"] = None
                    program["nextCardId"] = None

                    issues = program_control.validate_static(
                        program, repositories, root
                    )

                    self.assertIn(
                        "MISSING_BLOCKER", {issue.code for issue in issues}
                    )

    def test_each_executable_status_rejects_blocker(self) -> None:
        for status in program_control.EXECUTABLE_STATUSES:
            with self.subTest(status=status):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    program, repositories = valid_fixture(root)
                    program["cards"][0]["status"] = status
                    program["cards"][0]["blockers"] = ["Ainda bloqueado."]
                    if status == "ready":
                        program["activeCardId"] = None

                    issues = program_control.validate_static(
                        program, repositories, root
                    )

                    self.assertIn(
                        "EXECUTABLE_WITH_BLOCKERS",
                        {issue.code for issue in issues},
                    )

    def test_ready_card_requires_contract_sections(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "ready"
            program["activeCardId"] = None
            (root / program["cards"][0]["cardFile"]).write_text(
                "# CARD-001\n\n## Resultado esperado\n\nResultado.\n",
                encoding="utf-8",
            )

            issues = program_control.validate_static(program, repositories, root)

            self.assertIn(
                "EXECUTABLE_CONTRACT_SECTION", {issue.code for issue in issues}
            )

    def test_ready_card_requires_dor_checklist(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "ready"
            program["activeCardId"] = None
            card_path = root / program["cards"][0]["cardFile"]
            card_path.write_text(
                READY_CARD.replace("- [x] contrato completo.", "Contrato completo."),
                encoding="utf-8",
            )

            issues = program_control.validate_static(program, repositories, root)

            self.assertIn(
                "EXECUTABLE_DOR_CHECKLIST", {issue.code for issue in issues}
            )

    def test_ready_card_rejects_pending_dor_item(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "ready"
            program["activeCardId"] = None
            card_path = root / program["cards"][0]["cardFile"]
            card_path.write_text(
                READY_CARD.replace("- [x] contrato completo.", "- [ ] pendente."),
                encoding="utf-8",
            )

            issues = program_control.validate_static(program, repositories, root)

            self.assertIn(
                "EXECUTABLE_DOR_PENDING", {issue.code for issue in issues}
            )

    def test_ready_card_accepts_complete_contract_and_dor(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "ready"
            program["activeCardId"] = None

            issues = program_control.validate_static(program, repositories, root)
            executable_errors = {
                issue.code
                for issue in issues
                if issue.code.startswith("EXECUTABLE_")
            }

            self.assertEqual(set(), executable_errors)

    def test_active_status_cannot_bypass_contract_or_dor(self) -> None:
        for status in ("in_progress", "validating"):
            with self.subTest(status=status):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    program, repositories = valid_fixture(root)
                    program["cards"][0]["status"] = status
                    (root / program["cards"][0]["cardFile"]).write_text(
                        "# CARD-001\n\n## Resultado esperado\n\nResultado.\n",
                        encoding="utf-8",
                    )

                    issues = program_control.validate_static(
                        program, repositories, root
                    )

                    self.assertIn(
                        "EXECUTABLE_CONTRACT_SECTION",
                        {issue.code for issue in issues},
                    )

    def test_fenced_headings_and_checkboxes_do_not_satisfy_contract(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            fake_contract = """# CARD-001

```markdown
## Resultado esperado
## Impact Analysis
### Contratos afetados
### Riscos e mitigação
### Validação mínima
## Dependências
## Escopo
## Fora de escopo
## Critérios de aceite
## Rollback
## Definition of Ready
- [x] conteúdo de exemplo.
```
"""
            (root / program["cards"][0]["cardFile"]).write_text(
                fake_contract, encoding="utf-8"
            )

            issues = program_control.validate_static(program, repositories, root)
            codes = {issue.code for issue in issues}

            self.assertIn("EXECUTABLE_CONTRACT_SECTION", codes)

    def test_executable_contract_rejects_empty_sections_and_checkbox_text(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "ready"
            program["activeCardId"] = None
            empty_contract = """# CARD-001

## Resultado esperado
## Impact Analysis
### Contratos afetados
### Riscos e mitigação
### Validação mínima
## Dependências
## Escopo
## Fora de escopo
## Critérios de aceite
## Rollback
## Definition of Ready
- [x]
"""
            (root / program["cards"][0]["cardFile"]).write_text(
                empty_contract, encoding="utf-8"
            )

            issues = program_control.validate_static(program, repositories, root)
            codes = {issue.code for issue in issues}

            self.assertIn("EXECUTABLE_CONTRACT_EMPTY", codes)
            self.assertIn("EXECUTABLE_DOR_INVALID", codes)

    def test_dor_rejects_malformed_and_alternate_pending_tasks(self) -> None:
        invalid_variants = (
            "- [x] contrato completo.\n- [ ]",
            "- [x] contrato completo.\n* [ ] pendente.",
        )
        for dor in invalid_variants:
            with self.subTest(dor=dor):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    program, repositories = valid_fixture(root)
                    program["cards"][0]["status"] = "ready"
                    program["activeCardId"] = None
                    card_path = root / program["cards"][0]["cardFile"]
                    card_path.write_text(
                        READY_CARD.replace("- [x] contrato completo.", dor),
                        encoding="utf-8",
                    )

                    issues = program_control.validate_static(
                        program, repositories, root
                    )
                    codes = {issue.code for issue in issues}

                    expected = (
                        "EXECUTABLE_DOR_INVALID"
                        if dor.endswith("- [ ]")
                        else "EXECUTABLE_DOR_PENDING"
                    )
                    self.assertIn(expected, codes)

    def test_indented_code_task_does_not_satisfy_dor(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "ready"
            program["activeCardId"] = None
            card_path = root / program["cards"][0]["cardFile"]
            card_path.write_text(
                READY_CARD.replace(
                    "- [x] contrato completo.",
                    "    - [x] exemplo dentro de bloco de código",
                ),
                encoding="utf-8",
            )

            issues = program_control.validate_static(program, repositories, root)

            self.assertIn(
                "EXECUTABLE_DOR_CHECKLIST", {issue.code for issue in issues}
            )

    def test_duplicate_dor_is_rejected_even_if_first_is_complete(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "ready"
            program["activeCardId"] = None
            card_path = root / program["cards"][0]["cardFile"]
            card_path.write_text(
                READY_CARD
                + "\n## Definition of Ready\n\n- [ ] pendente oculto.\n",
                encoding="utf-8",
            )

            issues = program_control.validate_static(program, repositories, root)

            self.assertIn(
                "EXECUTABLE_CONTRACT_DUPLICATE",
                {issue.code for issue in issues},
            )

    def test_idle_program_without_ready_card_is_valid(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "waiting_external"
            program["cards"][0]["blockers"] = ["Prova externa pendente."]
            program["activeCardId"] = None
            program["nextCardId"] = None

            issues = program_control.validate_static(program, repositories, root)

            self.assertEqual(
                [], [issue for issue in issues if issue.level == "ERROR"]
            )

    def test_idle_program_with_ready_card_requires_next_selection(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "ready"
            program["activeCardId"] = None
            program["nextCardId"] = None

            issues = program_control.validate_static(program, repositories, root)

            self.assertIn("NEXT_REQUIRED", {issue.code for issue in issues})

    def test_active_program_requires_next_to_point_to_active_card(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["nextCardId"] = None

            issues = program_control.validate_static(program, repositories, root)

            self.assertIn("NEXT_ACTIVE_MISMATCH", {issue.code for issue in issues})

    def test_card_pointers_are_required(self) -> None:
        for field in ("activeCardId", "nextCardId"):
            with self.subTest(field=field):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    program, repositories = valid_fixture(root)
                    del program[field]

                    issues = program_control.validate_static(
                        program, repositories, root
                    )

                    self.assertIn(
                        "PROGRAM_POINTER_REQUIRED", {issue.code for issue in issues}
                    )

    def test_card_pointers_reject_non_string_values(self) -> None:
        for field in ("activeCardId", "nextCardId"):
            with self.subTest(field=field):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    program, repositories = valid_fixture(root)
                    program[field] = ["CARD-001"]

                    issues = program_control.validate_static(
                        program, repositories, root
                    )

                    self.assertIn(
                        "PROGRAM_POINTER_TYPE", {issue.code for issue in issues}
                    )

    def test_next_command_reports_legitimate_idle_state(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "waiting_external"
            program["cards"][0]["blockers"] = ["Prova externa pendente."]
            program["activeCardId"] = None
            program["nextCardId"] = None
            write_control_fixture(root, program, repositories)
            output = io.StringIO()

            with mock.patch.object(
                program_control, "ADR005_SUPERSEDED_BINDINGS", frozenset()
            ), redirect_stdout(output):
                exit_code = program_control.command_next(root)

            self.assertEqual(0, exit_code)
            self.assertIn("Nenhum card executável selecionado", output.getvalue())

    def test_next_command_rejects_invalid_card_pointer_schema(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["activeCardId"] = ["CARD-001"]
            write_control_fixture(root, program, repositories)
            output = io.StringIO()

            with redirect_stdout(output):
                exit_code = program_control.command_next(root)

            self.assertEqual(1, exit_code)
            self.assertIn("PROGRAM_POINTER_TYPE", output.getvalue())

    def test_next_command_rejects_missing_card_pointer(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            del program["nextCardId"]
            write_control_fixture(root, program, repositories)
            output = io.StringIO()

            with redirect_stdout(output):
                exit_code = program_control.command_next(root)

            self.assertEqual(1, exit_code)
            self.assertIn("PROGRAM_POINTER_REQUIRED", output.getvalue())

    def test_next_command_reports_missing_repositories_document(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, _ = valid_fixture(root)
            program_path = root / "program" / "program.json"
            program_path.write_text(json.dumps(program), encoding="utf-8")
            output = io.StringIO()

            with redirect_stdout(output):
                exit_code = program_control.command_next(root)

            self.assertEqual(1, exit_code)
            self.assertIn("[ERROR] LOAD: arquivo ausente:", output.getvalue())
            self.assertIn("repositories.json", output.getvalue())

    def test_next_command_rejects_static_error_before_announcing_card(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "ready"
            program["cards"][0]["blockers"] = ["Ainda bloqueado."]
            program["activeCardId"] = None
            write_control_fixture(root, program, repositories)
            output = io.StringIO()

            with redirect_stdout(output):
                exit_code = program_control.command_next(root)

            self.assertEqual(1, exit_code)
            self.assertIn("EXECUTABLE_WITH_BLOCKERS", output.getvalue())
            self.assertNotIn("CARD-001 [ready]", output.getvalue())

    def test_next_command_announces_statically_valid_ready_card(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "ready"
            program["activeCardId"] = None
            write_control_fixture(root, program, repositories)
            output = io.StringIO()

            with redirect_stdout(output):
                exit_code = program_control.command_next(root)

            self.assertEqual(0, exit_code)
            self.assertIn("CARD-001 [ready]", output.getvalue())

    def test_next_command_rejects_empty_executable_contract(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "ready"
            program["activeCardId"] = None
            (root / program["cards"][0]["cardFile"]).write_text(
                """# CARD-001

## Resultado esperado
## Impact Analysis
### Contratos afetados
### Riscos e mitigação
### Validação mínima
## Dependências
## Escopo
## Fora de escopo
## Critérios de aceite
## Rollback
## Definition of Ready
- [x]
""",
                encoding="utf-8",
            )
            write_control_fixture(root, program, repositories)
            output = io.StringIO()

            with redirect_stdout(output):
                exit_code = program_control.command_next(root)

            self.assertEqual(1, exit_code)
            self.assertIn("EXECUTABLE_CONTRACT_EMPTY", output.getvalue())
            self.assertNotIn("CARD-001 [ready]", output.getvalue())

    def test_next_command_rejects_duplicate_dor(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            program["cards"][0]["status"] = "ready"
            program["activeCardId"] = None
            card_path = root / program["cards"][0]["cardFile"]
            card_path.write_text(
                READY_CARD
                + "\n## Definition of Ready\n\n- [ ] pendente oculto.\n",
                encoding="utf-8",
            )
            write_control_fixture(root, program, repositories)
            output = io.StringIO()

            with redirect_stdout(output):
                exit_code = program_control.command_next(root)

            self.assertEqual(1, exit_code)
            self.assertIn("EXECUTABLE_CONTRACT_DUPLICATE", output.getvalue())
            self.assertNotIn("CARD-001 [ready]", output.getvalue())

    def test_path_rules_cover_directory_and_exact_file(self) -> None:
        self.assertTrue(program_control.path_matches("program/cards/A.md", "program/"))
        self.assertTrue(program_control.path_matches("AGENTS.md", "AGENTS.md"))
        self.assertFalse(program_control.path_matches("docs/new.md", "program/"))

    def test_idle_control_paths_cover_canonical_blocked_bookkeeping(self) -> None:
        expected_paths = (
            ".dev-harness/status.json",
            "program/RISK_REGISTER.md",
            "program/decisions/ADR.md",
        )
        for path in expected_paths:
            with self.subTest(path=path):
                self.assertTrue(
                    any(
                        program_control.path_matches(path, rule)
                        for rule in program_control.IDLE_CONTROL_PATHS
                    )
                )

    def test_dirty_status_parser_handles_rename(self) -> None:
        output = " M AGENTS.md\n?? program/README.md\nR  old.md -> new.md"
        self.assertEqual(
            ["AGENTS.md", "program/README.md", "new.md"],
            program_control.parse_dirty_paths(output),
        )

    def test_dashboard_overwrite_is_detected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            dashboard = root / ".dev-harness" / "dashboard.md"
            dashboard.parent.mkdir(parents=True)
            dashboard.write_text("# Generic dashboard\n", encoding="utf-8")
            program = {
                "activeCardId": "CARD-001",
                "productDecision": {"pilot": "HOLD", "production": "HOLD"},
            }
            issues = program_control.validate_dashboard(program, root)
            self.assertEqual(["DASHBOARD_STALE"], [issue.code for issue in issues])

    def test_superseded_adr005_acceptance_state_is_consistent(self) -> None:
        program = program_control.load_json(program_control.PROGRAM_PATH)
        card = next(
            item
            for item in program["cards"]
            if item["id"] == program_control.ADR005_CARD_ID
        )
        frozen_adr = (
            program_control.ROOT
            / "program"
            / "decisions"
            / "ADR-005-trip-scoped-location-access.md"
        ).read_bytes()

        self.assertNotEqual(
            program_control.ADR005_DIGEST, hashlib.sha256(frozen_adr).hexdigest()
        )
        self.assertEqual(program_control.ADR005_REFREEZE_CARD_STATUS, card["status"])
        self.assertIn(
            f"coordination:{program_control.ADR005_REVISION}", card["commits"]
        )
        self.assertEqual(
            [],
            program_control.validate_adr005_acceptance(program),
        )

    def test_adr005_superseded_state_rejects_tampering(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            record = (
                program_control.ROOT
                / "program"
                / "decisions"
                / "ADR-005-acceptance.md"
            ).read_text(encoding="utf-8")
            program = write_adr005_fixture(
                root,
                card_status=program_control.ADR005_REFREEZE_CARD_STATUS,
                record=record,
            )
            self.assertEqual(
                [], program_control.validate_adr005_acceptance(program, root)
            )

            record_path = root / "program" / "decisions" / "ADR-005-acceptance.md"
            adr_path = (
                root
                / "program"
                / "decisions"
                / "ADR-005-trip-scoped-location-access.md"
            )
            original_adr = adr_path.read_text(encoding="utf-8")

            tampered = record.replace("`INVALIDATED`", "`PENDING`", 1)
            record_path.write_text(tampered, encoding="utf-8")
            codes = {
                issue.code
                for issue in program_control.validate_adr005_acceptance(program, root)
            }
            self.assertIn("ADR005_SUPERSEDED_METADATA", codes)

            record_path.write_text(record, encoding="utf-8")
            adr = original_adr.replace(
                "REFREEZE_REQUIRED", "REFREEZE_REMOVED"
            )
            adr_path.write_text(adr, encoding="utf-8")
            codes = {
                issue.code
                for issue in program_control.validate_adr005_acceptance(program, root)
            }
            self.assertIn("ADR005_REFREEZE_REQUIRED", codes)

            adr = original_adr.replace(
                program_control.ADR005_REFREEZE_ADR_STATUS,
                "ACCEPTED / READY_FOR_IMPLEMENTATION",
                1,
            )
            adr_path.write_text(adr, encoding="utf-8")
            codes = {
                issue.code
                for issue in program_control.validate_adr005_acceptance(program, root)
            }
            self.assertIn("ADR005_REFREEZE_STATUS", codes)

            adr_path.write_text(
                original_adr + "\nReserva: `V999__location.sql`.\n",
                encoding="utf-8",
            )
            codes = {
                issue.code
                for issue in program_control.validate_adr005_acceptance(program, root)
            }
            self.assertIn("ADR005_PREMATURE_FLYWAY", codes)

            adr_path.write_text(original_adr, encoding="utf-8")
            program["cards"][0]["blockers"] = ["bloqueio genérico"]
            codes = {
                issue.code
                for issue in program_control.validate_adr005_acceptance(program, root)
            }
            self.assertIn("ADR005_ACCEPTANCE_STATE", codes)

    def test_superseded_adr005_binding_cannot_be_reaccepted(self) -> None:
        scenarios = (
            ("waiting_human", "WAITING_HUMAN"),
            ("ready", "ACCEPTED"),
        )
        for card_status, record_status in scenarios:
            with self.subTest(card_status=card_status):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    program = write_adr005_fixture(
                        root,
                        card_status=card_status,
                        record=adr005_acceptance_record(status=record_status),
                    )
                    codes = {
                        issue.code
                        for issue in program_control.validate_adr005_acceptance(
                            program, root
                        )
                    }
                    self.assertIn("ADR005_SUPERSEDED_REVISION", codes)

    def test_superseded_adr005_binding_survives_future_current_revision(self) -> None:
        historical_binding = (
            program_control.ADR005_HISTORICAL_SUPERSEDED_REVISION,
            program_control.ADR005_HISTORICAL_SUPERSEDED_DIGEST,
        )
        self.assertIn(historical_binding, program_control.ADR005_SUPERSEDED_BINDINGS)

        with mock.patch.multiple(
            program_control,
            ADR005_REVISION="future-immutable-revision",
            ADR005_DIGEST="future-immutable-digest",
        ):
            self.assertIn(
                historical_binding, program_control.ADR005_SUPERSEDED_BINDINGS
            )
            self.assertNotIn(
                (
                    program_control.ADR005_REVISION,
                    program_control.ADR005_DIGEST,
                ),
                program_control.ADR005_SUPERSEDED_BINDINGS,
            )

    def test_non_superseded_adr005_acceptance_states_are_validated(self) -> None:
        scenarios = (
            ("waiting_human", "WAITING_HUMAN"),
            ("ready", "ACCEPTED"),
            ("in_progress", "ACCEPTED"),
            ("validating", "ACCEPTED"),
            ("done", "ACCEPTED"),
        )
        for card_status, record_status in scenarios:
            with self.subTest(card_status=card_status):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    program = write_adr005_fixture(
                        root,
                        card_status=card_status,
                        record=adr005_acceptance_record(status=record_status),
                    )
                    self.assertEqual(
                        [], validate_non_superseded_adr005(program, root)
                    )

    def test_adr005_pending_state_preserves_declarations_for_human_review(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            record = adr005_acceptance_record(status="WAITING_HUMAN").replace(
                "- retenção ativa é 72 horas;\n", ""
            )
            program = write_adr005_fixture(
                root, card_status="waiting_human", record=record
            )
            codes = {
                issue.code
                for issue in validate_non_superseded_adr005(program, root)
            }
            self.assertIn("ADR005_ACCEPTANCE_DECLARATIONS", codes)

    def test_adr005_accepted_state_requires_every_metadata_field(self) -> None:
        for label in program_control.ADR005_ACCEPTANCE_FIELDS:
            with self.subTest(label=label):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    program = write_adr005_fixture(
                        root,
                        card_status="ready",
                        record=adr005_acceptance_record(
                            status="ACCEPTED", omitted={label}
                        ),
                    )
                    codes = {
                        issue.code
                        for issue in validate_non_superseded_adr005(program, root)
                    }
                    self.assertIn("ADR005_ACCEPTANCE_METADATA", codes)

    def test_adr005_accepted_state_rejects_placeholders_and_duplicates(self) -> None:
        for label in program_control.ADR005_ACCEPTANCE_FIELDS:
            with self.subTest(label=label):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    program = write_adr005_fixture(
                        root,
                        card_status="ready",
                        record=adr005_acceptance_record(
                            status="ACCEPTED", overrides={label: "<preencher>"}
                        ),
                    )
                    codes = {
                        issue.code
                        for issue in validate_non_superseded_adr005(program, root)
                    }
                    self.assertIn("ADR005_ACCEPTANCE_METADATA", codes)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            record = adr005_acceptance_record(status="ACCEPTED")
            record += "\n- aprovador de Produto: `Outro nome`;\n"
            program = write_adr005_fixture(
                root, card_status="ready", record=record
            )
            codes = {
                issue.code
                for issue in validate_non_superseded_adr005(program, root)
            }
            self.assertIn("ADR005_ACCEPTANCE_METADATA", codes)

    def test_adr005_accepted_state_validates_phrase_timestamp_and_declarations(
        self,
    ) -> None:
        scenarios = (
            (
                {"frase recebida": "Aceito ADR-005"},
                None,
                "ADR005_ACCEPTANCE_PHRASE",
            ),
            (
                {"timestamp do registro": "18/07/2026 13:30"},
                None,
                "ADR005_ACCEPTANCE_TIMESTAMP",
            ),
            (
                {"timestamp do registro": "2026-07-18T13:00:00Z"},
                None,
                "ADR005_ACCEPTANCE_TIMESTAMP",
            ),
            (
                {"timestamp do registro": "2026-13-40T25:61:61Z"},
                None,
                "ADR005_ACCEPTANCE_TIMESTAMP",
            ),
            (
                {"declarações obrigatórias": "SIM"},
                None,
                "ADR005_ACCEPTANCE_DECLARATIONS",
            ),
            (
                {},
                "- retenção ativa é 72 horas;\n",
                "ADR005_ACCEPTANCE_DECLARATIONS",
            ),
        )
        for overrides, removed_text, expected_code in scenarios:
            with self.subTest(expected_code=expected_code, overrides=overrides):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    record = adr005_acceptance_record(
                        status="ACCEPTED", overrides=overrides
                    )
                    if removed_text:
                        record = record.replace(removed_text, "")
                    program = write_adr005_fixture(
                        root, card_status="ready", record=record
                    )
                    codes = {
                        issue.code
                        for issue in validate_non_superseded_adr005(program, root)
                    }
                    self.assertIn(expected_code, codes)

    def test_adr005_single_approver_must_declare_both_authorities(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            record = adr005_acceptance_record(
                status="ACCEPTED",
                overrides={"aprovador de Privacidade": "Ada Produto"},
            )
            record = record.replace("Bia Privacidade", "Ada Produto")
            program = write_adr005_fixture(
                root, card_status="ready", record=record
            )
            codes = {
                issue.code
                for issue in validate_non_superseded_adr005(program, root)
            }
            self.assertIn("ADR005_ACCEPTANCE_PHRASE", codes)

            record = record.replace(
                "Declaro ciência",
                "Declaro que acumula autoridade de Produto e Privacidade. "
                "Declaro ciência",
            )
            (root / "program" / "decisions" / "ADR-005-acceptance.md").write_text(
                record, encoding="utf-8"
            )
            self.assertEqual(
                [], validate_non_superseded_adr005(program, root)
            )

    def test_next_rejects_incomplete_accepted_adr005_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            card = program["cards"][0]
            card["id"] = program_control.ADR005_CARD_ID
            card["cardFile"] = (
                f"program/cards/{program_control.ADR005_CARD_ID}.md"
            )
            card["commits"] = [
                f"coordination:{program_control.ADR005_REVISION}"
            ]
            (root / card["cardFile"]).write_text(
                READY_CARD.replace("CARD-001", program_control.ADR005_CARD_ID),
                encoding="utf-8",
            )
            program["activeCardId"] = None
            program["nextCardId"] = program_control.ADR005_CARD_ID
            write_adr005_fixture(
                root,
                card_status="ready",
                record=adr005_acceptance_record(
                    status="ACCEPTED", omitted={"aprovador de Produto"}
                ),
            )
            write_control_fixture(root, program, repositories)
            output = io.StringIO()

            with redirect_stdout(output):
                exit_code = program_control.command_next(root)

            self.assertEqual(1, exit_code)
            self.assertIn("ADR005_ACCEPTANCE_METADATA", output.getvalue())
            self.assertNotIn(
                f"{program_control.ADR005_CARD_ID} [ready]", output.getvalue()
            )

    def test_next_rejects_accepted_superseded_adr005_binding(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            program, repositories = valid_fixture(root)
            card = program["cards"][0]
            card["id"] = program_control.ADR005_CARD_ID
            card["cardFile"] = (
                f"program/cards/{program_control.ADR005_CARD_ID}.md"
            )
            card["commits"] = [
                f"coordination:{program_control.ADR005_REVISION}"
            ]
            (root / card["cardFile"]).write_text(
                READY_CARD.replace("CARD-001", program_control.ADR005_CARD_ID),
                encoding="utf-8",
            )
            program["activeCardId"] = None
            program["nextCardId"] = program_control.ADR005_CARD_ID
            write_adr005_fixture(
                root,
                card_status="ready",
                record=adr005_acceptance_record(status="ACCEPTED"),
            )
            write_control_fixture(root, program, repositories)
            output = io.StringIO()

            with redirect_stdout(output):
                exit_code = program_control.command_next(root)

            self.assertEqual(1, exit_code)
            self.assertIn("ADR005_SUPERSEDED_REVISION", output.getvalue())
            self.assertNotIn(
                f"{program_control.ADR005_CARD_ID} [ready]", output.getvalue()
            )

    def test_card_template_contains_every_required_contract_section(self) -> None:
        template = (
            program_control.ROOT / "program" / "templates" / "CARD.md"
        ).read_text(encoding="utf-8")
        missing = [
            heading
            for heading in program_control.READY_CARD_SECTIONS
            if program_control.markdown_section(template, heading) is None
        ]

        self.assertEqual([], missing)


if __name__ == "__main__":
    unittest.main()
