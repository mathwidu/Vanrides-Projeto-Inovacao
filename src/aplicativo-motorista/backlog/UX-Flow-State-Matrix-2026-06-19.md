# VanRides UX Flow State Matrix

Status: ACTIVE BASELINE
Date: 2026-06-19
Related plan: `UX-UI-Complete-Improvement-Plan-2026-06-19.md`
Related card: `UX-0001 - Matriz canonica de fluxos e estados`

## 1. Purpose

This matrix is the product contract for UX states in VanRides. It exists so
admin portal, passenger app, driver app, guardian app, backend errors and QA
all speak the same language.

Every state below must answer:
- what happened;
- what the user should do now;
- what the system is doing or protecting;
- which screen/widget owns the experience;
- which validation proves the state was handled.

If a future screen needs a state that is not here, the matrix must be updated
before implementing the UI.

## 2. Impact Analysis

### Context

The product already has several working screens and contracts, but many flows
still expose technical concepts such as context missing, link, manifest,
conflict, bad request, or unsupported role. The next UX/UI phase needs a single
source of truth before changing portal and mobile surfaces.

### Technical changes

- Create this state matrix in app backlog.
- Update QA execution log with the documentary validation.
- No runtime Flutter code changes.
- No backend contract changes.

### Affected contracts

None at runtime. This document maps existing concepts from:
- `AppCompanyLinkStatus`: `noCompany`, `pendingApproval`, `companyLinked`.
- backend `ViagemStatus`: `PLANNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELED`.
- backend `PresencaServicoStatus`: `UNKNOWN`, `CONFIRMED`, `ABSENT`,
  `CANCELED_BY_COMPANY`.
- backend `SupportTicketStatus`: `OPEN`, `IN_PROGRESS`, `RESOLVED`.
- invite/request statuses such as `PENDING`, `APPROVED`, `REJECTED`,
  `CANCELLED`, `ACTIVE`, `EXPIRED`, `USED`, `USED_UP`.

### Risks and mitigation

- Risk: this matrix becomes stale.
  - Mitigation: each future card must reference the state it changes.
- Risk: copy diverges between portal and mobile.
  - Mitigation: copy here is canonical intent; implementation may adapt length,
    but not meaning.
- Risk: unsupported states remain hidden behind generic support screens.
  - Mitigation: every fallback must name the missing condition and next action.

### Minimum validation

- Search current exposed technical states:
  - `rg -n "UnsupportedRoleScreen|sem contexto|Conflict|Bad Request|mocado|mock" lib`
- Review matrix against `ExperienceHomeRouter`.
- Keep this file ASCII and placeholder-free.

### Simple rollback

Revert this document and the QA log entry. No runtime rollback is needed.

## 3. Global UX Rules

1. A screen has one primary question.
2. A blocked state must explain why it is blocked.
3. A blocked state must offer one safe next action.
4. Technical words are not first-class user copy.
5. Empty states are not blank states; they guide setup.
6. Errors are recoverable whenever possible.
7. Support is a fallback, not the main CTA, unless the user truly cannot fix
   the state.
8. A state shown in mobile should have an equivalent admin meaning, and vice
   versa.
9. The app should never require the user to infer that scrolling, swiping, or
   deleting/recreating is necessary.
10. No essential status may be truncated in a primary CTA, sheet header, tab,
    list row, or warning banner.

## 4. Canonical Product Vocabulary

| Technical term | User-facing term | Notes |
| --- | --- | --- |
| Vinculo | Acesso / Empresa / Equipe | Pick based on surface. |
| Manifesto | Lista da viagem / Paradas | Use "manifesto" only in developer docs. |
| Viagem recorrente | Servico recorrente | Trip is the instance of the day. |
| Conflict | Ja existe uma viagem para este dia | Explain action. |
| Bad Request | Nao conseguimos salvar | Include field-level reason. |
| Sem contexto | Conta sem empresa ativa | Explain missing company/session. |
| Unsupported role | Perfil nao liberado | Explain role received. |
| Driver membership | Empresa do motorista | Use in app copy. |
| Company link request | Solicitacao de acesso | Use for manual user request. |
| Invite code/link | Convite da empresa | Use for company-originated flow. |
| Ticket | Atendimento | Ticket can remain in technical docs. |

