# Retenção técnica de sete dias e capacidades vencidas

`WaPrivacyTechnicalRetention` é um port privado sem bean, rota ou scheduler.
`WaPrivacyPolicy.disabled()` e dry-run são os padrões. Este incremento não ativa
limpeza na homologação, não contém dados de aparelhos e não conclui #83/#84.
Os testes usam TrackingService, ingress e retomada de suporte reais com GPS,
chaves, identidades e mensagens inteiramente sintéticos.

## Contrato para o coordenador

Construtores recebem `JdbcTemplate`, transaction manager, relógio e política;
o overload final recebe `boolean dryRun`. `tick(SYSTEM_JOB, limit, Position)`
aceita lote entre 1 e 100. O serviço rejeita transação ambiente e abre a sua.
A credencial é lida com `FOR SHARE`, exige validade e instância autorizada,
e fornece ambiente/instância. A validade é conferida novamente antes do commit.
Não depende de novos inícios, conexão de envio ou inbox humana fabricada.

`Position` contém `credentialId`, `environment`, `instanceId`, `asOf`, `after`,
`dryRun` e `syntheticOnly`. O runtime deve transportá-la no envelope opaco
autenticado `WaPrivacyCursor` com família `TECHNICAL_7D`. O port valida vínculo,
modos, formato e prazo de cinco minutos; não criptografa o cursor novamente.

`Result` contém `status`, `examined`, `erased`, `deleted`, `deferred`,
`wouldChange`, `complete`, `next` e `pending` (`family`, `id`, `reason`).
Cada unidade candidata altera ou exclui no máximo uma linha física. Locks de
donos e consultas de validação não são contados como linhas apagadas. O cursor
ordena família, instante e chave; para no primeiro recurso ocupado ou pendente,
sem saltar o prefixo. Um `NOT EXISTS` independente define `complete` no escopo
e corte desta manutenção, sem confiar no cursor. Isso não atesta conclusão
global da privacidade nem cobertura dos namespaces sem proveniência abaixo.

## Famílias executadas

| Família | Elegibilidade e efeito |
| --- | --- |
| `SAMPLE` | Recibo com mais de sete dias; sessão `STOPPED/EXPIRED/REVOKED` e viagem `COMPLETED/CANCELED`. Exclui um recibo de amostra. |
| `BATCH` | Mesmo prazo/estados; somente depois de todos os filhos de amostra saírem. Exclui uma linha do lote. |
| `CONTEXT` | Sete dias desde o último `MESSAGE.created_at` recebido pelo servidor na instância. Retira drafts e dono órfão; invalida confirmações contidas neles. |
| `REPLAY` | Resposta criptografada vencida e atribuída a dono comprovado, sem operação ativa. Troca o ciphertext pelo tombstone canônico `bytea 00`. |
| `CIPHER` | Outbox vencida com origem command/inbox na instância, destinatário na coorte e sem tentativa `SUBMITTING`. Limpa os dois ciphertexts transitórios. |
| `PAIRING` | Pareamento vinculado a motorista/coorte, vencido e em estado aprovado ou terminal, sem referência ativa nem replay ainda utilizável. Exclui a linha. |
| `DEVICE` | Dispositivo revogado, sem tokens ainda válidos ou operação ativa. Retira o JWK público, preservando ID, JKT, geração e referências. |

Todas as famílias exigem coorte não nula no ambiente autorizado. O dono deve
ter alias na instância. Aliases em outra instância geram `MULTI_INSTANCE`, sem
inferir que a autorização de uma instância cobre as outras. Objetos sem dono
comprovável ficam fora do conjunto executável, não são selecionados globalmente.

Os recibos GPS não contêm coordenadas. A exclusão mantém na sessão o maior
`last_sequence`, geração, estado e referência STOP, além da autorização e da
viagem. Dentro dos sete dias, os recibos continuam garantindo ACK idempotente.
Depois do prazo e em viagem terminal, um lote tardio pode somente receber
rejeição/STOP; não pode produzir nova amostra aceita, posição ou reativação.
A ordem de locks é viagem → sessão → dono → recibo, usando `SKIP LOCKED`.
Não se altera `captured_at` e não há adendo de schema.

