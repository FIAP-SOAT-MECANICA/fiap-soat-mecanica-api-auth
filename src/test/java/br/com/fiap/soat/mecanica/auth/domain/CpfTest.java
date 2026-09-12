package br.com.fiap.soat.mecanica.auth.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CpfTest {
    @Test
    void normalizesAMaskedCpf() {
        assertEquals("52998224725", new Cpf("529.982.247-25").value());
    }

    @Test
    void rejectsAnInvalidVerifier() {
        assertThrows(InvalidCpfException.class, () -> new Cpf("52998224726"));
    }

    @Test
    void rejectsRepeatedDigits() {
        assertThrows(InvalidCpfException.class, () -> new Cpf("11111111111"));
    }
}