## 5. Account And Identity States

### 5.1 Common account states

| State | Trigger/source | Copy intent | Primary CTA | Secondary CTA | Owner surface | Validation |
| --- | --- | --- | --- | --- | --- | --- |
| Email pending | Login or bootstrap returns unverified account | "Confirme seu e-mail para liberar sua conta." | Reenviar verificacao | Alterar e-mail | Login, admin login, onboarding pending | Widget test for pending e-mail screen; smoke for resend. |
| Email verified | Auth response/profile says verified | "Conta verificada." | Continue | Open account | All shells | Profile/status chip renders verified without blocking flow. |
| Missing required profile fields | `account/me` lacks name, phone, birth date or photo when required by role | "Complete seu perfil para deixar a operacao segura." | Completar perfil | Agora nao, quando non-blocking | Account screen and first-run nudge | Account profile tests by role. |
| Profile complete | Required fields present | "Perfil pronto." | Editar perfil | Ver dados da conta | Account screen | Completion card hidden. |
| Unknown role | Role does not map to supported destination | "Perfil nao liberado para este app." | Falar com suporte | Sair da conta | `UnsupportedRoleScreen` | Router test for unsupported role. |
| Missing token context | Role is valid but token lacks company/user context | "Sua conta esta sem empresa ativa nesta sessao." | Entrar novamente | Falar com empresa | `ExperienceHomeRouter`, role shell fallback | Router test for admin/guardian missing company. |
| Session expired | 401/refresh failure | "Sua sessao expirou." | Entrar novamente | Reportar problema | Login boundary/interceptors | Service tests for unauthorized handling. |

### 5.2 Role-specific profile requirements

| Role | Minimum now | Recommended editable fields | Future fields not blocking this phase |
| --- | --- | --- | --- |
| Passenger | Name, e-mail, phone, birth date, profile photo, company link, stop/address context | Photo, phone, birth date, display name, preferred contact, stop confirmation | Payment status, chat settings, frequent addresses beyond current stop. |
| Driver | Name, e-mail, phone, birth date, profile photo, company link | Photo, phone, display name, professional contact | Driver documents, license, professional photo approval, vehicle assignment history. |
| Guardian | Name, e-mail, phone, birth date, profile photo, linked passengers | Photo, phone, notification preferences | Minor consent documents, family relationship proof. |
| Company admin | Name, e-mail, phone, birth date, profile photo, company context | Photo, phone, admin display name, company contact | Billing owner, permission groups. |

## 6. Company Access States

### 6.1 Shared company access model

| State | Backend/source | User copy | Primary CTA | Secondary CTA | Owner surface | Validation |
| --- | --- | --- | --- | --- | --- | --- |
| No company | `NO_COMPANY` / `AppCompanyLinkStatus.noCompany` | "Escolha sua empresa para continuar." | Buscar empresa | Usar convite | Passenger/driver company gate | Onboarding flow tests. |
| Pending approval | request `PENDING` / `pendingApproval` | "Sua solicitacao foi enviada para a empresa." | Ver status | Cancelar solicitacao, when supported | Passenger/driver pending screen, admin requests | Pending screen test and portal approval test. |
| Linked | `COMPANY_LINKED` / `companyLinked` | "Voce esta vinculado a {empresa}." | Ir para operacao | Ver perfil | Home shell | Router sends to role home. |
| Rejected | request `REJECTED` | "A empresa recusou esta solicitacao." | Solicitar outra empresa | Falar com empresa | Onboarding status screen | Backend/app tests when exposed. |
| Suspended | future membership status | "Seu acesso esta pausado pela empresa." | Falar com empresa | Sair da conta | Blocking role screen | Future contract and tests. |
| Invite active | invite `ACTIVE` | "Convite encontrado para {empresa}." | Criar/entrar na conta | Escolher outra empresa | Deep link onboarding | Deep link tests. |
| Invite expired | invite `EXPIRED` | "Este convite expirou." | Pedir novo convite | Buscar empresa | Deep link onboarding | Invite preview tests. |
| Invite cancelled | invite `CANCELLED` | "Este convite foi cancelado pela empresa." | Pedir novo convite | Buscar empresa | Deep link onboarding | Invite preview tests. |
| Invite used | invite `USED` / `USED_UP` | "Este convite ja foi usado." | Entrar na conta | Pedir novo convite | Deep link onboarding | Invite preview tests. |

