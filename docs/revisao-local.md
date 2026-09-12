# Revisão e execução local — 12/09/2026

## Correções verificadas

- Requisições inválidas e CPF inválido são rejeitados antes da inicialização dos serviços AWS.
- O handler aceita eventos com corpo Base64 e rejeita Base64 inválido, JSON `null`, tipo incorreto, campos extras e conteúdo após o objeto JSON.
- Falhas internas de configuração retornam `500 INTERNAL_ERROR`; falhas de consulta continuam retornando `503`.
- O cliente Secrets Manager é inicializado somente quando necessário, permitindo instanciar o handler sem região ou credenciais locais.
- O JAR preserva os descritores `META-INF/services`, conforme a [documentação do Maven Shade](https://maven.apache.org/plugins/maven-shade-plugin/examples/resource-transformers.html).
- `backend.hcl` está ignorado e a versão mínima do Terraform é 1.10, compatível com o [locking nativo S3](https://docs.aws.amazon.com/prescriptive-guidance/latest/terraform-aws-provider-best-practices/backend.html).

## Resultados

- Java Temurin 21.0.11 e Maven 3.9.14: `mvn --batch-mode clean verify` aprovado.
- 24 testes unitários e um teste de execução do JAR em JVM separada aprovados.
- JAR executado também com PostgreSQL 18.4 isolado em `127.0.0.1:55439`: cliente ativo retornou 200 com JWT verificável; ausente e inativo retornaram 401.
- Assinatura HS256, UUID em `sub`, issuer, audience, principal, JTI e validade de uma hora verificados sem imprimir tokens.
- Terraform 1.15.8: `fmt -check -recursive`, `init -backend=false -input=false` e `validate` aprovados.

## Repetir o teste com banco local

Use exclusivamente uma instância descartável local. Crie nela o usuário e banco `auth_review`, sem senha, acessíveis somente via loopback. A fixture abaixo pertence somente ao teste, não ao banco compartilhado do projeto:

```sql
CREATE TABLE clientes (id UUID PRIMARY KEY, cpf VARCHAR(11) UNIQUE, status VARCHAR(20));
INSERT INTO clientes VALUES ('b2e5886b-d42f-42ac-a72b-53f230a8baa6', '52998224725', 'ATIVO');
```

Depois de preparar a fixture no banco `auth_review`, execute no PowerShell, substituindo a porta pela da instância descartável:

```powershell
$env:AUTH_TEST_DB_PORT = '55439'
mvn --batch-mode clean verify
Remove-Item Env:AUTH_TEST_DB_PORT
```

O teste altera temporariamente o status da fixture para `INATIVO` e restaura `ATIVO`. O relatório `target/packaged-lambda-smoke.log` deve conter `POSTGRES_AUTH_OK` e `PACKAGED_LAMBDA_OK`. Sem `AUTH_TEST_DB_PORT`, usa um repositório em memória. Encerre a instância descartável ao terminar.

## Limites desta validação

Não houve deploy, acesso ao Secrets Manager real, execução de GitHub Actions ou chamada ao API Gateway publicado. VPC, IAM, segredos, backend remoto e aceitação do token pela API principal dependem do ambiente integrado e continuam no checklist de aceite. O projeto é uma Lambda, sem servidor HTTP local próprio.

O Maven informa sobreposição de recursos de licença/manifesto e o SDK informa ausência de implementação SLF4J; essas mensagens não impediram os testes. Os logs estruturados da aplicação usam stdout diretamente.
