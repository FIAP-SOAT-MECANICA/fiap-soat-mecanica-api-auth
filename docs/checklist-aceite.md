# Checklist de aceite e demonstração

Os itens marcados nesta seção foram comprovados localmente por testes automatizados ou validações de código. Os itens que dependem de ambiente integrado, infraestrutura compartilhada ou API principal permanecem abertos até a demonstração final.

## Função de autenticação

- [x] CPF válido de um cliente `ATIVO` retorna `200`, `Bearer` e JWT verificável.
- [x] CPF com máscara é aceito e normalizado.
- [x] CPF com tamanho, dígito verificador ou sequência repetida inválidos retorna `400 INVALID_CPF`.
- [x] CPF de cliente inexistente retorna `401 ACCESS_DENIED`.
- [x] CPF de cliente `INATIVO` retorna `401 ACCESS_DENIED`.
- [x] Banco indisponível retorna `503 CUSTOMER_DIRECTORY_UNAVAILABLE`, sem token.
- [x] A resposta possui `Cache-Control: no-store` e `x-correlation-id`.

## JWT e proteção integrada

- [x] Token contém UUID em `sub`, `principal_type=CLIENTE`, `iss`, `aud`, `iat`, `exp` e `jti`.
- [ ] Rota de cliente na API principal aceita token válido e devolve/propaga a correlação.
- [ ] Mesma rota rejeita token ausente, assinatura alterada, expirado, issuer/audience incorretos e `principal_type` inadequado.
- [ ] Nenhuma rota sensível da aplicação permanece pública por configuração acidental.

## Infraestrutura, pipeline e documentação

- [x] `mvn verify` passa.
- [x] `terraform -chdir=infra fmt -check -recursive` e `terraform -chdir=infra validate` passam.
- [ ] O bucket S3 de state existe, está cifrado e usa uma chave por ambiente.
- [ ] Os três secrets temporários AWS Academy, a região e o ARN do `LabRole` estão configurados no ambiente GitHub antes do deploy.
- [ ] O workflow CI passa em pull request; merge em `homolog` e `main` executa deploy nos ambientes correspondentes.
- [ ] Proteção de `main` e `homolog` exige pull request e checks.
- [x] README, OpenAPI, coleção Postman, RFC, ADR e diagramas estão atualizados.

## Evidências locais registradas em 12/09/2026

- `AuthHandlerTest` valida o contrato HTTP, CPF com máscara, CPF inválido, cliente inativo, falha de diretório, `Cache-Control` e correlação.
- `AuthenticateCustomerUseCaseTest` cobre cliente ativo, inexistente e inativo; `JwtTokenIssuerTest` verifica o contrato do JWT.
- `PackagedLambdaIT` executa o JAR final em uma JVM separada e valida a emissão e leitura do JWT sem imprimir token ou segredo.
- A pipeline de CI executa os mesmos comandos de Maven e Terraform. A evidência do workflow em Pull Request continua pendente até a PR desta branch ser aberta e concluída.

## Evidências para o vídeo

1. Mostre o merge de um pull request com o check CI aprovado.
2. Mostre o workflow de deploy concluído e o endpoint publicado.
3. Faça as chamadas de sucesso, CPF inválido e cliente não autorizado sem exibir dados reais.
4. Use o token mascarado para uma rota protegida e mostre a rejeição sem token.
5. Mostre o `requestId` do log do API Gateway e o mesmo valor em `apiGatewayRequestId` no log JSON da Lambda; use `x-correlation-id` na resposta e entre serviços, sem CPF/token nos logs.
6. Mostre o diagrama e explique a separação entre Auth, aplicação, banco e infraestrutura compartilhada.
