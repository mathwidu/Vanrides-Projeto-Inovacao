# Entrada privada Evolution: laboratório e homologação

O mesmo main.mjs preserva o perfil original do laboratório e admite um modo
explícito HML_V1. Não conecta instância, emite credencial, cadastra pessoa,
publica rota ou altera a API. O bootstrap de aliases continua separado e
não foi alterado.

## Dois contratos separados

| Regra | Laboratório original | HML_V1 |
|---|---|---|
| Seleção | Documento sem mode, schema original exato | mode=HML_V1 e environment=vanrides-mvp-hml |
| Aprovados | Até dois contatos A/B | De um a quatro contatos A–D com approved=true |
| Janela | Até30min | Até30dias |
| API | Origem privada conforme política original | Origem HTTPS canônica obrigatória |
| Conteúdo humano | Texto simples | Texto simples e PIN estático |
| Resposta citada | Metadata removida como antes | Somente stanzaId conforme o backend |
| Quota | Cumulativa da rodada, preservada | Janela deslizante de60s, com recuperação |
| Receipt | PERSISTED/DUPLICATE | PERSISTED/DUPLICATE/IGNORED e header de ambiente esperado |

Ausência de mode NÃO converte um documento de homologação para o laboratório:
campos extras, approved e contatos além do schema antigo são recusados.
Um mode desconhecido também é recusado. Não alterar o modo de um pacote antigo
ou reaproveitar seu manifest como prova da nova configuração.

## Arquivo privado HML

WA120_INGRESS_CONFIG_FILE aponta para um único JSON absoluto, regular, não
symlink final, dono do UID efetivo, modo0600 exato e até8192bytes. O nome do env
foi conservado por compatibilidade. Não há fallback de token em env, descoberta,
reload ou endpoint HTTP de configuração.

Campos obrigatórios e únicos:

| Campo | Contrato |
|---|---|
| mode | HML_V1 |
| environment | vanrides-mvp-hml |
| instance | Instância aprovada; ASCII alfanumérico/_/-, 1–80 |
| webhookToken | Token canônico de32bytes base64url, exclusivo da Evolution→ingress |
| channelToken | Outro token canônico de32bytes, principal CHANNEL da API |
| apiOrigin | HTTPS sem path/userinfo/query/fragment, por exemplo https://vanrides-inovacao-api.matheusduarte.dev.br |
| startsAt/endsAt | RFC3339 válido; intervalo positivo de até30dias |
| listenHost | 127.0.0.1, ::1 ou0.0.0.0 |
| listenPort | Inteiro1–65535; porta7180 na receita de container |
| contacts | Um a quatro objetos aprovados, somente os que já existem |
| limitsPerMinute | Objeto fechado com human/perContact/status/connection |

Cada contato contém label A/B/C/D único, approved=true e pn no formato
numérico@s.whatsapp.net. lid numérico@lid é opcional e deve ser confirmado
antes de ser incluído. Nenhum alias pode aparecer em dois contatos. Não
preencher B/C/D com placeholders para começar com apenas A.

O indicador approved é declaração do operador que prepara o segredo; não é
prova automática de consentimento. O documento inteiro, com contatos e tokens,
fica fora do Git. Esses campos são copiados, congelados e não enumeráveis na
configuração do processo; o JSON de configuração não entra nos logs.

Sugestão inicial de limites, para decisão do operador:
~~~json
{"human":60,"perContact":20,"status":240,"connection":60}
~~~
São limites de tentativas por60s, não limites cumulativos da homologação.
Caps aceitos: human≤240, perContact≤120, status≤1200, connection≤120; valores
inteiros positivos, perContact≤human. A estrutura só mantém timestamps
limitados por esses caps, sem corpos, PINs ou identificadores de mensagem.
Uma réplica: os limites são locais ao processo e reiniciam num redeploy.

## HTTPS e autenticação

O servidor recebe somente POST /evolution/webhook, Authorization Bearer com
webhookToken e JSON limitado a32KiB. Autentica antes de ler o corpo. Não aceitar
token na URL/query, apikey do provedor como autenticação implícita, Origin,
headers duplicados, compressão, grupos, broadcasts, ecos ou mensagens editadas.

TLS termina no proxy aprovado. O Node escuta na rede privada e não deve ter
porta publicada no host. Para compartilhar o domínio da API, uma receita de
proxy pode usar Host exato + Path exato /evolution/webhook + Method POST com
prioridade acima do router geral da API; serviço interno7180 e os controles
de TLS já aprovados. Essa rota/stack não é criada por este diretório.

