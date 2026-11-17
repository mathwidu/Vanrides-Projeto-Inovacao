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
schema, revise o DDL no PR, prepare e prove a restauração em uma cópia recuperável
do banco e confirme a compatibilidade da versão de retorno com o schema e os
dados que ela poderá encontrar.
O retorno automatizado restaura a especificação anterior do serviço, mantendo
o estado do banco observado após a tentativa. Ele não restaura o banco nem
comprova a compatibilidade de comportamento da imagem anterior.

Uma publicação pode adicionar de **uma a oito migrações**, todas aprovadas no
mesmo arquivo privado do host. A aprovação antiga de uma linha continua válida.
Depois de comprovar backup restaurável e a versão de retorno sobre o schema novo, fixe
o SHA completo da `main` que contém a migração. Se o GitHub gerar um commit de
merge, use o SHA efetivo da `main`. Provisione a aprovação no host antes de o
job de deploy iniciar ou antes de reexecutá-lo. O arquivo regular `0600`
pertence ao operador e contém uma linha por SQL no formato do `sha256sum`:
o SHA-256 do SQL revisado nesse commit, dois espaços e o nome do arquivo
`V<versão>__<nome>.sql`. As versões devem ser distintas. Para uma única migração:

```sh
(
  umask 077
  set -C
  git show "$APPROVED_MAIN_SHA:src/backend/src/main/resources/db/migration/$MIGRATION" | sha256sum \
    | sed "s|-\$|$MIGRATION|" > "$HOME/.local/share/vanrides/cd/migration-$APPROVED_MAIN_SHA.sha256"
)
```

O workflow segue pelo comando SSH normal. A aprovação só se aplica ao SHA no
nome do arquivo e à adição de **exatamente todo o lote**, com os nomes e hashes
aprovados. Migração faltante, extra, repetida ou com hash divergente é recusada.
As demais migrações, as bibliotecas Flyway e a configuração de migração
continuam idênticas ao JAR anterior. A VPS remove a aprovação após confirmar
a imagem e a saúde da API. Uma falha preserva a aprovação para reconciliação.
Se o serviço já estiver saudável no mesmo digest após uma interrupção,
reexecutar o deploy permite concluir o consumo somente se **todos** os SQLs
aprovados estiverem nessa imagem com os hashes esperados. Os próximos
pushes com o mesmo inventário seguem pelo CD normal. A V39 entrou por este
mesmo caminho, com o nome de arquivo anterior `bootstrap-v39-<SHA>.sha256`.
O operador privado legado precisa de um manifesto correspondente ao runtime
instalado enquanto continuar em uso. [Operador legado](canal/OPERADOR.md)

### Ensaio de lote: manifesto v3

O CLI de `rehearse_migration.py` permanece o mesmo e os manifestos v1/v2
continuam suportados conforme o [guia do ensaio](REHEARSE_MIGRATION.md).
Para um lote, use `schemaVersion: 3`, mantenha `environment`, `previous` e
`candidate` com as mesmas âncoras e substitua `migration` por `migrations`:

```json
{
  "schemaVersion": 3,
  "environment": "vanrides-mvp-hml",
  "previous": {
    "apiImage": "ghcr.io/mathwidu/vanrides-projeto-inovacao-team-api@sha256:<digest-anterior>",
    "postgresImage": "postgres@sha256:<digest-postgres>",
    "revision": "<SHA-Git-anterior-completo>",
    "tree": "<arvore-Git-anterior-completa>",
    "jarSha256": "<sha256-do-JAR-anterior>"
  },
  "candidate": {
    "jar": "/home/mathwidu/ensaio-revisado/backend.jar",
    "jarSha256": "<sha256-do-JAR-candidato>",
    "revision": "<SHA-Git-candidato-completo>",
    "tree": "<arvore-Git-candidata-completa>"
  },
  "migrations": [
    {
      "path": "BOOT-INF/classes/db/migration/V45__whatsapp_route_pickup.sql",
      "sha256": "<sha256-dos-bytes-SQL-V45-no-JAR>"
    },
    {
      "path": "BOOT-INF/classes/db/migration/V46__whatsapp_finish_intent.sql",
      "sha256": "<sha256-dos-bytes-SQL-V46-no-JAR>"
    }
  ]
}
```

