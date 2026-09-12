package br.com.fiap.soat.mecanica.auth.infrastructure;

public record DatabaseConfig(String host, int port, String database, String username, String password) {
    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }
}
