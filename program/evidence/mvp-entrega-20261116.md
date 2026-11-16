# Entrega técnica do MVP — 16/11/2026

## Integração da presença

Os PRs #113 e #121 foram integrados com a autoria e os ancestrais preservados.
O aviso ao motorista usa V42; V41 continua sendo o cadastro por convite.
A integração inclui seleção por data/sentido, lista do motorista, corte da
alteração e avisos de saída/cancelamento com revalidação antes do envio.

A frente registrou 250 casos Java em 27 classes em rodadas direcionadas,
com zero falhas, erros ou pulos no conjunto final dos relatórios. A primeira
rodada revelou uma expectativa antiga de migração e uma restrição do Docker
local; os 23 casos afetados foram repetidos após as correções. Não é uma
execução única limpa da suíte completa. Também passaram 714 testes Node,
303 Python, 46 verificações de wiring e 11 do agrupamento PostgreSQL.
Esses resultados cobrem o laboratório, sem provar entrega real dos novos
avisos no WhatsApp. O recibo detalhado permanece privado no Mac.

## Recuperação de conversa pela equipe — #80

A consulta informa se a conversa está pausada e identifica o caso atual.
A retomada exige sessão GitHub ativa, CSRF, contato confirmado pela Evolution,
coorte correta e perfil ativo ou em cadastro. A transação encerra somente o
caso selecionado, invalida confirmações antigas, preserva os dados de cadastro,
o aceite e as permissões, e registra o identificador GitHub do operador.
Repetir uma retomada antiga não encerra uma nova pausa.

Verificações executadas no candidato local:

- `TeamConversationRecoveryIntegrationTest`: quatro cenários PostgreSQL,
  incluindo preservação de permissões, rollback após perda da sessão,
  repetição contra uma nova pausa e recusa de perfil/coorte incompatível.
- `WaTeamWebBoundaryTest` e `WaTeamSessionsTest`: oito casos aprovados.
- `node --test scripts/homologacao/team_ui_test.mjs scripts/homologacao/team_simulator_test.mjs`:
  15 casos aprovados, incluindo vínculo entre telefone/caso e proteção CSRF.
- `python3 src/backend/scripts/qa_test_groups_test.py`: 11 casos aprovados;
  a matriz inclui a nova classe PostgreSQL.
- `python3 scripts/check_repository.py`: links, backlog e documentos válidos.

A primeira execução identificou somente o tipo de erro esperado para coorte
incompatível; a expectativa foi corrigida para `ADMISSION_CONFLICT` e os quatro
casos PostgreSQL passaram na repetição. O armazenamento temporário em memória
foi usado apenas no ensaio local devido ao disco do Docker e removido dos
fontes. A publicação desta etapa foi concluída no PR #122; a conferência pelo WhatsApp continua pendente.

## Limites desta etapa

A V42/presença/suporte está publicada. Finalização V43, privacidade, nova versão
do aplicativo e a jornada real do conjunto ainda precisam da integração e publicação próprias. Percurso físico, medição de bateria,
utilidade do aviso e aceite final ficam para a rodada do proprietário. As
issues correspondentes permanecem abertas até existir essa evidência.

## Finalização, ensaio e revisão integrada

Foram integrados os commits de finalização e dos aplicativos. A finalização
manual usa revisão e confirmação da viagem atual; a automática usa progresso,
destino do snapshot e permanência com precisão/velocidade válidas. O encerramento
revoga autorizações de coleta, solicita STOP e aguarda ACK real. Parar GPS
continua sendo uma ação técnica distinta. A última posição e as amostras de
aproximação são removidas na parada, revogação, expiração e ACK.

A revisão independente encontrou três falhas que receberam correção:
ordem de locks do aviso de cancelamento, ordem de locks da retomada e perda
da pausa de suporte ao salvar uma revisão de finalização. Os testes dirigidos
também mostraram que a fila parava antes de alcançar o comando de finalização
durante suporte. A fila agora consulta as capacidades da API: mensagens sem
capacidade são concluídas sem IA ou comando; a conversa permanece pausada,
e uma finalização posterior pode ser processada.

Resultados finais das rodadas dirigidas desta integração:

- `WaRunFinishPolicyTest`: 8 aprovados.
- `WaRunFinishIntegrationTest`: 13 aprovados, incluindo revisão, abandono,
  confirmação e ACK durante suporte, com o mesmo chamado aberto.
- `WaTrackingLifecycleIntegrationTest`: 6 aprovados, com verificação da remoção
  da posição; `WaApproachIntegrationTest`: 18 aprovados.
- `TeamConversationRecoveryIntegrationTest`: 5 aprovados; a concorrência verifica
  a ordem profile→identity e a preservação das referências de negócio.
- `WaStopCanceledNoticeIntegrationTest`: 8 aprovados; a concorrência verifica
  profile/conversa antes de viagem/parada e recusa motorista suspenso.
- `WaConversationFencingIntegrationTest`: 7 aprovados, mantendo a pausa e a
  ausência de capacidades para mensagens de negócio.
- `WaRoutineEligibilityIntegrationTest`: 5 aprovados; `MvpOnlineModeTest`: 16;
  `MvpRehearsalConfigurationTest`: 5, após alinhar a expectativa aos erros de
  configuração emitidos pelos dois validadores existentes.
- `python3 scripts/check_whatsapp.py`: 719 Node e 303 Python aprovados.

As primeiras rodadas e suas falhas foram preservadas em logs privados. A espera
nos dois testes concorrentes foi corrigida para atualizar o snapshot de
estatísticas PostgreSQL antes de observar outro backend; não foi removida a
verificação da ordem de locks. Somente os cenários afetados foram repetidos.

A flag `WA_MVP_REHEARSAL_ROUTINES` permanece desligada por padrão. Quando
explicitamente habilitada, somente na homologação online com a coorte real
configurada, rotinas e motoristas ativos, consentimento e aparelho autorizado
podem participar. Os três consumidores compartilham a mesma elegibilidade.
Isso prepara a equipe para ensaiar novas rotinas sem copiar UUIDs no servidor.
Os parâmetros de chegada são iniciais de ensaio e ainda dependem de percurso.

