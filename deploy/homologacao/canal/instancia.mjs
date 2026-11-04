// Run only in the dedicated network. The QR output must be piped to a private file.
import {readFileSync} from 'node:fs';

const action = process.argv[2];
const base = 'https://evolution-hml:8443', instance = 'vanrides-hml';
const secret = name => readFileSync('/run/secrets/' + name, 'utf8').trim();
async function api(path, data) {
  const response = await fetch(base + path, {
    method: data ? 'POST' : 'GET', redirect: 'error', signal: AbortSignal.timeout(20000),
    headers: {apikey: secret('evo_key'), 'content-type': 'application/json'},
    ...(data ? {body: JSON.stringify(data)} : {})
  });
  if (!response.ok) throw new Error('EVOLUTION_HTTP_' + response.status);
  const parsed = await response.json();
  if (parsed?.error) throw new Error('EVOLUTION_REJEITOU');
  return parsed;
}
const webhook = () => ({
  enabled: true, url: 'http://ingress:7180/evolution/webhook',
  byEvents: false, base64: false, headers: {Authorization: 'Bearer ' + secret('webhook_token')},
  events: ['MESSAGES_UPSERT', 'MESSAGES_UPDATE', 'CONNECTION_UPDATE']
});
try {
  if (action === 'criar') {
    await api('/instance/create', {
      instanceName: instance, token: secret('evo_instance'), integration: 'WHATSAPP-BAILEYS',
      qrcode: false, groupsIgnore: true, syncFullHistory: false, readMessages: false, alwaysOnline: false,
      webhook: webhook()
    });
    console.log('Instância criada.');
  } else if (action === 'configurar-webhook') {
    await api('/webhook/set/' + instance, {webhook: webhook()});
    console.log('Webhook configurado.');
  } else if (action === 'estado') {
    const result = await api('/instance/connectionState/' + instance);
    if (result?.instance?.instanceName !== instance || !['open','connecting','close','refused'].includes(result.instance.state))
      throw new Error('ESTADO_DESCONHECIDO');
    console.log(result.instance.state);
  } else if (action === 'qr' && !process.stdout.isTTY) {
    const result = await api('/instance/connect/' + instance);
    const image = result?.base64 ?? result?.qrcode?.base64;
    if (typeof image !== 'string' || !image.startsWith('data:image/png;base64,') || image.length > 500000)
      throw new Error('QR_INDISPONIVEL');
    const bytes = Buffer.from(image.slice('data:image/png;base64,'.length),'base64');
    if (bytes.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a') throw new Error('QR_INVALIDO');
    process.stdout.write(bytes);
  } else throw new Error('ACAO_INVALIDA');
} catch (error) {
  process.stderr.write((/^(?:EVOLUTION_HTTP_[0-9]{3}|EVOLUTION_REJEITOU|ESTADO_DESCONHECIDO|QR_INDISPONIVEL|QR_INVALIDO|ACAO_INVALIDA)$/.test(error.message) ? error.message : 'FALHA_NO_CANAL') + '\n');
  process.exitCode = 1;
}
