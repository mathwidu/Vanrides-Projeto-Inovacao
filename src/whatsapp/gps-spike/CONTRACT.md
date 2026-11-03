# GPS spike v1 — contrato congelado em 30/10/2026

VR-WA-020: laboratório isolado, sem usuários reais, n8n, Evolution ou cadastro legado.
Produtor e consumidores implementam este contrato. Não comprova pareamento DEVICE_KEY.

## Transporte e identidade

Receptor dedicado Python/SQLite, uma instância atrás de HTTPS; nunca incorporado ao
artefato Spring. CLI privada cria testRunId, alias sintético, token aleatório 256 bits
(validez máxima 4 h) e startIntentId (5 min). Banco guarda somente hash do token.
Token inserido manualmente na tela de ensaio, aberto pelo ícone; nunca URL, log,
argumento CLI, dart-define ou fonte. Todos os endpoints abaixo exigem Bearer.
Rejeitar redirects. Timeout por requisição 8 s, uma em voo; callbacks atrasados não
restauram sessão encerrada. Credencial nativa: Keychain AfterFirstUnlockThisDeviceOnly
ou Keystore AES-GCM + arquivo privado excluído de backup. Sem autenticação biométrica
por uso. Token só pode ser substituído quando coletor parado.

Host HTTPS de ensaio permitido: gps-spike.vanrides.matheusduarte.dev.br, porta 443,
sem userinfo/query/fragment/path de base. DNS/publicação ainda NÃO executados.
Somente debug em simulador/emulador admite HTTP 127.0.0.1:18765 (iOS) ou
10.0.2.2:18765 (Android); builds físicos/release recusam HTTP. Não desabilitar
validação de certificado. Erros não carregam URI completa, coordenadas ou segredos.

Todos os instantes no JSON são inteiros Unix em milissegundos. IDs opacos ASCII
[A-Za-z0-9_-], até 80 caracteres. schemaVersion=1. Campos desconhecidos rejeitados
no receptor. Finite numbers: latitude [-90,90], longitude [-180,180], accuracyM [0,10000],
speedMps [0,150] opcional, headingDeg [0,360) opcional. sequence inteiro >=1.

## HTTP

- GET /driver-app/v1/status: `{schemaVersion, environment:"gps-spike", serverTime,
  credentialExpiresAt, session:null|{sessionId,generation,state,leaseExpiresAt,gpsState}}`.
  Não inicia/reinicia coleta. Estado remoto reconciliado antes de iniciar.
  session.state: RUNNING|STOPPED|EXPIRED|REVOKED; generation inteiro >=1.
- POST /driver-app/v1/start-intents/{id}/activate, corpo `{schemaVersion:1}`:
  ativa idempotentemente o intent próprio; mesma intenção retorna mesma sessão/geração,
  e sempre CONTROLE ATUAL. Intent de outro aparelho é inacessível. Nenhum endpoint
  público de provisionamento. Uma sessão ativa por identidade. Expirada/parada não reinicia.
- POST /driver-app/v1/tracking-sessions/{id}/samples, corpo
  `{schemaVersion:1,sessionId,generation,batchId,deviceTime,samples:[
  {sequence,capturedAt,latitude,longitude,accuracyM,speedMps?,headingDeg?}]}`.
  1–30 amostras, <=32768 bytes UTF-8 no corpo inteiro. ACK individual:
  `{...controle,accepted:[sequence],duplicate:[sequence],rejected:[{sequence,code}]}`.
  Recibo SQLite transacional (WAL/FULL) antes de responder. Chave sessão/geração/sequence;
  hash canônico da amostra e do lote; mesmo ID+conteúdo=duplicate, diferente=409.
  Não apagar lacunas não explicitamente confirmadas/rejeitadas. Mesmo batchId com corpo
  diferente é conflito, inclusive deviceTime. Retry conserva corpo exato enquanto válido.
