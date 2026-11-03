import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_navigation_panel.dart';
import 'package:vanrides_frontend_dev/features/navigation/presentation/widgets/driver_navigation_panel.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';

const _ttsDeviceSmokeEnabled = bool.fromEnvironment(
  'VANRIDES_NAV_TTS_DEVICE_SMOKE',
  defaultValue: false,
);
const _sourceGitSha = String.fromEnvironment('VANRIDES_NAV_TTS_SOURCE_SHA');
const _sourceGitTree = String.fromEnvironment('VANRIDES_NAV_TTS_SOURCE_TREE');
const _runId = String.fromEnvironment('VANRIDES_NAV_TTS_RUN_ID');

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'driver navigation repeat emits native TTS smoke prompt',
    (tester) async {
      if (!_ttsDeviceSmokeEnabled) {
        throw TestFailure(
          'Run with VANRIDES_NAV_TTS_DEVICE_SMOKE=true for native TTS smoke.',
        );
      }
      if (!RegExp(r'^[0-9a-f]{40,64}$').hasMatch(_sourceGitSha) ||
          !RegExp(r'^[0-9a-f]{40,64}$').hasMatch(_sourceGitTree) ||
          !RegExp(r'^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$').hasMatch(_runId)) {
        throw TestFailure(
          'Source SHA, source tree and run id must be provided by the producer.',
        );
      }

      await tester.binding.setSurfaceSize(const Size(390, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(
        MaterialApp(
          debugShowCheckedModeBanner: false,
          home: Scaffold(
            body: Center(
              child: DriverHomeNavigationPanel(
                trip: _trip(),
                canOpenMaps: true,
                openingNavigation: false,
                runningLifecycleAction: false,
                onCompleteStop: () {},
                onOpenNavigation: () {},
              ),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle(const Duration(seconds: 1));

      expect(find.byKey(DriverNavigationPanel.rootKey), findsOneWidget);
      expect(
        find.byKey(DriverNavigationPanel.repeatInstructionKey),
        findsOneWidget,
      );

      await tester.tap(find.byKey(DriverNavigationPanel.repeatInstructionKey));
      await tester.pump(const Duration(seconds: 1));

      debugPrintSynchronously(
        'NAVIGATION_TTS_DEVICE_SMOKE_AUDIO_READY '
        'source_sha=$_sourceGitSha source_tree=$_sourceGitTree '
        'run_id=$_runId scenario=driver-navigation-repeat',
      );
      await tester.pump(const Duration(seconds: 4));
    },
    timeout: const Timeout(Duration(minutes: 3)),
  );
}

DriverTripRecord _trip() {
  return DriverTripRecord(
    id: 9101,
    rotaBaseId: 401,
    rotaNome: 'Rota Smoke',
    servicoNome: 'Linha Voz',
    dataOperacao: DateTime(2026, 7, 6),
    status: TripTrackingStatus.inProgress,
    rotaVersao: 1,
    etaMinutosAteProximaParada: 7,
    etaAtualizadoEm: DateTime(2026, 7, 6, 7, 30),
    motorista: const TripTrackingDriverRecord(id: 77, nome: 'Motorista Smoke'),
    proximaParada: _stop,
    paradas: const [_stop],
    rotaGeografica: null,
  );
}

const _stop = DriverTripStopRecord(
  id: 8001,
  ordem: 1,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 7,
  confirmadoPresenca: true,
  proximaParada: true,
  passageiroId: 3001,
  passageiroNome: 'Passageiro Smoke',
  menorDeIdade: false,
);
