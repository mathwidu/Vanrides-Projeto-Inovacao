import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { runInNewContext } from 'node:vm';

const directory = new URL('../../src/backend/src/main/resources/static/equipe/', import.meta.url);
const source = readFileSync(new URL('simulador.js', directory), 'utf8');

class Element {
  value = '';
  children = [];
  attributes = {};
  events = {};

  addEventListener(type, listener) { this.events[type] = listener; }
  setAttribute(name, value) { this.attributes[name] = value; }
  removeAttribute(name) { delete this.attributes[name]; }
  append(child) { this.children.push(child); }
  replaceChildren() { this.children = []; }
  contains(child) { return this.children.includes(child); }
  get firstElementChild() { return this.children[0]; }
  focus() { this.focused = true; }
}

function page() {
  const elements = new Map();
  const document = new Element();
  document.getElementById = id => {
    if (!elements.has(id)) elements.set(id, new Element());
    return elements.get(id);
  };
  document.createElement = () => new Element();
  const window = {};
  runInNewContext(source, { document, window });

  const node = id => elements.get('sim-' + id);
  const choose = role => {
    node('role').value = role;
    node('role').events.change();
  };
  const buttons = () => node('options').children.map(button => button.textContent);
  const click = label => {
    const button = node('options').children.find(option => option.textContent === label);
    assert.ok(button, `Esperava a opção “${label}”. Opções atuais: ${buttons().join(', ')}.`);
    assert.equal(button.type, 'button');
    button.events.click();
    return button;
  };
  const submit = name => {
    node('input').value = name;
    let prevented = false;
    node('form').events.submit({ preventDefault() { prevented = true; } });
    assert.ok(prevented);
  };
  const transcript = () => node('log').children.map(line => line.textContent).join('\n');
  const createRoutine = () => {
    click('Usar convite de motorista');
    click('Usar nome Lara Demo');
    click('Aceitar aviso de dados');
    click('Criar rotina');
    click('Confirmar rotina');
  };

  return { document, window, node, choose, buttons, click, submit, transcript, createRoutine };
}

