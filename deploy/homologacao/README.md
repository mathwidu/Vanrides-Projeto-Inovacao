# API de homologação da equipe — preparo e operação

Card: VR-HML-010-TEAM-API. Destino exclusivo: stack vanrides-hml-api, domínio
vanrides-inovacao-api.matheusduarte.dev.br, ambiente vanrides-team-hml.
Este diretório prepara arquivos locais. Nenhum comando publica automaticamente,
conecta SSH, faz login, inicializa Swarm ou autoriza um deploy.

O backend exige perfil homolog exclusivo, WA_TEAM_API_ONLY=true e
VANRIDES_EMAIL_PROVIDER=disabled. As quatro rotas existentes de conversa usam
Bearer nominal e fixture sintética por integrante. Não há worker, Evolution,
n8n, Redis, webhook de WhatsApp, modelo, GPS ou credencial de provedor real.

## Arquivos e limites

| Arquivo | Responsabilidade |
|---|---|
| Dockerfile | Runtime Java17 por digest; copia somente JAR testado e entrypoint. Não compila Maven. |
| entrypoint.sh | Confere o hash do JAR antes de Java; lê os cinco secrets por arquivo; preserva argumentos do operador nonweb. |
| stack.json | Compose3.8 em JSON, aceito pelo parser Docker; API+DB, sem portas publicadas e sem ingresso por padrão. |
| db-init.sh | Bootstrap de volume novo: usuário da API sem superuser/createdb/createrole/replication; senha administrativa separada. |
| prepare.py | Valida candidato/gate/seis jornadas/JAR, prepara contexto, confere o conteúdo real da imagem e renderiza stack. |
| docker_client.py | Captura um endpoint Unix e fixa --host em toda consulta, mutação e cleanup da rodada. Recusa endpoint remoto explícito. |
| owned_container.py | Nome/nonce/labels exclusivos, inspeção antes da remoção, reconciliação após resposta perdida e cleanup sob SIGTERM. |
| backup_restore.py | Backup somente do DB exato da stack e com API parada; restore em container temporário sem rede. |
| docker_restore_smoke.py | Ensaio local opcional com fixture SQL pequena e perfil de evidência próprio. Não simula labels de uma stack real. |
| *_test.py | Provas leves com fixtures inventadas e doubles de Docker, sem daemon, JVM ou rede. |

Os helpers precisam deste checkout na revisão aprovada, inclusive os validadores
em src/whatsapp/lab/live-runtime. Não copiar esses scripts para uma árvore antiga
ou importar um manifesto histórico110 como prova desta revisão.

## Contrato dos recursos

| Recurso | Nome |
|---|---|
| Stack | vanrides-hml-api |
| Serviços | vanrides-hml-api_api e vanrides-hml-api_db, uma réplica cada |
| Banco/usuário da aplicação | vanrides_hml_api |
| Volume local | vanrides-hml-api_pgdata |
| Overlay privada | vanrides-hml-api_private, não attachable |
| Overlay do ingresso, somente quando aberto | network_swarm_public |
| Routers | vanrides-hml-api-health e vanrides-hml-api-team |
| Serviço HTTP Traefik | vanrides-hml-api-http, porta8080 |
| Entrypoint/resolver | websecure / letsencryptresolver |
| Repositório proposto da imagem | ghcr.io/mathwidu/vanrides-projeto-inovacao-team-api |

API e DB ficam no nó srv1397065, que possui o volume. A API usa UID/GID10001,
filesystem somente leitura, /tmp64MiB, limite1280MiB/1CPU; DB512MiB/1CPU.
Apenas a API entra na overlay pública quando um router é habilitado. Outros
containers dessa overlay podem alcançar a porta interna: o bloqueio do backend
e os Bearers são obrigatórios também nesse acesso. Não reutilizar banco, volume,
secret, helper de reset ou serviço da API DEV.

O volume usa o layout PostgreSQL16 /var/lib/postgresql/data. Antes de adotar um
digest, conferir a versão real e o layout do image escolhido. Troca de major
exige plano de migração próprio. Imagem existente e manifest de registry não
comprovam patch level, segurança, funcionamento da API ou autorização de release.

