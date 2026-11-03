import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_theme.dart';
import 'package:vanrides_frontend_dev/features/account/data/account_profile_service.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_password_change.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_profile.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_profile_update.dart';
import 'package:vanrides_frontend_dev/features/account/presentation/account_profile_screen.dart';
import 'package:vanrides_frontend_dev/features/account/presentation/change_account_password_sheet.dart';

void main() {
  testWidgets('AccountProfileScreen mantém primeira dobra completa por papel', (
    tester,
  ) async {
    final cases = <_ProfileFirstFoldCase>[
      _ProfileFirstFoldCase(
        profile: _completeProfile(
          id: 21,
          role: 'PASSENGER',
          type: 'Passageiro',
          name: 'Ana Passageira',
          email: 'ana@vanrides.dev',
        ),
        roleLabel: 'Passageiro',
      ),
      _ProfileFirstFoldCase(
        profile: _completeProfile(
          id: 7,
          role: 'DRIVER',
          type: 'Motorista',
          name: 'Maria Motorista',
          email: 'maria@vanrides.dev',
        ),
        roleLabel: 'Motorista',
      ),
      _ProfileFirstFoldCase(
        profile: _completeProfile(
          id: 77,
          role: 'GUARDIAN',
          type: 'Responsavel',
          name: 'Marina Responsável',
          email: 'marina@vanrides.dev',
        ),
        roleLabel: 'Responsável',
      ),
      _ProfileFirstFoldCase(
        profile: _completeProfile(
          id: 3,
          role: 'COMPANY_ADMIN',
          type: 'Admin',
          name: 'Admin VanRides',
          email: 'admin@vanrides.dev',
        ),
        roleLabel: 'Administrador',
      ),
    ];

    for (final profileCase in cases) {
      await _pumpAccountProfile(tester, profileCase.profile);

      expect(find.text(profileCase.profile.nome), findsOneWidget);
      expect(find.text(profileCase.profile.email), findsWidgets);
      expect(find.text(profileCase.roleLabel), findsWidgets);
      expect(find.text('E-mail verificado'), findsOneWidget);
      expect(find.text('20/05/1992'), findsOneWidget);
      expect(
        find.byKey(const Key('account.profile.avatarImage')),
        findsOneWidget,
      );
      expect(find.text('Perfil operacional incompleto'), findsNothing);
      expect(find.text('Perfil operacional completo'), findsNothing);
      expect(find.text('Completar cadastro'), findsNothing);

      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpAndSettle();
    }
  });

  testWidgets(
    'AccountProfileScreen trata foto pendente como recomendação leve',
    (tester) async {
      await _pumpAccountProfile(
        tester,
        _completeProfile(
          id: 8,
          role: 'PASSENGER',
          type: 'Passageiro',
          name: 'Ana Sem Foto',
          email: 'ana.sem.foto@vanrides.dev',
          photoUrl: null,
        ),
      );

      expect(find.text('Perfil operacional completo'), findsOneWidget);
      expect(find.text('Foto recomendada'), findsOneWidget);
      expect(find.text('Perfil operacional incompleto'), findsNothing);
      expect(find.text('Telefone obrigatório'), findsNothing);
      expect(find.text('Nascimento obrigatório'), findsNothing);
    },
  );

  testWidgets('AccountProfileScreen envia foto pelo fluxo comum', (
    tester,
  ) async {
    final gateway = _RecordingAccountProfileGateway(
      const AccountProfile(
        id: 7,
        tipoUsuario: 'Motorista',
        papel: 'DRIVER',
        nome: 'Maria Motorista',
        email: 'maria@vanrides.dev',
        telefone: '51999990007',
        emailVerified: true,
        motoristaId: 7,
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: AccountProfileScreen(
          surfaceKey: const Key('account.test.surface'),
          editButtonKey: const Key('account.test.edit'),
          logoutButtonKey: const Key('account.test.logout'),
          fallbackName: 'Maria Login',
          fallbackEmail: 'maria.login@vanrides.dev',
          roleFallback: 'Motorista',
          avatarFallback: 'M',
          enabled: true,
          accountProfileGateway: gateway,
          photoPicker: () async => _tinyPngDataUri,
          onLogout: () {},
        ),
      ),
    );

    await tester.pumpAndSettle();
    expect(find.byTooltip('Trocar foto'), findsOneWidget);

    await tester.tap(find.byKey(const Key('account.profile.photoButton')));
    await tester.pumpAndSettle();

    expect(gateway.lastUpdateFotoPerfilUrl, _tinyPngDataUri);
    expect(gateway.profile.fotoPerfilUrl, _tinyPngDataUri);
  });

  testWidgets('AccountProfileScreen orienta perfil operacional incompleto', (
    tester,
  ) async {
    final gateway = _RecordingAccountProfileGateway(
      const AccountProfile(
        id: 8,
        tipoUsuario: 'Administrador da empresa',
        papel: 'ADMIN',
        nome: 'Admin Portal',
        email: 'admin@vanrides.dev',
        telefone: '',
        emailVerified: true,
        companyAdminId: 8,
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: AccountProfileScreen(
          surfaceKey: const Key('account.test.surface'),
          editButtonKey: const Key('account.test.edit'),
          logoutButtonKey: const Key('account.test.logout'),
          fallbackName: 'Admin Login',
          fallbackEmail: 'admin.login@vanrides.dev',
          roleFallback: 'Administrador',
          avatarFallback: 'A',
          enabled: true,
          accountProfileGateway: gateway,
          onLogout: () {},
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Perfil operacional incompleto'), findsOneWidget);
    expect(find.text('Telefone obrigatório'), findsOneWidget);
    expect(find.text('Nascimento obrigatório'), findsOneWidget);
    expect(find.text('Foto recomendada'), findsOneWidget);
    expect(find.text('Completar cadastro'), findsOneWidget);
  });

  testWidgets('AccountProfileScreen permite alterar senha da conta', (
    tester,
  ) async {
    final gateway = _RecordingAccountProfileGateway(
      _completeProfile(
        id: 21,
        role: 'PASSENGER',
        type: 'Passageiro',
        name: 'Ana Passageira',
        email: 'ana@vanrides.dev',
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: VanRidesTheme.dark(),
        home: AccountProfileScreen(
          surfaceKey: const Key('account.test.surface'),
          editButtonKey: const Key('account.test.edit'),
          logoutButtonKey: const Key('account.test.logout'),
          fallbackName: 'Ana Login',
          fallbackEmail: 'ana.login@vanrides.dev',
          roleFallback: 'Passageiro',
          avatarFallback: 'A',
          enabled: true,
          accountProfileGateway: gateway,
          onLogout: () {},
        ),
      ),
    );

    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const Key('account.profile.changePasswordButton')),
    );
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('account.password.currentField')),
      'Atual1234',
    );
    await tester.enterText(
      find.byKey(const Key('account.password.newField')),
      'Nova12345',
    );
    await tester.enterText(
      find.byKey(const Key('account.password.confirmField')),
      'Nova12345',
    );
    await tester.tap(find.byKey(const Key('account.password.saveButton')));
    await tester.pumpAndSettle();

    expect(gateway.lastPasswordChange?.senhaAtual, 'Atual1234');
    expect(gateway.lastPasswordChange?.novaSenha, 'Nova12345');
    expect(find.text('Senha alterada com sucesso.'), findsOneWidget);
  });

  testWidgets('AccountProfileScreen mostra erro de senha no sheet', (
    tester,
  ) async {
    final gateway = _RecordingAccountProfileGateway(
      _completeProfile(
        id: 22,
        role: 'DRIVER',
        type: 'Motorista',
        name: 'Bia Motorista',
        email: 'bia@vanrides.dev',
      ),
    )..passwordError = const AccountProfileException('Senha atual inválida');

    await tester.pumpWidget(
      MaterialApp(
        theme: VanRidesTheme.dark(),
        home: AccountProfileScreen(
          surfaceKey: const Key('account.test.error.surface'),
          editButtonKey: const Key('account.test.error.edit'),
          logoutButtonKey: const Key('account.test.error.logout'),
          fallbackName: 'Bia Login',
          fallbackEmail: 'bia.login@vanrides.dev',
          roleFallback: 'Motorista',
          avatarFallback: 'B',
          enabled: true,
          accountProfileGateway: gateway,
          onLogout: () {},
        ),
      ),
    );

    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const Key('account.profile.changePasswordButton')),
    );
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('account.password.currentField')),
      'AtualErrada',
    );
    await tester.enterText(
      find.byKey(const Key('account.password.newField')),
      'Nova12345',
    );
    await tester.enterText(
      find.byKey(const Key('account.password.confirmField')),
      'Nova12345',
    );
    await tester.tap(find.byKey(const Key('account.password.saveButton')));
    await tester.pumpAndSettle();

    expect(find.text('Senha atual inválida'), findsOneWidget);
    expect(find.byType(ChangeAccountPasswordSheet), findsOneWidget);
  });
}

