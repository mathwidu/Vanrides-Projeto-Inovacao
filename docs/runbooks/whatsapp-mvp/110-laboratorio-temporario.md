> Registro técnico anterior à centralização. Caminhos pessoais e endereços operacionais foram omitidos. Para executar a versão atual, use os guias em `docs/desenvolvimento/`; este histórico não autoriza deploy nem comprova a nova revisão.

# Laboratório temporário para o Redmi — card 110

**Motivo.** O Mac interrompeu a preparação local mesmo executando apenas uma JVM de cada vez. A alternativa é usar um backend e um banco descartáveis para este ensaio; o Redmi, o USB, a autorização de localização e a automação de tela permanecem no Mac do proprietário. O resultado desejado é conferir se o mesmo servidor que emite o vínculo também recebe uma aquisição real e reconcilia a parada. O canal de conversa continua com um receptor de laboratório.

**Autorização confirmada em 01/11/2026.** O proprietário aprovou este ensaio e autorizou as alterações na VPS necessárias para preparar o ambiente do MVP e o piloto real. O escopo e os limites estão na [ADR-008](../../../program/decisions/ADR-008-remote-validation-and-pilot.md). Esta rodada permanece restrita ao pacote abaixo, sem sensor; o resultado real será registrado na evidência do card.

## Ensaio autorizado: testar o servidor, sem ligar o GPS

A primeira execução proposta é **somente de infraestrutura**, com o rótulo
`redmi-vps-infra-a1`. Ela existe para responder uma pergunta: o executável
já comprovado pela CI inicia com um banco descartável nesta VPS, atende só
em loopback e pode ser removido sem alterar os serviços existentes? O pacote
recusa iniciar a ponte do telefone. Não há coleta, envio ao WhatsApp ou dado
de passageiro nesta etapa.

- Pacote local de oito arquivos, 58.347.520 bytes; SHA-256 do tar:
  `8e85301042d46f921542d769aded7f8d0a9689d5d700ee023a085adea4767725`.
  O selo do conteúdo é
  `85f93f82a3fa0ec1fc003eb9b9cb653f293475cb9fbbee066fcce1733cf1aef6`.
- Destino: um diretório novo
  `/home/mathwidu/.local/share/vanrides/wa110-vps-lab/redmi-vps-infra-a1`.
  A operação pode baixar as imagens públicas Java e PostgreSQL fixadas por
  digest. Cria uma rede privada e dois contêineres Compose desse ensaio.
  Não há alteração de Swarm, DNS, GHCR, firewall ou serviços existentes.
- Etapas: conferir origem e recibo de staging; validar identidade, capacidade,
  digests e portas; executar `infra-smoke` supervisionado. A API deve responder
  com saúde em loopback, o IP público deve recusar a porta e o banco deve
  comprovar SCRAM e ausência de credenciais do produto. Após cinco segundos
  de observação saudável, encerrar e conferir. O tempo de preparação é adicional.
- Reversão: conferir proprietário e rótulos antes de remover qualquer recurso;
  apagar apenas contêineres, rede, arquivos de teste e diretório desta rodada.
  As camadas de imagens baixadas podem permanecer no cache compartilhado; não
  usar `prune`. Em interrupção abrupta usar `stop` e `status` da mesma
  tentativa e conservar o recibo sanitizado antes de `remove-private`. Antes
  da primeira partida, `unstage-unused` pode remover somente a árvore intacta
  com os hashes e o identificador do diretório retornados pelo staging.
- Antes da execução: 12 testes de fixtures do operador, oito de transporte e análise
  estática de Compose. **Ainda não há** resultado de saúde nem de rollback
  observado neste servidor. A ponte para a jornada física abaixo é uma etapa
  distinta e não é executada por este pacote.

