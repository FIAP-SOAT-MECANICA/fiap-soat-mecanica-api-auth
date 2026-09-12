package br.com.fiap.soat.mecanica.auth.api;

public record ErrorResponse(String code, String message, String correlationId) {
}
