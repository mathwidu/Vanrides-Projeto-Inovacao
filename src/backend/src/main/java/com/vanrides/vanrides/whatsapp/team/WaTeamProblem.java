package com.vanrides.vanrides.whatsapp.team;

final class WaTeamProblem extends RuntimeException {
    enum Code {
        WHATSAPP_NOT_FOUND(404), ADMISSION_CONFLICT(409), PROFILE_CONFLICT(409),
        INVITE_EXPIRED(409), INVITE_NOT_RENEWABLE(409), INVITE_UNAVAILABLE(409);
        final int status;
        Code(int status) { this.status = status; }
    }
    final Code code;
    WaTeamProblem(Code code) { super(code.name(), null, false, false); this.code = code; }
}
