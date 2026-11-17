# Contrato WhatsApp MVP v1

## Modos publicados de homologação

`WA_MVP_ONLINE=true` habilita o MVP funcional implementado, com perfil
`homolog` exclusivo, `WA_TEAM_API_ONLY=false`, `WA_ENVIRONMENT=vanrides-mvp-hml`,
`WA_MVP_ENABLED=true`, `WA_NEW_STARTS_ENABLED=true`, `WA_TEST_COHORT_ONLY=true`
e `VANRIDES_EMAIL_PROVIDER=disabled`. `WA_PUBLIC_BASE_URL` e
`WA_START_LINK_BASE_URL` devem conter a mesma origem HTTPS pública, sem barra
final. A publicação atual mantém `WA_APPROACH_ENABLED=false` e
`WA_APPROACH_REVIEWED_ROUTINE_IDS` vazio; maps/Google/push/e-mail externos e
auto-verificação QA ficam desativados. JWT forte, Basic desativado e rate limits
continuam obrigatórios.

Nesse modo, o filtro admite GET `/actuator/health` e somente os métodos/rotas
marcados `available` em `WaRoutes`. Autenticação, coorte, vínculo, consentimento,
leases e autorização de negócio são os mesmos do produto: CHANNEL,
CONVERSATION_WORKER, DELIVERY_WORKER, SYSTEM_JOB e DPoP do aparelho. Não se aplica
a limitação sintética/de comandos do sandbox. Rotas legadas, operator/admin,
actuator adicional e funcionalidades futuras continuam bloqueados. Disponibilizar
a API não conecta uma conta WhatsApp nem inicia o sensor do aparelho.

`WA_TEAM_API_ONLY=true` continua sendo um sandbox separado de quatro rotas
CONVERSATION_WORKER, origem SYNTHETIC e comandos de onboarding/ajuda, com
`WA_ENVIRONMENT=vanrides-team-hml` e novos starts desligados. Os dois modos são
mutuamente exclusivos e recusam perfis locais, produção ou mistos. `disabled`
fora de um desses modos válidos é erro de configuração; nunca envia e-mail nem
registra seu conteúdo. Respostas, inclusive health, incluem
`X-VanRides-Environment` com o ambiente fixo do modo; não há revisão fornecida
pelo cliente. Sem modo explícito, esse marcador não é emitido.

Card130: `GET_DRIVER_LOCATION` está materializado com payload vazio, seleção
pela API e `STATIC_PIN` somente no `authorize-submit`. Claim não fornece GPS;
payload da outbox e recibos também não o retêm. Veja
[política e limites de ensaio](../../docs/whatsapp-approach.md). Os parâmetros
de aproximação são hipóteses versionadas, flag off e allowlist de rotinas revisadas.

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
mensagem/estado. `FAQ` responde perguntas reconhecidas no evento autenticado
sem avançar o cadastro nem renovar o aviso de privacidade.
`REQUEST_SUPPORT` exige pedido humano explícito, abre caso
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

O caminho normal do candidato local é a conexão guiada descrita abaixo. O
`begin` legado continua como contingência explícita pelo app aberto no ícone
confiável, com chave P-256 no Keychain/Keystore, PKCE S256 e `Idempotency-Key`.
Chave, pedido, desafio, plataforma e ambiente são imutáveis. Custom scheme não
cria nem substitui um pedido legado.

Cada prova é um JWS ES256, `typ=dpop+jwt`, chave pública embutida sem referência
remota, `nonce` do servidor, `jti` único, `iat`, `htm` e `htu` canônica da
configuração do servidor. Não confiar em `Host`/forwarded headers arbitrários.
O claim `vr_body_sha256` é SHA256 base64url sem padding dos bytes exatos do
corpo e é obrigatório em todos os POST do aparelho, incluindo os novos
`pairing-links/{id}/preview` e `pairing-links/{id}/claim`. Para uma nova
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

## Conexão guiada e opções de início (candidato local, OpenAPI 1.11.0)

Implementações V47/V48 em preparação; a referência remota conferida continua
em V44. Este contrato não comprova publicação, abertura física ou aceite da
experiência. O [roteiro do candidato](../../../../docs/desenvolvimento/roteiro-validacao-fluxo-simples.md)
separa essas provas.

