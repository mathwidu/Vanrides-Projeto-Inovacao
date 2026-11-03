import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { test } from 'node:test';
import assert from 'node:assert/strict';
const code = readFileSync(new URL('./driver-start.js', import.meta.url), 'utf8');
const page = readFileSync(new URL('./driver-start.html', import.meta.url), 'utf8');
const site = 'https://lab.invalid/motorista/saida';
const fragment = '#v1.00000000-0000-4000-8000-000000000001.1.' + 'a'.repeat(42)+'A';
function visit(href) {
  const opened = [], replaced = [], handlers = {};
  const button = { hidden:true, addEventListener: (k, f) => { handlers[k] = f; } };
  vm.runInNewContext(code, { URL, location:{ href, assign:v=>opened.push(v) },
    history:{ replaceState:(_s,_t,v)=>replaced.push(v) }, document:{ getElementById:k=>k==='open-driver'?button:{} } });
  return { opened,replaced,handlers,button };
}
test('preview does not open an app; only the explicit tap transfers the reviewed intent', () => {
  const { opened,replaced,handlers,button } = visit(site + fragment);
  assert.deepEqual(replaced,['/motorista/saida']); assert.deepEqual(opened,[]); assert.equal(button.hidden,false);
  handlers.click(); assert.deepEqual(opened,['vanridesdriver://run/start' + fragment]);
  assert.match(page,/content="no-referrer"/); assert.match(page,/connect-src 'none'/);
  assert.match(page,/src="\/motorista\/saida.js"/); assert.match(page,/id="open-driver" type="button" hidden/);
});
test('missing, ambiguous, malformed and unsafe links give recovery guidance without a handoff', () => {
  for (const href of [site, site+'?x=y'+fragment, site+fragment+'x',site+fragment.slice(0,-1)+'x',site+fragment.replace('.1.','.01.'),
    site+fragment.replace('.1.','.9007199254740992.'), site+fragment.replace('#v1.','#%761.'),
    site.replace('https:','http:')+fragment, site.replace('lab.invalid','user@lab.invalid')+fragment,
    site.replace('lab.invalid','lab.invalid:1234')+fragment, site+'/'+fragment]) {
    const state=visit(href); assert.equal(state.button.hidden,true); assert.equal(state.handlers.click,undefined);
    assert.deepEqual(state.opened,[]);
  }
});
