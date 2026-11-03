# VR-WA-100-ATTENDANCE — Entregar presença por ocorrência e calendário

## Resultado esperado

Cada pessoa responde para data/sentido certos; pendente continua previsto.

## Estado

`done`. Pacote P07, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

## Impact Analysis

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

## Contratos

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; no preflight, só contratos de controle e configuração são alteráveis.

## Escopo

- `coordination`: `program/program.json`, `program/cards/`, `program/evidence/`, `program/RISK_REGISTER.md`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `docs/plans/2026-10-28-whatsapp-mvp/`, `src/whatsapp/`, `docs/runbooks/whatsapp-mvp/`.
- `backend`: `src/main/`, `src/test/`, `contracts/whatsapp-mvp/`, `backlog/`, `docs/`, `pom.xml`.

Paths são previsão de responsabilidade, não autorização para substituir arquivos inteiros ou adotar mudanças alheias. Antes do primeiro write, restringir o diff aos componentes efetivamente necessários e preservar os preexistentes.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

[VR-WA-090-PASSENGERS](VR-WA-090-PASSENGERS.md)

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [x] Dependências concluídas; versões/dirty paths conferidos e recursos necessários disponíveis para esta etapa.
- [x] Gates, smoke, evidência e sequência confirmados para a revisão que será executada.

## Plano sequencial

1. Implementar materialização de ocorrências, timezone e exceções; W04 apenas desperta job idempotente.
2. Gerar pergunta única às 20h do dia anterior e resolver resposta por questionRef/estado da conversa.
3. Persistir GOING/NOT_GOING/PENDING e resumo ao motorista; ambiguidade requer esclarecimento.
4. Implementar política de atraso/cancelamento e concorrência para futuro snapshot; retomar após downtime sem spam.

## Critérios de aceite

- [x] Silêncio não vira confirmação nem exclusão; ausência explícita remove elegibilidade.
- [x] Pergunta antiga não altera outra data/sentido.
- [x] Schedule duplicado/restart não repete mensagem; relógio e corrida têm testes.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Gate backend/PostgreSQL, relógio controlado e W04; T06–T08/T25/T35.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-100-ATTENDANCE.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Checkpoint de 31/10/2026 às 20h15 de Brasília

Naquele checkpoint, o proprietário pausou a execução e o card ficou em `validating`. Os cards 060, 080 e 090 estão concluídos no laboratório. O teste físico do iPhone segue pausado.

