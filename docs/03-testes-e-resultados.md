# Testes e resultados

Situação atualizada em 04/11/2026. Resultados são vinculados à versão avaliada;
`main`, branch de integração e binário da VPS podem ter revisões diferentes.

| Área | O que foi verificado | O que falta |
|---|---|---|
| Cadastros | Motorista, convite e ponto em laboratório; perfil básico do passageiro com nome e aceite no WhatsApp real | Validar motorista, vínculo, dias e aprovação do ponto no canal real |
| Presença | Regras de data/sentido, correção e silêncio em laboratório; consulta sem rotina libera a conversa no WhatsApp real | Validar presença efetiva de ida e volta com motorista e rotina |
| Atendimento | Ajuda e retomada de passageiro sem vínculo validadas no WhatsApp real; IA desligada na homologação | Avaliar a jornada completa; melhorias do PR #105 ainda não publicadas na API |
| Envio de mensagens | Regras de repetição em laboratório; 15 respostas DELIVERED acumuladas no canal real, quatro no reteste da API acc572f | Ampliar cenários reais de falha e recuperação |
| Localização Android | Vínculo do aparelho, primeira posição e parada com recuperação de conexão | Percurso, tela bloqueada e bateria sem USB |
| Localização iPhone | Instalação e ensaios iniciais de localização | Retomar os testes físicos e validar o fluxo completo |
| Consulta de localização | Motorista e passageiro elegível recebem pin recente no canal simulado. A API revalida respostas atrasadas | Conferir o pin no WhatsApp real |
| Aproximação | Política, elegibilidade, pontos compartilhados e deduplicação passaram nos testes de PostgreSQL | Medir utilidade em percurso antes de ativar avisos |
| Encerramento | Critérios definidos para chegada, parada do GPS e contingência | Implementar a conclusão automática e comprovar a parada do coletor |

A revisão dd12cf0 passou em 1.683 testes do backend e seis jornadas com provedor simulado. O [registro de localização e aproximação](../program/evidence/VR-WA-130-APPROACH.md) identifica a versão e os limites do ensaio.

Os testes automatizados e os ensaios em aparelho estão registrados por versão
no desenvolvimento. Uma simulação do canal permite avaliar regras, mas não
confirma que uma mensagem chegou ao WhatsApp. Os ensaios com o aparelho parado
também não substituem os testes em percurso.

O detalhamento por tarefa está no [acompanhamento de apoio](07-como-ler-o-backlog.md). Uma tarefa
marcada como implementada pode ainda depender da validação no canal real;
uma parte implementada não conclui todo o objetivo da funcionalidade. O link
inicia a coleta no aplicativo do motorista; navegação externa está fora do MVP.

A [evidência do passageiro](runbooks/whatsapp-mvp/cadastro-passageiro-sem-motorista.md)
registra a publicação `acc572f`, os 26 testes direcionados anteriores a ela e a
repetição no WhatsApp. A [homologação da equipe](desenvolvimento/homologacao-da-equipe.md)
informa os acessos e as restrições atuais. Não aplicar resultados de uma versão
a outra sem nova validação.

## Próximos testes completos

1. Cadastrar motorista e passageiro pelo WhatsApp.
2. Confirmar e corrigir a presença para uma ida e uma volta.
3. Iniciar o trajeto e confirmar o compartilhamento da localização.
4. Após calibração e ativação autorizada, receber o aviso de aproximação no passageiro correto.
5. Interromper e restabelecer a internet, verificando recuperação e duplicidade.
6. Validar parada técnica; depois da implementação pendente, confirmar encerramento automático do trajeto.
7. Repetir no Android e, quando o proprietário retomar os testes físicos, no iPhone, com tela bloqueada e medição de bateria.

## Como registrar novos resultados

Cada teste deve indicar data, versão, ambiente, cenário, resultado esperado,
resultado observado e eventuais pendências. Capturas e gravações para a
apresentação devem usar contas e dados de teste. Resultados com usuários serão
registrados separadamente, ligados às hipóteses do Canvas.
