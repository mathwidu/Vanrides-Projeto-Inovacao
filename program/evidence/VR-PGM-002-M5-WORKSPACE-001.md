# Recibo histórico — VR-PGM-002-M5-WORKSPACE-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-PGM-002-M5-WORKSPACE-001.md`. SHA-256 do original: `1c4226603e677449f1594059b78ac53177f1eab85c3b336d7b7628f74fe011a5`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

## Resultado

**PASS no escopo de recuperação e organização local**, em 2026-09-02, no Mac M5
arm64, com Python 3.9.6. Coordenação, app e backend recuperados em clones
independentes. Mapas permanece ausente por seleção explícita do proprietário.

## Versões avaliadas

| Repositório | Commit da organização |
|---|---|
| Coordenação | `869da87f7be96b293531e3a220845d02e9d3ec5a` |
| App | `eab050928ced0c6a7bb9027496e7fcd9a6749801` |
| Backend | `8dace9109a8c9879a3a5e355b6100a24a969ddaa` |

## Revisões e reprodução históricas

- `coordination:869da87f7be96b293531e3a220845d02e9d3ec5a`
- `app:eab050928ced0c6a7bb9027496e7fcd9a6749801`
- `backend:8dace9109a8c9879a3a5e355b6100a24a969ddaa`

- Comando registrado na origem: `git fsck --full`.
- Comando registrado na origem: `python3 scripts/program_control_test.py`.
- Comando registrado na origem: `python3 scripts/workspace_profile_test.py`.
- Comando registrado na origem: `python3 scripts/program_control.py check --workspace core`.
- Comando registrado na origem: `python3 scripts/program_control.py summary --workspace core`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
