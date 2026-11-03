#!/usr/bin/env python3
"""Verify one exact Actions artifact through GETs; prepare inputs, never authorize a run."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import shutil
import subprocess
import sys
import zipfile

sys.dont_write_bytecode = True
import monorepo_receipt as M
import export as exporter
from plan import Blocked, CARD, require, receipt_bytes, hash_file

FILES = {'backend.jar', 'gate.json', 'smoke.json', 'candidate.json'}
JOB_NAME = 'Testar e preservar o mesmo binário'


def github_get(route):
    require(route.startswith('repos/' + M.REPOSITORY + '/actions/') and '..' not in route, 'COLLECT_API_ROUTE_INVALID')
    result = subprocess.run(['gh', 'api', '--hostname', 'github.com', '--method', 'GET', route],
                            capture_output=True, timeout=30)
    require(result.returncode == 0 and len(result.stdout) <= M.MAX_JSON_BYTES, 'COLLECT_GITHUB_READ_FAILED')
    return M.object_json(result.stdout)


def inspect_artifact(archive, expected_digest):
    archive = Path(archive)
    require(M.sha(expected_digest) and archive.is_absolute() and archive.parent.resolve() == archive.parent
            and archive.is_file() and not archive.is_symlink() and 0 < archive.stat().st_size <= 105000000
            and hash_file(archive) == expected_digest, 'COLLECT_ARTIFACT_DIGEST_CHANGED')
    originals = {}
    with zipfile.ZipFile(archive) as bundle:
        rows = bundle.infolist()
        require(len(rows) == len(FILES) and {row.filename for row in rows} == FILES, 'COLLECT_ARTIFACT_FILES_INVALID')
        for row in rows:
            mode = row.external_attr >> 16
            require(not row.is_dir() and not row.flag_bits & 1
                    and stat.S_IFMT(mode) in (0, stat.S_IFREG)
                    and 0 < row.file_size <= (100000000 if row.filename == 'backend.jar' else M.MAX_JSON_BYTES), 'COLLECT_ARTIFACT_FILES_INVALID')
            if row.filename != 'backend.jar':
                originals[row.filename] = bundle.read(row)
        digest = hashlib.sha256(); count = 0
        with bundle.open('backend.jar') as stream:
            for chunk in iter(lambda: stream.read(262144), b''):
                digest.update(chunk); count += len(chunk)
        jar = {'sha256': digest.hexdigest(), 'bytes': count}
    candidate = M.object_json(originals['candidate.json'])
    M.candidate(candidate, M.object_json(originals['gate.json']), M.object_json(originals['smoke.json']))
    require(candidate['gateSha256'] == M.digest(originals['gate.json'])
            and candidate['smokeSha256'] == M.digest(originals['smoke.json']) and candidate['jar'] == jar, 'COLLECT_ARTIFACT_BINDING_CHANGED')
    require(hash_file(archive) == expected_digest, 'COLLECT_ARTIFACT_DIGEST_CHANGED')
    return candidate, originals


def collect(archive, artifact_sha256, run_id, artifact_id, expected_sha, expected_tree, images, output, api=github_get):
    require(M.positive(run_id) and M.positive(artifact_id) and M.sha(expected_sha, 40)
            and M.sha(expected_tree, 40), 'COLLECT_EXPECTATION_INVALID')
    M.images(images)
    candidate, originals = inspect_artifact(archive, artifact_sha256)
    require(candidate['source']['sha'] == expected_sha and candidate['source']['tree'] == expected_tree
            and candidate['ci']['runId'] == run_id, 'COLLECT_EXPECTED_SOURCE_CHANGED')
    prefix = 'repos/' + M.REPOSITORY + '/actions/'
    run = api(prefix + 'runs/' + str(run_id))
    require(run.get('id') == run_id and run.get('head_sha') == expected_sha
            and run.get('path') == M.WORKFLOW and run.get('event') == 'workflow_dispatch'
            and run.get('status') == 'completed' and run.get('conclusion') == 'success'
            and run.get('run_attempt') == candidate['ci']['runAttempt']
            and isinstance(run.get('head_branch'), str)
            and candidate['ci']['workflowRef'] == M.REPOSITORY + '/' + M.WORKFLOW + '@refs/heads/' + run['head_branch']
            and isinstance(run.get('repository'), dict) and run['repository'].get('full_name') == M.REPOSITORY,
            'COLLECT_RUN_NOT_EXACT')
    attempt = run['run_attempt']
    jobs = api(prefix + f'runs/{run_id}/attempts/{attempt}/jobs?per_page=100')
    require(jobs.get('total_count') == 1 and isinstance(jobs.get('jobs'), list) and len(jobs['jobs']) == 1, 'COLLECT_JOBS_NOT_EXACT')
    job = jobs['jobs'][0]
    require(job.get('name') == JOB_NAME and M.positive(job.get('id')) and job.get('run_id') == run_id
            and job.get('head_sha') == expected_sha and job.get('status') == 'completed'
            and job.get('conclusion') == 'success', 'COLLECT_JOBS_NOT_EXACT')
    artifact = api(prefix + 'artifacts/' + str(artifact_id))
    require(artifact.get('id') == artifact_id and artifact.get('expired') is False
            and artifact.get('digest') == 'sha256:' + artifact_sha256
            and artifact.get('name') == f'whatsapp-lab-{expected_sha}-{run_id}-{attempt}'
            and isinstance(artifact.get('workflow_run'), dict) and artifact['workflow_run'].get('id') == run_id
            and artifact['workflow_run'].get('head_sha') == expected_sha, 'COLLECT_ARTIFACT_PROVENANCE_CHANGED')
    provenance = {'repository': M.REPOSITORY, 'runId': run_id, 'runAttempt': attempt, 'headSha': expected_sha,
                  'workflowPath': M.WORKFLOW, 'event': 'workflow_dispatch', 'status': 'completed', 'conclusion': 'success',
                  'job': 'candidate', 'jobId': job['id'], 'jobConclusion': 'success', 'artifactId': artifact_id,
                  'artifactSha256': artifact_sha256}
    M.provenance(provenance, candidate['ci'])
    envelope = {'schemaVersion': 2, 'evidenceProfile': M.PROFILE, 'status': 'VERIFIED_FOR_PREFLIGHT',
                'candidateOriginal': originals['candidate.json'].decode('utf-8'), 'gateOriginal': originals['gate.json'].decode('utf-8'),
                'smokeOriginal': originals['smoke.json'].decode('utf-8'), 'provenance': provenance}
    raw = receipt_bytes(envelope)
    require(len(raw) <= M.MAX_JSON_BYTES, 'COLLECT_ENVELOPE_TOO_LARGE')
    contract = {'schemaVersion': 2, 'evidenceProfile': M.PROFILE, 'cardId': CARD, 'state': 'PINNED_FOR_PREFLIGHT',
                'source': candidate['source'], 'backend': {'sourceSha': expected_sha, 'jarSha256': candidate['jar']['sha256'],
                'jarBytes': candidate['jar']['bytes'], 'verifiedCiReceiptSha256': M.digest(raw)},
                'verification': {'runId': run_id, 'runAttempt': attempt, 'artifactId': artifact_id, 'artifactSha256': artifact_sha256,
                'candidateSha256': M.digest(originals['candidate.json']), 'gateSha256': M.digest(originals['gate.json']),
                'smokeSha256': M.digest(originals['smoke.json'])}, 'images': images, 'policy': dict(M.POLICY)}
    M.ready_pins(contract)
    output = exporter.output_parent(Path(output)); output.mkdir(mode=0o700)
    owned, identity = {}, output.stat()
    try:
        owned['verified-ci-receipt.json'] = exporter.write_new(output / 'verified-ci-receipt.json', raw)
        owned['contract.json'] = exporter.write_new(output / 'contract.json', receipt_bytes(contract))
        target = output / 'backend.jar'
        fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        owned['backend.jar'] = os.fstat(fd)
        with os.fdopen(fd, 'wb') as stream, zipfile.ZipFile(archive) as bundle, bundle.open('backend.jar') as original:
            shutil.copyfileobj(original, stream, 262144); stream.flush(); os.fsync(stream.fileno())
        require(hash_file(archive) == artifact_sha256, 'COLLECT_ARTIFACT_DIGEST_CHANGED')
        M.validate_envelope(contract, raw, target)
        return contract
    except BaseException:
        for name, info in owned.items():
            path = output / name
            if path.exists() and not path.is_symlink() and (path.stat().st_dev, path.stat().st_ino) == (info.st_dev, info.st_ino):
                path.unlink()
        if (output.stat().st_dev, output.stat().st_ino) == (identity.st_dev, identity.st_ino):
            try:
                output.rmdir()
            except OSError:
                pass
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('artifact-zip', 'images-file', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    for name in ('run-id', 'artifact-id'):
        parser.add_argument('--' + name, type=int, required=True)
    for name in ('expected-artifact-sha256', 'expected-source-sha', 'expected-source-tree'):
        parser.add_argument('--' + name, required=True)
    args = parser.parse_args(); os.umask(0o077)
    try:
        value = collect(args.artifact_zip, args.expected_artifact_sha256, args.run_id, args.artifact_id,
                        args.expected_source_sha, args.expected_source_tree,
                        M.object_json(exporter.read_regular(args.images_file)), args.output)
        print(json.dumps({'status': 'VERIFIED_FOR_PREFLIGHT', 'releaseEligible': False, 'runtimeAuthorized': False,
                          'verifiedReceiptSha256': value['backend']['verifiedCiReceiptSha256']}))
        return 0
    except Exception as error:
        print(json.dumps({'status': 'BLOCKED', 'code': str(error) if isinstance(error, Blocked) else 'COLLECT_INPUT_INVALID'}))
        return 2


if __name__ == '__main__':
    sys.exit(main())
