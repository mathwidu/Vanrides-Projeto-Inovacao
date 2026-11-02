# Recibo histórico — VR-QA-BE-GATE-CAPABILITY-SPIKE-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-QA-BE-GATE-CAPABILITY-SPIKE-001.md`. SHA-256 do original: `20fe539cce8ede53ba04f7b874b47f5feb58969a3853d5bf9d5963c597bd4235`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD, FAIL pertencem às respectivas revisões históricas e não aprovam este checkout.

## Veredito da spike

`PASS / CAPABILITY_SPIKE / NOT_A_GATE` em 2026-07-30.

Matheus selecionou explicitamente a Rota A e autorizou uma capability spike
local bounded. A seam foi demonstrada por interface e adapters sintéticos, mas
a execução dos adapters reais de Maven/Docker/PostgreSQL/smoke continua
intencionalmente não provada. Este PASS conclui o experimento; não aprova o
card permanente, Gate A, Gate B ou Gate C.

## Identidade congelada

## Revisões e reprodução históricas

- `ba6bc0722e41b0a24534155c8d02b53cd422bada`
- `2cd9c279aeea56ce6a535734bb4575f00b02fae1`
- `c72b85d8c2c99638a0439099e93376240e29a1ef`
- `b15837c5ec3ebf4e73ae546efe2d00704947322c`
- `fbddba1847e13707b7301b6afdc5a71122e56075`
- `29f2309c92cf8d43ecd350d997c577069ea50cb2`
- `69aa6c34baa18918c0bdd04bc4d84207008da148`
- `b58942d914c16dc7fd4e413c7949c59e50587811`
- `31c0dbbd99c2c3cb810711fd7d8b84ae02feefab`
- `8ad2fca45f1c7e50b010aaaa4647c3f32e7b205a`
- `66301f5686487f54ed3b3f1077a131eb18b162da`
- `e3ea8a8c45c3aa5fb8c87b1c12b67637d8848b29`

- Comando registrado na origem: `git diff/show --check`.
- Comando registrado na origem: `mvnw`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
