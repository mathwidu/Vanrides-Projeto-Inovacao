package com.vanrides.vanrides.exception;

import org.springframework.http.HttpStatus;

public class LoginIdentityConflictException extends ApiStatusException {

    public LoginIdentityConflictException() {
        super(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_IN_USE",
                "O e-mail informado já está em uso."
        );
    }
}
