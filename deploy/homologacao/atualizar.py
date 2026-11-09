#!/usr/bin/env python3
"""Comando SSH restrito para atualizar somente a API online do VanRides."""

import fcntl
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from zipfile import BadZipFile, ZipFile


REPOSITORY = 'ghcr.io/mathwidu/vanrides-projeto-inovacao-team-api'
SOURCE = 'https://github.com/mathwidu/Vanrides-Projeto-Inovacao.git'
SERVICE = 'vanrides-hml-api_api'
HOST = 'vanrides-inovacao-api.matheusduarte.dev.br'
ENVIRONMENT = 'vanrides-mvp-hml'
STATE = Path.home() / '.local/share/vanrides/cd'
DOCKER = ('docker', '--host', 'unix:///var/run/docker.sock')
SHA = r'[a-f0-9]{40}'
HASH = r'[a-f0-9]{64}'
JAR_ENV = 'VANRIDES_JAR_SHA256='
ENTRYPOINT = ['/bin/sh', '/run/online/entrypoint.sh']
TEAM_MIGRATION = 'BOOT-INF/classes/db/migration/V39__whatsapp_team_admission.sql'


class DeployError(Exception):
    pass


def require(condition, reason):
    if not condition:
        raise DeployError(reason)


def run(command, timeout=20):
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        raise DeployError('Comando local indisponível ou tempo esgotado.') from None
    operation = command[len(DOCKER)] if command[0] == 'docker' else command[2]
    require(result.returncode == 0, f'Falha na operação {command[0]} {operation}.')
    return result.stdout.strip()


def docker(*args, timeout=20):
    return run([*DOCKER, *args], timeout)


def git(*args, timeout=20):
    return run(['git', f'--git-dir={STATE / "source.git"}', *args], timeout)


@dataclass(frozen=True)
class Request:
    source: str
    digest: str
    jar: str

    @property
    def image(self):
        return f'{REPOSITORY}@{self.digest}'


def request_from(original):
    match = re.fullmatch(rf'deploy ({SHA}) (sha256:{HASH}) ({HASH})', original)
    require(match is not None, 'Comando SSH inválido.')
    return Request(*match.groups())


@dataclass(frozen=True)
class Service:
    image: str
    jar: str
    state: str
    version: str


def jar_template(container):
    n = len(JAR_ENV)
    return (
        '{{range ' + container + '.Env}}'
        '{{if and (ge (len .) ' + str(n) + ') (eq (slice . 0 ' + str(n) + ') "' + JAR_ENV + '")}}'
        '{{slice . ' + str(n) + '}}{{end}}{{end}}'
    )


def service():
    container = '.Spec.TaskTemplate.ContainerSpec'
    fields = [
        '{{' + container + '.Image}}',
        jar_template(container),
        '{{if .UpdateStatus}}{{.UpdateStatus.State}}{{end}}',
        '{{.Version.Index}}',
        '{{index .Spec.Labels "com.docker.stack.namespace"}}',
        '{{.Spec.Mode.Replicated.Replicas}}',
        '{{json ' + container + '.Command}}',
        '{{range ' + container + '.Mounts}}{{if and (eq .Type "tmpfs") (eq .Target "/tmp")}}yes{{end}}{{end}}',
    ]
    image, jar, state, version, namespace, replicas, command, tmp = docker(
        'service', 'inspect', SERVICE, '--format', '|'.join(fields),
    ).split('|')
    require(
        namespace == 'vanrides-hml-api' and replicas == '1' and json.loads(command) == ENTRYPOINT and tmp == 'yes',
        'O serviço diverge da topologia online de homologação.',
    )
    require(re.fullmatch(HASH, jar), 'Checksum atual do serviço indisponível.')
    return Service(image, jar, state, version)


def previous():
    container = '.PreviousSpec.TaskTemplate.ContainerSpec'
    template = '{{if .PreviousSpec}}{{' + container + '.Image}}|' + jar_template(container) + '{{end}}'
    return docker('service', 'inspect', SERVICE, '--format', template)


def image_info(reference):
    require(
        re.fullmatch(re.escape(REPOSITORY) + rf'(?::[A-Za-z0-9_.-]+)?(?:@sha256:{HASH})?', reference),
        'Referência de imagem fora do repositório da API.',
    )
    fields = (
        '{{.Id}}|{{.Os}}|{{.Architecture}}|{{index .Config.Labels "org.opencontainers.image.revision"}}|'
        '{{index .Config.Labels "dev.vanrides.source-tree"}}|{{index .Config.Labels "dev.vanrides.jar-sha256"}}'
    )
    identity, system, arch, source, tree, jar = docker('image', 'inspect', reference, '--format', fields).split('|')
    require(
        system == 'linux' and arch == 'amd64' and re.fullmatch(rf'sha256:{HASH}', identity)
        and re.fullmatch(SHA, source) and re.fullmatch(SHA, tree) and re.fullmatch(HASH, jar),
        'Imagem sem identidade, plataforma ou proveniência esperada.',
    )
    return identity, source, tree, jar


