# VR-SEC-001-LOCATION-DOMAIN-001 — Fechar domínio, REST e retenção de GPS

## Resultado esperado

O backend grava e lê GPS somente por uma viagem autorizada, com lifecycle,
janela e purge aceitos separadamente, concorrência serializada, REST
trip-scoped e freshness em UTC. Reatribuição cria nova versão de escopo e nunca
herda a amostra do motorista anterior. O slice é aditivo/fail-closed, não
habilita Maps e permanece desligado enquanto ADR-005 estiver `NOT_ACCEPTED`.
Antes de ser promovível isoladamente, ele desliga e rejeita o realtime legado;
somente o sucessor REALTIME poderá habilitar destinos novos por usuário.

## Estado

`analyzed` — depende do aceite da ADR-005 no card coordenador.

## Dependências

- `VR-SEC-001-LOCATION-READ-SCOPE-001` concluído;
- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` concluído com o prazo
  normal aplicável a GPS aceito e vinculado por revisão/digest;
- `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` concluído com autoridade, escopo,
  prazo, release, auditoria e precedência de purge aceitos — ou `N/A` explícito
  para GPS por revisão/digest próprio;
- backend limpo no SHA revalidado;
- número Flyway não reservado enquanto `analyzed`; ao entrar em `ready`,
  revalidar o maior número presente/aplicado, reservar o próximo contínuo,
  atualizar a allowlist e provar ausência de collision/out-of-order;
- nenhum banco remoto ou dado real sem autorização de promoção separada.

Os valores de janela, cutoff, freshness e purge abaixo são exclusivamente
`CANDIDATE_FROM_ADR005 / NOT_ACCEPTED`. O freeze de `ready` só poderá substituir
os marcadores por valores de contrato após aceite humano exato da ADR-005; este
card não transforma candidato técnico em política.

## Escopo

- `startedAt`, `endedAt`, `locationSharingExpiresAt`, `locationClosedAt`,
  `locationScopeVersion` e `serverTime` em viagem/amostra/DTO conforme o dono do
  campo;
- lock do motorista antes da viagem e índice parcial de uma `IN_PROGRESS`;
- reassign ativo com locks dos dois motoristas em ordem determinística, versão
  incrementada, `204` até nova amostra e `409 TARGET_DRIVER_ACTIVE_TRIP`
  atômico;
- migration expand-first alocada no freeze de `ready`, com
  trip/company/capturedAt UTC, schema completo e inerte do
  outbox (idempotência, refs, recipient de controle, claim/retry/TTL, cascade,
  sem payload/GPS) e
  cutoff `UNSCOPED_LEGACY = migration + <retention_cutoff>`; 72h permanece
  somente candidato não aceito da ADR-005;
- writer REST zero/uma/múltiplas/expirada com `400/409` sem efeitos parciais;
- `GET /viagens/{viagemId}/localizacao` com `200/204/401/404` opaco;
- endpoint geral de histórico indisponível para todos os papéis e bridge de
  latest legado limitada a exatamente uma viagem ativa/autorizada;
- freshness `<fresh|stale|expired>`; 30s/90s/5min permanece somente candidato
  não aceito da ADR-005 e, após o limite aceito, nenhuma coordenada é servida;
- kill switch fail-closed: nenhum publish/subscribe de GPS nos tópicos STOMP
  legados enquanto o protocolo seguro ainda não estiver entregue;
- job/corte lógico de purge no prazo normal que vier a ser aceito; 72h permanece
  somente candidato não aceito da ADR-005, com restore seguro, métricas
  sanitizadas e suspensão somente por hold formal aceito no predecessor de
  incidente — nunca por ticket, flag ou pedido informal;
- contrato de promoção: backup lógico exclui GPS; físico/PITR permanece `HOLD`
  sem lifecycle/criptografia/acesso/restore-purge provados por `VR-OPS-002`;
- DTOs mínimos, contratos, testes PostgreSQL, gate e smoke do mesmo JAR.

## Fora de escopo

- novo protocolo STOMP, JWT de socket, Origin, outbox dispatcher e projeções
  realtime; o desligamento mínimo dos tópicos legados permanece neste card;
- mudanças no Flutter ou Maps;
- decidir a política de hold de incidente, backfill inferido, deploy ou
  promoção. A implementação do purge deve consumir a decisão aceita do
  predecessor de incidente, sem inventá-la neste card.

## Critérios de aceite

- [ ] zero/uma/duas viagens e corrida com start/complete/cancel/reassign falham ou
  convergem conforme ADR, sem ponto fora da janela;
- [ ] reassign incrementa a versão, revoga o writer anterior, nunca devolve sua
  amostra ao novo motorista, retorna `204` até nova captura e reverte tudo se o
  alvo já opera outra viagem;
- [ ] migration alocada no freeze migra do baseline PostgreSQL observado e
  impede duas `IN_PROGRESS` por motorista;
- [ ] migration prova constraints/índices completos do outbox sem armazenar
  payload ou coordenadas; nenhum outro card é predecessor apenas por numeração;
- [ ] legado fica invisível imediatamente e é purgado no prazo aceito; 72h é
  apenas candidato não aceito da ADR-005;
- [ ] viagem órfã fecha no lease aceito e ancora purge mesmo ainda
  `IN_PROGRESS`; 12h é apenas candidato não aceito da ADR-005;
- [ ] viagem A nunca retorna ponto de B; non-enumeration mantém bytes canônicos;
- [ ] `/historico` geral retorna o contrato 404 opaco para driver, admin,
  guardian e passenger, sem consultar a série; bridge latest resolve somente
  uma viagem ativa autorizada e nunca usa latest global;
- [ ] tópicos legados de localização/viagem não aceitam subscription nem
  publicam GPS até o sucessor REALTIME habilitar apenas destinos seguros;
- [ ] UTC/freshness e `200/204/401/404/409` passam em fronteiras de relógio;
- [ ] restore reaplica cutoff antes de liberar o ambiente;
- [ ] backup lógico restaurado contém zero GPS; manifest local registra que
  backup físico/PITR e promoção seguem `HOLD` até a prova de `VR-OPS-002`;
- [ ] gate backend, smoke same-JAR, QA Log, contrato e evidência passam limpos.

## Arquivos e execução

A allowlist canônica está em `program/program.json`. Implementar TDD em pequenos
incrementos, commitar somente no backend e registrar SHA/tree/JAR/run IDs.

## Freeze PRIV-002

O inventário pai está congelado em `a3f7140e0a25fe5c440700146a42401500a87e4a`
(SHA-256 `d1dc70c65a0d03ff23950c55c6433378b4294a31c896fd095764dfe1d4be9891`).
Este card é owner do REST/persistência trip-scoped e purge técnico de
D11–D14/F-PRIV-003/004/005 e do subescopo F-PRIV-016 para DTOs REST GPS em
`/api/localizacao/**`, sem entidade/relação aninhada. Realtime, app, Maps e
decisões humanas permanecem em slices próprios; candidatos da ADR-005 seguem
`NOT_ACCEPTED`. O prazo normal vem da decisão cross-family e qualquer
suspensão/release vem exclusivamente da decisão de incidente. PRIV-002 não
autoriza GPS, prazo, provider, backup, hold ou novo path de produto.

## Rollback

Manter a migration expand-first já aplicada e os dados trip-scoped. Desligar
leitura/escrita de GPS é o fallback; nunca restaurar latest global, histórico
geral ou exposição de legado.
