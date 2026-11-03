# Worker privado local070

Serviço Node sem banco próprio, nova fila ou dependências npm. A API continua
autoritativa para leases, autorização, comandos, recibos e reconciliação.
`main.mjs` integra os módulos; `process-inbox.mjs` preserva as regras
determinísticas e permite classificação OpenAI explicitamente configurada.

O n8n envia apenas `{}` e recebe status/contagens. Mensagens, aliases,
destinatário, tokens de inbox/outbox e credenciais de serviço não entram em
items, execuções, logs ou exports dos workflows. O serviço não registra corpos,
URLs, configuração ou exceções originais. A credencial de controle do n8n é
própria e diferente das credenciais CONVERSATION e DELIVERY.

## Rotas e resposta pública

`GET /healthz` retorna somente `{status:'UP',role:'WA070_WORKER'}`. Não expõe
ambiente, instância, identidade, credencial, filas ou contagens.

`POST /v1/dispatch-one` e `POST /v1/process-inbox` exigem
`Authorization: Bearer <controlToken>`, `Content-Type: application/json` e corpo
exatamente `{}`. Limite de corpo: 1024 bytes, leitura em até 3s. Query, campos
extras, origem de browser, compressão, método ou rota desconhecidos falham.

Despacho: `{status,claimed,submitted,unknown,failed}`; status fechado em
`IDLE|BUSY|SUBMITTED|NOT_SUBMITTED|UNKNOWN|FAILED|UNAVAILABLE`, contagens inteiras 0 ou 1.
Processamento: `{status,claimed,processed,retried,paused}`; status fechado em
`PROCESSED|IDLE|RETRY|PAUSED|UNAVAILABLE`, contagens 0 ou 1. Callbacks retornando
campos extras, exceções ou valores inconsistentes viram UNAVAILABLE sanitizado.
Erros de controle retornam apenas `{status}` com
`UNAUTHORIZED|INVALID_REQUEST|NOT_FOUND|UNAVAILABLE` e HTTP 4xx/503.

Há um despacho local e até dois callbacks de conversa simultâneos. Isso vale
para **uma réplica**; não é um semáforo distribuído de modelos. O backend050
continua cercando submissões concorrentes por instância. Timeout da resposta de
controle não libera um callback ainda ativo. Desconexão do chamador não cancela
uma operação já autorizada nem seu recibo. O teto da resposta de operação é 25s.

## Sequência e recuperação

`createDispatcher({api,config})` retorna `dispatchOne()`. A sequência é consulta atual
connectionState → claim (`batchLimit:1,leaseSeconds:60`) → authorize-submit →
adapter050 TEXT → result. A consulta usa somente GET, não conecta a instância.
CLOSED/UNKNOWN, erro ou resposta tardia interrompem antes do claim, retornando
UNAVAILABLE sem consumir tentativa. Só OPEN da mesma instância permite seguir.
Budgets máximos: 2s + 4s + 4s + 10s + 5s = 25s, preservando o teto existente. O corpo do
resultado é o mesmo retornado pelo adapter; há uma única tentativa de gravá-lo.
Falha/ACK perdido no resultado retorna UNKNOWN e não repete envio. Perda do ACK
da autorização deixa a tentativa para reconciliação do backend, sem adivinhar
token. O adapter consulta a conexão outra vez dentro dos 10s, depois da
autorização, e retorna NOT_SUBMITTED/CHANNEL_UNAVAILABLE somente sem invocar POST.
Esse status tem claimed=1 e contadores submitted/unknown/failed=0; não promete
repetição. A API decide PENDING, EXPIRED, CANCELED ou FAILED e impõe no máximo
três tentativas totais, com intervalos 5s/15s e TTL original. Um UNKNOWN antigo
nunca autoriza reenvio. Timeout do wrapper aborta o adapter; como não conhece
a fase interna, registra UNKNOWN conservador, mesmo se nenhum POST ocorreu.
A consulta prévia não garante que a rede permaneça disponível após a leitura;
o fencing transacional da API e a classificação conservadora do POST continuam
necessários. A conclusão tardia de um probe já expirado não alcança o claim.

`createApiClient(...)` retorna `{request}`. Aceita apenas os paths WhatsApp
fechados necessários, query UUID canônica do contexto e headers de lease,
idempotência e correlação. URL base e Bearer vêm somente da configuração.
O cliente não segue redirect nem faz retry, e limita requisição/resposta a
32KiB; o timeout cobre headers **e corpo**. Erros WorkerError têm código fechado
e não retêm causa original. O adapter050 preserva a política conservadora:
HTTP 400/422 é UNKNOWN, 401/403/404 é rejeição conhecida; 201 não prova entrega.

