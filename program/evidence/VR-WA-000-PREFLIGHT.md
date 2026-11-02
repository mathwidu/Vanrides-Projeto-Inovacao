# Recibo histórico — VR-WA-000-PREFLIGHT

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-WA-000-PREFLIGHT.md`. SHA-256 do original: `ed082cbe55e417cf1e0cd69faecce7f91f4c67d2ad7c9a7c26c6929408f3f0a7`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

## Escopo e versões

Data: 2026-10-30. Controle/configuração local, sem funcionalidade de app/API, publicação ou envio. Coordenador `5be29675992631b11c7675805e7bb29baaf0688f`, app `91ea136693bc727eac4ba87dd1469fe71dd616fb`, backend `a262154fdee8c811563dc70b223ddea1b9aa1f2a`. Os commits de fechamento documental posteriores não mudam o comportamento testado.

## Validação executada

PASS: `python3 scripts/program_control_test.py` 59/59; `python3 scripts/workspace_profile_test.py` 11/11. `python3 scripts/program_control.py check --workspace core`: PASS, zero erros, cinco avisos esperados (HEAD coordenador posterior à origem, três ausências de CI remoto e mapas adiado). Testes executados no SHA coordenador acima.

## Revisões e reprodução históricas

- `coordination:5be29675992631b11c7675805e7bb29baaf0688f`
- `app:91ea136693bc727eac4ba87dd1469fe71dd616fb`
- `backend:a262154fdee8c811563dc70b223ddea1b9aa1f2a`

- Comando registrado na origem: `python3 scripts/program_control_test.py`.
- Comando registrado na origem: `python3 scripts/workspace_profile_test.py`.
- Comando registrado na origem: `python3 scripts/program_control.py check --workspace core`.
- Comando registrado na origem: `bash -n scripts/graphify_refresh.sh`.
- Comando registrado na origem: `./scripts/graphify_refresh.sh`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
