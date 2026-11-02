# Lista de Débitos Técnicos Visíveis — 2026-04-22

Este documento consolida débitos técnicos observados por leitura de código após o fechamento do bloco de qualidade e refinamento (cards 0216-0227). Cada item inclui evidência objetiva, impacto, custo estimado e proposta de card de refinamento.

> **Critério de inclusão:** o débito deve ser visível por evidência (linha de código, métrica, regra de design quebrada). Impressões subjetivas ficam para `qa-manual/resultados/SUMARIO.md` após o smoke.

## D-01 — Telas operacionais maiores que 1.000 linhas

**Evidência:**
| Arquivo | Linhas |
|---|---|
| `lib/home_motorista_screen.dart` | 3.345 |
| `lib/features/admin/presentation/admin_rotas_screen.dart` | 2.030 |
| `lib/features/admin/presentation/admin_vinculos_screen.dart` | 1.926 |
| `lib/features/admin/presentation/admin_viagens_screen.dart` | 1.574 |
| `lib/features/admin/presentation/admin_passageiros_screen.dart` | 1.460 |
| `lib/home_passageiro_screen.dart` | 1.359 |
| `lib/features/admin/presentation/admin_responsaveis_screen.dart` | 1.279 |
| `lib/features/admin/presentation/admin_login_screen.dart` | 1.267 |
| `lib/features/admin/presentation/admin_motoristas_screen.dart` | 1.181 |
| `lib/features/passenger_onboarding/presentation/passenger_company_link_flow.dart` | 1.064 |
| `lib/features/guardian/presentation/guardian_localizacao_screen.dart` | 1.021 |

**Impacto:** edição de qualquer uma dessas telas exige rolar 1k+ linhas; busca por widget é lenta; mudança em estilo visual concentrado precisa percorrer várias seções inline; revisão de PR é sofrível.

**Proposta de card:**
- `D-01 Refactor: extrair sub-widgets de telas com 1k+ linhas` — começar pela `home_motorista_screen` (já tem presenters extraídos no card 0220; falta extrair `MotoristaStopsListView`, `MotoristaMapFullscreenView`, `MotoristaActionBar` em arquivos próprios). Critério: nenhum arquivo final > 600 linhas.

**Custo estimado:** alto, deve ser dividido em ondas (uma tela grande por card).

---

## D-02 — `home_motorista_screen` concentra 19 chamadas a `setState`

**Evidência:** `grep -c "setState((" lib/home_motorista_screen.dart` retorna 19.

**Impacto:** mesmo após a extração dos presenters (card 0220), a UI ainda gerencia muito estado local. Cada `setState` é uma oportunidade de rebuild desnecessário e dificulta diagnóstico de regressão.

**Proposta de card:**
- `D-02 Migrar mutações de estado da home_motorista_screen para presenters` — substituir `setState` por chamadas aos presenters existentes ou criar novos presenters específicos. Critério: ≤ 5 `setState` na home final.

**Custo estimado:** médio.

---

## D-03 — Telas legadas leem `SharedPreferences` diretamente em vez de usar `AppSessionStore`

**Status:** RESOLVIDO em 2026-04-23 pelo card 0228 (`cd4b04c`). Após a auditoria
real do código, o resíduo direto de `SharedPreferences.getInstance()` em `lib/`
ficou concentrado no `login_screen.dart`, no fluxo de "lembrar login". O card
0228 migrou esse último ponto para o `AppSessionStore`, e o `grep` final passou
`lib/app/session/app_session_store.dart` como único ponto restante no app shell.

**Evidência:** após a criação do `AppSessionStore` no card 0227, ele convive com chaves legadas (`email`, `senha`) porque o `login_screen` ainda faz `prefs.setString` direto além de chamar `writeCurrent`. Há 15 arquivos com 119 chamadas totais a `SharedPreferences.getInstance()`:

| Arquivo | Ocorrências |
|---|---|
| `lib/home_motorista_screen.dart` | 19 |
| `lib/features/admin/presentation/admin_responsaveis_screen.dart` | 16 |
| `lib/cadastro_passageiro_screen.dart` | 13 |
| `lib/features/admin/presentation/admin_viagens_screen.dart` | 11 |
| `lib/confirmar_presenca_screen.dart` | 10 |
| `lib/features/admin/presentation/admin_motoristas_screen.dart` | 10 |
| (outros 9 arquivos) | menos |

