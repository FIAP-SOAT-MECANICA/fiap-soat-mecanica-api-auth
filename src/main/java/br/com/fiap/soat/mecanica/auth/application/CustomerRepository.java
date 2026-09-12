package br.com.fiap.soat.mecanica.auth.application;

import br.com.fiap.soat.mecanica.auth.domain.Cpf;
import br.com.fiap.soat.mecanica.auth.domain.Customer;

import java.util.Optional;

public interface CustomerRepository {
    Optional<Customer> findByCpf(Cpf cpf);
}
