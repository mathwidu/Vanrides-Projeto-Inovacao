# Especificações das tarefas

Cada tarefa define problema, resultado, limites e prova. Os critérios devem ser revisados com o grupo;
nenhuma caixa marcada é inferida do estado de implementação. Use a issue para registrar a revisão.

A navegação externa foi retirada do MVP. T76/T77 permanecem somente no histórico.
T72/T73/T74 são propostas de escopo e exigem decisão antes da execução.

[Voltar ao backlog](07-como-ler-o-backlog.md)

<a id="t01"></a>
## T01 — Preparar API e banco

F09 · Onda 1 · [Issue #25](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/25)

**Problema:** Os cadastros precisam de um serviço e banco configurados de forma reproduzível.

**Resultado esperado:** A API Java e o banco ficam prontos para receber os cadastros do atendimento.

**Situação:** Implementado · Laboratório. API e PostgreSQL usados nos ensaios de cadastro; não é implantação final.

**Inclui:**

- Configuração da API Spring Boot, banco e ambiente de cadastro, preservando a configuração anterior.

**Fora desta tarefa:**

- Publicar o serviço ou refazer os cadastros nesta tarefa.

**Critérios de conclusão:**

- [ ] A API inicia com a configuração de teste documentada.
- [ ] O banco aceita a estrutura prevista para os cadastros e uma operação de teste.
- [ ] A configuração anterior fica identificada e recuperável, sem credenciais no repositório.

**Como testar (Laboratório):**

- Iniciar API e banco separados, realizar uma operação de teste e conferir o procedimento de recuperação.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Versões do serviço e banco identificadas e acesso a um ambiente de teste.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Manter a configuração compatível na versão de teste.

**Onde conferir:**

- `src/backend/pom.xml`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/config/WaSettings.java`

<a id="t02"></a>
## T02 — Cadastrar motorista

F09 · Onda 1 · [Issue #26](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/26)

**Problema:** Sem identificar o motorista, o atendimento não pode associar rotinas e passageiros ao responsável correto.

**Resultado esperado:** O motorista consegue se cadastrar pelo WhatsApp com seus dados validados.

**Situação:** Implementado · Laboratório. Jornada de cadastro e rotina com mensagens simuladas aprovada.

**Inclui:**

- Receber os dados previstos, validar e registrar o motorista autorizado.

**Fora desta tarefa:**

- Criar cadastro de empresa ou acrescentar novos campos ao perfil.

**Critérios de conclusão:**

- [ ] Uma conversa válida registra o motorista e confirma o resultado.
- [ ] Dados inválidos ou acesso não autorizado são recusados sem criar cadastro válido.
- [ ] Repetir a mesma entrada não cria outro motorista.

**Como testar (Laboratório):**

- Executar cadastro válido, entrada inválida e repetição da mesma mensagem em ambiente de teste.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Identidade da conversa autenticada e operações de cadastro disponíveis.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Repetir a conversa com o número dedicado.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaDriverSetup.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaDriverMutations.java`

<a id="t03"></a>
## T03 — Restringir ações por perfil

F09 · Onda 1 · [Issue #27](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/27)

**Problema:** Uma pessoa ou automação não pode executar operações em nome de outra.

**Resultado esperado:** Cada ação usa a identidade e a permissão corretas.

**Situação:** Implementado · Laboratório. Regras de acesso e integração com PostgreSQL verificadas.

**Inclui:**

- Autorização de motorista, passageiro, aparelho e automações nos comandos existentes.

**Fora desta tarefa:**

- Adicionar novos papéis ou trocar toda a autenticação do produto.

**Critérios de conclusão:**

- [ ] Ações permitidas ao perfil autenticado são aceitas.
- [ ] Trocar o alvo para outro motorista ou passageiro é recusado sem alteração de dados.
- [ ] Uma credencial de automação não é aceita como ação de pessoa, e o inverso também é recusado.

**Como testar (Teste automatizado):**

- Executar a matriz de acesso com identidades próprias, alvos de outra pessoa e tipos de credencial incompatíveis.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Perfis e operações permitidas definidos no contrato atual.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Preservar a autorização ao conectar o canal real.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/auth/WaIngressFilter.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/auth/WaProofFilter.java`

<a id="t04"></a>
## T04 — Cadastrar passageiro e ponto

F10 · Onda 1 · [Issue #28](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/28)

**Problema:** O motorista precisa saber quem será transportado, em quais dias e onde deverá buscar a pessoa.

**Resultado esperado:** O passageiro informa os dados necessários e tem seu ponto conferido pelo motorista.

**Situação:** Implementado · Laboratório. Jornada de cadastro, correção e aprovação testada com canal simulado.

**Inclui:**

- Dados essenciais, dias da rotina e ponto de embarque do passageiro.

**Fora desta tarefa:**

- Exigir aplicativo do passageiro ou localização contínua da pessoa.

**Critérios de conclusão:**

- [ ] A conversa coleta os campos necessários e os dias sem confundir ida e volta.
- [ ] Um ponto inválido pede correção e não é aprovado silenciosamente.
- [ ] O vínculo e o ponto passam pela confirmação do motorista correto.

**Como testar (Laboratório):**

- Percorrer cadastro, envio de ponto, correção e aprovação, incluindo tentativa de outro motorista.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Convite ou rotina válida e motorista habilitado para aprovar o pedido.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Conferir o cadastro no WhatsApp real.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaPassengerSetup.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaPassengerMutations.java`

<a id="t05"></a>
## T05 — Convidar e vincular passageiro

F10 · Onda 1 · [Issue #29](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/29)

**Problema:** O passageiro precisa entrar na rotina certa sem reutilizar convites vencidos ou criar vínculos repetidos.

**Resultado esperado:** O convite permite uma solicitação válida e o motorista decide o vínculo.

**Situação:** Implementado · Laboratório. Concorrência pelo último uso e repetição de mensagens testadas.

**Inclui:**

- Emissão, validade, consumo e aprovação do convite existente.

**Fora desta tarefa:**

- Compartilhar convites sem prazo ou vincular alguém sem aprovação.

**Critérios de conclusão:**

- [ ] O convite válido identifica a rotina e permite a solicitação prevista.
- [ ] Convite vencido ou revogado é recusado sem criar vínculo.
- [ ] Repetição ou disputa pelo mesmo uso não cria solicitações aceitas além do permitido.

**Como testar (Laboratório):**

- Testar convite válido, vencido, revogado, repetido e uso concorrente em dados fictícios.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Motorista, rotina e regras de uso do convite disponíveis.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Verificar convite com participantes autorizados no número dedicado.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/transport/WaPrivateInviteDelivery.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaPassengerSetup.java`

<a id="t6"></a>
## T6 — Responder presença na viagem certa

F02 · Onda 1 · [Issue #30](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/30)

**Problema:** Uma resposta sem data e sentido claros pode alterar a viagem errada.

**Resultado esperado:** Cada passageiro confirma ou informa ausência para a viagem indicada.

**Situação:** Implementado · Laboratório. Confirmar, corrigir e manter outra viagem pendente passaram no laboratório.

**Inclui:**

- Registrar e corrigir a resposta da ocorrência selecionada.

**Fora desta tarefa:**

- Tratar falta de resposta como confirmação automática.

**Critérios de conclusão:**

- [ ] A resposta modifica somente a data, sentido e passageiro indicados.
- [ ] Quem não respondeu continua pendente e previsto.
- [ ] Uma correção válida substitui a resposta anterior sem alterar outra viagem.

**Como testar (Laboratório):**

- Confirmar ida, deixar volta pendente e corrigir a resposta; conferir a lista resultante.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Ocorrências de ida e volta e vínculo do passageiro existentes.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Repetir respostas e lembrete no canal real.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaAttendanceMutation.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaAttendanceConversation.java`

<a id="t7"></a>
## T7 — Receber GPS do aparelho vinculado

F05 · Onda 2 · [Issue #31](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/31)

**Problema:** Posições sem origem ou horário confiáveis podem apresentar uma van no lugar errado.

**Resultado esperado:** A API recebe somente as posições válidas da sessão e do aparelho autorizados.

**Situação:** Implementado · Físico parado. Testes de GPS e cenário Android parado aprovados; percurso e iPhone pendentes.

**Inclui:**

- Recepção de posições e validação de identidade, sequência e atualização.

**Fora desta tarefa:**

- Calcular aproximação ou prometer continuidade física apenas por receber uma posição.

**Critérios de conclusão:**

- [ ] Posição válida do aparelho vinculado é registrada na sessão correta.
- [ ] Posição de outro aparelho ou viagem é recusada.
- [ ] Amostra antiga ou repetida não substitui uma posição mais atual nem renova sua validade indevidamente.

**Como testar (Teste automatizado):**

- Enviar amostras válidas, antigas, repetidas e de outra sessão e conferir os resultados persistidos.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Sessão de localização e regras de validade da versão avaliada configuradas.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Validar continuidade durante os percursos nos dois aparelhos.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/tracking/WaTrackingSamples.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/tracking/WaTrackingService.java`

<a id="t9a"></a>
## T9a — Autorizar consulta da posição

F05 · Onda 2 · [Issue #33](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/33)

**Problema:** A posição da van não deve ser exposta a quem não participa da viagem.

**Resultado esperado:** Somente o motorista e os passageiros autorizados consultam a posição daquela viagem.

**Situação:** Parcial · Teste automatizado. GET_DRIVER_LOCATION ainda é recusado como dependência indisponível no código.

**Inclui:**

- Autorizar a operação de consulta e conferir vínculo e viagem.

**Fora desta tarefa:**

- Criar a mensagem de localização ou divulgar mapa público.

**Critérios de conclusão:**

- [ ] Um participante elegível consulta a própria viagem.
- [ ] Outro motorista ou passageiro sem vínculo não recebe a posição.
- [ ] A perda de autorização impede uma nova consulta, mesmo após uma consulta anterior válida.

**Como testar (Teste automatizado):**

- Executar consulta com participante válido, pessoa externa e vínculo removido.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Operação de consulta prevista e regra de elegibilidade da viagem disponíveis.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Implementar a consulta com escopo dos participantes e seus testes.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/auth/WaIngressFilter.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/auth/WaProofFilter.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaCommandService.java`

<a id="t9b"></a>
## T9b — Responder consulta de localização

F05 · Onda 2 · [Issue #34](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/34)

**Problema:** Receber um ponto sem horário pode fazer o passageiro acreditar que uma posição antiga é atual.

**Resultado esperado:** A resposta informa a última posição válida e quando ela foi obtida.

**Situação:** Planejado · Pendente. O comando GET_DRIVER_LOCATION está explicitamente na lista de ações futuras.

**Inclui:**

- Compor a resposta da consulta e indicar posição desatualizada ou indisponível.

**Fora desta tarefa:**

- Enviar trajeto completo ou previsão de chegada em minutos.

**Critérios de conclusão:**

- [ ] A consulta com posição válida devolve o ponto e seu horário de referência.
- [ ] Posição vencida ou ausente é apresentada como desatualizada ou indisponível.
- [ ] Uma resposta atrasada é revalidada antes do envio e não apresenta GPS vencido como atual.

**Como testar (Laboratório):**

- Consultar com posição recente, vencida, ausente e envio atrasado usando relógio controlado.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Consulta autorizada e acesso à última posição com seu horário.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Implementar a resposta com horário e indicação de posição antiga.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaCommandService.java`

<a id="t10"></a>
## T10 — Guardar a última posição válida

F05 · Onda 2 · [Issue #35](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/35)

**Problema:** A consulta precisa recuperar a posição mais recente sem voltar para uma amostra antiga.

**Resultado esperado:** O armazenamento fornece a última posição válida e o horário para a consulta autorizada.

**Situação:** Parcial · Teste automatizado. WaTrackingSamples atualiza a última posição; GET_DRIVER_LOCATION continua futuro.

**Inclui:**

- Persistência da última posição e disponibilização para consulta.

**Fora desta tarefa:**

- Histórico integral do trajeto ou produtor de aproximação.

**Critérios de conclusão:**

- [ ] Uma nova amostra válida atualiza posição e horário.
- [ ] Repetição ou amostra anterior não faz a posição recuar.
- [ ] A consulta autorizada recupera esses dados e distingue ausência ou desatualização.

**Como testar (Teste automatizado):**

- Gravar posições em ordem, fora de ordem e repetidas; consultar antes e depois da atualização.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Recepção de GPS validada e operação de consulta autorizada.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Concluir a consulta autorizada e testar posição desatualizada.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/tracking/WaTrackingSamples.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/tracking/WaTrackingService.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaCommandService.java`

<a id="t11"></a>
## T11 — Preparar aviso de aproximação

F11 · Onda 2 · [Issue #36](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/36)

**Problema:** O passageiro precisa ser avisado da aproximação sem receber avisos de outra pessoa ou repetições.

**Resultado esperado:** Uma aproximação válida gera um aviso para o passageiro correto.

**Situação:** Planejado · Pendente. A prova isolada do receptor não comprova este aviso no produto.

**Inclui:**

- Criar a intenção de aviso por viagem, ponto e passageiro, segundo a regra configurada.

**Fora desta tarefa:**

- Escolher novos limiares de distância ou prometer horário de chegada nesta tarefa.

**Critérios de conclusão:**

- [ ] Uma aproximação elegível gera aviso para o destinatário e ponto corretos.
- [ ] Repetir a avaliação ou reiniciar o processamento não gera outro aviso para o mesmo caso.
- [ ] Mudar a versão da regra não autoriza repetir o aviso na mesma viagem e ponto.

**Como testar (Teste automatizado):**

- Avaliar o mesmo caso mais de uma vez, com reinício e versão de regra diferente; conferir avisos e destinatários.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Regra de aproximação configurada e base de envio e recuperação disponíveis.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Implementar a regra e o aviso por passageiro antes do teste de utilidade.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaCommandService.java`

<a id="t23"></a>
## T23 — Explicar cadastro e correção do ponto

F10 · Onda 1 · Issue a criar

**Problema:** Mensagens ambíguas dificultam saber o que informar e quem precisa aprovar.

**Resultado esperado:** A conversa orienta cadastro, envio do ponto e correção de forma compreensível.

**Situação:** Implementado · Laboratório. A conversa de envio do ponto e retificação foi exercitada no laboratório.

**Inclui:**

- Textos e sequência das mensagens do cadastro do passageiro.

**Fora desta tarefa:**

- Adicionar novos canais, formulários ou dados de cadastro.

**Critérios de conclusão:**

- [ ] Cada etapa informa a ação esperada do passageiro ou motorista.
- [ ] A solicitação de correção indica que o ponto ainda precisa ser confirmado.
- [ ] A aprovação ou recusa mostra o resultado sem apresentar um pedido pendente como aprovado.

**Como testar (Laboratório):**

- Percorrer os textos com uma pessoa da equipe, incluindo ponto recusado e nova aprovação.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Fluxo de cadastro e papéis de passageiro e motorista definidos.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Revisar a compreensão das mensagens com a equipe no canal real.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaPassengerTexts.java`
- `src/whatsapp/agent/passenger-setup.mjs`

<a id="t24"></a>
## T24 — Criar calendário de ida e volta

F02 · Onda 1 · Issue a criar

**Problema:** Uma rotina recorrente precisa gerar a viagem certa para cada dia e sentido.

**Resultado esperado:** As ocorrências seguem os dias e horários da rotina no fuso configurado.

**Situação:** Implementado · Laboratório. Matriz de datas/fusos e jornada integrada registradas; a lógica não é fixa a Manaus.

**Inclui:**

- Criação de ocorrências independentes de ida e volta usando São Paulo no cenário do backlog.

**Fora desta tarefa:**

- Inventar feriados ou alterar o calendário sem configuração.

**Critérios de conclusão:**

- [ ] Os dias selecionados geram as ocorrências previstas e os demais não.
- [ ] Ida e volta mantêm identidades e horários independentes.
- [ ] Executar novamente a criação não duplica ocorrências para a mesma rotina, data e sentido.

**Como testar (Teste automatizado):**

- Usar um calendário fictício em São Paulo, comparar dias/sentidos e repetir a criação das viagens.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Rotina com dias, horários e fuso identificados.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Conferir a configuração de São Paulo no ensaio real.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/attendance/WaAttendanceCalendar.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/attendance/WaAttendanceMaterializer.java`

<a id="t25"></a>
## T25 — Enviar lembrete de presença

F02 · Onda 1 · Issue a criar

**Problema:** Sem uma pergunta no momento combinado, a confirmação depende de cobranças manuais.

**Resultado esperado:** O passageiro recebe a pergunta da viagem correta às 20h do dia anterior.

**Situação:** Implementado · Laboratório. A janela real de 20h e o reinício foram exercitados em laboratório com fuso de Manaus.

**Inclui:**

- Agendamento do lembrete por ocorrência, sem repetição indevida.

**Fora desta tarefa:**

- Garantir entrega real apenas com confirmação técnica de envio.

**Critérios de conclusão:**

- [ ] No fuso configurado, o lembrete fica elegível às 20h do dia anterior.
- [ ] A pergunta identifica a ocorrência correta e respeita a preferência de recebimento.
- [ ] Reexecução ou reinício não cria outra pergunta para a mesma ocorrência.

**Como testar (Laboratório):**

- Controlar o relógio em torno da janela do lembrete, repetir o acionamento e conferir o destinatário e a viagem.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Calendário de viagens criado e passageiro elegível para receber lembrete.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Executar o agendamento com São Paulo no ambiente autorizado.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/attendance/WaAttendanceCalendar.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/attendance/WaAttendanceMaterializer.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/attendance/WaAttendanceReminders.java`
- `src/whatsapp/worker/attendance-jobs.mjs`

<a id="t26"></a>
## T26 — Testar respostas antigas e repetidas

F02 · Onda 1 · Issue a criar

**Problema:** Falhas de tempo ou repetição podem alterar presença sem intenção do passageiro.

**Resultado esperado:** Os testes demonstram que o contexto de cada resposta é preservado.

**Situação:** Implementado · Teste automatizado. A evidência 100 registra a matriz PostgreSQL e os cenários integrados aprovados.

**Inclui:**

- Cenários de resposta antiga, silêncio e repetição do lembrete.

**Fora desta tarefa:**

- Reexecutar toda a jornada física para testar regras de calendário.

**Critérios de conclusão:**

- [ ] Resposta de uma pergunta antiga não altera outra ocorrência atual.
- [ ] Sem resposta, a presença permanece pendente.
- [ ] O mesmo lembrete ou resposta repetido não duplica perguntas nem efeitos.

**Como testar (Teste automatizado):**

- Executar os cenários com relógio controlado e comparar os registros antes e depois.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Dados de teste com duas ocorrências e perguntas identificadas.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Manter regressões e repetir os casos essenciais no WhatsApp real.

**Onde conferir:**

- `src/backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaAttendanceCutoffIntegrationTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaAttendanceJobsIntegrationTest.java`

<a id="t27"></a>
## T27 — Preservar versões e recuperação

F09 · Onda 1 · Issue a criar

**Problema:** Sem registrar o ponto de partida, uma mudança pode apagar trabalho anterior ou ficar impossível de desfazer.

**Resultado esperado:** O grupo identifica as versões e consegue recuperar a configuração anterior.

**Situação:** Implementado · Teste automatizado. Inventário e controle do workspace validados; arquivos anteriores preservados.

**Inclui:**

- Inventário dos três repositórios e recuperação das alterações existentes.

**Fora desta tarefa:**

- Enviar cópias com segredos ou publicar os repositórios.

**Critérios de conclusão:**

- [ ] A versão e as alterações existentes de cada repositório ficam registradas.
- [ ] A cópia ou procedimento de recuperação identifica o que pode ser restaurado.
- [ ] A conferência não remove mudanças de outra pessoa nem inclui credenciais na publicação.

**Como testar (Revisão documental ou reprodução):**

- Conferir o inventário com os arquivos e simular a recuperação em destino separado.
- Lista dos arquivos ou procedimentos conferidos, versão correspondente e registro da revisão ou reprodução.

**Dependências:**

- Acesso de leitura aos três repositórios e local separado para a cópia.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Preservar a cópia recuperável nas próximas entregas.

**Onde conferir:**

- `docs/workspace/preflight-2026-10-30.md`

<a id="t28"></a>
## T28 — Preparar aplicativo mínimo Android

F09 · Onda 1 · Issue a criar

**Problema:** O desenvolvimento precisa de um aplicativo Android que compile, instale e abra.

**Resultado esperado:** Uma versão mínima identificada funciona no aparelho de teste.

**Situação:** Implementado · Físico parado. Instalação e abertura no Redmi Note 8 sem USB confirmadas; não é prova de percurso.

**Inclui:**

- Ferramentas necessárias e geração do aplicativo mínimo Android.

**Fora desta tarefa:**

- Validar GPS em percurso ou todas as funções do MVP.

**Critérios de conclusão:**

- [ ] A versão mínima é gerada com as ferramentas documentadas.
- [ ] A instalação e a abertura funcionam no aparelho indicado.
- [ ] A identificação da versão e os passos de reprodução ficam registrados.

**Como testar (Teste físico delimitado):**

- Instalar e abrir a versão mínima, desconectar o Mac e verificar a reabertura.
- Registro por aparelho e cenário, com tela/rede consideradas e resultado sanitizado, sem GPS bruto ou credenciais.

**Dependências:**

- Aparelho Android disponibilizado e permissões de instalação concedidas pelo proprietário.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Usar a versão integrada nos próximos testes físicos.

**Onde conferir:**

- `src/aplicativo-motorista/android/app/build.gradle.kts`

<a id="t29"></a>
## T29 — Preparar aplicativo mínimo iPhone

F09 · Onda 1 · Issue a criar

**Problema:** O grupo precisa distinguir um arquivo gerado de um aplicativo que realmente abre no iPhone.

**Resultado esperado:** Uma versão mínima instalada tem assinatura e renovação documentadas.

**Situação:** Implementado · Físico parado. Abertura do build inicial no iPhone confirmada; não valida o GPS do MVP atual.

**Inclui:**

- Geração, instalação inicial e instruções da assinatura de desenvolvimento.

**Fora desta tarefa:**

- Publicação em loja ou prova física do coletor de GPS.

**Critérios de conclusão:**

- [ ] O aplicativo mínimo instala e abre no iPhone de teste.
- [ ] A assinatura e a validade efetiva estão identificadas sem expor dados privados.
- [ ] O procedimento de renovação permite repetir a instalação quando necessário.

**Como testar (Teste físico delimitado):**

- Instalar, abrir e reabrir sem o Mac; conferir a validade da assinatura e os passos de renovação.
- Registro por aparelho e cenário, com tela/rede consideradas e resultado sanitizado, sem GPS bruto ou credenciais.

**Dependências:**

- iPhone e conta de desenvolvimento fornecidos pelo proprietário, com autorizações no fluxo nativo.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Renovar a instalação e retomar os testes do coletor quando autorizado.

**Onde conferir:**

- `src/aplicativo-motorista/ios/Runner.xcodeproj/project.pbxproj`
- `docs/runbooks/whatsapp-mvp/010-builds-e-instalacao.md`

<a id="t30"></a>
## T30 — Coletar GPS no Android

F05 · Onda 2 · Issue a criar

**Problema:** A posição não pode depender de manter uma tela do aplicativo aberta.

**Resultado esperado:** O Android coleta posições em serviço nativo e mostra seu estado.

**Situação:** Implementado · Laboratório. Cenários em emulador do coletor isolado aprovados; aparelho real tem prova separada.

**Inclui:**

- Coleta em segundo plano, estado visível e fila limitada de posições não enviadas.

**Fora desta tarefa:**

- Declarar autonomia de bateria ou percurso aprovado a partir de ensaio controlado ou emulador; essas provas pertencem a T33, T35 e T67.

**Critérios de conclusão:**

- [ ] Com permissão concedida e serviço nativo ativo, bloquear a tela não encerra indevidamente a coleta; posições disponíveis continuam sendo encaminhadas no ensaio do componente.
- [ ] O aplicativo distingue coleta ativa, falta de permissão e falha.
- [ ] Sem rede, a quantidade de posições pendentes respeita o limite configurado.

**Como testar (Laboratório):**

- Executar o serviço nativo com tela bloqueada, permissão negada e perda de rede; observar estado e fila. Registrar se a prova usa emulador ou aparelho e não atribuir a ela o percurso ainda pendente.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Permissões de localização e receptor de teste compatível disponíveis.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Concluir os ensaios de percurso e energia no Android.

**Onde conferir:**

- `src/aplicativo-motorista/android/app/src/driver/kotlin/com/example/vanrides_frontend_dev/driver/DriverTrackingRuntime.kt`
- `src/aplicativo-motorista/android/app/src/driver/kotlin/com/example/vanrides_frontend_dev/driver/DriverTrackingService.kt`

<a id="t31"></a>
## T31 — Coletar GPS no iPhone

F05 · Onda 2 · Issue a criar

**Problema:** O ciclo da tela não pode ser o responsável pela coleta no iPhone.

**Resultado esperado:** O coletor nativo mantém o estado de localização independentemente da tela.

**Situação:** Implementado · Laboratório. Cenários em simulador e testes nativos aprovados; falta prova física do coletor.

**Inclui:**

- Permissões e coleta nativa iOS no contrato atual.

**Fora desta tarefa:**

- Prometer continuidade após encerramento forçado ou substituir teste físico por simulador.

**Critérios de conclusão:**

- [ ] A coleta usa o componente nativo com as permissões exigidas.
- [ ] Abrir e fechar a tela de apresentação não inicia outro coletor indevido.
- [ ] Permissão negada ou coleta interrompida produz estado compreensível.

**Como testar (Laboratório):**

- Executar cenários nativos e em simulador, separando seus resultados da prova em aparelho.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Aplicativo iOS mínimo, configuração de localização e receptor compatível.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Executar o coletor no iPhone físico após a retomada.

**Onde conferir:**

- `src/aplicativo-motorista/ios/Runner/DriverTrackingCoordinator.swift`
- `src/aplicativo-motorista/ios/Runner/DriverTrackingCore.swift`

<a id="t32"></a>
## T32 — Receber GPS em ambiente de prova

F05 · Onda 2 · Issue a criar

**Problema:** Testar o coletor sem isolamento pode atingir dados ou serviços reais.

**Resultado esperado:** As posições fictícias chegam a um receptor separado e autenticado.

**Situação:** Implementado · Laboratório. Testes do receptor, integração nativa e recuperação do volume aprovados.

**Inclui:**

- Receptor de teste, autenticação e recibo das posições.

**Fora desta tarefa:**

- Usar o receptor de prova como API final do produto.

**Critérios de conclusão:**

- [ ] Uma sessão de teste autorizada envia posições e obtém resultado identificável.
- [ ] Pedido sem autorização é recusado sem registrar posição válida.
- [ ] Dados e configurações de prova ficam separados dos dados reais.

**Como testar (Laboratório):**

- Enviar uma posição autorizada e outra sem credencial e conferir o armazenamento isolado.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Contrato do coletor de prova e ambiente separado disponíveis.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Manter separado do receptor integrado e dos dados reais.

**Onde conferir:**

- `src/whatsapp/gps-spike/receiver/service.py`
- `src/whatsapp/gps-spike/receiver/store.py`

<a id="t33"></a>
## T33 — Verificar localização em percurso Android

F05 · Onda 2 · Issue a criar

**Problema:** Um teste parado não mostra se o GPS continua útil durante o trajeto.

**Resultado esperado:** O percurso Android produz evidência de continuidade e recuperação da rede.

**Situação:** Parcial · Pendente. A8 não cobre duração com tela bloqueada, rede em movimento ou bateria.

**Inclui:**

- Percurso, tela bloqueada, interrupção de rede e atraso das posições no Android.

**Fora desta tarefa:**

- Compensar falha de campo com resultado de emulador.

**Critérios de conclusão:**

- [ ] O registro distingue os períodos de movimento, tela bloqueada e interrupção de rede.
- [ ] As posições e seus horários permitem medir atrasos e lacunas no cenário combinado.
- [ ] A recuperação após a rede voltar e as falhas observadas ficam registradas contra os critérios do roteiro vigente.

**Como testar (Teste físico delimitado):**

- Realizar o roteiro Android com observação, coletando somente métricas sanitizadas e comparando com o roteiro vigente.
- Registro por aparelho e cenário, com tela/rede consideradas e resultado sanitizado, sem GPS bruto ou credenciais.

**Dependências:**

- Aparelho instalado, ambiente alcançável pela rede móvel e percurso privado combinado com o proprietário.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Realizar o percurso Android e registrar atrasos e recuperação da rede.

**Onde conferir:**

- `src/aplicativo-motorista/android/app/src/driver/kotlin/com/example/vanrides_frontend_dev/driver/DriverTrackingRuntime.kt`
- `src/aplicativo-motorista/android/app/src/driver/kotlin/com/example/vanrides_frontend_dev/driver/DriverTrackingService.kt`

<a id="t34"></a>
## T34 — Verificar localização em percurso iPhone

F05 · Onda 2 · Issue a criar

**Problema:** A implementação iOS precisa de prova própria nas condições de uso.

**Resultado esperado:** O percurso no iPhone demonstra continuidade ou expõe precisamente suas limitações.

**Situação:** Planejado · Pendente. Testes físicos do coletor iPhone estão pausados; a abertura mínima não fecha esta tarefa.

**Inclui:**

- Percurso, tela bloqueada, interrupção de rede e atraso das posições no iPhone.

**Fora desta tarefa:**

- Usar o resultado Android como comprovação do iPhone.

**Critérios de conclusão:**

- [ ] O cenário é executado no iPhone físico com a versão identificada.
- [ ] São registrados continuidade, lacunas e recuperação nas condições do roteiro.
- [ ] Resultado e limites são avaliados separadamente do Android, sem ocultar etapas não executadas.

**Como testar (Teste físico delimitado):**

- Retomar o aparelho e executar o roteiro físico específico do iPhone, com observação e métricas sanitizadas.
- Registro por aparelho e cenário, com tela/rede consideradas e resultado sanitizado, sem GPS bruto ou credenciais.

**Dependências:**

- Retomada dos testes físicos autorizada pelo proprietário, instalação válida e ambiente alcançável.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Retomar o aparelho e executar o percurso completo.

**Onde conferir:**

- `src/aplicativo-motorista/ios/Runner/DriverTrackingCoordinator.swift`
- `src/aplicativo-motorista/ios/Runner/DriverTrackingCore.swift`

<a id="t35"></a>
## T35 — Medir bateria e recuperação

F05 · Onda 2 · Issue a criar

**Problema:** Sem medir os aparelhos fora do USB, não é possível avaliar o custo real da coleta.

**Resultado esperado:** Cada aparelho tem resultado de consumo e recuperação no cenário combinado.

**Situação:** Parcial · Pendente. Diagnósticos de energia Android são limitados e não demonstram autonomia do produto.

**Inclui:**

- Medição de energia e interrupções por plataforma, sem USB.

**Fora desta tarefa:**

- Extrapolar autonomia diária a partir de uma janela limitada.

**Critérios de conclusão:**

- [ ] A medição informa aparelho, versão, duração efetiva e condições de tela/rede.
- [ ] Consumo e interrupções são registrados separadamente para cada plataforma.
- [ ] A recuperação ou a falha de recuperação fica explícita, sem aprovação presumida da plataforma ausente.

**Como testar (Teste físico delimitado):**

- Executar a janela definida no roteiro, registrar condições e comparar consumo e recuperação por aparelho.
- Registro por aparelho e cenário, com tela/rede consideradas e resultado sanitizado, sem GPS bruto ou credenciais.

**Dependências:**

- Aparelhos carregados e disponíveis, janela de observação e roteiro de energia definidos.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Medir bateria e interrupções em Android e iPhone no cenário previsto.

**Onde conferir:**

- `src/aplicativo-motorista/android/app/src/driver/kotlin/com/example/vanrides_frontend_dev/driver/DriverTrackingRuntime.kt`
- `src/aplicativo-motorista/android/app/src/driver/kotlin/com/example/vanrides_frontend_dev/driver/DriverTrackingService.kt`

<a id="t36"></a>
## T36 — Evitar ações duplicadas na entrada

F09 · Onda 1 · Issue a criar

**Problema:** O mesmo evento de WhatsApp pode chegar novamente e repetir um efeito.

**Resultado esperado:** A mensagem é registrada e processada uma única vez para aquela identidade.

**Situação:** Implementado · Laboratório. Normalização, duplicatas e reinício testados com entradas e provedor simulados.

**Inclui:**

- Registro e identificação dos eventos recebidos, sem repetir efeitos.

**Fora desta tarefa:**

- Interpretar toda mensagem por IA ou provar entrega de saída.

**Critérios de conclusão:**

- [ ] Um evento válido é registrado com a identidade e o contexto corretos.
- [ ] Receber novamente o mesmo evento não executa outra ação de negócio.
- [ ] Reiniciar o processamento não repete o efeito dos eventos já tratados.

**Como testar (Laboratório):**

- Reenviar o mesmo evento antes e depois de reiniciar o serviço e conferir os efeitos gravados.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Entrada autenticada e formato de eventos do serviço de WhatsApp identificado.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Validar os eventos do número dedicado.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/transport/WaInboxService.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/transport/WaEvolutionNormalizer.java`

<a id="t37"></a>
## T37 — Guardar e enviar respostas

F09 · Onda 1 · Issue a criar

**Problema:** Sem uma resposta persistida, uma falha pode perder o que o sistema precisava enviar.

**Resultado esperado:** Respostas elegíveis ficam registradas e são encaminhadas ao serviço de WhatsApp.

**Situação:** Implementado · Laboratório. Envios ao receptor simulado e falhas controladas aprovados; entrega real ainda falta.

**Inclui:**

- Fila persistida e integração do envio de respostas.

**Fora desta tarefa:**

- Tratar envio aceito pelo serviço como entrega confirmada ao passageiro.

**Critérios de conclusão:**

- [ ] Uma resposta a enviar permanece registrada após reinício do processamento.
- [ ] O envio usa o destinatário e o conteúdo autorizados daquela resposta.
- [ ] O resultado técnico do envio fica registrado sem inventar confirmação de entrega.

**Como testar (Laboratório):**

- Criar uma resposta, reiniciar o processamento e conferir a submissão ao receptor controlado.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Adaptador do serviço de WhatsApp e respostas autorizadas disponíveis.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Comprovar a entrega e seu retorno pelo WhatsApp real.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/transport/WaOutboxService.java`
- `src/whatsapp/transport/evolution-text.mjs`

<a id="t38"></a>
## T38 — Interpretar mensagens com IA de forma limitada

F09 · Onda 1 · Issue a criar

**Problema:** Uma interpretação ambígua ou falha do modelo não pode executar uma ação incorreta.

**Resultado esperado:** A IA sugere intenção, mas validação e permissão continuam controlando a ação.

**Situação:** Implementado · Laboratório. Corpus com modelo real registrado; integração completa com WhatsApp não comprovada.

**Inclui:**

- Interpretação limitada e tratamento de recusa, demora e saída inválida.

**Fora desta tarefa:**

- Permitir que o modelo escolha a identidade ou autorize uma operação.

**Critérios de conclusão:**

- [ ] Uma interpretação válida resulta apenas em uma ação permitida pelo contexto.
- [ ] Saída inválida, recusa ou falha do modelo não executa ação de negócio.
- [ ] A tentativa de mudar identidade ou comando fora do permitido é recusada.

**Como testar (Teste automatizado):**

- Usar um conjunto fixo de mensagens e respostas simuladas do modelo, incluindo ambiguidades e falhas; conferir os comandos emitidos.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Intenções permitidas e regras de validação identificadas; chamadas pagas não são necessárias para estes cenários.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Revalidar as mensagens do uso real mantendo a autorização na API.

**Onde conferir:**

- `src/whatsapp/agent/model-policy.mjs`
- `src/whatsapp/worker/model-client.mjs`

<a id="t39"></a>
## T39 — Preparar automações importáveis

F09 · Onda 1 · Issue a criar

**Problema:** O grupo precisa transferir as automações sem levar credenciais ou ativá-las por engano.

**Resultado esperado:** As oito automações do n8n podem ser importadas e permanecem desativadas.

**Situação:** Implementado · Laboratório. Importação, execução isolada e reinício/reexportação aprovados; rotinas ficam desativadas.

**Inclui:**

- Arquivos de automação, referências e importação inativa.

**Fora desta tarefa:**

- Ativar agendas ou operar o WhatsApp real nesta tarefa.

**Critérios de conclusão:**

- [ ] Os oito arquivos são importados sem perder as conexões e referências previstas.
- [ ] Após importar e reiniciar, as automações permanecem desativadas.
- [ ] Os arquivos exportados não contêm senhas, chaves ou conversas reais.

**Como testar (Laboratório):**

- Importar em n8n separado, conferir referências, reiniciar e revisar a exportação resultante.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Versão do n8n e referências de configuração identificadas.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Ativar somente no ambiente autorizado após revisar a operação.

**Onde conferir:**

- `src/whatsapp/workflows/generate.mjs`
- `src/whatsapp/workflows/W08.json`

<a id="t40"></a>
## T40 — Configurar rotinas do motorista

F09 · Onda 1 · Issue a criar

**Problema:** Dias, horários e sentidos diferentes não podem ser tratados como uma única rotina implícita.

**Resultado esperado:** O motorista configura sua rotina pela conversa e confirma as mudanças.

**Situação:** Implementado · Laboratório. Criar, editar, pausar e retomar rotina foram exercitados.

**Inclui:**

- Dias, horários e sentidos da rotina nos comandos existentes.

**Fora desta tarefa:**

- Otimizar automaticamente o trajeto ou configurar outra pessoa sem permissão.

**Critérios de conclusão:**

- [ ] O motorista informa os dados e vê um resumo antes da confirmação.
- [ ] Ida e volta mantêm suas próprias configurações.
- [ ] Alteração inválida ou confirmação antiga não muda silenciosamente a rotina.

**Como testar (Laboratório):**

- Criar e editar rotinas de ida e volta e tentar confirmar uma proposta antiga.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Motorista cadastrado e autorizado para administrar suas rotinas.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Validar a configuração da rotina com participantes reais autorizados.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaDriverSetup.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaDriverMutations.java`

<a id="t41"></a>
## T41 — Mostrar vínculo e recuperação no aplicativo

F09 · Onda 1 · Issue a criar

**Problema:** O motorista precisa entender se o aparelho está vinculado ou se alguma ação é necessária.

**Resultado esperado:** A tela apresenta código, vínculo, falha e recuperação sem mensagens contraditórias.

**Situação:** Implementado · Físico parado. Vínculo no Redmi e testes de apresentação registrados; iPhone completo pendente.

**Inclui:**

- Apresentação dos estados do vínculo do aparelho.

**Fora desta tarefa:**

- Implementar novas regras de pareamento ou afirmar que o GPS iniciou ao abrir a tela.

**Critérios de conclusão:**

- [ ] O código aparece quando há pedido de vínculo válido.
- [ ] Vínculo ativo e aparelho desvinculado são apresentados como estados distintos.
- [ ] Uma falha ou recuperação pendente informa a ação necessária sem mostrar sucesso antecipado.

**Como testar (Laboratório):**

- Abrir os estados controlados do aplicativo e conferir o texto e as ações disponíveis.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Estados e respostas do vínculo disponíveis para o aplicativo.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Conferir a apresentação no iPhone e no percurso.

**Onde conferir:**

- `src/aplicativo-motorista/lib/features/driver_mvp/presentation/driver_pairing_app.dart`
- `src/aplicativo-motorista/lib/features/driver_mvp/presentation/driver_tracking_view_state.dart`

<a id="t42"></a>
## T42 — Vincular o aparelho correto

F09 · Onda 1 · Issue a criar

**Problema:** Copiar ou antecipar um link não pode vincular outro aparelho ao motorista.

**Resultado esperado:** A confirmação associa a conta à chave do pedido iniciado no aplicativo.

**Situação:** Implementado · Físico parado. Jornada de pareamento no Redmi com canal sintético aprovada.

**Inclui:**

- Pedido do aparelho e confirmação autenticada do vínculo.

**Fora desta tarefa:**

- Criar vínculo pela prévia de um link ou por identidade declarada pelo modelo.

**Critérios de conclusão:**

- [ ] O pedido iniciado no aplicativo é confirmado para a chave original daquele aparelho.
- [ ] Uma chave ou aparelho diferente não consegue obter o mesmo vínculo.
- [ ] Prévia do link e repetição da confirmação não criam um novo vínculo indevido.

**Como testar (Laboratório):**

- Confirmar um pedido válido, tentar outra chave e abrir a prévia do link; conferir o vínculo final.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Motorista autenticado e aplicativo capaz de criar o pedido de vínculo.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Repetir com a conta WhatsApp dedicada e depois no iPhone.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/pairing/WaPairingService.java`
- `src/aplicativo-motorista/lib/features/driver_mvp/presentation/driver_pairing_controller.dart`

<a id="t43"></a>
## T43 — Fixar a lista da partida

F05 · Onda 2 · Issue a criar

**Problema:** Mudanças posteriores de cadastro ou presença não podem reescrever o que foi considerado na partida.

**Resultado esperado:** A viagem inicia com uma lista identificada de passageiros e pontos.

**Situação:** Implementado · Laboratório. Teste activationFreezesTheCurrentNamesPinsOrderPolicyAndPendingPresence e jornada integrada registrados.

**Inclui:**

- Captura de participantes, pontos, ordem e presença no início.

**Fora desta tarefa:**

- Recalcular retroativamente toda a lista de uma viagem já iniciada.

**Critérios de conclusão:**

- [ ] O início captura os participantes elegíveis, incluindo quem está pendente segundo a regra atual.
- [ ] Mudanças posteriores de cadastro ou ordem não reescrevem essa lista inicial.
- [ ] Tentativas concorrentes não criam duas partidas para a mesma ocorrência.

**Como testar (Teste automatizado):**

- Iniciar a ocorrência, alterar dados depois e comparar a lista; repetir com inícios concorrentes.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Ocorrência, participantes e pontos aprovados disponíveis para iniciar a viagem.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Conferir o conjunto de passageiros no percurso real.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaRunPreparation.java`
- `src/backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaRunPreparationIntegrationTest.java`

<a id="t44"></a>
## T44 — Confirmar início somente com GPS válido

F05 · Onda 2 · Issue a criar

**Problema:** Abrir o link não significa que a posição já esteja sendo coletada e recebida.

**Resultado esperado:** O aplicativo só mostra coleta ativa após uma posição válida aceita pela API.

**Situação:** Implementado · Físico parado. Cenário A8 no Redmi parado comprovou primeira aquisição e confirmação única.

**Inclui:**

- Consumo do pedido de início e transição dos estados de coleta.

**Fora desta tarefa:**

- Prometer coleta invisível pelo navegador ou contornar permissões do aparelho.

**Critérios de conclusão:**

- [ ] Um pedido válido abre a preparação do acompanhamento no aparelho vinculado.
- [ ] Sem permissão ou sem posição aceita, não aparece confirmação de coleta ativa.
- [ ] A primeira posição válida aceita produz a confirmação correta sem duplicação.

**Como testar (Laboratório):**

- Abrir um pedido com permissão negada, depois sem posição e depois com primeira posição aceita.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Aparelho vinculado, pedido de início válido e receptor integrado disponíveis.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Repetir no iPhone e durante o percurso.

**Onde conferir:**

- `src/aplicativo-motorista/lib/features/driver_mvp/presentation/driver_tracking_setup.dart`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/tracking/WaTrackingService.java`
- `src/aplicativo-motorista/android/app/src/driver/kotlin/com/example/vanrides_frontend_dev/driver/DriverTrackingRuntime.kt`
- `src/aplicativo-motorista/android/app/src/driver/kotlin/com/example/vanrides_frontend_dev/driver/DriverTrackingService.kt`

<a id="t45"></a>
## T45 — Recuperar a parada sem internet

F05 · Onda 2 · Issue a criar

**Problema:** Uma parada feita sem rede não pode se perder nem reiniciar o sensor ao reconectar.

**Resultado esperado:** O aparelho para a coleta e confirma o mesmo pedido quando a comunicação volta.

**Situação:** Implementado · Físico parado. A8 confirmou o cenário Android; código iOS tem testes, sem repetição física atual.

**Inclui:**

- Parada local, persistência do pedido e reconciliação após reabrir.

**Fora desta tarefa:**

- Detectar automaticamente o fim da viagem nesta tarefa.

**Critérios de conclusão:**

- [ ] A parada interrompe o sensor mesmo sem conexão.
- [ ] Reabrir o aplicativo preserva a parada pendente e não inicia nova coleta.
- [ ] Ao reconectar, a confirmação encerra a pendência sem criar outro acompanhamento.

**Como testar (Teste físico delimitado):**

- Iniciar a coleta, cortar a rede, parar, reabrir e reconectar; conferir sensor e confirmação remota separadamente.
- Registro por aparelho e cenário, com tela/rede consideradas e resultado sanitizado, sem GPS bruto ou credenciais.

**Dependências:**

- Sessão integrada e aparelho com a versão identificada disponíveis em ensaio controlado.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Exercitar a mesma parada no iPhone físico e no percurso.

**Onde conferir:**

- `src/aplicativo-motorista/android/app/src/driver/kotlin/com/example/vanrides_frontend_dev/driver/DriverTrackingRuntime.kt`
- `src/aplicativo-motorista/android/app/src/driver/kotlin/com/example/vanrides_frontend_dev/driver/DriverTrackingService.kt`
- `src/aplicativo-motorista/ios/Runner/DriverTrackingCoordinator.swift`
- `src/aplicativo-motorista/ios/Runner/DriverTrackingCore.swift`

<a id="t46"></a>
## T46 — Repetir envios somente com segurança

F11 · Onda 2 · Issue a criar

**Problema:** Uma falha não pode levar vários processos a enviar o mesmo aviso ou repetir um envio incerto.

**Resultado esperado:** Cada tentativa tem controle exclusivo e repete apenas quando houver prova de que o envio não ocorreu.

**Situação:** Implementado · Laboratório. Testes de três tentativas e jornada integrada 120 passaram; canal externo não foi usado.

**Inclui:**

- Reserva da mensagem e novas tentativas limitadas pela política existente.

**Fora desta tarefa:**

- Reenviar cegamente uma tentativa com resultado desconhecido.

**Critérios de conclusão:**

- [ ] Dois processamentos concorrentes não autorizam o mesmo envio ao mesmo tempo.
- [ ] Nova tentativa só ocorre no caso seguro permitido e respeita limite e prazo originais.
- [ ] Reinício ou resultado tardio não reabre uma mensagem já encerrada.

**Como testar (Teste automatizado):**

- Simular disputa pelo envio, falha comprovadamente anterior à submissão, envio incerto e reinício.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Mensagem persistida e política atual de tentativas identificadas.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Comprovar as mesmas regras com o número dedicado.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/transport/WaDeliveryAttempts.java`
- `src/backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaDeliveryRetryIntegrationTest.java`
- `src/whatsapp/worker/dispatch.mjs`

<a id="t47"></a>
## T47 — Distinguir envio de entrega

F11 · Onda 2 · Issue a criar

**Problema:** Sem retorno confiável, o sistema pode informar que alguém recebeu uma mensagem que nunca foi confirmada.

**Resultado esperado:** O estado de entrega reflete a confirmação disponível e mantém a incerteza quando necessário.

**Situação:** Implementado · Laboratório. Cenários de retorno tardio e desconhecido testados; falta retorno real da Evolution.

**Inclui:**

- Conciliação dos resultados e retornos do serviço de WhatsApp.

**Fora desta tarefa:**

- Inferir entrega por semelhança de texto, telefone ou horário.

**Critérios de conclusão:**

- [ ] Um retorno válido é associado à tentativa correta.
- [ ] Sem prova suficiente, a tentativa não é apresentada como entregue.
- [ ] Retorno tardio pode melhorar o registro da tentativa sem reabrir uma mensagem encerrada.

**Como testar (Laboratório):**

- Aplicar retornos válidos, desconhecidos, repetidos e tardios e conferir o estado registrado.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Tentativas identificadas e formato do retorno do serviço documentado.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Verificar envio e confirmação reais com destinatário autorizado.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/transport/WaDeliveryReconciler.java`
- `src/backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaDeliveryRecoveryIntegrationTest.java`

<a id="t48"></a>
## T48 — Encerrar avisos sem utilidade

F11 · Onda 2 · Issue a criar

**Problema:** Um aviso atrasado ou recusado pelo passageiro não deve ser enviado como se ainda fosse útil.

**Resultado esperado:** A fila descarta mensagens vencidas ou sem autorização de recebimento.

**Situação:** Implementado · Laboratório. Expiração, perda de elegibilidade e retorno tardio cobertos na suíte 120.

**Inclui:**

- Expiração e preferência do destinatário antes de novo envio.

**Fora desta tarefa:**

- Escolher novos prazos ou garantir retirada de mensagem já submetida ao serviço.

**Critérios de conclusão:**

- [ ] Mensagem fora do prazo é encerrada antes de nova submissão.
- [ ] A perda de elegibilidade ou recusa de recebimento impede novas tentativas.
- [ ] Um retorno tardio não reativa a mensagem expirada ou cancelada.

**Como testar (Teste automatizado):**

- Avançar o relógio, mudar a preferência e aplicar retorno tardio; conferir que não há novo envio.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Prazos e preferências aplicáveis definidos na configuração da mensagem.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Repetir expiração e recusa durante o teste real de avisos.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/transport/WaDeliveryMaintenance.java`
- `src/backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaDeliveryRecoveryIntegrationTest.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/transport/WaDeliveryAttempts.java`
- `src/backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaDeliveryRetryIntegrationTest.java`

<a id="t49"></a>
## T49 — Avaliar aproximação por ponto e sentido

F11 · Onda 2 · Issue a criar

**Problema:** Distância isolada pode gerar aviso para o ponto ou sentido errado.

**Resultado esperado:** A regra usa posição atual, ponto e sentido e recusa situações ambíguas.

**Situação:** Planejado · Pendente. A regra do receptor isolado não é a entrega do aviso por pessoa.

**Inclui:**

- Avaliação da aproximação conforme a política configurada do MVP.

**Fora desta tarefa:**

- Inventar limiar de metros ou previsão numérica de chegada neste apoio.

**Critérios de conclusão:**

- [ ] A sequência controlada de posições produz o resultado documentado para o ponto e sentido selecionados, considerando tendência de aproximação e proteção contra oscilações.
- [ ] GPS desatualizado ou ambiguidade do cenário não gera aviso como se a aproximação estivesse comprovada.
- [ ] O texto não informa minutos de chegada sem cálculo validado.

**Como testar (Teste automatizado):**

- Reproduzir posições e pontos controlados, incluindo sentido incorreto, dado antigo e cenário ambíguo.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Antes de iniciar, documentar a versão da regra, os parâmetros de ensaio e os resultados esperados para aproximação, sentido incorreto e ambiguidade. Dispor de posições válidas e pontos aprovados.

**Decisão pendente:** Confirmar o conjunto versionado de parâmetros e casos usado no ensaio. Os valores iniciais existentes são hipóteses; a calibração e a utilidade em percurso continuam pendentes.

**Próxima ação:** Implementar a regra com posição atual, sentido e ponto corretos.

<a id="t50"></a>
## T50 — Respeitar progressão e cancelamento

F11 · Onda 2 · Issue a criar

**Problema:** O aviso precisa acompanhar a sequência dos pontos sem incluir quem cancelou.

**Resultado esperado:** Somente os próximos pontos e passageiros elegíveis podem gerar aviso.

**Situação:** Parcial · Teste automatizado. Lista congelada e ausência tardia testadas; regra de passagem e aproximação ainda pendente.

**Inclui:**

- Progressão da ordem, atualidade da posição e cancelamento da parada.

**Fora desta tarefa:**

- Confirmar embarque apenas por passar perto de um ponto.

**Critérios de conclusão:**

- [ ] A progressão não volta para um ponto já superado devido a amostra antiga.
- [ ] Cancelamento elimina a elegibilidade para avisos futuros daquele passageiro.
- [ ] Ponto ou sentido diferente não é tratado como o próximo embarque correto.

**Como testar (Teste automatizado):**

- Aplicar progressão, posição antiga e ausência durante a viagem e conferir os avisos elegíveis.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Lista da partida e regra de aproximação integradas.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Integrar a progressão dos pontos ao aviso de aproximação.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaDriverMutations.java`
- `src/backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaDriverRoutinesIntegrationTest.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaAttendanceMutation.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaAttendanceConversation.java`

<a id="t51"></a>
## T51 — Testar destinatário e repetição do aviso

F11 · Onda 2 · Issue a criar

**Problema:** Uma regra sem cenários de erro pode avisar a pessoa errada ou repetir mensagens em pontos comuns.

**Resultado esperado:** Os testes demonstram os limites do produtor de aproximação.

**Situação:** Planejado · Pendente. Testes do GPS isolado não fecham os cenários do produtor futuro.

**Inclui:**

- Destinatário, ponto compartilhado e prevenção de avisos repetidos.

**Fora desta tarefa:**

- Usar teste sintético como prova de utilidade durante um percurso real.

**Critérios de conclusão:**

- [ ] O teste falha quando o aviso aponta para destinatário ou ponto incorreto.
- [ ] Ponto compartilhado respeita a elegibilidade de cada pessoa.
- [ ] Reavaliação e reinício não produzem avisos duplicados para o mesmo caso.

**Como testar (Teste automatizado):**

- Executar uma matriz fixa de pessoas e pontos com contagem de avisos e comparação de destinatários.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Produtor de aproximação disponível e cenários esperados congelados antes da execução.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Criar e executar os cenários de destinatário, ponto comum e duplicação.

<a id="t52"></a>
## T52 — Encerrar a viagem automaticamente

F05 · Onda 2 · Issue a criar

**Problema:** A coleta não deve continuar sem necessidade, mas também não pode terminar antes da viagem.

**Resultado esperado:** O término real da viagem encerra a operação, com alternativa manual identificada.

**Situação:** Planejado · Pendente. Há proteção de sessão vencida, sem prova do término automático de negócio.

**Inclui:**

- Regra final de encerramento e comando manual de contingência.

**Fora desta tarefa:**

- Considerar silêncio do GPS ou uma passagem próxima como prova suficiente de fim.

**Critérios de conclusão:**

- [ ] Uma sequência de posições válidas compatível com progresso da viagem, destino final, velocidade e permanência da regra documentada encerra a viagem uma única vez.
- [ ] Ausência de posição ou passagem próxima isolada não encerra prematuramente a viagem.
- [ ] O encerramento manual permanece disponível e é identificado como contingência.

**Como testar (Laboratório):**

- Executar cenários de fim verdadeiro, passagem próxima, perda de GPS e término manual; conferir estados.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Antes de iniciar, documentar a versão da regra final, seus parâmetros e os cenários de fim verdadeiro e fim indevido. A regra deve considerar progresso, validade e precisão das posições, velocidade e permanência; a sessão integrada deve estar disponível.

**Decisão pendente:** Confirmar os parâmetros de ensaio e seus resultados esperados antes da implementação. As hipóteses existentes precisam de calibração em percurso; terminar manualmente não aprova o encerramento automático.

**Próxima ação:** Implementar e testar o reconhecimento de fim da viagem.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaCommandService.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/tracking/WaTrackingSamples.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/tracking/WaTrackingService.java`

<a id="t53"></a>
## T53 — Confirmar que a coleta parou

F05 · Onda 2 · Issue a criar

**Problema:** Encerrar uma viagem no servidor não comprova que o aparelho deixou de coletar.

**Resultado esperado:** O aplicativo informa parada confirmada ou pendência até a confirmação ou expiração prevista.

**Situação:** Parcial · Físico parado. Parada manual/offline Android testada; fim automático da viagem ainda não.

**Inclui:**

- Aplicação do encerramento no coletor e apresentação do estado de parada.

**Fora desta tarefa:**

- Declarar sucesso apenas porque a tela mudou de texto.

**Critérios de conclusão:**

- [ ] O encerramento recebido leva o coletor ao estado de parada.
- [ ] Se não houver confirmação, a pendência permanece visível até sua resolução prevista.
- [ ] Reabrir ou reconectar não reinicia a coleta encerrada.

**Como testar (Teste físico delimitado):**

- Encerrar com rede disponível e indisponível, conferir o estado nativo e depois reabrir e reconectar.
- Registro por aparelho e cenário, com tela/rede consideradas e resultado sanitizado, sem GPS bruto ou credenciais.

**Dependências:**

- Encerramento da viagem integrado ao contrato do coletor.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Integrar ao encerramento automático e repetir nos dois aparelhos.

**Onde conferir:**

- `src/aplicativo-motorista/android/app/src/driver/kotlin/com/example/vanrides_frontend_dev/driver/DriverTrackingRuntime.kt`
- `src/aplicativo-motorista/android/app/src/driver/kotlin/com/example/vanrides_frontend_dev/driver/DriverTrackingService.kt`
- `src/aplicativo-motorista/ios/Runner/DriverTrackingCoordinator.swift`
- `src/aplicativo-motorista/ios/Runner/DriverTrackingCore.swift`

<a id="t54"></a>
## T54 — Oferecer ajuda e recuperação

F05 · Onda 2 · Issue a criar

**Problema:** Uma falha ou retirada de acesso não pode deixar a pessoa presa em respostas automáticas.

**Resultado esperado:** A conversa oferece ajuda, pausa adequada e retomada controlada.

**Situação:** Parcial · Laboratório. Pausa e retomada de conversa testadas; não equivalem ao suporte operacional completo.

**Inclui:**

- AJUDA, retirada de acesso e recuperação da conversa no ciclo existente.

**Fora desta tarefa:**

- Criar central de atendimento completa ou prometer suporte humano imediato.

**Critérios de conclusão:**

- [ ] O pedido de ajuda preserva o contexto necessário e informa a ação disponível.
- [ ] A pausa para suporte impede respostas automáticas de negócio naquela conversa.
- [ ] Retirada de acesso e retomada deixam estado claro e não restauram permissões indevidas.

**Como testar (Laboratório):**

- Pedir ajuda em uma etapa incompleta, pausar, retirar acesso e executar a retomada permitida.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Estados de conversa e procedimentos de suporte e revogação identificados.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Concluir retirada de acesso e recuperação do ciclo de viagem.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaConversationPolicy.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaOnboardingCommands.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/pairing/WaPairingService.java`
- `src/aplicativo-motorista/lib/features/driver_mvp/presentation/driver_pairing_controller.dart`

<a id="t55"></a>
## T55 — Autorizar as automações de presença

F02 · Onda 1 · Issue a criar

**Problema:** Rotinas automáticas não devem fingir ser uma pessoa para alterar o calendário ou enviar lembretes.

**Resultado esperado:** A automação opera com permissão própria e não repete efeitos ao reiniciar.

**Situação:** Implementado · Laboratório. Presença e repetição após reinício testadas; operação contínua ainda pendente.

**Inclui:**

- Credencial da automação e processamento dos lembretes sem repetir efeitos.

**Fora desta tarefa:**

- Validar a agenda contínua de todo o ambiente nesta tarefa.

**Critérios de conclusão:**

- [ ] A operação automática aceita somente a permissão de serviço prevista.
- [ ] Credencial de pessoa não executa o processamento automático.
- [ ] Repetir a janela ou reiniciar preserva o resultado sem duplicar lembretes.

**Como testar (Teste automatizado):**

- Executar a mesma janela com credencial correta, incorreta e após reinício; conferir o resultado persistido.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Operações de calendário e lembrete implementadas e credencial de teste adequada.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Conferir o agendamento no ambiente definitivo de teste.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/attendance/WaAttendanceJobs.java`
- `src/whatsapp/worker/attendance-jobs.mjs`

<a id="t56"></a>
## T56 — Preservar automações ao reiniciar n8n

F09 · Onda 1 · Issue a criar

**Problema:** Uma reinicialização não pode perder as automações ou mensagens ainda pendentes.

**Resultado esperado:** O modo de execução escolhido recupera os componentes necessários sem repetir efeitos.

**Situação:** Parcial · Laboratório. Ensaio standalone 120 não valida o modo de fila/agendamento compartilhado.

**Inclui:**

- Persistência, reinício e recuperação das automações e pendências.

**Fora desta tarefa:**

- Alterar serviços compartilhados sem plano de ambiente próprio.

**Critérios de conclusão:**

- [ ] A configuração persistida é recuperada após reiniciar o modo de execução escolhido.
- [ ] Pendências continuam identificadas e não geram efeitos duplicados.
- [ ] Uma falha de comunicação deixa resultado recuperável e verificável, sem sucesso presumido.

**Como testar (Laboratório):**

- Preparar pendências em ambiente separado, reiniciar e repetir o processamento no modo operacional escolhido.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Modo de execução e armazenamento do n8n definidos para o ambiente avaliado.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Validar o modo operacional, a fila e as agendas do ambiente escolhido.

**Onde conferir:**

- `src/whatsapp/workflows/generate.mjs`
- `src/whatsapp/workflows/W08.json`

<a id="t57"></a>
## T57 — Limitar dados e aplicar exclusão

F10 · Onda 1 · Issue a criar

**Problema:** Cadastros e credenciais não devem ser guardados além do necessário ou sem aviso coerente.

**Resultado esperado:** O cadastro usa dados essenciais e aplica os prazos definidos para seu ciclo de vida.

**Situação:** Parcial · Laboratório. Aceite e descarte de alguns conteúdos testados; retenção integral ainda pendente.

**Inclui:**

- Minimização, consentimento e exclusão dos dados e credenciais previstos na tarefa.

**Fora desta tarefa:**

- Criar política jurídica nova ou declarar que todos os dados de fornecedores foram apagados sem prova.

**Critérios de conclusão:**

- [ ] O cadastro solicita somente os campos necessários ao fluxo definido.
- [ ] O aceite referencia o aviso apresentado à pessoa.
- [ ] Os prazos configurados produzem exclusão ou expiração verificável nas famílias cobertas, sem apagar registros fora da regra.

**Como testar (Teste automatizado):**

- Cadastrar dados fictícios, registrar o aceite e simular a passagem dos prazos configurados; conferir os destinos dos dados.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Finalidades, famílias de dados e prazos aplicáveis documentados para o cenário.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Definir e testar retenção e exclusão de todas as famílias necessárias.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaOnboardingCommands.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/transport/WaDeliveryMaintenance.java`

<a id="t58"></a>
## T58 — Provar cópia, restauração e exclusão

F09 · Onda 1 · Issue a criar

**Problema:** Ter um arquivo de cópia não demonstra que o serviço pode ser recuperado sem reintroduzir dados excluídos.

**Resultado esperado:** O conjunto coberto é restaurado em ambiente separado e respeita as exclusões previstas.

**Situação:** Parcial · Laboratório. O ensaio 020 não é prova de recuperação completa da API, n8n e dados do MVP.

**Inclui:**

- Cópia, restauração e reaplicação das regras de exclusão do conjunto do MVP delimitado.

**Fora desta tarefa:**

- Restaurar produção ou considerar a prova do receptor isolado suficiente para todo o produto.

**Critérios de conclusão:**

- [ ] A cópia identifica o conjunto e a versão cobertos.
- [ ] A restauração em destino separado recupera os registros de controle e o funcionamento esperado.
- [ ] Dados que deveriam permanecer excluídos não voltam a ficar utilizáveis após a restauração.

**Como testar (Laboratório):**

- Criar dados fictícios, realizar exclusão prevista, restaurar em outro destino e conferir dados e operação.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Catálogo do conjunto de cópia e regras de exclusão definidos, com destino de restauração separado.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Executar restauração isolada e reaplicação de exclusões do conjunto completo.

**Onde conferir:**

- `src/whatsapp/gps-spike/deploy/state_backup.py`

<a id="t59"></a>
## T59 — Orientar operação e parada segura

F09 · Onda 1 · Issue a criar

**Problema:** Sem alertas e procedimento claro, uma falha pode continuar produzindo envios incorretos.

**Resultado esperado:** O operador identifica falhas e pausa novos envios com segurança.

**Situação:** Parcial · Pendente. Inspeção dos guias e funções de saúde; sem teste operacional completo desta tarefa.

**Inclui:**

- Alertas, limites de uso e guia de operação do MVP delimitado.

**Fora desta tarefa:**

- Monitorar serviços não relacionados ou criar uma operação de suporte comercial.

**Critérios de conclusão:**

- [ ] Uma falha monitorada aparece ao operador com ação recomendada.
- [ ] O procedimento pausa novos envios sem impedir parada e recuperação já necessárias.
- [ ] O guia identifica configuração, limites e responsável de operação, sem credenciais expostas.

**Como testar (Laboratório):**

- Simular uma falha e seguir o procedimento de pausa e recuperação no ambiente separado.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Ambiente de teste, limites configurados e papel do operador definidos.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Consolidar alertas, limites e parada segura no ensaio de operação.

**Onde conferir:**

- `src/whatsapp/worker/README.md`
- `docs/runbooks/whatsapp-mvp/120-entrega-e-canal-real.md`

<a id="t60"></a>
## T60 — Identificar os arquivos da versão de teste

F05 · Onda 2 · Issue a criar

**Problema:** Sem uma lista única, o aplicativo e o servidor podem pertencer a versões incompatíveis.

**Resultado esperado:** A versão de teste tem seus componentes e origem identificados.

**Situação:** Parcial · Pendente. Provas individuais não certificam o candidato final de todas as funções.

**Inclui:**

- Inventário dos arquivos e versões aprovados que compõem o candidato.

**Fora desta tarefa:**

- Publicar ou aprovar a versão apenas por organizar seus arquivos.

**Critérios de conclusão:**

- [ ] Aplicativos, servidor, automações e configurações necessárias aparecem no mesmo inventário.
- [ ] A origem e a integridade de cada arquivo podem ser conferidas.
- [ ] Não há componente essencial sem origem ou arquivo necessário omitido.

**Como testar (Revisão documental ou reprodução):**

- Comparar o inventário com os arquivos reais do candidato e os resultados de verificação correspondentes.
- Lista dos arquivos ou procedimentos conferidos, versão correspondente e registro da revisão ou reprodução.

**Dependências:**

- Componentes e verificações do candidato disponíveis para identificação.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Fechar dependências e identificar todos os arquivos da versão integrada.

**Onde conferir:**

- `program/evidence/VR-WA-110-RUN-TRACKING.md`
- `program/evidence/VR-WA-120-DELIVERY-RECOVERY.md`

<a id="t61"></a>
## T61 — Preparar instalação de teste Android

F05 · Onda 2 · Issue a criar

**Problema:** O grupo precisa instalar uma versão identificável sem depender de um arquivo de origem desconhecida.

**Resultado esperado:** O pacote Android de teste tem assinatura e integridade conferíveis.

**Situação:** Implementado · Físico parado. Instalação e abertura conferidas; não implica que todas as funções do MVP estejam prontas.

**Inclui:**

- Pacote Android, assinatura de teste e instrução de instalação.

**Fora desta tarefa:**

- Publicação em loja ou aceite de todas as funções do MVP.

**Critérios de conclusão:**

- [ ] O pacote informa a versão esperada e tem assinatura identificada.
- [ ] A integridade do arquivo recebido pode ser conferida.
- [ ] A instalação e a abertura seguem as instruções no aparelho de referência.

**Como testar (Teste físico delimitado):**

- Conferir o pacote, instalar no Android de referência e abrir a versão identificada.
- Registro por aparelho e cenário, com tela/rede consideradas e resultado sanitizado, sem GPS bruto ou credenciais.

**Dependências:**

- Versão Android candidata e material de assinatura mantido fora da publicação.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Gerar nova versão quando os módulos pendentes forem integrados.

**Onde conferir:**

- `src/aplicativo-motorista/android/app/build.gradle.kts`
- `docs/runbooks/whatsapp-mvp/010-builds-e-instalacao.md`

<a id="t62"></a>
## T62 — Preparar instalação e renovação no iPhone

F05 · Onda 2 · Issue a criar

**Problema:** Uma assinatura temporária pode vencer e impedir que a equipe repita o teste.

**Resultado esperado:** O iPhone recebe a versão identificada e há instruções de instalação e renovação.

**Situação:** Parcial · Físico parado. Instalação mínima 010 aprovada; GPS integrado e renovação atual permanecem pendentes.

**Inclui:**

- Versão de desenvolvimento, instalação e renovação da autorização efetiva.

**Fora desta tarefa:**

- Exigir publicação em loja ou assinatura paga como requisito criado nesta tarefa.

**Critérios de conclusão:**

- [ ] A versão instalada corresponde ao pacote de teste identificado.
- [ ] Assinatura e validade efetivas são conferidas sem publicar material privado.
- [ ] O procedimento informa como renovar e repetir a instalação quando necessário.

**Como testar (Teste físico delimitado):**

- Instalar e abrir no aparelho de referência, conferir a validade e reproduzir os passos de renovação possíveis.
- Registro por aparelho e cenário, com tela/rede consideradas e resultado sanitizado, sem GPS bruto ou credenciais.

**Dependências:**

- iPhone disponibilizado, assinatura válida e ações de conta realizadas pelo proprietário.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Instalar e validar a versão integrada no iPhone após retomar os testes.

**Onde conferir:**

- `src/aplicativo-motorista/ios/Runner.xcodeproj/project.pbxproj`
- `docs/runbooks/whatsapp-mvp/010-builds-e-instalacao.md`

<a id="t63"></a>
## T63 — Planejar instalação e retorno no servidor

F05 · Onda 2 · Issue a criar

**Problema:** Uma atualização sem sequência e reversão pode atingir serviços ou dados indevidos.

**Resultado esperado:** Existe um plano concreto da instalação da versão de teste e de retorno à anterior.

**Situação:** Parcial · Pendente. O laboratório temporário não é o ambiente final de teste do produto.

**Inclui:**

- Serviços, atualização do banco, verificação de funcionamento e reversão do candidato.

**Fora desta tarefa:**

- Executar a instalação ou mudar DNS nesta tarefa de preparação.

**Critérios de conclusão:**

- [ ] O plano identifica ambiente, versão e componentes que serão alterados.
- [ ] A ordem da atualização e as verificações esperadas estão descritas.
- [ ] O retorno à versão anterior informa condições, dados preservados e passos verificáveis.

**Como testar (Revisão documental ou reprodução):**

- Revisar o plano contra os arquivos do candidato e a configuração observada do ambiente.
- Lista dos arquivos ou procedimentos conferidos, versão correspondente e registro da revisão ou reprodução.

**Dependências:**

- Versão candidata identificada e inventário de leitura do ambiente alvo.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Consolidar a implantação da versão completa com verificações e retorno.

**Onde conferir:**

- `docs/runbooks/whatsapp-mvp/110-laboratorio-temporario.md`

<a id="t64"></a>
## T64 — Instalar no ambiente autorizado de teste

F05 · Onda 2 · Issue a criar

**Problema:** A equipe precisa testar a versão integrada em um endereço e conjunto de serviços identificados.

**Resultado esperado:** O ambiente autorizado executa os componentes corretos e passa nas verificações combinadas.

**Situação:** Parcial · Laboratório. Prontidão e limpeza 120 aprovadas sem conta conectada ou despacho real.

**Inclui:**

- Instalação do candidato e conferência de endereço seguro e versões.

**Fora desta tarefa:**

- Produção ampla ou alterações extras em serviços compartilhados.

**Critérios de conclusão:**

- [ ] A instalação usa a versão e os serviços do plano autorizado.
- [ ] O endereço seguro responde e permite as operações previstas no teste de funcionamento.
- [ ] Serviços não abrangidos são preservados e o retorno pode ser acionado se a verificação falhar.

**Como testar (Laboratório):**

- Executar as verificações do plano no ambiente autorizado e comparar versões e serviços antes e depois.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Plano e candidato conferidos, autorização externa específica e configuração de endereço realizada quando necessária.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Preparar ambiente final quando as funções e o número dedicado estiverem disponíveis.

**Onde conferir:**

- `docs/runbooks/whatsapp-mvp/110-laboratorio-temporario.md`
- `src/whatsapp/worker/README.md`
- `docs/runbooks/whatsapp-mvp/120-entrega-e-canal-real.md`

<a id="t65"></a>
## T65 — Conferir o número dedicado

F11 · Onda 2 · Issue a criar

**Problema:** Simular o serviço de mensagens não demonstra que o bot conversa com os participantes reais autorizados.

**Resultado esperado:** A conta dedicada envia mensagem e tem o retorno de entrega conferido.

**Situação:** Planejado · Pendente. Proprietário precisa de outra linha; nenhuma entrega real foi comprovada.

**Inclui:**

- Pareamento do número de teste, envio e retorno correspondente.

**Fora desta tarefa:**

- Comprar linha, usar conta alheia ou inferir entrega só por resposta da API.

**Critérios de conclusão:**

- [ ] A conta de teste é pareada sob controle do proprietário.
- [ ] Uma mensagem é enviada somente ao participante autorizado e identificado para o ensaio.
- [ ] O retorno real é associado ao envio correto; ausência de confirmação continua explícita.

**Como testar (Laboratório):**

- Com o proprietário, parear a conta dedicada, enviar mensagem de teste e conferir o retorno correspondente.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Nova linha sob controle do proprietário e destinatário distinto e autorizado disponíveis.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Disponibilizar a linha e conferir envio e confirmação reais.

**Onde conferir:**

- `src/whatsapp/worker/README.md`
- `docs/runbooks/whatsapp-mvp/120-entrega-e-canal-real.md`

<a id="t66"></a>
## T66 — Testar agendas no ambiente escolhido

F02 · Onda 1 · Issue a criar

**Problema:** Uma chamada manual isolada não prova que os lembretes serão executados e recuperados continuamente.

**Resultado esperado:** A agenda real e as automações do ambiente de teste resistem à falha e ao reinício.

**Situação:** Parcial · Laboratório. Importação e chamada manual não comprovam agenda/fila contínuas do ambiente integrado.

**Inclui:**

- Agendamentos e recuperação no modo operacional autorizado.

**Fora desta tarefa:**

- Usar importação inativa como prova de agenda em execução.

**Critérios de conclusão:**

- [ ] A agenda dispara a operação prevista no ambiente configurado.
- [ ] Falha ou reinício preserva a pendência recuperável sem duplicar efeitos.
- [ ] A execução usa a permissão própria da automação e registra seu resultado.

**Como testar (Laboratório):**

- Observar um acionamento agendado, interromper o serviço no cenário combinado e conferir a recuperação.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Ambiente e modo de execução escolhidos e autorizados, com calendário de teste configurado.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Executar agenda real, reinício e recuperação no modo operacional escolhido.

**Onde conferir:**

- `src/whatsapp/workflows/generate.mjs`
- `src/whatsapp/workflows/W08.json`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/attendance/WaAttendanceJobs.java`
- `src/whatsapp/worker/attendance-jobs.mjs`

<a id="t67"></a>
## T67 — Testar a jornada completa no Android

F05 · Onda 2 · Issue a criar

**Problema:** Cenários parciais não demonstram que o motorista consegue usar o conjunto durante uma viagem.

**Resultado esperado:** O Android de referência executa a jornada integrada com WhatsApp real e resultado registrado.

**Situação:** Parcial · Físico parado. A8 não cobre WhatsApp real, aproximação útil e encerramento automático.

**Inclui:**

- Ciclo completo no Redmi Note 8 com a versão e o roteiro identificados.

**Fora desta tarefa:**

- Aprovar iPhone ou piloto ampliado com base no resultado Android.

**Critérios de conclusão:**

- [ ] A jornada inclui os fluxos essenciais do roteiro vigente no canal real.
- [ ] Percurso, aviso correto e encerramento são registrados no mesmo conjunto de versões.
- [ ] Falhas e etapas não executadas ficam explícitas e impedem declarar o ciclo completo aprovado.

**Como testar (Teste físico delimitado):**

- Executar o roteiro completo no Redmi Note 8 com participantes autorizados e coletar resultados sanitizados.
- Registro por aparelho e cenário, com tela/rede consideradas e resultado sanitizado, sem GPS bruto ou credenciais.

**Dependências:**

- Prova de localização Android e ambiente integrado com canal real disponíveis.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Executar o ciclo completo no Android com o canal real e percurso.

**Onde conferir:**

- `src/aplicativo-motorista/android/app/src/driver/kotlin/com/example/vanrides_frontend_dev/driver/DriverTrackingRuntime.kt`
- `src/aplicativo-motorista/android/app/src/driver/kotlin/com/example/vanrides_frontend_dev/driver/DriverTrackingService.kt`
- `docs/runbooks/whatsapp-mvp/110-laboratorio-temporario.md`

<a id="t68"></a>
## T68 — Testar a jornada completa no iPhone

F05 · Onda 2 · Issue a criar

**Problema:** O funcionamento Android não comprova os limites próprios do iPhone.

**Resultado esperado:** O iPhone executa a jornada integrada e tem resultado independente.

**Situação:** Planejado · Pendente. Abertura inicial e testes em simulador não substituem essa jornada física.

**Inclui:**

- Ciclo completo no iPhone físico com WhatsApp real e versão identificada.

**Fora desta tarefa:**

- Substituir percurso físico por simulador ou aprovação de outra plataforma.

**Critérios de conclusão:**

- [ ] A jornada é executada no iPhone físico com os fluxos do roteiro vigente.
- [ ] Percurso, aviso e encerramento são registrados na versão avaliada.
- [ ] Resultado e pendências são avaliados separadamente do Android.

**Como testar (Teste físico delimitado):**

- Retomar os testes físicos e executar o roteiro completo com participantes autorizados.
- Registro por aparelho e cenário, com tela/rede consideradas e resultado sanitizado, sem GPS bruto ou credenciais.

**Dependências:**

- Retomada do iPhone autorizada, prova de localização da plataforma e ambiente com canal real disponíveis.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Retomar o aparelho depois de fechar as dependências.

**Onde conferir:**

- `src/aplicativo-motorista/ios/Runner/DriverTrackingCoordinator.swift`
- `src/aplicativo-motorista/ios/Runner/DriverTrackingCore.swift`

<a id="t69"></a>
## T69 — Medir se o aviso ajuda no embarque

F11 · Onda 2 · Issue a criar

**Problema:** Um aviso tecnicamente enviado pode chegar tarde ou para a pessoa errada.

**Resultado esperado:** O ensaio mostra se a antecedência é útil e se o destinatário está correto.

**Situação:** Planejado · Pendente. Não há prova de avisos úteis nos percursos dos dois aparelhos.

**Inclui:**

- Utilidade e correção dos avisos nos percursos dos dois aparelhos.

**Fora desta tarefa:**

- Inventar uma meta numérica de minutos ou declarar satisfação sem observação.

**Critérios de conclusão:**

- [ ] O ensaio registra a referência do evento, recebimento do aviso e avaliação de utilidade pelo participante.
- [ ] O destinatário corresponde ao passageiro e ponto do cenário.
- [ ] O resultado por plataforma é comparado ao critério combinado antes do ensaio, mantendo falhas e perdas visíveis.

**Como testar (Teste físico delimitado):**

- Combinar a avaliação com o grupo e observar avisos reais durante os percursos, separadamente por plataforma.
- Registro por aparelho e cenário, com tela/rede consideradas e resultado sanitizado, sem GPS bruto ou credenciais.

**Dependências:**

- Produtor de aproximação, canal real e percursos disponíveis.

**Decisão pendente:** Combinar como será reconhecido um aviso útil antes de avaliar os percursos; este apoio não define minutos ou metros.

**Próxima ação:** Medir antecedência útil e destinatário correto após implementar os avisos.

<a id="t70"></a>
## T70 — Consolidar guia e resultados

F05 · Onda 2 · Issue a criar

**Problema:** Sem um guia e relatório único, a equipe depende de explicações informais para repetir o uso.

**Resultado esperado:** A entrega reúne instruções e resultados com seus limites reais.

**Situação:** Parcial · Pendente. Documentação existente não equivale ao pacote concluído do MVP.

**Inclui:**

- Guia de uso e relatório do conjunto do MVP.

**Fora desta tarefa:**

- Declarar piloto aprovado ou ocultar pendências para apresentar o pacote como completo.

**Critérios de conclusão:**

- [ ] O guia explica instalação, entrada, uso essencial e recuperação.
- [ ] O relatório identifica versões, cenários executados e resultados não executados ou reprovados.
- [ ] Limites e próximos passos ficam claros para quem não acompanhou o desenvolvimento.

**Como testar (Revisão documental ou reprodução):**

- Pedir a uma pessoa da equipe que siga o guia e compare cada conclusão do relatório com sua evidência.
- Lista dos arquivos ou procedimentos conferidos, versão correspondente e registro da revisão ou reprodução.

**Dependências:**

- Arquivos de instalação, roteiros e resultados dos componentes reunidos.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Consolidar o guia e os resultados após a validação completa.

**Onde conferir:**

- `src/whatsapp/worker/README.md`
- `docs/runbooks/whatsapp-mvp/120-entrega-e-canal-real.md`
- `docs/runbooks/whatsapp-mvp/010-builds-e-instalacao.md`

<a id="t71"></a>
## T71 — Reproduzir a instalação final

F05 · Onda 2 · Issue a criar

**Problema:** Arquivos soltos ou passos ocultos podem impedir que outra pessoa instale o que foi entregue.

**Resultado esperado:** A equipe repete instalação e renovação a partir do pacote identificado.

**Situação:** Planejado · Pendente. Instalações individuais anteriores não certificam a entrega final.

**Inclui:**

- Reprodução do pacote completo e conferência de arquivos necessários.

**Fora desta tarefa:**

- Reutilizar uma instalação antiga como prova do pacote novo.

**Critérios de conclusão:**

- [ ] A instalação usa somente os arquivos e referências de configuração indicados.
- [ ] A versão aberta no aparelho corresponde à entrega avaliada.
- [ ] A renovação aplicável pode ser seguida sem credencial em chat ou arquivo privado esquecido no computador de quem preparou.

**Como testar (Teste físico delimitado):**

- Repetir a instalação a partir do pacote separado e conferir a renovação e a versão executada.
- Registro por aparelho e cenário, com tela/rede consideradas e resultado sanitizado, sem GPS bruto ou credenciais.

**Dependências:**

- Pacote final e instruções completos, aparelhos e ações de conta do proprietário disponíveis.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Reproduzir instalação e renovação do pacote final nos dois aparelhos.

**Onde conferir:**

- `docs/runbooks/whatsapp-mvp/010-builds-e-instalacao.md`

<a id="t72"></a>
## T72 — Proposta: cancelar ou corrigir presença

F06 · Onda 2 · Issue a criar

**Problema:** O passageiro pode mudar sua participação e precisa saber qual viagem será afetada.

**Resultado esperado:** Se a inclusão for aprovada, cancelamento e correção têm um resultado claro para a ocorrência certa.

**Situação:** Proposta de escopo · Laboratório. Mudança de resposta e ausência após início foram testadas no laboratório.

**Inclui:**

- Proposta de uso do cancelamento e correção já identificados no código no recorte da Onda 2.

**Fora desta tarefa:**

- Aprovar a mudança no planejamento de funcionalidades ou avisar o motorista como parte desta definição.

**Critérios de conclusão:**

- [ ] A resposta altera somente a ocorrência selecionada pelo passageiro autorizado.
- [ ] A correção mantém o histórico mínimo previsto e não altera outra data ou sentido.
- [ ] O retorno explica o resultado e a eventual limitação após o início da viagem.

**Como testar (Laboratório):**

- Revisar os cenários existentes de correção e ausência e demonstrar seus resultados à equipe.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Decisão do grupo sobre a inclusão na Onda 2 e regra de presença vigente identificadas.

**Decisão pendente:** Confirmar a inclusão de cancelamento e correção no recorte acadêmico e registrar a mudança do planejamento.

**Próxima ação:** Confirmar o enquadramento da funcionalidade com a equipe.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaAttendanceMutation.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaAttendanceConversation.java`
- `src/backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaAttendanceCutoffIntegrationTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaAttendanceJobsIntegrationTest.java`

<a id="t73"></a>
## T73 — Proposta: definir limite da alteração

F06 · Onda 2 · Issue a criar

**Problema:** O texto de horário limite pode ser entendido como bloqueio total, mas a regra atual permite informar ausência depois da partida.

**Resultado esperado:** Se a proposta for aprovada, equipe e implementação usam a mesma regra de alteração e histórico.

**Situação:** Proposta de escopo · Teste automatizado. Os testes cobrem corte e preservação da lista, com semântica diferente de proibir todo cancelamento tardio.

**Inclui:**

- Significado do limite e comportamento antes e depois dele, preservando a lista da partida.

**Fora desta tarefa:**

- Escolher silenciosamente bloquear toda ausência tardia.

**Critérios de conclusão:**

- [ ] A regra acordada descreve quais alterações são permitidas antes e depois do limite.
- [ ] Os cenários dos dois lados do limite produzem exatamente o comportamento acordado.
- [ ] A alteração não reescreve a lista inicial e deixa o histórico mínimo previsto.

**Como testar (Teste automatizado):**

- Comparar os testes de corte atuais com a regra que a equipe escolher e registrar as diferenças antes de mudar código.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Acordo explícito sobre a semântica de horário limite e sobre a proposta de escopo.

**Decisão pendente:** Resolver a divergência entre o texto do backlog e a regra atual que permite ausência após a partida; não há bloqueio total aprovado por esta especificação.

**Próxima ação:** Alinhar o significado de horário limite antes de declarar a tarefa pronta.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaAttendanceMutation.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaAttendanceConversation.java`
- `src/backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaAttendanceCutoffIntegrationTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaAttendanceJobsIntegrationTest.java`

<a id="t74"></a>
## T74 — Proposta: avisar cancelamento ao motorista

F12 · Onda 2 · Issue a criar

**Problema:** Alterar a presença sem informar o motorista pode manter a expectativa de um embarque que foi cancelado.

**Resultado esperado:** Se aprovada, a mudança gera aviso adequado ao motorista correto.

**Situação:** Proposta de escopo · Pendente. WaAttendanceMutation responde ao passageiro; não foi identificado envio correspondente ao motorista.

**Inclui:**

- Aviso de alteração de presença ao motorista pelo WhatsApp.

**Fora desta tarefa:**

- Tratar a resposta ao passageiro como prova de que o motorista foi avisado.

**Critérios de conclusão:**

- [ ] Uma alteração elegível gera aviso para o motorista responsável pela viagem.
- [ ] Repetir a alteração ou o processamento não cria avisos repetidos para a mesma mudança.
- [ ] A mensagem identifica a mudança e não é enviada como atual depois de perder a utilidade.

**Como testar (Laboratório):**

- Simular alteração de presença, conferir a mensagem destinada ao motorista e repetir o evento.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Proposta de escopo aprovada e cancelamento da ocorrência identificado.

**Decisão pendente:** Confirmar a inclusão e a regra de quando o motorista deve receber o aviso; a entrega ao motorista ainda não foi comprovada.

**Próxima ação:** Aprovar o ajuste e implementar ou localizar o aviso ao motorista.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaAttendanceMutation.java`
- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaAttendanceConversation.java`

<a id="t75"></a>
## T75 — Mostrar lista de presença ao motorista

F02 · Onda 1 · Issue a criar

**Problema:** O motorista precisa distinguir confirmação, ausência e falta de resposta para organizar a viagem.

**Resultado esperado:** A lista apresenta a ocorrência correta e a situação de cada passageiro autorizado.

**Situação:** Implementado · Laboratório. Motorista consultou a lista e duas desistências no laboratório.

**Inclui:**

- Consulta da lista por data e sentido no atendimento do motorista.

**Fora desta tarefa:**

- Expor listas de outro motorista ou converter pendência em confirmação.

**Critérios de conclusão:**

- [ ] A consulta identifica data e sentido da lista apresentada.
- [ ] Confirmados, ausentes e pendentes aparecem como situações distintas.
- [ ] Outro motorista não acessa a mesma lista sem autorização.

**Como testar (Laboratório):**

- Consultar duas ocorrências com situações diferentes e tentar acesso com outra identidade.
- Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito.

**Dependências:**

- Motorista autorizado e ocorrências com respostas de teste existentes.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Verificar a mesma consulta pelo número dedicado.

**Onde conferir:**

- `src/backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaAttendanceSummary.java`

<a id="t78"></a>
## T78 — Conferir contrato, permissões e banco

F09 · Onda 1 · Issue a criar

**Problema:** Mudanças nas operações ou estrutura dos dados podem quebrar cadastros e consumidores sem ficar visíveis.

**Resultado esperado:** Os testes verificam as operações previstas, suas permissões e as atualizações do banco.

**Situação:** Implementado · Teste automatizado. Suítes PostgreSQL e contratos constam nas evidências; nenhum teste novo foi executado nesta leitura.

**Inclui:**

- Testes de contrato, autorização e migrações do domínio do MVP.

**Fora desta tarefa:**

- Executar implantação em produção ou considerar testes ignorados como aprovação.

**Critérios de conclusão:**

- [ ] As operações aceitam e recusam os exemplos previstos no contrato avaliado.
- [ ] A matriz de permissões cobre identidade correta, outra pessoa e credencial incompatível.
- [ ] As atualizações do banco e invariantes passam no PostgreSQL de teste, com falhas e casos não executados visíveis.

**Como testar (Teste automatizado):**

- Executar as verificações proporcionais do produtor e consumidor e os cenários de banco exigidos para a alteração avaliada.
- Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas.

**Dependências:**

- Contrato e versões avaliadas identificados e ambiente de banco de teste disponível.

**Decisão pendente:** Nenhuma adicional identificada.

**Próxima ação:** Preservar os testes ao concluir os próximos componentes.

**Onde conferir:**

- `src/backend/contracts/whatsapp-mvp/openapi.yaml`
- `src/backend/src/test/java/com/vanrides/vanrides/whatsapp/auth/WaFoundationIntegrationTest.java`
- `src/backend/src/test/java/com/vanrides/vanrides/whatsapp/persistence/WhatsappMigrationIntegrationTest.java`
