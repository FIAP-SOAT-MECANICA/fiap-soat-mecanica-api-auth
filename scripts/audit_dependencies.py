"""Consulta avisos OSV para a lista de dependencias runtime resolvida pelo Maven."""
import json
from pathlib import Path
import re
import sys
import urllib.request


def main():
    dependencies = re.findall(r"^\s+([\w.-]+):([\w.-]+):jar:([^:\s]+):(compile|runtime)",
                              Path(sys.argv[1]).read_text(encoding="utf-8"), re.MULTILINE)
    if not dependencies:
        raise RuntimeError("Lista de dependencias vazia; execute mvn dependency:list -DincludeScope=runtime.")
    queries = [{"package": {"name": f"{group}:{artifact}", "ecosystem": "Maven"}, "version": version}
               for group, artifact, version, _ in dependencies]
    request = urllib.request.Request("https://api.osv.dev/v1/querybatch", data=json.dumps({"queries": queries}).encode(),
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=40) as response:
        results = json.load(response)["results"]
    if len(results) != len(queries):
        raise RuntimeError("Resposta OSV incompleta.")
    findings = []
    for query, result in zip(queries, results):
        for vulnerability in result.get("vulns", []):
            findings.append({"package": query["package"]["name"], "version": query["version"], "id": vulnerability["id"]})
    report = {"packages": len(queries), "source": "https://osv.dev", "findings": findings}
    Path("target/dependency-audit.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return bool(findings)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print(f"Consulta OSV nao concluida: {type(error).__name__}. Nenhuma conclusao sobre vulnerabilidades.", file=sys.stderr)
        sys.exit(2)
