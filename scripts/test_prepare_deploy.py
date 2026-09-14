import copy
import unittest
from prepare_deploy import ConfigurationError, prepare, validate_key


ACCOUNT = "123456789012"
REGION = "us-east-1"
DB_ARN = f"arn:aws:secretsmanager:{REGION}:{ACCOUNT}:secret:mecanica-db-credentials-abcdef"


class DeploymentTest(unittest.TestCase):
    def setUp(self):
        self.env = {"TF_STATE_BUCKET": "fixture-state", "DEPLOY_ENVIRONMENT": "production"}
        self.db = {"DBInstanceStatus": "available", "Engine": "postgres", "DBName": "mecanica",
                   "Endpoint": {"Address": "fixture.rds.amazonaws.com", "Port": 5432},
                   "DBSubnetGroup": {"VpcId": "vpc-fixture", "Subnets": [
                       {"SubnetIdentifier": "subnet-a", "SubnetStatus": "Active"},
                       {"SubnetIdentifier": "subnet-b", "SubnetStatus": "Active"}]},
                   "VpcSecurityGroups": [{"VpcSecurityGroupId": "sg-db"}]}
        self.db_group = {"VpcId": "vpc-fixture", "IpPermissions": [{"IpProtocol": "tcp", "FromPort": 5432,
                         "ToPort": 5432, "UserIdGroupPairs": [{"GroupId": "sg-client", "UserId": ACCOUNT}]}]}
        self.source = {"VpcId": "vpc-fixture", "IpPermissionsEgress": [
            {"IpProtocol": "-1", "IpRanges": [{"CidrIp": "0.0.0.0/0"}]}]}
        self.endpoint_group = {"IpPermissions": [{"IpProtocol": "tcp", "FromPort": 443, "ToPort": 443,
                               "UserIdGroupPairs": [{"GroupId": "sg-client"}]}]}
        self.endpoints = []
        self.subnets = [{"SubnetId": f"subnet-{zone}", "VpcId": "vpc-fixture", "AvailabilityZone": f"us-east-1{zone}",
                         "AvailableIpAddressCount": 32, "CidrBlock": f"10.0.{i}.0/24"} for i, zone in enumerate("ab")]
        self.calls = []

    def aws(self, service, operation, *args):
        self.calls.append((service, operation, args))
        if operation == "get-caller-identity": return {"Account": ACCOUNT}
        if operation == "head-bucket": return {}
        if operation == "get-bucket-location": return {"LocationConstraint": None}
        if operation == "get-bucket-encryption": return {"ServerSideEncryptionConfiguration": {"Rules": [{}]}}
        if operation == "describe-db-instances": return {"DBInstances": [self.db]}
        if operation == "describe-subnets": return {"Subnets": self.subnets}
        if operation == "describe-vpc-attribute":
            return {"EnableDnsSupport": {"Value": True}, "EnableDnsHostnames": {"Value": True}}
        if operation == "describe-security-groups":
            return {"SecurityGroups": [self.db_group if args[1] == "sg-db" else self.endpoint_group if args[1] == "sg-endpoint" else self.source]}
        if operation == "describe-secret": return {"ARN": DB_ARN}
        if operation == "get-secret-value":
            return {"SecretString": '{"host":"fixture.rds.amazonaws.com","port":5432,"dbname":"mecanica","username":"test","password":"fixture-only"}'}
        if operation == "get-role":
            return {"Role": {"Arn": f"arn:aws:iam::{ACCOUNT}:role/LabRole", "AssumeRolePolicyDocument": {
                "Statement": [{"Effect": "Allow", "Principal": {"Service": "lambda.amazonaws.com"}}]}}}
        if operation == "describe-vpc-endpoints": return {"VpcEndpoints": self.endpoints}
        self.fail(f"Operacao AWS inesperada: {service}/{operation}")

    def test_discovers_contract_without_mutating_aws_or_including_secret_values(self):
        config, backend = prepare(self.aws, self.env)
        self.assertEqual(config["lambda_execution_role_arn"], f"arn:aws:iam::{ACCOUNT}:role/LabRole")
        self.assertTrue(config["create_secrets_endpoint"])
        self.assertEqual(config["lambda_security_group_ids"], ["sg-client"])
        self.assertEqual(backend["key"], "auth/production/terraform.tfstate")
        self.assertNotIn("fixture-only", str(config))
        self.assertTrue(all(op.startswith(("get-", "describe-", "head-")) for _, op, _ in self.calls))

    def test_homolog_state_is_separate(self):
        self.env["DEPLOY_ENVIRONMENT"] = "homologation"
        self.assertEqual(prepare(self.aws, self.env)[1]["key"], "auth/homologation/terraform.tfstate")

    def test_stale_org_security_group_fails(self):
        self.env["ALLOWED_SG_ID"] = "sg-other-account"
        with self.assertRaisesRegex(ConfigurationError, "ALLOWED_SG_ID"): prepare(self.aws, self.env)

    def test_rds_not_ready_fails_before_terraform(self):
        self.db["DBInstanceStatus"] = "creating"
        with self.assertRaisesRegex(ConfigurationError, "available"): prepare(self.aws, self.env)

    def test_ambiguous_sources_do_not_guess(self):
        self.db_group["IpPermissions"][0]["UserIdGroupPairs"].append({"GroupId": "sg-other"})
        with self.assertRaisesRegex(ConfigurationError, "inequivoco"): prepare(self.aws, self.env)

    def test_one_subnet_per_az(self):
        extra = copy.deepcopy(self.subnets[0])
        extra["SubnetId"] = "subnet-c"
        self.subnets.append(extra)
        self.assertEqual(len(prepare(self.aws, self.env)[0]["private_subnet_ids"]), 2)

    def test_cannot_use_two_subnets_in_same_az(self):
        self.subnets[1]["AvailabilityZone"] = self.subnets[0]["AvailabilityZone"]
        with self.assertRaisesRegex(ConfigurationError, "duas AZs"): prepare(self.aws, self.env)

    def endpoint(self, owned=False):
        self.endpoints = [{"State": "available", "PrivateDnsEnabled": True, "Groups": [{"GroupId": "sg-endpoint"}],
                           "Tags": [{"Key": "Project", "Value": "fiap-soat-mecanica-auth"},
                                    {"Key": "Environment", "Value": "production"}] if owned else []}]

    def test_reuses_accessible_existing_endpoint(self):
        self.endpoint()
        self.assertFalse(prepare(self.aws, self.env)[0]["create_secrets_endpoint"])

    def test_redeploy_keeps_owned_endpoint_in_state(self):
        self.endpoint(owned=True)
        self.assertTrue(prepare(self.aws, self.env)[0]["create_secrets_endpoint"])

    def test_inaccessible_existing_endpoint_fails(self):
        self.endpoint()
        self.endpoint_group["IpPermissions"] = []
        with self.assertRaisesRegex(ConfigurationError, "HTTPS"): prepare(self.aws, self.env)

    def test_key_validation_does_not_accept_short_or_malformed_keys(self):
        for key in ("not-base64", "YQ==", "!"):
            with self.assertRaises(ConfigurationError): validate_key(key)


if __name__ == "__main__": unittest.main()
