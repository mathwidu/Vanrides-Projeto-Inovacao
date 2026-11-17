import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_finish_intent.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_finish_platform.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_session_links.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_session_platform.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_tracking_platform.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/presentation/driver_finish_panel.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/presentation/driver_pairing_app.dart';
import 'driver_finish_intent_test.dart'
    show finishId, finishRun, finishLink, finishPreview, finishResult;

class _Session implements DriverSessionPlatform {
  @override
  Future<DriverSessionSnapshot> run(DriverSessionAction action) async =>
      const DriverSessionSnapshot(DriverSessionState.ready);
  @override
  Future<String?> showPairingCode() async => null;
}

class _Links implements DriverSessionLinks {
  final events = StreamController<Uri>.broadcast(sync: true);
  int opens = 0;
  bool icon = false;
  @override
  Future<Uri?> initial() async => icon ? null : finishLink();
  @override
  Stream<Uri> get incoming => events.stream;
  @override
  Future<bool> isLauncherVisit() async => false;
  @override
  bool acceptsReturn(Uri uri) => false;
  @override
  bool get hasConversation => true;
  @override
  Future<bool> openConversation() async {
    opens++;
    return true;
  }
}

class _Gps implements DriverTrackingPlatform {
  int stops = 0, starts = 0;
  DriverTrackingStatus value = const DriverTrackingStatus(
    state: 'TRACKING',
    permission: 'GRANTED',
    gpsState: 'FRESH',
  );
  @override
  Future<DriverTrackingStatus> getStatus() async => value;
  @override
  Future<DriverTrackingStatus> syncStatus() async => value;
  @override
  Future<DriverTrackingStatus> stop() async {
    stops++;
    return value = const DriverTrackingStatus(state: 'STOPPED');
  }

