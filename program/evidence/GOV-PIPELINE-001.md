# Recibo histórico — GOV-PIPELINE-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/GOV-PIPELINE-001.md`. SHA-256 do original: `b849f170d80757e1cfdb06c1c6f7f3da4acd007a8ab4da9eaa71edb805773c42`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

## Identidade

- data: 2026-07-16;
- branch: `codex/vanrides-next-quality-phase`;
- commit de implementação:
  `7f6e979cf72a0acd46d10a99cdaf02a800eebc42`;
- commit de validação e evidência:
  `5040e0034bc44eeb4c6bed8c19e18306424c0888`;
- superfície alterada: workspace de coordenação;
- backend, app e maps platform: nenhuma alteração.

## Resultado

`PASS` para a fundação local do programa.

## Revisões e reprodução históricas

- `7f6e979cf72a0acd46d10a99cdaf02a800eebc42`
- `5040e0034bc44eeb4c6bed8c19e18306424c0888`

- Comando registrado na origem: `python3 scripts/program_control_test.py`.
- Comando registrado na origem: `python3 scripts/program_control.py check`.
- Comando registrado na origem: `python3 scripts/qa_route_plan_payload_contract_test.py`.
- Comando registrado na origem: `python3 -m dev_harness ... check`.
- Comando registrado na origem: `python3 -m dev_harness ... done-check`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
