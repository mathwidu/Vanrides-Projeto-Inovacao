# Lifecycle e descarte de dados pessoais do WhatsApp

Este incremento prepara a V44 e o pacote `whatsapp.privacy`. Não registra
scheduler, bean habilitado ou rota HTTP e não executa limpeza na homologação.
`WaPrivacyPolicy.disabled()` é o padrão; a prova usa somente coorte sintética.
As issues #83 e #84 não estão concluídas por esta fatia.

## Fechamento explícito e referências preservadas

`WaPrivacyService.close(scope, subjectId)` registra `profile.closed_at` e
`privacy_subject.erase_after`, fecha os vínculos e a participação futura e
revoga capacidades de coleta/pareamento do sujeito. Fechar apenas um vínculo,
pausar atendimento ou parar GPS não cria esse lifecycle. Uma viagem/coleta
ativa bloqueia a operação. UUIDs e a identidade continuam existindo.

O registro `privacy_erasure_ledger` não tem FK para o alvo. Guarda somente
UUIDs, escopo, versão da política, datas e hashes; nenhum nome, alias, pin ou
corpo de conversa entra no journal. Seu estado começa em `INTENT`; payload,
identidade, escopo e hash são imutáveis. Não há exclusão desse registro pelo
serviço. A janela de trinta dias parte do fechamento registrado, sem inferir
uma data para perfis antigos que já estavam `CLOSED`.

## Journal obrigatório

`WaPrivacyJournal.append(entry)` deve ser idempotente pelo `eventId` e retornar
somente após conteúdo e metadados do arquivo estarem duráveis. `verify` deve
reler/verificar o registro e a cadeia. `Ack.chainDigest` identifica o registro
original da cadeia, permanecendo estável quando novos registros são anexados.
O hash do payload canônico precisa coincidir com o hash persistido.
Ao importar o journal numa restauração, preservar `eventId`, a sequência de
origem e as datas canônicas de fechamento/prazo. Não substituir `closedAt`
pelo horário do restore; `close` é uma operação normal, não uma API de importação.

`WaPrivacyJournal.unavailable()` nega a operação. Sem ACK verificável o perfil
pode permanecer fechado, mas o ledger continua `INTENT` e nenhum payload é
apagado. Um journal em memória valida o protocolo em teste; não comprova
persistência operacional, recuperação ou integridade de um arquivo real.

O principal deve implementar o adapter privado de filesystem e o gate de
restauração: journal ausente, incompleto ou adulterado bloqueia a reabertura
da API, coleta e envio. Um dump antigo deve receber o journal atual e reaplicar
os fechamentos/descartes antes de servir tráfego. Este pacote não atesta isso.

## Projeção pessoal terminal

`erase(scope, subjectId)` exige prazo vencido, perfil fechado, journal
confirmado e ausência de referência operacional ativa. Os autores de cópias
são cercados pelo lock de perfil; se estiverem ocupados, há adiamento.

A V44 permite somente a projeção determinística dos campos pessoais de sujeitos
elegíveis em `operation_run.snapshot_json.participants` e `passengerNames`.
Os guards originais continuam protegendo UUIDs, donos, ordem, presença,
instantes/política do snapshot e os campos de outros passageiros. Em
`run_stop`, coordenadas e referência ficam nulas com marca de descarte;
nenhuma coordenada fictícia ou nome substituto é persistido. Os pontos,
aliases e perfis mantêm suas referências como esqueletos sem payload pessoal.

Também são consideradas as cópias atribuídas em razões operacionais, inbox,
outbox e rascunhos. Mensagens compartilhadas de listas/resumos que contêm o
sujeito fechado perdem o conteúdo transitório inteiro, preservando destinatário,
chaves e recibos. Isso não reescreve a presença ou o snapshot dos demais.
O descarte só retorna `ERASED` após a transação concluir as cópias mapeadas;
uma repetição retorna `ALREADY_ERASED`, sem novo evento ou efeito.

