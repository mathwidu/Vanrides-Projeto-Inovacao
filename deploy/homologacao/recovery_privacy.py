"""Same-JAR V44 laboratory command, with source traffic fenced and no live input."""
import io
import os
import re
import secrets
import uuid
from pathlib import Path
from datetime import datetime, timedelta, timezone
from zipfile import ZipFile

from rehearse_migration import MAX_JAR, encode, object_json, read, require, sha, write_new
import recovery_fixture as fixture

MAIN = 'com.vanrides.vanrides.whatsapp.privacy.WaPrivacyRecoveryCommand'
PREFIX = 'BOOT-INF/classes/com/vanrides/vanrides/whatsapp/privacy/'
CLASSES = tuple(PREFIX + name + '.class' for name in (
    'WaPrivacyRecoveryCommand', 'WaPrivacyRecoveryFiles', 'WaPrivacyRecoveryFixture',
    'WaPrivacyRestore', 'WaPrivacyFileJournal', 'WaPrivacyService', 'WaPrivacyTechnicalRetention',
    'WaPrivacyTerminalMaintenance', 'WaPrivacyHistoryRetention', 'WaPrivacyHistoryProjection',
    'WaPrivacyJobs', 'WaPrivacyJobController', 'WaPrivacySettings', 'WaPrivacyStartupGate'))
LAUNCHER = 'org/springframework/boot/loader/launch/PropertiesLauncher.class'
FAMILIES = ('TERMINAL_7D', 'TECHNICAL_7D', 'PROFILE_30D', 'HISTORY_PROJECTION_90D', 'HISTORY_90D')


def class_inventory(jar):
    with ZipFile(io.BytesIO(jar)) as archive:
        require(LAUNCHER in archive.namelist() and all(path in archive.namelist()
                and archive.getinfo(path).file_size <= 2 * 1024 * 1024 for path in CLASSES), 'REC_PRIVACY_CLASSES_CHANGED')
        return {'state': 'REQUIRED_V44', 'classes': {path: sha(archive.read(path)) for path in CLASSES}}


def validate_manifest(value, jar):
    if value['schemaVersion'] == 1:
        require(value['privacy'] == {'state': 'PENDING_V44'}, 'REC_PRIVACY_MANIFEST_INVALID'); return
    privacy = value['privacy']
    require(value['schemaVersion'] == 2 and value['candidate']['flywayVersion'] >= 44
            and isinstance(privacy, dict) and set(privacy) == {'state', 'classes'}
            and privacy['state'] == 'REQUIRED_V44' and isinstance(privacy['classes'], dict)
            and set(privacy['classes']) == set(CLASSES), 'REC_PRIVACY_MANIFEST_INVALID')
    require(privacy == class_inventory(jar), 'REC_PRIVACY_CLASSES_CHANGED')


def probe_disabled(session):
    with session.owned.budget(30):
        before = session.sql('source-pg', fixture.DATABASES[0], fixture.FINGERPRINT)
        path = '/integrations/whatsapp/v1/privacy/tick'
        require(session.http('source-channel', 'api', path, {'family': FAMILIES[0], 'batchLimit': 1})['status'] == 401,
                'REC_PRIVACY_UNAUTHENTICATED_TICK_ACCEPTED')
        for family in FAMILIES:
            response = session.http('source-channel', 'api', path, {'family': family, 'batchLimit': 1}, session.keys['SYSTEM_JOB'])
            body = response['body']
            require(response['status'] == 200 and set(body) == {'status', 'family', 'examined', 'erased', 'deleted', 'deferred', 'wouldChange',
                    'complete', 'serverTime', 'nextCursor', 'reasonCodes'} and body['status'] == 'DISABLED' and body['family'] == family
                    and body['complete'] is False and body['nextCursor'] is None and body['reasonCodes'] == []
                    and all(type(body[key]) is int and body[key] == 0 for key in ('examined', 'erased', 'deleted', 'deferred', 'wouldChange')),
                    'REC_PRIVACY_DEFAULT_OFF_UNVERIFIED')
        require(session.sql('source-pg', fixture.DATABASES[0], fixture.FINGERPRINT) == before, 'REC_PRIVACY_DISABLED_MUTATED_DATABASE')
        return {'status': 'DISABLED_VERIFIED', 'families': list(FAMILIES), 'unauthenticatedStatus': 401,
                'databaseFingerprintSha256': sha(before.encode()), 'apiHealthyWithoutJournalMount': True}


