# VanRides — desenvolvimento em um repositório

Leia README.md, CONTRIBUTING.md e program/README.md. Antes de alterar código,
consulte o programa e o AGENTS.md do módulo. Um card vertical ativo; preservar
alterações de colegas e os estados reais de validação.

A raiz Git é esta pasta; as raízes de build ficam em src/backend,
src/aplicativo-motorista e src/whatsapp. Não criar clones Git aninhados nem
usar as origens privadas como dependência de build. Contratos canônicos estão
em src/backend/contracts. Guias atuais em docs/desenvolvimento.

Não tratar evidência histórica como teste do SHA atual. Executar os checks
proporcionais à mudança e registrar limites. Testes físicos iPhone pausados;
serviços reais e deploy não fazem parte da CI de contribuição.

Nunca publicar credenciais, conversa ou GPS real. Exemplos e testes devem usar
dados sintéticos. PR e revisão humana precedem o encerramento de tarefas.

<!-- graphify-standard:start -->
## Graphify neste projeto

Para arquitetura, navegação entre módulos, dependências e impacto de mudanças,
use `$graphify` e consulte o grafo pelo comando abaixo, que verifica os fontes
e atualiza o índice antes de responder:

```bash
"$HOME/.local/bin/graphify-project" query "pergunta ou símbolos" --root .
```

O estado e a cobertura estão em `graphify-project status --root .`.
Confirme relações importantes nos arquivos e linhas indicados; ausência de
aresta não comprova ausência de dependência. Documentos com semântica pendente
devem ser lidos diretamente ou enriquecidos pela skill no escopo da tarefa.
Se a atualização falhar, use os fontes atuais e informe a limitação.
Ao terminar alterações relevantes, execute `graphify-project ensure --root .`.
Configuração: `.graphify/config.json` e `.graphifyignore`. Os artefatos em
`graphify-out/` são locais, regeneráveis e separados por checkout/worktree.
Se este Mac ainda não tiver a ferramenta, seguir o bootstrap documentado no
projeto Mac-M5; não instalar uma versão aleatória ou depender de um grafo antigo.
<!-- graphify-standard:end -->
