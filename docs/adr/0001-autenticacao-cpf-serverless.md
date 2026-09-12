# ADR 0001 — Autenticação de cliente por CPF em Lambda

- Status: aceito
- Data: 2026-09-11

## Contexto

O desafio exige uma Function Serverless que valide CPF, consulte existência e status do cliente e forneça JWT às APIs protegidas. A aplicação existente já possui autenticação de usuários internos por e-mail e senha; esse fluxo não identifica clientes por CPF nem pode ser reutilizado como contrato de cliente sem adaptação.

## Decisão

Este repositório contém uma Lambda Java exposta por HTTP API Gateway em `POST /auth/cpf`. A função normaliza e valida o CPF, consulta diretamente a tabela `clientes` no PostgreSQL e só autentica o status `ATIVO`.

O JWT é assinado com HS256 usando uma chave Base64 no Secrets Manager. O `sub` contém o UUID do cliente, e não CPF. Também inclui `principal_type=CLIENTE`, `iss`, `aud`, `iat`, `exp` e `jti`. A API de oficina deve validar todos esses campos em rotas de cliente.

A Lambda fica em VPC privada, recebe somente os ARNs dos segredos e usa IAM de menor privilégio para lê-los. Os logs são JSON, recebem ou criam `x-correlation-id` e não registram CPF, token ou segredo.

## Consequências

- A aplicação principal precisa reconhecer o principal `CLIENTE` e não tratar o UUID de `sub` como e-mail.
- O banco, a Lambda e a aplicação precisam compartilhar conectividade de VPC e o mesmo segredo JWT ou um mecanismo de chave assimétrica acordado pelo grupo.
- Cliente ausente e inativo retornam a mesma resposta `401`, evitando enumeração de clientes.
- Uma falha do banco impede a emissão de token e responde `503`; não há autenticação em modo degradado.

## Alternativas não adotadas

- Reaproveitar o login e-mail/senha da aplicação: não atende ao fluxo CPF exigido.
- Incluir CPF no token: amplia exposição de dado pessoal sem necessidade para autorização.
- Usar credenciais estáticas AWS no GitHub Actions: OIDC reduz o material secreto de CI/CD.