def verify_source(new, installed):
    git('fetch', '--filter=tree:0', '--no-tags', '--no-recurse-submodules', '--no-write-fetch-head',
        SOURCE, '+refs/heads/main:refs/heads/main', timeout=90)
    try:
        git('merge-base', '--is-ancestor', new, 'refs/heads/main')
    except DeployError:
        raise DeployError('O candidato não faz parte da linhagem atual da main.') from None
    try:
        git('merge-base', '--is-ancestor', installed, new)
    except DeployError:
        raise DeployError('O candidato não contém a revisão atualmente instalada.') from None
    tree = git('show', '-s', '--format=%T', new)
    require(re.fullmatch(SHA, tree), 'Árvore da revisão candidata inválida.')
    return tree


def migration_inventory(jar):
    with ZipFile(jar) as contents:
        files = [item for item in contents.namelist() if not item.endswith('/')]
        migrations = [item for item in files if item.startswith('BOOT-INF/classes/db/migration/')]
        require(migrations and len(migrations) == len(set(migrations)), 'Inventário Flyway ausente ou duplicado.')
        checksums = {item: hashlib.sha256(contents.read(item)).hexdigest() for item in migrations}
        for profile in ('application.properties', 'application-homolog.properties'):
            path = f'BOOT-INF/classes/{profile}'
            settings = contents.read(path).decode('utf-8').splitlines()
            checksums[path] = tuple(
                line.strip() for line in settings if line.strip().startswith(('spring.flyway.', 'spring.jpa.hibernate.ddl-auto'))
            )
        flyway = [item for item in files if item.startswith('BOOT-INF/lib/flyway-')]
        checksums.update({item: hashlib.sha256(contents.read(item)).hexdigest() for item in flyway})
        return checksums


def allow_migrations(previous_inventory, next_inventory, bootstrap_checksum):
    if next_inventory == previous_inventory:
        return False
    require(bootstrap_checksum is not None,
            'Migrações ou configuração Flyway divergentes. A alteração exige plano de banco próprio.')
    require(TEAM_MIGRATION not in previous_inventory
            and next_inventory == {**previous_inventory, TEAM_MIGRATION: bootstrap_checksum},
            'O bootstrap aceita somente a adição da V39 com o checksum revisado.')
    return True


