# Testes e resultados

Situação atualizada em 16/11/2026. Cada resultado vale para a revisão e o
ambiente indicados na evidência. A API publicada está em `880ea56`, schema V43,
com presença, cancelamento e encerramento manual/automático. Aproximação e fim
automático permanecem desligados. A V44 foi avaliada nos ensaios abaixo e ainda
não está publicada nesta atualização. O
[registro da entrega](../program/evidence/mvp-entrega-20261116.md) identifica
as versões e os limites de cada rodada.

| Área | O que foi verificado | O que falta |
|---|---|---|
| Cadastros | Emissão de link, dias sugeridos, vínculo e aprovação e correção do ponto no WhatsApp real, com perfis já cadastrados. Nome e aceite no fluxo simplificado em PostgreSQL. | Repetir o cadastro simplificado com um perfil novo no canal real. |
| Presença | Data/sentido, correção, cancelamento, limite de alteração e avisos passaram em testes dirigidos; consulta sem rotina libera a conversa no WhatsApp real. | Validar ida e volta com motorista e rotina na nova jornada real. |
| Atendimento | Ajuda e correção do ponto validadas no WhatsApp real; retomada pela equipe e finalização durante suporte passaram em PostgreSQL. | Exercitar a conversa durante presença e saída com os dois papéis. |
| Envio de mensagens | Regras de repetição em laboratório. Entrega real do pedido, da aprovação e da atualização do ponto. | Ampliar cenários reais de falha e recuperação. |
| Localização Android | Vínculo, primeira posição e parada com recuperação têm ensaios anteriores. APK 0.4.0+14 publicado e instalado por atualização no Redmi. | Smoke de GPS do novo build, percurso, tela bloqueada e bateria sem USB. |
| Localização iPhone | Build completo 0.4.0+14 sem assinatura aprovado em 45,36 s; 480 fontes e dois lockfiles preservados. | Desbloquear a sessão do Mac e disponibilizar conta Apple/Personal Team no Xcode, assinar, instalar e validar GPS. O build sem assinatura não é instalável. |
| Consulta de localização | Motorista e passageiro elegível recebem pin recente no canal simulado. A API revalida respostas atrasadas | Conferir o pin no WhatsApp real |
| Aproximação | Política, elegibilidade, pontos compartilhados e deduplicação passaram nos testes de PostgreSQL | Medir utilidade em percurso antes de ativar avisos |
| Encerramento | Finalização manual com revisão, chegada automática com progresso/destino/permanência e reconciliação STOP/ACK passaram em testes dirigidos e estão publicadas. | Habilitar o ensaio controlado do automático e comprovar encerramento e parada no aparelho; parâmetros ainda não calibrados em percurso. |
| Recuperação do conjunto | Ensaio real isolado na VPS com API, PostgreSQL, worker, n8n em fila, Redis AOF e canal simulado: quatro agendas, reinícios, dois dumps restaurados em outro PG e zero efeitos duplicados. | Conferir a nova jornada com o WhatsApp real; o ensaio não comprova qualquer envio interrompido com resultado incerto. |
| Retenção e exclusões | V44: retenções de 7/30/90 dias, journal atual fora do dump antigo, reaplicação e descarte sem ressuscitar Alfa, preservando Beta e controles do fluxo. Probe HTTP real: cinco famílias OFF, contadores zero, banco inalterado e 401 sem autenticação. | Publicar a versão avaliada e revisar a prévia antes de qualquer ativação com pessoas reais. Limpeza real OFF; rotação física de backups ainda não implementada. |
| Migração e retorno | Cópia real do banco ativo, 107 relações e cerca de 2,77 MB, restaurada em destino isolado. API anterior/V43, candidata/V44 e anterior/V44 ficaram saudáveis, com conteúdo restaurado comparado. | Publicação V44 e conferência posterior da versão. Nenhum serviço ativo foi alterado pelo ensaio. |

Historicamente, a revisão `dd12cf0` passou em 1.683 testes do backend e seis
jornadas com provedor simulado. O
[registro de localização e aproximação](../program/evidence/VR-WA-130-APPROACH.md)
identifica aquela versão; seus números não representam uma execução da V44.

A matriz atual inventaria 104 classes e 818 casos PostgreSQL. Isso é inventário,
não execução integral da suíte. As mudanças desta entrega receberam testes
PostgreSQL proporcionais e 59 testes Node dirigidos na composição final da
privacidade. O ensaio isolado terminou como `CORE_AND_PRIVACY_VERIFIED`, com
dois eventos, dois efeitos no canal simulado e zero duplicados. O
`releaseEligible: false` do recibo limita o ensaio e não constitui aceite de
piloto, produção ou WhatsApp real. O inventário dos backups fornece prévia de
expiração em sete dias, sem deleção automática de arquivos ou volumes.

A CI de PR e `main` executa construção, sem testes automáticos. Na `main`, a
imagem e o deploy da API/interface são publicados pelo fluxo próprio. Os
testes dirigidos e as revisões são registrados por alteração, fora dessa CI.

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
6. Validar parada técnica, finalização manual e, com o ensaio habilitado, encerramento automático do trajeto e ACK do coletor.
7. Repetir no Android e, após assinatura e instalação, no iPhone, com tela bloqueada e medição de bateria.

O preparo iOS foi retomado. O
[roteiro por plataforma](desenvolvimento/rodada-final.md) está pronto;
percurso, bateria, utilidade do aviso e aceite final permanecem com o
mantenedor. As 65 issues MVP continuam abertas até critérios, evidências e
revisão humana correspondentes, sem encerramento apenas por haver código.

## Como registrar novos resultados

Cada teste deve indicar data, versão, ambiente, cenário, resultado esperado,
resultado observado e eventuais pendências. Capturas e gravações para a
apresentação devem usar contas e dados de teste. Resultados com usuários serão
registrados separadamente, ligados às hipóteses do Canvas.
