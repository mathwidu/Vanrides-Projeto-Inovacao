import test from 'node:test';
import assert from 'node:assert/strict';
import { serialProvisionPlan as serialProvisionPrototype } from './110-run-phase-contract.mjs';

const uuid = n => '00000000-0000-4000-8000-' + String(n).padStart(12, '0');
function fixture() {
  let now = Date.parse('2026-11-01T04:00:00Z'), running = false, pressure = 1, frozen = true;
  const binding = { sourceSha: 'a'.repeat(40), backendTree: 'b'.repeat(40),
    ...Object.fromEntries(['jarSha256','assetsSha256','configSha256','operatorInputSha256','apkSha256','caSha256'].map((name, i) => [name, String(i + 1).repeat(64)])) };
  const context = { input: { operator: { environment: 'wa070-local' } }, batch: uuid(1), actor: uuid(2), aliasId: uuid(3),
    instance: 'wa070_synthetic_' + uuid(1).replaceAll('-', ''), address: '000000000000000@s.whatsapp.net',
    peers: [{ actor: uuid(4), aliasId: uuid(5), address: '00000000000000@s.whatsapp.net' },
      { actor: uuid(6), aliasId: uuid(7), address: '0000000000000@s.whatsapp.net' }], credentials: [], attendance: true,
    interruption: { assertActive() {} } };
  const calls = [], claims = [], scopedCredentials = new Set(), rawTools = {
    operate(operation, fields = {}) {
      assert.equal(running, false, 'An operator Spring process cannot overlap the API');
      calls.push({ operation, ...fields });
      const common = { status: 'COMMITTED', operation, environment: 'wa070-local', batchId: context.batch, resourceId: uuid(100 + calls.length) };
      if (operation === 'provision-service-credential') {
        scopedCredentials.add(uuid(100 + calls.length));
        return { ...common, ...fields, credentialId: uuid(100 + calls.length), credential: Buffer.alloc(32, calls.length).toString('base64url') };
      }
      if (operation === 'driver-bootstrap') return { ...common, ...fields, driverInviteCode: Buffer.alloc(32, 91).toString('base64url') };
      return common;
    }, sql() {}, count() { return 0; },
  };
  const gates = {
    assertFrozen() { assert.equal(frozen, true, 'frozen evidence changed'); },
    assertApiStopped() { assert.equal(running, false, 'API must be absent'); },
    pressure() { return pressure; },
    assertOwnedApi(pid) { assert.equal(running, true); assert.equal(pid, 1000); },
    assertScopedTechnicalCredential(id, scope) {
      assert.equal(scopedCredentials.has(id), true, 'CLEANUP_OUT_OF_SCOPE');
      assert.deepEqual(scope, { batchId: context.batch, instance: context.instance, environment: 'wa070-local' });
    },
  };
  return { binding, context, rawTools, gates, calls, claims, clock: () => now,
    setRunning(v) { running = v; }, setPressure(v) { pressure = v; }, setFrozen(v) { frozen = v; }, advance(ms) { now += ms; },
    make() { return serialProvisionPrototype({ binding, context, rawTools, gates, clock: () => now }); } };
}
async function ready() { const f = fixture(), plan = f.make(); await plan.prepare(); return { ...f, plan }; }
function enter(f) { f.setRunning(true); f.plan.enterFunctional(f.binding, 1000); }
function request(f, identityId = f.context.actor) { return { identityId, expiresAt: new Date(f.clock() + 1_800_000).toISOString() }; }

