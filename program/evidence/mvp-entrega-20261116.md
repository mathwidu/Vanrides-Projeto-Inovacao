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
