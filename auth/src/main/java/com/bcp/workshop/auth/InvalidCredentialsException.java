package com.bcp.workshop.auth;

public class InvalidCredentialsException extends RuntimeException {
    private final String username;

    public InvalidCredentialsException(String username) {
        this.username = username;
    }

    public String username() {
        return username;
    }
}
