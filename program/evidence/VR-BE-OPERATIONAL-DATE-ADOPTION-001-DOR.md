# Recibo histórico — VR-BE-OPERATIONAL-DATE-ADOPTION-001-DOR

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-BE-OPERATIONAL-DATE-ADOPTION-001-DOR.md`. SHA-256 do original: `43e18030e9f4a616e8b240266192e4bba0ef99d31ed9359c3edc67c47df936e7`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

- data da análise: 2026-07-18
- veredito: `NOT_READY`
- motivo único: decisão de Produto da ADR-003 ainda não aceita
- card permanece: `analyzed`
- implementação de produto: não iniciada
- piloto: `HOLD`
- produção: `HOLD`

## Baseline observado

| Repositório | Branch | SHA/tree | Estado relevante |
|---|---|---|---|
| coordenação | `codex/vanrides-next-quality-phase` | `27c2f178de06cb661c8cee8a464b2f212e68ada9` | trabalho conhecido fora de escopo preservado |
| backend | `dev` | SHA `6efdb61846d0e4dcc59fd5cab921d275ad9eca98`; tree `237586b41e991d0ca5d896c8be900c5635f2c0c2` | limpo |
| app | `quality/app-quality-stack-2026-06-13` | `b53296b67dd0dc6ddb4b1125dcc64caadb8c7c6c` | trabalho preexistente no worktree principal; fixtures serão alterados somente em worktree limpa |
| mapas | `main` | `a0c2a659eee2` | limpo e fora de escopo |

O card vertical ativo durante esta descoberta é
`VR-QA-INTEGRATION-PROVENANCE-001`. Esta análise foi somente leitura no código
do produto e não consumiu um segundo WIP.

## Revisões e reprodução históricas

- `27c2f178de06cb661c8cee8a464b2f212e68ada9`
- `6efdb61846d0e4dcc59fd5cab921d275ad9eca98`
- `237586b41e991d0ca5d896c8be900c5635f2c0c2`
- `b53296b67dd0dc6ddb4b1125dcc64caadb8c7c6c`



## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
