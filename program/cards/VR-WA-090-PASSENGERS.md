# VR-WA-090-PASSENGERS — Entregar cadastro do passageiro, pin e vínculo

## Resultado esperado

Passageiro se cadastra pelo WhatsApp e tem vínculo/ponto aprovado pelo motorista correto.

## Estado

`done`. Pacote P06, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

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

[VR-WA-080-DRIVER-ROUTINES](VR-WA-080-DRIVER-ROUTINES.md)

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [x] Dependências concluídas; versões/dirty paths conferidos e recursos necessários disponíveis para esta etapa.
- [x] Gates, smoke, evidência e sequência confirmados para a revisão que será executada.

## Plano sequencial

1. Concluir W03, aceite do notice e perfil mínimo via convite.
2. Receber pin estático por referência do evento autenticado, pedir referência textual e confirmar ponto de embarque.
3. Solicitar aprovação do motorista e materializar vínculo/membro/ponto aprovado atomicamente.
4. Tratar ponto compartilhado, divergência de referência, correção e revogação; novo ponto não altera snapshot ativo.

## Critérios de aceite

- [x] F03/F04 completos sem app passageiro, live location ou coordenada gerada.
- [x] Acesso entre motoristas/passageiros bloqueado; aprovação auditável.
- [x] Retomada e mensagens duplicadas não criam perfis/vínculos repetidos.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Gate backend, W03/fixtures, T02–T05/T18/T20/T25/T37.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-090-PASSENGERS.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Bloquear novas aprovações, preservar históricos/versionamento e reverter contratos compatíveis; nunca apagar vínculos por cascade improvisado.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.

## Integração recebida do contrato080

Consumir a família `PASSAGEIRO <token>` já redigida pelo normalizador, com hash
purpose `passenger-invite`; não confundir com bootstrap do motorista. Validar
dono/TTL/revogação/limite sob lock. Incrementar uso somente quando criar um novo
REQUEST_LINK durável; replay não consome outra vez. Revogar convite não desfaz
vínculos já existentes. T02 só é completo após esta jornada, além das provas080.

Ao aprovar/remover membro, mudar ponto ou ordem, incrementar routine.version e
as versões específicas, invalidando confirmações080 capturadas anteriormente.
Ordem aprovada exige permutação completa de membros ativos e pontos aprovados;
novos membros não entram silenciosamente em um snapshot iniciado. Preparar
convite encaminhável/link usando o número dedicado da coorte, sem envio a
terceiros escolhido pelo agente.080 não comprova ingresso real de passageiros.

## Preparação de 31/10/2026 durante o ensaio 060

O inventário foi feito no backend `8411f9ac47fb59517e57aa7165d97d7c8e55c66e`
e nos componentes de conversa da coordenação
`7069fbdc1e69bafa9382c110dc4d72c7b94ed668`. A execução de 090 começa depois
da transição de WIP. Estes pontos registram o que precisa mudar.

- O 080 já cria o convite, limita usos e entrega o texto privadamente ao
  motorista. O normalizador reconhece o token de passageiro e persiste o hash.
  O primeiro comando de 090 deve consumir o hash do próprio evento autenticado.
  O contrato reservado `RequestLinkPayload.inviteCode` precisa ser ajustado com
  seus consumidores e testes para o worker nunca receber ou reenviar o segredo.
- Reutilizar nome e aceite do perfil. Identificar o motorista do convite antes
  da confirmação do passageiro e conservar a etapa no servidor ao pedir nome,
  dias/sentidos, o pin e, se necessário, uma referência legível. O próprio
  passageiro é o interlocutor. O fluxo habitual usa um ponto aprovado por
  passageiro conforme F03/F04. A seleção de rotinas e de sentidos é explícita.
- A V26 já registra solicitação de vínculo, ponto versionado e membro de rotina
  com referências de dono. A aprovação só pode usar recursos da mesma dupla e
  rotinas ativas do motorista. Na transação que aceita a proposta, verificar
  versão e consumir o uso do convite uma vez, conforme a etapa escolhida de
  reserva. Cobrir corrida entre usuários pelo último uso e replay após restart.
- Coordenadas vêm de um evento de pin estático do próprio passageiro, no
  ambiente e na instância autenticados. O agente trabalha com referências.
  Uma mensagem de localização ao vivo precisa resultar em orientação. Um
  texto ambíguo precisa de esclarecimento. Ao corrigir o ponto, preservar o
  histórico aprovado e o snapshot anterior já iniciado. A prova com execução
  de percurso real terá continuidade no 110.
