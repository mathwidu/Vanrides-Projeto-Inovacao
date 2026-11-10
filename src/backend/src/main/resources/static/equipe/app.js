'use strict';
const $ = id => document.getElementById(id);
let csrf = null, busy = false, pending = null, invitation = null, currentPhone = null, testPending = null, linkButtons = [];
const messages = {
  INVALID_CREDENTIAL: 'Sua sessão terminou. Entre novamente pelo GitHub.',
  INVALID_PAYLOAD: 'Confira o número e o papel do participante.',
  ACTION_NOT_ALLOWED: 'Sua conta não tem acesso a esta operação.',
  DEPENDENCY_UNAVAILABLE: 'Não foi possível consultar os serviços agora. Tente novamente.',
  RATE_LIMITED: 'Aguarde um instante antes de tentar novamente.',
  IDEMPOTENCY_CONFLICT: 'Este pedido não corresponde à operação anterior. Consulte o cadastro antes de continuar.',
  WHATSAPP_NOT_FOUND: 'O WhatsApp não confirmou este número. Confira país, DDD e a linha do participante.',
  ADMISSION_CONFLICT: 'O número está associado a um cadastro incompatível. Peça uma revisão à equipe.',
  PROFILE_CONFLICT: 'O cadastro atual tem outro papel ou já concluiu esta etapa. Consulte o estado do participante.',
  INVITE_EXPIRED: 'O convite expirou. Consulte o cadastro e use a opção de gerar um novo convite.',
  INVITE_NOT_RENEWABLE: 'Somente um convite expirado e ainda não utilizado pode ser renovado.',
  INVITE_UNAVAILABLE: 'Há um convite incompatível com esta operação. Consulte o cadastro.',
  UNAUTHORIZED: 'Sua sessão terminou. Entre novamente pelo GitHub.',
  FORBIDDEN: 'Sua conta não tem acesso às operações da equipe.',
  INVALID_REQUEST: 'Confira o número, o papel e a confirmação de participação.',
  PROFILE_ALREADY_ACTIVE: 'Este contato já concluiu o cadastro. Confira o papel antes de gerar outro convite.',
  CONTACT_NOT_FOUND: 'O WhatsApp não confirmou este número. Confira país, DDD e a linha do participante.',
  EVOLUTION_UNAVAILABLE: 'Não foi possível confirmar o número no WhatsApp agora. Tente novamente.',
  UPSTREAM_UNAVAILABLE: 'Um serviço está indisponível. Tente novamente em instantes.',
  LINK_UNAVAILABLE: 'Este pedido mudou ou não pode mais ser decidido. Atualize os pedidos.',
  TEAM_DISABLED: 'A área da equipe ainda não está habilitada neste ambiente.',
};
function message(text, error = false) { $('message').textContent = text; $('message').classList.toggle('error', error); }
function controls(value) {
  busy = value;
  for (const id of ['submit', 'status', 'logout', 'renew', 'test-invite', 'test-refresh']) $(id).disabled = value;
  for (const button of linkButtons) button.disabled = value;
  for (const id of ['phone', 'role', 'consent']) $(id).disabled = value || pending !== null;
}
function phone() {
  const value = $('phone').value.trim().replace(/[\s()\-]/g, '');
  if (!/^\+[1-9][0-9]{7,14}$/.test(value)) throw Error('Informe o número com +, país e DDD.');
  return value;
}
async function request(path, body) {
  const response = await fetch('/equipe/api/' + path, { method: body === undefined ? 'GET' : 'POST',
    credentials: 'same-origin', cache: 'no-store', redirect: 'error',
    headers: body === undefined ? {} : { 'Content-Type': 'application/json', 'X-CSRF-Token': csrf },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = Error(messages[data.code] || 'Não foi possível concluir esta etapa. Consulte o cadastro ou tente novamente.');
    error.status = response.status;
    if (response.status === 401) { csrf = null; $('workspace').hidden = true; $('login').hidden = false; clearInvitation(); $('test-invitation').hidden = true; $('test-invite-code').value = ''; }
    throw error;
  }
  return data;
}
function clearInvitation() { invitation = null; $('invite-code').value = ''; $('invitation').hidden = true; }
const labels = { ADMITTED: 'Liberado', NOT_ADMITTED: 'Ainda não liberado', ACTIVE: 'Ativo', ONBOARDING: 'Em cadastro',
  PASSENGER: 'Passageiro', DRIVER: 'Motorista', NONE: 'Ainda não iniciado', AVAILABLE: 'Disponível', ISSUED: 'Disponível',
  EXPIRED: 'Expirado', CONSUMED: 'Utilizado', REVOKED: 'Revogado', PENDING: 'Pendente' };
function label(value, fallback) { return labels[value] || fallback; }
function validResult(data) {
  if (!data || !['ADMITTED', 'NOT_ADMITTED'].includes(data.admissionStatus)
    || !['NONE', 'ACTIVE', 'EXPIRED', 'REVOKED', 'CONSUMED'].includes(data.inviteStatus)
    || data.driverInviteCode && (!/^[A-Za-z0-9_-]{43}$/.test(data.driverInviteCode) || !Number.isFinite(Date.parse(data.expiresAt))))
    throw Error('O servidor não confirmou o resultado. Consulte o cadastro ou tente novamente.');
  return data;
}
function result(data) {
  validResult(data); clearInvitation();
  $('result').hidden = false;
  $('admission').textContent = label(data.admissionStatus, 'Consulte novamente');
  $('profile').textContent = [label(data.profileRole, ''), label(data.profileStatus, 'Ainda não iniciado')].filter(Boolean).join(' · ');
  $('invite-status').textContent = label(data.inviteStatus, 'Sem convite');
  $('result-description').textContent = data.driverInviteCode ? 'Convite pronto. Entregue-o ao motorista para ele continuar pelo WhatsApp.' : 'Confira o estado do participante. Ele conclui o cadastro pelo WhatsApp.';
  $('renew').hidden = $('role').value !== 'driver' || data.profileStatus === 'ACTIVE' || data.inviteStatus !== 'EXPIRED' || !data.inviteId;
  if (data.driverInviteCode && /^[A-Za-z0-9_-]{43}$/.test(data.driverInviteCode)) {
    invitation = { code: data.driverInviteCode, expiresAt: data.expiresAt };
    $('invite-code').value = invitation.code;
    $('expiry').textContent = 'Válido até ' + new Date(data.expiresAt).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' }) + ' (Brasília).';
    $('invitation').hidden = false;
  }
}
async function mutate(action) {
  if (busy) return;
  try {
    if (!$('consent').checked) { message('Confirme que a pessoa concordou em participar do teste.', true); return; }
    const number = phone();
    if (!pending) pending = { phoneNumber: number, action, operationId: crypto.randomUUID() };
    controls(true); clearInvitation(); message('Conferindo o contato e preparando a liberação…');
    const data = validResult(await request('participants', pending));
    if (data.operationId !== pending.operationId) throw Error('O servidor não confirmou este pedido. Tente novamente.');
    currentPhone = number; pending = null; result(data); message('Operação concluída. O cadastro continua com o próprio participante.');
  } catch (error) {
    if (error.status && error.status < 500 && ![408, 429].includes(error.status)) pending = null;
    message(error.message + (pending ? ' A resposta não foi confirmada. Use “Tentar novamente” para conferir o mesmo pedido.' : ''), true);
  } finally { controls(false); $('submit').textContent = pending ? 'Tentar novamente' : $('role').value === 'driver' ? 'Liberar motorista' : 'Liberar passageiro'; }
}
$('participant-form').addEventListener('submit', event => { event.preventDefault(); mutate($('role').value === 'driver' ? 'ISSUE_DRIVER_INVITE' : 'ADMIT_PASSENGER'); });
$('role').addEventListener('change', () => { $('submit').textContent = $('role').value === 'driver' ? 'Liberar motorista' : 'Liberar passageiro'; $('result').hidden = true; clearInvitation(); });
$('phone').addEventListener('input', () => { $('result').hidden = true; currentPhone = null; clearInvitation(); });
$('status').addEventListener('click', async () => {
  if (busy) return;
  try { const number = phone(); controls(true); message('Consultando cadastro…'); currentPhone = number; result(await request('participants/status', { phoneNumber: number })); message('Consulta concluída.'); }
  catch (error) { message(error.message, true); } finally { controls(false); }
});
$('renew').addEventListener('click', () => {
  if (currentPhone !== $('phone').value.trim().replace(/[\s()\-]/g, '')) return;
  if (window.confirm('Gerar um novo convite para substituir o convite expirado?')) mutate('RENEW_DRIVER_INVITE');
});
$('copy').addEventListener('click', async () => {
  if (!invitation) return;
  try { await navigator.clipboard.writeText('Seu WhatsApp foi liberado como motorista no VanRides. Envie somente este código ao bot:\n\n' + invitation.code + '\n\nDepois envie AJUDA e siga as perguntas do cadastro. ' + $('expiry').textContent); message('Instruções copiadas. Envie em uma conversa privada com o participante.'); }
  catch { message('Não foi possível copiar automaticamente. Selecione o convite e copie manualmente.', true); }
});
const linkStates = { REQUESTED: 'Aguardando aprovação', APPROVED: 'Aprovado' };
const decisions = {
  APPROVE_TEST_LINK: ['Aprovar vínculo e ponto', 'Vínculo e ponto aprovados. O passageiro recebe o aviso no WhatsApp.'],
  APPROVE_TEST_POINT: ['Aprovar novo ponto', 'Novo ponto aprovado. O passageiro recebe o aviso no WhatsApp.'],
  RECTIFY_TEST_POINT: ['Pedir correção do ponto', 'Correção pedida. O passageiro recebe o pedido no WhatsApp.'],
  REJECT_TEST_LINK: ['Recusar', 'Pedido recusado. O passageiro recebe o aviso no WhatsApp.', 'Recusar o pedido de '],
  CLOSE_TEST_LINK: ['Encerrar vínculo', 'Vínculo encerrado. O passageiro recebe o aviso no WhatsApp.', 'Encerrar o vínculo de '],
};
function linkActions(link) {
  if (link.state === 'REQUESTED') return link.pointPending ? ['APPROVE_TEST_LINK', 'RECTIFY_TEST_POINT', 'REJECT_TEST_LINK'] : ['REJECT_TEST_LINK'];
  return link.pointPending ? ['APPROVE_TEST_POINT', 'RECTIFY_TEST_POINT', 'CLOSE_TEST_LINK'] : ['CLOSE_TEST_LINK'];
}
function renderLinks(links) {
  const list = $('test-links'); list.replaceChildren(); linkButtons = [];
  for (const link of Array.isArray(links) ? links : []) {
    if (!/^[0-9a-f-]{36}$/.test(link.linkId ?? '') || !linkStates[link.state]) continue;
    const item = document.createElement('li');
    const title = document.createElement('strong'); title.textContent = link.passengerName || 'Passageiro sem nome';
    const detail = document.createElement('span');
    detail.textContent = ' · ' + linkStates[link.state] + (link.pointPending ? ' · ponto aguardando aprovação'
      : link.state === 'REQUESTED' ? ' · aguardando novo ponto do passageiro' : '');
    const routines = document.createElement('p'); routines.className = 'hint'; routines.textContent = link.routines || '';
    const actions = document.createElement('div'); actions.className = 'actions';
    for (const [index, action] of linkActions(link).entries()) {
      const [label, , confirmText] = decisions[action];
      const button = document.createElement('button'); button.type = 'button'; button.textContent = label;
      if (index > 0) button.className = 'secondary';
      button.addEventListener('click', () => { if (!confirmText || window.confirm(confirmText + title.textContent + '?')) testOperation(action, link.linkId); });
      actions.append(button); linkButtons.push(button);
    }
    item.append(title, detail, routines, actions);
    list.append(item);
  }
  $('test-empty').textContent = 'Nenhum pedido aguardando aprovação ou aprovado.';
  $('test-empty').hidden = list.children.length > 0;
}
async function refreshTestDriver(quiet = false) {
  const data = await request('test-driver/status', {});
  renderLinks(data.links);
  if (!quiet) message('Pedidos atualizados.');
}
function showTestInvite(data) {
  if (!/^[A-Za-z0-9_-]{43}$/.test(data.passengerInviteCode ?? '') || !Number.isFinite(Date.parse(data.expiresAt)))
    throw Error('O convite não está mais disponível. Gere outro convite.');
  $('test-invite-code').value = 'PASSAGEIRO ' + data.passengerInviteCode;
  $('test-expiry').textContent = 'Uso único. Válido até ' + new Date(data.expiresAt).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' }) + ' (Brasília).';
  $('test-invitation').hidden = false;
}
async function testOperation(action, linkId) {
  if (busy) return;
  try {
    if (!testPending || testPending.action !== action || testPending.linkId !== linkId)
      testPending = { action, operationId: crypto.randomUUID(), ...(linkId ? { linkId } : {}) };
    controls(true); message(action === 'ISSUE_TEST_PASSENGER_INVITE' ? 'Gerando convite de passageiro…' : 'Registrando a decisão…');
    const data = await request('test-driver', testPending);
    if (data.operationId !== testPending.operationId) throw Error('O servidor não confirmou este pedido. Tente novamente.');
    testPending = null;
    if (action === 'ISSUE_TEST_PASSENGER_INVITE') { showTestInvite(data); message('Convite pronto. Envie a mensagem ao bot pelo WhatsApp do passageiro.'); }
    else { await refreshTestDriver(true); message(decisions[action][1]); }
  } catch (error) {
    if (error.status && error.status < 500 && ![408, 429].includes(error.status)) testPending = null;
    message(error.message + (testPending ? ' A resposta não foi confirmada. Repita a mesma ação para conferir o pedido.' : ''), true);
  } finally { controls(false); }
}
$('test-invite').addEventListener('click', () => { $('test-invitation').hidden = true; testOperation('ISSUE_TEST_PASSENGER_INVITE'); });
$('test-refresh').addEventListener('click', async () => {
  if (busy) return;
  try { controls(true); await refreshTestDriver(); } catch (error) { message(error.message, true); } finally { controls(false); }
});
$('test-copy').addEventListener('click', async () => {
  try { await navigator.clipboard.writeText($('test-invite-code').value); message('Mensagem copiada. Envie ao bot pelo WhatsApp do passageiro.'); }
  catch { message('Não foi possível copiar automaticamente. Selecione a mensagem e copie manualmente.', true); }
});
$('logout').addEventListener('click', async () => {
  controls(true);
  try { await request('logout', {}); csrf = null; pending = null; testPending = null; clearInvitation(); $('test-invitation').hidden = true; $('test-invite-code').value = ''; $('workspace').hidden = true; $('login').hidden = false; $('phone').value = ''; message('Você saiu da área da equipe.'); }
  catch (error) { message(error.message, true); } finally { controls(false); }
});
(async () => {
  try { const session = await request('session');
    if (!/^[A-Za-z0-9-]{1,39}$/.test(session.login ?? '') || !/^[A-Za-z0-9_-]{43}$/.test(session.csrfToken ?? '')) throw Error('Não foi possível confirmar sua sessão.');
    csrf = session.csrfToken; $('username').textContent = session.login; $('workspace').hidden = false; message('Acesso confirmado. Você pode preparar os testes da equipe.'); }
  catch (error) {
    $('login').hidden = false;
    const auth = new URLSearchParams(window.location.search).get('auth');
    const reason = { denied: 'Sua conta precisa ter permissão de escrita no repositório VanRides.', invalid: 'O login não foi concluído. Tente entrar novamente.', unavailable: 'O GitHub não pôde confirmar seu acesso agora. Tente novamente.' }[auth];
    message(reason || (error.status === 401 ? 'Entre para acessar as operações da equipe.' : error.message), Boolean(reason) || error.status !== 401);
  }
})();
