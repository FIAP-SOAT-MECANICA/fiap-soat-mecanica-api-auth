# RFC 0001 — Autenticação de cliente por CPF com Function Serverless

- **Status:** aprovado
- **Data:** 2026-09-12
- **Escopo:** repositório `fiap-soat-mecanica-api-auth` e contrato consumido pela API principal

## 1. Contexto

O Tech Challenge da Fase 3 exige que a autenticação de clientes ocorra por CPF em uma Function Serverless. O fluxo deve validar o documento, consultar a existência e o status do cliente em banco de dados gerenciado e emitir um JWT para acesso posterior às rotas protegidas.

A aplicação principal já possui autenticação de usuários internos. Esse modelo usa outro tipo de identidade e não atende, por si só, à autenticação de clientes. A solução também precisa permitir deploy automatizado, manter segredos fora do código, produzir logs estruturados com correlação e ser demonstrável de forma independente da aplicação executada no Kubernetes.

## 2. Problema a resolver

É necessário definir um contrato seguro e estável entre a entrada pública de autenticação, a base de clientes e as APIs protegidas. O contrato deve:

- aceitar CPF com ou sem máscara e rejeitar formato, dígitos verificadores e sequências inválidas;
- autenticar apenas clientes existentes e com status `ATIVO`;
- não expor se um CPF inexistente ou inativo foi a causa da negação;
- emitir uma identidade de cliente que a API principal possa validar sem consultar a Lambda;
- impedir que CPF, token, senhas e segredos apareçam em logs, código ou pipeline;
- permitir rastrear a chamada entre API Gateway, Lambda e serviços consumidores.

## 3. Decisão proposta

Será usada uma AWS Lambda Java 21 exposta por AWS API Gateway HTTP API no endpoint `POST /auth/cpf`.

1. O API Gateway recebe a chamada e a encaminha para a Lambda no formato HTTP API payload 2.0.
2. A Lambda normaliza o CPF, aplica a validação de tamanho, sequência repetida e dígitos verificadores.
3. Para um CPF válido, a Lambda lê a configuração de banco e a chave de assinatura no AWS Secrets Manager.
4. A Lambda consulta a tabela `clientes` no PostgreSQL e aceita somente o status `ATIVO`.
5. Quando autorizada, ela emite um JWT `HS256` e retorna `200` com `accessToken`, `tokenType` e `expiresIn`.
6. A API principal valida o token localmente antes de liberar rotas de cliente.

O contrato HTTP completo está em [openapi.yaml](../../openapi.yaml), e o contrato de consumo está em [Integração com a API principal](../integracao-api-principal.md).

## 4. Contrato de identidade e token

O JWT representa somente um cliente. Seus campos mínimos são:

| Campo | Regra |
| --- | --- |
| algoritmo | `HS256` |
| `sub` | UUID do cliente; CPF não é incluído |
| `principal_type` | `CLIENTE` |
| `iss` | `fiap-soat-mecanica-auth`, salvo configuração explícita |
| `aud` | valor comum acordado para a API principal |
| `iat` e `exp` | obrigatórios; validade padrão de 3.600 segundos |
| `jti` | identificador único para auditoria |

O token deve ser enviado pelas APIs consumidoras em `Authorization: Bearer <token>`. A aplicação principal deve verificar assinatura, expiração, emissor, audiência e `principal_type=CLIENTE`. Ela não deve interpretar o `sub` desse token como e-mail de usuário interno.

CPF inexistente e cliente inativo retornam ambos `401 ACCESS_DENIED`. CPF inválido retorna `400 INVALID_CPF`; indisponibilidade do diretório de clientes retorna `503 CUSTOMER_DIRECTORY_UNAVAILABLE`; falha de configuração interna retorna `500 INTERNAL_ERROR` sem detalhes sensíveis.

## 5. Segurança, privacidade e observabilidade

Os segredos de banco e a chave JWT são armazenados no AWS Secrets Manager. A Lambda recebe somente seus ARNs por variáveis de ambiente e usa uma política IAM limitada à leitura desses segredos, aos logs necessários e às interfaces de rede da Lambda.

A função é implantada em sub-redes privadas e seu security group deve permitir apenas TCP/5432 para o banco. Como o segredo é lido em execução, a rede precisa de saída HTTPS controlada ou de endpoint de interface para o Secrets Manager.

Os logs da Lambda são JSON e não contêm CPF, token, senha, segredo ou string de conexão. A função devolve `x-correlation-id`; quando fornecido pelo cliente, o mesmo identificador é preservado. O ID de requisição do API Gateway também é registrado como `apiGatewayRequestId`, permitindo correlacionar o log de acesso do gateway com o log da Lambda.

## 6. Alternativas avaliadas

### Reaproveitar o login de usuários internos

Rejeitada. O requisito determina autenticação por CPF de cliente e o identificador dos usuários internos é diferente. Misturar os dois tipos de principal tornaria a autorização ambígua.

### Emitir token com CPF como assunto

Rejeitada. O CPF é dado pessoal e não é necessário para autorização. O UUID do cliente fornece uma identidade estável com menor exposição.

### Validar o token pela Lambda a cada chamada protegida

Rejeitada. A verificação local da assinatura pela API principal reduz latência e dependência síncrona da Lambda. A Lambda fica responsável apenas pela emissão do token.

### Usar chaves AWS estáticas na pipeline

Rejeitada para a conta final. O workflow usa OIDC do GitHub Actions e um papel AWS restrito. No Learner Lab, onde o IAM é limitado, o Terraform reutiliza temporariamente o `LabRole` existente apenas para testes manuais.

### Usar token assimétrico

Não adotada nesta fase. JWT assimétrico eliminaria o compartilhamento da chave de verificação, mas exige distribuição e rotação de chaves públicas. `HS256` com segredo compartilhado no gerenciador de segredos atende ao escopo atual, desde que os dois workloads recebam o segredo por mecanismos protegidos.

## 7. Consequências e responsabilidades

Esta decisão cria uma separação explícita entre autenticação de cliente e autenticação interna. A API principal precisa implementar um caminho de autorização para `CLIENTE`; o repositório de banco precisa disponibilizar cliente ativo, segredo e conectividade; a infraestrutura Kubernetes precisa fornecer a rede privada e os grupos de segurança compatíveis.

O repositório Auth mantém a Lambda, o contrato OpenAPI, o Terraform de Lambda/API Gateway, a pipeline e a documentação desta decisão. A infraestrutura compartilhada mantém VPC, sub-redes, state remoto, segredos, banco e o papel OIDC da conta definitiva.

## 8. Estratégia de entrega e validação

As alterações passam por Pull Request com a pipeline de CI executando `mvn verify`, `terraform fmt -check` e `terraform validate`. Os merges em `homolog` e `main` disparam o workflow de deploy para homologação e produção, respectivamente, usando state remoto separado por ambiente.

O aceite integrado deve demonstrar: CPF válido de cliente ativo emitindo JWT; CPF inválido; cliente ausente ou inativo; acesso autorizado e rejeitado a uma rota de cliente; logs correlacionados entre API Gateway e Lambda; e execução aprovada das pipelines. O roteiro detalhado está em [Checklist de aceite](../checklist-aceite.md).

## 9. Critérios para revisão futura

Esta RFC deve ser revisada se houver necessidade de revogação imediata de tokens, integração com outro provedor de identidade, múltiplas APIs consumidoras, auditoria centralizada ou adoção de assinatura assimétrica. Nesses casos, a rotação de chaves, os públicos permitidos e o formato do principal devem ser discutidos antes de alterar o contrato.
