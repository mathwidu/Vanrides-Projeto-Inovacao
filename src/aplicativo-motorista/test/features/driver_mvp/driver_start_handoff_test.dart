import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_run_intent.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_session_links.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_session_platform.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_tracking_platform.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/presentation/driver_collection_panel.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/presentation/driver_pairing_app.dart';

const _firstId = '12345678-1234-1234-1234-123456789012';
const _secondId = '12345678-1234-1234-1234-123456789013';
const _ready = DriverTrackingStatus(state: 'READY', permission: 'GRANTED');
const _tracking = DriverTrackingStatus(
  state: 'TRACKING',
  permission: 'GRANTED',
  gpsState: 'FRESH',
);

Uri _entry([String id = _firstId]) => Uri.parse(
  'vanridesdriver://run/start#v1.$id.7.${List.filled(43, 'A').join()}',
);

class _Session implements DriverSessionPlatform {
  @override
  Future<DriverSessionSnapshot> run(DriverSessionAction action) async =>
      const DriverSessionSnapshot(DriverSessionState.ready);

  @override
  Future<String?> showPairingCode() async => null;
}

class _Links implements DriverSessionLinks {
  final stream = StreamController<Uri>.broadcast(sync: true);
  int opens = 0;
  bool opened = true;
  bool fail = false;
  Completer<bool>? launch;

  @override
  Future<Uri?> initial() async => _entry();
  @override
  Stream<Uri> get incoming => stream.stream;
  @override
  Future<bool> isLauncherVisit() async => false;
  @override
  bool acceptsReturn(Uri uri) => DeviceSessionLinks.isReturn(uri, '');
  @override
  bool get hasConversation => true;
  @override
  Future<bool> openConversation() async {
    opens++;
    if (fail) throw PlatformException(code: 'UNAVAILABLE');
    return launch == null ? opened : await launch!.future;
  }
}

class _Tracking implements DriverTrackingPlatform, DriverRunPlatform {
  DriverTrackingStatus value = _ready;
  DriverTrackingStatus result = _tracking;
  Completer<DriverTrackingStatus>? activation;
  bool fail = false;
  int stops = 0;
  int syncs = 0;
  final starts = <DriverRunIntent>[];

  @override
  Future<DriverTrackingStatus> startRun(DriverRunIntent intent) async {
    starts.add(intent);
    if (fail) {
      value = _tracking;
      throw PlatformException(code: 'NETWORK_ERROR');
    }
    final next = activation == null ? result : await activation!.future;
    return value = next;
  }

  @override
  Future<DriverTrackingStatus> getStatus() async => value;
  @override
  Future<DriverTrackingStatus> syncStatus() async {
    syncs++;
    return value;
  }

  @override
  Future<DriverTrackingStatus> stop() async {
    stops++;
    return value = const DriverTrackingStatus(state: 'STOPPED');
  }

