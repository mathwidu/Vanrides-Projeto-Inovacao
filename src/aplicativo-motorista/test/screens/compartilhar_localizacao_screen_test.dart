import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator/geolocator.dart';
import 'package:vanrides_frontend_dev/features/driver_location/presentation/compartilhar_localizacao_screen.dart';

import '../support/fakes/fake_location_gateway.dart';
import '../support/fakes/fake_websocket_service.dart';
import '../support/test_position.dart';

void main() {
  testWidgets('connects and disconnects websocket on lifecycle', (
    tester,
  ) async {
    final gateway = FakeLocationGateway();
    final ws = FakeWebSocketService();

    await tester.pumpWidget(
      MaterialApp(
        home: CompartilharLocalizacaoScreen(
          motoristaId: 1,
          email: 'driver@vanrides.dev',
          senha: 'Secret123',
          accessToken: 'eyJhbGciOiJIUzI1NiJ9.eyJjb21wYW55SWQiOjY2fQ.signature',
          locationGateway: gateway,
          webSocketService: ws,
        ),
      ),
    );

    expect(ws.connectCalls, 1);
    expect(ws.lastCompanyId, 66);
    expect(ws.lastAuthorizationHeader, startsWith('Bearer '));

    await tester.pumpWidget(const MaterialApp(home: SizedBox()));
    expect(ws.disconnectCalls, 1);

    await gateway.dispose();
  });

  testWidgets('deniedForever shows snackbar with settings action', (
    tester,
  ) async {
    final gateway =
        FakeLocationGateway()..permission = LocationPermission.deniedForever;
    final ws = FakeWebSocketService();

    await tester.pumpWidget(
      MaterialApp(
        home: CompartilharLocalizacaoScreen(
          motoristaId: 1,
          email: 'driver@vanrides.dev',
          senha: 'Secret123',
          accessToken: 'eyJhbGciOiJIUzI1NiJ9.eyJjb21wYW55SWQiOjY2fQ.signature',
          locationGateway: gateway,
          webSocketService: ws,
        ),
      ),
    );

    await tester.tap(find.text('Iniciar compartilhamento'));
    await tester.pumpAndSettle();

    expect(
      find.text(
        'Permissão de localização permanentemente negada. Habilite em Ajustes.',
      ),
      findsOneWidget,
    );
    expect(find.text('Abrir Ajustes'), findsOneWidget);

    await tester.tap(find.text('Abrir Ajustes'));
    await tester.pumpAndSettle();

    expect(gateway.openAppSettingsCalls, 1);
    await gateway.dispose();
  });

  testWidgets(
    'service disabled prompts to enable location and opens settings',
    (tester) async {
      final gateway = FakeLocationGateway()..serviceEnabled = false;
      final ws = FakeWebSocketService();

      await tester.pumpWidget(
        MaterialApp(
          home: CompartilharLocalizacaoScreen(
            motoristaId: 1,
            email: 'driver@vanrides.dev',
            senha: 'Secret123',
            accessToken:
                'eyJhbGciOiJIUzI1NiJ9.eyJjb21wYW55SWQiOjY2fQ.signature',
            locationGateway: gateway,
            webSocketService: ws,
          ),
        ),
      );

      await tester.tap(find.text('Iniciar compartilhamento'));
      await tester.pumpAndSettle();

      expect(
        find.text('Por favor, ative a localização no dispositivo'),
        findsOneWidget,
      );
      expect(gateway.openLocationSettingsCalls, 1);
      await gateway.dispose();
    },
  );

  testWidgets('start/stop sharing sends locations and cancels stream', (
    tester,
  ) async {
    final gateway =
        FakeLocationGateway()
          ..permission = LocationPermission.whileInUse
          ..currentPosition = testPosition(latitude: 9, longitude: 19);
    final ws = FakeWebSocketService();

    await tester.pumpWidget(
      MaterialApp(
        home: CompartilharLocalizacaoScreen(
          motoristaId: 42,
          email: 'driver@vanrides.dev',
          senha: 'Secret123',
          accessToken: 'eyJhbGciOiJIUzI1NiJ9.eyJjb21wYW55SWQiOjY2fQ.signature',
          locationGateway: gateway,
          webSocketService: ws,
        ),
      ),
    );

    expect(
      find.text('Localização não está sendo compartilhada'),
      findsOneWidget,
    );

    await tester.tap(find.text('Iniciar compartilhamento'));
    await tester.pump();

    expect(
      find.text('Compartilhando localização em tempo real'),
      findsOneWidget,
    );
    expect(gateway.lastAllowBackground, isFalse);

    gateway.positionController.add(testPosition(latitude: 10, longitude: 20));
    await tester.pump();

    expect(ws.sentLocations, [
      {'latitude': 9.0, 'longitude': 19.0, 'motoristaId': 42},
      {'latitude': 10.0, 'longitude': 20.0, 'motoristaId': 42},
    ]);

    await tester.tap(find.text('Parar compartilhamento'));
    await tester.pump();

    expect(
      find.text('Localização não está sendo compartilhada'),
      findsOneWidget,
    );

    gateway.positionController.add(testPosition(latitude: 11, longitude: 21));
    await tester.pump();

    expect(ws.sentLocations, [
      {'latitude': 9.0, 'longitude': 19.0, 'motoristaId': 42},
      {'latitude': 10.0, 'longitude': 20.0, 'motoristaId': 42},
    ]);

    await gateway.dispose();
  });
}
