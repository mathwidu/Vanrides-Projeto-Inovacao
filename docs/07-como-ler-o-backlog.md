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
| Encerramento | Validar fim automático e parada na rodada de campo | Regra integrada; calibração e cenários de rede pendentes |
| Android e iPhone | Percurso completo, rede, bateria e destinatário correto | Apps +15 com prova estacionada; [registro atual](../program/evidence/mvp-entrega-20261116.md) |
| Contribuição do grupo | Reproduzir uma frente e enviar o primeiro PR | Guia de desenvolvimento e revisão de um colega |

## Como interpretar a situação

| Implementação | Quantidade | O que falta para encerrar |
|---|---:|---|
| Implementado | 56 | Conferir escopo e provas específicas; não significa aceite real |
| Parcial | 7 | Completar a lacuna descrita e testar |
| Planejado | 2 | Definir execução, implementar e validar |
| Proposta de escopo | 0 | Decidir com o grupo antes de implementar |

Essas contagens não medem percentual concluído. Teste automatizado, laboratório, aparelho parado e percurso são provas diferentes.
Uma tarefa só termina quando seus critérios são atendidos, o PR foi revisado e a prova registra a versão avaliada.

## Lista por onda

Todas as tarefas atuais têm uma issue. Já há implementação de cadastro e presença na Onda 1 e de coleta de localização na Onda 2. As issues das duas ondas registram esse andamento e o trabalho restante; publicar uma tarefa não inicia sua execução.
Referências antigas ficam identificadas como histórico e fora da lista atual. T76/T77 foram retiradas junto da navegação externa.

### Onda 1