O motorista autorizado envia “Conectar meu celular”. O bot emite um link de
cinco minutos: `/motorista/conectar#v1.<linkId>.<linkToken>`; o esquema nativo é
`vanridesdriver://pair/connect` com o mesmo fragmento. Não há versão de viagem
nesse formato. O fragmento fica fora da requisição HTTP e é removido da URL da
página intermediária. Sua leitura local apenas mostra a revisão; o toque no app
é que prepara chave/PKCE e solicita a conexão.

- `POST /driver-app/v1/pairing-links/{id}/preview`: somente PROOF/DPoP, sem
  bearer, corpo fechado `{linkToken}`. Confere pedido vigente e informa se é
  o aparelho atual ou uma substituição. Não cria autorização nem liga GPS.
- `POST /driver-app/v1/pairing-links/{id}/claim`: PROOF/DPoP e
  `Idempotency-Key`; corpo fechado com `linkToken`, `publicJwk`,
  `pkceChallenge`, `pkceMethod`, `platform` e `appVersion`. A chave deve ser a
  da prova. Retorna pedido/estado/prazo, **sem `userCode`**. Repetir o mesmo
  pedido preserva a chave, PKCE e prazo originais; não gera outra conexão.
- Após o claim, o app volta à conversa fixa do bot. A aprovação exige mensagem
  humana “Conectar este celular”, na mesma instância e identidade, posterior
  à submissão confirmada da revisão atual. Uma citação deve apontar exatamente
  para essa revisão; sem citação, exige horário de envio do provider estritamente
  posterior à submissão, além do recebimento posterior. Horário de recebimento
  sozinho ou `date_time` substituto não prova essa sequência. Empate no segundo
  pede nova confirmação. Não inferir aprovação pelo modelo ou por um clique.
- “Concluir no app” usa o retorno já existente, sem segredo:
  `/motorista/vinculo/retorno`. O app usa status/redeem existentes, a mesma chave
  e PKCE, verifica a sessão e retorna à conversa após ação explícita. Consultar,
  fazer polling ou reabrir a tela não retorna ao WhatsApp em loop.
- Trocar aparelho requer “Usar este celular” depois da revisão no app. Claim e
  redeem recusam coleta sem parada confirmada; um novo START/RESUME entre os
  dois também impede a troca. Revogação administrativa do motorista, perfil
  apagado, geração inválida ou coorte diferente não são contornados pelo link.
  Expiração/reconexão não apagam nome, trajetos ou passageiros.
- Autorização do link só é revelada no limite de envio privado, cifrada na
  outbox. Não passa pela projeção do agente; links de conexão colados na conversa
  são suprimidos pelo normalizador. Não registrar fragmentos completos.

A conexão inicial tem contagem própria. Depois de aparelho e trajetos preparados,
“Começar viagem” já entrega cada saída pronta com nome, direção, horário e link
próprio. A V47 permite até 30 opções, em mensagens com até três opções cada,
sem outro menu por texto. Todas compartilham o prazo original de cinco minutos;
a primeira ativação aceita consome sua opção e invalida as demais atomicamente.
Cada opção preserva versão e lista da própria viagem. Abrir links não ativa GPS;
uma opção alterada perde seu link sem esconder as irmãs ainda válidas.

O teto a validar é uma mensagem, abrir o link escolhido e confirmar no app:
três ações no Android. No iPhone provisório, o botão da página intermediária
leva a quatro. Não anunciar botões nativos ou abertura direta no iPhone como
prova existente. `START`, `RESUME`, `FINISH` e conexão são propósitos separados;
um link não pode ser reinterpretado como outro.

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

O convite padrão tem um uso e vale 24 horas. A mensagem `CONVIDAR` solicita
o link diretamente por `ADVANCE_DRIVER_SETUP`, com emissão e resposta
privada na mesma transação. O convite personalizado e o envio pelo bot
mantêm a revisão e usam `CREATE_INVITE`. Na instância da equipe com
admissão ativa, pode incluir um link `wa.me` que preenche `PASSAGEIRO <código>`.
O primeiro remetente autenticado elegível reserva o convite, sem consumir seu
uso. Convites personalizados de vários usos e o modo legado exigem admissão prévia.
O envio pelo bot vincula o código ao destinatário da mesma instância/coorte e
requer opt-in explícito e recente. A entrega verifica o destinatário novamente.

