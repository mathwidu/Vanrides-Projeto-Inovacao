# Publicação automática da API do VanRides

O fluxo segue a estrutura simples da API Ponto da Testing Company:
**construir → publicar imagem → atualizar o Swarm → conferir saúde**.
O único workflow automático é [backend.yml](../../.github/workflows/backend.yml).

- **Pull request ou push em `dev`:** compila e empacota a API e constrói a imagem.
  Não publica no GHCR nem altera a VPS.
- **Push em `main`:** constrói a aplicação/imagem, publica no GHCR e atualiza
  automaticamente o ambiente compartilhado. Não depende de um disparo manual.
- **Testes:** PostgreSQL, jornadas HTTP, auxiliares, WhatsApp e mobile ficam
  fora desse fluxo. Os comandos permanecem no repositório e os workflows de
  verificação só podem ser executados separadamente, quando necessários.

A imagem usa o JAR construído nessa mesma execução, com revisão e checksum.
O deploy recebe o digest imutável, atualiza imagem/checksum juntos e verifica
saúde HTTPS. Falha na construção impede a publicação; falha de saúde tenta
retornar à imagem anterior. Isso não equivale à validação funcional do MVP.
Primeira execução da main: **2 min 22 s**, incluindo build (57 s) e deploy
(75 s). O PR levou 33 s. São medições dessa execução, não um limite garantido
para toda execução futura.

O único serviço atualizado automaticamente é **`vanrides-hml-api_api`**, que
contém API e interface da equipe. Banco/volumes, worker, receptor, Evolution,
n8n e APK não são recriados por esse workflow. Mudanças de schema exigem a
preparação de migração descrita abaixo, não são liberadas por um build verde.

## Configuração inicial

- Configure a variável de repositório `VANRIDES_HML_JAVA_IMAGE` com uma imagem
  `eclipse-temurin@sha256:…` Java 17 JRE Linux amd64 revisada. Vincule o pacote
  `vanrides-projeto-inovacao-team-api` ao repositório e confira permissão de
  escrita para o workflow. O job publica com o `GITHUB_TOKEN` da execução.
- Crie o Environment `homologacao`, permitindo somente `main`. Cadastre
  `VANRIDES_HML_SSH_TARGET` com `mathwidu@host`, `VANRIDES_HML_SSH_KNOWN_HOSTS`
  com a host key previamente verificada e `VANRIDES_HML_SSH_KEY` com a chave
  exclusiva do Actions. A chave de uso pessoal permanece no Mac.
- Na VPS, prepare um diretório `~/.local/share/vanrides/cd` privado (`0700`).
  Instale `atualizar.py` a partir do commit revisado em `cd/atualizar.py`, sob
  propriedade do operador. Prepare `cd/source.git` com `git clone --bare
  --single-branch --branch main --filter=tree:0` do repositório canônico.
  Se o pacote GHCR for privado, configure no host a leitura do pacote com uma
  credencial dedicada. Os secrets de runtime permanecem no Swarm.
- Vincule a chave pública exclusiva ao comando forçado abaixo no `authorized_keys`
  do operador. A entrada não recebe opções do cliente, caminhos nem nomes de
  serviço. Instalação e renovação do auxiliar são operações do mantenedor.

```text
restrict,command="/usr/bin/python3 -I -B /home/mathwidu/.local/share/vanrides/cd/atualizar.py" ssh-ed25519 CHAVE_PUBLICA_DEDICADA vanrides-cd
```

O auxiliar serializa as trocas com lock local. Ele busca os commits de `main`
no GitHub e exige que a revisão solicitada esteja nessa linhagem e contenha o
commit instalado. Na primeira integração, use merge que preserve a ancestralidade
de `acc572f` ou avanço fast-forward. Squash e rebase perderiam essa relação.
Uma nova tentativa com o mesmo digest e checksum já saudáveis termina com
sucesso. Outro digest para o mesmo SHA passa pelas verificações completas e
pela troca da tarefa. Uma tentativa anterior à revisão instalada falha.
Um push novo preserva o workflow em curso. Se o GitHub substituir uma execução
pendente por outra mais recente, a nova imagem contém o checkout completo do
novo SHA. PR e execução manual usam filas separadas.

## Migrações

Na rotina, diferenças no inventário Flyway ou na configuração de migração do
JAR interrompem a operação antes de alterar o serviço. Para uma mudança de
schema, revise o DDL no PR, prepare e prove o retorno em uma cópia recuperável
do banco e confirme a compatibilidade da versão anterior com o schema novo.
O retorno automatizado restaura a especificação anterior do serviço, mantendo
o estado do banco observado após a tentativa.

Uma publicação pode adicionar uma única migração nova, aprovada no host. Depois
de comprovar backup restaurável e a versão anterior sobre o schema novo, fixe
o SHA completo da `main` que contém a migração. Se o GitHub gerar um commit de
merge, use o SHA efetivo da `main`. Provisione a aprovação no host antes de o
job de deploy iniciar ou antes de reexecutá-lo. O arquivo regular `0600`
pertence ao operador e contém uma linha no formato do `sha256sum`: o SHA-256 do
SQL revisado nesse commit, dois espaços e o nome do arquivo `V<versão>__<nome>.sql`.

```sh
(
  umask 077
  set -C
  git show "$APPROVED_MAIN_SHA:src/backend/src/main/resources/db/migration/$MIGRATION" | sha256sum \
    | sed "s|-\$|$MIGRATION|" > "$HOME/.local/share/vanrides/cd/migration-$APPROVED_MAIN_SHA.sha256"
)
```

