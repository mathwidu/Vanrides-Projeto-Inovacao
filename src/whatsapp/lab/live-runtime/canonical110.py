"""Load the sealed, unchanged 110 code; never initialize its live Runtime."""
import importlib.util
import json
from pathlib import Path
import sys

import plan


def load(name, relative):
    pins = json.loads((plan.HERE / 'canonical110-pins.json').read_bytes())
    path = plan.resolve_source(relative)
    plan.require(relative in pins and path.is_file() and not path.is_symlink()
                 and plan.hash_file(path) == pins[relative], 'CANONICAL110_SOURCE_CHANGED')
    spec = importlib.util.spec_from_file_location(name, path)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


def stage_receiver():
    return load('wa120_original_stage110', 'integrations/whatsapp/scripts/vps110/physical/stage_physical_remote.py')


def guards():
    previous = sys.modules.get('protocol')
    load('wa120_original_protocol110', 'integrations/whatsapp/scripts/vps110/server/protocol.py')
    sys.modules['protocol'] = plan._protocol
    try:
        return load('wa120_original_guards110', 'integrations/whatsapp/scripts/vps110/server/control.py')
    finally:
        if previous is None:
            del sys.modules['protocol']
        else:
            sys.modules['protocol'] = previous