Os placeholders são inválidos de propósito. O exemplo representa somente
V45/V46 sobre uma base V44. Se o pacote contiver V47 e/ou V48, a lista e a
aprovação precisam conter também esses arquivos exatos, com seus hashes;
não use wildcard nem aprove uma versão que não existe no JAR. O helper aceita
de uma a oito entradas, com versões únicas e contíguas imediatamente após a
maior versão anterior. Para este pacote, congele o lote real entre V45 e V48
antes do ensaio e não o altere entre o ensaio, a aprovação e o deploy.

No v3, **todo** o inventário anterior de SQL, bibliotecas e configurações do
JAR deve permanecer idêntico byte a byte. Não se aceita
`reviewedConfigurations`; a exceção estreita do manifesto v2 permanece só no
v2. O ensaio faz dump privado, restauração isolada e os cenários
`previous-on-previous`, `candidate-on-new` e `previous-on-new`, usando a versão
final do lote. O recibo v3 registra `manifestSchemaVersion`, `migrations`,
`previousSchemaVersion`, `targetSchemaVersion` e `inventorySha256`.
`businessCompatibilityVerified`, `returnAfterNewWritesVerified` e
`releaseEligible` permanecem **false**. Um teste com Docker simulado não é um
ensaio real; startup saudável e Flyway na versão esperada não provam regras
de negócio nem retorno após escritas novas.

Após o ensaio real do pacote exato, o operador pode gerar o arquivo de
aprovação a partir de uma lista explícita. O exemplo abaixo é somente V45/V46;
ajuste a lista revisada ao lote efetivo e execute com Bash. `pipefail` impede
que uma falha ao ler o SQL seja escondida pelo cálculo do hash. Qualquer
arquivo parcial deve ser descartado e recriado antes de liberar o deploy.

```bash
(
  set -euo pipefail
  umask 077
  set -C
  for migration in \
    V45__whatsapp_route_pickup.sql \
    V46__whatsapp_finish_intent.sql
  do
    git show "${APPROVED_MAIN_SHA}:src/backend/src/main/resources/db/migration/${migration}" \
      | sha256sum | sed "s|-\$|${migration}|"
  done > "$HOME/.local/share/vanrides/cd/migration-${APPROVED_MAIN_SHA}.sha256"
)
```

Confira cada nome/hash contra o manifesto e o JAR ensaiado antes de permitir
o job. Instale os helpers revisados antes de usar o novo formato no host;
somente editar este checkout não atualiza o comando SSH restrito instalado.

### Transição V44 → V45/V46 e recuperação

A V45 remove a regra de ponto global para permitir pontos distintos por
trajeto. A imagem V44 ainda presume essa regra. O retorno para ela só pode
ser considerado **antes de novas escritas de pontos distintos**, dentro de
uma janela sem escritas e com a compatibilidade correspondente verificada.
Não liberar o lote apenas porque a V44 iniciou sobre o schema novo no ensaio.

Antes do primeiro upgrade, o principal precisa coordenar o congelamento de
escritas na API e nos produtores, comprovar o backup dessa janela e sua
restauração, fixar as imagens/JARs e conferir o estado anterior. O lock do
deploy serializa operadores; ele **não congela escritas** da API, worker ou
outros clientes. O helper detecta mudanças entre fingerprints, mas não impõe
esse congelamento. O mecanismo de pausa e retomada pertence ao plano
operacional do pacote e precisa ser verificado antes da troca.

Ainda com escritas congeladas, atualizar e validar uma base compatível com
V45/V46, incluindo comportamento dos pontos por trajeto e intenções de
finalização, além da saúde técnica. Preservar uma imagem de recuperação
compatível com o schema **e os dados novos**, por exemplo a base `33e2b06`
após validada e identificada por SHA completo, digest e checksum de JAR.
Só depois dessa base comprovada e do plano de recuperação registrado liberar
novas escritas e continuar com as adições seguintes do lote revisado.

Após novas escritas, usar a imagem compatível de recuperação; **nunca fazer
rollback cego para V44**. A restauração do backup anterior também descartaria
escritas posteriores e exige sua própria decisão e reconciliação. O retorno
automático do atualizador continua sendo para a especificação imediatamente
anterior: antes de uma etapa que reabra escritas, o principal deve assegurar
que essa especificação seja uma base compatível ou que a janela permaneça
congelada até concluir a recuperação prevista. Este documento prepara o
procedimento; não registra congelamento, restauração ou compatibilidade reais
como executados.

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
