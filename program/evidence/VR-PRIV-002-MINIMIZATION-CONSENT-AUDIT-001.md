# Recibo histórico — VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001.md`. SHA-256 do original: `f1c2740d9987d8b09cdb5f1cfc9ac8e813e0a6b3e004bdcb0a1a759c6227edbb`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

## Identidade

- data de validação: `2026-07-29`;
- timezone: `America/Sao_Paulo`;
- ambiente: macOS local, sem consulta a rede, banco, provider ou produção;
- branch: `codex/vanrides-next-quality-phase`;
- commit de implementação:
  `830238cccda4b089fe7ef33fff9b755544dc0acc`;
- tree de implementação: `2d803f6573554d99fc0d8c3064fe589e1adff391`;
- commits do freeze:
  `87ca3598b872d95b8b521508e9fdf899021321e1` e
  `375fe07f55521f6aec37a20908ff2a6326280b35` e
  `830238cccda4b089fe7ef33fff9b755544dc0acc`.

## Resultado

result=PASS
matrix_families=34
matrix_findings=19
matrix_successor_cards=31
program_cards=67

## Revisões e reprodução históricas

- `coordination:830238cccda4b089fe7ef33fff9b755544dc0acc`

- Comando registrado na origem: `python3 program/evidence/verify_vr_priv_002.py --coordination-ref HEAD --implementation-ref 830238cccda4b089fe7ef33fff9b755544dc0acc --expected-implementation-ref 830238cccda4b089fe7ef33fff9b755544dc0acc --expect-card-status done`.
- Comando registrado na origem: `python3 scripts/program_control_test.py`.
- Comando registrado na origem: `python3 scripts/program_control.py check`.
- Comando registrado na origem: `git diff --check e8845c912e0624d1a6d9706ed1ccb0ff95645013 HEAD --`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
