"""Descobre dependencias existentes. Nao cria/atualiza nenhum recurso AWS."""
import base64
import binascii
import ipaddress
import json
import os
from pathlib import Path
import re
import subprocess
import sys


class ConfigurationError(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise ConfigurationError(message)


def validate_key(value):
    try:
        decoded = base64.b64decode(value, validate=True)
        require(len(decoded) >= 32, "Chave JWT precisa de pelo menos 256 bits.")
    except (ValueError, binascii.Error):
        raise ConfigurationError("Chave JWT deve estar em base64 valido.") from None


class Aws:
    def __init__(self, region):
        self.region = region

    def __call__(self, service, operation, *args):
        result = subprocess.run(
            ["aws", service, operation, *args, "--region", self.region,
             "--output", "json", "--no-cli-pager"],
            capture_output=True, text=True, timeout=60, check=False,
        )
        if result.returncode:
            # Nao repassa stderr: respostas AWS podem conter informacoes sensiveis.
            match = re.search(r"\(([A-Za-z0-9]+)\)", result.stderr)
            code = match.group(1) if match else "AWSCommandFailed"
            raise ConfigurationError(f"{service}/{operation}: {code}. Verifique credenciais, permissoes e infraestrutura do grupo.")
        return json.loads(result.stdout) if result.stdout.strip() else {}


def permits_port(permission, port):
    return permission["IpProtocol"] == "-1" or (
        permission["IpProtocol"] in ("tcp", "6")
        and permission.get("FromPort", 65536) <= port <= permission.get("ToPort", -1))


def endpoint_reachable(endpoint_groups, source_groups, subnet_cidrs):
    rules = [p for g in endpoint_groups for p in g["IpPermissions"] if permits_port(p, 443)]
    if any(pair["GroupId"] in source_groups for p in rules for pair in p.get("UserIdGroupPairs", [])):
        return True
    networks = [ipaddress.ip_network(r["CidrIp"]) for p in rules for r in p.get("IpRanges", [])]
    return all(any(ipaddress.ip_network(cidr).subnet_of(network) for network in networks) for cidr in subnet_cidrs)


def prepare(aws, env):
    region = env.get("AWS_REGION") or env.get("TF_STATE_REGION") or "us-east-1"
    stage = env.get("DEPLOY_ENVIRONMENT", "production")
    require(stage in ("production", "homologation"), "Ambiente invalido.")
    account = aws("sts", "get-caller-identity")["Account"]
    bucket = env.get("TF_STATE_BUCKET") or f"mecanica-tfstate-{account}"
    aws("s3api", "head-bucket", "--bucket", bucket, "--expected-bucket-owner", account)
    location = aws("s3api", "get-bucket-location", "--bucket", bucket)["LocationConstraint"] or "us-east-1"
    location = "eu-west-1" if location == "EU" else location
    state_region = env.get("TF_STATE_REGION") or region
    require(location == state_region, "TF_STATE_REGION difere da regiao do bucket de state.")
    encryption = aws("s3api", "get-bucket-encryption", "--bucket", bucket)
    require(bool(encryption.get("ServerSideEncryptionConfiguration", {}).get("Rules")), "Bucket de state sem criptografia.")

    identifier = env.get("RDS_INSTANCE_IDENTIFIER") or "mecanica-db-prod"
    db = aws("rds", "describe-db-instances", "--db-instance-identifier", identifier)["DBInstances"][0]
    require(db["DBInstanceStatus"] == "available", "RDS ainda nao esta available. Conclua o deploy do repo DB.")
    require(db["Engine"] == "postgres" and db.get("DBName") == "mecanica", "Esperado PostgreSQL com database mecanica.")
    vpc = db["DBSubnetGroup"]["VpcId"]
    subnet_ids = [s["SubnetIdentifier"] for s in db["DBSubnetGroup"]["Subnets"] if s["SubnetStatus"] == "Active"]
    subnets = aws("ec2", "describe-subnets", "--subnet-ids", *subnet_ids)["Subnets"]
    by_az = {}
    for subnet in sorted(subnets, key=lambda s: s["SubnetId"]):
        require(subnet["VpcId"] == vpc, "Subnet fora da VPC do RDS.")
        if subnet.get("AvailableIpAddressCount", 0) >= 8:
            by_az.setdefault(subnet["AvailabilityZone"], subnet)
    selected = list(by_az.values())
    require(len(selected) >= 2, "RDS precisa de duas AZs com subnets e IPs disponiveis para Lambda/endpoint.")
    for attribute, key in (("enableDnsSupport", "EnableDnsSupport"), ("enableDnsHostnames", "EnableDnsHostnames")):
        require(aws("ec2", "describe-vpc-attribute", "--vpc-id", vpc, "--attribute", attribute)[key]["Value"],
                f"VPC precisa de {attribute} para Secrets Manager privado.")

    db_groups = [s["VpcSecurityGroupId"] for s in db["VpcSecurityGroups"]]
    groups = aws("ec2", "describe-security-groups", "--group-ids", *db_groups)["SecurityGroups"]
    candidates = {pair["GroupId"] for g in groups for p in g["IpPermissions"]
                  if permits_port(p, db["Endpoint"]["Port"])
                  for pair in p.get("UserIdGroupPairs", []) if pair.get("UserId", account) == account}
    hint = env.get("ALLOWED_SG_ID")
    if hint:
        require(hint in candidates, "ALLOWED_SG_ID nao esta autorizado no RDS desta conta; atualize a variable da org.")
        candidates = {hint}
    require(len(candidates) == 1, "RDS deve permitir um SG de origem inequivoco; configure ALLOWED_SG_ID se houver varios.")
    source_groups = sorted(candidates)
    source = aws("ec2", "describe-security-groups", "--group-ids", *source_groups)["SecurityGroups"]
    require(all(g["VpcId"] == vpc for g in source), "SG da Lambda fora da VPC do RDS.")
    # O contrato atual do repo K8s permite egress livre. Nao ampliamos regras compartilhadas.
    require(any(p["IpProtocol"] == "-1" and any(r["CidrIp"] == "0.0.0.0/0" for r in p.get("IpRanges", []))
                for g in source for p in g["IpPermissionsEgress"]),
            "SG compartilhado sem egress esperado. Confirme HTTPS/5432 com a infra antes do deploy.")

    db_secret_id = env.get("DB_SECRET_ARN") or "mecanica-db-credentials"
    metadata = aws("secretsmanager", "describe-secret", "--secret-id", db_secret_id)
    require(metadata["ARN"].split(":")[4] == account and metadata["ARN"].split(":")[3] == region,
            "Segredo DB fora da conta/regiao de deploy.")
    secret = json.loads(aws("secretsmanager", "get-secret-value", "--secret-id", metadata["ARN"])["SecretString"])
    require(secret.get("host") == db["Endpoint"]["Address"] and secret.get("port") == db["Endpoint"]["Port"]
            and secret.get("dbname") == db["DBName"] and bool(secret.get("username")) and bool(secret.get("password")),
            "Segredo mecanica-db-credentials nao corresponde ao RDS/contrato do Auth.")
    jwt_arn = env.get("JWT_SECRET_ARN") or None
    if env.get("JWT_SECRET"):
        validate_key(env["JWT_SECRET"])
    if jwt_arn:
        require(jwt_arn.split(":")[3:5] == [region, account], "Segredo JWT fora da conta/regiao.")
        key = json.loads(aws("secretsmanager", "get-secret-value", "--secret-id", jwt_arn)["SecretString"])
        validate_key(key.get("secret", ""))
        require(not env.get("JWT_SECRET") or key["secret"] == env["JWT_SECRET"], "JWT_SECRET diverge do segredo JWT existente.")

    role = aws("iam", "get-role", "--role-name", "LabRole")["Role"]
    require(role["Arn"].split(":")[4] == account, "LabRole fora da conta atual.")
    statements = role["AssumeRolePolicyDocument"]["Statement"]
    require(any(s["Effect"] == "Allow" and "lambda.amazonaws.com" in (
        [s.get("Principal", {}).get("Service")] if isinstance(s.get("Principal", {}).get("Service"), str)
        else s.get("Principal", {}).get("Service", [])) for s in statements), "LabRole nao confia no servico Lambda.")

    endpoints = aws("ec2", "describe-vpc-endpoints", "--filters", f"Name=vpc-id,Values={vpc}",
                    f"Name=service-name,Values=com.amazonaws.{region}.secretsmanager")["VpcEndpoints"]
    endpoints = [e for e in endpoints if e.get("PrivateDnsEnabled") and e["State"] not in ("deleted", "rejected")]
    require(len(endpoints) <= 1, "Mais de um endpoint privado Secrets Manager encontrado.")
    create_endpoint = True
    if endpoints:
        endpoint = endpoints[0]
        require(endpoint["State"] == "available", "Endpoint Secrets Manager ainda nao esta available.")
        tags = {t["Key"]: t["Value"] for t in endpoint.get("Tags", [])}
        owned = tags.get("Project") == "fiap-soat-mecanica-auth" and tags.get("Environment") == stage
        create_endpoint = owned  # Manter recursos ja gerenciados no segundo deploy.
        if not owned:
            endpoint_groups = aws("ec2", "describe-security-groups", "--group-ids",
                                  *[g["GroupId"] for g in endpoint["Groups"]])["SecurityGroups"]
            require(endpoint_reachable(endpoint_groups, source_groups, [s["CidrBlock"] for s in selected]),
                    "Endpoint existente nao permite HTTPS do SG/subnets da Lambda.")

    return ({"aws_region": region, "environment": stage, "vpc_id": vpc,
             "db_secret_arn": metadata["ARN"], "jwt_secret_arn": jwt_arn,
             "jwt_audience": env.get("JWT_AUDIENCE") or "fiap-soat-mecanica-api",
             "private_subnet_ids": sorted(s["SubnetId"] for s in selected),
             "lambda_security_group_ids": source_groups, "lambda_execution_role_arn": role["Arn"],
             "create_secrets_endpoint": create_endpoint},
            {"bucket": bucket, "region": state_region, "key": f"auth/{stage}/terraform.tfstate",
             "encrypt": True, "use_lockfile": True})


def main():
    try:
        env = dict(os.environ)
        config, backend = prepare(Aws(env.get("AWS_REGION") or env.get("TF_STATE_REGION") or "us-east-1"), env)
        directory = Path(__file__).resolve().parents[1] / "infra"
        (directory / "deploy.auto.tfvars.json").write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
        (directory / "backend.hcl").write_text("\n".join(f"{key} = {json.dumps(value)}" for key, value in backend.items()) + "\n", encoding="utf-8")
        print("Preflight OK: conta, state, RDS, segredo DB, rede e LabRole conferidos. Nenhum recurso alterado.")
    except (ConfigurationError, KeyError, ValueError, TypeError, IndexError, OSError, subprocess.TimeoutExpired) as exc:
        message = str(exc) if isinstance(exc, ConfigurationError) else "Resposta AWS/configuracao invalida; verifique os contratos documentados."
        print(f"Preflight falhou: {message}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
