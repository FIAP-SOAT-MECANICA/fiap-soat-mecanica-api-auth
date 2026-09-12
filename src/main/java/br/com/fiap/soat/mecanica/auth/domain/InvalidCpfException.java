package br.com.fiap.soat.mecanica.auth.domain;

public final class InvalidCpfException extends RuntimeException {
    public InvalidCpfException() {
        super("CPF inválido");
    }
}
