# Backlog do MVP

O backlog descreve trabalho; implementação e validação são informações separadas.
As issues são o espaço de discussão e revisão. A planilha mantém o formato do professor.

## Por onde começar

1. Leia a funcionalidade e seus critérios na issue. Confira se pertence ao objetivo ativo do programa.
2. Combine com o grupo quem implementa e quem revisa; registre os arquivos envolvidos.
3. Reproduza a situação atual e registre a lacuna antes de alterar código.
4. Abra um PR pequeno com resultado, teste e limitações. A CI não substitui o aceite específico.

| Frente | Próximo resultado necessário | Dependência |
|---|---|---|
| Cadastro e presença | Repetir as conversas no canal real | Linha dedicada e participantes autorizados |
| Aproximação | Concluir regras, testes e calibração do aviso | Posição recente, ordem de pontos e parâmetros revisados |
| Encerramento | Implementar a regra de fim e confirmar a parada da coleta | Critérios de chegada e recuperação |
| Android e iPhone | Percurso completo, rede, bateria e destinatário correto | Funções integradas; testes físicos iPhone pausados |
| Contribuição do grupo | Reproduzir uma frente e enviar o primeiro PR | Guia de desenvolvimento e revisão de um colega |

## Como interpretar a situação

| Implementação | Quantidade | O que falta para encerrar |
|---|---:|---|
| Implementado | 34 | Conferir escopo e provas específicas; não significa aceite real |
| Parcial | 18 | Completar a lacuna descrita e testar |
| Planejado | 10 | Definir execução, implementar e validar |
| Proposta de escopo | 3 | Decidir com o grupo antes de implementar |

Essas contagens não medem percentual concluído. Teste automatizado, laboratório, aparelho parado e percurso são provas diferentes.
Uma tarefa só termina quando seus critérios são atendidos, o PR foi revisado e a prova registra a versão avaliada.

## Lista por onda

Todas as tarefas atuais terão uma issue. A segunda onda fica no backlog, sem ser iniciada por sua publicação.
Referências antigas ficam identificadas como histórico e fora da lista atual. T76/T77 foram retiradas junto da navegação externa.

### Onda 1

