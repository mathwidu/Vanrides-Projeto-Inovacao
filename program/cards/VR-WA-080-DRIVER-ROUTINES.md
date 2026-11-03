# VR-WA-080-DRIVER-ROUTINES — Entregar cadastro de motorista, rotina e convites

## Resultado esperado

Motorista conhecido administra rotinas e convites somente no WhatsApp.

## Estado

`done`. Pacote P04, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

## Impact Analysis

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

## Contratos

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; no preflight, só contratos de controle e configuração são alteráveis.

## Escopo

- `coordination`: `program/program.json`, `program/cards/`, `program/evidence/`, `program/RISK_REGISTER.md`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `docs/plans/2026-10-28-whatsapp-mvp/`, `src/whatsapp/`, `docs/runbooks/whatsapp-mvp/`.
- `backend`: `src/main/`, `src/test/`, `contracts/whatsapp-mvp/`, `backlog/`, `docs/`, `pom.xml`, `scripts/qa_integration_profile.sh`, `scripts/ci_quality_gate_test.py`.

Paths são previsão de responsabilidade, não autorização para substituir arquivos inteiros ou adotar mudanças alheias. Antes do primeiro write, restringir o diff aos componentes efetivamente necessários e preservar os preexistentes.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

[VR-WA-070-AGENT-WORKFLOWS](VR-WA-070-AGENT-WORKFLOWS.md)

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [x] Dependência070 concluída localmente em027459a; backend013aaa6, app9fda8da, três checkouts limpos antes da IA. Publicação e iPhone físico continuam fora desta execução.
- [x] Gates, smoke, evidência e sequência da IA abaixo confirmados; compilações sequenciais sob guarda de memória.

## Plano sequencial

1. Concluir W02 e handlers de onboarding/notice com bootstrap previamente autorizado.
2. Criar/editar dias, hora, sentido, destino e sequência de pontos; confirmação de resumo e versão antes de aplicar.
3. Implementar convite com hash, TTL, revogação e limite; lista/resumo somente do motorista.
4. Tratar retomada/ambiguidade, rotina sem passageiros e pausa/cancelamento por data.

## Critérios de aceite

- [x] F01/F05 completos sem empresa e sem privilégio concedido por autodeclaração.
- [x] Ida/volta não são confundidas nem invertidas implicitamente.
- [x] Emissão do convite preserva privacidade, hash, TTL, revogação e limites; consumo pelo passageiro e concorrência de uso são comprovados no090.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Gate backend e contratos W02; T01/T02/T05/T06/T18/T34.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-080-DRIVER-ROUTINES.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Pausar novas rotinas/convites, preservar vínculos existentes e revogar convites afetados; reverter handlers compatíveis.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.

## Inventário preparatório —31/10, sem implementação

Leitura independente no backend013aaa6 e coordenador f5377d0; Graphify backend
CURRENT, semântica documental pendente. O card070 continua ativo; esta leitura
não satisfaz a dependência nem promove080.

Reuso confirmado: migrationV26 já possui `wa.driver_invite` com dono/hash/TTL/
revogação/limite/versão, `wa.routine` com dias1–7/hora/timezone/sentido/destino/
versão e membros/ocorrências com propriedade composta. O padrão WA é JdbcTemplate.
Não reutilizar diretamente RotaBase/ServicoRecorrente/DriverCompanyInvite do
legado, que exigem empresa. Bootstrap operacional e convite motorista→passageiro
são conceitos distintos; a CLI atual só emite bootstrap em lote sintético.

Lacunas a resolver na IA executável antes de escrever código:

- CREATE_INVITE, REVOKE_INVITE, CONFIGURE_ROUTINE, SET_STOP_ORDER,
  CANCEL_OCCURRENCE e GET_SUMMARY ainda retornam503 em WaCommandService.
- Contexto devolve options vazio e versão do perfil; criação/edição de rotina
  precisa de draft, referências autorizadas, confirmação e versão do agregado.
  ConfigureRoutinePayload já exige routineRef/destinationMessageId/confirmationRef,
  mas nenhum emissor prepara esse primeiro draft.
