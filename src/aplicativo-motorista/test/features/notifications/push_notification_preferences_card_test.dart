import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_theme.dart';
import 'package:vanrides_frontend_dev/features/notifications/data/push_notification_gateway.dart';
import 'package:vanrides_frontend_dev/features/notifications/domain/push_notification_models.dart';
import 'package:vanrides_frontend_dev/features/notifications/presentation/push_notification_preferences_card.dart';

class _FakePushNotificationGateway implements PushNotificationGateway {
  _FakePushNotificationGateway(this.status);

  final PushPermissionStatus status;
  int requestPermissionCalls = 0;
  int currentPermissionStatusCalls = 0;

  @override
  Future<PushPermissionStatus> requestPermission() async {
    requestPermissionCalls += 1;
    return status;
  }

  @override
  Future<PushPermissionStatus> currentPermissionStatus() async {
    currentPermissionStatusCalls += 1;
    return status;
  }

  @override
  Future<PushNotificationToken?> getToken() async => null;

  @override
  Stream<Map<String, dynamic>> get onMessageOpenedApp => const Stream.empty();

  @override
  Stream<Map<String, dynamic>> get onMessageReceivedForeground =>
      const Stream.empty();

  @override
  Stream<PushNotificationToken> get onTokenRefresh => const Stream.empty();
}

void main() {
  testWidgets('mostra readiness e categorias sem pedir permissao', (
    tester,
  ) async {
    final gateway = _FakePushNotificationGateway(
      PushPermissionStatus.unavailable,
    );

    await _pumpCard(tester, gateway);

    expect(find.text('Notificações'), findsOneWidget);
    expect(find.text('Preparando'), findsOneWidget);
    expect(find.text('Viagem'), findsWidgets);
    expect(find.text('Presença'), findsWidgets);
    expect(find.text('Mensagens'), findsOneWidget);
    expect(find.text('Operação'), findsWidgets);
    expect(gateway.currentPermissionStatusCalls, 1);
    expect(gateway.requestPermissionCalls, 0);
  });

  testWidgets('mostra permissao negada com orientacao de ajustes', (
    tester,
  ) async {
    await _pumpCard(
      tester,
      _FakePushNotificationGateway(PushPermissionStatus.denied),
    );

    expect(find.text('Negadas'), findsOneWidget);
    expect(find.textContaining('Ajustes do sistema'), findsOneWidget);
  });

  testWidgets('mostra categorias ativas quando permissao ja existe', (
    tester,
  ) async {
    await _pumpCard(
      tester,
      _FakePushNotificationGateway(PushPermissionStatus.granted),
    );

    expect(find.text('Permitidas'), findsOneWidget);
    expect(find.textContaining('avisos operacionais'), findsOneWidget);
  });

  testWidgets('mostra notificacoes acionaveis com contexto por papel', (
    tester,
  ) async {
    await _pumpCard(
      tester,
      _FakePushNotificationGateway(PushPermissionStatus.granted),
      width: 430,
    );

    expect(find.text('Avisos planejados'), findsOneWidget);
    expect(find.text('Passageiro'), findsWidgets);
    expect(find.text('Responsável'), findsOneWidget);
    expect(find.text('Motorista'), findsOneWidget);
    expect(find.text('Empresa'), findsOneWidget);
    expect(find.text('Van iniciou a viagem'), findsOneWidget);
    expect(find.text('Van perto da parada'), findsOneWidget);
    expect(find.text('Abrir rota'), findsOneWidget);
    expect(find.text('Atendimento'), findsWidgets);
    expect(find.textContaining('pessoa acompanhada'), findsOneWidget);
  });

  testWidgets('mantem titulo em uma linha em largura de iPhone', (
    tester,
  ) async {
    await _pumpCard(
      tester,
      _FakePushNotificationGateway(PushPermissionStatus.unavailable),
      width: 342,
    );

    final titleSize = tester.getSize(find.text('Notificações'));

    expect(titleSize.height, lessThan(34));
    expect(tester.takeException(), isNull);
  });
}

Future<void> _pumpCard(
  WidgetTester tester,
  PushNotificationGateway gateway, {
  double? width,
}) async {
  final card = PushNotificationPreferencesCard(gateway: gateway);
  await tester.pumpWidget(
    MaterialApp(
      theme: VanRidesTheme.dark(),
      home: Scaffold(
        body: SingleChildScrollView(
          child: width == null ? card : SizedBox(width: width, child: card),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}
