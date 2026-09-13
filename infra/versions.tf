terraform {
  required_version = ">= 1.14.5, < 1.16.0"

  # O backend e configurado pelo CI/CD ou por backend.hcl.example.
  # O bucket de state deve existir antes do primeiro terraform init.
  backend "s3" {}

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "Terraform"
      Component   = "auth-serverless"
    }
  }
}