- WaConversationPolicy.emitted sobrescreve context_json/question_ref fora do
  aviso; não permitir que uma resposta comum apague o draft da rotina.
- Novas respostas precisam entrar na elegibilidade fechada de
  WaFoundationResponses, reutilizada por envio e reconciliação, além de possuir
  templates e outbox atômica com o recibo.
- GET_SUMMARY aparece atribuído a100 no OpenAPI, apesar do resumo exigido aqui;
  pausa de rotina tem estado persistido, mas não comando atual correspondente.
- W02 é stub e deterministic.mjs ainda aceita somente os sete comandos070.
  Contratos de produtor/consumidor e fluxo de controle precisam evoluir juntos.

Foco mínimo previsto: commands/ (handlers/store/contexto/versionamento), templates
e elegibilidade em transport/, OpenAPI/catalog de fixtures, testes PostgreSQL
de conversa/fencing e novos casos de rotina/convite, política determinística e
W02/gerador/manifest no coordenador. Novas classes de integração precisarão entrar
na matriz obrigatória de qa_integration_profile.sh, com teste do wiring do gate.
Nenhuma dessas alterações foi executada neste inventário.

## IA executável — 31/10/2026

Parecer de arquitetura independente recebido e adotado pelo principal. Grafo
backend CURRENT no inventário, relações críticas conferidas em V26,
WaCommandService/Policy, normalizador e outbox. Sem alteração de stack, banco
legado, autenticação/CORS/WS públicos ou configuração externa. As rotas
`/src/whatsapp/v1/context` e `/commands` continuam com credencial,
inbox, lease e ator derivados na API. O consumidor afetado é o worker Node;
busca em lib/test do Flutter não encontrou consumo desses contratos. Migrations
aplicadas ficam imutáveis; qualquer acréscimo usa nova versão.

### Decisões e interface congelada para implementação

- Acrescentar `ADVANCE_DRIVER_SETUP {}` para avançar um rascunho pela mensagem
  autenticada, com parsing e estado na API. Um rascunho por motorista em
  `conversation_state.context_json.driverDraft`; criação de rotina só depois de
  todos os campos e confirmação. Não inventar valores para inserir uma rotina
  incompleta. Motorista habilitado/ativo com notice aceito pode configurar em
  PAIRING, pois080 antecede060.
- Etapas de menu, seleção, coleta de campo, revisão e confirmação; uma pergunta
  por vez. Voltar/corrigir/continuar/abandonar operam o rascunho. Ajuda e respostas
  comuns não o apagam; suporte pausa e invalida a confirmação, preservando o que
  pode ser retomado. O contexto Node recebe somente opções/ref/labels neutros;
  nomes, pin e resumo ficam na API/outbox. Não é necessário novo campo de
  contexto se allowedCommands/options já expressam a operação disponível.
- Manter expectedVersion como fence do perfil; cada avanço/mutação080 incrementa
  essa versão. A confirmação captura também revisão/digest do rascunho, versões
  da rotina/membros/pontos, ação, ator, alvo, inbox de origem e outbox do resumo.
  Validade5min, consumo único. Validar `CONFIRMAR <marcador>` no texto da inbox
  para impedir que um "sim" antigo confirme outro resumo. Contexto/ref não
  concede autoridade sem essa mensagem explícita. Revalidar tudo após locks.
- Materializar os schemas antes FUTURE com payloads de referências:
  CONFIGURE_ROUTINE/SET_STOP_ORDER/SET_ROUTINE_STATE `{routineRef,confirmationRef}`;
  CREATE_INVITE `{confirmationRef}`; REVOKE_INVITE `{inviteRef,confirmationRef}`;
  CANCEL_OCCURRENCE `{occurrenceRef,confirmationRef}`; GET_SUMMARY `{summaryRef}`.
  SET_ROUTINE_STATE é novo; catálogo passa de25 para27 comandos. Nenhum payload
  transporta valores livres, ator, destinatário ou GPS. As referências são
  resolvidas no escopo do motorista e da proposta persistida.
