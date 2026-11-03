# Testes e resultados

Situação conferida em 02/11/2026 a partir dos registros de desenvolvimento.

| Área | O que foi verificado | O que falta |
|---|---|---|
| Cadastros | Motorista, passageiro, convite, aprovação e ponto de embarque | Repetir a experiência no WhatsApp real |
| Presença | Data e sentido corretos, correção, lembretes e ausência de resposta | Testar a conversa completa com o número dedicado |
| Atendimento | Interpretação de pedidos e tratamento de ambiguidades em uma amostra de mensagens | Avaliar conversas reais e corrigir novas dificuldades |
| Envio de mensagens | Repetições, expiração e recuperação com um receptor de teste | Confirmar envio e recebimento pelo WhatsApp |
| Localização Android | Vínculo do aparelho, primeira posição e parada com recuperação de conexão | Percurso, tela bloqueada e bateria sem USB |
| Localização iPhone | Instalação e ensaios iniciais de localização | Retomar os testes físicos e validar o fluxo completo |
| Consulta de localização | Recepção e armazenamento de posições na API | Concluir a consulta autorizada pelo passageiro e a indicação de posição antiga |
| Aproximação e encerramento | Planejamento e critérios definidos | Implementar e validar as duas funcionalidades |

Os testes automatizados e os ensaios em aparelho estão registrados por versão
no desenvolvimento. Uma simulação do canal permite avaliar regras, mas não
confirma que uma mensagem chegou ao WhatsApp. Os ensaios com o aparelho parado
também não substituem os testes em percurso.

O detalhamento por tarefa está no [acompanhamento de apoio](07-como-ler-o-backlog.md). Uma tarefa
marcada como implementada pode ainda depender da validação no canal real;
uma parte implementada não conclui todo o objetivo da funcionalidade. O link
inicia a coleta no aplicativo do motorista; navegação externa está fora do MVP.

## Próximos testes completos

1. Cadastrar motorista e passageiro pelo WhatsApp.
2. Confirmar e corrigir a presença para uma ida e uma volta.
3. Iniciar o trajeto e confirmar o compartilhamento da localização.
4. Receber o aviso de aproximação no passageiro correto.
5. Interromper e restabelecer a internet, verificando recuperação e duplicidade.
6. Encerrar o trajeto e confirmar que a localização deixou de ser compartilhada.
7. Repetir no Android e no iPhone, com tela bloqueada e medição de bateria.

## Como registrar novos resultados

Cada teste deve indicar data, versão, ambiente, cenário, resultado esperado,
resultado observado e eventuais pendências. Capturas e gravações para a
apresentação devem usar contas e dados de teste. Resultados com usuários serão
registrados separadamente, ligados às hipóteses do Canvas.
