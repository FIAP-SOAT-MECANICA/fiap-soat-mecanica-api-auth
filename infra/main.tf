locals {
  resource_name             = "${var.project_name}-${var.environment}"
  lambda_logs               = "/aws/lambda/${local.resource_name}"
  lambda_execution_role_arn = var.lambda_execution_role_arn != null ? var.lambda_execution_role_arn : aws_iam_role.lambda[0].arn
  jwt_secret_arn            = var.jwt_secret_arn != null ? var.jwt_secret_arn : aws_secretsmanager_secret.jwt[0].arn
}

data "aws_partition" "current" {}

resource "aws_cloudwatch_log_group" "lambda" {
  name              = local.lambda_logs
  retention_in_days = 30
}

resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = "/aws/apigateway/${local.resource_name}"
  retention_in_days = 30
}

data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lambda" {
  count = var.lambda_execution_role_arn == null ? 1 : 0

  name               = "${local.resource_name}-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

data "aws_iam_policy_document" "lambda_runtime" {
  statement {
    sid    = "WriteApplicationLogs"
    effect = "Allow"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
    resources = ["${aws_cloudwatch_log_group.lambda.arn}:*"]
  }

  statement {
    sid     = "ReadOnlyAuthSecrets"
    effect  = "Allow"
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      var.db_secret_arn,
      local.jwt_secret_arn
    ]
  }

  statement {
    sid    = "ManageVpcNetworkInterfaces"
    effect = "Allow"
    actions = [
      "ec2:CreateNetworkInterface",
      "ec2:DeleteNetworkInterface",
      "ec2:DescribeNetworkInterfaces",
      "ec2:DescribeSubnets",
      "ec2:DescribeSecurityGroups",
      "ec2:DescribeVpcs",
      "ec2:AssignPrivateIpAddresses",
      "ec2:UnassignPrivateIpAddresses"
    ]
    resources = ["*"]
  }

  dynamic "statement" {
    for_each = length(var.kms_key_arns) == 0 ? [] : [var.kms_key_arns]

    content {
      sid       = "DecryptAuthSecrets"
      effect    = "Allow"
      actions   = ["kms:Decrypt"]
      resources = statement.value
    }
  }
}

resource "aws_iam_role_policy" "lambda_runtime" {
  count = var.lambda_execution_role_arn == null ? 1 : 0

  name   = "${local.resource_name}-runtime"
  role   = aws_iam_role.lambda[0].id
  policy = data.aws_iam_policy_document.lambda_runtime.json
}

resource "aws_lambda_function" "auth" {
  function_name    = local.resource_name
  description      = "Autenticacao de clientes por CPF para a oficina FIAP SOAT"
  role             = local.lambda_execution_role_arn
  runtime          = "java21"
  handler          = "br.com.fiap.soat.mecanica.auth.api.AuthHandler::handleRequest"
  filename         = var.artifact_path
  source_code_hash = filebase64sha256(var.artifact_path)
  memory_size      = var.lambda_memory_size
  timeout          = var.lambda_timeout_seconds

  vpc_config {
    subnet_ids         = var.private_subnet_ids
    security_group_ids = var.lambda_security_group_ids
  }

  environment {
    variables = {
      DB_SECRET_ARN   = var.db_secret_arn
      JWT_SECRET_ARN  = local.jwt_secret_arn
      JWT_AUDIENCE    = var.jwt_audience
      JWT_ISSUER      = var.jwt_issuer
      JWT_TTL_SECONDS = tostring(var.jwt_ttl_seconds)
    }
  }

  logging_config {
    log_format = "JSON"
    log_group  = aws_cloudwatch_log_group.lambda.name
  }

  depends_on = [aws_iam_role_policy.lambda_runtime, aws_secretsmanager_secret_version.jwt, aws_vpc_endpoint.secrets]
}

resource "aws_apigatewayv2_api" "auth" {
  name          = local.resource_name
  protocol_type = "HTTP"
  description   = "Entrada HTTP da autenticacao CPF"
}

resource "aws_apigatewayv2_integration" "auth" {
  api_id                 = aws_apigatewayv2_api.auth.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.auth.invoke_arn
  integration_method     = "POST"
  payload_format_version = "2.0"
  timeout_milliseconds   = 30000
}

resource "aws_apigatewayv2_route" "authenticate_customer" {
  api_id    = aws_apigatewayv2_api.auth.id
  route_key = "POST /auth/cpf"
  target    = "integrations/${aws_apigatewayv2_integration.auth.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.auth.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_burst_limit = 20
    throttling_rate_limit  = 10
  }

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gateway.arn
    format = jsonencode({
      requestId         = "$context.requestId"
      extendedRequestId = "$context.extendedRequestId"
      requestTime       = "$context.requestTime"
      httpMethod        = "$context.httpMethod"
      routeKey          = "$context.routeKey"
      status            = "$context.status"
      responseLength    = "$context.responseLength"
      integrationError  = "$context.integrationErrorMessage"
    })
  }
}

resource "aws_lambda_permission" "allow_api_gateway" {
  statement_id  = "AllowApiGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.auth.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.auth.execution_arn}/*/POST/auth/cpf"
}
