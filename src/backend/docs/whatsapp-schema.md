# Fundação PostgreSQL do WhatsApp

Contrato local de VR-WA-040. A migration
`src/main/resources/db/migration/V26__whatsapp_foundation.sql` materializa o
[modelo 02](../../../docs/desenvolvimento/whatsapp.md)
e os [contratos 08](../contracts/whatsapp-mvp/README.md).
As tabelas destinadas aos cards posteriores não representam handlers disponíveis.

## Isolamento e tipos

- Schema `wa`, com 34 tabelas e referências qualificadas. A migration não altera
  objetos em `public`, configurações de `search_path` ou cadastros legados.
- IDs são UUID; instantes são `timestamptz`; `local_date`, `local_time`, `timezone`
  pertencem ao calendário da rotina. Versão, geração e sequência são `bigint`.
- Identidade, aliases, inbox e conversa podem existir antes de `profile`.
  Não se cria empresa, email ou senha para representar usuários do WhatsApp.
- Código/credencial/nonce são somente hashes SHA-256 em hexadecimal minúsculo
  de 64 caracteres. Chave pública e thumbprint ficam em `public_jwk`/`jkt`;
  nunca se armazena a chave privada. JWK com propriedade privada `d` é recusado.
- `environment` é texto não vazio, conferido pela aplicação em cada operação.
  `test_cohort` recusa `prod`/`production`; `synthetic` tem default `false`.
  Um lote real autorizado pode ser de teste sem se tornar apagável pelo reset
  sintético. O reset exige ambiente/lote exatos e `synthetic=true`.
- FKs não fazem exclusões em cascata. Retenção/reset precisa excluir explicitamente
  as dependências autorizadas na transação, preservando a auditoria aplicável.

## Núcleo utilizado em 040

Colunas de estado não indicadas como opcionais são obrigatórias. Os defaults
de `created_at`/`updated_at` são o instante da transação; versões começam em 0.
Atualizar versões/instantes é responsabilidade da aplicação.

| Tabela | Colunas e contrato de integração |
|---|---|
| `test_cohort` | `id`, `environment`, `synthetic=false`, `created_at` |
| `identity` | `id`, `test_cohort_id?`, `created_at` |
| `identity_alias` | `id`, `identity_id`, `provider`, `instance_id`, `alias_type=PN/LID/JID`, `alias_value`, `verified_phone?`, `created_at`; identificador opaco único por provedor/instância/tipo/valor |
| `profile` | PK/FK `identity_id`, `role=DRIVER/PASSENGER`, `status=ONBOARDING/ACTIVE/SUSPENDED/CLOSED`, `display_name?`, `notice_version?`, `notice_accepted_at?`, `driver_enabled=false`, `preferences_json={}`, `version`, `created_at`, `updated_at` |
| `service_credential` | `id`, `principal=CHANNEL/CONVERSATION_WORKER/DELIVERY_WORKER/SYSTEM_JOB/OPERATOR`, `environment`, `token_hash` único, `allowed_instance_id?`, `expires_at`, `revoked_at?`, `created_at`; CHANNEL exige instância |
| `driver_bootstrap_invite` | `id`, `identity_id`, `code_hash` único, `expires_at`, `revoked_at?`, `consumed_at?`, `created_at`, `created_by`; FK composta com `created_by_principal=OPERATOR` default impede emissor de outro papel |
| `integration_inbox` | `id`, `identity_id?`, `provider`, `instance_id`, `message_id?`, `event_key?`, `category=MESSAGE/STATUS/CONNECTION/ECHO`, `normalized_kind=TEXT/PIN/UNSUPPORTED`, `payload_json`, `state=PENDING/CLAIMED/COMPLETED/RETRY/SKIPPED/EXPIRED`, `lease_credential_id?`, `lease_token_hash?`, `lease_generation=0`, `lease_expires_at?`, `created_at` |
| `command_receipt` | PK `command_id`, `identity_id`, `inbox_event_id`, `idempotency_key`, `command_type`, `request_hash`, `result_json`, `created_at`, `expires_at`; unicidade por evento e por ator/chave; FK composta garante o ator original |
| `driver_authorization` | PK/FK `driver_id`, `generation=0`; FK composta com `driver_role=DRIVER` default exige perfil do motorista |
| `driver_device` | `id`, `driver_id`, `jkt`, `public_jwk`, `generation>0`, `environment`, `platform=ANDROID/IOS`, `app_version`, `status=ACTIVE/REVOKED`, `created_at`, `revoked_at?`; um ativo por motorista e por chave; revogação exige instante |
| `pairing_challenge` | `id`, `public_jwk`, `jkt`, `pkce_challenge`, `platform`, `app_version`, `environment`, `code_hash` único, `state=PENDING/APPROVED/REDEEMED/REVOKED/EXPIRED`, `expires_at`, `driver_id?`, `approval_generation?`, `failed_attempts=0` limitado a 5, `device_id?`, `created_at` |
| `device_token` | `id`, `device_id`, `token_hash` único, `kind=ACCESS/REFRESH`, `family_id`, `expires_at`, `consumed_at?`, `revoked_at?`, `rotated_to?`, `created_at`; rotação exige REFRESH consumido e destino na mesma família/aparelho/tipo |
| `proof_nonce` | PK `nonce_hash`, `jkt`, `environment`, `expires_at`, `consumed_at?`, `created_at`; índice por chave/prazo |
| `proof_replay` | PK composta `jkt,jti`, `expires_at` |
| `operation_replay` | `id`, `operation_kind=PAIR_BEGIN/PAIR_REDEEM/REFRESH/SESSION_REVOKE`, `scope_id`, `request_key`, `request_hash`, `response_ciphertext` bytea, `expires_at`; chave única tipo/escopo/chave; escopo polimórfico é validado pela aplicação |
| `actor_attempt_window` | PK composta `identity_id,action,window_start`, `attempts` entre 0 e 5 |
| `audit_event` | `id`, `actor_identity_id?`, `credential_id?`, `actor_kind`, `action`, `resource_id?`, `correlation_id` até 128 caracteres, `reason_code?`, `created_at` |