Somente o hash do código entra em `driver_invite`. O texto privado fica cifrado
na outbox e só é aberto em `authorize-submit`. Resultado, expiração ou
cancelamento apagam a cifra. `REQUEST_LINK` consome um uso quando o pedido fica
gravado. Uma repetição do comando não consome outro. Código, link e contato
completo não integram recibo, contexto do agente nem logs.

## Passageiro090

`ADVANCE_PASSENGER_SETUP {}` inicia ou avança o wizard privado, inclusive para
convidado sem perfil em ONBOARDING. O servidor lê convite, nome, notice,
preferência inicial de avisos, rotinas/dias, pin fixo e referência do evento e
do draft. Nada disso entra no modelo, no payload de comando ou nos itens n8n.
`SET_PREFERENCE {preference,enabled}` altera uma preferência após o cadastro, a partir
de `LIGAR AVISOS CHEGADA`, `DESLIGAR AVISOS CHEGADA`, `LIGAR AVISOS PRESENÇA`
ou `DESLIGAR AVISOS PRESENÇA`. A API compara o payload com a mensagem autenticada
e oferece o comando somente ao passageiro ativo, com aceite vigente, sem pausa
ou rascunho ativo. A outra chave, o default desligado e a conversa são preservados.
Replay não repete a mutação; informar o valor atual retorna `NO_CHANGE`.
Entrega pendente revalida a preferência antes da submissão; uma mensagem já
submetida pode chegar depois de desligar.

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

A confirmação final do passageiro é a palavra CONFIRMAR, sem código. Ela só vale
com o resumo já enviado (SUBMITTED, DELIVERED ou READ), recebida não antes do segundo em que ele foi emitido (os eventos têm precisão de
segundos), em até 5 minutos e com rascunho e perfil inalterados. O motorista
pode responder citando o resumo atual com `SALVAR ROTINA`,
`APROVAR EMBARQUE`, `APROVAR PONTO` ou `PEDIR CORREÇÃO` no fluxo
correspondente. A resposta citada precisa referenciar a tentativa de entrega
do próprio resumo. A alternativa `CONFIRMAR <código>` segue disponível e
continua exigida no convite direto e nas ações destrutivas.

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

O passageiro pode declarar presença por ocorrência, sem menu. A API escolhe a
viagem pela citação do lembrete entregue; sem citação, pelos filtros de dia e
sentido ou, numa resposta isolada, pela primeira viagem cuja parada ainda não foi
passada. Quando não há viagem, a citação é de uma viagem encerrada ou a mensagem
traz mais de uma escolha, o agente encaminha `ADVANCE_ATTENDANCE` e a API responde
só com texto. A escolha autorizada em `attendanceChoice` e a única referência
`QUESTION` compõem `SET_ATTENDANCE`. Depois da saída, NÃO VOU retira uma parada ainda aguardando embarque sem
reescrever a lista da partida; VOU só confirma quem continua incluído nela.
Uma parada passada ou pulada bloqueia a alteração. Uma ausência já registrada
não pode ser revertida para reabrir a parada após a saída. O motorista consulta o seu resumo por
`GET_SUMMARY` e abre a lista com `ADVANCE_ATTENDANCE`.

O W04 aciona somente `MATERIALIZE_OCCURRENCES` e `DUE_REMINDERS` com a
credencial SYSTEM_JOB da instância. Cada página aceita até 100 unidades.
A janela é o minuto UTC da chamada inicial e o servidor fornece o cursor.
O n8n recebe apenas os contadores. `DUE_REMINDERS` também enfileira para o
motorista a lista de pré-saída, 30 minutos antes da viagem. Um NÃO VOU depois
da saída enfileira um aviso ao motorista quando retira uma parada ainda ativa.
Antes da saída, a ausência aparece na lista; não gera aviso individual.
Cada ocorrência tem no máximo uma lista de pré-saída e cada parada cancelada,
um aviso individual. Os avisos expiram na saída e em cinco minutos,
respectivamente; uma viagem encerrada impede um aviso de cancelamento pendente.
A migration aditiva V42 mantém a V41 do cadastro.

A presença atual fica em `wa.attendance`; a lista original permanece em
`wa.operation_run`/`wa.run_stop`. Correções usam o histórico já existente de
`wa.command_receipt` ligado ao evento e à resposta da outbox, com os prazos de
retenção vigentes. Não há novo log de conversas nem coleta adicional de GPS.

