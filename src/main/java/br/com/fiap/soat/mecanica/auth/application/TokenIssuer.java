package br.com.fiap.soat.mecanica.auth.application;

import br.com.fiap.soat.mecanica.auth.domain.Customer;

public interface TokenIssuer {
    IssuedToken issue(Customer customer);
}