  @override
  Future<DriverTrackingStatus> start(String id) async {
    starts++;
    return value;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Finish implements DriverFinishPlatform {
  int confirms = 0;
  Completer<Map<Object?, Object?>> pending = Completer();
  bool restored = false;
  @override
  Future<DriverFinishPreview?> pendingFinish() async =>
      restored
          ? DriverFinishPreview.fromMap(
            finishPreview(),
            DriverFinishIntent.fromJournal(finishId, 7),
          )
          : null;
  @override
  Future<DriverFinishPreview> previewFinish(DriverFinishIntent intent) async =>
      DriverFinishPreview.fromMap(finishPreview(intent.id), intent);
  @override
  Future<Map<Object?, Object?>> finishRun(DriverFinishIntent intent) {
    confirms++;
    return pending.future;
  }
}

Future<void> _show(
  WidgetTester t,
  _Links links,
  _Gps gps,
  _Finish finish,
) async {
  await t.pumpWidget(
    DriverPairingApp(
      session: _Session(),
      tracking: gps,
      links: links,
      finishing: finish,
    ),
  );
  await t.pumpAndSettle();
}

Future<void> _tapFinish(WidgetTester t) async {
  final button = find.text('Finalizar viagem e parar localização');
  await t.ensureVisible(button);
  await t.tap(button);
  await t.pump();
}

Future<void> _close(WidgetTester t, _Links links) async {
  await t.pumpWidget(const SizedBox.shrink());
  await links.events.close();
  t.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
}

void main() {
  testWidgets(
    'an expired pending authorization asks for a new link without navigation',
    (t) async {
      final links = _Links(), gps = _Gps(), finish = _Finish();
      await _show(t, links, gps, finish);
      await _tapFinish(t);
      finish.pending.completeError(
        PlatformException(code: 'FINISH_REVIEW_REQUIRED'),
      );
      await t.pumpAndSettle();
      expect(find.textContaining('para receber um novo link'), findsOneWidget);
      expect(links.opens, 0);
      expect(gps.starts, 0);
      expect(finish.confirms, 1);
      await _close(t, links);
    },
  );
  testWidgets(
    'opening by icon exposes the original journal without navigating or new GPS',
    (t) async {
      final links = _Links()..icon = true,
          gps = _Gps(),
          finish = _Finish()..restored = true;
      await _show(t, links, gps, finish);
      expect(find.text('Finalização pendente'), findsOneWidget);
      expect(links.opens, 0);
      expect(finish.confirms, 0);
      expect(gps.starts, 0);
      final retry = find.text('Tentar finalizar novamente');
      await t.ensureVisible(retry);
      await t.tap(retry);
      await t.pump();
      finish.pending.complete(finishResult());
      await t.pumpAndSettle();
      expect(links.opens, 1);
      expect(finish.confirms, 1);
      expect(gps.starts, 0);
      await _close(t, links);
    },
  );
  testWidgets(
    'preview is read only and only the explicit exact ACK returns once',
    (t) async {
      final links = _Links(), gps = _Gps(), finish = _Finish();
      await _show(t, links, gps, finish);
      expect(find.textContaining('Faculdade sintética'), findsOneWidget);
      expect(finish.confirms, 0);
      expect(gps.stops, 0);
      expect(links.opens, 0);
      await _tapFinish(t);
      expect(links.opens, 0);
      finish.pending.complete(finishResult());
      await t.pumpAndSettle();
      expect(links.opens, 1);
      expect(finish.confirms, 1);
      expect(gps.starts, 0);
      await t.pump(const Duration(seconds: 6));
      await t.pumpAndSettle();
      expect(links.opens, 1);
      await _close(t, links);
    },
  );
  testWidgets('local stop stays available while FINISH is awaiting network', (
    t,
  ) async {
    final links = _Links(), gps = _Gps(), finish = _Finish();
    await _show(t, links, gps, finish);
    await _tapFinish(t);
    final stop = find.text('Parar localização');
    await t.ensureVisible(stop);
    await t.tap(stop);
    await t.pump();
    expect(gps.stops, 1);
    expect(finish.confirms, 1);
    expect(links.opens, 0);
    expect(gps.starts, 0);
    finish.pending.complete(finishResult());
    await t.pumpAndSettle();
    expect(links.opens, 1);
    expect(gps.stops, 1);
    await _close(t, links);
  });
  testWidgets(
    'an unconfirmed stop or another session never completes the handoff',
    (t) async {
      final links = _Links(), gps = _Gps(), finish = _Finish();
      await _show(t, links, gps, finish);
      await _tapFinish(t);
      final invalid = finishResult();
      invalid['receipt'] = {
        ...invalid['receipt'] as Map,
        'trackingSessionId': finishRun,
      };
      finish.pending.complete(invalid);
      await t.pumpAndSettle();
      expect(links.opens, 0);
      expect(find.textContaining('Não consegui confirmar'), findsOneWidget);
      expect(finish.confirms, 1);
      await _close(t, links);
    },
  );
  testWidgets(
    'leaving foreground keeps manual return without late navigation',
    (t) async {
      final links = _Links(), gps = _Gps(), finish = _Finish();
      await _show(t, links, gps, finish);
      await _tapFinish(t);
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      t.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      finish.pending.complete(finishResult());
      await t.pumpAndSettle();
      expect(links.opens, 0);
      final manual = find.text('Voltar à conversa no WhatsApp');
      await t.ensureVisible(manual);
      await t.tap(manual);
      await t.pumpAndSettle();
      expect(links.opens, 1);
      expect(finish.confirms, 1);
      expect(gps.starts, 0);
      await _close(t, links);
    },
  );
  testWidgets(
    'a replaced intent and disposal cannot consume or navigate the new action',
    (t) async {
      final links = _Links(), gps = _Gps(), finish = _Finish();
      await _show(t, links, gps, finish);
      await _tapFinish(t);
      links.events.add(finishLink(finishRun));
      finish.pending.complete(finishResult(finishId));
      await t.pumpAndSettle();
      expect(
        t.widget<DriverFinishPanel>(find.byType(DriverFinishPanel)).intent.id,
        finishRun,
      );
      expect(links.opens, 0);
      await _close(t, links);
    },
  );
  testWidgets('a finalization reply after disposal never returns to WhatsApp', (
    t,
  ) async {
    final links = _Links(), gps = _Gps(), finish = _Finish();
    await _show(t, links, gps, finish);
    await _tapFinish(t);
    await _close(t, links);
    finish.pending.complete(finishResult());
    await t.pump();
    expect(links.opens, 0);
    expect(t.takeException(), isNull);
  });
}
