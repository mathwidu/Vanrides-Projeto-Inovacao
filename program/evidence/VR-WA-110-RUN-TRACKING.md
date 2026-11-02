# Recibo histórico — VR-WA-110-RUN-TRACKING

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-WA-110-RUN-TRACKING.md`. SHA-256 do original: `d8551191c750c46ecfa0ff179a5c2da9bff98eabe7a4f8dc95bc4b3c061bedc6`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, FAIL pertencem às respectivas revisões históricas e não aprovam este checkout.

**Situação em 01/11/2026: jornada integrada estacionada aprovada no Redmi, incluindo GPS real e confirmação remota de parada após reabertura.** Cada linha identifica a revisão que produziu a evidência.

**Resultado atual — A8:** sete verificações físicas do executor Mac e cinco
verificações do backend passaram com APK12 `e022b8f2…b94cce`, produto `76a0fc5`,
laboratório `ef64acb` e backend `5ddb7c3`. O sensor parou sem conexão; após
encerrar/reabrir o processo lab, a parada pendente chegou ao servidor sem
reiniciar coleta. O ensaio preservou QA e prazos da falha A6. Captura, status,
comparação dos 38 serviços e remoção do diretório remoto passaram. Este resultado
não inclui percurso/bateria nem mensagens reais de WhatsApp. iPhone físico
permanece pausado; aplicativos do Mac foram mantidos abertos. A7 permanece
uma interrupção histórica de preparação, com causa não confirmada e recuperação
própria; o sucesso A8 não reclassifica aquela tentativa.

**Infraestrutura real aprovada em `redmi-vps-infra-a7`.** Fonte `63b9712`,
projeção `1ea0f17`, CI 41327221383 (referência histórica no original)
com 569 Node + oito transporte + 42 controlador. Pacote v7 SHA-256
`17cdb489e8cb4dd6bba0624b76b450f75a1cdcd05203809f0dba882937903e48`,
mesmo JAR Linux `fc5dccdf…d3063ce` e mesmas imagens fixadas. O smoke real passou:
HTTP saudável, SQL/SCRAM, portas limitadas ao loopback, janela global normal
antes/depois do hold, nenhum OOM/reinício/evento de limite. Pico de memória:
API 381.022.208 bytes e PostgreSQL 105.558.016 bytes; host com ao menos
10.781 MiB disponíveis. PSI some/full próprio e full global máximos zero.

Stop, status, captura do recibo, comparação dos 38 serviços e remoção do
diretório passaram. Recibo SHA-256
`90ef6ad49d85b82c9b2b91d570e11aae299ad1c6424c87ea60bc16b40845f07c`.
Um

## Revisões e reprodução históricas

- `coordination:c6512998bf2c814318b47bb43436d12f149ed944`
- `backend:5ddb7c3db6c38385c018028741470ab6a509604f`
- `backend:ffed57876769bf3a8b4305035e25d199b8aa5d29`
- `backend:1d5109b10a8e85ef14ba7ccf8e7091ebf94d10b5`
- `app:76a0fc56b859c896547074feee9b2c6966e0db8d`
- `app:b9fcedbbf949ad9f1684efcc1a2b0f2176cf7877`

- Comando registrado na origem: `python3 -B run_physical_owned_v7.py capture`.
- Comando registrado na origem: `python3 -B scripts/program_control_test.py`.
- Comando registrado na origem: `python3 -B scripts/workspace_profile_test.py`.
- Comando registrado na origem: `python3 -B scripts/program_control.py check --workspace core`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