`pairing_challenge` congela pedido/chave/PKCE/ambiente/validade no banco. A primeira
aprovação congela motorista e geração; somente o aparelho do mesmo motorista
e `jkt` e do mesmo ambiente pode preencher `device_id`. Estados terminais não reabrem. Verificação
criptográfica P-256/ES256, cálculo do thumbprint, PKCE e validade/nonce/JTI ocorrem
na aplicação; o schema não substitui essas verificações.

Inbox MESSAGE deduplica por `(instance_id,message_id)`. Outras categorias exigem
`event_key` autenticado e deduplicam por `(provider,instance_id,category,event_key)`;
DELIVERED/READ diferentes permanecem eventos distintos. Apenas um evento pode
ficar CLAIMED por identidade. Lease parcial é recusado; geração/prazo/principal
são revalidados pela aplicação dentro da transação do efeito.

## Entidades para as próximas fatias

| Tabela | Dados e invariantes materializados |
|---|---|
| `driver_invite` | Motorista, hash único, validade/revogação, limite/contador de usos e versão |
| `driver_passenger_link` | Donos com papel correto, convite do próprio motorista, REQUESTED/APPROVED/REJECTED/CLOSED, aprovador/instante/vigência/versão; um aprovado por passageiro e um pedido por par |
| `pickup_point` | Donos e link consistentes, pin de inbox do passageiro, coordenadas/rótulo/referência, aprovação/vigência/versão; um ponto aprovado vigente por passageiro |
| `routine` | Dono DRIVER, nome/sentido/dias 1–7/hora/timezone, destino, estado, política de presença e versão; destino válido obrigatório ao ativar |
| `routine_member` | Único por rotina/passageiro; rotina/link/ponto do mesmo motorista/passageiro; ordem positiva e versão |
| `routine_occurrence` | Rotina/motorista/data local/hora prevista/estado/versão; única por rotina/data |
| `attendance` | Única por rotina/passageiro/data; membro e ocorrência existentes; PENDING/GOING/NOT_GOING, ator/credencial/pergunta/instante/versão; resposta exige o próprio passageiro |
| `operation_run` | Único por ocorrência e rotina/data; dono consistente, estado, política/snapshot e versão; STARTING/START_FAILED/TRACKING/DEGRADED reservam o motorista |
| `run_stop` | Único por run/passageiro, `physical_stop_key`, ordem/pin/referência/presença congelados, estado e cancelamento separado; mesma parada física pode atender destinatários diferentes |
| `start_intent` | Hash único, dono/ocorrência/aparelho/run consistentes, ambiente, validade, consumo e estado |
| `tracking_session` | Run/aparelho do dono, geração, lease, sequência, última posição mínima, pedido/ACK de parada separados e versão; uma ativa por run/aparelho e geração única por run |
| `tracking_batch_receipt` | PK sessão/lote, geração própria, hash/corpo do ACK/instante; sem colunas de GPS |
| `tracking_sample_receipt` | Sessão/geração/lote, sequência única por sessão, captura/recepção, aceito/rejeitado e código; sem colunas de GPS |
| `conversation_state` | Identidade antes de perfil, etapa/pergunta/contexto mínimo, evento do próprio ator, pausa, lease e versão |
| `message_outbox` | Destinatário interno, finalidade, referências estruturais, política, conteúdo efêmero, validade/lease/estado/versão; índices abaixo |
| `message_attempt` | Mensagem, tentativa 1–3, geração/segredo de submissão por hash, instância/ID externo/resultado; uma submissão em andamento por mensagem |
| `support_case` | Identidade, chave deduplicada, motivo/estado, operador/prazo, correlação, encerramento e versão |

