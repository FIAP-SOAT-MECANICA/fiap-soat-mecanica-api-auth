output "api_endpoint" {
  description = "Base URL do API Gateway HTTP."
  value       = aws_apigatewayv2_api.auth.api_endpoint
}

output "authenticate_customer_url" {
  description = "Endpoint de autenticacao por CPF."
  value       = "${aws_apigatewayv2_api.auth.api_endpoint}/auth/cpf"
}

output "lambda_function_name" {
  description = "Nome da funcao Lambda provisionada."
  value       = aws_lambda_function.auth.function_name
}

output "jwt_secret_arn" {
  description = "ARN (nao o valor) da chave que a API deve consumir."
  value       = local.jwt_secret_arn
}

output "jwt_issuer" {
  value = var.jwt_issuer
}

output "jwt_audience" {
  value = var.jwt_audience
}
