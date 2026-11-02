# VR-SEC-001-LOCATION-READ-SCOPE-001 — Aprovar política GPS trip-scoped e liberar entregas

## Resultado esperado

A política de GPS trip-scoped é aceita explicitamente, o contrato global fica
congelado e a implementação é liberada em cinco cards menores, cada um com gate,
rollback, evidência e commit próprios. Este card é a decisão coordenadora; ele
não altera código, schema, app ou infraestrutura.

## Estado

`analyzed / REFREEZE_REQUIRED` — a revisão histórica de 2026-07-18 foi
invalidada para aceite porque reservava números Flyway antes de `ready` e
criava ordem artificial entre cards. A autorização REST, STOMP, app e Maps
continua mapeada e os valores de janela/retenção permanecem candidatos
`NOT_ACCEPTED`, mas Produto/Privacidade não devem responder ao digest antigo.

Por estar `analyzed`, este card não ocupa o WIP global. Antes de voltar a
`waiting_human`, deve remover toda numeração antecipada, produzir ADR/DOR e
registro de aceite em nova revisão imutável e obter nova revisão independente.

Depois do aceite, este card ocupa o WIP apenas para registrar a ADR aceita,
sincronizar os cinco cards de entrega e concluir o checkpoint de decisão. Código
continua proibido neste escopo.

## Risco ou objetivo associado

`R-001`, `R-007`, épico `VR-SEC-001` e gates `G1 — TRUSTED`,
`G3 — INCLUSIVE`, `G6 — PILOT_READY`.

## Contexto comprovado

### REST

`VR-SEC-001-BE-READ` passou a exigir uma relação por `ViagemParada` do dia
operacional. A leitura subsequente, porém, ainda busca a última coordenada
global por `motoristaId + companyId`; o histórico retorna toda a série do
motorista. `PLANNED`, `IN_PROGRESS` e `COMPLETED` autorizam a relação atual.

Amostra de outra viagem, antes do início ou depois do término pode atravessar a
fronteira. A consulta de latest também carrega a lista completa para escolher o
primeiro item, e `/historico` serializa entidade JPA em vez de DTO mínimo.

### Realtime

O interceptor STOMP compara somente o tenant:

- qualquer conta do tenant pode assinar `/topic/localizacao/{companyId}`;
- qualquer conta do tenant pode assinar
  `/topic/viagens/{companyId}/{viagemId}` se conhecer o ID;
- o tópico bruto recebe GPS de todos os motoristas;
- o snapshot de viagem inclui GPS, próximo passageiro e dados da parada;
- cancelamento publica snapshot terminal ainda composto com latest global.

O Flutter filtra `motoristaId` ou `viagemId` depois de receber a mensagem. Isso
é robustez de apresentação, não autorização.

O JWT é validado apenas no `CONNECT`, o endpoint aceita qualquer Origin e o
broker/user registry são locais à JVM. Sem correção, uma sessão passiva pode
receber GPS depois de `exp` e uma segunda réplica pode consumir o outbox sem
alcançar a conexão mantida na primeira.

### Persistência e tempo

`localizacao_motorista` possui apenas coordenadas, `TIMESTAMP` sem timezone e
`motorista_id`, sem `viagem_id`, tenant de captura, retenção ou índice
trip-scoped. `Viagem` possui data/horário planejados e status, mas não captura
instantes reais de início/fim. Publicar GPS pode selecionar/criar viagem do dia
sem exigir exatamente uma `IN_PROGRESS`.

### Maps Platform

Quando habilitado, o adapter backend envia ao route planner GPS do motorista,
coordenadas e nomes usados como labels. `POST /maps/routes/plan` não autentica
o chamador, a stack DEV configura sua exposição via Traefik e o adapter OSRM
coloca coordenadas no path da chamada interna. `no-store` não elimina access
logs ou tracing.

O plano documental de telemetria propõe 72 horas para GPS bruto e até sete dias
por incidente auditado, mas declara que endpoint, banco, migration e UI ainda
não implementam o TTL. A retenção efetiva atual é indefinida; a recomendação da
ADR-005 substitui a exceção de sete dias por nenhuma extensão informal neste
ciclo, até existir um fluxo de hold próprio.

## Decisão humana congelada

Fonte: `program/decisions/ADR-005-trip-scoped-location-access.md`.

