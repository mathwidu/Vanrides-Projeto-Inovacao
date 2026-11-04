/** Bounded rolling minute; observation counters never consume future capacity. */
export function createMinuteLimit(limits) {
  const buckets = new Map(['human', 'status', 'connection', 'A', 'B', 'C', 'D'].map(key => [key, []]));
  let latest = -1;
  return Object.freeze({
    take(event, now) {
      if (!Number.isSafeInteger(now) || now < latest) return { allowed: false, retryAfter: 1 };
      latest = now;
      const keys = event.kind === 'human' ? ['human', event.contact] : [event.kind];
      let retryAfter = 0;
      for (const key of keys) {
        const queue = buckets.get(key);
        const cap = ['A', 'B', 'C', 'D'].includes(key) ? limits.perContact : limits[key];
        if (!queue || !Number.isSafeInteger(cap)) return { allowed: false, retryAfter: 60 };
        while (queue.length && queue[0] <= now - 60_000) queue.shift();
        if (queue.length >= cap) retryAfter = Math.max(retryAfter, Math.ceil((queue[0] + 60_000 - now) / 1000));
      }
      if (retryAfter) return { allowed: false, retryAfter: Math.max(1, retryAfter) };
      for (const key of keys) buckets.get(key).push(now);
      return { allowed: true, retryAfter: 0 };
    },
  });
}