Secrets externos exclusivos, versão inicial v1:
- vanrides-hml-api-db-password-v1, montado na API e no bootstrap do DB.
- vanrides-hml-api-db-admin-password-v1, somente no DB.
- vanrides-hml-api-jwt-secret-v1.
- vanrides-hml-api-wa-hash-key-v1.
- vanrides-hml-api-wa-response-key-v1.
- vanrides-hml-api-wa-operator-v1.

A senha da aplicação deve ter pelo menos32 caracteres. JWT precisa atender ao
guard público; os três valores WA são32 bytes canônicos em base64url, hash e
response keys distintos. Gerar/armazenar pelo responsável no cofre; nenhum
valor entra em env versionado, argumentos, evidências ou exemplos. O bootstrap
lê a senha no PostgreSQL por pg_read_file; a senha não aparece em argv nem em
arquivo SQL gerado. O DB inicia como administrador somente para criar seu
banco/role e depois o entrypoint oficial executa PostgreSQL com seu usuário.
A API não recebe a senha administrativa. Os scripts de init não rodam em
volume já inicializado: não tentar corrigir um volume existente por redeploy.

## De CI comprovada ao contexto mínimo

Todos os caminhos de entrada/saída são absolutos; saída nova, fora do Git.
Os valores abaixo são variáveis preenchidas com provas reais, sem SHA de
exemplo para usar em publicação.

A âncora de --expected-candidate-sha256 deve vir dos pins do contrato emitido
pelo coletor que consultou no GitHub o run, tentativa, job, artifact e digest
do ZIP exatos. No schema atual, o campo é
contract.json → verification.candidateSha256. Não calcular essa âncora do
próprio candidate.json para uma publicação: isso só compara o arquivo consigo.

O coletor já existente é src/whatsapp/lab/live-runtime/collect.py. A consulta
GitHub e o download do artefato pertencem à etapa autorizada pelo principal.
Ele preserva backend.jar, contract.json e verified-ci-receipt.json. O envelope
contém os três JSONs originais como strings, com os hashes independentes fixados
pelo contrato; não extrair um ZIP arbitrário para montar a entrada.

Receita de montagem local depois de obter o diretório verificado pelo coletor:
~~~bash
python3 -B - "$COLLECTED_DIR" "$BUNDLE_DIR" <<'PY'
import os, sys
from pathlib import Path
sys.path.insert(0, 'deploy/homologacao')
import prepare as P
source, target = map(Path, sys.argv[1:])
contract = P.obj(source / 'contract.json')
envelope = P.read(source / 'verified-ci-receipt.json', P.M.MAX_JSON_BYTES)
P.M.validate_envelope(contract, envelope, source / 'backend.jar')
original = P.M.object_json(envelope)
P.new_directory(target)
for name in ('candidate', 'gate', 'smoke'):
    P.write_new(target / (name + '.json'), original[name + 'Original'].encode('utf-8'))
P.write_new(target / 'backend.jar', P.read(source / 'backend.jar'))
print('BUNDLE_RECONSTRUCTED_FROM_VERIFIED_COLLECTOR_ENVELOPE')
PY

python3 -B deploy/homologacao/prepare.py artifact \
  --bundle "$BUNDLE_DIR" \
  --expected-candidate-sha256 "$CANDIDATE_SHA256_FROM_COLLECTOR" \
  --expected-source-sha "$APPROVED_SOURCE_SHA" \
  --expected-source-tree "$APPROVED_SOURCE_TREE" \
  --java-image "$JAVA_IMAGE_DIGEST" \
  --output "$ARTIFACT_DIR"
~~~

O empacotador exige o checkout limpo no mesmo SHA/tree. Revalida gate terminal,
all-tests/PostgreSQL sem skips, seis jornadas, fonte, hashes e bytes do JAR;
copia a receita de runtime/stack e os recibos exatos. Rejeita symlink, caminho
ambíguo, arquivo extra, troca de JAR, hash ou sobrescrita da saída.

Saída:
- artifact.json, com hash do JAR, fonte, matriz, base Java e todos os arquivos.
- context/backend.jar, Dockerfile e entrypoint.sh, somente.
- evidence/candidate.json, gate.json e smoke.json.
- recipe/stack.json e db-init.sh.

