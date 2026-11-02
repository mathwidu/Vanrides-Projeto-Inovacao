# Transporte WhatsApp050

Perfil: Evolution2.3.7/Baileys7.0.0-rc.9, HTTP POST com credencial CHANNEL da
instância. Fontes fixadas e fixtures em
[contrato Evolution](../contracts/whatsapp-mvp/evolution-2.3.7/README.md).
Versão e fonte não comprovam sessão real; n8n/conversa completa pertencem a070,
recuperação operacional a120 e ensaio integrado a160.

## Recepção e identidade

`WaTransportController` usa a mesma allowlist/filtros e erros fechados da
fundação. O guard limita32KiB, não aceita Origin/OPTIONS e separa principals.
Eventos persistem dentro da transação antes de202. Falha de DB responde503
sanitizado. Não persistir envelope, apikey, sender do bot, mídia ou quoted GPS.

`WaEvolutionNormalizer` separa texto/pin estático/unsupported, callback de
status, conexão e eco. Static pin vem somente de locationMessage não live,
com coordenadas finitas nos limites. `messages.edited`, fromMe, grupos e
broadcast não criam comando humano. Conteúdo humano não suportado fica como
referência pendente para orientação do fluxo070, sem conteúdo bruto.

`WaEventService` bloqueia credencial, chave de dedup, aliases ordenados e
identidade antes da nova inbox. Replay não resolve novamente aliases nem
substitui payload. PN/LID de tipos diferentes só são associados quando o
provider fornece os dois explicitamente; duas identidades ou mudança ambígua
do mesmo tipo geram recibo ignorado, sem merge. Cohort-only desconhecido não
fica como backlog reativável. Sem a restrição de coorte, identity pode existir
antes do perfil; não se cria empresa ou conta legada.

`senderAliasId` é uma referência interna ao alias principal daquele evento.
Ela permite responder exatamente ao remetente que originou o comando, sem
preferir PN antigo nem transformar LID em telefone. Não sai na projeção para
o trabalhador de conversa. Códigos exatos de bootstrap/pareamento também não
saem: ficam somente como hashes.070 deve consumi-los por caminho determinístico
no backend, sem pedir ao LLM que reconstrua o código ou escolha outra identidade.

## Inbox

V27 adiciona `received_order`, sequência gerada pelo PostgreSQL. O ingresso
serializa o insert por identidade; o claim respeita o primeiro evento não
terminal nessa ordem. Não depende de timestamp/UUID para desempate. RETRY com
not_before futuro e PAUSED bloqueiam sucessores da mesma conversa, sem
ultrapassar um head bloqueado por outro worker.

Lease usa hash do token, geração e principal. Expiração é conferida após
esperas e antes de efeitos. Claim após expiração troca a geração/token; ACK
antigo não autoriza efeito. Resultado já comprometido pode ser repetido pelo
mesmo dono/corpo sem novo efeito; alteração do corpo gera conflito.
Recibo de comando existente conclui a inbox mesmo se o ACK anterior se perdeu;
não chama o modelo novamente. `conversation_state` não cria lease concorrente.

O trabalhador recebe apenas referência opaca, texto limitado e referência de
reply da própria identidade/instância. Pin é STATIC_PIN_REFERENCE, sem GPS.
Não recebe alias, credencial do canal, código, nome de outra pessoa ou dados
de outra conversa.

## Outbox

`WaFoundationResponses` salva resposta fixa junto com command_receipt; replay
preserva os mesmos responseIds.050 libera somente RESPONSE/TEXT e política
foundation-v1. Rejeições controladas de comando não ganham envio automático;
070 completa as mensagens da conversa. Propósitos futuros não são liberados
por existir um schema.

Claim não revela texto/destinatário. Autorizar confere dono/token/deadline,
coorte/perfil e alias exato, reserva SUBMITTING e só então devolve payload.
Submissão é serial por instância; submitBefore é o menor entre10s, TTL,
lease e validade da credencial. Prazo expirado durante espera faz rollback.
Queda/timeout após reserva vira UNKNOWN, sem retry. Result pode chegar depois
do prazo para registrar o fato ocorrido, sem autorizar outra submissão.

Callbacks são duráveis e reconciliados em outbox result/claim por
instância+ID externo. Um callback anterior ao resultado aguarda esse vínculo;
não há associação por texto/horário. Precedência: SUBMITTED < FAILED < DELIVERED
< READ. ACK atrasado não apaga erro; prova de entrega/leitura supera erro
anterior. HTTP201 do provider significa aceitação, não DELIVERED.

O adapter Node no coordenador envia number/text intactos para sendText, sem
retry/redirect, com resposta limitada a32KiB e deadline. Configuração inválida
não produz HTTP; resposta ambígua não vira sucesso. Sem logs de payload/token.
Flags desligadas bloqueiam novos efeitos, preservando recibos de canal/result.

## Operação e limites

### Admissão de entrega — primeira fatia120

