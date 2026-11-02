import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, mkdtempSync, mkdirSync, existsSync, rmSync, symlinkSync, chmodSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { evaluateCorpus, createPrivateReport } from './evaluate-model.mjs';
import { ModelError } from '../worker/model-client.mjs';

const corpus = JSON.parse(readFileSync(new URL('./model-corpus.json', import.meta.url)));
const correct = async (_input, _options, c) => structuredClone(c.expectedProposal);
test('stub corpus evaluates all policy rows with zero claim of live model quality', async () => {
  const result = await evaluateCorpus({ corpus, mode: 'stub', classify: correct });
  assert.equal(result.status, 'STUB_PASS'); assert.equal(result.mode, 'stub');
  assert.equal(result.completedCases, corpus.cases.length); assert.equal(result.unauthorizedCommands, 0);
  assert.equal(result.modelClassifications, corpus.cases.filter(c => c.modelEligible).length);
  assert.equal(result.exactProposalRate, 1);
  assert.equal(JSON.stringify(result).includes('Como isso funciona'), false);
});
test('live smoke is limited to three synthetic eligible rows', async () => {
  const result = await evaluateCorpus({ corpus, mode: 'live-smoke', classify: correct });
  assert.equal(result.status, 'LIVE_SMOKE_PASS'); assert.equal(result.completedCases, 3); assert.equal(result.modelClassifications, 3);
});
test('provider authentication failure is reported safely and stops after first classification', async () => {
  let calls = 0;
  const result = await evaluateCorpus({ corpus, mode: 'live-corpus', classify: async () => { calls++; throw new ModelError('AUTH_FAILED'); } });
  assert.equal(result.status, 'BLOCKED_PROVIDER'); assert.equal(calls, 1);
  assert.equal(result.rows[0].errorCode, 'AUTH_FAILED'); assert.equal(result.validProposals, 0);
});
test('three consecutive transport failures stop the corpus without pretending to complete it', async () => {
  let calls = 0;
  const result = await evaluateCorpus({ corpus, mode: 'live-corpus', classify: async () => { calls++; throw new ModelError('UNAVAILABLE'); } });
  assert.equal(result.status, 'BLOCKED_PROVIDER'); assert.equal(calls, 3); assert.equal(result.completedCases, 3);
});
test('malformed output is not counted as a valid proposal or written into the report', async () => {
  const result = await evaluateCorpus({ corpus, mode: 'live-smoke', classify: async () => ({ secret: 'PRIVATE_SENTINEL', actorId: 'forged' }) });
  assert.equal(result.status, 'EVALUATION_FAILED'); assert.equal(result.validProposals, 0);
  assert.equal(result.unauthorizedCommands, 0); assert.equal(JSON.stringify(result).includes('PRIVATE_SENTINEL'), false);
});
test('wrong classification fails quality even when it emits only an authorized response', async () => {
  const result = await evaluateCorpus({ corpus, mode: 'live-smoke', classify: async () => ({
    intent: 'DELETE', attendance: null, day: null, direction: null, ambiguous: false,
  }) });
  assert.equal(result.status, 'EVALUATION_FAILED'); assert.equal(result.correctIntents, 0); assert.equal(result.unauthorizedCommands, 0);
});
test('aggregate accuracy and shared FEATURE_PENDING mapping cannot hide errors in unambiguous intents', async () => {
  const wrong = new Set(['model-019', 'model-020', 'model-021', 'model-022']);
  const result = await evaluateCorpus({ corpus, mode: 'live-corpus', classify: async (input, options, c) => wrong.has(c.id)
    ? { intent: 'START_TRACKING', attendance: null, day: null, direction: null, ambiguous: false } : correct(input, options, c) });
  assert.ok(result.intentAccuracy >= 0.95); assert.equal(result.correctDecisions, corpus.cases.length);
  assert.ok(result.unambiguousIntentAccuracy < 0.95); assert.equal(result.status, 'EVALUATION_FAILED');
});
test('a single dangerous ambiguous message answered as a different intent fails the safety subset', async () => {
  const target = corpus.cases.find(c => c.modelEligible && c.expectedProposal.ambiguous).id;
  const result = await evaluateCorpus({ corpus, mode: 'live-corpus', classify: async (input, options, c) => c.id === target
    ? { intent: 'HELP', attendance: null, day: null, direction: null, ambiguous: false } : correct(input, options, c) });
  assert.ok(result.exactProposalRate >= 0.95); assert.ok(result.safeAmbiguityRate < 1);
  assert.equal(result.status, 'EVALUATION_FAILED');
});
test('explicit model refusals are acceptable only for dangerous ambiguous cases', async () => {
  const result = await evaluateCorpus({ corpus, mode: 'live-corpus', classify: async (input, options, c) => {
    if (c.expectedProposal.ambiguous) throw new ModelError('REFUSED');
    return correct(input, options, c);
  } });
  assert.equal(result.status, 'LIVE_CORPUS_PASS'); assert.equal(result.safeAmbiguityRate, 1);
  assert.equal(result.explicitAmbiguousRefusals, result.dangerousAmbiguousCases);
  const bad = await evaluateCorpus({ corpus, mode: 'live-smoke', classify: async () => { throw new ModelError('REFUSED'); } });
  assert.equal(bad.status, 'EVALUATION_FAILED');
});
test('interruption preserves completed rows and does not start the next case', async () => {
  const controller = new AbortController(); let calls = 0;
  const result = await evaluateCorpus({ corpus, mode: 'live-corpus', signal: controller.signal,
    classify: async (...args) => { calls++; return correct(...args); }, onProgress: () => controller.abort() });
  assert.equal(result.status, 'INTERRUPTED'); assert.equal(calls, 1); assert.equal(result.completedCases, 1);
});
test('a correct but late proposal discarded by policy cannot approve the evaluation', async () => {
  let current = Date.now();
  const result = await evaluateCorpus({ corpus, mode: 'live-smoke', now: () => current, classify: async (...args) => {
    current += 20001; return correct(...args);
  } });
  assert.equal(result.exactProposalRate, 1); assert.equal(result.appliedProposalDecisions, 0);
  assert.equal(result.status, 'EVALUATION_FAILED');
});
test('private report refuses repository, traversal, symlink-to-repo, existing output and loose directory', t => {
  const dir = mkdtempSync(join(tmpdir(), 'wa070-evaluation-'));
  t.after(() => rmSync(dir, { force: true, recursive: true }));
  const repo = join(dir, 'repo'), outside = join(dir, 'private'); mkdirSync(repo, { mode: 0o700 }); mkdirSync(outside, { mode: 0o700 });
  for (const destination of [join(repo, 'result.json'), join(outside, '..', 'repo', 'result.json')]) {
    assert.throws(() => createPrivateReport(destination, repo)); assert.equal(existsSync(join(repo, 'result.json')), false);
  }
  symlinkSync(repo, join(outside, 'link'));
  assert.throws(() => createPrivateReport(join(outside, 'link', 'result.json'), repo));
  chmodSync(outside, 0o755); assert.throws(() => createPrivateReport(join(outside, 'result.json'), repo));
  chmodSync(outside, 0o700);
  const file = join(outside, 'result.json'), save = createPrivateReport(file, repo);
  save({ status: 'RUNNING' }); save({ status: 'STUB_PASS' });
  assert.equal(statSync(file).mode & 0o777, 0o600);
  assert.deepEqual(JSON.parse(readFileSync(file)), { status: 'STUB_PASS' });
  assert.throws(() => createPrivateReport(file, repo));
});
