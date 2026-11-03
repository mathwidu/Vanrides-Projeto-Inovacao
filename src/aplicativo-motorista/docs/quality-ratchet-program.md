# VanRides App Quality Ratchet Program

Data: 2026-06-06

## Objetivo

Criar uma etapa de qualidade incremental para o app Flutter em que cada card preserve o comportamento atual e melhore, ou pelo menos nao piore, a legibilidade, testabilidade, organizacao e previsibilidade do codigo.

O programa nao e uma refatoracao grande inicial. Primeiro ele mede a base atual, registra limites objetivos e adiciona um quality gate que atua como ratchet: divida existente fica visivel, mas divida nova deve ser bloqueada.

## Principios

1. Clean as you code: codigo novo ou alterado deve obedecer a um padrao mais alto que a base historica.
2. Baseline versionado: metricas atuais entram como contrato temporario; qualquer aumento precisa ser deliberado e justificado.
3. Mudanca pequena: refatoracoes grandes viram cards independentes, com testes antes e smoke depois.
4. Agent-friendly: docs e gates devem responder rapidamente onde mexer, como validar e qual limite nao pode piorar.
5. Sem quebrar contrato: qualquer mudanca que dependa do backend continua seguindo Impact Analysis + smoke ponta a ponta.

## Baseline inicial

Medido localmente em 2026-06-06.

| Metrica | Valor atual |
|---|---:|
| `flutter analyze --no-fatal-infos --no-fatal-warnings` | PASS |
| `flutter test` | PASS, 248 testes |
| Linhas Dart em `lib/`, `test/`, `integration_test/` | 58.306 |
| Maior arquivo Dart | `lib/home_motorista_screen.dart`, 3.655 linhas |
| Arquivos Dart em `lib/` | 140 |
| Telas `*_screen.dart` no root de `lib/` | 11 |
| Ocorrencias de `setState(` em `lib/` | 332 |
| Ocorrencias de `debugPrint`/`print` em app e testes | 32 |
| `ignore_for_file` ou `// ignore:` | 4 |
| `SharedPreferences.getInstance()` em `lib/` | 5, concentradas em `AppSessionStore` |

Hotspots iniciais medidos no baseline:

- `lib/home_motorista_screen.dart` — 3.655 linhas.
- `lib/features/admin/presentation/admin_viagens_screen.dart` — 3.011 linhas.
- `lib/features/admin/presentation/admin_vinculos_screen.dart` — 2.382 linhas.
- `lib/features/admin/presentation/admin_servicos_recorrentes_screen.dart` — 2.204 linhas.
- `lib/features/admin/presentation/admin_rotas_screen.dart` — 2.095 linhas.
- `lib/home_passageiro_screen.dart` — 1.716 linhas.

## Estado atual do ratchet

Atualizado em 2026-06-13 apos o card `PHASE-0307`.

| Metrica | Valor atual |
|---|---:|
| Linhas Dart em `lib/`, `test/`, `integration_test/` | 65.782 |
| Arquivos Dart em `lib/`, `test`, `integration_test` | 329 |
| Maior arquivo Dart | `test/features/admin/admin_viagens_screen_test.dart`, 870 linhas |
| `test/features/admin/admin_viagens_screen_test.dart` | 870 linhas |
| `home_motorista_screen.dart` | 850 linhas |
| `cadastro_passageiro_screen.dart` | 804 linhas |
| `admin_portal_layout.dart` | 788 linhas |
| `admin_login_screen.dart` | 765 linhas |
| `email_verification_pending_screen.dart` | 754 linhas |
| `admin_dashboard_screen.dart` | 737 linhas |
| `guardian_localizacao_screen.dart` | 699 linhas |
| `home_passageiro_screen_test.dart` | 691 linhas |
| `admin_passageiros_screen.dart` | 685 linhas |
| `admin_responsaveis_screen.dart` | 678 linhas |
| Telas `*_screen.dart` no root de `lib/` | 11 |
| Ocorrencias de `setState(` em `lib/` | 300 |
| Ocorrencias de `debugPrint`/`print` em app e testes | 17 |
| `ignore_for_file` ou `// ignore:` | 2 |
| Violacoes diretas de `SharedPreferences.getInstance()` | 0 |

Arquivos de widgets criados para manter a home do motorista sem novo
monolito:

- `driver_home_base_widgets.dart` — 402 linhas, incluindo o estado
  loading/erro/sem viagem extraido no `QUALITY-0049`.
