package br.com.fiap.soat.mecanica.auth.infrastructure;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

public final class AwsSecretsManagerReader implements SecretReader {
    private SecretsManagerClient client;

    public AwsSecretsManagerReader() {
    }

    AwsSecretsManagerReader(SecretsManagerClient client) {
        this.client = client;
    }

    @Override
    public String read(String secretArn) {
        String secret = client().getSecretValue(GetSecretValueRequest.builder().secretId(secretArn).build())
                .secretString();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Segredo AWS não possui SecretString");
        }
        return secret;
    }

    private synchronized SecretsManagerClient client() {
        if (client == null) {
            client = SecretsManagerClient.builder().overrideConfiguration(config -> config
                    .apiCallTimeout(java.time.Duration.ofSeconds(4))
                    .apiCallAttemptTimeout(java.time.Duration.ofSeconds(2))).build();
        }
        return client;
    }
}
