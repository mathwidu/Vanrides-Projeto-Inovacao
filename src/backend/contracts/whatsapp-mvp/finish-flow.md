# Finalização confirmada no aplicativo

Contrato canônico: `openapi.yaml`, schemas `FinishIntent*`. Implementação local
do fluxo alinhado em 17/11/2026; não é prova de publicação ou aceite físico.

- O bot prepara intenção `FINISH`, vinculada a viagem, aparelho, sessão,
  geração, versão e prazo de cinco minutos. A autorização fica cifrada na
  outbox; o link usa fragmento, nunca query. START/RESUME recusam esse propósito.
- HTTPS `/motorista/fim#v1.<intentId>.<expectedVersion>.<token>` abre o app
  `vanridesdriver://run/finish` após ação explícita na página intermediária.
  Abrir link e consultar resumo não encerra nem inicia coleta.
- `POST /driver-app/v1/finish-intents/{id}/preview`: DEVICE + DPoP;
  corpo `expectedVersion,intentToken`. Mostra viagem e vínculo sanitizados.
- O toque “Finalizar viagem e parar localização” persiste operação local e
  remove o provider imediatamente. Até resolver o primeiro FINISH remoto,
  segurar apenas o envio normal de STOP, sem manter sensor ligado.
- `POST /driver-app/v1/runs/{id}/finish`: DEVICE + DPoP + Idempotency-Key;
  corpo `intentId,expectedVersion,intentToken,foregroundConfirmed=CONFIRMED`.
  Responde `{receipt,control}`. `control` tem o formato já existente, com STOP.
- Persistir a referência de parada daquele pacote e transmitir STOPPED pelo
  caminho existente. Confirmar somente sessão, geração e referência exatas.
- Em falha/offline, liberar STOP cleanup, guardar finalização pendente e não
  reiniciar GPS. Uma única transição da própria parada pode avançar a versão:
  TRACKING→DEGRADED ou STARTING→START_FAILED, versão emitida + 1, sessão atual
  idêntica e parada posterior à emissão. Outros conflitos exigem link novo.
- `POST /driver-app/v1/finish-intents/{id}/receipt`: DPoP sem bearer, mesmo
  corpo do preview. Só relê consumo persistido, mesmo com prazo vencido; não
  renova autorização nem toca viagem posterior. Mantém cercas de chave,
  ambiente/coorte, perfil não apagado e dados retidos.
- `finishConsumedAt` confirma negócio. `stopAcknowledged` confirma ACK exato do
  sensor; são fatos distintos. Se já estava parado na emissão, comparar baseline
  imutável da mesma sessão/geração/referência sem inventar nova parada.
- O app combina recibo atual com provider local desligado antes de retornar ao
  WhatsApp. Resposta antiga, operação substituída, descarte ou saída da tela
  não navegam depois. O destino externo é fixo e não recebe segredo ou GPS.

Preservar o caminho local “Parar localização” sem rede e após revogação. Parar
somente localização não conclui a viagem. Comandos legados de contingência
continuam separados da entrada curta “Quero finalizar a viagem”.

## Recuperação após reconectar o celular

Se o link normal não puder ser emitido e a última sessão da viagem já estiver
`STOPPED`, com `stopped_at` persistido, o bot pode oferecer a revisão na conversa:
“Finalizar esta viagem”. Não é um comando livre de encerramento nem uma parada
presumida. A confirmação recente precisa corresponder à revisão entregue e à
identidade/instância, com as mesmas cercas temporais da conexão guiada.

A revisão captura versão da viagem e ID, geração, referência e data da parada.
A confirmação trava e revalida esses fatos antes do encerramento. START/RESUME,
parada posterior ou revisão vencida não podem ser encerrados por uma resposta
antiga. Essa contingência não transfere a sessão antiga ao aparelho novo,
não inventa ACK e não altera o recibo de parada já persistido.
