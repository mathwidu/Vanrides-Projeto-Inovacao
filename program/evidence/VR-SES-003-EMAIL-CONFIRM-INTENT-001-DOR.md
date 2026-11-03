# Recibo histórico — VR-SES-003-EMAIL-CONFIRM-INTENT-001-DOR

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-SES-003-EMAIL-CONFIRM-INTENT-001-DOR.md`. SHA-256 do original: `dea06dbf967f85c3f87e78c62339b57ef7035e219dc47a5bd8396d2b0ee66054`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

## Veredito

`READY`. O risco `R-019`, os consumidores, o contrato HTTP, a compatibilidade,
a superfície de logs, a landing, acessibilidade, testes, smoke e rollback estão
congelados para implementação local. Não existe dependência de app ou Maps.

## Snapshot auditado

- backend: `6efd2e2f66cb5ac3784085d317ba98174750c815`, limpo;
- app: `b53296b67dd0dc6ddb4b1125dcc64caadb8c7c6c`;
- Maps: `a0c2a659eee262758191dff83300ef5b1045ef08`;
- coordenação no fechamento do inventário: `371e9f93390d05a014bd9433c6a547b8b99a4006`;
- piloto, produção, push e deploy: `HOLD`.

## Revisões e reprodução históricas

- `backend:ab2ea3df7e4004bf71428bb49683bbd9772ff0de`
- `backend:1ea483dc26b2111fb1b6d2fefa31ef7d827755ac`
- `backend:dc54acf415e8f763e9b63e24771ca3e5bc0f4113`
- `backend:643bd80f6267c38414a06416f7d83fbf8c3a85b8`
- `backend:f2886e406179085bbc67d9976f9f027c7e40616b`
- `backend:9072a08a79ada4683444c821392eaba427f6c3b5`
- `backend:f3b7b9d9cb420f2b0c691676c09c100565e62113`
- `backend:b2dd00cf1e4e7cbfe6a7d50d606ee948d8b7f871`



## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
