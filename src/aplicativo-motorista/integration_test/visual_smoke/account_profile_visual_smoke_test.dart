import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_theme.dart';
import 'package:vanrides_frontend_dev/features/account/data/account_profile_service.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_profile.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_profile_update.dart';
import 'package:vanrides_frontend_dev/features/account/presentation/account_profile_screen.dart';

void main() {
  testWidgets(
    'account profile complete and incomplete visual states',
    (tester) async {
      for (final scenario in _scenarios) {
        await tester.pumpWidget(
          MaterialApp(
            theme: VanRidesTheme.dark(),
            debugShowCheckedModeBanner: false,
            home: AccountProfileScreen(
              surfaceKey: const Key('account.visual.surface'),
              editButtonKey: const Key('account.visual.edit'),
              logoutButtonKey: const Key('account.visual.logout'),
              fallbackName: '${scenario.profile.nome} Login',
              fallbackEmail: 'login.${scenario.profile.email}',
              roleFallback: scenario.profile.tipoUsuario,
              avatarFallback: scenario.profile.nome.substring(0, 1),
              enabled: true,
              accountProfileGateway: _StaticAccountProfileGateway(
                scenario.profile,
              ),
              onLogout: () {},
            ),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text(scenario.profile.nome), findsOneWidget);
        expect(find.text(scenario.roleLabel), findsWidgets);
        expect(find.text('E-mail verificado'), findsOneWidget);

        if (scenario.complete) {
          expect(find.text('Perfil operacional incompleto'), findsNothing);
          expect(find.text('Completar cadastro'), findsNothing);
          expect(
            find.byKey(const Key('account.profile.avatarImage')),
            findsOneWidget,
          );
        } else {
          expect(find.text('Perfil operacional incompleto'), findsOneWidget);
          expect(find.text('Nascimento obrigatório'), findsOneWidget);
          expect(find.text('Foto recomendada'), findsOneWidget);
        }

        await _visualReady(tester, scenario.name);
      }
    },
    timeout: const Timeout(Duration(minutes: 3)),
  );
}

Future<void> _visualReady(WidgetTester tester, String state) async {
  await tester.pumpAndSettle(const Duration(milliseconds: 250));
  debugPrintSynchronously('VANRIDES_ACCOUNT_PROFILE_VISUAL_READY:$state');
  await tester.pump(const Duration(seconds: 2));
}

final _scenarios = <_AccountProfileVisualScenario>[
  _AccountProfileVisualScenario.complete(
    name: 'account-passenger-complete',
    roleLabel: 'Passageiro',
    profile: _profile(
      id: 21,
      type: 'Passageiro',
      role: 'PASSENGER',
      name: 'Ana Passageira',
      email: 'ana.complete@vanrides.dev',
    ),
  ),
  _AccountProfileVisualScenario.complete(
    name: 'account-driver-complete',
    roleLabel: 'Motorista',
    profile: _profile(
      id: 7,
      type: 'Motorista',
      role: 'DRIVER',
      name: 'Maria Motorista',
      email: 'maria.complete@vanrides.dev',
    ),
  ),
  _AccountProfileVisualScenario.complete(
    name: 'account-guardian-complete',
    roleLabel: 'Responsável',
    profile: _profile(
      id: 77,
      type: 'Responsavel',
      role: 'GUARDIAN',
      name: 'Marina Responsável',
      email: 'marina.complete@vanrides.dev',
    ),
  ),
  _AccountProfileVisualScenario.complete(
    name: 'account-admin-complete',
    roleLabel: 'Administrador',
    profile: _profile(
      id: 3,
      type: 'Admin',
      role: 'COMPANY_ADMIN',
      name: 'Admin VanRides',
      email: 'admin.complete@vanrides.dev',
    ),
  ),
  _AccountProfileVisualScenario.incomplete(
    name: 'account-passenger-incomplete',
    roleLabel: 'Passageiro',
    profile: _profile(
      id: 22,
      type: 'Passageiro',
      role: 'PASSENGER',
      name: 'Ana Sem Nascimento',
      email: 'ana.incomplete@vanrides.dev',
      birthDate: null,
      photoUrl: null,
    ),
  ),
];

AccountProfile _profile({
  required int id,
  required String type,
  required String role,
  required String name,
  required String email,
  String? birthDate = '1992-05-20',
  String? photoUrl = _tinyPngDataUri,
}) {
  return AccountProfile(
    id: id,
    tipoUsuario: type,
    papel: role,
    nome: name,
    email: email,
    telefone: '51999990000',
    dataNascimento: birthDate,
    avatarUrl: photoUrl,
    emailVerified: true,
  );
}

class _AccountProfileVisualScenario {
  const _AccountProfileVisualScenario.complete({
    required this.name,
    required this.roleLabel,
    required this.profile,
  }) : complete = true;

  const _AccountProfileVisualScenario.incomplete({
    required this.name,
    required this.roleLabel,
    required this.profile,
  }) : complete = false;

  final String name;
  final String roleLabel;
  final AccountProfile profile;
  final bool complete;
}

class _StaticAccountProfileGateway
    implements AccountProfileGateway, AccountProfileEditableGateway {
  const _StaticAccountProfileGateway(this.profile);

  final AccountProfile profile;

  @override
  Future<AccountProfile> fetchMe() async => profile;

  @override
  Future<AccountProfile> updateMe({
    required String nome,
    required String telefone,
    String? dataNascimento,
  }) async {
    return profile.copyWith(
      nome: nome,
      telefone: telefone,
      dataNascimento: dataNascimento,
    );
  }

  @override
  Future<AccountProfile> updateProfile(AccountProfileUpdate update) async {
    return profile.copyWith(
      nome: update.nome ?? profile.nome,
      telefone: update.telefone ?? profile.telefone,
      dataNascimento: update.dataNascimento ?? profile.dataNascimento,
      avatarUrl: update.fotoPerfilUrl ?? profile.fotoPerfilUrl,
    );
  }
}

const _tinyPngDataUri =
    'data:image/png;base64,'
    'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAEUlEQVR4nGPw2HfnPwgzwBgAYzALhQCsEKIAAAAASUVORK5CYII=';
