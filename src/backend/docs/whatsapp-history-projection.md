# Projeção histórica de noventa dias

A V44 ainda não publicada recebe um adendo aditivo; a versão operacional
anterior é V43. `WaPrivacyHistoryProjection` é um port interno, sem bean,
rota, scheduler ou configuração de ativação. A política da integração continua
desabilitada e o construtor sem modo explícito faz dry-run. Esta fatia não
conclui #83/#84 nem comprova exclusão em fornecedores ou readiness do MVP.

## Origem antes das folhas

`routine_occurrence` conserva ambiente, instância, tipo/UUID da fonte e data de
vinculação. Os cinco campos são todos ausentes ou preenchidos; depois do primeiro
pin são imutáveis. O UUID não tem FK para uma fonte histórica descartável.
O primeiro pin por UPDATE altera exclusivamente esses cinco campos e usa
`transaction_timestamp()` para a data. Alias atual nunca comprova origem.

As fontes existentes precisam concordar, inclusive quando há mais de uma:

- intent e seu comando/inbox originais;
- ambiente/instância da pergunta de presença persistida;
- inbox original da decisão operacional;
- evento de destino somente quando seu UUID já está dentro do snapshot congelado.

O destino atual da rotina não é usado para reconstruir a história. Ausência,
origem desconhecida ou conflito produz adiamento explícito. Uma fonte existente
com ambiente/instância ainda desconhecidos continua candidata a esse adiamento
no escopo compatível com sua coorte; não é tratada como ausência de trabalho.
Origem comprovadamente estrangeira continua fora do escopo. Remover uma pergunta
ou decisão não destrói o anchor já comprovado.

A materialização futura grava `SYSTEM_JOB` na mesma transação do INSERT,
validando credencial, principal, ambiente e instância permitida. O vínculo usa o
horário da transação no banco e não altera o relógio usado pelo calendário e
pelas perguntas existentes. INSERTs da API anterior podem continuar sem anchor.

## Transições permitidas

O corte do novo executor é o menor instante entre `WaClock.now()` e o relógio
da transação PostgreSQL, truncado para microssegundos UTC. Markers não podem
estar depois de `transaction_timestamp()`. Ocorrência futura não vence pela
criação antiga.

Viagens precisam estar COMPLETED/CANCELED com `completed_at` conhecido. A maior
data entre encerramento, horário da ocorrência, snapshot e criação da viagem
precisa completar noventa dias; sessão ativa ou capability válida bloqueia.
O snapshot vira exatamente um objeto com o `rosterDigest` original, sem
recomputá-lo. O objeto vazio só é admitido quando a ausência do digest já
existia com `snapshot_at` ausente. `completion_reason` vira NULL.
UUIDs, FKs, estado terminal, política, instantes e versões permanecem iguais.

Nas paradas, a transição remove coordenadas, referência, presença congelada,
estado e cancelamento. Conserva donos, chave física, ordem, pickup/version,
instantes e os marcadores pessoais anteriores. A combinação sem marker exige
os campos normais e seus enums; a combinação histórica exige ausência completa
dos campos removidos. Os guards de snapshot e progresso reconhecem a mesma
transição estreita, sem desativação de triggers ou dependência de lógica SQL NULL.

Os ciclos 30→90 e 90→30 são suportados. Após a projeção histórica, o descarte
pessoal pode somente acrescentar `privacy_erased_at`, sob a autorização normal
do journal/lifecycle e sem reconstituir nenhum campo. Um sujeito já descartado
sem linha de alias pode comprovar o escopo pelo ledger/`privacy_subject`; coorte
e ausência de alias conflitante continuam obrigatórias. Alias com instância
NULL é desconhecido e bloqueia, inclusive junto de um alias válido.

`approach_progress` e `run_finish_progress` são removidos por folha somente
quando a viagem terminal é elegível e não há referência ativa. O ciclo
intent↔session não é removido. Deduplicação, replay, credenciais e filas pertencem
a suas famílias de manutenção e continuam com os UUIDs necessários.

## Ocorrência e completude

Uma ocorrência sem viagem pode perder o snapshot de agenda após noventa dias
do maior instante entre agenda e criação, com origem comprovada e sem capability
ou pergunta válida. Seu estado, inclusive PLANNED, é preservado. Não se inventa
uma data de encerramento.

Com viagem, a ocorrência aguarda as projeções correspondentes das viagens e
paradas e a remoção dos progressos. Também aguarda as folhas pessoais de presença,
pergunta e decisão. Uma linha marcada não significa que todo o agregado terminou:
a consulta de completude procura independentemente todas as cópias restantes.
Novos INSERTs e alterações de fatos pessoais sob ocorrência/viagem histórica são
recusados; UPDATE também considera o dono anterior para impedir reanexação.

