import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { androidAssociation } from './association.mjs';

test('association ties the distributed package to one explicitly supplied certificate', () => {
  const certificate = Array(32).fill('AB').join(':');
  const [statement] = androidAssociation(certificate);
  assert.deepEqual(statement.relation, ['delegate_permission/common.handle_all_urls']);
  assert.equal(statement.target.package_name, 'dev.matheusduarte.vanrides.driver');
  assert.deepEqual(statement.target.sha256_cert_fingerprints, [certificate]);
  for (const value of [null, '', certificate + '\n', certificate.toLowerCase(), certificate.slice(3)]) {
    assert.throws(() => androidAssociation(value));
  }
});

test('a preview of the static page has no side effect or secret-bearing redirect', () => {
  const page = readFileSync(new URL('./driver-return.html', import.meta.url), 'utf8');
  assert.doesNotMatch(page, /<script|<form|http-equiv|\son[a-z]+=|https?:\/\//iu);
  assert.deepEqual([...page.matchAll(/href="([^"]+)"/gu)].map((match) => match[1]), ['vanridesdriver://pairing/return']);
});
