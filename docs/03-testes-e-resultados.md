# Testes e resultados

Situação atualizada em 16/11/2026. A
[publicação do PR #127](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/43896934669)
passou em 81 s e publicou a API `2a211d1`, schema V44, com imagem, JAR e HTTPS
conferidos. A
[publicação anterior do PR #126](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/43891354871)
passou em 85 s. O worker saudável usa 56 fontes de `efd7d8b`; não houve nova migração.
A combinação está identificada no [registro da entrega](../program/evidence/mvp-entrega-20261116.md).

As flags `WA_MVP_REHEARSAL_ROUTINES`, `WA_APPROACH_ENABLED` e
`WA_AUTO_FINISH_ENABLED` estão ON para rotinas elegíveis da coorte compartilhada,
sem whitelist por rotina. Limpeza real de privacidade e IA continuam OFF.
A ativação não substitui a prova de utilidade e encerramento automático em campo.

| Área | O que foi verificado | O que falta |
|---|---|---|
| Cadastros | Emissão de link, dias sugeridos, vínculo e aprovação e correção do ponto no WhatsApp real, com perfis já cadastrados. Nome e aceite no fluxo simplificado em PostgreSQL. | Repetir o cadastro simplificado com um perfil novo no canal real. |
| Presença | Presença de amanhã/ida, alteração para ausência e lista do motorista; nova confirmação de hoje/volta no WhatsApp real. | Conferir os limites após o início na rodada final. |
| Atendimento | Ajuda e correção do ponto validadas no WhatsApp real; retomada pela equipe e finalização durante suporte passaram em PostgreSQL. Preferências de chegada ON/OFF/ON receberam ACK real após a correção de elegibilidade SET_PREFERENCE. | Exercitar pausa/retomada e cenários restantes da conversa com os dois papéis. |
| Envio de mensagens | Regras de repetição em laboratório. Entrega real do pedido, da aprovação e da atualização do ponto. | Ampliar cenários reais de falha e recuperação. |
| Localização Android | APK normal +15 publicado: primeiro GPS em 2,738 s, 81 aceitas/uma RUN_TERMINAL tardia; pin, FINALIZAR/CONFIRMAR, COMPLETED e STOP/ACK reais, com bot e UI confirmando o fim. Hash HTTPS igual ao instalado, assinatura/dados preservados e +14 retido para retorno. | Percurso, tela bloqueada, rede e bateria sem USB pendentes. |
| Localização iPhone | App normal +15 instalado/aberto: recibo terminal recuperado com UI parada, sem apagar journal. Geração 3: primeiro GPS em 5,651 s, 57 aceitas/uma STALE de cache antigo; STOP pelo app com ACK e consulta indisponível. | Percurso, tela bloqueada, rede e bateria. Perfil vence em 23/11 às 08:27 (Brasília). |
| Consulta de localização | Passageiro recebeu o pin real do iPhone e do Redmi; após STOP recebeu localização indisponível. | Conferir continuidade e frescor em percurso e sem rede. |
| Aproximação | Política, elegibilidade, pontos compartilhados e deduplicação passaram em PostgreSQL; flag ON para elegíveis da coorte compartilhada. | Medir destinatário, calibração e utilidade em percurso; ON não é aceite do aviso. |
| Encerramento | FINALIZAR real recebeu ACK dos coletores iPhone +14 e Android normal +15; iOS +15 também teve STOP/ACK pelo app. Recibo após expiração e parada pré-GPS passaram em testes dirigidos. Fim automático ON na coorte compartilhada. | Comprovar encerramento automático e ACK em campo. |
| Recuperação do conjunto | Ensaio 006 isolado na VPS com API, PostgreSQL, worker, n8n em fila, Redis AOF e canal simulado: agendas, reinícios, dois dumps restaurados em outro PG, exclusões reaplicadas e zero efeitos duplicados. Os três critérios de T58 possuem prova. | Revisão dos critérios e pendência operacional de rotação física dos backups. O ensaio não prova qualquer envio interrompido com resultado incerto. |
| Retenção e exclusões | V44 publicada: retenções de 7/30/90 dias, journal atual fora do dump antigo, reaplicação e descarte sem ressuscitar Alfa, preservando Beta e controles do fluxo. Probe HTTP publicado: cinco famílias DISABLED, contadores zero e 401 sem autenticação; no ensaio, banco também permaneceu inalterado. | Revisar a prévia antes de qualquer ativação com pessoas reais. Limpeza real OFF; rotação física de backups ainda não implementada. |
| Migração e retorno | Cópia real do banco ativo, 107 relações e cerca de 2,77 MB, restaurada em destino isolado. API anterior/V43, candidata/V44 e anterior/V44 ficaram saudáveis, com conteúdo restaurado comparado. Publicação V44 concluída e saúde conferida. | Preservar o retorno e revisar a evidência. Nenhum serviço ativo foi alterado pelo ensaio de migração. |
| Operação e pausa | CLI/guia, 11 testes Python e cenário PostgreSQL aprovados. Diagnóstico sem alertas e plano de pausa conferido sem revogar credenciais reais. STOP/ACK físico comprovado separadamente nos apps. | Revisão humana e cenários de pausa/retomada operacional. A prova do sensor não equivale à execução de uma pausa de serviço. |

Historicamente, a revisão `dd12cf0` passou em 1.683 testes do backend e seis
jornadas com provedor simulado. O
[registro de localização e aproximação](../program/evidence/VR-WA-130-APPROACH.md)
identifica aquela versão; seus números não representam uma execução da V44.

O inventário registrado na composição de privacidade tinha 105 classes e
819 casos PostgreSQL; não é uma execução integral nem uma contagem auditada
do candidato atual. Essa composição recebeu testes PostgreSQL proporcionais
e 59 testes Node dirigidos. A rodada isolada 006 terminou como `CORE_AND_PRIVACY_VERIFIED`,
repetindo os quatro checks de fila, agendas, reinícios e restauração com o JAR
efetivamente publicado. Foram dois eventos, dois efeitos no canal simulado e
zero duplicados. Journal e ACK originais permaneceram intactos, e os contêineres
próprios foram removidos com volumes diagnósticos preservados. O
`releaseEligible: false` do recibo limita o ensaio e não constitui aceite de
piloto, produção ou WhatsApp real. O inventário dos backups fornece prévia de
expiração em sete dias, sem deleção automática de arquivos ou volumes.

O [guia de operação](desenvolvimento/operacao.md) distingue diagnóstico,
plano e revogação confirmada. O teste PostgreSQL de pausa passou sem falhas,
erros ou skips; seus serviços transacionais e banco são reais, mas o cenário
continua sintético. A regressão executa também a consulta canônica de paradas
pendentes para três estados e sua baixa após ACK. Na VPS, o diagnóstico de
leitura terminou sem alertas, e o plano identificou somente a credencial de
envio, preservando STOP e reconciliação. Nenhuma pausa real foi executada.

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

1. Conferir os artefatos +15 publicados/instalados e o retorno preservado antes da rodada de campo.
2. Reutilizar vínculos/pontos e presenças de ida/volta já conferidos, verificando limites de alteração após início.
3. Confirmar a combinação final de versões e flags no começo da rodada de campo.
4. Com aproximação ON, conferir destinatário correto, calibração e utilidade do aviso.
5. Interromper e restabelecer a internet, verificando recuperação e duplicidade.
6. Validar parada técnica, finalização manual e encerramento automático habilitado, com ACK do coletor.
7. Repetir em Android e iPhone com tela bloqueada, percurso e medição de bateria sem USB.

O iOS +15 já está instalado e tem prova de GPS/STOP. O
[roteiro por plataforma](desenvolvimento/rodada-final.md) permanece como referência;
percurso, bateria, utilidade do aviso e aceite final permanecem com o
mantenedor. As 65 issues MVP continuam abertas até critérios, evidências e
revisão humana correspondentes, sem encerramento apenas por haver código.

## Como registrar novos resultados

Cada teste deve indicar data, versão, ambiente, cenário, resultado esperado,
resultado observado e eventuais pendências. Capturas e gravações para a
apresentação devem usar contas e dados de teste. Resultados com usuários serão
registrados separadamente, ligados às hipóteses do Canvas.
