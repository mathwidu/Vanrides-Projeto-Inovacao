# VanRides App API Contract Consumption

Data: 2026-06-13

Ultima atualizacao: 2026-07-17 (`VR-SEC-001-BE-READ`).

Este documento e o espelho Flutter da matriz backend
`src/backend/backlog/API-Contract-Matrix.md`. Ele registra quais services
ou gateways consomem cada familia de endpoint e quais testes do app protegem
rota, metodo, auth header e parsing basico.

## Regra de manutencao

Qualquer mudanca em rota, payload, status code, auth, CORS ou WS deve atualizar:

1. a matriz backend;
2. este documento;
3. o teste backend do controller/service;
4. o teste Flutter do service/gateway consumidor.

## V3 - rota, ETA e controle de custo (`PHASE-0453`)

Atualizado em 2026-06-14 durante `PHASE-0453`.

Documento de arquitetura:
- workspace root `docs/architecture/2026-06-14-route-eta-cost-control.md`.

Esta camada ainda nao muda o app em runtime. Ela define como os proximos cards
devem consumir o contrato aditivo de mapa premium sem transformar GPS ao vivo em
recomputacao paga de rota.

Decisoes para o app:

1. `TripRouteGeometryRecord` continua sendo o objeto renderizavel de rota.
2. `navegacaoRestante` continua sendo a URL preferencial para handoff externo
   do motorista.
3. `TripLiveLocationRecord` ja consome `updatedAt` e `freshness` aditivos,
   mantendo compatibilidade com payload antigo `{latitude,longitude}`.
4. `TripTrackingRecord`, `DriverTripRecord` e `TripRouteEvent` devem receber um
   `trackingState` aditivo em card futuro.
5. A UI do mapa deve tratar `LIVE`, `DELAYED`, `STALE`, `PAUSED` e
   `UNAVAILABLE` como estados de produto.
6. O app nao decide quando recomputar rota por provedor; ele renderiza snapshot,
   deltas e estados vindos do backend.

Campos planejados por consumidor:

| Consumidor Flutter | Campos futuros | Uso esperado |
| --- | --- | --- |
| `TripTrackingRecord` | `trackingState.locationFreshness`, `trackingState.etaConfidence`, `trackingState.stopsBeforePassenger`, `trackingState.stopsRemaining` | Passageiro/responsavel ve ETA confiavel, paradas antes dele e sinal da van. |
| `DriverTripRecord` | `trackingState.routeSnapshotStatus`, `trackingState.locationFreshness`, `trackingState.etaConfidence`, `trackingState.stopsRemaining`, `trackingState.nextStopId` | Motorista ve proxima acao, rota restante e confianca da navegacao. |
| `TripRouteGeometryRecord` | `snapshotKey`, `routeInputHash`, `routeSnapshotStatus`, `routeRecomputeReason`, `routeRecomputeAllowedAfter` | App exibe/diagnostica snapshot sem conhecer custo/provedor. |
| `TripLiveLocationRecord` | `updatedAt`, `freshness` (ja consumidos; opcionais no payload legado) | Marcador da van nao parece vivo quando a coordenada esta velha. |
| `TripRouteEvent` | `trackingState{...}` | WebSocket atualiza progresso/ETA/freshness sem forcar refresh completo de rota. |

Estados que o app deve mapear visualmente:

- `routeSnapshotStatus`: `PLANNED`, `MATERIALIZED`, `STALE`, `UNAVAILABLE`.
- `locationFreshness`: `LIVE`, `DELAYED`, `STALE`, `PAUSED`, `UNAVAILABLE`.
- `etaConfidence`: `HIGH`, `MEDIUM`, `LOW`, `UNAVAILABLE`.

## Consumo por dominio