## Configuração explícita

`loadConfig(env)` exige `WA070_ENVIRONMENT=wa070-local`. Variáveis não secretas:

| Variável | Regra |
|---|---|
| `WA070_BACKEND_BASE_URL` | Origem sem path, userinfo, query ou fragment |
| `WA070_EVOLUTION_BASE_URL` | Mesmo formato; perfil HTTP estrito do adapter050 |
| `WA070_INSTANCE` | ASCII alfanumérico, `_` e `-`, até 128 caracteres |
| `WA070_LOCAL_MODE` | `true|false`, default false |
| `WA070_HTTP_HOSTS` | Hosts privados exatos separados por vírgula, sem wildcard |
| `WA070_HOST` | Default `127.0.0.1`; permite `::1` ou `0.0.0.0` explicitamente |
| `WA070_PORT` | Default 7080; inteiro 1–65535 |

HTTPS é aceito. HTTP exige localMode e allowlist exata: loopback literal,
Docker DNS de um único label, `host.docker.internal` ou IPv4 RFC1918 canônico.
Não permite FQDN público em HTTP, IPv4 abreviado/integer, redirect ou URL do
chamador. Evolution050 continua aceitando HTTP **somente loopback**, mesmo em
modo local; fake provider deve estar nesse namespace, ou usar HTTPS confiável.

Segredos só pelos arquivos explicitamente indicados em
`WA070_CONVERSATION_TOKEN_FILE`, `WA070_DELIVERY_TOKEN_FILE`,
`WA070_CONTROL_TOKEN_FILE` e `WA070_EVOLUTION_API_KEY_FILE`. Exige arquivo regular
0600, dono do processo, sem symlink final, leitura limitada. Um newline final
é permitido. Não há descoberta de chaves nem fallback para variável plaintext;
as variantes sem `_FILE` são rejeitadas. Tokens são base64url canônico de 32
bytes, distintos entre si e da apikey. Campos secretos não são enumeráveis no
objeto de configuração. Não exportar esse objeto nem arquivos de credencial.

## n8n e execução local

Usar HTTP Request nativo 4.5 com credential `httpHeaderAuth` para o Bearer de
controle. Desabilitar retry e redirects. O gerador070 usa timeout de controle
de 29s, acima dos 25s do serviço, `responseFormat:'json'` e `neverError:false` em
`options.response.response`. O corpo e os erros desse endpoint já são metadados
fechados; não são os erros da Evolution. O envio continua limitado a 10s no
adapter. A fonte não secreta é o manifest/render dos workflows; não depender de
`$env`, `$vars` licenciado ou Code para ler cofre.