**Impacto:** o `AppSessionStore` é a fonte de verdade nova, mas as telas continuam lendo do mesmo lugar de antes. Isso multiplica o ponto de mudança quando algo na sessão precisa evoluir (ex: adicionar `companyId` reflexivo).

**Proposta de card:**
- `D-03 Migrar leituras de SharedPreferences para AppSessionStore` — em ondas, começando pelas telas de `lib/features/admin/`. Critério: 0 chamadas a `SharedPreferences.getInstance()` em screens (apenas em stores e em `services/auth_headers`).

**Custo estimado:** médio-alto (precisa testar cada tela após migração).

---

## D-04 — Estado global do app é manual; sem state management compartilhado

**Status:** DECIDIDO em 2026-06-13 pelo card 0231 (`0a2b2d7`).
ADR: `docs/adr/2026-06-13-state-management.md`.
Decisão: adotar `flutter_riverpod` de forma incremental, começando por um
piloto no fluxo do motorista (`home_motorista_screen.dart`). A migração ampla
fica em cards filhos, começando por `PHASE-0202`.

**Evidência:** o projeto não usa Riverpod, Bloc ou Provider. A mediação acontece via `StatefulWidget` + `ChangeNotifier` (presenters introduzidos no card 0220) e via `SharedPreferences` (estado de sessão, parcialmente coberto pelo `AppSessionStore`).

**Impacto:** a passagem de dados entre telas exige construtor explícito (visível em `DeepLinkNavigationHandler` que precisa carregar `email`, `senha`, `accessToken`, `motoristaId` para construir telas). Adicionar uma feature que reage a evento global (ex: refresh forçado de sessão) custa muito.

**Proposta de card:**
- `D-04 Avaliar introdução de state management compartilhado (Riverpod | Bloc)` — fase 1: spike comparando os dois com cenários reais (deep linking precisa de sessão, presenters precisam de gateway, multiplicação de SharedPreferences). Fase 2: introduzir gradualmente, começando pela sessão.

**Custo estimado:** alto. Deve ser planejado como bloco próprio, não como card único. Decisão arquitetural.

---

## D-05 — Roteamento manual via `Navigator.push`; sem `GoRouter` ou registry

**Evidência:** `lib/main.dart` usa `MaterialApp` simples com `home: ExperienceLaunchScreen()`. Cada navegação no app é um `Navigator.push(MaterialPageRoute(...))` direto. O `DeepLinkNavigationHandler` precisa conhecer cada construtor de tela alvo.

**Impacto:** deep linking, push notification roteado, e qualquer recurso que precise abrir tela por nome (não por construtor) custa muito. Toda evolução de roteamento bate em N arquivos.

**Proposta de card:**
- `D-05 Introduzir GoRouter e migrar navegação principal` — pode ser feito após D-04 (state management) para evitar duas refatorações grandes na mesma janela. Critério: deep links e pushes externos resolvem por nome de rota; nenhum `MaterialPageRoute` em código de navegação.

**Decisão 2026-06-13:** ADR publicada em
`docs/adr/2026-06-13-navigation-routing.md`. Direção aceita: adotar
`go_router` incrementalmente, começando por contrato de rotas e testes antes
de trocar `MaterialApp`/`Navigator`. D-05 ainda não está resolvido; a
implementação deve ser splitada em ondas menores.

**Custo estimado:** alto. Decisão arquitetural.

---

## D-06 — `cadastro_motorista_screen` e `cadastro_passageiro_screen` sem testes (débito explícito do card 0219)

**Status:** RESOLVIDO em 2026-06-18 pelos cards 0235/0236
(commit: `286f8fc`).

**Evidência:** o card 0219 deixou explícito que essas duas telas dependem de extração de gateway antes de virar testáveis. Hoje fazem REST direto sem injeção.

**Impacto:** os dois pontos de entrada de novos usuários (motorista e passageiro) não têm cobertura.

**Proposta de cards:**
- `D-06a Extrair MotoristaCadastroGateway de cadastro_motorista_screen` — modelar como gateway injetável + adicionar testes happy/erro/validação.
- `D-06b Extrair PassageiroCadastroGateway de cadastro_passageiro_screen` — mesmo padrão, mais complexo porque envolve `google_place` para autocomplete de endereço.

