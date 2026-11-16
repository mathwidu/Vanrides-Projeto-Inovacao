# Folhas históricas em noventa dias

`WaPrivacyHistoryRetention` é um port privado, sem bean, rota, scheduler ou
flag de ativação no runtime. Usa `WaPrivacyPolicy.disabled()` como política
padrão da integração; o construtor sem modo explícito faz dry-run.
Esta fatia não conclui #83/#84 nem a retenção integral de noventa dias.
Não houve ativação, publicação ou limpeza em serviços externos.

## Autoridade e paginação

`tick(WaPrincipal actor, int limit, Position cursor)` exige `SYSTEM_JOB`.
Ambiente e instância vêm da credencial persistida, válida e não revogada,
cercada por `FOR SHARE` até o commit. A instância do ator deve coincidir.
O relógio é `WaClock` do servidor. Transação ambiente é rejeitada antes de
qualquer efeito. O limite de um a cem corresponde a linhas físicas examinadas;
cada exclusão é uma folha, sem DELETE de pai nem cascatas implícitas.

`Position` tipa família, instante do fato e UUID, além de credencial, ambiente,
instância, instante de corte e modos dry-run/synthetic-only. Vence em cinco
minutos. A integração deve envolvê-lo no cursor opaco autenticado da família
`HISTORY_90D`; este port Java não autentica um cursor apresentado pelo cliente.

Uma linha ocupada por outra transação produz `ROW_BUSY` e interrompe a página
antes de avançar o prefixo. Pendências de domínio são reportadas individualmente
e permitem examinar outras folhas, dentro do mesmo orçamento. O cursor apenas
marca a posição da varredura; `complete` é calculado por outra consulta de todo
o escopo, independentemente dessa posição. Ao terminar uma varredura aplicada
com pendências, a próxima posição reinicia o exame. Assim, uma posição avançada
não converte dado pendente em conclusão.

`examined` conta linhas selecionadas e verificadas, `deleted` conta DELETEs
confirmados, `erased` é zero nesta fatia e `deferred` conta os adiamentos da
página. Dry-run mantém os dados e informa `wouldDelete` para folhas deletáveis
no estado atual; não simula a ausência de uma pergunta para seus dependentes.
UUIDs e códigos de pendência não carregam conversa, contato ou coordenadas.

## Elegibilidade e origem preservada

A idade de ocorrência usa `scheduled_at`, jamais seu `created_at` isolado.
Uma viagem precisa estar `COMPLETED` ou `CANCELED`; seu `completed_at` deve
existir e ter noventa dias. A maior data entre o fato e esse encerramento,
mais a criação/resposta/prazo da folha quando aplicável, governa o descarte.
Ocorrência futura, encerramento recente e viagem ativa não vencem pela criação.

A coorte no ambiente autorizado é obrigatória, inclusive com synthetic-only
desligado. Perguntas e decisões exigem coorte tanto do motorista quanto do
passageiro. Uma origem histórica preservada nesta fatia é a cadeia
`start_intent.request_command_id → command_receipt → integration_inbox`,
com motorista, ambiente e instância compatíveis. Comando, intent e inbox não
são excluídos. Outra origem conflitante impede a exclusão.

Alias atual não é usado para autorizar ou esconder fatos históricos. Sem
origem comprovada, a pendência permanece visível sem inferir a instância do fato. Pergunta ou decisão
que constitui a única prova da ocorrência retorna
`HISTORY_ORIGIN_UNATTRIBUTABLE`. O anchor/projeção terminal de noventa dias
pertence a um incremento separado de schema coordenado pelo principal.

| Folha | Condições adicionais / pendências |
| --- | --- |
| `attendance_question` | Origem preservada; criação, resposta e `answer_until` também vencidos. Toda pergunta ligada a outbox é conservada com `QUESTION_OUTBOX_PROVENANCE`, evitando perder a proveniência usada pela manutenção de sete dias. |
| `attendance` | Origem preservada e nenhuma pergunta ainda referenciando a presença. Uma pergunta conservada produz `QUESTION_REFERENCE`. |
| `occurrence_roster_override` | Origem preservada e inbox original no escopo; decisão também com noventa dias. Guard de UPDATE append-only permanece intacto. |
| `support_case` | Somente `CLOSED` com `closed_at` e criação vencidos. DELIVERY usa ambiente/instância explícitos; CONVERSATION exige correlação com comando/inbox original da pessoa. Sem origem comprovada: `SUPPORT_ORIGIN_UNATTRIBUTABLE`. |
| `audit_event` | Data do próprio fato vencida; ator com coorte e origem de comando/inbox ou credencial com instância explícita. Sem origem/coorte: pendência explícita. Credencial histórica não precisa continuar válida; somente a credencial executora precisa. |

