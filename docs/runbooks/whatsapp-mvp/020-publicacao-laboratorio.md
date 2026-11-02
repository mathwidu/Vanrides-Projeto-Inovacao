> Registro técnico anterior à centralização. Caminhos pessoais e endereços operacionais foram omitidos. Para executar a versão atual, use os guias em `docs/desenvolvimento/`; este histórico não autoriza deploy nem comprova a nova revisão.

# VR-WA-020 — plano de publicação do laboratório GPS

Data: 30/10/2026. **Laboratório publicado; HTTPS e jornada sintética aprovados.
Testes físicos em andamento.**
Este plano viabiliza a prova física030. Cadastro, presença e mensagens reais do
agente WhatsApp pertencem aos cards seguintes; o laboratório não é o MVP final.

## Fonte e resultado preparado

- Repositório privado: `mathwidu/vanrides-program`.
- Branch isolada: `codex/gps-spike-publication`, baseada em
  `86c523b434513bcf13baffe71e4474e25a313e8b`, `main` remoto conferido por leitura.
- Commit de publicação: **9fd20d9140291c082a1f8630e94dffbe4ee38ce6**.
- Conteúdo: somente `integrations/whatsapp/gps-spike/` e os workflows
  `gps-spike-ci.yml` / `gps-spike-publish.yml`. Os 330 commits antigos do checkout
  de coordenação não fazem parte da ancestralidade desta branch.
- App de runtime: `02183270813550d020bf3e223d22be798de1fd85`, versão 0.1.0+2,
  artefatos assinados preservados localmente. Publicar esse app nas lojas ou
  enviar o perfil privado de iOS ao GitHub não faz parte desta operação.
- Evidência e hashes: [card020](../../../program/evidence/VR-WA-020-GPS-SPIKE.md)
  e manifest associado. Imagens locais amd64 são ensaiadas por emulação no Mac;
  o workflow executará novamente o gate nativo amd64 antes de publicar.

## Operação externa proposta para aprovação

1. Publicar somente a branch isolada acima, abrir PR e executar sua CI. Conferir
   o diff restrito, checks e base remota; integrar na `main` por merge preservando
   o commit de fonte. A branch de coordenação atual e os repositórios de app e
   backend ficam fora desse push.
2. Preparar o Environment `gps-spike-isolated-lab` para a `main`, sem credenciais
   de GPS ou SSH. O consentimento desta operação é a aprovação explícita deste
   plano; não se presume proteção técnica/reviewer já configurado no GitHub.
3. Disparar `gps-spike-publish.yml` na `main`, com o **commit exato acima** e o
   ambiente `gps-spike-isolated-lab`. O gate testa Python, gateway, ingestão,
   replay, parada e restauração; exporta as mesmas duas imagens aprovadas.
4. Publicar essas imagens em **GHCR privado**:
   `ghcr.io/mathwidu/vanrides-gps-spike-receiver` e
   `ghcr.io/mathwidu/vanrides-gps-spike-gateway`. Registrar os digests produzidos
   e conferir os image IDs do gate; nenhum rebuild entre teste e publicação.
   Conferir visibilidade/acesso dos destinos antes de enviar qualquer imagem.
5. Preparar na VPS acesso de leitura a esses packages privados. Em 30/10, não
   havia configuração Docker de autenticação GHCR para `mathwidu`. A credencial
   de leitura deve ser específica para essa finalidade e informada diretamente
   no terminal/cofre; nunca pelo chat, Git ou arquivo de evidência.
6. Com DNS resolvendo e preflight revalidado, criar a stack abaixo usando os
   **digests verificados**. Provisionar identidades individuais de ensaio e a
   rota de prova privada, validar a configuração e reiniciar somente o receptor
   antes de iniciar o percurso. Coordenadas de pontos não entram na imagem/Git.
7. Validar convergência, health, certificado HTTPS, 401 sem bearer, contrato
   JSON e uma jornada sintética autenticada com ACK/replay/STOPPED. Revogar as
   credenciais sintéticas após o smoke. Prosseguir com os testes físicos030
   usando novas credenciais, roteiro e pontos combinados com o proprietário.

O workflow recebe permissão `packages:write` somente no job de publicação.
O job não executa scripts recebidos no artifact e confere hashes, image IDs,
plataforma e revisão. Jobs de PR/push executam CI, sem publicação/deploy.
A operação de VPS segue por SSH já configurado, após os gates e digests; não
requer copiar a chave SSH do Mac para o GitHub.

## Destino, dados e efeitos

| Item | Valor |
|---|---|
| Servidor | `vps-pessoal-nova`, `mathwidu@srv1397065`, `VPS_DE_TESTE` |
| Ambiente | `gps-spike-isolated-lab`, isolado do backend/n8n/Evolution existentes |
| Stack/serviços novos | `vanrides-gps-spike`; `vanrides-gps-spike_receiver` e `vanrides-gps-spike_gateway` |
| Persistência | volume novo `vanrides-gps-spike_state`, diretório privado UID10001 |
| Redes | overlay interna nova `vanrides-gps-spike_private`; gateway na `network_swarm_public` existente |
| Recursos máximos | receptor 256 MiB/0,50 CPU; gateway 64 MiB/0,25 CPU |
| HTTPS | `ambiente.example.invalid`, porta443, Traefik/certresolver existentes |
| Escritas autorizáveis | estado/recibos do laboratório, identidades de teste, configuração privada de pontos, smoke sintético e ensaio físico030 |
| GPS recebido | memória volátil; no SQLite, somente recibos/hashes/estados/eventos sanitizados |

