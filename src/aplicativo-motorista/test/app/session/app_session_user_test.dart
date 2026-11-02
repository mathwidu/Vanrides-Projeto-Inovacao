import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/session/app_company_link_status.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_user.dart';
import 'package:vanrides_frontend_dev/app/session/app_user_role.dart';

void main() {
  test('extrai nome limpo da mensagem de boas-vindas do responsavel', () {
    final sessionUser = AppSessionUser.fromLoginResponse(
      data: {
        'id': 9,
        'tipoUsuario': 'Responsavel',
        'mensagem': 'Bem-vindo, Responsavel Matheus Responsavel!',
        'accessToken': 'header.payload.signature',
      },
      email: 'responsavel@vanrides.dev',
      password: 'senha123',
    );

    expect(sessionUser.displayName, 'Matheus Responsavel');
    expect(sessionUser.role, AppUserRole.guardian);
  });

  test('preserva nome explícito quando o backend o fornecer', () {
    final sessionUser = AppSessionUser.fromLoginResponse(
      data: {
        'id': 1,
        'tipoUsuario': 'Admin',
        'nome': 'Admin VanRides',
        'mensagem': 'Bem-vindo, Admin Admin VanRides!',
        'accessToken': 'header.payload.signature',
      },
      email: 'admin@vanrides.dev',
      password: 'senha123',
    );

    expect(sessionUser.displayName, 'Admin VanRides');
    expect(sessionUser.role, AppUserRole.companyAdmin);
  });

  test('interpreta estado de onboarding do passageiro sem empresa', () {
    final sessionUser = AppSessionUser.fromLoginResponse(
      data: {
        'id': 22,
        'tipoUsuario': 'Passageiro',
        'mensagem': 'Bem-vindo, Passageiro Bruna!',
        'companyLinkStatus': 'NO_COMPANY',
        'accessToken': 'header.payload.signature',
      },
      email: 'bruna@vanrides.dev',
      password: 'senha123',
    );

    expect(sessionUser.role, AppUserRole.passenger);
    expect(sessionUser.companyLinkStatus, AppCompanyLinkStatus.noCompany);
    expect(sessionUser.needsCompanyOnboarding, isTrue);
  });

  test('extrai companyId do accessToken quando presente no JWT', () {
    final sessionUser = AppSessionUser.fromLoginResponse(
      data: {
        'id': 22,
        'tipoUsuario': 'Passageiro',
        'mensagem': 'Bem-vindo, Passageiro Bruna!',
        'accessToken':
            'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJjb21wYW55SWQiOjk5fQ.signature',
      },
      email: 'bruna@vanrides.dev',
      password: 'senha123',
    );

    expect(sessionUser.companyId, 99);
  });

  test('rejeita payload de login sem id numerico', () {
    expect(
      () => AppSessionUser.fromLoginResponse(
        data: {
          'tipoUsuario': 'Passageiro',
          'mensagem': 'Bem-vindo, Passageiro Bruna!',
          'accessToken': 'header.payload.signature',
        },
        email: 'bruna@vanrides.dev',
        password: 'senha123',
      ),
      throwsA(isA<FormatException>()),
    );
  });

  test('rejeita payload de login sem tipoUsuario textual', () {
    expect(
      () => AppSessionUser.fromLoginResponse(
        data: {
          'id': 22,
          'tipoUsuario': 123,
          'accessToken': 'header.payload.signature',
        },
        email: 'bruna@vanrides.dev',
        password: 'senha123',
      ),
      throwsA(isA<FormatException>()),
    );
  });
}