def read_bootstrap_checksum(approval):
    try:
        fd = os.open(approval, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    except FileNotFoundError:
        return None
    with os.fdopen(fd, 'r', encoding='ascii') as file:
        info = os.fstat(file.fileno())
        require(stat.S_ISREG(info.st_mode) and stat.S_IMODE(info.st_mode) == 0o600
                and info.st_uid == os.getuid(), 'A aprovação local da V39 deve ser um arquivo privado 0600.')
        checksum = file.read(66)
    require(re.fullmatch(rf'{HASH}\n?', checksum), 'Checksum inválido na aprovação local da V39.')
    return checksum.rstrip('\n')


def inspect_jar(image, expected, directory):
    with tempfile.TemporaryDirectory(prefix='vanrides-inspect-', dir=directory) as folder:
        jar = Path(folder) / 'app.jar'
        # Criar um contêiner sem iniciá-lo permite ler o JAR sem executar a imagem.
        container = docker('create', '--network', 'none', image)
        require(re.fullmatch(HASH, container), 'Contêiner temporário sem identidade.')
        try:
            docker('cp', f'{container}:/app/app.jar', str(jar), timeout=60)
            with jar.open('rb') as contents:
                checksum = hashlib.sha256()
                for chunk in iter(lambda: contents.read(1024 * 1024), b''):
                    checksum.update(chunk)
            require(checksum.hexdigest() == expected, 'Os bytes do JAR divergem do checksum aprovado.')
            return migration_inventory(jar)
        finally:
            docker('rm', container)


def https_ready():
    connection = http.client.HTTPSConnection(HOST, timeout=5)
    try:
        connection.request('GET', '/actuator/health', headers={'Accept': 'application/json'})
        response = connection.getresponse()
        return (
            response.status == 200 and response.getheader('X-VanRides-Environment') == ENVIRONMENT
            and json.loads(response.read(4096)).get('status') == 'UP'
        )
    except (OSError, http.client.HTTPException, ValueError, AttributeError):
        return False
    finally:
        connection.close()


def running_image():
    containers = docker(
        'ps', '--no-trunc', '--filter', f'label=com.docker.swarm.service.name={SERVICE}', '--format', '{{.ID}}',
    ).splitlines()
    if len(containers) == 1 and re.fullmatch(HASH, containers[0]):
        return docker('inspect', containers[0], '--format', '{{.Image}}')
    return None


def matches(service_state, image, jar):
    return (service_state.image, service_state.jar) == (image, jar)


def wait_ready(image, jar, identity):
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        status = service()
        require(matches(status, image, jar), 'A especificação do serviço mudou durante a atualização.')
        require(status.state not in ('paused', 'rollback_paused'), 'O Swarm pausou a atualização.')
        if status.state in ('', 'completed', 'rollback_completed') and running_image() == identity and https_ready():
            return
        time.sleep(3)
    raise DeployError('A imagem esperada ou a saúde HTTPS não convergiu no prazo.')


def rollout(request, old, old_id, new_id):
    try:
        docker(
            'service', 'update', '--detach', '--with-registry-auth', '--image', request.image,
            '--env-add', JAR_ENV + request.jar, SERVICE, timeout=45,
        )
        wait_ready(request.image, request.jar, new_id)
    except DeployError as failure:
        status = service()
        if matches(status, old.image, old.jar):
            wait_ready(old.image, old.jar, old_id)
            raise DeployError(f'{failure} A versão anterior continua saudável.') from None
        require(matches(status, request.image, request.jar), 'Outra revisão alterou o serviço. Retorno bloqueado.')
        require(previous() == f'{old.image}|{old.jar}', 'Especificação anterior mudou. Retorno bloqueado.')
        docker('service', 'update', '--detach', '--rollback', SERVICE, timeout=45)
        wait_ready(old.image, old.jar, old_id)
        raise DeployError(f'{failure} Imagem e checksum anteriores restaurados.') from None


def deploy(request, bootstrap_checksum=None):
    require(
        docker('info', '--format', '{{.Name}}|{{.Swarm.LocalNodeState}}|{{.Swarm.ControlAvailable}}')
        == 'srv1397065|active|true'
        and len(docker('node', 'ls', '--format', '{{.ID}}').splitlines()) == 1,
        'Destino diferente do manager da homologação.',
    )
    old = service()
    old_id, installed, _, installed_jar = image_info(old.image)
    require(old.jar == installed_jar, 'Checksum anterior diverge da imagem instalada.')
    require(running_image() == old_id and https_ready(), 'A versão atual não está saudável para iniciar a troca.')
    tree = verify_source(request.source, installed)
    if matches(old, request.image, request.jar):
        require(installed == request.source, 'Revisão do serviço diverge do candidato solicitado.')
        used_bootstrap = bootstrap_checksum is not None and (
            inspect_jar(old.image, old.jar, STATE).get(TEAM_MIGRATION) == bootstrap_checksum
        )
        wait_ready(old.image, old.jar, old_id)
        print(f'API já saudável em {request.source} {request.digest}.')
        return used_bootstrap
    docker('pull', '--platform', 'linux/amd64', request.image, timeout=150)
    new_id, source, image_tree, jar = image_info(request.image)
    require((source, image_tree, jar) == (request.source, tree, request.jar), 'Rótulos da imagem divergem do candidato aprovado.')
    used_bootstrap = allow_migrations(inspect_jar(old.image, old.jar, STATE),
                                     inspect_jar(request.image, request.jar, STATE), bootstrap_checksum)
    require(service() == old, 'O serviço mudou enquanto a imagem era preparada.')
    rollout(request, old, old_id, new_id)
    print(f'API saudável em {request.source} {request.digest}.')
    return used_bootstrap


def main():
    try:
        require(len(sys.argv) == 1, 'A entrada deve vir do comando SSH restrito.')
        request = request_from(os.environ.get('SSH_ORIGINAL_COMMAND', ''))
        approval = STATE / f'bootstrap-v39-{request.source}.sha256'
        os.umask(0o077)
        fd = os.open(STATE / 'deploy.lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        with os.fdopen(fd, 'w') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            bootstrap_checksum = read_bootstrap_checksum(approval)
            if deploy(request, bootstrap_checksum):
                require(read_bootstrap_checksum(approval) == bootstrap_checksum,
                        'A aprovação da V39 mudou durante a publicação. Confira o arquivo local.')
                approval.unlink()
                print('Aprovação local da V39 consumida após verificação da API.')
    except DeployError as error:
        print(str(error), file=sys.stderr)
        return 1
    except BlockingIOError:
        print('Outra publicação da homologação está em curso.', file=sys.stderr)
        return 1
    except (OSError, BadZipFile, KeyError, ValueError):
        print('Falha na conferência local da publicação. Consulte o operador da homologação.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
