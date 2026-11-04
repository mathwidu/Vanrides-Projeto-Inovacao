"""One verified Unix endpoint, pinned in every command of an operation."""
import json
import os
import subprocess
from urllib.parse import urlsplit

from plan import Blocked, require


def local_docker(execute=subprocess.run):
    require(not os.environ.get('DOCKER_HOST') and not os.environ.get('DOCKER_CONTEXT'), 'HML_LOCAL_ENDPOINT_REQUIRED')
    environment = {key: value for key, value in os.environ.items()
                   if key not in ('DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH')}
    result = execute(['docker', 'context', 'inspect', '--format', '{{json .Endpoints.docker}}'],
                     capture_output=True, timeout=20, env=environment)
    require(result.returncode == 0 and len(result.stdout) <= 16000, 'HML_LOCAL_ENDPOINT_REQUIRED')
    try:
        endpoint = json.loads(result.stdout)['Host']
        url = urlsplit(endpoint)
        require(url.scheme == 'unix' and not url.netloc and url.path.startswith('/')
                and not url.query and not url.fragment and '\n' not in endpoint, 'HML_LOCAL_ENDPOINT_REQUIRED')
    except (ValueError, TypeError, KeyError, AttributeError):
        raise Blocked('HML_LOCAL_ENDPOINT_REQUIRED') from None

    def run(args, data=None, maximum=1000000):
        # --host is immutable for the whole round, including reconciliation/cleanup.
        result = execute(['docker', '--host', endpoint, *args], input=data, capture_output=True,
                         timeout=120, env=environment)
        require(result.returncode == 0 and len(result.stdout) <= maximum, 'HML_DOCKER_OPERATION_FAILED')
        return result.stdout
    return run
