#!/usr/bin/env python3
"""Confere o único JAR recebido da compilação desta execução da CI."""
import argparse
import hashlib
from pathlib import Path
import re


def verify(directory, expected):
    if not re.fullmatch('[a-f0-9]{64}', expected):
        raise ValueError('Checksum esperado inválido')
    jar = directory / 'backend.jar'
    if (directory.is_symlink() or not directory.is_dir()
            or set(directory.iterdir()) != {jar} or jar.is_symlink() or not jar.is_file()):
        raise ValueError('O artefato deve conter somente backend.jar regular')
    digest = hashlib.sha256()
    with jar.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    if digest.hexdigest() != expected:
        raise ValueError('JAR recebido difere do binário empacotado nesta execução')
    return jar


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--sha256', required=True)
    args = parser.parse_args()
    verify(args.directory, args.sha256)
    print('JAR candidato conferido; isso não equivale a testes aprovados.')
