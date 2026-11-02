# ADR: State Management Compartilhado

Data: 2026-06-13

Status: Aceita para piloto

## Contexto

O app VanRides ainda usa `StatefulWidget` + `setState` como mecanismo
principal de estado. O ratchet atual mede 320 ocorrencias de `setState(` em
`lib/`, com concentracao em telas operacionais e administrativas.

Leitura feita para esta decisao:
- `lib/home_motorista_screen.dart`: 856 linhas, 20 `setState(`, ja usa
  `MotoristaSessionPresenter` e `MotoristaLocationPresenter` baseados em
  `ChangeNotifier`.
- `lib/home_passageiro_screen.dart`: 855 linhas, 12 `setState(`, concentra
  carregamento de viagens, presencas, selecao e conexao de eventos de rota.
- `lib/features/guardian/presentation/guardian_localizacao_screen.dart`: 699
  linhas, 13 `setState(`, concentra passageiro selecionado, viagem atual,
  historico, WebSocket e erros de rastreio.
- `lib/features/admin/presentation/admin_list_state_mixin.dart`: 73 linhas, 7
  `setState(`, oferece um padrao reutilizavel para listas, mas ainda acopla
  loading/erro/itens ao `State`.
- `lib/features/deep_linking/presentation/deep_link_navigation_handler.dart`:
  ainda precisa montar telas passando sessao por construtor.
- `lib/app/session/app_session_store.dart`: centraliza persistencia em
  `SharedPreferences`, mas nao oferece estado reativo compartilhado.

Hoje o projeto nao tem Riverpod, Bloc ou Provider no `pubspec.yaml`.

## Decisao

Adotar `flutter_riverpod` como biblioteca de estado compartilhado do app.

O uso inicial sera incremental:
- criar um `ProviderScope` no app shell;
- expor `AppSessionStore`, usuario de sessao atual e gateways por providers;
- manter os presenters `ChangeNotifier` existentes no curto prazo, expondo-os
  por providers quando isso reduzir passagem manual de dependencias;
- preferir `Notifier` ou `AsyncNotifier` para novo estado de fluxo;
- manter estados imutaveis pequenos para loading, erro, selecao e dados
  carregados;
- injetar gateways por providers para simplificar widget tests com overrides.

## Por Que Riverpod

Riverpod e a escolha mais compativel com o estado atual do repo porque:
- permite migracao por ilha, sem trocar todos os `StatefulWidget` de uma vez;
- funciona bem com os presenters `ChangeNotifier` ja extraidos;
- remove a dependencia de `BuildContext` para ler estado compartilhado;
- melhora testes por meio de provider overrides, sem criar muita estrutura de
  eventos artificiais;
- combina com o problema real do app: sessao, gateways, selecao de viagem e
  estados async compartilhados entre telas.

## Alternativas Consideradas

### Bloc

Nao escolhido para o piloto.

Bloc e forte quando o dominio precisa de eventos explicitos, historico de
transicoes e regras complexas de orquestracao. No VanRides atual, a maior dor
e reduzir passagem manual de sessao/gateways e quebrar `setState` em telas
grandes. Adotar Bloc agora adicionaria eventos, estados e arquivos em um stack
que ainda esta sendo fatiado. O custo de migracao seria maior que o beneficio
no primeiro piloto.

### Continuar Apenas Com ChangeNotifier

Nao escolhido como solucao estrutural.

`ChangeNotifier` ja ajuda nos presenters do motorista, mas nao resolve sozinho
estado global, overrides de dependencia, leitura fora da arvore de widgets e
coordenacao entre deep links, sessao e telas. Ele continua permitido como
ponte durante a migracao.

## Primeiro Piloto

O primeiro piloto de implementacao sera o fluxo do motorista em
`lib/home_motorista_screen.dart`.

Escopo recomendado para `PHASE-0202`:
- adicionar `flutter_riverpod`;
- criar providers para `DriverTripGateway`, `TripRouteEventGateway`,
  `LocationGateway`, `WebSocketServiceBase` e presenters do motorista;
- mover o estado de loading/erro/viagem selecionada para uma fronteira
  consumida por Riverpod;
- manter UI, textos, contratos de API e navegacao sem alteracao funcional;
- reduzir `setState` da tela apenas nos pontos cobertos pelo piloto;
- cobrir com testes existentes de motorista e com novo teste focado quando
  uma fronteira nova for criada.

## Fora De Escopo Do Piloto

Nao entram no primeiro piloto:
- migrar todo o app para Riverpod;
- trocar navegacao para GoRouter;
- mexer no contrato de deep links;
- migrar `home_passageiro_screen.dart`;
- migrar `guardian_localizacao_screen.dart`;
- migrar portal admin inteiro;
- remover todos os `setState` do app;
- alterar persistencia fisica de `AppSessionStore`;
- alterar endpoints, payloads ou status codes.

## Consequencias

Positivas:
- estado compartilhado passa a ter um padrao unico;
- testes podem substituir stores/gateways sem montar telas inteiras;
- proximos cards podem reduzir `setState` sem criar inventario paralelo;
- deep links e sessao ganham uma trilha clara para deixar de passar tudo por
  construtor.

Custos:
- adiciona uma dependencia nova;
- exige convencao de providers para evitar espalhar estado global sem dono;
- cards de migracao precisam ser pequenos para nao criar churn de arquitetura.

## Regras De Uso

- Providers devem ficar perto do dominio que representam.
- Estado global de app fica em `lib/app/`.
- Estado de feature fica em `lib/features/<feature>/`.
- Gateways continuam atras de interfaces ja existentes.
- Widgets nao devem criar gateways diretamente quando existir provider.
- `setState` continua permitido para estado puramente local e efemero de UI
  ate existir card de migracao.
- Cada nova migracao deve registrar impacto, testes e rollback no card.

## Validacao

Como esta ADR nao muda codigo executavel, a validacao minima e:
- `flutter analyze --no-fatal-infos --no-fatal-warnings`

Cards de implementacao derivados devem rodar:
- `flutter analyze --no-fatal-infos --no-fatal-warnings`
- `flutter test`
- `RUN_E2E=0 ./scripts/qa_gate_card.sh` quando fecharem card de qualidade