test('current provision performs five CLI operations, then one real bootstrap for exactly the driver actor, before API', async () => {
  const f = await ready();
  assert.equal(f.calls.length, 6); assert.equal(f.calls[5].operation, 'driver-bootstrap');
  assert.equal(f.calls[5].identityId, f.context.actor);
  assert.equal(f.context.credentials.length, 4); assert.equal(f.plan.audit().phase, 'PREPARED');
});
test('normal bootstrap operation consumes the exact pre-issued code once without another CLI', async () => {
  const f = await ready(); enter(f); f.advance(1000);
  const response = f.plan.liveOperate('driver-bootstrap', request(f));
  assert.equal(response.driverInviteCode, Buffer.alloc(32, 91).toString('base64url'));
  assert.equal(response.identityId, f.context.actor); assert.equal(f.calls.length, 6);
  assert.throws(() => f.plan.liveOperate('driver-bootstrap', request(f)), /BOOTSTRAP_CONSUMPTION_NOT_BOUND/);
});
test('wrong actor and an extra field cannot consume the invitation', async () => {
  const f = await ready(); enter(f);
  assert.throws(() => f.plan.liveOperate('driver-bootstrap', request(f, f.context.peers[0].actor)), /NOT_BOUND/);
  assert.throws(() => f.plan.liveOperate('driver-bootstrap', { ...request(f), forged: true }), /NOT_BOUND/);
  assert.equal(f.plan.audit().bootstrapConsumedOnce, false);
});
test('expired invitation cannot enter the functional phase; requested lease cannot extend the normal 30 minutes', async () => {
  const f = await ready(); f.advance(1_800_001); f.setRunning(true);
  assert.throws(() => f.plan.enterFunctional(f.binding, 1000), /EXPIRED/);
  const g = await ready(); enter(g);
  assert.throws(() => g.plan.liveOperate('driver-bootstrap', { identityId: g.context.actor, expiresAt: new Date(g.clock() + 1_800_001).toISOString() }), /EXPIRY_INVALID/);
});
test('changed SHA, JAR, configuration, operator file, source assets, APK or CA cannot enter API phase', async () => {
  for (const key of ['sourceSha','jarSha256','backendTree','configSha256','operatorInputSha256','assetsSha256','apkSha256','caSha256']) {
    const f = await ready(); f.setRunning(true);
    assert.throws(() => f.plan.enterFunctional({ ...f.binding, [key]: 'changed' }, 1000), /BINDING_CHANGED/);
  }
});
test('no operator call, including cleanup, is allowed while API is alive', async () => {
  const f = await ready(); enter(f);
  assert.throws(() => f.plan.liveOperate('reset-test-data', {}), /OPERATOR_WHILE_API_LIVE/);
  assert.throws(() => f.plan.enterCleanup(), /API must be absent/);
  assert.equal(f.calls.length, 6);
});
test('after owned API stop, original reset and four revocations run with the same cohort; outside targets rejected', async () => {
  const f = await ready(); enter(f); f.plan.liveOperate('driver-bootstrap', request(f));
  f.setRunning(false); f.plan.enterCleanup(); f.plan.cleanupOperate('reset-test-data');
  for (const credential of f.context.credentials) f.plan.cleanupOperate('revoke-service-credential', { credentialId: credential.credentialId, reasonCode: 'SYNTHETIC_SMOKE_COMPLETED' });
  assert.equal(f.calls.length, 11);
  assert.throws(() => f.plan.cleanupOperate('revoke-service-credential', { credentialId: uuid(999), reasonCode: 'SYNTHETIC_SMOKE_COMPLETED' }), /OUT_OF_SCOPE/);
});
test('memory warning and already-running API reject provisioning before any mutation', async () => {
  const f = fixture(); f.setPressure(2); await assert.rejects(f.make().prepare(), /HOST_MEMORY_PRESSURE/); assert.equal(f.calls.length, 0);
  const g = fixture(); g.setRunning(true); await assert.rejects(g.make().prepare(), /API must be absent/); assert.equal(g.calls.length, 0);
});
test('partial provisioning failure remains failed and permits only scoped cleanup after API absence', async () => {
  const f = fixture(), original = f.rawTools.operate;
  f.rawTools.operate = (operation, fields) => {
    if (f.calls.length === 3) throw new Error('SYNTHETIC_CLI_INTERRUPTED');
    return original(operation, fields);
  };
  const p = f.make(); await assert.rejects(p.prepare(), /SYNTHETIC_CLI_INTERRUPTED/);
  assert.equal(p.audit().phase, 'FAILED'); assert.equal(f.context.credentials.length, 2);
  p.enterCleanup(); f.rawTools.operate = original; p.cleanupOperate('reset-test-data');
  assert.throws(() => p.enterFunctional(f.binding, 1000), /PROVISION_NOT_COMPLETE/);
});
test('a committed issuance with lost acknowledgement can be discovered and revoked under the same scoped database proof', async () => {
  const f = fixture(), original = f.rawTools.operate;
  f.rawTools.operate = (operation, fields) => {
    const response = original(operation, fields);
    if (f.calls.length === 4) throw new Error('ACK_LOST_AFTER_COMMIT');
    return response;
  };
  const p = f.make(); await assert.rejects(p.prepare(), /ACK_LOST_AFTER_COMMIT/);
  assert.equal(f.context.credentials.length, 2); // Third real issuance is absent from the in-memory acknowledgements.
  p.enterCleanup(); f.rawTools.operate = original;
  p.cleanupOperate('revoke-service-credential', { credentialId: uuid(104), reasonCode: 'SYNTHETIC_SMOKE_COMPLETED' });
  assert.equal(p.audit().phase, 'CLEANUP');
});
test('audit is sanitized, has no invite/token/actors or proof of physical success', async () => {
  const f = await ready(); enter(f); const response = f.plan.liveOperate('driver-bootstrap', request(f));
  const text = JSON.stringify(f.plan.audit());
  for (const secret of [response.driverInviteCode, f.context.actor, ...f.context.credentials.map(c => c.credential)]) assert.equal(text.includes(secret), false);
  assert.equal(f.plan.audit().profile, 'NATIVE_SERIAL_PROVISIONING_V1'); assert.equal('physicalAndroid' in f.plan.audit(), false);
});
test('restored private checkpoint consumes the same real invitation without provisioning again', async()=>{
  const f=await ready(), checkpoint=f.plan.privateCheckpoint();
  const plan=serialProvisionPrototype({binding:f.binding,context:f.context,rawTools:f.rawTools,gates:f.gates,clock:f.clock,checkpoint});
  f.setRunning(true);plan.enterFunctional(f.binding,1000);
  assert.equal(plan.liveOperate('driver-bootstrap',request(f)).driverInviteCode,checkpoint.invitation.driverInviteCode);
  assert.equal(f.calls.length,6);
});
test('cohort mutation after provisioning cannot exchange the driver actor',async()=>{
  const f=await ready();f.context.actor=f.context.peers[0].actor;f.setRunning(true);
  assert.throws(()=>f.plan.enterFunctional(f.binding,1000),/PHASE_COHORT_CHANGED/);
});
test('PREPARED output without COMMITTED never authorizes the API phase',async()=>{
  const f=fixture(),original=f.rawTools.operate;
  f.rawTools.operate=(...args)=>({...original(...args),status:'PREPARED'});
  const plan=f.make();await assert.rejects(plan.prepare(),/OPERATOR_NOT_COMMITTED/);
  assert.equal(f.calls.length,1);assert.equal(plan.audit().phase,'FAILED');
});
