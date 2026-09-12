package br.com.fiap.soat.mecanica.auth.infrastructure;

public final class CustomerLookupException extends RuntimeException {
    public CustomerLookupException(Throwable cause) {
        super("Falha ao consultar cliente", cause);
    }
}
