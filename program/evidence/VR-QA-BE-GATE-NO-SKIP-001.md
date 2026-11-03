# Recibo histórico — VR-QA-BE-GATE-NO-SKIP-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-QA-BE-GATE-NO-SKIP-001.md`. SHA-256 do original: `319fdc2d520a2e17e5f9b1fa7fada1a063c576c4a5a5791504fdc7f7fc3abe55`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

## Veredito

`PASS_LOCAL / DONE` em 2026-07-17. O gate canônico do backend executa uma
única suíte Maven ampla, exige Docker local inspecionado, comprova a matriz
PostgreSQL obrigatória e falha se qualquer relatório Surefire do run contiver
skip, failure ou error.

O gate local por card usa o mesmo caminho antes do smoke HTTP. A lane funcional
cross-repo da mesma versão também passou depois de três feedbacks fail-closed
que não publicaram manifesto `PASS`.

Este resultado não comprova execução remota do GitHub Actions e não autoriza
push, build/push de imagem, deploy, release, piloto ou produção. As decisões
`pilot=HOLD` e `production=HOLD` permanecem inalteradas.

## Revisões e reprodução históricas

- `6b36830fba4f8b2b37a249e2db549b0d04e48f81`
- `157a80380b79b0dd22aaaf56b9bcf56ba7157f02`
- `d501d3c0dd5df6fda37e14a0a32b5161acba7047`
- `3e72c0548cd7f6d644a3a141cd879a54ff188d48`
- `07a3493110eb5742a684aa91c8797ab68413c742`
- `68e231eaca0dcb6f60281631507d2ce675d1760b`
- `de02c7940d6888242f775b025c61f06a98b326b7`
- `8f44ab45c21e7b86a38a11f7522fcda718b8783d`
- `6ccb0e087e5a5dd8a812518b35398adf38037bf6`
- `e645762ea3538877955ea1542623abdacb861ec5`
- `f0aa378b9a7f78f03b9e16fef6b30e60c16f4e38`
- `cc4b46a18335322cf4a230af7ae709d6b259a156`
- `797224156eed7a0e81697d66f6163f183f77a33c`
- `be1cb2780f12d661e4a3d5153982ac10dd9ae387`
- `528a4c40d4eb76c1eacf66334ba3b51a38eef650`
- `29257a35a22128a4937bae4cd8dcf9f63c7e9098`

- Comando registrado na origem: `VANRIDES_QA_REQUIRE_CLEAN=1 ./scripts/ci_quality_gate.sh`.
- Comando registrado na origem: `VANRIDES_QA_REQUIRE_CLEAN=1 ./scripts/qa_integration_profile.sh`.
- Comando registrado na origem: `VANRIDES_QA_REQUIRE_CLEAN=1 ./scripts/qa_rbac_suite.sh`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