Future<void> _pumpAccountProfile(
  WidgetTester tester,
  AccountProfile profile,
) async {
  await tester.pumpWidget(
    MaterialApp(
      theme: VanRidesTheme.dark(),
      home: AccountProfileScreen(
        surfaceKey: const Key('account.test.surface'),
        editButtonKey: const Key('account.test.edit'),
        logoutButtonKey: const Key('account.test.logout'),
        fallbackName: '${profile.nome} Login',
        fallbackEmail: 'login.${profile.email}',
        roleFallback: profile.tipoUsuario,
        avatarFallback: profile.nome.substring(0, 1),
        enabled: true,
        accountProfileGateway: _RecordingAccountProfileGateway(profile),
        onLogout: () {},
      ),
    ),
  );
  await tester.pumpAndSettle();
}

AccountProfile _completeProfile({
  required int id,
  required String role,
  required String type,
  required String name,
  required String email,
  String? photoUrl = _tinyPngDataUri,
}) {
  return AccountProfile(
    id: id,
    tipoUsuario: type,
    papel: role,
    nome: name,
    email: email,
    telefone: '51999990000',
    dataNascimento: '1992-05-20',
    avatarUrl: photoUrl,
    emailVerified: true,
  );
}

class _ProfileFirstFoldCase {
  const _ProfileFirstFoldCase({required this.profile, required this.roleLabel});

