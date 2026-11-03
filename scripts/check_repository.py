#!/usr/bin/env python3
"""Confere entrada, backlog e links dos guias atuais, sem rede ou credenciais."""
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def main():
    subprocess.run([sys.executable, str(ROOT / 'scripts/backlog.py'), '--check'], check=True)
    documents = [ROOT/'README.md', ROOT/'CONTRIBUTING.md', ROOT/'src/README.md']
    documents += sorted((ROOT/'docs').glob('0*.md'))
    documents += sorted((ROOT/'docs/desenvolvimento').glob('*.md'))
    broken = []
    checked = 0
    for file in documents:
        for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)', file.read_text(encoding="utf-8")):
            if target.startswith(('https:', 'http:', 'mailto:', '#')): continue
            local = target.split('#')[0]
            if not local: continue
            checked += 1
            resolved = (file.parent/local).resolve()
            if not resolved.is_relative_to(ROOT) or not resolved.exists():
                broken.append(f'{file.relative_to(ROOT)} -> {target}')
    if broken:
        raise SystemExit('Links locais inválidos:\n'+'\n'.join(broken))
    for name in ('STACK.docx', 'BACKLOG.xlsx'):
        with zipfile.ZipFile(ROOT/name) as z:
            assert z.testzip() is None, name
    with zipfile.ZipFile(ROOT/'BACKLOG.xlsx') as z:
        ns={'s':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
        workbook=ET.fromstring(z.read('xl/workbook.xml'))
        assert [e.attrib['name'] for e in workbook.findall('s:sheets/s:sheet', ns)] == ['BACKLOG','INSTRUÇÕES']
    data=json.loads((ROOT/'docs/backlog/tarefas.json').read_text(encoding="utf-8"))
    assert len(data['tasks']) == 65 and not {'T76','T77'} & {r[0] for r in data['tasks']}
    with zipfile.ZipFile(ROOT/'BACKLOG.xlsx') as z:
        strings = []
        if 'xl/sharedStrings.xml' in z.namelist():
            strings = [''.join(e.itertext()) for e in ET.fromstring(z.read('xl/sharedStrings.xml'))]
        sheet = ET.fromstring(z.read('xl/worksheets/sheet1.xml'))
        cells = {}
        for cell in sheet.findall('.//s:sheetData/s:row/s:c', ns):
            value = cell.find('s:v', ns)
            text = value.text if value is not None else ''
            if cell.get('t') == 's': text = strings[int(text)]
            if cell.get('t') == 'inlineStr': text = ''.join(cell.find('s:is', ns).itertext())
            cells[cell.get('r')] = text
        tasks = {task[0]: task for task in data['tasks']}
        rows = {cells.get(f'A{row}'): row for row in range(5, 70)}
        assert set(rows) == set(tasks), 'IDs da planilha divergem do backlog'
        for task_id, row in rows.items():
            task = tasks[task_id]
            expected = [task[0], f'Onda {task[1]}', *task[2:6],
                        task[6] or 'A definir pela equipe',
                        f'#{task[7]}' if isinstance(task[7], int) else '--']
            assert [cells.get(f'{column}{row}') for column in 'ABCDEFGH'] == expected, f'{task_id}: planilha diverge dos dados'
    references = 0
    for name in ('acompanhamento.json', 'criterios.json'):
        records=json.loads((ROOT/'docs/backlog'/name).read_text(encoding='utf-8'))
        for task in records['tasks']:
            for field in ('sourcePaths','documentationPaths','evidencePaths','planningPaths','validationEvidencePaths'):
                for path in task.get(field, []):
                    resolved=(ROOT/path).resolve()
                    assert resolved.is_relative_to(ROOT) and resolved.exists(), f"{task['taskId']}: {path}"
                    references += 1
    required=['src/backend/pom.xml','src/aplicativo-motorista/pubspec.yaml',
              'src/whatsapp/worker/dispatch.mjs','program/program.json']
    for path in required: assert (ROOT/path).is_file(), path
    print(f'Repositório: {checked} links locais, {references} referências de tarefas, 520 células do backlog e pacotes Office válidos')


if __name__ == '__main__':
    main()
