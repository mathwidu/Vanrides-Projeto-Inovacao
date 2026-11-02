# Runtime WhatsApp — fundação 040

Estado: **PASS local da fundação040** no commit técnico
`8b5676b1868b30785497cf81a7ff1d67db1d0332`, em31/10/2026. Gate canônico:
1.154 testes, incluindo125 PostgreSQL, zero falhas/erros/skips. Smoke integrado
do mesmo JAR passou, com dados sintéticos. Este resultado não aprova piloto,
produção, integração Evolution/n8n ou coleta nativa. O fechamento documental
não altera o artefato avaliado; proveniência e limites no QA Log e evidence040.

Fontes: [OpenAPI e fixtures](../contracts/whatsapp-mvp/README.md),
[schema](whatsapp-schema.md), [operação restrita](whatsapp-operator.md) e
[card 040](../../../program/cards/VR-WA-040-DOMAIN-AUTH.md).

## Escopo executável

O guard dedicado protege os prefixos `/integrations/whatsapp` e `/driver-app`
antes do CORS global. A allowlist considera método, caminho e principal. Rota
ou descendente desconhecido não ganha acesso por compartilhar um prefixo;
método desconhecido, OPTIONS e cabeçalho Origin não são permitidos. Credenciais
legadas Basic/Bearer não autenticam o aparelho. Respostas usam `no-store` e
correlação; erros públicos não incluem segredo, corpo original, GPS ou stack.

| Superfície | Autenticação/autoridade em 040 |
|---|---|
| `GET /integrations/whatsapp/v1/context` | Bearer `CONVERSATION_WORKER`; evento e lease próprios |
| `POST /integrations/whatsapp/v1/commands` | Mesmo principal; ator sempre derivado da inbox autenticada |
| `POST /driver-app/v1/pairings/begin` | Prova da chave do app; ainda sem motorista/token |
| `GET /driver-app/v1/pairings/{id}/status` | Prova da mesma chave e ambiente do pedido |
| `POST /driver-app/v1/pairings/redeem` | Mesma chave, aprovação humana e PKCE original |
| `POST /driver-app/v1/session/refresh` | Refresh opaco no corpo e prova da chave vinculada |
| `GET /driver-app/v1/status` | `Authorization: DPoP <access>` e prova vinculada ao access |
| `POST /driver-app/v1/session/revoke` | Mesmo vínculo; aceita credencial revogada ainda no prazo somente para revogar o próprio aparelho |
| Operador | CLI restrita; nenhuma administração HTTP pública |

CHANNEL, CONVERSATION_WORKER, DELIVERY_WORKER, SYSTEM_JOB e OPERATOR têm
credenciais separadas por principal/ambiente, sem fallback. Endpoint catalogado
para card futuro retorna `503 DEPENDENCY_UNAVAILABLE` após os controles aplicáveis;
isso inclui entrada/claim/ACK reais do canal, jobs, outbox, início/fim/retomada e
telemetria. Não enviar mensagens WhatsApp para validar esta fundação.

## Perfil de prova e sessão

Este é o perfil v1 do produto: DPoP com ES256/P-256, PKCE S256 e a extensão
`vr_body_sha256`. Não é uma implementação de servidor OAuth. A chave privada
permanece no aparelho; o backend recebe somente JWK pública fechada em
`kty/crv/x/y`, seu thumbprint JKT e provas assinadas.

- Cabeçalho JWT fechado: `typ=dpop+jwt`, `alg=ES256`, `jwk` pública.
- Claims permitidos: `jti`, `iat`, `htm`, `htu`, `nonce`, `ath` e `vr_body_sha256`.
  `htm` deve ser o método recebido; `htu` combina a URL pública canônica
  configurada com o caminho efetivo. `iat` aceita no máximo 60 segundos de idade
  ou 10 segundos no futuro, usando o relógio do servidor.
- POST vincula `vr_body_sha256` ao SHA-256 dos bytes exatos do corpo, codificado
  em Base64url sem padding. A serialização canônica para idempotência é outra
  etapa; alterar os bytes exige uma nova prova.
