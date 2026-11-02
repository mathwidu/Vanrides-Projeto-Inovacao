# VR-SEC-003-LOCATION-REALTIME-001 — Fechar realtime de localização

## Resultado esperado

GPS realtime deixa de usar broadcast tenant-wide e passa por STOMP
deny-by-default, destinos de usuário, autorização por publicação, outbox
pós-commit e sessão limitada pelo JWT. O runtime permanece em uma réplica até
existir broker/user registry compartilhado.

## Estado

`analyzed` — depende do domínio trip-scoped concluído.

## Dependências

- `VR-SEC-001-LOCATION-DOMAIN-001` concluído com sua migration trip-scoped
  alocada no freeze e provada na mesma versão;
- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- backend limpo no SHA revalidado;
- escala horizontal continua bloqueada pelo épico `VR-OPS-004`, mas não é
  precondição deste slice de réplica única nem uma aresta `dependsOn`.

## Escopo

- bloquear `/topic/localizacao/**`, `/queue/**` direto e destinos desconhecidos;
- `/user/queue/viagens/{viagemId}` e ACK de writer por `correlationId`;
- recipients/projeções por papel recalculados em cada publish;
- projeções de passageiro/guardian sem nome, ID ou dado de outro passageiro e
  sem manifesto administrativo;
- outbox sem GPS, TTL 5min/24h, sem DLQ com payload e tombstones pós-commit;
- closer de lease com `LOCATION_EXPIRED` e tombstones `DRIVER_REASSIGNED`,
  `TRIP_TERMINATED` e `LOCATION_REVOKED` para a audiência anterior;
- JWT `exp` em inbound/outbound e fechamento físico de sessão passiva;
- Origin/CORS HTTPS allowlisted e wildcard rejeitado em runtime público;
- cliente nativo sem `Origin` continua exigindo JWT válido e política por
  viagem/papel;
- habilitar realtime somente quando destinos/projeções seguros estiverem
  prontos; tópicos legados permanecem negados permanentemente;
- stacks/status/preflight com uma réplica `stop-first`;
- testes STOMP, concorrência, retry/idempotência, gate e smoke.

## Fora de escopo

- adoção dos novos destinos no app;
- broker externo/escala horizontal;
- Maps, UI, refresh rotation ou promoção.

## Critérios de aceite

- [ ] conta sem vínculo, outro tenant e destino desconhecido recebem zero GPS;
- [ ] projeções de passageiro/guardian não contêm nome, ID ou dados de outro
  passageiro nem manifesto administrativo;
- [ ] REST/STOMP compartilham conflitos `UNAVAILABLE/AMBIGUOUS/EXPIRED`;
- [ ] commit falho não publica e retry não duplica evento;
- [ ] término/revogação/reassign entrega tombstone sem GPS à audiência anterior;
- [ ] lease corta autorização exatamente no relógio e o closer entrega
  `LOCATION_EXPIRED` idempotente mesmo após atraso/restart, sem republicar GPS;
- [ ] token expirado bloqueia inbound/outbound, fecha socket passivo e nunca fica
  armazenado/logado bruto;
- [ ] Origin arbitrário/wildcard falha e origem HTTPS explícita passa;
- [ ] cliente nativo sem `Origin` e JWT ausente/inválido falha; com JWT válido
  ainda passa por política por viagem/papel;
- [ ] runtime/preflight falham com mais de uma réplica até `VR-OPS-004`;
- [ ] gate backend, smoke STOMP, QA Log e evidência passam no mesmo SHA/JAR.

## Freeze PRIV-002

O inventário pai está congelado em `a3f7140e0a25fe5c440700146a42401500a87e4a`
(SHA-256 `d1dc70c65a0d03ff23950c55c6433378b4294a31c896fd095764dfe1d4be9891`).
Este card é owner dos destinations/authorization/projections realtime de
D12–D15/F-PRIV-003/005. REST, app e decisão humana de audiência/janela ficam em
slices próprios; PRIV-002 não habilita broadcast, provider ou path adicional.

## Rollback

Desligar realtime e manter polling REST trip-scoped. Nunca reativar tópico amplo
ou sessão além do JWT para preservar compatibilidade.
