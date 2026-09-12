package br.com.fiap.soat.mecanica.auth.application;

import br.com.fiap.soat.mecanica.auth.domain.Cpf;
import br.com.fiap.soat.mecanica.auth.domain.Customer;
import br.com.fiap.soat.mecanica.auth.domain.CustomerStatus;

public final class AuthenticateCustomerUseCase {
    private final CustomerRepository customerRepository;
    private final TokenIssuer tokenIssuer;

    public AuthenticateCustomerUseCase(CustomerRepository customerRepository, TokenIssuer tokenIssuer) {
        this.customerRepository = customerRepository;
        this.tokenIssuer = tokenIssuer;
    }

    public IssuedToken execute(String document) {
        Cpf cpf = new Cpf(document);
        Customer customer = customerRepository.findByCpf(cpf)
                .orElseThrow(AccessDeniedException::new);

        if (customer.status() != CustomerStatus.ATIVO) {
            throw new AccessDeniedException();
        }

        return tokenIssuer.issue(customer);
    }
}
