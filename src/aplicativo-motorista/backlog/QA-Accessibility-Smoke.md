# QA Accessibility Smoke - VanRides App

Baseline: 2026-07-06

Objetivo: garantir que cards visuais nao fechem sem uma verificacao minima de
texto, foco, semantica, contraste e preferencias de movimento.

## Escopo Critico Atual

| Fluxo | Superficie | Criterio minimo | Evidencia automatizada |
| --- | --- | --- | --- |
| Motorista | `DriverNavigationPanel` | Regiao semantica, texto 1.6x, botoes de repetir/mutar com tooltip e nenhuma overflow exception. | `test/features/accessibility/integrated_accessibility_smoke_test.dart` |
| Motorista | `DriverHomeActiveSheet` | Primeira acao e dados da viagem continuam legiveis em largura compacta e texto aumentado. | `test/features/driver_trip/presentation/widgets/driver_home_active_sheet_accessibility_test.dart` |
| Responsavel | `ServicePresenceDecisionList` | Card de servico tem label semantica com horario, sentido, servico, status e destino; decisao nao depende so de cor. | `test/features/service_presence/service_presence_decision_list_test.dart` |
| Admin | `AdminVinculosTaskQueuePanel` | Fila tem regiao semantica, loading explica progresso e CTAs continuam acessiveis com texto 1.6x. | `test/features/accessibility/integrated_accessibility_smoke_test.dart` |
| Compartilhado | Liquid/glass/motion | High contrast, accessible navigation e disable animations usam fallback solido ou duracao zero quando aplicavel. | `test/app/widgets/vanrides_liquid_surface_test.dart`, `test/app/theme/vanrides_motion_test.dart`, `test/features/tracking/presentation/widgets/vanrides_glass_surface_test.dart` |

## Comando Focado

```bash
flutter test \
  test/features/accessibility/integrated_accessibility_smoke_test.dart \
  test/features/navigation/presentation/driver_navigation_panel_test.dart \
  test/features/admin/presentation/widgets/admin_vinculos_task_queue_widgets_test.dart \
  test/features/service_presence/service_presence_decision_list_test.dart \
  test/features/driver_trip/presentation/widgets/driver_home_active_sheet_accessibility_test.dart
```

## Checklist Por Card Visual

- Texto: rodar pelo menos um estado com `TextScaler.linear(1.35)` ou maior; para fluxos criticos, preferir `1.6`.
- Foco: em Web/admin, tabular os CTAs principais e confirmar que nao existe trap; em mobile, confirmar ordem logica de leitura dos blocos.
- Semantica: painéis compostos precisam de label de regiao; botoes icon-only precisam de tooltip/label; status nao pode depender apenas de cor.
- Contraste: high contrast deve manter leitura de texto, borda e estado selecionado.
- Movimento: `accessibleNavigation` ou `disableAnimations` nao pode esconder informacao nem depender de transicao longa.
- Evidencia: registrar comando ou smoke manual no `backlog/QA-Execution-Log.md`.
- Achados: se nao forem corrigidos no card, abrir card rastreavel com severidade, superficie e reproducao.

## Smoke Manual Minimo

### Mobile

1. Ativar texto grande no sistema ou simular com build/test harness.
2. Abrir home do motorista com viagem ativa.
3. Conferir se a proxima acao, instrucao de navegacao, repetir/mutar voz e fallback permanecem legiveis.
4. Abrir fluxo do responsavel em `Presencas`.
5. Conferir se `Vou`, `Nao vou`, status e destino continuam compreensiveis sem depender so de cor.
6. Ativar reduzir movimento quando possivel e confirmar que o app nao perde informacao essencial.

### Admin Web

1. Abrir `Onboarding e vinculos`.
2. Tabular ate `Revisar solicitacao`, `Novo convite` e `Vincular responsavel`.
3. Confirmar foco visivel, ausencia de trap e estado disabled compreensivel quando faltam passageiro/responsavel.
4. Aumentar zoom/texto do navegador e conferir que contadores e CTAs nao quebram layout.

## Ledger De Achados

| ID | Severidade | Superficie | Evidencia | Status |
| --- | --- | --- | --- | --- |
| A11Y-20260706-001 | Media | `AdminVinculosTaskQueuePanel` | RED do smoke integrado mostrou overflow dos pills de contadores com texto 1.6x. | Corrigido neste card com wrap/flex do label do pill. |

## Regra De Fechamento

Um card visual so pode ser marcado como DONE quando tiver pelo menos uma das
evidencias abaixo:

- teste widget cobrindo texto aumentado, semantica ou fallback de movimento;
- smoke manual registrado no QA log;
- justificativa explicita de nao aplicabilidade no QA log.
