# GOV-DASHBOARD-RUNTIME-FRESHNESS-001 — Separar dashboard determinístico de estado runtime

## Resultado esperado

O dashboard rastreado é uma projeção determinística e validável da fonte
canônica, sem afirmar HEAD/dirty counts que envelhecem no próprio commit. Estado
runtime continua disponível por comando/snapshot explicitamente volátil, com
timestamp e origem, e nunca é confundido com evidência da versão promovida.

## Estado

`analyzed` — a impossibilidade de manter o próprio HEAD atual em um arquivo
rastreado está comprovada, mas o contrato de separação e a migração compatível
com harness/consumidores ainda precisam ser congelados.

## Risco ou objetivo associado

`R-003`, `R-010`, `VR-PGM-004` e G0.

## Impact Analysis preliminar

### Contexto

`dashboard` coleta branch, HEAD e dirty count vivos e grava esses valores em
`.dev-harness/dashboard.md`. Assim que o arquivo entra num commit, o HEAD de
coordenação muda e o snapshot já está stale. `validate_dashboard()` hoje
compara marker, card ativo e decisões de release, mas não detecta os campos
voláteis divergentes.

### Opções a congelar

1. remover fatos runtime do arquivo rastreado e mantê-los apenas em `summary`;
2. gerar um snapshot runtime ignorado separado, com timestamp/TTL e status
   explícito;
3. manter no dashboard apenas baselines estáveis de `repositories.json` e link
   para consulta live.

### Riscos e mitigação

- quebrar harness/consumidor: inventariar leitores e adicionar testes;
- perder diagnóstico rápido: preservar comando live e formato legível;
- tratar snapshot velho como prova: incluir volatilidade/TTL e nunca usá-lo em
  promoção;
- reintroduzir recursão do próprio SHA: teste pós-commit conceitual e saída
  determinística sem HEAD atual no arquivo rastreado.

## Dependências

- `GOV-WAITING-STATE-INTEGRITY-001` concluído;
- inventário dos leitores de `.dev-harness/dashboard.md` e `status.json`;
- decisão sobre projeção rastreada versus snapshot ignorado.

## Escopo

- gerador/validador/testes do dashboard;
- dashboard/status, documentação, risco, card e evidência;
- compatibilidade com `summary`, `check`, `next` e harness local.

## Fora de escopo

- métricas de produto, observabilidade de produção ou dashboard externo;
- alterar código backend/app/Maps;
- push, deploy, piloto, produção ou evidência remota;
- resolver toda a reconciliação legada de `VR-PGM-002`.

## Definition of Ready

- [ ] leitores e contratos atuais inventariados;
- [ ] opção escolhida com compatibilidade e rollback;
- [ ] formato determinístico e formato runtime definidos;
- [ ] TTL/origem/semântica de evidência do snapshot definidos;
- [ ] testes positivos, stale/negative e pós-regeneração especificados;
- [ ] dirty paths e impacto no harness congelados.

## Critérios de aceite preliminares

- [ ] arquivo rastreado não contém fato que envelhece por causa do próprio
  commit;
- [ ] projeção canônica é comparada integralmente ou por contrato explícito;
- [ ] estado live informa origem/tempo e não é prova de promoção;
- [ ] `dashboard`, `summary`, `check` e `next` permanecem coerentes;
- [ ] testes cobrem drift, overwrite, estado idle e WIP;
- [ ] evidência registra antes/depois, comandos e rollback.

## Rollback

Reverter gerador, validador, testes e formatos juntos. Se algum consumidor não
for compatível, restaurar o dashboard anterior com aviso explícito de snapshot
volátil; nunca declarar seus HEAD/dirty counts como atuais sem nova consulta.
