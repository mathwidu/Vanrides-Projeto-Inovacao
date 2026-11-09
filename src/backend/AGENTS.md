# Backend — instruções do módulo

Leia `../../AGENTS.md`, `../../program/README.md`, o card ativo e
`../../docs/desenvolvimento/backend.md`. Este módulo é `src/backend` dentro do
repositório acadêmico; não é outro repositório Git nem submódulo.

- Execute comandos Maven nesta pasta; comandos `program_control` na raiz Git.
- Preserve contratos HTTP/auth/CORS/WebSocket, schema, migrations e compatibilidade mobile.
- Execute testes proporcionais ao comportamento alterado. Para uma validação completa,
  `./scripts/ci_quality_gate.sh` preserva a matriz PostgreSQL/Docker e os recibos.
  Não confundir uma construção sem testes com aprovação funcional.
- Gate da equipe, na raiz: `bash scripts/check_backend.sh`; `--light` não compila.
- Proveniência usa SHA/tree/dirt do repositório inteiro; relatórios e Maven pertencem
  ao módulo. Preserve isolamento do Maven, locks, sanitização e release antes de PASS.
- O workflow é `../../.github/workflows/backend.yml`: PR/dev constroem sem testes;
  push na main constrói/publica a imagem e atualiza a API com checagem de saúde. Consulte
  `../../deploy/homologacao/AUTOMATICO.md` antes de alterar publicação.
- Mudança de contrato exige gates dos consumidores e smoke da mesma versão.
- `deploy/ops` é fonte de guardrails testados; não executar operação externa sem autorização.
- Nunca versionar `.env`, tokens, dados pessoais, GPS real ou relatórios brutos.
- Preserve arquivos concorrentes; commits e publicação pertencem ao principal da tarefa.
- Para relações de arquitetura use Graphify na raiz Git, confirmando as arestas nos fontes.