| ID / issue | Resultado da tarefa | Implementação | Validação | Próxima ação |
|---|---|---|---|---|
| [T01 · #25](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/25) | A API Java e o banco ficam prontos para receber os cadastros do atendimento. | Implementado | Laboratório | Manter a configuração compatível na versão de teste. |
| [T02 · #26](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/26) | O motorista consegue se cadastrar pelo WhatsApp com seus dados validados. | Implementado | Laboratório | Repetir a conversa com o número dedicado. |
| [T03 · #27](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/27) | Cada ação usa a identidade e a permissão corretas. | Implementado | Laboratório | Preservar a autorização ao conectar o canal real. |
| [T04 · #28](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/28) | O passageiro informa os dados necessários e tem seu ponto conferido pelo motorista. | Implementado | Laboratório | Conferir o cadastro no WhatsApp real. |
| [T05 · #29](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/29) | O convite permite uma solicitação válida e o motorista decide o vínculo. | Implementado | Laboratório | Verificar convite com participantes autorizados no número dedicado. |
| [T6 · #30](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/30) | Cada passageiro confirma ou informa ausência para a viagem indicada. | Implementado | Laboratório | Repetir respostas e lembrete no canal real. |
| [T23](08-especificacoes-mvp.md#t23) | A conversa orienta cadastro, envio do ponto e correção de forma compreensível. | Implementado | Laboratório | Revisar a compreensão das mensagens com a equipe no canal real. |
| [T24](08-especificacoes-mvp.md#t24) | As ocorrências seguem os dias e horários da rotina no fuso configurado. | Implementado | Laboratório | Conferir a configuração de São Paulo no ensaio real. |
| [T25](08-especificacoes-mvp.md#t25) | O passageiro recebe a pergunta da viagem correta às 20h do dia anterior. | Implementado | Laboratório | Executar o agendamento com São Paulo no ambiente autorizado. |
| [T26](08-especificacoes-mvp.md#t26) | Os testes demonstram que o contexto de cada resposta é preservado. | Implementado | Teste automatizado | Manter regressões e repetir os casos essenciais no WhatsApp real. |
| [T27](08-especificacoes-mvp.md#t27) | O grupo identifica as versões e consegue recuperar a configuração anterior. | Implementado | Teste automatizado | Preservar a cópia recuperável nas próximas entregas. |
| [T28](08-especificacoes-mvp.md#t28) | Uma versão mínima identificada funciona no aparelho de teste. | Implementado | Físico parado | Usar a versão integrada nos próximos testes físicos. |
| [T29](08-especificacoes-mvp.md#t29) | Uma versão mínima instalada tem assinatura e renovação documentadas. | Implementado | Físico parado | Renovar a instalação e retomar os testes do coletor quando autorizado. |
| [T36](08-especificacoes-mvp.md#t36) | A mensagem é registrada e processada uma única vez para aquela identidade. | Implementado | Laboratório | Validar os eventos do número dedicado. |
| [T37](08-especificacoes-mvp.md#t37) | Respostas elegíveis ficam registradas e são encaminhadas ao serviço de WhatsApp. | Implementado | Laboratório | Comprovar a entrega e seu retorno pelo WhatsApp real. |
| [T38](08-especificacoes-mvp.md#t38) | A IA sugere intenção, mas validação e permissão continuam controlando a ação. | Implementado | Laboratório | Revalidar as mensagens do uso real mantendo a autorização na API. |
| [T39](08-especificacoes-mvp.md#t39) | As oito automações do n8n podem ser importadas e permanecem desativadas. | Implementado | Laboratório | Ativar somente no ambiente autorizado após revisar a operação. |
| [T40](08-especificacoes-mvp.md#t40) | O motorista configura sua rotina pela conversa e confirma as mudanças. | Implementado | Laboratório | Validar a configuração da rotina com participantes reais autorizados. |
| [T41](08-especificacoes-mvp.md#t41) | A tela apresenta código, vínculo, falha e recuperação sem mensagens contraditórias. | Implementado | Físico parado | Conferir a apresentação no iPhone e no percurso. |
| [T42](08-especificacoes-mvp.md#t42) | A confirmação associa a conta à chave do pedido iniciado no aplicativo. | Implementado | Físico parado | Repetir com a conta WhatsApp dedicada e depois no iPhone. |
| [T55](08-especificacoes-mvp.md#t55) | A automação opera com permissão própria e não repete efeitos ao reiniciar. | Implementado | Laboratório | Conferir o agendamento no ambiente definitivo de teste. |
| [T56](08-especificacoes-mvp.md#t56) | O modo de execução escolhido recupera os componentes necessários sem repetir efeitos. | Parcial | Laboratório | Validar o modo operacional, a fila e as agendas do ambiente escolhido. |
| [T57](08-especificacoes-mvp.md#t57) | O cadastro usa dados essenciais e aplica os prazos definidos para seu ciclo de vida. | Parcial | Laboratório | Definir e testar retenção e exclusão de todas as famílias necessárias. |
| [T58](08-especificacoes-mvp.md#t58) | O conjunto coberto é restaurado em ambiente separado e respeita as exclusões previstas. | Parcial | Laboratório | Executar restauração isolada e reaplicação de exclusões do conjunto completo. |
| [T59](08-especificacoes-mvp.md#t59) | O operador identifica falhas e pausa novos envios com segurança. | Parcial | Pendente | Consolidar alertas, limites e parada segura no ensaio de operação. |
| [T66](08-especificacoes-mvp.md#t66) | A agenda real e as automações do ambiente de teste resistem à falha e ao reinício. | Parcial | Laboratório | Executar agenda real, reinício e recuperação no modo operacional escolhido. |
| [T75](08-especificacoes-mvp.md#t75) | A lista apresenta a ocorrência correta e a situação de cada passageiro autorizado. | Implementado | Laboratório | Verificar a mesma consulta pelo número dedicado. |
| [T78](08-especificacoes-mvp.md#t78) | Os testes verificam as operações previstas, suas permissões e as atualizações do banco. | Implementado | Teste automatizado | Preservar os testes ao concluir os próximos componentes. |

### Onda 2

| ID / issue | Resultado da tarefa | Implementação | Validação | Próxima ação |
|---|---|---|---|---|
| [T7 · #31](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/31) | A API recebe somente as posições válidas da sessão e do aparelho autorizados. | Implementado | Físico parado | Validar continuidade durante os percursos nos dois aparelhos. |
| [T9a · #33](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/33) | Somente o motorista e os passageiros autorizados consultam a posição daquela viagem. | Parcial | Teste automatizado | Implementar a consulta com escopo dos participantes e seus testes. |
| [T9b · #34](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/34) | A resposta informa a última posição válida e quando ela foi obtida. | Planejado | Pendente | Implementar a resposta com horário e indicação de posição antiga. |
| [T10 · #35](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/35) | O armazenamento fornece a última posição válida e o horário para a consulta autorizada. | Parcial | Teste automatizado | Concluir a consulta autorizada e testar posição desatualizada. |
| [T11 · #36](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/36) | Uma aproximação válida gera um aviso para o passageiro correto. | Planejado | Pendente | Implementar a regra e o aviso por passageiro antes do teste de utilidade. |
| [T30](08-especificacoes-mvp.md#t30) | O Android coleta posições em serviço nativo e mostra seu estado. | Implementado | Laboratório | Concluir os ensaios de percurso e energia no Android. |
| [T31](08-especificacoes-mvp.md#t31) | O coletor nativo mantém o estado de localização independentemente da tela. | Implementado | Laboratório | Executar o coletor no iPhone físico após a retomada. |
| [T32](08-especificacoes-mvp.md#t32) | As posições fictícias chegam a um receptor separado e autenticado. | Implementado | Laboratório | Manter separado do receptor integrado e dos dados reais. |
| [T33](08-especificacoes-mvp.md#t33) | O percurso Android produz evidência de continuidade e recuperação da rede. | Parcial | Pendente | Realizar o percurso Android e registrar atrasos e recuperação da rede. |
| [T34](08-especificacoes-mvp.md#t34) | O percurso no iPhone demonstra continuidade ou expõe precisamente suas limitações. | Planejado | Pendente | Retomar o aparelho e executar o percurso completo. |
| [T35](08-especificacoes-mvp.md#t35) | Cada aparelho tem resultado de consumo e recuperação no cenário combinado. | Parcial | Pendente | Medir bateria e interrupções em Android e iPhone no cenário previsto. |
| [T43](08-especificacoes-mvp.md#t43) | A viagem inicia com uma lista identificada de passageiros e pontos. | Implementado | Laboratório | Conferir o conjunto de passageiros no percurso real. |
| [T44](08-especificacoes-mvp.md#t44) | O aplicativo só mostra coleta ativa após uma posição válida aceita pela API. | Implementado | Físico parado | Repetir no iPhone e durante o percurso. |
| [T45](08-especificacoes-mvp.md#t45) | O aparelho para a coleta e confirma o mesmo pedido quando a comunicação volta. | Implementado | Físico parado | Exercitar a mesma parada no iPhone físico e no percurso. |
| [T46](08-especificacoes-mvp.md#t46) | Cada tentativa tem controle exclusivo e repete apenas quando houver prova de que o envio não ocorreu. | Implementado | Laboratório | Comprovar as mesmas regras com o número dedicado. |
| [T47](08-especificacoes-mvp.md#t47) | O estado de entrega reflete a confirmação disponível e mantém a incerteza quando necessário. | Implementado | Laboratório | Verificar envio e confirmação reais com destinatário autorizado. |
| [T48](08-especificacoes-mvp.md#t48) | A fila descarta mensagens vencidas ou sem autorização de recebimento. | Implementado | Laboratório | Repetir expiração e recusa durante o teste real de avisos. |
| [T49](08-especificacoes-mvp.md#t49) | A regra usa posição atual, ponto e sentido e recusa situações ambíguas. | Planejado | Pendente | Implementar a regra com posição atual, sentido e ponto corretos. |
| [T50](08-especificacoes-mvp.md#t50) | Somente os próximos pontos e passageiros elegíveis podem gerar aviso. | Parcial | Teste automatizado | Integrar a progressão dos pontos ao aviso de aproximação. |
| [T51](08-especificacoes-mvp.md#t51) | Os testes demonstram os limites do produtor de aproximação. | Planejado | Pendente | Criar e executar os cenários de destinatário, ponto comum e duplicação. |
| [T52](08-especificacoes-mvp.md#t52) | O término real da viagem encerra a operação, com alternativa manual identificada. | Planejado | Pendente | Implementar e testar o reconhecimento de fim da viagem. |
| [T53](08-especificacoes-mvp.md#t53) | O aplicativo informa parada confirmada ou pendência até a confirmação ou expiração prevista. | Parcial | Físico parado | Integrar ao encerramento automático e repetir nos dois aparelhos. |
| [T54](08-especificacoes-mvp.md#t54) | A conversa oferece ajuda, pausa adequada e retomada controlada. | Parcial | Laboratório | Concluir retirada de acesso e recuperação do ciclo de viagem. |
| [T60](08-especificacoes-mvp.md#t60) | A versão de teste tem seus componentes e origem identificados. | Parcial | Pendente | Fechar dependências e identificar todos os arquivos da versão integrada. |
| [T61](08-especificacoes-mvp.md#t61) | O pacote Android de teste tem assinatura e integridade conferíveis. | Implementado | Físico parado | Gerar nova versão quando os módulos pendentes forem integrados. |
| [T62](08-especificacoes-mvp.md#t62) | O iPhone recebe a versão identificada e há instruções de instalação e renovação. | Parcial | Físico parado | Instalar e validar a versão integrada no iPhone após retomar os testes. |
| [T63](08-especificacoes-mvp.md#t63) | Existe um plano concreto da instalação da versão de teste e de retorno à anterior. | Parcial | Pendente | Consolidar a implantação da versão completa com verificações e retorno. |
| [T64](08-especificacoes-mvp.md#t64) | O ambiente autorizado executa os componentes corretos e passa nas verificações combinadas. | Parcial | Laboratório | Preparar ambiente final quando as funções e o número dedicado estiverem disponíveis. |
| [T65](08-especificacoes-mvp.md#t65) | A conta dedicada envia mensagem e tem o retorno de entrega conferido. | Planejado | Pendente | Disponibilizar a linha e conferir envio e confirmação reais. |
| [T67](08-especificacoes-mvp.md#t67) | O Android de referência executa a jornada integrada com WhatsApp real e resultado registrado. | Parcial | Físico parado | Executar o ciclo completo no Android com o canal real e percurso. |
| [T68](08-especificacoes-mvp.md#t68) | O iPhone executa a jornada integrada e tem resultado independente. | Planejado | Pendente | Retomar o aparelho depois de fechar as dependências. |
| [T69](08-especificacoes-mvp.md#t69) | O ensaio mostra se a antecedência é útil e se o destinatário está correto. | Planejado | Pendente | Medir antecedência útil e destinatário correto após implementar os avisos. |
| [T70](08-especificacoes-mvp.md#t70) | A entrega reúne instruções e resultados com seus limites reais. | Parcial | Pendente | Consolidar o guia e os resultados após a validação completa. |
| [T71](08-especificacoes-mvp.md#t71) | A equipe repete instalação e renovação a partir do pacote identificado. | Planejado | Pendente | Reproduzir instalação e renovação do pacote final nos dois aparelhos. |
| [T72](08-especificacoes-mvp.md#t72) | Se a inclusão for aprovada, cancelamento e correção têm um resultado claro para a ocorrência certa. | Proposta de escopo | Laboratório | Confirmar o enquadramento da funcionalidade com a equipe. |
| [T73](08-especificacoes-mvp.md#t73) | Se a proposta for aprovada, equipe e implementação usam a mesma regra de alteração e histórico. | Proposta de escopo | Teste automatizado | Alinhar o significado de horário limite antes de declarar a tarefa pronta. |
| [T74](08-especificacoes-mvp.md#t74) | Se aprovada, a mudança gera aviso adequado ao motorista correto. | Proposta de escopo | Pendente | Aprovar o ajuste e implementar ou localizar o aviso ao motorista. |

## Manutenção

Os dados ficam em `docs/backlog/tarefas.json`, `acompanhamento.json` e `criterios.json`.
Preserve os IDs. Atualize os dados e a issue no mesmo trabalho; gere este documento com `python3 scripts/backlog.py`.
O programa controla o objetivo ativo e os gates. Não marque todas as tarefas como concluídas por fechar um card técnico agregado.
BACKLOG.xlsx conserva duas abas e oito colunas; os números de issue devem ser reais. Para atualizar a planilha, siga o guia de contribuição.