A recomendação é:

1. coleta/exposição somente em viagem `IN_PROGRESS`;
2. janela `[startedAt, endedAt)` sem tolerância externa;
3. lease máxima de 12 horas, com `serverTime`, `LOCATION_EXPIRED` e cutoff local
   conservador no app mesmo sem entrega do tombstone;
4. exatamente uma viagem ativa por writer; zero/múltiplas/lease expirada
   retornam `409` sem persistência/evento; reatribuição incrementa
   `locationScopeVersion`, revoga o writer anterior e só volta a exibir GPS após
   amostra do novo motorista;
5. latest REST por `viagemId`, `404` opaco e freshness 30s/90s/5min;
6. nenhum histórico bruto para passageiro/guardian;
7. driver próprio e admin apenas ao vivo; incidente histórico exige fluxo
   futuro próprio;
8. retenção ativa de 72 horas após fechamento, backups lógicos sem GPS e backup
   físico/PITR inevitável criptografado/inacessível por no máximo sete dias da
   criação e nunca além de `locationClosedAt + 10 dias` ou, no legado,
   `legacyClassifiedAt + 10 dias`;
9. legado sem `viagem_id` não recebe backfill/exposição e é eliminado 72 horas
   após a classificação pela migration alocada somente no freeze de `ready`;
10. tópico bruto por empresa é desativado;
11. STOMP é deny-by-default, com ACK e eventos por usuário/papel, autorização
    recalculada e tombstones pós-commit para término, revogação, reatribuição e
    expiração;
12. Maps é interno/autenticado com segredo rotacionável, recebe IDs opacos e não
    registra dados reais;
13. estados acessíveis removem marcador sem posição utilizável;
14. app antigo degrada de forma segura;
15. rollback nunca restaura o contrato amplo.

O aceite deve citar o commit e SHA-256 da revisão, identificar quem responde por
Produto e Privacidade — ou declarar uma pessoa acumulando ambos os papéis — e
reconhecer que validação jurídica/DPO e `GO` de piloto/produção continuam
separados. A frase exata e as alternativas estão na ADR-005 e no DOR.

## Decomposição canônica

| Ordem | Card | Resultado isolado |
|---|---|---|
| 1 | `VR-SEC-001-LOCATION-DOMAIN-001` | lifecycle/lease, migration alocada em `ready`, REST/histórico, legado/purge e kill switch STOMP antigo |
| 2 | `VR-SEC-003-LOCATION-REALTIME-001` | habilitação segura por usuário, JWT/Origin, outbox/tombstones e réplica única |
| 3 | `VR-MAP-001-LOCATION-S2S-001` | Maps interno autenticado, rede/segredo/logs e fallback seguro |
| 4 | `VR-APP-001-LOCATION-ADOPTION-001` | app trip-scoped, token fresco, estados acessíveis e todos os consumidores |
| 5 | `VR-QA-004-LOCATION-INTEGRATION-001` | gates nos SHAs finais e smoke/manifest cross-repo |

`VR-SES-001-REFRESH-ROTATION-001` é independente da numeração da migration e
entra antes da adoção final do app;
`VR-SEC-001-APP-DRIVER-SWITCH` permanece um hardening posterior do ciclo visual
A → B → A e também antecede a integração final.

O campo `deliverySurfaceArchive` do programa preserva somente o inventário
auditado que originou a divisão. Ele não é uma allowlist executável: durante
cada entrega, apenas o `allowedDirtyPaths` do card ativo autoriza mudanças.

## Impact Analysis congelada

### Backend — domínio e persistência

- adicionar instantes de lifecycle a `Viagem`, cobrindo início explícito e
  início/fim derivados de atualização de parada;
- congelar `locationSharingExpiresAt = startedAt + 12h` e usar
  `locationClosedAt = min(endedAt, locationSharingExpiresAt)` como corte;
- incluir `serverTime` e âncora temporal conservadora no contrato; sem âncora
  válida, o app não apresenta marcador;
- versionar o escopo de localização por atribuição de motorista; reassign ativo
  bloqueia ambos os motoristas em ordem determinística, incrementa
  `locationScopeVersion`, invalida latest anterior e falha atomicamente se o
  alvo já opera outra viagem;
