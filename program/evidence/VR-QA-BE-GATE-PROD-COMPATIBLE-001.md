# Recibo histórico — VR-QA-BE-GATE-PROD-COMPATIBLE-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-QA-BE-GATE-PROD-COMPATIBLE-001.md`. SHA-256 do original: `63199447df32bbac23a29be9a3c3fa94c06f903debfe961f5bbb2aa0d2bd5d7c`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

## Veredito

`NOT_RUN / ANALYZED / ROUTE_A_SELECTED` em 2026-07-30.

Matheus selecionou explicitamente a Rota A. Este card é o investimento
permanente para o gate prod-compatible; B, waiver e adapter temporário foram
rejeitados. A execução autorizada nesta fase pertence exclusivamente ao card
bounded `VR-QA-BE-GATE-CAPABILITY-SPIKE-001`.

Nenhum gate, Maven, Docker, container, backend de produto, CI remoto ou deploy
foi executado/alterado para este card permanente.

## Revisões e reprodução históricas

- `e05d7e7cdb6a997d833159b30172fb39f5f8f86a`
- `753cf5d1b7ea48ed6e49873409a9dfc76ff0ae83`
- `e304199b1359a44ba15b441d85c5363404528b6f`
- `5f4a4c737d23839af5774c7fdc64d48a91a8076e`
- `7231574ec15f91adad1ab8382a5a97d565b922dc`
- `c5b64ac27c61e33f219e3e5f4c995d0a0c4f0111`

- Comando registrado na origem: `./scripts/qa_gate_card.sh`.
- Comando registrado na origem: `./scripts/ci_quality_gate.sh`.
- Comando registrado na origem: `git ls-tree`.
- Comando registrado na origem: `git ls-files program/waivers/** program/authorizations/**`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
