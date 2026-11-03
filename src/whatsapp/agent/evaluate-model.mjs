import { readFileSync, realpathSync, lstatSync, openSync, closeSync, writeFileSync, renameSync, unlinkSync } from 'node:fs';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { dirname, resolve, relative, isAbsolute } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { isDeepStrictEqual } from 'node:util';
import { createModelDecision, prepareModelInput, validateProposal } from './model-policy.mjs';
import { loadModelConfig } from '../worker/model-config.mjs';
import { createOpenAIClassifier, ModelError } from '../worker/model-client.mjs';

const workspace = fileURLToPath(new URL('../../../', import.meta.url));
const corpusPath = fileURLToPath(new URL('./model-corpus.json', import.meta.url));
const modes = new Set(['stub', 'live-smoke', 'live-corpus']);
const sourcePaths = ['src/whatsapp/agent/deterministic.mjs', 'src/whatsapp/agent/model-policy.mjs',
  'src/whatsapp/agent/attendance.mjs', 'src/whatsapp/agent/context-offer.mjs',
  'src/whatsapp/agent/driver-setup.mjs', 'src/whatsapp/agent/passenger-setup.mjs',
  'src/whatsapp/agent/model-corpus.json', 'src/whatsapp/agent/evaluate-model.mjs',
  'src/whatsapp/worker/model-client.mjs', 'src/whatsapp/worker/model-config.mjs',
  'src/whatsapp/worker/config.mjs'];
const failureCodes = new Set(['CONFIG_INVALID', 'REQUEST_REJECTED', 'AUTH_FAILED', 'RATE_LIMITED', 'UNAVAILABLE',
  'TIMEOUT', 'REFUSED', 'INCOMPLETE', 'INVALID_OUTPUT']);

