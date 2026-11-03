package com.vanrides.vanrides.whatsapp.operator;

/** Never retain an input value, filesystem path, SQL exception or secret-bearing cause. */
public final class WaOperatorFailure extends RuntimeException {
    public WaOperatorFailure() { super("WA_OPERATOR_FAILED", null, false, false); }
}