- Ao usar access, `ath` é o SHA-256 Base64url do token opaco. Não há `ath` nos
  pedidos autenticados somente por prova. GET não aceita corpo.
- Sem nonce utilizável, o cliente recebe `401 PROOF_NONCE_REQUIRED` e um
  `DPoP-Nonce`. A nonce vale 120 segundos, pertence à chave/ambiente e é de uso
  único; cada repetição HTTP precisa de nova prova/JTI/nonce, preservando a
  Idempotency-Key e o corpo quando recuperar uma resposta perdida.
- O ledger recusa JTI repetido por chave e guarda sua proteção por 300 segundos.
  Consumo da prova é uma transação independente do efeito de negócio.

Pareamento começa no app aberto pelo ícone confiável. `begin` vincula pedido,
JWK/JKT, desafio PKCE, plataforma, versão e ambiente antes de mostrar o código.
Custom scheme não cria nem troca esse vínculo. O código tem oito caracteres
do alfabeto base20 `BCDFGHJKMNPQRSTVWXYZ`, validade de cinco minutos e uso único.
Somente um motorista ACTIVE, habilitado e com notice aceito pode confirmá-lo
pela conversa autenticada. Código/convite nunca selecionam o ator do comando.

Resgate exige a chave e o verifier originais. Erros de PKCE contabilizam até
cinco tentativas por pedido; retry com a mesma chave idempotente/corpo recupera
a decisão anterior sem gastar outra tentativa. O resgate serializa o motorista,
incrementa a geração, revoga aparelho/tokens anteriores, invalida aprovações
antigas e solicita parada de eventual sessão de tracking anterior. Pedido de
parada e ACK do sensor são estados separados.

Access opaco vale cinco minutos. Refresh é rotativo, vinculado à mesma chave,
com limite de 30 dias a partir da emissão original; a rotação não amplia esse
prazo. Reutilizar refresh consumido com outra Idempotency-Key revoga o aparelho
e sua família somente depois de comprovar a chave correta. Uma chave errada
não provoca revogação do dono. Retry conhecido cujo replay expirou retorna
`410 INTENT_EXPIRED`, sem ser tratado como reutilização maliciosa.

Replay de begin fica disponível até a expiração do pedido; replay de resgate,
refresh e revogação tem prazo de cinco minutos. Respostas são cifradas com
AES-GCM e AAD por ambiente/operação/escopo/chave. Mesmo key/corpo recupera o
resultado original enquanto autorizado; corpo diferente retorna 409. Tokens
rotacionados, revogados ou expirados não voltam a ser expostos pelo replay.

O status do motorista mostra somente o próprio aparelho/perfil e referência
do próprio run, sem GPS. `READY` indica preparo do cadastro, sem atestar
permissão nativa, sensor ativo ou acompanhamento disponível.

## Comandos humanos e leases

040 executa somente START_ONBOARDING, SET_PROFILE_NAME, ACCEPT_NOTICE,
CLAIM_DRIVER_INVITE e CONFIRM_PAIRING. Os demais comandos do catálogo fechado
retornam 503, sem sucesso fictício. `responseIds` permanece vazio nesta fatia;
não existe despacho de resposta pelo WhatsApp em 040.

O worker informa `inboxEventId`, `expectedVersion`, payload fechado,
Idempotency-Key e `X-Inbox-Lease`. A API deriva a identidade do evento e confere
instância, credencial/principal/ambiente, estado, hash da lease, geração e prazo
na transação do efeito. A vigência precisa sobreviver à espera pelos locks:
há verificações antes/depois do efeito, não apenas no filtro HTTP. Lease vencida
não autoriza commit de efeito nem ACK antigo. A ordem de locks preserva
inbox/perfil antes da autorização do motorista/aparelho.

CLAIM_DRIVER_INVITE e CONFIRM_PAIRING também exigem que o hash do código coincida
com o código normalizado da mensagem autenticada (`driverInviteCodeHash` ou
`pairingCodeHash`). Nome/telefone/actorId produzidos pelo modelo não substituem
essa origem. Há até cinco tentativas por ator/ação/janela de cinco minutos.
Erros esperados retornam uma decisão transacional para que a contagem persista;
o controller transforma essa decisão em erro HTTP depois do commit.

