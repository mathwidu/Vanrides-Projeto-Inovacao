import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_models.dart';
import 'package:vanrides_frontend_dev/features/navigation/presentation/widgets/driver_navigation_panel.dart';

void main() {
  testWidgets('panel shows current instruction, next stop and fallback', (
    tester,
  ) async {
    var primaryTaps = 0;
    var fallbackTaps = 0;
    var repeatTaps = 0;
    var skipTaps = 0;
    var noShowTaps = 0;

    await tester.pumpWidget(
      _app(
        DriverNavigationPanel(
          viewModel: _viewModel(),
          onPrimaryAction: () => primaryTaps++,
          onFallback: () => fallbackTaps++,
          onRepeatInstruction: () => repeatTaps++,
          onSkipStop: () => skipTaps++,
          onNoShowStop: () => noShowTaps++,
        ),
      ),
    );

    expect(find.text('Em 300 m'), findsOneWidget);
    expect(find.text('Vire a direita na Av. Brasil'), findsOneWidget);
    expect(find.text('Proxima parada'), findsOneWidget);
    expect(find.text('Ana Clara'), findsOneWidget);
    expect(find.text('Confirmar embarque'), findsOneWidget);
    expect(find.text('Pular'), findsOneWidget);
    expect(find.text('Faltou'), findsOneWidget);
    expect(find.text('Fallback'), findsOneWidget);

    await tester.tap(find.byKey(DriverNavigationPanel.primaryActionKey));
    await tester.tap(find.byKey(DriverNavigationPanel.skipStopActionKey));
    await tester.tap(find.byKey(DriverNavigationPanel.noShowActionKey));
    await tester.tap(find.byKey(DriverNavigationPanel.fallbackActionKey));
    await tester.tap(find.byKey(DriverNavigationPanel.repeatInstructionKey));
    await tester.pumpAndSettle();

    expect(primaryTaps, 1);
    expect(skipTaps, 1);
    expect(noShowTaps, 1);
    expect(fallbackTaps, 1);
    expect(repeatTaps, 1);
  });

  testWidgets('panel tolerates large text without a real map', (tester) async {
    await tester.pumpWidget(
      _app(
        MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(1.6)),
          child: DriverNavigationPanel(
            viewModel: _viewModel(),
            onPrimaryAction: () {},
            onFallback: () {},
            onRepeatInstruction: () {},
          ),
        ),
      ),
    );

    expect(find.byKey(DriverNavigationPanel.rootKey), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('panel shows local action queue summary when present', (
    tester,
  ) async {
    var retryTaps = 0;

    await tester.pumpWidget(
      _app(
        DriverNavigationPanel(
          viewModel: _viewModel(
            queuedActionSummaryLabel: '1 acao local pendente',
            queuedActionDetailLabel: 'Confirmar embarque de Ana Clara',
            queuedActionHelperText:
                'Sera enviada quando a conexao estiver segura.',
          ),
          onPrimaryAction: () {},
          onFallback: () {},
          onRepeatInstruction: () {},
          onRetryQueuedActions: () => retryTaps++,
        ),
      ),
    );

    expect(
      find.byKey(DriverNavigationPanel.actionQueueStatusKey),
      findsOneWidget,
    );
    expect(find.text('1 acao local pendente'), findsOneWidget);
    expect(find.text('Confirmar embarque de Ana Clara'), findsOneWidget);
    expect(
      find.text('Sera enviada quando a conexao estiver segura.'),
      findsOneWidget,
    );
    expect(
      find.byKey(DriverNavigationPanel.actionQueueRetryKey),
      findsOneWidget,
    );

    await tester.tap(find.byKey(DriverNavigationPanel.actionQueueRetryKey));
    await tester.pump();

    expect(retryTaps, 1);
  });
}

Widget _app(Widget child) {
  return MaterialApp(home: Scaffold(body: Center(child: child)));
}

DriverNavigationViewModel _viewModel({
  String? queuedActionSummaryLabel,
  String? queuedActionDetailLabel,
  String? queuedActionHelperText,
}) {
  return DriverNavigationViewModel(
    mode: DriverNavigationMode.internal,
    instructionText: 'Vire a direita na Av. Brasil',
    distanceLabel: 'Em 300 m',
    helperText: 'Siga com atencao ate a proxima parada.',
    nextStopLabel: 'Proxima parada',
    nextStopName: 'Ana Clara',
    nextStopSummary: 'Parada 1 - Linha Manha',
    primaryActionLabel: 'Confirmar embarque',
    secondaryActions: const [
      DriverNavigationSecondaryAction(
        type: DriverNavigationSecondaryActionType.skipStop,
        label: 'Pular',
      ),
      DriverNavigationSecondaryAction(
        type: DriverNavigationSecondaryActionType.noShow,
        label: 'Faltou',
      ),
    ],
    fallbackLabel: 'Fallback',
    statusLabel: 'Offline pronto',
    voicePrompt: 'Em 300 metros, vire a direita na Avenida Brasil',
    fallbackIsPrimary: false,
    canUseInternalNavigation: true,
    queuedActionSummaryLabel: queuedActionSummaryLabel,
    queuedActionDetailLabel: queuedActionDetailLabel,
    queuedActionHelperText: queuedActionHelperText,
  );
}