- A API anuncia no máximo um comando080 por mensagem, junto aos comandos070
  compatíveis. Node copia apenas as referências exigidas das opções autorizadas;
  ambiguidade/múltiplas opções falham fechadas. ADVANCE tem payload vazio.
  ContextOption pode usar STOP/SUMMARY além dos tipos existentes. O modelo não
  recebe nem decide dados do rascunho. Não alterar prompt/schema OpenAI neste card.
- Rotina coleta nome, sentido explícito, dias, hora, timezone e destino por pin
  estático autenticado; volta é independente. Ordem é permutação completa dos
  membros ativos/vínculos e pontos aprovados. Rotina vazia é válida, com ordem
  pendente; provar reordenação com fixtures até o ingresso de membros090.
- Pausa/retomada ACTIVE↔PAUSED exigem confirmação e preservam run existente.
  Cancelar data cria/atualiza somente ocorrência CANCELED por `(routine,date)`;
  não cria presença/job. Rejeitar ocorrência já iniciada/encerrada. Configuração
  nova não modifica snapshot ativo. Resumo080 contém somente rotinas/convites do
  dono; presença permanece100. Estes limites entram nos contratos100/110.
- Convite usa `PASSAGEIRO <token>` e hash purpose próprio, distinto de bootstrap.
  O normalizador já redige essa família; consumo por passageiro permanece090.
  Defaults mostrados no resumo:1uso/24h; limites1–10usos e5–1440min. Motorista
  compartilha, sem envio automático/importação de contatos. Token só nasce no
  commit confirmado; hash em driver_invite, cifra de entrega ligada a
  ambiente/outbox/comando/destinatário e decifrada apenas em authorize-submit.
  Não incluir segredo em draft/recibo/contexto. Revalidar convite antes de enviar
  e remover cifra ao submeter/expirar/cancelar; purge operacional integral150.
- W02 passa a entrada de controle do worker comum, com corpo vazio e contadores;
  nenhuma conversa/identidade é levada ao n8n. Preservar IDs e inatividade dos
  exports. W03–W05 seguem fechados. Atualizar gerador/manifest e smoke n8n.

### Sequência, propriedade e verificação

1. Backend: handlers/rascunho/persistência/contexto, templates/outbox privada,
   normalizador e contratos/fixtures. Testes de jornada, isolamento, confirmações
   antigas/expiradas, replay, versões concorrentes, pin externo, ida/volta,
   permutação, cancelamento, pausa e cifra/TTL/revogação. Incluir novas classes
   PostgreSQL na matriz obrigatória, sem reduzir testes/gates.
2. Coordenador: política determinística de comando080/referências, testes de
   contrato e garantia de zero chamada ao modelo nessas etapas, W02/export,
   smoke integrado e evidências. Um responsável por repositório; arquivos
   compartilhados só pelo principal. Não executar testes pesados concorrentes.
3. Revisão independente antes do gate final. Build/CI backend limpo sob guarda;
   depois API/PG em loopback e smoke HTTP do mesmo JAR/SHA, com Evolution falsa,
   eventos reais do normalizador, restart/replay e limpeza do lote. CI e smoke
   sequenciais como070; não chamar de PASS do comando combinado interrompido.
4. Node, contratos e import/restart n8n em instância local isolada; imagem atual
   por digest, sem rede/provedor real para testes de container. QA/backlog,
   evidência e commits separados. Nenhum push/deploy ou aparelho físico.

T01/T34 cobrem cadastro restrito/bootstrap sintético; pessoa real será habilitada
na coorte autorizada170. T02 consumo ponta a ponta pertence090, embora emissão,
revogação/TTL/limite sejam verificáveis080. T05/T06 usam fixtures de snapshot/
ocorrência e não aprovam execução futura100/110. Rollback: worker anterior e
modo disabled, desativação de novos comandos, preservação de rotinas/recibos;
nunca apagar migration aplicada ou restaurar convite revogado.

## Adendo de recursos —31/10