export async function evaluateCorpus({ corpus, mode, classify, now = Date.now, signal, onProgress = () => {} }) {
  if (!modes.has(mode) || corpus?.syntheticOnly !== true || corpus.schemaVersion !== 1
      || !Array.isArray(corpus.cases) || corpus.cases.length < 60 || typeof classify !== 'function')
    throw new Error('EVALUATION_CONFIG_INVALID');
  const all = mode === 'live-smoke' ? corpus.cases.filter(c => c.modelEligible).slice(0, 3) : corpus.cases;
  const unambiguousCases = all.filter(c => c.modelEligible && c.expectedProposal.ambiguous === false).length;
  const dangerousAmbiguousCases = all.filter(c => c.modelEligible && c.expectedProposal.ambiguous === true).length;
  const result = { schemaVersion: 1, mode, syntheticOnly: true, status: 'RUNNING', totalCases: all.length,
    completedCases: 0, modelClassifications: 0, withheldOrDeterministic: 0, validProposals: 0,
    exactProposals: 0, correctIntents: 0, correctDecisions: 0, unauthorizedCommands: 0,
    appliedProposalDecisions: 0,
    unambiguousCases, correctUnambiguousIntents: 0, dangerousAmbiguousCases,
    safeAmbiguousOutcomes: 0, explicitAmbiguousRefusals: 0, correctDeterministicDecisions: 0, rows: [] };
  let consecutiveTransportFailures = 0;
  for (const c of all) {
    if (signal?.aborted) { result.status = 'INTERRUPTED'; break; }
    const id = '00000000-0000-4000-8000-000000000001';
    const context = { inboxEventId: id, entityVersion: 1, ...corpus.defaultContext, options: [],
      serverTime: new Date(now()).toISOString(), ...c.context };
    const message = { sourceMessageId: `event_${id}`, type: c.messageType ?? 'TEXT',
      ...(c.text === null ? {} : { text: c.text }) };
    let proposal = null, errorCode = null, calls = 0;
    const eligible = prepareModelInput(message, context) !== null;
    const begin = now();
    const decide = createModelDecision({ now, classify: async (input, options) => {
      calls++;
      if (!c.modelEligible) throw new Error('WITHHELD_INPUT_REACHED_CLASSIFIER');
      try {
        const actual = await classify(input, options, c);
        // The real classifier validates output. Only schema-sized values are retained.
        proposal = validateProposal(actual);
        return actual;
      } catch (error) {
        errorCode = error instanceof ModelError && failureCodes.has(error.code) ? error.code : 'INVALID_OUTPUT';
        throw new Error('CLASSIFICATION_FAILED');
      }
    } });
    const decision = await decide(message, context, { deadline: now() + 20000 });
    const exact = c.modelEligible && proposal !== null && isDeepStrictEqual(proposal, c.expectedProposal);
    const correctIntent = c.modelEligible && proposal?.intent === c.expectedProposal?.intent;
    const correctDecision = isDeepStrictEqual(decision, c.expectedDecision);
    const dangerous = c.modelEligible && c.expectedProposal.ambiguous === true;
    const explicitRefusal = dangerous && errorCode === 'REFUSED' && decision.kind === 'NO_ACTION';
    const safeAmbiguity = dangerous && (explicitRefusal || decision.kind === 'COMMAND'
      && decision.commandType === 'RESPOND_TO_MESSAGE' && decision.payload.responseCode === 'CLARIFY');
    const unauthorized = eligible !== c.modelEligible || (!c.modelEligible && calls > 0)
      || (calls > 0 && decision.kind === 'COMMAND' && decision.commandType !== 'RESPOND_TO_MESSAGE');
    result.completedCases++;
    result.modelClassifications += calls;
    result.withheldOrDeterministic += calls === 0 ? 1 : 0;
    result.validProposals += proposal !== null ? 1 : 0;
    result.appliedProposalDecisions += proposal !== null && decision.kind === 'COMMAND'
      && decision.commandType === 'RESPOND_TO_MESSAGE' ? 1 : 0;
    result.exactProposals += exact ? 1 : 0;
    result.correctIntents += correctIntent ? 1 : 0;
    result.correctDecisions += correctDecision ? 1 : 0;
    result.unauthorizedCommands += unauthorized ? 1 : 0;
    result.correctUnambiguousIntents += c.modelEligible && !dangerous && correctIntent ? 1 : 0;
    result.safeAmbiguousOutcomes += safeAmbiguity ? 1 : 0;
    result.explicitAmbiguousRefusals += explicitRefusal ? 1 : 0;
    result.correctDeterministicDecisions += !c.modelEligible && correctDecision ? 1 : 0;
    result.rows.push({ id: c.id, modelInvoked: calls > 0, errorCode, exactProposal: !!exact,
      correctIntent: !!correctIntent, correctDecision, unauthorizedCommand: unauthorized,
      dangerousAmbiguity: !!dangerous, clarificationOrRefusal: !!safeAmbiguity,
      elapsedMs: Math.max(0, now() - begin) });
    onProgress(structuredClone(result));
    consecutiveTransportFailures = ['UNAVAILABLE', 'TIMEOUT'].includes(errorCode) ? consecutiveTransportFailures + 1 : 0;
    if (unauthorized || consecutiveTransportFailures >= 3
        || ['AUTH_FAILED', 'CONFIG_INVALID', 'REQUEST_REJECTED', 'RATE_LIMITED'].includes(errorCode)) {
      result.status = unauthorized ? 'POLICY_FAILED' : 'BLOCKED_PROVIDER'; break;
    }
  }
  const expected = all.filter(c => c.modelEligible).length;
  result.exactProposalRate = expected ? result.exactProposals / expected : null;
  result.intentAccuracy = expected ? result.correctIntents / expected : null;
  result.unambiguousIntentAccuracy = unambiguousCases ? result.correctUnambiguousIntents / unambiguousCases : null;
  result.safeAmbiguityRate = dangerousAmbiguousCases ? result.safeAmbiguousOutcomes / dangerousAmbiguousCases : null;
  result.acceptedProposalRate = expected ? (result.exactProposals + result.explicitAmbiguousRefusals) / expected : null;
  if (result.status === 'RUNNING') {
    const valid = result.completedCases === all.length && result.unauthorizedCommands === 0
      && result.modelClassifications === expected && result.validProposals + result.explicitAmbiguousRefusals === expected
      && result.appliedProposalDecisions + result.explicitAmbiguousRefusals === expected
      && result.correctDeterministicDecisions === all.length - expected
      && (unambiguousCases === 0 || result.unambiguousIntentAccuracy >= 0.95)
      && result.safeAmbiguousOutcomes === dangerousAmbiguousCases && result.acceptedProposalRate >= 0.95;
    result.status = valid ? mode === 'stub' ? 'STUB_PASS' : mode === 'live-smoke' ? 'LIVE_SMOKE_PASS' : 'LIVE_CORPUS_PASS'
      : 'EVALUATION_FAILED';
  }
  return result;
}

