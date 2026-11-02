package com.vanrides.vanrides.whatsapp.auth;

import java.util.UUID;

/** Trusted request identity; never deserialized from a request or model output. */
public record WaPrincipal(Kind kind, UUID credentialId, UUID deviceId, UUID driverId,
                          String instanceId, String jkt, long generation, boolean revoked) {
    public enum Kind { CHANNEL, CONVERSATION_WORKER, DELIVERY_WORKER, SYSTEM_JOB, OPERATOR, DEVICE }
}
