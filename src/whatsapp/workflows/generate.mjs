import { mkdirSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = new URL('./', import.meta.url);
const ids = Object.fromEntries(Array.from({ length: 8 }, (_, i) => [`W0${i + 1}`, `vrWa070Flow0000${i + 1}`]));
const versions = { scheduleTrigger: 1.4, httpRequest: 4.5, executeWorkflowTrigger: 1.3, errorTrigger: 1, set: 3.5 };
const names = { W01: 'Conversa', W02: 'Motorista', W03: 'Passageiro', W04: 'Presença', W05: 'Início', W06: 'Envio', W07: 'Suporte', W08: 'Operação' };
const node = (flow, type, name, parameters, index = 0) => ({ id: `${flow}-${index}-${type}`, name,
  type: `n8n-nodes-base.${type}`, typeVersion: versions[type], position: [index * 260, 0], parameters });
const schedule = (flow, seconds) => node(flow, 'scheduleTrigger', 'Agendar', {
  rule: { interval: [{ field: 'seconds', secondsInterval: seconds }] },
});
const trigger = flow => node(flow, 'executeWorkflowTrigger', 'Subfluxo', { inputSource: 'passthrough' });
const http = (flow, path, method = 'POST') => ({ ...node(flow, 'httpRequest', 'Executar operação privada', {
  method, url: `http://vanrides-wa070-worker:7080${path}`, authentication: 'genericCredentialType', genericAuthType: 'httpHeaderAuth',
  ...(method === 'POST' ? { sendBody: true, specifyBody: 'json', jsonBody: '{}' } : {}),
  options: { timeout: flow === 'W05' ? 10000 : 29000, redirect: { redirect: { followRedirects: false } },
    response: { response: { responseFormat: 'json', neverError: false } } },
}, 1), credentials: { httpHeaderAuth: { id: 'vrWa070Control01', name: 'VanRides Controle070' } }, retryOnFail: false });
const fixed = (flow, value, index = 1) => node(flow, 'set', 'Estado sanitizado', {
  mode: 'manual', assignments: { assignments: [{ id: `${flow}-status`, name: 'status', type: 'string', value }] },
  includeOtherFields: false, options: {},
}, index);
const manifest = { schemaVersion: 1, card: 'VR-WA-120-DELIVERY-RECOVERY', n8nVersion: '2.45.4',
  n8nSourceCommit: 'e0c02a617f5e6ede3d6c448aff41f632a782aaca',
  n8nLocalImage: 'docker.n8n.io/n8nio/n8n@sha256:80e23ced901f0e22597d4f3464e3aaf9d835372262ea6f08ef760d8ca35df25c',
  imagePlatform: 'linux/amd64', activation: 'ALL_INACTIVE_REQUIRES_ENVIRONMENT_VALIDATION',
  importOrder: ['W07', 'W02', 'W03', 'W04', 'W05', 'W08', 'W06', 'W01'],
  credentials: [{ id: 'vrWa070Control01', name: 'VanRides Controle070', type: 'httpHeaderAuth',
    headerName: 'Authorization', valueSource: 'PRIVATE_CONTROL_TOKEN', containsNoExportedSecret: true }],
  nodeVersions: versions, workflows: [],
  privacy: { conversationPayloadInN8n: false, deliveryPayloadInN8n: false, executionSaveFlagsDisabled: true,
    note: 'save*none does not prove zero retention: CLI rows and transient queue errors may persist; control outputs contain closed counters only.' },
  limits: ['OpenAI classification belongs to the private worker; n8n never receives model or conversation data',
    'W02, W03 and W05 invoke the common inbox worker; actor authorization and private draft state remain in the API', 'W04 advances bounded attendance jobs; only the device API receives GPS and proves departure',
    'W08 delegates bounded HEALTH_RECONCILE ticks to the private worker; job cursors remain private and retention remains150', 'No real WhatsApp or physical-device proof'] };

mkdirSync(root, { recursive: true });
for (const flow of Object.keys(ids)) {
  let nodes;
  if (flow === 'W01') nodes = [schedule(flow, 5), http(flow, '/v1/process-inbox')];
  else if (['W02', 'W03', 'W05'].includes(flow)) nodes = [trigger(flow), http(flow, '/v1/process-inbox')];
  else if (flow === 'W04') nodes = [schedule(flow, 60), http(flow, '/v1/tick-attendance')];
  else if (flow === 'W06') nodes = [schedule(flow, 2), http(flow, '/v1/dispatch-one')];
  else if (flow === 'W08') nodes = [schedule(flow, 60), http(flow, '/v1/reconcile-delivery')];
  else if (flow === 'W07') nodes = [node(flow, 'errorTrigger', 'Falha de execução', {}), fixed(flow, 'NEEDS_REVIEW'),
    { ...trigger(flow), position: [0, 220] }];
  else throw new Error('UNKNOWN_WORKFLOW');
  // n8n 2.45.4 CLI does not start a Schedule Trigger. This separate internal
  // entry also supports bounded operator checks without activating schedules.
  if (nodes[0].type === 'n8n-nodes-base.scheduleTrigger')
    nodes.push({ ...trigger(flow), position: [0, 220] });
  const connections = { [nodes[0].name]: { main: [[{ node: nodes[1].name, type: 'main', index: 0 }]] } };
  if (nodes.length === 3) connections[nodes[2].name] = { main: [[{ node: nodes[1].name, type: 'main', index: 0 }]] };
  const workflow = { id: ids[flow], name: `VanRides070 · ${flow} ${names[flow]}`, active: false,
    nodes, connections, settings: { executionOrder: 'v1', saveDataErrorExecution: 'none',
      saveDataSuccessExecution: 'none', saveManualExecutions: false, saveExecutionProgress: false,
      executionTimeout: 40, timezone: 'America/Sao_Paulo', callerPolicy: 'workflowsFromAList',
      callerIds: [ids.W01, ids.W08].join(','), ...(flow === 'W07' ? {} : { errorWorkflow: ids.W07 }) },
    staticData: null, pinData: {}, tags: [] };
  const file = `${flow}.json`, content = JSON.stringify(workflow, null, 2) + '\n';
  writeFileSync(new URL(file, root), content);
  manifest.workflows.push({ logicalId: flow, id: ids[flow], file, sha256: createHash('sha256').update(content).digest('hex'),
    active: false, mode: 'IMPLEMENTED_CONTROL',
    prerequisite: null, errorWorkflow: flow === 'W07' ? null : ids.W07,
    acceptsConversationPayload: false });
}
writeFileSync(new URL('manifest.json', root), JSON.stringify(manifest, null, 2) + '\n');
process.stdout.write(`Generated eight inactive workflows in ${fileURLToPath(root)}\n`);
