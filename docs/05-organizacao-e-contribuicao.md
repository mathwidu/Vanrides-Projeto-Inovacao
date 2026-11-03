# Organização e contribuição

O [guia de contribuição](../CONTRIBUTING.md) é a entrada para o grupo: clone,
preparação Windows/macOS, escolha da tarefa, testes, PR e revisão.

| Informação | Fonte |
|---|---|
| Produto e fluxo | [Experiência](01-produto-e-experiencia.md) e [arquitetura](02-arquitetura-e-decisoes.md) |
| Objetivo, critérios e teste por tarefa | [Especificações](08-especificacoes-mvp.md) e issue Txx |
| Situação e próxima ação | [Backlog](07-como-ler-o-backlog.md) |
| Campos acadêmicos do professor | [Planilha](../BACKLOG.xlsx) |
| Trabalho ativo e dependências | [Programa](../program/README.md) |
| Teste da nova estrutura e origem dos arquivos | [Migração](desenvolvimento/migracao.md) |

## Uso das issues

Cada tarefa atual recebe uma issue com problema, resultado, limites, critérios,
forma de teste, dependências e próxima ação. O mesmo ID Txx aparece no documento
e na planilha. As tarefas da segunda onda ficam no backlog até sua ativação.

Filtros no GitHub:

- [MVP atual](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues?q=is%3Aissue+is%3Aopen+label%3Amvp)
- [Primeira onda](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues?q=is%3Aissue+is%3Aopen+label%3Amvp+label%3A%22onda%3A+1%22)
- [Segunda onda](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues?q=is%3Aissue+is%3Aopen+label%3Amvp+label%3A%22onda%3A+2%22)
- [Propostas a decidir](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues?q=is%3Aissue+is%3Aopen+label%3A%22situacao%3A+proposta%22)
- [Planejamento histórico](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues?q=is%3Aissue+label%3Ahistorico)

## Estados de trabalho

| Estado | Condição |
|---|---|
| Backlog | Proposta, dependência ou trabalho ainda não selecionado |
| A Fazer | Escopo e critérios claros, dependências resolvidas e responsável combinado |
| Em Progresso | Implementação efetivamente iniciada no objetivo ativo |
| Em Revisão | Escopo coberto e provas disponíveis para outro integrante conferir |
| Concluído | Critérios aprovados e resultado registrado |

As etiquetas de implementação não movem um cartão nem encerram uma issue.
Código implementado pode aguardar teste real; uma parte implementada não torna
a tarefa pronta para revisão. Responsáveis e revisores são combinados pelo grupo.

O Project citado pela equipe ainda precisa de acesso/link confirmado. A conta
usada nesta migração permite repositório/issues, mas não leitura de Projects.
Não foi criado outro quadro. Os filtros acima funcionam independentemente dele.
