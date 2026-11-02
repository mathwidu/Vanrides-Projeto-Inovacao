"""Private physical stage receiver: no commands/network/package execution."""
import ast
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import pwd
import re
import socket
import stat
import sys
sys.dont_write_bytecode = True
import tarfile

FIXED_FILES = ('control.py', 'infra.py', 'protocol.py', 'layout.py', 'bridge.mjs', 'tools.mjs', 'physical_session.mjs', 'compose.yaml', 'contract.json', 'source-manifest.json', 'canonical-source-manifest.json', 'source/integrations/whatsapp/agent/attendance.mjs', 'source/integrations/whatsapp/agent/context-offer.mjs', 'source/integrations/whatsapp/agent/deterministic.mjs', 'source/integrations/whatsapp/agent/driver-setup.mjs', 'source/integrations/whatsapp/agent/model-policy.mjs', 'source/integrations/whatsapp/agent/passenger-setup.mjs', 'source/integrations/whatsapp/scripts/060-pairing-consumer.mjs', 'source/integrations/whatsapp/scripts/060-pairing-tls.mjs', 'source/integrations/whatsapp/scripts/070-worker-common.mjs', 'source/integrations/whatsapp/scripts/070-worker-lifecycle.mjs', 'source/integrations/whatsapp/scripts/070-worker-preflight.mjs', 'source/integrations/whatsapp/scripts/070-worker-runtime.mjs', 'source/integrations/whatsapp/scripts/070-worker-scenarios.mjs', 'source/integrations/whatsapp/scripts/070-worker-smoke.mjs', 'source/integrations/whatsapp/scripts/080-driver-evidence.mjs', 'source/integrations/whatsapp/scripts/090-passenger-conversation.mjs', 'source/integrations/whatsapp/scripts/100-attendance-scenarios.mjs', 'source/integrations/whatsapp/scripts/100-attendance-window.mjs', 'source/integrations/whatsapp/scripts/110-run-redmi-sidecar.mjs', 'source/integrations/whatsapp/scripts/110-run-redmi.mjs', 'source/integrations/whatsapp/scripts/110-run-scenarios.mjs', 'source/integrations/whatsapp/scripts/110-run-serial-cleanup.mjs', 'source/integrations/whatsapp/scripts/110-run-smoke.mjs', 'source/integrations/whatsapp/scripts/110-run-wire.mjs', 'source/integrations/whatsapp/transport/evolution-text.mjs', 'source/integrations/whatsapp/worker/attendance-jobs.mjs', 'source/integrations/whatsapp/worker/config.mjs', 'source/integrations/whatsapp/worker/dispatch.mjs', 'source/integrations/whatsapp/worker/errors.mjs', 'source/integrations/whatsapp/worker/http-client.mjs', 'source/integrations/whatsapp/worker/main.mjs', 'source/integrations/whatsapp/worker/model-client.mjs', 'source/integrations/whatsapp/worker/model-config.mjs', 'source/integrations/whatsapp/worker/process-inbox.mjs', 'source/integrations/whatsapp/worker/server.mjs', 'inputs/backend.jar', 'inputs/verified-ci-receipt.json', 'inputs/public-ca.der')
ALLOW = set(FIXED_FILES) | {"SHA256SUMS.json"}
MAX_BYTES = 120_000_000
MAX_ENTRIES = 64
PIN_SOURCE = "5ddb7c3db6c38385c018028741470ab6a509604f"
PIN_JAR = "fc5dccdf30e234f6dc9a572ce679aa5cbc126b3e5fc90b4bf9f312270d3063ce"
PIN_CA = "153618de000dc4ad8a23f01771f7557920ea8901ada2a15055cbcbf0b3598781"
PIN_RECEIPT = "8c856988e236956e156a7c8011b1810378263fd3eb6b50c4753c12275efbc035"
PROFILE = "VPS_PHYSICAL_JOURNEY_V1"


def require(ok, code='PHYSICAL_STAGE_SCOPE_OR_INTEGRITY_MISMATCH'):
    if not ok:
        raise ValueError(code)


def parents_for(files):
    return {parent.as_posix() for name in files for parent in PurePosixPath(name).parents if parent.as_posix() != '.'}


def normalized_member(item):
    name = item.name.removeprefix('./')
    if name == 'package' and item.isdir():
        return ''
    return name.removeprefix('package/')


def validate_arguments(attempt, expected, module, size, files):
    require(re.fullmatch('[a-z][a-z0-9-]{2,39}', attempt) and re.fullmatch('[a-f0-9]{64}', expected)
            and re.fullmatch('[a-f0-9]{64}', module) and type(size) is int and 0 < size <= MAX_BYTES
            and isinstance(files, dict) and set(files) == ALLOW and len(files) <= MAX_ENTRIES)
    require(all(isinstance(value, str) and re.fullmatch('[a-f0-9]{64}', value) for value in files.values()))


