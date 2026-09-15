# Guia de demonstração — Auth Serverless

Responsável: José Manoel. Referência: mensagem/enunciado oficial do professor Thiago S. Adriano, de 04/09/2026, fornecida pelo grupo. Roteiro preparado em 15/09/2026.

O vídeo completo do grupo tem limite de **15 minutos**. Reserve aproximadamente **4 minutos para o Auth**, incluindo a passagem do JWT à aplicação principal. Faça instalações, cadastros e provisionamento antes da gravação.

Este guia descreve como executar a demonstração; não certifica que o deploy ou a integração já passaram. O [checklist de aceite](checklist-aceite.md) registra as evidências disponíveis. Na preparação deste guia, não havia confirmação registrada de deploy completo com `200` no RDS nem de uso do JWT na API principal. Confirme ambos antes de apresentar o fluxo como concluído.

## 1. O que o enunciado exige da sua parte

| Exigência oficial | O que mostrar | Responsabilidade |
| --- | --- | --- |
| API Gateway controlando e roteando requisições | Rota `POST /auth/cpf` integrada à Lambda | José; o roteamento das outras APIs integra a solução do grupo |
| Function Serverless validando CPF, existência e status | Cliente ativo: `200`; CPF inválido: `400`; cliente ausente/inativo: `401` | José |
| JWT válido utilizado em APIs protegidas | Token emitido pelo Auth utilizado com sucesso em uma rota de cliente; acesso sem token rejeitado | José + Lenilson |
| Repositório separado e CI/CD funcional | Repo Auth, PR, CI e execução bem-sucedida de `Deploy AWS` | José |
| Principal protegida e alterações por PR | Regra efetiva de proteção e um PR com checks | José/administrador do repo |
| Deploy automatizado para homologação e produção | Workflows, branches/ambientes e execuções correspondentes | José, com infraestrutura disponível |
| Logs estruturados e correlação | JSON da Lambda e do Gateway referentes à mesma chamada | José; propagação entre componentes é integrada |
| README, diagramas, RFC/ADR e contrato de API | README, sequência de autenticação, RFC, ADR, OpenAPI/collection | José |
| Kubernetes, banco gerenciado, dashboards e traces | Evidências da infraestrutura e da aplicação em execução | Karen, Gabriel e Lenilson, com alinhamento do grupo |

CloudWatch com logs correlacionados comprova essa parte do Auth. **Não substitui** os dashboards e traces em execução exigidos para a solução completa. A sequência de abertura de ordem de serviço e o diagrama global também precisam aparecer na documentação do grupo.

## 2. Preparação antes de gravar

### Ambiente e dados

1. Confirme que todos usam a conta e a região acordadas e que o Learner Lab está ativo.
2. Confirme bucket de state, RDS disponível, secret do banco, rede e `LabRole`. O deploy do Auth não cria RDS nem Kubernetes.
3. Confirme migrations da aplicação no banco da demonstração. Cadastre um **cliente fictício ativo** e outro **inativo**, com CPFs matematicamente válidos. Separe também um CPF válido que comprovadamente não exista nesse banco.
4. Não aplique `src/test/resources/cliente-fixture.sql` no RDS compartilhado: essa fixture pertence ao banco descartável do CI.
5. Confirme com Lenilson a URL exata de uma rota protegida acessível a clientes e a resposta esperada. Não use uma rota pública de healthcheck para provar autenticação.
6. Confirme a mesma chave JWT, `iss`, `aud` e suporte a `principal_type=CLIENTE` e `sub=UUID` na API. A revisão anterior da API interpretava `sub` como e-mail; é necessário verificar a versão efetivamente implantada.

Use dados sintéticos no vídeo. Mantenha o token completo apenas em memória ou em variável local do cliente HTTP; mostre sua presença e os claims sem exibir o bearer completo ou a chave de assinatura. Não abra o valor dos secrets nem envie o token a sites externos para decodificá-lo.

### Abas que devem ficar prontas

- GitHub: README, PR aprovado, CI concluído, `Deploy AWS` concluído e configurações de proteção.
- AWS: API Gateway, Lambda e os grupos de logs do Auth.
- Terminal PowerShell com os comandos abaixo preparados, ou Postman com a collection importada.
- Aplicação principal: rota protegida acordada com Lenilson.
- Documentação: diagrama do README, RFC e ADR.