- `driver_trip_overview_widgets.dart` — 436 linhas.
- `driver_trip_stop_widgets.dart` — 422 linhas, incluindo a aba de paradas
  extraida no `QUALITY-0043`.
- `driver_trip_action_widgets.dart` — 149 linhas.
- `driver_home_account_widgets.dart` — 156 linhas.
- `driver_trip_map_widgets.dart` — 208 linhas, incluindo a aba de mapa
  completo extraida no `QUALITY-0040`.
- `driver_home_trip_agenda_widgets.dart` — 112 linhas.
- `driver_home_trip_bar_widgets.dart` — 134 linhas.
- `driver_home_trip_sheet_widgets.dart` — 532 linhas, incluindo a sheet de
  edicao de rota diaria extraida no `QUALITY-0027`.
- `driver_home_trip_tab.dart` — 241 linhas, incluindo a aba inicial extraida
  no `QUALITY-0051`.
- `driver_trip_detail_panel_widgets.dart` — 231 linhas, incluindo o painel de
  detalhes da viagem extraido no `QUALITY-0032`.
- `driver_home_trip_widgets.dart` — 4 linhas.
- `home_motorista_screen.dart` — 856 linhas apos ficar focada em lifecycle,
  presenters, gateways e callbacks operacionais.

Arquivos de widgets criados para manter a listagem de viagens fora da tela
stateful:

- `admin_viagens_widgets.dart` — 243 linhas.
- `admin_viagens_list_widgets.dart` — 582 linhas.
- `admin_viagens_status_widgets.dart` — 111 linhas.
- `admin_viagens_detail_widgets.dart` — 597 linhas.
- `admin_viagens_create_widgets.dart` — 509 linhas.
- `admin_viagens_generation_result_widgets.dart` — 160 linhas.

Arquivos de widgets criados para manter a tela de vínculos menor:

- `admin_vinculos_widgets.dart` — 357 linhas, incluindo o painel de criacao e
  o estado de erro extraidos no `PHASE-0306`.
- `admin_vinculos_onboarding_widgets.dart` — 493 linhas.
- `admin_vinculos_service_widgets.dart` — 313 linhas.
- `admin_vinculos_guardian_widgets.dart` — 305 linhas.
- `admin_vinculos_motorista_widgets.dart` — 166 linhas.
- `admin_vinculos_access_request_dialog.dart` — 238 linhas, incluindo o dialog
  de aprovacao/rejeicao de solicitacao extraido no `QUALITY-0048`.
- `admin_vinculos_invite_creation_dialog.dart` — 81 linhas, incluindo o dialog
  de criacao de convite extraido no `PHASE-0306`.
- `admin_vinculos_screen.dart` — 543 linhas apos ficar focada em lifecycle,
  gateway, carregamento e callbacks de orquestracao.

Arquivo de widgets criado para manter a home do passageiro menor:

- `passenger_home_account_widgets.dart` — 225 linhas.
- `passenger_home_presence_widgets.dart` — 319 linhas.
- `passenger_home_trip_widgets.dart` — 137 linhas.

Arquivo de widgets criado para manter o onboarding de empresa do passageiro
menor:

- `passenger_company_link_widgets.dart` — 239 linhas, incluindo os cards
  visuais extraidos no `QUALITY-0042` e o card de revisao da empresa extraido
  no `QUALITY-0052`.
- `passenger_company_link_flow.dart` — 852 linhas apos ficar mais focado em
  busca, codigo de convite, solicitacao, status e navegacao entre estados.

Arquivo de widgets criado para manter a tela de passageiros menor:

- `admin_passageiro_form_dialog.dart` — 379 linhas, incluindo o dialog de
  cadastro de passageiro extraido no `QUALITY-0044`.
- `admin_passageiros_widgets.dart` — 583 linhas.

Arquivos de widgets criados para manter a tela de responsaveis menor:

- `admin_responsaveis_widgets.dart` — 298 linhas.
- `admin_responsaveis_detail_widgets.dart` — 322 linhas.

Arquivos de widgets criados para listagem de serviços recorrentes:

- `admin_servicos_recorrentes_list_widgets.dart` — 209 linhas.
- `admin_servicos_recorrentes_detail_widgets.dart` — 531 linhas.
- `admin_servicos_recorrentes_form_widgets.dart` — 260 linhas.
- `admin_servico_recorrente_form.dart` — 561 linhas, incluindo o formulario de
  criacao/edicao extraido no `QUALITY-0047`.
- `admin_servicos_recorrentes_screen.dart` — 486 linhas apos ficar focada em
  lifecycle, gateway, selecao e troca entre lista/detalhe/formulario.

