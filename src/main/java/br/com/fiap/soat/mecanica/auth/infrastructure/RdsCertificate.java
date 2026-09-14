package br.com.fiap.soat.mecanica.auth.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Materializa a CA publica empacotada no unico diretorio gravavel da Lambda. */
final class RdsCertificate {
    private static Path certificate;

    private RdsCertificate() {}

    static synchronized Path path() {
        if (certificate == null) {
            try (var input = RdsCertificate.class.getResourceAsStream("/rds-global-bundle.pem")) {
                if (input == null) throw new IllegalStateException("Bundle CA RDS ausente no JAR");
                Path temporary = Files.createTempFile("auth-rds-ca-", ".pem");
                try {
                    Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    temporary.toFile().deleteOnExit();
                    certificate = temporary;
                } catch (IOException error) {
                    Files.deleteIfExists(temporary);
                    throw error;
                }
            } catch (IOException error) {
                throw new IllegalStateException("Nao foi possivel carregar CA RDS", error);
            }
        }
        return certificate;
    }
}
