package br.com.fiap.soat.mecanica.auth.application;

public record IssuedToken(String accessToken, long expiresInSeconds) {
}
