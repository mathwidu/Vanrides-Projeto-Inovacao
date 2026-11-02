# ADR-005 — GPS vinculado à viagem, janela ao vivo e retenção mínima

- status: `PROPOSED / INCOMPLETE / REFREEZE_REQUIRED / NOT_ACCEPTED`
- data: 2026-07-18
- revisão: rascunho reaberto em 2026-07-18 após invalidar a reserva antecipada
  de migrations; novo commit/digest só será congelado depois de revalidar o DoR
- responsáveis: `Produto`, `Privacidade`, `SEC`, `BE`, `APP`, `QA`
- card relacionado: `VR-SEC-001-LOCATION-READ-SCOPE-001`
- execução: `VR-SEC-001-LOCATION-DOMAIN-001`,
  `VR-SEC-003-LOCATION-REALTIME-001`, `VR-MAP-001-LOCATION-S2S-001`,
  `VR-APP-001-LOCATION-ADOPTION-001` e
  `VR-QA-004-LOCATION-INTEGRATION-001`

A revisão histórica `7d12d250f50a1e2a3a8a9a045da603f9c77a339a` foi
marcada `SUPERSEDED / DO_NOT_ACCEPT` porque reservava números Flyway antes de
`ready`. Os
valores abaixo continuam apenas candidatos técnicos; este arquivo precisa de
novo freeze imutável e nova revisão independente antes de voltar a
`waiting_human`.

## Contexto

O contrato atual autoriza passageiro ou responsável quando existe alguma
`ViagemParada` no dia operacional, mas depois devolve a última coordenada
global do motorista. Como `PLANNED`, `IN_PROGRESS` e `COMPLETED` são tratados
como estados legíveis, uma viagem de hoje pode expor amostra anterior ao início,
posterior ao término ou pertencente a outra viagem.

O histórico é ainda mais amplo: a consulta recebe apenas `motoristaId` e retorna
toda a série daquele motorista no tenant. A tabela `localizacao_motorista` não
registra `viagem_id`, `company_id` da captura nem instante com timezone; seus
campos são anuláveis e o relacionamento único é com o motorista.

O realtime não preserva sequer a fronteira parcial do REST:

- `/topic/localizacao/{companyId}` aceita qualquer sessão autenticada do tenant
  e transmite GPS bruto de todos os motoristas;
- `/topic/viagens/{companyId}/{viagemId}` também verifica somente o tenant;
- o app recebe o fluxo amplo e descarta localmente mensagens de outro
  `motoristaId` ou `viagemId`;
- o snapshot de viagem pode carregar localização, próximo passageiro e dados de
  parada para todo assinante do tenant.

A fronteira também alcança Maps Platform. Quando o adapter está habilitado, o
backend monta o pedido de rota com GPS do motorista, coordenadas das paradas e
nomes usados como labels. O endpoint `POST /maps/routes/plan` não autentica o
chamador e a stack DEV configura sua publicação via Traefik; no adapter OSRM,
as coordenadas ainda aparecem no path interno da URL.
`cache-control: no-store` não prova ausência de access logs, tracing ou logs do
engine.

