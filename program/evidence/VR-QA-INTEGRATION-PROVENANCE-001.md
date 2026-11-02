# Recibo histórico — VR-QA-INTEGRATION-PROVENANCE-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-QA-INTEGRATION-PROVENANCE-001.md`. SHA-256 do original: `cff5bfb4e1e8207d41a55aa366bb9fd1f27b70c156f8f46cc1f4c6a5bf47bca9`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

## Veredito

`PASS_REMOTE / DONE` em 2026-07-29. A geração de bytecode na fonte foi corrigida
no backend
`e304199b1359a44ba15b441d85c5363404528b6f`, tree
`5f4a4c737d23839af5774c7fdc64d48a91a8076e`. Gate, matriz PostgreSQL e smoke
same-JAR passaram localmente com a worktree limpa. O SHA corrigido foi publicado
uma única vez em `codex/qa-integration-provenance-e304199`; a única run manual
`30466145519`, attempt `1`, terminou `success` no evento, ref e SHA autorizados.
O artifact único foi validado integralmente e a run histórica `30450775307`,
attempt `1`, permanece apenas como diagnóstico falho do SHA anterior.

Não haverá novo push, nova run, retry, rerun, deploy, VPS, staging, piloto ou
produção. `pilot=HOLD` e `production=HOLD` permanecem inalterados.

## Handoff coordenador

## Revisões e reprodução históricas

- `backend:ad52de11f760c86da1b632309bf04ee0e11f5931`
- `backend:6efdb61846d0e4dcc59fd5cab921d275ad9eca98`
- `backend:e304199b1359a44ba15b441d85c5363404528b6f`
- `coordination:34b8d6863e92d0726ef2a616435293ca0441883e`
- `coordination:da39142fcdc29c9d5e0203cb54d459b2780156b9`
- `coordination:43c849bd7abfa98eeeb3e16693c65b35a165090c`
- `coordination:27c2f178de06cb661c8cee8a464b2f212e68ada9`
- `coordination:16739204ad0cb16193aae8a0acb91b1ed4864cd3`
- `coordination:af7262ec9d9f48c074a120b3c623055eb88cbf00`
- `coordination:2edbf39c3f2467b2b86b2af5bc539c909f93eda6`

- Comando registrado na origem: `bash -n scripts/ci_quality_gate.sh`.
- Comando registrado na origem: `./scripts/qa_gate_card.sh`.
- Comando registrado na origem: `bash -n`.
- Comando registrado na origem: `git diff --check`.
- Comando registrado na origem: `git ls-remote --heads`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
