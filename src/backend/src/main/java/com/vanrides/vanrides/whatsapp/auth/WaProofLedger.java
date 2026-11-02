package com.vanrides.vanrides.whatsapp.auth;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaProofLedger {
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    private final WaSettings settings;
    public WaProofLedger(JdbcTemplate jdbc, WaClock clock, WaSettings settings) {
        this.jdbc = jdbc; this.clock = clock; this.settings = settings;
    }
    /** Committed independently so returning a nonce challenge does not roll it back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String issue(String jkt) {
        Instant now = clock.now();
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, "wa-proof:" + jkt);
        jdbc.update("DELETE FROM wa.proof_nonce WHERE nonce_hash IN (SELECT nonce_hash FROM wa.proof_nonce WHERE expires_at < ? LIMIT 100)", Timestamp.from(now));
        jdbc.update("DELETE FROM wa.proof_replay WHERE (jkt,jti) IN (SELECT jkt,jti FROM wa.proof_replay WHERE expires_at < ? LIMIT 100)", Timestamp.from(now));
        Long count = jdbc.queryForObject("SELECT count(*) FROM wa.proof_nonce WHERE jkt=? AND expires_at>? AND consumed_at IS NULL", Long.class, jkt, Timestamp.from(now));
        if (count != null && count >= 40) throw new WaProblem(WaProblem.Code.RATE_LIMITED);
        String nonce = WaCrypto.randomToken();
        jdbc.update("INSERT INTO wa.proof_nonce(nonce_hash,jkt,environment,expires_at,created_at) VALUES (?,?,?,?,?)",
            WaCrypto.digest(nonce), jkt, settings.environment(), Timestamp.from(now.plusSeconds(120)), Timestamp.from(now));
        return nonce;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean consume(WaDpopVerifier.Proof proof) {
        if (proof.nonce() == null) return false;
        Instant now = clock.now();
        int changed = jdbc.update("UPDATE wa.proof_nonce SET consumed_at=? WHERE nonce_hash=? AND jkt=? AND environment=? AND consumed_at IS NULL AND expires_at>?",
            Timestamp.from(now), WaCrypto.digest(proof.nonce()), proof.jkt(), settings.environment(), Timestamp.from(now));
        if (changed != 1) return false;
        int inserted = jdbc.update("INSERT INTO wa.proof_replay(jkt,jti,expires_at) VALUES (?,?,?) ON CONFLICT DO NOTHING",
            proof.jkt(), proof.jti(), Timestamp.from(now.plusSeconds(300)));
        if (inserted != 1) throw WaProblem.unauthorized();
        return true;
    }
}
