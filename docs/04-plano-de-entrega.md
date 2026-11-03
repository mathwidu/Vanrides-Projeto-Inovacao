# Plano de entrega do MVP

O plano está organizado pelos resultados que poderão ser demonstrados.
A planilha BACKLOG detalha as tarefas; o Kanban acompanha sua execução.
Os responsáveis e prazos serão confirmados pelo grupo.

A planilha segue o exemplo do professor, com as abas BACKLOG e INSTRUÇÕES.
O [acompanhamento de apoio](07-como-ler-o-backlog.md) reúne a situação da
implementação, a validação e os próximos passos de cada tarefa.

| Etapa | Entrega demonstrável | Situação atual | Critério para concluir |
|---|---|---|---|
| 1. Cadastro e presença | Motorista e passageiros organizam a lista pelo WhatsApp | Funções testadas em ambiente de desenvolvimento | Completar os cadastros, convites e respostas no canal real |
| 2. Atendimento no WhatsApp | Bot recebe, responde e confirma o resultado dos envios | Integração preparada; conta dedicada ativa, pareamento pendente | Trocar mensagens reais sem perda ou duplicação indevida |
| 3. Localização do motorista | Início, consulta e parada no Android e iPhone | Coleta implementada; consulta pelo passageiro e testes completos pendentes | Manter posições recentes em percurso, recuperar a conexão e permitir consulta autorizada |
| 4. Avisos de aproximação | Passageiro recebe o aviso antes do embarque | Implementação pendente | Usar posição válida, destinatário correto e evitar avisos repetidos |
| 5. Encerramento do trajeto | Compartilhamento encerrado ao finalizar | Implementação pendente | Confirmar parada automática e alternativa manual |
| 6. Ambiente para testes | Instalação e operação reproduzíveis | Preparação parcial | Documentar configuração, recuperação, monitoramento e executar o fluxo completo |
| 7. Piloto e entrega | Grupo demonstra o MVP e reúne feedback | Depende das etapas anteriores | Validar com participantes, registrar resultados e entregar os guias |

## Ordem e dependências

A conta dedicada do bot está ativa no WhatsApp Business. Falta preparar o
ambiente da versão centralizada, parear a conta e comprovar a entrega real.
A implementação dos avisos e a preparação dos testes avançam nas issues existentes. A disponibilidade dos aparelhos condiciona os ensaios
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
