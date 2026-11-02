# Recibo histórico — VR-REL-001-BACKEND-WORKFLOW-DEFAULT-SAFE-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-REL-001-BACKEND-WORKFLOW-DEFAULT-SAFE-001.md`. SHA-256 do original: `ae9d9838fbfe2c3e63365cd152a6c37639285f45a2e0f74898ad9b54670975d6`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD, WAITING_HUMAN pertencem às respectivas revisões históricas e não aprovam este checkout.

## Veredito

`ANALYZED / ROUTE_A_SELECTED / B_REJECTED` em 2026-07-30.

Matheus selecionou explicitamente a Rota A. O investimento permanente pertence
a `VR-QA-BE-GATE-PROD-COMPATIBLE-001`; a execução atual fica limitada à
capability spike `VR-QA-BE-GATE-CAPABILITY-SPIKE-001`. A spike passou `15/15`
no protótipo inicial, `22/22` após a primeira correção, `26/26` após a correção
runtime e `32/32` duas vezes após a correção de protocolos
`29f2309c92cf8d43ecd350d997c577069ea50cb2`, seguida por `42/42` duas vezes
após snapshot/bindings e lifecycle de `BaseException` em
`69aa6c34baa18918c0bdd04bc4d84207008da148` e por `48/48` duas vezes após
snapshot único de output e correção do `FileRunLock` real em
`b58942d914c16dc7fd4e413c7949c59e50587811`, por `50/50` após cleanup
conservador no acquire em `31c0dbbd99c2c3cb810711fd7d8b84ae02feefab`,
por `56/56` após snapshot fail-closed e receipt pré-execução em
`8ad2fca45f1c7e50b010aaaa4647c3f32e7b205a` e por `58/58` após receipt
pós-release em `66301f5686487f54ed3b3f1077a131eb18b162da`, seguida por `63/63`
após guard pós-ownership, limites inteiros e precedência de control-flow em
`e3ea8a8c45c3aa5fb8c87b1c12b67637d8848b29`, sempre duas vezes e somente com
doubles sintéticos. Isso não torna o card permanente `ready`, não autoriza
copiar os 166 candidatos classificados e não autoriza importar os 419 commits
divergentes.

O texto histórico abaixo preserva a análise que rejeitou B. Seus marcadores
`ROUTE_SELECTION_NOT_OPEN` descrevem o snapshot de 2026-07-29 e foram
superseded pela decisão humana de 2026-07-30; não reabrem B.

## Revisões e reprodução históricas

- `29f2309c92cf8d43ecd350d997c577069ea50cb2`
- `69aa6c34baa18918c0bdd04bc4d84207008da148`
- `b58942d914c16dc7fd4e413c7949c59e50587811`
- `31c0dbbd99c2c3cb810711fd7d8b84ae02feefab`
- `8ad2fca45f1c7e50b010aaaa4647c3f32e7b205a`
- `66301f5686487f54ed3b3f1077a131eb18b162da`

- Comando registrado na origem: `git ls-remote`.
- Comando registrado na origem: `mvnw`.
- Comando registrado na origem: `./scripts/qa_gate_card.sh`.
- Comando registrado na origem: `./scripts/ci_quality_gate.sh`.
- Comando registrado na origem: `./mvnw test`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