**Resultado entregue:** `cadastro_motorista_screen` e
`cadastro_passageiro_screen` passaram a usar gateways injetáveis para cadastro,
com resultados tipados de sucesso, validação e erro de rede. O cadastro de
passageiro também recebeu abstração testável para autocomplete/detalhes do
Google Places, permitindo testes offline dos cenários principais. A validação
incluiu widget tests focados, `flutter analyze`, `flutter test`, gate local e
smokes reais de cadastro no backend DEV.

**Custo estimado:** médio cada.

---

## D-07 — `AppStrings` cobre apenas portal admin; resto do app continua hardcoded

**Status:** RESOLVIDO (card 0229, commit: `c476fab`)

**Evidência:** `lib/l10n/app_strings.dart` contém constantes para mensagens de erro do portal admin (entregue no card 0222). Strings de motorista, passageiro, guardian, deep linking e erros de rede continuam hardcoded em diversos `Text(...)` e `SnackBar(...)`.

**Impacto:** internacionalização impossível sem nova etapa; variações ortográficas continuam possíveis nos arquivos não migrados.

**Proposta de card:**
- `D-07 Estender AppStrings para passageiro, motorista, guardian e mensagens de rede` — auditar e migrar; o trabalho mecânico pode ser quase todo automatizado. Critério: 0 string PT-BR de mensagem de erro/label fora de `AppStrings`.

**Custo estimado:** baixo-médio (mecânico).

**Resultado entregue:** `AppStrings` foi reorganizado por namespace (`admin`, `motorista`, `passageiro`, `guardian`, `network`, `deepLink`), com migração das features modernas e das telas legadas auditadas no card. A auditoria final do escopo do card ficou com 9 ocorrências residuais justificadas: labels numéricos dinâmicos, iniciais de avatar e sanitização de erro retornado pelo backend.

---

## D-08 — Push notifications: gap entre abstração pronta e ativação real

**Evidência:** card 0226 entregue em escopo Flutter, mas o `NoopPushNotificationGateway` continua como default. Pré-requisitos externos pendentes:

1. Projeto Firebase + `GoogleService-Info.plist` + `google-services.json`
2. Certificado APNs no Apple Developer
3. Endpoint `POST /devices/fcm-token` no `vanrides-backend`
4. Substituir injeção do `Noop` por `FirebaseMessagingPushNotificationGateway`

**Impacto:** o app não recebe nenhuma notificação push. Toda a infra Flutter está esperando.

**Proposta de cards:**
- `D-08a Backend: criar endpoint POST /devices/fcm-token` — armazena token por usuário/device.
- `D-08b Configurar Firebase project + APNs` — passo de infra, fora de código.
- `D-08c App: implementar FirebaseMessagingPushNotificationGateway e plugar no binder` — uma única substituição de injeção quando os 2 acima estiverem prontos.

**Custo estimado:** alto pelo conjunto (Firebase setup + APNs + endpoint + integração). O code change final é pequeno.

---

## D-09 — Deep linking: navegação para `ViagemDeepLink` com foco por `tripId`

**Status:** RESOLVIDO em 2026-05-26 pelo card 0237 (`2a50623`). As homes de motorista e passageiro aceitam `initialTripId`, o handler de deep link passa `target.tripId`, o responsável também recebe foco de viagem, e há fallback com snackbar quando a viagem do link não pertence à agenda do usuário.

**Evidência anterior:** TODOs explícitos em `lib/features/deep_linking/presentation/deep_link_navigation_handler.dart` linhas 207 e 222 indicavam que o link `vanrides://viagem/123` caía apenas na home, sem destacar a viagem 123.

**Impacto resolvido:** abrir o link `vanrides://viagem/123` quando logado como motorista ou passageiro agora abre a home operacional já focada na viagem quando ela pertence à agenda do dia; se não pertencer, o app avisa `Viagem não encontrada.` e permanece em uma viagem válida.

**Card entregue:** `0237 — home_motorista_screen e home_passageiro_screen aceitam focusTripId (deep link)`.

---

## D-10 — `setState` espalhado: 119 ocorrências em 15+ arquivos (visão consolidada)

**Evidência:** `grep -c "setState((" lib/**/*.dart` retorna 119 ocorrências. Concentração nas telas grandes do D-01.

