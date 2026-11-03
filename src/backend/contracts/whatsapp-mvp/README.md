# Contrato WhatsApp MVP v1

`openapi.yaml` usa OpenAPI 3.0.3 e serialização JSON, que também é YAML válido.
A fonte funcional são os documentos 02/08 do coordenador. `fixtures/catalog.json`
contém somente dados sintéticos e não é evidência de execução.

O catálogo v1.4.0 tem 29 comandos e 20 operações. Cada operação e variante de comando
identifica o card responsável em `x-vanrides-implementation-card` e distingue
`CARD_040_FOUNDATION`, `CARD_050_TRANSPORT`, `CARD_070_CONVERSATION`, `CARD_080_DRIVER_SETUP`, `CARD_090_PASSENGER_SETUP`, `CARD_100_ATTENDANCE` e `FUTURE`. Isso não substitui os testes do produtor,
consumidor, PostgreSQL, guard e smoke do mesmo artefato.

No card 040, comandos com efeito ficam limitados a `START_ONBOARDING`,
`SET_PROFILE_NAME`, `ACCEPT_NOTICE`, `CLAIM_DRIVER_INVITE` e `CONFIRM_PAIRING`.
As ações restantes têm schema e exemplo para evolução coordenada; devem ser
recusadas sem efeito, resposta de sucesso ou anúncio em `allowedCommands`.
A fundação inclui contexto com lease, pareamento, refresh, status e revogação.
050 implementa recepção normalizada e claim/result de filas, com envio de
respostas fixas de texto.070 amplia a conversa com `RESPOND_TO_MESSAGE` (template
fechado), `REQUEST_SUPPORT` mínimo e variante vazia de códigos vinculados à inbox.
Jobs, rotinas, presença, coleta e
recuperação avançada permanecem nos cards indicados. Nenhum exemplo habilita
um endpoint futuro; contrato local não comprova uma sessão real do WhatsApp.

Todos os DTOs/envelopes controlados pelo produto têm `additionalProperties:false`.
A única exceção é `EvolutionRawEventRequest`: corpo raw do perfil
`evolution-2.3.7-baileys`, baseado no inventário de versão e fontes fixadas nas
[fixtures do provider](evolution-2.3.7/README.md). O envelope pode evoluir, mas o
normalizador só persiste campos permitidos; apikey, remetente do bot e mídia
são descartados. `/events` exige credencial CHANNEL vinculada à instância e
confirma202 só depois do commit. Nunca aceitar evento normalizado HTTP que
escolha livremente o ator. Número/sessão de teste e entrega real ainda requerem
ensaio integrado autorizado.

Inbox ordena mensagens por `received_order`, uma sequência de recebimento do
banco. RETRY/PAUSED bloqueiam apenas a própria conversa. Lease e credencial são
revalidados depois de esperas; replay de ACK já comprometido não repete efeito.
Pin, alias e códigos não entram na projeção para o trabalhador de conversa.
O destinatário de uma resposta é o alias exato do evento do comando, resolvido
pela API. STATUS/CONNECTION/ECHO nunca são comandos humanos.

Outbox050 libera apenas `RESPONSE/TEXT`, política `foundation-v1`, produzida
atomicamente com os cinco comandos acima. O endpoint de autorização persiste
a tentativa antes de revelar conteúdo/endereço e mantém uma submissão por
instância. Crash/timeout ambíguo é UNKNOWN, sem reenvio automático. Callbacks
duráveis são conciliados por instância+ID externo em result/claim; READ não
regride para ACK. Outros propósitos, backoff e reconciliação operacional
completa continuam em120. Flags desligadas preservam o recebimento de status
e resultados, bloqueando novas mensagens processáveis e submissões.

## Admissão de entrega120 (OpenAPI1.6.0)

Primeira fatia, sem novos DTOs, propósitos ou retry: o schemaVersion continua
1.4.0 e as fixtures de payload permanecem iguais. CONNECTION autenticado pelo
CHANNEL correspondente projeta o estado por ambiente/instância. `open` admite;
`connecting`/`close`/`refused` suspendem. Eventos antigos não reabrem e empate
prefere suspensão. Até30s de futuro, ordena por agora da API; acima disso,
INVALID_PAYLOAD desfaz recibo/projeção. A política consta de
`x-vanrides-security-policy.deliveryConnection`.

