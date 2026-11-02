import { uuid } from './070-worker-common.mjs';

export const linkOrigin = 'https://vanrides.invalid';
const wire = /#v1\.([a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12})\.(0|[1-9][0-9]{0,15})\.([A-Za-z0-9_-]{42}[AEIMQUYcgkosw048])$/u;
export function extractSyntheticIntent(text) {
  if (typeof text !== 'string') return null;
  const urls = text.match(/https?:\/\/\S+/gu);
  if (urls?.length !== 1 || !urls[0].startsWith(linkOrigin + '/motorista/saida#')) return null;
  const bits = wire.exec(urls[0]);
  if (!bits || !uuid(bits[1]) || !Number.isSafeInteger(Number(bits[2]))) return null;
  return { id:bits[1], expectedVersion:Number(bits[2]), intentToken:bits[3] };
}

// Choose a supported zone with time left on the same local date to run W04.
// No host clock or schedule data is changed.
export function fixtureTimes(now = new Date()) {
  for (const [zone,label] of [['America/Manaus','MANAUS'],['America/Rio_Branco','RIO BRANCO'],['America/Noronha','NORONHA']]) {
    const hours = new Intl.DateTimeFormat('en-GB',{timeZone:zone,hour:'2-digit',minute:'2-digit',hourCycle:'h23'});
    const minute = date => { const [h,m] = hours.format(date).split(':').map(Number); return h*60+m; };
    const t = minute(now);
    if (t + 65 >= 1440) continue;
    const clock = offset => hours.format(new Date(now.valueOf()+offset*60000));
    return { zone:label, schedules:[['Ida escolar','IDA',clock(40)],['Volta escolar','VOLTA',clock(65)]] };
  }
  throw new Error('RUN_TEST_ZONE_UNAVAILABLE');
}
export function menuOption(text,label) {
  const match = [...(text ?? '').matchAll(/(?:^|\s)([1-9][0-9]?) — ([^.]*)\./gu)].filter(m=>m[2].includes(label));
  return match.length === 1 ? match[0][1] : null;
}
