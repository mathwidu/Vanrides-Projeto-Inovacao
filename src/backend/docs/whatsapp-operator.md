# Operação local do WhatsApp MVP — card 040

Esta CLI opera somente o contexto `wa` em ambiente local não produtivo e lote
sintético explícito. Não publica administração HTTP, recebe eventos de canal,
aciona n8n/Evolution nem comprova app/dispositivo. O backend web continua com o
mesmo fluxo quando `vanrides.wa.operator.request-file` não está definido.

A entrada é um `ApplicationRunner` opt-in. Exige contexto sem servidor web e
`spring.main.web-application-type=none`; um `WebApplicationContext` é recusado
mesmo se essa propriedade for falsamente declarada. Contextos de servidor web
e seus contextos filhos também são recusados. Perfis Spring começando por
`prod` bloqueiam a CLI mesmo quando o request declara ambiente de teste.
O serviço verifica a mesma
restrição, usa transação Spring e não aceita principal humano/dispositivo
fabricado pelo chamador.
Após retorno bem-sucedido dos runners, o entrypoint fecha o contexto apenas
nessa combinação explícita de modo `none` e request de operador. Isso encerra
os schedulers/datasource também quando componentes legados não web criam
threads; uma operação confirmada deve terminar com exit0. Falha de startup ou
do runner continua propagando erro. O modo HTTP normal permanece aberto.

## Arquivos e execução

Prepare request e destino em diretório privado local, fora do Git, de pastas
sincronizadas e de logs. O request precisa ser arquivo regular, sem symlink,
pertencente ao usuário do processo, com permissão exata `0600`, até 16 KiB.
O caminho de saída deve ser absoluto e inexistente. A CLI cria o arquivo com
`CREATE_NEW` e `0600` antes de qualquer efeito no banco; nunca sobrescreve um
resultado anterior. Não use segredo em argumento, URI ou variável impressa.

Com o JAR já validado e o banco isolado/configurações `_FILE` preparados pelo
runbook do ambiente, a forma de invocação é:

```bash
java -jar /caminho/absoluto/vanrides.jar \
  --spring.main.web-application-type=none \
  --spring.main.banner-mode=off \
  --vanrides.wa.operator.request-file=/diretorio/privado/request.json \
  --vanrides.wa.operator.output-file=/diretorio/privado/resultado-novo.json
```

Os caminhos são ilustrativos, não comandos executados nesta entrega. A CLI
não carrega arquivos `_FILE` por conta própria; respeita `WaSettings`, depois
do entrypoint/cofre preparar a configuração. Não copiar credenciais reais
para este documento ou salvar arquivos privados no repositório.

A CLI registra somente `WA_OPERATOR_OK` em SLF4J INFO ou `WA_OPERATOR_FAILED`
em WARN, sem argumentos, causa ou stack da exceção. O console de inicialização
do Spring pode conter mensagens de infraestrutura; os eventos da CLI não
incluem request, resultado, paths ou a exceção original. Falhas têm
código genérico, sem causa/stack que retenha valores recebidos.

O writer grava e força o resultado `PREPARED` dentro da transação. Falha nessa
gravação aborta a transação, inclusive a inicialização do operador. Depois do
commit, o runner publica `COMMITTED` usando um temporário `0600` no mesmo
diretório, force e substituição atômica. Antes de substituir, confere dono,
permissões e fileKey do destino reservado por esta execução. O arquivo
`PREPARED` não é truncado para finalizar; falha na escrita do temporário
preserva seus bytes, IDs e segredos. Temporários são removidos em falha e só
depois da publicação completa é registrado sucesso.
A validade configurada e a validade/revogação da credencial OPERATOR são
revalidadas com o relógio atual antes e depois do writer, mantendo o lock da
credencial. Expirar enquanto a operação aguarda locks ou grava o arquivo
aborta a transação; `Actor.now` não renova esse prazo.
Se ocorrer falha de commit ou da gravação final, o resultado pode permanecer
`PREPARED`: é estado indeterminado, não prova de aplicação nem de rollback.
Falha na publicação final após o serviço retornar não desfaz o commit já
ocorrido: o resultado privado continua recuperável, ainda que permaneça
`PREPARED`. Preserve esse arquivo para reconciliar os IDs no banco isolado; não
repita provisionamento/seed/bootstrap cegamente. Um resultado `PREPARED` pode
conter um segredo gerado cujo registro não chegou a ser confirmado.

## Request fechado

