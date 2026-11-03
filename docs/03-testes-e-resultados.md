# Testes e resultados

Situação atualizada em 03/11/2026 a partir dos registros de desenvolvimento.

| Área | O que foi verificado | O que falta |
|---|---|---|
| Cadastros | Motorista, passageiro, convite, aprovação e ponto de embarque | Repetir a experiência no WhatsApp real |
| Presença | Data e sentido corretos, correção, lembretes e ausência de resposta | Testar a conversa completa com o número dedicado |
| Atendimento | Interpretação de pedidos e tratamento de ambiguidades em uma amostra de mensagens | Avaliar conversas reais e corrigir novas dificuldades |
| Envio de mensagens | Repetições, expiração e recuperação com um receptor de teste | Confirmar envio e recebimento pelo WhatsApp |
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
