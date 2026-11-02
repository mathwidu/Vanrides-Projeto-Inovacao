#!/usr/bin/env python3
"""Gate offline stdlib/Node. Não inicia serviços, usa credenciais nem envia mensagens."""
from pathlib import Path
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / 'src/whatsapp'
ENV = {**os.environ, 'PYTHONDONTWRITEBYTECODE': '1'}

def run(*args, cwd=ROOT):
    subprocess.run(args, cwd=cwd, env=ENV, check=True)

def main():
    tests = sorted(p for folder in ('agent', 'worker', 'transport', 'links', 'workflows', 'scripts', 'lab/live-ingress')
                   for p in (MODULE / folder).glob('*.test.mjs'))
    if not tests:
        raise SystemExit('Nenhum teste Node encontrado')
    run('node', '--test', '--test-concurrency=2', *(str(p.relative_to(ROOT)) for p in tests))
    run(sys.executable, 'src/whatsapp/scripts/070-n8n-smoke-test.py')
    run(sys.executable, '-m', 'unittest', 'discover', '-s', 'tests', '-v', cwd=MODULE / 'gps-spike')
    run(sys.executable, '-m', 'unittest', 'discover', '-s', '.', '-p', '*_test.py', cwd=MODULE / 'gps-spike/ios-ui-lab')
    for folder in ('lab/live-runtime', 'scripts/vps110', 'scripts/vps110/server'):
        run(sys.executable, '-m', 'unittest', 'discover', '-s', '.', '-p', '*_test.py', cwd=MODULE / folder)
    # Exact-v6 reconstruction needs the original Git history and private inputs.
    # Keep its unit assertions, but do not impersonate that historical package.
    run(sys.executable, '-m', 'unittest', 'discover', '-s', '.', '-p', '*_test.py', cwd=MODULE / 'scripts/vps110/physical')
    run(sys.executable, '-m', 'unittest', 'test_mac_prep.py', 'install_private_test.py', cwd=MODULE / 'scripts/vps110/mac')
    print('PASS: WhatsApp offline, contratos canônicos e receptor GPS sintético')

if __name__ == '__main__':
    main()