class PrivacyRecovery:
    def __init__(self, session, on_stage=lambda stage: None):
        self.session, self.owned = session, session.owned
        self.on_stage = on_stage
        self.enabled = session.value['schemaVersion'] == 2
        self.directory = session.output / 'privacy'
        self.credential = secrets.token_urlsafe(32)
        self.fixture_hash, self.retained_hash, self.source_result = None, None, None
        self.journal_hashes = None

    def quiescent(self, prefix):
        self.owned.check(prefix + '-pg')
        for kind in ('api', 'wa', 'main', 'n8n-worker'):
            role = prefix + '-' + kind
            if role not in self.owned.containers:
                require(prefix == 'restored', 'REC_PRIVACY_SOURCE_CONSUMER_MISSING'); continue
            cid = self.owned.check(role)
            require(self.owned.run(['inspect', '--format', '{{json .State.Running}}', cid]).strip() == b'false',
                    'REC_PRIVACY_TRAFFIC_NOT_BLOCKED')

    def prepare(self):
        if not self.enabled: return
        self.on_stage('PREPARE')
        with self.owned.budget(120):
            self.quiescent('source')
            self.directory.mkdir(mode=0o700); (self.directory / 'journal').mkdir(mode=0o700)
            instance = 'wa_privacy_recovery_' + self.owned.nonce
            self.session.sql('source-pg', 'vanrides_wa070',
                "INSERT INTO wa.service_credential(id,principal,environment,token_hash,allowed_instance_id,expires_at) "
                "VALUES ('%s','SYSTEM_JOB','wa070-local','%s','%s',clock_timestamp()+interval '120 days');" %
                (uuid.uuid4(), sha(self.credential.encode()), instance))
            self.command('PREPARE', 'source-pg')
            raw = read(self.directory / 'fixture.json', 64000); state = object_json(raw)
            require(set(state) == {'schemaVersion', 'nonce', 'baseAt', 'coreDigest', 'betaDigest'}
                    and state['nonce'] == self.owned.nonce and state['schemaVersion'] == 1, 'REC_PRIVACY_FIXTURE_INVALID')
            self.fixture_hash = sha(raw)

    def close_purge(self):
        if not self.enabled: return
        self.on_stage('CLOSE_PURGE')
        with self.owned.budget(120):
            self.quiescent('source')
            self.source_result = self.command('CLOSE_PURGE', 'source-pg')
            raw = encode(self.source_result['head']); write_new(self.directory / 'retained-head.json', raw)
            self.retained_hash = sha(raw)
            fd = os.open(self.directory, os.O_RDONLY | os.O_DIRECTORY)
            try: os.fsync(fd)
            finally: os.close(fd)
            self.journal_hashes = self.journal()

    def reapply(self):
        self.on_stage('REAPPLY_PURGE')
        require(self.enabled and self.source_result is not None and self.journal_hashes is not None, 'REC_PRIVACY_NOT_PREPARED')
        with self.owned.budget(120):
            self.quiescent('restored')
            require(sha(read(self.directory / 'retained-head.json', 64000)) == self.retained_hash
                    and self.journal() == self.journal_hashes, 'REC_PRIVACY_CURRENT_JOURNAL_CHANGED')
            result = self.command('REAPPLY_PURGE', 'restored-pg')
            require(result['head'] == self.source_result['head'] and self.journal() == self.journal_hashes,
                    'REC_PRIVACY_ORIGINAL_HEAD_CHANGED')
            return {'state': 'V44_REAPPLIED_VERIFIED', 'reapplied': True, 'absenceVerified': True,
                    'betaPreserved': True, 'corePreserved': True, 'target': 'RESTORED_DATABASE_ONLY',
                    'source': self.source_result, 'restored': result, 'journalFilesSha256': self.journal_hashes,
                    'retainedHeadSha256': self.retained_hash}

    def journal(self):
        return {name: sha(read(self.directory / 'journal' / name, maximum))
                for name, maximum in (('erasures.jsonl', 8 * 1024 * 1024), ('head.json', 256))}

    def command(self, operation, pg):
        require((operation, pg) in (('PREPARE', 'source-pg'), ('CLOSE_PURGE', 'source-pg'), ('REAPPLY_PURGE', 'restored-pg')),
                'REC_PRIVACY_TARGET_INVALID')
        value, owned = self.session.value, self.owned
        require(sha(read(self.session.output / 'candidate.jar', MAX_JAR)) == value['candidate']['jarSha256'], 'REC_PRIVATE_JAR_CHANGED')
        request = {'schemaVersion': 1, 'operation': operation, 'environment': 'wa070-local', 'nonce': owned.nonce,
                   'password': self.session.keys['postgres'], 'credential': self.credential}
        if operation != 'PREPARE': request['fixtureSha256'] = self.fixture_hash
        if operation == 'REAPPLY_PURGE': request['retainedHeadSha256'] = self.retained_hash
        name = operation.lower(); result_file = self.directory / (name + '.result.json')
        require(not result_file.exists() and not result_file.is_symlink(), 'REC_PRIVACY_EXTERNAL_RESULT_REJECTED')
        write_new(self.directory / (name + '.request.json'), encode(request))
        pg_id = owned.check(pg)
        options = ['--network', 'container:' + pg_id, '--memory', '640m', '--memory-swap', '640m', '--user', self.session.uid,
                   '--read-only', '--tmpfs', '/tmp:rw,nosuid,nodev,size=64m,mode=1777',
                   '--mount', 'type=bind,source=' + str(self.session.output / 'candidate.jar') + ',target=/app/app.jar,readonly',
                   '--mount', 'type=bind,source=' + str(self.directory) + ',target=/privacy', '--entrypoint', 'java']
        cid = owned.create('privacy-' + name.replace('_', '-'), value['images']['apiRuntime'], options,
            ['-Xms24m', '-Xmx384m', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=2', '-Dloader.main=' + MAIN,
             '-cp', '/app/app.jar', 'org.springframework.boot.loader.launch.PropertiesLauncher', '/privacy/' + name + '.request.json'])
        require(owned.run(['wait', owned.check('privacy-' + name.replace('_', '-'))], timeout=120).strip() == b'0', 'REC_PRIVACY_COMMAND_FAILED')
        require(result_file.stat().st_uid == os.getuid() and result_file.stat().st_mode & 0o777 == 0o600, 'REC_PRIVACY_RESULT_NOT_PRIVATE')
        result = object_json(read(result_file, 64000))
        expected = {'PREPARE': 'PREPARED', 'CLOSE_PURGE': 'SOURCE_ERASURE_VERIFIED', 'REAPPLY_PURGE': 'RESTORED_ERASURE_VERIFIED'}[operation]
        fields = {'schemaVersion', 'status', 'ready', 'environment', 'synthetic', 'releaseEligible'}
        fields |= {'fixtureReady'} if operation == 'PREPARE' else {'head', 'maintenance', 'alphaAbsent', 'betaPreserved', 'corePreserved', 'originalClosureAndAckPreserved', 'journalUnchangedAfterPurge'}
        require(set(result) == fields and type(result.get('schemaVersion')) is int and result['schemaVersion'] == 1
                and result.get('status') == expected and result.get('synthetic') is True
                and result.get('environment') == 'wa070-local' and result.get('releaseEligible') is False
                and result.get('ready') is (operation != 'PREPARE'), 'REC_PRIVACY_COMMAND_UNVERIFIED')
        if operation != 'PREPARE':
            require(all(result.get(key) is True for key in ('alphaAbsent', 'betaPreserved', 'corePreserved',
                    'originalClosureAndAckPreserved', 'journalUnchangedAfterPurge')), 'REC_PRIVACY_ABSENCE_UNVERIFIED')
            require(isinstance(result['head'], dict) and set(result['head']) == {'sequence', 'digest'}
                    and type(result['head']['sequence']) is int and result['head']['sequence'] == 1
                    and re.fullmatch('[0-9a-f]{64}', result['head']['digest']), 'REC_PRIVACY_HEAD_INVALID')
            require(isinstance(result['maintenance'], dict) and set(result['maintenance']) == {'terminalErased', 'technicalChanged', 'historyDeleted', 'historyProjected'}
                    and all(type(count) is int and count > 0 for count in result['maintenance'].values()), 'REC_PRIVACY_MAINTENANCE_UNVERIFIED')
        return result