O diagnóstico final51PG passou, mas o CI completo em bd9bbb7 foi interrompido
duas vezes pela guarda de memória (145,9s e151s), sem erro Maven observado antes
do sinal. As tentativas permanecem FAIL; a segunda ocorreu após restart Docker.
Docker2CPU/2GiB e host Codex foram preservados. RSS agregado não identifica a
causa da pressão. A inspeção encontrou47 classes com contexto Spring e cache
padrão32, mantido pelo fork reutilizado: retenção é hipótese a verificar.

Ensaio mínimo aprovado no escopo local: limitar apenas o cache dos testes a8
no Surefire/pom.xml, mantendo heap, metaspace, GC, fork, matriz e guarda. O
contexto descartado pode precisar ser recarregado, aumentando o tempo. Provar
o valor efetivo, repetir o CI inteiro em novo SHA limpo e comparar recursos;
não substituir o gate por diagnósticos. Documentação oficial e retorno ficam
em docs/development.md do backend. Sem mudança da JVM da API ou da VPS.

Cache 8 efetivo nos XMLs: a guarda interrompeu novamente aos 166,1 s; pico
amostrado 1.176,4 MiB, final 923 MiB. Os 1.140 testes já reportados não apresentavam
falhas/erros, mas o gate permanece FAIL. Ensaio seguinte altera somente o coletor
do fork Surefire para SerialGC, mantendo cache 8 e todos os demais limites e
critérios. A comparação mede redução de pressão, sem atribuir a causa a um único
processo. Referência Java 17, possível aumento de pausas e retorno individual
estão documentados no backend.

Cache 8 + SerialGC completou o CI no SHA4e11826: 1.317 testes e matriz
PostgreSQL254 em33 classes. A evidência final deve usar os
totais efetivos do SHA corrigido abaixo, sem reutilizar esse PASS anterior.

## Integração — correções e nova prova

O primeiro smoke usava nome fictício com palavra reservada e recebeu a resposta
de esclarecimento prevista. Corrigido somente o dado sintético. O segundo
revelou espaço inicial indevido no menu quando o aviso anterior estava vazio;
o conteúdo era recusado pelo contrato de envio. A hipótese inicial de LF foi
descartada após inspecionar o payload: primeiro codepoint32 e zero quebras.

A revisão independente confirmou a composição defeituosa. Novo teste passa
por menu real → claim → authorize: falhou com INVALID_PAYLOAD no código antigo
e passou com o separador condicional. WaJson.text e transporte permanecem
intactos. Duas tentativas anteriores do teste tiveram erro de compilação,
foram corrigidas e não contam como reprodução funcional.

Backend técnico b1d8081, coordenador9dc92ff. Smoke080:10 checks,36 eventos,
três replays e três inícios do worker,36 recibos/outboxes/submissões, zero
chamada OpenAI, um conflito controlado recuperado e limpeza PASS. HTTP legado
também PASS no mesmo JAR. Após esta correção, o smoke foi executado antes do
CI final para encurtar a investigação; API/banco devem ser parados antes do
gate, mantendo a sequência e o mesmo SHA limpo. O CI final ainda define o
fechamento; nenhum resultado parcial substitui sua aprovação.

O CI final de b1d8081 foi interrompido pela guarda aos125,8s:1.074 testes
reportados sem erro/falha, pico1.043,9MiB, sem aprovação. Após parar Docker,
a pressão voltou a1. O próximo ensaio no commit502e4ad reduz somente o cache
Spring de8 para4; preserva SerialGC, heaps, prazos, casos e guarda. A hipótese
e o retorno estão no backend. O observador privado separa RSS das JVMs e da
VM e registra compressor/swap; não registra argumentos nem habilita DEBUG
do cache. Qualquer melhoria será observada, sem atribuição causal exclusiva.

Cache4 foi interrompido aos165,7s, com1.140 testes sem falha/erro/skip e pico
do grupo926,7MiB. As JVMs terminaram; Docker foi parado e pressão voltou a1.
Maven/fork/VM tiveram picos RSS408,7/597/1080,3MiB; RSS não mede heap ocupado.
O observador foi corrigido para uma falha de coleta não encerrar a supervisão.

