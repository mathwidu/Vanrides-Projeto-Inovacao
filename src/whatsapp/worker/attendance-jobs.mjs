const path = '/integrations/whatsapp/v1/jobs/tick';
const jobs = [['MATERIALIZE_OCCURRENCES', 'calendarProcessed'], ['DUE_REMINDERS', 'reminderProcessed']];
const limit = 100;
const utcMinute = now => new Date(Math.floor(now / 60000) * 60000).toISOString().replace('.000Z', 'Z');
const cursorPattern = /^jobcursor_[0-9a-f]{32}$/u;

export const emptyAttendanceSummary = status => ({ status, calendarProcessed: 0, reminderProcessed: 0 });

export function safeAttendanceSummary(value) {
  if (!value || Object.keys(value).length !== 3 || !Object.hasOwn(value, 'status')
      || !['IDLE', 'BUSY', 'PROCESSED', 'MORE', 'UNAVAILABLE'].includes(value.status)
      || !jobs.every(([, field]) => Object.hasOwn(value, field) && Number.isInteger(value[field])
        && value[field] >= 0 && value[field] <= 2 * limit)) return emptyAttendanceSummary('UNAVAILABLE');
  const total = value.calendarProcessed + value.reminderProcessed;
  if (['IDLE', 'BUSY'].includes(value.status) && total || value.status === 'PROCESSED' && !total)
    return emptyAttendanceSummary('UNAVAILABLE');
  return { status: value.status, calendarProcessed: value.calendarProcessed, reminderProcessed: value.reminderProcessed };
}

function validPage(response, type, previousCursor) {
  const body = response?.body;
  return response?.status === 200 && body && typeof body === 'object' && Object.keys(body).length === 4
    && body.jobType === type && Number.isInteger(body.processed) && body.processed >= 0 && body.processed <= limit
    && (body.nextCursor === null || typeof body.nextCursor === 'string' && cursorPattern.test(body.nextCursor)
      && body.nextCursor !== previousCursor)
    && typeof body.serverTime === 'string' && Number.isFinite(Date.parse(body.serverTime));
}

// The backend owns business idempotency. Cursors only help drain larger local
// batches and may be discarded on restart. They never travel through n8n.
export function createAttendanceTicker({ api, now = Date.now }) {
  const continuations = new Map();
  return async () => {
    const summary = emptyAttendanceSummary('IDLE');
    let failed = false;
    for (const [type, field] of jobs) {
      const started = now(), saved = continuations.get(type);
      if (saved && started - saved.started >= 4 * 60000) continuations.delete(type);
      const work = continuations.get(type) ?? { windowKey: utcMinute(started), started };
      try {
        for (let page = 0; page < 2; page++) {
          const body = { jobType: type, windowKey: work.windowKey, batchLimit: limit };
          if (work.cursor) body.cursor = work.cursor;
          const response = await api.request(path, { body, deadline: started + 9000 });
          if (!validPage(response, type, work.cursor)) {
            if ([400, 404, 409, 410, 422].includes(response?.status)) continuations.delete(type);
            failed = true;
            break;
          }
          summary[field] += response.body.processed;
          if (response.body.nextCursor === null) { continuations.delete(type); break; }
          work.cursor = response.body.nextCursor;
          continuations.set(type, work);
        }
      } catch { failed = true; }
    }
    summary.status = failed ? 'UNAVAILABLE' : continuations.size ? 'MORE'
      : summary.calendarProcessed + summary.reminderProcessed ? 'PROCESSED' : 'IDLE';
    return summary;
  };
}
