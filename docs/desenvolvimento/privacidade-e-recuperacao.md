# Privacidade e recuperação do MVP

Estado em 16/11/2026: arquitetura técnica escolhida para concluir #83 e #84.
Este documento não atesta exclusão nos fornecedores nem aprovação jurídica.
O aviso deve refletir os prazos antes da ativação com pessoas reais.

## Regras de armazenamento

| Família | Prazo e destino |
| --- | --- |
| Posição atual e três amostras de aproximação | Remover ao encerrar, parar, revogar ou expirar a coleta. |
| Recibos GPS sem coordenadas | Sete dias em sessão encerrada, mantendo sequência e geração contra replay. |
| Contexto e rascunhos de conversa | Sete dias de inatividade humana; preservar pausa, chaves e controle necessário. |
| Conteúdo transitório de inbox/outbox terminal | Sete dias da criação da mensagem; atividade nova não renova o prazo de mensagens antigas. |
| Perfil, alias, ponto e suas cópias | Enquanto ativos; descarte em até 30 dias do fechamento registrado. |
| Presença, decisões, auditoria e suporte encerrado | Noventa dias do fato/encerramento; ocorrências futuras não vencem pela data de criação. |
| Credenciais, provas, pareamentos e intents | Revogar no fechamento; descartar após validade sem perder proteção de replay e parada. |
| Backups próprios do MVP | Sete dias, com inventário e exclusões reaplicadas antes de restaurar o uso. |

Os prazos vêm da baseline do MVP. A regra do conteúdo transitório terminal em
sete dias da mensagem, independente da inatividade do contexto, e do suporte
encerrado em 90 dias é a definição técnica desta entrega.
Metadados mínimos que ainda impedem replay ou sustentam uma referência válida
permanecem como registros sem conteúdo pessoal; isso deve ser distinguido de
exclusão física completa.

## Descarte sem alterar a história de outros passageiros

Excluir a identidade inteira quebra referências entre presença, embarque e
viagem. Apagar apenas o nome ou o ponto também deixa cópias nos snapshots e
nas mensagens. A V44 terá ciclo explícito de fechamento/descarte, registro de
exclusões e projeção terminal restrita dos campos pessoais.

A escolha é conservar UUIDs, donos, ordem e fatos operacionais necessários,
removendo nome, contato roteável, coordenadas e referências da pessoa fechada.
Campos apagados usam ausência explícita, sem coordenadas fictícias. Os guards
continuam rejeitando alterações operacionais: somente uma transição monotônica
de descarte, autorizada pelo registro de exclusão e no estado terminal, pode
remover os campos definidos. Não haverá desativação de triggers nem um bypass
genérico. Fechar vínculo, parar GPS e pausar suporte não fecham o perfil.

O job de retenção usa credencial SYSTEM_JOB, relógio do servidor e lote máximo
100. Cada família informa quantidade examinada, apagada e adiada. Uma referência
ativa ou falha de journal deixa pendência explícita. A manutenção continua
possível quando novos inícios ou envios estão pausados.

## Cópias e restauração

O registro de exclusões precisa de journal privado fora do banco restaurado.
A intenção é registrada primeiro; o descarte só é confirmado após sua cópia
durável no journal. Restaurar um dump antigo exige o journal atual, com sequência
e integridade verificadas, e a reaplicação idempotente das exclusões antes de
liberar a operação. Journal ausente, incompleto ou adulterado bloqueia o retorno.

O ensaio usa schema real, API, worker, workflows/configuração próprios do n8n
e canal simulado, com versões identificadas e sem saída para contatos reais.
O retorno começa com coleta/intents/tokens e conexão de envio desabilitados;
a reabilitação é uma etapa controlada e testada. O backup simplificado anterior
continua histórico e não comprova recuperação desse conjunto.

## Prova mínima

Criar uma viagem encerrada com dois passageiros e suas mensagens/snapshots.
Fechar somente um e avançar 31 dias. Conferir que seus campos pessoais somem
em todas as cópias, que o outro passageiro permanece intacto, e que presença,
chaves e guards continuam válidos. Repetir a limpeza sem novos efeitos.

Restaurar uma cópia anterior ao descarte em banco separado, reaplicar o journal
atual e repetir as verificações. Webhook, token e submit antigos não podem
recuperar dados nem produzir novos efeitos. A evidência deverá registrar também
as famílias ainda adiadas e os limites dos serviços externos.

A implementação, revisão, ativação e resultados são registrados separadamente
na evidência da entrega; esta descrição não equivale a teste executado.

## Journal no host

