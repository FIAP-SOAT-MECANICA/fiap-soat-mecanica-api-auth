# Gravação do Auth — CPF e consumo da API protegida

Roteiro de 2–3 minutos para José Manoel. Pipeline, deploy, dashboards, logs e traces já foram gravados pelo grupo; não é necessário repeti-los neste trecho. O vídeo completo continua limitado a 15 minutos pelo enunciado do professor.

## Resultado da consulta à organização em 15/09/2026

- API principal, `main` em `d7d80ab`: existe **GET /clientes/me**, exclusivo do principal `CLIENTE`. O filtro já aceita UUID no `sub` do JWT do Auth. A incompatibilidade descrita na revisão anterior foi corrigida no código.
- Auth: **POST /auth/cpf**, resposta `accessToken`, `tokenType` e `expiresIn`.
- A migration `V9__mock_dados.sql` da API inclui o cliente sintético **Cliente PF Teste**, CPF **52998224725**, status **ATIVO**, UUID **33333333-3333-3333-3333-333333333333**. Isso confirma os dados previstos no código; confira se a migration foi aplicada no banco usado pelo Auth e pela API.
- O CD da API lê a chave de cliente do Secrets Manager para **AUTH_JWT_SECRET**. O `JWT_SECRET` da API é a chave do login dos funcionários e não substitui essa configuração.
- O acesso documentado à API no EKS passa pelo gateway **Traefik**, via `kubectl port-forward`, em `http://127.0.0.1:8080/clientes/me`.
- O deploy Auth [35006237418](https://github.com/FIAP-SOAT-MECANICA/fiap-soat-mecanica-api-auth/actions/runs/35006237418) terminou com sucesso na conta `572040336155`, mas seu smoke comprovou somente **400**: `AUTH_TEST_CPF` não estava configurado.
- A URL daquela execução foi `https://6ssh75w9oi.execute-api.us-east-1.amazonaws.com/auth/cpf`. Na consulta atual, esse hostname **não resolveu por DNS nesta máquina**. Não use essa URL sem reconfirmá-la na conta da demonstração.
- O deploy Auth posterior [35025084854](https://github.com/FIAP-SOAT-MECANICA/fiap-soat-mecanica-api-auth/actions/runs/35025084854) falhou em `s3api/head-bucket: 404`. O CD da API [35025227281](https://github.com/FIAP-SOAT-MECANICA/fiap-soat-mecanica-api/actions/runs/35025227281) falhou com `No cluster found for name: mecanica`.

**Antes de gravar, precisa haver um ambiente ativo com Auth, RDS e API integrados.** Os últimos erros não comprovam que toda a infraestrutura foi removida; podem envolver outra conta, região ou configuração. A consulta não confirmou um ambiente pronto para gravar agora. Não recrie recursos nem altere credenciais da organização apenas para seguir este roteiro sem alinhar a conta com o grupo.

## 1. Setup inicial — fora da gravação

### Instalar e conferir ferramentas

Na máquina consultada, `kubectl` está instalado, não há contextos Kubernetes configurados e `aws` não está no PATH. Se ainda faltar AWS CLI, execute no PowerShell, conclua o instalador e abra novamente o terminal:

```powershell
msiexec.exe /i https://awscli.amazonaws.com/AWSCLIV2.msi
```

Confira:

```powershell
aws --version
kubectl version --client
```

Referência: [instalação oficial AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html).

### Terminal 1 — conectar à conta e abrir o gateway

Use as credenciais temporárias da **conta que possui o ambiente integrado**, com o Lab ativo. Estar logado no navegador não autentica automaticamente o terminal. O bloco abaixo solicita as três credenciais com entrada oculta e as mantém somente no processo atual:

```powershell
foreach ($Nome in 'AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY','AWS_SESSION_TOKEN') {
    $Valor = [System.Net.NetworkCredential]::new('', (Read-Host $Nome -AsSecureString)).Password
    [Environment]::SetEnvironmentVariable($Nome, $Valor, 'Process')
}
$Valor = $null
$env:AWS_DEFAULT_REGION = 'us-east-1'
$env:AWS_PAGER = ''
aws sts get-caller-identity --query Account --output text
aws eks describe-cluster --name mecanica --region us-east-1 --query 'cluster.status' --output text
```

Confirme a conta com o grupo e o cluster `ACTIVE`. Se aparecer `No cluster found`, pare: essa conta/região/nome não possui o cluster esperado. Não é um erro do token.

Descubra o endereço atual do Auth sem ler valores de secrets:

```powershell
$AuthBase = aws apigatewayv2 get-apis --region us-east-1 `
    --query "Items[?Name=='fiap-soat-mecanica-auth-production'].ApiEndpoint | [0]" --output text
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($AuthBase) -or $AuthBase -eq 'None') {
    throw 'Auth production nao encontrado nesta conta/regiao. Confira o deploy.'
}
$AuthBase = $AuthBase.Trim().TrimEnd('/')
$AuthUrl = "$AuthBase/auth/cpf"
$AuthUrl
```

Copie a URL exibida para o Terminal 2. Para usar homologação, confirme antes os nomes e segredos daquele ambiente; este roteiro usa produção, conforme o CD consultado.

Conecte ao EKS, confira a API e abra o túnel pelo gateway:

```powershell
aws eks update-kubeconfig --name mecanica --region us-east-1
if ($LASTEXITCODE -ne 0) { throw 'Falha ao configurar acesso ao EKS.' }
kubectl get pods -n mecanica
kubectl get svc traefik -n traefik
kubectl port-forward svc/traefik 8080:80 --namespace traefik
```

Deixe esse terminal aberto. Deve aparecer `Forwarding from 127.0.0.1:8080`. O túnel usa o EKS real; não inicia uma API local. `Forbidden` no kubectl exige acesso ao cluster para a identidade usada. Não faça port-forward do service direto da API se quiser demonstrar o caminho pelo gateway.

### Terminal 2 — preparar a demonstração

Abra outro PowerShell. Não precisa compilar Java, executar Terraform, iniciar PostgreSQL ou subir Docker para consumir o ambiente implantado.

```powershell
$AuthUrl = Read-Host 'Cole a URL completa do Auth obtida no Terminal 1'
$ApiUrl = 'http://127.0.0.1:8080'
$Cpf = '52998224725'
$Correlacao = 'tc3-auth-' + [guid]::NewGuid().ToString('N')
curl.exe -sS -i "$ApiUrl/actuator/health/readiness"
```

Espere `200` e saúde `UP`. Healthcheck confirma disponibilidade, não autenticação. Ensaie os passos seguintes antes de gravar. Use o cliente fictício da migration, sem dados pessoais reais.

Se o Auth emitir JWT e `/clientes/me` rejeitar, confira se a API foi implantada **depois** da criação do segredo JWT do Auth: o CD lê `AUTH_JWT_SECRET` durante o deploy e permite continuar com esse valor vazio quando o secret não existe. Nesse caso, Lenilson precisa atualizar a implantação da API para carregar o secret correto. Um deploy verde antigo da API não comprova essa configuração.

## 2. Gravação — comandos, tela e fala

Grave o **Terminal 2 do VS Code**, com fonte legível. Deixe o Terminal 1 funcionando em segundo plano. Não mostre credenciais. Execute cada bloco separado e explique o resultado.

### Cena 1 — rota protegida sem token (20 segundos)

**Rodar:**

```powershell
curl.exe -sS -i "$ApiUrl/clientes/me"
```

**Mostrar:** status de rejeição `401` ou `403`, conforme a configuração efetiva da API; nunca `200`.

**Falar:** “Esta rota retorna os dados do cliente autenticado. Sem um token, a API bloqueia o acesso.”

### Cena 2 — autenticação por CPF (40 segundos)

**Rodar:**

```powershell
$Resposta = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $AuthUrl `
    -ContentType 'application/json' -Headers @{ 'x-correlation-id' = $Correlacao } `
    -Body (@{ cpf = $Cpf } | ConvertTo-Json -Compress)
$Login = $Resposta.Content | ConvertFrom-Json
$Token = $Login.accessToken
if ($Resposta.StatusCode -ne 200 -or [string]::IsNullOrWhiteSpace($Token)) {
    throw 'Autenticacao nao comprovada. Confira cadastro, banco e logs antes de gravar.'
}
[pscustomobject]@{
    HTTP = $Resposta.StatusCode
    tokenType = $Login.tokenType
    expiresIn = $Login.expiresIn
    accessToken = '[JWT recebido e guardado em memoria]'
} | Format-List
```

**Mostrar:** CPF fictício enviado, `HTTP 200`, `Bearer`, `expiresIn`. O token fica em `$Token`; não imprima `$Token`, `$Login` ou a resposta completa.

**Falar:** “Envio o CPF para o API Gateway, que aciona a função serverless. Ela valida o CPF e consulta a existência e o status do cliente no banco. Como o cliente está ativo, recebemos um JWT Bearer com validade de uma hora.”

Use a fala sobre validade de uma hora somente se `expiresIn` for `3600`, o padrão atual.

### Cena 3 — consumir a mesma API usando esse JWT (40 segundos)

**Rodar:**

```powershell
$Cliente = Invoke-WebRequest -UseBasicParsing -Uri "$ApiUrl/clientes/me" `
    -Headers @{ Authorization = "Bearer $Token"; 'x-correlation-id' = $Correlacao }
$Dados = $Cliente.Content | ConvertFrom-Json
[pscustomobject]@{
    HTTP = $Cliente.StatusCode
    id = $Dados.id
    nome = $Dados.nome
    status = $Dados.status
} | Format-List
```

**Mostrar:** `HTTP 200`, `Cliente PF Teste`, `ATIVO` e o UUID do cliente da migration. Se os dados de teste tiverem sido alterados pelo grupo, confirme previamente o resultado esperado.

**Falar:** “Agora uso o mesmo token recém-gerado no cabeçalho Authorization. A mesma rota que negou acesso retorna os dados do cliente identificado pelo JWT. A API valida o token e usa o UUID do cliente para consultar seus dados.”

Não use `/auth/login`: essa é a autenticação por e-mail e senha dos funcionários. Não use `/clientes/por-cpf/...`: essa rota exige o papel `ATENDENTE`, não `CLIENTE`.

### Cena 4 — CPF inválido não gera token (20 segundos)

**Rodar:**

```powershell
@{ cpf = '00000000000' } | ConvertTo-Json -Compress | curl.exe -sS -i `
    -X POST $AuthUrl -H 'Content-Type: application/json' --data-binary '@-'
```

**Mostrar:** `400`, `INVALID_CPF` e ausência de token.

**Falar:** “Quando o CPF é inválido, a função rejeita a autenticação e não emite token.”

### Cena 5 — token adulterado também é rejeitado (20 segundos, recomendada)

**Rodar:**

```powershell
$Partes = $Token.Split('.')
$Assinatura = $Partes[2]
$Primeiro = if ($Assinatura[0] -eq 'A') { 'B' } else { 'A' }
$TokenAlterado = $Partes[0] + '.' + $Partes[1] + '.' + $Primeiro + $Assinatura.Substring(1)
curl.exe -sS -i "$ApiUrl/clientes/me" -H "Authorization: Bearer $TokenAlterado"
```

**Mostrar:** rejeição `401/403`, sem dados do cliente. `500` é falha, não evidência de proteção correta; a correção desse caso está no PR #30 da API, portanto confira a versão implantada.

**Falar:** “Ao adulterar a assinatura do token, a API volta a negar acesso. Assim demonstramos a emissão pela função serverless e a validação na API protegida.”

**Encerramento:** “Esse foi o fluxo de autenticação por CPF e consumo autenticado da API. As evidências de infraestrutura, pipeline e observabilidade estão nos demais trechos do vídeo do grupo.”

## 3. Critério para começar a gravação definitiva

O ensaio deve mostrar: **sem token → rejeição; CPF ativo → 200 e JWT; mesmo JWT em /clientes/me → 200; CPF inválido → 400; assinatura adulterada → rejeição**.

Não afirme que esse fluxo passou com base apenas em código, CI ou smoke `400`. A consulta à organização confirmou o contrato e os comandos, mas os bloqueios de ambiente descritos no início precisam estar resolvidos para produzir a evidência real.

## Referências verificadas

- [Rota de cliente na API, commit d7d80ab](https://github.com/FIAP-SOAT-MECANICA/fiap-soat-mecanica-api/blob/d7d80abe63052b9de7a5a10a6cfda32b311632b4/src/main/java/br/com/fiap/soat/mecanica/adapters/in/web/cliente/ClienteController.java)
- [CD: secret do Auth e acesso pelo Traefik](https://github.com/FIAP-SOAT-MECANICA/fiap-soat-mecanica-api/blob/d7d80abe63052b9de7a5a10a6cfda32b311632b4/.github/workflows/cd.yml)
- [Dados fictícios da migration V9](https://github.com/FIAP-SOAT-MECANICA/fiap-soat-mecanica-api/blob/d7d80abe63052b9de7a5a10a6cfda32b311632b4/src/main/resources/db/migration/V9__mock_dados.sql)
- [Contrato e instruções do Auth](../README.md)
