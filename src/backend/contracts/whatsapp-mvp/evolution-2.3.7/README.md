# Fixtures sintéticas do adapter Evolution

Perfil: Evolution **2.3.7**, fonte oficial fixada em
[`cd800f2976e1e5b682fbf86a01ee4d85ae61f370`](https://github.com/EvolutionAPI/evolution-api/tree/cd800f2976e1e5b682fbf86a01ee4d85ae61f370).
Baileys observado: **7.0.0-rc.9**. Os exemplos abaixo são construídos para o
contrato local; não são captura de sessão, contato, telefone ou localização real.
Os aliases compostos exclusivamente por zeros são artificiais e não endereçáveis.
O pin `(0, 0)` é um caso de borda numérico, não um percurso ou posição observada.

Fontes primárias:

- [Envelope e retry do webhook](https://github.com/EvolutionAPI/evolution-api/blob/cd800f2976e1e5b682fbf86a01ee4d85ae61f370/src/api/integrations/event/webhook/webhook.controller.ts).
- [prepareMessage, messages.upsert, messages.update e connectionUpdate](https://github.com/EvolutionAPI/evolution-api/blob/cd800f2976e1e5b682fbf86a01ee4d85ae61f370/src/api/integrations/channel/whatsapp/whatsapp.baileys.service.ts).
- [locationMessage/liveLocationMessage](https://github.com/WhiskeySockets/Baileys/blob/v7.0.0-rc.9/WAProto/WAProto.proto).

`message-extended-reply.json` também cobre o formato extendedTextMessage antes
da conversão de `prepareMessage`; no webhook2.3.7, `conversation` e
`data.contextInfo` são os campos já convertidos. O normalizador aceita ambos
sem copiar `quotedMessage`.

`WaEvolutionNormalizerTest` verifica os exemplos e os descartes. A suíte
`WaEvolutionIngressIntegrationTest` cobre persistência PostgreSQL, dedup,
coorte, alias e rollback. O gate do card deve executar essas classes; a
presença das fixtures não comprova que testes, sessão real ou entrega rodaram.
