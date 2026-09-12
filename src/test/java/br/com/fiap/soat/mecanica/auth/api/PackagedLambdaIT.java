package br.com.fiap.soat.mecanica.auth.api;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagedLambdaIT {
    @Test
    void runsThePackagedArtifactInAnIsolatedJvm() throws Exception {
        Path output = Path.of("target", "packaged-lambda-smoke.log");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = Path.of("target", "test-classes") + File.pathSeparator
                + Path.of("target", "auth-lambda.jar");
        ProcessBuilder builder = new ProcessBuilder(java, "-cp", classpath, PackagedLambdaProbe.class.getName());
        builder.environment().put("AWS_EC2_METADATA_DISABLED", "true");
        Process process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Execução do JAR excedeu 30 segundos");
            assertEquals(0, process.exitValue(), () -> readOutput(output));
            assertTrue(readOutput(output).contains("PACKAGED_LAMBDA_OK"));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private String readOutput(Path output) {
        try {
            return Files.readString(output);
        } catch (Exception exception) {
            return exception.toString();
        }
    }
}