  @override
  Future<DriverTrackingStatus> requestPermission() async => value;
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<void> _show(WidgetTester t, _Links links, _Tracking gps) async {
  await t.pumpWidget(
    DriverPairingApp(session: _Session(), tracking: gps, links: links),
  );
  await t.pumpAndSettle();
}

Future<void> _start(WidgetTester t) async {
  final button = find.text('Começar e compartilhar localização');
  await t.ensureVisible(button);
  await t.tap(button);
  await t.pump();
}

Future<void> _close(WidgetTester t, _Links links) async {
  await t.pumpWidget(const SizedBox.shrink());
  await links.stream.close();
  t.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
}

void main() {
  testWidgets('only an explicit successful START returns to the chat once', (
    t,
  ) async {
    final links = _Links(), gps = _Tracking();
    await _show(t, links, gps);
    expect(links.opens, 0);
    expect(gps.starts, isEmpty);
    await _start(t);
    await t.pumpAndSettle();
    expect(links.opens, 1);
    expect(gps.starts, hasLength(1));
    expect(find.text('Parar localização'), findsOneWidget);
    await t.pump(const Duration(seconds: 6));
    t.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    t.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await t.pumpAndSettle();
    expect(links.opens, 1);
    expect(gps.starts, hasLength(1));
    expect(gps.stops, 0);
    await _close(t, links);
  });

  testWidgets('STARTING and a failed START never return using a later status', (
    t,
  ) async {
    for (final fail in [false, true]) {
      final links = _Links();
      final gps =
          _Tracking()
            ..fail = fail
            ..result = const DriverTrackingStatus(
              state: 'STARTING',
              permission: 'GRANTED',
            );
      await _show(t, links, gps);
      await _start(t);
      await t.pumpAndSettle();
      expect(links.opens, 0);
      gps.value = _tracking;
      await t.pump(const Duration(seconds: 6));
      await t.pumpAndSettle();
      expect(links.opens, 0);
      expect(gps.starts, hasLength(1));
      await _close(t, links);
    }
  });

  testWidgets('historical tracking, refresh and resume never return to chat', (
    t,
  ) async {
    final links = _Links(), gps = _Tracking()..value = _tracking;
    await _show(t, links, gps);
    await t.pump(const Duration(seconds: 6));
    t.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    t.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await t.pumpAndSettle();
    expect(gps.syncs, greaterThan(1));
    expect(gps.starts, isEmpty);
    expect(links.opens, 0);
    expect(gps.stops, 0);
    await _close(t, links);
  });

  testWidgets(
    'leaving foreground invalidates a START return even after resume',
    (t) async {
      final links = _Links();
      final gps = _Tracking()..activation = Completer<DriverTrackingStatus>();
      await _show(t, links, gps);
      await _start(t);
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await t.pump();
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      gps.activation!.complete(_tracking);
      await t.pumpAndSettle();
      expect(gps.starts, hasLength(1));
      expect(links.opens, 0);
      expect(find.text('Parar localização'), findsOneWidget);
      final manual = find.text('Voltar à conversa no WhatsApp');
      expect(manual, findsOneWidget);
      await t.ensureVisible(manual);
      await t.tap(manual);
      await t.pumpAndSettle();
      expect(links.opens, 1);
      expect(gps.starts, hasLength(1));
      expect(gps.stops, 0);
      await _close(t, links);
    },
  );

  testWidgets(
    'failed chat launch preserves collection and offers manual return',
    (t) async {
      for (final throws in [false, true]) {
        final links =
            _Links()
              ..opened = false
              ..fail = throws;
        final gps = _Tracking();
        await _show(t, links, gps);
        await _start(t);
        await t.pumpAndSettle();
        expect(links.opens, 1);
        expect(find.textContaining('Para voltar à conversa'), findsOneWidget);
        expect(find.text('Parar localização'), findsOneWidget);
        expect(find.text('Começar e compartilhar localização'), findsNothing);
        expect(find.text('Aparelho vinculado'), findsOneWidget);
        links.opened = true;
        links.fail = false;
        final manual = find.text('Voltar à conversa no WhatsApp');
        await t.ensureVisible(manual);
        await t.tap(manual);
        await t.pumpAndSettle();
        expect(links.opens, 2);
        expect(gps.starts, hasLength(1));
        expect(gps.stops, 0);
        await _close(t, links);
      }
    },
  );

  testWidgets('an old START cannot consume or return using a newer intent', (
    t,
  ) async {
    final links = _Links();
    final gps = _Tracking()..activation = Completer<DriverTrackingStatus>();
    await _show(t, links, gps);
    await _start(t);
    links.stream.add(_entry(_secondId));
    gps.activation!.complete(_tracking);
    await t.pumpAndSettle();
    final panel = t.widget<DriverCollectionPanel>(
      find.byType(DriverCollectionPanel),
    );
    expect(panel.intent?.id, _secondId);
    expect(gps.starts.single.id, _firstId);
    expect(links.opens, 0);
    await _close(t, links);
  });

  testWidgets(
    'a pending chat launch never keeps the collector or stop UI busy',
    (t) async {
      final links = _Links()..launch = Completer<bool>();
      final gps = _Tracking();
      await _show(t, links, gps);
      await _start(t);
      await t.pumpAndSettle();
      expect(links.opens, 1);
      expect(gps.starts, hasLength(1));
      expect(find.text('Posição recente recebida'), findsOneWidget);
      final stop = find.widgetWithText(OutlinedButton, 'Parar localização');
      expect(t.widget<OutlinedButton>(stop).onPressed, isNotNull);
      expect(find.text('Começar e compartilhar localização'), findsNothing);
      await t.pump(const Duration(seconds: 6));
      await t.pumpAndSettle();
      expect(gps.syncs, greaterThan(1));
      expect(t.widget<OutlinedButton>(stop).onPressed, isNotNull);
      expect(gps.starts, hasLength(1));
      expect(gps.stops, 0);
      links.launch!.complete(true);
      await t.pumpAndSettle();
      expect(links.opens, 1);
      expect(gps.starts, hasLength(1));
      await _close(t, links);
    },
  );

  testWidgets('a START reply after disposal never returns to chat', (t) async {
    final links = _Links();
    final gps = _Tracking()..activation = Completer<DriverTrackingStatus>();
    await _show(t, links, gps);
    await _start(t);
    await _close(t, links);
    gps.activation!.complete(_tracking);
    await t.pump();
    expect(links.opens, 0);
    expect(t.takeException(), isNull);
  });
}