## Inventário da baseline e dependências restantes

| Família | Regra | Responsabilidade neste incremento / pendência |
| --- | --- | --- |
| Posição atual e aproximação | Remover ao terminar/parar/revogar/expirar | O runtime de tracking pertence ao principal; não é alterado por este pacote. |
| Recibos GPS sem coordenadas | Sete dias, sessão encerrada | Contrato para o tick geral; preservar sequência/geração e recibos úteis. Ainda não executado aqui. |
| Contexto e conteúdo transitório | Sete dias de inatividade humana | Descarte das cópias do sujeito fechado nesta fatia; limpeza geral de sete dias ainda depende do tick. Pausa e chaves não são removidas. |
| Perfil, alias, ponto e cópias | Ativos; até trinta dias após fechamento explícito | Lifecycle, ledger e projeção V44 nesta fatia, sujeitos ao journal durável. |
| Presença, decisões, auditoria e suporte encerrado | Noventa dias do fato/encerramento | Preservados na prova de 31 dias. A limpeza de noventa dias e suas dependências ainda exigem implementação/ensaio; ocorrências futuras não vencem pela criação. |
| Credenciais, provas, pareamentos e intents | Revogar ao fechar; descarte após validade | Revogação no fechamento; descarte geral ainda pendente. `rotated_to`, hashes/chaves de replay e último recibo não podem ser apagados por cascata. |
| Backups próprios e fornecedores | Sete dias nos backups próprios; journal reaplicado antes do uso | Adapter externo, inventário, restore e prova fail-closed pertencem à integração seguinte. Não há alegação de exclusão em fornecedores. |

`proof_replay` não possui ambiente; limpeza geral não pode atribuir por suposição
um escopo a uma chave. Ciphertexts de `operation_replay` são outra cópia a
considerar: os leitores atuais recusam registros vencidos antes de decriptar,
mas excluir a linha inteira também remove a chave de idempotência. O executor
geral precisa preservar esse esqueleto ou comprovar a segurança de outra regra.

## Contrato para integrar o tick

O chamador autenticará `SYSTEM_JOB`, usará relógio do servidor, escopo explícito
de ambiente/instância e lote entre um e cem sujeitos. Esse limite vale para a
seleção; a projeção de um sujeito considera suas cópias na transação. O tick
geral ainda precisa orçamentar esse fanout e os lotes de cada família.
`candidates` lista fechamentos
vencidos; cada `erase` devolve estado, contadores por cópia e adiamentos.
`ACTIVE_REFERENCE`, `COPY_WRITER_BUSY`, `NOT_DUE`, `JOURNAL_PENDING` e
`OUT_OF_SCOPE` não são sucesso de descarte. O tick deve continuar disponível
com novos inícios/envios desabilitados e não fabricar inbox humana.

`REQUEST_DATA_DELETION` e uma interface de fechamento autenticada precisam ser
alinhados pelo principal; a disponibilidade desta classe não concede acesso
HTTP, nem autoriza limpeza dos participantes da homologação.

## Validação e retorno

`WaPrivacyErasureIntegrationTest` usa PostgreSQL real e dados sintéticos:
dois passageiros na mesma viagem terminal, fechamento de um, passagem de
31 dias, ausência dos seus dados nas cópias, preservação do outro e dos fatos
de presença/auditoria, repetição, prazo, escopo, falta de journal e guards.
A rodada também cobre projeções do motorista, ACK adulterado e um autor de
cópia ocupado. Resultados executados devem constar do recibo da frente; a descrição do teste
não substitui sua execução. O journal em memória não é prova operacional.

Retorno do código: reverter o incremento sem remover tabelas/colunas ou ledger
existentes. Dados efetivamente apagados não voltam com rollback do binário.
Um backup anterior não pode ser servido para recuperar PII descartada: o
journal atual deve ser reaplicado e verificado antes da reativação.