Filtragem no cliente não é autorização. A [orientação oficial sobre os
princípios da LGPD](https://www.gov.br/saude/pt-br/acesso-a-informacao/lgpd/principios)
destaca finalidade, necessidade, segurança, prevenção e prestação de contas; a
[orientação governamental sobre tipos de dados](https://www.gov.br/funasa/pt-br/acesso-a-informacao/lei-geral-de-protecao-de-dados-pessoais-lgpd/tipos-de-dados-abordados-pela-lgpd)
inclui geolocalização entre dados pessoais. A
[OWASP WebSocket Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html)
recomenda autorização por ação/mensagem e alerta que uma conexão autenticada
não concede acesso ilimitado.

Essas fontes orientam minimização e desenho de controle. A base legal, aviso de
privacidade, direitos do titular e eventuais exigências específicas para
crianças/adolescentes ainda precisam de validação pelo responsável jurídico ou
encarregado; esta ADR não substitui essa análise.

## Decisão proposta

Esta decisão depende de novo freeze e aceite explícito de Produto e
Privacidade. Enquanto o freeze estiver inválido, o card permanece `analyzed /
REFREEZE_REQUIRED`; nenhuma resposta humana, código ou dado de produto deve ser
alterado.

### 1. Finalidades permitidas

O GPS bruto terá somente estas finalidades de produto neste ciclo:

- `DRIVER`: publicar sua posição durante a viagem que está operando;
- `PASSENGER`: acompanhar ao vivo a viagem da qual participa;
- `GUARDIAN`: acompanhar ao vivo a mesma viagem em nome de passageiro
  atualmente vinculado;
- `COMPANY_ADMIN`: acompanhar ao vivo viagens ativas do próprio tenant para
  despacho e suporte operacional imediato;
- `MAPS_PLATFORM`: calcular rota efêmera de uma viagem ativa, com identificadores
  opacos e sem persistir nomes, coordenadas ou request body.

Não são finalidades implícitas:

- vigiar motorista fora de uma viagem ativa;
- consultar trajetória histórica geral por motorista;
- oferecer replay pós-viagem a passageiro ou responsável;
- usar coordenadas para marketing, perfil comportamental ou métricas que
  preservem uma rota reidentificável;
- inferir uma viagem histórica para dados legados.

Uma nova finalidade exige decisão, transparência, retenção, autorização,
auditoria e card próprios antes de ampliar acesso.

### 2. Janela temporal autoritativa

A janela compartilhável será o intervalo semiaberto
`[startedAt, endedAt)` da viagem:

- `startedAt` é capturado pelo servidor, em `Instant`, na primeira transição
  efetiva de `PLANNED` para `IN_PROGRESS`, inclusive quando uma atualização de
  parada inicia a viagem automaticamente;
- `endedAt` é capturado uma única vez quando a viagem chega a `COMPLETED` ou
  `CANCELED`;
- `locationSharingExpiresAt` é congelado pelo servidor em `startedAt + 12 horas`
  quando a viagem começa; essa lease é um teto de segurança contínuo, não uma
  estimativa de duração;
- apenas `IN_PROGRESS` com `startedAt` conhecido, `endedAt=null` e instante atual
  anterior a `locationSharingExpiresAt` admite coleta, leitura ou publicação de
  coordenada ao vivo;
- `PLANNED` não abre uma janela antecipada; o motorista inicia a viagem antes
  de sair para a primeira parada;
- `COMPLETED` e `CANCELED` encerram coordenadas imediatamente. A UI pode manter
  um resumo operacional sem GPS bruto;
- remoção do passageiro/manifesto, revogação do responsável ou perda de papel
  interrompe novas entregas sem período de tolerância.

Ao atingir a lease de 12 horas, leitura e realtime fecham de forma opaca e o
writer retorna `409 TRIP_LOCATION_SCOPE_EXPIRED`, sem persistir, publicar ou
chamar Maps. Um watchdog registra somente contagens sanitizadas e alerta a
operação para encerrar/cancelar a viagem órfã; ele não prolonga a lease nem muda
silenciosamente o estado operacional. Não haverá extensão administrativa neste
ciclo. Uma duração legítima superior exige decisão de produto própria antes de
ampliar coleta.

`locationSharingExpiresAt` e `serverTime` integram o contrato operacional da
viagem e são entregues ao app antes de qualquer coordenada. O cliente ancora o
prazo no início da requisição autenticada que recebeu `serverTime`, escolhendo
o deadline conservador que pode remover cedo, nunca depois do relógio do
servidor; sem âncora válida, falha fechado sem marcador. No instante da
expiração, um closer server-side persiste no outbox o controle
`LOCATION_EXPIRED`, sem GPS,
para a audiência autorizada imediatamente anterior. A autorização por relógio
fecha mesmo se o closer ou o broker atrasar. O app agenda o mesmo cutoff com o
relógio corrigido pela resposta do servidor, remove o marcador localmente no
prazo e trata o tombstone posterior como idempotente. Assim, atraso de job,
socket offline ou perda de mensagem nunca cria tolerância adicional.

Não será usada tolerância temporal antes/depois da viagem. Se Produto precisar
de “van se aproximando antes da partida”, a solução recomendada é iniciar a
viagem no início real do deslocamento, não expor o dia inteiro do motorista.

### 3. Coleta e proveniência no servidor

Uma publicação REST ou STOMP só persiste quando o servidor resolve exatamente
uma viagem `IN_PROGRESS` para `companyId + motoristaId`:

- nenhuma viagem: `409 TRIP_LOCATION_SCOPE_UNAVAILABLE`, sem persistência nem
  evento;
- mais de uma viagem: `409 TRIP_LOCATION_SCOPE_AMBIGUOUS`, sem escolher por
  ordem, horário ou ID;
- uma viagem: persistir `companyId`, `viagemId`, `motoristaId`, latitude,
  longitude e `capturedAt` gerado pelo servidor;
- `motoristaId`, empresa, viagem e instante nunca vêm como autoridade do
  payload cliente;
- coordenadas inválidas ou fora de faixa retornam `400`, sem mutação;
- não há coleta “somente para admin” fora da viagem neste ciclo.

Writer e lifecycle usam a mesma ordem de concorrência: bloqueiam primeiro a
linha do motorista no tenant e depois a viagem. Uma unique partial constraint no
PostgreSQL impede mais de uma viagem `IN_PROGRESS` para o mesmo
`company_id + motorista_id`; conflito converge para erro de domínio, nunca para
uma escolha arbitrária. A amostra e seu evento de outbox são gravados na mesma
transação. Broker/Maps só são acionados depois do commit; rollback de banco não
pode deixar mensagem fantasma.

Reatribuição de motorista durante `IN_PROGRESS` é uma transição de segurança,
não simples troca de foreign key:

- a viagem possui `locationScopeVersion`, iniciada em `1`; cada amostra guarda
  a versão e o motorista que a capturou;
- a transação bloqueia os motoristas anterior e novo em ordem crescente de ID e
  depois a viagem, revalida tenant, papel e lifecycle, incrementa a versão e
  troca o motorista;
- o motorista anterior perde o writer no commit; amostras de versões anteriores
  ficam retidas apenas até o purge, mas deixam imediatamente de ser elegíveis
  para latest, realtime ou Maps;
- a leitura da viagem retorna `204` até o novo motorista publicar uma amostra na
  nova versão; nenhuma coordenada antiga é reinterpretada como posição dele;
- a transição persiste `DRIVER_REASSIGNED`, sem coordenadas, para a audiência
  anterior remover o marcador;
- se o novo motorista já opera outra viagem `IN_PROGRESS`, a operação retorna
  `409 TARGET_DRIVER_ACTIVE_TRIP` e reverte integralmente troca, versão, outbox
  e efeitos externos.

O outbox não duplica coordenadas: guarda o tipo do evento, referência opaca à
amostra/viagem, destinatário de controle quando necessário, tentativas e prazo.
O dispatcher relê a amostra trip-scoped somente se a autorização ainda estiver
válida. Evento live expira após cinco minutos; tombstone expira após 24 horas e
ambos são eliminados/compactados depois do sucesso ou do prazo, com métricas sem
IDs pessoais. Não há dead-letter com payload: erro esgotado vira somente
contador por classe. A referência possui `ON DELETE CASCADE`, e o purge remove
outbox expirado antes da amostra para nunca bloquear nem prolongar a retenção.

`LocalizacaoMotorista` passa a usar instante com timezone/UTC. O dia operacional
continua útil para provisionamento, mas não substitui a janela instantânea da
viagem.

### 4. Contrato REST por recurso

O recurso público deixa de ser a localização global do motorista e passa a ser
a localização da viagem:

```text
GET /viagens/{viagemId}/localizacao
```

Contrato proposto:

- `200`: `{viagemId, motoristaId, locationScopeVersion,
  locationSharingExpiresAt, serverTime, latitude, longitude, capturedAt,
  freshness}`;
- `204`: viagem ativa e autorizada, mas sem amostra trip-scoped;
- `401`: autenticação ausente ou inválida;
- `404` com o mesmo corpo canônico: viagem inexistente, outro tenant, papel sem
  relação, passageiro fora do manifesto, responsável sem vínculo, viagem fora
  da janela ou amostra de outra viagem;
- nunca retornar coordenada de linha com `viagem_id IS NULL`;
- `PASSENGER` e `GUARDIAN` não recebem coleção de coordenadas nem endpoint de
  replay neste ciclo.

`freshness` é calculado pelo servidor sobre `capturedAt` em UTC, preservando as
faixas atuais: `LIVE` até 30 segundos, `DELAYED` até 90 segundos e `STALE` até
cinco minutos. Amostra mais antiga, futura ou inválida é `UNAVAILABLE` e não
devolve coordenadas (`204` para recurso ativo e autorizado). O app faz a
transição temporal mesmo sem nova mensagem e nunca mantém marcador
`UNAVAILABLE` com aparência de posição ao vivo.

O DTO operacional da viagem e todo snapshot realtime autorizado carregam
`locationSharingExpiresAt`, `locationScopeVersion` e `serverTime`, inclusive
quando latest é `204`. Se esses campos ou a âncora temporal estiverem ausentes
ou inválidos, o cliente falha fechado sem marcador. O cutoff local é o menor
instante entre a expiração conservadora da lease e a expiração da freshness; ele
não aguarda nova resposta ou evento.

`DRIVER` e `COMPANY_ADMIN` também usam a viagem como objeto. O endpoint
`GET /api/localizacao/{motoristaId}/historico` deixa de ser um contrato geral;
nenhuma UI normal recebe histórico bruto. A rota antiga de latest poderá
existir apenas durante a compatibilidade e deve resolver no servidor uma única
viagem ativa autorizada. Nunca poderá voltar à última linha global.

### 5. Autorização realtime no servidor

Os tópicos amplos não serão tratados como fronteira de segurança:

- parar de publicar e rejeitar novas assinaturas em
  `/topic/localizacao/{companyId}`;
- deixar de entregar snapshot de viagem com GPS por um tópico que qualquer
  membro do tenant possa assinar;
- entregar eventos por destino de usuário, por exemplo
  `/user/queue/viagens/{viagemId}`, após política por objeto no servidor;
- recalcular destinatários em cada publicação, de modo que revogação de vínculo
  afete a próxima mensagem mesmo se a conexão continuar aberta;
- `DRIVER` recebe somente a própria viagem ativa;
- `PASSENGER` recebe somente viagem ativa cujo manifesto contém seu ID;
- `GUARDIAN` recebe somente a projeção do passageiro vinculado naquela viagem;
- `COMPANY_ADMIN` recebe somente viagem ativa do próprio tenant;
- projeções de passageiro/responsável não incluem nome/ID de outro passageiro,
  manifesto completo ou campos administrativos;
- evento terminal pode informar `COMPLETED`/`CANCELED`, mas deve remover
  coordenadas e encerrar novas entregas.

O protocolo STOMP é allowlist/deny-by-default:

- cliente nunca assina `/queue/**` diretamente e nenhum `/topic/**` transporta
  GPS;
- `SUBSCRIBE` só aceita os destinos `/user/queue/viagens/{viagemId}` e
  `/user/queue/location-publish-acks`, após autenticação e política do objeto;
- `SEND` só aceita o destino de publicação de localização conhecido; destino,
  comando ou formato desconhecido é rejeitado;
- cada publicação leva `correlationId` não autoritativo. REST usa HTTP `409`; no
  STOMP, o servidor responde em `/user/queue/location-publish-acks` com ACK
  tipado `{correlationId, accepted, status, errorCode}` e os mesmos códigos
  `UNAVAILABLE`, `AMBIGUOUS` ou `EXPIRED`;
- falha de auth/formato não encaminha o body à policy, persistência, outbox ou
  Maps.

A autenticação também possui limite temporal de sessão:

- `CONNECT` valida assinatura/claims e guarda apenas `sessionId`, identidade e
  `exp` — nunca o token bruto;
- todo `SEND` e `SUBSCRIBE` revalida que `now < exp`; um interceptor outbound
  descarta qualquer mensagem para sessão expirada;
- um registro/sweeper server-side encerra a conexão no instante `exp`, mesmo que
  o cliente fique passivo e só receba eventos;
- o app nunca reconecta com o mesmo token expirado. Ele pede token fresco a um
  provider serializado e só então reconecta/refaz subscriptions; se não houver
  credencial nova, remove o marcador e mostra sessão expirada/ação de entrar;
- até a rotação atômica do sucessor `VR-SES-001-REFRESH-ROTATION-001`, não há
  retry automático concorrente de refresh; falha fecha o realtime.

O handshake WebSocket reutiliza a allowlist configurável de CORS. Ambiente
público falha no startup se a lista estiver vazia, contiver `*`, `null`, esquema
não HTTPS ou pattern amplo. Origens explícitas são comparadas antes do upgrade;
cliente nativo sem header `Origin` ainda depende do `CONNECT` autenticado, mas
um `Origin` presente e não permitido é rejeitado.

Para término, revogação, reatribuição e expiração, a transação/closer captura a
audiência autorizada imediatamente anterior, aplica a mudança e persiste um
tombstone de outbox sem coordenadas: `TRIP_TERMINATED`, `LOCATION_REVOKED`,
`DRIVER_REASSIGNED` ou `LOCATION_EXPIRED`. O dispatcher entrega esse controle
ao conjunto anterior somente depois do commit, com ID idempotente e retry
limitado. Eventos normais continuam usando a audiência atual recalculada.
Assim, remover um vínculo, trocar o motorista ou vencer a lease não mantém
acesso a GPS, mas ainda permite apagar o marcador. O cliente também aplica
freshness e lease localmente caso o tombstone não chegue.

O broker simples e o user registry atuais são locais à JVM. Até
`VR-OPS-004 — Escala horizontal e isolamento de estado` entregar broker/user
registry compartilhados, stacks e preflight fixam o backend em uma réplica com
update `stop-first`; escalar horizontalmente é `NO_GO`. O dispatcher pode usar
claim transacional/idempotente, mas isso não torna sessões de outra JVM
endereçáveis.

Autorização apenas no `SUBSCRIBE` é insuficiente para revogação durante uma
conexão longa. Filtrar depois do recebimento no Flutter permanece defesa de
robustez, nunca controle de acesso.

### 6. Política por papel

| Papel | Publica | Latest ao vivo | Histórico bruto | Depois do encerramento |
|---|---|---|---|---|
| `DRIVER` | própria viagem ativa | própria viagem ativa | não pela API geral | resumo sem coordenadas |
| `PASSENGER` | não | viagem ativa contendo o próprio passageiro | não | resumo sem coordenadas |
| `GUARDIAN` | não | viagem ativa contendo passageiro ainda vinculado | não | resumo sem coordenadas |
| `COMPANY_ADMIN` | não | viagens ativas do tenant | não pela UI normal | resumo sem coordenadas |

Esta ADR não decide investigação, conservação, hold nem release. Essas
autoridades pertencem a `INCIDENT-HOLD-001` e ao card
`VR-PRIV-003-INCIDENT-HOLD-DECISION-001`, com Privacidade + Jurídico/DPO +
SEC/OPS e vínculo imutável. Pedido informal, ticket ou flag manual nunca adiam
o purge; uma decisão de incidente formal e aceita pode suspendê-lo pelo escopo
e prazo aprovados. Histórico geral de produto continua proibido nos dois casos.

### 7. Retenção e eliminação

A proposta padrão é reter GPS bruto até `72 horas` após o encerramento da
autorização de localização,
alinhada ao plano de privacidade de telemetria já registrado no workspace:

- `locationClosedAt` é o menor instante entre `endedAt`, quando existente, e
  `locationSharingExpiresAt`; ele ancora o corte mesmo se a viagem ficar
  eternamente `IN_PROGRESS`;
- enquanto a lease está válida, as amostras da viagem não são eliminadas;
- o acesso de produto termina em `locationClosedAt`; retenção física não
  prolonga a autorização de leitura;
- um job idempotente remove em lotes pequenos dados expirados e registra apenas
  contagens, duração e resultado, sem coordenadas, IDs pessoais ou trajetórias;
- consultas aplicam o corte lógico mesmo se o job estiver atrasado;
- restore de backup deve reaplicar a eliminação antes de liberar o ambiente;
- exports, logs, analytics e evidências não podem criar cópia paralela da rota;
- atraso operacional, ticket ou investigação informal não suspendem o purge;
  hold formal só existe se a decisão `INCIDENT-HOLD-001` aceita definir
  autoridade, escopo, prazo, auditoria e release. Sem essa decisão, este card
  não pode implementar nem promover o purge.

A política distingue dado ativo de cópia de recuperação:

- backups lógicos rotineiros excluem os dados das tabelas de GPS, outbox que
  referencie amostra e qualquer staging correspondente; schema e metadados sem
  localização/referência pendente podem permanecer;
- se o ambiente usar backup físico/PITR que não permita exclusão por tabela, a
  cópia é criptografada, inventariada e acessível apenas ao papel de recuperação,
  com lifecycle automático máximo de sete dias desde a criação. Como uma linha
  pode entrar na cópia pouco antes do purge de 72 horas, o limite físico absoluto
  é `locationClosedAt + 10 dias`; para legado, o teto equivalente é
  `legacyClassifiedAt + 10 dias`;
- snapshot físico discreto falha antes da cópia se o preflight encontrar linha
  logicamente expirada ainda não purgada; a janela de WAL/PITR também não pode
  exceder sete dias;
- nenhuma cópia de recuperação pode servir API, analytics, suporte ou teste
  funcional; somente ensaio controlado de restore em ambiente isolado é aceito;
- restore ocorre isolado, sem tráfego de aplicação, e executa o corte/purge antes
  de abrir rede ou credenciais. A instância temporária é destruída em até 24
  horas após o ensaio. Em incidente real, o ambiente só pode substituir o
  anterior depois do purge e dos gates de recuperação; não é uma cópia de teste;
- `VR-OPS-002` e o manifest de promoção devem provar modo de backup, exclusão ou
  lifecycle, criptografia, acesso, restore/purge e destruição. Sem essa prova,
  piloto e produção permanecem `HOLD` para GPS.

Linhas `UNSCOPED_LEGACY` usam um cutoff próprio porque não possuem viagem: a
migration alocada no freeze de `ready` grava `legacyClassifiedAt` e
`purgeAfter = legacyClassifiedAt + 72h`, sem
alterar ou inferir a captura original. Elas ficam invisíveis imediatamente e o
mesmo job as elimina após essa janela de rollback. Nenhum timestamp legado é
tratado como verdade de viagem.

O prazo de 72 horas é uma recomendação de minimização, não conclusão jurídica.
Produto/Privacidade podem escolher outro prazo normal, mas devem registrar
finalidade, necessidade, acesso, backups e impacto do prazo substituto antes de
aceitar a ADR. Aceitar a recomendação atual significa aceitar 72 horas no banco
ativo, exclusão de backups lógicos e, somente quando PITR físico for inevitável,
o teto inacessível de dez dias descrito acima. Esse aceite não autoriza nem
nega hold: `INCIDENT-HOLD-001` é uma decisão separada, com autoridades próprias,
e precisa estar concluída antes de implementar o purge.

### 8. Dados legados e migration alocada no freeze

A migration trip-scoped, cujo número não está reservado enquanto o card estiver
`analyzed`, será aditiva:

- adiciona `company_id`, `viagem_id` e captura temporal/proveniência necessárias;
- adiciona `location_scope_version` na viagem e na amostra para que
  reatribuição nunca reutilize latest da versão anterior;
- adiciona os instantes reais `started_at`, `ended_at` e
  `location_sharing_expires_at` necessários ao fechamento auditável da janela;
- cria índices para latest por `company + viagem + captured_at` e purge por
  expiração;
- cria, ainda inerte, o schema completo do outbox exigido pelo slice realtime:
  ID idempotente único, tipo, referências opacas à viagem/amostra, destinatário
  de controle opcional para tombstone, tentativas/claim, disponibilidade,
  expiração e timestamps; índices de dispatch/expiração, FKs com cascade e
  checks impedem payload, coordenadas ou prazo inválido no outbox;
- não tenta inferir `viagem_id` de timestamp, motorista, rota ou agenda;
- linhas legadas permanecem `UNSCOPED_LEGACY`, invisíveis a todos os contratos
  de produto, com `legacy_classified_at` e `purge_after` fixados pela migration
  para eliminação 72 horas depois;
- novos writers sempre persistem escopo completo;
- constraints finais que dependam de limpar legado podem entrar em migration
  posterior, sem quebrar rollback para binário anterior.

Nenhum preflight remoto, job de purge ou migration será executado durante a
implementação local. Antes de promover a migration alocada em `ready`, a
autorização separada de promoção
deve receber contagens agregadas por categoria e confirmar o cutoff; nenhum
backfill será feito.

### 9. Maps Platform como processador interno

Maps Platform participa da mesma fronteira de dados:

- `POST /maps/routes/plan` exige autenticação serviço-a-serviço e limite de
  chamadas; não fica disponível por router público sem essa proteção;
- backend envia IDs opacos, nunca nome do motorista/passageiro, e somente os
  pontos necessários à viagem ativa;
- serviço, Traefik, tracing, Valhalla e OSRM não registram body, path com
  coordenadas, headers de autenticação nem resposta de geometria;
- pedido e resposta são efêmeros, sem cache compartilhado ou persistência;
- acesso ao engine ocorre por rede interna; health público não implica route
  planner público;
- testes usam apenas coordenadas sintéticas e provam redaction de logs;
- se esse hardening não estiver presente na versão integrada, o backend mantém
  o adapter desabilitado e usa fallback local seguro. Não existe waiver para
  enviar dados reais a uma rota pública sem autenticação.

O segredo serviço-a-serviço é lido de secret file, comparado em tempo constante
e rotacionado por dois slots (`current`/`next`) sem registrar valor. Auth, limite
de conexão/body/chamadas e deadline acontecem antes de ler/parsing do body. A
rota de health pode permanecer pública, mas o planner só existe na overlay
compartilhada e criptografada com o backend. A janela `current/next` de rotação
dura no máximo 24 horas: primeiro Maps aceita ambos, depois o backend troca, e o
segredo anterior é removido; readiness falha se os dois slots permanecerem além
do prazo. Enquanto a configuração versionada de um engine não
provar ausência de access log contendo coordenadas, o provider real — em especial
OSRM, cuja URL contém pontos — permanece desabilitado para payload de produto;
somente fixtures sintéticas/fallback podem atravessar os testes.

### 10. Compatibilidade e rollout

A mudança será coordenada, mas segura para versões antigas:

1. o slice DOMAIN cria schema, fecha o histórico geral, resolve latest legado
   somente com uma única viagem ativa autorizada e ativa por padrão o kill
   switch fail-closed dos tópicos STOMP legados;
2. apps antigos podem perder realtime e cair para estado degradado, mas não
   recebem dados amplos; o slice REALTIME só substitui o kill switch quando os
   destinos/projeções por usuário estiverem completos e os tópicos antigos
   continuarem permanentemente negados;
3. Maps Platform entra por endpoint interno autenticado e payload minimizado,
   ou permanece desabilitada;
4. o novo app passa `viagemId`, usa destino por usuário e trata explicitamente
   `204`, viagem encerrada, acesso revogado e realtime indisponível;
5. depois da adoção mínima definida, as rotas antigas são removidas ou ficam
   permanentemente deny-by-default;
6. promoção exige manifest com SHAs backend, app e Maps, migrations e imagens
   executados juntos.

Não haverá dual-publish inseguro apenas para manter o app antigo aparentemente
funcional. Compatibilidade que reabre exposição não é compatibilidade válida.

### 11. Acessibilidade e estados do app

Quando a janela não estiver aberta ou for encerrada, o app deve remover o
marcador e anunciar um estado explícito, como “viagem ainda não iniciada”,
“localização temporariamente indisponível” ou “viagem encerrada”:

- não conservar coordenada antiga com aparência de posição ao vivo;
- não depender apenas de cor, animação ou movimento do mapa;
- anunciar em `Semantics`/live region somente mudanças significativas de estado
  (`live`, atraso, indisponível, sessão expirada, encerrada/revogada), nunca cada
  amostra de GPS; anúncios são deduplicados para não inundar o leitor de tela;
- manter foco previsível na mensagem/ação recuperável, leitura por VoiceOver e
  TalkBack, Dynamic Type/text scaling até 200% e alvos de toque de pelo menos
  48dp;
- respeitar redução de movimento e oferecer conteúdo textual equivalente;
- contraste, ícones e rótulos atendem WCAG 2.2 AA como alvo de produto; a prova
  automatizada é apenas apoio e não substitui passagem manual assistiva;
- responsável deixa de montar uma timeline a partir do histórico global e usa
  eventos operacionais autorizados da viagem.

### 12. Prova obrigatória

A implementação só poderá ser concluída com:

- testes de unidade, WebMvc, STOMP e PostgreSQL para matriz por papel, viagem,
  tenant, janela e legado;
- concorrência de zero/uma/duas viagens ativas sem escolha ambígua;
- viagem órfã ainda `IN_PROGRESS` após 12 horas bloqueando writer/leitura/evento
  e iniciando o prazo de purge sem depender de `endedAt`;
- closer de lease gera `LOCATION_EXPIRED`; app remove o marcador exatamente no
  cutoff mesmo com closer/broker atrasado ou offline;
- skew de relógio e latência provam que a âncora `serverTime` pode antecipar a
  remoção, mas nunca a posterga além do cutoff do servidor;
- reatribuição ativa incrementa `locationScopeVersion`, revoga writer anterior,
  retorna `204` até nova amostra e falha atomicamente quando o alvo já opera
  outra viagem;
- prova de que viagem A nunca recebe latest/evento da viagem B do mesmo
  motorista;
- remoção/revogação/cancelamento impedindo a próxima entrega;
- teste de projeção que impede passageiro/responsável de ver outro passageiro;
- teste do job/corte lógico de retenção sem registrar coordenadas;
- teste de backup lógico sem GPS e prova de que backup físico/PITR, quando
  configurado, respeita criptografia, acesso, lifecycle de sete dias, purge
  pré-abertura e teto físico de dez dias;
- teste Maps de autenticação, rede interna, payload opaco e logs sanitizados;
- rotação `current/next`, token ausente/inválido, body acima do limite e deadline
  rejeitados antes do parse/engine;
- corrida writer versus complete/cancel/reassign e duas partidas concorrentes,
  com lock/constraint, outbox pós-commit e ausência de evento fantasma;
- tombstones de término/revogação/reatribuição/expiração entregues à audiência
  anterior sem GPS e com idempotência/retry;
- token expirando antes/no/depois de `exp` bloqueia inbound/outbound, fecha a
  sessão passiva e só reconecta com token diferente ainda válido;
- origem ausente nativa, origem HTTPS permitida e origem arbitrária/wildcard em
  runtime público cobertas sem bypass de auth;
- stack, startup/preflight e smoke comprovam uma réplica `stop-first`; tentativa
  de promoção horizontal falha até `VR-OPS-004`;
- testes do app para rotas/eventos novos e estados acessíveis;
- smoke ponta a ponta antes/durante/depois/cancelada no mesmo conjunto de SHAs;
- gate canônico de backend e app em worktrees limpas;
- manifest de migrations, JAR/app, ambiente, hashes e cleanup.

Gate local não autoriza piloto ou produção.

## Alternativas consideradas

### Manter a viagem “legível” durante todo o dia operacional

Rejeitada. Relacionar uma pessoa ao motorista naquele dia não prova que a
última coordenada pertence à viagem nem que a finalidade continua ativa.

### Manter tópico por empresa e filtrar no Flutter

Rejeitada. O dispositivo já recebeu os dados; um cliente modificado ignora o
filtro. Também não resolve revogação nem minimiza payload.

### Autorizar tópico por viagem somente no `SUBSCRIBE`

Melhora enumeração, mas não basta. Um responsável revogado durante conexão
continua inscrito e todos os assinantes recebem a mesma projeção. A entrega por
usuário e a política por publicação são recomendadas.

### Oferecer histórico trip-scoped a responsável e admin

Possível somente com finalidade e prazo explicitamente aprovados. Não é
necessário para o mapa ao vivo atual, aumenta exposição de endereços/rotinas e
fica fora da proposta padrão.

### Backfill de `viagem_id` por horário e motorista

Rejeitado. Viagens podem sobrepor, horários podem mudar e timestamps antigos
não têm timezone confiável. Uma inferência produziria falsa proveniência.

### Retenção geral ou hold informal de 7, 30 ou 90 dias

Não recomendada sem requisito operacional demonstrável. Aumenta impacto de
incidente, cópias em backup e obrigação de controle sem melhorar o fluxo ao
vivo. Hold formal não é uma alternativa decidida aqui: depende de
`INCIDENT-HOLD-001`, com fluxo implementado e aprovado no card próprio. Sem
essa prova, não se pode atrasar o purge.

### Manter Maps Platform pública porque o body usa HTTPS

Rejeitada. TLS protege o transporte, mas não autentica o backend como único
chamador, não limita abuso e não impede cópias em logs/proxies. O route planner
deve ser interno e minimizar os labels.

## Rollback seguro

Rollback não pode restaurar última posição global nem tópico bruto por empresa.

- migrations aditivas e dados já trip-scoped permanecem;
- o app novo tolera indisponibilidade e volta a polling seguro;
- o adapter Maps pode ser desligado sem bloquear a viagem; fallback não recebe
  ou persiste GPS fora do processo autorizado;
- se o backend novo falhar, o modo seguro desabilita leitura/publicação de GPS
  para passageiro/responsável e mostra estado indisponível;
- reimplantar binário antigo só é permitido com kill switch que bloqueie as
  rotas/tópicos inseguros;
- correção preferencial é forward-fix na mesma fronteira de segurança.

## Consequências

- o motorista precisa iniciar a viagem para compartilhar localização e a lease
  contínua termina após 12 horas, mesmo se a viagem ficar órfã;
- passageiro e responsável podem perder realtime em app antigo durante a
  transição, mas não recebem dados excessivos;
- a autorização passa a ser por objeto/viagem e avaliada no servidor;
- o app deixa de depender de histórico global para construir a experiência;
- dados legados não ganham proveniência fabricada;
- a empresa mantém visibilidade operacional ao vivo, mas não navegação livre
  por trajetórias históricas;
- Maps Platform deixa de receber nomes e não pode processar rota real por uma
  entrada pública não autenticada;
- R-001 e R-007 só podem ser reduzidos após prova cross-repo e retenção ativa.

## Aceite humano necessário

Produto e Privacidade precisam aceitar ou substituir explicitamente cada item:

1. finalidades e papéis limitados ao acompanhamento operacional ao vivo;
2. coleta e exposição somente em `IN_PROGRESS`, na janela
   `[startedAt, endedAt)`, sem tolerância prévia ou posterior;
3. lease contínua de 12 horas, fail-closed, com
   `locationSharingExpiresAt`, `serverTime`, tombstone `LOCATION_EXPIRED` e
   cutoff local conservador no app mesmo sem mensagem, para impedir viagem órfã
   coletável ou visível;
4. writer/lifecycle são serializados por lock do motorista e constraint de uma
   viagem ativa; o writer exige exatamente uma viagem própria autorizada e
   retorna `TRIP_LOCATION_SCOPE_UNAVAILABLE`, `AMBIGUOUS` ou `EXPIRED` sem
   efeito; reatribuição incrementa `locationScopeVersion`, invalida a amostra do
   motorista anterior, entrega `DRIVER_REASSIGNED`, retorna `204` até nova
   amostra e falha atomicamente se o novo motorista já estiver em viagem;
5. REST por `viagemId`, com `200/204/401/404` opaco, faixas de freshness
   30s/90s/5min e nenhuma coordenada depois de `UNAVAILABLE`;
6. ausência de histórico bruto para passageiro/responsável;
7. driver próprio e admin limitados ao ao vivo, com investigação/hold/release
   em fluxo separado e sem endpoint geral de histórico;
8. retenção normal de 72 horas após `locationClosedAt`; pedido informal, ticket
   ou flag não suspendem o purge, e eventual hold formal depende da decisão
   separada `INCIDENT-HOLD-001`, sem ser aprovado ou negado por este item;
   backups lógicos excluem GPS e backup físico/PITR inevitável é inacessível,
   criptografado, dura no máximo sete dias da criação e nunca ultrapassa
   `locationClosedAt + 10 dias` — ou `legacyClassifiedAt + 10 dias` para legado
   —, com purge antes de restore servir tráfego;
9. legado sem backfill/exposição, classificado pela migration alocada no freeze
   de `ready` e eliminado 72 horas após a classificação;
10. desativação do tópico bruto por empresa;
11. STOMP deny-by-default com ACK tipado, projeção por usuário/papel,
    autorização recalculada, outbox/tombstones pós-commit para término,
    revogação, reatribuição e expiração, expiração do JWT, origem pública
    allowlisted e uma réplica até broker compartilhado;
12. Maps interno, autenticado com segredo rotacionável, labels opacos e logs
    sanitizados;
13. estados acessíveis removem marcador quando não há posição utilizável;
14. degradação segura para app antigo;
15. rollback fail-closed, sem restaurar contrato inseguro.

O aceite precisa ser registrável, não apenas inferido de uma frase. Antes da
resposta, o coordenador informa o commit e o SHA-256 desta revisão. Produto e
Privacidade registram somente a política normal desta ADR; não podem aceitar
hold ou release em nome das autoridades do incidente. Devem registrar:

- nome do aprovador de cada papel e sua autoridade; uma mesma pessoa pode
  responder pelos dois somente se declarar explicitamente acumular ambos;
- a frase `Aceito a recomendação completa da ADR-005 na revisão <commit>,
  SHA-256 <digest>` ou alterações por item com regra substituta;
- ciência de que base legal, aviso, direitos do titular e tratamento de menores
  ainda exigem validação jurídica/DPO e que o aceite não concede `GO` a piloto
  ou produção.

O coordenador registra frase, aprovadores/papéis, timestamp e revisão imutável em
`program/decisions/ADR-005-acceptance.md`. Qualquer alteração posterior da ADR
exige novo aceite; somente então o card pode ir para `ready`.
