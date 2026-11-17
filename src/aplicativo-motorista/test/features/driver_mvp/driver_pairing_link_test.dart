import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_pairing_intent.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_pairing_link_platform.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_session_platform.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_session_links.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_tracking_platform.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/presentation/driver_pairing_controller.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/presentation/driver_pairing_app.dart';

const id = '12345678-1234-1234-1234-123456789012';
final token = List.filled(43, 'A').join();
final address = 'vanridesdriver://pair/connect#v1.$id.$token';

class Session implements DriverSessionPlatform {
  DriverSessionSnapshot value = const DriverSessionSnapshot(
    DriverSessionState.unpaired,
  );
  final calls = <DriverSessionAction>[];
  bool approve = false;
  @override
  Future<DriverSessionSnapshot> run(DriverSessionAction action) async {
    calls.add(action);
    if (approve && action == DriverSessionAction.syncSession) {
      value = const DriverSessionSnapshot(
        DriverSessionState.ready,
        handoff: true,
      );
    }
    return value;
  }

  @override
  Future<String?> showPairingCode() async =>
      throw StateError('No reveal in handoff');
}

class Links implements DriverSessionLinks {
  Links(this.url);
  final Uri? url;
  final stream = StreamController<Uri>.broadcast();
  int opens = 0;
  bool launchWorks = true;
  @override
  Future<Uri?> initial() async => url;
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
    return launchWorks;
  }
}

class Pairing implements DriverPairingLinkPlatform {
  Pairing(this.session);
  final Session session;
  int preparations = 0, claims = 0, consumptions = 0;
  bool replacement = false, connected = false;
  Completer<void>? prepareWait, claimWait;
  PlatformException? failure;
  @override
  Future<DriverPairingReview> prepare(DriverPairingIntent intent) async {
    preparations++;
    if (prepareWait != null) await prepareWait!.future;
    if (failure != null) throw failure!;
    return DriverPairingReview.fromNative({
      'preview': {
        'linkId': intent.id,
        'state': 'READY',
        'expiresAt': '2099-01-01T00:00:00Z',
        'serverTime': '2098-12-31T23:55:00Z',
        'sameCurrentDevice': connected,
        'replacesDevice': replacement,
        'alreadyConnected': connected,
      },
      'session': {'state': connected ? 'READY' : session.value.state.wire},
    }, intent);
  }

  @override
  Future<DriverSessionSnapshot> claim(DriverPairingIntent intent) async {
    claims++;
    if (claimWait != null) await claimWait!.future;
    return session.value = DriverSessionSnapshot(
      DriverSessionState.pending,
      expiresAt: DateTime.utc(2099),
      handoff: true,
    );
  }

  @override
  Future<bool> completeReturn() async {
    consumptions++;
    return consumptions == 1;
  }
}

class GPS implements DriverTrackingPlatform {
  int starts = 0, stops = 0, permissions = 0;
  @override
  Future<DriverTrackingStatus> getStatus() async =>
      const DriverTrackingStatus();
  @override
  Future<DriverTrackingStatus> syncStatus() => getStatus();
  @override
  Future<DriverTrackingStatus> stop() async {
    stops++;
    return const DriverTrackingStatus(state: 'STOPPED');
  }

  @override
  Future<DriverTrackingStatus> requestPermission() async {
    permissions++;
    return const DriverTrackingStatus();
  }

  @override
  dynamic noSuchMethod(Invocation invocation) {
    starts++;
    throw StateError('No GPS in pairing');
  }
}