  final AccountProfile profile;
  final String roleLabel;
}

class _RecordingAccountProfileGateway
    implements
        AccountProfileGateway,
        AccountProfileEditableGateway,
        AccountPasswordGateway {
  _RecordingAccountProfileGateway(this.profile);

  AccountProfile profile;
  String? lastUpdateFotoPerfilUrl;
  AccountPasswordChange? lastPasswordChange;
  Object? passwordError;

  @override
  Future<AccountProfile> fetchMe() async => profile;

  @override
  Future<AccountProfile> updateMe({
    required String nome,
    required String telefone,
    String? dataNascimento,
  }) async {
    profile = profile.copyWith(
      nome: nome,
      telefone: telefone,
      dataNascimento: dataNascimento,
    );
    return profile;
  }

  @override
  Future<AccountProfile> updateProfile(AccountProfileUpdate update) async {
    lastUpdateFotoPerfilUrl = update.fotoPerfilUrl;
    profile = profile.copyWith(
      nome: update.nome ?? profile.nome,
      telefone: update.telefone ?? profile.telefone,
      dataNascimento: update.dataNascimento ?? profile.dataNascimento,
      avatarUrl: update.fotoPerfilUrl ?? profile.fotoPerfilUrl,
    );
    return profile;
  }

  @override
  Future<void> changePassword(AccountPasswordChange change) async {
    lastPasswordChange = change;
    final error = passwordError;
    if (error != null) {
      throw error;
    }
  }
}

const _tinyPngDataUri =
    'data:image/png;base64,'
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=';
