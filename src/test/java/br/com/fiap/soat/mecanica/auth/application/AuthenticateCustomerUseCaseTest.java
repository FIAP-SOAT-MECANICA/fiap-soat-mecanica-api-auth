package br.com.fiap.soat.mecanica.auth.application;

import br.com.fiap.soat.mecanica.auth.domain.Customer;
import br.com.fiap.soat.mecanica.auth.domain.CustomerStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticateCustomerUseCaseTest {
    private final Customer activeCustomer = new Customer(UUID.fromString("b2e5886b-d42f-42ac-a72b-53f230a8baa6"), CustomerStatus.ATIVO);
    private final TokenIssuer tokenIssuer = customer -> new IssuedToken("token-assinado", 3600);

    @Test
    void issuesTokenForAnActiveCustomer() {
        AuthenticateCustomerUseCase useCase = new AuthenticateCustomerUseCase(cpf -> Optional.of(activeCustomer), tokenIssuer);

        IssuedToken token = useCase.execute("529.982.247-25");

        assertEquals("token-assinado", token.accessToken());
    }

    @Test
    void deniesAnUnknownCustomerWithoutIssuingAToken() {
        AuthenticateCustomerUseCase useCase = new AuthenticateCustomerUseCase(cpf -> Optional.empty(), tokenIssuer);

        assertThrows(AccessDeniedException.class, () -> useCase.execute("52998224725"));
    }

    @Test
    void deniesAnInactiveCustomerWithoutIssuingAToken() {
        Customer inactive = new Customer(activeCustomer.id(), CustomerStatus.INATIVO);
        AuthenticateCustomerUseCase useCase = new AuthenticateCustomerUseCase(cpf -> Optional.of(inactive), tokenIssuer);

        assertThrows(AccessDeniedException.class, () -> useCase.execute("52998224725"));
    }
}
