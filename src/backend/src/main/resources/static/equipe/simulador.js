(() => {
  'use strict';

  const driver = 'motorista';
  const passenger = 'passageiro';
  const inviteLink = 'https://vanrides.invalid/convite/passageiro-demo';
  const routine = 'Ida ao Campus · segunda a sexta, 07:30 (Brasília) · destino fixo: Campus Exemplo';
  const pickup = 'Praça Exemplo, portão azul';
  const roleLabels = { [driver]: 'Motorista', [passenger]: 'Passageiro' };
  const role = document.getElementById('sim-role');
  const log = document.getElementById('sim-log');
  const hint = document.getElementById('sim-hint');
  const form = document.getElementById('sim-form');
  const input = document.getElementById('sim-input');
  const options = document.getElementById('sim-options');
  const reset = document.getElementById('sim-reset');

  let state;
  let renderedRole;
  let renderedMessages;

  function message(to, from, text) {
    state.chats[to].push({ from, text });
  }

  function bot(to, text) {
    message(to, 'bot', text);
  }

  function reply(to, text, step, response) {
    message(to, to, text);
    state.steps[to] = step;
    bot(to, response);
  }

  function registerName(as, name) {
    state.names[as] = name;
    reply(as, name, 'consent', 'Leia o aviso de exemplo. No WhatsApp, o VanRides usa o nome para identificar a pessoa no cadastro e organiza rotinas, pontos e presenças.');
  }

  function invite(kind) {
    state.invite = kind;
    if (kind === 'direct') {
      reply(driver, 'Convidar passageiro pelo bot', 'invited', 'O convite direto foi entregue à conta fictícia do passageiro nesta simulação.');
      bot(passenger, `Você recebeu um convite direto de ${state.names[driver]} para a rotina ${routine}.`);
    } else {
      reply(driver, 'CONVIDAR LINK', 'invited', `Link de exemplo: ${inviteLink}`);
    }
  }

  function approvePickup() {
    reply(driver, 'Aprovar vínculo e ponto', 'approved', `Vínculo de ${state.names[passenger]} aprovado. Ponto de embarque: ${pickup}.`);
    state.steps[passenger] = 'approved';
    bot(passenger, 'Seu vínculo e o ponto de embarque foram aprovados. A próxima ida de exemplo está disponível às 07:30.');
  }

  function requestPickup() {
    reply(passenger, 'Confirmar ponto de embarque', 'requested', 'Pedido enviado ao motorista. Aguarde a aprovação do vínculo e do ponto.');
    state.steps[driver] = 'pending';
    bot(driver, `${state.names[passenger]} pediu vínculo para a rotina Ida ao Campus, de segunda a sexta às 07:30. Ponto fixo: ${pickup}.`);
  }

  const driverSteps = {
    start: () => ({
      hint: 'Use o convite fictício reservado para esta conta de motorista.',
      actions: [['Usar convite de motorista', () => reply(driver, 'Usar convite de motorista', 'name', 'Qual é o seu nome fictício?')]],
    }),
    name: () => ({
      hint: 'Digite um nome fictício ou use o exemplo.',
      name: true,
      actions: [['Usar nome Lara Demo', () => registerName(driver, 'Lara Demo')]],
    }),
    consent: () => ({
      hint: 'Simule o aceite para concluir o cadastro.',
      actions: [['Aceitar aviso de dados', () => reply(driver, 'ACEITO', 'registered', 'Cadastro de motorista concluído. Crie uma rotina para receber passageiros.')]],
    }),
    registered: () => ({
      hint: 'O trajeto e o destino fixo já estão preenchidos para esta demonstração.',
      actions: [['Criar rotina', () => reply(driver, 'CRIAR ROTINA', 'routineReview', `Confira a rotina de exemplo: ${routine}.`)]],
    }),
    routineReview: () => ({
      hint: 'Confira o sentido, os dias, o horário e o destino antes de confirmar.',
      actions: [['Confirmar rotina', () => reply(driver, 'Confirmar rotina', 'routineReady', 'Rotina criada. Você já pode convidar o passageiro de exemplo.')]],
    }),
    routineReady: () => ({
      hint: state.acceptsDirectInvite
        ? 'O passageiro permitiu o envio direto. Se preferir, gere um link de exemplo.'
        : 'Gere um link de exemplo ou mude para Passageiro para autorizar o envio direto.',
      actions: state.acceptsDirectInvite
        ? [['Gerar link de convite', () => invite('link')], ['Enviar convite direto', () => invite('direct')]]
        : [['Gerar link de convite', () => invite('link')]],
    }),
    invited: () => ({
      hint: 'Selecione Passageiro para usar o convite e solicitar o ponto de embarque.',
    }),
    pending: () => ({
      hint: 'Revise o pedido na conversa e aprove o vínculo e o ponto.',
      actions: [['Aprovar vínculo e ponto', approvePickup]],
    }),
    approved: () => ({
      hint: 'Consulte a próxima ida de exemplo. O passageiro pode confirmar presença no papel Passageiro.',
      actions: [['Consultar presenças', () => reply(driver, 'PRESENÇAS', 'approved',
        `Próxima ida de exemplo às 07:30. ${state.names[passenger]}: ${state.present ? 'presença confirmada' : 'confirmação pendente'}.`)]],
    }),
  };

  const passengerSteps = {
    start: () => {
      if (state.invite) {
        return {
          hint: 'Use o convite de exemplo para iniciar o cadastro e solicitar o embarque.',
          actions: [[state.invite === 'link' ? 'Abrir link de exemplo' : 'Usar convite recebido', () => reply(
            passenger, 'Usar convite de passageiro', 'name', `O convite é de ${state.names[driver]}, para a rotina ${routine}. Qual é o seu nome fictício?`,
          )]],
        };
      }

      return {
        hint: state.acceptsDirectInvite
          ? 'Selecione Motorista para enviar o convite direto à sua conta fictícia.'
          : 'Selecione Motorista para gerar um link ou autorize um convite direto para esta conta fictícia.',
        actions: state.acceptsDirectInvite
          ? [['Retirar permissão de convite direto', () => {
            state.acceptsDirectInvite = false;
            reply(passenger, 'PARAR CONVITES', 'start', 'Permissão de envio direto retirada nesta simulação.');
          }]]
          : [['Permitir convite direto', () => {
            state.acceptsDirectInvite = true;
            reply(passenger, 'ACEITO RECEBER CONVITES', 'start', 'Sua conta fictícia já pode receber um convite direto nesta simulação.');
          }]],
      };
    },
    name: () => ({
      hint: 'Digite um nome fictício ou use o exemplo.',
      name: true,
      actions: [['Usar nome Caio Demo', () => registerName(passenger, 'Caio Demo')]],
    }),
    consent: () => ({
      hint: 'Simule o aceite para continuar até o ponto de embarque.',
      actions: [['Aceitar aviso de dados', () => reply(passenger, 'ACEITO', 'pickupReview',
        `Confira o embarque de exemplo: Ida ao Campus, segunda a sexta às 07:30, com ponto fixo em ${pickup}.`)]],
    }),
    pickupReview: () => ({
      hint: 'Confirme o ponto fictício para enviar o pedido ao motorista.',
      actions: [['Confirmar ponto de embarque', requestPickup]],
    }),
    requested: () => ({
      hint: 'Selecione Motorista para revisar e aprovar seu pedido.',
    }),
    approved: () => ({
      hint: 'Confirme a presença na próxima ida de exemplo.',
      actions: [['Confirmar presença', () => {
        state.present = true;
        reply(passenger, 'VOU 1', 'present', 'Presença confirmada na próxima ida de exemplo às 07:30.');
      }]],
    }),
    present: () => ({
      hint: 'Selecione Motorista e consulte as presenças para ver a confirmação.',
    }),
  };

  function currentView() {
    const steps = role.value === driver ? driverSteps : passengerSteps;
    return steps[state.steps[role.value]]();
  }

  function renderLog(as) {
    if (renderedRole !== as) {
      log.replaceChildren();
      renderedRole = as;
      renderedMessages = 0;
    }

    const messages = state.chats[as];
    for (const { from, text } of messages.slice(renderedMessages)) {
      const line = document.createElement('li');
      line.className = `sim-message sim-message-${from === 'bot' ? 'bot' : 'user'}`;
      const author = from === 'bot' ? 'VanRides' : roleLabels[from];
      line.textContent = `${author} · ${text}`;
      log.append(line);
    }
    if (messages.length > renderedMessages) log.scrollTop = log.scrollHeight;
    renderedMessages = messages.length;
  }

  function focusNext() {
    const next = form.hidden ? (options.firstElementChild || role) : input;
    next.focus();
  }

  function render() {
    renderLog(role.value);
    const view = currentView();
    hint.textContent = view.hint;
    form.hidden = !view.name;
    input.disabled = form.hidden;
    input.removeAttribute('aria-invalid');
    options.replaceChildren();

    for (const [label, run] of view.actions || []) {
      const button = document.createElement('button');
      button.type = 'button';
      button.textContent = label;
      button.addEventListener('click', () => {
        if (!options.contains(button)) return;
        run();
        input.value = '';
        render();
        focusNext();
      });
      options.append(button);
    }
  }

  function resetTeamSimulator() {
    state = {
      steps: { [driver]: 'start', [passenger]: 'start' },
      chats: { [driver]: [], [passenger]: [] },
      names: {},
      invite: null,
      acceptsDirectInvite: false,
      present: false,
    };
    bot(driver, 'Olá! Você está usando uma conta fictícia de motorista. Comece pelo cadastro.');
    bot(passenger, 'Olá! Você está usando uma conta fictícia de passageiro. Use um convite para se cadastrar e pedir o ponto de embarque.');
    renderedRole = null;
    role.value = driver;
    input.value = '';
    render();
  }

  if (!role || !log || !hint || !form || !input || !options || !reset) return;

  form.addEventListener('submit', event => {
    event.preventDefault();
    if (!currentView().name) return;

    const name = input.value.trim();
    if (!/^[\p{L}\p{M}][\p{L}\p{M} .'-]{1,39}$/u.test(name)) {
      hint.textContent = 'Informe um nome fictício de 2 a 40 caracteres, com letras.';
      input.setAttribute('aria-invalid', 'true');
      input.focus();
      return;
    }

    registerName(role.value, name);
    input.value = '';
    render();
    focusNext();
  });
  role.addEventListener('change', () => {
    input.value = '';
    render();
  });
  reset.addEventListener('click', resetTeamSimulator);
  document.addEventListener('vanrides:team-ended', resetTeamSimulator);
  window.resetTeamSimulator = resetTeamSimulator;
  resetTeamSimulator();
})();
