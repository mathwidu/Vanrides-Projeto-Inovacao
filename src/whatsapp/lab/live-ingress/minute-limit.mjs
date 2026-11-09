/** Bounded rolling minute; observation counters never consume future capacity. */
export function createMinuteLimit(limits, apiAdmission = false) {
  const buckets = new Map(['human', 'status', 'connection', 'A', 'B', 'C', 'D'].map(key => [key, []]));
  if (apiAdmission) buckets.set('transport', []);
  let latest = -1;
  return Object.freeze({
    take(event, now) {
      if (!Number.isSafeInteger(now) || now < latest) return { allowed: false, retryAfter: 1 };
      latest = now;
      // Prune inactive dynamic contacts and cap memory even for unadmitted senders.
      for (const [key, queue] of buckets) {
        while (queue.length && queue[0] <= now - 60_000) queue.shift();
        if (key.startsWith('P:') && !queue.length) buckets.delete(key);
      }
      if (event.kind === 'human' && /^P:[a-f0-9]{64}$/.test(event.contact) && !buckets.has(event.contact)) {
        if (buckets.size >= (apiAdmission ? 264 : 263)) return { allowed: false, retryAfter: 60 };
        buckets.set(event.contact, []);
      }
      const keys = event.kind === 'human' ? [...(apiAdmission ? ['transport'] : []), 'human', event.contact] : [event.kind];
      let retryAfter = 0;
      for (const key of keys) {
        const queue = buckets.get(key);
        const cap = (['A', 'B', 'C', 'D'].includes(key) || key.startsWith('P:')) ? limits.perContact : key === 'transport' ? 240 : limits[key];
        if (!queue || !Number.isSafeInteger(cap)) return { allowed: false, retryAfter: 60 };
        while (queue.length && queue[0] <= now - 60_000) queue.shift();
        if (queue.length >= cap) retryAfter = Math.max(retryAfter, Math.ceil((queue[0] + 60_000 - now) / 1000));
      }
      if (retryAfter) return { allowed: false, retryAfter: Math.max(1, retryAfter) };
      for (const key of keys) buckets.get(key).push(now);
      let refunded = false;
      return { allowed: true, retryAfter: 0, refundIgnored() {
        // Only an authenticated IGNORED receipt refunds the participant budget.
        // Transport and per-sender quotas remain consumed, bounding unknown floods.
        if (!apiAdmission || event.kind !== 'human' || refunded) return;
        refunded = true;
        const human = buckets.get('human'), index = human.indexOf(now);
        if (index >= 0) human.splice(index, 1);
      } };
    },
  });
}
