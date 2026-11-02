import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_session_links.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_run_intent.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_session_platform.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_tracking_platform.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/presentation/driver_pairing_app.dart';

class _Session implements DriverSessionPlatform {
  DriverSessionState current = DriverSessionState.unpaired;
  final actions = <DriverSessionAction>[];
  bool approved = false;
  bool loseRevokeResponse = false;
  int syncNetworkFailures = 0;
  int reveals = 0;
  Completer<String?>? reveal;
  @override
  Future<DriverSessionSnapshot> run(DriverSessionAction action) async {
    actions.add(action);
    if (action == DriverSessionAction.beginPairing) {
      current = DriverSessionState.pending;
    }
    if (action == DriverSessionAction.syncSession) {
      if (syncNetworkFailures > 0) {
        syncNetworkFailures--;
        throw const DriverSessionFailure('NETWORK');
      }
      if (approved) current = DriverSessionState.ready;
    }
    if (action == DriverSessionAction.revokeSession) {
      if (loseRevokeResponse) {
        current = DriverSessionState.revoking;
        throw const DriverSessionFailure('NETWORK');
      }
      current = DriverSessionState.revoked;
    }
    return DriverSessionSnapshot(current);
  }

  @override
  Future<String?> showPairingCode() async {
    reveals++;
    return reveal == null ? 'BCDFGHJZ' : await reveal!.future;
  }
}

class _Links implements DriverSessionLinks {
  _Links([this.opened]);
  final Uri? opened;
  bool trusted = false;
  @override
  Future<bool> isLauncherVisit() async => trusted;
  final stream = StreamController<Uri>.broadcast();
  @override
  Future<Uri?> initial() async => opened;
  @override
  Stream<Uri> get incoming => stream.stream;
  @override
  bool acceptsReturn(Uri uri) => DeviceSessionLinks.isReturn(uri, '');
  @override
  bool get hasConversation => false;
  @override
  Future<bool> openConversation() async => false;
}

class _Tracking implements DriverTrackingPlatform, DriverRunPlatform {
  final starts = <DriverRunIntent>[];
  @override
  Future<DriverTrackingStatus> startRun(DriverRunIntent intent) async {
    starts.add(intent);
    return value = const DriverTrackingStatus(
      state: 'TRACKING',
      gpsState: 'STALE',
    );
  }

  DriverTrackingStatus value = const DriverTrackingStatus();
  int stopped = 0;
  @override
  Future<DriverTrackingStatus> getStatus() async => value;
  @override
  Future<DriverTrackingStatus> stop() async {
    stopped++;
    return value = const DriverTrackingStatus(state: 'STOPPED');
  }

