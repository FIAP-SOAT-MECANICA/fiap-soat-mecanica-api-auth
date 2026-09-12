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
  description = "ARN do segredo da chave JWT no AWS Secrets Manager."
  type        = string
  sensitive   = true
}

variable "kms_key_arns" {
  description = "ARNs de chaves KMS usadas para criptografar os segredos, se aplicavel."
  type        = list(string)
  default     = []
}

variable "jwt_audience" {
  description = "Audience que as APIs protegidas devem exigir ao validar o JWT."
  type        = string
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
  description = "Subnets privadas com rota para o banco e, se necessario, para AWS APIs."
  type        = list(string)
}

variable "lambda_security_group_ids" {
  description = "Security groups da Lambda; devem permitir apenas o acesso necessario ao banco."
  type        = list(string)
}

variable "lambda_memory_size" {
  description = "Memoria da Lambda em MB."
  type        = number
  default     = 512
}

variable "lambda_timeout_seconds" {
  description = "Timeout da Lambda em segundos."
  type        = number
  default     = 15
}

variable "artifact_path" {
  description = "Caminho do JAR sombreado gerado por mvn package."
  type        = string
  default     = "../target/auth-lambda.jar"
}
