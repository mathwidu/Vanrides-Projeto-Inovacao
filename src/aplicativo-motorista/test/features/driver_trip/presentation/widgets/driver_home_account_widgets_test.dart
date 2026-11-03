import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_tokens.dart';

import '../../../../support/fakes/fake_account_profile_gateway.dart';

void main() {
  testWidgets(
    'DriverAccountTab renders common profile without selected trip data',
    (tester) async {
      var logoutTaps = 0;
      final firstTrip = _driverTripRecord(
        id: 1,
        servicoNome: 'Linha Centro',
        passageiroNome: 'Ana Passageira',
      );
      final secondTrip = _driverTripRecord(
        id: 2,
        servicoNome: 'Linha Norte',
        passageiroNome: 'Bruno Passageiro',
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: DriverAccountTab(
              nome: 'Maria Motorista',
              email: 'maria@vanrides.test',
              trip: firstTrip,
              dayTrips: [firstTrip, secondTrip],
              onLogout: () => logoutTaps++,
            ),
          ),
        ),
      );

      expect(
        find.byKey(const Key('account.profile.avatarInitials')),
        findsOneWidget,
      );
      expect(find.text('Maria Motorista'), findsOneWidget);
      expect(find.text('maria@vanrides.test'), findsWidgets);
      expect(find.text('Motorista operacional'), findsWidgets);
      expect(find.text('Nascimento'), findsOneWidget);
      expect(
        find.byKey(const Key('driver.account.editProfileButton')),
        findsOneWidget,
      );
      expect(find.text('Viagem selecionada'), findsNothing);
      expect(find.text('Linha Centro'), findsNothing);
      expect(find.text('Ana Passageira'), findsNothing);
      expect(find.text('2 viagens hoje'), findsNothing);
      expect(find.text('2 paradas hoje'), findsNothing);

      await tester.scrollUntilVisible(
        find.byKey(const Key('driver.account.logout.darkButton')),
        260,
      );
      await tester.drag(find.byType(ListView), const Offset(0, -120));
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const Key('driver.account.logout.darkButton')),
      );
      await tester.pumpAndSettle();

      expect(logoutTaps, 1);
    },
  );

  testWidgets('DriverAccountTab usa superficie dark de conta', (tester) async {
    final trip = _driverTripRecord(
      id: 3,
      servicoNome: 'Linha Sul',
      passageiroNome: 'Clara Passageira',
    );

    await tester.pumpWidget(
      MaterialApp(
        home: DriverAccountTab(
          nome: 'Maria Motorista',
          email: 'maria@vanrides.test',
          trip: trip,
          dayTrips: [trip],
          onLogout: () {},
        ),
      ),
    );

    final root = tester.widget<Container>(
      find.byKey(const Key('driver.account.darkSurface')),
    );
    expect(root.color, VanRidesMapTokens.backgroundDeep);

    await tester.scrollUntilVisible(
      find.byKey(const Key('driver.account.logout.darkButton')),
      260,
    );
    await tester.pumpAndSettle();

    final logout = tester.widget<FilledButton>(
      find.byKey(const Key('driver.account.logout.darkButton')),
    );
    final background = logout.style?.backgroundColor?.resolve(<WidgetState>{});
    final foreground = logout.style?.foregroundColor?.resolve(<WidgetState>{});
    expect(background, isNot(Colors.white));
    expect(foreground, VanRidesMapTokens.textPrimary);
  });

  testWidgets('DriverAccountTab carrega perfil real do motorista', (
    tester,
  ) async {
    final trip = _driverTripRecord(
      id: 4,
      servicoNome: 'Linha Feevale',
      passageiroNome: 'Davi Passageiro',
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DriverAccountTab(
            nome: 'Maria Motorista',
            email: 'maria@vanrides.test',
            enabled: true,
            accountProfileGateway: const FakeAccountProfileGateway.driver(),
            trip: trip,
            dayTrips: [trip],
            onLogout: () {},
          ),
        ),
      ),
    );

    await tester.pump();
    await tester.pumpAndSettle();

    expect(find.text('Maria Perfil Real'), findsOneWidget);
    expect(find.text('maria.real@vanrides.dev'), findsWidgets);
    expect(find.text('Motorista'), findsWidgets);
    expect(find.text('E-mail verificado'), findsOneWidget);
    expect(find.text('15/01/1990'), findsOneWidget);

    await tester.drag(find.byType(ListView), const Offset(0, -420));
    await tester.pumpAndSettle();

    expect(find.text('51999990007'), findsOneWidget);
    expect(find.text('Feevale'), findsWidgets);
  });

  testWidgets('DriverAccountTab renders fallback state without trip', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DriverAccountTab(
            nome: '',
            email: 'sem-viagem@vanrides.test',
            trip: null,
            dayTrips: const [],
            onLogout: () {},
          ),
        ),
      ),
    );

    expect(
      find.byKey(const Key('account.profile.avatarInitials')),
      findsOneWidget,
    );
    expect(find.text('Sem viagem publicada hoje'), findsNothing);
    expect(find.text('Sem proxima parada'), findsNothing);
    expect(find.text('0 viagens hoje'), findsNothing);
    expect(find.text('0 paradas hoje'), findsNothing);
    expect(find.text('sem-viagem@vanrides.test'), findsWidgets);
  });
}

DriverTripRecord _driverTripRecord({
  required int id,
  required String servicoNome,
  required String passageiroNome,
}) {
  final stop = DriverTripStopRecord(
    id: id + 100,
    ordem: 1,
    status: TripTrackingStopStatus.pending,
    etaMinutos: 8,
    confirmadoPresenca: true,
    proximaParada: true,
    passageiroId: id + 200,
    passageiroNome: passageiroNome,
    menorDeIdade: false,
  );

  return DriverTripRecord(
    id: id,
    rotaBaseId: 7,
    rotaNome: 'Rota Centro',
    dataOperacao: DateTime(2026, 6, 11),
    status: TripTrackingStatus.planned,
    rotaVersao: 2,
    etaMinutosAteProximaParada: 8,
    etaAtualizadoEm: DateTime(2026, 6, 11, 7, 30),
    motorista: const TripTrackingDriverRecord(id: 77, nome: 'Maria Motorista'),
    proximaParada: stop,
    paradas: [stop],
    rotaGeografica: null,
    horarioPartida: '07:30',
    servicoNome: servicoNome,
    tipoOperacao: 'IDA',
  );
}