Claim desconhecido/suspenso retorna `items:[]`; authorize de lease válido
retorna503 DEPENDENCY_UNAVAILABLE sem tentativa/endereço/conteúdo. OPEN só
vale enquanto o CHANNEL fonte existe e permanece válido no mesmo escopo.
Seu expiry limita submitBefore. A mesma trava ordena CONNECTION e reserva;
reconectar não renova TTL nem impede expiração antes de drenar o backlog.
Result e callbacks históricos continuam possíveis com credencial válida.
O evento não prova conexão atual; o probe do worker complementa o gate.

Instância deve ser exclusiva por ambiente, pois a deduplicação histórica da
inbox ainda usa instância sem ambiente. Nenhuma fixture habilita canal real;
evolução/retentativa segura permanece pendente no card120. Testes PostgreSQL
da nova projeção estão preparados para CI; isto não registra PASS de runtime.

## Retentativa segura120 (OpenAPI1.7.0/schemaVersion1.5.0)

`OutboxResultRequest` tem quatro variantes fechadas: SUBMITTED exige ID externo;
UNKNOWN exige failureCode; FAILED permite CHANNEL_UNAVAILABLE/REJECTED;
NOT_SUBMITTED aceita somente CHANNEL_UNAVAILABLE e nenhum ID externo. O último
afirma zero POST invocado, inclusive sem continuação assíncrona posterior; não
é inferência a partir de timeout/status HTTP. Produtor e consumidor precisam
estar alinhados antes de habilitar o resultado novo.

Somente NOT_SUBMITTED comprometido permite repetir: máximo3 totais, esperas
5s/15s, nova lease/geração/token e revalidação integral. PENDING não garante
retry: conexão fechada bloqueia claim, TTL/negócio podem expirar/cancelar e o
limite esgotado retorna FAILED. Ciphers privados só sobrevivem ao retry seguro
pendente. UNKNOWN/FAILED não repetem; EXPIRED/CANCELED não reabrem. Replay de
qualquer tentativa devolve o recibo original sem tocar tentativa posterior.
As fixtures continuam sintéticas; reconciliação UNKNOWN/health é outra fatia.

## Manutenção120 (OpenAPI1.8.0)

HEALTH_RECONCILE agora usa o JobTickRequest/Response já reservado, com os
mesmos DTOs/schemaVersion1.5.0. Independe canal/flags, processa até100 outboxes
por página e usa cursor da mesma janela UTC/ambiente/instância/tipo. Expira
UNKNOWN no TTL e registra caso operacional sem pausar conversa; também expira
pendências não submetidas. Fatos tardios avançam apenas a evidência da tentativa
quando a outbox é terminal. Replay confirmado é imutável; sem ID externo não
há correlação inventada. Casos são consultados somente pela CLI privada
auditada, com metadados; não foi criada API admin ou fechamento automático.

## Política delivery-v1 (OpenAPI1.9.0)

Sem mudar DTOs/schema1.5.0:30 autorizações/60s por ambiente+instância e12 por
destinatário nesse escopo, todos estados/credenciais históricas contados.
Quota cheia é429 sem novo token/tentativa; limpa lease e adia ou expira conforme
o deadline de negócio, sem renovar TTL. Ordem estrita0/10/20 para
APPROACH/RESPONSE/REMINDER, com FIFO no empate; APPROACH continua fechado130.
Não promete fairness nem representa limite do provider. Aviso GPS confirmado
também respeita captura+20s/lease da sessão mais recente; indisponibilidade
mantém validade informativa própria. Valores e limites de laboratório constam
de `x-vanrides-security-policy.deliveryAdmission` e do documento de transporte.

## Conversa070

Contexto informa `registrationStep` e `eventFreshness` sem nome, hash ou contato.
A janela de mensagem corrente é300s, tolerância futura30s, com nova checagem
após locks. Mensagens antigas/sem data só podem gerar orientação fixa. Replay
comprometido conserva o resultado anterior sem reexecutar efeito.

Convite e pareamento aceitam payload `{}`: somente o hash da própria inbox é
usado na consulta. A variante com código bruto preserva compatibilidade e deve
corresponder ao mesmo hash. Nenhum hash/ator/referência livre é aceito no payload.

