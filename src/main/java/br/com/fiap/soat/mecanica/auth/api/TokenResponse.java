package br.com.fiap.soat.mecanica.auth.api;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
