# Checklist de aceite e demonstração

## Função de autenticação

- [ ] CPF válido de um cliente `ATIVO` retorna `200`, `Bearer` e JWT verificável.
- [ ] CPF com máscara é aceito e normalizado.
- [ ] CPF com tamanho, dígito verificador ou sequência repetida inválidos retorna `400 INVALID_CPF`.
- [ ] CPF de cliente inexistente retorna `401 ACCESS_DENIED`.
- [ ] CPF de cliente `INATIVO` retorna `401 ACCESS_DENIED`.
- [ ] Banco indisponível retorna `503 CUSTOMER_DIRECTORY_UNAVAILABLE`, sem token.
- [ ] A resposta possui `Cache-Control: no-store` e `x-correlation-id`.

## JWT e proteção integrada

- [ ] Token contém UUID em `sub`, `principal_type=CLIENTE`, `iss`, `aud`, `iat`, `exp` e `jti`.
- [ ] Rota de cliente na API principal aceita token válido e devolve/propaga a correlação.
- [ ] Mesma rota rejeita token ausente, assinatura alterada, expirado, issuer/audience incorretos e `principal_type` inadequado.
- [ ] Nenhuma rota sensível da aplicação permanece pública por configuração acidental.

## Infraestrutura, pipeline e documentação

- [ ] `mvn verify` passa.
- [ ] `terraform -chdir=infra fmt -check -recursive` e `terraform -chdir=infra validate` passam.
- [ ] O bucket S3 de state existe, está cifrado e usa uma chave por ambiente.
- [ ] O papel OIDC do GitHub permite somente o repositório/ambiente esperado.
- [ ] O workflow CI passa em pull request; merge em `homolog` e `main` executa deploy nos ambientes correspondentes.
- [ ] Proteção de `main` e `homolog` exige pull request e checks.
- [ ] README, OpenAPI, coleção Postman, ADR e diagrama estão atualizados.

## Evidências para o vídeo

1. Mostre o merge de um pull request com o check CI aprovado.
2. Mostre o workflow de deploy concluído e o endpoint publicado.
3. Faça as chamadas de sucesso, CPF inválido e cliente não autorizado sem exibir dados reais.
4. Use o token mascarado para uma rota protegida e mostre a rejeição sem token.
5. Mostre o `requestId` do log do API Gateway e o mesmo valor em `apiGatewayRequestId` no log JSON da Lambda; use `x-correlation-id` na resposta e entre serviços, sem CPF/token nos logs.
6. Mostre o diagrama e explique a separação entre Auth, aplicação, banco e infraestrutura compartilhada.
