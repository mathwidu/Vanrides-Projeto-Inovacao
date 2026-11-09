package com.vanrides.vanrides.whatsapp.auth;

import com.vanrides.vanrides.filtros.BoundedRateLimitStore;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import org.springframework.stereotype.Component;

/** Dedicated partitions reuse the tested bounded store without changing legacy quotas. */
@Component
public final class WaRateLimiter {
    private final BoundedRateLimitStore general = new BoundedRateLimitStore(1200, 60, 4096);
    private final BoundedRateLimitStore proof = new BoundedRateLimitStore(60, 60, 4096);
    private final BoundedRateLimitStore team = new BoundedRateLimitStore(60, 60, 4096);

    void acquireTeam(String key) {
        if (!team.acquire(BoundedRateLimitStore.Route.LOGIN, key).allowed())
            throw new WaProblem(WaProblem.Code.RATE_LIMITED);
    }

    public void acquire(String key, boolean unauthenticatedProof) {
        if (!general.acquire(BoundedRateLimitStore.Route.LOGIN, key).allowed()
                || unauthenticatedProof && !proof.acquire(BoundedRateLimitStore.Route.BOOTSTRAP, key).allowed())
            throw new WaProblem(WaProblem.Code.RATE_LIMITED);
    }
}