## 3. Como rodar localmente

Na raiz deste repositório, com Java 21, Maven 3.9+, Python 3 e Terraform **1.15.8** no PATH:

```powershell
java -version
mvn -version
terraform version
mvn --batch-mode clean verify
python -m unittest discover -s scripts -p 'test_*.py' -v
```

Confirme que `mvn -version` também mostra Java 21. `mvn clean verify` gera `target/auth-lambda.jar` e executa os testes; não inicia um servidor HTTP. Este projeto é uma Lambda e não precisa de `spring-boot:run`, Dockerfile ou PostgreSQL local de produção.

Sem `AUTH_TEST_DB_PORT`, a verificação local do JAR usa dados em memória. O CI configura PostgreSQL 17 descartável e testa o JAR contra esse banco. Não apresente um teste local em memória como acesso ao RDS.

Para validar Terraform **em checkout de validação que ainda não foi inicializado com backend remoto**:

```powershell
terraform -chdir=infra fmt -check -recursive
terraform -chdir=infra init -backend=false -lockfile=readonly
terraform -chdir=infra validate
terraform -chdir=infra test
```

Esses testes de plano usam providers simulados e não criam recursos AWS. Se seu diretório já gerencia um state remoto, use outro checkout para essa validação; não reconfigure o backend do deploy durante a gravação.

**Fala sugerida:** “O CI compila em Java 21, executa testes unitários e do artefato empacotado com PostgreSQL descartável, testa a preparação do deploy, consulta vulnerabilidades conhecidas e valida os planos Terraform. O banco de testes não é o banco de produção.”

## 4. Como executar o deploy automatizado

No GitHub, confira o acesso do repo aos três secrets temporários da sessão: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` e `AWS_SESSION_TOKEN`. Confira `TF_STATE_REGION` e `TF_STATE_BUCKET` e, quando necessários, os overrides documentados no README: `RDS_INSTANCE_IDENTIFIER`, `DB_SECRET_ARN` e `ALLOWED_SG_ID`.

Se houver configuração específica da conta de teste no repo/ambiente, revise-a antes de mudar para a conta do grupo: valores locais podem sobrepor os da organização. Não altere secrets da organização para testar apenas este repo.

Configure `AUTH_TEST_CPF` com o CPF fictício ativo do banco da demonstração. Sem ele, o smoke automático testa apenas `400`, o que não comprova a consulta ao RDS.

1. Abra **Actions → Deploy AWS → Run workflow**.
2. Selecione `homolog` para o ambiente `homologation` ou `main` para `production`.
3. Execute e aguarde os jobs de validação e implantação terminarem com sucesso. Push/merge nessas branches também dispara o fluxo.
4. Abra o job **Implantar Lambda e API Gateway**. Mostre preparação das dependências, Terraform e smoke. O resumo fornece a URL completa de autenticação.
5. Confira que o smoke registrou **HTTP 400 OK e HTTP 200 OK**.
6. Para evidenciar os dois ambientes, mantenha execuções reais correspondentes disponíveis. A existência de dois nomes no YAML comprova a estratégia, mas não uma implantação realizada nos dois.

Não espere todo o provisionamento dentro dos quatro minutos do Auth: grave a execução ou o disparo e, após um corte identificado, mostre o resultado da mesma execução e commit. Não substitua evidência de CD por um `terraform apply` manual.

**Fala sugerida:** “As alterações entram por pull request. O workflow valida o código antes de implantar Lambda e API Gateway. Homologação e produção usam ambientes e chaves de state separados. A preparação confere as dependências da conta; o smoke final confirma a chamada pública e a autenticação com o cliente de teste no RDS.”

Só use a última frase quando o smoke `200` estiver comprovado. Se falhar, corrija antes de gravar a versão final.

## 5. Chamadas da demonstração em PowerShell

### 5.1 Preparar as variáveis

Execute na raiz do repo. Preencha os valores nos prompts antes da gravação:

```powershell
$AuthUrl = Read-Host 'URL completa do Auth, terminando em /auth/cpf'
$CpfAtivo = Read-Host 'CPF ficticio ATIVO cadastrado no RDS'
$CpfInativo = Read-Host 'CPF ficticio INATIVO cadastrado no RDS'
$CpfAusente = Read-Host 'CPF valido confirmado como ausente no RDS'
$RotaProtegida = Read-Host 'URL completa da rota de cliente acordada com Lenilson'
$Correlacao = 'tc3-auth-' + [guid]::NewGuid().ToString('N')
$Headers = @{ 'x-correlation-id' = $Correlacao }
```

### 5.2 Cliente ativo: emitir token e mostrar o contrato

```powershell
$Resposta = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $AuthUrl `
    -ContentType 'application/json' -Headers $Headers `
    -Body (@{ cpf = $CpfAtivo } | ConvertTo-Json -Compress)
