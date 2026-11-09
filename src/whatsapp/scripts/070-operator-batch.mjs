import fs from 'node:fs';
import { spawn } from 'node:child_process';
import { demand, privateWrite, readPrivate, SmokeFailure } from './070-worker-common.mjs';

const allowed = new Set(['create-test-cohort', 'provision-service-credential',
  'reset-test-data', 'revoke-service-credential']);

// Only the academic harness opts in. Legacy labs retain single-operation CLI calls.
export function createOperatorBatch({ java, input, frozen, work, batch, next }) {
  return async (operations, { signal, onCommitted = () => {} } = {}) => {
    demand(Array.isArray(operations) && operations.length > 0 && operations.length <= 16,
      'OPERATOR_BATCH_INVALID');
    frozen.assertArtifacts(); frozen.assertContainer();
    if (signal?.aborted) throw new SmokeFailure('SMOKE_INTERRUPTED');
    const requestFile = next('operator-batch.json'), cancelFile = signal && next('operator-cancel');
    const entries = operations.map(({ operation, fields = {} }) => {
      demand(allowed.has(operation) && fields && typeof fields === 'object' && !Array.isArray(fields)
        && !['environment', 'operatorCredential', 'batchId', 'operation'].some(key => key in fields),
      'OPERATOR_BATCH_INVALID');
      return { request: { ...input.operator, ...fields, operation, batchId: batch },
        outputFile: next('operator-result.json') };
    });
    const bytes = JSON.stringify({ batchVersion: 1, operations: entries, ...(cancelFile ? { cancelFile } : {}) });
    demand(Buffer.byteLength(bytes) <= 16384, 'OPERATOR_BATCH_INVALID');
    privateWrite(requestFile, bytes);
    const cancel = () => {
      try { privateWrite(cancelFile, 'cancelled\n'); }
      catch { if (!fs.existsSync(cancelFile)) child?.kill('SIGTERM'); }
    };
    let child, failed = false, size = 0;
    const chunks = [];
    signal?.addEventListener('abort', cancel, { once: true });
    const code = await new Promise(resolve => {
      child = spawn(java, ['-Xms24m', '-Xmx192m', '-XX:MaxMetaspaceSize=192m',
        '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=2', '-jar', input.jar,
        '--spring.main.web-application-type=none', '--vanrides.wa.operator.request-file=' + requestFile],
      { cwd: work, env: { ...frozen.env, SPRING_CONFIG_IMPORT: 'file:' + input.configFile + '[.properties]' },
        stdio: ['ignore', 'pipe', 'pipe'] });
      let force;
      const stop = () => {
        failed = true; child.kill('SIGTERM');
        force ??= setTimeout(() => child.kill('SIGKILL'), 5000);
      };
      const timer = setTimeout(stop, 45000);
      const collect = bytes => {
        size += bytes.length;
        if (size <= 512 * 1024) chunks.push(bytes); else stop();
      };
      child.stdout.on('data', collect); child.stderr.on('data', collect);
      child.once('error', () => { failed = true; });
      child.once('close', status => {
        clearTimeout(timer); clearTimeout(force); signal?.removeEventListener('abort', cancel); resolve(status);
      });
      if (signal?.aborted) cancel();
    });
    privateWrite(next('operator.log'), Buffer.concat(chunks));
    let invalid = false;
    const results = entries.map((entry, index) => {
      let result;
      try { result = readPrivate(entry.outputFile); } catch { invalid = true; return null; }
      if (result.status !== 'COMMITTED' || result.operation !== entry.request.operation
          || result.batchId !== batch || result.environment !== input.operator.environment) {
        invalid = true; return null;
      }
      // Retain proven partial commits for cleanup, even when a later operation failed.
      onCommitted(result, index); return result;
    });
    if (signal?.aborted) throw new SmokeFailure('SMOKE_INTERRUPTED');
    demand(code === 0 && !failed, 'OPERATOR_CLI_FAILED');
    demand(!invalid, 'OPERATOR_RESULT_NOT_COMMITTED');
    return results;
  };
}