### 6.2 Passenger company flow

| State | Copy intent | Primary CTA | Secondary CTA | Owner |
| --- | --- | --- | --- | --- |
| First entry, no company | Explain that VanRides works through a company route. | Buscar empresa | Tenho convite | `PassengerCompanyGateScreen` |
| Manual request sent | Explain company must approve. | Ver status | Buscar outra empresa | `PassengerCompanyPendingScreen` |
| Company invite accepted | Explain account will be linked after e-mail/profile steps. | Continuar cadastro | Entrar se ja tenho conta | Passenger onboarding link flow |
| Linked but no trip today | Explain no active service/trip for today. | Ver minha empresa | Atualizar | Passenger trip empty state |
| Linked with service but no presence | Explain today needs a decision. | Confirmar presenca | Nao vou hoje | Presence screen / trip alert |

### 6.3 Driver company flow

| State | Copy intent | Primary CTA | Secondary CTA | Owner |
| --- | --- | --- | --- | --- |
| Opened app without company | Driver may request company access or use company link. | Buscar empresa | Tenho convite | Driver onboarding gate |
| Opened company link before account | Create account, confirm e-mail, then link automatically. | Criar conta | Entrar | Deep link handler + driver onboarding |
| Opened company link with existing account | Authorize/link directly to company after login/session. | Entrar e vincular | Cancelar | Deep link handler |
| Manual request pending | Company needs to approve. | Ver status | Cancelar solicitacao, when supported | `DriverCompanyPendingScreen` |
| Linked, no assigned trip | Driver is inside company but has nothing to drive now. | Atualizar agenda | Ver conta | Driver home empty |
| Linked, trip ready | Driver has next operation. | Abrir viagem | Ver lista de paradas | Driver home trip tab |

## 7. Operational Trip States

### 7.1 Trip lifecycle

| State | Backend value | Copy intent | Primary CTA | Secondary CTA | Owner surface | Validation |
| --- | --- | --- | --- | --- | --- | --- |
| No service configured | No recurring service for day/company | "Configure um servico recorrente antes de montar viagens." | Criar servico | Ver rotas | Admin dashboard/services | Admin empty-state tests. |
| No trip for date | No `Viagem` instance for selected date | "O dia ainda nao foi montado." | Montar viagens do dia | Criar viagem extra | Admin viagens | Admin trips tests. |
| Planned | `PLANNED` | "Viagem preparada, aguardando inicio." | Revisar lista | Iniciar, when driver/admin context permits | Admin trips, driver home, passenger tracking | Trip status widget tests. |
| Waiting start | `PLANNED` but current time near service | "Van ainda nao iniciou." | Ver minha parada | Atualizar | Passenger/guardian tracking | Visual smoke for passenger/guardian. |
| In progress | `IN_PROGRESS` | "Van em deslocamento." | Ver van | Minha parada/proxima parada | Passenger, guardian, driver | Tracking tests and map smoke. |
| Paused signal | Location stale or no driver heartbeat | "Sinal pausado. Ultima posicao conhecida." | Atualizar | Ver detalhes | Tracking sheet/status | Tracking stale-state test. |
| Completed | `COMPLETED` | "Viagem concluida." | Ver resumo | Ver proxima viagem | Admin, driver, passenger history | Trip lifecycle tests. |
| Canceled | `CANCELED` | "Viagem cancelada." | Ver detalhes | Ver suporte | Admin/passenger/driver | Trip status tests. |
| Conflict existing trip | Backend 409 or local duplicate detection | "Ja existe uma viagem para este servico e data." | Abrir viagem existente | Escolher outra data | Admin create manual | Admin create conflict test. |

### 7.2 Admin trip maintenance states

