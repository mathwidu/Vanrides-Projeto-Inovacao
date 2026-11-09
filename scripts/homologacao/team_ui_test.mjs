import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { randomUUID } from 'node:crypto';
const source = readFileSync(new URL('../../src/backend/src/main/resources/static/equipe/app.js', import.meta.url), 'utf8');
const session = { login: 'synthetic-user', csrfToken: 'c'.repeat(43) };
const response = (data, status = 200) => ({ ok: status >= 200 && status < 300, status, json: async () => data });
const flush = () => new Promise(resolve => setImmediate(resolve));
async function page(handler) {
  const nodes = new Map(), calls = [];
  const node = id => {
    if (!nodes.has(id)) nodes.set(id, { value: '', checked: false, hidden: true, disabled: false, textContent: '',
      classList: { toggle() {} }, events: {}, addEventListener(event, fn) { this.events[event] = fn; } });
    return nodes.get(id);
  };
  node('role').value = 'driver'; node('phone').value = '+15550000001'; node('consent').checked = true;
  runInNewContext(source, { document: { getElementById: node }, URLSearchParams, crypto: { randomUUID },
    window: { location: { search: '' }, confirm: () => true }, navigator: { clipboard: { writeText: async () => {} } },
    fetch: async (url, options) => {
      calls.push({ url, options });
      if (url.endsWith('/session')) return response(session);
      return handler(url, options);
    } });
  await flush();
  return { node, calls, submit: async () => { node('participant-form').events.submit({ preventDefault() {} }); await flush(); } };
}
const result = (id, extras = {}) => ({ admissionStatus: 'ADMITTED', inviteStatus: 'ACTIVE', operationId: id,
  inviteId: 'synthetic-invite', driverInviteCode: 'a'.repeat(43), expiresAt: '2026-11-10T12:00:00Z', ...extras });

test('lost response retries the same operation, with CSRF and no redirect', async () => {
  let count = 0;
  const ui = await page(async (url, options) => {
    if (++count === 1) throw Error('Rede indisponível');
    return response(result(JSON.parse(options.body).operationId));
  });
  await ui.submit();
  assert.equal(ui.node('phone').disabled, true);
  assert.equal(ui.node('submit').textContent, 'Tentar novamente');
  await ui.submit();
  const mutations = ui.calls.filter(call => call.url.endsWith('/participants'));
  assert.equal(mutations.length, 2); assert.equal(mutations[0].options.body, mutations[1].options.body);
  assert.equal(mutations[1].options.headers['X-CSRF-Token'], session.csrfToken);
  assert.equal(mutations[1].options.redirect, 'error');
  assert.equal(ui.node('phone').disabled, false); assert.equal(ui.node('invitation').hidden, false);
  assert.equal(ui.node('renew').hidden, true);
});

test('malformed or unrelated success never clears a pending operation', async () => {
  for (const body of [{}, result('wrong-operation')]) {
    const ui = await page(async () => response(body)); await ui.submit();
    assert.equal(ui.node('phone').disabled, true);
    assert.equal(ui.node('submit').textContent, 'Tentar novamente');
    assert.equal(ui.node('invitation').hidden, true);
  }
});

test('expired invitation exposes renewal; rejection and session expiry clear stale access', async () => {
  const ui = await page(async (url, options) => url.endsWith('/status')
    ? response(result(undefined, { driverInviteCode: undefined, inviteStatus: 'EXPIRED' }))
    : response({ code: 'INVALID_CREDENTIAL' }, 401));
  await ui.node('status').events.click();
  assert.equal(ui.node('renew').hidden, false); assert.equal(ui.node('invitation').hidden, true);
  await ui.submit();
  assert.equal(ui.node('workspace').hidden, true); assert.equal(ui.node('login').hidden, false);
  assert.equal(ui.node('phone').disabled, false);
});