O adapter em preparação usa arquivo JSONL e uma cabeça de cadeia, fora do
backup do banco, em diretório privado. Registros contêm somente UUID, escopo,
política e datas; não contêm contato, nome, conversa ou GPS. O ACK só é emitido
após gravação, sincronização do arquivo e publicação atômica da cabeça, com
sincronização do diretório. Uma inicialização vazia é operação explícita de
primeiro provisionamento, não resposta automática a arquivo perdido.

A restauração deve conservar o journal atual e sua cabeça confiável, sem
substituí-los pelos de um dump antigo. Arquivo ausente, escrita parcial ou
cadeia/cabeça divergente bloqueiam a operação. O filesystem e seu proprietário
são a autoridade operacional; a cadeia detecta divergência, não é uma promessa
de resistência a um administrador que altere todos os arquivos e referências.
O adapter, sua integração e o ensaio de restore têm resultados separados.

## Preparo da retenção física dos backups

O helper [`backup_retention.py`](../../deploy/homologacao/backup_retention.py)
prepara catálogo e plano de sete dias. Não há cron, timer ou ativação automática.
O mantenedor opera a ferramenta em Linux/macOS com Python 3, em diretório
canônico explicitamente escolhido, pertencente ao operador e privado (0700).
Cada `.dump` deve ser arquivo regular privado (0600), sem symlink/hardlink,
com cabeçalho PostgreSQL custom e até 64 MiB. Somente nomes simples de filhos
diretos são aceitos; a ferramenta não percorre subdiretórios. Não corrige
permissões de arquivos existentes nem acessa banco, Docker ou serviços.

O catálogo `.backup-retention.json`, os planos, as aprovações e os recibos são
privados (0600). O registro conserva SHA-256, tamanho, identidade do arquivo,
dataset lógico e `recordedAt` UTC estável. `createdAt` só deve ser informado
com uma data original comprovada por evidência independente. Para o legado sem
essa prova, ele permanece `null`: a idade histórica é **UNKNOWN**, e o prazo
conservador só vence sete dias após o registro. Nome, mtime e ctime não datam
o backup; estes dois últimos servem somente para detectar alterações.
O inventário antigo do ensaio, baseado em ctime e com `deletionImplemented=false`,
continua sendo uma prévia histórica, sem comprovar cumprimento dos sete dias.

O dataset identifica o banco/conjunto lógico, por exemplo `api` e `n8n`.
Um dump restaurável de um dataset não substitui o de outro. Para cada dataset
que tenha arquivos a eliminar, deve restar no mesmo catálogo uma cópia íntegra,
não selecionada, cujo hash tenha restauração atestada pelo operador.
`--restored-sha256` precisa coincidir com o dump e
`--restore-evidence-sha256` identifica o recibo independente guardado à parte.
Isso registra uma **declaração do operador**: o helper não executa nem valida
o conteúdo de um restore. Sem cópia restante assim atestada, o plano bloqueia:
gerar novo backup, restaurá-lo isoladamente e registrá-lo com outro nome.
Cada arquivo é registrado uma vez; registrar novamente não renova sua idade.
Essa proteção não comprova a consistência temporal entre vários bancos ou
a recuperação de todo o conjunto sem os respectivos ensaios.

O inventário de 16/11 encontrou 14 dumps próprios em 11 pastas de tentativas.
O helper trata cada pasta como um escopo independente; não executa uma rotação
global desses arquivos. Uma pasta com apenas um dump não poderá eliminá-lo
sem outra cópia restaurável do mesmo dataset naquele catálogo. A prévia não
autoriza mover arquivos legados nem considerar cumprido o prazo histórico.

### Próxima prévia na VPS, sem exclusão

O principal pode copiar os dois arquivos Python para uma pasta privada de
versão, conferir seus hashes contra o commit revisado e executar ali:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest -v backup_retention_test.py
```

Os testes criam e apagam exclusivamente fixtures descartáveis dentro de
`TemporaryDirectory`. Não apontar os testes para um diretório de backups.
Depois, escolher o diretório real exato e os basenames existentes, sem copiar
journal/head para outro lugar e sem modificar dumps em uso. Exemplo de registro
legado e prévia; os caminhos/nomes devem ser conferidos pelo operador:

```sh
umask 077
retention_tool=/caminho/privado/versao-revisada/backup_retention.py
backup_scope=/caminho/canonico/privado/do/ensaio
python3 "$retention_tool" --directory "$backup_scope" register \
  --dump api-antigo.dump --dataset api