def verify_bindings(files, captured, module):
    require(set(files) == ALLOW and files['inputs/backend.jar'] == PIN_JAR
            and files['inputs/public-ca.der'] == PIN_CA and files['inputs/verified-ci-receipt.json'] == PIN_RECEIPT,
            'PHYSICAL_INPUT_PIN_CHANGED')
    require(files['source-manifest.json'] == module, 'PHYSICAL_MODULE_PIN_CHANGED')
    try:
        contract = json.loads(captured['contract.json'])
        manifest = json.loads(captured['source-manifest.json'])
        seal = json.loads(captured['SHA256SUMS.json'])
        layout = ast.parse(captured['layout.py'].decode('utf8'))
        assignments = [node for node in layout.body if isinstance(node, ast.Assign)
                       and any(isinstance(target, ast.Name) and target.id == 'FIXED_FILES' for target in node.targets)]
        require(len(assignments) == 1)
        selected = ast.literal_eval(assignments[0].value)
        require(isinstance(selected, tuple) and len(selected) == len(FIXED_FILES) and set(selected) == set(FIXED_FILES),
                'PHYSICAL_LAYOUT_CHANGED')
        require(contract['profile'] == PROFILE and contract['backend']['commit'] == PIN_SOURCE
                and contract['backend']['jarSha256'] == PIN_JAR
                and contract['backend']['verifiedReceiptSha256'] == PIN_RECEIPT
                and contract['apk']['publicCaSha256'] == PIN_CA and contract['moduleSha256'] == module,
                'PHYSICAL_CONTRACT_CHANGED')
        effective = ALLOW - {'SHA256SUMS.json', 'source-manifest.json', 'contract.json',
                             'inputs/backend.jar', 'inputs/public-ca.der', 'inputs/verified-ci-receipt.json'}
        require(manifest['profile'] == PROFILE and set(manifest['files']) == effective
                and all(manifest['files'][name] == files[name] for name in effective), 'PHYSICAL_MODULE_MATRIX_CHANGED')
        require(set(seal) == set(FIXED_FILES) and all(seal[name] == files[name] for name in FIXED_FILES),
                'PHYSICAL_SEAL_CHANGED')
    except (KeyError, TypeError, SyntaxError, RecursionError, UnicodeError, json.JSONDecodeError):
        raise ValueError('PHYSICAL_MANIFEST_INVALID') from None


def identity_from_stat(info, relative, is_directory):
    require((stat.S_ISDIR(info.st_mode) if is_directory else stat.S_ISREG(info.st_mode))
            and info.st_uid == os.getuid() and stat.S_IMODE(info.st_mode) == (0o700 if is_directory else 0o600)
            and (is_directory or info.st_nlink == 1))
    return {'path': relative, 'device': info.st_dev, 'inode': info.st_ino, 'uid': info.st_uid,
            'mode': stat.S_IMODE(info.st_mode)}


def identity(path, relative, is_directory):
    return identity_from_stat(path.lstat(), relative, is_directory)


def matches(path, record, is_directory):
    try:
        return identity(path, record['path'], is_directory) == record
    except (OSError, ValueError, KeyError, TypeError):
        return False


def safe_parents(dest, relative, directories):
    require(matches(dest, directories[''], True), 'PHYSICAL_STAGE_PARENT_CHANGED')
    for parent in reversed(PurePosixPath(relative).parents):
        name = parent.as_posix()
        if name != '.':
            require(name in directories and matches(dest / name, directories[name], True), 'PHYSICAL_STAGE_PARENT_CHANGED')


