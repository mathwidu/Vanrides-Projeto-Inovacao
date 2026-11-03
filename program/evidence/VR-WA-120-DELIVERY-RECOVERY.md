# Recibo histórico — VR-WA-120-DELIVERY-RECOVERY

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-WA-120-DELIVERY-RECOVERY.md`. SHA-256 do original: `c9905e4eddcc4f12147f444568bf6827bc21d518e2c9aa122f843e4fbd87ab4f`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, FAIL, SKIPPED pertencem às respectivas revisões históricas e não aprovam este checkout.

## Estado — 2026-11-01

Em implementação. Ainda não há aceite integral deste card, callback real nem
promoção de piloto/produção. Dependências 050/110 concluídas; o110 encerrou na
coordenação 2a35796516954bd910c41461f461fd51e1f7392f, projeção 213a7a30e8881718edbca9929c894cf8ba948948,
CI 41358026276 (referência histórica no original)
SUCCESS com 585/585 Node. Essa CI pertence ao fechamento110, não ao código novo120.

## Análise e decisões

Inventário por fonte em backend 1d5109b10a8e85ef14ba7ccf8e7091ebf94d10b5 confirmou:
a base já tem unicidade, lock/lease, autorização, idempotência e UNKNOWN sem
retry. Conexão persistida ainda não bloqueia despachos; somente uma tentativa
é autorizada. Primeiro incremento trata conexão; segundo exige prova explícita
NOT_SUBMITTED antes de permitir nova tentativa. Revisão especializada aprovou
três tentativas totais, repetições após 5/15 s, sem repetir FAILED/REJECTED ou UNKNOWN.
Uma operação assíncrona expirada não pode prosseguir posteriormente até o POST.

## Revisões e reprodução históricas

- `2a35796516954bd910c41461f461fd51e1f7392f`
- `213a7a30e8881718edbca9929c894cf8ba948948`
- `1d5109b10a8e85ef14ba7ccf8e7091ebf94d10b5`
- `0c3114c27245acd4da1e0828435344ce81d7eda8`
- `970d15529aaabae92f2ead975b67a672ec0bc0c7`
- `b9fcedbbf949ad9f1684efcc1a2b0f2176cf7877`

- Comando registrado na origem: `node --test --test-concurrency=2 integrations/whatsapp/transport/evolution-connection.test.mjs`.
- Comando registrado na origem: `node --test --test-concurrency=2 integrations/whatsapp/transport/evolution-connection.test.mjs integrations/whatsapp/worker/dispatch.test.mjs integrations/whatsapp/scripts/070-worker-smoke.test.mjs`.
- Comando registrado na origem: `node --max-old-space-size=256 --test --test-concurrency=2`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
