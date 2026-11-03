# Release Manifest — <RUN-ID>

## Decisão

- lane: `INTEGRATION_CANDIDATE | RELEASE_CANDIDATE | CONTROLLED_PILOT | PRODUCTION`
- resultado: `PASS | FAIL | BLOCKED | HOLD | SKIPPED | WAIVED`
- decisão humana:
- aprovador:
- data:

## Identidade imutável

| Superfície | Branch | SHA | Artefato/digest | Dirty |
|---|---|---|---|---|
| workspace | | | | |
| backend | | | | |
| app | | | | |
| maps | | | | |

- hash da configuração de gates:
- versão Flyway/schema:
- versão do dataset/pacote de mapas:
- ambiente e URL:

## Compatibilidade

- backend N/N-1:
- app N/N-1:
- schema expand/contract:
- providers/fallback:

## Gates

| Gate | Comando/execução | Resultado | Evidência/checksum |
|---|---|---|---|
| unitário | | | |
| integração | | | |
| contrato | | | |
| segurança | | | |
| acessibilidade | | | |
| E2E | | | |
| desempenho/carga | | | |
| backup/restore | | | |
| rollback | | | |

## Dispositivos e navegadores

<Matriz efetivamente executada.>

## Holds, blockers e waivers

<Nenhum, ou lista completa com validade e aprovador.>

## Rollback

- backend:
- app:
- maps:
- banco:
- configuração:
- critérios de acionamento:

## Observação

- janela:
- SLOs:
- alertas:
- dono:
- critérios de encerramento:

## Sanitização

- [ ] nenhum segredo ou credencial;
- [ ] nenhum dado pessoal desnecessário;
- [ ] artefatos referenciados por checksum;
- [ ] manifest corresponde exatamente ao que foi executado/promovido.
