# VanRides

MVP para organizar transporte recorrente pelo WhatsApp: cadastro de motorista e
passageiro, confirmação de presença e avisos de aproximação da van. O motorista
usa um aplicativo de apoio para enviar a localização; o passageiro permanece no
WhatsApp. Integração com Maps/Waze não faz parte desta entrega.

Projeto da disciplina **Projeto – Inovações Tecnológicas (GER383780A)**,
Universidade Feevale, 2026/2.

## Comece aqui

| Quero… | Acesse |
|---|---|
| Entender o produto | [Produto e experiência](docs/01-produto-e-experiencia.md) |
| Ver os componentes e o fluxo | [Arquitetura](docs/02-arquitetura-e-decisoes.md) |
| Conferir o que foi testado | [Testes e resultados](docs/03-testes-e-resultados.md) |
| Entender uma tarefa e o que falta | [Backlog](docs/07-como-ler-o-backlog.md) e [issues do MVP](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues?q=is%3Aissue+is%3Aopen+label%3Amvp) |
| Preparar o computador e contribuir | [Guia de contribuição](CONTRIBUTING.md) |
| Testar uma versão com o grupo | [Homologação da equipe](docs/desenvolvimento/homologacao-da-equipe.md) |
| Testar o cadastro no WhatsApp | [Cadastro de motorista e passageiro](docs/desenvolvimento/cadastro-guiado-whatsapp.md) |
| Entender a simplificação em andamento | [Cadastro e início simples](docs/desenvolvimento/proposta-fluxo-simples.md) e [validação local](program/evidence/mvp-ux-20261117.md) |
| Acompanhar a entrega técnica | [Plano e issues](docs/desenvolvimento/entrega-mvp.md) e [evidências](program/evidence/mvp-entrega-20261116.md) |
| Fazer os testes finais nos aparelhos | [Roteiro por plataforma](docs/desenvolvimento/rodada-final.md) e [pacote identificado da rodada](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/releases/tag/mvp-0.4.0-15) |
| Conferir falhas ou pausar envios | [Operação e recuperação](docs/desenvolvimento/operacao.md) |
| Consultar a entrega da disciplina | [STACK](STACK.docx), [BACKLOG](BACKLOG.xlsx) e [Canvas](docs/06-canvas-mvp.md) |

## Situação do MVP

O ensaio de 16/11 foi interrompido antes do novo GPS: o preparo mostrou excesso
de etapas e dificuldades de retomada. A usabilidade permanece sem aceite. A
[proposta de simplificação](docs/desenvolvimento/proposta-fluxo-simples.md) define
uma interação com o bot e até três ações totais para iniciar uma saída já
preparada. O primeiro incremento está em implementação e validação locais,
incluindo início curto e reuso do cadastro; não é a versão publicada nem
comprova o teto de ações no iPhone ou com várias saídas.

Em 16/11/2026, a API da homologação usa schema V44, com HTTPS e worker
saudáveis. O [pacote da rodada final](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/releases/tag/mvp-0.4.0-15)
identifica os componentes e hashes conferidos; novos merges na `main` atualizam
a API e precisam ser reconferidos antes do percurso. No WhatsApp real passaram convite,
vínculo, aprovação do ponto, presença de ida e volta, consulta da lista e pin
recebido pelo passageiro. As preferências de chegada foram ligadas, desligadas
e ligadas novamente, com confirmação do bot após a correção da resposta.

O APK normal Android 0.4.0+15 está publicado e passou no teste estacionado:
primeiro GPS em 2,738 s, 81 amostras aceitas e um frame tardio corretamente
recusado após FINALIZAR.
O passageiro recebeu o pin; a finalização pelo WhatsApp levou a COMPLETED e
STOP/ACK, com confirmação do bot e UI de trajeto concluído. O download HTTPS
confere com o APK +15 instalado, com dados preservados. O +14 permanece para
retorno e também teve GPS/STOP/ACK reais, com 451 aceitas e zero rejeitadas.

O iPhone usa o app normal 0.4.0+15. Ele recuperou o recibo terminal e confirmou
na UI a coleta desligada, preservando o journal. Na nova sessão, recebeu o
primeiro GPS em 5,651 s: 57 amostras aceitas e um cache antigo rejeitado como
STALE. A parada pelo app recebeu ACK e a consulta de posição ficou indisponível.
O perfil gratuito vence em 23/11/2026 às 08:27 (Brasília).

As flags de rotinas de ensaio, aproximação e fim automático estão **ON** para
rotinas elegíveis da coorte compartilhada, sem whitelist por rotina. Essa
ativação ainda não comprova a utilidade do aviso ou o encerramento automático
em campo. IA e limpeza real de privacidade continuam **OFF**. Percurso, tela
bloqueada, recuperação de rede, bateria e aceite permanecem no
[roteiro da rodada final](docs/desenvolvimento/rodada-final.md).

A recuperação isolada de API, PostgreSQL, Redis e n8n passou em reinício,
agendas e restauração sem duplicar os efeitos do canal simulado. A reaplicação
do journal V44 impediu o retorno dos dados sintéticos excluídos. A rotação
física dos backups de sete dias continua pendente; não impede testar o MVP,
mas permanece requisito operacional. O
[registro da entrega](program/evidence/mvp-entrega-20261116.md) identifica as
versões, as provas e seus limites. As 65 issues MVP continuam abertas até a
conferência dos critérios e revisão humana.

## Código e desenvolvimento

| Componente | Fontes | Preparação e testes |
|---|---|---|
| API e persistência | [Backend](src/backend/) | [Guia](docs/desenvolvimento/backend.md) |
| Aplicativo do motorista | [Flutter](src/aplicativo-motorista/) | [Guia](docs/desenvolvimento/aplicativo.md) |
| Bot e automações | [WhatsApp](src/whatsapp/) | [Guia](docs/desenvolvimento/whatsapp.md) |
| Estado e dependências | [Programa](program/) | [Guia](docs/desenvolvimento/programa.md) |

As novas contribuições ficam concentradas neste repositório. As origens
anteriores permanecem como histórico. O corte e os testes da nova estrutura ficam no [registro da migração](docs/desenvolvimento/migracao.md).
O deploy automático da API pela `main` e a [área da equipe](https://vanrides-inovacao-api.matheusduarte.dev.br/equipe) estão ativos.
Veja o [fluxo de publicação](deploy/homologacao/AUTOMATICO.md) e o
[estado da área da equipe](docs/desenvolvimento/area-da-equipe.md). PRs executam
a construção, sem testes automáticos; a publicação é exclusiva de push na `main`.

## Equipe e histórico

Alex Steffens Fenner, Gustavo Cornalewski, Matheus Winck Maisonette Duarte
e Rafael Augusto Scherer.

[Entrega anterior](docs/historico/entrega-anterior/README.md) ·
[Origem dos fontes e autoria](docs/desenvolvimento/origem-e-autoria.md) ·
[Relatório para o professor](docs/apresentacoes/retorno-2026-11-03.pdf)

Codex e ChatGPT apoiaram o trabalho de forma substancial. A declaração e o
compromisso de revisão humana estão na STACK. A centralização não atribui todo
o código à pessoa que fez a importação.
