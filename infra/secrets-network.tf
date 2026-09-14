# Estes recursos pertencem exclusivamente ao Auth. O stack nao altera RDS/EKS,
# suas regras de rede, o LabRole ou o bucket compartilhado de state.
resource "random_password" "jwt" {
  count   = var.jwt_secret_arn == null ? 1 : 0
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "jwt" {
  count                   = var.jwt_secret_arn == null ? 1 : 0
  name                    = "${local.resource_name}-jwt"
  description             = "Chave HS256 do Auth; a API deve consumir a mesma chave base64"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "jwt" {
  count     = var.jwt_secret_arn == null ? 1 : 0
  secret_id = aws_secretsmanager_secret.jwt[0].id
  secret_string = jsonencode({
    secret = var.jwt_shared_secret != null ? var.jwt_shared_secret : base64encode(random_password.jwt[0].result)
  })
}

resource "aws_security_group" "secrets_endpoint" {
  count       = var.create_secrets_endpoint ? 1 : 0
  name_prefix = "${local.resource_name}-secrets-"
  description = "HTTPS da Lambda para Secrets Manager"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Lambda Auth"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = var.lambda_security_group_ids
  }
}

resource "aws_vpc_endpoint" "secrets" {
  count               = var.create_secrets_endpoint ? 1 : 0
  vpc_id              = var.vpc_id
  service_name        = "com.amazonaws.${var.aws_region}.secretsmanager"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = var.private_subnet_ids
  security_group_ids  = [aws_security_group.secrets_endpoint[0].id]
}
