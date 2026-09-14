package br.com.fiap.soat.mecanica.auth.infrastructure;

public record DatabaseConfig(String host, int port, String database, String username, String password, String sslMode) {
    public DatabaseConfig(String host, int port, String database, String username, String password) {
        this(host, port, database, username, password, "verify-full");
    }

    public DatabaseConfig {
        if (host == null || !host.matches("[A-Za-z0-9.-]+") || port < 1 || port > 65535
                || database == null || !database.matches("[A-Za-z0-9_-]+")
                || username == null || username.isBlank() || password == null
                || !java.util.Set.of("require", "verify-full", "disable").contains(sslMode)) {
            throw new IllegalArgumentException("Configuracao PostgreSQL invalida");
        }
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database
                + "?sslmode=" + sslMode + "&connectTimeout=3&socketTimeout=5&loginTimeout=5&tcpKeepAlive=true"
                + (sslMode.equals("verify-full") ? "&sslrootcert=" + java.net.URLEncoder.encode(
                        RdsCertificate.path().toString(), java.nio.charset.StandardCharsets.UTF_8) : "");
    }

    @Override
    public String toString() {
        return "DatabaseConfig[redacted]";
    }
}