| ID / issue | Resultado da tarefa | Implementação | Validação | Próxima ação |
|---|---|---|---|---|
| [T01 · #25](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/25) | A API Java e o banco ficam prontos para receber os cadastros do atendimento. | Implementado | Laboratório | Manter a configuração compatível na versão de teste. |
| [T02 · #26](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/26) | O motorista consegue se cadastrar pelo WhatsApp com seus dados validados. | Implementado | Laboratório | Revisar os critérios de cadastro na issue e reproduzir os casos ainda não cobertos com participantes autorizados. |
| [T03 · #27](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/27) | Cada ação usa a identidade e a permissão corretas. | Implementado | Laboratório | Preservar a autorização ao conectar o canal real. |
| [T04 · #28](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/28) | O passageiro informa os dados necessários e tem seu ponto conferido pelo motorista. | Implementado | Laboratório | Repetir o cadastro simplificado com perfil novo no canal real e revisar os critérios com a equipe. |
| [T05 · #29](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/29) | O convite permite uma solicitação válida e o motorista decide o vínculo. | Implementado | Laboratório | Revisar convite/vínculo e completar o cenário de perfil novo e dois passageiros autorizados. |
| [T6 · #30](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/30) | Cada passageiro confirma ou informa ausência para a viagem indicada. | Implementado | Laboratório | Conferir os limites após início na jornada real e revisar a prova por data e sentido. |
| [T23 · #49](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/49) | A conversa orienta cadastro, envio do ponto e correção de forma compreensível. | Implementado | Laboratório | Revisar a compreensão das mensagens com a equipe no canal real. |
| [T24 · #50](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/50) | As ocorrências seguem os dias e horários da rotina no fuso configurado. | Implementado | Laboratório | Conferir a configuração de São Paulo no ensaio real. |
| [T25 · #51](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/51) | O passageiro recebe a pergunta da viagem correta às 20h do dia anterior. | Implementado | Laboratório | Executar o agendamento com São Paulo no ambiente autorizado. |
| [T26 · #52](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/52) | Os testes demonstram que o contexto de cada resposta é preservado. | Implementado | Teste automatizado | Manter regressões e repetir os casos essenciais no WhatsApp real. |
| [T27 · #53](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/53) | O grupo identifica as versões e consegue recuperar a configuração anterior. | Implementado | Teste automatizado | Preservar a cópia recuperável nas próximas entregas. |
| [T28 · #54](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/54) | Uma versão mínima identificada funciona no aparelho de teste. | Implementado | Físico parado | Usar o APK +15 identificado na rodada de campo e preservar o +14 para retorno. |
| [T29 · #55](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/55) | Uma versão mínima instalada tem assinatura e renovação documentadas. | Implementado | Físico parado | Executar a rodada de campo e renovar a instalação antes do vencimento, conforme o guia. |
| [T36 · #62](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/62) | A mensagem é registrada e processada uma única vez para aquela identidade. | Implementado | Laboratório | Validar os eventos do número dedicado. |
| [T37 · #63](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/63) | Respostas elegíveis ficam registradas e são encaminhadas ao serviço de WhatsApp. | Implementado | Laboratório | Ampliar falhas de comunicação e recuperação real, preservando resultado incerto e evitando duplicação. |
| [T38 · #64](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/64) | A IA sugere intenção, mas validação e permissão continuam controlando a ação. | Implementado | Laboratório | Preservar a IA OFF na rodada atual; qualquer validação futura do modelo deve manter intenções/contexto autorizados e recusa de saída inválida. |
| [T39 · #65](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/65) | As oito automações do n8n podem ser importadas e permanecem desativadas. | Implementado | Laboratório | Revisar os exports e a configuração publicada, preservando o escopo das flags ON e os limites do ensaio. |
| [T40 · #66](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/66) | O motorista configura sua rotina pela conversa e confirma as mudanças. | Implementado | Laboratório | Validar a configuração da rotina com participantes reais autorizados. |
| [T41 · #67](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/67) | A tela apresenta código, vínculo, falha e recuperação sem mensagens contraditórias. | Implementado | Físico parado | Conferir estados sob rede interrompida, tela bloqueada e percurso; preservar a correspondência entre UI e ACK. |
| [T42 · #68](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/68) | A confirmação associa a conta à chave do pedido iniciado no aplicativo. | Implementado | Físico parado | Revisar os critérios de pareamento e repetir apenas os cenários de troca/recusa ainda não cobertos na combinação final. |
| [T55 · #81](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/81) | A automação opera com permissão própria e não repete efeitos ao reiniciar. | Implementado | Laboratório | Conferir o agendamento no ambiente definitivo de teste. |
| [T56 · #82](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/82) | O modo de execução escolhido recupera os componentes necessários sem repetir efeitos. | Implementado | Laboratório | Revisar a evidência 006 e os critérios do conjunto, mantendo separados os cenários reais de resultado incerto. |
| [T57 · #83](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/83) | O cadastro usa dados essenciais e aplica os prazos definidos para seu ciclo de vida. | Parcial | Laboratório | Revisar a prévia e a ativação humana de descarte; implementar a lacuna operacional de rotação física sem apagar backups por esta atualização. |
| [T58 · #84](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/84) | O conjunto coberto é restaurado em ambiente separado e respeita as exclusões previstas. | Implementado | Laboratório | Revisar os três critérios contra a evidência 006. Manter separada a pendência operacional de rotação física; nenhum backup é apagado por este registro. |
| [T59 · #85](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/85) | O operador identifica falhas e pausa novos envios com segurança. | Implementado | Laboratório | Revisar guia/responsável e conferir cenários operacionais ainda necessários, distinguindo plano, revogação e retorno efetivo. |
| [T66 · #92](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/92) | A agenda real e as automações do ambiente de teste resistem à falha e ao reinício. | Implementado | Laboratório | Revisar a prova das agendas/identidade de automação e registrar os efeitos reais específicos ainda não cobertos. |
| [T75 · #101](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/101) | A lista apresenta a ocorrência correta e a situação de cada passageiro autorizado. | Implementado | Laboratório | Revisar a lista por ocorrência e completar os cenários reais após início sem reescrever o snapshot da partida. |
| [T78 · #102](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/102) | Os testes verificam as operações previstas, suas permissões e as atualizações do banco. | Implementado | Teste automatizado | Preservar os testes ao concluir os próximos componentes. |

### Onda 2

| ID / issue | Resultado da tarefa | Implementação | Validação | Próxima ação |
|---|---|---|---|---|
| [T7 · #31](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/31) | A API recebe somente as posições válidas da sessão e do aparelho autorizados. | Implementado | Físico parado | Validar continuidade e atrasos em percurso nas duas plataformas, com a combinação final de versões. |
| [T9a · #33](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/33) | Somente o motorista e os passageiros autorizados consultam a posição daquela viagem. | Implementado | Laboratório | Revisar autorização e critérios restantes; conferir frescor e continuidade em percurso e falha de rede. |
| [T9b · #34](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/34) | A resposta informa a última posição válida e quando ela foi obtida. | Implementado | Laboratório | Revisar autorização e critérios restantes; conferir frescor e continuidade em percurso e falha de rede. |
| [T10 · #35](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/35) | O armazenamento fornece a última posição válida e o horário para a consulta autorizada. | Implementado | Laboratório | Revisar autorização e critérios restantes; conferir frescor e continuidade em percurso e falha de rede. |
| [T11 · #36](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/36) | Uma aproximação válida gera um aviso para o passageiro correto. | Implementado | Teste automatizado | Validar destinatário, calibração e utilidade em percurso com passageiros consentidos; manter o escopo da coorte compartilhada. |
| [T30 · #56](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/56) | O Android coleta posições em serviço nativo e mostra seu estado. | Implementado | Físico parado | Concluir percurso, tela bloqueada, recuperação de rede e energia no Android +15 sem transferir a prova estacionada para esses cenários. |
| [T31 · #57](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/57) | O coletor nativo mantém o estado de localização independentemente da tela. | Implementado | Físico parado | Executar percurso, tela bloqueada, recuperação de rede e energia no iPhone +15; preservar os limites da prova estacionada. |
| [T32 · #58](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/58) | As posições fictícias chegam a um receptor separado e autenticado. | Implementado | Laboratório | Manter separado do receptor integrado e dos dados reais. |
| [T33 · #59](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/59) | O percurso Android produz evidência de continuidade e recuperação da rede. | Parcial | Pendente | Realizar o percurso Android +15 e registrar atrasos, destinatário, rede e consumo sem USB. |
| [T34 · #60](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/60) | O percurso no iPhone demonstra continuidade ou expõe precisamente suas limitações. | Planejado | Pendente | Executar o percurso completo no iPhone +15 quando o proprietário iniciar a rodada de campo. |
| [T35 · #61](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/61) | Cada aparelho tem resultado de consumo e recuperação no cenário combinado. | Parcial | Pendente | Medir bateria e interrupções nos dois aparelhos +15, sem USB e com versões/flags registradas. |
| [T43 · #69](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/69) | A viagem inicia com uma lista identificada de passageiros e pontos. | Implementado | Laboratório | Conferir o conjunto de passageiros no percurso real. |
| [T44 · #70](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/70) | O aplicativo só mostra coleta ativa após uma posição válida aceita pela API. | Implementado | Físico parado | Conferir início/frescor em percurso e falhas de rede, sem confundir heartbeat com nova posição. |
| [T45 · #71](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/71) | O aparelho para a coleta e confirma o mesmo pedido quando a comunicação volta. | Implementado | Teste automatizado | Nos dois apps +15, parar sem rede, reabrir e reconectar; comprovar sensor parado, pendência durável e baixa sem reiniciar coleta. |
| [T46 · #72](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/72) | Cada tentativa tem controle exclusivo e repete apenas quando houver prova de que o envio não ocorreu. | Implementado | Laboratório | Exercitar falha/reconexão real de envio sem duplicar e preservar UNKNOWN quando não houver confirmação. |
| [T47 · #73](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/73) | O estado de entrega reflete a confirmação disponível e mantém a incerteza quando necessário. | Implementado | Laboratório | Revisar a associação dos retornos reais e completar cenários sem callback, entrega incerta e destinatários distintos. |
| [T48 · #74](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/74) | A fila descarta mensagens vencidas ou sem autorização de recebimento. | Implementado | Laboratório | Repetir expiração e recusa durante o teste real de avisos. |
| [T49 · #75](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/75) | A regra usa posição atual, ponto e sentido e recusa situações ambíguas. | Implementado | Teste automatizado | Validar o aviso com passageiros consentidos e rotina revisada em percurso. |
| [T50 · #76](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/76) | Somente os próximos pontos e passageiros elegíveis podem gerar aviso. | Implementado | Teste automatizado | Validar o aviso com passageiros consentidos e rotina revisada em percurso. |
| [T51 · #77](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/77) | Os testes demonstram os limites do produtor de aproximação. | Implementado | Teste automatizado | Validar o aviso com passageiros consentidos e rotina revisada em percurso. |
| [T52 · #78](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/78) | O término real da viagem encerra a operação, com alternativa manual identificada. | Implementado | Laboratório | Calibrar e comprovar fim automático em campo e revisar os critérios; a contingência manual não substitui essa prova. |
| [T53 · #79](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/79) | O aplicativo informa parada confirmada ou pendência até a confirmação ou expiração prevista. | Implementado | Físico parado | Validar o encerramento automático em campo e os cenários atuais de rede/tela bloqueada nas duas plataformas. |
| [T54 · #80](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/80) | A conversa oferece ajuda, pausa adequada e retomada controlada. | Implementado | Laboratório | Conferir no canal real a pausa/retomada e retirada de acesso, sem restaurar permissões nem perder o contexto. |
| [T60 · #86](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/86) | A versão de teste tem seus componentes e origem identificados. | Parcial | Laboratório | Conferir o manifesto conjunto após a revisão final da fonte e guias, preservando versões, hashes, flags e retorno. |
| [T61 · #87](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/87) | O pacote Android de teste tem assinatura e integridade conferíveis. | Implementado | Físico parado | Preservar hash/versão/retorno e usar o APK +15 publicado na rodada de campo. |
| [T62 · #88](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/88) | O iPhone recebe a versão identificada e há instruções de instalação e renovação. | Implementado | Físico parado | Renovar antes do vencimento e conferir a reprodução da instalação pelo procedimento, sem transferir a prova de outro build. |
| [T63 · #89](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/89) | Existe um plano concreto da instalação da versão de teste e de retorno à anterior. | Implementado | Laboratório | Preservar o retorno ensaiado e revisar o plano/recibos da mesma combinação de versões. |
| [T64 · #90](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/90) | O ambiente autorizado executa os componentes corretos e passa nas verificações combinadas. | Implementado | Laboratório | Preservar a combinação publicada e revisar os critérios de funcionamento/retorno no ambiente compartilhado. |
| [T65 · #91](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/91) | A conta dedicada envia mensagem e tem o retorno de entrega conferido. | Implementado | Laboratório | Revisar pareamento/envios/retornos e completar destinatários distintos e ausência de confirmação sem presumir entrega. |
| [T67 · #93](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/93) | O Android de referência executa a jornada integrada com WhatsApp real e resultado registrado. | Parcial | Físico parado | Executar o roteiro de campo Android +15 e registrar etapas/falhas; não declarar jornada completa pelo teste estacionado. |
| [T68 · #94](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/94) | O iPhone executa a jornada integrada e tem resultado independente. | Parcial | Físico parado | Executar o roteiro de campo iPhone +15 e registrar resultados separadamente do Android, sem promover GPS parado a jornada completa. |
| [T69 · #95](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/95) | O ensaio mostra se a antecedência é útil e se o destinatário está correto. | Planejado | Pendente | Medir antecedência útil e destinatário correto no percurso autorizado, com os apps +15 e flags registradas. |
| [T70 · #96](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/96) | A entrega reúne instruções e resultados com seus limites reais. | Implementado | Laboratório | Revisar os guias com o grupo e acrescentar os resultados de campo/bateria/utilidade quando executados. |
| [T71 · #97](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/97) | A equipe repete instalação e renovação a partir do pacote identificado. | Parcial | Físico parado | Reproduzir a instalação/entrada pelos guias e a renovação aplicável, preservando hashes e sem compartilhar credenciais. |
| [T72 · #98](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/98) | Se a inclusão for aprovada, cancelamento e correção têm um resultado claro para a ocorrência certa. | Implementado | Laboratório | Revisar os critérios e completar a alteração após início no canal real; não reabrir a inclusão já integrada como proposta. |
| [T73 · #99](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/99) | Se a proposta for aprovada, equipe e implementação usam a mesma regra de alteração e histórico. | Implementado | Teste automatizado | Revisar a redação de horário limite com a equipe e conferir a regra após início no canal real, preservando os critérios existentes. |
| [T74 · #100](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/100) | Se aprovada, a mudança gera aviso adequado ao motorista correto. | Implementado | Laboratório | Conferir no WhatsApp real o aviso ao motorista após ausência elegível pós-saída, sem duplicação e antes de perder a utilidade. |

## Manutenção

Os dados ficam em `docs/backlog/tarefas.json`, `acompanhamento.json` e `criterios.json`.
Preserve os IDs. Atualize os dados e a issue no mesmo trabalho; gere este documento com `python3 scripts/backlog.py`.
O programa controla o objetivo ativo e os gates. Não marque todas as tarefas como concluídas por fechar um card técnico agregado.
BACKLOG.xlsx conserva duas abas e oito colunas; os números de issue devem ser reais. Para atualizar a planilha, siga o guia de contribuição.