Em cada workflow: `saveDataErrorExecution:'none'`,
`saveDataSuccessExecution:'none'`, `saveManualExecutions:false`,
`saveExecutionProgress:false`, `executionOrder:'v1'` e timeout explícito.
Isso não prova ausência de persistência transitória em queue mode: o
[hook do worker 2.45.4](https://github.com/n8n-io/n8n/blob/e0c02a617f5e6ede3d6c448aff41f632a782aaca/packages/cli/src/execution-lifecycle/execution-lifecycle-hooks.ts#L727-L814)
pode salvar erros antes da limpeza pelo main. O [CLI execute](https://github.com/n8n-io/n8n/blob/e0c02a617f5e6ede3d6c448aff41f632a782aaca/packages/cli/src/commands/execute.ts#L100-L143)
usa modo regular e imprime dados completos; não serve como prova de queue nem
deve receber payloads privados. O isolamento evita pôr esses payloads no n8n.

Testes próprios, sem serviços externos:

```bash
node --test src/whatsapp/worker/config.test.mjs src/whatsapp/worker/http-client.test.mjs src/whatsapp/worker/dispatch.test.mjs src/whatsapp/worker/server.test.mjs
```

Dockerfile tem contexto `integrations/whatsapp`, copia worker/agent e apenas o
adapter TEXT do transporte, usa usuário `node` e não instala pacotes. Imagem
base Node22.23.2 está fixada por digest no Dockerfile. Em31/10/2026 o build
local arm64 passou, com usuário `node`; o container retornou `v22.23.2`.
Isso ainda não comprova integração de runtime dentro do container. Segredos
montados precisam manter uid do processo e modo0600. Evidências de integração,
n8n e limites estão no runbook070 da coordenação. Os testes unitários acima
não comprovam envio real, deploy, aparelhos ou chave de modelo.

## Modelo opcional e limites

WA070_MODEL_MODE=disabled é o padrão, mesmo se existir chave no ambiente.
openai exige exatamente uma fonte: OPENAI_API_KEY (configuração privada local)
ou OPENAI_API_KEY_FILE (mesmas verificações0600/dono/arquivo regular). Sem
segredo descoberto automaticamente; fontes duplicadas ou OPENAI_BASE_URL falham.
A chave não é enumerável no objeto. Nunca passá-la em argumento de comando.

Destino fixo https://api.openai.com/v1/responses, gpt-6-luna low, store:false,
saída600, schema fechado, sem ferramentas/stream/redirect. Pedido completo
até3200bytesUTF8 (instruções/schema incluídos), texto até1200bytes e resposta32KiB.
O teto de entrada é conservador; avaliação real verifica input_tokens<=4000 e
output_tokens<=600. store:false não garante retenção zero no provedor.

O modelo recebe somente texto elegível e enums actorState/registrationStep.
Determinísticos têm prioridade; nome/aceite/convite/pareamento, mensagem antiga,
mídia e pin não dependem dele. Texto com números, indicadores de segredo,
formatos reais de código/token ou URLs/IDN é retido. O filtro inclui falsos
positivos e não promete detectar toda PII/ofuscação.070 só mapeia categorias a
respostas enumeradas; não produz nome, ID, comando de negócio ou texto livre.
UNKNOWN/ambiguidade válidos pedem esclarecimento. Recusa, incompleto, falha,
JSON inválido e timeout pausam a inbox sem comando. AJUDA funciona sem modelo.

Processamento25s com10s reservados para comando/ACK. Prazo do modelo é o menor
entre20s, saldo dessa reserva e lease, normalmente abaixo20s. Uma repetição só
para falha transitória/429/5xx previstos dentro do mesmo prazo; Retry-After
respeitado. Headers/corpo compartilham timeout. Auth, redirect, recusa e saída
inválida não são repetidos. Processador limita também decisão não cooperativa.

Ver avaliação e configuração privada no runbook070 da coordenação. Resultados
anteriores de imagem/integração permanecem históricos, vinculados aos hashes
originais; não comprovam os arquivos acrescentados nesta retomada.

## Lembretes de presença

O W04 chama `POST /v1/tick-attendance` com o mesmo controle privado e corpo
`{}`. O worker usa uma quinta credencial, do principal `SYSTEM_JOB` vinculado
à instância, lida de `WA100_JOB_TOKEN_FILE` com as mesmas exigências de acesso
dos outros arquivos. A ausência dessa credencial deixa a operação indisponível
e permite executar os cards anteriores no laboratório.

Cada acionamento processa até duas páginas de 100 por tipo de job, primeiro
o calendário e depois as perguntas devidas. A chamada retorna `status`,
`calendarProcessed` e `reminderProcessed`. O cursor fica no worker. A API
deduplica cada efeito e continua procurando itens pendentes após reinício.
`ATTENDANCE_REMINDER` segue o mesmo fluxo de reserva, autorização e comprovação
do resultado no W06. O relógio da API e a preferência atual limitam cada envio.

O W05 usa o mesmo `POST /v1/process-inbox` sem corpo de domínio que W02 e W03.
A inbox identifica o motorista; o backend resolve a ocorrência, a pessoa e a
revisão autorizadas. A API gera a autorização de início/retomada e o transportador
revela o link apenas ao preparar a submissão para o motorista certo. O watchdog
e a decisão de confirmar GPS não dependem de uma execução n8n. O W06 transporta
a confirmação da API quando ela ainda é válida. Ver o
[procedimento de ensaio](../../../docs/desenvolvimento/whatsapp.md).


## Reconciliação de entrega — card120

`POST /v1/reconcile-delivery` exige a mesma credencial de controle e corpo `{}`.
Retorna somente `{status,processed}`: IDLE/BUSY/PROCESSED/MORE/UNAVAILABLE e
contagem inteira0..200. Um executor independente chama HEALTH_RECONCILE usando
SYSTEM_JOB, em no máximo duas páginas de100 itens e18s totais. Cursor e minuto
UTC ficam privados no worker; a API mantém idempotência e autorização. Sem
credencial de job, o endpoint responde503 sanitizado.

W08 usa esse POST a cada60s, sem retry do nó. A operação não depende da conexão
Evolution nem da fila de envio; W04 continua com seus jobs de presença e W06
com despacho serial. GET/healthz permanece disponível para healthcheck técnico.
Timeout não deixa uma resposta tardia avançar o cursor ou abrir outra página.
Os exports permanecem inativos até validação do ambiente; testes Node/Python
não comprovam execução n8n/Spring do novo job.
