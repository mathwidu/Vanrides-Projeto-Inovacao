# Runtime privado de retenção

Este incremento liga os ports ao endpoint privado
`POST /integrations/whatsapp/v1/privacy/tick`. O deploy e a ativação continuam
separados. Todos os testes de descarte usam dados sintéticos; não há prova de
limpeza dos participantes reais nem encerramento de #83/#84.

O [contrato OpenAPI](../contracts/whatsapp-mvp/privacy.openapi.yaml) define corpo
fechado `{family, batchLimit, cursor?}`. Não aceita `scope`, `asOf`, posição em
texto, IDs de sujeitos ou query string. O ingresso e o controller exigem
`SYSTEM_JOB`; cada transação volta a cercar validade e escopo no banco.
A rota permanece disponível com novos efeitos pausados.

## Flags e startup

| Variável | Padrão e regra |
| --- | --- |
| `WA_PRIVACY_ENABLED` | `false`; off não consulta FS nem tabelas de privacidade. A autenticação da chamada continua obrigatória. |
| `WA_PRIVACY_APPLY` | `false`; requer enabled. Sem apply, somente dry-run e nenhum erase/append. |
| `WA_PRIVACY_ALLOW_REAL` | `false`; o padrão limita a coortes sintéticas. Para true, exige MVP online, ambiente `vanrides-mvp-hml`, cohort-only e Team Web habilitados. Não autoriza identidades sem coorte. |
| `WA_PRIVACY_JOURNAL_DIR` | Caminho privado absoluto e canônico, obrigatório somente em apply. Diretório 700 e arquivos 600 são verificados pelo adapter. |
| `WA_PRIVACY_RESTORE_MODE` | `false`; true é rejeitado, reservado para uma CLI futura. Não é bypass para restauração. |

Booleanos aceitam apenas `true`/`false`. O runtime nunca chama
`initializeEmpty`: provisionar um journal vazio é um ato explícito anterior,
separado da inicialização de uma base já usada.

`WaPrivacyStartupGate` depende da inicialização do banco e executa antes de
construir o runtime dependente. Em apply, obtém `FileJournal.snapshot()` e
compara entradas, sequência/hash/ACK, ledger e lifecycle fechado. É somente
leitura: não importa entradas, não confirma ACK, não fecha perfis e não usa
`Restore.reconcile` como validação.

- Journal ausente/adulterado ou DB `JOURNALED/PURGED` sem cópia externa:
  `PRIVACY_STARTUP_BLOCKED`, sem liberar o runtime HTTP.
- Registro externo sem correspondente no banco, datas/escopo diferentes ou
  fechamento explícito órfão: `PRIVACY_RESTORE_REQUIRED`.
- `INTENT` coerente sem registro externo pode ficar `INTENT_PENDING`. Isso
  permite o job resolver a intenção, sem afirmar que os descartes estão prontos.
- Prazo de retenção vencido com ACK/lifecycle coerentes não bloqueia boot:
  o tick posterior existe para resolver esse trabalho.

Default off e preview não acessam journal ou suas tabelas no startup, mantendo
a compatibilidade da inicialização. A decisão de apply precisa do ensaio
prévio de dry-run e da revisão operacional da perda permanente; este código
não executa essa ativação.

## Uma família por chamada

| Família | Port e contadores |
| --- | --- |
| `TERMINAL_7D` | Inbox/outbox terminal. O port existente conta candidatos de preview como deferred; o adapter conserva esse significado e deixa wouldChange em zero. |
| `TECHNICAL_7D` | Recibos GPS, contexto e capacidades vencidas; unidades por linha física. |
| `PROFILE_30D` | Somente fechamentos explícitos já existentes, até 25 sujeitos lógicos por chamada. Examined/erased/wouldChange contam sujeitos; o fanout de cópias de um sujeito pode superar 100 linhas. |
| `HISTORY_PROJECTION_90D` | Projeções monotônicas dos payloads históricos, unidade física e autoridade cercada. |
| `HISTORY_90D` | Folhas históricas elegíveis, preservando referências/proveniência; não é o agregador das demais. |

O endpoint não fecha perfis automaticamente e não oferece uma rota close.
Profile dry-run apenas conta candidatos e retorna `wouldChange`; não chama
erase nem journal. Em apply, usa obrigatoriamente o callback de autoridade
antes e depois de cada transação de candidates/erase/ACK, com journal entre
commits. Não adicionar `@Transactional` externo ao controller ou coordenador.

A resposta tem exatamente onze campos: `status`, `family`, `examined`,
`erased`, `deleted`, `deferred`, `wouldChange`, `complete`, `nextCursor`,
`serverTime` e `reasonCodes`. Só expõe contadores e códigos, sem nomes, aliases,
GPS, public keys, UUIDs de registros ou motivos humanos. Códigos internos
desconhecidos são reduzidos a `RETENTION_DEFERRED`.

`complete` vale somente para a família, corte e escopo selecionados. Não é
um sinal de conclusão de todo o pipeline. Preview pode retornar complete=false
e nextCursor=null; perfis adiados também encerram a página sem um loop infinito.
Uma família adiada não impede que o chamador execute as outras quatro.

## Cursor e orçamento do chamador

`WaPrivacyCursor` emite AES-GCM com TTL de cinco minutos, vinculado à
credencial/instância e ao contexto criptográfico do ambiente. O JSON interno
usa campos fixos e `JavaTimeModule`; é rejeitado se vier de outra família,
ambiente, modo, credencial ou instância. O primeiro Position dos ports antigos
também é criado pelo servidor com ambiente fixo, antes da transação do port;
uma troca de ambiente da credencial não pode ampliar o escopo entre transações.

O contrato permite uma página 20 por família. O worker escala até duas
famílias por W08 em round-robin, com orçamento de 2,5 segundos por família e
cinco segundos no total; as cinco famílias recebem vez em até três ticks.
O cursor privado do worker vence em quatro minutos, antes do limite do servidor.
As medições locais de Profile20 apply foram 1.036 ms e 999 ms somente no
coordenador, portanto um segundo não é margem suficiente para HTTP/VPS.

Timeout é orçamento do chamador, não garantia de cancelamento da transação
da API. Se a resposta for ignorada pelo timeout, a transação pode terminar;
os ports são idempotentes/monotônicos para a próxima execução. A página de
perfis depende do fanout e dos locks, sem promessa de equivalência com a VPS.
Não registrar cursor ou token em logs.

## Verificação e limites

Os testes dirigidos verificam flags, papel no ingresso real, corpo fechado,
cursor adulterado/cross-scope/vencido, default off sem schema de privacidade,
preview das cinco famílias sem escritas, limite lógico, ACK com revogação e
startup com journal ausente/divergente. Os ensaios de filesystem são POSIX.
Fixture de teste com coorte administrativa não sintética continua contendo
somente dados sintéticos; não autoriza purge de ambiente real.

Os limites de proveniência dos controles preauth e do histórico continuam
descritos nos documentos dos ports. O coordenador não amplia esse escopo.
Retorno do código exige manter flags de apply desativadas; rollback do binário
não restaura conteúdo já descartado e não substitui a reaplicação do journal.
