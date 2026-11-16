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
| Acompanhar a entrega técnica | [Plano e issues](docs/desenvolvimento/entrega-mvp.md) e [evidências](program/evidence/mvp-entrega-20261116.md) |
| Fazer os testes finais nos aparelhos | [Roteiro por plataforma](docs/desenvolvimento/rodada-final.md) |
| Consultar a entrega da disciplina | [STACK](STACK.docx), [BACKLOG](BACKLOG.xlsx) e [Canvas](docs/06-canvas-mvp.md) |

## Situação do MVP

A API, a área da equipe, o APK Android e o bot já estão disponíveis em
homologação. Em 15/11/2026, validamos no WhatsApp real a emissão imediata do
convite por link, a solicitação de vínculo e a aprovação e correção do ponto
de embarque. O [guia de cadastro](docs/desenvolvimento/cadastro-guiado-whatsapp.md)
documenta os comandos, o teste com um só WhatsApp e os resultados da rodada.

Em 16/11, a API publicada está em `880ea56`, com schema V43. Presença,
alterações de presença e encerramento manual/automático passaram em testes
dirigidos; a conferência dessas etapas no WhatsApp real ainda está pendente.
Aproximação e fim automático permanecem desligados até habilitar o ensaio.

O APK Android 0.4.0+14 está publicado e instalado no Redmi. A jornada com GPS
do novo build ainda precisa ser conferida. O preparo do iPhone foi retomado;
a assinatura aguarda a conta Apple/Personal Team disponível no Xcode. O
mantenedor fará percurso, bateria e avaliação do aviso após a preparação dos
artefatos, conforme o roteiro da rodada final.

A recuperação isolada com API, PostgreSQL, Redis e n8n em fila passou no
reinício, nos agendamentos e na restauração, sem duplicar os dois efeitos do
canal simulado. Retenção e reaplicação de exclusões estão em integração na
V44, ainda não publicada; limpeza real continua desabilitada. O registro de
evidências distingue os testes locais, o publicado e a rodada física restante.

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
