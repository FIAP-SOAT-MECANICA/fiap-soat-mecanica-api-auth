package br.com.fiap.soat.mecanica.auth.domain;

import java.util.UUID;

public record Customer(UUID id, CustomerStatus status) {
}
