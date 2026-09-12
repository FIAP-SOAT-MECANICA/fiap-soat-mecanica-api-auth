## Alteracao

Descreva a mudanca e seu efeito no fluxo CPF, JWT, infraestrutura ou contrato de integracao.

## Validacao

- [ ] `mvn verify`
- [ ] `terraform -chdir=infra fmt -check -recursive`
- [ ] `terraform -chdir=infra validate`
- [ ] Testes novos ou atualizados quando a regra mudou

## Seguranca e integracao

- [ ] Nao inclui segredo, CPF de cliente ou token real
- [ ] Mantem o contrato de JWT ou documenta a mudanca para a aplicacao principal
- [ ] Nao adiciona rota protegida sem validacao correspondente na aplicacao