| Dominio | Endpoints consumidos | Service/gateway Flutter | Testes do app | Testes backend de contrato |
| --- | --- | --- | --- | --- |
| Auth admin e sessao | `POST /login`, `POST /company-admins/bootstrap`, `POST /auth/email-verifications/resend`, `POST /auth/email-verifications/change-email` | `lib/features/admin/data/admin_portal_auth_service.dart` | `test/features/admin/admin_portal_auth_service_test.dart`, `test/features/admin/admin_login_screen_test.dart` | `LoginControllerWebMvcTest`, `CompanyAdminControllerWebMvcTest`, `AuthControllerWebMvcTest` |
| Auth publica e confirmacao | `POST /auth/email-verifications/resend` e suporte de erro `EMAIL_NOT_VERIFIED` | `lib/services/public_auth_support.dart` | `test/services/public_auth_support_test.dart`, `test/login_screen_test.dart` | `AuthControllerWebMvcTest`, `LoginControllerWebMvcTest`, `SecurityFilterWebMvcTest` |
| Cadastros publicos | `POST /motoristas`, `POST /passageiros`, `GET /motoristas` | `lib/features/driver_onboarding/data/motorista_cadastro_gateway.dart`, `lib/features/passenger_onboarding/data/passageiro_cadastro_gateway.dart`, `integration_test/_support/api_support.dart` | `test/features/driver_onboarding/motorista_cadastro_screen_test.dart`, `test/features/passenger_onboarding/passageiro_cadastro_screen_test.dart`, `test/services/public_auth_support_test.dart`; fixtures E2E usam o `id` retornado no `POST /motoristas`, nao e-mail publico | `MotoristaControllerWebMvcTest`, `PassageiroControllerWebMvcTest`, `SecurityFilterWebMvcTest` |
| Dashboard admin | `GET /company-admins/dashboard/summary?data=YYYY-MM-DD` | `lib/features/admin/data/admin_dashboard_service.dart` | `test/features/admin/admin_dashboard_service_test.dart`, `test/features/admin/admin_dashboard_screen_test.dart` | `CompanyAdminDashboardControllerWebMvcTest`, `CompanyAdminDashboardServiceTest` |
| Motoristas admin | `GET/POST/PUT/DELETE /company-admins/motoristas` | `lib/features/admin/data/admin_motorista_portal_service.dart` | `test/features/admin/admin_motorista_portal_service_test.dart`, `test/features/admin/admin_motoristas_screen_test.dart` | `CompanyAdminMotoristaControllerWebMvcTest`, `CompanyAdminMotoristaServiceTest` |
| Passageiros admin | `GET/POST/PUT /company-admins/passageiros`, `GET/PUT /company-admins/passageiros/{id}/servicos`, `PUT /company-admins/passageiros/{id}/motorista` | `lib/features/admin/data/admin_passageiro_portal_service.dart` | `test/features/admin/admin_passageiro_portal_service_test.dart`, `test/features/admin/admin_passageiros_screen_test.dart` | `CompanyAdminPassageiroControllerWebMvcTest`, `CompanyAdminPassageiroServiceTest` |
| Responsaveis admin | `GET/POST/PUT/DELETE /company-admins/responsaveis` | `lib/features/admin/data/admin_responsavel_portal_service.dart` | `test/features/admin/admin_responsavel_portal_service_test.dart`, `test/features/admin/admin_responsaveis_screen_test.dart` | `CompanyAdminGuardianControllerWebMvcTest`, `CompanyAdminGuardianServiceTest` |
| Rotas base admin | `GET/POST/PUT/DELETE /company-admins/rotas-base` e listas auxiliares de motoristas/passageiros | `lib/features/admin/data/admin_rotas_portal_service.dart` | `test/features/admin/admin_rotas_screen_test.dart`, widget tests de rotas | `CompanyAdminRotaControllerWebMvcTest`, `CompanyAdminRotaServiceTest` |
| Servicos recorrentes admin | `GET/POST/PUT/DELETE /company-admins/servicos-recorrentes`, `GET/PUT /company-admins/servicos-recorrentes/{id}/passageiros` | `lib/features/admin/data/admin_servicos_recorrentes_portal_service.dart` | `test/features/admin/admin_servicos_recorrentes_portal_service_test.dart`, widget tests de servicos recorrentes | `CompanyAdminServicoRecorrenteControllerWebMvcTest`, `CompanyAdminServicoRecorrenteServiceTest` |
| Viagens admin | `GET/POST /company-admins/viagens`, `POST /company-admins/viagens/gerar-automaticas`, `GET /company-admins/viagens/{id}`, `PUT /company-admins/viagens/{id}/motorista`, `POST /company-admins/viagens/{id}/cancelar`, `POST/PUT /presencas-servico` | `lib/features/admin/data/admin_viagens_portal_service.dart` | `test/features/admin/admin_viagens_portal_service_test.dart`, `test/features/admin/admin_viagens_screen_test.dart`, widget tests de viagens | `CompanyAdminViagemControllerWebMvcTest`, `CompanyAdminViagemServiceTest`, `ViagemProvisioningServiceTest`, `PresencaServicoControllerWebMvcTest` |
| Vinculos admin | `/company-admins/vinculos/*`, `/company-admins/access-requests*`, `/company-admins/invite-codes*`, `/company-admins/passageiros/{id}/servicos` | `lib/features/admin/data/admin_vinculo_portal_service.dart` | `test/features/admin/admin_vinculo_portal_service_test.dart`, `test/features/admin/admin_vinculos_screen_test.dart`, widget tests de vinculos | `CompanyAdminVinculoControllerWebMvcTest`, `PassengerOnboardingServiceTest`, `CompanyAdminPassageiroControllerWebMvcTest` |
| Places admin | `GET /company-admins/places/autocomplete`, `GET /company-admins/places/details` | `lib/features/admin/data/admin_places_gateway.dart` | `test/features/admin/data/admin_places_gateway_test.dart`, destination picker widget tests | `PlacesProxyServiceTest`; falta `CompanyAdminPlacesControllerWebMvcTest` |
| Onboarding do passageiro | `GET /passageiro-onboarding/status`, `GET /passageiro-onboarding/companies`, `POST /passageiro-onboarding/requests`, `POST /passageiro-onboarding/invite-codes/redeem`, `POST /passageiro-onboarding/session-upgrade` | `lib/features/passenger_onboarding/data/passenger_onboarding_service.dart` | `test/features/passenger_onboarding/passenger_company_link_flow_test.dart`, `test/features/passenger_onboarding/presentation/widgets/passenger_company_link_widgets_test.dart` | `PassengerOnboardingControllerWebMvcTest`, extracted service tests |
| Operacao do motorista | `GET /viagens/motorista/{id}/hoje`, `GET /viagens/motorista/{id}/dia`, `PUT /viagens/{id}/rota`, lifecycle/paradas | `lib/features/driver_trip/data/driver_trip_service.dart` | `test/features/driver_trip/driver_trip_service_test.dart`, `home_motorista_*_test.dart` | `ViagemControllerWebMvcTest`, `ViagemOperacionalServiceTest`, `ViagemProvisioningServiceTest` |
| Tracking passageiro | `GET /viagens/passageiro/{id}/atual`, `GET /viagens/passageiro/{id}/dia`, `GET /api/localizacao/{motoristaId}` | `lib/features/tracking/data/trip_tracking_service.dart`, `lib/features/tracking/data/trip_live_location_service.dart` | `test/features/tracking/trip_tracking_service_test.dart`, `test/features/tracking/data/trip_live_location_service_test.dart`, map-first widget tests | `ViagemControllerWebMvcTest`, `LocalizacaoMotoristaControllerWebMvcTest`, `GuardianAccessServiceTest` |
| Presenca por servico | `GET /passageiros/{id}/servicos-disponiveis`, `POST/PUT/DELETE /presencas-servico` | `lib/features/service_presence/data/service_presence_service.dart` | `test/features/service_presence/service_presence_service_test.dart`, `service_presence_decision_list_test.dart` | `PassageiroServicoControllerWebMvcTest`, `PresencaServicoControllerWebMvcTest`, `PresencaServicoServiceTest` |
| Responsavel mobile | `GET /passageiros`, `GET /presencas/passageiro/{id}`, `POST /presencas`, `DELETE /presencas/{id}`, `GET /api/localizacao/{motoristaId}/historico`; nao usa `PUT/DELETE /passageiros/{id}` para ficha cadastral | `lib/features/guardian/data/guardian_passageiro_service.dart`, `guardian_presenca_service.dart`, `guardian_localizacao_service.dart` | `test/features/guardian/guardian_passageiro_service_test.dart`, `guardian_presenca_service_test.dart`, `guardian_localizacao_service_test.dart`; tentativa futura de editar/remover ficha deve tratar `403 ACTION_UNAVAILABLE` como acao indisponivel, nao erro generico | `GuardianPassageiroAccessWebMvcTest`, `GuardianPassengerMutationPolicyTest`, `PresencaRbacWebMvcTest`, `LocalizacaoMotoristaControllerWebMvcTest` |
| WebSocket localizacao | `WS /ws-location`, `SEND /app/enviarLocalizacao`, `SUB /topic/localizacao/{companyId}` | `lib/services/websocket_service.dart` | `test/services/websocket_service_test.dart`, `test/services/location_sharing_use_case_test.dart` | `StompTenantChannelInterceptorTest`, `LocalizacaoSocketControllerTest` |
| WebSocket viagem | `WS /ws-location`, `SUB /topic/viagens/{companyId}`, `SUB /topic/viagens/{companyId}/{viagemId}` | `lib/features/tracking/data/trip_route_event_service.dart` | map-first widget tests; falta teste unitario direto do gateway se o protocolo mudar | `StompTenantChannelInterceptorTest`, `ViagemRealtimePublisherServiceTest`, lifecycle service tests |
| Push token | `POST /devices/fcm-token` | `lib/features/notifications/data/push_notification_registration_service.dart` | `test/features/notifications/push_notification_registration_service_test.dart`, binder tests | `DeviceTokenControllerWebMvcTest`, `DeviceTokenServiceTest` |