python3 "$retention_tool" --directory "$backup_scope" plan --output retention-plan-01.json
```

O registro acima mantém criação desconhecida e não declara restauração.
Para uma cópia já restaurada, acrescentar os dois hashes exatos do atestado
no momento do registro. Para uma cópia nova com data comprovada, informar
`--created-at` no formato UTC, por exemplo `2026-11-16T12:00:00+00:00`;
não reutilizar a data do exemplo como dado real. A saída pública contém somente
status, hash do plano e contagens; detalhes dos arquivos ficam no plano privado.
Revisar `delete`, `keep`, datasets, datas, hashes e recibos de restauração.
Um plano vazio é esperado quando as cópias legadas acabaram de ser registradas.

### Aprovação futura vinculada à lista concreta

Não há aprovação de purga real nesta entrega. Após eventual confirmação do
usuário sobre a lista revisada, o mantenedor pode registrar a aprovação:

```sh
reviewed_plan_sha=SHA256_EXATO_DO_PLANO_REVISADO
python3 "$retention_tool" --directory "$backup_scope" approve \
  --plan retention-plan-01.json --plan-sha256 "$reviewed_plan_sha" --output retention-approval-01.json
python3 "$retention_tool" --directory "$backup_scope" apply \
  --plan retention-plan-01.json --plan-sha256 "$reviewed_plan_sha" --approval retention-approval-01.json
```

`approve` só grava a guarda privada para aquele hash; não apaga arquivos.
`apply` sem `--execute` é **SIMULATION_ONLY**. Somente uma nova chamada explícita
com `--execute`, o mesmo plano/hash e sua aprovação pode remover os `.dump`
enumerados. O plano vale 24 horas; alteração no catálogo, no escopo ou em qualquer
arquivo registrado exige nova prévia e nova aprovação. Arquivos não cadastrados,
journal, cabeça confiável, segredos, logs e volumes não são alvos de retenção.

A ferramenta usa lock exclusivo cooperativo e revalida identidade, conteúdo,
prazo e cópias restantes. Na execução, move os candidatos para uma pasta nova
privada `.retention-purge-*` e os confere antes de qualquer unlink. Uma troca
detectada nessa conferência bloqueia e conserva os arquivos para inspeção;
não remover essa pasta automaticamente. Entre a conferência e o unlink por
nome ainda existe uma janela de troca por outro processo com o mesmo usuário.
O lock é cooperativo e não impede esse escritor. O operador deve manter todos
os outros escritores parados até o fim; sem essa condição, não executar a purga.
Uma falha de I/O pode interromper o lote: conservar catálogo, plano, aprovação,
recibo e pasta de preparo e reconciliar o que ocorreu, sem repetir às cegas.
O catálogo atualizado impede reutilizar uma aprovação concluída.

Os testes sintéticos não comprovam expurgo real, recuperação de arquivo apagado,
apagamento físico de blocos, retenção em fornecedores ou rotação em produção.
O ensaio de restauração com exclusões reaplicadas é uma prova separada;
journal/head atuais continuam obrigatórios antes de liberar consumidores.

## Paginação das rotinas

A manutenção conserva o mesmo corte de datas enquanto percorre uma lista
finita. Cada página aceita renova a validade do cursor autenticado; um corte
antigo restringe os dados elegíveis e não substitui a conferência atual de
credencial, instância, coorte ou referências ativas. Ao terminar a lista, a
resposta não traz continuação, mesmo se houver pendências. A visita seguinte
usa um corte novo. Assim os registros que venceram depois também recebem vez.

Pendências de origem ou dependência ficam registradas e permitem alcançar os
demais candidatos. Locks, envio em SUBMITTING e leases ainda válidos preservam
o prefixo para outra tentativa. A regra de idade usa o corte; a verificação
de lease usa o horário operacional atual. Um lease vencido não pode continuar
ocupado apenas porque a varredura começou antes do vencimento.

Os perfis usam a ordem `erase_after, subject_id`, com filtro de coorte antes
do limite. O worker alterna duas famílias por minuto, com páginas de 20 e até
cinco segundos para a manutenção. A escolha foi ajustada após medir cerca de
um segundo para um lote de 20 perfis. O cursor fica em memória, com quatro
minutos de inatividade local e cinco minutos por emissão na API. Isso atende
ao ciclo contínuo do MVP; interrupções ou locks permanentes continuam sendo
pendências operacionais, sem promessa de progresso durável entre reinícios.

A completude é conferida sobre toda a família e seu corte, sem usar a posição
do cursor para esconder registros. Essa decisão foi revisada separadamente e
exercitada com pendências antes de candidatos válidos, cortes acima de cinco
minutos, revogação e renovação do envelope. A ativação de descarte real continua
dependendo da composição publicada, da prévia e da decisão do mantenedor.