`RESPOND_TO_MESSAGE` seleciona apenas `responseCode` enumerado e permitido pela
mensagem/estado. `REQUEST_SUPPORT` exige pedido humano explícito, abre caso
mínimo e pausa a conversa sem suspender o perfil. Detail livre não é persistido.
Retomada assistida e operação completa pertencem a140; W01 não substitui equipe.

`ACCEPT_NOTICE` exige ACEITO inequívoco, mensagem corrente e aviso emitido pela
API ainda válido, após o nome. Registrar a emissão não prova entrega/leitura.
O texto wa-mvp-v1 é provisório para ensaio sintético; não libera contatos reais.
Respostas070, inclusive negativas com recibo, usam `conversation-v1`/parte
`conversation` na mesma transação. A outbox mantém compatibilidade com respostas
`foundation-v1` já persistidas. Não executar um segundo comando para responder
ao erro de um comando que já consumiu o evento.

## Autorização e idempotência

O guard deve negar método/rota/principal não enumerados, descendentes
inexistentes, `OPTIONS` e qualquer requisição com `Origin`. Não há origem CORS
permitida no MVP. As credenciais legadas nunca autorizam os prefixos novos.
`CHANNEL`, `CONVERSATION_WORKER`, `DELIVERY_WORKER` e `SYSTEM_JOB` usam credenciais
separadas por ambiente e função. `OPERATOR` existe somente na CLI; não há rota
administrativa HTTP neste contrato.

O ator humano deriva de inbox autenticada e do lease corrente. `actorId`,
`driverId`, `passengerId`, papel e destinatário não entram no envelope/payload
de comandos. IDs de ator em respostas próprias não concedem autoridade.
`X-Inbox-Lease` é obrigatório em `context`, `commands` e `inbox/{id}/result`,
vinculado a evento, conversa, credencial, geração e expiração; a operação
revalida tudo dentro da transação do efeito/ACK.

Há duas unicidades independentes: chave idempotente vinculada ao principal,
operação e hash do corpo; e evento humano (`inboxEventId`). Mesma chave/corpo
retorna o resultado original; corpo diferente gera `409 IDEMPOTENCY_CONFLICT`.
Trocar a chave não repete o efeito do evento. A recuperação por chave em comandos dura 24h e contém somente resultado sem
segredos. Em `begin`, o resultado só é recuperável até a expiração do pedido
original (cinco minutos). `redeem`, `refresh` e `revoke` usam replay criptografado
por cinco minutos, com prova nova e revalidação do estado autorizado atual.
Pedido expirado/revogado não reabre; resultado expirado ou credenciais
supersedidas/revogadas não são reemitidos. Após expirar a recuperação, a
unicidade do evento/resgate ainda impede novo efeito. Conservar recibos
conforme a política de retenção, sem segredos ou GPS em logs/evidências.

## Perfil DPoP do produto

O primeiro `begin` vem do app aberto pelo ícone confiável, com chave P-256
mantida no Keychain/Keystore, desafio PKCE S256 e `Idempotency-Key`. Chave,
pedido, desafio, plataforma e ambiente são imutáveis. Custom scheme apenas
retorna ao pedido existente e não cria/substitui esse vínculo.

Cada prova é um JWS ES256, `typ=dpop+jwt`, chave pública embutida sem referência
remota, `nonce` do servidor, `jti` único, `iat`, `htm` e `htu` canônica da
configuração do servidor. Não confiar em `Host`/forwarded headers arbitrários.
O claim `vr_body_sha256` é SHA256 base64url sem padding dos bytes exatos do
corpo e é obrigatório em `begin`, `redeem`, `refresh` e `revoke`. Para uma nova
prova, não reserializar o JSON já enviado ao calcular seu digest. Quando há
access token, `ath` vincula a prova ao token apresentado. Requests sem prova
assinada retornam `401 INVALID_CREDENTIAL`; a negociação de nonce usa
`401 PROOF_NONCE_REQUIRED` e cabeçalho `DPoP-Nonce`. Nova tentativa cria prova
assinada/JTI novos e preserva a mesma chave/corpo idempotente.

