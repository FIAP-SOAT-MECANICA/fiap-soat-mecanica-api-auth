mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = { json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}" }
  }
}
mock_provider "random" {}

variables {
  aws_region                = "us-east-1"
  environment               = "production"
  vpc_id                    = "vpc-00000000000000001"
  db_secret_arn             = "arn:aws:secretsmanager:us-east-1:123456789012:secret:db-abcdef"
  private_subnet_ids        = ["subnet-00000000000000001", "subnet-00000000000000002"]
  lambda_security_group_ids = ["sg-00000000000000001"]
  lambda_execution_role_arn = "arn:aws:iam::123456789012:role/LabRole"
}

run "academy_manages_auth_resources_only" {
  command = plan
  assert {
    condition     = length(aws_iam_role.lambda) == 0 && length(aws_iam_role_policy.lambda_runtime) == 0
    error_message = "O Learner Lab nao deve criar/alterar papeis IAM."
  }
  assert {
    condition     = length(aws_secretsmanager_secret.jwt) == 1 && length(aws_vpc_endpoint.secrets) == 1
    error_message = "Primeiro deploy deve preparar chave JWT e acesso privado."
  }
  assert {
    condition     = aws_lambda_function.auth.role == var.lambda_execution_role_arn && aws_lambda_function.auth.runtime == "java21"
    error_message = "Lambda deve executar Java 21 com LabRole."
  }
  assert {
    condition     = aws_apigatewayv2_route.authenticate_customer.route_key == "POST /auth/cpf"
    error_message = "Rota publica deve manter o contrato CPF."
  }
}

run "reuse_shared_secret_and_endpoint" {
  command = plan
  variables {
    jwt_secret_arn          = "arn:aws:secretsmanager:us-east-1:123456789012:secret:jwt-abcdef"
    create_secrets_endpoint = false
  }
  assert {
    condition     = length(aws_secretsmanager_secret.jwt) == 0 && length(aws_vpc_endpoint.secrets) == 0
    error_message = "Nao deve duplicar infraestrutura explicitamente compartilhada."
  }
  assert {
    condition     = aws_lambda_function.auth.environment[0].variables.JWT_SECRET_ARN == var.jwt_secret_arn
    error_message = "Lambda deve usar o segredo compartilhado."
  }
}

run "reject_invalid_environment" {
  command = plan
  variables { environment = "dev" }
  expect_failures = [var.environment]
}