- criar `TripLocationScopeService` como única autoridade para resolver viagem,
  janela, tenant, papel, passageiro/guardian e conflito;
- persistir amostra com `companyId`, `viagemId`, `motoristaId` e `capturedAt`
  server-side;
- migration aditiva alocada somente no freeze de `ready`, sem inferir viagem
  para legado, e cutoff executável
  `legacyClassifiedAt + 72h`;
- query de latest usa viagem/janela no SQL e `LIMIT 1`, não coleção filtrada em
  memória;
- DTOs mínimos substituem entidade JPA no wire;
- job de retenção idempotente em lotes, corte lógico e métricas sanitizadas;
- backup lógico exclui GPS; backup físico/PITR excepcional usa criptografia,
  acesso restrito, lifecycle de sete dias, teto físico de dez dias e purge antes
  de qualquer restore servir tráfego;
- serializar `Instant` em UTC com `Z` e atualizar tracking/freshness sem
  `LocalDateTime` do host;
- nenhum log/evidence contém coordenada, payload completo ou path privado.

### Backend — contratos e realtime

- novo `GET /viagens/{viagemId}/localizacao` com `200/204/401/404` definidos;
- bridge legado, se mantido temporariamente, resolve única viagem ativa no
  servidor e nunca latest global;
- writer REST e STOMP compartilham a mesma transação/política e `409`;
- writer e lifecycle bloqueiam motorista/viagem na mesma ordem; índice parcial
  impede duas viagens `IN_PROGRESS` do mesmo motorista;
- bloquear `/topic/localizacao/{companyId}`;
- aplicar allowlist STOMP deny-by-default, sem `/queue/**` direto, e ACK tipado
  por `correlationId` para o equivalente STOMP aos conflitos REST;
- registrar somente `exp`/identidade da sessão, bloquear inbound/outbound e
  desconectar no vencimento, inclusive conexão passiva;
- usar allowlist HTTPS explícita de Origin/CORS e guard público sem wildcard;
- substituir broadcast por destino de usuário/viagem;
- publisher recalcula destinatários e projeção a cada evento;
- persistir outbox na transação e publicar somente após commit;
- término/revogação/reatribuição captura audiência anterior e envia tombstone
  idempotente sem GPS; o closer da lease faz o mesmo com `LOCATION_EXPIRED`;
- passageiro/guardian não recebe nome/ID de outro passageiro;
- terminal remove coordenadas e novas entregas;
- autenticação, CORS, erro canônico e non-enumeration permanecem invariantes.

### App

- `TripLiveLocationGateway` recebe `viagemId`, não somente `motoristaId`;
- usar parâmetro nomeado/tipo de ID distinto e migrar todos os call sites do
  painel (passageiro, guardian, driver e admin), impedindo troca silenciosa entre
  `motoristaId` e `viagemId`;
- `WebSocketService` e `TripRouteEventService` usam destinos por usuário/viagem;
- tratar ACK STOMP aceito/rejeitado e tombstones de término/revogação;
- obter token fresco por provider serializado; nunca reconectar com JWT expirado
  e, sem refresh/reauth, mostrar sessão expirada sem GPS;
- responsável deixa de consultar histórico global e usa latest/eventos da
  viagem selecionada;
- terminal/revogação remove marker e estado live imediatamente;
- `locationSharingExpiresAt` agenda cutoff local determinístico; reatribuição ou
  mudança de `locationScopeVersion` limpa marker e invalida respostas/eventos da
  versão anterior;
- estados vazio, planejado, degradado e encerrado possuem mensagem e ação
  acessíveis, sem depender só de mapa/cor/animação;
- Semantics anuncia somente transições deduplicadas, não cada coordenada;
- VoiceOver/TalkBack, text scaling 200%, contraste WCAG 2.2 AA, foco, alvos 48dp
  e redução de movimento entram na matriz manual;
- fakes, records, tests e E2E são atualizados no mesmo contrato.

A correção detalhada de ciclo de vida A → B → A, respostas tardias e dispose
permanece no sucessor `VR-SEC-001-APP-DRIVER-SWITCH`; este card só migra o
contrato/call sites e impede consumo do tópico amplo.

### Maps

- autenticar `/maps/routes/plan` serviço-a-serviço e limitar abuso;
- usar secret files com rotação `current/next`, comparação constante e
  fail-closed antes de ler o body;