O snapshot do run e o snapshot de cada parada são imutáveis; alterações de
presença/cancelamento têm campos separados. Run terminal não reabre por
telemetria atrasada. As FKs impedem sessão de um motorista com aparelho de outro,
e lote/recibo de geração diferente.

As chaves de negócio da outbox são estruturais, sem `policy_version`:

- APPROACH: `(run_id,stop_id,recipient_identity_id)`.
- REMINDER: `(occurrence_id,recipient_identity_id,reminder_window)`.
- RESPONSE/PIN: `(command_id,recipient_identity_id,response_part)`, inclusive
  impedindo que o mesmo replay troque de texto para pin e duplique a resposta.
- OPERATIONAL/SUPPORT: `(recipient_identity_id,purpose,business_key)`.

Pin tem deadline máximo de aquisição +20 segundos. Para marcar submissão,
entrega, leitura, expiração ou cancelamento, o conteúdo do pin precisa estar
apagado na mesma transação. Retenção, watchdog, autorização final de submissão
e progresso geográfico serão implementados nos respectivos cards.

## Verificação e recuperação

São 19 casos PostgreSQL em três classes de
`src/test/java/com/vanrides/vanrides/whatsapp/persistence/`:

| Classe | Casos | Alcance |
|---|---:|---|
| `WhatsappMigrationIntegrationTest` | 4 | Banco vazio, upgrade V25 com fixture legada, rollback transacional e identidade/coorte antes de perfil |
| `WhatsappSchemaInvariantIntegrationTest` | 10 | Aliases/inbox/comandos, papéis/vínculos, rotina/presença, snapshots/runs, sessão/geração, pareamento/proof replay, aparelho/rotação e avisos/pins |
| `WhatsappSchemaConcurrencyIntegrationTest` | 5 | Runs incluindo START_FAILED, aprovação de vínculo, aparelho ativo, comando com chave trocada e claim por conversa; bloqueio observado via `pg_blocking_pids` antes do resultado |

Os testes usam PostgreSQL 16 Testcontainers e fixtures inteiramente sintéticas.
As coordenadas 0/0 e 0/1 são valores artificiais; as JWKs de persistência só
testam a forma JSON/constraints, sem pretender validar criptografia. A suíte
de protocolo usa chaves criptográficas próprias.

`disabledWithoutDocker=true` mantém o padrão do repositório; skip não comprova
validação. A matriz canônica precisa exigir execução real das três classes.
Este documento descreve a suíte; resultados reais ficam no QA Log e na evidência
do card, após execução sequencial pelo integrador.

Teste de rollback escrito: transação contendo o SQL completo de V26, inserção
e ROLLBACK devem remover todo o contexto novo e preservar `public`/histórico V25;
depois a aplicação normal de Flyway deve funcionar. Isso prova atomicidade em
banco descartável. Após uma V26 aplicada com dados ativos, retornar o código
compatível e desligar novos efeitos; preservar schema/dados e migrations
aplicadas. Não executar DROP ou reescrever V26 como rollback operacional.
