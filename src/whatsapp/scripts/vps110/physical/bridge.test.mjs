import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { Bridge, PROFILE, binding, phaseBody, pairingMatch, cleanupCredentials } from './bridge.mjs';
import { shutdownBudget } from './physical_session.mjs';

const nonce = 'A'.repeat(43), jkt = 'E'.repeat(42) + 'A';
const sourceSha = 'a'.repeat(40), jarSha256 = 'b'.repeat(64), moduleSha256 = 'c'.repeat(64);
const pairId = '11111111-1111-4111-8111-111111111111';
const fresh = clock => new Bridge({ attemptId: 'fixture', sourceSha, jarSha256, moduleSha256, ...(clock ? { clock } : {}) });
const headers = b => ({ 'x-wa110-attempt': b.scope.attemptId, 'x-wa110-source-sha': sourceSha,
  'x-wa110-nonce': nonce, 'x-wa110-bridge-token': b.secret });

test('bootstrap pins physical profile/module/JAR and consumes its one opportunity', () => {
  const b = fresh(() => 100000), result = b.bootstrap(nonce);
  assert.equal(result.result.profile, PROFILE); assert.equal(result.result.jarSha256, jarSha256);
  assert.equal(result.result.moduleSha256, moduleSha256); assert.equal(result.nonce, nonce);
  assert.throws(() => b.bootstrap(nonce), /ALREADY_USED/);
  assert.throws(() => b.bootstrap('B'.repeat(42) + 'A'), /ALREADY_USED/);
});
test('recovery before bootstrap creates no credential and cannot enable a journey', () => {
  const b = fresh(); b.recovery(nonce);
  assert.equal(b.secret, null); assert.throws(() => b.bootstrap(nonce), /ALREADY_USED/);
  assert.throws(() => b.recovery('B'.repeat(42) + 'A'), /CONTEXT_MISMATCH/);
});
test('cross-attempt/source/nonce/token and expiry are rejected', () => {
  let now = 0; const b = fresh(() => now); b.bootstrap(nonce); b.authorize(headers(b));
  for (const key of ['x-wa110-attempt','x-wa110-source-sha','x-wa110-nonce','x-wa110-bridge-token']) {
    assert.throws(() => b.authorize({ ...headers(b), [key]: 'foreign' }));
  }
  now = 900001; assert.throws(() => b.authorize(headers(b)), /EXPIRED/);
});
test('pair requires the TLS witness and rejects body injection/reordered phases', () => {
  phaseBody('/pair', { code: 'BCDFGHJK', pairingId: pairId, jkt }, 0);
  assert.throws(() => phaseBody('/pair', { code: 'BCDFGHJK' }, 0));
  for (const body of [{ code:'BCDFGHJK',pairingId:pairId,jkt,sql:'SELECT 1' }, { uri:'https://foreign.invalid' }]) {
    assert.throws(() => phaseBody('/pair', body, 0));
  }
  assert.throws(() => phaseBody('/started', {}, 0), /PHASE_ORDER/);
});
test('concurrent exchange and a lost/failed action cannot advance twice', async () => {
  const b = fresh(); b.bootstrap(nonce); let finish;
  const first = b.exchange('/pair', { code:'BCDFGHJK',pairingId:pairId,jkt }, () => new Promise(resolve => { finish = resolve; }));
  await assert.rejects(b.exchange('/pair', { code:'BCDFGHJK',pairingId:pairId,jkt }, async () => ({})), /CONCURRENT/);
  finish({}); await first; assert.equal(b.step,1);
  await assert.rejects(b.exchange('/intent', {}, async () => { throw new Error('synthetic fixture'); }));
  assert.equal(b.failed,true); assert.equal(b.step,1);
  await assert.rejects(b.exchange('/intent', {}, async () => ({uri:'ignored'})), /FAILED/);
});
test('an authenticated order/body rejection closes the opportunity for a later corrected retry', async () => {
  for (const [route,body] of [['/intent',{}],['/pair',{code:'BCDFGHJK'}]]) {
    const b=fresh();b.bootstrap(nonce);
    await assert.rejects(b.exchange(route,body,async()=>({})));
    assert.equal(b.failed,true);assert.equal(b.step,0);
    await assert.rejects(b.exchange('/pair',{code:'BCDFGHJK',pairingId:pairId,jkt},async()=>({})),/FAILED/);
  }
});
test('pairing matches code HMAC, exact Mac JKT/id, PKCE, environment and readiness window', () => {
  const key = Buffer.alloc(32,7), body = {code:'BCDFGHJK',pairingId:pairId,jkt}, now=100000;
  const row = {id:pairId,jkt,codeHash:crypto.createHmac('sha256',key).update('pairing-code\0'+body.code).digest('hex'),
    pkce:'A'.repeat(43),platform:'ANDROID',state:'PENDING',environment:'wa070-local',driverId:null,
    createdAt:new Date(95000).toISOString(),expiresAt:new Date(120000).toISOString()};
  assert.deepEqual(pairingMatch([row],body,key,now,90000),{id:pairId,jkt});
  const changes=[{jkt:'A'.repeat(43)},{id:'22222222-2222-4222-8222-222222222222'},{codeHash:'0'.repeat(64)},
    {pkce:'invalid'},{driverId:pairId},{environment:'production'},{state:'REDEEMED'},{platform:'IOS'},
    {createdAt:new Date(80000).toISOString()},{expiresAt:new Date(90000).toISOString()}];
  for(const change of changes) assert.throws(()=>pairingMatch([{...row,...change}],body,key,now,90000));
  assert.throws(()=>pairingMatch([row,row],body,key,now,90000),/NOT_UNIQUE/);
});
test('lost operator receipt discovery stays restricted to batch/instance/environment and four principals', () => {
  const scope={batch:pairId,instance:'wa070_synthetic_'+'1'.repeat(32)};
  const row={id:pairId,principal:'CHANNEL',environment:'wa070-local',instance:scope.instance,cohortId:scope.batch,synthetic:true};
  assert.deepEqual(cleanupCredentials([row],scope),[pairId]);
  for(const change of [{environment:'production'},{instance:'foreign'},{cohortId:'foreign'},{synthetic:false},{principal:'OPERATOR'}]) {
    assert.throws(()=>cleanupCredentials([{...row,...change}],scope));
  }
  assert.throws(()=>cleanupCredentials([row,row],scope));
});
test('context must be canonical and a closed bridge loses its credential', () => {
  assert.throws(()=>binding('foreign/attempt',sourceSha,nonce));
  const b=fresh();b.bootstrap(nonce);const h=headers(b);b.close();assert.equal(b.secret,null);
  assert.throws(()=>b.authorize(h),/CLOSED/);
});
test('shutdown shares one nonrenewable monotonic deadline rather than summing phase allowances',()=>{
  let now=1000;const budget=shutdownBudget(()=>now);
  assert.equal(budget.remainingMs(),280000);now+=180000;assert.equal(budget.remainingMs(),100000);
  now+=60000;assert.equal(budget.remainingMs(),40000);now+=75000;assert.equal(budget.remainingMs(),0);
});
