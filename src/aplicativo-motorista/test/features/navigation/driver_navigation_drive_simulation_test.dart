import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_drive_simulation.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_models.dart';

void main() {
  test(
    'default simulation covers internal guidance, fallback and safe mode',
    () async {
      final result = await const DriverNavigationDriveSimulation().run();

      expect(result.steps.map((step) => step.id), [
        'start-internal',
        'next-stop-action',
        'raw-provider-instruction',
        'weak-signal-fallback',
        'partial-pack-corridor',
        'off-route-safe-mode',
      ]);
      expect(result.coversInternalNavigation, isTrue);
      expect(result.coversFallback, isTrue);
      expect(result.coversSafeMode, isTrue);
      expect(result.coversOfflineRisk, isTrue);

      final firstStep = result.steps.first;
      expect(firstStep.viewModel.mode, DriverNavigationMode.internal);
      expect(firstStep.viewModel.statusLabel, 'Offline pronto');
      expect(firstStep.viewModel.nextStopName, 'Ana Clara');
      expect(firstStep.spokenText, 'Siga por 800 metros ate a Rua das Flores');

      final nextStopStep = result.stepById('next-stop-action');
      expect(nextStopStep.eventSpokenText, 'Prepare a chegada em Ana Clara.');

      final fallbackStep = result.stepById('weak-signal-fallback');
      expect(
        fallbackStep.viewModel.mode,
        DriverNavigationMode.externalFallback,
      );
      expect(fallbackStep.viewModel.fallbackIsPrimary, isTrue);
      expect(
        fallbackStep.viewModel.instructionText,
        'Use o fallback para seguir com seguranca',
      );
      expect(
        fallbackStep.spokenText,
        'Use o fallback para seguir com seguranca',
      );
      expect(
        fallbackStep.eventSpokenText,
        'Navegacao offline limitada. Use o fallback se precisar.',
      );

      final rawProviderStep = result.stepById('raw-provider-instruction');
      expect(rawProviderStep.viewModel.mode, DriverNavigationMode.internal);
      expect(
        rawProviderStep.viewModel.instructionText,
        'Converta a direita em Main Street.',
      );
      expect(rawProviderStep.spokenText, 'Converta a direita em Main Street.');

      final safeStep = result.stepById('off-route-safe-mode');
      expect(safeStep.viewModel.mode, DriverNavigationMode.safeMode);
      expect(safeStep.viewModel.statusLabel, 'Modo seguro');
      expect(safeStep.viewModel.instructionText, 'Fora da rota planejada');
      expect(safeStep.viewModel.nextStopName, 'Bruno Lima');
      expect(safeStep.spokenText, 'Fora da rota planejada');
      expect(
        safeStep.eventSpokenText,
        'Fora da rota planejada. Use o modo seguro.',
      );
    },
  );

  test('report output is explicit that this is local QA evidence', () async {
    final result = await const DriverNavigationDriveSimulation().run();

    final report = result.toMarkdown();
    final json = result.toJson();

    expect(report, contains('APP-NAV-SIM-DRIVE-QA-001'));
    expect(report, contains('Simulacao local com dados QA'));
    expect(
      report,
      contains('Nao usa parceiro, GPS real, backend, deploy ou VPS'),
    );
    expect(report, contains('start-internal'));
    expect(report, contains('off-route-safe-mode'));
    expect(report, contains('Evento de voz'));
    expect(
      report,
      contains('Navegacao offline limitada. Use o fallback se precisar.'),
    );
    expect(json['card'], 'APP-NAV-SIM-DRIVE-QA-001');
    expect(json['fieldReady'], isFalse);
    expect(json['eventSpokenPrompts'], isA<List<Object?>>());
    expect(
      json['eventSpokenPrompts'],
      contains('Fora da rota planejada. Use o modo seguro.'),
    );
    expect(json['steps'], isA<List<Object?>>());
  });
}
