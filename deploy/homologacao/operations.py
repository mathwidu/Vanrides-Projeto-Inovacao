#!/usr/bin/env python3
"""Diagnóstico local da homologação e plano de pausa; nunca executa uma mutação."""
import argparse
from datetime import datetime, timezone
import os
from pathlib import Path
import re
from subprocess import TimeoutExpired
import time
import uuid

from rehearse_migration import (Blocked, Docker, deploy_lock, encode, host_identity,
                               new_directory, object_json, read, require, sha, write_new)

ENVIRONMENT, INSTANCE = 'vanrides-mvp-hml', 'vanrides-hml'
NODE = 'node@sha256:48e4b67d85f87bd551df43704e24d252f56cc5f8e9718841aace50f19948f0f9'
POSTGRES = 'postgres@sha256:cbf42f0edc946c0bc1b8eeed62403734e1f25dc67a3491455d07a92fdb558b28'
API_REPOSITORY = 'ghcr.io/mathwidu/vanrides-projeto-inovacao-team-api'
TARGETS = {'api': ('vanrides-hml-api_api', 'vanrides-hml-api'),
           'database': ('vanrides-hml-api_db', 'vanrides-hml-api'),
           'worker': ('vanrides-hml-canal_worker', 'vanrides-hml-canal')}
SERVICE = ('{"id":{{json .ID}},"name":{{json .Spec.Name}},"image":{{json .Spec.TaskTemplate.ContainerSpec.Image}},'
           '"namespace":{{json (index .Spec.Labels "com.docker.stack.namespace")}},'
           '"replicas":{{json .Spec.Mode.Replicated.Replicas}}}')
CONTAINER = ('{"id":{{json .Id}},"image":{{json .Config.Image}},"running":{{json .State.Running}},'
             '"service":{{json (index .Config.Labels "com.docker.swarm.service.id")}},'
             '"name":{{json (index .Config.Labels "com.docker.swarm.service.name")}},'
             '"namespace":{{json (index .Config.Labels "com.docker.stack.namespace")}}}')
WORKER_HEALTH = """fetch('http://127.0.0.1:7080/healthz',{signal:AbortSignal.timeout(3000)})
.then(async r=>{const t=await r.text();if(r.status!==200||t.length>512)process.exit(2);process.stdout.write(t)})
.catch(()=>process.exit(2));"""
ROLES = ('delivery', 'job', 'conversation', 'channel')
METRICS = (*ROLES, 'expiringCredentials', 'submitting', 'staleSubmitting', 'unknown', 'deliveryCases',
           'inboxOld', 'stopPending', 'stopOld', 'foreignScope', 'schemaVersion', 'schemaFailures')
AGGREGATES = """
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='3s'; SET LOCAL lock_timeout='500ms';
WITH grants AS (SELECT * FROM wa.service_credential WHERE environment='vanrides-mvp-hml'
 AND allowed_instance_id='vanrides-hml' AND revoked_at IS NULL AND expires_at>clock_timestamp()),
attempts AS (SELECT a.* FROM wa.message_attempt a JOIN wa.service_credential c ON c.id=a.credential_id
 WHERE c.environment='vanrides-mvp-hml' AND a.instance_id='vanrides-hml'),
stops AS (SELECT s.* FROM wa.tracking_session s JOIN wa.driver_device d ON d.id=s.device_id
 WHERE d.environment='vanrides-mvp-hml' AND s.state IN ('STOP_REQUESTED','REVOKED','EXPIRED')
 AND s.stop_requested_at IS NOT NULL AND s.stopped_at IS NULL)
SELECT json_build_object(
 'delivery',(SELECT count(*) FROM grants WHERE principal='DELIVERY_WORKER'),
 'job',(SELECT count(*) FROM grants WHERE principal='SYSTEM_JOB'),
 'conversation',(SELECT count(*) FROM grants WHERE principal='CONVERSATION_WORKER'),
 'channel',(SELECT count(*) FROM grants WHERE principal='CHANNEL'),
 'expiringCredentials',(SELECT count(*) FROM grants WHERE expires_at<=clock_timestamp()+interval '7 days'),
 'submitting',(SELECT count(*) FROM attempts WHERE state='SUBMITTING'),
 'staleSubmitting',(SELECT count(*) FROM attempts WHERE state='SUBMITTING' AND submit_before<=clock_timestamp()),
 'unknown',(SELECT count(*) FROM attempts WHERE state='UNKNOWN'),
 'deliveryCases',(SELECT count(*) FROM wa.support_case WHERE environment='vanrides-mvp-hml'
   AND instance_id='vanrides-hml' AND purpose='DELIVERY' AND state='OPEN'),
 'inboxOld',(SELECT count(*) FROM wa.integration_inbox WHERE instance_id='vanrides-hml' AND category='MESSAGE'
   AND state IN ('PENDING','RETRY','CLAIMED') AND created_at<clock_timestamp()-interval '2 minutes'),
 'stopPending',(SELECT count(*) FROM stops),
 'stopOld',(SELECT count(*) FROM stops WHERE stop_requested_at<clock_timestamp()-interval '60 seconds'),
 'foreignScope',((SELECT count(*) FROM wa.test_cohort WHERE environment<>'vanrides-mvp-hml')+
   (SELECT count(*) FROM wa.service_credential WHERE environment<>'vanrides-mvp-hml')),
 'schemaVersion',(SELECT COALESCE(max(version::integer),0) FROM public.flyway_schema_history),
 'schemaFailures',(SELECT count(*) FROM public.flyway_schema_history WHERE NOT success));
ROLLBACK;
"""


