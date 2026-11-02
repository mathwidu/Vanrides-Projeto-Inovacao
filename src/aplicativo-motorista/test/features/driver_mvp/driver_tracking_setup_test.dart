import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_tracking_platform.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/presentation/driver_setup_app.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/presentation/driver_tracking_setup.dart';

import 'driver_energy_fixture.dart';

class TrackingFake implements DriverTrackingPlatform {
  DriverTrackingStatus status = const DriverTrackingStatus(
    state: 'READY',
    credentialConfigured: true,
    permission: 'GRANTED',
  );
  int starts = 0;
  int stops = 0;
  int reads = 0;
  int configs = 0;
  Completer<DriverTrackingStatus>? activation;
  Completer<DriverTrackingStatus>? shutdown;
  @override
  Future<DriverTrackingStatus> getStatus() async {
    reads++;
    return status;
  }

  @override
  Future<DriverTrackingStatus> syncStatus() async => status;
  @override
  Future<DriverTrackingStatus> configureCredential(
    String baseUrl,
    String token,
  ) async {
    configs++;
    return status;
  }

  @override
  Future<DriverTrackingStatus> requestPermission() async => status;
  @override
  Future<DriverTrackingStatus> start(String intentId) async {
    starts++;
    if (activation != null) return activation!.future;
    return status = const DriverTrackingStatus(
      state: 'TRACKING',
      credentialConfigured: true,
      permission: 'GRANTED',
    );
  }

  @override
  Future<DriverTrackingStatus> stop() async {
    stops++;
    if (shutdown != null) return status = await shutdown!.future;
    return status = const DriverTrackingStatus(
      state: 'STOPPED',
      credentialConfigured: true,
      permission: 'GRANTED',
      stopRequestedAt: 1800000000000,
      stoppedAt: 1800000000001,
    );
  }
}

Future<void> show(WidgetTester tester, TrackingFake fake) async {
  await tester.pumpWidget(DriverSetupApp(platform: fake));
  await tester.pump();
}