`begin` retorna código base20 de oito caracteres, validade de cinco minutos
e limite de cinco tentativas por ator/janela. A constante `WaCrypto.CODE_ALPHABET`
é `BCDFGHJKMNPQRSTVWXYZ`: 20 caracteres únicos, sem `L` e com `Z`, evitando
confusão entre `L` e `1`. O metadado `userCodeAlphabet` e o pattern de
`PairingUserCode` devem acompanhar essa constante. As fixtures cobrem um código
válido com `Z` e rejeição de código com `L`.
`status` prova a mesma chave e
não devolve identidade do motorista. `redeem` exige aprovação na conversa,
chave e PKCE originais; substitui atomicamente aparelho/credenciais anteriores.
Access/refresh são tokens opacos, aleatórios e armazenados por hash; access
dura cinco minutos e refresh é rotativo, com validade máxima de 30 dias. A sessão
usa `Authorization: DPoP <access token>`, sem JWT legado. Refresh usa o token
no corpo e a mesma chave; refresh/revoke também exigem `Idempotency-Key`.
Este é um perfil de produto e não uma implementação OAuth completa.

Revogação própria continua disponível com flags de novos efeitos desligadas.
Estado do coletor e fim de run não comprovam parada física antes do ACK.
`status` inclui somente identidade/preparo/run próprios, sem GPS global.

## Operação restrita por CLI

`driver-bootstrap` habilita somente identidade de teste previamente autorizada
por allowlist/convite de uso único; dizer “sou motorista” nunca concede papel.
Bootstrap usa a identidade/alias já registrados e não fabrica inbox humana.
`support` e `revoke-device` exigem escopo mínimo, motivo e auditoria.

`seed-test-data`/`reset-test-data` são operações locais limitadas ao ambiente
e lote sintético exatos. Devem recusar produção, dados fora do lote, ambiente
ambíguo e credencial de outro principal. Reset não é purge genérico nem altera
cadastros legados. Os nomes/argumentos executáveis ficam no runbook da CLI do
runtime; este documento não presume que um comando sugerido já foi executado.
Segredos entram por cofre/arquivos `_FILE`, nunca em argumentos documentados,
fixtures, Git ou saídas de teste. Flags devem preservar revogação/parada/cleanup.

## Verificação do artefato

`WhatsAppOpenApiContractTest` verifica estrutura com `swagger-parser-v3 2.1.48`,
referências exclusivamente locais, fechamento dos DTOs controlados, exemplos
e todos os tipos de comando/job. O teste recusa `$ref` remoto antes de chamar
o parser e usa `readContents` com resolução/flatten desabilitados.
`ContractFixtureValidator` valida o subconjunto de Schema Objects usado neste
contrato (tipos, campos, obrigatoriedade, enum, composição, limites e formatos).
Os testes negativos adicionam ator/papel/empresa/GPS ao comando e campos
proibidos ao pareamento/jobs; isso deve falhar mesmo quando o JSON é bem formado.

Os exemplos de chave pública, PKCE, nonce, lease e tokens são marcadores sem
par privado/sessão válida. Coordenadas `[0,0]` existem somente em fixtures
sintéticas de coleta/pin futuro. Não usar fixtures como credenciais de um smoke.

