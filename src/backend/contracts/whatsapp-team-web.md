# Área da equipe em homologação

A área usa HTTPS na mesma origem da API de homologação. A ativação exige o
perfil Spring `homolog`, `WA_MVP_ONLINE=true`, um coorte real existente e
uma instância Evolution dedicada. O painel começa desligado. Consultas e mutações
de participantes retornam 503 até a política de ingresso estrito da API estar
ativa. Disponibilize a área após a troca e a verificação do receptor.

## Sessão

- `GET /equipe/login` inicia a autorização do GitHub App com estado de uso
  único e PKCE S256. `GET /equipe/callback` valida a tentativa e volta para
  `/equipe`. Falhas retornam à página com `auth=invalid|denied|unavailable`.
- `GET /equipe/api/session` retorna `{login, csrfToken}`. A sessão dura até
  30 minutos e expira também após reiniciar a API.
- `POST /equipe/api/logout` recebe `{}` e retorna 204.
- O GitHub App tem instalação dedicada a `mathwidu/Vanrides-Projeto-Inovacao`,
  com acesso de leitura a Metadata e tokens expiráveis. A API consulta a
  permissão atual de escrita, manutenção ou administração do próprio usuário
  na instalação em cada operação autenticada. Tokens GitHub ficam na API.
- Os POSTs do navegador exigem `Content-Type: application/json`, `Origin`
  igual à origem HTTPS do painel e `X-CSRF-Token` da sessão. As rotas usam
  cookies opacos `HttpOnly`, `Secure` e `SameSite=Lax`.

## Participantes

`POST /equipe/api/participants/status` recebe `{phoneNumber}` em formato
internacional E.164. A consulta de existência na Evolution define o PN
canônico. O status só lê o coorte configurado.

`POST /equipe/api/participants` recebe `{phoneNumber, action, operationId}`.
A equipe escolhe uma das ações e informa um UUID v4 da operação:

- `ADMIT_PASSENGER` libera a conversa para cadastro do passageiro. A pessoa
  preenche o próprio perfil e aceite no WhatsApp.
- `ISSUE_DRIVER_INVITE` emite ou recupera o convite ativo para um perfil
  elegível. O motorista envia somente o código no WhatsApp. A validade máxima
  é de 24 horas e o perfil ativo impede nova emissão.
- `RENEW_DRIVER_INVITE` revoga o convite anterior já expirado e emite outro
  com novo UUID. Uma nova ação de emissão sobre convite expirado pede renovação.

Repetir o mesmo `operationId` com a mesma conta GitHub e os mesmos dados
recupera o resultado da operação. O código de um convite válido é reconstruído
no servidor. O recibo no banco guarda ator GitHub, ação, IDs, data, correlação e
HMAC do pedido. O histórico de perfil e consentimento permanece com o produto.

As respostas indicam `admissionStatus` (`ADMITTED` ou `NOT_ADMITTED`) e
`inviteStatus` (`NONE`, `ACTIVE`, `EXPIRED`, `REVOKED` ou `CONSUMED`).
Conforme o caso incluem `identityId`, `profileRole`, `profileStatus`,
`inviteId`, `expiresAt`, `operationId` e `driverInviteCode`. O código só
é exibido na resposta de mutação ou recuperação elegível. Erros usam `code`
e `correlationId`; conflitos específicos incluem `ADMISSION_CONFLICT`,
`PROFILE_CONFLICT`, `INVITE_EXPIRED`, `INVITE_NOT_RENEWABLE` e
`INVITE_UNAVAILABLE`.

Se a Evolution retornar um PN diferente do número digitado, a API preserva a
identidade canônica existente. Uma admissão histórica com o número digitado
pode coexistir se pertencer ao mesmo coorte, tiver apenas seu alias PN e estiver
livre de vínculos de domínio. Um conflito com dados existentes exige revisão.

## Ingresso Evolution

O receptor em modo API envia o mesmo envelope técnico de `/events` por
`POST /integrations/whatsapp/v1/team-events`, com a credencial `CHANNEL` e
o header obrigatório `X-Vanrides-Admission-Policy: TEAM_V1`. A API aplica a
política estrita na instância configurada e confirma o mesmo header no 202.
Versões sem essa rota recusam o pedido. `/events` também aplica a política
estrita à instância quando a flag estiver ativa.

A política requer PN associado ao coorte real e uma liberação registrada no
painel, ou um perfil/convite bootstrap já existente. Uma admissão antiga que
tenha somente alias PN precisa ser reafirmada no painel. Antes de ativar a
política, confira se os legados elegíveis coincidem com os contatos já liberados.

Para vincular um LID novo, a Evolution deve verificá-lo na consulta autenticada
ou enviá-lo em evento da mesma instância com um PN já admitido e compatível.
A primeira mensagem válida pode vincular esse LID se ele estiver livre e a
identidade ainda não tiver outro. Mensagem duplicada não cria vínculo. PN
desconhecido, LID isolado desconhecido e aliases conflitantes retornam
`IGNORED` sem criar identidade ou entrada na inbox.

## Configuração privada

As chaves abaixo usam o prefixo `VANRIDES_WA_TEAM_WEB_`:

- `ENABLED` e `STRICT_INGRESS` começam em `false`. Para abrir as operações
  de participantes, ative ambas após conferir as admissões e preparar o receptor.
- `COHORT_ID` e `INSTANCE` identificam o coorte real e a instância em uso.
- `GITHUB_CLIENT_ID` e `GITHUB_INSTALLATION_ID` identificam o GitHub App e
  sua instalação. Configure a callback para `WA_PUBLIC_BASE_URL/equipe/callback`.
- `GITHUB_CLIENT_SECRET_FILE`, `EVOLUTION_KEY_FILE` e `EVOLUTION_CA_FILE`
  apontam para arquivos privados montados. A origem TLS interna da Evolution
  fica em `EVOLUTION_ORIGIN`.
