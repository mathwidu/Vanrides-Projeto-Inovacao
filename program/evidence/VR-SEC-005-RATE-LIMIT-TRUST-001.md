# Recibo histórico — VR-SEC-005-RATE-LIMIT-TRUST-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-SEC-005-RATE-LIMIT-TRUST-001.md`. SHA-256 do original: `91233261f5760987631ab69589c677af6d289dccdc42bf8c9ae1539fab49d806`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

## Veredito

`PASS_LOCAL_TECHNICAL` e `READY_TO_CLOSE` no escopo implementável localmente.
Este resultado conclui o card de código e prova local, mas não autoriza piloto,
staging ou produção. A promoção continua em `HOLD` até a validação separada da
topologia real de ingress.

## Identidade da versão

- data local: 2026-07-17, `America/Sao_Paulo`;
- repositório: `vanrides-backend`;
- branch: `dev`;
- baseline vulnerável: `4824f54abab0ad978502cb2e056115c5c9348a28`;
- SHA final avaliado: `46cd5e8a8dc7d2a3f76771fb7d65aba286a71d06`;
- tree final: `eb504aee161f3529c1da105ad6681c2e0667c80e`;
- estado do backend no gate e no encerramento: limpo;
- Graphify no SHA final: 11.610 nós, 24.253 relações e 693 comunidades.

## Revisões e reprodução históricas

- `backend:cbf93667020b94f5afea5fc7e654f0a156007438`
- `backend:a6053b895ad39aed924d10db01054138ab899727`
- `backend:0c405f3d50926f1c19dc0e41ba9143f172cf9246`
- `backend:d8f383085c3144b3a384deecc81023be58b9848a`
- `backend:98efc6307c06ec3a0ab7183678f2f3c841687662`
- `backend:e84a663d790cfdaf888ad9fe069468de6247c995`
- `backend:46cd5e8a8dc7d2a3f76771fb7d65aba286a71d06`
- `coordination:afaf31f8b337bb0809968734a0afbcae3e609a1c`



- Comando histórico transcrito do original (sem executar nesta migração): `VANRIDES_QA_REQUIRE_CLEAN=1 ./scripts/qa_gate_card.sh`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
