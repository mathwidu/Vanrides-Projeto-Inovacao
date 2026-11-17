import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { randomUUID } from 'node:crypto';
const source = readFileSync(new URL('../../src/backend/src/main/resources/static/equipe/app.js', import.meta.url), 'utf8');
const html = readFileSync(new URL('../../src/backend/src/main/resources/static/equipe/index.html', import.meta.url), 'utf8');
const session = { login: 'synthetic-user', csrfToken: 'c'.repeat(43) };
const response = (data, status = 200) => ({ ok: status >= 200 && status < 300, status, json: async () => data });
const flush = () => new Promise(resolve => setImmediate(resolve));
async function page(handler, { auth = response(session), bot = () => response({}) } = {}) {
  const nodes = new Map(), calls = [], clipboard = [];
  const element = tag => ({ tagName: tag, children: [], value: '', checked: false, hidden: true, disabled: false, textContent: '', attributes: {},
    classList: { toggle() {} }, events: {}, addEventListener(event, fn) { this.events[event] = fn; },
    append(...children) { this.children.push(...children); }, replaceChildren(...children) { this.children = children; },
    setAttribute(name, value) { this.attributes[name] = value; }, removeAttribute(name) { delete this.attributes[name]; } });
  const node = id => {
    if (!nodes.has(id)) nodes.set(id, element('div'));
    return nodes.get(id);
  };
  node('role').value = 'driver'; node('phone').value = '+15550000001'; node('consent').checked = true;
  node('bot-fallback').hidden = false;
  runInNewContext(source, { document: { getElementById: node, createElement: element }, URLSearchParams, crypto: { randomUUID },
    window: { location: { search: '' }, confirm: () => true }, navigator: { clipboard: { writeText: async text => clipboard.push(text) } },
    fetch: async (url, options) => {
      calls.push({ url, options });
      if (url.endsWith('/session')) return auth;
      if (url.endsWith('/bot')) return bot(node);
      return handler(url, options);
    } });
  await flush();
  return { node, calls, clipboard, submit: async () => { node('participant-form').events.submit({ preventDefault() {} }); await flush(); } };
}
const result = (id, extras = {}) => ({ admissionStatus: 'ADMITTED', inviteStatus: 'ACTIVE', operationId: id,
  inviteId: 'synthetic-invite', driverInviteCode: 'a'.repeat(43), expiresAt: '2026-11-10T12:00:00Z', ...extras });

test('separate pickup cards send only the selected reference and keep technical IDs out of the copy', async () => {
  const linkId = randomUUID(), first = randomUUID(), second = randomUUID();
  const links = [{ linkId, passengerName: 'Pessoa sintética', state: 'APPROVED', pointPending: true,
    routines: 'Faculdade: ida e volta', pickupOptions: [
      { pickupId: first, routines: 'Faculdade · ida às 07:30 · segunda e quarta' },
      { pickupId: second, routines: 'Faculdade · volta às 18:00 · terça e quinta' },
    ] }];
  const ui = await page(async (url, options) => url.endsWith('/test-driver/status') ? response({ links })
    : response({ operationId: JSON.parse(options.body).operationId, linkState: 'APPROVED' }));
  await ui.node('test-refresh').events.click(); await flush();
  const visit = node => [node, ...node.children.flatMap(visit)];
  const displayed = visit(ui.node('test-links'));
  const text = displayed.map(node => node.textContent).join('\n');
  assert.match(text, /Ponto 1 — Faculdade · ida.*segunda e quarta/);
  assert.match(text, /Ponto 2 — Faculdade · volta.*terça e quinta/);
  for (const technical of [linkId, first, second, 'pickupId', 'latitude', 'longitude']) assert.equal(text.includes(technical), false);
  const approve = displayed.filter(node => node.tagName === 'button' && node.textContent === 'Aprovar novo ponto');
  assert.equal(approve.length, 2);
  approve[0].events.click(); await flush();
  approve[1].events.click(); await flush();
  const requests = ui.calls.filter(call => call.url.endsWith('/test-driver'));
  assert.equal(requests.length, 2);
  assert.deepEqual(requests.map(call => JSON.parse(call.options.body).pickupId), [first, second]);
  assert.equal(JSON.parse(requests[0].options.body).linkId, linkId);
  assert.notEqual(JSON.parse(requests[0].options.body).operationId, JSON.parse(requests[1].options.body).operationId);
  for (const request of requests) assert.equal(request.options.headers['X-CSRF-Token'], session.csrfToken);
});