| State | User question | UI pattern | Primary CTA | Secondary CTA | Owner |
| --- | --- | --- | --- | --- | --- |
| Day healthy | "Esta tudo pronto para hoje?" | Single status panel + next action | Revisar viagens | Atualizar dia | Admin dashboard/viagens |
| Presence pending | "Quem ainda precisa decidir?" | Compact alert with count and list drilldown | Cobrar presencas | Ver passageiros | Admin dashboard/viagens |
| Passenger added after generation | "Como coloco no dia sem apagar tudo?" | Maintenance banner on service/passenger detail | Sincronizar lista | Abrir viagem | Admin passenger/service/trips |
| Driver unavailable | "Quem dirige esta viagem?" | Driver readiness card | Trocar motorista | Ver motoristas | Admin trips detail |
| Route incomplete | "A rota esta pronta?" | Setup checklist | Completar rota | Ver servico | Admin dashboard/services |
| Manual exception | "Preciso criar uma viagem fora da recorrencia." | Explicit exception flow | Criar viagem extra | Cancelar | Admin create manual |

## 8. Presence States

| State | Backend value | User copy | Primary CTA | Secondary CTA | Owner surface | Validation |
| --- | --- | --- | --- | --- | --- | --- |
| Pending decision | `UNKNOWN` | "Confirme se voce vai neste horario." | Vou neste horario | Nao vou | Passenger presence | Presence widget tests. |
| Confirmed | `CONFIRMED` | "Presenca confirmada." | Alterar decisao | Ver viagem | Passenger presence/tracking | Presence service tests. |
| Absent | `ABSENT` | "Voce marcou que nao vai." | Alterar para vou | Ver detalhes | Passenger presence | Presence widget tests. |
| Cancelled by company | `CANCELED_BY_COMPANY` | "Empresa removeu este horario da sua agenda." | Ver detalhes | Falar com empresa | Passenger/admin presence | Backend/app status handling. |
| Expired window | Future policy/time window | "Prazo de confirmacao encerrado." | Ver viagem | Falar com empresa | Passenger presence | Future test. |
| Locked by active trip | Trip already in progress/completed | "Esta viagem ja esta em andamento." | Ver van | Ver detalhes | Passenger presence/tracking | Trip-presence integration. |

## 9. Tracking And Map States

| State | User copy | Primary CTA | Secondary CTA | Owner surface | Validation |
| --- | --- | --- | --- | --- | --- |
| Map loading | "Carregando mapa." | None | None | Map shell | Widget/golden smoke should not show blank. |
| Map unavailable | "Nao conseguimos carregar o mapa." | Tentar novamente | Ver detalhes da viagem | Map shell | Map fallback test. |
| Driver location unknown | "Ainda nao recebemos a localizacao da van." | Atualizar | Ver minha parada | Passenger/guardian tracking | Tracking no-location test. |
| Driver location stale | "Ultima posicao conhecida." | Atualizar | Ver detalhes | Passenger/guardian tracking | Stale signal test. |
| Passenger stop visible | "Minha parada." | Centralizar em mim | Ver detalhes | Passenger tracking | Visual smoke. |
| Van visible | "Van." | Centralizar na van | Minha parada | Passenger/guardian tracking | Visual smoke. |
| Route visible | "Rota do dia." | Ver paradas | Recentralizar | Passenger/guardian/driver map | Visual smoke. |
| Sheet mini | Minimal status only | Expandir | Center action buttons | Passenger/guardian tracking | Gesture/integration test. |
| Sheet medium | Status + current stop + key actions | Expandir/Recolher | Details | Passenger/guardian tracking | Detent test. |
| Sheet expanded | Full detail list and actions | Recolher | Details/support | Passenger/guardian tracking | Detent test. |

## 10. Driver Operational States