$Autenticacao = $Resposta.Content | ConvertFrom-Json
$Token = $Autenticacao.accessToken
if ($Resposta.StatusCode -ne 200 -or [string]::IsNullOrWhiteSpace($Token)) {
    throw 'Autenticacao nao comprovada: confira a resposta e os logs.'
}
[pscustomobject]@{
    status = $Resposta.StatusCode
    tokenType = $Autenticacao.tokenType
    expiresIn = $Autenticacao.expiresIn
    accessToken = '[presente; valor ocultado]'
    correlationId = $Resposta.Headers['x-correlation-id']
    cacheControl = $Resposta.Headers['Cache-Control']
} | Format-List
```

**Mostrar:** `200`, `Bearer`, expiração configurada, correlação e `no-store`.

**Falar:** “O Gateway encaminha o CPF para a Lambda. A função valida os dígitos, consulta a existência e o status no PostgreSQL e só emite o JWT quando o cliente está ativo.”

### 5.3 Mostrar os claims sem publicar o token

```powershell
function Read-JwtPart([string]$Part) {
    $Base64 = $Part.Replace('-', '+').Replace('_', '/')
    $Base64 = $Base64.PadRight($Base64.Length + ((4 - $Base64.Length % 4) % 4), '=')
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Base64)) | ConvertFrom-Json
}
$Partes = $Token.Split('.')
Read-JwtPart $Partes[0] | Select-Object alg, typ | Format-List
Read-JwtPart $Partes[1] | Select-Object sub, principal_type, iss, aud, iat, exp, jti | Format-List
```

**Falar:** “O token usa HS256, identifica o cliente por UUID e informa emissor, destinatário e expiração. O CPF não vai no token. Esta leitura apenas decodifica os campos; a assinatura e as regras de acesso são verificadas pela API que recebe o bearer.”

### 5.4 CPF inválido, cliente ausente e inativo

As chamadas abaixo exibem somente respostas de erro e cabeçalhos, sem gerar bearer:

```powershell
@{ cpf = '00000000000' } | ConvertTo-Json -Compress | curl.exe -sS -i `
    -X POST $AuthUrl -H 'Content-Type: application/json' `
    -H "x-correlation-id: $Correlacao-invalid" --data-binary '@-'

@{ cpf = $CpfAusente } | ConvertTo-Json -Compress | curl.exe -sS -i `
    -X POST $AuthUrl -H 'Content-Type: application/json' `
    -H "x-correlation-id: $Correlacao-absent" --data-binary '@-'

@{ cpf = $CpfInativo } | ConvertTo-Json -Compress | curl.exe -sS -i `
    -X POST $AuthUrl -H 'Content-Type: application/json' `
    -H "x-correlation-id: $Correlacao-inactive" --data-binary '@-'
```

**Esperado:** `400 INVALID_CPF`; depois `401 ACCESS_DENIED` para os dois clientes não elegíveis. Nenhuma resposta de erro contém `accessToken`.

**Falar:** “CPF inválido é recusado antes da consulta. Cliente inexistente e inativo recebem a mesma negativa, sem detalhar o cadastro. Nesses casos, nenhum JWT é emitido.”

### 5.5 Usar o JWT na API principal — com Lenilson

O exemplo pressupõe uma rota **GET** de cliente e dados fictícios. Ajuste método e corpo à operação real antes da gravação. A resposta esperada deve ser acordada; não invente uma URL ou aceite qualquer `2xx` de rota pública como prova.

```powershell
# A mesma rota, primeiro SEM token: deve negar acesso.
curl.exe -sS -i $RotaProtegida -H "x-correlation-id: $Correlacao"

