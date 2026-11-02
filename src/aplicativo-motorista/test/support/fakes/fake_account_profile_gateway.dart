import 'package:vanrides_frontend_dev/features/account/data/account_profile_service.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_profile.dart';

class FakeAccountProfileGateway implements AccountProfileGateway {
  const FakeAccountProfileGateway(this.profile);

  const FakeAccountProfileGateway.passenger()
    : profile = const AccountProfile(
        id: 21,
        tipoUsuario: 'Passageiro',
        papel: 'PASSENGER',
        nome: 'Ana Perfil Real',
        email: 'ana.real@vanrides.dev',
        telefone: '51999990021',
        dataNascimento: '2012-05-20',
        avatarUrl: '/uploads/avatars/passenger-21.png',
        menorDeIdade: true,
        emailVerified: true,
        empresa: AccountCompany(id: 5, nome: 'Feevale'),
        passageiroId: 21,
        motoristaId: 7,
      );

  const FakeAccountProfileGateway.driver()
    : profile = const AccountProfile(
        id: 7,
        tipoUsuario: 'Motorista',
        papel: 'DRIVER',
        nome: 'Maria Perfil Real',
        email: 'maria.real@vanrides.dev',
        telefone: '51999990007',
        dataNascimento: '1990-01-15',
        avatarUrl: '/uploads/avatars/driver-7.png',
        emailVerified: true,
        empresa: AccountCompany(id: 5, nome: 'Feevale'),
        motoristaId: 7,
      );

  const FakeAccountProfileGateway.guardian()
    : profile = const AccountProfile(
        id: 77,
        tipoUsuario: 'Responsavel',
        papel: 'GUARDIAN',
        nome: 'Marina Perfil Real',
        email: 'marina.real@vanrides.dev',
        telefone: '51999990077',
        dataNascimento: '1988-04-12',
        avatarUrl: '/uploads/avatars/guardian-77.png',
        emailVerified: true,
        empresa: AccountCompany(id: 5, nome: 'Feevale'),
        responsavelId: 77,
      );

  const FakeAccountProfileGateway.admin()
    : profile = const AccountProfile(
        id: 3,
        tipoUsuario: 'Admin',
        papel: 'COMPANY_ADMIN',
        nome: 'Admin Perfil Real',
        email: 'admin.real@vanrides.dev',
        telefone: '51999990003',
        dataNascimento: '1988-04-12',
        avatarUrl: '/uploads/avatars/admin-3.png',
        emailVerified: true,
        empresa: AccountCompany(id: 5, nome: 'Feevale', slug: 'feevale'),
        companyAdminId: 3,
      );

  final AccountProfile profile;

  @override
  Future<AccountProfile> fetchMe() async {
    await Future<void>.delayed(Duration.zero);
    return profile;
  }

  @override
  Future<AccountProfile> updateMe({
    required String nome,
    required String telefone,
    String? dataNascimento,
  }) async {
    await Future<void>.delayed(Duration.zero);
    return profile.copyWith(
      nome: nome,
      telefone: telefone,
      dataNascimento: dataNascimento,
    );
  }
}