Arquivos de widgets criados para a area legada de rotas-base:

- `admin_rotas_widgets.dart` — 594 linhas.
- `admin_rotas_detail_view.dart` — 257 linhas.
- `admin_rota_form_view.dart` — 598 linhas, incluindo o formulario de
  criacao/edicao extraido no `QUALITY-0046`.
- `admin_rota_form_summary.dart` — 148 linhas, incluindo o resumo lateral do
  formulario extraido no `QUALITY-0046`.
- `admin_rotas_screen.dart` — 396 linhas apos ficar focada em lifecycle,
  gateway, selecao e troca entre lista/detalhe/formulario.

Arquivo de widgets criado para manter o login admin menor:

- `admin_login_bootstrap_dialog.dart` — 351 linhas, incluindo o dialog de
  bootstrap do primeiro admin extraido no `QUALITY-0045`.
- `admin_login_hero.dart` — 233 linhas.

Arquivo de widgets criado para manter a tela de motoristas menor:

- `admin_motoristas_widgets.dart` — 289 linhas, incluindo a listagem de
  motoristas extraida no `QUALITY-0041`.
- `admin_motorista_detail_widgets.dart` — 271 linhas, incluindo detalhe e
  estado de erro extraidos no `PHASE-0305`.
- `admin_motorista_form_dialog.dart` — 313 linhas, incluindo o dialog de
  criacao/edicao extraido no `PHASE-0305`.
- `admin_motoristas_screen.dart` — 241 linhas apos ficar focada em lifecycle,
  carregamento, selecao e callbacks.

Artefatos compartilhados criados para destino final admin:

- `admin_places_gateway.dart` — 108 linhas.
- `admin_destination_picker.dart` — 292 linhas.

Arquivos de widgets criados para manter a localizacao do responsavel menor:

- `guardian_localizacao_widgets.dart` — 329 linhas, incluindo a agenda de
  viagens do dia extraida no `QUALITY-0050`.
- `guardian_localizacao_screen.dart` — 918 linhas apos ficar focada em
  lifecycle, gateways, selecao, realtime e composicao de alto nivel.

## Ratchet v1

O primeiro gate deve bloquear apenas regressao objetiva, sem exigir que toda a divida historica seja resolvida no mesmo card.

Artefatos versionados da v1:

- `scripts/quality_metrics.py` coleta metricas e compara baseline.
- `scripts/quality_metrics_test.py` cobre regras centrais do comparador.
- `scripts/quality_baseline.sh` atualiza `quality/ratchet-baseline.json`.
- `scripts/quality_gate.sh` valida o estado atual contra `quality/ratchet-baseline.json`.
- `scripts/ci_quality_gate.sh` executa o perfil de CI do app, validando o
  wiring do workflow e delegando para o gate local sem E2E real.
- `scripts/ci_quality_gate_test.py` protege o contrato entre workflow,
  entrypoint de CI e `qa_gate_card.sh --no-e2e`.
- `scripts/qa_gate_card.sh` executa os testes do comparador, o ratchet,
  analyze e testes Flutter como parte do gate oficial por card.
- `../../../.github/workflows/aplicativo.yml` chama o wrapper da raiz `scripts/check_app.sh`, que delega a `scripts/ci_quality_gate.sh`
  para manter paridade entre gate local e CI sem acoplar o workflow ao gate de
  card.
- `docs/codex-cloud.md` e `scripts/codex_cloud_setup.sh` recomendam
  `scripts/ci_quality_gate.sh` como check principal de agentes em Codex Cloud.
- O índice Graphify pertence à raiz do repositório acadêmico; consulte a
  skill e as instruções da raiz para diagnosticar dependências.

Regras propostas para o app:

1. `flutter analyze --no-fatal-infos --no-fatal-warnings` deve continuar passando.
2. `flutter test` deve continuar passando.
3. Nenhum arquivo Dart novo pode nascer acima de 600 linhas.
4. Arquivos acima de 1.000 linhas entram em allowlist temporaria e so podem aumentar com justificativa no Impact Analysis.
5. Nenhum novo `ignore_for_file` ou `// ignore:` sem comentario explicando motivo e card de limpeza.
6. Nenhum novo acesso direto a `SharedPreferences.getInstance()` fora de stores/infra aprovadas.
7. Nenhum novo `debugPrint` de fluxo normal fora de pontos de logging permitidos; logs existentes ficam como divida de limpeza.
8. Nenhuma nova tela operacional no root de `lib/`; novas telas devem nascer em `lib/features/<area>/presentation/`.
9. Todo card que altera comportamento deve adicionar ou ajustar teste no ponto de risco.