test('an ambiguous legacy pickup request asks for the exact trip without changing the action', async () => {
  const linkId = randomUUID();
  const ui = await page(async url => url.endsWith('/test-driver/status')
    ? response({ links: [{ linkId, passengerName: 'Pessoa sintética', state: 'APPROVED', pointPending: true, routines: 'Dois trajetos' }] })
    : response({ code: 'POINT_SELECTION_REQUIRED' }, 409));
  await ui.node('test-refresh').events.click(); await flush();
  const item = ui.node('test-links').children[0];
  const actions = item.children.at(-1);
  actions.children[0].events.click(); await flush();
  assert.match(ui.node('message').textContent, /Escolha o trajeto e o ponto/);
  assert.equal(JSON.parse(ui.calls.find(call => call.url.endsWith('/test-driver')).options.body).pickupId, undefined);
});

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
    : response({ code: 'INVALID_CREDENTIAL' }, 401), { bot: () => response({ phoneNumber: '15550000002' }) });
  await ui.node('status').events.click();
  assert.equal(ui.node('renew').hidden, false); assert.equal(ui.node('invitation').hidden, true);
  assert.match(ui.node('result-description').textContent, /Gerar novo convite de motorista/);
  await ui.submit();
  assert.equal(ui.node('workspace').hidden, true); assert.equal(ui.node('login').hidden, false);
  assert.equal(ui.node('phone').disabled, false);
  assert.equal(ui.node('bot-link').hidden, true);
  assert.equal(ui.node('bot-link').attributes.href, undefined);
});

test('copy preserves the driver code and the complete passenger command without extra text', async () => {
  const passengerCode = 'b'.repeat(43);
  const ui = await page(async (url, options) => {
    const { operationId } = JSON.parse(options.body);
    return url.endsWith('/test-driver')
      ? response({ operationId, passengerInviteCode: passengerCode, expiresAt: '2026-11-10T12:00:00Z' })
      : response(result(operationId));
  });
  await ui.submit();
  assert.match(ui.node('result-description').textContent, /somente o código ao bot.*AJUDA/);
  assert.match(ui.node('expiry').textContent, /válido até.*Brasília/);
  await ui.node('copy').events.click();
  await ui.node('test-invite').events.click();
  await flush();
  assert.equal(ui.node('test-invitation').hidden, false);
  await ui.node('test-copy').events.click();
  assert.deepEqual(ui.clipboard, ['a'.repeat(43), 'PASSAGEIRO ' + passengerCode]);
  assert.equal(ui.calls.filter(call => call.options.method === 'POST').length, 2);
});

test('the test-driver shortcut prefills only the validated invitation and clears it on logout', async () => {
  const code = 'c'.repeat(43);
  const ui = await page(async (url, options) => {
    if (url.endsWith('/logout')) return response({}, 204);
    return response({ operationId: JSON.parse(options.body).operationId,
      passengerInviteCode: code, expiresAt: '2030-01-01T12:00:00Z' });
  }, { bot: () => response({ phoneNumber: '15550000002' }) });
  const link = ui.node('test-wa-link');
  assert.equal(link.hidden, true);
  await ui.node('test-invite').events.click();
  await flush();
  assert.equal(link.attributes.href, 'https://wa.me/15550000002?text=PASSAGEIRO%20' + code);
  assert.equal(link.hidden, false);
  await ui.node('logout').events.click();
  assert.equal(link.hidden, true);
  assert.equal(link.attributes.href, undefined);
  assert.equal(ui.node('test-invite-code').value, '');
});

