# Recibo histórico — VR-WA-020-GPS-SPIKE

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-WA-020-GPS-SPIKE.md`. SHA-256 do original: `48a719689f5957c7d691b1f7f4bd9afbc4ea96a5ee0ae46014ff0fdec41c481f`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

Data: 2026-10-30. **PASS no escopo local de implementação**. Publicação e prova física030
pendentes. Este card prepara a coleta e o laboratório; não entrega ainda o
agente WhatsApp, cadastro, presença ou notificações reais do MVP.

## Fontes e escopo

- App de runtime: `02183270813550d020bf3e223d22be798de1fd85`.
- App, atualizações somente documentais/QA: `81f3c72b57f46e7d2bd00b1e5418b1e9ada729dd` e `1b1a1865949c680720a71d6b08c81695a2141f0b`.
- Receptor na coordenação: `0fb135d72217b63ab37cabfb28ba435017993b78`.
- Receptor na branch isolada de publicação: `9fd20d9140291c082a1f8630e94dffbe4ee38ce6`.
- Backend preservado: `f930e6eb7f156b285d65c1376818337063bfc0e9`.

Os blobs e modos do spike e dos dois workflows são iguais nos dois commits do
receptor. A branch `codex/gps-spike-publication` contém somente dois commits
sobre `86c523b434513bcf13baffe71e4474e25a313e8b`, main remoto verificado por
leitura. Os 330 commits históricos do checkout de coordenação não entram nessa
publicação. Nenhum push, merge remoto, dispatch, package ou deploy foi executado.

## Revisões e reprodução históricas

- `app:02183270813550d020bf3e223d22be798de1fd85`
- `app:81f3c72b57f46e7d2bd00b1e5418b1e9ada729dd`
- `app:1b1a1865949c680720a71d6b08c81695a2141f0b`
- `coordination:0fb135d72217b63ab37cabfb28ba435017993b78`
- `coordination:9fd20d9140291c082a1f8630e94dffbe4ee38ce6`
- `coordination:abbe926bd5ed63028919dfd40e03c9020dc35efb`

- Comando registrado na origem: `./scripts/qa_gate_card.sh --no-e2e`.
- Comando registrado na origem: `git archive`.
- Comando registrado na origem: `./verify.sh`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
