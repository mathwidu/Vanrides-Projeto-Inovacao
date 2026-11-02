# Recibo histórico — VR-WA-100-ATTENDANCE

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-WA-100-ATTENDANCE.md`. SHA-256 do original: `e357cf73f468e8869061242021e8ea7a9f58158fa40129a52e046633c3da7f84`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS pertencem às respectivas revisões históricas e não aprovam este checkout.

**PASS no laboratório em 2026-10-31.** O passageiro confirmou a ida citando o lembrete enviado para ele e depois corrigiu a resposta. A volta continuou pendente. O motorista escolheu a data e o sentido na conversa, viu um confirmado e um pendente e, depois das duas desistências, recebeu a lista com duas ausências.

## Suíte completa e jornadas

Os 1.494 testes da suíte completa do backend passaram em 210 classes. Nenhum foi pulado. A matriz obrigatória incluiu 409 testes PostgreSQL em 52 classes. Os cenários no mesmo JAR passaram nas 12 verificações do agente, dez das rotinas do motorista, dez do cadastro do passageiro e dez da presença. Os três testes HTTP retornaram 200, 200 e 400 conforme o contrato.

O ensaio usou a API Spring, o worker e o PostgreSQL reais e um receptor Evolution de loopback. Dois passageiros se cadastraram, um dos quais dispensou lembretes. Foram 64 entradas distintas, uma entrada repetida e 70 envios verificados no receptor. As duas perguntas chegaram na janela real das 20h de Manaus. O worker reiniciou e o segundo acionamento preservou os envios. Uma citação de outro passageiro foi recusada. O teste de horário, cancelamento e disputa pelo corte da saída também foi exercitado com relógio controlado na matriz PostgreSQL.

## Revisões e reprodução históricas

- `coordination:8dba2d803d7198dd4c7a496c1005b4b690915a87`
- `coordination:798fc9ae8611f1f2def530a5f6d7fc5ec820d56d`
- `coordination:a79cd76a471950e486e54e65ed52cf20f1365523`
- `coordination:1e1dbb56e1baa5dea612ce71707b15eedde2a581`
- `coordination:0e12bb8fc4276603050ae338565a4a7b628a6a18`
- `backend:3856974d0f9df9ac9ec6aea549a02134b066a795`
- `backend:f17a1b0dc9f6dfa0b272b58b967dec2305201799`
- `backend:8b4a7084821183fcc17637c534a3ed7f7fcbf376`
- `backend:8769c177d0ceb1337ef2fb576a8758c4623dfb7d`
- `backend:aa128db7f4cdbd5982e51903ac72f2b0cb083122`
- `backend:ebcae45014f70223f128be8b9c2d15c8b7d6226e`
- `backend:d6a395cf57bef9689485d9c48b7062cbe9a2f059`

- Comando registrado na origem: `./scripts/ci_quality_gate.sh`.
- Comando registrado na origem: `node integrations/whatsapp/scripts/100-attendance-smoke.mjs DIRETORIO_PRIVADO SHA_BACKEND`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
