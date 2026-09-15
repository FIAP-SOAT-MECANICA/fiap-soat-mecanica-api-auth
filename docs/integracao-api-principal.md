# Contrato de integração — Auth Serverless e API principal

Este documento fixa o que a API principal precisa aceitar para que o fluxo CPF seja demonstrável sem acoplá-la à Lambda. A Auth não altera nem emite tokens de usuários internos; ela emite apenas tokens de clientes.

## Token recebido pela API principal

Após `POST /auth/cpf`, a aplicação recebe `accessToken` e o usa como `Authorization: Bearer <accessToken>`. Antes de liberar uma rota destinada a cliente, o filtro deve verificar:

| Item | Regra |
| --- | --- |
| Assinatura | HS256 com a mesma chave HMAC Base64 definida no segredo JWT compartilhado. |
| `sub` | UUID de um cliente existente; nunca interpretar como e-mail. |
| `principal_type` | Exatamente `CLIENTE`. |
| `iss` | Mesmo valor de `JWT_ISSUER`, atualmente `fiap-soat-mecanica-auth`. |
| `aud` | Mesmo valor de `JWT_AUDIENCE`, normalmente `fiap-soat-mecanica-api`. |
| Tempo | Exigir `iat` e `exp`; rejeitar token expirado. |
| Identidade | Preservar `jti` para auditoria sem gravar o token inteiro. |

O filtro atual da API principal carrega um `UserDetails` pelo assunto do token. Isso funciona para os tokens internos cujo `sub` é e-mail, mas não para o token desta Lambda, cujo `sub` é UUID. A integração deve ter um caminho próprio para `principal_type=CLIENTE`, sem tentar consultar o cliente como se fosse usuário interno.

## Configuração compartilhada

| Dado | Responsável por fornecer | Consumidores |
| --- | --- | --- |
| Segredo JWT HMAC Base64 | Auth gera por padrão; ou grupo fornece `JWT_SECRET`/segredo externo | Auth e API principal |
| `JWT_ISSUER` | Auth (valor padrão documentado) | Auth e API principal |
| `JWT_AUDIENCE` | Grupo | Auth e API principal |
| URL da Auth | Terraform deste repositório (`authenticate_customer_url`) | Cliente/demonstração |
| Acesso ao PostgreSQL e segredo do banco | Infraestrutura de banco | Auth |

O segredo JWT deve ser igual nos dois componentes, mas seu valor não pode entrar em Git, logs, manifestos Kubernetes ou vídeo. Cada workload deve recebê-lo pelo mecanismo de segredos do ambiente.

## Status da integração

A API principal já valida o token de cliente (chave própria lida do Secrets
Manager deste repo, `iss`/`aud`/`principal_type` conferidos) e expõe
`GET /clientes/me`, autenticado com esse token, sem tratar o UUID como e-mail.
Um token inválido (assinatura alterada, expirado, issuer/audience incorretos)
é rejeitado sem derrubar a API com erro 500.

Ainda pendente: a resposta da API principal não propaga `x-correlation-id`
recebido do Auth — isso faz parte do trabalho de observabilidade estruturada
da aplicação, não da validação do token em si.

## Roteiro mínimo de aceite integrado

1. Criar ou usar um cliente `ATIVO` com CPF válido no banco compartilhado.
2. Chamar `POST /auth/cpf` e guardar o token apenas na sessão de teste.
3. Chamar uma rota de cliente da API principal com o token e confirmar sucesso.
4. Repetir sem token, com assinatura adulterada, `iss`/`aud` incompatíveis, `principal_type` diferente e token expirado; todas devem ser rejeitadas.
5. Usar o `x-correlation-id` retornado para a rastreabilidade entre chamadas. Para o log de acesso do API Gateway, cruzar `requestId` com `apiGatewayRequestId` da Lambda.

## Pendência externa para implantação

O preflight do Auth descobre RDS `mecanica-db-prod`, segredo `mecanica-db-credentials`, VPC/subnets, SG autorizado e `LabRole` usando a conta da sessão. Basta disponibilizar as credenciais temporárias e o bucket compartilhado já configurado na organização, desde que os recursos de DB/rede existam. O Terraform prepara o segredo JWT e o endpoint privado do Secrets Manager quando não compartilhados.

O segredo DB precisa seguir o JSON `host`, `port` inteiro, `dbname`, `username`, `password`. As migrations e clientes são responsabilidade da aplicação; o Auth só faz SELECT, sem criar tabela, inserir cliente ou modificar banco do grupo. Homologação e produção têm recursos/state Auth separados; o banco atual do grupo é compartilhado e isso não cria isolamento de dados entre ambientes.

Após deploy, `jwt_secret_arn` é o ARN que a API deve usar para obter o campo JSON `secret`. Esse campo já é base64: decodifique uma única vez ao construir a chave HS256. Se o grupo fornecer `JWT_SECRET` ao Auth, a API deve receber exatamente o mesmo valor. Não grave o segredo em manifesto versionado.

Resolvido: `JwtAuthenticationFilter` na API agora tenta validar o token como cliente (chave própria, `iss`/`aud`/`principal_type` conferidos) antes de cair no fluxo por e-mail dos usuários internos, e `GET /clientes/me` autoriza e retorna os dados do cliente pelo UUID do token, sem convertê-lo em identidade de funcionário.