O workflow segue pelo comando SSH normal. A aprovação só se aplica ao SHA no
nome do arquivo e à adição de exatamente uma migração, com o nome e o hash
aprovados.
As demais migrações, as bibliotecas Flyway e a configuração de migração
continuam idênticas ao JAR anterior. A VPS remove a aprovação após confirmar
a imagem e a saúde da API. Uma falha preserva a aprovação para reconciliação.
Se o serviço já estiver saudável no mesmo digest após uma interrupção,
reexecutar o deploy permite concluir o consumo da aprovação. Os próximos
pushes com o mesmo inventário seguem pelo CD normal. A V39 entrou por este
mesmo caminho, com o nome de arquivo anterior `bootstrap-v39-<SHA>.sha256`.
O operador privado legado precisa de um manifesto correspondente ao runtime
instalado enquanto continuar em uso. [Operador legado](canal/OPERADOR.md)

## Confirmação e retorno

O resumo da execução registra SHA, digest e checksum do JAR. No destino, o
auxiliar exige a tarefa única com a imagem esperada e `GET /actuator/health`
HTTPS com `UP` e o identificador da homologação. Se a troca falhar, só aplica
o retorno quando a especificação anterior ainda coincide com a registrada.
Qualquer atualização, bootstrap ou retorno manual do mesmo serviço deve usar
o lock `~/.local/share/vanrides/cd/deploy.lock` durante toda a operação. A
verificação da especificação e a alteração pelo Docker CLI são chamadas
separadas. O lock coordena quem o adota.
O job continua vermelho mesmo após o retorno saudável. Em caso de falha na
recuperação, o operador confere o estado antes de nova tentativa. Saúde da API
e a jornada real da equipe são evidências separadas.

## Estado da ativação inicial — 09/11/2026

PR #108 integrado por merge normal em `f433dbf`, preservando a ancestralidade.
[Build/deploy aprovado](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/42643485312).
O Environment homologacao está restrito à main, a chave de deploy recusa
comandos de terminal arbitrários, e a V39 foi aplicada. API e receptor foram
conferidos online; o login individual e as operações estão no
[guia da equipe](../../docs/desenvolvimento/area-da-equipe.md).

A configuração de runtime da área adiciona a rede privada do canal e três
secrets somente de leitura: cliente GitHub, chave da instância Evolution e CA.
As variáveis e os endpoints estão no
[contrato da área](../../src/backend/contracts/whatsapp-team-web.md).
A atualização automática preserva essa configuração. As receitas antigas de
criação em `online.py`/`canal/stack.yml` não devem ser reaplicadas sobre o ambiente
ativo sem incorporar essa configuração: elas descrevem a instalação anterior.
Snapshots privados atualizados ficam no diretório de ativação da VPS; os
valores de credenciais, contatos e coorte não pertencem ao Git.

A troca do bind do receptor usa `--mount-add` com o mesmo destino `/opt/src`,
que atualiza a origem. Não combinar com `--mount-rm /opt/src` na mesma chamada:
a remoção prevalece e deixa o serviço sem os fontes. A primeira tentativa
falhou por isso, foi revertida e a segunda foi validada. Banco e sessão do bot
foram preservados.

## Publicação da V40 — 11/11/2026

O PR #110 adicionou o motorista fictício e a migração V40. Sua execução da
main construiu a imagem, mas o atualizador recusou a diferença de schema antes
de alterar o serviço. O PR #111 generalizou a aprovação para uma adição de
migração com nome e hash fixados.

O atualizador do PR #111 foi instalado no host com checksum
`e0b4cc3e1a47a427c27f48cb73ae891b7e5f1bd0c98510d26d4992534b1d8186`.
O merge normal resultou em `4a15aea0ff5592465288d4c5bd805697f2dbe564`.
A aprovação local desse SHA liberou somente `V40__whatsapp_team_test_driver.sql`,
SHA-256 `70cfb56b96cdc00c890e27c17c03c5002895ea18082c4d89f2cc618f577888cd`.

Antes da ativação, um backup real de 1.718.892 bytes foi restaurado em
PostgreSQL com rede isolada. A comparação conferiu o conteúdo de 75 tabelas.
Na cópia, a imagem anterior iniciou em V39, a candidata aplicou V40 e a imagem
anterior voltou a iniciar com V40. As configurações eram sintéticas e o ensaio
conferiu restauração e inicialização das APIs com o usuário postgres da cópia.
A migração no ambiente ativo executou pela configuração normal da API.
Cadastro e avisos reais têm sua validação própria.

A [publicação automática](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/43032044040)
passou em 1min53s. A imagem efetiva é
`sha256:2e500eabd68d64430f7dbfe83330cf6f5c69a5ad858c98d97405eb621c367706`,
com JAR `c6e1858fecd14a4177b23f889bcde2ff86c17cb0a61cae3792a393ccd07a4fed`.
A API respondeu UP, o banco confirmou V40 e a aprovação foi consumida. Os
contêineres temporários do ensaio foram removidos. Backup, snapshots e logs
privados permanecem no diretório da release na VPS.

A primeira tentativa do ensaio parou por faltar a variável de URL de
verificação de e-mail na configuração isolada. A segunda tentativa definiu o
valor vazio exigido pelo perfil e concluiu as três inicializações.