O [preparador do pacote](../../../integrations/whatsapp/scripts/vps110/server/prepare.py),
o [transporte e a remoção antes da partida](../../../integrations/whatsapp/scripts/vps110/stage.py)
e o [controlador do ensaio](../../../integrations/whatsapp/scripts/vps110/server/control.py)
são versionados. A CI executa os testes com arquivos e recursos simulados; não
se conecta à VPS. O pacote final foi gerado novamente a partir desses fontes:
os oito arquivos da nova cópia produziram o mesmo hash do tar acima.

| Item | Valor conferido em 01/11/2026 |
|---|---|
| Servidor | VPS pessoal `VPS_DE_TESTE`, identidade SSH e host esperados conferidos; Linux amd64; Docker 29.5 e Compose 5.1.3. |
| Capacidade na consulta | 4 vCPUs, 11.343 MiB de memória disponíveis, 127.712 MiB de disco livres; sem pressão recente de memória no host. Repetir antes de iniciar. |
| Colisões na consulta | Nenhum contêiner, volume ou rede Docker com nome contendo `wa110`. Revalidar os nomes e portas exatos antes de criar qualquer recurso. |
| Produto | Backend candidato `5ddb7c3db6c38385c018028741470ab6a509604f`; Redmi Note 8 com APK 0.2.0+11, SHA-256 `902962e5d67eb688f0001d2a76d89b04c5fc86b5f0698f7512ca7cbc2dfb3b05`. |
| Binário para esta rodada | JAR Linux de 58.304.246 bytes, SHA-256 `fc5dccdf30e234f6dc9a572ce679aa5cbc126b3e5fc90b4bf9f312270d3063ce`, baixado e conferido com a [jornada 9/9 da mesma execução](https://github.com/mathwidu/vanrides-backend/actions/runs/41267558403). Nada a compilar no Mac ou na VPS para produzir esse arquivo. |
| Fora do ensaio | Aplicativos do usuário, iPhone físico, número real do WhatsApp, serviços existentes de n8n, Evolution, backend, Traefik e laboratório 020. Não alterar DNS, nem publicar em lojas ou produção. |

A conexão planejada parte do Mac. As portas temporárias da VPS são
`127.0.0.1:28083` (API) e `127.0.0.1:28444` (controle do ensaio);
nenhuma das duas tinha listener na consulta inicial. O Mac não expõe ADB nem
aceita uma sessão de shell vinda da VPS. A validação TLS do aplicativo acontece
no Mac com o certificado de laboratório do APK. A chave privada permanece no
Mac. A consulta de configuração SSH confirmou a identidade dedicada, verificação
estrita da chave de host e encaminhamento do agente desativado.

O bind explícito ao loopback faz parte da verificação, não é o padrão presumido
do runtime. Referências: [publicação de portas no Docker](https://docs.docker.com/engine/network/port-publishing/)
e [encaminhamento local no OpenSSH](https://man.openbsd.org/ssh#L).

## Etapa posterior: o que o recibo da jornada física deve demonstrar

1. O pacote e a assinatura lidos do Redmi correspondem ao build revisado. Antes
   do início, a porta de laboratório precisa estar livre e o coletor,
   desligado. Se já existir uma sessão, interromper sem tomar controle dela.
2. Um motorista sintético chega ao vínculo pelos mesmos comandos que o worker
   usa na conversa. Abrir ou repetir o link não basta para confirmar partida.
3. Uma aquisição do sensor é aceita pela API dentro dos limites do card; só
   então o receptor de mensagens de laboratório recebe a confirmação. O
   recibo guarda contagem e resultado, sem telefone nem coordenadas.
4. Interromper apenas o túnel do ensaio para testar a parada sem rede.
   Comprovar no Android que o serviço deixou de coletar e, após reconectar,
   na API que o **mesmo** aparelho confirmou a parada da **mesma** sessão.
5. Conferir separadamente o encerramento local (Android, portas, túnel) e
   remoto (revogações, registros e recursos exclusivos desta tentativa). Se
   uma etapa falhar, o relatório registra a última comprovada e a limpeza,
   sem converter a falha em aprovação.

Um ensaio estacionado bem-sucedido não valida bateria, trajeto, aviso de
aproximação, entrega em um WhatsApp real ou funcionamento em iPhone físico.
Esses testes continuam nas etapas próprias do MVP.

## Recursos e dados

A especificação de ensaio reserva uma rede privada e um projeto com nome
exclusivo `wa110-physical-<tentativa>`. A API usa a imagem Java fixada por
digest e o JAR conferido acima; o Java roda com o UID/GID não-root da conta
SSH, sem alterar permissões dos serviços existentes. A coorte não usa nenhum banco, volume ou
credencial dos serviços já instalados. O PostgreSQL do ensaio guarda os dados
em memória temporária; desligar esse contêiner perde seus dados. Isso é
intencional. Obter o recibo sanitizado e reconciliar a coorte antes de
encerrar os recursos. O limite individual do banco é 512 MiB e o da API,
1.280 MiB. A janela prevista é de até 30 minutos.

Nenhum log HTTP, SQL, de contêiner ou de aplicação destinado a evidência deve
conter o corpo de uma amostra. Credenciais de teste ficam vinculadas à
tentativa. As ações da ponte são, em ordem: parear, obter a intenção de
início, verificar a partida e verificar a parada. O servidor não pode enviar
um comando arbitrário ao celular. Se qualquer identificação, assinatura,
digest, porta, estado inicial ou resultado de limpeza divergir, interromper
e registrar o último passo verificado.


## Revisão da proteção de recursos — 01/11/2026

As tentativas `redmi-vps-infra-a1` e `a2` usaram o pacote acima. A guarda
interrompeu por PSI, e as duas tiveram limpeza e ausência de mudança nos
38 serviços comprovadas. O pacote original fica preservado como referência.

A revisão posterior distingue pressão transitória de sustentada. Antes de
iniciar exige dez segundos contínuos com PSI full avg10 inferior a 1 e ao
menos 2.048 MiB disponíveis, dentro de um prazo de 60 segundos. Durante a
execução, o piso de 2.048 MiB continua imediato. PSI full avg10 de 5 ou mais
interrompe imediatamente; entre 1 e 5 interrompe após dez segundos contínuos.
Uma lacuna de mais de dois segundos entre amostras elevadas interrompe a
execução por perda de observação; no preflight exige nova janela normal. São limites operacionais deste laboratório, não recomendações
universais do kernel. Não alterar a proteção do Mac por analogia.

A tentativa `redmi-vps-infra-a3`, com essa revisão, também interrompeu por
pressão sustentada: mínimo de 11.165 MiB disponíveis e pico de PSI de 2,37.
A limpeza, as portas fechadas e os 38 serviços sem mudanças foram conferidos.
Isso não comprova falta de RAM nem identifica a etapa que causou a pressão.

A rodada `a4` mantém os mesmos limites e acrescenta o nome da etapa ao
resultado sanitizado. O download passa a executar PostgreSQL e Java em
sequência, com `docker compose --parallel 1 pull <serviço>`, conforme a
[documentação oficial](https://docs.docker.com/reference/cli/docker/compose/#configuring-parallelism).
Não muda a configuração do daemon compartilhado. As duas imagens já estavam
no cache por digest na consulta posterior a `a3`; uma melhora em `a4` não
isolaria, sozinha, o efeito do paralelismo do efeito desse cache.
Pacote `serial_pull_v5.tar`: 58.357.760 bytes, SHA-256
`a0d624604577c60f2f314c08ede2aee92488a3db6180e31dabb5beb679d8a6d2`;
selo `111a12f74195e8f78e2dbc26c710fef62e938510afdf8bc10c048287753ad40b`.
Os 22 testes do controlador passam localmente; a execução real terá recibo próprio.

O recibo do operador inclui somente mínimo de memória, pico de PSI, número de
amostras e limiares. A API e o banco também precisam continuar ativos, sem OOM
ou reinício, e responder aos checks autenticados/técnicos antes e depois da
janela observada. Nenhuma revisão recebe aprovação por ter passado apenas nos
testes de fixtures. Conferir o digest novo e a execução real na evidência do card.

## Guarda por contêiner — preparada após `a5`

O diagnóstico `a5`, detalhado na evidência, observou pressão global com os dois
cgroups próprios sem eventos de limite/OOM ou pressão full. A nova guarda
mantém o piso global de 2.048 MiB e usa PSI full dos cgroups próprios para os
cortes de 5 imediato e 1 por dez segundos. Eventos de limite/OOM, troca de
PID/cgroup/arquivos, limite alterado ou perda de observação bloqueiam. O
controlador resolve os cgroups validados após cada partida e depois lê seus
arquivos diretamente, sem executar Docker a cada amostra. Registra bytes,
eventos e PSI some/full agregados, sem IDs, coordenadas ou credenciais.

A admissão, o início do hold e a conclusão exigem dez segundos globais normais
dentro de 60 segundos. Durante a espera final, as guardas próprias continuam
ativas. A saúde HTTP/SQL, o prazo de inicialização, limites e cleanup permanecem.
O PSI global continua medido; não se atribui sua origem à API sem evidência.

Pacote preparado `owned_guard_v6.tar`: 58.357.760 bytes, SHA-256
`72b055023baef9897b4af5685587d729f68602c11b5092a05af192cb4e42619c`;
selo `25043af7ba5acb47ca898485236367289691e475d4b49dccdd2069046707cea8`.
Os 34 testes offline passaram. A tentativa prevista é `redmi-vps-infra-a6`,
com o mesmo JAR/imagens e sem telefone. Ainda depende da revisão e da execução
real; teste de fixture não demonstra melhora na VPS.

## Rede de acesso exclusiva da API — preparação de `a7`

Após a falha de acesso em `a6`, um servidor mínimo demonstrou que o Docker
ignorava a publicação de porta na rede somente interna. A revisão conecta a
API também à bridge `edge` exclusiva desta tentativa. PostgreSQL permanece
somente em `private`, interna. O bind continua `127.0.0.1:28083`; a guarda
confere o binding efetivo, identidade/driver/escopo de cada rede e impede
PostgreSQL ou membros alheios na edge. A edge permite saída de rede da API;
não existe alteração de firewall, rede compartilhada ou serviço existente.

Pacote `edge_network_v7.tar`: 58.368.000 bytes, SHA-256
`17cdb489e8cb4dd6bba0624b76b450f75a1cdcd05203809f0dba882937903e48`;
selo `ead789f3bcfffab6ce92e14f3a4bf1b0d18af81bbf8125756f0ca5ba82a0c18b`.
Os 42 testes do servidor e a normalização pelo `docker compose config`
passaram sem iniciar daemon/contêiner no Mac. A tentativa prevista é
`redmi-vps-infra-a7`; guarda v6, binário e imagens permanecem iguais.
O novo pacote ainda exige smoke real, cleanup e comparação dos serviços.

**Resultado da execução:** `redmi-vps-infra-a7` passou em 01/11/2026. HTTP,
PostgreSQL/SCRAM, guardas, recuperação global e hold foram aprovados no pacote
acima. Stop/status, remoção do diretório e comparação dos 38 serviços também
passaram. Recibo `90ef6ad49d85b82c9b2b91d570e11aae299ad1c6424c87ea60bc16b40845f07c`.
A [evidência do card](../../../program/evidence/VR-WA-110-RUN-TRACKING.md)
detalha consumo e limites. Não é implantação permanente nem prova física.

## Jornada física e recuperação dos fontes — 01/11/2026

O pacote físico v4 (`e1d033d7…19b41`, módulo `64f9240e…037c5`)
reutiliza o JAR Linux já atestado e a rede comprovada no infra-only a7.
A tentativa `redmi-vps110-a1` preparou motorista, duas rotinas e duas
passageiras pelo worker original. A etapa Mac falhou antes do vínculo/GPS.
Reset, quatro revogações, destruição do banco temporário, encerramento
dos listeners e remoção do diretório foram confirmados. Os 38 serviços
existentes ficaram iguais. O recibo de limpeza não é um PASS físico.

A ponta Mac passou a usar o agente SSH local do proprietário com identidade
explícita; o socket precisa pertencer ao usuário dentro de diretório0700.
Não há agent forwarding nem acesso ao socket pelo executor Android.
O manual instalado `ssh_config(5)` fundamenta `IdentityAgent=SSH_AUTH_SOCK`.
O probe real confirmou os dois listeners próprios de loopback e seu
encerramento. A preparação do leitor exige app de laboratório aberto e
tela desbloqueada; uma tela com bloqueio seguro depende do proprietário.
Não solicitar PIN/senha pelo chat nem alterar a segurança do aparelho.

O versionamento dos executores mantém JAR, chaves, credenciais, journals
e GPS fora do Git. A reprodução usa fontes e entradas pinadas. A CI usa
`fetch-depth: 0` para consultar o commit imutável do worker, conforme a
[documentação do checkout](https://github.com/actions/checkout#fetch-all-history-for-all-tags-and-branches);
`persist-credentials: false` e permissões somente de leitura são mantidos.
A CI offline não inicia SSH, ADB, Java, VPS, sensores ou envio de mensagens.

Fontes recuperáveis: [servidor físico](../../../integrations/whatsapp/scripts/vps110/physical/README.md)
e [ponta Mac](../../../integrations/whatsapp/scripts/vps110/mac/README.md).
O primeiro reproduz o tar v4 com entradas externas; o segundo instala apenas
fontes pinados em diretório privado novo. A execução da jornada continua
sendo uma operação separada do gate offline e depende do aparelho desbloqueado.

## Repetição A7 com correção da parada no Android

Retomada autorizada em 01/11. Repositórios de validação preservados; VPS
`VPS_DE_TESTE`, usuário `mathwidu`, laboratório temporário de coorte sintética.
O produto Android `76a0fc5` corrige o acionamento do envio de parada ao reabrir;
CI normal `41351275820` completa PASS. Laboratório `ef64acb`, CI `41351547473`
PASS, versão 0.2.0+12 instalada/readback no Redmi. iPhone físico pausado.

Artefatos e escopo concreto de `redmi-vps110-a7`:

- APK assinado `e022b8f2eafe1caa0f3eed13ea7e20d48e27f8a85076383d0175f98bf6b94cce`.
- Pacote V5 `bb71b220694eaff05e46a86cfb070541d00c2231666952f843748bbe9f2eb916`, 58.685.440 bytes.
- Plano `f11e578d5d150245d0dedbab47afb44a83abd7b4e2104abcfbeeea6f144b61a2`.
- Contrato `794279fa208a121b9a44cf5e46028536a3787910962c4c5a68ab3b145d341eff`.
- Módulo físico `64f9240e2510fa78c34d5fb2789b897ad35dbbf6f69626a1e33ce35eaa3037c5`,
  preservado porque os fontes de runtime não mudaram.
- Bundle Mac `4ee621e3d374bd8908fb14af09f8e5dd2f04d221f3f29e8ed8ccc36392d22373`;
  spec `fea7a390ade577318675ee76a401f46587be0c0dd28ce31ff3e793548b52bd93`.
- Operador privado `65dc3cb399ebaa052ad8a8ff48f39c52651f90e3105ca609adba78b7aa1966dd`;
  somente a pasta permitida do novo runtime muda em relação ao operador A6.

Backend/JAR, runtime, imagens, limites de memória, portas e prazos permanecem
os da A6. O contrato passa a registrar corretamente o QA `fa1da0a` e leitor613
já usados naquela rodada. A baseline somente leitura registrou 38 serviços
existentes; compará-los depois. A fixture antiga do app lab foi removida apenas
após comprovar o teardown da A6 e preservar seus diagnósticos.

Antes da execução: gate físico48Python+16Node e49Mac, revisão independente,
commit/projeção e CI da mesma revisão, staging com verificação de plano/tar,
preflight e bootstrap privado. Não iniciar o sensor sem backend pronto. Depois:
captura de recibos, parada dos recursos próprios, status STOPPED, comparação
dos serviços existentes e remoção do diretório da tentativa. A7 só passa se
receber GPS válido e confirmar remotamente a parada após reabertura, mantendo
o sensor desligado. Limpeza bem-sucedida continua separada do resultado funcional.

Rollback: encerrar A7 e conferir recursos/portas/serviços; conservar V4,
APK antigo902962 e bundles/specs correspondentes para uma nova tentativa
coerente, sem reutilizar nonce, credenciais ou journal. Nenhuma alteração em
Traefik, n8n/Evolution ou stacks atuais; nenhuma submissão real ao WhatsApp.

## Repetição A8 com diagnóstico restrito do supervisor

A7 não chegou a READY; a causa foi ocultada pelo código genérico de erro.
A recuperação independente removeu seu diretório após confirmar ausência dos
recursos; os 38 serviços da baseline permaneceram iguais. Nenhum executor Mac
ou GPS foi iniciado. As credenciais possivelmente criadas pelo filho são
UNKNOWN; banco temporário destruído, sem alegação de revogação individual.

A8 usa o mesmo APK/backend/QA/bundleMac4ee da A7. O V6 acrescenta somente
diagnóstico de classe/etapa/posições de código e preserva funcionamento,
failures, deadlines, ownership e cleanup. Pins:

- Módulo `77f4cfcdc8be3be39d88cd1e328053fb4eefaaa715da6dc8e88a331e41967d07`.
- Plano `57ed553c994de6a23c6401141dbc40bff5771e318b32d8783bff59987c42c7ed`.
- Tar `854127a87e91b2dc6a8cddbd42bdf8ea803f9b2d771b069f06dac20837dbc89d`.
- Contrato `e965a00939794bee83fcabdd0440af4b57bf0bc1398e27885d36009d95aaf0af`.
- Spec `f482517e26061294fa4f648300c4f1e9d45074071a75c74ad4bb8811094a1989`.
- OperadorV7 `46ffc0d9c05a5fc70fb6e4eb20992c4b488b81751ab8770a4da752498f60c586`.

**Planejamento anterior à execução A8:** 52Python+16NodePASS locais, revisão
do diagnóstico e do filtro PASS. Publicar
na branch de validação e conferir CI antes de staging/preflight/serve A8.
O produto Android ainda necessita da prova de confirmação remota de parada.
Se a preparação falhar, registrar o diagnóstico sanitizado, preservar FAIL e
confirmar a recuperação; não aumentar prazos para obter aprovação. V5 continua
preservado como referência. O retorno consiste em encerrar os recursos A8 e
restaurar um conjunto coerente de pins, nunca reutilizar seu nonce/journal.

### Resultado A8 e estado de retorno

A8 executada com os pins acima e CI41356523157 SUCCESS. Sete checks físicos
Mac e cinco do backend PASS: GPS real, parada offline e ACK após reabrir sem
nova coleta. Recibo `b428ae3a13f3adc08ac0c13923f2e84bd3e14739d47b8ac9b5c4048f97b660dd`.
Capture/status STOPPED/baseline38 inalterada/remove-private PASS; quatro
credenciais revogadas e banco temporário destruído. Não há runtime A8 a retomar.
Para uma nova rodada, usar nova tentativa e bootstrap, nunca o journal A8.
O APK12 permanece instalado com a sessão de laboratório encerrada. Nenhum
contato real recebeu mensagem; iPhone físico e gates de percurso seguem
pendentes. Detalhes na evidência canônica do card110.