No envio de GPS, o recibo persistido contém somente os resultados por sequência (`accepted`, `duplicate` e `rejected`). Um reenvio preserva esses resultados, mas devolve a ação, o estado do GPS e a validade da autorização atuais. Reenviar um recibo antigo não renova a posição nem a autorização e não reativa uma sessão encerrada.


### Finalização da viagem (140)

`FINALIZAR` ou `FINALIZAR VIAGEM` abre uma revisão de contingência para o
motorista da viagem ativa. `CONFIRMAR <código>` aplica somente a versão
revisada; `ABANDONAR` preserva a viagem. O consumidor copia a referência `RUN`
autorizada pela API e o motivo `MANUAL_COMPLETION`, sem escolher ator ou viagem
pelo texto. As flags de novos inícios e de aproximação não bloqueiam esse fim.

`Parar GPS` continua sendo uma parada técnica: a viagem fica recuperável,
sem ser apresentada como concluída. O fim da viagem revoga intents e leases,
cancela avisos ainda pendentes e mantém o snapshot e os recibos. O estado
`STOP_PENDING` permanece até o ACK autenticado do aplicativo; a API não afirma
que o sensor parou antes dessa confirmação. Avisos de fim e de ACK usam a
origem autenticada da partida e passam pela autorização/revalidação da entrega.
Uma tentativa que já saiu ao provedor não é tratada como retirada garantida.

A chegada automática fica **desligada por padrão** (`WA_AUTO_FINISH_ENABLED=false`)
e usa somente rotinas da allowlist geográfica já revisada, ou a elegibilidade
restrita de ensaio abaixo. Os valores iniciais para ensaio são: precisão até 25 m; evidência anterior fora de 200 m do destino;
todos os pontos de embarque terminais (inclusive o caso sem embarques elegíveis);
velocidade informada até 2 m/s; posições com intervalo máximo de 20 s; permanência
de 60 s dentro de 50 m. A margem de precisão deve caber dentro do raio de chegada
e provar a saída do raio externo. O destino vem do snapshot da partida.

Silêncio, perda de GPS, velocidade ausente, passagem isolada, ponto pendente,
GPS ruim e troca de sessão não completam a viagem nem somam uma permanência
não observada. Somente a última aquisição válida do lote pode disparar o fim.
`wa.run_finish_progress` (V43) guarda tempos e progresso sem trilha de coordenadas;
terminar a viagem remove esse progresso, e excluir sessão/viagem o remove por
cascade. Os parâmetros precisam de calibração física antes de ativar o ensaio
com participantes. Teste sintético não comprova percurso, bateria ou utilidade.


#### Rotinas do ensaio restrito da equipe

`WA_MVP_REHEARSAL_ROUTINES=false` preserva a seleção por UUIDs revisados. O opt-in
`true` permite conferir a elegibilidade diretamente nos dados da equipe, sem
copiar IDs de rotina: exige `WA_MVP_ONLINE=true`, perfil `homolog` exclusivo,
ambiente `vanrides-mvp-hml`, `WA_TEST_COHORT_ONLY=true`, Team Web habilitado e
`VANRIDES_WA_TEAM_WEB_COHORT_ID` válido. Aproximação e finalização automática
continuam com suas flags próprias; o opt-in não liga nenhuma delas sozinho.

O mesmo filtro é usado na produção de aproximação, no fim automático e antes
da entrega do aviso. A rotina precisa estar ativa; motorista, autorização e
aparelho devem estar ativos e coerentes no ambiente, com aviso aceito. A coorte
do motorista deve ser exatamente a configurada pela equipe, no ambiente correto
e com `synthetic=false`. Outra coorte, aparelho revogado, geração antiga, rotina
inativa ou conversa pausada para suporte impedem o ensaio. As revisões da rotina
e dos pontos feitas na partida continuam obrigatórias. Essa seleção operacional
não afirma calibração geográfica nem utilidade já medidas em percurso.

O guard aceita `WA_NEW_STARTS_ENABLED=false` para a API subir em pausa
operacional. Parada e finalização autenticadas permanecem disponíveis. Uma
revisão de finalização não retoma uma conversa pausada nem fecha seu caso de
suporte; a retomada continua na operação própria da equipe.
