# Operação local do WhatsApp MVP — card 040

Esta CLI opera somente o contexto `wa` em ambiente não produtivo e lote
explícito. O laboratório exige lote sintético; a admissão de participantes reais
é restrita ao modo online de homologação descrito abaixo. Não publica administração HTTP, recebe eventos de canal,
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
| `create-authorized-cohort` | `reasonCode:AUTHORIZED_HOMOLOGATION_GROUP` | Somente MVP online/homolog: cria o lote autorizado com `synthetic:false`; replay preserva o lote, sem converter coorte sintética ou outro ambiente. |
| `admit-participant` | `identityId`, `allowedInstanceId`, `phoneNumber`, `phoneVerification:PARTICIPANT_CONFIRMED` | Somente lote real do modo online: reserva alias EVOLUTION/PN do participante confirmado privadamente, sem perfil ou papel. CHANNEL válido da instância já deve existir. |
| `provision-service-credential` | `principal`, `allowedInstanceId`, `expiresAt` UTC | Gera segredo CSPRNG exclusivo e persiste somente SHA256; principal é CHANNEL/CONVERSATION_WORKER/DELIVERY_WORKER/SYSTEM_JOB; expira em até 30 dias. |
| `revoke-service-credential` | `credentialId`, `reasonCode` | Revoga somente credencial técnica do ambiente; não aceita OPERATOR como alvo. |
| `driver-bootstrap` | `identityId`, `expiresAt` UTC; `inviteId` UUID obrigatório no lote real | Identidade já precisa pertencer ao lote; emite convite de 43 caracteres, até 24h, ligado à identidade e ao operador. No lote real, `inviteId` fixa o replay; não cria perfil nem concede papel. |
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
Não há operação que aceite SQL, nome de tabela/schema, URL, GPS, alias livre,
ator, papel humano ou texto arbitrário de auditoria.

As operações do laboratório exigem cohort previamente criado explicitamente,
`synthetic=true` e ambiente exato, exceto a própria criação. `prod` e
`production` são recusados, inclusive quando a credencial do operador confere.
Esse limite local é deliberado; operação produtiva exige outro escopo/gate.

## Admissão de participantes autorizados — MVP online / issue91

A admissão usa a mesma CLI de arquivo privado, autenticação OPERATOR, transação,
auditoria e saída PREPARED/COMMITTED. Não há rota HTTP de cadastro administrativo,
autoadmissão por mensagem ou criação automática de identidade desconhecida. Exige
`SPRING_PROFILES_ACTIVE=homolog` exclusivo, `WA_MVP_ONLINE=true`, sandbox desligado
e `WA_ENVIRONMENT=vanrides-mvp-hml`, com todas as demais proteções do modo online.
Executar sem servidor web, por `spring.main.web-application-type=none`, usando o
JAR/digest e o banco do ambiente autorizado. Preparar pedidos/saídas em diretório
privado fora de Git; não colocar telefone, código ou credencial em argv/chat/log.
O agendamento automático do watchdog fica restrito ao processo web; a CLI não
reconcilia sessões ou expira dados de outras coortes durante esperas por locks.

1. Manter um `batchId` técnico **sintético** para provisionar/revogar CHANNEL,
   CONVERSATION_WORKER, DELIVERY_WORKER e SYSTEM_JOB, com instância/ambiente exatos.
   A admissão não provisiona credenciais e não conecta uma conta WhatsApp.
2. Escolher outro `batchId` UUID estável para o grupo de participantes e executar
   `create-authorized-cohort` com `reasonCode=AUTHORIZED_HOMOLOGATION_GROUP`.
   O lote nasce `synthetic=false`. Um UUID existente com tipo/ambiente diferente
   é recusado; não há promoção/reassociação de dados.
3. Confirmar privadamente com cada participante a autorização para o ensaio e o
   PN completo da sua conta WhatsApp. No pedido `admit-participant`, usar um
   `identityId` UUID estável, `allowedInstanceId` da instância já provisionada,
   `phoneNumber` E.164 canônico (`+`, DDI e dígitos, máximo15) e
   `phoneVerification=PARTICIPANT_CONFIRMED`. Essa marca registra a declaração
   do operador; **não é OTP nem verificação automática de posse pelo WhatsApp**.
   Não inferir PN pelo nome, por mensagem de desconhecido ou por um LID.
4. A operação reserva somente `EVOLUTION`/`PN`, usando o JID canônico derivado
   do número e a mesma trava de alias do ingresso. Repetir o mesmo pedido é
   seguro. Trocar telefone/instância de uma identidade, usar identidade de outro
   lote ou reservar o PN já ligado a outra identidade é recusado. LIDs já
   associados pelo ingresso autenticado permanecem preservados. Admissão não
   cria inbox, perfil, consentimento, papel de motorista ou habilitação.
5. Para o motorista desse lote, usar `driver-bootstrap` com `identityId`,
   `inviteId` UUID estável e `expiresAt` UTC fixo em até24h. O código de43
   caracteres fica somente na saída privada. No modo real ele é derivado por
   HMAC-SHA256 do servidor com propósito separado e contexto
   ambiente/coorte/identidade/inviteId; não há segredo escolhido pelo cliente,
   código bruto persistido ou migration adicional. O mesmo pedido recupera o
   mesmo código e uma única linha. Trocar identidade/TTL sob o mesmo `inviteId`,
   renovar convite expirado ou reabrir convite consumido/revogado é recusado.
   Uma rotação da chave de hash não recupera o código antigo; coordenar rotação
   separadamente, sem substituir silenciosamente o convite existente.
6. Encaminhar o código somente ao participante autorizado por canal privado.
   O fluxo normal de onboarding, aviso/aceite, convite, pareamento e permissões
   continua obrigatório. A emissão do convite não equivale a cadastro completo,
   consentimento, sensor ativo ou aceite de demonstração.

O PN necessário ao roteamento fica no armazenamento operacional restrito
`wa.identity_alias`; não é devolvido no recibo da CLI, contexto LLM ou auditoria.
O campo `verified_phone` não é preenchido para inventar uma verificação do
provider. Não ativar logs de SQL/argumentos/corpos. Resultados usam somente IDs,
instância e estado; falhas da CLI continuam sanitizadas.

Coortes reais **não** aceitam `seed-test-data`, `complete-test-inbox`,
`reset-test-data`, provisionamento técnico ou `create-test-cohort`. Semântica e
reset dos laboratórios sintéticos permanecem iguais. Retirada de participante,
retenção e operação assistida exigem seu fluxo próprio; nunca usar reset de teste
como apagamento de dados reais. Esta preparação local não executa admissão de
pessoas, conexão do número, operação na VPS ou publicação.

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