**Impacto:** cada `setState` é uma oportunidade de bug de rebuild; o agregado torna refatoração lenta e a previsibilidade do comportamento baixa.

**Relação com outros débitos:** D-04 (state management) cobre a solução estrutural. D-02 cobre a maior tela específica.

**Custo estimado:** dependente de D-04.

---

## D-11 — Nenhuma tela legada (`lib/*.dart`) está em `lib/features/`

**Evidência:** `lib/home_motorista_screen.dart`, `lib/home_passageiro_screen.dart`, `lib/login_screen.dart`, `lib/cadastro_*.dart`, `lib/confirmar_presenca_screen.dart`, `lib/presencas_hoje_screen.dart`, `lib/ver_localizacao_screen.dart`, `lib/compartilhar_localizacao_screen.dart`, `lib/email_verification_pending_screen.dart` — todas no root de `lib/`, fora da estrutura `features/` adotada para o resto do projeto.

**Impacto:** convenção arquitetural quebrada; busca por feature exige conhecer dois lugares; importações inconsistentes.

**Proposta de card:**
- `D-11 Migrar screens de root/lib para features/<area>/presentation/` — em ondas, uma tela por card, mantendo backward-compat de imports até o último passo.

**Resultado entregue:** card `0238` concluído em `ae9392e`. As 11 telas
`*_screen.dart` que estavam no root de `lib/` foram movidas para
`lib/features/<area>/presentation/`, incluindo `escolha_cadastro_screen.dart`.
Os imports do app, testes e integration tests passaram a apontar direto para
`features/`. Validação final: `find lib -maxdepth 1 -type f -name '*screen.dart'`
retorna 0 arquivos; `flutter analyze` e `flutter test` passaram.

**Custo estimado:** baixo-médio (mecânico, mas tocando muitos imports).

---

## D-12 — `home_motorista_screen` reduzida só de 3.406 para 3.345 (-61 linhas) após card 0220

**Evidência:** o card 0220 extraiu dois presenters (sessão e localização) mas a redução de linhas foi pequena porque a árvore de widgets continua monolítica.

**Impacto:** o resultado do card é correto (lógica testável foi para fora), mas a percepção de "tela ainda gigante" continua.

**Proposta de card:**
- `D-12 Quebrar árvore de widgets da home_motorista_screen em arquivos próprios` — sub-widgets `MotoristaStopsListView`, `MotoristaMapPanelHeader`, `MotoristaActionBar`, `MotoristaPassengerListSection` (provisório, ajustar conforme análise). Critério: arquivo principal ≤ 600 linhas.

**Custo estimado:** alto, exige smoke manual extenso para confirmar zero regressão.

---

## D-13 — Estrutura de módulos mistura `lib/features/` e `lib/services/` sem critério claro

**Status:** RESOLVIDO em 2026-06-18 pelo card 0239
(commit: `f1b1594`).

**Evidência:** `lib/services/api_constants.dart`, `lib/services/auth_headers.dart`, `lib/services/jwt_claims.dart`, `lib/services/websocket_service.dart` convivem com `lib/features/<feature>/data/` que também tem gateways e clients.

**Impacto:** novo desenvolvedor (humano ou agente) precisa decidir caso a caso onde colocar um service novo. Resultado: inconsistência cresce.

**Proposta de card:**
- `D-13 Definir e documentar critério de quando algo vai para services vs feature/data` — passo 1 escrever a regra, passo 2 mover o que estiver no lugar errado. Critério: documentação em `lib/README.md` ou em `CLAUDE.md`/`AGENTS.md`.

**Resultado entregue:** a regra operacional foi publicada em `lib/README.md` e
formalizada na ADR `docs/adr/2026-06-18-feature-data-boundaries.md`. A auditoria
de `lib/services/*.dart` confirmou que os gateways de feature especifica estao
em `lib/features/<area>/data/`; `lib/services` fica reservado para infra
transversal, auth, transporte, plataforma e utilitarios globais.

**Custo estimado:** baixo (documentação) + baixo (movimentações pontuais).

---

## D-14 — Sem `lints` específicos do projeto além do default

**Status:** RESOLVIDO em 2026-06-18 pelo card 0240
(commit: `3b59571`).

**Evidência:** `analysis_options.yaml` provavelmente herda `flutter_lints` padrão (verificar). Não há regras customizadas para evitar `print`, `late` perigoso, `TODO` sem dono, etc.