- limitar overlap da rotação a 24h e usar overlay compartilhada criptografada;
- expor route planner somente em rede/rota interna; health pode permanecer
  separado;
- remover nomes/endereços textuais e usar IDs opacos no request;
- impedir logs de body/path/coordenadas/geometria em serviço, Traefik e engines;
- manter provider real/OSRM desabilitado enquanto sua configuração versionada
  não provar ausência de access logs com coordenadas;
- manter requests/respostas efêmeros e sem cache compartilhado;
- usar somente fixtures públicas/sintéticas nos testes;
- se o hardening não estiver na composição, backend desabilita adapter e usa
  fallback local seguro.

### Contratos compartilhados

| Contrato | Mudança |
|---|---|
| REST latest | driver-scoped global → trip-scoped |
| REST histórico | geral por motorista → indisponível na UI normal |
| writer | qualquer momento do driver → única viagem ativa |
| reatribuição | troca de FK/snapshot antigo → nova versão de escopo, `204` até nova amostra e tombstone |
| concorrência | read/save/publish soltos → lock do motorista + constraint + outbox |
| STOMP localização | tópico tenant-wide → desativado |
| STOMP viagem | broadcast tenant/trip → projeção por usuário/papel |
| STOMP writer | fire-and-forget → ACK tipado por correlation ID |
| sessão STOMP | auth só no CONNECT → inbound/outbound + disconnect em `exp` |
| WebSocket Origin | `*` → allowlist HTTPS e guard público fail-closed |
| realtime multi-réplica | broker JVM → réplica única até `VR-OPS-004` |
| evento de corte | podia conter/manter GPS → tombstones sem coordenadas para término, revogação, reassign e lease |
| Maps route plan | público/labels humanos → interno autenticado/IDs opacos |
| freshness | relógio/local amplo → UTC 30s/90s/5min; depois sem coordenada |
| retenção | indefinida → lease 12h + purge ativo 72h; backup lógico sem GPS e físico limitado/protegido |

### Riscos e mitigação

- quebrar app antigo: backend entra primeiro com bridge REST segura e realtime
  pode degradar; nunca dual-publish amplo;
- driver publicar antes de iniciar: UI orienta iniciar viagem; `409` é estado
  explícito, sem coleta;
- duas viagens ativas: fail-closed e alerta sanitizado, sem escolha arbitrária;
- writer versus encerrar/cancelar/reassign: lock comum, versão de escopo, índice
  parcial e outbox pós-commit evitam ponto/evento fantasma ou latest herdado;
- viagem esquecida `IN_PROGRESS`: lease fecha acesso/coleta e ancora purge;
- closer atrasado: autorização server-side e timer local cortam no instante da
  lease; `LOCATION_EXPIRED` é limpeza idempotente, não fonte de autorização;
- revogação após subscribe: destinatários recalculados no publish, não apenas
  no handshake, e tombstone pós-commit limpa o cliente anteriormente autorizado;
- JWT expirar com socket passivo: interceptor outbound + sweeper desconectam; app
  não reutiliza token velho;
- broker local em réplica errada: stacks/preflight fixam uma réplica stop-first;
  escala horizontal continua bloqueada por `VR-OPS-004`;
- migration impedir rollback: expand-first, campos legados tolerados e
  constraints finais posteriores;
- Maps indisponível após hardening: fallback local, não bypass de auth;
- necessidade de preservar incidente: card/ADR próprios antes de suspender TTL;
- job atrasar: corte lógico nas queries e alerta operacional;
- backup prolongar retenção: lógico exclui GPS; físico/PITR fica inacessível,
  criptografado, autoexpira e só restaura após purge, com prova em `VR-OPS-002`;
- logs de proxy/engine: testes e configuração explícita de redaction/no access
  path para rota sensível.

## Escopo

- aceitar ou substituir os quinze itens da ADR-005;
- registrar aprovador(es), papéis, timestamp, commit/digest aceito e ciência de
  que jurídico/DPO e promoção continuam pendentes;
- mudar ADR/DOR de `proposed/NOT_READY` para `accepted/READY`;
- sincronizar dependências, prioridades e allowlists dos cinco cards derivados;
- registrar evidência e commit local de decisão;
- selecionar explicitamente somente o primeiro card executável depois de
  concluir este checkpoint.

