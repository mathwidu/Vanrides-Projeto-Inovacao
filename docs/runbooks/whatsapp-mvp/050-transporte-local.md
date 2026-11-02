> Registro técnico anterior à centralização. Caminhos pessoais e endereços operacionais foram omitidos. Para executar a versão atual, use os guias em `docs/desenvolvimento/`; este histórico não autoriza deploy nem comprova a nova revisão.

# Transporte WhatsApp — perfil observado e implementação local050

Em31/10/2026, inspeção somente leitura da VPSVPS_DE_TESTE confirmou:

| Componente | Configuração observada |
|---|---|
| Evolution | `evoapicloud/evolution-api:v2.3.7@sha256:1bd8afc4a6cf48822e6cf02469aeae7bd35a12a6b616eacd1291926307f4d339`,1/1 |
| Pacotes dentro da Evolution | Evolution2.3.7; Baileys7.0.0-rc.9 |
| n8n | `docker.n8n.io/n8nio/n8n:2.45.4`, editor/mcp_api/webhook/worker1/1 |

Não foram consultados segredos, contatos, QR ou configuração de sessão lógica.
Versão instalada e fonte da tag não comprovam build reproduzível nem entrega
real. O número de teste autorizado pelo usuário ainda precisa de configuração
isolada, revisão e publicação específica. A autorização da stack GPS anterior
não é uma autorização para publicar este novo backend ou alterar webhooks.

## Contrato do provider

Fonte oficial Evolution tag2.3.7 resolvida no commit
`cd800f2976e1e5b682fbf86a01ee4d85ae61f370`:

- [Webhook e retries](https://github.com/EvolutionAPI/evolution-api/blob/cd800f2976e1e5b682fbf86a01ee4d85ae61f370/src/api/integrations/event/webhook/webhook.controller.ts): inscrição em UPPER_UNDERSCORE, evento no corpo em lower.case; `event/instance/data/date_time`. Envelope também pode trazer `apikey`, `sender`, `destination`, `server_url`; descartar estes campos. `sender` é o bot, não o ator humano. Configuração futura: webhook local da instância, headers próprios, byEvents=false/base64=false. Credencial CHANNEL é distinta da apikey Evolution. Conferir LOG.LEVEL antes de integrar: WEBHOOKS pode registrar o envelope com chave.
- [Tipos de evento](https://github.com/EvolutionAPI/evolution-api/blob/cd800f2976e1e5b682fbf86a01ee4d85ae61f370/src/api/types/wa.types.ts): `messages.upsert`, `messages.update`, `connection.update`, `messages.edited`, `send.message` são eventos distintos.
- [Canal Baileys](https://github.com/EvolutionAPI/evolution-api/blob/cd800f2976e1e5b682fbf86a01ee4d85ae61f370/src/api/integrations/channel/whatsapp/whatsapp.baileys.service.ts): upsert por mensagem, key com id/remoteJid/fromMe e possíveis aliases Alt; extendedTextMessage é convertido para conversation. Status usa objeto `keyId/remoteJid/fromMe/status`, status textual, não número Baileys. Conexão traz `data.state` diretamente, sem objeto stateConnection. Guardar apenas estado e tempo para dedup; descartar perfil/wuid. Edição/eco/grupo/broadcast não são nova intenção humana.
- [sendText](https://github.com/EvolutionAPI/evolution-api/blob/cd800f2976e1e5b682fbf86a01ee4d85ae61f370/src/api/routes/sendMessage.router.ts): POST `/message/sendText/:instanceName`, header apikey, JSON `number/text`, resposta201 com `key.id`; aceitação HTTP não prova entrega. `createJid` preserva JID/LID; whatsappNumber considera LID válido mesmo sem cache. Isso permite encaminhar LID intacto; nunca fabricar um telefone a partir dele. Capacidade em fonte ainda exige ensaio na sessão real.
- [Baileys7.0.0-rc.9 WAProto](https://github.com/WhiskeySockets/Baileys/blob/v7.0.0-rc.9/WAProto/WAProto.proto): `locationMessage` estático e `liveLocationMessage` são tipos distintos. Rejeitar liveLocationMessage e locationMessage.isLive=true como pin. Aceitar latitude/longitude finitas nos limites; zero é válido. Não extrair coordenadas de quotedMessage, texto ou LLM.

O normalizador aceitará extensões do provider, mas salvará somente campos
explicitamente permitidos. Mensagens inválidas/ignoradas recebem recibo durável
sanitizado; falha antes de commit não pode produzir202. Dedup separa mensagem,
status e conexão, de modo que DELIVERED/READ não colidam com a mensagem original.

## Limites e validação

Esta etapa constrói filas e envio básico de respostas fixas;070 compõe os
workflows n8n e a conversa.120 amplia política de recuperação,160 valida a
integração real. Ficar UNKNOWN é conservador após possível envio; nunca inferir
que timeout significa que a mensagem não saiu. Não reenviar cegamente.

Executar normalização/HTTP/contrato, PostgreSQL com concorrência, gate canônico
e smoke do mesmo artefato contra um provider falso local. Registrar resultados
somente após execução. Fixture sintética não comprova sessão WhatsApp real,
iPhone/Redmi, navegação, percurso ou bateria. Nenhuma prova física é executada
por este runbook; o iPhone físico continua pausado por solicitação do usuário.

## Escolhas de implementação

- Ordenação da inbox por sequência `received_order` do banco, com insert
  serializado por identidade: evita desempate acidental de timestamps iguais
  por UUID. RETRY/PAUSED não permitem ultrapassagem na mesma conversa.
- Alias da resposta é o remetente exato do evento (`senderAliasId` interno),
  preservando LID e evitando preferir um PN antigo. Associação ambígua não faz
  merge automático. O trabalhador de conversa não recebe esse alias.
- RESPONSE/TEXT e policy foundation-v1 são as únicas saídas liberadas050.
  Resposta e recibo do comando são atômicos; os outros propósitos aguardam120.
- FAILED prevalece sobre SERVER_ACK; DELIVERY_ACK/READ são evidências mais
  fortes. Dessa forma, a ordem de callbacks não apaga uma falha por um ACK
  antigo. Recibo antecipado espera o ID externo real para correlacionar.
- Credencial e lease são revalidados depois de locks de perfil/alias. A revisão
  encontrou um caminho que cancelava após expiração e motivou testes com
  espera real no PostgreSQL; correção validada na rodada focada19/19.
- Integração HTTP usa a advice de erros fechados também no controller novo.
  Teste com falha SQL confirmou503, ausência de202 e de detalhes privados.
- O adapter local é um módulo executável e testado; exports n8n serão produzidos
  em070. Testar o módulo com servidor falso não equivale a executá-lo no n8n.

Fechamento local: gate canônico1227/1227, incluindo182 PostgreSQL, e smoke8/8
do mesmo JAR/SHA passaram. Adapter Node24/24 e preflight negativo25/25 também
passaram. Hashes, revisão, tentativas não aprovadas e limites estão na
[evidência050](../../../program/evidence/VR-WA-050-WHATSAPP-TRANSPORT.md).
Não publicar esse resultado como PASS de piloto ou de funcionalidade física.

A fonte da Evolution mostra envio e operações posteriores dentro do mesmo try/catch;
uma falha posterior é convertida em HTTP400. Por isso400/422 recebemUNKNOWN no
adapter, com teste que registra o efeito antes de devolver400. Não tratar esse
HTTP como prova de que nada foi enviado.
