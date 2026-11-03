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
| Consultar a entrega da disciplina | [STACK](STACK.docx), [BACKLOG](BACKLOG.xlsx) e [Canvas](docs/06-canvas-mvp.md) |

## Situação do MVP

Cadastros, vínculos, rotinas e presença possuem implementação e ensaios de
laboratório. O aplicativo de localização está implementado para Android/iPhone;
a integração recente foi ensaiada no Android, com o aparelho parado.

Faltam confirmação do canal real com a linha dedicada do bot, conclusão dos
avisos e do encerramento automático, percurso completo e aceite de uso.
Os testes físicos do iPhone estão pausados. **O piloto ainda não está liberado.**
CI aprovada comprova os testes declarados, não WhatsApp real ou percurso.

## Código e desenvolvimento

| Componente | Fontes | Preparação e testes |
|---|---|---|
| API e persistência | [Backend](src/backend/) | [Guia](docs/desenvolvimento/backend.md) |
| Aplicativo do motorista | [Flutter](src/aplicativo-motorista/) | [Guia](docs/desenvolvimento/aplicativo.md) |
| Bot e automações | [WhatsApp](src/whatsapp/) | [Guia](docs/desenvolvimento/whatsapp.md) |
| Estado e dependências | [Programa](program/) | [Guia](docs/desenvolvimento/programa.md) |

As novas contribuições ficam concentradas neste repositório. As origens
anteriores permanecem como histórico. O corte e os testes da nova estrutura ficam no [registro da migração](docs/desenvolvimento/migracao.md).
A implantação na VPS é independente; estes workflows não publicam imagens nem fazem deploy.

## Equipe e histórico

Alex Steffens Fenner, Gustavo Cornalewski, Matheus Winck Maisonette Duarte
e Rafael Augusto Scherer.

[Entrega anterior](docs/historico/entrega-anterior/README.md) ·
[Origem dos fontes e autoria](docs/desenvolvimento/origem-e-autoria.md) ·
[Relatório para o professor](docs/apresentacoes/retorno-2026-11-03.pdf)

Codex e ChatGPT apoiaram o trabalho de forma substancial. A declaração e o
compromisso de revisão humana estão na STACK. A centralização não atribui todo
o código à pessoa que fez a importação.