# Agora COM o JWT emitido acima: deve permitir a operacao autorizada.
$RespostaApi = Invoke-WebRequest -UseBasicParsing -Uri $RotaProtegida `
    -Headers @{ Authorization = "Bearer $Token"; 'x-correlation-id' = $Correlacao }
$RespostaApi.StatusCode
$RespostaApi.Content

# Assinatura adulterada: a mesma rota deve negar acesso.
$Assinatura = $Partes[2]
$Primeiro = if ($Assinatura[0] -eq 'A') { 'B' } else { 'A' }
$TokenAlterado = $Partes[0] + '.' + $Partes[1] + '.' + $Primeiro + $Assinatura.Substring(1)
curl.exe -sS -i $RotaProtegida -H "Authorization: Bearer $TokenAlterado" `
    -H "x-correlation-id: $Correlacao-tampered"
```

**Esperado:** sem token e assinatura adulterada são negados, conforme o contrato de segurança da API; o JWT válido permite apenas a operação autorizada ao cliente. Não exiba dados de outros clientes.

**Fala de passagem:** “Este é o token que acabamos de obter pela autenticação por CPF. Agora vamos usá-lo na aplicação principal. A mesma rota nega a chamada sem token e aceita o cliente autenticado.”

Se houver incompatibilidade `UUID` versus e-mail, chave ou claims divergentes, essa parte continua pendente. Sucesso na Lambda não comprova sozinho a proteção da aplicação. Casos de expiração, issuer/audience incorretos e autorização entre clientes devem ser ensaiados no aceite integrado; não espere uma hora pela expiração durante o vídeo.

### Alternativa pelo Postman

Importe `docs/postman/auth-serverless.postman_collection.json`. Defina `authBaseUrl` **sem** `/auth/cpf` e `cpfAtivo`. A collection atual contém cliente ativo e CPF inválido; não salva o token automaticamente. Prepare antes as chamadas de ausente, inativo e da rota protegida e armazene o bearer apenas em variável local, sem compartilhar/exportar seu valor. Não afirme que esses requests adicionais já fazem parte da collection versionada.

## 6. Logs e correlação

1. No CloudWatch, abra `/aws/lambda/fiap-soat-mecanica-auth-production` (troque o sufixo por `homologation` se esse foi o ambiente usado).
2. Localize o valor de `$Correlacao` e mostre o evento `auth_succeeded`, o `correlationId` e o `apiGatewayRequestId`.
3. Abra `/aws/apigateway/fiap-soat-mecanica-auth-production` e encontre o mesmo identificador no campo `requestId`, com status `200`.
4. Na aplicação principal, procure o mesmo `x-correlation-id` propagado. Mostre o trace real na ferramenta do grupo quando a instrumentação estiver integrada.

Para localizar a chamada no Logs Insights sem depender de como o envelope JSON da Lambda foi interpretado, substitua o texto abaixo pelo valor gerado:

```text
fields @timestamp, @message
| filter @message like /COLE_A_CORRELACAO_AQUI/
| sort @timestamp desc
| limit 20
```

No grupo do Gateway, procure o `apiGatewayRequestId` encontrado na Lambda, pois o log de acesso usa `requestId`, e não necessariamente o correlation ID enviado pelo cliente.

**Falar:** “Os eventos são estruturados em JSON. O identificador do Gateway liga os logs do Gateway e da Lambda. O correlation ID também acompanha a chamada à aplicação, quando propagado por ela. Os logs do Auth não registram CPF, bearer ou credenciais.”

Não diga que há trace distribuído apenas porque existe correlation ID: mostre o trace instrumentado ou mantenha essa evidência como pendente.

## 7. Roteiro de fala e telas — aproximadamente 4 minutos

| Tempo | Tela / ação | Fala sugerida |
| --- | --- | --- |
| 0:00–0:25 | README e diagrama | “Sou José Manoel, responsável pelo Auth Serverless. Este repositório atende à autenticação por CPF: API Gateway, Lambda Java 21, consulta ao PostgreSQL e emissão de JWT. A aplicação, Kubernetes e banco têm repositórios próprios.” |
| 0:25–0:55 | PR, proteção, CI/CD e resumo do deploy | “As mudanças passam por PR e checks. A pipeline valida o componente e automatiza a implantação. Aqui está a execução concluída e seu endpoint; homologação e produção têm states separados.” |
| 0:55–1:45 | Chamada ativa e claims | “O cliente está ativo na base. Recebemos 200 e um JWT Bearer. O assunto é o UUID, sem CPF no token, com emissor, audiência e expiração.” |
| 1:45–2:15 | Chamadas inválida, ausente e inativa | “CPF inválido retorna 400. Cadastro ausente ou inativo retorna 401 e não gera token.” |
| 2:15–3:05 | Rota protegida com Lenilson | “Usamos o mesmo JWT na aplicação. Sem token a chamada é negada; com token válido, a operação autorizada funciona. Uma assinatura alterada também é rejeitada.” |
| 3:05–3:40 | Logs do Gateway, Lambda e passagem para observabilidade | “Estes registros pertencem à mesma requisição. O request ID vincula Gateway e Lambda; a aplicação propaga a correlação para acompanhar o fluxo integrado.” |
| 3:40–4:00 | RFC, ADR, OpenAPI e sequência | “As decisões e os contratos estão documentados. A RFC explica a estratégia e a ADR registra a decisão. O README contém execução, deploy, pipeline e diagramas; o contrato pode ser consultado no OpenAPI ou Postman.” |

As falas pressupõem resultados efetivamente demonstrados. Ajuste-as ao ambiente real. Uma gravação de ensaio com pendências não deve ser apresentada como entrega integralmente concluída.

## 8. Se algo falhar no ensaio

| Sintoma | Conferir antes de repetir |
| --- | --- |
| `Unsupported Terraform Core version` | Terraform `1.15.8`, dentro de `>= 1.14.5, < 1.16.0` |
| Credenciais inválidas/expiradas | Sessão do Lab e as três credenciais da mesma sessão/conta |
| Preflight bloqueado | Bucket/owner/região, RDS disponível, secret correspondente, SG e valores antigos sobrepondo a org |
| Smoke `400` passa e `200` falha | Cadastro ativo, schema, rede, acesso ao Secrets Manager e logs da Lambda |
| `503` | Falha na consulta ao banco; verificar conexão/TLS e disponibilidade sem expor secrets |
| `500` | Configuração/runtime; investigar evento correlacionado antes de atribuir uma causa |
| Lambda emite token, API nega | Chave, issuer/audience, `principal_type`, UUID no `sub` e autorização da rota |
| Sem correlação na API | Propagação do header e instrumentação na aplicação principal |

## 9. Conferência final da entrega

- [ ] Cliente ativo retorna `200` no ambiente AWS e os casos negativos foram demonstrados.
- [ ] O mesmo JWT funciona numa rota protegida; sem token e token adulterado são negados.
- [ ] Há execução real de CI e deploy automatizado, com evidências dos ambientes usados.
- [ ] A proteção efetiva da branch principal e o fluxo por PR foram conferidos.
- [ ] Logs JSON e correlação foram mostrados; dashboards e traces estão cobertos pelo restante do vídeo.
- [ ] README, diagrama de autenticação, RFC/ADR e OpenAPI/collection estão acessíveis.
- [ ] O grupo incluiu diagrama global e sequência de abertura da ordem de serviço.
- [ ] O usuário `soat-architecture` tem acesso aos **quatro** repositórios.
- [ ] Vídeo completo de até 15 minutos publicado no YouTube ou Vimeo, público ou não listado.
- [ ] Um único PDF para o Portal do Aluno centraliza os quatro repos, vídeo, documentações arquiteturais e confirmação do acesso do professor.

O professor não estabeleceu quantidade fixa de RFCs/ADRs nem exigiu Dockerfile para componentes que não precisam dele. O Auth é entregue como JAR para Lambda; não acrescente um Dockerfile apenas para a gravação.