Canário Docker1024MiB sem JVM passou: PostgreSQL saudável após100mil linhas
sintéticas, sem OOM/restart/swap, com MemAvailable506.844KiB no final. RSS da VM
não caiu de forma comprovada. Backup privado e retorno2048 estão documentados
no projeto Mac-M5; cópia externa não comprovada. Próxima rodada mantém502e4ad,
heaps e guarda, registrando jstat/guest a30s e interrompendo também em OOM,
swap-out ou menos128MiB disponíveis no guest. Canário não substitui CI.


## Impedimento operacional e retomada — 31/10

A rodada Docker1024 foi interrompida por pressão do host aos151s, com1.092
resultados parciais sem falha/erro/skip; não aprovou CI. O guest manteve folga,
sem swap/OOM/restarts nas amostras. O coletor jstat tinha erro de locale: a
JVM de diagnóstico reproduziu vírgula decimal e comprovou a correção com
locale inglês. Nenhuma ocupação válida das JVMs reais pode ser inferida desse
probe. Não reduzir heaps ou dispensar testes com base nesses dados ausentes.

O orçamento Docker2048MiB/2CPU foi restaurado e conferido; Desktop, JVMs e
containers foram encerrados. Nenhum aplicativo do host foi encerrado à força.
Após seis interrupções, solicitar reinício assistido do Mac é a próxima ação
para recuperar um baseline, sem promessa de que isso resolverá a causa.
O card não está concluído; próximo card continua inelegível até este gate.

Checkpoint sanitizado: `program/evidence/VR-WA-080-MEMORY-CHECKPOINT.json`.
Procedimento completo: `docs/runbooks/whatsapp-mvp/080-retomada-memoria.md`.
A evidência080 existente é rascunho parcial e não entra no array canônico de
conclusão enquanto não houver CI e smoke do candidato aprovados.

## Retomada após reinício do Mac

Em 31/10, o proprietário confirmou o reinício. Pressão normal, swap zerado e
compressor reduzido de cerca de 4,7 GiB para 1,5 GiB antes do Docker. As três
árvores estavam limpas. Docker a 2 GiB e duas CPUs, portas livres e nenhum
container ativo permitiram retomar a suíte integral em `502e4ad`.

Os 189 relatórios dessa tentativa registraram 1.318 testes sem falhas, erros
ou skips. O gate terminou em `failed` na validação da matriz PostgreSQL. A
lista exigia 15 casos de `WaDriverWizardIntegrationTest` e o XML continha 16,
incluindo `generatedMenuCanBeAuthorizedForDelivery`. A guarda terminou sem
interrupção aos 291,1 s. O diagnóstico está no run local
`integration-20261031T202628Z-8678` e foi preservado.

O commit backend `1e022e83c4d2d0980874dbd2d105121b5950491e` eleva a
expectativa dessa classe para 16. A conferência dos XMLs identificou 33
classes obrigatórias e 255 testes. A comprovação do novo candidato exige
reexecutar CI, build, HTTP e jornadas 070/080 com seu próprio SHA e JAR.

Na execução de `1e022e8`, a proteção de wiring ainda esperava 254 testes
no total e interrompeu o gate antes de iniciar Maven. O commit
`aec364e2265bd1a7988b5c9c7fb06533ccb25140` ajustou a asserção para
255. O próprio teste de descoberta das classes Docker passou isoladamente.
As duas mudanças afetam apenas a matriz de verificação.

## Resultado final em 31/10/2026

CI integral PASS com 1.318 testes, matriz obrigatória 255/33, HTTP 200/200/400
e jornadas 10/10 e 12/12 no JAR de `aec364e`. A guarda registrou pressão normal
até a conclusão. Os 36 eventos da jornada 080 produziram 36 comandos e envios
sintéticos. Três reentregas não produziram efeito adicional. O registro separa
as provas por fonte, as duas divergências de contagem e as seis interrupções
anteriores. [Evidência canônica](../evidence/VR-WA-080-DRIVER-ROUTINES.md).
