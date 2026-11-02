import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/home_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';

import 'home_motorista_test_support.dart';

void main() {
  setUpAll(() async {
    await initializeDateFormatting('pt_BR');
  });

  testWidgets('backend route plan fixture powers internal driver guidance', (
    tester,
  ) async {
    await setPhoneViewport(tester);
    final payload =
        jsonDecode(
              File(
                'test/fixtures/contracts/driver_route_plan_payload_validated_ready.json',
              ).readAsStringSync(),
            )
            as Map<String, dynamic>;
    final trip = DriverTripRecord.fromJson(payload);

    expect(trip.status, TripTrackingStatus.inProgress);
    expect(trip.navigationRoutePlan?.routeId, 'route-br-rs-001');
    expect(trip.navigationRoutePlan?.coverageStatus, 'validada');
    expect(trip.navigationRoutePlan?.offlinePackStatus, 'ready');

    await pumpHomeMotorista(tester, tripGateway: FakeDriverTripGateway(trip));
    await tester.pumpAndSettle();

    expect(find.text('Siga pela Avenida Ipiranga.'), findsOneWidget);
    expect(find.text('Use o fallback para seguir com seguranca'), findsNothing);
    expect(
      find.byKey(HomeMotoristaScreen.seguirNavegacaoButtonKey),
      findsOneWidget,
    );
  });
}
