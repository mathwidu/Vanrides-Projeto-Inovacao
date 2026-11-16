# Operação e pausa segura do MVP

Este guia cobre somente a homologação VanRides `vanrides-mvp-hml`, instância
`vanrides-hml`. O mantenedor do ambiente coordena a operação; nesta entrega,
Matheus Duarte prepara e executa as etapas autorizadas. Outro integrante só
assume depois de combinar o acesso e conferir o alvo e a versão. Isso não
oferece suporte comercial nem autoriza alterações em outros projetos da VPS.

## Diagnóstico e alerta ao operador

O [helper](../../deploy/homologacao/operations.py) executa uma conferência local,
sob o lock de publicação existente. Não instala cron, abre portas, envia
alertas a terceiros, muda configuração ou executa uma pausa. Só funciona no
host/usuário previstos pelo preflight atual. Não rodar no Mac nem apontar
`DOCKER_HOST` para outro daemon.

O operador prepara fora do Git um manifesto com estas cinco entradas e sua
versão de formato. A imagem deve ser o digest efetivo conferido da API; o
placeholder abaixo precisa ser substituído antes do uso:

```json
{
  "schemaVersion": 1,
  "environment": "vanrides-mvp-hml",
  "instance": "vanrides-hml",
  "apiImage": "ghcr.io/mathwidu/vanrides-projeto-inovacao-team-api@sha256:DIGEST_REVISADO",
  "schema": 44
}
```

Guardar manifesto e saídas em diretório privado `0700`; arquivos privados são
`0600`. Conferir seu SHA-256 por uma referência revisada e usar uma saída nova
a cada execução. As variáveis abaixo representam caminhos e hash, nunca tokens:

```sh
python3 deploy/homologacao/operations.py diagnose \
  --manifest "$OPS_MANIFEST" --sha256 "$OPS_MANIFEST_SHA256" \
  --output "$OPS_NEW_PRIVATE_OUTPUT"
```

O resultado JSON no terminal traz `TECHNICAL_CHECKS_OK` ou `ATTENTION`, contagens
agregadas, códigos fechados e uma ação para cada alerta. Código de saída `2`
exige atenção; `BLOCKED` significa alvo, dados ou prazo não comprovados. Mesmo
uma pausa intencional gera o alerta de credencial de envio ausente. Essa saída
é o alerta local ao operador; não há promessa de vigilância contínua quando
ninguém executar a conferência.

O helper verifica somente `vanrides-hml-api_api`, `vanrides-hml-api_db` e
`vanrides-hml-canal_worker`: imagem imutável, namespace, serviço e tarefa única.
Lê a saúde da API pela rede privada do banco e a do worker por loopback. O SQL
é fixo, em transação somente leitura, sem argumentos SQL livres; rejeita banco
com coortes ou credenciais de outro ambiente. Não consulta n8n compartilhado,
conteúdo de mensagens, GPS, destinatários, tokens, `Env`, `Args` ou `Command`.
Os arquivos de diagnóstico ficam privados. Uma exceção não imprime a saída
bruta do subprocesso.

| Alerta | Ação |
| --- | --- |
| Alvo ou imagem mudou; API/worker sem saúde | Conferir a publicação e a tarefa esperadas antes de qualquer ação. Não reiniciar stack compartilhada. |
| Credencial DELIVERY ausente ou múltipla | Distinguir pausa, vencimento e rotação incompleta. Conferir todos os grants da instância antes de retomar. |
| CHANNEL, CONVERSATION ou SYSTEM_JOB ausente | Restaurar o papel afetado; preservar os outros. W08 precisa de SYSTEM_JOB para reconciliar. |
| Credencial vence em sete dias | Renovar e conferir o secret do papel correto antes do vencimento. |
| Tentativa em SUBMITTING | Ela pode terminar mesmo depois da pausa. Aguardar até 25 s e reconciliar; não repetir POST. |
| SUBMITTING vencido, UNKNOWN ou caso de entrega aberto | Conferir W08 e o caso pelo operador privado. Não reabrir outbox nem inventar recibo. |
| Inbox atrasado mais de 120 s | Conferir W01/worker e a pendência durável. |
| STOP sem ACK há mais de 60 s | Orientar parada local e restabelecer conexão. Não registrar parada confirmada sem ACK do aparelho. |

