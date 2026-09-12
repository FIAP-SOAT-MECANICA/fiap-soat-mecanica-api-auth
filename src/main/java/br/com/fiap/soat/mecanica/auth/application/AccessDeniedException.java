package br.com.fiap.soat.mecanica.auth.application;

public final class AccessDeniedException extends RuntimeException {
    public AccessDeniedException() {
        super("Acesso não autorizado");
    }
}