V34 acrescenta `wa.delivery_connection`, sem presumir OPEN para instâncias já
existentes. Somente CONNECTION aceito pela credencial CHANNEL do ambiente e
instância correspondentes alimenta a projeção. `open` admite; `connecting`,
`close` e `refused` suspendem. Não há rota para escrever estado normalizado.
Evento antigo não substitui o mais novo; em empate, suspensão prevalece em
qualquer ordem. Eventos até30s no futuro ordenam por agora da API; acima disso,
INVALID_PAYLOAD desfaz recibo e projeção. A ordenação é defensiva e não prova
conectividade no instante do POST; o probe atual do worker continua necessário.

A projeção vincula ambiente/instância/CHANNEL por FK composta. Claim e
authorize exigem fonte presente, não revogada e não expirada, com locks e
rechecagem de tempo após esperas. Revogação/expiry bloqueiam sem exigir CLOSE;
remoção da credencial ou recibo fonte apaga a projeção. Uma nova credencial
precisa publicar OPEN mais recente; replay do evento anterior não a promove.
O `submitBefore` também não ultrapassa a validade do CHANNEL fonte.

CONNECTION e reserva usam o mesmo lock de entrega por ambiente/instância.
Conexão desconhecida/suspensa devolve `items:[]` em claim. Para lease válido,
authorize retorna o código existente503 DEPENDENCY_UNAVAILABLE sem conteúdo
ou tentativa. Claim continua conciliando resultados e expira até100 itens
vencidos por chamada antes de selecionar; todo item vencido fica excluído da
seleção, mesmo acima desse lote. Reconectar não renova TTL. Result e callbacks
históricos continuam aceitos com a credencial do chamador ainda válida.

O namespace legado da inbox/dedup é por instância, sem ambiente. Usar uma
instância exclusiva por ambiente evita colisão conservadora de recibos;
esta fatia não modifica índices antigos nem reinterpreta eventos históricos.
Fixtures de entrega publicam OPEN pelo ingresso autenticado real, sem bypass.
Retry/NOT_SUBMITTED, backoff, prioridades novas e a prova de canal real ainda
pertencem às próximas fatias120. Payloads HTTP e schemaVersion1.4.0 preservados;
OpenAPI1.6.0 documenta a nova condição. A matriz preparada é61 classes/462
casos PostgreSQL; execução remota e smoke do mesmo JAR continuam pendentes.

Rollback desta fatia: desligar novos efeitos, preservar recibos e V34 aplicada,
e reverter o código próprio. A versão anterior não tem o gate de conexão;
não reabilitar despachos nesse rollback sem decisão explícita. Não usar um
JAR anterior como prova do gate120.

### Retentativa comprovadamente sem POST — segunda fatia120

V35 acrescenta somente o fato `NOT_SUBMITTED/CHANNEL_UNAVAILABLE` à tentativa.
O worker autenticado só pode declará-lo se não invocou POST; o backend não
consegue observar essa fronteira de rede por conta própria. Os quatro corpos
de result estão fechados em variantes no OpenAPI1.7.0/schemaVersion1.5.0.
UNKNOWN/FAILED continuam sem retry, incluindo UNKNOWN já abandonado antes de
um NOT_SUBMITTED tardio. Não foi implementada reconciliação operacional nova.

Com TTL e negócio ainda válidos, result guarda o recibo imutável, invalida a
lease e adia PENDING5s após a primeira tentativa e15s após a segunda. São no
máximo3 tentativas totais;45s não cria uma quarta. Conexão fechada preserva a
intenção PENDING, mas claim/authorize exigem OPEN vigente. Cada repetição usa
nova geração/token/autorizações; nenhuma condição ou TTL é renovado. Sem
janela para o próximo atraso, expira; inelegível cancela; limite esgotado falha.

Cipher de convite/link inicial permanece somente no retry seguro pendente,
sem gerar outro segredo. Expiração/cancelamento/UNKNOWN/FAILED/submissão
purgam os ciphers. Expiração e cancelamento de convite consideram o histórico
NOT_SUBMITTED; result/callback nunca reabre EXPIRED/CANCELED. Replay antigo
retorna exatamente seu JSON salvo e não toca a lease ou tentativa seguinte.
Matriz preparada64 classes/485 PostgreSQL; os23 cenários novos cobrem limites,
backoff/restart, concorrência, escopo, TTL e preservação/purga de segredos.
Compilação, PostgreSQL e smoke do mesmo JAR aguardam a CI/operador.

Rollback: manter V35 e as tentativas/recibos, suspender novos efeitos e usar
versões produtor/consumidor compatíveis. Não voltar ao binário anterior com
despachos ligados: ele não conhece o resultado novo nem sua política de retry.

### Expiração e visibilidade de UNKNOWN — terceira fatia120

HEALTH_RECONCILE usa SYSTEM_JOB na rota existente, sem depender de OPEN, claim
ou flag de novos efeitos. Reutiliza a janela UTC/cursor duráveis dos jobs, até
100 outboxes por página e o mesmo delivery lock. Revalida a credencial após
esperas/efeitos. Também expira pendências sem POST, incluindo NOT_SUBMITTED.
O executor consumidor limita a invocação a2 páginas/18s; isso não transforma
timeout HTTP em cancelamento comprovado da transação no banco.