Referências do parser: [uso oficial](https://github.com/swagger-api/swagger-parser#usage)
e [release 2.1.48](https://github.com/swagger-api/swagger-parser/releases/tag/v2.1.48).
A dependência é somente de teste; o contrato não autoriza atualizar Spring/Jackson
do runtime ou configurar serviços externos.
# Configuração de motorista080

Na versão1.2.0 o catálogo tinha27 comandos; a1.3.0 acrescenta o avanço090.
ADVANCE_DRIVER_SETUP consome somente a inbox autenticada,
sem valores no payload. CONFIGURE_ROUTINE/SET_STOP_ORDER/SET_ROUTINE_STATE usam
routineRef+confirmationRef; CREATE_INVITE usa confirmationRef; REVOKE_INVITE usa
inviteRef+confirmationRef; CANCEL_OCCURRENCE usa occurrenceRef+confirmationRef;
GET_SUMMARY usa summaryRef. A disponibilidade real vem de allowedCommands.
Há no máximo um comando080 por mensagem e referências neutras de alvo único.

O rascunho único é privado em conversation_state.context_json.driverDraft. O
perfil incrementa versão em cada avanço/mutação; confirmação CONFIRMAR+marcador
vale5min, captura revisão/digest, versões dos alvos e outbox do resumo. SIM,
marcador antigo e referências fabricadas nunca autorizam o efeito. Nome, GPS,
valores do draft e marcador não entram no contexto do worker. Ajuda/pareamento
preservam o draft; suporte invalida a confirmação e pausa preservando seus dados.

Rotinas de ida/volta são independentes; sem membros, a ordem fica pendente.
Reordenação exige permutação completa de membros ativos/vínculos e pontos
aprovados. Pausa não muda run; cancelamento por data apenas cria/atualiza a
ocorrência CANCELED, rejeitando execução iniciada/encerrada e preservando snapshot.
Presença e execução continuam100/110.

Convite emitido é texto encaminhável PASSAGEIRO+token, não link wa.me final170.
Defaults exibidos:1uso/24h; limites1–10usos/5–1440min. Só existe hash purpose
passenger-invite em driver_invite; texto privado cifrado na outbox usa AAD de
ambiente/outbox/comando/ator. A decifra ocorre apenas em authorize-submit, com
revalidação de TTL/revogação/limite. Resultado/expiração/cancelamento apagam a
cifra; purge integral operacional pertence150. Não guardar token em recibo,
payload_json, draft, contexto ou logs. REQUEST_LINK090 reserva um uso apenas
quando o novo pedido fica durável; replay não reconsome.080 não comprova T02 E2E.

## Passageiro090

`ADVANCE_PASSENGER_SETUP {}` inicia ou avança o wizard privado, inclusive para
convidado sem perfil em ONBOARDING. O servidor lê convite, nome, notice,
preferência inicial de avisos, rotinas/dias, pin fixo e referência do evento e
do draft. Nada disso entra no modelo, no payload de comando ou nos itens n8n.
`SET_PREFERENCE` permanece um comando separado reservado ao140.

As operações finais copiam somente as referências autorizadas do contexto:
`REQUEST_LINK {confirmationRef}`;
`APPROVE_LINK {linkRef,pickupRef,confirmationRef}`;
`CLOSE_LINK {linkRef,confirmationRef}`;
`PROPOSE_PICKUP {sourceMessageId,confirmationRef}`;
`APPROVE_PICKUP {pickupRef,confirmationRef}`. `sourceMessageId` vem da opção
QUESTION para um evento de pin do passageiro; não é escolhido pelo texto nem
substituído pelo ID da mensagem de confirmação. O draft define recusa, correção
ou encerramento no CLOSE_LINK. O contrato reservado anterior com inviteCode,
reason ou reference livre deixa de ser aceito nesses payloads090.

Ator e dono são sempre derivados do evento autenticado. Aprovações pertencem
ao motorista elegível em PAIRING/READY, e a API revalida a dupla, etapa e versões.
O agente copia uma única oferta completa; ofertas conflitantes, referências
ambíguas/incompletas, pausa ou evento antigo nunca autorizam mutação. Ajuda e
suporte preservam a prioridade. Convite, uso, vínculo, ponto e notificações à
contraparte são responsabilidade transacional do servidor.

W03 usa o mesmo endpoint privado de controle do W02 com corpo vazio. Todos os
exports continuam inativos; W04/W05 continuam fechados por dependência. Essa
evolução não altera os recibos históricos070/080 nem comprova ensaio físico.

## Presença no card 100

O passageiro pode declarar presença por ocorrência. A API identifica a pergunta
pela citação entregue ou pela seleção explícita de data e sentido. Se houver
ambiguidade, o agente encaminha `ADVANCE_ATTENDANCE`. A escolha autorizada em
`attendanceChoice` e a única referência `QUESTION` compõem `SET_ATTENDANCE`.
O motorista consulta o seu resumo por `GET_SUMMARY`.

O W04 aciona somente `MATERIALIZE_OCCURRENCES` e `DUE_REMINDERS` com a
credencial SYSTEM_JOB da instância. Cada página aceita até 100 unidades.
A janela é o minuto UTC da chamada inicial e o servidor fornece o cursor.
O n8n recebe apenas os contadores.

No envio de GPS, o recibo persistido contém somente os resultados por sequência (`accepted`, `duplicate` e `rejected`). Um reenvio preserva esses resultados, mas devolve a ação, o estado do GPS e a validade da autorização atuais. Reenviar um recibo antigo não renova a posição nem a autorização e não reativa uma sessão encerrada.
