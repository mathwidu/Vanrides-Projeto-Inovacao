import { loadConfig } from './config.mjs';
import { createApiClient } from './http-client.mjs';
import { createDispatcher } from './dispatch.mjs';
import { createInboxProcessor } from './process-inbox.mjs';
import { createAttendanceTicker } from './attendance-jobs.mjs';
import { createDeliveryTicker } from './delivery-jobs.mjs';
import { createWorkerServer } from './server.mjs';
import { loadModelConfig } from './model-config.mjs';
import { createOpenAIClassifier } from './model-client.mjs';
import { createModelDecision } from '../agent/model-policy.mjs';

try {
  const config = loadConfig();
  const modelConfig = loadModelConfig();
  const decide = modelConfig.mode === 'openai'
    ? createModelDecision({ classify: createOpenAIClassifier({ config: modelConfig }) }) : undefined;
  const options = { baseUrl: config.backendBaseUrl, localMode: config.localMode, allowedHttpHosts: config.allowedHttpHosts };
  const conversationApi = createApiClient({ ...options, token: config.conversationToken });
  const deliveryApi = createApiClient({ ...options, token: config.deliveryToken });
  const jobApi = config.jobToken && createApiClient({ ...options, token: config.jobToken });
  const server = createWorkerServer({ config,
    dispatchOne: createDispatcher({ api: deliveryApi, config }),
    processInbox: createInboxProcessor({ api: conversationApi, decide }),
    tickAttendance: jobApi && createAttendanceTicker({ api: jobApi }),
    reconcileDelivery: jobApi && createDeliveryTicker({ api: jobApi }),
  });
  server.on('error', () => { process.stderr.write('WA070_START_FAILED\n'); process.exitCode = 1; });
  server.listen(config.port, config.host, () => process.stdout.write('WA070_READY\n'));
  let closing = false;
  const shutdown = () => {
    if (closing) return;
    closing = true;
    // Finish the already authorized attempt even when the caller disconnected.
    server.close(() => { process.exitCode = 0; });
    server.closeIdleConnections();
    setTimeout(() => process.exit(0), 27000).unref();
  };
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
} catch {
  process.stderr.write('WA070_CONFIG_INVALID\n');
  process.exitCode = 1;
}