No TTL, UNKNOWN vira EXPIRED na outbox, purga os ciphers e mantém o significado
da tentativa. Cria um único support_case purpose=DELIVERY por outbox, reason
DELIVERY_UNKNOWN_EXPIRED, dedup `delivery-unknown:<outboxId>` e correlação UUID
da outbox. Não pausa conversa nem reutiliza suporte humano. Caso permanece
OPEN para acompanhamento; leitura privada está na CLI, sem nova API admin.

Primeiro result tardio de reserva abandonada pode registrar ID externo/fato;
callbacks correlacionados avançam a evidência da tentativa monotonamente,
inclusive com outbox terminal. EXPIRED/CANCELED nunca reabrem. Replay UNKNOWN
já confirmado continua devolvendo seu JSON original e mudança de corpo dá
conflito; callback sem ID comprovado fica sem associação. Sem inferência por
texto, telefone ou horário. OpenAPI1.8.0 mantém schemaVersion1.5.0 e DTOs.
Se reset já detém a identidade, o fence KEY SHARE SKIP LOCKED adia a unidade
health; result novo falha503 sem recibo/avanço. Evita ciclo identity/outbox.
`processed` conta unidades avaliadas, não prova expiração ou entrega.
Matriz preparada67 classes/507 PostgreSQL; execução remota ainda pendente.

### Admissão delivery-v1 e validade do aviso GPS — quarta fatia120

Limites próprios VanRides, fixos e versionados:30 autorizações por60s no
ambiente/instância e12 por identidade destinatária nesse escopo. Não são
capacidade prometida da Evolution. Todo message_attempt de transporte conta,
em qualquer estado, inclusive NOT_SUBMITTED e credencial revogada/rotacionada.
A janela usa `started_at > agora−60s`. A vaga é a30ª/12ª autorização mais
recente+60s, inclusive em histórico anterior acima do limite. Não há override
de laboratório, nova fila ou token bucket. Reset autorizado da coorte apaga
seu histórico e é um limite explícito dessa prova de laboratório.

Claim exclui saturados antes do LIMIT e ordena APPROACH0/RESPONSE10/REMINDER20,
seguido de created_at/id. SOURCE continua fechada para APPROACH130. A prioridade
é estrita: não promete fairness; tráfego superior contínuo pode fazer lembrete
expirar. Capacidade e utilidade precisam de avaliação no piloto. Poll5s de W01
é global e não uma cadência por usuário.

Authorize revalida quota após esperas, sob delivery lock, antes de gerar token.
Quota cheia retorna429 sem tentativa: invalida os3 campos de lease e mantém
geração/TTL. Adia PENDING à maior vaga dos dois escopos; se ela não couber no
deadline de negócio, expira e purga ciphers sem alterar not_before para depois
do TTL. Uma próxima lease/credencial será revalidada. Payload privado pendente
continua cifrado; resultado antigo permanece imutável.

O fence profile SHARE existente já ordena stop/revoke/watchdog/reset oficiais,
que tomam profile UPDATE antes de run/session. Não foram adicionados locks
redundantes. Aviso confirmado exige sessão ACTIVE da origem mais recente,
device/generation vigentes e lease/frescor atuais; submitBefore respeita
aquisição+20s e a lease GPS, além dos limites anteriores. O aviso de
indisponibilidade não recebe esse cap, pois comunica justamente recuperação.
Frescor e TTL são rechecados após esperas. Finish140 e produtor130 continuam
fechados. V37 acrescenta índice de quota; OpenAPI1.9.0 mantém DTOs/schema1.5.0.
Preparados24 PG novos, matriz71/531; execução real ainda aguarda a CI.

V27 é aditiva; V26 permanece imutável. Não há configuração de sessão/número
real ou mudança remota nesta implementação. Seed/reset continua limitado a
coortes sintéticas; fixtures de transporte usam aliases artificiais no DB
isolado, não uma admissão de contato real.070 deve definir validade de mensagens
históricas antes de habilitar comandos operacionais;040/050 não iniciam runs.

Rollback: desabilitar integração e parar runtime local específico, preservando
recibos e banco. Reverter código próprio sem apagar migration aplicada. Nunca
aplicar automaticamente a stack GPS ou reconfigurar n8n/Evolution existentes.
Gates e hashes finais ficam no QA Log e na evidência canônica do programa.

Validação local fechada em31/10/2026 na fonte limpa
`b69a39b4e9ab2b83de022af6df3ff01f9614bcd0`: gate1227 testes sem falhas, erros
ou skips, incluindo182 obrigatórios PostgreSQL. Smoke integrado8/8 no mesmo
JAR conferiu o protocolo com provider falso, cleanup e revogação. Isso não
comprova importação n8n, sessão real, dispositivos ou liberação de produção.
