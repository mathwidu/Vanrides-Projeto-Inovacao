import { loadConfig } from './config.mjs';
import { createLiveIngressServer } from './server.mjs';

try {
  const config = loadConfig(process.env.WA120_INGRESS_CONFIG_FILE);
  const log = record => process.stdout.write(JSON.stringify(record) + '\n');
  const server = createLiveIngressServer({ config, log });
  server.on('error', () => { log({ code: 'LISTEN_FAILED' }); process.exitCode = 1; server.shutdown(); });
  server.listen(config.listenPort, config.listenHost, () => log({ code: 'READY' }));
  for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => { log({ code: 'STOPPED' }); server.shutdown(); });
} catch {
  process.stderr.write('{"code":"CONFIG_INVALID"}\n');
  process.exitCode = 1;
}