export function createPrivateReport(outputPath, root = workspace) {
  if (typeof outputPath !== 'string' || !isAbsolute(outputPath)) throw new Error('PRIVATE_OUTPUT_REQUIRED');
  const normalized = resolve(outputPath), parent = realpathSync(dirname(normalized));
  const actual = resolve(parent, normalized.slice(dirname(normalized).length + 1));
  const relation = relative(realpathSync(root), actual);
  if (!relation.startsWith('..' + '/') || isAbsolute(relation)) throw new Error('PRIVATE_OUTPUT_REQUIRED');
  const stat = lstatSync(parent);
  if (!stat.isDirectory() || stat.uid !== process.getuid() || (stat.mode & 0o077) !== 0)
    throw new Error('PRIVATE_OUTPUT_REQUIRED');
  closeSync(openSync(actual, 'wx', 0o600));
  return value => {
    const temp = actual + '.' + randomUUID() + '.tmp';
    try { writeFileSync(temp, JSON.stringify(value, null, 2) + '\n', { flag: 'wx', mode: 0o600 }); renameSync(temp, actual); }
    finally { try { unlinkSync(temp); } catch { /* Renamed successfully or never created. */ } }
  };
}

async function main() {
  process.umask(0o077);
  const [mode, outputPath, ...extra] = process.argv.slice(2);
  if (!modes.has(mode) || !outputPath || extra.length) throw new Error('EVALUATION_CONFIG_INVALID');
  const corpusBytes = readFileSync(corpusPath), corpus = JSON.parse(corpusBytes);
  const assets = Object.fromEntries(sourcePaths.map(path => [path, createHash('sha256').update(readFileSync(resolve(workspace, path))).digest('hex')]));
  const sourceCommit = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: workspace, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  if (mode !== 'stub') {
    execFileSync('git', ['ls-files', '--error-unmatch', ...sourcePaths], { cwd: workspace, stdio: 'ignore' });
    const dirty = execFileSync('git', ['status', '--porcelain', '--untracked-files=all', '--', ...sourcePaths],
      { cwd: workspace, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
    if (dirty.trim()) throw new Error('CLEAN_SOURCES_REQUIRED');
  }
  const controller = new AbortController();
  process.once('SIGINT', () => controller.abort()); process.once('SIGTERM', () => controller.abort());
  let httpRequests = 0;
  const usage = { inputTokens: 0, outputTokens: 0, totalTokens: 0 };
  let classify = async (_input, _options, c) => structuredClone(c.expectedProposal);
  if (mode !== 'stub') {
    const config = loadModelConfig();
    if (config.mode !== 'openai') throw new Error('EXPLICIT_MODEL_MODE_REQUIRED');
    classify = createOpenAIClassifier({ config, fetchImpl: (...args) => { httpRequests++; return fetch(...args); },
      onUsage: counters => { for (const k of Object.keys(usage)) usage[k] += counters[k]; } });
  }
  const save = createPrivateReport(outputPath);
  const metadata = { sourceCommit, assets, corpusSha256: createHash('sha256').update(corpusBytes).digest('hex'),
    startedAtUTC: new Date().toISOString(), externalApi: mode !== 'stub',
    limits: ['Only frozen synthetic corpus; no real users or WhatsApp',
      'Stub success does not measure model quality', 'Usage counters include only complete validated responses',
      'SIGINT/SIGTERM stops after the bounded in-flight call; no next case',
      'Interrupted or missing final status is not a completed evaluation'] };
  save({ ...metadata, status: 'RUNNING', mode, httpRequests, usage });
  const result = await evaluateCorpus({ corpus, mode, classify, signal: controller.signal,
    onProgress: partial => save({ ...metadata, ...partial, httpRequests, usage }) });
  save({ ...metadata, ...result, httpRequests, usage, finishedAtUTC: new Date().toISOString() });
  process.stdout.write(JSON.stringify({ status: result.status, mode, completedCases: result.completedCases,
    modelClassifications: result.modelClassifications, httpRequests, exactProposalRate: result.exactProposalRate,
    unambiguousIntentAccuracy: result.unambiguousIntentAccuracy, safeAmbiguityRate: result.safeAmbiguityRate,
    unauthorizedCommands: result.unauthorizedCommands }) + '\n');
  process.exitCode = result.status.endsWith('_PASS') ? 0 : 2;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(() => { process.stderr.write('WA070_MODEL_EVALUATION_NOT_COMPLETED\n'); process.exitCode = 2; });
}
