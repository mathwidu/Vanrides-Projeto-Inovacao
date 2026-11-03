# Recibo histórico — VR-PRIV-001-DATA-INVENTORY-PURPOSE-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-PRIV-001-DATA-INVENTORY-PURPOSE-001.md`. SHA-256 do original: `ffa2c7f9a0a825fd120d8efa7fc8ab1904a35974ab43ad6b087d53c9bb2e11e9`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

## Identidade

- data da validação: `2026-07-18`;
- ambiente: macOS `26.5.2` (`25F84`), Python `3.9.6`;
- branch: `codex/vanrides-next-quality-phase`;
- commit de implementação:
  `a3f7140e0a25fe5c440700146a42401500a87e4a`;
- tree de implementação: `64cadefba19a3343d5423a46d70da3203f96f0a6`;
- superfície alterada: somente coordenação, documentação e estado do programa;
- backend, app e Maps Platform: nenhuma alteração neste card.

## Resultado

`PASS`. O corte estático possui 34 famílias de dados (`D01`–`D34`), 19
findings (`F-PRIV-001`–`F-PRIV-019`) e roteamento vertical para todos os
achados. Foram criados 17 cards `analyzed` novos; findings já cobertos por
cards canônicos permaneceram ligados a esses cards, sem duplicação.

## Revisões e reprodução históricas

- `coordination:a3f7140e0a25fe5c440700146a42401500a87e4a`

- Comando registrado na origem: `python3 scripts/program_control_test.py`.
- Comando registrado na origem: `python3 -m json.tool program/program.json`.
- Comando registrado na origem: `python3 scripts/program_control.py check`.
- Comando registrado na origem: `git diff --check HEAD`.
- Comando registrado na origem: `git show`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