def manifest(path, expected):
    raw = read(path, 16000)
    require(re.fullmatch('[0-9a-f]{64}', expected or '') and sha(raw) == expected, 'OPS_MANIFEST_CHANGED')
    value = object_json(raw)
    require(set(value) == {'schemaVersion', 'environment', 'instance', 'apiImage', 'schema'}
            and type(value['schemaVersion']) is int and value['schemaVersion'] == 1
            and value['environment'] == ENVIRONMENT and value['instance'] == INSTANCE
            and isinstance(value['apiImage'], str)
            and re.fullmatch(re.escape(API_REPOSITORY) + '@sha256:[0-9a-f]{64}', value['apiImage'])
            and type(value['schema']) is int and 43 <= value['schema'] <= 99, 'OPS_MANIFEST_INVALID')
    return value


def pause_request(path):
    path = Path(path); info = path.lstat()
    require(info.st_uid == os.getuid() and info.st_mode & 0o777 == 0o600
            and path.parent.stat().st_mode & 0o777 == 0o700, 'OPS_PRIVATE_REQUEST_REQUIRED')
    raw = read(path, 16000); value = object_json(raw)
    require(set(value) == {'operation', 'environment', 'batchId', 'credentialId', 'reasonCode'}
            and value['operation'] == 'revoke-service-credential' and value['environment'] == ENVIRONMENT
            and value['reasonCode'] == 'OPERATIONAL_PAUSE', 'OPS_PAUSE_REQUEST_INVALID')
    for key in ('batchId', 'credentialId'):
        require(isinstance(value[key], str) and str(uuid.UUID(value[key])) == value[key], 'OPS_PAUSE_REQUEST_INVALID')
    return value, sha(raw)