## Ratchet v2

O v2 separa metricas em tres categorias para evitar um gate fragil:

- `blocking`: regressao nova que deve falhar localmente e no CI.
- `warning`: sinal de arquitetura ou governanca que deve aparecer no relatorio, mas pode aguardar card dedicado.
- `report-only`: informacao de tendencia para planejamento.

Checks `blocking` do app:

1. `flutter analyze --no-fatal-infos --no-fatal-warnings` continua obrigatorio.
2. `flutter test` continua obrigatorio.
3. Nenhum arquivo Dart novo nasce acima de 600 linhas.
4. `analyzer_ignore_count` nao aumenta.
5. `shared_preferences_violation_count` permanece em `0`.
6. `SharedPreferences.getInstance()` continua restrito a stores/infra aprovadas.

Checks `warning` do app:

1. `set_state_count` aumenta.
2. `debug_print_count` aumenta.
3. `root_screen_count` aumenta.
4. Quantidade de arquivos acima de 600 linhas aumenta.
5. Tamanho maximo aumenta por categoria: producao, widget test e integration test.
6. Card de backlog fica com `### Status` em `IN_PROGRESS` enquanto o DoD ja registra `DONE`.
7. Graphify reporta codigo stale.

Checks `report-only` do app:

- maiores arquivos por categoria;
- tendencia de linhas totais;
- quantidade de arquivos Dart;
- freshness do grafo quando so docs mudaram.

Politica de baseline v2:

- divida existente fica versionada;
- melhora intencional deve apertar a baseline;
- aumento intencional exige Impact Analysis, card dono, rollback e evidencia do gate.

Implementacao inicial do app:

- `scripts/quality_metrics.py` reporta `dart_files_over_limit_count`,
  `production_max_file_lines`, `widget_test_max_file_lines`,
  `integration_test_max_file_lines` e `stale_completed_card_count`.
- `details.files_over_limit` lista arquivos acima do limite com categoria,
  caminho e linhas.
- `details.max_file_lines_by_category` separa os maximos de producao,
  widget test e integration test.
- `details.stale_completed_cards` lista cards `QUALITY-*` com status
  `IN_PROGRESS` apesar de DoD ja concluido.

## Politica de excecao

Excecao so pode entrar com:

- motivo explicito no Impact Analysis;
- prazo ou card de remocao;
- evidencia de que nao ha alternativa menor para aquele card;
- validacao minima executada.

Excecao silenciosa e regressao.

## Fases

### Fase 1: Baseline + gate

- Criar script de baseline que calcula as metricas acima.
- Versionar snapshot inicial em formato legivel para humanos e facil de comparar por script.
- Integrar o ratchet ao `scripts/qa_gate_card.sh`.
- Integrar o gate local ao CI do app por `scripts/ci_quality_gate.sh` para
  `push`, `pull_request` e execucao manual.
- Documentar como atualizar baseline apenas quando uma metrica melhora ou quando uma excecao foi aprovada.

### Fase 2: Refatoracoes guiadas por hotspot

- Quebrar telas admin grandes em subwidgets e controllers/presenters menores.
- Quebrar `home_motorista_screen.dart` por responsabilidade visual.
- Migrar telas restantes do root de `lib/` para `features/`.
- Reduzir `setState` por area, com testes cobrindo o comportamento antes da mudanca.
- Manter `graphify-out/` local e atualizado apos refatoracoes relevantes:
  - usar `scripts/graphify_refresh.sh` para refresh normal;
  - usar `GRAPHIFY_FORCE=1 scripts/graphify_refresh.sh` apenas quando uma
    refatoracao/normalizacao intencional reduzir o numero de nós e o Graphify
    recusar overwrite por seguranca.

### Fase 3: Gate de maturidade

- Avaliar cobertura automatizada com `flutter test --coverage`.
- Endurecer `analysis_options.yaml` gradualmente.
- Adicionar checks de build Android quando o runner e as credenciais estiverem padronizados.
- Criar limites por camada: presentation, data, domain, shared.

## Fora de escopo do primeiro card

- Introduzir Riverpod/Bloc/GoRouter.
- Refatorar telas gigantes.
- Remover todos os logs de debug.
- Resolver todos os debitos do `Technical-Debt-2026-04-22.md`.
- Alterar contrato com backend.

## Validacao minima do card de fundacao

- `flutter analyze --no-fatal-infos --no-fatal-warnings`.
- `flutter test`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- Execucao manual do novo script de baseline.