Todo request exige `environment`, `operation`, `batchId` UUID e
`operatorCredential`. O ambiente deve corresponder exatamente a `WaSettings`;
a credencial deve ser base64url canônica de 32 bytes/43 caracteres, igual à
configurada e ainda dentro de `operator-expires-at`. Não há fallback para
credencial técnica, token legado, usuário do app ou outra instância/ambiente.

A primeira operação autenticada cria a linha `service_credential` de
`OPERATOR`, se o hash ainda não existe. Uma linha existente precisa continuar
sendo `OPERATOR` no mesmo ambiente, sem instância, não expirada/revogada.
A CLI não reativa nem altera uma linha incompatível. Auditoria guarda somente
credencial do operador, ação/recurso, correlação e motivo tipado; nunca o
segredo. A identidade do operador não é um perfil humano.

| `operation` | Campos adicionais obrigatórios | Efeito |
|---|---|---|
| `create-test-cohort` | `synthetic:true` | Cria o cohort do `batchId` no ambiente exato; existente compatível é preservado; `false` nunca é promovido. |
| `provision-service-credential` | `principal`, `allowedInstanceId`, `expiresAt` UTC | Gera segredo CSPRNG exclusivo e persiste somente SHA256; principal é CHANNEL/CONVERSATION_WORKER/DELIVERY_WORKER/SYSTEM_JOB; expira em até 30 dias. |
| `revoke-service-credential` | `credentialId`, `reasonCode` | Revoga somente credencial técnica do ambiente; não aceita OPERATOR como alvo. |
| `driver-bootstrap` | `identityId`, `expiresAt` UTC | Identidade já precisa pertencer ao lote; gera convite de 43 caracteres, até 24h, ligado à identidade e ao operador; não cria perfil nem concede papel. |
| `seed-test-data` | `identityId`, `inboxEventId`, `workerCredentialId`, `leaseExpiresAt` UTC | Cria/reutiliza identidade do lote e alias sintético; cria inbox CLAIMED com lease gerado de até 60s para worker existente e vigente. |
| `complete-test-inbox` | `inboxEventId` | Conclui somente inbox SYNTHETIC/MESSAGE/CLAIMED do lote que já possui command_receipt durável; limpa o lease para preparar a próxima etapa sintética. |
| `reset-test-data` | Nenhum | Remove somente o grafo das identidades do lote após verificar isolamento; preserva cohort/credenciais e auditoria de operador. |
| `revoke-device` | `deviceId`, `reasonCode` | Revoga aparelho próprio do lote pelo OPERATOR auditado, tokens e aprovações anteriores; solicita parada de sessões existentes. |
| `list-delivery-cases` | `allowedInstanceId`, `limit` inteiro1–100; opcional `afterCaseId` UUID | Consulta somente casos DELIVERY do ambiente/coorte/instância, em ordem de caseId e com auditoria. Não altera conversa nem fecha casos. |

A consulta retorna `cases` e `nextAfterCaseId` UUID ou null. Cada item contém
somente caseId, outboxId, state, reasonCode, correlationId, createdAt,
attemptState e hasExternalMessageId boolean. O ID externo bruto, identidade,
contato e conteúdo nunca são retornados. Os campos comuns e o envelope
PREPARED/COMMITTED continuam obrigatórios; o resultado fica no arquivo privado
0600 do fluxo existente. Logs permanecem apenas WA_OPERATOR_OK/FAILED.
Auth OPERATOR, modo local sem servidor web, ambiente não produtivo/coorte
sintética, escopos e rechecagem após escrita continuam exigidos. Consulta não
resolve caso, recupera ID desconhecido ou fornece administração HTTP.

`reasonCode` usa `[A-Z][A-Z0-9_]{0,63}`. Instância usa identificador simples de
1–80 caracteres. Campos adicionais desconhecidos, nulos, documentos JSON
concatenados, chaves duplicadas e secrets escolhidos para credential/lease/
bootstrap são recusados. A saída privada devolve IDs e, quando necessário,
`credential`, `driverInviteCode` ou `leaseToken` recém-gerados pelo serviço.
Não há operação que aceite SQL, nome de tabela/schema, URL, GPS, alias real,
ator, papel humano ou texto arbitrário de auditoria.

Todas as operações desta fatia exigem cohort previamente criado explicitamente,
`synthetic=true` e ambiente exato, exceto a própria criação. `prod` e
`production` são recusados, inclusive quando a credencial do operador confere.
Esse limite local é deliberado; operação produtiva exige outro escopo/gate.

