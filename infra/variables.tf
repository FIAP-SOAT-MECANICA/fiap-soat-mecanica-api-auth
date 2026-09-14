variable "aws_region" {
  description = "Regiao AWS onde os recursos serao criados."
  type        = string
}

variable "project_name" {
  description = "Nome usado como prefixo dos recursos."
  type        = string
  default     = "fiap-soat-mecanica-auth"
}

variable "environment" {
  description = "Ambiente da implantacao, por exemplo homologation ou production."
  type        = string

  validation {
    condition     = contains(["homologation", "production"], var.environment)
    error_message = "environment deve ser homologation ou production."
  }
}

variable "db_secret_arn" {
  description = "ARN do segredo do banco PostgreSQL no AWS Secrets Manager."
  type        = string
  sensitive   = true
}

variable "jwt_secret_arn" {
  description = "ARN de segredo JWT existente; null cria um segredo gerenciado por este stack."
  type        = string
  default     = null
}

variable "jwt_shared_secret" {
  description = "Chave HS256 base64 compartilhada com a API. null gera uma chave; nunca e exibida em outputs."
  type        = string
  sensitive   = true
  default     = null
  validation {
    condition     = var.jwt_shared_secret == null ? true : can(regex("^[A-Za-z0-9+/]{43,}={0,2}$", var.jwt_shared_secret))
    error_message = "A chave JWT deve ser base64 e conter pelo menos 256 bits."
  }
}

variable "vpc_id" {
  description = "VPC do RDS compartilhado, descoberta antes do deploy."
  type        = string
}

variable "create_secrets_endpoint" {
  description = "Criar endpoint privado Secrets Manager quando nao existe um endpoint compativel na VPC."
  type        = bool
  default     = true
}

variable "kms_key_arns" {
  description = "ARNs de chaves KMS usadas para criptografar os segredos, se aplicavel."
  type        = list(string)
  default     = []
}

variable "jwt_audience" {
  description = "Audience que as APIs protegidas devem exigir ao validar o JWT."
  type        = string
  default     = "fiap-soat-mecanica-api"
  validation {
    condition     = length(trimspace(var.jwt_audience)) > 0
    error_message = "jwt_audience nao pode ser vazio."
  }
}

variable "jwt_issuer" {
  description = "Issuer gravado no JWT."
  type        = string
  default     = "fiap-soat-mecanica-auth"
}

variable "jwt_ttl_seconds" {
  description = "Validade, em segundos, do access token emitido."
  type        = number
  default     = 3600

  validation {
    condition     = var.jwt_ttl_seconds >= 300 && var.jwt_ttl_seconds <= 86400
    error_message = "jwt_ttl_seconds deve estar entre 300 e 86400 segundos."
  }
}

variable "private_subnet_ids" {
  description = "Subnets da VPC do RDS, uma por AZ; o acesso AWS usa endpoint privado."
  type        = list(string)
  validation {
    condition     = length(var.private_subnet_ids) >= 2 && length(var.private_subnet_ids) <= 16
    error_message = "Informe pelo menos duas subnets em AZs distintas."
  }
}

variable "lambda_security_group_ids" {
  description = "Security groups da Lambda; devem permitir apenas o acesso necessario ao banco."
  type        = list(string)
  validation {
    condition     = length(var.lambda_security_group_ids) >= 1 && length(var.lambda_security_group_ids) <= 5
    error_message = "Informe de um a cinco security groups autorizados pelo RDS."
  }
}

variable "lambda_execution_role_arn" {
  description = "ARN de um papel de execucao Lambda existente. Quando nulo, este stack cria um papel com permissoes minimas."
  type        = string
  default     = null
  nullable    = true
}

variable "lambda_memory_size" {
  description = "Memoria da Lambda em MB."
  type        = number
  default     = 512
}

variable "lambda_timeout_seconds" {
  description = "Timeout da Lambda em segundos."
  type        = number
  default     = 28
}

variable "artifact_path" {
  description = "Caminho do JAR sombreado gerado por mvn package."
  type        = string
  default     = "../target/auth-lambda.jar"
}
