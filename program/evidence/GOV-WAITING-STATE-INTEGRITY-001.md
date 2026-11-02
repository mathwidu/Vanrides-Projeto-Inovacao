# Recibo histórico — GOV-WAITING-STATE-INTEGRITY-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/GOV-WAITING-STATE-INTEGRITY-001.md`. SHA-256 do original: `4668885f411db36bda19b7e686c6876753a303ba0447a986718343c2c3843128`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD, WAITING_HUMAN pertencem às respectivas revisões históricas e não aprovam este checkout.

## Identidade

- data da validação: `2026-07-18`;
- ambiente: macOS `26.5.2` (`25F84`), Python `3.9.6`;
- branch: `codex/vanrides-next-quality-phase`;
- commit de implementação:
  `167994b467d46442c713f555b99966669f121533`;
- tree de implementação: `15bef8f2d78700eb28ed6a4bbf048cdcc40a1bf1`;
- superfície alterada: somente coordenação/governança;
- backend, app e Maps Platform: nenhuma alteração neste card.

## Resultado

`PASS`. Estados executáveis agora falham fechado se possuírem blockers, contrato
Markdown ausente/vazio/duplicado ou Definition of Ready malformada/pendente.
`next` executa validação estática antes de anunciar trabalho. Estados humanos e
externos registram a ação que realmente os libera, sem transformar espera em
sucesso.

## Revisões e reprodução históricas

- `coordination:167994b467d46442c713f555b99966669f121533`

- Comando registrado na origem: `python3 scripts/program_control_test.py`.
- Comando registrado na origem: `python3 -m py_compile scripts/program_control.py scripts/program_control_test.py`.
- Comando registrado na origem: `python3 -m json.tool program/program.json`.
- Comando registrado na origem: `python3 scripts/program_control.py dashboard`.
- Comando registrado na origem: `python3 scripts/program_control.py check`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
