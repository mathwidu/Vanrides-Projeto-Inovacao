# Localização e aproximação — card 130

`GET_DRIVER_LOCATION` aceita `{}`. A identidade vem da inbox autenticada. A API
autoriza o motorista dono ou resolve vínculo aprovado do passageiro, run ativo e participação no snapshot; quando há mais
de uma opção, emite menu e revalida a seleção persistida. A consulta nunca aceita
run, destinatário ou coordenadas escolhidos pelo worker/modelo. O resultado do
comando e `context` só contêm referências; a outbox guarda texto e `run_id`.

Em `authorize-submit`, a API consulta novamente a aquisição mais recente do
dispositivo autorizado da geração vigente. Apenas run `TRACKING`, sessão
`ACTIVE/FRESH`, lease/credencial válidos, precisão até 50 m e aquisição com menos
de 20 s liberam `STATIC_PIN`. O formato canônico é `type`, `latitude`,
`longitude`, `capturedAt`, `label`; o label é `Localização atual do veículo`.
`submitBefore` é limitado por aquisição + 20 s, TTL da resposta (300 s), lease
da fila, dispositivo, vínculo, credencial do worker e conexão. Sem posição válida,
fim ou perda de autorização, a resposta é texto de indisponibilidade. Não há
coordenadas persistidas na outbox/recibo, nem pin nativo de live location.

O bot usa o worker determinístico para encaminhar o pin estático à Evolution.
Não enviar o payload autorizado a modelos, logs ou armazenamento de execuções.
Retries precisam de nova autorização. Replay do comando preserva `responseIds`;
duas consultas legítimas têm comandos diferentes e aquisições reavaliadas.

A política `approach-v1` é chamada pela ingestão de novas amostras aceitas, na
mesma transação de progresso/decisão/outbox. Duplicadas e rejeitadas não entram
na janela. Apenas as últimas três fixações da sessão são consideradas. Elas vencem em
`capturedAt +20s`; o watchdog da API (ciclo de5s) purga as vencidas mesmo sem
novas amostras e com o bot parado.
A evidência de chegada permanece durante parada com aquisições contínuas da mesma sessão; gap superior a20s, sessão diferente ou trajetória ambígua invalidam essa continuidade. O registro de progresso retém distância mínima, razão, sequência e contagem de
supressões; não retém coordenadas. O reset de lote apaga os novos registros.

Hipóteses de calibração: raio de aviso 800 m, precisão até 50 m, três aquisições
com idade até 20 s, movimento maior que precisão/10 m, velocidade inferida até
45 m/s, curva entre segmentos até 30 graus e direção compatível com heading
quando presente (45 graus). A tendência deve diminuir e o último segmento
apontar ao ponto (30 graus). Isso não fornece ETA individual nem prova embarque.

Somente o próximo ponto físico é elegível. Pontos compartilhados avançam juntos,
com uma outbox por passageiro. Após aquisição até 80 m e três fixações de
afastamento consistente, histerese de 120 m permite `PASSED`; passagem próxima
até 200 m com a mesma evidência permite `SKIPPED`. Sem evidência, a parada fica
pendente. Estados terminais nunca voltam para `WAITING/APPROACHING`; cancelamento
preserva ordem e snapshot. A chave única continua run/stop/recipient e independe
de `policy_version`. Outbox de aproximação vence em 60 s; autorização reavalia
trajetória, GPS, run, vínculo, perfil, cancelamento e opt-out.

As flags são privadas do ambiente e desligadas por padrão:

```text
WA_APPROACH_ENABLED=false
WA_APPROACH_REVIEWED_ROUTINE_IDS=
```

Para um ensaio sintético/local, habilitar a flag e informar UUIDs das rotinas
explicitamente revisadas, separados por vírgula. Uma rotina fora da lista
suprime o aviso e conta a oportunidade perdida. Essa lista é evidência externa
de contexto do percurso, não um detector de rua paralela/viaduto. GPS sozinho
não distingue pisos. Não habilitar uma rotina com geografia ambígua; exige
evidência de corredor/rota e nova calibração. A lista não dá aceite físico ao MVP.

Verificações obrigatórias: `WaDriverLocationIntegrationTest` (T43),
`WaApproachIntegrationTest` e `WaApproachPolicyTest` (T19–22), contratos canônicos,
gates dos consumidores e smoke do mesmo artefato. A matriz PostgreSQL exige os
testes novos sem skips. Percurso, antecedência/cobertura e WhatsApp real continuam
pendências de ensaio; testes sintéticos não demonstram utilidade física.
