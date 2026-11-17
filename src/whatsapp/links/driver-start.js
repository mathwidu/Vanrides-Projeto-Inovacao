// A fragment never reaches the web server. Only a deliberate tap opens the app;
// the device, not this page, authenticates and activates a reviewed departure.
(() => {
  const url = new URL(location.href);
  const fragment = url.hash;
  const finish = url.pathname === '/motorista/fim';
  const connect = url.pathname === '/motorista/conectar';
  if (fragment || url.search) history.replaceState(null, '', url.pathname);
  const match = (connect
    ? /^#v1\.([a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12})\.([A-Za-z0-9_-]{42}[AEIMQUYcgkosw048])$/u
    : /^#v1\.([a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12})\.(0|[1-9][0-9]{0,15})\.([A-Za-z0-9_-]{42}[AEIMQUYcgkosw048])$/u).exec(fragment);
  if (url.protocol !== 'https:' || url.username || url.password || url.port || !['/motorista/saida','/motorista/fim','/motorista/conectar'].includes(url.pathname)
      || url.search || !match || !connect && !Number.isSafeInteger(Number(match[2]))) return;
  const button = document.getElementById('open-driver');
  document.getElementById('intent-title').textContent = connect ? 'Conectar seu celular' : finish ? 'Finalizar sua viagem' : 'Preparar sua saída';
  document.getElementById('instruction').textContent = connect
    ? 'Abra o VanRides neste celular e confira o pedido de conexão. A localização permanece desligada nesta etapa.'
    : finish
    ? 'Abra o VanRides neste celular para conferir a viagem e confirmar a finalização. Abrir este link não encerra a viagem.'
    : 'Abra o VanRides neste celular. O aplicativo vai verificar a autorização antes de usar a localização.';
  button.hidden = false;
  button.addEventListener('click', () => location.assign((connect ? 'vanridesdriver://pair/connect' : 'vanridesdriver://run/' + (finish ? 'finish' : 'start')) + fragment));
})();