class Operations:
    def __init__(self, run, value, clock=time.monotonic):
        self.run, self.value, self.clock = run, value, clock
        self.deadline = clock() + 45
        self.containers = {}

    def call(self, args, data=None):
        remaining = min(6, self.deadline - self.clock())
        require(remaining > 0, 'OPS_DEADLINE_EXPIRED')
        try: return self.run(args, data, maximum=16000, timeout=remaining)
        except TimeoutExpired: raise Blocked('OPS_COMMAND_TIMEOUT') from None

    def target(self, role):
        name, namespace = TARGETS[role]
        image = self.value['apiImage'] if role == 'api' else NODE if role == 'worker' else POSTGRES
        service = object_json(self.call(['service', 'inspect', '--format', SERVICE, name]))
        require(set(service) == {'id', 'name', 'image', 'namespace', 'replicas'}
                and isinstance(service['id'], str) and re.fullmatch('[a-z0-9]{25}', service['id'])
                and service['name'] == name and service['namespace'] == namespace and service['image'] == image
                and type(service['replicas']) is int and service['replicas'] == 1, 'OPS_TARGET_NOT_VERIFIED')
        ids = self.call(['ps', '--no-trunc', '--filter', 'label=com.docker.swarm.service.id=' + service['id'],
                         '--format', '{{.ID}}']).decode().splitlines()
        require(len(ids) == 1 and re.fullmatch('[a-f0-9]{64}', ids[0]), 'OPS_ONE_RUNNING_TASK_REQUIRED')
        actual = object_json(self.call(['inspect', '--format', CONTAINER, ids[0]]))
        require(actual == {'id': ids[0], 'image': image, 'running': True, 'service': service['id'],
                           'name': name, 'namespace': namespace}, 'OPS_TASK_NOT_VERIFIED')
        self.containers[role] = ids[0]

    def sql(self, statement):
        require('database' in self.containers, 'OPS_DATABASE_NOT_VERIFIED')
        return object_json(self.call(['exec', '-i', '--user', 'postgres', self.containers['database'], 'psql',
            '--no-psqlrc', '-w', '-qAt', '--set', 'ON_ERROR_STOP=1', '-U', 'postgres', '-d', 'vanrides_hml_api'], statement.encode()))

    def diagnose(self):
        alerts, health = [], {}
        def alert(code, component, action):
            alerts.append({'code': code, 'component': component, 'action': action})
        for role in TARGETS:
            try: self.target(role); health[role] = 'TASK_VERIFIED'
            except (Blocked, OSError, ValueError, TypeError, KeyError):
                health[role] = 'UNVERIFIED'
                alert('TARGET_UNAVAILABLE_OR_CHANGED', role, 'Conferir versão e tarefa esperadas; não pausar ou reiniciar outro serviço.')
        if 'database' in self.containers and 'api' in self.containers:
            try:
                body = object_json(self.call(['exec', self.containers['database'], '/bin/busybox', 'wget', '-T', '3',
                    '-q', '-O', '-', '--header', 'Host: vanrides-inovacao-api.matheusduarte.dev.br',
                    'http://vanrides-hml-api_api:8080/actuator/health']))
                require(body == {'status': 'UP'}, 'OPS_API_UNHEALTHY'); health['api'] = 'UP'
            except (Blocked, OSError, ValueError, TypeError):
                health['api'] = 'UNHEALTHY'; alert('API_UNHEALTHY', 'api', 'Manter pausa de envio; restaurar API/DB e conferir STOP antes de retomar.')
        if 'worker' in self.containers:
            try:
                body = object_json(self.call(['exec', self.containers['worker'], 'node', '-e', WORKER_HEALTH]))
                require(body == {'status': 'UP', 'role': 'WA070_WORKER'}, 'OPS_WORKER_UNHEALTHY'); health['worker'] = 'UP'
            except (Blocked, OSError, ValueError, TypeError):
                health['worker'] = 'UNHEALTHY'; alert('WORKER_UNHEALTHY', 'worker', 'Conferir somente o worker VanRides; preservar API, ingresso e SYSTEM_JOB.')
        metrics = None
        if 'database' in self.containers:
            try:
                metrics = self.sql(AGGREGATES)
                require(set(metrics) == set(METRICS) and all(type(n) is int and 0 <= n <= 10**9 for n in metrics.values()), 'OPS_AGGREGATES_INVALID')
                require(metrics['foreignScope'] == 0, 'OPS_SCOPE_CHANGED')
            except (Blocked, OSError, ValueError, TypeError):
                metrics = None; alert('DATABASE_SCOPE_OR_QUERY_UNVERIFIED', 'database', 'Reconciliar o banco dedicado e o escopo; não preparar mutação sem essa prova.')
        if metrics is not None:
            if metrics['schemaVersion'] != self.value['schema'] or metrics['schemaFailures']:
                alert('SCHEMA_UNEXPECTED', 'database', 'Conferir migração e retorno ensaiados da mesma versão; não alterar Flyway manualmente.')
            for role in ROLES:
                if metrics[role] != 1:
                    alert('DELIVERY_PAUSED_OR_AMBIGUOUS' if role == 'delivery' else 'RECOVERY_ROLE_UNAVAILABLE_OR_AMBIGUOUS', role,
                          'Conferir credencial do papel correto. Retorno usa credencial nova; nunca reativar uma revogada.')
            for field, code, action in (
                ('expiringCredentials', 'CREDENTIAL_EXPIRING_7D', 'Renovar o papel afetado antes do prazo, sem divulgar valores.'),
                ('submitting', 'INFLIGHT_MAY_COMPLETE_25S', 'Uma tentativa autorizada pode terminar; aguardar até25s e reconciliar sem repetir POST.'),
                ('staleSubmitting', 'RECONCILIATION_OVERDUE', 'Manter W08/SYSTEM_JOB; investigar a agenda sem iniciar outro envio.'),
                ('unknown', 'DELIVERY_UNKNOWN', 'Preservar UNKNOWN e conferir o caso operacional; não reenviar automaticamente.'),
                ('deliveryCases', 'DELIVERY_CASE_OPEN', 'Consultar o caso pelo operador privado; não exportar destinatário ou conversa.'),
                ('inboxOld', 'INBOX_DELAY_OVER_120S', 'Conferir W01/worker; preservar ingresso e conferir pendências antes de retomar.'),
                ('stopOld', 'STOP_ACK_PENDING_OVER_60S', 'Orientar parada local no aparelho e recuperar conexão; não inventar ACK.')):
                if metrics[field]: alert(code, 'delivery' if field not in ('inboxOld', 'stopOld') else 'recovery', action)
        return {'schemaVersion': 1, 'status': 'ATTENTION' if alerts else 'TECHNICAL_CHECKS_OK', 'environment': ENVIRONMENT,
                'observedAt': datetime.now(timezone.utc).isoformat(), 'health': health, 'counts': metrics, 'alerts': alerts,
                'mutationsExecuted': False, 'whatsappDeliveryVerified': False, 'n8nSchedulesVerified': False, 'stopAckVerified': False}

    def plan_pause(self, request, request_sha, report):
        for key in ('credentialId', 'batchId'):
            require(isinstance(request[key], str) and str(uuid.UUID(request[key])) == request[key], 'OPS_PAUSE_REQUEST_INVALID')
        metrics = report['counts']
        require(metrics is not None and report['health'] == {'api': 'UP', 'database': 'TASK_VERIFIED', 'worker': 'UP'}
                and metrics['schemaVersion'] == self.value['schema'] and metrics['schemaFailures'] == 0
                and all(metrics[role] == 1 for role in ROLES), 'OPS_PAUSE_PREFLIGHT_INCOMPLETE')
        # UUIDs are canonical and go only over stdin. Output contains no credential or cohort ID.
        target = self.sql("BEGIN READ ONLY; SET LOCAL statement_timeout='3s'; SELECT json_build_object('target',"
            "EXISTS(SELECT 1 FROM wa.service_credential WHERE id='%s' AND principal='DELIVERY_WORKER' "
            "AND environment='vanrides-mvp-hml' AND allowed_instance_id='vanrides-hml' AND revoked_at IS NULL AND expires_at>clock_timestamp()),"
            "'technicalBatch',EXISTS(SELECT 1 FROM wa.test_cohort WHERE id='%s' AND synthetic AND environment='vanrides-mvp-hml')); ROLLBACK;"
            % (request['credentialId'], request['batchId']))
        require(target == {'target': True, 'technicalBatch': True}, 'OPS_DELIVERY_TARGET_NOT_VERIFIED')
        return {'schemaVersion': 1, 'status': 'PAUSE_PLAN_READY_NOT_EXECUTED', 'requestSha256': request_sha,
                'operation': 'revoke-service-credential', 'targetRole': 'DELIVERY_WORKER', 'revocationPermanent': True,
                'preserveRoles': ['CHANNEL', 'CONVERSATION_WORKER', 'SYSTEM_JOB', 'DEVICE'],
                'preservePaths': ['APP_STOP_ACK', 'W01_PROCESS_INBOX', 'W08_HEALTH_RECONCILE'],
                'inflightMayCompleteSeconds': 25, 'inflightObserved': metrics['submitting'],
                'requiredBeforeExecution': ['VERIFY_PRIVATE_BACKUP_AND_RETURN_RECIPE', 'VERIFY_OPERATOR_JAR_AND_LIVE_TARGET',
                    'RUN_EXISTING_PRIVATE_OPERATOR_ONCE', 'RECONCILE_UNCERTAIN_OPERATOR_RESULT'],
                'requiredAfterExecution': ['WAIT_INFLIGHT_AND_RUN_DIAGNOSIS_AGAIN', 'PROVE_NO_ACTIVE_DELIVERY_CREDENTIAL',
                    'PROVE_OLD_CLAIM_AND_AUTHORIZE_DENIED_IN_ISOLATED_REHEARSAL', 'KEEP_STOP_AND_W08_AVAILABLE'],
                'resume': 'CREATE_AND_INSTALL_NEW_DELIVERY_CREDENTIAL_BEFORE_ANY_NEW_CLAIM', 'mutationsExecuted': False}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('diagnose', 'pause-plan'))
    parser.add_argument('--manifest', type=Path, required=True); parser.add_argument('--sha256', required=True)
    parser.add_argument('--output', type=Path, required=True); parser.add_argument('--request', type=Path)
    args = parser.parse_args(argv)
    try:
        require((args.mode == 'pause-plan') == (args.request is not None), 'OPS_ARGUMENTS_INVALID')
        value = manifest(args.manifest, args.sha256)
        request = pause_request(args.request) if args.request else None
        host_identity()
        with deploy_lock():
            output = new_directory(args.output); operation = Operations(Docker(output), value)
            report = operation.diagnose(); write_new(output / 'diagnosis.json', encode(report))
            result = operation.plan_pause(*request, report) if request else report
            if request: write_new(output / 'pause-plan.json', encode(result))
        print(encode(result).decode(), end='')
        return 0 if request or not report['alerts'] else 2
    except (Blocked, OSError, ValueError, TypeError, KeyError):
        print(encode({'status': 'BLOCKED', 'action': 'Preservar saídas privadas; conferir alvo, escopo e prazo. Nenhuma mutação foi executada.',
                      'mutationsExecuted': False}).decode(), end=''); return 2


if __name__ == '__main__': raise SystemExit(main())
