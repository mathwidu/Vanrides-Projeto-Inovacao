# Adapter local Evolution TEXT

`evolution-text.mjs` exporta `submitEvolutionText({ baseUrl, instance, apiKey,
reservation, fetchImpl = fetch, now = () => Date.now(), readConnection, deadline, signal })`. O módulo não lê
variáveis de ambiente, não executa CLI e não registra requisições ou erros.
Node 22.23.2 foi usado na validação local; não há dependências de pacote.

A entrada `reservation` é a resposta de `outbox/{id}/authorize-submit` da API:
`outboxId`, `attemptToken`, `submitBefore`, `recipient`, `content` e `serverTime`.
Somente `content.type=TEXT`, com 1–2000 caracteres Unicode, é suportado nesta
fatia. `recipient.addressType=JID|LID` deve corresponder exatamente ao sufixo
`@s.whatsapp.net|@lid`, precedido por 1–32 dígitos. O endereço vem da API;
nenhum destinatário é selecionado pelo modelo e nenhum PN é inferido de LID.

O POST usa `/message/sendText/{instance}`, header `apikey` e somente
`{number: address, text: content.text, linkPreview: false}`. A configuração
aceita HTTPS ou HTTP apenas para `127.0.0.1`, `localhost` e `[::1]`, sem
credenciais na URL, query, fragment ou path além da raiz. A instância aceita
letras ASCII, números, `_` e `-` (até 128 caracteres). O prazo usa o relógio
local injetável em milissegundos; o chamador precisa manter esse relógio
sincronizado com a API. `serverTime` e `referenceTime` não prorrogam o prazo.

O resultado é o corpo para `outbox/{id}/result`:

- `SUBMITTED` inclui `attemptToken` e `externalMessageId` opaco (1–160
  caracteres ASCII alfanuméricos, `_` ou `-`). Exige HTTP 201 e `key.id` válido.
- `FAILED/REJECTED` cobre configuração/reserva/prazo inválidos antes do POST,
  ou rejeição HTTP 401/403 (guard de autenticação) e 404 (rota).
- `NOT_SUBMITTED/CHANNEL_UNAVAILABLE` comprova que nenhum POST foi invocado:
  conexão indisponível, probe inválido/expirado ou cancelamento antes da submissão.
- `UNKNOWN/TIMEOUT` cobre timeout após invocar o POST; `UNKNOWN/CHANNEL_UNAVAILABLE`
  cobre falha de rede, redirect, HTTP 400/422, outros status ou resposta inválida/incompleta
  depois da invocação. Até uma exceção síncrona do fetch já é incerta.

Na Evolution 2.3.7, `sendMessageWithTyping` envolve `sendMessage` e gravações
posteriores no mesmo `try/catch`; o `catch` lança
`BadRequestException(error.toString())`. Assim, HTTP 400 pode ocorrer **depois**
do envio. A base é o [serviço Baileys, linhas 2289–2557, no commit fixado
`cd800f2976e1e5b682fbf86a01ee4d85ae61f370`](https://github.com/EvolutionAPI/evolution-api/blob/cd800f2976e1e5b682fbf86a01ee4d85ae61f370/src/api/integrations/channel/whatsapp/whatsapp.baileys.service.ts#L2289-L2557).
HTTP 400 e 422 não comprovam ausência de efeito e permanecem UNKNOWN, sem retry.

Se nem o `attemptToken` puder ser validado, o retorno fixo não contém token:
`{outcome:'FAILED', failureCode:'REJECTED'}`. O chamador deve tratar isso como
falha local e **não postar** esse objeto na API. Nenhuma exceção original ou
campo privado adicional entra no resultado.

Após validar a reserva, há um GET connectionState e no máximo um POST, sem
redirect ou retry no adapter. A leitura inclui limite de 2s e faz parte do orçamento
total de 10s. O timeout inclui headers e corpo e respeita submitBefore e o deadline
menor do chamador. Cancelamento fecha definitivamente o caminho até o POST,
mesmo se a consulta ignorar AbortSignal e retornar OPEN mais tarde. A resposta é
lida por stream até 32KiB, cancelando a leitura excedente. A aceitação 201 não
prova entrega. Um resultado UNKNOWN exige reconciliação; não autoriza reenvio.

Perfil e fontes fixadas: [runbook050](../../../docs/runbooks/whatsapp-mvp/050-transporte-local.md).
Testes usam apenas aliases artificiais começando por zero e HTTP loopback:

```bash
node --test src/whatsapp/transport/evolution-text.test.mjs
```

Este adapter prepara o smoke050 e o contrato de envio futuro de W06. Não é um
export n8n, não configura uma sessão real e não comprova entrega WhatsApp.


## Consulta de conexão — primeiro incremento do120

`evolution-connection.mjs` fornece `readEvolutionConnection({baseUrl, instance,
apiKey, fetchImpl, now, deadline, signal})`. Usa somente
`GET /instance/connectionState/{instance}`, header apikey, sem connect, retry,
redirect ou envio. Fonte do contrato: [controller Evolution2.3.7 fixado](https://github.com/evolution-foundation/evolution-api/blob/cd800f2976e1e5b682fbf86a01ee4d85ae61f370/src/api/controllers/instance.controller.ts).
Resposta exige exatamente `{instance:{instanceName,state}}`, com a instância
configurada. `open` produz OPEN; `close/connecting/refused`, CLOSED. Qualquer
incerteza, identidade diferente, body inválido, cancelamento ou prazo vencido
produz somente UNKNOWN. Nenhum dado do provider entra no retorno.

Prazo máximo de 2 s, incluindo corpo de até 16 KiB; pode ser reduzido pelo deadline.
HTTP exige loopback escrito explicitamente; demais origens usam HTTPS. O worker
faz essa leitura antes de claim, mantendo um único despachante e 25 s totais.
Uma observação OPEN não é prova de entrega nem garantia de disponibilidade no
POST posterior. O adapter consulta novamente depois de authorize-submit e antes
do POST. Somente NOT_SUBMITTED permite ao backend considerar outra tentativa,
com nova lease/token, limite de três totais e intervalos de 5s/15s dentro do TTL.
FAILED e UNKNOWN antigos nunca são reclassificados para permitir repetição.

O provider de smoke reconhece a consulta GET autenticada e mantém seu contador
separado das submissões: `connectionReadCount()`. Os recibos existentes continuam
contando tentativas de mensagem; consultas não são mensagens. O freeze de
fontes inclui o novo módulo. Pacotes históricos do110 continuam pinados aos
fontes originais, sem reescrever suas provas.