Contrato de `POST /company-admins/bootstrap`: o request exige `companyName`
novo, além dos dados do administrador. `companySlug` é derivado pelo backend e
permanece somente na resposta/sessão; o app e seus E2Es não o enviam como
seletor de tenant. Ausência de `companyName` ou payload legado slug-only recebe
`400`; empresa preexistente recebe `409`.

## Leitura funcional de localizacao (`VR-SEC-001-BE-READ`)

Contrato consumido por `TripLiveLocationService.fetchLatestLocation`:

- `200`: retorna `TripLiveLocationRecord`, com latitude/longitude obrigatorias e
  `updatedAt`/`freshness` aditivos quando presentes;
- `204`: retorna `null`, representando ausencia de localizacao atual sem erro;
- `404`: entra no mesmo ramo generico de qualquer resposta diferente de
  `200/204` e produz `TripLiveLocationException` com a mensagem publica do app,
  sem revelar se o alvo foi negado, cancelado, legado, cross-tenant ou
  inexistente.

O probe funcional usa o service de producao e um cliente HTTP real, sem
`MockClient`. A saida `attestation_scope=consumer_only` prova o parsing e o
tratamento observados pelo consumidor; `backend_binding=coordinator_required`
deixa explicito que somente o runner do workspace pode vincular esse probe ao
processo/JAR backend exato.