- O 090 precisa entregar o handler W03, o agente determinístico e seus contratos,
  os comandos e testes PostgreSQL, a retomada de conversa e um ensaio integrado
  com duas identidades. O cenário verifica solicitação, aprovação pelo motorista,
  recusa de acesso cruzado, entregas aos destinatários corretos e duplicatas.
  A exposição do número de teste e a ativação dos workflows na VPS pertencem
  à publicação do ambiente 170.

## Análise de impacto e execução

Origem do backend `8411f9ac47fb59517e57aa7165d97d7c8e55c66e`. Fonte da coordenação usada para ler agente e workflow `4a887ec8afa9ff70ab1c3c07744b5739089f7c11`. O 080 já emite convite particular. O próximo incremento cobre do primeiro contato até a resposta de aprovação, recusa, correção ou encerramento. O grafo do backend estava CURRENT. As relações de comandos, outbox e reconciliação foram conferidas no Java e nas constraints PostgreSQL.

1. Conservar no servidor o convite reconhecido pelo hash `passengerInviteCodeHash` de uma mensagem autenticada. Mostrar o motorista antes de pedir aceite. Reaproveitar nome e aviso de privacidade já válidos. Coletar a escolha de avisos de transporte separadamente do aceite do aviso. Selecionar rotinas e dias explicitamente. Um endereço ou link ambíguo pede pin fixo. O passageiro confirma o ponto e pode pular a referência quando o acesso já está claro.
2. Reservar um uso do convite apenas quando a solicitação final confirmada ficar durável. Revalidar dono, validade, revogação e usos sob lock. A aprovação usa a reserva já persistida. O vencimento de um convite impede novos pedidos e mantém os que já foram encaminhados. O convite não substitui a aprovação. Revisar replay, corrida do último uso e o caso de um passageiro já vinculado a outro motorista. Uma nova mensagem com o mesmo pin não pode duplicar uma aprovação.
3. A API deriva motorista e passageiro da solicitação e escolhe a origem autenticada do destinatário no mesmo ambiente e instância. A outbox de respostas existente usa o mesmo ator dos dois lados. Criar um caminho próprio para a notificação à contraparte e verificá-lo em claim, authorize-submit, registro de resultado e reconciliação. Manter o bloqueio de mistura de atores nas respostas existentes. O resumo de revisão mostra um link de mapa do pin confirmado, renderizado pela API somente para o destinatário elegível, com referência opcional. Ações destrutivas exigem confirmação ligada à proposta e a suas versões.
4. Persistir vínculo, ponto e membros das rotinas aprovadas e versões em transação. Reutilizar `driver_invite`, `driver_passenger_link`, `pickup_point` e `routine_member`. A migration aditiva registra o subconjunto de dias de cada passageiro e a proposta pendente. Permitir ponto habitualmente compartilhado preservando titular e visibilidade de cada registro. Cada proposta deve ter o próprio pin ou a escolha explícita de um ponto comum publicado pelo motorista. Uma lista de pontos pessoais de outros passageiros não é um catálogo autorizado. A correção preserva o ponto anterior até aprovação. Encerramento retira as participações habituais. Uma saída já iniciada conserva seu snapshot. Os testes separam respostas entregues da reserva em trânsito e verificam que uma retificação avisa a pessoa correta.
5. Atualizar OpenAPI, schemas de decisões e o handler W03 na mesma entrega. Dados pessoais, pin, link de mapa, código de convite e destinos ficam fora dos inputs do modelo e dos itens do n8n. O agente só emite a operação e as referências que a API ofereceu para aquela mensagem. O suporte e a ajuda preservam a etapa, e o suporte invalida a confirmação pendente.
6. Gate do backend com a matriz PostgreSQL completa e suites novas declaradas em `scripts/qa_integration_profile.sh` e em `scripts/ci_quality_gate_test.py`. Validar todos os testes Node, importar W03 no n8n local fixado e fazer smoke integrado usando o mesmo JAR. Incluir coorte isolada com duas conversas, recusa entre motoristas, destinatários distintos e replay depois de reiniciar o worker. Conferir limpeza das três credenciais, novos dados da coorte e ausência de segredos nos recibos públicos. Executar apenas uma carga pesada por vez.

Retorno por desabilitação dos novos comandos e notificações da coorte e por reversão dos binários compatíveis. Preservar histórico, tokens consumidos, vínculos encerrados e snapshots. Os exports n8n ficam inativos até a publicação autorizada do ambiente 170. A implementação local não depende do aparelho físico do passageiro.

## Resultado local

Cadastro e correção passaram no backend, no worker e no W03 de laboratório. O canal real integra o 170. [Provas, versões e limites](../evidence/VR-WA-090-PASSENGERS.md).
