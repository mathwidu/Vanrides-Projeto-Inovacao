// Generates the Digital Asset Links response for an explicitly supplied distribution certificate.
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export function androidAssociation(fingerprint) {
  if (typeof fingerprint !== 'string' || !/^(?:[A-F0-9]{2}:){31}[A-F0-9]{2}$/u.test(fingerprint) || fingerprint.length !== 95) {
    throw new Error('EXPECTED_SHA256_CERTIFICATE_FINGERPRINT');
  }
  return [{
    relation: ['delegate_permission/common.handle_all_urls'],
    target: {
      namespace: 'android_app',
      package_name: 'dev.matheusduarte.vanrides.driver',
      sha256_cert_fingerprints: [fingerprint],
    },
  }];
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (process.argv.length !== 3) throw new Error('EXPECTED_ONE_FINGERPRINT');
    process.stdout.write(JSON.stringify(androidAssociation(process.argv[2]), null, 2) + '\n');
  } catch {
    process.stderr.write('Informe o SHA-256 do certificado de distribuição em hexadecimal maiúsculo separado por dois-pontos.\n');
    process.exitCode = 1;
  }
}