- POST /driver-app/v1/tracking-sessions/{id}/state, corpo
  `{schemaVersion:1,generation,state:"RUNNING"|"STOPPED",reason?,stopRequestedAt?,stoppedAt?}`.
  Heartbeat a cada 30 s; STOPPED é separado de silêncio. Credencial válida pode confirmar
  parada após lease expirar. Revogada só recebe STOP/403; receptor não inventa ACK de sensor.

Controle atual: `{schemaVersion:1,environment:"gps-spike",sessionId,generation,
serverTime,leaseExpiresAt,action:"CONTINUE"|"STOP",gpsState:"FRESH"|"STALE"|"UNKNOWN"}`.
Erros `{code}` e HTTP 400/401/403/404/409/410/413/429/503. 401/403/404/409/410 e
contrato inválido provocam parada local fail-closed; timeout/429/5xx só retry dentro do
lease. Content-Type application/json. Sem mensagens/tracebacks do framework no payload.

## Lease e ciclo de vida

Lease máximo 300000 ms, limitado pela validade da credencial e janela do ensaio.
Ativar/estado/amostras autenticados podem renovar apenas sessão ainda autorizada,
antes do vencimento; lease vencido nunca ressuscita. Guardar deadline monotônico que
inclui repouso, calculado conservadoramente a partir de serverTime/leaseExpiresAt e
início monotônico da requisição. Resposta replay não estende o mesmo leaseExpiresAt.
Timer nativo + checagem em todo callback/envio/resume. Android usa ainda
LocationRequest.Builder.setDurationMillis(remainingLease). iOS CoreLocation com
allowsBackgroundLocationUpdates=true, indicador visível, pausa automática desabilitada.
URLSession ephemeral/default; nunca background transfer para GPS.

Parar: invalidar geração local de callbacks, parar a própria solicitação do provedor,
cancelar transmissão de amostras, limpar fila, registrar stopRequestedAt/stoppedAt,
e só então enviar STOPPED. Repetir stop é idempotente. Não reiniciar após reboot,
force-stop ou recriação do processo: limpar fila anterior, reconciliar online e aguardar
novo início explícito. Estado remoto não afirma que o sensor parou sem ACK.

Fila em memória: <=120 amostras e idade útil <=120000 ms; purga antes de acesso/envio,
contabiliza descartes. Não persistir GPS no aparelho neste spike; processo encerrado
perde o buffer e exige reconciliação online. Essa decisão reduz retenção e não promete
recuperação pós-force-stop. Diagnóstico persistido só com estados/tempos/contadores,
sem coordenadas/IDs privados/token; preservar prova da chamada nativa de parada.
Coleta desejada a cada 5 s, distância mínima zero no ensaio para medir cenário parado.

## Frescor e sink

Recibos duráveis não guardam GPS bruto. Última posição/janela só em RAM, descartada
na parada; reinício do receptor degrada e exige fixação nova. Future>10s, age>20s,
accuracy>50m ou capturedAt não crescente não alimentam frescor/decisão. São recibos
válidos para transporte, sem efeito operacional. Watchdog próprio a cada 5 s degrada
quando age>20s, independentemente de heartbeat/n8n. Sink é somente evento sintético
persistido com ID/instante/tipo; sem WhatsApp. Os parâmetros de aproximação v1 do plano
08 são hipótese; não afirmar cobertura antes de 030. Fixtures geográficas são sintéticas.

## Configuração privada da rota de ensaio

Sem alteração dos payloads HTTP: o receptor carrega uma rota global imutável
na inicialização, a partir de `/data/route-v1.json`. Ausente significa hipótese
de aproximação desabilitada, mantendo ingestão/frescor/lease/STOP. Presente
inválido impede startup com erro sanitizado. Nunca usar rota sintética como
fallback do serviço real; somente testes a injetam explicitamente.

