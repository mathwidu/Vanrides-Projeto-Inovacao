# VR-WA-110-RUN-TRACKING — Integrar início, snapshot e telemetria de produção

## Resultado esperado

Uma saída autenticada congela a lista e recebe GPS contínuo sem depender de protótipo.

## Estado

`done`. Pacote P08, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

## Impact Analysis

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

### Impacto confirmado na retomada

Os três repositórios estavam limpos antes da projeção do dashboard. O card integra a ocorrência e as respostas do 100 à sessão de aparelho do 060. A API deve serializar início, confirmação de lista e amostras usando os mesmos registros; o app envia provas do aparelho e o agente trabalha apenas com opções da conversa autenticada. As mudanças ficam no domínio WhatsApp, no companion do motorista e nos testes e documentos desses fluxos. Preservar a API legada e o app principal. Executar as cargas de compilação em série, sem fechar aplicativos do usuário; testes físicos do iPhone continuam pausados. O rollback e os limites de promoção estão abaixo.

## Contratos

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; no preflight, só contratos de controle e configuração são alteráveis.

## Escopo

- `coordination`: `program/program.json`, `program/cards/`, `program/evidence/`, `program/RISK_REGISTER.md`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `docs/plans/2026-10-28-whatsapp-mvp/`, `src/whatsapp/`, `docs/runbooks/whatsapp-mvp/`.
- `backend`: `src/main/`, `src/test/`, `contracts/whatsapp-mvp/`, `backlog/`, `docs/`, `pom.xml`.
- `app`: `lib/`, `test/`, `integration_test/`, `android/`, `ios/`, `pubspec.yaml`, `pubspec.lock`, `backlog/`, `docs/`, `README.md`.

Paths são previsão de responsabilidade, não autorização para substituir arquivos inteiros ou adotar mudanças alheias. Antes do primeiro write, restringir o diff aos componentes efetivamente necessários e preservar os preexistentes.

### Ampliação autorizada: versões de validação no GitHub

A pedido do proprietário, manter no GitHub privado os fontes de validação, o histórico de resultados e referências recuperáveis do MVP nos três repositórios do workspace core. Inclui `.github/workflows/` e os scripts/testes de CI necessários na coordenação e no app. O backend usa o workflow existente. As branches dedicadas só executam verificações, sem PR obrigatório, sem merge em dev/produção, sem publicação de imagem, lojas ou VPS. Os jobs macOS, quando usados, só podem produzir o build genérico sem assinatura; nenhum teste físico de iPhone está autorizado nesta etapa.

A revisão enviada é uma candidata. Registrar como aprovada apenas com os jobs concluídos, SHA exato, escopo e resultado conferidos. Se um módulo falhar, preservar as referências e o último resultado aprovado; não marcar o conjunto como pronto para o piloto. Compartilhar somente fontes e evidência sanitizada; manter arquivos pessoais, credenciais, dados de teste sensíveis e códigos de assinatura fora dos envios. Para o Redmi, usar a branch de laboratório do app, com o mesmo código funcional e só duas diferenças: workflow dedicado e certificado TLS público de teste. Conferir pacote, fonte e assinatura local antes da instalação.

### Autorização de infraestrutura — 01/11/2026

O proprietário aprovou o laboratório preparado e ampliou a autorização às
alterações na VPS pessoal para viabilizar o MVP e um piloto com usuários reais.
Aplicar a [ADR-008](../decisions/ADR-008-remote-validation-and-pilot.md).
Manter testes físicos do iPhone pausados e os aplicativos do Mac abertos.
O registro acadêmico amplia o escopo documental a `README.md`,
`program/decisions/README.md` e à ADR-008, sem ampliar o código de produto.
O primeiro artefato executado é o pacote infra-only, sem aparelho; registrar
resultado e rollback antes de integrar a jornada física.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

[VR-WA-060-DRIVER-APP](VR-WA-060-DRIVER-APP.md), [VR-WA-100-ATTENDANCE](VR-WA-100-ATTENDANCE.md)

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [x] Dependências concluídas; versões/dirty paths conferidos e recursos necessários disponíveis para esta etapa.
- [x] Gates, smoke, evidência e sequência confirmados para a revisão que será executada.

## Plano sequencial

1. Implementar request/activate/resume idempotentes e snapshot transacional em STARTING.
2. Aplicar unicidade de run/dispositivo/generation e contratos de amostra/ACK parcial/lease.
3. Integrar app de produção, estados degradados, relógio, fila offline e stop state separado. Watchdog da API a cada 5 s degrada em até 10 s após ultrapassar frescor, sem depender de pacote novo ou W08/n8n.
4. Confirmar início no WhatsApp só após GPS válido; expirar START_FAILED reconciliado e remover rotas/credenciais de spike.

## Critérios de aceite