  @override
  Future<DriverTrackingStatus> requestPermission() async => value;
  @override
  Future<DriverTrackingStatus> syncStatus() async => value;
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<void> _show(
  WidgetTester t,
  _Session s,
  _Links links,
  _Tracking gps,
) async {
  await t.pumpWidget(DriverPairingApp(session: s, tracking: gps, links: links));
  await t.pumpAndSettle();
}

Future<void> _close(WidgetTester t, _Links links) async {
  await t.pumpWidget(const SizedBox.shrink());
  await links.stream.close();
}

void main() {
  testWidgets(
    'a start link waits for explicit confirmation and does not expose its token',
    (t) async {
      final token = List.filled(43, 'A').join();
      final links = _Links(
        Uri.parse(
          'vanridesdriver://run/start#v1.12345678-1234-1234-1234-123456789012.7.$token',
        ),
      );
      final session =
          _Session()
            ..current = DriverSessionState.ready
            ..approved = true;
      final tracking =
          _Tracking()
            ..value = const DriverTrackingStatus(
              state: 'READY',
              permission: 'GRANTED',
            );
      await _show(t, session, links, tracking);
      expect(tracking.starts, isEmpty);
      expect(find.textContaining(token), findsNothing);
      final start = find.text('Iniciar localização desta saída');
      await t.ensureVisible(start);
      await t.tap(start);
      await t.pumpAndSettle();
      expect(tracking.starts, hasLength(1));
      expect(find.text('Parar localização'), findsOneWidget);
      expect(find.text('Iniciar localização desta saída'), findsNothing);
      await _close(t, links);
    },
  );

  testWidgets('an invalid start link never offers activation', (t) async {
    final links = _Links(
      Uri.parse('vanridesdriver://run/start?intentToken=untrusted'),
    );
    final session =
        _Session()
          ..current = DriverSessionState.ready
          ..approved = true;
    final tracking = _Tracking();
    await _show(t, session, links, tracking);
    expect(find.text('Iniciar localização desta saída'), findsNothing);
    expect(tracking.starts, isEmpty);
    await _close(t, links);
  });

  testWidgets(
    'a scheduled network retry waits when the driver leaves the app',
    (t) async {
      final session =
          _Session()
            ..current = DriverSessionState.setupRequired
            ..approved = true
            ..syncNetworkFailures = 1;
      final links = _Links();
      await _show(t, session, links, _Tracking());
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      await t.pump();
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await t.pump(const Duration(seconds: 4));
      expect(
        session.actions.where((a) => a == DriverSessionAction.syncSession),
        hasLength(1),
      );
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await t.pumpAndSettle();
      expect(find.text('Aparelho vinculado'), findsOneWidget);
      expect(
        session.actions.where((a) => a == DriverSessionAction.syncSession),
        hasLength(2),
      );
      await _close(t, links);
    },
  );

  testWidgets(
    'a visible session retries a lost response once and preserves manual recovery',
    (t) async {
      final session =
          _Session()
            ..current = DriverSessionState.setupRequired
            ..approved = true
            ..syncNetworkFailures = 2;
      final links = _Links();
      await _show(t, session, links, _Tracking());
      await t.pump(const Duration(seconds: 3));
      await t.pumpAndSettle();
      await t.pump(const Duration(seconds: 30));
      expect(
        session.actions.where((a) => a == DriverSessionAction.syncSession),
        hasLength(2),
      );
      expect(find.text('Aparelho vinculado'), findsNothing);
      await t.tap(find.text('Atualizar vínculo'));
      await t.pumpAndSettle();
      expect(find.text('Aparelho vinculado'), findsOneWidget);
      await _close(t, links);
    },
  );

  testWidgets(
    'lost revoke shows the locally saved intent until the server confirms',
    (t) async {
      final session =
          _Session()
            ..current = DriverSessionState.ready
            ..loseRevokeResponse = true;
      final links = _Links();
      await _show(t, session, links, _Tracking());
      await t.tap(find.text('Desvincular este celular'));
      await t.pumpAndSettle();
      await t.tap(find.text('Desvincular'));
      await t.pumpAndSettle();
      expect(find.text('Desvinculação em andamento'), findsOneWidget);
      expect(find.text('Aparelho desvinculado'), findsNothing);
      expect(
        session.actions.where(
          (action) => action == DriverSessionAction.revokeSession,
        ),
        hasLength(1),
      );
      expect(session.actions.last, DriverSessionAction.getState);
      session.current = DriverSessionState.revoked;
      await t.tap(find.text('Atualizar vínculo'));
      await t.pumpAndSettle();
      expect(find.text('Aparelho desvinculado'), findsOneWidget);
      await _close(t, links);
    },
  );

  testWidgets(
    'icon to pending, reveal on demand, return to READY, then confirmed revoke',
    (t) async {
      final session = _Session(), links = _Links(), gps = _Tracking();
      await _show(t, session, links, gps);
      expect(session.actions, [DriverSessionAction.getState]);
      await t.tap(find.text('Vincular este celular'));
      await t.pumpAndSettle();
      expect(find.text('Confirme pelo WhatsApp'), findsOneWidget);
      expect(session.reveals, 0);
      await t.tap(find.text('Mostrar código'));
      await t.pumpAndSettle();
      expect(find.text('BCDFGHJZ'), findsOneWidget);
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      await t.pump();
      expect(find.text('BCDFGHJZ'), findsNothing);
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      session.approved = true;
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await t.pumpAndSettle();
      expect(find.text('Aparelho vinculado'), findsOneWidget);
      expect(find.text('Coleta parada'), findsOneWidget);
      await t.tap(find.text('Desvincular este celular'));
      await t.pumpAndSettle();
      await t.tap(find.text('Manter vínculo'));
      await t.pumpAndSettle();
      expect(
        session.actions.contains(DriverSessionAction.revokeSession),
        isFalse,
      );
      await t.tap(find.text('Desvincular este celular'));
      await t.pumpAndSettle();
      await t.tap(find.text('Desvincular'));
      await t.pumpAndSettle();
      expect(find.text('Aparelho desvinculado'), findsOneWidget);
      await _close(t, links);
    },
  );

  testWidgets(
    'an incoming link cannot create a pairing on a new installation',
    (t) async {
      for (final uri in [
        'vanridesdriver://pairing/return',
        'vanridesdriver://pairing/begin?origin=external',
      ]) {
        final s = _Session(), l = _Links(Uri.parse(uri));
        await _show(t, s, l, _Tracking());
        expect(find.text('Vincular este celular'), findsNothing);
        expect(s.actions, [DriverSessionAction.getState]);
        await _close(t, l);
      }
    },
  );

  testWidgets(
    'reopening via the launcher restores the explicit button after a link',
    (t) async {
      final session = _Session();
      final links = _Links(Uri.parse('vanridesdriver://pairing/return'));
      await _show(t, session, links, _Tracking());
      expect(find.text('Vincular este celular'), findsNothing);
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await t.pumpAndSettle();
      expect(find.text('Vincular este celular'), findsNothing);
      links.trusted = true;
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await t.pumpAndSettle();
      expect(find.text('Vincular este celular'), findsOneWidget);
      expect(
        session.actions.where(
          (action) => action == DriverSessionAction.beginPairing,
        ),
        isEmpty,
      );
      await _close(t, links);
    },
  );

  testWidgets('a warm return only synchronizes an existing request', (t) async {
    final s = _Session()..current = DriverSessionState.pending, l = _Links();
    await _show(t, s, l, _Tracking());
    final before = s.actions.length;
    l.stream.add(Uri.parse('vanridesdriver://pairing/return?code=ignored'));
    await t.pumpAndSettle();
    expect(s.actions.length, before);
    s.approved = true;
    l.stream.add(Uri.parse('vanridesdriver://pairing/return'));
    await t.pumpAndSettle();
    expect(find.text('Aparelho vinculado'), findsOneWidget);
    expect(
      s.actions.where((c) => c == DriverSessionAction.beginPairing),
      isEmpty,
    );
    await _close(t, l);
  });

  testWidgets(
    'a reveal that finishes after leaving the app cannot render the code',
    (t) async {
      final s =
              _Session()
                ..current = DriverSessionState.pending
                ..reveal = Completer<String?>(),
          l = _Links();
      await _show(t, s, l, _Tracking());
      await t.tap(find.text('Mostrar código'));
      await t.pump();
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      s.reveal!.complete('BCDFGHJZ');
      await t.pump();
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await t.pumpAndSettle();
      expect(find.text('BCDFGHJZ'), findsNothing);
      await _close(t, l);
    },
  );

  testWidgets(
    'an existing collector remains stoppable after updating the app',
    (t) async {
      final l = _Links(),
          gps =
              _Tracking()
                ..value = const DriverTrackingStatus(
                  state: 'TRACKING',
                  gpsState: 'STALE',
                );
      await _show(t, _Session(), l, gps);
      expect(find.text('Sem posição recente no receptor'), findsOneWidget);
      await t.tap(find.text('Parar localização'));
      await t.pumpAndSettle();
      expect(gps.stopped, 1);
      expect(find.text('Localização interrompida'), findsOneWidget);
      await _close(t, l);
    },
  );
}
