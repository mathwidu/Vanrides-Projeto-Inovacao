# Recibo histórico — VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-001-DOR

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-001-DOR.md`. SHA-256 do original: `dc8021434675d3a04cd6b73271a1da2fb20db2ac113a6b8dcd46a9833dbef8ce`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

## Veredito

`NOT_READY` em 2026-07-29.

O inventário e PRIV-002 estão concluídos, e a matriz possui binding imutável.
ADR-002 permanece `NOT_ACCEPTED`, todas as decisões humanas continuam
`PENDING_*` e não existe decisão de incidente/hold aceita.

Este DOR separa:

## Revisões e reprodução históricas

- `a3f7140e0a25fe5c440700146a42401500a87e4a`
- `830238cccda4b089fe7ef33fff9b755544dc0acc`
- `830238cccda4b089fe7ef33fff9b755544dc0acc`
- `dd863a45a5931a883277af73dbfc90a19c075f9e`



## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