Preflight somente leitura confirmou manager Swarm, arquitetura amd64, espaço e
ausência de colisões para os nomes novos. Traefik descarta headers no access log;
mantém metadados de requisição/caminho. O contrato proíbe bearer/coordenadas em
URL e recusa query. Mudanças no proxy compartilhado não estão neste plano.

## Ação do proprietário: DNS

Criar no domínio `ambiente.example.invalid`:

| Tipo | Nome | Destino |
|---|---|---|
| A | `gps-spike.vanrides` | `VPS_DE_TESTE` |

Usar resolução direta para a VPS e conferir ausência de AAAA/CNAME conflitante.
O proprietário confirmou a criação em 30/10/2026. A consulta posterior retornou
A `VPS_DE_TESTE`, sem AAAA/CNAME. O certificado será validado após a publicação.

## Retorno incluído na proposta

Se serviços/health não convergirem, houver digest divergente, falha TLS/401 ou
falha de ACK/STOPPED, interromper novas sessões, revogar identidades de prova e
remover **somente a stack nova**, preservando o volume para diagnóstico. Na
primeira publicação ainda não existe uma versão anterior desse receptor.

Para recuperar estado, usar o backup SQLite verificado em volume novo; revogar
todas as identidades restauradas, reprovisionar a rota privada e emitir novas
credenciais antes de servir. O ensaio local testa restauração real, contagem de
recibos/lotes e rejeição do bearer antigo. Remoção definitiva de volumes,
alterações de banco legado ou de DNS não fazem parte desse retorno.

Comandos reproduzíveis, guardas e exemplos de recuperação estão no
[runbook técnico](../../../integrations/whatsapp/gps-spike/deploy/README.md).

## Estado de autorização

**APPROVED em 30/10/2026**, pelo proprietário neste chat: “Certo, autorizo executar
esse plano no repositório mathwidu/vanrides-program e na VPS VPS_DE_TESTE. Pode
seguir com a publicação e os testes no iPhone e no Redmi, me avisando quando
precisar da minha ajuda.”

A aprovação cobre o plano acima, fonte exata
`9fd20d9140291c082a1f8630e94dffbe4ee38ce6`, incluindo push/PR/merge, Environment,
dispatch, packages privados, autenticação de leitura GHCR na VPS, stack isolada,
escritas de prova e retorno descrito. Não autoriza outra release/ambiente nem
promoção geral do MVP. No momento deste registro, nenhuma alteração externa
havia sido executada. Resultados serão registrados separadamente na evidência030.
DNS confirmado; a credencial privada ainda depende de entrada no terminal/cofre.

O proprietário também solicitou registrar escolhas e motivos para explicação
futura. O registro de decisões do plano e as evidências por card distinguem
decisões, alternativas, resultados testados e hipóteses ainda abertas.

## Ajuste do workflow identificado pela execução remota