test('o cadastro aceita um nome fictício e preserva a etapa ao alternar os papéis', () => {
  const ui = page();
  assert.deepEqual(ui.buttons(), ['Usar convite de motorista']);
  assert.equal(ui.node('form').hidden, true);
  assert.equal(ui.node('input').disabled, true);
  ui.submit('Nome antecipado');
  assert.doesNotMatch(ui.transcript(), /Nome antecipado/);

  ui.click('Usar convite de motorista');
  assert.equal(ui.node('form').hidden, false);
  for (const text of ['  ', '555555', '<b>nome</b>']) {
    ui.submit(text);
    assert.equal(ui.node('input').attributes['aria-invalid'], 'true');
    assert.match(ui.node('hint').textContent, /nome fictício de 2 a 40 caracteres/);
  }
  ui.choose('passageiro');
  assert.equal(ui.node('form').hidden, true);
  assert.equal(ui.node('input').value, '');
  assert.doesNotMatch(ui.transcript(), /seu nome fictício/);
  ui.choose('motorista');
  assert.equal(ui.node('form').hidden, false);
  ui.submit("Íris D'Ávila");
  assert.match(ui.transcript(), /Motorista · Íris D'Ávila/);
  assert.equal(ui.node('input').attributes['aria-invalid'], undefined);
  assert.equal(ui.node('input').disabled, true);

  ui.click('Aceitar aviso de dados');
  ui.click('Criar rotina');
  assert.match(ui.transcript(), /Ida ao Campus.*07:30.*destino fixo: Campus Exemplo/);
  assert.deepEqual(ui.buttons(), ['Confirmar rotina']);
  ui.click('Confirmar rotina');
  assert.deepEqual(ui.buttons(), ['Gerar link de convite']);
});

test('o convite por link libera o pedido e a presença só aparece após a aprovação', () => {
  const ui = page();
  ui.createRoutine();
  ui.click('Gerar link de convite');
  assert.match(ui.transcript(), /https:\/\/vanrides\.invalid\/convite\/passageiro-demo/);

  ui.choose('passageiro');
  assert.deepEqual(ui.buttons(), ['Abrir link de exemplo']);
  ui.click('Abrir link de exemplo');
  assert.match(ui.transcript(), /convite é de Lara Demo/);
  ui.click('Usar nome Caio Demo');
  ui.click('Aceitar aviso de dados');
  assert.match(ui.transcript(), /Praça Exemplo, portão azul/);
  ui.click('Confirmar ponto de embarque');
  assert.deepEqual(ui.buttons(), []);
  assert.match(ui.node('hint').textContent, /Motorista/);

  ui.choose('motorista');
  assert.match(ui.transcript(), /Caio Demo pediu vínculo.*Praça Exemplo, portão azul/);
  ui.click('Aprovar vínculo e ponto');
  ui.click('Consultar presenças');
  assert.match(ui.transcript(), /Caio Demo: confirmação pendente/);

  ui.choose('passageiro');
  assert.match(ui.transcript(), /vínculo e o ponto de embarque foram aprovados/);
  ui.click('Confirmar presença');
  assert.match(ui.transcript(), /Presença confirmada.*07:30/);
  assert.deepEqual(ui.buttons(), []);

  ui.choose('motorista');
  ui.click('Consultar presenças');
  assert.match(ui.transcript(), /Caio Demo: presença confirmada/);
});

test('o envio direto só é oferecido com permissão atual do passageiro', () => {
  const ui = page();
  ui.createRoutine();
  ui.choose('passageiro');
  ui.click('Permitir convite direto');
  assert.match(ui.transcript(), /ACEITO RECEBER CONVITES/);

  ui.choose('motorista');
  assert.deepEqual(ui.buttons(), ['Gerar link de convite', 'Enviar convite direto']);
  const priorOption = ui.node('options').children[1];
  ui.choose('passageiro');
  ui.click('Retirar permissão de convite direto');
  ui.choose('motorista');
  assert.deepEqual(ui.buttons(), ['Gerar link de convite']);
  priorOption.events.click();
  assert.doesNotMatch(ui.transcript(), /convite direto foi entregue/);

  ui.choose('passageiro');
  ui.click('Permitir convite direto');
  ui.choose('motorista');
  const oneUseOption = ui.click('Enviar convite direto');
  oneUseOption.events.click();
  const deliveries = ui.transcript().match(/convite direto foi entregue/g);
  assert.equal(deliveries.length, 1);

  ui.choose('passageiro');
  assert.match(ui.transcript(), /recebeu um convite direto de Lara Demo/);
  assert.deepEqual(ui.buttons(), ['Usar convite recebido']);
  ui.click('Usar convite recebido');
  assert.match(ui.transcript(), /Qual é o seu nome fictício\?/);
});

test('reinício e encerramento de sessão descartam convite, permissão, conversa e rascunho', () => {
  for (const restart of [
    ui => ui.node('reset').events.click(),
    ui => ui.document.events['vanrides:team-ended'](),
    ui => ui.window.resetTeamSimulator(),
  ]) {
    const ui = page();
    ui.createRoutine();
    ui.choose('passageiro');
    ui.click('Permitir convite direto');
    ui.choose('motorista');
    ui.click('Enviar convite direto');
    ui.choose('passageiro');
    ui.click('Usar convite recebido');
    ui.node('input').value = 'Rascunho Demo';

    restart(ui);

    assert.equal(ui.node('role').value, 'motorista');
    assert.equal(ui.node('input').value, '');
    assert.deepEqual(ui.buttons(), ['Usar convite de motorista']);
    assert.doesNotMatch(ui.transcript(), /Lara Demo|Campus/);
    ui.choose('passageiro');
    assert.deepEqual(ui.buttons(), ['Permitir convite direto']);
    assert.doesNotMatch(ui.transcript(), /Lara Demo|recebeu um convite|Rascunho Demo/);
  }
});
