package com.vanrides.vanrides.whatsapp.privacy;

/** Explicit local policy. Constructing the module does not activate retention. */
public record WaPrivacyPolicy(boolean enabled, boolean syntheticOnly) {
    public static WaPrivacyPolicy disabled() { return new WaPrivacyPolicy(false, true); }
    public static WaPrivacyPolicy syntheticTests() { return new WaPrivacyPolicy(true, true); }
}