| State | User question | Copy intent | Primary CTA | Secondary CTA | Owner |
| --- | --- | --- | --- | --- | --- |
| No company | "Onde trabalho no app?" | Join/request company. | Buscar empresa | Tenho convite | Driver onboarding |
| Email pending | "Por que nao vejo viagens?" | Confirm e-mail first. | Reenviar e-mail | Alterar e-mail | Driver pending/auth |
| Linked but empty | "Tenho algo para dirigir?" | No trip assigned now. | Atualizar agenda | Ver conta | Driver home |
| Planned trip | "Qual viagem inicio?" | Next trip ready. | Iniciar viagem | Ver paradas | Driver home/trip |
| In progress | "Qual a proxima parada?" | Current navigation step. | Cheguei na parada | Reportar problema | Driver trip |
| Stop completed | "O que falta?" | Stop marked complete and next stop shown. | Proxima parada | Ver lista | Driver trip |
| Trip completed | "Terminei?" | Trip complete summary. | Encerrar | Ver resumo | Driver trip |
| Location permission missing | "Como compartilho localizacao?" | Need permission to share live location. | Ativar localizacao | Continuar sem mapa, if allowed | Driver home/trip |

## 11. Guardian States

| State | User question | Copy intent | Primary CTA | Secondary CTA | Owner |
| --- | --- | --- | --- | --- | --- |
| No linked passenger | "Quem acompanho?" | Ask company/passenger to link. | Solicitar acesso | Ver conta | Guardian home |
| Passenger has no trip | "Tem viagem hoje?" | No trip available today. | Atualizar | Ver presencas | Guardian home |
| Trip waiting start | "A van ja saiu?" | Van not started. | Ver parada | Atualizar | Guardian localization |
| Trip in progress | "A pessoa esta segura?" | Show van, passenger stop, status. | Ver van | Ver parada | Guardian map |
| Signal paused | "A localizacao esta atualizada?" | Last known position. | Atualizar | Ver suporte | Guardian map |
| Presence needs decision | "Preciso confirmar algo?" | Guardian can review linked passenger presence if allowed. | Abrir presencas | Ver viagem | Guardian presence |

## 12. Support/Ticket States

| State | Backend value | User copy | Primary CTA | Secondary CTA | Owner surface | Validation |
| --- | --- | --- | --- | --- | --- | --- |
| No ticket | None | "Nenhum atendimento aberto." | Abrir atendimento | Voltar | Account/support | Support empty test. |
| Open | `OPEN` | "Recebemos sua mensagem." | Ver atendimento | Adicionar mensagem | Support mobile/admin | Support service tests. |
| In progress | `IN_PROGRESS` | "Empresa esta analisando." | Ver resposta | Enviar complemento | Support mobile/admin | Support status tests. |
| Resolved | `RESOLVED` | "Atendimento resolvido." | Ver resumo | Reabrir, if supported | Support mobile/admin | Support status tests. |
| Admin queue empty | No company tickets | "Sem atendimentos pendentes." | Atualizar | Voltar ao painel | Admin support | Admin support empty test. |
| Admin needs answer | Ticket open/in progress | "Usuario aguarda retorno." | Responder | Marcar resolvido | Admin support | Admin support flow test. |

## 13. Admin Portal Entity States

### 13.1 Passenger

| State | UI meaning | Primary CTA | Secondary CTA |
| --- | --- | --- | --- |
| Ready | Passenger has profile, company, service/stop. | Ver ficha | Editar |
| Missing profile | Required profile data absent. | Completar dados | Enviar convite |
| Missing service | Passenger is not in a recurring service. | Adicionar a servico | Ver servicos |
| Missing stop/address | Passenger cannot be routed. | Definir parada | Editar endereco |
| Presence pending today | Passenger has a decision pending. | Ver presenca | Abrir viagem |
| Minor without guardian | Birth date indicates minor and no guardian linked. | Vincular responsavel | Editar passageiro |

### 13.2 Driver

| State | UI meaning | Primary CTA | Secondary CTA |
| --- | --- | --- | --- |
| Ready | Driver linked and profile usable. | Ver ficha | Atribuir viagem |
| Invited | Company link sent/created. | Copiar convite | Cancelar convite |
| Request pending | Driver requested access. | Aprovar | Recusar |
| Missing profile | Driver needs profile completion. | Enviar orientacao | Editar |
| No trips | Driver has no assigned trip today. | Atribuir viagem | Ver viagens |
| Busy/in trip | Driver has active trip. | Ver viagem | Trocar motorista, when allowed |

### 13.3 Guardian

