package com.vanrides.vanrides.whatsapp.api;

/** Closed public errors. Never wrap a provider exception or echo input here. */
public final class WaProblem extends RuntimeException {
    public enum Code {
        INVALID_PAYLOAD(400), INVALID_CREDENTIAL(401), PROOF_NONCE_REQUIRED(401),
        ACTION_NOT_ALLOWED(403), RESOURCE_NOT_ACCESSIBLE(404),
        VERSION_CONFLICT(409), IDEMPOTENCY_CONFLICT(409), ACTIVE_RUN_EXISTS(409),
        INTENT_EXPIRED(410), AMBIGUOUS_OCCURRENCE(422), POINT_NOT_APPROVED(422),
        RATE_LIMITED(429), DEPENDENCY_UNAVAILABLE(503);
        private final int status;
        Code(int status) { this.status = status; }
        public int status() { return status; }
    }
    private final Code code;
    private final String nonce;
    public WaProblem(Code code) { this(code, null); }
    public WaProblem(Code code, String nonce) {
        super(code.name(), null, false, false);
        this.code = code; this.nonce = nonce;
    }
    public Code code() { return code; }
    public String nonce() { return nonce; }
    public static WaProblem invalid() { return new WaProblem(Code.INVALID_PAYLOAD); }
    public static WaProblem unauthorized() { return new WaProblem(Code.INVALID_CREDENTIAL); }
    public static WaProblem denied() { return new WaProblem(Code.ACTION_NOT_ALLOWED); }
    public static WaProblem missing() { return new WaProblem(Code.RESOURCE_NOT_ACCESSIBLE); }
}
