# Controle operacional no monorepo

O programa continua canônico em [`program/`](../../program/README.md).
A centralização torna o Git compartilhado; mantém módulos, gates e evidências
com escopos distintos. Consulte [a migração](migracao.md) para origem, inventário,
atribuições, verificações e condição de mudança efetiva da fonte principal.

## Começar e verificar

Git e Python 3.9+ bastam para o controle. Ele não instala Flutter, Java, Node,
Docker ou mapas e não usa credenciais. Execute na raiz do clone:

```sh
python3 scripts/program_control.py dashboard --workspace core
python3 scripts/program_control.py summary --workspace core
python3 scripts/program_control.py check --workspace core
python3 scripts/program_control.py next --workspace core
python3 scripts/program_control_test.py
python3 scripts/workspace_profile_test.py
```

No Windows, `py -3` pode substituir `python3` quando esse for o launcher
instalado. O controle usa `pathlib` e Git; a configuração versionada em
`workspaces/harness.json` contém caminhos relativos, sem vault pessoal ou clones
privados. Status/dashboard são projeções geradas em `.dev-harness/`, ignoradas.
Um status local divergente do card ativo falha no check; não é corrigido em
silêncio. `next` valida o contrato antes de mostrar trabalho executável.

## Git e diretórios dos módulos

| Identificador preservado | `repositoryRoot` | `moduleRoot` / `path` | Gate executado a partir de |
|---|---|---|---|
| `coordination` (inclui WhatsApp) | `.` | `.` | raiz; WhatsApp em `src/whatsapp/` |
| `backend` | `.` | `src/backend` | raiz Maven |
| `app` | `.` | `src/aplicativo-motorista` | raiz Flutter |
| `maps` (adiado) | `vanrides-maps-platform` | `vanrides-maps-platform` | não avaliado no core |

`repositoryRoot` identifica o Git real e `moduleRoot` o módulo, seus gates e
suas regras `allowedDirtyPaths`. `path` é mantido como alias de `moduleRoot`
para o harness. Raízes absolutas, escapes e inconsistências são rejeitados.
Um diretório herdando o Git pai só é aceito com `repositoryRoot` explícito;
a checagem de clones independentes anterior continua válida para entradas
legadas.

O controlador lê branch, HEAD e status na raiz Git declarada. Cada caminho
alterado pertence ao módulo mais específico; a coordenação recebe o restante.
Assim, um arquivo fora do escopo não desaparece por estar em um monorepo. Em
renames, origem e destino são avaliados. Caminhos com espaços e acentos são
lidos com o formato NUL do Git. `.` como regra permite o módulo inteiro apenas
quando o card o declara explicitamente; o card de migração usa essa regra nos
dois módulos que transfere.

As baselines anteriores permanecem em `origin.baseline`; não se comparam os
SHAs privados de cada repositório diretamente ao Git compartilhado. A baseline
atual é a base acadêmica antes da importação, com `remoteEvidence=false`.
`BRANCH_DRIFT`, `HEAD_DRIFT` e `NO_REMOTE_EVIDENCE` continuam explícitos; concluir
a migração exige nova evidência do SHA candidato, sem reaproveitar os testes de
origem como aprovação atual.

## CI e limites

[Programa](../../.github/workflows/programa.yml) roda em `ubuntu-latest`, com
Python 3.12, somente `contents: read`, sem credenciais persistidas e limite de
cinco minutos. Executa testes Python e dashboard/check/next. Não faz build dos
módulos, deploy, publicação, promoção, teste físico ou acesso à VPS.
A configuração segue a [sintaxe oficial de workflows](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)
e o [guia Python do GitHub](https://docs.github.com/en/actions/tutorials/build-and-test-code/python).

PASS do controle confirma integridade de estado/contratos e organização local;
os gates de backend, bot, app e integração têm comandos e evidências próprios.
O modo sem perfil continua exigindo mapas. Piloto e produção estão HOLD, iOS
físico pausado e o limite é um card vertical ativo. Outros colegas podem atuar
em partes independentes desse mesmo card, com propriedade de arquivos definida.

## Recuperação

Antes da publicação, abandonar a branch de migração mantém as origens intactas.
Depois de contribuições no destino, reconciliar os commits antes de retornar;
não substituir a árvore por uma cópia antiga ou forçar histórico. Regerar o
harness local é possível, mas guardar a projeção divergente para análise antes
de removê-la. Configuração versionada, contratos e estados não dependem do
acervo privado de evidências históricas.