| State | UI meaning | Primary CTA | Secondary CTA |
| --- | --- | --- | --- |
| Ready | Guardian has profile and linked passengers. | Ver ficha | Editar |
| No passenger | Guardian exists but follows nobody. | Vincular passageiro | Convidar passageiro |
| Minor coverage needed | Passenger minor has no guardian. | Vincular responsavel | Ver passageiro |
| Access issue | Guardian has token/company mismatch. | Revisar acesso | Falar com suporte |

### 13.4 Service and route

| State | UI meaning | Primary CTA | Secondary CTA |
| --- | --- | --- | --- |
| Route missing | Service cannot generate trip. | Definir rota | Ver rotas |
| No passengers | Service has no stops. | Adicionar passageiros | Ver passageiros |
| Ready for generation | Service can generate day trips. | Montar dia | Revisar |
| Generated today | There is already a trip for today. | Abrir viagem | Sincronizar lista |
| Needs maintenance | New passenger/service change affects future/current trip. | Sincronizar lista | Ver impacto |

## 14. Error And Empty-State Rules

| Raw condition | Bad copy | Required copy pattern | Required action |
| --- | --- | --- | --- |
| HTTP 400 | Bad Request | "Nao conseguimos salvar. Revise {field/reason}." | Focus first invalid field. |
| HTTP 401 | Unauthorized | "Sua sessao expirou." | Login. |
| HTTP 403 | Forbidden | "Sua conta nao tem permissao para esta acao." | Explain owner/admin. |
| HTTP 404 | Not found | "Nao encontramos este registro." | Return to list / refresh. |
| HTTP 409 | Conflict | "Ja existe {entity} para {date/context}." | Open existing / change input. |
| Network | Failed to fetch | "Sem conexao com o servidor." | Try again. |
| Empty list | Nothing here | State-specific setup copy. | Create/request/refresh. |
| Unsupported role | Role label only | Explain the role received and what is missing. | Support/logout. |

## 15. QA Requirements By State Family

| State family | Required automated evidence | Required visual/manual evidence |
| --- | --- | --- |
| Account/profile | Flutter widget tests for all roles; service tests for photo/profile. | Profile screenshots for passenger, driver, guardian, admin. |
| Company access | Router/onboarding tests; service tests for request/invite. | Link/request screenshots and admin approval flow. |
| Trips/admin operations | Admin portal widget tests; Playwright smoke with real DEV where possible. | Dashboard/trips/services screenshots. |
| Presence | Widget/service tests for UNKNOWN/CONFIRMED/ABSENT/CANCELED. | Passenger presence screenshots. |
| Tracking/map | Widget/integration tests for detents and map fallback. | Passenger/guardian/driver screenshots, no text truncation. |
| Support/tickets | Service/widget tests for OPEN/IN_PROGRESS/RESOLVED. | Mobile/account and admin support screenshots. |
| Errors | Service and UI tests for 400/401/403/404/409. | At least one screenshot of conflict/error recovery in admin. |

## 16. Current Gaps Found During Baseline Search

Command:

```bash
rg -n "UnsupportedRoleScreen|sem contexto|Conflict|Bad Request|mocado|mock" lib
```

Findings from 2026-06-19 baseline:
- `ExperienceHomeRouter` still routes admin/guardian missing context to
  `UnsupportedRoleScreen` with technical role labels.
- `unsupported_role_screen.dart` is visually polished but still receives raw
  role/context labels instead of structured product state.
- `admin_viagens_screen.dart` contains create-conflict handling that must be
  converted into a friendly "open existing trip or change date" state.
- `admin_portal_auth_service.dart` has an auth error for missing company
  context; next portal cards should ensure it is rendered as a recovery path.
- No `mocado`/`mock` runtime hits were found in `lib` in this baseline search.

These are not regressions introduced by this card. They become explicit input
for UX-0102, UX-0501, UX-0603 and UX-1001.

## 17. Implementation Rule For Future Cards

Every future UX card must include a line in its QA evidence:

```text
State matrix coverage: <state ids/families covered>
```

If a card intentionally adds a new state, it must edit this file in the same
commit or in a preceding documentation commit.
