package org.nwh.securejwt.service;

public final class AuthExceptions {

    private AuthExceptions() {
    }

    /** Mensagem sempre igual: nao revela se o utilizador existe ou se esta bloqueado. */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Credenciais invalidas");
        }
    }

    public static class TooManyRequestsException extends RuntimeException {
        public TooManyRequestsException() {
            super("Demasiadas tentativas. Tente novamente dentro de um minuto.");
        }
    }
}