test('participant status explains the next step without revealing codes or making changes', async () => {
  const activeInvite = { inviteStatus: 'ACTIVE', expiresAt: '2026-11-10T12:00:00Z' };
  const passengerOnboarding = { profileRole: 'PASSENGER', profileStatus: 'ONBOARDING' };
  const passengerActive = { ...passengerOnboarding, profileStatus: 'ACTIVE' };
  const cases = [
    ['passenger', { admissionStatus: 'NOT_ADMITTED' }, /Liberar passageiro/],
    ['driver', { admissionStatus: 'NOT_ADMITTED' }, /Liberar motorista/],
    ['passenger', {}, /pode conversar com o bot.*AJUDA/],
    ['driver', {}, /Liberar motorista.*gerar o convite/],
    ['driver', { ...passengerOnboarding, ...activeInvite }, /recuperar o mesmo convite com a validade original/],
    ['driver', { profileRole: 'DRIVER', profileStatus: 'ONBOARDING', inviteStatus: 'CONSUMED' }, /código já foi utilizado.*AJUDA/],
    ['driver', { profileRole: 'DRIVER', profileStatus: 'ACTIVE', inviteStatus: 'CONSUMED' }, /Cadastro concluído.*ROTINAS/],
    ['driver', passengerActive, /cadastro como passageiro/],
    ['passenger', passengerOnboarding, /cadastro está em andamento.*AJUDA/],
    ['passenger', passengerActive, /Cadastro concluído.*convite/],
    ['passenger', activeInvite, /convite de motorista ativo.*Selecione Motorista/],
  ];
  for (const [role, data, instruction] of cases) {
    const ui = await page(async () => response({ admissionStatus: 'ADMITTED', inviteStatus: 'NONE', ...data }));
    ui.node('role').value = role;
    await ui.node('status').events.click();
    assert.match(ui.node('result-description').textContent, instruction);
    assert.equal(ui.node('invitation').hidden, true);
    assert.equal(ui.node('renew').hidden, true);
    assert.deepEqual(ui.calls.filter(call => call.options.method === 'POST').map(call => call.url), ['/equipe/api/participants/status']);
  }
});

test('support recovery binds the selected case and phone, uses CSRF and clears stale controls', async () => {
  const supportCaseId = randomUUID();
  const active = { admissionStatus: 'ADMITTED', inviteStatus: 'NONE', profileRole: 'DRIVER', profileStatus: 'ACTIVE' };
  const ui = await page(async (url, options) => {
    if (url.endsWith('/participants/resume')) {
      assert.deepEqual(JSON.parse(options.body), { phoneNumber: '+15550000001', supportCaseId });
      assert.equal(options.headers['X-CSRF-Token'], session.csrfToken);
      return response({ ...active, conversationPaused: false });
    }
    return response({ ...active, conversationPaused: true, supportCaseId });
  });
  await ui.node('status').events.click();
  assert.equal(ui.node('resume-support').hidden, false);
  assert.match(ui.node('result-description').textContent, /pausada para suporte.*Retomar conversa/);
  await ui.node('resume-support').events.click();
  assert.equal(ui.node('resume-support').hidden, true);
  assert.match(ui.node('message').textContent, /Conversa retomada.*AJUDA/);
  await ui.node('status').events.click();
  ui.node('phone').value = '+15550000003';
  await ui.node('resume-support').events.click();
  assert.equal(ui.calls.filter(call => call.url.endsWith('/participants/resume')).length, 1);
  ui.node('phone').events.input();
  assert.equal(ui.node('resume-support').hidden, true);
});

test('support resume is unavailable for suspended profiles or malformed case references', async () => {
  for (const data of [
    { profileStatus: 'SUSPENDED', supportCaseId: randomUUID() },
    { profileStatus: 'ACTIVE', supportCaseId: 'untrusted-reference' },
  ]) {
    const ui = await page(async () => response({ admissionStatus: 'ADMITTED', inviteStatus: 'NONE',
      profileRole: 'DRIVER', conversationPaused: true, ...data }));
    await ui.node('status').events.click();
    assert.equal(ui.node('resume-support').hidden, true);
    await ui.node('resume-support').events.click();
    assert.equal(ui.calls.filter(call => call.url.endsWith('/participants/resume')).length, 0);
  }
});

