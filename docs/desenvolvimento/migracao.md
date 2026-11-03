# Centralização para contribuição do grupo

Estado: **em validação na branch de migração**. O destino é
mathwidu/Vanrides-Projeto-Inovacao. A mudança foi autorizada em 02/11/2026;
a implantação atual na VPS não é alterada por esta publicação.

## O que está sendo transferido

Raízes de backend e Flutter, bot/adaptadores/automação, testes, contratos,
planejamento, evidências históricas necessárias e documentos da disciplina.
A entrega acadêmica anterior fica preservada em docs/historico/entrega-anterior.

As dependências instaladas, builds, ambientes privados, credenciais, assinaturas,
bancos e histórico Git privado não são copiados. Materiais de marketing e o
módulo de mapas adiado não integram a execução do MVP. Recibos históricos
sanitizados não comprovam a nova árvore; seus originais permanecem no acervo.

## O que muda para desenvolver

Um clone, três raízes de build: src/backend, src/aplicativo-motorista e src/whatsapp.
Scripts deixam de exigir Git separado por módulo. Os contratos e checks mantêm
suas verificações de versão e consistência. Guias e CI usam caminhos da árvore nova.

Os workflows executam testes e builds em runners padrão do repositório público,
sem deploy, publicação de imagem, credenciais de produção ou uploads de builds.
A documentação oficial do GitHub informa gratuidade desses runners em projetos
públicos; serviços adicionais não foram contratados.
[Referência](https://docs.github.com/en/billing/concepts/product-billing/github-actions).

## Condição de corte

Antes de liberar novas contribuições de produto, conferir os SHAs de origem,
a CI da revisão final e o remoto. Registrar aqui o commit integrado e o resultado.
Depois do corte, novos PRs entram neste repositório; as origens ficam como histórico.
Não usar exportações periódicas que possam sobrescrever trabalho do grupo.

## Resultados

Os resultados efetivos da CI e a conferência remota serão preenchidos após a
execução. Ainda não há afirmação de teste dos computadores pessoais dos colegas,
WhatsApp real, percurso ou novo teste físico iPhone.

## Recuperação

Antes de integrar, abandonar a branch mantém as origens. Depois de contribuições
no destino, preservar e reconciliar seus commits antes de qualquer retorno.
Não fazer force push nem substituir a árvore por uma cópia antiga.
