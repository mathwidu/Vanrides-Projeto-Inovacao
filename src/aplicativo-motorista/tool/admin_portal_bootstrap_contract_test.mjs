import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';

import {
  buildAdminBootstrapPayload,
  deriveBackendCompanySlug,
  requireBootstrapResponseCompanySlug,
  shouldBootstrapAdmin,
} from './admin_portal_bootstrap_contract.mjs';
import { createAdminPortalWebE2EMockServer } from './admin_portal_web_e2e_mock_server.mjs';

const host = '127.0.0.1';
let baseUrl;
let server;

before(async () => {
  server = createAdminPortalWebE2EMockServer();
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, host, resolve);
  });
  const address = server.address();
  assert.equal(typeof address, 'object');
  baseUrl = `http://${host}:${address.port}`;
});

after(async () => {
  if (server?.listening) {
    await new Promise((resolve, reject) =>
      server.close((error) => (error ? reject(error) : resolve())),
    );
  }
});

test('builder exige companyName e nunca envia companySlug', () => {
  const payload = buildAdminBootstrapPayload({
    companyName: '  Empresa E2E Única  ',
    nome: 'Admin E2E',
    email: 'admin.e2e@vanrides.dev',
    telefone: '51999999999',
    senha: 'Admin@1234',
    companySlug: 'legacy-selector',
  });

  assert.deepEqual(payload, {
    companyName: 'Empresa E2E Única',
    nome: 'Admin E2E',
    email: 'admin.e2e@vanrides.dev',
    telefone: '51999999999',
    senha: 'Admin@1234',
  });
  assert.equal(Object.hasOwn(payload, 'companySlug'), false);
  assert.throws(
    () => buildAdminBootstrapPayload({ companyName: '   ' }),
    /companyName is required/,
  );
});

test('slug local e derivado somente do nome da empresa', () => {
  assert.equal(
    deriveBackendCompanySlug('  Ônibus São José  '),
    'nibus-s-o-jos',
  );
});

test('fixture existente reutiliza admin e execução isolada faz bootstrap', () => {
  assert.equal(shouldBootstrapAdmin({}), true);
  assert.equal(
    shouldBootstrapAdmin({
      admin_email: 'fixture@vanrides.dev',
      admin_password: 'fixture-password',
    }),
    false,
  );
  assert.equal(
    shouldBootstrapAdmin({
      admin_email: 'fixture@vanrides.dev',
      admin_password: '',
    }),
    true,
  );
});

test('resposta 201 exige companySlug não vazio', () => {
  assert.equal(
    requireBootstrapResponseCompanySlug({ companySlug: ' empresa-e2e ' }),
    'empresa-e2e',
  );
  assert.throws(
    () => requireBootstrapResponseCompanySlug({}),
    /companySlug is required/,
  );
  assert.throws(
    () => requireBootstrapResponseCompanySlug({ companySlug: '   ' }),
    /companySlug is required/,
  );
});

test('mock reproduz contrato: nome cria e slug-only recebe 400', async () => {
  const valid = await fetch(`${baseUrl}/company-admins/bootstrap`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(
      buildAdminBootstrapPayload({
        companyName: 'Empresa Mock Única',
        nome: 'Admin E2E',
        email: 'admin.e2e@vanrides.dev',
        telefone: '51999999999',
        senha: 'Admin@1234',
      }),
    ),
  });
  assert.equal(valid.status, 201);
  assert.equal((await valid.json()).companySlug, 'empresa-mock-nica');

  for (const body of [
    {},
    { companySlug: 'legacy-selector' },
    { companyName: '---' },
  ]) {
    const invalid = await fetch(`${baseUrl}/company-admins/bootstrap`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    assert.equal(invalid.status, 400);
  }
});
