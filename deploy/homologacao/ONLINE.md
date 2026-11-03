# API integral do MVP — preview de imagem local em um nó

Perfil do pacote: ONLINE_PREVIEW_FROM_LOCAL_IMAGE. Esta receita prepara uma
publicação rápida sem exigir nova CI ou push de imagem no GHCR. Não transforma
essa ausência em PASS: os recibos declaram ciReexecuted=false,
registryPublished=false e releaseEligible=false. O principal congela a revisão,
empacota e apresenta os artefatos concretos para autorização remota.

## Configuração acordada com o backend

- SPRING_PROFILES_ACTIVE=homolog, exclusivo.
- WA_MVP_ONLINE=true, WA_TEAM_API_ONLY=false, WA_MVP_ENABLED=true.
- WA_ENVIRONMENT=vanrides-mvp-hml; WA_TEST_COHORT_ONLY=true.
- WA_NEW_STARTS_ENABLED=true; WA_APPROACH_ENABLED=false; reviewed-routines vazio.
- WA_PUBLIC_BASE_URL e WA_START_LINK_BASE_URL:
  https://vanrides-inovacao-api.matheusduarte.dev.br.
- E-mail disabled; URL de verificação/Resend/QA auto-verify/Google/SMTP/OpenAI
  vazios; maps=false, routing=static, push=none.
- Health expõe somente saúde sem detalhes. Bearers de CHANNEL/conversa/entrega/job
  e autenticação nativa DPoP/PKCE seguem os contratos existentes da aplicação.
  O time não compartilha a credencial OPERATOR.

O modo online permite todas as rotas efetivamente disponíveis em WaRoutes,
incluindo pareamento, ativação, amostras, estado, eventos, inbox/outbox e jobs.
O comando finish ainda indisponível e cadastros/auth/operador legados continuam
negados pela allowlist do backend. Não alterar essa política no proxy.

O router usa Host exato para TODOS os caminhos/métodos do novo domínio, TLS,
prioridade10000 e o serviço da própria API. Assim, rotas desconhecidas e seus
Bearers chegam à negação da API e não caem no catch-all de outro projeto.
Não usa ACL de um único IP/colega. Antes de aplicar, conferir que nenhum router
existente com o mesmo host/prioridade superior causará conflito. Não alterar
DNS nem configuração global do Traefik. Network network_swarm_public,
entrypoint websecure, resolver letsencryptresolver, porta interna8080.

## Imagem local e referência depois do load

Um ImageID sha256 identifica o objeto local, mas não presumir que uma referência
nua sha256:... seja interpretada pelo Swarm como ImageID. O pacote usa uma
referência NOMINAL e confere seu ImageID antes e em cada container efetivo.

Preferir uma referência nominal por digest apenas se ela continuar presente
depois de docker load. docker save/load preserva imagens e tags; não assumir
preservação de RepoDigests. Se o digest nominal não existir, falhar antes do
Swarm ou preparar OUTRO plano com uma tag exclusiva do arquivo, por exemplo
vanrides-mvp-preview:<fonte12>-<jar12>. Não usar latest/main/dev, nem trocar a
referência de um plano já aprovado silenciosamente.

A tag exclusiva é uma referência LOCAL verificada pelo ImageID e pelo hash do
arquivo; não é um pin de registry remoto. O plano registra esse limite. A imagem
precisa estar carregada no único nó srv1397065, onde API e DB ficam fixados.
Não oferece redistribuição para outro nó, pull automático, HA ou recuperação
pela registry. Não aplicar prune nas imagens de retorno.

O arquivo de transporte recebe SHA256 conferido nos dois hosts. Esse hash
prova integridade contra a âncora aprovada; não é assinatura digital nem
atestado de CI. O operador deve comparar por canal já autorizado.

## Preparo local, sem executar publicação

Depois de a revisão final conter o backend online e o JAR estar pronto,
o principal prepara uma imagem runtime linux/amd64 com os labels de SHA/tree
e hash do JAR já usados pelo Dockerfile. Preservar exatamente o JAR escolhido;
não recompilar dentro da imagem. Criar o arquivo docker-save de UMA imagem,
com a referência nominal aprovada. Os comandos de build/save não são executados
por online.py.

~~~bash
python3 -B deploy/homologacao/online.py render \
  --api-image-ref "$LOCAL_NOMINAL_IMAGE_REFERENCE" \
  --api-image-id "$APPROVED_IMAGE_ID" \
  --api-archive "$DOCKER_SAVE_ARCHIVE" \
  --api-archive-sha256 "$APPROVED_ARCHIVE_SHA256" \
  --jar "$CHOSEN_BACKEND_JAR" \
  --jar-sha256 "$APPROVED_JAR_SHA256" \
  --source-sha "$SOURCE_SHA" \
  --source-tree "$SOURCE_TREE" \
  --postgres-image "$ALREADY_AVAILABLE_POSTGRES_DIGEST" \
  --expires-at "$PREVIEW_EXPIRY_UTC" \
  --target-directory "$APPROVED_PRIVATE_VPS_DIRECTORY" \
  --output "$NEW_PRIVATE_LOCAL_BUNDLE"
~~~

Não inventar SHA/digest. Referência de PostgreSQL deve ser postgres@sha256 real
já disponível no destino, linux/amd64, com layout PostgreSQL16. A saída precisa
ser nova, fora de Git; destino sob /home/mathwidu/.local/share/vanrides/.

O render confere hash do arquivo docker-save, configuração/ImageID/plataforma,
labels de fonte/JAR e os bytes do app/app.jar diretamente nas camadas do arquivo,
sem extrair o filesystem no host nem executar Docker. Compara também o JAR
separado e sua revisão declarada. Se a árvore estiver suja, o manifesto registra
isso e o hash do diff rastreado; não afirma que o binário recebeu prova de CI.
Preferir congelamento/commit da revisão final pelo principal antes do pacote.