def create_private_file(dest, name, directories):
    safe_parents(dest, name, directories)
    parent = PurePosixPath(name).parent.as_posix()
    parent = '' if parent == '.' else parent
    directory_fd = os.open(dest / parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    fd = None
    try:
        require(identity_from_stat(os.fstat(directory_fd), parent, True) == directories[parent], 'PHYSICAL_STAGE_PARENT_CHANGED')
        fd = os.open(PurePosixPath(name).name, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                     0o600, dir_fd=directory_fd)
        record = identity_from_stat(os.fstat(fd), name, False)
        output = os.fdopen(fd, 'wb'); fd = None
        return output, record
    finally:
        os.close(directory_fd)
        if fd is not None:
            os.close(fd)


def stage(root, attempt, expected, module, size, files, source):
    validate_arguments(attempt, expected, module, size, files)
    require(root.is_dir() and root.resolve() == root and root.stat().st_uid == os.getuid()
            and stat.S_IMODE(root.stat().st_mode) == 0o700)
    dest = root / attempt
    dest.mkdir(mode=0o700)
    directories = {'': identity(dest, '', True)}
    written = {}; seen = set(); size_out = 0; captured = {}; count = 0
    transfer = dest / '.transfer'
    try:
        target, written['.transfer'] = create_private_file(dest, '.transfer', directories)
        with target:
            digest = hashlib.sha256(); received = 0
            while received < size:
                chunk = source.read(min(262144, size - received))
                require(chunk and len(chunk) <= size - received)
                target.write(chunk); digest.update(chunk); received += len(chunk)
            require(not source.read(1) and digest.hexdigest() == expected, 'PHYSICAL_TRANSFER_DIGEST_CHANGED')
        with tarfile.open(transfer, mode='r:*') as archive:
            for item in archive:
                count += 1; require(count <= MAX_ENTRIES, 'PHYSICAL_ENTRY_LIMIT')
                name = normalized_member(item)
                if item.isdir() and name in parents_for(files) | {''}:
                    continue
                require(name in files and name not in seen and item.isfile() and 0 < item.size
                        <= (100_000_000 if name == 'inputs/backend.jar' else 1_000_000), 'PHYSICAL_UNEXPECTED_ENTRY')
                size_out += item.size; require(size_out <= MAX_BYTES, 'PHYSICAL_BUNDLE_TOO_LARGE'); seen.add(name)
                parent = dest
                parts = PurePosixPath(name).parts
                for index, component in enumerate(parts[:-1]):
                    relative = '/'.join(parts[:index + 1])
                    safe_parents(dest, relative, directories)
                    parent = parent / component
                    if relative not in directories:
                        parent.mkdir(mode=0o700)
                        directories[relative] = identity(parent, relative, True)
                    require(matches(parent, directories[relative], True), 'PHYSICAL_STAGE_PARENT_CHANGED')
                safe_parents(dest, name, directories)
                path = dest / name
                output, written[name] = create_private_file(dest, name, directories)
                with output:
                    digest = hashlib.sha256(); actual_size = 0; chunks = []
                    with archive.extractfile(item) as member:
                        for block in iter(lambda: member.read(262144), b''):
                            actual_size += len(block); require(actual_size <= item.size)
                            output.write(block); digest.update(block)
                            if name in ('contract.json', 'source-manifest.json', 'SHA256SUMS.json', 'layout.py'):
                                chunks.append(block)
                    require(actual_size == item.size and digest.hexdigest() == files[name], 'PHYSICAL_FILE_DIGEST_CHANGED')
                    safe_parents(dest, name, directories)
                    require(matches(path, written[name], False), 'PHYSICAL_STAGE_FILE_REPLACED')
                    if chunks:
                        captured[name] = b''.join(chunks)
        require(seen == set(files), 'PHYSICAL_MISSING_ENTRY')
        verify_bindings(files, captured, module)
        safe_parents(dest, '.transfer', directories)
        require(matches(transfer, written['.transfer'], False)); transfer.unlink(); del written['.transfer']
        return {'staged': True, 'attempt': attempt, 'sha256': expected, 'moduleSha256': module, 'files': len(seen),
                'servicesStarted': False, **{key: value for key, value in directories[''].items() if key != 'path'},
                'directories': directories, 'fileIdentities': written}
    except BaseException:
        for name, record in reversed(list(written.items())):
            try:
                safe_parents(dest, name, directories)
                if matches(dest / name, record, False):
                    (dest / name).unlink()
            except (OSError, ValueError):
                pass  # Preserve replaced/foreign objects, including a replaced ancestor.
        for name, record in reversed(list(directories.items())):
            try:
                safe_parents(dest, name or '.', directories)
                if matches(dest / name, record, True):
                    (dest / name).rmdir()
            except (OSError, ValueError):
                pass
        raise


def unstage_unused(root, attempt, expected, module, files, receipt):
    validate_arguments(attempt, expected, module, 1, files)
    require(root.resolve() == root and root.stat().st_uid == os.getuid()
            and stat.S_IMODE(root.stat().st_mode) == 0o700)
    require(receipt.get('staged') is True and receipt.get('attempt') == attempt and receipt.get('sha256') == expected
            and receipt.get('moduleSha256') == module and receipt.get('servicesStarted') is False)
    dest = root / attempt
    directories, written = receipt['directories'], receipt['fileIdentities']
    known_dirs = parents_for(files)
    require(set(directories) == known_dirs | {''} and set(written) == set(files)
            and matches(dest, directories[''], True))
    require({str(path.relative_to(dest)) for path in dest.rglob('*')} == set(files) | known_dirs,
            'PHYSICAL_UNUSED_TREE_CHANGED')
    captured = {}
    size_out = 0
    for name in directories:
        require(matches(dest / name, directories[name], True), 'PHYSICAL_UNUSED_DIRECTORY_CHANGED')
    for name, record in written.items():
        safe_parents(dest, name, directories); require(matches(dest / name, record, False), 'PHYSICAL_UNUSED_FILE_CHANGED')
        size = (dest / name).stat().st_size
        require(0 < size <= (100_000_000 if name == 'inputs/backend.jar' else 1_000_000), 'PHYSICAL_UNUSED_SIZE_CHANGED')
        size_out += size; require(size_out <= MAX_BYTES, 'PHYSICAL_UNUSED_SIZE_CHANGED')
        digest = hashlib.sha256(); chunks = []
        fd = os.open(dest / name, os.O_RDONLY | os.O_NOFOLLOW)
        with os.fdopen(fd, 'rb') as member:
            require(identity_from_stat(os.fstat(member.fileno()), name, False) == record, 'PHYSICAL_UNUSED_FILE_CHANGED')
            count = 0
            for block in iter(lambda: member.read(262144), b''):
                count += len(block); require(count <= size, 'PHYSICAL_UNUSED_SIZE_CHANGED')
                digest.update(block)
                if name in ('contract.json', 'source-manifest.json', 'SHA256SUMS.json', 'layout.py'):
                    chunks.append(block)
        require(count == size and digest.hexdigest() == files[name], 'PHYSICAL_UNUSED_FILE_CHANGED')
        if chunks:
            captured[name] = b''.join(chunks)
    verify_bindings(files, captured, module)
    for name, record in written.items():
        safe_parents(dest, name, directories); require(matches(dest / name, record, False), 'PHYSICAL_UNUSED_FILE_CHANGED')
        (dest / name).unlink()
    for name in sorted(known_dirs, key=lambda item: (-item.count('/'), item)):
        safe_parents(dest, name, directories); require(matches(dest / name, directories[name], True)); (dest / name).rmdir()
    require(matches(dest, directories[''], True)); dest.rmdir()
    return {'unusedPackageRemoved': True, 'attempt': attempt, 'sha256': expected, 'moduleSha256': module}


ROOT_PARENTS = ('.local', '.local/share', '.local/share/vanrides', '.local/share/vanrides/wa110-vps-lab')


def remove_created_parents(home, created):
    require(isinstance(created, dict) and set(created) <= set(ROOT_PARENTS))
    retained = 0
    for name in reversed(ROOT_PARENTS):
        if name not in created:
            continue
        path = home / name
        try:
            if path.resolve() == path and matches(path, created[name], True):
                path.rmdir()
            else:
                retained += 1
        except OSError:
            retained += 1  # New foreign contents or replaced parents are preserved.
    return retained


def private_root(home):
    require(home.resolve() == home and home.is_dir() and home.stat().st_uid == os.getuid())
    created = {}
    try:
        for name in ROOT_PARENTS:
            path = home / name
            if not path.exists():
                path.mkdir(mode=0o700)
                created[name] = identity(path, name, True)
            info = path.lstat()
            require(path.resolve() == path and stat.S_ISDIR(info.st_mode) and info.st_uid == os.getuid()
                    and not stat.S_IMODE(info.st_mode) & 0o022, 'PHYSICAL_ROOT_PARENT_INVALID')
        return home / ROOT_PARENTS[-1], created
    except BaseException:
        remove_created_parents(home, created)
        raise


if __name__ == '__main__':
    os.umask(0o077)
    require(pwd.getpwuid(os.getuid()).pw_name == 'mathwidu' and socket.gethostname() == 'srv1397065'
            and Path.home() == Path('/home/mathwidu'))
    root, new_parents = private_root(Path.home())
    try:
        if len(sys.argv) == 8 and sys.argv[6] == 'unstage-unused':
            receipt = json.loads(sys.argv[7])
            result = unstage_unused(root, sys.argv[1], sys.argv[2], sys.argv[3], json.loads(sys.argv[5]), receipt)
            result['rootParentsRetained'] = remove_created_parents(Path.home(), receipt.get('rootParentsCreated', {}))
        else:
            require(len(sys.argv) == 6)
            result = stage(root, sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), json.loads(sys.argv[5]), sys.stdin.buffer)
            result['rootParentsCreated'] = new_parents
    except BaseException:
        remove_created_parents(Path.home(), new_parents)
        raise
    print(json.dumps(result))