Auditoria e suporte antigos com coorte, mas sem origem, são pendências em
qualquer instância do ambiente, mesmo sem alias atual. Auditoria antiga sem
ator nem credencial não tem ambiente atribuível. Ela é
exposta como pendência não destrutiva em qualquer varredura, sem fabricar
escopo. Auditoria atribuída explicitamente a outro ambiente/instância fica
fora do escopo. Nenhuma chave de replay é removida para resolver esse caso.

## Grafo e controles preservados

`operation_run.snapshot_json`, `run_stop` e `routine_occurrence.schedule_snapshot`
continuam pendentes com `HISTORY_GRAPH_PENDING`. Falta de timestamp terminal
produz `MISSING_TERMINAL_AT`. Essas pendências mantêm `complete=false` mesmo
quando todas as folhas seguras já foram removidas.

Não há DELETE/UPDATE de inbox, outbox, mensagens/tentativas, intents,
recibos GPS, sessões, comandos, perfis, aliases, pontos ou cadastros ativos.
A V36 liga suporte DELIVERY a outbox com `ON DELETE CASCADE`: conservar o pai
preserva obrigatoriamente casos abertos ou encerrados recentemente. Uma futura
limpeza do grafo precisa bloquear esse pai até todos os casos dependentes
cumprirem seu próprio prazo; a idade da outbox não substitui `closed_at`.

A limpeza técnica de sete dias e o lifecycle pessoal de trinta dias têm seus
próprios executores. Esta classe não altera suas guardas, cursores ou writers.
A projeção geral dos snapshots exige uma transição de schema específica; as
exceções V44 para perfil fechado e journal não autorizam retenção geral.

## Verificação e retorno

`WaPrivacyHistoryRetentionIntegrationTest` usa PostgreSQL real com duas pessoas
ativas, viagem terminal antiga, encerramento recente e ocorrência futura criada
há noventa dias. Verifica limite físico, idempotência, dry-run, escopo,
credencial, cursor, locks, proveniência preservada, suporte aberto/recente e
esqueletos inalterados. A fixture é sintética e usa migrations reais.
Em 16/11/2026 a classe executou treze testes PostgreSQL: zero falhas, erros
ou skips, em 12,654 segundos (17,507 segundos no Maven). O PostgreSQL usou
tmpfs local de 256 MiB durante a prova por restrição de disco do Mac; essa
adaptação foi removida da fixture antes do commit. A primeira rodada teve
uma falha no próprio teste de lock, que selecionava a cabeça por criação,
em vez da maior data efetiva do fato; o comparador foi corrigido mantendo
a asserção de que nenhuma linha posterior pode ser removida. O recibo final,
os logs e o XML ficam na evidência privada da entrega do principal.
Um ajuste posterior removeu filtros de diagnóstico por aliases atuais. Seus
três métodos direcionados passaram com zero falhas, erros ou skips em 3,537
segundos (9,510 segundos no Maven), incluindo um cenário novo sem alias.
Uma segunda revisão incluiu auditoria de operador com ambiente conhecido,
sem ator e sem instância. Somente o método de órfãos foi repetido: passou
sem falhas, erros ou skips em 1,828 segundos (7,929 segundos no Maven).
A evidência incremental cobre quatorze cenários distintos; a classe inteira
não foi repetida depois desses ajustes de diagnóstico.
Nenhum dado real ou serviço externo participou dessa prova.

Retorno do código: reverter somente o incremento. Sem DDL nesta fatia.
DELETEs já confirmados não são revertidos por rollback do binário; restauração
continua sujeita ao journal e ao gate de recuperação do projeto. A ativação e
o fechamento das issues permanecem decisões do principal e do proprietário.