Os limiares de 120 s, 60 s e sete dias são valores iniciais para orientar essa
conferência, não um SLA. A consulta não prova disparo de agenda, recebimento no
WhatsApp ou STOP físico; esses campos permanecem explicitamente falsos no
recibo, mesmo com saúde UP. Não mede continuidade ou bateria do aparelho.

## Preparar a pausa de novos envios

O controle existente é a revogação **somente de DELIVERY_WORKER** pelo operador
privado. A revogação é permanente para aquela credencial. Retomar exige criar
e instalar outra, sem reativar a antiga. `WA_NEW_STARTS_ENABLED=false` pausa
novos trajetos, não os envios. Desligar `WA_MVP_ENABLED` não é receita válida
para esta homologação: o guard do perfil publicado exige esse valor ligado.

Antes da operação, conferir o alvo live, imagem/JAR/schema efetivos, recibo
privado de backup e retorno ensaiado da mesma versão. Preservar o acesso ao
cofre e a possibilidade de provisionar e instalar uma credencial substituta.
O helper não certifica o backup por um checkbox ou um recibo genérico PASS;
essa conferência é responsabilidade do operador antes da mutação.

Preparar um request privado no formato aceito pelo
[operador existente](../../deploy/homologacao/canal/OPERADOR.md). Os UUIDs são
metadados técnicos obtidos na conferência privada, não telefones ou tokens:

```json
{
  "operation": "revoke-service-credential",
  "environment": "vanrides-mvp-hml",
  "batchId": "UUID_DA_COORTE_TECNICA_SINTETICA",
  "credentialId": "UUID_DA_CREDENCIAL_DELIVERY",
  "reasonCode": "OPERATIONAL_PAUSE"
}
```

Não acrescentar `operatorCredential`: o runner existente injeta o secret no
contêiner privado. Usar o lote técnico sintético da equipe, não o lote de
participantes reais. O plano verifica no banco que o alvo pertence a
DELIVERY_WORKER, ambiente/instância corretos e que há exatamente um grant
ativo por papel. Um grant duplicado bloqueia o plano, pois revogar apenas um
não comprovaria a pausa de todos os emissores.

```sh
python3 deploy/homologacao/operations.py pause-plan \
  --manifest "$OPS_MANIFEST" --sha256 "$OPS_MANIFEST_SHA256" \
  --request "$PRIVATE_PAUSE_REQUEST" --output "$OPS_NEW_PRIVATE_OUTPUT"
```

`PAUSE_PLAN_READY_NOT_EXECUTED` é um plano, não uma pausa confirmada. O helper
não imprime IDs do request nem executa o operador. Conferir a versão atual dos
manifests online e do JAR; não reutilizar os hashes históricos do guia. Executar
uma única solicitação pelo runner existente:

```sh
python3 deploy/homologacao/canal/operador.py run --execute-local-vps \
  --online-manifest "$ONLINE_MANIFEST" --online-manifest-sha256 "$ONLINE_MANIFEST_SHA256" \
  --operator-manifest "$OPERATOR_MANIFEST" --operator-manifest-sha256 "$OPERATOR_MANIFEST_SHA256" \
  --jar "$OPERATOR_JAR" --request "$PRIVATE_PAUSE_REQUEST" --result "$PRIVATE_PAUSE_RESULT" \
  --job-directory "$NEW_PRIVATE_OPERATOR_JOB" --timeout-seconds 180
```

Só o resultado confirmado do operador prova o commit da revogação. Timeout ou
resposta perdida exigem `operador.py recover` para o mesmo job, sem reaplicar
automaticamente. Preservar CHANNEL, CONVERSATION_WORKER, SYSTEM_JOB, aparelho,
API, banco, ingresso e W01/W08. Não escalar o worker a zero nem parar n8n/Redis.
As respostas de WhatsApp ficam retidas enquanto não há DELIVERY autorizado;
o comando de finalização pode continuar sendo processado, embora sua resposta
não seja enviada durante a pausa. STOP/ACK do app usa sua própria credencial.

