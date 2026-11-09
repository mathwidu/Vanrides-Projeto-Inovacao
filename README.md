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
| Consultar a entrega da disciplina | [STACK](STACK.docx), [BACKLOG](BACKLOG.xlsx) e [Canvas](docs/06-canvas-mvp.md) |

## Situação do MVP

Cadastros, vínculos, rotinas e presença possuem implementação e ensaios de
laboratório. O aplicativo de localização está implementado para Android/iPhone;
a integração recente foi ensaiada no Android, com o aparelho parado.

A consulta da van passou no ensaio integrado com canal simulado. A regra de aproximação passou nos testes de banco e aguarda avaliação em percurso. A API, o APK e o canal estão publicados em homologação; perfil básico de passageiro, ajuda e retomada já foram validados no WhatsApp real. Faltam validar as jornadas completas nesse canal e concluir o encerramento automático.
O bot exige admissão individual: na conferência de 04/11 há um passageiro liberado,
e ainda falta motorista participante. O APK e a saúde da API são acessíveis ao
time inteiro pelo [guia de testes](docs/desenvolvimento/homologacao-da-equipe.md).
Os testes físicos do iPhone estão pausados. **O piloto ainda não está liberado.**
CI aprovada confirma a construção da API/imagem; não comprova testes de negócio, WhatsApp real ou percurso.

## Código e desenvolvimento

| Componente | Fontes | Preparação e testes |
|---|---|---|
| API e persistência | [Backend](src/backend/) | [Guia](docs/desenvolvimento/backend.md) |
| Aplicativo do motorista | [Flutter](src/aplicativo-motorista/) | [Guia](docs/desenvolvimento/aplicativo.md) |
| Bot e automações | [WhatsApp](src/whatsapp/) | [Guia](docs/desenvolvimento/whatsapp.md) |
| Estado e dependências | [Programa](program/) | [Guia](docs/desenvolvimento/programa.md) |

As novas contribuições ficam concentradas neste repositório. As origens
anteriores permanecem como histórico. O corte e os testes da nova estrutura ficam no [registro da migração](docs/desenvolvimento/migracao.md).
O deploy automático da API pela `main` e a área da equipe estão em implantação.
Veja o [fluxo de publicação](deploy/homologacao/AUTOMATICO.md) e o
[estado da área da equipe](docs/desenvolvimento/area-da-equipe.md). PRs executam
os testes; o job de publicação é exclusivo de push na `main`.

## Equipe e histórico

Alex Steffens Fenner, Gustavo Cornalewski, Matheus Winck Maisonette Duarte
e Rafael Augusto Scherer.

[Entrega anterior](docs/historico/entrega-anterior/README.md) ·
[Origem dos fontes e autoria](docs/desenvolvimento/origem-e-autoria.md) ·
[Relatório para o professor](docs/apresentacoes/retorno-2026-11-03.pdf)

Codex e ChatGPT apoiaram o trabalho de forma substancial. A declaração e o
compromisso de revisão humana estão na STACK. A centralização não atribui todo
o código à pessoa que fez a importação.
