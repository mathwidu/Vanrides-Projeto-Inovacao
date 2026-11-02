import { createChallenge, readPrivateJson, writeChallenge, claimChallenge, createBootstrapServer, writePendingCandidate } from './bootstrap.mjs';

const emit = code => process.stdout.write(JSON.stringify({ code }) + '\n');
try {
  const command = process.argv[2], file = process.env.WA120_BOOTSTRAP_CHALLENGE_FILE;
  if (command === 'prepare') {
    const config = createChallenge(readPrivateJson(process.env.WA120_BOOTSTRAP_SPEC_FILE));
    writeChallenge(file, config); emit('CHALLENGE_CREATED');
  } else if (command === 'collect') {
    const config = claimChallenge(file);
    const server = createBootstrapServer({ config, log: item => emit(item.code) });
    let stopped = false, timer;
    const stop = () => {
      if (stopped) return;
      stopped = true; clearTimeout(timer);
      const candidate = server.shutdown();
      try {
        if (candidate) { writePendingCandidate(config.candidateFile, candidate); emit('CANDIDATE_WRITTEN_PENDING_HUMAN_CONFIRMATION'); }
        else emit('NO_CANDIDATE');
      } catch { emit('PRIVATE_WRITE_FAILED'); process.exitCode = 1; }
      emit('COLLECTOR_STOPPED');
    };
    server.on('error', () => { emit('LISTEN_FAILED'); process.exitCode = 1; stop(); });
    server.listen(config.listenPort, config.listenHost, () => emit('COLLECTOR_READY'));
    timer = setTimeout(stop, Math.max(1, config.expires - Date.now()));
    for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, stop);
  } else throw new Error('BOOTSTRAP_INVALID');
} catch {
  process.stderr.write('{"code":"BOOTSTRAP_INVALID"}\n');
  process.exitCode = 1;
}