O executor processa no máximo cem linhas físicas por tick, entre pin, parada,
progresso, viagem e ocorrência. Usa transação própria, recusa transação ambiente,
deriva o escopo de SYSTEM_JOB válido com lock e exige callback de autoridade.
A cerca de autores precede os locks da ocorrência/viagem; registros ocupados
adiam sem avançar o prefixo. Um adiamento semântico, como origem ausente,
encerra somente aquela unidade: registra a pendência, avança a posição da
varredura e permite tratar unidades válidas posteriores. A pendência permanece
na consulta independente de completude e volta a ser examinada no ciclo seguinte.
O cursor tipado vincula ator, escopo, instante e
modos; precisa ficar no envelope autenticado do coordenador. Cursor avançado
não esconde trabalho nem transforma adiamento em `complete=true`.

`examined` conta unidades; `erased` conta as projeções de parada/viagem/ocorrência;
`deleted` conta progressos removidos; pin não é contado como exclusão de PII.
Dry-run informa `wouldChange` sem executar o pin/projeções. O executor não executa
limpeza implícita de outra família para conseguir marcar uma ocorrência.

## Integração com as folhas

`WaPrivacyHistoryQueries` exige anchor persistido antes de excluir perguntas,
presenças e decisões. Uma pergunta ligada a outbox só pode sair quando a outbox
está terminal, expirada, sem payload/ciphertexts, sem lease vivo e sem tentativa
SUBMITTING. A manutenção de sete dias precisa ter concluído o conteúdo primeiro.
Outbox, tentativas, suporte e dedup não são excluídos por essa regra.

O ciclo do coordenador pode executar uma batch de cada família: terminal 7d,
técnica 7d, projeção 90d, folhas 90d e perfil 30d. Um adiamento não bloqueia as
outras famílias. A primeira passagem fixa a origem/projeta cópias, as folhas
saem independentemente e outra passagem pode concluir a ocorrência.

Metadados mínimos permanecem deliberadamente, incluindo UUIDs, business keys,
relações e `driver_attendance_notice.kind=STOP_CANCELED`. Não são cópias de nome
ou pin e não representam anonimização completa. Perfis e pontos ativos e
calendário futuro permanecem com seus próprios prazos.

## Prova e limites

As novas fixtures usam T observado no PostgreSQL e fatos T−91 dias. Os testes
cobrem limites de idade, relógio futuro, ocorrência futura, encerramento ausente,
origem faltante/conflitante, pin futuro, perfis ativos, escopo, 30→90/90→30,
recomposição/IDs/digest/estado/marker, novos fatos e interrupção do lote.
O caso de alias NULL relaxa NOT NULL somente no banco sintético adversarial,
insere a linha desconhecida e restaura a restrição em `finally`; o schema do
produto continua NOT NULL.

`WaPrivacyHistoricalControlIntegrationTest` chama os serviços reais de aparelho
e entrega com PostgreSQL e recibos cifrados de replay: status, activate/resume,
samples, STOPPED e autorização tardia de uma outbox expirada. Verifica STOP ou
erro de domínio para chaves antigas/novas, nenhuma amostra aceita, reativação
ou envio. Não é prova HTTP/DPOP de aparelho físico nem de um fornecedor externo.

A evidência incremental executada cobre 63 métodos distintos: 11 de projeção,
14 das folhas adaptadas por pin explícito, 10 do materializador, 11 de descarte
pessoal, sete de fronteira, nove de restauração real e um de controles/replay.
As falhas de startup de contêiner legado por ENOSPC foram separadas dos testes
funcionais; quatro adaptações temporárias de tmpfs foram restauradas exatamente
antes do commit. Novas fixtures mantêm tmpfs de teste. O caso de escopo foi
repetido isoladamente após o ajuste de comparação NULL, sem repetir os verdes.
Logs e recibos sanitizados ficam fora do Git. Não houve VPS, purge de dados reais,
ativação ou fechamento de issues. O startup/upgrade de um schema V43 e a revisão
independente de DDL/guards pertencem à integração conduzida pelo principal.

## Ajustes após revisão independente

A revisão identificou uma fonte existente sem instância conhecida que ficava
fora da consulta e uma pendência semântica que interrompia a paginação antes de
outra ocorrência válida. As duas condições foram corrigidas sem mudar DDL,
contrato do port ou autoridade para descarte.

Quatro métodos dirigidos passaram em PostgreSQL, sem falhas, erros ou skips:
os dois novos casos, o positivo de preservação de controles e o lock ocupado
já existente. O primeiro novo caso mantém um intent REVOKED vencido sem comando
como pendência, com cópias intactas, e verifica a exclusão de fonte comprovadamente
estrangeira do escopo. O segundo mantém duas ocorrências no mesmo banco: a
primeira sem origem continua pendente enquanto a segunda conclui pin, projeções,
folhas e ocorrência em páginas de uma linha. A rodada levou 5,904 segundos nos
testes e 12,098 segundos no Maven. Os 63 métodos anteriores não foram repetidos.
