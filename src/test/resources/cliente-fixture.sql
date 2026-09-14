-- Fixture sintetica e descartavel. Espelha V2__create.cliente.sql da API em 79f6b3f.
-- Executar exclusivamente no database auth_review criado para os testes.
DO $$ BEGIN
    IF current_database() <> 'auth_review' THEN
        RAISE EXCEPTION 'Fixture permitida apenas no database auth_review';
    END IF;
END $$;
CREATE TABLE clientes (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ATIVO', 'INATIVO')),
    nome VARCHAR(255) NOT NULL,
    cpf VARCHAR(11) UNIQUE,
    cnpj VARCHAR(14) UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    telefone VARCHAR(20),
    usuario_id UUID NOT NULL,
    CHECK ((cpf IS NOT NULL AND cnpj IS NULL) OR (cpf IS NULL AND cnpj IS NOT NULL))
);
CREATE INDEX idx_cliente_usuario_id ON clientes(usuario_id);
INSERT INTO clientes (id, status, nome, cpf, email, usuario_id) VALUES
('b2e5886b-d42f-42ac-a72b-53f230a8baa6', 'ATIVO', 'Fixture Auth', '52998224725',
 'auth-fixture@example.invalid', '00000000-0000-0000-0000-000000000001');
