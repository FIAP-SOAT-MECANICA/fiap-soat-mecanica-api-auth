package br.com.fiap.soat.mecanica.auth.domain;

public record Cpf(String value) {

    public Cpf {
        value = normalize(value);
        if (!isValid(value)) {
            throw new InvalidCpfException();
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidCpfException();
        }
        value = value.trim();
        if (!value.matches("[0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}")) {
            throw new InvalidCpfException();
        }
        return value.replace(".", "").replace("-", "");
    }

    private static boolean isValid(String cpf) {
        if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }

        int firstDigit = checkDigit(cpf, 9, 10);
        int secondDigit = checkDigit(cpf, 10, 11);
        return firstDigit == Character.getNumericValue(cpf.charAt(9))
                && secondDigit == Character.getNumericValue(cpf.charAt(10));
    }

    private static int checkDigit(String cpf, int digits, int weight) {
        int sum = 0;
        for (int index = 0; index < digits; index++) {
            sum += Character.getNumericValue(cpf.charAt(index)) * (weight - index);
        }
        int result = 11 - (sum % 11);
        return result >= 10 ? 0 : result;
    }
}
