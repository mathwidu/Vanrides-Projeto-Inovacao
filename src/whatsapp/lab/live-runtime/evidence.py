"""Explicit evidence-profile dispatch. Historical receipt semantics stay intact."""
import monorepo_receipt as monorepo
import receipt as legacy
from plan import require


def is_monorepo(contract):
    if contract.get('schemaVersion') == 2:
        require(contract.get('evidenceProfile') == monorepo.PROFILE, 'EVIDENCE_PROFILE_INVALID')
        return True
    require(contract.get('schemaVersion') == 1 and 'evidenceProfile' not in contract, 'EVIDENCE_PROFILE_INVALID')
    return False


def ready_pins(contract):
    return (monorepo if is_monorepo(contract) else legacy).ready_pins(contract)


def validate_envelope(contract, raw, jar):
    return (monorepo if is_monorepo(contract) else legacy).validate_envelope(contract, raw, jar)


def package_profile(contract):
    return monorepo.PROFILE if is_monorepo(contract) else 'WA120_LIVE_DELIVERY_ONLY_V1'
