#!/usr/bin/env python3
"""Build local da imagem com o JAR aprovado pelas jornadas; não publica nada."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def tested_jar(backend, receipt_path, source_sha, source_tree):
    if receipt_path.is_symlink() or not receipt_path.is_file():
        raise ValueError('Recibo das jornadas ausente ou inválido')
    receipt = json.loads(receipt_path.read_text())
    if (receipt.get('schemaVersion') != 1
            or receipt.get('profile') != 'VANRIDES_ACADEMIC_INTEGRATION_V1'
            or receipt.get('status') != 'PASS'
            or receipt.get('sourceSha') != source_sha
            or receipt.get('sourceTree') != source_tree
            or receipt.get('cleanup') != {'status': 'OWNED_RESOURCES_REMOVED', 'errors': []}):
        raise ValueError('Recibo não comprova jornadas aprovadas e limpas desta revisão')
    jars = list((backend / 'target').glob('*.jar'))
    if len(jars) != 1 or jars[0].is_symlink() or not jars[0].is_file():
        raise ValueError('Esperado exatamente um JAR regular em target')
    jar = jars[0]
    hasher = hashlib.sha256()
    with jar.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            hasher.update(block)
    digest = hasher.hexdigest()
    if receipt.get('jar') != {'sha256': digest, 'bytes': jar.stat().st_size}:
        raise ValueError('O JAR mudou depois das jornadas')
    return digest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--receipt', required=True, type=Path)
    args = parser.parse_args()
    def git(*parts):
        return subprocess.check_output(['git', '-C', str(ROOT), *parts], text=True).strip()
    if git('status', '--porcelain', '--untracked-files=all'):
        raise ValueError('A imagem exige checkout limpo')
    source_sha, source_tree = git('rev-parse', 'HEAD'), git('rev-parse', 'HEAD^{tree}')
    backend = ROOT / 'src/backend'
    digest = tested_jar(backend, args.receipt, source_sha, source_tree)
    subprocess.run(['docker', 'build', '--target', 'tested-jar',
                    '--build-arg', f'JAR_SHA256={digest}',
                    '--build-arg', f'SOURCE_SHA={source_sha}',
                    '-t', 'vanrides-backend:ci', str(backend)], check=True)
    print(f'Imagem local: JAR {digest} das jornadas em {source_sha}; sem publicação.')


if __name__ == '__main__':
    main()