O [PR #122](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/pull/122)
publicou a primeira etapa (V42/presença/suporte), preservando os commits dos
PRs #113 e #121. Na revisão isolada `5647bd3`, 44 casos PostgreSQL passaram:
retomada 5, aviso de cancelamento 8, saída 6 e seleção de presença 25.

Antes da publicação foi realizado backup real do banco e restauração em
PostgreSQL isolado, sem volumes/rede do serviço ativo. A comparação do conteúdo
restaurado passou. Três inicializações foram aprovadas: API anterior/V41,
candidata/V42 e API anterior/V42. A primeira tentativa do auxiliar falhou na
configuração de logs do container de ensaio; os containers próprios foram
removidos, a configuração foi corrigida e a segunda tentativa passou. Nenhum
volume do ambiente ativo foi removido.

O merge efetivo foi `480d8dcedfc329e92e33804c4088b4d5e0cba837`.
A [publicação automática](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/43808812256)
passou na tentativa 2. A primeira interrompeu antes de alterar o serviço,
pois a aprovação de migração ainda não estava preparada para o SHA de merge.
A VPS confirmou imagem `sha256:f0bdb8f775b061ba4a83a555cbdd6bb7deb7697d4868fe532dbc1271e4871f09`,
JAR `1da3c73de71dc4e23919b8b7697acecf53978ccbcfa5711017658ed7ae18c365`,
Flyway V42 sem falhas, tarefa convergida e HTTPS `UP`. A aprovação temporária
foi consumida. Isso comprova publicação e saúde; não substitui a conferência
dos novos avisos no WhatsApp real.

## Aplicativos

A frente executou 48 testes Flutter, 116 execuções Kotlin, 15 testes Swift e
seis testes Python, com analyzer sem problemas e lockfiles intactos. O runner
iOS passou na checagem de tipos do SDK físico. O APK Android 0.4.0+14 foi gerado em 75,49 s, com assinatura e isolamento
MVP verificados. SHA-256:
`2b4667111bc39bf4c9dcc687168086c6a9ed5b334a3f7db1cf7e3faf83c996b5`.
A assinatura coincide com a versão anterior e a instalada no Redmi; a
instalação física está em andamento. Dois lockfiles e 480 fontes do aplicativo
permaneceram iguais. JDK e compatibilidade DartDefines estão isolados do
ambiente pessoal, sem editar o SDK. O build iOS está em preparação. Esses
resultados ainda não comprovam os fluxos físicos dos novos builds.

## Publicação da finalização — V43

O [PR #123](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/pull/123)
foi integrado em `880ea56c235990eb58a4e84f2f7a158db68d4892`.
Antes do merge, o JAR de `b8c8c6d` passou nos 13 casos PostgreSQL de finalização
e foi empacotado. A adaptação tmpfs permaneceu apenas no ensaio local.

Foi realizado novo backup real e restauração isolada, com conteúdo comparado
e probes API anterior/V42, candidata/V43 e anterior/V43 aprovados. O manifesto
v2 permitiu somente as duas novas propriedades, com default false, mantendo
inalterados os bytes das demais configurações, bibliotecas e SQLs anteriores.
O helper v2 passou em 21 testes Python/mock/ZIP.

A [execução da main](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/43811921133)
passou na primeira tentativa. A VPS confirmou V43 sem falhas, HTTPS UP,
imagem `sha256:ee467926fe9b1bf105959a02581afedc449b700092ab2c5ce1b96612b419aeff`
e JAR `780f7b8ff0367c4d91402488012f87a585a1186ef35b35374cf0c5e2ea6b138c`.
O worker usa o bundle de 52 arquivos do mesmo candidato; hash do arquivo
`a74d558b00baf1ddfbd45bd5b1210b1022c712f31fa6abbe1010e6e3a747c017`,
healthz UP. A origem anterior foi preservada para retorno.

O APK 0.4.0+14 foi instalado por atualização no Redmi, com certificado igual
ao anterior. O download público foi atualizado em serviço separado e o hash
HTTPS coincide com o APK gerado. A versão 13 foi preservada para retorno.
Não houve mudança no banco por essa atualização de arquivo.

O preparo iOS (pub com lock, configuração de release e Pods deployment) passou.
A única tentativa de xcodebuild terminou com código 65: No Accounts/perfil
ausente. HOME/usuário corretos, certificado existente e fontes/locks intactos;
isso não prova remoção da conta. A sessão/chaveiro do Mac precisa estar
disponível e a conta/Personal Team deve aparecer no Xcode antes de repetir.
Nenhum app iOS assinado novo foi produzido.

A captura/automação da tela do Redmi falhou nesta rodada, embora a atividade
do novo app conste como retomada no Android. Isso não foi contabilizado como
smoke visual ou GPS. A sessão do Mac também estava bloqueada ao abrir WhatsApp.
Jornada real, habilitação controlada dos avisos e rodada física continuam
separadas dos resultados de build, instalação e publicação acima.

## Privacidade em integração — sem limpeza real

V44 e o lifecycle foram integrados. A correção passou em 53 casos únicos/7
classes PostgreSQL: mensagens recentes de terceiros preservadas, cópias
reais de motivo/revisão removidas depois do prazo, filas fora dos locks de
perfil, gate de completude e autoridade entre transações. Falhas iniciais de
fixture foram preservadas, e somente os casos afetados foram repetidos.
Uma revisão posterior identificou lifecycle multi-instância; o addendo
`ae3a953` nega fechamento/descarte nesse caso, com sete casos Boundary
aprovados, sem pulos. A matriz passou a 94 classes/745 casos PostgreSQL.

O journal de filesystem passou em 15 testes no Mac, sem pulos. Incluem
inicialização explícita, escrita parcial, retry após falha de fsync do
diretório, JSON estrito, datas da política, snapshot atômico e duas JVMs
separadas disputando o FileLock. A primeira rodada falhou no caminho
canônico da fixture temporária do macOS; o fixture passou a usar toRealPath,
sem enfraquecer a validação do adapter. O teste é específico de POSIX; não
certifica Windows nem o filesystem da VPS.

A revisão independente do adapter encerrou estaticamente dois achados de
durabilidade e parsing, corrigidos antes da rodada verde. O journal ainda
não está vinculado ao runtime/restore; retenção geral e recuperação do
conjunto seguem em implementação. A V44 não foi publicada, a limpeza real
está desabilitada e #83/#84 permanecem abertas.

O envelope de continuação da manutenção passou em seis testes unitários:
AEAD vincula credencial/instância/ambiente e validade de cinco minutos; cursor
fora do escopo, adulterado ou vencido é recusado. Ainda não existe ativação
do job pela rota ou n8n.

O primeiro ensaio real de recuperação em fila foi interrompido em PREPARE:
a fixture combinava perfil local com provedor de e-mail desativado, que o
guard do produto corretamente recusa fora do modo publicado. Nenhuma agenda
foi aprovada nessa rodada. Os containers próprios foram removidos com
identidade conferida, os volumes sintéticos foram retidos e os serviços
compartilhados não foram alterados. A correção pertence à fixture, sem
enfraquecer o guard da aplicação. Recibo e logs permanecem privados.

### Recuperação em fila — núcleo aprovado

A tentativa isolada 004 terminou como `CORE_VERIFIED_PRIVACY_PENDING`, com
`releaseEligible: false`. Foram usados o JAR `4317e5c7` de `b8c8c6d`, Flyway
V43, n8n em modo de fila, Redis com AOF, API, worker e canal simulado. O hash
do manifesto é `ebf12a4d7add0549f9b2c17673aaf9e7bbd5d2e3cafed5adf3b596a0c49a1237`.
O JRE veio da imagem publicada; a identidade do JAR candidato é registrada
separadamente. Não foi um ensaio com Evolution ou WhatsApp real.

Quatro verificações passaram na VPS, sem reiniciar serviços compartilhados:

- A agenda publicada enfileirou trabalho no Redis com o worker parado.
- O reinício retomou a pendência uma vez e as quatro agendas dispararam.
- PostgreSQL, Redis, API e workers retomaram nos mesmos volumes sem reenviar
  respostas já concluídas.
- Dois dumps reais foram restaurados em outro PostgreSQL; a pendência foi
  concluída e a repetição dos eventos não produziu efeitos novos.

Foram dois eventos sintéticos, dois efeitos no provedor simulado e zero
efeitos duplicados. O backup ocorreu com produtores quiescentes e a pendência
estava no inbox durável da API: não é prova de restauração de qualquer job
Redis arbitrariamente interrompido durante envio. Os containers exclusivos
foram removidos com identidade conferida; os volumes de diagnóstico foram
preservados. A reaplicação V44 ainda consta como pendente e não foi substituída
por um recibo externo de sucesso.

As tentativas 002/003 encontraram, respectivamente, ausência do diretório
de cache e falta de espaço no tmpfs do n8n. A solução manteve raiz somente
leitura e rede interna: cache de 128 MiB e limite de memória de 1 GiB por
main/worker. No ensaio aprovado, o cache usou 47.704 KiB, acima da capacidade
anterior de 32 MiB. `/tmp` usou 20 KiB e `.n8n`, 40 KiB. São medições de disco
tmpfs nessa rodada, não de consumo RSS nem recomendação para carga real.

### Retenção e restauração — ports integrados

Além dos testes de base, a retenção técnica passou em 14 cenários únicos de
três classes PostgreSQL, incluindo serviços reais de transporte e suporte
com GPS sintético. O histórico passou em 14 cenários e o port de restauração,
em nove. A prova deste último inclui pg_dump anterior ao fechamento,
pg_restore controlado, journal atual, reaplicação e descarte, preservando a
outra pessoa e os controles. Não equivale ainda ao ensaio conjunto com n8n.

Dados sem origem atribuível ficam explicitamente adiados; hashes e controles
mínimos de replay não são anunciados como anonimização completa. Fechar
perfil não é consequência automática de parar GPS ou encerrar viagem.

### Rotina do worker preparada, ainda desabilitada

O W08 conserva seu contrato e os oito exports. O novo adapter, desligado por
padrão, pede uma página de 20 unidades para duas famílias por execução, em
rodízio e com até cinco segundos após a reconciliação. Cursores e detalhes permanecem privados
no worker; o n8n recebe somente seu resumo de entrega. Logs são agregados e
só saem quando há trabalho/falha e mudança do resumo.

Passaram 58 testes Node direcionados nos cinco arquivos de teste afetados,
incluindo configuração, HTTP, limites, tempo excedido, continuação por família,
ausência de vazamento no resumo e regressão do servidor W08. Não foram
adicionadas suítes à CI automática. Publicação da V44, journal na VPS e
ativação de descarte real continuam separados dessa prova offline.

A matriz local agora inventaria 99 classes/782 casos PostgreSQL, incluindo
histórico, restauração e retenção técnica. Isso é inventário, não afirmação de
uma execução integral. Os 11 testes do seletor passaram; sua união continua
exata, sem classes omitidas ou repetidas. Os pesos resultaram em diferença de
3,105 s entre grupos de cerca de 180 s (1,73%). O teste de balanceamento passou
a aceitar até 5% da média, com mínimo de 3 s, evitando ajuste a cada pequena
mudança de inventário. Contagens, autenticação dos recibos e exigência de zero
falhas/erros/pulos foram preservadas. O fluxo automático de build/deploy não
foi alterado.

O teste do runtime mediu 1.036 ms e 999 ms no coordenador para 20 perfis,
incluindo o journal real. Por isso o worker passou a reservar até 2,5 s por
família em rodízio, mantendo 5 s totais e o orçamento anterior da reconciliação.
Uma falha/adiamento continua cedendo vez às outras famílias. Os nove casos do
adapter Node passaram novamente, incluindo prazo por página, cursores vencidos
e progresso independente. Uma resposta tardia é ignorada no worker; isso não
promete cancelamento da transação da API. Latência e execução dessa rotina na
VPS ainda precisam ser verificadas na composição final.

### Composição revisada de paginação e recuperação

O runtime privado foi integrado de `ed2e754`: cinco famílias, onze campos de
resposta, autenticação SYSTEM_JOB, flags OFF, preview e gate de startup
somente de leitura. Foram 37 casos únicos verdes, sem skips, com falhas de
fixture anteriores preservadas. O gate não inicializa nem reconcilia um
journal automaticamente e distingue pendência de descarte de uma restauração
necessária. Timestamps legítimos de purge podem diferir entre atualizações;
o gate exige os marcadores e suas condições temporais, não igualdade artificial.

A revisão independente encontrou dois defeitos no Projection: fonte
desconhecida podia sumir da seleção e um adiamento semântico podia impedir
ocorrências válidas seguintes. `e1963c0` corrigiu ambos, com duas regressões,
positivo e busy aprovados. A mesma revisão identificou a interação entre TTL
e rodízio e a ausência de paginação de perfis. O parecer técnico escolheu
passada finita, corte fixo, validade renovável por página e consulta independente
de completude. Não foram alterados DDL, criptografia, permissões ou prazos de
retenção por essa correção.

`e45e649` ajustou os quatro ports e o horário operacional dos leases técnicos.
Passaram 17 casos PostgreSQL dirigidos, incluindo seis novos de passada finita
e onze controles afetados. As três adaptações locais de tmpfs foram restauradas
exatamente antes do commit. `96d0a4b` acrescentou keyset/filtro de coorte aos
perfis: seis casos dirigidos passaram, incluindo duas regressões, preview
25+8 sem escrita, corte de 480 s com envelopes renovados, apply e revogação
durante ACK. Os métodos de fechamento/descarte e os fences de autoridade
permaneceram intactos.

O Node passou no cenário de 16 execuções com as cinco famílias: alcançou e
processou uma vez o candidato 41 após quarenta pendências, com as mesmas
páginas e limites. O revisor reproduziu esse cenário em memória e encerrou os
achados de progresso. A revisão consolidada até `a75ec4d` não encontrou novos
achados acionáveis no escopo, incluindo o adapter de recuperação. Isso é
revisão de fonte, não execução remota nem aprovação de purge real.

O adapter V44 foi integrado de `b26c988`. Passaram quatro cenários PostgreSQL
de comando, três de arquivos e 31+10 testes Python. A prova de banco inclui
dump anterior ao close, journal atual, source purge, restauração e reaplicação,
sem ressuscitar Alfa e preservando controles Beta/core. A correção posterior
do UID foi repetida somente nos três testes de arquivo. O uso real de
PropertiesLauncher, o probe HTTP e o conjunto na VPS ainda precisam rodar com
o JAR final. O recibo distinguirá esses passos e a rotação física de backups,
que não foi implementada por esse ensaio.

A matriz final preparada inventaria 104 classes/818 casos PostgreSQL. A
distribuição não é uma alegação de execução integral. Testes e revisão
permanecem proporcionais ao delta; nenhuma suíte foi recolocada na CI.

Uma consulta remota somente de metadados confirmou as cinco credenciais de
serviço ativas. Canal, processamento, envio e job vencem em 01/12/2026; operador,
em 02/12/2026. A manutenção precisa renovar esses acessos antes do prazo. Nenhum
token, contato ou conteúdo de conversa foi consultado ou publicado nesse registro.

### Recuperação e migração V44 — composição aprovada em isolamento

A tentativa 005 terminou como `CORE_AND_PRIVACY_VERIFIED`, usando o JAR
`f76bcb0335f2c40955df86804d67754f0a68e28c29426c778364466468f5224b`
empacotado de `7886c9b`. Repetiu o núcleo em fila e acrescentou o probe HTTP
das cinco famílias desabilitadas, sem alterar o fingerprint do banco. O
comando isolado rodou no JRE Linux real com journal externo ao dump: um
backup anterior ao fechamento foi restaurado, as exclusões foram reaplicadas
antes de liberar consumidores e os dados de Alfa não ressurgiram. Beta e os
controles permaneceram preservados, inclusive data original, sequência e ACK
do fechamento. Não houve Evolution, mensagem real ou consumo de OpenAI.

O descarte sintético mudou três mensagens terminais, uma unidade técnica,
três projeções históricas e uma unidade de histórico em cada banco. O
journal atual permaneceu intacto após a reaplicação. Os containers exclusivos
foram removidos com identidade conferida e os volumes foram preservados.
`releaseEligible: false` continua explícito: esse recibo comprova a composição
isolada, não autoriza apagar dados reais ou substituir o aceite do MVP. A
rotação física dos backups continua sem implementação, com inventário e
preview de expiração; nenhum backup foi removido.

Separadamente, a V44 passou sobre uma cópia restaurada do banco compartilhado.
O dump de 2.772.852 bytes cobriu 107 relações, e o conteúdo restaurado foi
conferido antes da migração. Passaram os três cenários: API anterior/V43,
candidato/V44 e API anterior/V44, todos com saúde UP. O SQL revisado tem SHA-256
`214c688fb30acb73ed6b2646e2d224618ad4f561671573d3b92f43911968b7ba`.
A limpeza dos recursos exclusivos foi verificada. A primeira chamada usou um
nome de argumento incorreto e foi recusada pelo parser antes de acessar
Docker; a execução corrigida terminou como
`RESTORE_AND_MIGRATION_RETURN_VERIFIED`. O serviço publicado não foi alterado
por esses ensaios.

A rodada final de cinco arquivos Node passou em 59 casos dirigidos. Não
reexecutamos a suíte PostgreSQL inteira: as 104 classes/818 casos continuam
sendo inventário. Compilação do JAR, testes dirigidos e ensaios remotos têm
identidades próprias; um novo JAR do Actions será identificado separadamente.

### iPhone — compilação completa sem assinatura

O build completo 0.4.0+14 compilou com Xcode em 45,36 s. Pub com lockfile,
configuração Flutter e Pods também terminaram com sucesso. Os 480 fontes
rastreados do aplicativo e os dois lockfiles permaneceram idênticos. O
resultado está sem provisionamento, portanto não é instalável nem comprova
execução no iPhone. Assinatura gratuita, instalação e smoke continuam pendentes.
O Mac bloqueado impede conferir o Xcode e usar os WhatsApps reais no momento;
foi solicitada somente a liberação da sessão, preservando os aplicativos abertos.
