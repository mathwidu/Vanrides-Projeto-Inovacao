// A fragment never reaches the web server. Only a deliberate tap opens the app;
// the device, not this page, authenticates and activates a reviewed departure.
(() => {
  const url = new URL(location.href);
  const fragment = url.hash;
  if (fragment || url.search) history.replaceState(null, '', url.pathname);
  const match = /^#v1\.([a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12})\.(0|[1-9][0-9]{0,15})\.([A-Za-z0-9_-]{42}[AEIMQUYcgkosw048])$/u.exec(fragment);
  if (url.protocol !== 'https:' || url.username || url.password || url.port || url.pathname !== '/motorista/saida'
      || url.search || !match || !Number.isSafeInteger(Number(match[2]))) return;
  const button = document.getElementById('open-driver');
  document.getElementById('instruction').textContent = 'Abra o VanRides neste celular. O aplicativo vai verificar a autorização antes de usar a localização.';
  button.hidden = false;
  button.addEventListener('click', () => location.assign('vanridesdriver://run/start' + fragment));
})();