O encaminhamento usa somente HTTPS para
/integrations/whatsapp/v1/events, Authorization Bearer com channelToken distinto,
redirect=manual, um POST e prazo máximo5s. Nenhum token ou URL vem do webhook.
No modo HML, o receipt exige X-VanRides-Environment: vanrides-mvp-hml além do
HTTP202 e schema fechado. Ambiente ausente/diferente vira503 sanitizado.

PERSISTED e DUPLICATE contam como forwarded. IGNORED é preservado no receipt,
mas contado separadamente como ignored: não significa processamento de conversa.
Uma allowlist do ingresso não cadastra nem vincula identidades na API.
CHANNEL/instance/environment precisam corresponder e o backend com coortes
restritas precisa ter os contatos/aliases aprovados preparados separadamente.

## Conteúdo e callbacks

Apenas PN/LID explicitamente aprovados podem produzir mensagem humana.
Quando o evento contém dois aliases, ambos precisam pertencer ao MESMO contato.
Alias LID novo não é aprendido automaticamente.

No modo HML, locationMessage único pode levar degreesLatitude/degreesLongitude
numéricos finitos dentro das faixas e isLive ausente/false. O envelope enviado
contém somente essas coordenadas e isLive=false. Live location, mistura com
texto, coordenada inválida, mídia e conteúdo encapsulado continuam descartados.
Nome/endereço/thumbnail e qualquer corpo citado são removidos.

Somente contextInfo.stanzaId válido passa como referência opaca, nesta ordem:
data.contextInfo, extendedTextMessage.contextInfo, locationMessage.contextInfo.
É a precedência de WaEvolutionNormalizer. O backend ainda verifica a referência
na conversa/identidade correta; o ingress não escolhe ator nem aceita replyTo
arbitrário como campo de comando.

messages.update aceita os statuses existentes, fromMe=true e keyId;
connection.update aceita open/connecting/close/refused. Ambos exigem instância,
autenticação e timestamp válidos, são reconstruídos sem destinatários e seguem
o mesmo /events com CHANNEL. Um connection OPEN não é fabricado por este filtro.

## Recuperação de limite e renovação da janela

HML retorna429 RATE_LIMITED com Retry-After em segundos quando a janela de60s
estiver cheia. Novas tentativas voltam a ser admitidas conforme os timestamps
expiram. Os contadores de observação continuam acumulados, mas não esgotam a
capacidade futura. Busy/concorrência e deadlines continuam separados da quota.

Antes de startsAt e a partir de endsAt, a política descarta com204 e não faz
POST. Não há renovação por mensagem, callback, relógio do cliente ou alteração
do objeto original. Para renovar, preparar um NOVO segredo0600 com janela válida
de até30dias e fazer redeploy controlado após autorização. Usar início coerente
com os eventos ainda permitidos; eventos anteriores à nova janela serão descartados.
Credenciais da API/Evolution têm validade e revogação próprias: mudar endsAt não
renova essas credenciais. Não há SIGHUP novo.

## Container e validação focalizada

O Dockerfile usa o mesmo pin Node22.23.2 do worker, usuário node, contexto deste
diretório e somente módulos de runtime. Não instala pacotes. No Swarm, montar
o JSON como secret com uid/gid do usuário node e mode0600; defaults0444/0400
são recusados. Confirmar UID/GID na imagem final (receita Node:1000).
Definir WA120_INGRESS_CONFIG_FILE para o target montado e listenHost0.0.0.0
somente quando necessário ao proxy privado.

Teste mínimo executado:
~~~bash
node --test src/whatsapp/lab/live-ingress/ingress.test.mjs \
  src/whatsapp/lab/live-ingress/hml.test.mjs
~~~
Resultado:22/22, zero skips, cerca de5,2s. Inclui o perfil original, quotas e
timeouts anteriores, um a quatro aprovados, PIN/resposta sanitizados, callbacks
autenticados, recuperação exata após60s, janela imutável/nova implantação,
receipt IGNORED e negação de resposta de outro ambiente.

Usa apenas Node/arquivos sintéticos e HTTP loopback com upstream fake. Não
comprova TLS público, build da imagem, secrets Swarm, forma real de webhook,
cadastramento de coorte, pareamento, canal conectado ou entrega WhatsApp.
Nenhuma operação externa foi executada.