## Fora de escopo

- replay histórico para UI;
- analytics/telemetria de trânsito, rerouting automático ou perfil de rota;
- backfill inferido de localização legada;
- legal hold geral sem caso/autoridade definidos;
- corrida visual detalhada de troca A → B → A, respostas REST tardias e dispose,
  mantida em `VR-SEC-001-APP-DRIVER-SWITCH`;
- timezone por empresa;
- deploy, banco remoto, dados reais, piloto ou produção durante implementação
  local;
- manter contrato inseguro por compatibilidade;
- qualquer alteração de código, migration, app, Maps, stack ou smoke de módulo;
  essas mudanças pertencem exclusivamente aos cards derivados.

## Dependências

- `VR-SEC-001-BE-READ` concluído;
- nova revisão ADR-005 congelada e aceita ou substituída;
- os cinco cards derivados existem no programa com dependências/allowlists
  válidas;
- baseline, worktree app, alocação Flyway por card em `ready`, réplica única e
  gates externos são dependências dos cards de implementação correspondentes,
  não deste checkpoint.

## Definition of Ready

- [x] problema e risco comprovados em REST, schema, STOMP, app e Maps;
- [x] finalidade, papéis, janela e retenção propostos;
- [x] contrato REST/realtime/Maps e compatibilidade propostos;
- [x] legado, migration, rollout e rollback propostos;
- [x] superfícies/consumidores e arquivos previstos mapeados;
- [x] matriz de testes, gates e same-version smoke definida;
- [x] estratégia de isolamento do app dirty definida;
- [ ] Produto/Privacidade aceitam ou substituem os quinze itens da ADR-005;
- [ ] `ADR-005-acceptance.md` identifica aprovador(es)/papéis, timestamp,
  commit/SHA-256 e ciência das pendências jurídica/DPO/promoção;
- [ ] ADR-005 fica `accepted` e o DOR registra `READY`;
- [ ] programa sincroniza status `ready` e seleciona o card explicitamente.

## Critérios globais delegados

Os critérios abaixo não são DoD deste checkpoint de decisão. Eles formam o
contrato de conclusão conjunto dos cinco cards derivados e do smoke final; cada
card referencia o subconjunto que prova.

- [ ] GPS novo só persiste com uma única viagem própria `IN_PROGRESS` e escopo
  completo gerado pelo servidor.
- [ ] zero/múltiplas viagens retornam conflito determinístico e não persistem,
  publicam ou chamam Maps.
- [ ] `startedAt`/`endedAt`/lease cobrem todas as transições explícitas/implícitas;
  viagem órfã passa de 12h sem nova coleta/leitura/evento, app usa `serverTime`
  para cortar conservadoramente, `LOCATION_EXPIRED` limpa a audiência anterior
  e a lease ancora o purge.
- [ ] writer concorrente com start/complete/cancel/reassign não cria duas viagens
  ativas, ponto fora da janela ou evento sem commit.
- [ ] reassign incrementa `locationScopeVersion`, revoga imediatamente o writer
  antigo, não reaproveita sua amostra, retorna `204` até nova captura e falha
  atomicamente se o novo motorista já opera outra viagem.
- [ ] viagem A nunca lê/recebe amostra da viagem B do mesmo motorista.
- [ ] linha legada/null/ambígua nunca atravessa para produto.
- [ ] a migration alocada em `ready` torna legado invisível, fixa cutoff
  agregado/auditável e o elimina 72h
  após classificação, inclusive em restart/restore, sem usar timestamp antigo.
- [ ] passageiro/guardian recebem somente própria viagem/projeção; nenhum dado
  de outro passageiro.
- [ ] driver próprio e admin tenant-scoped respeitam finalidade ao vivo.
- [ ] cancelamento, conclusão, revogação, reassign e lease cortam a próxima
  entrega; tombstone pós-commit/closer chega à audiência anterior sem
  coordenadas e é idempotente.
- [ ] tópico bruto por empresa não publica nem aceita subscription.
- [ ] STOMP rejeita destinos desconhecidos/diretos e devolve ACK tipado com os
  mesmos conflitos de domínio do REST.
- [ ] JWT expirado bloqueia inbound/outbound, encerra conexão passiva e só novo
  token válido refaz subscriptions; token bruto não fica em sessão/log.