- O backend de presença está no commit `aa128db7f4cdbd5982e51903ac72f2b0cb083122`. O worker e o W04 estão no commit `8dba2d803d7198dd4c7a496c1005b4b690915a87` da coordenação. As provas locais incluem 515 testes Node no Mac, 379 na imagem do worker, W04 em n8n 2.45.4 e testes direcionados de calendário, entrega e resposta com PostgreSQL.
- A primeira tentativa da suíte integral foi interrompida por pressão de memória após 15,4 segundos, antes de Maven e PostgreSQL. Na segunda tentativa, a pausa encerrou a espera por memória estável antes de iniciar a suíte. Nesse momento as portas locais 18083 e 51222 estavam fechadas e havia zero contêineres em execução com a identificação deste candidato. Os recibos parciais estão preservados no Mac em `~/.local/share/vanrides/wa100/` e `~/.local/share/vanrides/wa040/`.
- Quando o proprietário pedir a retomada, conferir os commits e o estado do workspace, aplicar a [regra de uso do Mac](../../docs/runbooks/whatsapp-mvp/040-fundacao-local.md#uso-do-mac-durante-os-testes) e executar `./scripts/ci_quality_gate.sh` no backend sob a guarda de memória, com novo identificador. Após aprovação, testar HTTP e as jornadas 070, 080, 090 e 100 no mesmo JAR. Registrar os resultados e a limpeza antes de fechar o 100 e iniciar o 110. [Procedimento de presença](../../docs/runbooks/whatsapp-mvp/100-presenca.md).

## Retomada da validação em 31/10/2026

A tentativa local `wa100-backend-ci-03` no candidato `aa128db7` chegou ao Maven. A guarda de memória interrompeu o grupo de testes aos 60,6 segundos. Até a interrupção, quatro testes haviam terminado com sucesso. Essa tentativa não produziu aprovação integral.

O backend `ebcae45014f70223f128be8b9c2d15c8b7d6226e` reduz o heap inicial de Maven e Surefire para 24 MiB e limita as CPUs vistas pelos dois processos a duas. O perfil de integração usa SerialGC também no Maven. A comparação com `aa128db7` mostra mudanças somente em `pom.xml` e no script do perfil de integração. A matriz obrigatória, os tetos de heap e os fontes da aplicação são os mesmos. A tentativa integral usa o candidato novo.

O ensaio da coordenação `519dca2` seleciona no menu a ida do dia testado. Confere a lista e os totais do motorista antes e depois das desistências. Os três testes de contrato de presença passaram nesse preparo. A aprovação desta jornada depende da execução com o JAR e o PostgreSQL.

Na revisão `ebcae45`, a suíte integral `wa100-backend-ci-04` terminou por pressão de memória depois de 1.090 testes em 164 classes. Esses testes tiveram zero falhas, erros ou pulos. Aquela tentativa foi parcial.

**Presença aprovada de ponta a ponta no laboratório em 31/10/2026.** A coordenação `a79cd76` e o JAR `6c02c325efd4278167fb6c7f574b16465908e21a8166783961407204a4966049` passaram nas dez verificações. Foram 64 mensagens de entrada distintas, uma repetição e 70 envios conferidos no receptor local. As duas rotinas usaram Manaus e os dois lembretes foram enviados na janela natural, com `seededReminderWindows=0`. O passageiro que dispensou lembretes continuou previsto. A resposta citada atualizou somente a ida do destinatário. Após as desistências, a lista do motorista mostrou as duas ausências. O recibo privado tem SHA-256 `61792e220fce628c2b1f38ea96a740de5dcfa772cc41fccfedaeebe1ef1351be`. A mesma revisão passou no HTTP, nas 12 verificações do agente e nas dez de rotinas. O recebimento pelo número de WhatsApp real pertence ao 170.

As quatro jornadas passaram com o mesmo JAR. As dez verificações do cadastro de passageiro completaram o conjunto de 42. As tentativas encerraram dez lotes, revogaram 31 credenciais e fecharam 16 portas. A inspeção de 563 logs encontrou zero ocorrências dos segredos conhecidos do teste. A conferência de uma revogação interrompida está no recibo de reconciliação privado. [Resultados, comandos de negócio e hashes dos recibos](../evidence/VR-WA-100-ATTENDANCE-smokes.json).

O fechamento ocorreu com a suíte canônica local em `ebcae45`. A alternativa preparada para o GitHub Actions ficou restrita ao teste de envio com `git push --dry-run`. A VPS e o iPhone físico ficaram fora dessa validação.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Pausar novos lembretes, manter respostas/cancelamentos válidos e histórico; desativar job sem apagar presença.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.

## Integração recebida do contrato080

080 já pode persistir ocorrência CANCELED por `(routine_id,local_date)` antes
do scheduler. Materialização/retry/restart devem preservar esse cancelamento,
sem ressuscitar presença ou lembrete. Rotina PAUSED bloqueia novas operações;
retomada não desfaz exceções explícitas. Revalidar estado/configuração/versão
atual antes de materializar e antes de enviar, preservando snapshots iniciados.

GET_SUMMARY080 é restrito a rotinas/convites do próprio motorista. Acrescentar
escopos de presença com referências autorizadas, sem relaxar o escopo existente
ou expor outros passageiros. Alterações de membros/configuração incrementam
routine.version e versões específicas; testar confirmação080 antiga concorrente
com edição100. Jobs reais/SYSTEM_JOB/T35 continuam no aceite150/170.

## Preparação a partir do cadastro 090

O inventário leu o backend `221d039d17743d5dff0015975d094cb71b1a9d6a` e a coordenação `d64ce1e1c0e17b61f1c7240ef5c4575bc3ac5380`. A V26 já possui ocorrência única por rotina/data, presença única por pessoa na ocorrência e reserva de lembrete por janela. A V29 acrescenta os dias do membro. O 080 já grava um cancelamento antes de existir pergunta. No início do 100, conferir novamente esses pontos e os fontes do SHA final do 090.

1. A API materializa sete dias no fuso de cada rotina. Cruza os dias da rotina com os dias escolhidos pelo passageiro e mantém as exceções de cancelamento. Sem resposta, o passageiro continua previsto como pendente. Fazer o ensaio com ida e volta, virada da data e um fuso com mudança de horário.
2. A pergunta das 20h da véspera tem destinatário, ocorrência, validade e identidade próprias. Uma citação do WhatsApp deve apontar da tentativa enviada para essa pergunta, na mesma instância. Uma resposta livre só é aplicada se houver uma opção inequívoca. Exibir data e sentido no retorno. Uma citação antiga ou de outro passageiro não pode responder à pergunta mais recente.
3. W04 desperta o worker uma vez por minuto, com controle privado e resposta por contadores. A credencial `SYSTEM_JOB` chama somente o endpoint de jobs. Habilitar localmente `MATERIALIZE_OCCURRENCES` e `DUE_REMINDERS` neste card, para que W04 tenha um efeito verificável. Os demais tipos, a operação contínua e a ativação do ambiente ficam nos 150/170. Os lotes de até 100 efeitos precisam retomar por cursor e continuar idempotentes após reinício.
4. Uma resposta humana tem origem na inbox. Um lembrete tem origem no job. Usar a origem correspondente para autorizar o envio e revalidar preferência, presença, rotina, vínculo e prazo. Resultado de tentativa já autorizada precisa ser registrável após cancelamento. Cobrir pausa, ausência explícita, opt-out, cancelamento já criado e uma interrupção atravessando a janela de envio.
5. A presença e o futuro início do acompanhamento compartilham a trava da ocorrência. Demonstrar com operações concorrentes que o corte acontece em `STARTING`. Uma desistência tardia pode suprimir os próximos avisos e mantém a lista que foi capturada ao iniciar. O 110 repetirá o ensaio pelo caminho real de ativação.

Incluir os novos testes PostgreSQL na matriz obrigatória. Executar contrato produtor/consumidor, relógio controlado, lotes acima de 100, callbacks, autorização entre atores, W04 importado e o smoke de presença com API e worker do mesmo candidato. Os ensaios com mensagens reais exigem o número e o ambiente preparados no 170.

## Resultado local

Calendário, respostas, resumo do motorista e W04 passaram em laboratório. O canal real integra o 170. [Provas, versões e limites](../evidence/VR-WA-100-ATTENDANCE.md).
