"""Verifica o endpoint publicado sem imprimir CPF, token ou corpo de resposta."""
import json
import os
import sys
import urllib.error
import urllib.request
import uuid


def check(url, cpf, expected):
    correlation = f"auth-smoke-{uuid.uuid4()}"
    request = urllib.request.Request(url, data=json.dumps({"cpf": cpf}).encode(), method="POST",
                                     headers={"Content-Type": "application/json", "x-correlation-id": correlation})
    try:
        response = urllib.request.urlopen(request, timeout=35)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = json.loads(response.read())
        if response.status != expected or response.headers.get("x-correlation-id") != correlation or response.headers.get("Cache-Control") != "no-store":
            raise RuntimeError(f"Contrato HTTP inesperado: esperado {expected}, recebido {response.status}.")
        if expected == 200 and (not body.get("accessToken") or body.get("tokenType") != "Bearer"):
            raise RuntimeError("Resposta nao possui o contrato de token.")
        if expected != 200 and "accessToken" in body:
            raise RuntimeError("Resposta de erro nao pode conter token.")
    print(f"Smoke HTTP {expected} OK; correlacao e cache conferidos.")


def main():
    url = os.environ["AUTH_URL"]
    if not url.startswith("https://"):
        raise RuntimeError("AUTH_URL deve usar HTTPS.")
    check(url, "00000000000", 400)
    cpf = os.environ.get("AUTH_TEST_CPF")
    if cpf:
        check(url, cpf, 200)
    else:
        print("Smoke publico OK. Sucesso com RDS requer AUTH_TEST_CPF de fixture ativa; ainda nao comprovado na AWS.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"Smoke falhou ({type(error).__name__}); consulte status e logs correlacionados.", file=sys.stderr)
        sys.exit(1)
