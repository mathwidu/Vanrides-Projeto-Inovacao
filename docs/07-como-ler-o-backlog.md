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
| Implementado | 41 | Conferir escopo e provas específicas; não significa aceite real |
| Parcial | 15 | Completar a lacuna descrita e testar |
| Planejado | 6 | Definir execução, implementar e validar |
| Proposta de escopo | 3 | Decidir com o grupo antes de implementar |

Essas contagens não medem percentual concluído. Teste automatizado, laboratório, aparelho parado e percurso são provas diferentes.
Uma tarefa só termina quando seus critérios são atendidos, o PR foi revisado e a prova registra a versão avaliada.

## Lista por onda

Todas as tarefas atuais têm uma issue. Já há implementação de cadastro e presença na Onda 1 e de coleta de localização na Onda 2. As issues das duas ondas registram esse andamento e o trabalho restante; publicar uma tarefa não inicia sua execução.
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
| [T23 · #49](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/49) | A conversa orienta cadastro, envio do ponto e correção de forma compreensível. | Implementado | Laboratório | Revisar a compreensão das mensagens com a equipe no canal real. |
| [T24 · #50](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/50) | As ocorrências seguem os dias e horários da rotina no fuso configurado. | Implementado | Laboratório | Conferir a configuração de São Paulo no ensaio real. |
| [T25 · #51](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/51) | O passageiro recebe a pergunta da viagem correta às 20h do dia anterior. | Implementado | Laboratório | Executar o agendamento com São Paulo no ambiente autorizado. |
| [T26 · #52](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/52) | Os testes demonstram que o contexto de cada resposta é preservado. | Implementado | Teste automatizado | Manter regressões e repetir os casos essenciais no WhatsApp real. |
| [T27 · #53](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/53) | O grupo identifica as versões e consegue recuperar a configuração anterior. | Implementado | Teste automatizado | Preservar a cópia recuperável nas próximas entregas. |
| [T28 · #54](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/54) | Uma versão mínima identificada funciona no aparelho de teste. | Implementado | Físico parado | Usar a versão integrada nos próximos testes físicos. |
| [T29 · #55](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/55) | Uma versão mínima instalada tem assinatura e renovação documentadas. | Implementado | Físico parado | Renovar a instalação e retomar os testes do coletor quando autorizado. |
| [T36 · #62](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/62) | A mensagem é registrada e processada uma única vez para aquela identidade. | Implementado | Laboratório | Validar os eventos do número dedicado. |
| [T37 · #63](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/63) | Respostas elegíveis ficam registradas e são encaminhadas ao serviço de WhatsApp. | Implementado | Laboratório | Comprovar a entrega e seu retorno pelo WhatsApp real. |
| [T38 · #64](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/64) | A IA sugere intenção, mas validação e permissão continuam controlando a ação. | Implementado | Laboratório | Revalidar as mensagens do uso real mantendo a autorização na API. |
| [T39 · #65](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/65) | As oito automações do n8n podem ser importadas e permanecem desativadas. | Implementado | Laboratório | Ativar somente no ambiente autorizado após revisar a operação. |
| [T40 · #66](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/66) | O motorista configura sua rotina pela conversa e confirma as mudanças. | Implementado | Laboratório | Validar a configuração da rotina com participantes reais autorizados. |
| [T41 · #67](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/67) | A tela apresenta código, vínculo, falha e recuperação sem mensagens contraditórias. | Implementado | Físico parado | Conferir a apresentação no iPhone e no percurso. |
| [T42 · #68](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/68) | A confirmação associa a conta à chave do pedido iniciado no aplicativo. | Implementado | Físico parado | Repetir com a conta WhatsApp dedicada e depois no iPhone. |
| [T55 · #81](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/81) | A automação opera com permissão própria e não repete efeitos ao reiniciar. | Implementado | Laboratório | Conferir o agendamento no ambiente definitivo de teste. |
| [T56 · #82](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/82) | O modo de execução escolhido recupera os componentes necessários sem repetir efeitos. | Parcial | Laboratório | Validar o modo operacional, a fila e as agendas do ambiente escolhido. |
| [T57 · #83](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/83) | O cadastro usa dados essenciais e aplica os prazos definidos para seu ciclo de vida. | Parcial | Laboratório | Definir e testar retenção e exclusão de todas as famílias necessárias. |
| [T58 · #84](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/84) | O conjunto coberto é restaurado em ambiente separado e respeita as exclusões previstas. | Parcial | Laboratório | Executar restauração isolada e reaplicação de exclusões do conjunto completo. |
| [T59 · #85](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/85) | O operador identifica falhas e pausa novos envios com segurança. | Parcial | Pendente | Consolidar alertas, limites e parada segura no ensaio de operação. |
| [T66 · #92](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/92) | A agenda real e as automações do ambiente de teste resistem à falha e ao reinício. | Parcial | Laboratório | Executar agenda real, reinício e recuperação no modo operacional escolhido. |
| [T75 · #101](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/101) | A lista apresenta a ocorrência correta e a situação de cada passageiro autorizado. | Implementado | Laboratório | Verificar a mesma consulta pelo número dedicado. |
| [T78 · #102](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/102) | Os testes verificam as operações previstas, suas permissões e as atualizações do banco. | Implementado | Teste automatizado | Preservar os testes ao concluir os próximos componentes. |

### Onda 2

| ID / issue | Resultado da tarefa | Implementação | Validação | Próxima ação |
|---|---|---|---|---|
| [T7 · #31](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/31) | A API recebe somente as posições válidas da sessão e do aparelho autorizados. | Implementado | Físico parado | Validar continuidade durante os percursos nos dois aparelhos. |
| [T9a · #33](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/33) | Somente o motorista e os passageiros autorizados consultam a posição daquela viagem. | Implementado | Laboratório | Validar consulta e recebimento do pin no WhatsApp de teste, após publicar a homologação. |
| [T9b · #34](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/34) | A resposta informa a última posição válida e quando ela foi obtida. | Implementado | Laboratório | Validar consulta e recebimento do pin no WhatsApp de teste, após publicar a homologação. |
| [T10 · #35](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/35) | O armazenamento fornece a última posição válida e o horário para a consulta autorizada. | Implementado | Laboratório | Validar consulta e recebimento do pin no WhatsApp de teste, após publicar a homologação. |
| [T11 · #36](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/36) | Uma aproximação válida gera um aviso para o passageiro correto. | Implementado | Teste automatizado | Validar o aviso com passageiros consentidos e rotina revisada em percurso. |
| [T30 · #56](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/56) | O Android coleta posições em serviço nativo e mostra seu estado. | Implementado | Laboratório | Concluir os ensaios de percurso e energia no Android. |
| [T31 · #57](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/57) | O coletor nativo mantém o estado de localização independentemente da tela. | Implementado | Laboratório | Executar o coletor no iPhone físico após a retomada. |
| [T32 · #58](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/58) | As posições fictícias chegam a um receptor separado e autenticado. | Implementado | Laboratório | Manter separado do receptor integrado e dos dados reais. |
| [T33 · #59](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/59) | O percurso Android produz evidência de continuidade e recuperação da rede. | Parcial | Pendente | Realizar o percurso Android e registrar atrasos e recuperação da rede. |
| [T34 · #60](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/60) | O percurso no iPhone demonstra continuidade ou expõe precisamente suas limitações. | Planejado | Pendente | Retomar o aparelho e executar o percurso completo. |
| [T35 · #61](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/61) | Cada aparelho tem resultado de consumo e recuperação no cenário combinado. | Parcial | Pendente | Medir bateria e interrupções em Android e iPhone no cenário previsto. |
| [T43 · #69](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/69) | A viagem inicia com uma lista identificada de passageiros e pontos. | Implementado | Laboratório | Conferir o conjunto de passageiros no percurso real. |
| [T44 · #70](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/70) | O aplicativo só mostra coleta ativa após uma posição válida aceita pela API. | Implementado | Físico parado | Repetir no iPhone e durante o percurso. |
| [T45 · #71](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/71) | O aparelho para a coleta e confirma o mesmo pedido quando a comunicação volta. | Implementado | Físico parado | Exercitar a mesma parada no iPhone físico e no percurso. |
| [T46 · #72](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/72) | Cada tentativa tem controle exclusivo e repete apenas quando houver prova de que o envio não ocorreu. | Implementado | Laboratório | Comprovar as mesmas regras com o número dedicado. |
| [T47 · #73](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/73) | O estado de entrega reflete a confirmação disponível e mantém a incerteza quando necessário. | Implementado | Laboratório | Verificar envio e confirmação reais com destinatário autorizado. |
| [T48 · #74](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/74) | A fila descarta mensagens vencidas ou sem autorização de recebimento. | Implementado | Laboratório | Repetir expiração e recusa durante o teste real de avisos. |
| [T49 · #75](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/75) | A regra usa posição atual, ponto e sentido e recusa situações ambíguas. | Implementado | Teste automatizado | Validar o aviso com passageiros consentidos e rotina revisada em percurso. |
| [T50 · #76](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/76) | Somente os próximos pontos e passageiros elegíveis podem gerar aviso. | Implementado | Teste automatizado | Validar o aviso com passageiros consentidos e rotina revisada em percurso. |
| [T51 · #77](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/77) | Os testes demonstram os limites do produtor de aproximação. | Implementado | Teste automatizado | Validar o aviso com passageiros consentidos e rotina revisada em percurso. |
| [T52 · #78](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/78) | O término real da viagem encerra a operação, com alternativa manual identificada. | Planejado | Pendente | Implementar e testar o reconhecimento de fim da viagem. |
| [T53 · #79](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/79) | O aplicativo informa parada confirmada ou pendência até a confirmação ou expiração prevista. | Parcial | Físico parado | Integrar ao encerramento automático e repetir nos dois aparelhos. |
| [T54 · #80](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/80) | A conversa oferece ajuda, pausa adequada e retomada controlada. | Parcial | Laboratório | Concluir retirada de acesso e recuperação do ciclo de viagem. |
| [T60 · #86](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/86) | A versão de teste tem seus componentes e origem identificados. | Parcial | Pendente | Fechar dependências e identificar todos os arquivos da versão integrada. |
| [T61 · #87](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/87) | O pacote Android de teste tem assinatura e integridade conferíveis. | Implementado | Físico parado | Gerar nova versão quando os módulos pendentes forem integrados. |
| [T62 · #88](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/88) | O iPhone recebe a versão identificada e há instruções de instalação e renovação. | Parcial | Físico parado | Instalar e validar a versão integrada no iPhone após retomar os testes. |
| [T63 · #89](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/89) | Existe um plano concreto da instalação da versão de teste e de retorno à anterior. | Parcial | Pendente | Consolidar a implantação da versão completa com verificações e retorno. |
| [T64 · #90](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/90) | O ambiente autorizado executa os componentes corretos e passa nas verificações combinadas. | Parcial | Laboratório | Publicar a revisão aprovada no endereço reservado, conferir certificado, versão e executar uma operação autenticada. |
| [T65 · #91](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/91) | A conta dedicada envia mensagem e tem o retorno de entrega conferido. | Planejado | Pendente | Executar a primeira prova delimitada com Evolution exclusiva e o WhatsApp Business do Redmi; conferir envio e callback. |
| [T67 · #93](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/93) | O Android de referência executa a jornada integrada com WhatsApp real e resultado registrado. | Parcial | Físico parado | Executar o ciclo completo no Android com o canal real e percurso. |
| [T68 · #94](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/94) | O iPhone executa a jornada integrada e tem resultado independente. | Planejado | Pendente | Retomar o aparelho depois de fechar as dependências. |
| [T69 · #95](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/95) | O ensaio mostra se a antecedência é útil e se o destinatário está correto. | Planejado | Pendente | Medir antecedência útil e destinatário correto após implementar os avisos. |
| [T70 · #96](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/96) | A entrega reúne instruções e resultados com seus limites reais. | Parcial | Pendente | Consolidar o guia e os resultados após a validação completa. |
| [T71 · #97](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/97) | A equipe repete instalação e renovação a partir do pacote identificado. | Planejado | Pendente | Reproduzir instalação e renovação do pacote final nos dois aparelhos. |
| [T72 · #98](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/98) | Se a inclusão for aprovada, cancelamento e correção têm um resultado claro para a ocorrência certa. | Proposta de escopo | Laboratório | Confirmar o enquadramento da funcionalidade com a equipe. |
| [T73 · #99](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/99) | Se a proposta for aprovada, equipe e implementação usam a mesma regra de alteração e histórico. | Proposta de escopo | Teste automatizado | Alinhar o significado de horário limite antes de declarar a tarefa pronta. |
| [T74 · #100](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/100) | Se aprovada, a mudança gera aviso adequado ao motorista correto. | Proposta de escopo | Pendente | Aprovar o ajuste e implementar ou localizar o aviso ao motorista. |

## Manutenção

Os dados ficam em `docs/backlog/tarefas.json`, `acompanhamento.json` e `criterios.json`.
Preserve os IDs. Atualize os dados e a issue no mesmo trabalho; gere este documento com `python3 scripts/backlog.py`.
O programa controla o objetivo ativo e os gates. Não marque todas as tarefas como concluídas por fechar um card técnico agregado.
BACKLOG.xlsx conserva duas abas e oito colunas; os números de issue devem ser reais. Para atualizar a planilha, siga o guia de contribuição.
