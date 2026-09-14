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
- [x] Preflight descobre conta, RDS, segredo DB, VPC/subnets, SG permitido e `LabRole`; bucket vem de Variable da organização.
- [x] Pipeline valida o código antes de deploy; suporta execução manual e states separados para `homolog`/`main`.
- [ ] As três credenciais AWS temporárias estão válidas e acessíveis ao repo na conta escolhida.
- [ ] Deploy AWS passou nos ambientes da entrega; smoke `200` consultou fixture ativa no RDS.
- [ ] Proteção de `main` e `homolog` exige pull request e checks.
- [x] README, OpenAPI, coleção Postman, RFC, ADR e diagramas estão atualizados.

## Evidências da auditoria local em 14/09/2026

- `mvn clean verify`: **40 testes unitários e 1 teste de integração**, sem falhas. `AuthHandlerTest` cobre contrato, JSON duplicado/excedente, CPF, erros e correlação; `ConfigurationTest` cobre segredos, TLS, timeouts e renovação/falha de cache; domínio/caso de uso/JWT possuem testes próprios.
- `PackagedLambdaIT`: JAR final em JVM isolada contra PostgreSQL **18.4 local**, descartável, com schema equivalente à migration da API `79f6b3f`. Cliente ativo gera JWT verificável; inexistente/inativo são negados; configuração de produção recusa PostgreSQL sem TLS. Marcadores: `POSTGRES_AUTH_OK`, `PACKAGED_LAMBDA_OK`. No CI, o serviço usa **PostgreSQL 17**, alinhado ao repo DB.
- Python: **11 testes** da descoberta, incluindo SG antigo da org, RDS indisponível, origem ambígua, seleção de AZs, segredo inválido e preservação do endpoint próprio no redeploy. Nenhuma chamada AWS de escrita.
- Terraform **1.15.8**: `fmt -check -recursive`, `validate` e **3 testes de plano** passaram, com providers simulados. Verificados LabRole sem criação IAM, chave/rede próprias, reutilização de segredo/endpoint e ambiente inválido. Lock inclui Linux e Windows.
- `actionlint` **1.7.12**: workflows sem erros; `git diff --check` sem erros.
- Consulta OSV das **53 dependências runtime**: nenhum aviso conhecido após atualização de PostgreSQL JDBC para **42.7.12** e Jackson para **2.18.9**. Relatório reproduzível em `target/dependency-audit.json`; o CI repete a consulta e falha se encontrar avisos ou não conseguir consultar. Isso não equivale a garantia de ausência de vulnerabilidades.
- Bundle CA RDS dentro do JAR; JDBC usa `verify-full`. Conectividade, certificado efetivo e permissões da conta ainda precisam de teste AWS. O smoke público `400` não comprova acesso ao RDS.

## Alinhamento externo que permanece obrigatório

- Banco/rede: recursos dos repos DB/K8s criados na conta única, migrations aplicadas pela aplicação e fixture ativa cadastrada. O Auth não cria nem modifica esses recursos.
- API principal: aceitar UUID de cliente em `sub`, validar HS256/issuer/audience/expiração/principal e restringir acesso aos dados do cliente. Na revisão `79f6b3f`, o filtro ainda interpreta `sub` como e-mail de usuário interno e o CD ainda usa Kind/GHCR.
- Compartilhar a mesma chave JWT por mecanismo de segredo, sem versionar o valor. Se gerada pelo Auth, usar o ARN informado nos outputs; a chave já está em base64 no campo `secret`.
- GitHub: proteção/Rulesets, acesso do professor e ambientes são configurações da plataforma; não são comprovados por testes de código. Não marcar entrega integrada apenas com o CI local aprovado.

## Evidências para o vídeo

1. Mostre o merge de um pull request com o check CI aprovado.
2. Mostre o workflow de deploy concluído e o endpoint publicado.
3. Faça as chamadas de sucesso, CPF inválido e cliente não autorizado sem exibir dados reais.
4. Use o token mascarado para uma rota protegida e mostre a rejeição sem token.
5. Mostre o `requestId` do log do API Gateway e o mesmo valor em `apiGatewayRequestId` no log JSON da Lambda; use `x-correlation-id` na resposta e entre serviços, sem CPF/token nos logs.
6. Mostre o diagrama e explique a separação entre Auth, aplicação, banco e infraestrutura compartilhada.
