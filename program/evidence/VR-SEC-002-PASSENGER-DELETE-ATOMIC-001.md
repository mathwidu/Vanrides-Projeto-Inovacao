# Recibo histórico — VR-SEC-002-PASSENGER-DELETE-ATOMIC-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-SEC-002-PASSENGER-DELETE-ATOMIC-001.md`. SHA-256 do original: `c685227db0b8eed2799ab5bc4a7450bbf4cd112f4355b0e214dbff870283a293`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

## Veredito

`PASS_LOCAL_TECHNICAL` e `READY_TO_CLOSE` no escopo implementável localmente.
A autorização tenant-scoped e o hard delete foram reduzidos a uma única DML
autoritativa e comprovados na versão final limpa. Este resultado não autoriza
piloto, staging, produção ou uma política de lifecycle de dados.

## Identidade da versão

- data local: 2026-07-17, `America/Sao_Paulo`;
- repositório: `vanrides-backend`;
- branch: `dev`;
- baseline vulnerável: `46cd5e8a8dc7d2a3f76771fb7d65aba286a71d06`;
- SHA final avaliado: `6efd2e2f66cb5ac3784085d317ba98174750c815`;
- tree final: `142c51caebbc0eca9b93ed828c78924963c7f7c6`;
- estado no gate, smoke e encerramento: limpo;
- Graphify no SHA final: 11.722 nós, 24.507 relações e 717 comunidades.

## Revisões e reprodução históricas

- `backend:2ce65d09ba65354db9bf9abff8de3da10f98c885`
- `backend:2e106dda4390028b9e7744bcc0acd610ce79b9d9`
- `backend:6efd2e2f66cb5ac3784085d317ba98174750c815`
- `coordination:0b316fa4d307c7250bf5b207c7748b550ad0e175`
- `coordination:371e9f93390d05a014bd9433c6a547b8b99a4006`



- Comando histórico transcrito do original (sem executar nesta migração): `VANRIDES_QA_REQUIRE_CLEAN=1 ./scripts/qa_gate_card.sh`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