**Impacto:** débitos novos podem entrar sem alarme. O card 0216 limpou o que existia, mas não ergueu uma barreira contra novos.

**Proposta de card:**
- `D-14 Endurecer analysis_options.yaml com regras customizadas` — bloquear `avoid_print`, exigir `library_private_types_in_public_api`, `prefer_const_constructors`, `unawaited_futures`, etc. Cuidado para não gerar onda nova de avisos sem plano de limpeza.

**Resultado entregue:** `analysis_options.yaml` agora ativa regras de higiene
específicas do projeto para prints, futures não aguardados, construtores/listas
const, APIs privadas expostas, uso assíncrono de `BuildContext` e trailing
commas. A limpeza inicial removeu os avisos gerados pelo novo conjunto, a regra
foi documentada em `docs/lint-rules.md`, e o ratchet foi rebaselined depois da
migração estrutural do card 0238.

**Custo estimado:** baixo (configuração) + médio (limpar avisos novos).

---

## D-15 — Endpoint `POST /devices/fcm-token` não existe no backend

**Evidência:** `lib/features/notifications/data/push_notification_registration_service.dart` chama `$baseUrl/devices/fcm-token`, mas grep no `vanrides-backend` por essa rota não encontra controller. O `try/catch + debugPrint` foi adicionado justamente porque o endpoint não existe ainda.

**Impacto:** parte do D-08, listado separado para deixar visível que é trabalho de backend (não app).

**Proposta de card:**
- `D-15 (backend): criar POST /devices/fcm-token` — recebe `{token, platform}` autenticado; persiste em tabela `device_tokens` com FK para `usuario`. Idempotente (upsert por (userId, token)).

**Custo estimado:** baixo-médio.

---

## D-16 — `motorista_session_presenter` e `motorista_location_presenter` sem documentação de invariantes

**Status:** RESOLVIDO (card 0241, commit: `24a835c`)

**Evidência:** os dois presenters criados no card 0220 não têm comentário explicando o que cada método garante (ex: `loadTrip` é seguro de chamar antes de qualquer outra operação? `connectRouteEvents` requer chamada prévia a `loadTrip`?).

**Impacto:** próximo desenvolvedor (humano ou agente) que for usar esses presenters precisa ler todo o `home_motorista_screen` para entender a ordem.

**Proposta de card:**
- `D-16 Documentar invariantes dos presenters em comentários curtos` — uma linha por método público explicando precondição/efeito. Custos baixíssimos, valor alto.

**Custo estimado:** baixo (1-2 horas).

---

## Visão consolidada

| Categoria | Cards propostos |
|---|---|
| Refatoração de telas grandes | D-01, D-02, D-12 |
| Padronização arquitetural | D-04, D-05, D-11, D-13 |
| Sessão e estado global | D-03 |
| Cobertura de testes | D-06a, D-06b |
| i18n / strings | D-07 |
| Push notifications (ativação) | D-08a, D-08b, D-08c, D-15 |
| Deep linking refinamento | D-09 |
| Higiene de código | D-14, D-16 |
| Métrica observacional | D-10 (resolve via D-04) |

## Sugestão de ordem para os próximos blocos

1. **Bloco "Sessão e Strings"** (D-03 + D-07): mecânico, baixo risco, libera trabalho subsequente.
2. **Bloco "Push real"** (D-15 + D-08a/b/c): destrava feature visível ao usuário; depende de Firebase + APNs (calendário externo).
3. **Bloco "State management"** (D-04 + D-05): decisão arquitetural grande, exige spike e plano antes de virar cards.
4. **Bloco "Refator de telas grandes"** (D-01, D-12, D-02): após state management, fica muito mais barato.
5. **Bloco "Cobertura de cadastro"** (D-06): depende de D-04 idealmente.
6. **Higiene contínua** (D-14, D-16, D-09, D-11, D-13): cards menores, podem ser intercalados.

## Critério de promoção a card no backlog

Um item desta lista vira card no `Flutter-Cards.md` quando:
1. Há decisão de pegar nesse ciclo;
2. O escopo está suficientemente claro (alguém consegue ler o card e implementar);
3. O critério de aceite é objetivo (linhas, testes, arquivos).

Itens que ainda exigem **decisão arquitetural** (D-04, D-05) devem virar **spike + ADR** antes de virar card de implementação.