def artifact_inventory(output, now=None):
    """Expiry preview only. Never removes a backup, journal, diagnostic or owned volume."""
    now = now or datetime.now(timezone.utc)
    result = []
    for path in sorted(Path(output).rglob('*')):
        require(not path.is_symlink(), 'REC_ARTIFACT_SYMLINK_REJECTED')
        if not path.is_file(): continue
        name = str(path.relative_to(output)); info = path.stat()
        require(len(result) < 6000, 'REC_ARTIFACT_INVENTORY_LIMIT')
        kind = ('database-backup' if path.suffix == '.dump' else 'erasure-journal' if name.startswith('privacy/journal/')
                else 'independent-retained-head' if name == 'privacy/retained-head.json'
                else 'synthetic-request-secret' if name.endswith('.request.json')
                else 'fixture-provenance' if name == 'privacy/fixture.json'
                else 'candidate-jar' if name == 'candidate.jar' else 'diagnostic-or-runtime-file')
        created = datetime.fromtimestamp(info.st_ctime, timezone.utc)
        retain_until = created + timedelta(days=7) if kind == 'database-backup' else None
        result.append({'path': name, 'classification': kind, 'bytes': info.st_size, 'sha256': sha(read(path, MAX_JAR)) if info.st_size else sha(b''),
                       'createdAt': created.isoformat(), 'createdAtBasis': 'OBSERVED_INODE_CHANGE_TIME',
                       'retainUntil': retain_until.isoformat() if retain_until else None,
                       'expiryPreview': bool(retain_until and now >= retain_until), 'deletionImplemented': False})
    return {'status': 'INVENTORIED', 'observedAt': now.isoformat(), 'physicalBackupRotationVerified': False,
            'deletionImplemented': False, 'artifacts': result}
