# Reaplicação do journal num banco restaurado

`WaPrivacyRestore` é uma porta interna para reconciliar o journal atual do
host com um dump anterior ao fechamento/descarte. Não registra bean, rota,
scheduler ou CLI, não executa purge e não abre HTTP, coleta ou envio.
Não constitui um bundle completo de recuperação e não conclui #83/#84.
A política da integração deve continuar `WaPrivacyPolicy.disabled()` até a
ativação deliberada do coordenador responsável pelo retorno.

## Fronteira de confiança

O construtor exige `WaPrivacyFileJournal` concreto, callback de autoridade e
`BooleanSupplier trafficBlocked`. O coordenador autentica SYSTEM_JOB e fornece
um callback que valida/cerca ambiente, instância, validade e revogação na própria
transação. Não há construtor sem esse callback. O fornecedor do sinal de tráfego
deve representar o bloqueio real de todos os escritores e saídas relevantes;
um booleano fornecido por um cliente HTTP não constitui essa prova.

`reconcile(scope, expectedHeadSequence, expectedHeadDigest, afterSequence, limit)`
recebe a cabeça confiável do journal **atual**, retida independentemente do dump.
Não copiar journal/cabeça antigos sobre os arquivos atuais. O adapter verifica
conteúdo, cadeia, limites, permissões e sincronização de dados/cabeça sob lock.
Journal ausente, parcial, adulterado ou com cabeça diferente bloqueia a operação.
Nenhum caminho de restauração chama `initializeEmpty()`.

Todos os registros do snapshot são validados antes de qualquer fechamento:
escopo único, política conhecida, IDs e sequências sem conflito e datas válidas.
Uma entrada de outra instância/ambiente bloqueia o snapshot inteiro, inclusive
quando o cursor tentaria pulá-la. O limite do adapter é dez mil registros/8 MiB.
A cabeça é reconferida entre unidades e antes do resultado final; mudança deixa
o retorno bloqueado, mesmo que fechamentos seguros anteriores já tenham sido
confirmados. O sinal de tráfego bloqueado permanece obrigatório durante isso.

## Aplicação e datas

Cada página examina até cem **entradas**. Cada entrada usa transação própria;
`Subjects.close` mantém o fanout existente de vínculos, pontos e capacidades,
portanto não há promessa de cem UPDATEs físicos. Uma transação ambiente é
rejeitada antes de efeitos. Autoridade e bloqueio de tráfego são conferidos no
início e antes de cada commit; perda da cerca reverte fechamento, intenção e ACK.

A coorte no ambiente e a instância única dos aliases são obrigatórias para um
fechamento importado. Referência operacional ativa, autor de cópia ocupado,
perfil ausente e conflitos de fechamento/ledger impedem a reaplicação.
O executor chama `WaPrivacySubjects.close` com o `closedAt` do payload original;
não chama `WaPrivacyService.close()` nem reinicia o prazo pelo relógio do restore.

São preservados exatamente:

- `closedAt`, `eraseAfter`, `eventId` e UUID do sujeito;
- sequência de banco, payload canônico e hash;
- sequência e digest do ACK original no journal durável.

`WaPrivacyLedger.importIntent` insere somente INTENT com
`OVERRIDING SYSTEM VALUE`, sem desabilitar triggers. Um registro já existente
precisa coincidir em toda identidade/payload e conserva seu `created_at`.
O journal não contém o `ledger.created_at` original: para um registro novo,
`created_at=closedAt` é uma reconstrução lógica determinística, não recuperação
do timestamp exato. `journaled_at` e um eventual novo `purged_at` são horários
observados na reaplicação; não se presume que sejam os horários anteriores.

O ledger recebe o ACK somente após a verificação do arquivo. A sequência
GENERATED avança para o maior valor entre sua posição atual e o máximo
persistido; nunca retrocede. O ajuste ocorre sob lock da tabela e as cercas da
transação. `setval` não é transacional: uma falha antes do commit pode deixar
um salto para frente, mas não registros importados pela metade nem colisão
futura. O ambiente deve permanecer sem escritores concorrentes nessa etapa.

## Resultado e prontidão