Idempotência é por ator/chave/corpo e também por evento original: trocar a chave
do cliente não executa a mesma inbox novamente. Perfil bloqueado, versão
divergente e referências fora do escopo são recusados. Locks de perfil e
autorização permitem revalidar suspensão/revogação antes de emitir credenciais.

## Configuração observada

| Variável | Uso |
|---|---|
| `WA_MVP_ENABLED` | Default false; bloqueia novos efeitos quando desligado |
| `WA_NEW_STARTS_ENABLED` | Gate adicional de novos inícios/confirmação; não prova implementação de tracking |
| `WA_TEST_COHORT_ONLY` | Default true; exige lote do ambiente, permitindo synthetic=false para lote real autorizado |
| `WA_ENVIRONMENT` | Nome explícito do ambiente; sem fallback para credenciais de outro ambiente |
| `WA_PUBLIC_BASE_URL` | Origem HTTPS canônica, sem path/query/fragmento; HTTP somente localhost/loopback local |
| `WA_SECRET_HASH_KEY` | Chave de HMAC para códigos de convite/pareamento |
| `WA_RESPONSE_ENCRYPTION_KEY` | Chave distinta para replay cifrado |
| `WA_OPERATOR_CREDENTIAL` | Credencial exclusiva do operador restrito |
| `WA_OPERATOR_EXPIRES_AT` | Prazo explícito do operador |

As três credenciais/chaves configuradas exigem 32 bytes em Base64url canônico,
sem padding; as chaves de hash e cifra devem ser distintas. Com o MVP desligado
e nenhuma credencial nova configurada, o legado pode iniciar. Configuração
parcial do contexto falha fechada. Desligar novos efeitos preserva refresh,
consultas de status e revogação autenticados quando a configuração completa
continua disponível; não desconfigurar as chaves para pausar a operação.

`docker-entrypoint.sh` aceita os seguintes arquivos de segredo:

- `WA_SECRET_HASH_KEY_FILE`;
- `WA_RESPONSE_ENCRYPTION_KEY_FILE`;
- `WA_OPERATOR_CREDENTIAL_FILE`.

O entrypoint lê o arquivo, remove CR/LF, exporta a variável correspondente e
remove seu seletor `_FILE` antes de iniciar Java. Valor direto não vazio junto
com `_FILE` é recusado; arquivo ausente/ilegível interrompe a inicialização.
Usar arquivo privado 0600 e diretório restrito no preparo local. O script não
valida permissões POSIX nem imprime o conteúdo; erros podem identificar o nome
da variável e o caminho do arquivo, portanto o caminho também não deve conter
um segredo. Valores exportados continuam acessíveis ao processo: não publicar
dumps de ambiente ou habilitar shell tracing.

Os mapeamentos existentes de SPRING_DATASOURCE_PASSWORD, GOOGLE_API_KEY,
VANRIDES_JWT_SECRET e VANRIDES_EMAIL_RESEND_API_KEY permanecem preservados.

## Validação executada

`WaSecretFileEntrypointTest` contém cinco testes de shell com valores sintéticos
gerados por CSPRNG e arquivos 0600. Um `java` temporário compara os valores
recebidos sem imprimi-los, confirma a remoção de `_FILE` e preserva os argumentos
do entrypoint. Os testes cobrem leitura, exclusividade, arquivo ausente e os
mapeamentos legados por arquivo/ambiente. Nenhum Docker ou Java real é iniciado
por esses casos. Asserções não reproduzem saídas que possam conter segredo.

A suíte canônica executou esses testes, contrato/RBAC, PostgreSQL/concorrência
e regressão legada. A jornada HTTP/CLI usou o mesmo artefato/SHA limpo, incluiu
cadastro, pareamento, refresh/replay e revogação; nenhum erro foi observado no
log da API dessa execução. Interrupções anteriores por recursos permanecem
falhas/incompletas. Resultados e hashes no
[QA Log](../../../docs/desenvolvimento/backend.md) e na
[evidência do card](../../../program/evidence/VR-WA-040-DOMAIN-AUTH.md).
