package br.com.fiap.soat.mecanica.auth.infrastructure;

import br.com.fiap.soat.mecanica.auth.application.CustomerRepository;
import br.com.fiap.soat.mecanica.auth.domain.Cpf;
import br.com.fiap.soat.mecanica.auth.domain.Customer;
import br.com.fiap.soat.mecanica.auth.domain.CustomerStatus;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class PostgresCustomerRepository implements CustomerRepository {
    private static final String FIND_CUSTOMER = """
            SELECT id, status
            FROM clientes
            WHERE cpf = ?
            LIMIT 1
            """;

    private final DatabaseConfig config;

    public PostgresCustomerRepository(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public Optional<Customer> findByCpf(Cpf cpf) {
        try (Connection connection = DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password());
             PreparedStatement statement = connection.prepareStatement(FIND_CUSTOMER)) {
            statement.setString(1, cpf.value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Customer(
                        result.getObject("id", UUID.class),
                        CustomerStatus.valueOf(result.getString("status"))
                ));
            }
        } catch (SQLException exception) {
            throw new CustomerLookupException(exception);
        }
    }
}
