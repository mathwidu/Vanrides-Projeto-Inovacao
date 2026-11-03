# Push Notifications Readiness - VanRides

Data: 2026-06-19

## Objetivo

Preparar a experiencia e o contrato tecnico de notificacoes push sem prometer
funcionamento real antes de Firebase, APNs, provider de envio e testes em
device fisico estarem prontos.

## Estado atual

- O app possui abstracao `PushNotificationGateway`.
- O gateway padrao e `NoopPushNotificationGateway`, que representa provider
  indisponivel e nao abre prompt do sistema.
- O login chama `PushNotificationSessionBinder`, mas a chamada e passiva:
  apenas registra token quando a permissao ja existe. Nao deve chamar
  `requestPermission()` no login.
- O endpoint `POST /devices/fcm-token` ja existe no backend por `BE-PUSH-001A`.
- O dispatch interno ja existe por `BE-PUSH-001B`, com provider `noop` e eventos
  auditaveis, mas sem envio real para Firebase/APNs.
- A tela de preferencias ja mostra avisos planejados por papel, acao e destino
  logico, sem persistir preferencias falsas e sem acionar prompt nativo.

## Regra de produto

O app so pode pedir permissao de push depois de uma acao explicita do usuario em
uma tela de preferencias. Login, onboarding inicial e troca de aba nao devem
abrir prompt nativo.

## Categorias de preferencia

1. Viagem: inicio, fim, cancelamento e mudancas de rota.
2. Presenca: lembretes e decisoes pendentes.
3. Mensagens: tickets, respostas da empresa e suporte.
4. Operacao: alertas importantes da empresa ou mudancas do dia.

Enquanto o backend de preferencias nao existir, essas categorias aparecem como
readiness visual e contrato de UX. Elas nao devem persistir uma escolha falsa.

## Avisos acionaveis planejados

Matriz inicial alinhada aos eventos internos do backend:

| Papel | Evento backend | Titulo UX | Acao | Destino logico |
| --- | --- | --- | --- | --- |
| Passageiro | `TRIP_STATUS_CHANGED` | Van iniciou a viagem | Acompanhar rota | Viagem em andamento |
| Responsavel | `STOP_STATUS_CHANGED` | Van perto da parada | Ver acompanhamento | Acompanhamento do responsavel |
| Motorista | `STOP_STATUS_CHANGED` | Proxima parada atualizada | Abrir rota | Corrida do motorista |
| Passageiro | `PRESENCE_STATUS_CHANGED` | Presenca precisa de decisao | Responder presenca | Confirmacao de presenca |
| Empresa | `SUPPORT_TICKET_MESSAGE_CREATED` | Novo retorno no atendimento | Abrir atendimento | Atendimento |

Regras de experiencia:
- notificacao deve aumentar confianca, nao ansiedade;
- responsavel recebe contexto da pessoa acompanhada, nao todos os eventos da
  empresa;
- eventos essenciais podem furar silencio operacional; eventos opcionais ficam
  para preferencias futuras;
- nao enviar atualizacao de localizacao a cada tick de tracking;
- o toque no push deve abrir o destino logico correspondente quando o provider
  real for ativado.

## Estados de permissao

- `unavailable`: provider externo ou backend ainda nao configurados.
- `notDetermined`: provider real existe, mas o app ainda nao pediu permissao.
- `granted`: permissao ativa.
- `provisional`: iOS com autorizacao provisoria.
- `denied`: permissao negada; orientar Ajustes do sistema.

## Contrato backend futuro

### Registrar token

`POST /devices/fcm-token`

Payload:

```json
{
  "token": "fcm-token",
  "platform": "ios"
}
```

Regras:
- autenticado;
- idempotente por usuario + token;
- atualiza plataforma e timestamp;
- nao falha login se provider estiver indisponivel.

### Preferencias

Endpoint futuro sugerido:

`GET/PATCH /account/notification-preferences`

Payload sugerido:

```json
{
  "trip": true,
  "presence": true,
  "support": true,
  "operation": true
}
```

## Pre-requisitos externos

1. Projeto Firebase DEV e PROD.
2. `GoogleService-Info.plist` para iOS e `google-services.json` para Android
   injetados no build, sem commit de segredo/config sensivel.
3. Chave APNs `.p8` no Apple Developer configurada no Firebase.
4. Configuracao segura do provider real no backend, sem chaves em codigo,
   docs, logs ou commits.
5. Worker/retry ou estrategia equivalente para enviar eventos do outbox sem
   bloquear fluxos operacionais.
6. Deep link/roteamento real a partir dos destinos logicos planejados.

## Roteiro de QA em iPhone fisico

1. Instalar build DEV no iPhone fisico.
2. Entrar com usuario passageiro, motorista e responsavel.
3. Abrir Conta > Notificacoes.
4. Acionar opt-in explicito quando o gateway Firebase estiver plugado.
5. Confirmar que o token foi registrado no backend.
6. Disparar evento manual de viagem iniciada pelo backend DEV.
7. Verificar push recebido.
8. Tocar no push e validar deep link para a tela correta.
9. Revogar permissao nos Ajustes e validar estado `denied`.
10. Trocar/reinstalar app e validar re-registro de token.

## Definition of Done para ativacao real

- App usa `FirebaseMessagingPushNotificationGateway`.
- Backend persiste tokens e dispara pelo Firebase Admin SDK.
- Preferencias por categoria persistem no backend.
- Eventos planejados abrem o destino logico correto por papel.
- Smokes iOS e Android fisicos passam.
- Nenhum token, chave Firebase ou segredo aparece em logs, docs ou commits.
