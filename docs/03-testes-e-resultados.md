# Testes e resultados

Situação atualizada em 15/11/2026. Cada resultado vale para a revisão e o
ambiente indicados na evidência. Para o cadastro, a revisão publicada ao
final da rodada foi `d389c31`. [Publicação](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/43775243359)

| Área | O que foi verificado | O que falta |
|---|---|---|
| Cadastros | Emissão de link, dias sugeridos, vínculo e aprovação e correção do ponto no WhatsApp real, com perfis já cadastrados. Nome e aceite no fluxo simplificado em PostgreSQL. | Repetir o cadastro simplificado com um perfil novo no canal real. |
| Presença | Regras de data/sentido, correção e silêncio em laboratório; consulta sem rotina libera a conversa no WhatsApp real | Validar presença efetiva de ida e volta com motorista e rotina |
| Atendimento | Ajuda, retomada e orientação para corrigir um ponto inválido validadas no WhatsApp real. | Exercitar a conversa durante presença e saída com os dois papéis. |
| Envio de mensagens | Regras de repetição em laboratório. Entrega real do pedido, da aprovação e da atualização do ponto. | Ampliar cenários reais de falha e recuperação. |
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

O [guia do cadastro](desenvolvimento/cadastro-guiado-whatsapp.md#o-que-já-foi-validado)
reúne as provas dos PRs #118 e #119. A
[evidência histórica do passageiro](runbooks/whatsapp-mvp/cadastro-passageiro-sem-motorista.md)
registra a rodada anterior com a revisão `acc572f`. Consulte o
[guia da homologação](desenvolvimento/homologacao-da-equipe.md) para os acessos.

## Próximos testes completos

1. Preparar um motorista e um passageiro de teste com vínculo e ponto aprovados pelo WhatsApp.
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
