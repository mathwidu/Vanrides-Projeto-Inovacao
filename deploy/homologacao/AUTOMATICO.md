# Publicação automática da API de homologação

Um push em `main`, incluindo o push de merge do GitHub, publica a API de
homologação quando o [workflow do backend](../../.github/workflows/backend.yml)
é acionado e todos os seus gates passam. PR e `workflow_dispatch` executam a CI.
A publicação monta o runtime Linux amd64 do Dockerfile de homologação com o
mesmo JAR aprovado. O Swarm recebe a referência por digest e o SHA-256 do JAR
na mesma atualização. A tag associa SHA, execução e tentativa para rastreio.

O destino é `vanrides-hml-api_api` no ambiente compartilhado atual. O processo
mantém a configuração online, o comando de entrada, o `/tmp` gravável e as
ligações existentes do serviço. Worker, receptor, Evolution, n8n, links e APK
têm seus próprios artefatos e procedimentos de publicação.

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

## Migrações e primeira V39

Na rotina, diferenças no inventário Flyway ou na configuração de migração do
JAR interrompem a operação antes de alterar o serviço. Para uma mudança de
schema, revise o DDL no PR, prepare e prove o retorno em uma cópia recuperável
do banco e confirme a compatibilidade da versão anterior com o schema novo.
O retorno automatizado restaura a especificação anterior do serviço, mantendo
o estado do banco observado após a tentativa.

O único bootstrap previsto é a adição de `V39__whatsapp_team_admission.sql`.
Depois de comprovar backup restaurável e a versão anterior sobre o schema V39,
fixe o SHA completo que entrará na `main` e calcule o SHA-256 do SQL revisado
nesse commit. Se o GitHub gerar um commit de merge, use o SHA efetivo da `main`.
Provisione a aprovação no host antes de o job de deploy iniciar. O arquivo
regular `0600` pertence ao operador e contém apenas o SHA-256 do SQL.

```sh
(
  umask 077
  set -C
  printf '%s\n' "$APPROVED_V39_SHA256" \
    > "$HOME/.local/share/vanrides/cd/bootstrap-v39-$APPROVED_MAIN_SHA.sha256"
)
```

O workflow segue pelo comando SSH normal. A aprovação só se aplica ao SHA no
nome do arquivo e à adição da V39 com o hash aprovado. As demais migrações e
ajustes Flyway continuam idênticos ao JAR anterior. A VPS remove a aprovação
após confirmar a imagem e a saúde da API. Uma falha preserva a aprovação para
reconciliação. Se o serviço já estiver saudável no mesmo digest após uma
interrupção, reexecutar o deploy permite concluir o consumo da aprovação.
Os próximos pushes com o mesmo inventário seguem pelo CD normal. O operador
privado legado precisa de um manifesto correspondente ao runtime instalado
enquanto continuar em uso. [Operador legado](canal/OPERADOR.md)

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
