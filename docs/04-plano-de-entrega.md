# Plano de entrega do MVP

O plano está organizado pelos resultados que poderão ser demonstrados.
A planilha BACKLOG detalha as tarefas; o Kanban acompanha sua execução.
Os responsáveis e prazos serão confirmados pelo grupo.

A planilha segue o exemplo do professor, com as abas BACKLOG e INSTRUÇÕES.
O [acompanhamento de apoio](07-como-ler-o-backlog.md) reúne a situação da
implementação, a validação e os próximos passos de cada tarefa.

| Etapa | Entrega demonstrável | Situação atual | Critério para concluir |
|---|---|---|---|
| 1. Cadastro e presença | Motorista e passageiros organizam a lista pelo WhatsApp | Funções em laboratório; perfil básico do passageiro já validado no WhatsApp real | Completar os cadastros, convites e respostas no canal real |
| 2. Atendimento no WhatsApp | Bot recebe, responde e confirma o resultado dos envios | Canal publicado e conectado; respostas e recibos reais comprovados | Trocar mensagens reais sem perda ou duplicação indevida |
| 3. Localização do motorista | Início, consulta e parada no Android e iPhone | Coleta implementada e consulta autorizada testada com canal simulado | Manter posições recentes em percurso, recuperar a conexão e permitir consulta autorizada |
| 4. Avisos de aproximação | Passageiro recebe o aviso antes do embarque | Regra implementada e testada em banco; utilidade em percurso pendente | Usar posição válida, destinatário correto e evitar avisos repetidos |
| 5. Encerramento do trajeto | Compartilhamento encerrado ao finalizar | Implementação pendente | Confirmar parada automática e alternativa manual |
| 6. Ambiente para testes | Instalação e operação reproduzíveis | API, banco, APK e canal publicados; falta a jornada completa da equipe | Documentar configuração, recuperação, monitoramento e executar o fluxo completo |
| 7. Piloto e entrega | Grupo demonstra o MVP e reúne feedback | Depende das etapas anteriores | Validar com participantes, registrar resultados e entregar os guias |

## Ordem e dependências

O bot está conectado e o perfil básico do passageiro foi validado. O próximo
passo é admitir um motorista da equipe, configurar rotina/convite e realizar
a jornada de vínculo, presença e GPS. A URL atende todo o grupo; cada contato
precisa de liberação individual antes de usar o bot.
A regra de aproximação está implementada, mas permanece desligada na homologação até os ensaios em percurso. As issues #89–91 acompanham o [ambiente do grupo](desenvolvimento/homologacao-da-equipe.md). A disponibilidade dos aparelhos condiciona os ensaios
físicos; o iPhone permanece pausado até a retomada pelo proprietário.

A sequência de implementação detalhada deve respeitar as dependências entre
as tarefas. Este resumo não muda o estado de atividades que ainda dependem de
validação ou de recursos externos.

## Nomes no plano e no Kanban

Usar títulos que descrevam uma ação e seu resultado. Exemplos:

- `[T02] Cadastrar motorista pelo WhatsApp`.
- `[T23] Orientar o cadastro e a confirmação do ponto de embarque`.
- `[T49] Identificar a aproximação da van ao ponto de embarque`.
- `[T68] Testar a experiência completa no iPhone`.

Os IDs Txx e Fxx são preservados para acompanhar a história da disciplina.
Códigos internos de desenvolvimento não precisam aparecer nos títulos públicos.

## Colunas do Kanban

Backlog, A Fazer, Em Progresso, Em Revisão e Concluído. Cada tarefa deve conter
objetivo, responsável, critério de conclusão e evidência do resultado. Um
impedimento deve explicar o recurso ou decisão necessários para avançar.

## Próxima revisão do grupo

Confirmar responsáveis e estimativas; validar os ajustes de escopo; localizar o
board já existente; atualizar as issues da onda ativa e registrar a data do
próximo resultado demonstrável. As tarefas de ondas futuras permanecem no
BACKLOG até sua ativação, preservando o histórico das issues já criadas.