void main() {
  testWidgets(
    'details copy only the displayed sanitized snapshot on explicit tap',
    (tester) async {
      final copied = <String>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(SystemChannels.platform, (call) async {
            if (call.method == 'Clipboard.setData') {
              copied.add((call.arguments as Map)['text'] as String);
            }
            return null;
          });
      addTearDown(() {
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(SystemChannels.platform, null);
      });
      final fake =
          TrackingFake()
            ..status = DriverTrackingStatus.fromMap({
              'state': 'STOPPED',
              'credentialConfigured': true,
              'permission': 'GRANTED',
              'queued': 0,
              'accepted': 2,
              'duplicates': 0,
              'rejected': 0,
              'dropped': 0,
              'gpsState': 'STALE',
              'leaseDeadlineElapsedMs': 300000,
              'nativeStopCompletedElapsedMs': 300002,
              'diagnosticBootCount': 7,
              'locationCallbacksReceived': 2,
              'lastLocationAccuracyM': 75.5,
              'lastLocationAgeWallMs': -100,
              'lastLocationWallState': 'FUTURE',
              'energySession': {
                ...nativeEnergySession(),
                'sessionId': 'PRIVATE_ENERGY_SESSION',
                'approved': true,
              },
              'token': 'PRIVATE_BEARER',
              'sessionId': 'PRIVATE_SESSION',
              'latitude': -90.0,
              'longitude': 180.0,
            });
      await show(tester, fake);
      await tester.ensureVisible(find.text('Detalhes do teste'));
      await tester.tap(find.text('Detalhes do teste'));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('"nativeStopCompletedElapsedMs": 300002'),
        findsOneWidget,
      );
      expect(
        find.textContaining('"lastLocationWallState": "FUTURE"'),
        findsOneWidget,
      );
      expect(find.textContaining('PRIVATE'), findsNothing);
      expect(
        find.textContaining(
          'Sem cálculo de autonomia ou aprovação automática.',
        ),
        findsOneWidget,
      );
      await tester.pump(const Duration(seconds: 1));
      expect(copied, isEmpty);
      await tester.ensureVisible(find.text('Copiar diagnóstico sanitizado'));
      await tester.tap(find.text('Copiar diagnóstico sanitizado'));
      await tester.pump();
      expect(copied, hasLength(1));
      final exported = jsonDecode(copied.single) as Map<String, dynamic>;
      expect(exported['nativeStopCompletedElapsedMs'], 300002);
      expect(exported['leaseDeadlineElapsedMs'], 300000);
      expect(exported['lastLocationAgeWallMs'], -100);
      expect(exported['gpsState'], 'STALE');
      expect(exported['energySession'], nativeEnergySession());
      expect(exported.keys, isNot(contains('token')));
      expect(exported.keys, isNot(contains('sessionId')));
      expect(exported.keys, isNot(contains('latitude')));
      expect(exported.keys, isNot(contains('longitude')));
      expect(copied.single, isNot(contains('PRIVATE')));
      expect(find.text('Diagnóstico sanitizado copiado.'), findsOneWidget);
      expect(fake.starts, 0);
      expect(fake.stops, 0);
      expect(fake.configs, 0);
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets('pending stop waits for confirmation and prevents a new start', (
    tester,
  ) async {
    final fake =
        TrackingFake()
          ..status = const DriverTrackingStatus(
            state: 'STOPPED',
            credentialConfigured: true,
            permission: 'GRANTED',
            stopPending: true,
          );
    await show(tester, fake);
    expect(find.text('Encerramento solicitado'), findsOneWidget);
    expect(
      find.text('Aguardando confirmação do encerramento.'),
      findsOneWidget,
    );
    expect(find.text('Localização encerrada'), findsNothing);
    expect(find.text('Localização interrompida'), findsNothing);
    expect(find.textContaining('A coleta parou.'), findsNothing);
    expect(
      tester
          .widget<FilledButton>(
            find.widgetWithText(FilledButton, 'Iniciar teste'),
          )
          .onPressed,
      isNull,
    );
    fake.status = const DriverTrackingStatus(
      state: 'STOPPED',
      credentialConfigured: true,
      permission: 'GRANTED',
    );
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('Aguardando confirmação do encerramento.'), findsNothing);
    expect(find.text('Localização interrompida'), findsOneWidget);
    expect(find.text('O coletor confirmou o encerramento.'), findsOneWidget);
    expect(
      tester
          .widget<FilledButton>(
            find.widgetWithText(FilledButton, 'Iniciar teste'),
          )
          .onPressed,
      isNotNull,
    );
    expect(fake.starts, 0);
    await tester.pumpWidget(const SizedBox());
  });
  for (final textScale in [1.0, 2.0]) {
    testWidgets(
      'stop feedback stays next to the control at text scale $textScale',
      (tester) async {
        tester.platformDispatcher.textScaleFactorTestValue = textScale;
        addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
        final semantics = tester.ensureSemantics();
        final fake =
            TrackingFake()
              ..status = const DriverTrackingStatus(
                state: 'TRACKING',
                credentialConfigured: true,
                permission: 'GRANTED',
              )
              ..shutdown = Completer<DriverTrackingStatus>();
        await show(tester, fake);
        final control = find.widgetWithText(
          OutlinedButton,
          'Parar localização',
        );
        await tester.ensureVisible(control);
        await tester.tap(control);
        await tester.pump();
        final pendingControl = find.widgetWithText(
          OutlinedButton,
          'Encerramento solicitado',
        );
        final feedback = find.byKey(const ValueKey('driver-stop-feedback'));
        expect(tester.widget<OutlinedButton>(pendingControl).onPressed, isNull);
        expect(find.text('Localização interrompida'), findsNothing);
        expect(
          tester.getSemantics(feedback),
          containsSemantics(isLiveRegion: true),
        );
        expect(
          tester.getRect(feedback).top - tester.getRect(pendingControl).bottom,
          inInclusiveRange(0, 16),
        );
        expect(
          tester
              .getRect(find.byType(SingleChildScrollView))
              .contains(tester.getCenter(pendingControl)),
          isTrue,
        );
        fake.shutdown!.complete(
          const DriverTrackingStatus(
            state: 'STOPPED',
            credentialConfigured: true,
            permission: 'GRANTED',
          ),
        );
        await tester.pump();
        final confirmedControl = find.widgetWithText(
          OutlinedButton,
          'Localização interrompida',
        );
        expect(confirmedControl, findsOneWidget);
        expect(
          tester.widget<OutlinedButton>(confirmedControl).onPressed,
          isNull,
        );
        expect(
          find.text('O coletor confirmou o encerramento.'),
          findsOneWidget,
        );
        expect(
          tester.getSemantics(feedback),
          containsSemantics(isLiveRegion: true),
        );
        expect(
          tester
              .getRect(find.byType(SingleChildScrollView))
              .contains(tester.getCenter(confirmedControl)),
          isTrue,
        );
        expect(tester.takeException(), isNull);
        expect(fake.stops, 1);
        semantics.dispose();
        await tester.pumpWidget(const SizedBox());
      },
    );
  }
  testWidgets('failed stop never shows confirmation from a terminal snapshot', (
    tester,
  ) async {
    final fake =
        TrackingFake()
          ..status = const DriverTrackingStatus(
            state: 'TRACKING',
            credentialConfigured: true,
            permission: 'GRANTED',
          )
          ..shutdown = Completer<DriverTrackingStatus>();
    await show(tester, fake);
    await tester.ensureVisible(find.text('Parar localização'));
    await tester.tap(find.text('Parar localização'));
    await tester.pump();
    fake.status = const DriverTrackingStatus(
      state: 'STOPPED',
      credentialConfigured: true,
      permission: 'GRANTED',
    );
    fake.shutdown!.completeError(PlatformException(code: 'NETWORK_ERROR'));
    await tester.pump();
    expect(find.text('Localização interrompida'), findsNothing);
    expect(find.text('Localização encerrada'), findsNothing);
    expect(find.text('O coletor confirmou o encerramento.'), findsNothing);
    expect(
      find.textContaining('Não foi possível alcançar o receptor.'),
      findsOneWidget,
    );
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('a new start does not keep the previous stop confirmation', (
    tester,
  ) async {
    final fake =
        TrackingFake()
          ..status = const DriverTrackingStatus(
            state: 'STOPPED',
            credentialConfigured: true,
            permission: 'GRANTED',
          )
          ..activation = Completer<DriverTrackingStatus>();
    await show(tester, fake);
    expect(find.text('Localização interrompida'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'intent_test');
    await tester.tap(find.text('Iniciar teste'));
    await tester.pump();
    expect(find.text('Localização interrompida'), findsNothing);
    expect(find.text('O coletor confirmou o encerramento.'), findsNothing);
    fake.activation!.complete(
      const DriverTrackingStatus(
        state: 'TRACKING',
        credentialConfigured: true,
        permission: 'GRANTED',
      ),
    );
    await tester.pump();
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets(
    'opening, backgrounding and disposing screen do not control collection',
    (tester) async {
      final fake = TrackingFake();
      await show(tester, fake);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      final before = fake.reads;
      await tester.pump(const Duration(seconds: 3));
      expect(fake.reads, before);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pump();
      expect(fake.reads, greaterThan(before));
      await tester.pumpWidget(const SizedBox());
      expect(fake.starts, 0);
      expect(fake.stops, 0);
    },
  );
  testWidgets('stop remains available while activation is awaiting network', (
    tester,
  ) async {
    final fake = TrackingFake()..activation = Completer<DriverTrackingStatus>();
    await show(tester, fake);
    await tester.enterText(find.byType(TextField), 'intent_test');
    await tester.tap(find.text('Iniciar teste'));
    await tester.pump();
    expect(fake.starts, 1);
    await tester.ensureVisible(find.text('Parar localização'));
    await tester.tap(find.text('Parar localização'));
    await tester.pump();
    expect(fake.stops, 1);
    fake.activation!.complete(
      const DriverTrackingStatus(
        state: 'TRACKING',
        credentialConfigured: true,
        permission: 'GRANTED',
      ),
    );
    await tester.pump();
    expect(find.text('Localização encerrada'), findsOneWidget);
    expect(find.text('Localização em andamento'), findsNothing);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('invalid intent never reaches the collector', (tester) async {
    final fake = TrackingFake();
    await show(tester, fake);
    await tester.enterText(find.byType(TextField), 'https://invalid/?secret');
    await tester.tap(find.text('Iniciar teste'));
    await tester.pump();
    expect(fake.starts, 0);
    expect(find.textContaining('Solicite um novo código'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets(
    'missing permission disables start and asks for precise location',
    (tester) async {
      final fake =
          TrackingFake()
            ..status = const DriverTrackingStatus(
              state: 'READY',
              credentialConfigured: true,
              permission: 'APPROXIMATE',
            );
      await show(tester, fake);
      final start = tester.widget<FilledButton>(
        find.widgetWithText(FilledButton, 'Iniciar teste'),
      );
      expect(start.onPressed, isNull);
      expect(find.text('Permitir localização'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets(
    'configuration secret is obscured, cleared and never repeated on screen',
    (tester) async {
      final fake = TrackingFake()..status = const DriverTrackingStatus();
      await show(tester, fake);
      final secret = find.byType(TextField).last;
      expect(tester.widget<TextField>(secret).obscureText, isTrue);
      await tester.enterText(secret, 'synthetic_local_test_only');
      await tester.tap(find.text('Configurar aparelho'));
      await tester.pump();
      expect(fake.configs, 1);
      expect(tester.widget<TextField>(secret).controller!.text, isEmpty);
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets('screen scrolls at large text scale without layout overflow', (
    tester,
  ) async {
    tester.platformDispatcher.textScaleFactorTestValue = 2.0;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    final fake = TrackingFake();
    await show(tester, fake);
    expect(tester.takeException(), isNull);
    await tester.ensureVisible(find.text('Detalhes do teste'));
    await tester.tap(find.text('Detalhes do teste'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Copiar diagnóstico sanitizado'));
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets(
    'notification denial keeps Android permission recovery available',
    (tester) async {
      final fake =
          TrackingFake()
            ..status = const DriverTrackingStatus(
              state: 'READY',
              credentialConfigured: true,
              permission: 'GRANTED',
              notificationPermissionGranted: false,
            );
      await show(tester, fake);
      expect(find.text('Permitir localização'), findsOneWidget);
      final start = tester.widget<FilledButton>(
        find.widgetWithText(FilledButton, 'Iniciar teste'),
      );
      expect(start.onPressed, isNull);
      await tester.pumpWidget(const SizedBox());
    },
  );
  test('platform exception details cannot leak into the interface', () {
    final error = PlatformException(
      code: 'UNKNOWN',
      message: 'TOKEN-secret',
      details: {'latitude': -90.0, 'private': 'sensitive'},
    );
    expect(safeTrackingError(error), isNot(contains('TOKEN')));
    expect(safeTrackingError(error), isNot(contains('sensitive')));
  });
}