Preservar o hash de artifact.json como âncora de revisão para os próximos passos.
O recibo continua PREPARED_FOR_REVIEW, releaseEligible=false e
ciProvenanceExternallyVerified=false: este processo local não consulta o
GitHub nem transforma candidato de laboratório em release aceita. A origem
externa é a prova anterior do coletor; a aprovação operacional é outra etapa.

## Construir a mesma aplicação e conferir a imagem

Receita futura de build, sem recompilar ou substituir o JAR:
~~~bash
docker build --platform linux/amd64 \
  --file "$ARTIFACT_DIR/context/Dockerfile" \
  --build-arg JAVA_IMAGE="$JAVA_IMAGE_DIGEST" \
  --build-arg SOURCE_SHA="$APPROVED_SOURCE_SHA" \
  --build-arg SOURCE_TREE="$APPROVED_SOURCE_TREE" \
  --build-arg JAR_SHA256="$JAR_SHA256_FROM_ARTIFACT" \
  --tag "$REVIEWED_IMAGE_TAG" "$ARTIFACT_DIR/context"
~~~

Build local, push e deploy são ações diferentes. Antes de push/deploy, apresentar
revisão, digests, domínio, recursos, dados, verificações e retorno e obter a
autorização específica. Não publicar por tag mutável. Depois de ter um digest
real da imagem no repositório aprovado, verificar a referência já presente no
daemon local; o verificador não faz pull:
~~~bash
python3 -B deploy/homologacao/prepare.py image \
  --artifact "$ARTIFACT_DIR" \
  --expected-artifact-sha256 "$REVIEWED_ARTIFACT_SHA256" \
  --api-image "$API_IMAGE_DIGEST" \
  --output "$IMAGE_PROOF_DIR"
~~~

A inspeção seleciona arquitetura, usuário, entrypoint, labels e digests; não
imprime envs. Cria container parado, sem rede, extrai /app/app.jar e o entrypoint
e compara seus hashes; remove somente o container próprio antes de gravar
image.json. Não inicia a aplicação nem atesta a configuração de homologação.
As labels de revisão, árvore e JAR são auxiliares; os bytes extraídos são a prova
de conteúdo. Cancelamento ou falha de cleanup não gera recibo PASS.

## Renderizar fechado e promover a entrada separadamente

~~~bash
python3 -B deploy/homologacao/prepare.py render \
  --artifact "$ARTIFACT_DIR" \
  --expected-artifact-sha256 "$REVIEWED_ARTIFACT_SHA256" \
  --api-image "$API_IMAGE_DIGEST" \
  --postgres-image "$POSTGRES_IMAGE_DIGEST" \
  --operator-expires-at "$OPERATOR_EXPIRY_UTC" \
  --output "$CLOSED_PLAN_DIR"
~~~

O modo default closed não cria router, não conecta a rede pública e mantém
traefik.enable=false. Pode preparar a definição sem prova de filesystem da
imagem; plan.json registra imageFilesystemVerified=false. Esse estado não
pode abrir operações.

Para gerar uma definição health ou team, repetir render para OUTRA saída nova,
com --ingress health ou --ingress team, --image-receipt apontando image.json e
--expected-image-receipt-sha256 com seu hash revisado. team também exige um ou
mais --allow-cidr aprovados, únicos e estreitos (IPv4 /24 ou menor faixa,
IPv6 /64 ou menor faixa). Não existe fallback que libere0.0.0.0/0.

| Modo | Acesso |
|---|---|
| closed | Sem router/rede pública |
| health | GET /actuator/health, exato, TLS |
| team | Health e quatro operações abaixo, TLS + ACL IP + Bearer nominal na API |

As únicas operações são POST /integrations/whatsapp/v1/inbox/claim,
GET /integrations/whatsapp/v1/context,
POST /integrations/whatsapp/v1/commands e
POST /integrations/whatsapp/v1/inbox/<UUID canônico>/result.
Não há PathPrefix amplo, método OPTIONS/HEAD, rota de cadastro/auth/e-mail,
driver-app, webhook events, outbox, job, Swagger ou WebSocket.

O middleware usa IPAllowList sem confiar em X-Forwarded-For do cliente.
Antes de abrir team, provar a origem efetiva no Traefik e o bloqueio de uma
origem alheia. Se routing mesh/proxy fizer origens distintas parecerem iguais,
não abrir: preparar transporte privado ou ingresso que preserve a origem.
Não alterar o Traefik global para contornar isso.