Revogar bloqueia novas autorizações depois do commit, mas não desfaz uma
reserva já autorizada ou um POST em voo. O worker tem orçamento de 25 s,
submissão de até 10 s; conferir a estabilização após a pausa. Um resultado
entregue pela credencial já revogada também pode ser recusado: W08 conserva e
reconcilia a tentativa, sem converter incerteza em sucesso nem repetir envio.
Repetir `diagnose` com saída nova e exigir ausência de grant DELIVERY ativo;
contagens não substituem a prova autenticada do ensaio abaixo.

## Retorno com credencial nova

1. Corrigir a causa e manter a credencial antiga revogada. Conferir casos
   UNKNOWN e tentativas em voo; preservar os controles e o journal privados.
2. Usar `provision-service-credential` do mesmo operador para uma nova
   DELIVERY_WORKER da instância/ambiente corretos, com validade explícita.
   Resultado perdido deve ser reconciliado antes de provisionar outra.
3. Com o worker ainda usando o valor revogado, validar privadamente o arquivo
   novo contra o hash do grant provisionado, papel e escopo. Criar o secret
   somente a partir desse arquivo conferido. A instalação da referência nova
   no worker VanRides é o último ato de liberação: com W06 ativo, ele pode
   enviar assim que subir. Não instalar um valor para descobrir depois se
   era o correto. Preservar os outros secrets, imagem e fontes. Essa etapa
   pertence à publicação do mantenedor, não à CLI de diagnóstico.
4. Conferir saúde, autorização, reconciliação e o único grant novo. Somente
   então liberar novos claims. Se uma agenda já chamar o worker, manter a
   barreira de envio até a conferência do secret e do destino. Não reabrir
   UNKNOWN, trocar token de uma tentativa antiga ou reiniciar n8n compartilhado.

## Evidência e ensaio isolado

`python3 -B deploy/homologacao/operations_test.py` usa executor e relógio falsos.
Verifica alvos fechados, orçamento absoluto de 45 s, alertas sanitizados,
papel de revogação e preservação dos caminhos de STOP/recuperação no plano.
Isso prova o helper, não uma pausa real da API.

O teste novo `WaSafeDeliveryPauseIntegrationTest` compõe os fixtures existentes
e os serviços transacionais reais: prepara duas mensagens sintéticas, reserva
a primeira e obtém lease da segunda; revoga só DELIVERY pelo operador real;
exige claim/authorize/result negados, reconciliação SYSTEM_JOB para UNKNOWN,
encerramento com STOP/ACK e retomada da segunda mensagem com credencial nova,
sem nova tentativa da primeira. Usa PostgreSQL descartável e relógio da fixture;
não faz HTTP, POST à Evolution nem coleta no aparelho. Seu comando dirigido é:

```sh
# Em src/backend, na janela Java17/Docker coordenada pelo integrador:
./mvnw -q -Dapi.version=1.40 -Dtest=WaSafeDeliveryPauseIntegrationTest test
```

Na entrega do código, esse teste ainda aguarda execução pelo integrador.
Existem provas complementares em `WaOutboxTransportIntegrationTest` (worker
revogado não reautoriza nem registra resultado), `WaDeliveryRecoveryIntegrationTest`
(reserva vencida vira UNKNOWN por SYSTEM_JOB) e `WaTrackingLifecycleIntegrationTest`
(STOP/ACK preservado). Nenhum desses resultados é uma nova execução desta frente.

O mantenedor registra separadamente o ensaio de operação no conjunto isolado
com a versão/JAR efetivos: falha injetada aparece ao operador, revogação
confirmada, chamada autenticada antiga recusada, W08 e STOP/ACK disponíveis,
credencial nova retoma só a pendência e zero reenvios da tentativa UNKNOWN.
Conferir também o alerta e o plano contra os alvos dessa composição, sem
afrouxar os nomes/ownership do helper de homologação nem tocar serviço live
para reproduzir a falha. O helper fixa serviços Swarm live; não aceita um
manifesto com nomes arbitrários de laboratório. A composição isolada precisa
usar suas próprias verificações de ownership e o mesmo cenário de serviço.

Recibos agregados podem ir à issue #85 após revisão. Dump, logs, requests,
tokens, contatos e GPS ficam privados. Resultado simulado não é WhatsApp real;
o fechamento da issue depende da execução e da revisão, sem aceite automático.
