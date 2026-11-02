# Recibo histórico — VR-QA-APP-GATE-HERMETIC-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-QA-APP-GATE-HERMETIC-001.md`. SHA-256 do original: `25673e1a24614f6d3ff99f96525f8a845fd70d3857f75edb5b1951cbeb7fd73f`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

## Veredito

`PASS TÉCNICO` em 2026-07-17 para o escopo do card. O gate do app foi
reproduzido duas vezes no mesmo SHA final e em worktree isolado; readiness de
produto permaneceu corretamente em `HOLD`. Esta prova não confirma áudio
humano, device físico, campo, piloto, produção, push ou deploy.

## Histórico negativo preservado

- app SHA inicial: `e716732efe5853731dab6ed401bad981cea09a70`;
- app tree inicial: `b85973fadd13c75191958a2c3dc906a83ae09547`;
- checkout puro: falha em
  `navigationTtsDeviceSmoke,routePlanPayloadE2e`;
- causa 1: caminho TTS hardcoded em `output/`, ignorado pelo Git;
- causa 2: contrato route-plan buscado no diretório pai do repositório;
- falso verde 1: busca textual por `ROUTE_PLAN_PAYLOAD_CONTRACT_OK` sem executar
  o contrato;
- falso verde 2: JSON local sem proveniência podia definir
  `operatorHeardPtBr=true`.

## Revisões e reprodução históricas

- `edfc94c707ca02b3a5e996fef6f45696e04da2bc`
- `b84c7d547be2237a0c0ae4f0cf3a0a0128fc40fe`
- `d209ab223268bd63a190558a1b34175616e198d0`
- `3c7c88ffca87ea921314fceeb68e02f51d0ef7ee`

- Comando registrado na origem: `./scripts/qa_gate_card.sh --no-e2e`.
- Comando registrado na origem: `./scripts/ci_quality_gate.sh`.
- Comando registrado na origem: `python3 scripts/qa_route_plan_payload_contract_test.py`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