A promoção operacional health/team EXIGE, além dos arquivos renderizados:
1. Prova independente do coletor para run/tentativa/artifact/JAR exatos.
2. Evidência HML do MESMO SHA/tree e hash/bytes do JAR: perfil restrito,
   comandos sintéticos, bloqueios legados/e-mail, identidade, replay e isolamento.
3. Prova da imagem com o mesmo JAR, bases compatíveis e digests reais.
4. Plano de migração/retorno, secrets exclusivos, recursos disponíveis e ausência
   de colisões na VPS, conferidos pelo responsável.
5. Aprovação explícita para criar/atualizar secrets, rede, volume, config, imagem,
   serviços e entrada, conforme o plano concreto.

O renderizador não consulta execução GitHub, não consome/atesta a evidência HML
e não autoriza aplicar a stack. Não usar --ingress como substituto dessas provas.
Todos os recibos mantêm releaseEligible=false.

O parser pode ser conferido sem deploy:
~~~bash
docker stack config --compose-file "$CLOSED_PLAN_DIR/stack.json"
~~~
Isso verifica composição; não prova que Swarm aplicou as restrições.
O renderer fixa o caminho absoluto do db-init.sh na config. Ao transportar a
definição para outro host, preparar/revisar o novo caminho e seu hash no plano;
não aplicar o caminho do Mac na VPS nem editar um plano assinado silenciosamente.
O arquivo stack.json bruto tem placeholders obrigatórios e não é alvo direto de
deploy. Aplicar somente um plano concreto revisado, após autorização.

## Bootstrap, operação e retorno

Após aprovação, criar apenas os secrets versionados da stack e os recursos do
plano. PostgreSQL executa o bootstrap uma vez no volume novo; confirmar que o
usuário da API é NOSUPERUSER e não entra em postgres/template1. Validar versão,
migrações, imagem/JAR em execução e health antes de abrir operações. API poderá
reiniciar enquanto DB ainda inicializa: a política tem tentativas limitadas;
não trocar a senha ou relaxar o guard para mascarar falha de bootstrap.

Provisionamento de integrante: operador nonweb no mesmo JAR/imagem, rede privada,
sem router, request/result0600 em pasta0700 de propriedade do UID do processo.
entrypoint.sh repassa argumentos, inclusive --spring.main.web-application-type=none.
Credencial temporária CONVERSATION_WORKER, coorte e instância distintas; não
provisionar CHANNEL/DELIVERY_WORKER/SYSTEM_JOB. O runner do colega pertence a
scripts/homologacao e à coordenação, não a este diretório.

DNS apontando à VPS e certificado padrão do Traefik não provam a API. Confirmar
o certificado do host correto, health JSON UP com
X-VanRides-Environment: vanrides-team-hml, digest/JAR instalado e a jornada
autenticada do integrante. O header de ambiente sozinho não prova revisão.

Primeira publicação com falha: fechar somente os routers novos, revogar
credenciais da rodada e parar a nova API preservando o volume/backup. Não tocar
API DEV, n8n, Evolution, rede pública ou DNS alheio.
Atualização: guardar digest/config anteriores; reverter imagem somente com
schema compatível. Flyway não desfaz migrations. Se incompatível, restaurar em
NOVO volume, inicializar role/segredos adequados, validar a API restaurada e só
então reabrir. Não remover volume/secrets ou executar prune/reset como cleanup.

## Backup e prova de restore

O helper não para nem escala serviço. O responsável precisa autorizar e parar
a API da nova stack antes de um backup consistente, sem operador concorrente.
O helper exige réplicas0 da API e container DB com ID completo, labels exatas da
stack e imagem por digest do plano; outro DB é recusado.

Executar no host cujo daemon Unix LOCAL foi autorizado, nunca via DOCKER_HOST
remoto ou por troca de contexto:
~~~bash
python3 -B deploy/homologacao/backup_restore.py --execute-local-docker backup \
  --plan "$REVIEWED_PLAN_DIR/plan.json" \
  --expected-plan-sha256 "$REVIEWED_PLAN_SHA256" \
  --container-id "$OWNED_STACK_DB_CONTAINER_ID" \
  --output "$NEW_PRIVATE_BACKUP_DIR"