- [x] Duas ativações/retomadas não criam sessões paralelas. Regressões PostgreSQL e jornada Linux da candidata: uma geração.
- [x] Amostra atrasada/repetida não renova frescor nem reabre terminal. Jornada Linux verificada antes da nova aquisição e após a parada.
- [x] Binários integrados preservam o contrato do coletor e não dependem de empresa ou endpoint de prova: gates Android/iOS genérico, nove checks Linux e jornada física Android A8 aprovados. Conforme ADR-007, percurso, bateria e aceite físico bilateral de P01 permanecem obrigatórios em 030/180; associações e canal real em 170.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Gates backend/app, races em PostgreSQL e integração mesmo manifest; T05/T08/T12–T16/T21/T30/T33.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-110-RUN-TRACKING.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Bloquear novos inícios, solicitar parada e drenar/reconciliar; manter endpoints stop/refresh restritos até encerrar sessões.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.

## Integração recebida do contrato080

Revalidar rotina ACTIVE, ocorrência não cancelada e ordem aprovada no momento de
iniciar; rotina vazia/com sequência pendente não prova prontidão para saída.
Edições080, pausa e reordenação mudam somente configuração futura, sem alterar
snapshot ativo. Cancelamento de uma data anterior à materialização100 deve ser
respeitado. Pausa da rotina não significa finish/stop do run existente. Testar
T05/T06 novamente com o fluxo110 real; fixtures080 não substituem esta integração.

## Integração recebida do 060

Integrar a sessão DPoP e o retorno do app 060 à ativação real, amostras e lease do Spring. Repetir início/recuperação/stop e estados de GPS no binário integrado.

## Preparação recebida do card 100

A ocorrência leva data, sentido, horário e fuso congelados em `schedule_snapshot`. O início deve travar a mesma ocorrência usada pela presença, ler as respostas e os membros vigentes e criar a lista operacional nessa transação. Guardar em `operation_run.snapshot_json.passengerNames` os nomes dos participantes incluídos, indexados por suas identidades internas. O resumo iniciado lê os nomes congelados e as paradas do próprio motorista.

Completar no WhatsApp a correção pré-partida prevista no fluxo F06. Ao corrigir a lista, o motorista seleciona a sua ocorrência e o participante, revisa a alteração e confirma. Registrar a autoria do motorista e o motivo operacional separadamente da declaração pessoal de presença. Uma desistência explícita do passageiro impede inclusão automática. Verificar o resumo antes/depois, troca de atores e a corrida entre revisão e ativação. Depois de STARTING, a lista obedece às regras de alteração tardia do card 100.

## Checkpoint de execução — 31/10/2026

O [registro de validação](../evidence/VR-WA-110-RUN-TRACKING.md) distingue os resultados já observados da suíte e dos aparelhos ainda pendentes. O card continua ativo. A API, o agente e o fluxo W05 já dispõem de implementação local. O companion Android/iOS passou a usar a autorização do aparelho para o percurso; o código do coletor de laboratório foi separado do alvo normal.

Para retomar, começar pelo [quadro de provas da candidata](../evidence/VR-WA-110-RUN-TRACKING.md). O backend `5ddb7c3db6c38385c018028741470ab6a509604f` passou na suíte integral, incluindo 446 testes com PostgreSQL, e na jornada integrada de nove passos em runner Linux com GPS sintético. Conferir no Redmi a primeira posição recente e a confirmação de parada na mesma sessão. O aplicativo de laboratório aberto no Redmi é o `4da0600`; a candidata normal Android/iOS `05900b7` passou nos três jobs. O iPhone físico está pausado.

## Retomada física — 01/11/2026

A suíte adicional `ffed578` passou integralmente: 1.543 testes, incluindo
447 PostgreSQL. O JAR de produto permanece em `5ddb7c3`. Infraestrutura v7
foi comprovada na VPS. A jornada física v4 preparou os atores sintéticos,
mas não concluiu vínculo/GPS no Redmi; limpeza lógica e física da tentativa
a1 foi conferida, com os 38 serviços existentes preservados.

O proprietário desbloqueou o Redmi, e a tentativa a2 abriu o APK conferido e
recebeu a resposta inicial de vínculo. O teste parou na leitura do código,
antes de `/pair` e GPS; limpeza e preservação dos 38 serviços foram verificadas.
O diagnóstico encontrou uma lacuna do leitor de QA para `contentDescription`
de um `Text` Flutter. A correção `3daa34c` passou na CI, e o JAR `46d23e67`
confirmou o vínculo na tentativa a3. A busca pelo título do painel parou o QA
antes de permissões e `/intent`; o botão de permissão foi observado no aparelho.
Ajustar o seletor de onboarding para o botão exato e validar a jornada a4,
com a fixture `.lab` limpa e permissões FINE/COARSE pendentes já comprovadas.
Limpeza da a3 e preservação dos 38 serviços passaram. APK/backend
permanecem inalterados. Não pedir credenciais/códigos nem alterar a segurança
do aparelho. Aplicativos do proprietário permanecem abertos e iPhone físico
segue pausado. Detalhes e hashes estão na evidência canônica.

