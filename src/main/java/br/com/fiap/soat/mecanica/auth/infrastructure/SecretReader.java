package br.com.fiap.soat.mecanica.auth.infrastructure;

public interface SecretReader {
    String read(String secretArn);
}