python3 -B deploy/homologacao/backup_restore.py --execute-local-docker restore \
  --backup "$NEW_PRIVATE_BACKUP_DIR" \
  --expected-backup-receipt-sha256 "$REVIEWED_BACKUP_RECEIPT_SHA256" \
  --output "$NEW_PRIVATE_RESTORE_PROOF_DIR"
~~~

Somente coortes sintéticas do ambiente e um comando persistido são aceitos.
O backup compara fingerprints antes/depois, valida PGDMP e escreve dump/recibo
em arquivos0600/pasta0700 fora do Git, sem sobrescrever. Limite aceito64MiB.
O recibo diz BACKED_UP_RESTORE_NOT_PROVEN. É necessário verificar retenção,
cópia recuperável e exclusões antes de qualquer remoção do volume original.

Restore verifica a âncora do recibo e hash/bytes do dump. Inicia PostgreSQL por
digest, network=none, sem portas/volume fonte, memória512MiB e dados em
tmpfs256MiB. O trust do PostgreSQL só existe nesse container inacessível por rede.
Compara hashes/conteagens das tabelas wa/public, incluindo histórico Flyway,
e remove o container próprio antes do recibo. Falha de cleanup impede PASS.

Esse restore comprova dados das tabelas; usa --no-owner/--no-privileges.
Não comprova roles/ACLs, sequência futura, schema compatível com imagem antiga,
migrations completas ou uso da API após recuperação. apiFunctionalTested=false
é preservado. O retorno operacional ainda requer bootstrap correto de roles,
verificação de sequências/migrations e a jornada autenticada da API restaurada.

## Verificações e evidência local desta preparação

Suíte leve prevista:
~~~bash
python3 -B -m unittest discover -s deploy/homologacao -p '*_test.py' -v
~~~

Última execução:20 testes passaram. Cobre trocas de JAR/recibo/fonte, symlink e
traversal, tags mutáveis, rotas/porta/rede/secret fora do escopo, abertura sem
prova de imagem, cleanup cancelado/perda da resposta, recurso alheio e mudança
concorrente de contexto Docker. Os dados/CI/digests dos unit tests são inventados
e não são provas de release. O parser Docker29.8.2 aceitou a stack fechada.

Ensaio local executado com PostgreSQL16 linux/arm64 já instalado, por digest
0cefaabd882e42bcb2a2f3dc56d3ce4770560ba62fd7f197f1a2e008fd5731ab,
sem pull ou Swarm. Dois containers próprios sem rede; source parado antes do
restore, dump real7320bytes, comparação de tabelas e cleanup passaram.
Recibo privado fora do Git:
~/.local/share/vanrides/mvp-20261103/hml-restore-local-20261104-01/local-restore.json.
Perfil VANRIDES_TEAM_API_LOCAL_RESTORE_SMOKE_V1,
backupSourceGuardTested=false, apiFunctionalTested=false,
releaseEligible=false. A fixture SQL não é o schema da aplicação. Isso não
prova o guard do backup contra Swarm real nem a imagem final linux/amd64.

Receita para repetir esse ensaio apenas quando houver autorização e slot:
~~~bash
python3 -B deploy/homologacao/docker_restore_smoke.py --execute-local-docker \
  --postgres-image "$ALREADY_LOCAL_POSTGRES_DIGEST" \
  --output "$NEW_PRIVATE_LOCAL_RESTORE_DIR"
~~~

Nenhum build de imagem final, push, login GHCR, deploy, GitHub write, SSH, recurso
VPS ou número WhatsApp foi operado nesta frente. Após pedido do usuário para
interromper novas rodadas, somente este README foi finalizado.

Referências: labels de Swarm ficam em deploy.labels e a porta do backend é
explícita no [provider Docker do Traefik2.11](https://doc.traefik.io/traefik/v2.11/providers/docker/);
a restrição de IP usa [IPAllowList](https://doc.traefik.io/traefik/v2.11/middlewares/http/ipallowlist/).
[Docker secrets](https://docs.docker.com/engine/swarm/secrets/) restringe a entrega
ao serviço autorizado; [docker stack config](https://docs.docker.com/reference/cli/docker/stack/config/)
apenas resolve/compoõe os arquivos.
