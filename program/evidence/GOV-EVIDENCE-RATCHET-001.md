# Recibo histórico — GOV-EVIDENCE-RATCHET-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/GOV-EVIDENCE-RATCHET-001.md`. SHA-256 do original: `a043b408188b3fc49dbfb944ebe61c03e73896ee3d085446d3cf3af51a7557f8`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

## Identidade

- data: 2026-07-18;
- ambiente: macOS 26.5.2, Python 3.9.6;
- branch: `codex/vanrides-next-quality-phase`;
- checkpoint anterior: `ee9090a5d678ead45a507c7561c9336e10e26032`;
- commit de implementação:
  `c45b53e9cbddfc7b004d9b315b6cf1bdda6d5886`;
- tree de implementação: `e7ee7fca5131aca1bb6d441b7f0de49ebcd03061`;
- superfície alterada: workspace de coordenação;
- backend, app e Maps Platform: nenhuma alteração.

## Resultado

`PASS`. O programa agora impede que um card seja marcado `done` sem listar e
possuir `program/evidence/<CARD_ID>.md` em UTF-8, com o próprio ID no conteúdo.
Baseline e evidências de outros cards continuam aceitas somente como referências
suplementares.

## Revisões e reprodução históricas

- `coordination:c45b53e9cbddfc7b004d9b315b6cf1bdda6d5886`

- Comando registrado na origem: `python3 scripts/program_control_test.py`.
- Comando registrado na origem: `python3 -m py_compile scripts/program_control.py scripts/program_control_test.py`.
- Comando registrado na origem: `python3 -m json.tool program/program.json`.
- Comando registrado na origem: `python3 scripts/program_control.py check`.
- Comando registrado na origem: `python3 scripts/program_control.py dashboard`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
