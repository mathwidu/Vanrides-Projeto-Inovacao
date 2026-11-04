# Operador privado na VPS

`operador.py` executa uma solicitação pela CLI não web em um serviço Swarm
efêmero. Não altera nem reinicia a API, publica portas, cria endpoint de
administração ou migra o banco. Executar somente no escopo operacional aprovado.

## Entradas e execução

Executar como `mathwidu` (UID 1000) em `srv1397065`, com Python 3.9+ e Docker
local por socket Unix. O helper fixa esse socket durante toda a rodada. Exige
manager de nó único, API `vanrides-hml-api_api`, rede
`vanrides-hml-api_private` e imagem efetiva igual à do manifest online aprovado.

Guardar os arquivos fora do Git, em pasta do operador com modo `0700`. Request
e recibos são `0600`. Todos os caminhos são absolutos e sem symlinks; os binds
também exigem nomes sem espaços/vírgulas. `--result` e `--job-directory` devem ser
novos e ter pai privado `0700`. Usar um job diferente para cada solicitação.

```sh
python3 operador.py run --execute-local-vps \
  --online-manifest "$ONLINE_MANIFEST" \
  --online-manifest-sha256 "$ONLINE_MANIFEST_SHA256" \
  --operator-manifest "$OPERATOR_MANIFEST" \
  --operator-manifest-sha256 "$OPERATOR_MANIFEST_SHA256" \
  --jar "$OPERATOR_JAR" \
  --request "$PRIVATE_REQUEST" --result "$PRIVATE_RESULT" \
  --job-directory "$NEW_PRIVATE_JOB" --timeout-seconds 180
```

Os hashes dos manifests vêm da transferência conferida pelo operador, não de
arquivos arbitrariamente substituídos no destino. O manifest online precisa
estar junto de seu `stack.online.json` original, cujo hash também é verificado.
O manifest do JAR registra `BUILT_FOCUSED_TESTS_ONLY`, fonte e testes focados;
isso não equivale a gate completo ou aprovação de produção.

Artefato de admissão preparado nesta rodada:

| Item | Valor conferido na preparação |
|---|---|
| JAR SHA256 | `29dd480124e7e515cd47aac30b4eeea46a56bfdf134e91f62b343aa4b91f9773` |
| JAR bytes | `58374463` |
| Manifest SHA256 | `93b0697295e50d4cb60fcf8dd92028d377f248fe0f121b983b95594c6b81d476` |
| Java / schema | Java 17 / `UNCHANGED_V38` |

## Solicitações privadas

Campos comuns: `operation`, `batchId` UUID estável e, opcionalmente,
`environment: "vanrides-mvp-hml"`. **Não incluir `operatorCredential`**:
o serviço insere esse valor a partir do mesmo secret da API, dentro de seu
tmpfs privado. Não colocar telefone, credenciais ou códigos em argumentos,
logs, chat ou documentação.

| Operação | Campos adicionais / condição |
|---|---|
| `create-test-cohort` | `synthetic: true`; lote técnico |
| `provision-service-credential` | `principal`, `allowedInstanceId`, `expiresAt`; lote técnico, validade UTC futura até 30 dias |
| `create-authorized-cohort` | `reasonCode: "AUTHORIZED_HOMOLOGATION_GROUP"`; lote real distinto do técnico |
| `admit-participant` | `identityId`, `allowedInstanceId`, `phoneNumber` E.164 autorizado e `phoneVerification: "PARTICIPANT_CONFIRMED"`; exige CHANNEL ativo na mesma instância/ambiente |
| `driver-bootstrap` | `identityId`, `inviteId` UUID estável, `expiresAt` UTC fixo futuro até 24 horas |
| `revoke-service-credential` | `credentialId`, `reasonCode` em maiúsculas; lote técnico |

Provisionar separadamente `CHANNEL`, `CONVERSATION_WORKER`, `DELIVERY_WORKER`
e `SYSTEM_JOB`. Os recibos privados podem ser nomeados `channel.json`,
`conversation.json`, `delivery.json` e `job.json`, conforme a entrada de
`preparar_segredos.py`. O helper não imprime seu conteúdo. O provisionamento
de credencial não é um mecanismo de replay idempotente: reconciliar falha
ambígua antes de emitir outra credencial.

Admissão repetida usa a mesma identidade/telefone/instância. Convite repetido
usa os mesmos `inviteId`, identidade e validade; divergência, consumo ou
revogação são recusados pelo backend. Nenhuma operação converte lote técnico
em real. O helper não disponibiliza seed/reset de dados.

## Isolamento, confirmação e recuperação

O serviço usa UID/GID `10001`, root filesystem somente leitura, `/tmp` tmpfs,
sem capabilities, sem healthcheck HTTP herdado, logs Docker desabilitados e
limites de 768 MiB, 1 CPU e 128 PIDs. O JAR é conferido novamente dentro do
serviço antes do Java. `passwd`/`group` somente leitura identificam UID 10001
como `vanridesop`, necessário à verificação de propriedade de `WaOperatorFiles`.
Não exige `sudo`, `chown` ou diretório privado de root na VPS.

Request humano entra por `docker exec -i` via stdin para arquivo `0600` em
diretório aleatório `0700` no serviço. Os mesmos cinco secrets da API são
montados para UID 10001: banco, JWT, hash, criptografia de resposta e operador.
Não rotacionar hash/resposta/operador: o convite precisa corresponder ao HMAC
usado pela API. Java roda não web, Flyway desligado e sem watchdog de sessões.

Somente saída Java zero mais recibo `COMMITTED` com operação, ambiente,
lote e IDs esperados confirma a operação. O recibo final é gravado depois da
remoção confirmada do serviço próprio. O stdout contém apenas o código de
resultado; falhas mostram um código sanitizado, nunca stderr bruto do Docker.

Em cancelamento, timeout ou perda de resposta, preservar o diretório do job.
`PREPARED` não comprova rollback. Não reenviar automaticamente a solicitação.

```sh
python3 operador.py recover --execute-local-vps --job-directory "$PRIVATE_JOB"
```

`recover` não executa a operação novamente: tenta capturar seu resultado e
remove somente o serviço com nonce, nome, imagem e labels de propriedade
conferidos. Também recupera captura privada já salva com hash e exit code,
mesmo depois de expirar a solicitação ou remover o serviço. Sem prova de
`COMMITTED`, retorna `OPERATOR_RECOVERED_WITHOUT_COMMIT_PROOF`: exige
reconciliação pelo operador e não autoriza tratar a operação como desfeita.
Recibo existente nunca é sobrescrito. SIGKILL/queda do host exige recuperação
manual a partir do journal; o timeout interno limita a vida do contêiner.

## Verificação desta entrega

`python3 deploy/homologacao/canal/operador.py self-test`: **6/6 PASS** na revisão
focalizada, sem Docker, JVM, SSH ou dependências externas. Cobre permissões sob umask privado,
nanosegundos do Java, expiração na recuperação, campos privados, confirmação
de resultado e argumentos isolados do serviço. Depois dessa prova, a consulta
de serviços foi corrigida a partir da observação do Docker 29 pelo operador:
`service ls` não aceita `--no-trunc`, e `-q` também abrevia IDs. O helper consulta
nomes e obtém o ID completo por `service inspect`, validando nome, nonce,
escopo e imagem antes de remover. Não houve nova bateria após esse ajuste.
A execução efetiva Swarm/CLI com banco e secrets continua sendo a prova
operacional da rodada autorizada.
