import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/account/data/account_profile_service.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_profile.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_profile_update.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_tokens.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_home_account_widgets.dart';

import '../../../../support/fakes/fake_account_profile_gateway.dart';

TripTrackingRecord _trip({
  required int id,
  required String servicoNome,
  required String passageiroNome,
  int? etaMinutosAtePassageiro = 12,
}) {
  return TripTrackingRecord(
    id: id,
    rotaNome: 'Rota Escolar',
    servicoId: id + 100,
    servicoNome: servicoNome,
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 3, 20),
    status: TripTrackingStatus.inProgress,
    rotaVersao: 1,
    etaMinutosAtePassageiro: etaMinutosAtePassageiro,
    etaAtualizadoEm: DateTime(2026, 3, 20, 7, 30),
    motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista Portal'),
    rotaGeografica: null,
    paradaDoPassageiro: TripTrackingPassengerStopRecord(
      id: id + 300,
      ordem: 1,
      status: TripTrackingStopStatus.pending,
      etaMinutos: etaMinutosAtePassageiro,
      confirmadoPresenca: true,
      proximaParada: true,
      passageiroId: 21,
      passageiroNome: passageiroNome,
      menorDeIdade: true,
    ),
    horarioPartida: '07:30',
  );
}

void main() {
  testWidgets('exibe perfil comum sem ETA ou viagem selecionada', (
    tester,
  ) async {
    final trip = _trip(
      id: 30,
      servicoNome: 'Linha Manha',
      passageiroNome: 'Ana Escola',
    );
    final gateway = _EditableAccountProfileGateway(
      const AccountProfile(
        id: 21,
        tipoUsuario: 'Passageiro',
        papel: 'PASSENGER',
        nome: 'Ana Perfil Real',
        email: 'ana.real@vanrides.dev',
        telefone: '51999990021',
        dataNascimento: '2012-05-20',
        fotoPerfilUrl: _tinyPngDataUri,
        emailVerified: true,
        empresa: AccountCompany(id: 5, nome: 'Feevale'),
        passageiroId: 21,
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: PassengerHomeAccountTab(
          nome: 'Ana',
          email: 'ana@vanrides.dev',
          enabled: true,
          accountProfileGateway: gateway,
          trip: trip,
          dayTrips: [
            trip,
            _trip(
              id: 31,
              servicoNome: 'Linha Tarde',
              passageiroNome: 'Ana Casa',
            ),
          ],
          onLogout: () {},
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Ana Perfil Real'), findsOneWidget);
    expect(find.text('ana.real@vanrides.dev'), findsWidgets);
    expect(find.text('Passageiro'), findsWidgets);
    expect(find.text('Nascimento'), findsOneWidget);
    expect(find.text('20/05/2012'), findsOneWidget);
    expect(find.text('51999990021'), findsOneWidget);
    expect(
      find.byKey(const Key('account.profile.avatarImage')),
      findsOneWidget,
    );
    expect(
      find.byKey(const Key('passenger.account.editProfileButton')),
      findsOneWidget,
    );
    expect(find.text('ETA atual'), findsNothing);
    expect(find.text('12 min'), findsNothing);
    expect(find.text('Viagem selecionada'), findsNothing);
    expect(find.text('Linha Manha'), findsNothing);
    expect(find.text('Ana Escola'), findsNothing);
  });

  testWidgets('usa superficie dark premium em vez de cards brancos', (
    tester,
  ) async {
    final trip = _trip(
      id: 30,
      servicoNome: 'Linha Manha',
      passageiroNome: 'Ana Escola',
    );

    await tester.pumpWidget(
      MaterialApp(
        home: PassengerHomeAccountTab(
          nome: 'Ana',
          email: 'ana@vanrides.dev',
          trip: trip,
          dayTrips: [trip],
          onLogout: () {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    final root = tester.widget<Container>(
      find.byKey(const Key('passenger.account.darkSurface')),
    );
    expect(root.color, VanRidesMapTokens.backgroundDeep);

    await tester.dragUntilVisible(
      find.byKey(const Key('passenger.account.logout.darkButton')),
      find.byType(ListView),
      const Offset(0, -300),
    );
    await tester.pumpAndSettle();

    final logout = tester.widget<FilledButton>(
      find.byKey(const Key('passenger.account.logout.darkButton')),
    );
    final background = logout.style?.backgroundColor?.resolve(<WidgetState>{});
    final foreground = logout.style?.foregroundColor?.resolve(<WidgetState>{});
    expect(background, isNot(Colors.white));
    expect(foreground, VanRidesMapTokens.textPrimary);
  });

  testWidgets(
    'usa fallback de avatar e estados vazios sem viagem selecionada',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: PassengerHomeAccountTab(
            nome: '',
            email: 'sem-viagem@vanrides.dev',
            trip: null,
            dayTrips: const [],
            onLogout: () {},
          ),
        ),
      );

      expect(
        find.byKey(const Key('account.profile.avatarInitials')),
        findsOneWidget,
      );
      expect(find.text('Não informado'), findsWidgets);
      expect(find.text('ETA em cálculo'), findsNothing);
      expect(find.text('Sem viagem selecionada'), findsNothing);
      expect(find.text('Parada ainda não publicada'), findsNothing);
      expect(find.text('sem-viagem@vanrides.dev'), findsWidgets);
    },
  );

  testWidgets('carrega perfil real quando aba de conta esta ativa', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: PassengerHomeAccountTab(
          nome: 'Ana Login',
          email: 'ana.login@vanrides.dev',
          enabled: true,
          accountProfileGateway: const FakeAccountProfileGateway.passenger(),
          trip: null,
          dayTrips: const [],
          onLogout: () {},
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Ana Perfil Real'), findsOneWidget);
    expect(find.text('ana.real@vanrides.dev'), findsWidgets);
    expect(find.text('E-mail verificado'), findsOneWidget);
    expect(find.text('Passageiro'), findsWidgets);
    expect(find.text('20/05/2012'), findsOneWidget);
    expect(find.text('Ana Login'), findsNothing);
  });

  testWidgets('edita dados comuns e mostra sucesso real', (tester) async {
    final gateway = _EditableAccountProfileGateway(
      const AccountProfile(
        id: 21,
        tipoUsuario: 'Passageiro',
        papel: 'PASSENGER',
        nome: 'Ana Perfil Real',
        email: 'ana.real@vanrides.dev',
        telefone: '51999990021',
        dataNascimento: '2012-05-20',
        fotoPerfilUrl: _tinyPngDataUri,
        emailVerified: true,
        empresa: AccountCompany(id: 5, nome: 'Feevale'),
        passageiroId: 21,
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: PassengerHomeAccountTab(
          nome: 'Ana Login',
          email: 'ana.login@vanrides.dev',
          enabled: true,
          accountProfileGateway: gateway,
          trip: null,
          dayTrips: const [],
          onLogout: () {},
        ),
      ),
    );

    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const Key('passenger.account.editProfileButton')),
    );
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const Key('account.profile.nameField')),
      'Ana Atualizada',
    );
    await tester.enterText(
      find.byKey(const Key('account.profile.phoneField')),
      '51988888888',
    );
    await tester.enterText(
      find.byKey(const Key('account.profile.birthDateField')),
      '2011-08-09',
    );
    await tester.tap(find.byKey(const Key('account.profile.saveButton')));
    await tester.pumpAndSettle();

    expect(gateway.lastUpdate?.toJson(), {
      'nome': 'Ana Atualizada',
      'telefone': '51988888888',
      'dataNascimento': '2011-08-09',
    });
    expect(find.text('Ana Atualizada'), findsWidgets);
    expect(find.text('09/08/2011'), findsOneWidget);
  });

  testWidgets('acao de sair aciona callback recebido', (tester) async {
    var logoutCalled = false;

    await tester.pumpWidget(
      MaterialApp(
        home: PassengerHomeAccountTab(
          nome: 'Ana',
          email: 'ana@vanrides.dev',
          trip: null,
          dayTrips: const [],
          onLogout: () => logoutCalled = true,
        ),
      ),
    );

    await tester.dragUntilVisible(
      find.text('Sair da conta'),
      find.byType(ListView),
      const Offset(0, -300),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Sair da conta'));
    await tester.pump();

    expect(logoutCalled, isTrue);
  });
}

class _EditableAccountProfileGateway
    implements AccountProfileGateway, AccountProfileEditableGateway {
  _EditableAccountProfileGateway(this.profile);

  AccountProfile profile;
  AccountProfileUpdate? lastUpdate;

  @override
  Future<AccountProfile> fetchMe() async {
    await Future<void>.delayed(Duration.zero);
    return profile;
  }

  @override
  Future<AccountProfile> updateProfile(AccountProfileUpdate update) async {
    await Future<void>.delayed(Duration.zero);
    lastUpdate = update;
    profile = profile.copyWith(
      nome: update.nome ?? profile.nome,
      telefone: update.telefone ?? profile.telefone,
      dataNascimento: update.dataNascimento ?? profile.dataNascimento,
      avatarUrl: update.fotoPerfilUrl ?? profile.fotoPerfilUrl,
    );
    return profile;
  }

  @override
  Future<AccountProfile> updateMe({
    required String nome,
    required String telefone,
    String? dataNascimento,
  }) async {
    await Future<void>.delayed(Duration.zero);
    final update = AccountProfileUpdate(
      nome: nome,
      telefone: telefone,
      dataNascimento: dataNascimento,
    );
    lastUpdate = update;
    profile = profile.copyWith(
      nome: update.nome ?? profile.nome,
      telefone: update.telefone ?? profile.telefone,
      dataNascimento: update.dataNascimento ?? profile.dataNascimento,
    );
    return profile;
  }
}

const _tinyPngDataUri =
    'data:image/png;base64,'
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=';