Na conversa, `updated_at` não mede atividade humana: manutenção e suporte
também o alteram. O cálculo usa `MAX(inbox.created_at)` de mensagens humanas.
O lock da identidade cerca a chegada de inbox; locks de perfil e contexto
também usam `SKIP LOCKED`. Se suporte possuir perfil e aguardar identidade,
a manutenção cede e libera sua transação, evitando o ciclo entre os dois.
Após cercar, repete a elegibilidade e verifica leases/referências ativas.
Preserva `PAUSED`, `paused_until`, suporte, último evento, leases, versões de
aviso, instantes de emissão e `ownerPromptEvents`. Nenhuma confirmação é renovada.

`operation_replay` conserva linha, kind, scope, chave, hash e validade.
O tombstone de um byte satisfaz o CHECK existente, mas não é um envelope AES
válido e não contém ciphertext recuperável. Os dois leitores de produção,
`WaReplayStore` e `WaTrackingStore`, verificam conflito/validade antes de
decriptar; o teste chama ambos. Respostas dentro do prazo continuam legíveis.
O vínculo é dispositivo para tracking/revoke, token para refresh, pareamento
para redeem e o hash documentado `(environment, JKT)` para begin, somente
quando a chave já possui dono comprovado.

## Controles mínimos e proveniência ainda necessária

Não há novo DELETE global para provas ou credenciais. Permanecem as linhas e
hashes necessários de `device_token`/`rotated_to`, convites, `start_intent`,
credenciais de serviço e seus vínculos. Não são armazenados tokens brutos
nessas tabelas. Expiração, revogação e leitores continuam impedindo uso;
a manutenção não remove capacidade STOP nem apaga a cadeia de refresh.

`proof_nonce` tem ambiente, mas não coorte/instância; `proof_replay` nem ambiente
tem. Nonces vencidos não podem ser consumidos, e provas DPoP têm janela de
tempo verificada. Esses hashes/JKT/JTI são controle mínimo com expiração lógica.
`WaProofLedger.issue` já possui cleanup global de expirados; esse comportamento
preexistente está fora deste incremento e não comprova purge por instância.

Precisam de proveniência adicional para descarte físico com esta autoridade:

- `pairing_challenge` sem motorista: JWK público, plataforma/versão do cliente,
  vínculo JKT/PKCE e código em hash. A validade impede aprovação/redeem tardio,
  mas a linha não tem coorte/instância atribuível para este job.
- `operation_replay.PAIR_BEGIN` sem chave de dono conhecido: resposta cifrada
  com UUID/código de pareamento e datas. O código está vencido e o leitor
  recusa o replay; o ciphertext não é apagado por suposição de origem.
- Outbox cifrada sem coorte do destinatário ou origem command/inbox na
  instância: requer vínculo persistido antes de ampliar a seleção.

Não há nome, GPS ou conversa nesses controles de prova/replay preauth na
baseline conhecida; JWK e metadados de cliente podem ser identificadores
técnicos. Esses limites devem constar da evidência e da próxima decisão de
proveniência. Não são uma promessa de anonimização ou exclusão integral.

## Verificação e retorno

As classes de teste cobrem limites por linha, filho antes do pai, sessões e
viagens ativas, pausa/suporte, atividade humana, replay dentro e fora do prazo,
ambiente/coorte/instância, dry-run, transação ambiente e concorrência com
ingress/retomada reais. Resultados executados e falhas de fixture ficam no
recibo privado da frente. Nenhum ensaio substitui teste em aparelho.

Retorno do código: reverter este incremento sem modificar schema ou ledger.
Conteúdo apagado não volta com rollback do binário. O coordenador deve manter
purge desabilitado até os gates do journal/restore e a revisão da composição.