A lane coordenada deve usar somente HTTP loopback, worktrees limpas, refs e
blobs Git exatos e um manifest create-only cujo SHA-256 bruto seja ancorado fora
do diretorio de execucao. A fixture de bootstrap dessa lane envia
`companyName` novo por run; `companySlug` continua sendo apenas resposta
derivada e nunca seletor de tenant.

Prova preliminar de 2026-07-17: app
`7b5d4d4048db439d9b5179630089658776f150a4`, tree
`866ebe239fe8c324e6c09659c91940420c8ea90c`, run
`location-read-20260717T101750Z-79192`, manifest SHA-256
`f7ee6ed4b0f58afa5ce5d5461ff43c0c8cafeb4696146c70e1f12692090ea6b8`,
payload SHA-256
`809a10290f98c8393d902bb46b15527320079aabc6a323960f54f8a4a3f55fef` e
log SHA-256
`5d6fe0efe6f2bc66d168b043cdae40689e1be6998879e2fc3de7110958422439`
(`3655` bytes), com cleanup `PASS`.

Essa prova e preliminar porque esta atualizacao documental altera o snapshot do
app. O fechamento deve repetir a lane no SHA documental final e registrar o
manifest/hashes definitivos em
`program/evidence/VR-SEC-001-BE-READ.md`, no repositorio coordenador. Nao houve
prova de UI ou device, nem remocao do fallback visual; contratos historicos e
realtime permanecem como estao, e piloto/producao continuam em `HOLD`.

## Gaps rastreados

| Gap | Situacao | Proximo card |
| --- | --- | --- |
| Places admin sem WebMvc dedicado | O app tem gateway/teste e o backend tem `PlacesProxyServiceTest`, mas falta teste de controller para auth/status. | `PHASE-0405-GAP-001` |
| Provider push real ausente | O app registra token e o backend possui controller/outbox interno, mas Firebase/APNs, worker de retry e entrega real ainda nao foram ativados. | Cards futuros de provider push real |
| E2E real por papel parcialmente revalidado | `PHASE-0501` endureceu o preflight DEV, validou fixture operacional, bloqueou fixture Igrejinha insuficiente antes do Flutter e executou a suite guardian real contra DEV. A divisao completa por papel ainda fica fora deste documento. | `PHASE-0502` e `PHASE-0503` |

## Validacao rapida da matriz

Comandos usados nesta fase:

```bash
rg -n "/company-admins|/viagens|/auth|/login|/ws-location" \
  src/backend/backlog/API-Contract-Matrix.md \
  vanrides-app/docs/api-contract-consumption.md

rg -n "devices/fcm-token|company-admins/places|change-email" \
  src/backend/backlog/API-Contract-Matrix.md \
  vanrides-app/docs/api-contract-consumption.md
```