A a4 validou os executores na CI, mas parou antes de `/pair`. Limpeza e os
38 serviços preservados foram comprovados. A revisão especializada reproduziu
uma regressão de prontidão no QA e identificou esperas insuficientes em
permissão/encerramento nativo. Corrigir somente o Python, preservar JAR/APK/API,
adicionar regressões no call-site real e repetir com uma fixture nova.
Ainda não há aceite de GPS ou parada física do card 110.

A correção QA `795c691` foi aprovada em revisão independente e na CI41347481260
(44 testes Python, contrato Java/DEX). O leitor reproduziu o mesmo JAR c4;
ponta Mac 49 PASS e pins revisados. A5 está preparada com baseline/preflight,
sem runtime iniciado. Executar a jornada com a nova espera por prontidão,
concessão única e ausência nativa observada; manter FAIL quando faltar prova.

A5 confirmou vínculo físico e parou na permissão, antes de GPS. Cleanup,
status STOPPED, 38 serviços iguais e remoção remota PASS. Próximo passo:
classificar transições no instante observado, preservar falhas fatais e
inspecionar o motivo enumerado do probe privado; validar a janela isoladamente
antes de abrir a próxima rodada VPS. iPhone físico continua pausado.

O probe613 reconheceu a permissão real pt-BR sem cliques; revisão composta
fa1da0a passou na CI em56Python+Java/DEX. Pins Mac49PASS/revisados. Repetir a6
com fixture nova e mesmo APK/backend; leitura de janela ainda não prova GPS.

A6 confirmou GPS real aceito pelo backend, permissão oficial e parada nativa offline.
Falhou ACK após force-stop/reabertura do lab (RUN_REAL_STOP_NOT_ACKNOWLEDGED).
Cleanup/status/38 serviços iguais/remoção PASS. Investigar restauração e reenvio
durável antes de novo julgamento da API ou aumento de prazo. Preservar fixture local.

A candidata Android `76a0fc5` trata o reenvio da parada restaurada na primeira
atualização de estado; laboratório `ef64acb`, versão 0.2.0+12, em CI. Antes de
repetir o ensaio A7, conferir a suíte, o APK, a assinatura e os novos pins do
executor. Backend, roteiro QA e prazos ficam iguais aos da A6. O resultado
físico da correção ainda está pendente; iPhone físico permanece pausado.

**Pausa solicitada pelo usuário:** laboratório 0.2.0+12 passou na CI, APK
`e022b8f2…b94cce` conferido e assinado, ainda não instalado. Suíte normal do app
ainda pendente na última consulta. A7 não preparada/iniciada; A6 limpa. Retomar
pelos pins e artefatos registrados na evidência; não executar teste enquanto
o usuário estiver com o Mac fechado.

**Retomada autorizada:** CI normal `41351275820` completa PASS e artefatos
conferidos; APK12 instalado/readback no Redmi. Preparação A7/V5 com pins novos
e runtime físico preservado está descrita no runbook. Gates48Python+16Node
e49Mac PASS; revisão/CI e execução física ainda pendentes neste registro.

A7 foi interrompida por `LOCAL_VALIDATION_ERROR` antes de READY e sem executor
Mac/GPS. API/banco estiveram prontos; o erro está na preparação/supervisão da
jornada, ainda sem causa confirmada. Encerrar recuperação estrita e adicionar
diagnóstico sanitizado antes de repetir; não aumentar prazos nem atribuir a
falha à correção Android sem prova. Detalhes na evidência canônica.

Recuperação A7 concluída: diretório/recursos próprios removidos, 38 serviços
iguais; contagem de credenciais mantida UNKNOWN. V6 acrescenta só diagnóstico
restrito ao supervisor, com52Python+16NodePASS. A8 usa o mesmo produto/QA/prazos;
aguarda revisão final de manifests, CI e execução.

A8 concluída **PASS** em 01/11: sete verificações físicas do Redmi e cinco do
backend, incluindo GPS real e ACK durável de parada após encerrar/reabrir o
processo, sem nova coleta. Produto76a0fc5/labef64acb/APK12e022, backend5ddb/JARfc5;
QA e prazos preservados. CI41356523157 PASS. Capture/status STOPPED/baseline38
inalterada/remove-private PASS. Revisar aceite do110 com essa prova e os gates
já publicados; não transportar esse resultado para percurso/bateria, canal
WhatsApp real ou iPhone físico, que permanece pausado. Evidência canônica
contém pins, comandos, resultados e limitações.

## Fechamento da integração — 01/11/2026

Revisão independente dos recibos e da ADR-007 aprovou o escopo integrado do110.
A8 cobre os sete checks do Android real e cinco do backend; concorrência,
replay/frescor e watchdog mantêm as provas PostgreSQL/Linux da mesma fonte de
produto. Gate normal Android/iOS genérico PASS. QA Logs e backlogs dos dois
módulos foram atualizados em commits próprios; evidência canônica e retorno
conferidos. Esta conclusão satisfaz a dependência110; o início do120 aguarda sua DoR,
sem alterar o estado
de030, os testes físicos do iPhone ou qualquer decisão de piloto/produção.