## Seed e jornada fundacional

O seed deriva a instância de `CONVERSATION_WORKER` existente, no mesmo ambiente,
sem revogação e com validade suficiente para o lease. Credencial sem instância
não serve. O alias é `SYNTHETIC`/`JID` em domínio `.invalid`, sem telefone
verificado. Não cria empresa ou perfil fictício. Uma identidade preexistente
de outro lote é recusada, sem ser reassociada.

São opcionais e mutuamente exclusivos `pairingCode` (oito caracteres de
`BCDFGHJKMNPQRSTVWXYZ`), `driverInviteCode` (32 bytes canônicos/43 caracteres)
e `messageText` (texto sintético1..2000 caracteres).
São dados da entrada privada, usados para criar somente `pairingCodeHash` via
`crypto.secretHash("pairing-code", code)` ou `driverInviteCodeHash` via
`crypto.secretHash("driver-bootstrap", code)`. Nenhum código bruto entra na
inbox.070 grava `occurredAt` pelo relógio do operador e `messageText` como texto
sintético quando informado. O seed nunca fabrica ACEITO por default; o smoke
de consentimento deve informar esse texto explicitamente depois do aviso.
O lease é aleatório, persiste por `WaCrypto.digest(raw)` e sua expiração
não ultrapassa a credencial do worker.

Sequência local: criar cohort; provisionar worker; seed de identidade/inbox;
bootstrap da identidade; concluir um evento sintético somente após seu receipt;
preparar o próximo evento com hash do convite/código necessário. Os comandos
HTTP de conversa continuam exigindo o worker e `X-Inbox-Lease` correntes.
`complete-test-inbox` é preparação de fixture, não ACK de um transporte real.
O seed não sobrescreve um evento, claim ativo ou receipt para contornar
idempotência; use IDs novos e respeite a conclusão explícita.

## Reset e revogação

O reset trava cohort, identidades/perfis em ordem UUID e autorizações antes
de analisar o grafo. Recusa links, membros, paradas, presenças e mensagens
com referências que atravessem o lote. Depois apaga filhos antes dos pais,
sempre por identificadores do lote. Constraints não reconhecidas continuam
bloqueando a transação; não há fallback destrutivo.

Não usa TRUNCATE/DROP SCHEMA, não toca tabelas `public`, não remove credenciais
compartilhadas nem purga `proof_nonce`/`proof_replay` globalmente. Replays de
begin sem dono comprovado permanecem até sua expiração normal; replays de
resgate/refresh/revoke com scope comprovadamente do lote são removidos.
Cohort e auditoria OPERATOR permanecem para rastrear/repetir o ensaio; registros
fora do lote permanecem intactos.

`revoke-device` segue a ordem perfil → autorização → aparelho/tokens. Não
fabrica um DEVICE para chamar o serviço de sessão. Revoga access/refresh,
avança a geração vigente e invalida aprovações antigas quando aplicável.
Sessões futuras existentes recebem `STOP_REQUESTED` e lease encerrado; isso
não afirma que um sensor físico parou. Repetir revogação de aparelho antigo
não invalida um aparelho sucessor ativo.

## Verificação preparada

`WaOperatorRequestTest` cobre o request fechado, dados duplicados, entropia
escolhida pelo chamador e formato dos códigos. `WaOperatorRunnerTest` cobre
opt-in, contexto HTTP, permissões/symlinks/CREATE_NEW, saída privada e falhas
sanitizadas, inclusive falha de escrita final sem evento de sucesso.
`WaOperatorFilesTest` recusa substituir outro arquivo no destino reservado.
`WaOperatorPostgresIntegrationTest` usa transação Spring real e
PostgreSQL isolado para autenticação/env/expiração, produção, cohort ausente/
false, hashes/lease/instância, receipt antes da conclusão, rollback por writer,
revogação técnica, reset isolado/vínculo cruzado e revogação OPERATOR. Também
confirma que falhar a finalização preserva `PREPARED` e a credencial que já
foi confirmada no banco, sem apresentar esse caso como rollback.

Esses testes precisam entrar na matriz e no gate principal; presença do código
não equivale a testes executados. Boot real com `web-application-type=none`,
smoke da CLI e compatibilidade dos demais beans legados são verificações
separadas do unitário do runner. Nenhuma publicação, mensagem ou dispositivo
está incluído neste runbook.
