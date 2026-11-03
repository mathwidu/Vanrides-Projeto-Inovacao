# Recibo histórico — VR-QA-INTEGRATION-PROVENANCE-001-DOR

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-QA-INTEGRATION-PROVENANCE-001-DOR.md`. SHA-256 do original: `cd863e7277b53642f07cdd86fd8015d244eb2e91327147beb0937face0305efe`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD, BLOCKED, FAIL pertencem às respectivas revisões históricas e não aprovam este checkout.

## Veredito de prontidão

`READY` em 2026-07-18. A dependência `VR-SEC-001-BE-READ` está `done`, o
backend está limpo em `b2dd00cf1e4e7cbfe6a7d50d606ee948d8b7f871`, tree
`f79e34a1c96939979046626e7700628fc7b09875`, e não há decisão humana,
credencial ou ambiente externo necessário para começar a implementação local.

Este freeze elimina o blocker técnico anterior. Ele não autoriza push,
execução remota, publicação, deploy, piloto ou produção.

> Nota de fechamento: esta seção registra o baseline observado antes da
> implementação. O resultado final e as diferenças validadas estão em
> `program/evidence/VR-QA-INTEGRATION-PROVENANCE-001.md`; os itens de dívida
> abaixo foram superados no SHA backend final `6efdb61846d...`.

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

- Comando registrado na origem: `VANRIDES_QA_LOCK_ROOT`.
- Comando registrado na origem: `git rev-parse --git-common-dir`.
- Comando registrado na origem: `VANRIDES_QA_REQUIRE_CLEAN=1`.
- Comando registrado na origem: `bash -n`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