Saída privada0600 em pasta0700:
- online.json: fonte, hashes, ImageID/ref, receita, distribuição e limites.
- api-image.tar: arquivo conferido, renomeado de forma estável.
- stack.online.json e api.env: definição e valores sem conteúdo de secrets.
- entrypoint-online.sh: configuração de startup online, hash do JAR e *_FILE.
- db-init.sh: bootstrap restrito do banco novo.
- online.py: mesmo verificador para o destino.

O entrypoint antigo da imagem só aceita o ambiente restrito da equipe. A stack
online usa uma Docker config própria com entrypoint-online.sh, sem modificar o
arquivo histórico da imagem. Continua verificando JAR e lendo secrets por arquivo.
A mudança de runtime está vinculada aos hashes do novo pacote.

A stack mantém vanrides-hml-api_api/db, volume vanrides-hml-api_pgdata,
overlay privada vanrides-hml-api_private e secrets v1 existentes no plano,
todos exclusivos desse ambiente. API não recebe a senha administrativa do DB.
Nenhuma porta do banco/API é publicada no host. Somente a API alcança a rede
pública do Traefik. O operador deve conferir que o volume é novo ou compatível;
não reutilizar o banco da API antiga.

expiresAt configura a validade do operador e a janela de revisão do preview.
Não é um TTL automático dos serviços. Ao vencer, o responsável deve fechar a
entrada/parar a API ou aprovar uma nova janela; as credenciais individuais têm
sua própria expiração/revogação. Não afirmar que Docker agenda esse encerramento.

## Sequência remota, somente após aprovação concreta

1. Conferir host, único manager/nó, recursos, nomes livres, volume e schema,
   segredo exclusivo por finalidade, domínio e ausência de router conflitante.
   Aprovar serviços, arquivos, hashes, imagem/ref e retorno antes da transferência.
2. Transferir o diretório privado ao targetDirectory registrado, sem editar
   online.json/stack/configs; os caminhos das configs são os do destino.
   Conferir SHA256 de online.json e api-image.tar com as âncoras aprovadas.
3. Após autorizar a importação, executar no daemon LOCAL da VPS:
~~~bash
docker image load --platform linux/amd64 --input "$VPS_BUNDLE/api-image.tar"
python3 -B "$VPS_BUNDLE/online.py" verify-local \
  --bundle "$VPS_BUNDLE" --expected-manifest-sha256 "$APPROVED_ONLINE_MANIFEST_SHA256"
~~~
O verificador só lê Docker: fixa o endpoint Unix, confirma srv1397065/manager,
único nó e imagem local/API/PG amd64. A ref da API precisa apontar ao ImageID
aprovado; um digest nominal também precisa constar de RepoDigests. Falha aqui
impede seguir ao Swarm. Não tentar pull, login GHCR ou renomear um digest.
4. Criar somente os secrets/configs/recursos aprovados e aplicar:
~~~bash
docker stack deploy --resolve-image never \
  --compose-file "$VPS_BUNDLE/stack.online.json" vanrides-hml-api
~~~
Não usar --with-registry-auth nem --prune. never evita resolver a imagem na
registry; não substitui a inspeção local. Todos os objetos precisam estar no nó.
5. Depois da convergência, conferir os ImageIDs efetivos de API e DB:
~~~bash
python3 -B "$VPS_BUNDLE/online.py" verify-local \
  --bundle "$VPS_BUNDLE" --expected-manifest-sha256 "$APPROVED_ONLINE_MANIFEST_SHA256" \
  --running
~~~
Cada serviço deve ter exatamente um container local em execução da stack,
com o ImageID esperado. Esse resultado ainda não equivale a API publicada.
6. Conferir certificado do domínio certo, GET /actuator/health e header
   X-VanRides-Environment: vanrides-mvp-hml. Rotas legadas/desconhecidas negadas
   pela própria API; não devem responder com outro projeto. Provar autenticação,
   coorte/pareamento e a operação escolhida pelo principal sem dados reais.
   Registrar revisão, hashes/ImageIDs e resultado, sem token, GPS bruto ou conversa.

## O que esta publicação habilita e o que falta

A API integral expõe as capacidades existentes do MVP com as autenticações
normais. Cada integrante/aparelho precisa de vínculo/credencial próprios; o APK110
de laboratório fixa localhost e não passa a usar o novo domínio automaticamente.
APK público compatível exige origem de API configurada no build correspondente.
Não enviar Bearer técnico no lugar da prova DPoP do aparelho.

O pacote não sobe worker, n8n, Evolution ou número WhatsApp. Esses consumidores
serão conectados depois, com principals/instâncias/coortes e provas próprios.
Ter /events ou /outbox disponível não conecta o bot nem prova entrega real.
E-mail/legado, mapas e aproximação continuam desligados. Percurso, bateria,
iPhone físico e piloto não foram aceitos por esta preparação.

Retorno inicial: fechar a rota nova/parar API preservando volume e arquivos
importados. Futuro rollback de imagem exige schema compatível; caso contrário,
restaurar em volume isolado e validar antes de reabrir. Nunca tocar API antiga,
gps-spike, n8n ou Evolution como limpeza implícita. Não remover volume ou imagem
de retorno sem autorização específica.

Referências: [docker load preserva imagens e tags](https://docs.docker.com/reference/cli/docker/image/load/);
[stack deploy --resolve-image never](https://docs.docker.com/reference/cli/docker/stack/deploy/)
não consulta a registry para resolução. A distribuição local continua limitada
ao nó que recebeu o arquivo e aos ImageIDs efetivamente conferidos.