- [ ] runtime público rejeita Origin arbitrário/wildcard e aceita somente
  allowlist HTTPS explícita; cliente nativo sem Origin continua exigindo auth.
- [ ] REST preserva `401`, `200`, `204`, `404` opaco e `409`; freshness usa
  30s/90s/5min e depois não entrega coordenadas.
- [ ] app usa `viagemId` tipado em todos os call sites, não consome tópico amplo,
  trata ACK/tombstone/expiração, só reconecta com token fresco e apresenta
  estados acessíveis sem marcador inválido.
- [ ] transições são anunciadas uma vez por Semantics, sem spam por ponto; prova
  manual cobre VoiceOver/TalkBack, text scaling 200%, foco, contraste, 48dp e
  redução de movimento.
- [ ] Maps rejeita caller não autenticado, não recebe nomes e não registra GPS;
  adapter desabilitado cai para fallback seguro.
- [ ] lease/corte lógico/purge de 72h passam inclusive com viagem ainda
  `IN_PROGRESS`; nenhum hold informal impede eliminação.
- [ ] backup lógico contém zero GPS; backup físico/PITR inevitável prova
  criptografia, acesso restrito, lifecycle de sete dias, teto de dez dias,
  restore isolado com purge e destruição da instância temporária de ensaio.
- [ ] backend, app e Maps passam gates oficiais em SHAs limpos.
- [ ] stack, status e preflight provam uma réplica real/`stop-first`; promoção
  com escala horizontal falha até `VR-OPS-004`.
- [ ] smoke ponta a ponta antes/durante/depois/cancelada passa no mesmo manifest.
- [ ] nenhum log, artifact ou evidence contém GPS bruto, token, nome de parada
  ou trajetória real.
- [ ] docs, contratos, backlog, QA Logs, risco e evidência ficam sincronizados.

## Arquivos previstos

Somente coordenação: programa, risco, este card, ADR-005, registro de aceite,
DOR, dashboard/status e evidência do checkpoint. As allowlists de código estão
nos cards derivados.

## Plano sequencial após novo freeze e aceite

1. remover numeração antecipada, revalidar DAG/DoR e congelar nova revisão
   imutável da ADR/DOR/registro de aceite;
2. receber aceite identificado da nova revisão e atualizar ADR/DOR/programa para
   `accepted/READY/ready`;
3. iniciar este checkpoint como único WIP, sem tocar código;
4. confirmar os cinco cards derivados, dependências e allowlists;
5. registrar evidência/commit e concluir este checkpoint;
6. selecionar `VR-SEC-001-LOCATION-DOMAIN-001` como próximo WIP;
7. executar os cards na ordem/dependências registradas;
8. promoção posterior continua separada e requer GO humano.

## Rollback

- invalidar a revisão/aceite e devolver ADR/card a `analyzed` se a decisão tiver
  sido registrada incorretamente;
- nenhum rollback de código/dado existe neste checkpoint;
- depois que um card derivado começar, mudanças de política exigem nova decisão
  e análise de impacto, não edição silenciosa da ADR aceita.

## Evidência de prontidão

- decisão: `program/decisions/ADR-005-trip-scoped-location-access.md` —
  `PROPOSED / INCOMPLETE / REFREEZE_REQUIRED / NOT_ACCEPTED`; a revisão
  histórica `7d12d250...` está `SUPERSEDED / DO_NOT_ACCEPT`;
- registro de aceite: `program/decisions/ADR-005-acceptance.md` —
  `SUPERSEDED / DO_NOT_ACCEPT`;
- freeze: `program/evidence/VR-SEC-001-LOCATION-READ-SCOPE-001-DOR.md` —
  `NOT_READY / REFREEZE_REQUIRED`;
- baseline backend: `6efdb618...`, limpo;
- baseline app: `b53296b...`, dirty preexistente, usar worktree limpa;
- baseline Maps: `a0c2a659...`, limpo;
- implementação/testes: não iniciados;
- revisão independente histórica: invalidada pela política Flyway posterior;
- blocker atual: remover numeração antecipada, revalidar DAG/DoR e produzir
  nova ADR/DOR/registro imutáveis com revisão independente; somente depois
  solicitar aceite identificado de Produto/Privacidade.