void main() {
  test('connect parser is raw strict and has a separate purpose', () {
    expect(DriverPairingIntent.parseRaw(address), isNotNull);
    expect(
      DriverPairingIntent.parseRaw(
        'https://links.example.test/motorista/conectar#v1.$id.$token',
        host: 'links.example.test',
      ),
      isNotNull,
    );
    for (final raw in [
      address.replaceFirst('1', '%31'),
      '$address?x=1',
      address.replaceFirst('pair/connect', 'run/start'),
      address.replaceFirst(token, '${token.substring(0, 42)}B'),
      'https://elsewhere.test/motorista/conectar#v1.$id.$token',
    ]) {
      expect(
        DriverPairingIntent.parseRaw(raw, host: 'links.example.test'),
        isNull,
      );
    }
    expect(
      DriverPairingIntent.parseRaw(address).toString(),
      isNot(contains(token)),
    );
  });
  test(
    'opening only reviews locally; one explicit gesture claims and returns once',
    () async {
      final session = Session(), links = Links(Uri.parse(address));
      final pairing = Pairing(session),
          controller = DriverPairingController(
            session,
            links,
            pairing: pairing,
          );
      await controller.initialize();
      expect(pairing.preparations, 0);
      expect(pairing.claims, 0);
      expect(links.opens, 0);
      await controller.connect();
      await Future<void>.delayed(Duration.zero);
      expect(pairing.preparations, 1);
      expect(pairing.claims, 1);
      expect(links.opens, 1);
      expect(controller.code, isNull);
      expect(controller.snapshot!.handoff, isTrue);
      expect(session.calls, isNot(contains(DriverSessionAction.beginPairing)));
      controller.dispose();
      await links.stream.close();
    },
  );
  test(
    'replacement needs specific second gesture; already connected never claims',
    () async {
      final session = Session(),
          links = Links(Uri.parse(address)),
          pairing = Pairing(Session());
      pairing.replacement = true;
      final controller = DriverPairingController(
        session,
        links,
        pairing: pairing,
      );
      await controller.initialize();
      await controller.connect();
      expect(controller.requiresReplacement, isTrue);
      expect(pairing.claims, 0);
      await controller.connect(replace: true);
      expect(pairing.claims, 1);
      controller.dispose();
      await links.stream.close();
      final nextLinks = Links(Uri.parse(address)),
          existing = Pairing(session)..connected = true;
      final next = DriverPairingController(
        session,
        nextLinks,
        pairing: existing,
      );
      await next.initialize();
      await next.connect();
      expect(existing.claims, 0);
      expect(next.snapshot!.state, DriverSessionState.ready);
      next.dispose();
      await nextLinks.stream.close();
    },
  );
  test(
    'background or replaced intent while preparing cannot claim or return',
    () async {
      for (final replace in [false, true]) {
        final session = Session(),
            links = Links(Uri.parse(address)),
            pairing = Pairing(Session())..prepareWait = Completer<void>();
        final controller = DriverPairingController(
          session,
          links,
          pairing: pairing,
        );
        await controller.initialize();
        final action = controller.connect();
        if (replace) {
          links.stream.add(
            Uri.parse(
              address.replaceFirst(id, '22345678-1234-1234-1234-123456789012'),
            ),
          );
          await Future<void>.delayed(Duration.zero);
        } else {
          controller.foreground(false);
          controller.foreground(true);
        }
        pairing.prepareWait!.complete();
        await action;
        expect(pairing.claims, 0);
        expect(links.opens, 0);
        if (replace) expect(controller.pairingIntent!.id, startsWith('2'));
        controller.dispose();
        await links.stream.close();
      }
    },
  );
  test(
    'a pending claim reply cannot clear a newer link or navigate after background',
    () async {
      for (final replace in [false, true]) {
        final session = Session(),
            links = Links(Uri.parse(address)),
            pairing = Pairing(Session())..claimWait = Completer<void>();
        final controller = DriverPairingController(
          session,
          links,
          pairing: pairing,
        );
        await controller.initialize();
        final action = controller.connect();
        await Future<void>.delayed(Duration.zero);
        expect(pairing.claims, 1);
        if (replace) {
          links.stream.add(
            Uri.parse(
              address.replaceFirst(id, '22345678-1234-1234-1234-123456789012'),
            ),
          );
          await Future<void>.delayed(Duration.zero);
        } else {
          controller.foreground(false);
          controller.foreground(true);
        }
        pairing.claimWait!.complete();
        await action;
        await Future<void>.delayed(Duration.zero);
        expect(links.opens, 0);
        if (replace) {
          expect(controller.pairingIntent!.id, startsWith('2'));
        } else {
          expect(controller.conversationReturnPending, isTrue);
        }
        controller.dispose();
        await links.stream.close();
      }
    },
  );
  test(
    'launch failure preserves pending and explicit return consumes completion once',
    () async {
      final session = Session(),
          links = Links(Uri.parse(address))..launchWorks = false;
      final pairing = Pairing(session),
          controller = DriverPairingController(
            session,
            links,
            pairing: pairing,
          );
      await controller.initialize();
      await controller.connect();
      await Future<void>.delayed(Duration.zero);
      expect(controller.conversationReturnPending, isTrue);
      expect(pairing.claims, 1);
      links.launchWorks = true;
      session.approve = true;
      await controller.refresh();
      expect(links.opens, 1); // Poll/recovery never opens chat.
      links.stream.add(Uri.parse('vanridesdriver://pairing/return'));
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);
      expect(links.opens, 2);
      expect(pairing.consumptions, 1);
      expect(pairing.claims, 1);
      links.stream.add(Uri.parse('vanridesdriver://pairing/return'));
      await Future<void>.delayed(Duration.zero);
      expect(links.opens, 2);
      controller.dispose();
      await links.stream.close();
    },
  );
  testWidgets('replacement UI requires Usar este celular before claim', (
    t,
  ) async {
    final session = Session(), links = Links(Uri.parse(address)), gps = GPS();
    final pairing = Pairing(session)..replacement = true;
    await t.pumpWidget(
      DriverPairingApp(
        session: session,
        links: links,
        tracking: gps,
        pairing: pairing,
      ),
    );
    await t.pumpAndSettle();
    await t.tap(find.text('Conectar este celular'));
    await t.pumpAndSettle();
    expect(pairing.claims, 0);
    expect(find.textContaining('Outro celular está conectado'), findsOneWidget);
    await t.ensureVisible(find.text('Usar este celular'));
    await t.tap(find.text('Usar este celular'));
    await t.pumpAndSettle();
    expect(pairing.claims, 1);
    expect(links.opens, 1);
    expect(gps.starts, 0);
    expect(gps.permissions, 0);
    expect(find.text('Mostrar código'), findsNothing);
    await t.pumpWidget(const SizedBox.shrink());
    await links.stream.close();
  });
  testWidgets(
    'link UI hides codes, reports active trip, and never starts GPS',
    (t) async {
      final session = Session(), links = Links(Uri.parse(address)), gps = GPS();
      final pairing = Pairing(session)
        ..failure = PlatformException(code: 'PAIRING_ACTIVE_RUN');
      await t.pumpWidget(
        DriverPairingApp(
          session: session,
          links: links,
          tracking: gps,
          pairing: pairing,
        ),
      );
      await t.pumpAndSettle();
      expect(pairing.preparations, 0);
      expect(find.text('Conectar este celular'), findsOneWidget);
      expect(find.text('Mostrar código'), findsNothing);
      expect(find.text('Copiar código'), findsNothing);
      expect(find.textContaining(token), findsNothing);
      await t.tap(find.text('Conectar este celular'));
      await t.pumpAndSettle();
      expect(find.textContaining('Há uma viagem em andamento'), findsOneWidget);
      expect(pairing.claims, 0);
      expect(gps.starts, 0);
      expect(gps.stops, 0);
      expect(gps.permissions, 0);
      await t.pumpWidget(const SizedBox.shrink());
      await links.stream.close();
    },
  );
}