`examined`, `imported` e `acknowledged` contam entradas examinadas, intenções
inseridas e transições de ACK confirmadas. Uma falha na unidade não inclui seus
efeitos revertidos nessas contagens. `nextAfter` usa a sequência do journal;
o coordenador deve manter a cabeça confiável e a autoridade para a próxima
página, sem tratar a posição como prova de conclusão.

O gate de prontidão consulta **todos** os registros esperados e todos os ledgers
do escopo, independentemente de `afterSequence`. Exige payload/ACK compatíveis,
perfil CLOSED com data original, coorte, ausência de referência ativa e, quando
o prazo já venceu, marcadores consistentes de PURGED/descarte. Registros do
banco ausentes no journal também bloqueiam. Uma posição avançada que deixou
trabalho para trás não produz `ready=true`; o próximo exame retorna ao início.

Um fechamento reaplicado com descarte vencido retorna `RETENTION_PENDING`.
O coordenador deve executar a manutenção terminal de sete dias e chamar o
`WaPrivacyService.erase` existente, com suas próprias transações e autoridade.
Depois reconcilia novamente contra a mesma cabeça atual. O executor não
acrescenta uma exceção para contornar journal, guards ou conteúdo pendente.

Um sujeito já PURGED não é reconstruído nem fechado novamente. A repetição
confere o ledger e os marcadores existentes sem exigir payload roteável ou
uma linha de alias que já tenha sido removida. Coorte, perfil fechado, datas,
ACK e ausência de referência ativa continuam sendo obrigatórios.

`READY` é somente a conclusão desta reaplicação de privacidade de trinta dias.
Não é autorização de tráfego, readiness geral do MVP, prova de retenção de
noventa dias nem comprovação de fornecedores. O coordenador superior continua
responsável pelo restante do banco restaurado e pelos gates de recuperação.

## Ensaio sintético

`WaPrivacyRestoreIntegrationTest` usa PostgreSQL real, migrations reais e
journal de arquivo privado no host. O caso principal:

1. cria dump custom com `pg_dump -Fc` antes de close/erase;
2. fecha somente Alfa, mantém o journal atual fora do banco e avança 31 dias;
3. executa manutenção terminal e descarte, preservando Beta e referências;
4. executa `pg_restore --clean --if-exists` somente no DB do Testcontainers;
5. reaplica o journal original, confirma as datas/sequências/ACK e aguarda purge;
6. executa a limpeza existente e repete o gate/idempotência sem reconstruir Alfa.

O dump fica dentro do PGDATA em tmpfs de 256 MiB do contêiner sintético, com
limite de 2 MiB, e é removido no fim do caso. Nada é restaurado na VPS e nenhum
dump bruto vai para o Git ou para os logs. A fixture nova conserva o tmpfs como
configuração de teste portátil, evitando escrita do dump no overlay do Mac.

Os casos negativos cobrem falta/adulteração/truncamento/cabeça errada,
multiescopo, coorte/instância, referência ativa, tráfego aberto, autoridade
vencida/perdida antes de commit, transação ambiente, limite, cursor avançado,
conflito de ledger e política desabilitada. O adapter usa permissões POSIX:
a classe executa no macOS/Linux. No Windows, o ensaio precisa de ambiente
Linux com filesystem POSIX, por exemplo WSL2; a classe não presume permissões
POSIX disponíveis no Windows nativo.

Em 16/11/2026 os nove testes PostgreSQL passaram, sem falhas, erros ou skips,
em 10,175 segundos (15,047 segundos no Maven). O caso principal executou o dump
e o restore custom reais, a reaplicação e o descarte; verificou Alfa sem os
campos pessoais mapeados, Beta/referências intactas, sequência 701 e ACK/datas
originais, além de repetição PURGED sem alias. O dump cumpriu a faixa de um byte
a 2 MiB e foi removido ao final do teste, antes da remoção do contêiner.

A primeira rodada foi interrompida no setup dos nove casos pelo caminho
`/var → /private` do diretório temporário do macOS. A fixture passou a usar
`toRealPath()`; nenhum guard de filesystem foi relaxado. Os logs privados e o
resumo JSON sanitizado registram as duas rodadas; dump e XML bruto não foram
copiados para a evidência. Isso não comprova que o bundle completo de
restauração já esteja integrado.
