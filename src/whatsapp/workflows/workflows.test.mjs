import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';

const read = name => readFileSync(new URL(name, import.meta.url), 'utf8');
const manifest = JSON.parse(read('./manifest.json'));
const workflows = manifest.workflows.map(row => ({ row, data: JSON.parse(read(row.file)), raw: read(row.file) }));
test('eight workflows are inactive, with immutable logical hashes and existing error references', () => {
  assert.equal(workflows.length, 8);
  const ids = new Set(workflows.map(w => w.data.id)); assert.equal(ids.size, 8);
  for (const { row, data, raw } of workflows) {
    assert.equal(data.active, false); assert.equal(data.id, row.id);
    assert.equal(createHash('sha256').update(raw).digest('hex'), row.sha256);
    if (data.settings.errorWorkflow) assert.ok(ids.has(data.settings.errorWorkflow));
    for (const id of data.settings.callerIds.split(',')) assert.ok(ids.has(id));
    const nodeNames = new Set(data.nodes.map(n => n.name));
    for (const [name, outputs] of Object.entries(data.connections)) {
      assert.ok(nodeNames.has(name));
      for (const output of outputs.main.flat()) assert.ok(nodeNames.has(output.node));
    }
  }
  assert.equal(manifest.importOrder[0], 'W07');
});
test('n8n receives only control calls, never conversation inputs or delivery secrets', () => {
  for (const { data, raw } of workflows) {
    assert.deepEqual(data.pinData, {});
    for (const field of ['text', 'recipient', 'inboxLease', 'leaseToken', 'attemptToken', 'latitude', 'longitude', 'apikey', 'cursor', 'nextCursor'])
      assert.equal(new RegExp(`"${field}"\\s*:`).test(raw), false);
    for (const n of data.nodes.filter(n => n.type === 'n8n-nodes-base.httpRequest')) {
      assert.equal(n.retryOnFail, false);
      assert.match(n.parameters.url, /^http:\/\/vanrides-wa070-worker:7080\/v1\/(process-inbox|dispatch-one|tick-attendance|reconcile-delivery)$/);
      assert.equal(n.parameters.options.timeout, data.id.endsWith('5') ? 10000 : 29000);
      assert.equal(n.credentials.httpHeaderAuth.id, manifest.credentials[0].id);
      assert.equal(n.parameters.method, 'POST');
      assert.equal(n.parameters.jsonBody, '{}');
      assert.equal(JSON.stringify(n).includes('$json'), false);
    }
    assert.equal(data.settings.saveDataErrorExecution, 'none');
    assert.equal(data.settings.saveDataSuccessExecution, 'none');
    assert.equal(data.settings.saveManualExecutions, false);
    assert.equal(data.settings.saveExecutionProgress, false);
  }
});
for (const flow of ['W02', 'W03', 'W05']) test(`${flow} delegates to the common private worker with no actor, text, draft or schedule`, () => {
  const { row, data } = workflows.find(w => w.row.logicalId === flow);
  assert.equal(row.mode, 'IMPLEMENTED_CONTROL'); assert.equal(row.prerequisite, null);
  assert.equal(data.nodes.length, 2);
  assert.equal(data.nodes[0].type, 'n8n-nodes-base.executeWorkflowTrigger');
  assert.equal(data.nodes[1].parameters.url, 'http://vanrides-wa070-worker:7080/v1/process-inbox');
  assert.equal(data.nodes[1].parameters.jsonBody, '{}');
  assert.equal(data.nodes.some(n => n.type === 'n8n-nodes-base.scheduleTrigger'), false);
});
test('scheduled flows have the same bounded operation available to the native CLI', () => {
  for (const { data } of workflows.filter(w => w.data.nodes.some(n => n.type === 'n8n-nodes-base.scheduleTrigger'))) {
    const schedule = data.nodes.find(n => n.type === 'n8n-nodes-base.scheduleTrigger');
    const internal = data.nodes.find(n => n.type === 'n8n-nodes-base.executeWorkflowTrigger');
    assert.ok(internal);
    assert.deepEqual(data.connections[internal.name], data.connections[schedule.name]);
    assert.equal(data.settings.callerPolicy, 'workflowsFromAList');
  }
});
test('error workflow removes original error data from its output and never sends it elsewhere', () => {
  const data = workflows.find(w => w.row.logicalId === 'W07').data;
  assert.equal(data.settings.errorWorkflow, undefined);
  const fixed = data.nodes.find(n => n.type === 'n8n-nodes-base.set');
  assert.equal(fixed.parameters.includeOtherFields, false);
  assert.equal(fixed.parameters.assignments.assignments[0].value, 'NEEDS_REVIEW');
  assert.equal(data.nodes.some(n => n.type === 'n8n-nodes-base.httpRequest'), false);
});

test('W04 wakes the private attendance controller once a minute without business input', () => {
  const { row, data } = workflows.find(w => w.row.logicalId === 'W04');
  assert.equal(row.mode, 'IMPLEMENTED_CONTROL');
  assert.equal(row.prerequisite, null);
  assert.deepEqual(data.nodes[0].parameters.rule.interval, [{ field: 'minutes', minutesInterval: 1 }]);
  assert.equal(data.nodes[1].parameters.url, 'http://vanrides-wa070-worker:7080/v1/tick-attendance');
  assert.equal(data.nodes[1].parameters.jsonBody, '{}');
});

test('W08 reconciles delivery once a minute through the private worker with no business input or retry', () => {
  const { row, data } = workflows.find(w => w.row.logicalId === 'W08');
  assert.equal(manifest.card, 'VR-WA-120-DELIVERY-RECOVERY');
  assert.equal(row.mode, 'IMPLEMENTED_CONTROL');
  assert.equal(row.prerequisite, null);
  assert.deepEqual(data.nodes[0].parameters.rule.interval, [{ field: 'minutes', minutesInterval: 1 }]);
  assert.equal(data.nodes[1].parameters.url, 'http://vanrides-wa070-worker:7080/v1/reconcile-delivery');
  assert.equal(data.nodes[1].parameters.method, 'POST');
  assert.equal(data.nodes[1].parameters.jsonBody, '{}');
  assert.equal(data.nodes[1].retryOnFail, false);
});

test('Schedule Trigger 1.4 exports use supported interval ranges', () => {
  for (const { data } of workflows) for (const node of data.nodes.filter(n => n.type === 'n8n-nodes-base.scheduleTrigger')) {
    for (const interval of node.parameters.rule.interval) {
      assert.ok(['seconds', 'minutes'].includes(interval.field));
      const value = interval.field === 'seconds' ? interval.secondsInterval : interval.minutesInterval;
      assert.ok(Number.isInteger(value) && value >= 1 && value <= 59, `${data.id}: interval outside Schedule Trigger range`);
    }
  }
});
