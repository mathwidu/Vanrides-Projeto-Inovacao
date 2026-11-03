# VR-QA-004-LOCATION-INTEGRATION-001 — Provar GPS trip-scoped same-version

## Resultado esperado

O conjunto final de backend, app e Maps é provado no mesmo manifest: antes,
durante e depois da viagem, revogação, concorrência, expiração, fallback e
acessibilidade. Nenhuma prova de outro SHA/digest fecha o programa.

## Estado

`analyzed` — último slice local da política ADR-005.

## Dependências

- `VR-SEC-001-LOCATION-DOMAIN-001` concluído;
- `VR-SEC-003-LOCATION-REALTIME-001` concluído;
- `VR-MAP-001-LOCATION-S2S-001` concluído;
- `VR-APP-001-LOCATION-ADOPTION-001` concluído;
- `VR-SEC-001-APP-DRIVER-SWITCH` concluído;
- gates canônicos dos módulos em worktrees limpas.

## Escopo

- orquestrador read-only/local com PostgreSQL descartável e JAR único;
- app limpo conectado ao JAR avaliado e Maps/fallback no SHA registrado;
- matriz driver/passenger/guardian/admin, mesmo tenant sem vínculo e outro tenant;
- start/publish/revoke/complete/cancel/reassign/lease/JWT/Origin/uma réplica;
- cutoff local no instante da lease com closer online, atrasado e após restart;
- skew/latência simulados provando que a âncora `serverTime` remove cedo ou no
  prazo, nunca depois da expiração do servidor;
- manifest do modo de backup: lógico sem GPS ou físico/PITR ainda `HOLD` sem a
  prova operacional completa de `VR-OPS-002`;
- evidência manual acessível do app final;
- manifest com SHA/tree, migration, hashes, run IDs, ambiente e cleanup;
- verificar read-only que backlogs e QA Logs dos módulos já foram sincronizados
  pelos respectivos slices; atualizar somente card, risco, programa e evidência
  coordenadores.

## Fora de escopo

- push, CI remoto, staging, banco remoto, piloto ou produção;
- substituir `VR-QA-INTEGRATION-PROVENANCE-001` ou aprovação de promoção.

## Critérios de aceite

- [ ] gates backend/app/Maps passam nos mesmos SHAs limpos do manifest;
- [ ] viagem A nunca retorna/publica/renderiza ponto de B;
- [ ] revogação/término/reassign/lease/JWT exp cortam a próxima entrega e
  removem marker; amostra da versão anterior nunca reaparece;
- [ ] viagem órfã/legado/purge e fallback Maps passam sem dado real;
- [ ] restore lógico prova zero GPS; qualquer modo físico/PITR sem lifecycle,
  criptografia, acesso e purge pré-abertura mantém promoção em `HOLD`;
- [ ] matriz manual acessível final possui evidência humana identificada;
- [ ] logs/bundle não contêm GPS bruto, token, nomes privados ou trajetória;
- [ ] cleanup de processos/containers/worktrees temporárias é comprovado;
- [ ] conclusão local não altera `Pilot/Production HOLD` sem GO separado.

## Rollback

Este card não altera produto. Evidência inválida é descartada e repetida no mesmo
conjunto de SHAs; nunca reutilizar smoke antigo para fechar o gate.