O push autorizado abriu o [PR1](https://github.com/mathwidu/vanrides-program/pull/1).
Os gates remotos [push](https://github.com/mathwidu/vanrides-program/actions/runs/41132562024)
e [PR](https://github.com/mathwidu/vanrides-program/actions/runs/41132600631)
passaram. O GitHub rejeitou o arquivo de publicação antes de criar jobs
([execução](https://github.com/mathwidu/vanrides-program/actions/runs/41132555457)).
`actionlint 1.7.12` reproduziu o uso inválido de `runner.temp` no `env` do job,
conforme a [disponibilidade oficial de contextos](https://docs.github.com/en/actions/reference/workflows-and-actions/contexts#context-availability).

Correção local revisada: `dc567d94b7fdb3a66e26f669030462295da6473e`, somente
`.github/workflows/gps-spike-publish.yml` (+3/-2 linhas). Move `DOCKER_CONFIG`
para o `env` dos steps de publicação e limpeza, mantendo o mesmo diretório,
`always()` na limpeza, permissões, triggers, checagens e destinos. Actionlint
dos dois workflows passou e a revisão independente não encontrou problemas.

A fonte das imagens permanece **9fd20d9140291c082a1f8630e94dffbe4ee38ce6**;
esse ajuste altera a revisão do workflow, não o receptor/gateway. Antes de
enviar o novo commit e integrar o PR, alinhar essa revisão adicional com o
proprietário, pois a aprovação inicial nomeava a revisão exata anterior.
**APPROVED em 30/10/2026**: proprietário respondeu “Autorizo essa correção e a
continuidade do plano.” O commit adicional do workflow pode ser enviado ao PR1;
o `source_sha` de build/publicação permanece `9fd20d9140291c082a1f8630e94dffbe4ee38ce6`.

## Credencial GHCR: exceção aceita pelo proprietário

Na primeira entrada privada, o auxiliar recusou a combinação de conta/escopos
por exigir exatamente `read:packages`; nenhuma credencial foi transferida.
O proprietário informou que atribuiu permissões adicionais e autorizou seu uso
(“por mim tudo bem”). O auxiliar passou a exigir identidade `mathwidu` e acesso
de leitura de packages, mantendo entrada oculta, HTTPS/SSH verificados e arquivo
0600 na VPS. A segunda execução confirmou `mathwidu` com `delete:packages`,
`repo` e `write:packages`, seguida de login GHCR na configuração dedicada.
Somente metadados e download foram usados. A expiração de30dias solicitada
não foi comprovada pela API; nenhum valor do token foi registrado no projeto.

Permissões adicionais não são removidas por usar somente operações de leitura.
A alternativa de menor privilégio continua sendo um token dedicado somente com
`read:packages`; a aceitação do proprietário é uma exceção para esta credencial,
não autorização para usar seus outros poderes em repositórios ou serviços.

## Adendo de implantação: montagem temporária no Swarm

Publicação GHCR concluída no [run41133694515](https://github.com/mathwidu/vanrides-program/actions/runs/41133694515).
As duas imagens são privadas, foram baixadas na VPS e tiveram plataforma,
revisão, manifest/config digests conferidos. A primeira stack falhou porque o
CLI Swarm ignorou o campo curto `tmpfs:`; rollback removeu os dois serviços e a
rede privada, preservando `vanrides-gps-spike_state`. Não houve coleta real.

Correção preparada e revisada no commit
**926345067a427cb4c85097ef75ca5e50e746c234**, branch
`codex/gps-spike-swarm-tmpfs`: somente `deploy/stack.yml` e `deploy/README.md`.
Declara `/tmp` nos `volumes` como `type: tmpfs`, tamanho16777216, nos dois
serviços. Mantém filesystem somente leitura, usuários, capabilities, limites,
dados, redes, servidor e domínio. As imagens e sua fonte9fd20d permanecem:

- receptor: `ghcr.io/mathwidu/vanrides-gps-spike-receiver@sha256:ce2864b38056f08509bb8bc610c43cd7aa70a352c8ae74ff5671938b4135b0c3`;
- gateway: `ghcr.io/mathwidu/vanrides-gps-spike-gateway@sha256:ab3d839d2e8cbed0067fb6c02e392c74130234bf6d32822b8df6612f3917ee84`.

Reprodução controlada confirmou: sem `/tmp`, Gunicornexit255 sem diretório
temporário; com mount tipado, mesma imagem/volume inicia e responde401, modo1777.
Diagnóstico de configuração Swarm com zero réplicas confirmou ausência do mount
no template antigo e presença/tamanho correto nos dois serviços com o novo;
as configurações diagnósticas foram removidas após leitura. Nenhum endpoint de
GPS foi servido nesses diagnósticos de zero réplicas. Revisão independente sem
achados. A stack resolvida corrigida tem SHA256
`a4d16b71a387e6e56b370ba17a344a59d4384d5ee11de53865004f2b84192a51`.

Proposta: publicar esse commit em PR próprio, conferir CI e integrar; repetir
deploy usando os digests acima e a montagem corrigida; verificar mounts reais,
health, HTTPS e smoke antes dos aparelhos. Não rebuildar/publicar outras imagens
para essa correção. Retorno permanece remover somente a stack, preservando volume.
**APPROVED em 30/10/2026**: proprietário respondeu “Autorizo esse ajuste e a nova
implantação.” Abrir PR da revisão9263450, conferir CI, integrar e repetir a
implantação com os mesmos digests e o mesmo retorno. O proprietário também
confirmou abertura estável dos builds0.1.0+2 nos dois aparelhos.

## Resultado da execução aprovada

- [PR2](https://github.com/mathwidu/vanrides-program/pull/2) integrado em
  `a921438d382ea02d0b6b734b5579fdbe86f530ca`, após CI de push e PR aprovadas.
- Stack com montagem tipada implantada; receiver e gateway convergiram em1/1,
  containers correntes saudáveis. Mounts, tamanho, escrita temporária sob os
  usuários sem root, rootfs somente leitura, capabilities e limites conferidos.
- HTTPS com certificado válido; ausência de autenticação devolve401 JSON.
  Jornada pública sintética aprovou ativação, ACK, duplicidade, STOPPED, replay
  terminal e revogação403. A credencial desse smoke foi revogada.
- Builds móveis do manifest020 instalados e abertos; proprietário confirmou
  a tela de preparação estável nos dois aparelhos. GPS físico e percurso têm
  evidência própria, sem substituir suas medições pelo smoke sintético.

Detalhamento, limitações e manifest derivado na
[evidência030](../../../program/evidence/VR-WA-030-GPS-PROOF.md).
Nenhum serviço existente de n8n, Evolution ou backend foi atualizado. Esta
execução usa a autorização já concedida e não libera piloto/produção nem
autoriza automaticamente a publicação de outro runtime.