test('bot link is authenticated and opens only the validated WhatsApp destination', async () => {
  const markup = html.match(/<a id="bot-link"[^>]+>/)?.[0];
  assert.match(markup, /\bhidden\b/);
  assert.doesNotMatch(markup, /\bhref=/);
  assert.match(markup, /target="_blank"/);
  assert.match(markup, /rel="noopener noreferrer"/);
  assert.match(markup, /referrerpolicy="no-referrer"/);

  for (const auth of [response({}, 401), response({ ...session, csrfToken: '' })]) {
    const denied = await page(undefined, { auth });
    assert.deepEqual(denied.calls.map(call => call.url), ['/equipe/api/session']);
    assert.equal(denied.node('workspace').hidden, true);
    assert.equal(denied.node('bot-link').hidden, true);
  }
  const ui = await page(undefined, { bot: node => {
    assert.equal(node('workspace').hidden, false);
    assert.equal(node('username').textContent, session.login);
    return response({ phoneNumber: '15550000002', code: 'a'.repeat(43) });
  } });
  assert.deepEqual(ui.calls.map(call => call.url), ['/equipe/api/session', '/equipe/api/bot']);
  assert.equal(ui.calls[1].options.method, 'GET');
  assert.equal(ui.calls[1].options.credentials, 'same-origin');
  assert.equal(ui.calls[1].options.cache, 'no-store');
  assert.equal(ui.calls[1].options.redirect, 'error');
  assert.equal(ui.node('bot-link').attributes.href, 'https://wa.me/15550000002');
  assert.equal(ui.node('bot-link').hidden, false);
  assert.equal(ui.node('bot-fallback').hidden, true);
});

test('the bot link falls back on bad or unavailable data without blocking operations', async () => {
  for (const payload of [{}, { phoneNumber: '15550000002?text=code' }, { phoneNumber: '+15550000002' }, { phoneNumber: 15550000002 }]) {
    const ui = await page(undefined, { bot: () => response(payload) });
    assert.equal(ui.node('bot-link').attributes.href, undefined);
    assert.equal(ui.node('bot-link').hidden, true);
    assert.equal(ui.node('bot-fallback').hidden, false);
  }
  const ui = await page(async (url, options) => response(result(JSON.parse(options.body).operationId)),
    { bot: () => response(null, 404) });
  assert.equal(ui.node('bot-link').hidden, true);
  assert.equal(ui.node('bot-fallback').hidden, false);
  await ui.submit();
  assert.equal(ui.node('invitation').hidden, false);

  const expired = await page(undefined, { bot: () => response({ phoneNumber: '15550000002' }, 401) });
  assert.equal(expired.node('workspace').hidden, true);
  assert.equal(expired.node('login').hidden, false);
  assert.match(expired.node('message').textContent, /sessão terminou/);
  assert.equal(expired.node('bot-link').hidden, true);
});

test('logout invalidates a pending bot lookup before the logout response', async () => {
  let finishLookup, finishLogout;
  const lookup = new Promise(resolve => { finishLookup = resolve; });
  const logout = new Promise(resolve => { finishLogout = resolve; });
  const ui = await page(() => logout, { bot: () => lookup });
  const signingOut = ui.node('logout').events.click();
  finishLookup(response({ phoneNumber: '15550000002' }));
  await flush();
  assert.equal(ui.node('bot-link').hidden, true);
  assert.equal(ui.node('bot-link').attributes.href, undefined);
  finishLogout(response({}, 204));
  await signingOut;
  assert.equal(ui.node('workspace').hidden, true);
  assert.equal(ui.node('login').hidden, false);
  assert.equal(ui.node('bot-link').hidden, true);
});