Arquivo regular, não symlink, 0600, dono igual ao usuário do processo, até
16384 bytes. JSON UTF-8 estrito `{schemaVersion:1,routeId,stops:[
{id,latitude,longitude,final}]}`; IDs opacos ASCII `[A-Za-z0-9_-]` de 1–80,
1–30 paradas com IDs e coordenadas distintos, números finitos nas faixas
latitude [-90,90]/longitude [-180,180], `final` booleano obrigatório e verdadeiro
exatamente na última parada. Campos extras/duplicados são inválidos.

`route-check --file ARQUIVO_PRIVADO` valida apenas o arquivo escolhido, sem
banco ou saída de coordenadas; retorna ID sanitizado e contagem. Não usa env
de GPS ou endpoint administrativo. O `serve-dev` procura esse mesmo nome ao lado
do SQLite privado. Não há reload: parar coletores/encerrar sessões antes de
trocar arquivo e reiniciar, seguindo de novo início explícito; nunca durante
RUNNING. A verificação de arquivo não comprova o snapshot de processo já iniciado.

Pontos fixos configurados intencionalmente são persistidos nesse arquivo privado;
amostras GPS recebidas permanecem em RAM. Rota/coordenadas não entram no SQLite,
logs, status ou evidência. Backup SQLite não inclui rota: após restore, exige
reprovisionamento/validação separados antes do ensaio físico.

## Bridge Flutter/nativo

MethodChannel `vanrides.driver/tracking.v1`:
- `configureCredential` recebe `{kind:"TEST_BEARER",baseUrl,token}`; valida/salva e
  consulta status online, retorna snapshot. Nunca retorna token. Sem coleta implícita.
- `requestPermission` sem args: solicita quando visível; retorna snapshot. Precisa
  localização precisa; Android notificação/FGS conforme versão. iOS progressivo.
- `start` recebe `{intentId}`: só UI visível e permissão válida, ativa online e
  começa nativo após resposta válida. Sem link de autoinício neste laboratório.
- `stop` sem args: imediatamente para localmente, notifica servidor se possível.
- `getStatus` sem args: somente snapshot local, sem GPS/credencial.
- `syncStatus` sem args: reconcilia online; nunca inicia coleta.

Snapshot: `{state:"UNCONFIGURED"|"READY"|"STARTING"|"TRACKING"|"STOPPED"|"ERROR",
credentialConfigured:bool,permission:"UNKNOWN"|"DENIED"|"APPROXIMATE"|"GRANTED",
queued:int,accepted:int,duplicates:int,rejected:int,dropped:int,
gpsState:"UNKNOWN"|"FRESH"|"STALE",leaseExpiresAt:int|null,reason:string|null,
stopRequestedAt:int|null,stoppedAt:int|null}`. Campos extras só sanitizados/contadores.
Falhas de método usam códigos estáveis; texto seguro local mapeado pela UI, nunca
exception.description ou stack. UI pode consultar snapshot a cada 1 s enquanto visível;
parar observação/destruir widget não controla serviço. Botão Parar sempre disponível.

## Verificação e limites

Testes: perda de ACK/replay após restart; mesmo ID com outro corpo; geração/dono;
revogação/fim durante request; lease antigo; lote/bytes/fila; sem fresco por heartbeat;
parada/expiração e callback tardio. Simulador/emulador com HTTP restrito testa integração,
não tela bloqueada física, bateria ou entrega WhatsApp. iOS offline parado bloqueado
por >5min deve registrar chamada de stop ainda bloqueado; se só ocorrer ao desbloquear,
reprova requisito atual. Não confundir fim da nossa solicitação com GNSS físico desligado.

Fontes técnicas revistas: Apple CoreLocation background updates/pauses; Apple limites
de execução e URLSession; Google LocationRequest.Builder.setDurationMillis; Android
FGS/Keystore; SQLite pragma synchronous. Parecer independente Astra e fontes atuais
sustentam arquitetura, não substituem prova física.
